package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.Effect
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class MapProjectionIRTest {
    private fun function(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue(name).single()
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        GlobalScope.localNamespaces.getValue("default.test").scope.functions.values.flatten().filter { it.ast != null }
            .forEach { assertNotNull(it.typedIR, "IR missing for ${it.identifier}") }
        return function("main")
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

    @Test fun projectionsKeepActualTypesNestedShapeAndIndependentStorage() = modes {
        val main = compile("""
            func main(){
                var values = {entries:[{key:"numbers",value:[2,3]},{key:"flag",value:true}]} as map<any>;
                var projected = values.keyValueSet;
                var keys = values.keys;
                projected["numbers"][0] = 7;
                keys[0] = "changed";
                var number = 0;
                if(projected["flag"]){ number = projected["numbers"][-1]; }
                dynamic var result = values["numbers"][0]*100 + projected["numbers"][0]*10 + number;
                dynamic var first = values.keys.indexOf("numbers");
                dynamic var direct = values.keyValueSet["numbers"][1] + 0;
                dynamic var changed = values.keys.contains("changed");
            }
        """)
        val machine = execute(main)
        assertEquals(273, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("first") as MCInt))
        assertEquals(3, machine.read(main.scope.getVar("direct") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("changed") as ScoreBool))
    }

    @Test fun runtimeKeyListsSupportRecursiveReturnsAndTargetsWithoutMacros() = modes {
        val main = compile("""
            func names(values as map<int>, depth as int) -> list<string> {
                if(depth == 0){ return values.keys; }
                return names(values,depth-1);
            }
            func first(values as map<int>) -> string { return values.keys[0]; }
            func main(){
                var values = {entries:[{key:"",value:2},{key:"q\".[]\\end",value:3}]} as map<int>;
                var keys = names(values,2);
                var key = first(values);
                values.clear();
                dynamic var found = keys.contains("q\".[]\\end");
                dynamic var captured = keys.contains(key);
                dynamic var empty = values.keys.contains("q\".[]\\end");
            }
        """, "1.20.1")
        val machine = execute(main)
        assertEquals(1, machine.read(main.scope.getVar("found") as ScoreBool))
        assertEquals(1, machine.read(main.scope.getVar("captured") as ScoreBool))
        assertEquals(0, machine.read(main.scope.getVar("empty") as ScoreBool))
        assertEquals(Effect.Pure, function("names").runtimeEffect)
        assertTrue(Project.macroFunction.isEmpty())
    }

    @Test fun projectionArgumentsRetainTheirValuesBeforeLaterStaticCalls() = modes {
        val main = compile("""
            func clear(static values as map<list<int>>) -> int { values.clear(); return 1; }
            func take(value as dict<list<int>>, ignored as int) -> int { return value["first"][0]; }
            func main(){
                var values = {entries:[{key:"first",value:[2,3]}]} as map<list<int>>;
                dynamic var result = take(values.keyValueSet,clear(values));
                dynamic var empty = values.isEmpty();
            }
        """)
        val machine = execute(main)
        assertEquals(2, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("empty") as ScoreBool))
    }

    @Test fun dictionaryProjectionRebuildsCurrentFieldsOnLoopBackedges() = modes {
        val main = compile("""
            func main(){
                var values = {entries:[{key:"first",value:2}]} as map<int>;
                var count = 0;
                var sum = 0;
                while(count < 3){
                    var copied = values.keyValueSet;
                    sum += copied["first"];
                    values["first"] += 1;
                    count += 1;
                }
                dynamic var result = sum;
            }
        """, "1.20.2")
        assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun dictionaryProjectionRejectsUnprovenKeysEmptyNamesAndOldLayouts() {
        for (source in listOf(
            "func project(values as map<int>) -> dict<int> { return values.keyValueSet; }\nfunc main(){ var values = {entries:[{key:\"a\",value:2}]} as map<int>; var result = project(values); }",
            "func main(){ var values = {entries:[{key:\"\",value:2}]} as map<int>; var result = values.keyValueSet; }",
            "func main(){ var values = {keys:[],values:{}} as map<int>; var result = values.keys; }")) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0, source)
        }
    }
}
