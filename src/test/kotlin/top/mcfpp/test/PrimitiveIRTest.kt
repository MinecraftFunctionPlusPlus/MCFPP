package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.Instruction
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrimitiveIRTest {
    @Test fun aRawNestedFunctionCallPreservesTheLegacyCallersBranchGuard() {
        MCFPPStringTest.readFromString("""
            func helper(){
                dynamic var flag = true;
                if(flag){
                    /say nested
                } else {
                    /say wrong helper
                }
            }
            func main(){
                dynamic var flag = true;
                if(flag){
                    /function default.test:helper
                } else {
                    /say wrong caller
                }
                /say after
            }
        """.trimIndent(), version = "1.20")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces["default.test"]!!.scope.functions.getValue("main").single()
        assertNotNull(main.typedIR)
        val machine = execute(main)
        assertEquals(listOf("nested", "after"), machine.messages)
    }
    @Test fun runtimeParametersLoopsAndScalarReturnsUseTheSameFlowPathAcrossBackends() {
        val previous = top.mcfpp.CompileSettings.foldIRConstants
        try {
            for (version in listOf("26.3", "1.20.2", "1.20")) for (enabled in listOf(true, false)) {
                top.mcfpp.CompileSettings.foldIRConstants = enabled
                MCFPPStringTest.readFromString("""
                    func accumulate(value as int) -> int {
                        var result = 0;
                        while(value > 0){
                            result += value;
                            value -= 1;
                        }
                        return result;
                    }
                    func decision(flag as bool) -> bool {
                        if(flag){ return true; } else { return false; }
                    }
                    func main(){
                        var sum = accumulate(4);
                        var chosen = decision(true) != decision(false);
                    }
                """.trimIndent(), version = version)
                assertEquals(0, Project.errorCount)
                val functions = GlobalScope.localNamespaces["default.test"]!!.scope.functions
                for (name in listOf("accumulate", "decision")) {
                    val function = functions.getValue(name).single()
                    assertNotNull(function.typedIR)
                    assertTrue(function.compiledFunctions.isEmpty())
                    assertNotNull(function.scope.getVar(function.normalParams.single().identifier)!!.symbol)
                }
                val main = functions.getValue("main").single()
                val machine = execute(main)
                assertEquals(10, machine.read(main.scope.getVar("sum") as MCInt))
                assertEquals(1, machine.read(main.scope.getVar("chosen") as ScoreBool))
            }
        } finally { top.mcfpp.CompileSettings.foldIRConstants = previous }
    }
    @Test fun enablingAndDisablingFoldingPreservesResultsAndEffectOrder() {
        val previous = top.mcfpp.CompileSettings.foldIRConstants
        try {
            for (enabled in listOf(true, false)) {
                top.mcfpp.CompileSettings.foldIRConstants = enabled
                val function = compile("""
                    var value = 2147483647 + 1;
                    dynamic var result = value + 2;
                    /say first
                    result += 1;
                    /say second
                """.trimIndent())
                val machine = execute(function)
                assertEquals(Int.MIN_VALUE + 3, machine.read(function.scope.getVar("result") as MCInt))
                assertEquals(listOf("first", "second"), machine.messages)
            }
        } finally { top.mcfpp.CompileSettings.foldIRConstants = previous }
    }
    private fun compile(body: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString("func arithmetic(){\n$body\n}", version = version)
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces["default.test"]!!.scope.functions["arithmetic"]!!.first().also {
            assertNotNull(it.typedIR)
        }
    }

    @Test fun constantsStayUnmaterializedUntilAnExplicitRuntimeDeclarationNeedsThem() {
        val function = compile("""
            var value = 40 + 2;
            var bigger = value > 10;
            var inverted = !bigger;
            var combined = bigger && !inverted;
            dynamic var result = combined;
        """.trimIndent())
        assertEquals(42, (function.scope.getVar("value") as MCIntConcrete).value)
        assertEquals(true, (function.scope.getVar("combined") as ScoreBoolConcrete).value)
        val commands = function.commands.analyzeAll().filter { it.startsWith("scoreboard ") || it.startsWith("execute ") }
        val result = function.scope.getVar("result") as ScoreBool
        assertEquals(listOf("scoreboard players set ${result.name} ${result.boolObject} 1"), commands)
    }


    private fun execute(function: Function): ScoreCommandExecutor = ScoreCommandExecutor(function.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() }).also {
        assertEquals(0, it.stackDepth, "Every reachable return must release the entrance stack frame")
        assertTrue(it.branchGuards.isEmpty(), "Every recursive legacy branch must release its guard")
    }

    @Test fun equalBranchConstantsFoldAndUnrelatedConstantsStayUnmaterialized() {
        val function = compile("""
            var unrelated = 99;
            var value = 0;
            dynamic var condition = true;
            if (condition) { value = 5; } else { value = 5; }
            dynamic var result = value + 2;
        """.trimIndent())
        val machine = execute(function)
        assertEquals(7, machine.read(function.scope.getVar("result") as MCInt))
        assertEquals(99, (function.scope.getVar("unrelated") as MCIntConcrete).value)
        val commands = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.flatMap { it.commands.analyzeAll() }
        assertTrue(commands.none { it.contains("${function.prefix}unrelated") })
        assertTrue(commands.none { it.contains(" += ") }, "The value after the join should fold")
    }

    @Test fun differingBranchesAndNestedElseIfExecuteTheSelectedPath() {
        for (condition in listOf(true, false)) {
            val function = compile("""
                var value = 0;
                dynamic var condition = $condition;
                if (condition) { value = 5; } else if (!condition) { value = 8; } else { value = 9; }
                dynamic var result = value + 2;
            """.trimIndent())
            assertEquals(if (condition) 7 else 10, execute(function).read(function.scope.getVar("result") as MCInt))
        }
    }

    @Test fun loopsUseFixedPointFactsAndBreakContinueFollowTheirEdges() {
        val function = compile("""
            var i = 0;
            var total = 0;
            while (i < 6) {
                i += 1;
                if (i == 2) { continue; }
                if (i == 5) { break; }
                total += i;
            }
            dynamic var result = total;
        """.trimIndent())
        val machine = execute(function)
        assertEquals(8, machine.read(function.scope.getVar("result") as MCInt))
        assertEquals(5, machine.read(function.scope.getVar("i") as MCInt))
    }

    @Test fun earlyReturnExcludesUnreachableWritesAndBalancesTheStack() {
        val function = compile("""
            dynamic var result = 3;
            dynamic var condition = true;
            if (condition) { result = 7; return; }
            result = 9;
        """.trimIndent())
        assertEquals(7, execute(function).read(function.scope.getVar("result") as MCInt))
    }

    @Test fun rawCommandsObservePendingWritesAndInvalidateKnownValues() {
        val function = compile("""
            var value = 3;
            /scoreboard players set default.test_func_arithmetic_value mcfpp_default 9
            dynamic var result = value + 1;
        """.trimIndent())
        assertEquals(10, execute(function).read(function.scope.getVar("result") as MCInt))
    }

    @Test fun legacyTargetsExecuteLoopsAndEarlyReturnsWithoutUnsupportedReturnCommands() {
        val function = compile("""
            var i = 0;
            while (i < 4) { i += 1; }
            dynamic var result = i;
            if (i == 4) { result = 7; return; }
            result = 9;
        """.trimIndent(), version = "1.20")
        assertEquals(7, execute(function).read(function.scope.getVar("result") as MCInt))
        val commands = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.flatMap { it.commands.analyzeAll() }
        assertTrue(commands.none { it.startsWith("return ") || it.contains("run return ") })
    }

    @Test fun writesAndBooleanResultsPreserveOperandsAndUseOneDeclarationIdentity() {
        val function = compile("""
            dynamic var a = 7;
            dynamic var b = -3;
            var sum = a + b;
            a *= 2;
            var equal = sum == 4;
            var conjunction = equal && false;
            var disjunction = conjunction || equal;
        """.trimIndent())
        val machine = execute(function)
        assertEquals(14, machine.read(function.scope.getVar("a") as MCInt))
        assertEquals(4, machine.read(function.scope.getVar("sum") as MCInt))
        assertEquals(1, machine.read(function.scope.getVar("equal") as ScoreBool))
        assertEquals(0, machine.read(function.scope.getVar("conjunction") as ScoreBool))
        assertEquals(1, machine.read(function.scope.getVar("disjunction") as ScoreBool))
        val symbol = function.scope.getVar("a")!!.symbol!!
        val reads = function.typedIR!!.blocks.single().instructions.filterIsInstance<Instruction.Read>()
        assertTrue(reads.count { it.place.root == symbol.id } >= 2)
    }

    @Test fun integerOverflowFoldsWithTheSameSigned32BitResult() {
        val function = compile("var value = 2147483647 + 1; dynamic var result = value;")
        assertEquals(Int.MIN_VALUE, (function.scope.getVar("value") as MCIntConcrete).value)
        assertEquals(Int.MIN_VALUE, ScoreCommandExecutor(function.commands.analyzeAll()).read(function.scope.getVar("result") as MCInt))
    }
}
