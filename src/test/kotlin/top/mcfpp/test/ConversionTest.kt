package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.backend.NumericConversions
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.*
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class ConversionTest {
    @Test fun legacyFloatStackRoundTripPreservesAllFourComponents() {
        reset("1.20.2")
        val value = MCFloat("runtime")
        value.nbtPath = top.mcfpp.lib.NBTPath.getNormalStackPath(value)
        Function.addCommand("data modify storage mcfpp:system stack_frame prepend value {}")
        val parts = listOf(value.sign to -1, value.int0 to 1250, value.int1 to 8765, value.exp to 3)
        for ((score, expected) in parts) Function.addCommand("scoreboard players set ${score.name} ${score.sbObject} $expected")
        val encoded = NumericConversions.toNBT(value)
        assertEquals(value.nbtPath.toCommandPart().toString(), encoded.nbtPath.toCommandPart().toString())
        for ((score, _) in parts) Function.addCommand("scoreboard players set ${score.name} ${score.sbObject} 0")
        value.getFromStack()
        Function.addCommand("data remove storage mcfpp:system stack_frame[0]")
        val machine = ScoreCommandExecutor(Function.currFunction.commands.analyzeAll())
        for ((score, expected) in parts) assertEquals(expected, machine.read(score))
        assertEquals(0, machine.stackDepth)
        assertEquals(0, Project.errorCount)
    }

    @Test fun legacyConstantEncodingAndWorkRegistersRetainTheirLayout() {
        reset("1.20.2")
        val value = MCFloatConcrete(-1.25f)
        val encoded = NumericConversions.toNBT(value) as NBTBasedDataConcrete
        val parts = MCFloat.floatToMCFloat(value.value)
        val tag = encoded.value as top.mcfpp.nbt.tags.CompoundTag
        for ((key, expected) in listOf("sign" to parts[0], "int0" to parts[1], "int1" to parts[2], "exp" to parts[3]))
            assertEquals(expected, (tag[key] as IntTag).value)
        assertTrue(Function.currFunction.commands.isEmpty())
        Function.currFunction.commands.clear()
        val temporary = value.getTempVar()
        val machine = ScoreCommandExecutor(Function.currFunction.commands.analyzeAll())
        for ((score, expected) in listOf(temporary.sign to parts[0], temporary.int0 to parts[1],
            temporary.int1 to parts[2], temporary.exp to parts[3])) {
            assertEquals("int", score.sbObject.toString())
            assertEquals(expected, machine.read(score))
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun legacyArithmeticNeverReusesAHostConstantForWorkRegisters() {
        reset("1.20.2")
        for ((operation, expected) in listOf("+" to "_add", "-" to "_rmv", "*" to "_mult", "/" to "_div")) {
            Function.currFunction.commands.clear()
            val left = MCFloatConcrete(1.25f)
            val right = MCFloatConcrete(1.0f)
            val result = left.binaryComputation(right, operation)
            assertFalse(result is MCFPPValue<*>, operation)
            assertEquals(1.25f, left.value)
            assertTrue(Function.currFunction.commands.analyzeAll().any { it.endsWith(expected) }, operation)
        }
        assertEquals(0, Project.errorCount)
    }
    private fun reset(version: String = "26.3") {
        MCFPPStringTest.readFromString("func arithmetic() {}", version = version)
        Function.currFunction = GlobalScope.localNamespaces["default.test"]!!.scope.functions.getValue("arithmetic").single()
        Function.currFunction.commands.clear()
    }

    @Test fun signedNarrowingConstantsAndCommandsAgreeAtIntegerBoundaries() {
        for (width in listOf(MCFPPNBTType.Byte, MCFPPNBTType.Short)) {
            for (input in listOf(Int.MIN_VALUE, -65537, -32769, -129, -128, -1, 0, 127, 128, 32767, 32768, 65535, Int.MAX_VALUE)) {
                reset()
                val source = MCIntConcrete(input)
                val constant = NumericConversions.convert(source, width)
                val dynamic = source.toDynamic(false) as MCInt
                val result = NumericConversions.convert(dynamic, width) as MCInt
                val commands = Function.currFunction.commands.analyzeAll()
                val machine = ScoreCommandExecutor(commands)
                val expected = if (width == MCFPPNBTType.Byte) input.toByte().toInt() else input.toShort().toInt()
                val folded = when (constant) {
                    is MCByteConcrete -> constant.value.toInt()
                    is MCShortConcrete -> constant.value.toInt()
                    else -> error("Expected a constant")
                }
                assertEquals(expected, folded)
                assertEquals(expected, machine.read(result))
                assertEquals(input, machine.read(dynamic), "conversion must not modify its input")
                assertEquals(width, result.type)
            }
        }
    }

    @Test fun nbtMappedInputsBecomeArithmeticOnlyThroughConversions() {
        reset()
        assertEquals(-7, (NumericConversions.convert(MCByteConcrete(-7), MCFPPBaseType.Int) as MCIntConcrete).value)
        assertEquals(32000, (NumericConversions.convert(MCShortConcrete(32000), MCFPPBaseType.Int) as MCIntConcrete).value)
        assertEquals(32000f, (NumericConversions.convert(MCShortConcrete(32000), MCFPPBaseType.Float) as MCFloatConcrete).value)
        assertEquals(32000L, (NumericConversions.convert(MCShortConcrete(32000), MCFPPNBTType.Long) as MCLongConcrete).value.value)
        assertEquals(32000.0, (NumericConversions.convert(MCShortConcrete(32000), MCFPPNBTType.Double) as MCDoubleConcrete).value.value)
        assertEquals(0, Project.errorCount)
    }

    @Test fun unknownNbtRoundingAlwaysUsesBackendDataRead() {
        reset()
        val result = NumericConversions.convert(MCDoubleConcrete(DoubleTag(-1.8)), MCFPPBaseType.Int)
        assertFalse(result is MCFPPValue<*>)
        val commands = Function.currFunction.commands.analyzeAll().joinToString("\n")
        assertTrue(commands.contains("set value -1.8d"), commands)
        assertTrue(commands.contains("run data get"), commands)
        assertEquals(0, Project.errorCount)
    }

    @Test fun legacyFloatConversionDoesNotFoldWithHostRounding() {
        reset("1.20.2")
        val result = NumericConversions.convert(MCFloatConcrete(-1.8f), MCFPPBaseType.Int)
        assertFalse(result is MCFPPValue<*>)
        assertTrue(Function.currFunction.commands.analyzeAll().any { it.contains("math.float:hpo/float/_toscore") })
        assertTrue(Function.currFunction.commands.analyzeAll().any { it.endsWith("= res int") })
        reset("1.20.2")
        val promoted = NumericConversions.convert(MCIntConcrete(16777217), MCFPPBaseType.Float)
        assertFalse(promoted is MCFPPValue<*>)
        assertTrue(Function.currFunction.commands.analyzeAll().any { it.contains("math.float:hpo/float/_scoreto") })
        assertTrue(Function.currFunction.commands.analyzeAll().any { it.startsWith("scoreboard players operation inp int =") })
    }

    @Test fun unsupportedRuntimeConversionsReportAnErrorForConstantsToo() {
        reset()
        assertTrue(NumericConversions.convert(MCLongConcrete(LongTag(7)), MCFPPBaseType.Float).isError)
        assertEquals(1, Project.errorCount)
        reset()
        assertTrue(NumericConversions.toNBT(MCFloatConcrete(Float.NaN)).isError)
        assertEquals(1, Project.errorCount)
    }

    @Test fun nbtEncodingRetainsSourceTagIdentity() {
        reset()
        assertIs<ByteTag>((NumericConversions.toNBT(MCByteConcrete(7)) as NBTBasedDataConcrete).value)
        assertIs<ShortTag>((NumericConversions.toNBT(MCShortConcrete(7)) as NBTBasedDataConcrete).value)
        assertIs<IntTag>((NumericConversions.toNBT(MCIntConcrete(7)) as NBTBasedDataConcrete).value)
        assertIs<FloatTag>((NumericConversions.toNBT(MCFloatConcrete(7f)) as NBTBasedDataConcrete).value)
        val runtime = MCShortConcrete(7).toDynamic(false) as MCShort
        NumericConversions.toNBT(runtime)
        assertTrue(Function.currFunction.commands.analyzeAll().any { it.contains("short 1 run scoreboard") })
    }

    @Test fun identityConversionsKeepRuntimeMappingTypesWithoutCommands() {
        reset()
        for (type in listOf(MCFPPNBTType.Byte, MCFPPNBTType.Short, MCFPPNBTType.Long, MCFPPNBTType.Double)) {
            val source = type.buildUnConcrete("runtime")
            assertSame(source, NumericConversions.convert(source, type))
            assertEquals(type, NumericConversions.convert(source, type).type)
        }
        assertTrue(Function.currFunction.commands.isEmpty())
    }

    @Test fun runtimePredicateEncodingMaterializesABooleanByteAtARealPath() {
        reset()
        val input = top.mcfpp.core.lang.bool.ScoreBool("input")
        val predicate = input.negation()
        val encoded = NumericConversions.toNBT(predicate)
        assertTrue(encoded.nbtPath.pathList.isNotEmpty())
        val commands = Function.currFunction.commands.analyzeAll()
        assertTrue(commands.any { it.startsWith("execute store success score") }, commands.toString())
        assertTrue(commands.any { it.contains("byte 1 run scoreboard players get") }, commands.toString())
        assertEquals(0, Project.errorCount)
    }

    @Test fun standardLibraryConversionOverloadsWorkWithKnownAnyAndRejectUnknownAny() {
        MCFPPStringTest.readFromString("""
            func arithmetic(){
                var encoded = 7b;
                var result = toInt(encoded) + 2;
                var wide = toLong(result);
                var payload = toNBT(encoded);
                dynamic var erased as any = 9;
                var promoted = toFloat(erased);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val function = GlobalScope.localNamespaces["default.test"]!!.scope.functions.getValue("arithmetic").single()
        assertEquals(9, (function.scope.getVar("result") as MCIntConcrete).value)
        assertEquals(MCFPPNBTType.Long, function.scope.getVar("wide")!!.type)
        assertEquals(MCFPPBaseType.Float, function.scope.getVar("promoted")!!.type)
        MCFPPStringTest.readFromString("func arithmetic(){ var erased as any; var result = toInt(erased); }", version = "26.3")
        assertTrue(Project.errorCount > 0)
    }
}
