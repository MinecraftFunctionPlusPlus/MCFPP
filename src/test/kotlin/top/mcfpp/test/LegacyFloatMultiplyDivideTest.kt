package top.mcfpp.test

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCFloatConcrete
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor

class LegacyFloatMultiplyDivideTest {
    private data class Decimal(val sign: Int, val mantissa: Int, val exponent: Int) {
        fun value() = if (sign == 0) BigDecimal.ZERO else
            BigDecimal(mantissa).scaleByPowerOfTen(exponent - 8).multiply(BigDecimal(sign))
        fun components() = listOf(sign, mantissa / 10000, mantissa % 10000, exponent)
    }

    private val datapack = Path.of("src/main/resources/datapack/stdlib/data")
    private val functions = Files.walk(datapack.resolve("math.float/function")).use { paths ->
        paths.filter { it.toString().endsWith(".mcfunction") }.iterator().asSequence().associate { path ->
            val relative = datapack.resolve("math.float/function").relativize(path).toString().replace('\\', '/')
            "math.float:" + relative.removeSuffix(".mcfunction") to Files.readAllLines(path)
        }
    }
    private val constants = Files.readAllLines(datapack.resolve("math/function/_init.mcfunction"))
        .filter { it.startsWith("scoreboard players set ") }
    private val precision = MathContext(8, RoundingMode.DOWN)

    private fun expected(value: BigDecimal): Decimal {
        val rounded = value.round(precision)
        if (rounded.signum() == 0) return Decimal(0, 0, 0)
        val exponent = rounded.precision() - rounded.scale()
        return Decimal(rounded.signum(), rounded.abs().scaleByPowerOfTen(8 - exponent).intValueExact(), exponent)
    }
    private fun oracle(left: Decimal, right: Decimal, operation: String): Decimal {
        // The finite legacy backend defines either zero operand, including a zero divisor, as zero.
        if (left.sign == 0 || right.sign == 0) return Decimal(0, 0, 0)
        return expected(if (operation == "*") left.value() * right.value()
            else left.value().divide(right.value(), precision))
    }
    private fun seed(value: Decimal, right: Boolean) =
        listOf("sign", "int0", "int1", "exp").zip(value.components()).map { (key, part) ->
            if (right) "scoreboard players set operand float_$key $part"
            else "scoreboard players set float_$key int $part"
        }
    private fun work(machine: ScoreCommandExecutor) =
        listOf("sign", "int0", "int1", "exp").map { machine.values.getValue("float_$it int") }
    private fun check(left: Decimal, right: Decimal, operation: String): ScoreCommandExecutor {
        val name = if (operation == "*") "_mult" else "_div"
        val machine = ScoreCommandExecutor(constants + seed(left, false) + seed(right, true) +
            "execute as operand run function math.float:hpo/float/$name", functions)
        assertEquals(oracle(left, right, operation).components(), work(machine), "$left $operation $right")
        assertEquals(right.components(), listOf("sign", "int0", "int1", "exp").map {
            machine.values.getValue("operand float_$it")
        }, "right operand must remain unchanged")
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        return machine
    }

    @Test fun zeroOperandsAndResidualFieldsAreClearedBeforeAnyDivision() {
        val zeros = listOf(Decimal(0, 0, 0), Decimal(0, 99999999, 39), Decimal(0, 12345678, -44))
        val nonzero = listOf(Decimal(1, 10000000, -44), Decimal(-1, 99999999, 39))
        for (zero in zeros) for (value in nonzero + zeros) for (operation in listOf("*", "/")) {
            assertEquals(listOf(0, 0, 0, 0), work(check(zero, value, operation)))
            assertEquals(listOf(0, 0, 0, 0), work(check(value, zero, operation)))
        }
    }

    @Test fun multiplicationPreservesExactTruncationAcrossSignsCarryAndExponentExtremes() {
        val values = listOf(Decimal(1, 10000000, 1), Decimal(1, 12345678, 1),
            Decimal(1, 31622776, 1), Decimal(1, 99999999, 1),
            Decimal(1, 10000000, -44), Decimal(1, 99999999, 39))
        for (left in values) for (right in values)
            for (leftSign in listOf(-1, 1)) for (rightSign in listOf(-1, 1))
                check(left.copy(sign = leftSign), right.copy(sign = rightSign), "*")
    }

    @Test fun divisionGeneratesEightDigitsForExactAndRepeatingQuotients() {
        val values = listOf(Decimal(1, 10000000, 1), Decimal(1, 10000024, 1),
            Decimal(1, 10000025, 1), Decimal(1, 30000000, 1),
            Decimal(1, 30000001, 1), Decimal(1, 99999999, 1))
        for (left in values) for (right in values)
            for (leftSign in listOf(-1, 1)) for (rightSign in listOf(-1, 1))
                check(left.copy(sign = leftSign), right.copy(sign = rightSign), "/")
        check(Decimal(1, 10000000, -44), Decimal(-1, 99999999, 39), "/")
        check(Decimal(-1, 99999999, 39), Decimal(1, 10000000, -44), "/")
    }

    @Test fun approximateDivisorAndPrematureNormalizationRegressionsAreExact() {
        val first = check(Decimal(1, 10000025, 1), Decimal(1, 10000024, 1), "/")
        assertEquals(listOf(1, 1000, 0, 1), work(first))
        val second = check(Decimal(1, 90000000, 1), Decimal(1, 90000024, 1), "/")
        assertEquals(listOf(1, 9999, 9973, 0), work(second))
    }

    @Test fun executorObservesFailedDivisionAndRemainderWithoutChangingDestination() {
        val commands = listOf("scoreboard players set dividend int 7", "scoreboard players set divisor int 0",
            "scoreboard players operation dividend int /= divisor int",
            "scoreboard players operation dividend int %= divisor int")
        val machine = ScoreCommandExecutor(commands)
        assertEquals(7, machine.values.getValue("dividend int"))
        assertEquals(commands.takeLast(2), machine.failedScoreOperations)
    }

    private fun reset() {
        MCFPPStringTest.readFromString("func main(){}", version = "1.20.1")
        Function.currFunction = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        Function.currFunction.commands.clear()
    }
    private fun encoded(value: Float): Decimal {
        val parts = MCFloat.floatToMCFloat(value)
        return Decimal(parts[0], parts[1] * 10000 + parts[2], parts[3])
    }
    private fun captured(value: MCFloat) = StorageAccess.capture(value) as MCFloat
    private fun calculate(left: MCFloat, right: MCFloat, operation: String): MCFloat {
        // Match the visitor's capture, right-entity preparation, and work-register reload order.
        val frozenLeft = captured(left)
        right.toTempEntity()
        return frozenLeft.getTempVar().binaryComputation(MCFloat.tempFloat, operation) as MCFloat
    }
    private fun components(machine: ScoreCommandExecutor, value: MCFloat) =
        listOf(value.sign, value.int0, value.int1, value.exp).map(machine::read)

    @Test fun consecutiveMultiplyAndDivideUseTheActualVisitorCaptureProtocol() {
        reset()
        val inputs = listOf(1.25f, 0.125f, 9f, 9.0000024f)
        val values = inputs.map { MCFloatConcrete(it).toDynamic(false) as MCFloat }
        val left = captured(calculate(values[0], values[1], "*"))
        val right = captured(calculate(values[2], values[3], "/"))
        val result = captured(calculate(left, right, "/"))
        val commands = Function.currFunction.commands.analyzeAll()
        assertEquals(0, Project.errorCount)
        assertEquals(3, commands.count { "run function math.float:hpo/float/_" in it })
        val machine = ScoreCommandExecutor(constants + commands, functions)
        val leftExpected = oracle(encoded(inputs[0]), encoded(inputs[1]), "*")
        val rightExpected = oracle(encoded(inputs[2]), encoded(inputs[3]), "/")
        assertEquals(leftExpected.components(), components(machine, left))
        assertEquals(rightExpected.components(), components(machine, right))
        assertEquals(oracle(leftExpected, rightExpected, "/").components(), components(machine, result))
        assertTrue(machine.failedScoreOperations.isEmpty())
        values.forEachIndexed { index, value ->
            assertEquals(encoded(inputs[index]).components(), components(machine, value))
        }
    }

    @Test fun knownZeroDivisorUsesLegacyZeroWithoutCompilerDiagnosticOrFailedCommand() {
        reset()
        val result = captured(calculate(MCFloatConcrete(1f), MCFloatConcrete(0f), "/"))
        assertEquals(0, Project.errorCount)
        val machine = ScoreCommandExecutor(constants + Function.currFunction.commands.analyzeAll(), functions)
        assertEquals(listOf(0, 0, 0, 0), components(machine, result))
        assertTrue(machine.failedScoreOperations.isEmpty())
    }
}
