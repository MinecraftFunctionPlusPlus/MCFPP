package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.backend.DictionaryOperations
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.nbt.NBTDictionaryConcrete
import top.mcfpp.core.lang.nbt.NBTMapConcrete
import top.mcfpp.core.lang.nbt.MCStringConcrete
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

    @Test fun constantAndRuntimeDictionariesExposeTheSameMemberSignatures() {
        compile("func main(){}")
        assertSame(NBTDictionary.data, NBTDictionaryConcrete.data)
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
        val value = type.build("known", hashMapOf<String, Var<*>>("value" to MCIntConcrete(4)))
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
            hasAssigned = true; isDynamic = true
        }
        val binding = StorageAccess.ensure(receiver)
        binding.data.facts.initialize(binding.place.field("kept"), ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
            ValueKnowledge.Constant(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(2)))))
        Function.addCommand(Commands.dataSetValue(receiver.nbtPath, CompoundTag().apply { put("kept", IntTag(2)) }))
        val before = binding.data.facts.fork()
        val commands = main.commands.analyzeAll().filterNot { it.startsWith("#") }
        val incoming = NBTDictionaryConcrete(hashMapOf("" to MCIntConcrete(7), "added" to MCIntConcrete(8)), "incoming")
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
        val nested = NBTDictionaryConcrete(hashMapOf("added" to MCIntConcrete(8)), "nested")
        val patch = NBTDictionaryConcrete(hashMapOf("patch" to nested, "scalar" to MCIntConcrete(7)), "patch")
        val values = NBTDictionaryConcrete(hashMapOf("patch" to patch, "runtime" to MCIntConcrete(0)), "values").apply {
            nbtPath = NBTPath.temp.memberIndex(identifier)
        }
        val binding = StorageAccess.ensure(values)
        binding.data.write(binding.place.field("runtime"), ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown))
        val incoming = StorageAccess.adapter(patch.type, "incoming", binding.copy(place = binding.place.field("patch"),
            path = binding.path.memberIndex("patch"))) as NBTDictionary
        DictionaryOperations.merge(values, incoming)
        assertEquals(0, Project.errorCount)
        val scalar = binding.data.facts.read(binding.place.field("scalar"))!!
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), scalar.type)
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)), assertIs<ValueKnowledge.Constant>(scalar.value).value)
        val result = assertIs<CompoundTag>(execute(main).readNbt("mcfpp:system", "temp.values"))
        assertEquals(7, assertIs<IntTag>(result["scalar"]).value)
        assertEquals(8, assertIs<IntTag>(assertIs<CompoundTag>(result["patch"])["added"]).value)
    }

    @Test fun compilerOnlyDictionariesCanStillMergeKnownEmptyKeysWithoutRuntimeEncoding() {
        val main = compile("func main(){}")
        Function.currFunction = main
        val values = NBTDictionaryConcrete(hashMapOf("kind" to MCFPPTypeVar(MCFPPBaseType.Int)), "values")
        val incoming = NBTDictionaryConcrete(hashMapOf("" to MCFPPTypeVar(MCFPPBaseType.Float)), "incoming")
        DictionaryOperations.merge(values, incoming)
        assertEquals(0, Project.errorCount)
        assertTrue(DictionaryOperations.containsKey(values, MCStringConcrete(StringTag(""))).let {
            assertIs<top.mcfpp.core.lang.bool.ScoreBoolConcrete>(it).value
        })
        assertNotNull(ValueSnapshot.of(values))
        assertEquals(StorageLayout.CompilerOnly, values.storageBinding!!.data.layout)
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "merge value" in it })
    }
}
