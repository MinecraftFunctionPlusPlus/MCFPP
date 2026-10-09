package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.backend.DictionaryOperations
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.lib.NBTPath
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDictType
import kotlin.test.*
import kotlin.test.Test

class DictionaryMemberTest {
    private fun compile(source: String): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }).also {
        assertEquals(0, it.stackDepth)
    }

    private fun dictionary(fields: Map<String, Var<*>>, name: String,
                           type: MCFPPDictType = MCFPPDictType(MCFPPBaseType.Any)): NBTDictionary {
        val initial = StorageAccess.dictionaryLiteral(type, fields, "$name-literal")
        val result = type.buildUnConcrete(name).apply { nbtPath = NBTPath.temp.memberIndex(name) }
        StorageAccess.declare(result, Symbol(SymbolId.fresh(), name, type.typeId, mutable = true))
        StorageAccess.write(result, initial)
        return assertIs<NBTDictionary>(result)
    }

    @Test fun constantAndRuntimeDictionariesExposeTheSameMemberSignatures() {
        compile("func main(){}")
        val type = MCFPPDictType(MCFPPBaseType.Int)
        val known = StorageAccess.dictionaryLiteral(type, mapOf("value" to MCInt(4)))
        val runtime = type.buildUnConcrete("runtime")
        assertSame(NBTDictionary.data, known.type.instanceData)
        assertSame(known.type.instanceData, runtime.type.instanceData)
        for (name in listOf("size", "isEmpty", "toText")) {
            val candidates = known.type.instanceData.scope.getFunctionCandidates(name).joinToString { "${it.identifier}<${(it as? top.mcfpp.model.function.NativeFunction)?.readOnlyParams.orEmpty()}>(${it.normalParams})" }
            val member = assertIs<top.mcfpp.model.function.NativeFunction>(known.getMemberFunction(name, emptyList(), emptyList(), top.mcfpp.model.Member.AccessModifier.PUBLIC).first, "Known $name; declarations: $candidates")
            val runtimeMember = assertIs<top.mcfpp.model.function.NativeFunction>(runtime.getMemberFunction(name, emptyList(), emptyList(), top.mcfpp.model.Member.AccessModifier.PUBLIC).first, "Runtime $name; declarations: $candidates")
            assertTrue(top.mcfpp.model.function.ParameterMatcher.sameSignature(member, runtimeMember))
            assertEquals(member.returnType.typeId, runtimeMember.returnType.typeId)
            assertEquals(member.javaMethod, runtimeMember.javaMethod)
        }
    }

    @Test fun mergeRejectsCompilerOnlyFieldsBeforeWritingARuntimeReceiver() {
        MCFPPStringTest.readFromString("""
            func main(){
                dynamic var values as dict<any> = {first:2};
                values.merge({kind:int});
            }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val values = main.scope.getVar("values")!!
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), values.storageBinding!!.data.facts.read(values.storageBinding!!.place.field("first"))!!.type)
        assertFalse(main.commands.analyzeAll().any { "merge from" in it || "merge value" in it })
    }

    @Test fun removingAQuotedKeyPreservesUnrelatedElementKnowledge() {
        val main = compile("""
            func main(){
                var source as dict<any> = {kept:2};
                source["a.b"] = 9;
                var view = source as dict<any>;
                view.remove("a.b");
                dynamic var kept = source["kept"] + 0;
                dynamic var absent = source.containsKey("a.b");
            }
        """)
        val machine = execute(main)
        assertEquals(2, machine.read(main.scope.getVar("kept") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("absent") as ScoreBool))
    }

    @Test fun mergingKnownFieldsIntoAPartialReceiverPreservesKnownSiblings() {
        val main = compile("""
            func update(source as dict<any>, runtime as int) -> int {
                source["runtime"] = runtime;
                source.merge({added:7});
                return source["added"] + (source["runtime"] as int);
            }
            func main(){
                var source as dict<any> = {kept:2};
                dynamic var result = update(source,5);
            }
        """)
        assertEquals(12, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun unknownStringMemberKeysHaveTheSameExplicitBackendDiagnosticAsIndexing() {
        for (operation in listOf("containsKey", "remove")) {
            MCFPPStringTest.readFromString("""
                func use(source as dict<any>, key as string){ source.$operation(key); }
                func main(){ var source as dict<any> = {first:2}; use(source,"first"); }
            """.trimIndent(), version = "26.3")
            assertTrue(Project.errorCount > 0, operation)
            assertTrue(Project.macroFunction.isEmpty(), operation)
        }
    }

    @Test fun dictionaryTypeFactoriesAndTemporariesKeepTheirGenericIdentity() {
        compile("func main(){}")
        val type = MCFPPDictType(MCFPPBaseType.Int)
        assertEquals(type.typeId, type.buildUnConcrete("unknown").type.typeId)
        val value = StorageAccess.dictionaryLiteral(type, mapOf("value" to MCInt(4)), "known")
        assertEquals(type.typeId, value.type.typeId)
        assertEquals(type.typeId, value.getTempVar().type.typeId)
    }

    @Test fun genericDictionaryParametersAndReturnsKeepTheirLayoutAndLanguageType() {
        val main = compile("""
            func copy(source as dict<int>) -> dict<int> { return source; }
            func main(){
                var source = {value:4} as dict<int>;
                var copied = copy(source);
                dynamic var result = copied["value"] + 1;
            }
        """)
        assertEquals(MCFPPDictType(MCFPPBaseType.Int).typeId, main.scope.getVar("copied")!!.type.typeId)
        assertEquals(5, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun aLegacyVisitorBooleanParameterReadsItsIncomingFrameAndKeepsBoolIdentity() {
        val main = compile("""
            func put(source as dict<any>, flag as bool) -> bool {
                source["flag"] = flag;
                return source["flag"] == true;
            }
            func main(){
                var source as dict<any> = {value:4};
                dynamic var result = put(source,true);
            }
        """)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as ScoreBool))
    }

    @Test fun mapDictionaryInteropUsesCurrentSharedStorageAfterNativeWrites() {
        val main = compile("""
            func main(){
                var caller = {entries:[{key:"first",value:2}]} as map<int>;
                var incoming = {entries:[{key:"second",value:7}]} as map<int>;
                caller.merge(incoming);
                var projection = caller.keyValueSet;
                caller.clear();
                dynamic var result = projection["second"] + caller.size();
            }
        """)
        assertEquals(7, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun aPartialDictionaryMergeDiagnosesUnsupportedKnownEmptyKeysBeforeWriting() {
        val main = compile("func main(){}")
        Function.currFunction = main
        val receiver = NBTDictionary("partial").apply {
            nbtPath = NBTPath.temp.memberIndex(identifier)
        }
        val binding = StorageAccess.bindIncomingParameter(receiver)
        binding.data.facts.initialize(binding.place.field("kept"), ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
            ValueKnowledge.Constant(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(2)))))
        Function.addCommand(Commands.dataSetValue(receiver.nbtPath, CompoundTag().apply { put("kept", IntTag(2)) }))
        val before = binding.data.facts.fork()
        val commands = main.commands.analyzeAll().filterNot { it.startsWith("#") }
        val incoming = StorageAccess.dictionaryLiteral(MCFPPDictType(MCFPPBaseType.Int), mapOf("" to MCInt(7), "added" to MCInt(8)), "incoming") as NBTDictionary
        DictionaryOperations.merge(receiver, incoming)
        assertTrue(Project.errorCount > 0)
        assertEquals(before, binding.data.facts)
        assertEquals(commands, main.commands.analyzeAll().filterNot { it.startsWith("#") })
        val machine = execute(main)
        val result = assertIs<CompoundTag>(machine.readNbt("mcfpp:system", "temp.partial"))
        assertNull(result[""])
        assertNull(result["added"])
        assertEquals(2, assertIs<IntTag>(result["kept"]).value)
    }

    @Test fun aBulkDeepMergeKeepsUnrelatedFactsAndRecordsKnownScalarInputs() {
        val main = compile("""
            func update(values as dict<any>, runtime as int) -> int {
                values["runtime"] = runtime;
                values.merge({nested:{added:8},scalar:7});
                return (values["runtime"] as int) + values["scalar"];
            }
            func main(){
                var values as dict<any> = {nested:{kept:2}};
                dynamic var result = update(values,5);
            }
        """)
        assertEquals(12, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun mergingAnUnknownRuntimeSourceCopiesWholeNbtAndDeepMergesExistingFields() {
        val main = compile("""
            func update(values as dict<any>, extra as dict<any>) -> int {
                values.merge(extra);
                var nested = values["nested"] as dict<any>;
                return (nested["kept"] as int)*100 + (nested["added"] as int)*10 + (values["scalar"] as int);
            }
            func main(){
                var values as dict<any> = {nested:{kept:2}};
                var extra as dict<any> = {nested:{added:8},scalar:7};
                dynamic var result = update(values,extra);
            }
        """)
        assertEquals(287, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun bulkMergeFreezesIncomingFactsBeforeAnOverlappingSourceIsInvalidated() {
        val main = compile("func main(){}")
        Function.currFunction = main
        val exit = main.commands.last()
        assertEquals(top.mcfpp.command.Commands.stackOut().analyze(), exit.toString())
        main.commands.removeAt(main.commands.lastIndex)
        val nested = dictionary(mapOf("added" to MCInt(8)), "nested")
        val patch = dictionary(mapOf("patch" to nested, "scalar" to MCInt(7)), "patch")
        val values = dictionary(mapOf("patch" to patch, "runtime" to MCInt(0)), "values")
        val binding = StorageAccess.ensure(values)
        StorageAccess.materialize(values)
        binding.data.write(binding.place.field("runtime"), ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown))
        val incoming = StorageAccess.adapter(patch.type, "incoming", binding.copy(place = binding.place.field("patch"),
            path = binding.path.memberIndex("patch"))) as NBTDictionary
        DictionaryOperations.merge(values, incoming)
        assertEquals(0, Project.errorCount)
        val scalar = binding.data.facts.read(binding.place.field("scalar"))!!
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), scalar.type)
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)), assertIs<ValueKnowledge.Constant>(scalar.value).value)
        StorageAccess.materialize(values)
        Function.addCommand(top.mcfpp.command.Command.buildAll(
            "data modify storage fixture:observation merged set from", binding.path))
        main.commands.add(exit)
        val machine = execute(main)
        val result = assertIs<CompoundTag>(machine.readNbt("fixture:observation", "merged"))
        assertEquals(7, assertIs<IntTag>(result["scalar"]).value)
        assertEquals(8, assertIs<IntTag>(assertIs<CompoundTag>(result["patch"])["added"]).value)
        assertEquals(0, machine.stackDepth)
    }

    @Test fun compilerOnlyDictionariesCanStillMergeKnownEmptyKeysWithoutRuntimeEncoding() {
        val main = compile("func main(){}")
        Function.currFunction = main
        val type = MCFPPDictType(top.mcfpp.type.MCFPPConcreteType.Type)
        val values = dictionary(mapOf("kind" to MCFPPTypeVar(MCFPPBaseType.Int)), "values", type)
        val incoming = dictionary(mapOf("" to MCFPPTypeVar(MCFPPBaseType.Float)), "incoming", type)
        DictionaryOperations.merge(values, incoming)
        assertEquals(0, Project.errorCount)
        val contains = DictionaryOperations.containsKey(values, MCString(StringTag("")))
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Bool.typeId, CompilerValue.Bool(true)), StorageAccess.snapshot(contains))
        assertEquals(CompilerValue.Typed(type.typeId, CompilerValue.Record(mapOf(
            "kind" to CompilerValue.Typed(top.mcfpp.type.MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(MCFPPBaseType.Int.typeId)),
            "" to CompilerValue.Typed(top.mcfpp.type.MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(MCFPPBaseType.Float.typeId))
        ))), StorageAccess.snapshot(values))
        assertEquals(StorageLayout.CompilerOnly, values.storageBinding!!.data.layout)
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "merge value" in it })
    }
}
