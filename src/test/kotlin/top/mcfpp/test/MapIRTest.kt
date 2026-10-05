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

class MapIRTest {
    private fun function(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue(name).single()
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        GlobalScope.localNamespaces.getValue("default.test").scope.functions.values.flatten()
            .filter { it.ast != null }.forEach { assertNotNull(it.typedIR, "IR missing for ${it.identifier}") }
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

    @Test fun knownKeysKeepActualTypesAndViewsFollowKeyPositionsAfterRemoval() = modes {
        val main = compile("""
            func main(){
                var root = {entries:[{key:"first",value:[2]},{key:"second",value:[3]}]};
                var values = root as map<any>;
                var view = values["second"] as list<int>;
                var copied = values;
                values.remove("first");
                view[0] = 7;
                values["flag"] = true;
                var number = 0;
                if(values["flag"]){ number = values["second"][0]; }
                dynamic var result = values.size()*100 + number*10 + copied["second"][0];
            }
        """)
        assertEquals(273, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun runtimeKeysAndRecursiveStaticWritesNeedNoMacrosForMapMutations() = modes {
        for (version in listOf("26.3", "1.20.1")) {
            val main = compile("""
                func edit(static values as map<int>, key as string, count as int) {
                    if(count == 0){ return; }
                    values[key] = count;
                    edit(values,key,count-1);
                }
                func lookup(values as map<int>, key as string) -> bool { return values.containsKey(key); }
                func main(){
                    var values = {entries:[]} as map<int>;
                    var index = 0;
                    while(index < 3){ values["loop"] = index; index += 1; }
                    edit(values,"recursive",3);
                    dynamic var found = lookup(values,"recursive");
                    dynamic var missing = lookup(values,"absent");
                    dynamic var before = values.size()*100 + values["loop"]*10 + values["recursive"];
                    values.clear();
                    dynamic var empty = values.isEmpty();
                }
            """, version)
            val machine = execute(main)
            assertEquals(221, machine.read(main.scope.getVar("before") as MCInt))
            assertEquals(1, machine.read(main.scope.getVar("found") as ScoreBool))
            assertEquals(0, machine.read(main.scope.getVar("missing") as ScoreBool))
            assertEquals(1, machine.read(main.scope.getVar("empty") as ScoreBool))
            assertIs<Effect.Writes>(function("edit").runtimeEffect)
            assertEquals(Effect.Pure, function("lookup").runtimeEffect)
            if (version == "1.20.1") assertTrue(Project.macroFunction.isEmpty())
        }
    }

    @Test fun mergeCopiesInputsAndReplacesWholeNestedValues() = modes {
        val main = compile("""
            func merge(values as map<dict<int>>, source as map<dict<int>>) -> map<dict<int>> { values.merge(source); return values; }
            func main(){
                var left = {entries:[{key:"same",value:{old:2}},{key:"other",value:{keep:3}}]} as map<dict<int>>;
                var right = {entries:[{key:"same",value:{fresh:7}},{key:"new",value:{fresh:9}}]} as map<dict<int>>;
                var result = merge(left,right);
                result.merge(result);
                dynamic var number = result.size()*100 + result["same"]["fresh"]*10 + left["same"]["old"];
                dynamic var removed = result["same"].containsKey("old");
            }
        """)
        val machine = execute(main)
        assertEquals(372, machine.read(main.scope.getVar("number") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("removed") as ScoreBool))
        assertEquals(Effect.Pure, function("merge").runtimeEffect)
    }

    @Test fun assignmentCapturesKeysAndDynamicReceiversBeforeRightHandSideCalls() = modes {
        val main = compile("""
            func change(static key as string, static index as int) -> int { key = "other"; index = 1; return 7; }
            func identity(value as int) -> int { return value; }
            func read(values as map<int>, key as string) -> int { return values[key]; }
            func main(){
                var first = {entries:[{key:"q\".[]\\end",value:2}]} as map<int>;
                var second = {entries:[{key:"q\".[]\\end",value:3}]} as map<int>;
                var maps = [first,second];
                var key = "q\".[]\\end";
                var index = identity(0);
                maps[index][key] = change(key,index);
                dynamic var number = read(maps[0],"q\".[]\\end")*10 + read(maps[1],"q\".[]\\end");
            }
        """, "1.20.2")
        assertEquals(73, execute(main).read(main.scope.getVar("number") as MCInt))
        assertTrue(Project.macroFunction.isNotEmpty())
    }

    @Test fun unknownKeysKeepOnlyCommonValueEvidence() = modes {
        val main = compile("""
            func identity(value as string) -> string { return value; }
            func main(){
                var values = {entries:[{key:"first",value:2},{key:"second",value:3}]} as map<any>;
                var key = identity("first");
                values[key] = 7;
                dynamic var result = values["first"] + values["second"];
            }
        """)
        assertEquals(10, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun invalidLayoutsUnprovenTypesAndUnsupportedKeyAccessAreDiagnosed() {
        for ((source, version) in listOf(
            "func main(){ var values = {keys:[],values:{}} as map<int>; var result = values.size(); }" to "26.3",
            "func main(){ var values = {entries:[]} as map<int>; values[1] = 2; }" to "26.3",
            "func main(){ var values = {entries:[]} as map<int>; values[\"a\"] = true; }" to "26.3",
            "func read(values as map<any>, key as string) -> int { return values[key] + 1; }\nfunc main(){ var values = {entries:[]} as map<any>; read(values,\"a\"); }" to "26.3",
            "func read(values as map<int>, key as string) -> int { return values[key]; }\nfunc main(){ var values = {entries:[]} as map<int>; read(values,\"a\"); }" to "1.20.1")) {
            MCFPPStringTest.readFromString(source, version = version)
            assertTrue(Project.errorCount > 0, source)
        }
    }
}
