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
                var root = {entries:[{key:"first",value:[2]},{key:"second",value:[3]}]}
                var values = root as map<any>
                var view = values["second"] as list<int>
                var copied = values
                values.remove("first")
                view[0] = 7
                values["flag"] = true
                var number = 0
                if(values["flag"]){ number = values["second"][0]; }
                external result = values.size()*100 + number*10 + copied["second"][0]
            }
        """)
        assertEquals(273, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun mergeCopiesInputsAndReplacesWholeNestedValues() = modes {
        val main = compile("""
            func merge(values: map<dict<int>>, source: map<dict<int>>): map<dict<int>> { values.merge(source); return values; }
            func main(){
                var left = {entries:[{key:"same",value:{old:2}},{key:"other",value:{keep:3}}]} as map<dict<int>>
                var right = {entries:[{key:"same",value:{fresh:7}},{key:"new",value:{fresh:9}}]} as map<dict<int>>
                var result = merge(left,right)
                result.merge(result)
                external number = result.size()*100 + result["same"]["fresh"]*10 + left["same"]["old"]
                external removed = result["same"].containsKey("old")
            }
        """)
        val machine = execute(main)
        assertEquals(372, machine.read(main.scope.getVar("number") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("removed") as ScoreBool))
        assertEquals(Effect.Pure, function("merge").runtimeEffect)
    }

    @Test fun unknownKeysKeepOnlyCommonValueEvidence() = modes {
        val main = compile("""
            func identity(value: string): string { return value; }
            func main(){
                var values = {entries:[{key:"first",value:2},{key:"second",value:3}]} as map<any>
                var key = identity("first")
                values[key] = 7
                external result = values["first"] + values["second"]
            }
        """)
        assertEquals(10, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun invalidLayoutsUnprovenTypesAndUnsupportedKeyAccessAreDiagnosed() {
        for ((source, version) in listOf(
            "func main(){ var values = {keys:[],values:{}} as map<int>; var result = values.size(); }" to "26.3",
            "func main(){ var values = {entries:[]} as map<int>; values[1] = 2; }" to "26.3",
            "func main(){ var values = {entries:[]} as map<int>; values[\"a\"] = true; }" to "26.3",
            "func read(values: map<any>, key: string): int { return values[key] + 1; }\nfunc main(){ var values = {entries:[]} as map<any>; read(values,\"a\"); }" to "26.3",
            "func read(values: map<int>, key: string): int { return values[key]; }\nfunc main(){ var values = {entries:[]} as map<int>; read(values,\"a\"); }" to "1.20.1")) {
            MCFPPStringTest.readFromString(source, version = version)
            assertTrue(Project.errorCount > 0, source)
        }
    }
}
