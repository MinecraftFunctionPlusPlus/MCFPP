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

class ListQueryIRTest {
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

    @Test fun runtimeQueriesArePureIndependentAndKeepTheirSource() = modes {
        for (version in listOf("26.3", "1.20.2", "1.20")) {
            val main = compile("""
                func find(values: list<int>, needle: int): int {
                    var first = values.indexOf(needle)
                    var last = values.lastIndexOf(needle)
                    var missing = values.indexOf(9)
                    return first*1000 + last*100 + missing*10 + values[0]
                }
                func main(){
                    var values = [2,4,2]
                    external result = find(values,2)
                    external found = values.contains(4)
                    external unchanged = values[0] + values[1] + values[2]
                    var empty: list<int> = []
                    external missing = empty.indexOf(2)
                }
            """, version)
            val machine = execute(main)
            assertEquals(192, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(1, machine.read(main.scope.getVar("found") as ScoreBool))
            assertEquals(8, machine.read(main.scope.getVar("unchanged") as MCInt))
            assertEquals(-1, machine.read(main.scope.getVar("missing") as MCInt))
            assertNotNull(function("find").typedIR)
            assertEquals(Effect.Pure, function("find").runtimeEffect)
        }
    }

    @Test fun erasedSearchesUseProvenElementIdentitiesAndKnownRemovalKeepsShape() = modes {
        val main = compile("""
            func identity(value: int): int { return value; }
            func main(){
                var values = [true,1,true] as list<any>
                external first = values.indexOf(true)
                external last = values.lastIndexOf(true)
                external integer = values.indexOf(identity(1))
                values.remove(1)
                external result = values[0] == values[1]
            }
        """)
        val machine = execute(main)
        assertEquals(0, machine.read(main.scope.getVar("first") as MCInt))
        assertEquals(2, machine.read(main.scope.getVar("last") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("integer") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("result") as ScoreBool))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        assertEquals(2, binding.data.facts.length(binding.place))
    }

    @Test fun unknownErasedElementsRequireAViewAndUniformLoopGrowthRemainsSearchable() = modes {
        MCFPPStringTest.readFromString("""
            func find(values: list<any>, needle: int): int { return values.indexOf(needle); }
            func main(){ var values = [2,4] as list<any>; var result = find(values,2); }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("find").typedIR)
        assertTrue(Project.errorCount > 0)
        val main = compile("""
            func find(values: list<any>, needle: int): int { return (values as list<int>).indexOf(needle); }
            func main(){
                var values = [2] as list<any>
                var i = 0
                while(i < 2){ values.add(i); i += 1; }
                external direct = values.indexOf(1)
                external result = find(values,0)
            }
        """)
        val machine = execute(main)
        assertEquals(2, machine.read(main.scope.getVar("direct") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("result") as MCInt))
    }
}
