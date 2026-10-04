package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.nbt.NBTDictionaryConcrete
import top.mcfpp.core.lang.nbt.NBTMapConcrete
import top.mcfpp.mni.NBTMapConcreteData
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
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
                var extra = {kind:int};
                values.merge(extra);
            }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val values = main.scope.getVar("values")!!
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), values.storageBinding!!.data.facts.read(values.storageBinding!!.place.field("first"))!!.type)
        assertFalse(main.commands.analyzeAll().any { "merge from" in it || "merge value" in it })
    }

    @Test fun mergeCopiesStaticFieldsAndUpdatesAllViews() {
        val main = compile("""
            func main(){
                var source = {kind:int};
                var view = source as dict<any>;
                var extra = {other:float};
                view.merge(extra);
                extra["other"] = bool;
                var preserved = source["other"] as type;
                view.remove("kind");
                var removed = source.containsKey("kind");
            }
        """)
        assertEquals(MCFPPBaseType.Float, assertIs<MCFPPTypeVar>(main.scope.getVar("preserved")).value)
        val value = assertIs<CompilerValue.Typed>(ValueSnapshot.of(main.scope.getVar("removed"))).payload
        assertEquals(CompilerValue.Bool(false), value)
        assertNotNull(ValueSnapshot.of(main.scope.getVar("source")))
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
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

    @Test fun nestedMergesKeepBothSidesFieldsAndDoNotShareIncomingContainers() {
        val main = compile("""
            func main(){
                var source = {nested:{kept:int} as any};
                var view = source as dict<any>;
                var incoming = {nested:{added:float} as any};
                view.merge(incoming);
                incoming["nested"]["added"] = bool;
                var kept = source["nested"]["kept"] as type;
                var added = source["nested"]["added"] as type;
            }
        """)
        assertEquals(MCFPPBaseType.Int, assertIs<MCFPPTypeVar>(main.scope.getVar("kept")).value)
        assertEquals(MCFPPBaseType.Float, assertIs<MCFPPTypeVar>(main.scope.getVar("added")).value)
        assertNotNull(ValueSnapshot.of(main.scope.getVar("source")))
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
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

    @Test fun legacyMapHostAdaptersKeepTheirBehaviorAfterRemovingDictionaryConcreteSignatures() {
        compile("func main(){}")
        val caller = NBTMapConcrete(hashMapOf("first" to MCIntConcrete(2)), "caller", MCFPPBaseType.Int)
        val incoming = NBTMapConcrete(hashMapOf("second" to MCIntConcrete(7)), "incoming", MCFPPBaseType.Int)
        NBTMapConcreteData.merge(incoming, caller)
        assertEquals(7, assertIs<MCIntConcrete>(caller.value["second"]).value)
        assertSame(caller.value, assertIs<NBTDictionaryConcrete>(caller.keyValueSet).value)
        NBTMapConcreteData.clear(caller)
        assertTrue(caller.value.isEmpty())
        assertTrue(assertIs<NBTDictionaryConcrete>(caller.keyValueSet).value.isEmpty())
    }
}
