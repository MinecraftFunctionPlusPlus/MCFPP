package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.NBTMap
import top.mcfpp.core.lang.nbt.NBTMapConcrete
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class MapMemberTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }).also {
        assertEquals(0, it.stackDepth)
    }

    @Test fun constantAndRuntimeMapsExposeTheSameMemberSignatures() {
        compile("func main(){}")
        assertSame(NBTMap.data, NBTMapConcrete.data)
    }

    @Test fun overwritingAKeyKeepsOneEntryAndViewsShareWrites() {
        val main = compile("""
            func main(){
                var source = {entries:[{key:"first",value:2},{key:"second",value:3}]};
                var values = source as map<int>;
                values["first"] = 4;
                values["third"] = 5;
                values["third"] = 6;
                dynamic var result = values.size()*100 + values["first"]*10 + values["third"];
                dynamic var rootValue = (source["entries"][0]["value"] as int) + 0;
            }
        """)
        val machine = execute(main)
        assertEquals(346, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(4, machine.read(main.scope.getVar("rootValue") as MCInt))
    }

    @Test fun ordinaryMapCopiesKeepIndependentStorage() {
        val main = compile("""
            func main(){
                var values = {entries:[{key:"first",value:2}]} as map<int>;
                var copy = values;
                copy["first"] = 7;
                copy["next"] = 9;
                dynamic var result = values.size()*100 + values["first"]*10 + copy["first"];
            }
        """)
        assertEquals(127, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun runtimeQueriesAndMutationsSupportParametersAndIndependentResults() {
        val main = compile("""
            func edit(values as map<int>, key as string, value as int) -> map<int> {
                values[key] = value;
                return values;
            }
            func lookup(values as map<int>, key as string) -> bool { return values.containsKey(key); }
            func main(){
                var original = {entries:[{key:"first",value:2}]} as map<int>;
                var changed = edit(original,"first",7);
                dynamic var found = lookup(changed,"first");
                dynamic var missing = lookup(changed,"absent");
                dynamic var empty = changed.isEmpty();
                dynamic var result = original["first"]*100 + changed["first"]*10 + changed.size();
            }
        """)
        val machine = execute(main)
        assertEquals(271, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("found") as ScoreBool))
        assertEquals(0, machine.read(main.scope.getVar("missing") as ScoreBool))
        assertEquals(0, machine.read(main.scope.getVar("empty") as ScoreBool))
    }

    @Test fun mergingMapsReplacesWholeValuesAndDeduplicatesKeys() {
        val main = compile("""
            func overlay(values as map<dict<int>>, extra as map<dict<int>>) -> map<dict<int>> {
                values.merge(extra);
                return values;
            }
            func main(){
                var left = {entries:[{key:"same",value:{old:2}},{key:"other",value:{keep:3}}]} as map<dict<int>>;
                var right = {entries:[{key:"same",value:{fresh:7}},{key:"new",value:{fresh:9}}]} as map<dict<int>>;
                var result = overlay(left,right);
                dynamic var number = result.size()*100 + result["same"]["fresh"]*10 + result["other"]["keep"];
                dynamic var old = result["same"].containsKey("old");
            }
        """)
        val machine = execute(main)
        assertEquals(373, machine.read(main.scope.getVar("number") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("old") as ScoreBool))
    }

    @Test fun unknownKeyRemovalWorksOnTargetsWithoutMacros() {
        val main = compile("""
            func erase(values as map<int>, key as string) -> map<int> { values.remove(key); return values; }
            func main(){
                var values = {entries:[{key:"first",value:2},{key:"second",value:3}]} as map<int>;
                var result = erase(values,"first");
                dynamic var number = result.size()*10 + result["second"];
            }
        """, "1.20.1")
        assertEquals(13, execute(main).read(main.scope.getVar("number") as MCInt))
        assertTrue(Project.macroFunction.isEmpty())
    }

    @Test fun runtimeStringKeysUseCompoundPredicatesWithTheirFullEscaping() {
        val main = compile("""
            func lookup(values as map<int>, key as string) -> int { return values[key]; }
            func edit(values as map<int>, key as string) -> map<int> { values[key] = 8; return values; }
            func main(){
                var values = {entries:[{key:"",value:2},{key:"q\".[]\\end",value:3}]} as map<int>;
                var result = edit(values,"q\".[]\\end");
                dynamic var number = lookup(values,"")*100 + lookup(result,"q\".[]\\end")*10 + result.size();
            }
        """, "1.20.2")
        assertEquals(282, execute(main).read(main.scope.getVar("number") as MCInt))
        assertTrue(Project.macroFunction.values.any { "[$(" in it })
    }

    @Test fun runtimeKeyInsertionAndReplacementWorkWithoutMacros() {
        val main = compile("""
            func put(values as map<int>, key as string, value as int) -> map<int> {
                values[key] = value;
                values[key] = value+1;
                return values;
            }
            func main(){
                var values = {entries:[{key:"first",value:2}]} as map<int>;
                var result = put(values,"second",7);
                dynamic var number = result.size()*10 + result["second"];
            }
        """, "1.20.1")
        assertEquals(28, execute(main).read(main.scope.getVar("number") as MCInt))
        assertTrue(Project.macroFunction.isEmpty())
    }

    @Test fun unknownSourceMergeInitializesTheReceiverOnceAndCopiesIncomingValues() {
        val main = compile("""
            func overlay(extra as map<int>) -> map<int> {
                var values = {entries:[{key:"first",value:2}]} as map<int>;
                values.merge(extra);
                values.merge(values);
                return values;
            }
            func main(){
                var extra = {entries:[{key:"second",value:3},{key:"third",value:4}]} as map<int>;
                var result = overlay(extra);
                dynamic var number = result.size()*1000 + result["first"]*100 + result["second"]*10 + result["third"];
            }
        """, "1.20.1")
        assertEquals(3234, execute(main).read(main.scope.getVar("number") as MCInt))
    }

    @Test fun keysAreIndependentProjectionsOfTheCurrentMembership() {
        val main = compile("""
            func allKeys(values as map<int>) -> list<string> { return values.keys; }
            func main(){
                var values = {entries:[{key:"first",value:2},{key:"second",value:3}]} as map<int>;
                var names = allKeys(values);
                names.remove("first");
                dynamic var original = values.size();
                dynamic var found = names.contains("second");
                dynamic var removed = names.contains("first");
            }
        """)
        val machine = execute(main)
        assertEquals(2, machine.read(main.scope.getVar("original") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("found") as ScoreBool))
        assertEquals(0, machine.read(main.scope.getVar("removed") as ScoreBool))
    }

    @Test fun runtimeMapsRejectCompilerOnlyValuesBeforeWriting() {
        val main = compile("func main(){ var values = {entries:[{key:\"first\",value:2 as any}]} as map<any>; }")
        val values = main.scope.getVar("values") as NBTMap
        val binding = StorageAccess.ensure(values)
        val before = binding.data.facts.fork()
        val commands = main.commands.analyzeAll().filterNot { it.startsWith("#") }
        Function.currFunction = main
        top.mcfpp.backend.MapOperations.put(values, top.mcfpp.core.lang.nbt.MCStringConcrete(top.mcfpp.nbt.tags.primitive.StringTag("first")),
            MCFPPTypeVar(top.mcfpp.type.MCFPPBaseType.Int))
        assertTrue(Project.errorCount > 0)
        assertEquals(before, binding.data.facts)
        assertEquals(commands, main.commands.analyzeAll().filterNot { it.startsWith("#") })
    }

    @Test fun uncheckedViewsDoNotConvertTheOldDualMapLayout() {
        MCFPPStringTest.readFromString("""
            func main(){ var values = {keys:["first"],keyValueSet:{first:2}} as map<int>; values.size(); }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun unknownKeyWritesKeepAUniformErasedValueTypeAndDropOldConstants() {
        val main = compile("""
            func edit(key as string, value as int) -> int {
                var values = {entries:[{key:"first",value:2 as any}]} as map<any>;
                values[key] = value;
                return values["first"] + values[key];
            }
            func main(){ dynamic var result = edit("first",7); }
        """)
        assertEquals(14, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun runtimeDictionaryProjectionDiagnosesUnsupportedEmptyMemberNames() {
        MCFPPStringTest.readFromString("""
            func main(){
                var values = {entries:[{key:"",value:2}]} as map<int>;
                var projected = values.keyValueSet;
            }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
    }
}
