package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.backend.LegacyFloatComparison
import top.mcfpp.backend.NumericConversions
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCFloatConcrete
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.*
import kotlin.test.Test

class LegacyFloatLayoutTest {
    private fun reset(version: String = "1.20.1") {
        MCFPPStringTest.readFromString("func main(){}", version = version)
        Function.currFunction = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        Function.currFunction.commands.clear()
    }

    @Test fun finiteValuesUseNormalizedEightDigitComponentsAcrossTheFloatRange() {
        assertContentEquals(arrayOf(1,1000,0,11), MCFloat.floatToMCFloat(1e10f))
        assertContentEquals(arrayOf(1,1401,2985,-44), MCFloat.floatToMCFloat(Float.MIN_VALUE))
        assertContentEquals(arrayOf(1,3402,8235,39), MCFloat.floatToMCFloat(Float.MAX_VALUE))
        assertContentEquals(arrayOf(0,0,0,0), MCFloat.floatToMCFloat(0f))
        assertContentEquals(arrayOf(0,0,0,0), MCFloat.floatToMCFloat(-0f))
        for (value in listOf(Float.MIN_VALUE, 1e-30f, 0.00001f, 0.0012345678f, 1.2345678f, 1e10f, Float.MAX_VALUE)) {
            for (signed in listOf(value, -value)) {
                val (sign, high, low, exponent) = MCFloat.floatToMCFloat(signed)
                assertTrue(high in 1000..9999)
                assertTrue(low in 0..9999)
                val unit = 10.0.pow(exponent - 8)
                val restored = sign * (high * 10000L + low) * unit
                assertTrue(abs(restored - signed.toDouble()) <= unit * 0.500001, "$signed -> $restored")
            }
        }
    }

    @Test fun comparisonsRespectSignAndLexicographicMagnitudeWithoutTouchingOperands() {
        reset()
        val pairs = listOf(1.99f to 2.01f, 12.9f to 9.99f, -1.25f to -1.5f, -100f to -2f,
            -1f to 0f, 0f to 1f, 0f to -0f, 1e-30f to 1e10f, Float.MIN_VALUE to 0f,
            Float.MAX_VALUE to 1e30f, 1.25f to 1.25f)
        for ((a, b) in pairs) for (operation in listOf("<", ">", "<=", ">=", "==", "!=")) {
            Function.currFunction.commands.clear()
            val left = MCFloatConcrete(a)
            val right = MCFloatConcrete(b)
            val result = left.binaryComputation(right, operation) as ScoreBool
            val commands = Function.currFunction.commands.analyzeAll()
            val machine = ScoreCommandExecutor(commands)
            val expected = when (operation) {
                "<" -> a < b; ">" -> a > b; "<=" -> a <= b; ">=" -> a >= b; "==" -> a == b; else -> a != b
            }
            assertEquals(if (expected) 1 else 0, machine.read(result), "$a $operation $b")
            for (value in listOf(left, right)) {
                val encoded = MCFloat.floatToMCFloat(value.value)
                listOf(value.sign, value.int0, value.int1, value.exp).forEachIndexed { index, score ->
                    assertEquals(encoded[index], machine.read(score))
                }
            }
            assertFalse(commands.any { "run return" in it || "run function" in it || MCFloat.tempFloatEntityUUID in it })
        }
        assertFalse(MCFloat.ssObj is MCFPPValue<*>)
        assertEquals(0, Project.errorCount)
    }

    @Test fun zeroSignIgnoresResidualMagnitudeFields() {
        val commands = mutableListOf<String>()
        val left = LegacyFloatComparison.Components("ls int", "lh int", "ll int", "le int")
        val right = LegacyFloatComparison.Components("rs int", "rh int", "rl int", "re int")
        for ((name, value) in listOf("ls" to 0, "lh" to 9999, "ll" to 8888, "le" to 39,
            "rs" to 0, "rh" to 0, "rl" to 0, "re" to -44)) commands += "scoreboard players set $name int $value"
        LegacyFloatComparison.emit(left, right, "==", "comparison int", "result int", commands::add)
        assertEquals(1, ScoreCommandExecutor(commands).values.getValue("result int"))
    }

    @Test fun parsedLegacyComparisonsWorkOnTargetsWithoutFunctionReturns() {
        MCFPPStringTest.readFromString("""
            func main(){
                dynamic var left = -1.25;
                dynamic var right = -1.5;
                dynamic var result = left > right;
            }
        """.trimIndent(), version = "1.20.1")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val machine = ScoreCommandExecutor(main.commands.analyzeAll())
        assertEquals(1, machine.read(main.scope.getVar("result") as ScoreBool))
    }

    @Test fun nonFiniteValuesCannotBeEncodedOrMaterializedOnLegacyTargets() {
        reset()
        assertFailsWith<IllegalArgumentException> { MCFloat.floatToMCFloat(Float.NaN) }
        assertTrue(NumericConversions.toNBT(MCFloatConcrete(Float.POSITIVE_INFINITY)).isError)
        assertTrue(MCFloatConcrete(Float.NaN).toDynamic(false).isError)
        assertEquals(2, Project.errorCount)
        assertTrue(Function.currFunction.commands.isEmpty())
        MCFPPStringTest.readFromString("func main(){ dynamic var invalid = 9999999999999999999999999999999999999999.0; }", version = "1.20.1")
        assertTrue(Project.errorCount > 0)
    }
}
