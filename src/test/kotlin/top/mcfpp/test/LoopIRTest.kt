package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class LoopIRTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        functions.values.flatten().filter { it.ast != null }.forEach { assertNotNull(it.typedIR, "IR missing for ${it.identifier}") }
        return functions.getValue("main").single()
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

    @Test fun doWhileTestsAfterTheBodyAndContinueRunsTheCondition() = modes {
        for (version in listOf("26.3", "1.20.2", "1.20.1")) {
            val main = compile("""
                func main(){
                    var count = 0
                    var sum = 0
                    do { count += 1; sum += 10; if(count == 2){ continue; }; sum += 1; } while(count < 3)
                    var value: any = true
                    do { value = 2; } while(false)
                    external result = sum*100 + count*10 + value
                }
            """, version)
            assertEquals(3232, execute(main).read(main.scope.getVar("result") as MCInt))
        }
    }

    @Test fun nestedRangeLoopsHaveIndependentScopesBreakAndContinueTargets() = modes {
        val main = compile("""
            func main(){
                var sum = 0
                for(index : 1 .. 3){
                    if(index == 2){ continue; }
                    for(index : 1 .. 4){
                        if(index == 3){ break; }
                        sum += index
                    }
                    sum += index*10
                }
                external result = sum
            }
        """, "1.20.1")
        assertEquals(46, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun integerBoundariesDoNotRoundOrOverflowAndUnknownEmptyRangesSkip() = modes {
        val main = compile("""
            func count(first: int, last: int): int {
                var result = 0
                for(value : first .. last){ result += 1; }
                return result
            }
            func main(){
                var sum = 0
                for(value : 2147483646 .. 2147483647){ sum += 1; }
                for(value : -2147483648 .. -2147483647){ sum += 1; }
                external result = sum*100 + count(5,4)*10 + count(2147483647,2147483647)
            }
        """, "1.20.2")
        assertEquals(401, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun returnAndRecursiveCallsPreserveLoopFrames() = modes {
        val main = compile("""
            func recurse(value: int): int {
                if(value == 0){ return 0; }
                for(index : value .. value){ return index + recurse(value-1); }
                return 99
            }
            func first(): int { do { return 7; } while(true); }
            func main(){ external result = recurse(4)*10 + first(); }
        """)
        assertEquals(107, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun loopBackedgesAndInvalidRangeBoundsAreChecked() {
        for (source in listOf(
            "func main(){ var value: any = 1; var count = 0; do { var bad = value + 1; value = true; count += 1; } while(count < 2); }",
            "func main(){ for(value : 3 .. 1){ }; }",
            "func main(){ for(value : 1 ..){ }; }",
            "func main(){ for(value : true .. 3){ }; }",
            "func bad(first: any){ for(value : first .. 3){ }; }\nfunc main(){ bad(1); }",
            "func main(){ for(value : 1 .. 2){ }; external outside = value; }")) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0, source)
        }
    }
}
