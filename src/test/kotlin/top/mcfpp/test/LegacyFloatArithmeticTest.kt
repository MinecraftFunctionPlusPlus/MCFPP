package top.mcfpp.test

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.Project
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCFloatConcrete
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor

class LegacyFloatArithmeticTest {
    private data class Decimal(val sign: Int, val mantissa: Int, val exponent: Int) {
        fun value(): BigDecimal = if (sign == 0) BigDecimal.ZERO else
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
    private fun oracle(left: Decimal, right: Decimal, operation: String) =
        expected(if (operation == "+") left.value() + right.value() else left.value() - right.value())

    private fun seed(value: Decimal, right: Boolean): List<String> =
        listOf("sign", "int0", "int1", "exp").zip(value.components()).map { (key, part) ->
            if (right) "scoreboard players set operand float_$key $part"
            else "scoreboard players set float_$key int $part"
        }
    private fun work(machine: ScoreCommandExecutor) =
        listOf("sign", "int0", "int1", "exp").map { machine.values.getValue("float_$it int") }
    private fun check(left: Decimal, right: Decimal, operation: String) {
        val name = if (operation == "+") "_add" else "_rmv"
        val machine = ScoreCommandExecutor(constants + seed(left, false) + seed(right, true) +
            "execute as operand run function math.float:hpo/float/$name", functions)
        assertEquals(oracle(left, right, operation).components(), work(machine), "$left $operation $right")
        assertEquals(right.components(), listOf("sign", "int0", "int1", "exp").map {
            machine.values.getValue("operand float_$it")
        }, "right operand must remain unchanged")
    }

    @Test fun zeroIgnoresResidualComponentsBeforeMagnitudeOrdering() {
        val zeros = listOf(Decimal(0, 0, 0), Decimal(0, 99999999, 39), Decimal(0, 12345678, -44))
        val values = listOf(Decimal(1, 10000000, -3), Decimal(-1, 12345678, -20),
            Decimal(1, 99999999, 39), Decimal(-1, 10000000, 0))
        for (zero in zeros) for (value in values + zeros) for (operation in listOf("+", "-")) {
            check(zero, value, operation)
            check(value, zero, operation)
        }
    }

    @Test fun signsSwapsCancellationAndLowDigitsUseExactDecimalArithmetic() {
        val magnitudes = listOf(Decimal(1, 10000000, 1), Decimal(1, 10000001, 1),
            Decimal(1, 12345678, 1), Decimal(1, 12345679, 1), Decimal(1, 99999999, 1),
            Decimal(1, 10000001, 0))
        for (left in magnitudes) for (right in magnitudes)
            for (leftSign in listOf(-1, 1)) for (rightSign in listOf(-1, 1))
                for (operation in listOf("+", "-"))
                    check(left.copy(sign = leftSign), right.copy(sign = rightSign), operation)
    }

    @Test fun alignmentRetainsGuardAndStickyForEveryDifferenceAndExtremeExponents() {
        for (difference in (0..9).toList() + listOf(20, 83))
            for (large in listOf(10000000, 12345678, 99999999))
                for (small in listOf(10000000, 10000001, 12345679, 99999999))
                    for (leftSign in listOf(-1, 1)) for (rightSign in listOf(-1, 1))
                        for (operation in listOf("+", "-")) {
                            val left = Decimal(leftSign, large, 39)
                            val right = Decimal(rightSign, small, 39 - difference)
                            check(left, right, operation)
                            check(right, left, operation)
                        }
    }

    @Test fun normalizationHandlesCarryAndPrecisionRecoveredByCancellation() {
        val cases = listOf(
            Decimal(1, 10000000, 1) to Decimal(1, 10000001, 0),
            Decimal(1, 10000000, 1) to Decimal(1, 99999999, 0),
            Decimal(1, 99999999, 1) to Decimal(1, 99999999, 1),
            Decimal(1, 10000000, 1) to Decimal(1, 10000000, -44),
            Decimal(1, 10000001, 1) to Decimal(1, 10000000, 1))
        for ((left, right) in cases) for (operation in listOf("+", "-")) {
            check(left, right, operation)
            check(left, right.copy(sign = -1), operation)
        }
        assertEquals(Decimal(1, 89999999, 0), oracle(cases[0].first, cases[0].second, "-"))
    }

    @Test fun executorScopesIdentitySwapsScoresAndShortCircuitsConditions() {
        val probes = functions + mapOf(
            "test:outer" to listOf("scoreboard players set @s marker 1",
                "execute as inner run function test:inner", "scoreboard players add @s marker 1"),
            "test:inner" to listOf("scoreboard players set @s marker 9"))
        val machine = ScoreCommandExecutor(listOf("execute as outer run function test:outer",
            "scoreboard players operation outer marker >< inner marker",
            "scoreboard players set flag int 1",
            "execute if score flag int matches 0 if score @s marker matches 1 run scoreboard players set flag int 0",
            "data modify storage test literal set value \"@s\""), probes)
        assertEquals(9, machine.values.getValue("outer marker"))
        assertEquals(2, machine.values.getValue("inner marker"))
        assertEquals(1, machine.values.getValue("flag int"))
        assertFailsWith<IllegalStateException> {
            ScoreCommandExecutor(listOf("execute as outer run function test:outer", "scoreboard players set @s marker 3"), probes)
        }
        assertFailsWith<IllegalStateException> {
            ScoreCommandExecutor(listOf("execute as @e run scoreboard players set @s marker 3"))
        }
    }

    private fun encoded(value: Float): Decimal {
        val parts = MCFloat.floatToMCFloat(value)
        return Decimal(parts[0], parts[1] * 10000 + parts[2], parts[3])
    }
    private fun captured(value: MCFloat) = top.mcfpp.analysis.StorageAccess.capture(value) as MCFloat
    private fun calculate(left: MCFloat, right: MCFloat, operation: String): MCFloat {
        // Follow the visitor: freeze the left value, prepare the right entity, then load work.
        val frozenLeft = captured(left)
        right.toTempEntity()
        val work = frozenLeft.getTempVar()
        return work.binaryComputation(MCFloat.tempFloat, operation) as MCFloat
    }
    private fun components(machine: ScoreCommandExecutor, value: MCFloat) =
        listOf(value.sign, value.int0, value.int1, value.exp).map(machine::read)

    @Test fun binaryComputationExecutesLibraryAndPreservesCapturedConsecutiveResults() {
        MCFPPStringTest.readFromString("func main(){}", version = "1.20.1")
        Function.currFunction = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        Function.currFunction.commands.clear()
        val inputs = listOf(1f, 0.10000001f, -1.25f, 0.125f)
        val values = inputs.map { MCFloatConcrete(it).toDynamic(false) as MCFloat }
        val left = captured(calculate(values[0], values[1], "-"))
        val right = captured(calculate(values[2], values[3], "+"))
        val result = captured(calculate(left, right, "-"))
        val commands = Function.currFunction.commands.analyzeAll()
        assertEquals(0, Project.errorCount)
        assertEquals(3, commands.count { "run function math.float:hpo/float/_" in it })
        val machine = ScoreCommandExecutor(constants + commands, functions)
        val leftExpected = oracle(encoded(inputs[0]), encoded(inputs[1]), "-")
        val rightExpected = oracle(encoded(inputs[2]), encoded(inputs[3]), "+")
        assertEquals(leftExpected.components(), components(machine, left))
        assertEquals(rightExpected.components(), components(machine, right))
        assertEquals(oracle(leftExpected, rightExpected, "-").components(), components(machine, result))
        values.forEachIndexed { index, value ->
            assertEquals(encoded(inputs[index]).components(), components(machine, value))
        }
    }
}
