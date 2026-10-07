package top.mcfpp.test

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import top.mcfpp.Project
import top.mcfpp.backend.NumericConversions
import top.mcfpp.core.lang.MCFloatConcrete
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class LegacyFloatConversionTest {
    private val datapack = Path.of("src/main/resources/datapack/stdlib/data")
    private val functions = Files.walk(datapack.resolve("math.float/function")).use { paths ->
        paths.filter { it.toString().endsWith(".mcfunction") }.iterator().asSequence().associate { path ->
            val relative = datapack.resolve("math.float/function").relativize(path).toString().replace('\\', '/')
            "math.float:" + relative.removeSuffix(".mcfunction") to Files.readAllLines(path)
        }
    }
    private val constants = Files.readAllLines(datapack.resolve("math/function/_init.mcfunction"))
        .filter { it.startsWith("scoreboard players set ") }

    private fun machine(commands: List<String>) = ScoreCommandExecutor(constants + commands, functions)
    private fun parts(machine: ScoreCommandExecutor) =
        listOf("sign", "int0", "int1", "exp").map { machine.values.getValue("float_$it int") }
    private fun seed(sign: Int, high: Int, low: Int, exponent: Int) =
        listOf("sign" to sign, "int0" to high, "int1" to low, "exp" to exponent)
            .map { (key, value) -> "scoreboard players set float_$key int $value" }

    @Test fun integerPromotionNormalizesEveryDecimalWidthWithoutChangingInput() {
        val inputs = linkedSetOf(0, Int.MIN_VALUE, Int.MAX_VALUE, 999999999, 123456789, 1234567890)
        var power = 1L
        while (power <= 1000000000L) {
            for (value in listOf(power - 1, power, power + 1)) {
                inputs += value.toInt()
                inputs += -value.toInt()
            }
            power *= 10
        }
        for (input in inputs) {
            val actual = machine(seed(-1, 9999, 8888, 39) +
                "scoreboard players set inp int $input" + "function math.float:hpo/float/_scoreto")
            val magnitude = kotlin.math.abs(input.toLong())
            val digits = magnitude.toString().length
            val expected = if (input == 0) listOf(0, 0, 0, 0) else {
                val mantissa = if (digits <= 8) magnitude * BigDecimal.TEN.pow(8 - digits).toLong()
                    else magnitude / BigDecimal.TEN.pow(digits - 8).toLong()
                listOf(if (input < 0) -1 else 1, (mantissa / 10000).toInt(), (mantissa % 10000).toInt(), digits)
            }
            assertEquals(expected, parts(actual), "input=$input")
            assertEquals(input, actual.values.getValue("inp int"))
        }
    }

    @Test fun floatConversionUsesDecimalExponentTruncatesAndSaturatesWithoutChangingSource() {
        for (sign in listOf(-1, 0, 1)) for (exponent in listOf(-44, -1, 0) + (1..11) + 39)
            for (mantissa in listOf(10000000, 12345678, 21474836, 21474837, 99999999)) {
                val high = mantissa / 10000
                val low = mantissa % 10000
                val actual = machine(seed(sign, high, low, exponent) + "function math.float:hpo/float/_toscore")
                val decimal = BigDecimal(mantissa).scaleByPowerOfTen(exponent - 8)
                    .multiply(BigDecimal(sign)).toBigInteger()
                val expected = decimal.max(java.math.BigInteger.valueOf(Int.MIN_VALUE.toLong()))
                    .min(java.math.BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
                assertEquals(expected, actual.values.getValue("res int"), "$sign/$mantissa/$exponent")
                assertEquals(listOf(sign, high, low, exponent), parts(actual))
            }
    }

    private fun reset() {
        MCFPPStringTest.readFromString("func main(){}", version = "1.20.1")
        Function.currFunction = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        Function.currFunction.commands.clear()
    }

    @Test fun oldConversionEntryRunsTheActualLibrary() {
        reset()
        val result = NumericConversions.convert(MCFloatConcrete(-12.75f), top.mcfpp.type.MCFPPBaseType.Int) as MCInt
        val commands = Function.currFunction.commands.analyzeAll()
        assertTrue(commands.any { "function math.float:hpo/float/_toscore" in it })
        assertEquals(-12, machine(commands).read(result))
        assertEquals(0, Project.errorCount)
    }

    @Test fun oldPromotionEntryRunsTheActualLibrary() {
        reset()
        val source = top.mcfpp.core.lang.MCIntConcrete(123456789).toDynamic(false) as MCInt
        val result = NumericConversions.promoteToFloat(source)
        val commands = Function.currFunction.commands.analyzeAll()
        assertTrue(commands.any { "function math.float:hpo/float/_scoreto" in it })
        val actual = machine(commands)
        assertEquals(listOf(1, 1234, 5678, 9), listOf(result.sign, result.int0, result.int1, result.exp).map(actual::read))
        assertEquals(123456789, actual.read(source))
        assertEquals(0, Project.errorCount)
    }

    @Test fun finiteOutOfRangeFloatsUseTheSameSaturatingLibraryForKnownAndRuntimeInputs() {
        for (value in listOf(2147483648f, -2147483904f, Float.MAX_VALUE, -Float.MAX_VALUE)) {
            reset()
            Function.addCommand(top.mcfpp.command.Commands.stackIn())
            val known=NumericConversions.convert(MCFloatConcrete(value),top.mcfpp.type.MCFPPBaseType.Int) as MCInt
            val source=MCFloatConcrete(value).toDynamic(false)
            val runtime=NumericConversions.convert(source,top.mcfpp.type.MCFPPBaseType.Int) as MCInt
            val commands=Function.currFunction.commands.analyzeAll()
            assertEquals(2,commands.count {it=="function math.float:hpo/float/_toscore"})
            val actual=machine(commands)
            val encoded=top.mcfpp.core.lang.MCFloat.floatToMCFloat(value)
            val integer=BigDecimal((encoded[0].toLong()*(encoded[1]*10000L+encoded[2])).toString())
                .scaleByPowerOfTen(encoded[3]-8).toBigInteger()
            val expected=integer.coerceIn(java.math.BigInteger.valueOf(Int.MIN_VALUE.toLong()),
                java.math.BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
            assertEquals(expected,actual.read(known));assertEquals(expected,actual.read(runtime))
            assertEquals(0,Project.errorCount)
        }
    }

    @Test fun parsedPromotionAndConversionSelectLegacyLibrary() {
        MCFPPStringTest.readFromString("""
            func main(){
                dynamic var input = 123456789;
                dynamic var promoted as float = input;
                dynamic var converted = toInt(promoted);
            }
        """.trimIndent(), version = "1.20.1")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val commands = main.commands.analyzeAll()
        assertTrue(commands.any { "function math.float:hpo/float/_scoreto" in it })
        assertTrue(commands.any { "function math.float:hpo/float/_toscore" in it })
        assertFalse(commands.any { "compute default" in it })
    }
}
