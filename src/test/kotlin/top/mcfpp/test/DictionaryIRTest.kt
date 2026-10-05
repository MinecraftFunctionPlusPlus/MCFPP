package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class DictionaryIRTest {
    private fun function(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue(name).single()
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return function("main").also { assertNotNull(it.typedIR) }
    }
    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }).also {
        assertEquals(0, it.stackDepth)
        assertTrue(it.branchGuards.isEmpty())
    }
    private fun modes(action: () -> Unit) {
        val original = CompileSettings.foldIRConstants
        try { for (enabled in listOf(true, false)) { CompileSettings.foldIRConstants = enabled; action() } }
        finally { CompileSettings.foldIRConstants = original }
    }

    @Test fun mutationsAndQueriesRunInsideTheSameLoopIR() = modes {
        for (version in listOf("26.3", "1.20.2", "1.20")) {
            val main = compile("""
                func main(){
                    var values as dict<int> = {first:2, second:9};
                    var i = 0;
                    while(i < 2){
                        values.remove("first");
                        values.merge({first:7} as dict<int>);
                        i += 1;
                    }
                    dynamic var present = values.containsKey("first");
                    dynamic var result = values["first"] + values["second"];
                    values.clear();
                    dynamic var removed = values.containsKey("first");
                }
            """, version)
            val machine = execute(main)
            assertEquals(16, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(1, machine.read(main.scope.getVar("present") as ScoreBool))
            assertEquals(0, machine.read(main.scope.getVar("removed") as ScoreBool))
        }
    }

    @Test fun deepMergeKeepsUntouchedFieldsAndCopiesTheSource() = modes {
        val main = compile("""
            func main(){
                var values as dict<any> = {nested:{first:2, second:9}, untouched:true};
                var source as dict<any> = {nested:{first:7}};
                values.merge(source);
                var nested = values["nested"] as dict<int>;
                nested["first"] = 8;
                var original = source["nested"] as dict<int>;
                dynamic var result = nested["first"] + nested["second"] + original["first"];
                dynamic var preserved = values["untouched"] == true;
            }
        """)
        val machine = execute(main)
        assertEquals(24, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("preserved") as ScoreBool))
    }

    @Test fun staticRemovalMapsItsFieldEffectAndPreservesSiblingTypes() = modes {
        val main = compile("""
            func erase(static values as dict<any>) { values.remove("first"); }
            func main(){
                var values as dict<any> = {first:2, second:9};
                erase(values);
                dynamic var result = values["second"] + 1;
                dynamic var removed = values.containsKey("first");
            }
        """)
        val machine = execute(main)
        assertEquals(10, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("removed") as ScoreBool))
        val erase = function("erase")
        assertNotNull(erase.typedIR)
        assertEquals(setOf(Place(erase.typedIR!!.parameters.single()).field("first")), assertIs<Effect.Writes>(erase.runtimeEffect).places)
    }

    @Test fun memberReceiverRetainsItsIndexAcrossArgumentEffects() = modes {
        val main = compile("""
            func identity(value as int) -> int { return value; }
            func change(static index as int) -> dict<int> { index = 1; return {first:7}; }
            func main(){
                var values = [{first:2},{first:9}];
                var index = identity(0);
                values[index].merge(change(index));
                dynamic var result = values[0]["first"] + values[1]["first"];
            }
        """)
        assertEquals(16, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun signaturesAndKeyCapabilitiesAreCheckedBeforeCommands() {
        for (source in listOf(
            "func main(){ var values as dict<int> = {first:2}; values.remove(1); }",
            "func main(){ var values as dict<int> = {first:2}; values.merge({first:true}); }",
            "func inspect(values as dict<int>, key as string) -> bool { return values.containsKey(key); }\nfunc main(){ var values as dict<int> = {first:2}; var found = inspect(values,\"first\"); }"
        )) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertNotNull(function("main").typedIR)
            assertTrue(Project.errorCount > 0)
            assertTrue(Project.macroFunction.isEmpty())
        }
        val main = compile("""
            func main(){
                var values as dict<int> = {first:2};
                values["a.b"] = 7;
                dynamic var before = values.containsKey("a.b");
                values.remove("a.b");
                dynamic var after = values.containsKey("a.b");
            }
        """)
        val machine = execute(main)
        assertEquals(1, machine.read(main.scope.getVar("before") as ScoreBool))
        assertEquals(0, machine.read(main.scope.getVar("after") as ScoreBool))
    }
}
