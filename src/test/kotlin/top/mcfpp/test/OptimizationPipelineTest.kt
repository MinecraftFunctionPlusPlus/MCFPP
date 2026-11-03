package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.CompileSettings
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.FunctionInventory
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.optimization.*
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class OptimizationPipelineTest {
    @Test fun invalidForwardTargetsDoNotGenerateMacroBodiesInTheirCallers() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                Project.config = ProjectConfig(optimization = OptimizationConfig(level))
                MCFPPStringTest.readFromString("""
                    func main(){ edit(0); }
                    func edit(index: int){ var values = [2]; values.insert(index,3); }
                """.trimIndent(), version = "1.20.1")
                assertTrue(Project.errorCount > 0)
                assertTrue(Project.macroFunction.isEmpty())
            }
        } finally { Project.config = previous }
    }

    @Test fun dumpNamesPassesFunctionsAndTheSelectedCallingConvention() {
        val previous = Project.config
        val output = java.nio.file.Files.createTempDirectory("mcfpp-optimization-dump-")
        try {
            Project.config = ProjectConfig(irOutputPath = output)
            MCFPPStringTest.readFromString("func increment(value:int):int{return value+1;}\nfunc main(){external result=increment(4);}", version = "26.3")
            assertEquals(0, Project.errorCount)
            val dump = java.nio.file.Files.readString(output.resolve("02-optimized.ir"))
            assertTrue(dump.contains("pass automatic-inlining"))
            assertTrue(dump.contains("default.test:increment_int"))
            assertTrue(dump.contains("calling-convention score-registers"))
            assertTrue(dump.contains("estimated="))
        } finally {
            Project.config = previous
            java.nio.file.Files.deleteIfExists(output.resolve("02-optimized.ir"))
            java.nio.file.Files.deleteIfExists(output)
        }
    }

    private fun compile(source: String, level: Int, disabled: Set<String> = emptySet()): Pair<Function, ScoreCommandExecutor> {
        Project.config = ProjectConfig(optimization = OptimizationConfig(level, disabled))
        CompileSettings.foldIRConstants = true
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val machine = ScoreCommandExecutor(main.commands.analyzeAll(), FunctionInventory.all().associate {
            it.namespaceID.toString() to it.commands.analyzeAll()
        })
        assertEquals(0, machine.stackDepth)
        return main to machine
    }

    @Test fun scalarCallsBecomeStacklessAndInlineAcrossLevels() {
        val previous = Project.config
        try {
            val counts = mutableListOf<Int>()
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func increment(value: int): int { return value + 1; }
                    func wrapper(value: int): int { return increment(value); }
                    func main(){ external result = wrapper(4); }
                """, level)
                assertEquals(5, machine.read(main.scope.getVar("result") as MCInt))
                counts.add(machine.executedCommands)
                println("optimization scalar O$level: executed=${machine.executedCommands}, stack=${machine.stackOperations}, generated=${main.commands.size}")
                if (level > 0) assertEquals(0, machine.stackOperations)
                if (level == 2) {
                    assertFalse(main.commands.any { it.toString().contains("function default.test:wrapper") })
                    assertEquals(1, machine.executedCommands, "An inlined constant call chain should only publish its result")
                }
            }
            assertTrue(counts[1] < counts[0], "$counts")
            assertTrue(counts[2] < counts[1], "$counts")
        } finally { Project.config = previous }
    }

    @Test fun recursiveCallsAndEarlierResultsRemainIndependent() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func sum(value: int): int {
                        if(value <= 0){ return 0; }
                        return value + sum(value - 1)
                    }
                    func add(a: int, b: int): int { return a + b; }
                    func main(){ external result = add(sum(3), sum(4)); }
                """, level)
                assertEquals(16, machine.read(main.scope.getVar("result") as MCInt))
            }
        } finally { Project.config = previous }
    }

    @Test fun disablingInliningKeepsTheCallAndOtherOptimizations() {
        val previous = Project.config
        try {
            val (main, machine) = compile("""
                func add(a: int, b: int): int { return a + b; }
                func main(){ external result = add(3, 4); }
            """, 2, setOf(PassIds.INLINE))
            assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(0, machine.stackOperations)
            assertTrue(main.commands.any { it.toString().contains("function default.test:add") })
        } finally { Project.config = previous }
    }

    @Test fun commandCostsIncludeNbtKindsAndMacroFunctionSurcharge() {
        val estimator = MinecraftCostEstimator(CommandCostModel())
        assertEquals(1.0, estimator.cost(MinecraftLowering.lower("scoreboard players set x values 2")))
        assertEquals(10.0, estimator.cost(MinecraftLowering.lower("data get storage test:values x")))
        assertEquals(60.0, estimator.cost(MinecraftLowering.lower("data get entity @e[limit=1] Health")))
        assertEquals(150.0, estimator.cost(MinecraftLowering.lower("data get entity @p Health")))
        assertEquals(1.0, estimator.cost(MinecraftLowering.lower("execute if entity @p run say hello")))
        assertEquals(150.0, estimator.cost(MinecraftLowering.lower("execute store result entity @p Health float 1 run scoreboard players get health values")))
        val ordinary = MinecraftLowering.lower("say hello")
        val macro = MinecraftLowering.lower("\$say \$(message)")
        assertEquals(estimator.cost(macro) + 2.0, estimator.cost(listOf(ordinary, macro)))
        val typed = top.mcfpp.analysis.TypedIR(0, listOf(top.mcfpp.analysis.BasicBlock(0,
            listOf(top.mcfpp.analysis.Instruction.RawCommand("say hello"),
                top.mcfpp.analysis.Instruction.RawCommand("\$say \$(message)")),
            top.mcfpp.analysis.Terminator.Return(null))))
        val inlineEstimator = TypedMinecraftCost(emptyMap())
        assertEquals(estimator.cost(listOf(ordinary, macro)), inlineEstimator.cost(typed, estimator.model))
        assertEquals(7.0, inlineEstimator.cost(typed, CommandCostModel(macroFunctionCommand = 7.0)) -
            inlineEstimator.cost(typed, CommandCostModel(macroFunctionCommand = 0.0)), 0.000001)
        assertFailsWith<IllegalArgumentException> { OptimizationConfig(level = 3) }
        assertFailsWith<IllegalArgumentException> { OptimizationConfig(disabledPasses = setOf("typo")) }
        assertEquals(emptyList(), MinecraftFramePlanning.run(listOf(MinecraftInstruction.Frame(true), MinecraftInstruction.Frame(false))))
        val observesFrame = listOf(MinecraftInstruction.Frame(true), MinecraftLowering.lower("data get storage mcfpp:system stack_frame[0].x"), MinecraftInstruction.Frame(false))
        assertEquals(observesFrame, MinecraftFramePlanning.run(observesFrame))
    }

    @Test fun disablingConstantPropagationKeepsInliningAndRuntimeArithmetic() {
        val previous = Project.config
        try {
            val (main, machine) = compile("""
                func add(a: int, b: int): int { return a + b; }
                func main(){ external result = add(3, 4); }
            """, 2, setOf(PassIds.CONSTANTS))
            assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
            assertFalse(main.commands.any { it.toString().contains("function default.test:add") })
            assertTrue(main.optimizedIR!!.blocks.any { block ->
                block.instructions.any { it is top.mcfpp.analysis.Instruction.Binary }
            })
            assertTrue(machine.executedCommands > 1)
        } finally { Project.config = previous }
    }

    @Test fun repeatedExpressionsAndWritesPreserveSnapshotsAndEffectOrder() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func main(){
                    external input = 3
                    var first = (input + 2) * (input + 2)
                    input = 7
                    var second = input + 0
                    /say first
                    external result = first + second
                    /say second
                    }
                """, level)
                assertEquals(32, machine.read(main.scope.getVar("result") as MCInt))
                assertEquals(listOf("first", "second"), machine.messages)
                if (level > 0) assertTrue(main.optimizedIR!!.blocks.sumOf { block ->
                    block.instructions.count { it is top.mcfpp.analysis.Instruction.Binary && it.operation == "+" }
                } < main.typedIR!!.blocks.sumOf { block ->
                    block.instructions.count { it is top.mcfpp.analysis.Instruction.Binary && it.operation == "+" }
                })
            }
        } finally { Project.config = previous }
    }

    @Test fun multipleReturnsAndLoopCallsPreserveEarlierExpressionValues() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func choose(value: int): int {
                        if(value > 2){ return value + 2; }
                        return value + 1
                    }
                    func main(){
                        external input = 2
                        var total = 0
                        var i = 0
                        while(i < 4){ total += choose(i); i += 1; }
                        external result = input + choose(input) + total
                    }
                """, level)
                assertEquals(16, machine.read(main.scope.getVar("result") as MCInt))
            }
        } finally { Project.config = previous }
    }

    @Test fun nbtParametersAndReturnsUseIndependentValuesAcrossCalls() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func identity(value: any): any { return value; }
                    func main(){
                        var original: any = 3
                        var first = identity(original)
                        original = 8
                        var second = identity(original)
                        external result = (first as int) + (second as int)
                    }
                """, level)
                assertEquals(11, machine.read(main.scope.getVar("result") as MCInt))
                if (level > 0) assertEquals(0, machine.stackOperations)
                if (level == 2) assertFalse(main.commands.any { it.toString().contains("function default.test:identity") })
            }
        } finally { Project.config = previous }
    }

    @Test fun inlineFunctionsWithKnownEffectsRunTheirBodiesAndArgumentsOnceInOrder() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func first(value: int): int {
                        /say first
                        return value + 1;
                    }
                    func second(value: int): int {
                        /say second
                        return value + 2;
                    }
                    func main(){ external result = first(3) + second(4); }
                """, level)
                assertEquals(10, machine.read(main.scope.getVar("result") as MCInt))
                assertEquals(listOf("first", "second"), machine.messages)
                if (level == 2) assertFalse(main.commands.any { it.toString().contains("function default.test:first") })
            }
        } finally { Project.config = previous }
    }

    @Test fun overloadedFunctionsHaveIndependentRegisters() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func calc(flag: bool): int { external result = 8; return result; }
                    func calc(value: int): int {
                        external result = value + 1
                        var more = calc(true)
                        return result + more
                    }
                    func main(){ external result = calc(2); }
                """, level)
                assertEquals(11, machine.read(main.scope.getVar("result") as MCInt))
                if (level > 0) assertEquals(0, machine.stackOperations)
            }
        } finally { Project.config = previous }
    }

    @Test fun constantInlineBranchesKeepOnlyTheSelectedEffectsAndFinalValue() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func choose(value: int): int {
                        if(value > 3){
                            /say high
                            return (value + 2) * 2
                        }
                        /say low
                        return value + 1
                    }
                    func main(){ external result = choose(4) + choose(1); }
                """, level)
                assertEquals(14, machine.read(main.scope.getVar("result") as MCInt))
                assertEquals(listOf("high", "low"), machine.messages)
                if (level == 2) assertEquals(3, machine.executedCommands)
            }
        } finally { Project.config = previous }
    }

    @Test fun runtimeInlineArgumentsKeepRuntimeReadsAndCombineIntegerOffsets() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func adjust(value: int): int { var copy = value; return ((copy + 2) + 3) * 1 + 0; }
                    func main(){ external input = 4; external result = adjust(input); }
                """, level)
                assertEquals(9, machine.read(main.scope.getVar("result") as MCInt))
                if (level == 2) {
                    assertEquals(3, machine.executedCommands)
                    assertTrue(main.commands.any { it.toString().startsWith("scoreboard players add ") && it.toString().endsWith(" 5") })
                }
            }
        } finally { Project.config = previous }
    }

    @Test fun coalescedDestinationsDoNotOverwriteEarlierOperandsOrComparisons() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func main(){
                        external x = 3
                        external y = 7
                        y = x + y
                        x = y - x
                        external flag = true
                        flag = flag == false
                        external result = x * 100 + y * 10
                        if(flag){ result += 1; }
                    }
                """, level)
                assertEquals(800, machine.read(main.scope.getVar("result") as MCInt))
                assertEquals(7, machine.read(main.scope.getVar("x") as MCInt))
                assertEquals(10, machine.read(main.scope.getVar("y") as MCInt))
            }
        } finally { Project.config = previous }
    }

    @Test fun inPlaceIntegerOffsetsNeedOnlyOneArithmeticCommand() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func main(){ external result = 4; result = ((result + 2) + 3) * 1 + 0; }
                """, level)
                assertEquals(9, machine.read(main.scope.getVar("result") as MCInt))
                if (level > 0) assertEquals(2, machine.executedCommands)
            }
        } finally { Project.config = previous }
    }

    @Test fun absorbingArithmeticRetainsArgumentEffectsAndEarlierValueSnapshots() {
        val previous = Project.config
        try {
            for (level in 0..2) {
                val (main, machine) = compile("""
                    func effect(value: int): int {
                        /say evaluated
                        return value + 2
                    }
                    func main(){
                        external input = 4
                        var captured = input + 2
                        input = 9
                        external result = effect(3) * 0 + captured * 1 + (input - input)
                    }
                """, level)
                assertEquals(6, machine.read(main.scope.getVar("result") as MCInt))
                assertEquals(listOf("evaluated"), machine.messages)
            }
        } finally { Project.config = previous }
    }
}
