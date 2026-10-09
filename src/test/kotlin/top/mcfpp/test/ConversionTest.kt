package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
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
    private fun integral(value: Var<*>): Long =
        (assertIs<CompilerValue.Typed>(assertNotNull(StorageAccess.snapshot(value))).payload as CompilerValue.Integral).value

    private fun float(value: Var<*>): Float = Float.fromBits(
        assertIs<CompilerValue.FloatBits>(assertIs<CompilerValue.Typed>(assertNotNull(StorageAccess.snapshot(value))).payload).bits)

    private fun runtime(value: Var<*>): Var<*> {
        val result = value.type.buildUnConcrete("runtime_${value.identifier}").apply {
            nbtPath = top.mcfpp.lib.NBTPath(top.mcfpp.lib.StorageSource("fixture:numeric"))
                .memberIndex(identifier)
        }
        StorageAccess.declare(result, Symbol(SymbolId.fresh(), result.identifier, result.type.typeId, mutable = true))
        StorageAccess.write(result, value)
        StorageAccess.materialize(result)
        val binding = StorageAccess.ensure(result)
        binding.data.facts.refine(binding.place, ValueFacts(TypeKnowledge.Exact(result.type.typeId), ValueKnowledge.Unknown))
        return StorageAccess.read(result)
    }
    @Test fun legacyFloatStackRoundTripPreservesAllFourComponents() {
        reset("1.20.2")
        val value = MCFloat("runtime")
        value.nbtPath = top.mcfpp.lib.NBTPath.getNormalStackPath(value)
        Function.addCommand("data modify storage mcfpp:system stack_frame prepend value {}")
        val parts = listOf(value.sign to -1, value.int0 to 1250, value.int1 to 8765, value.exp to 3)
        for ((score, expected) in parts) Function.addCommand("scoreboard players set ${score.name} ${score.sbObject} $expected")
        StorageAccess.publishLegacyFloat(value, parts.map { (score, _) -> StorageLayout.Scoreboard(score.name, score.sbObject.toString()) })
        val encoded = NumericConversions.toNBT(value)
        assertNotEquals(StorageAccess.ensure(value).place, StorageAccess.ensure(encoded).place)
        assertNotEquals(StorageAccess.ensure(value).path.toCommandPart().toString(), StorageAccess.ensure(encoded).path.toCommandPart().toString())
        Function.addCommand(top.mcfpp.command.Command.buildAll("data modify storage fixture:conversion encoded set from", StorageAccess.ensure(encoded).path.toCommandPart()))
        for ((score, _) in parts) Function.addCommand("scoreboard players set ${score.name} ${score.sbObject} 0")
        value.getFromStack()
        Function.addCommand("data remove storage mcfpp:system stack_frame[0]")
        val machine = ScoreCommandExecutor(Function.currFunction.commands.analyzeAll())
        for ((score, expected) in parts) assertEquals(expected, machine.values.getValue("${score.name} ${score.sbObject}"))
        val copied = machine.readNbt("fixture:conversion", "encoded") as top.mcfpp.nbt.tags.CompoundTag
        for ((key, expected) in listOf("sign" to -1, "int0" to 1250, "int1" to 8765, "exp" to 3))
            assertEquals(IntTag(expected), copied[key])
        assertEquals(0, machine.stackDepth)
        assertEquals(0, Project.errorCount)
    }

    @Test fun legacyConstantEncodingAndWorkRegistersRetainTheirLayout() {
        reset("1.20.2")
        val value = MCFloat(-1.25f)
        val encoded = NumericConversions.toNBT(value) as NBTBasedData
        val parts = MCFloat.floatToMCFloat(float(value))
        val tag = StorageAccess.constantEncoding(encoded) as top.mcfpp.nbt.tags.CompoundTag
        for ((key, expected) in listOf("sign" to parts[0], "int0" to parts[1], "int1" to parts[2], "exp" to parts[3]))
            assertEquals(expected, (tag[key] as IntTag).value)
        assertTrue(Function.currFunction.commands.isEmpty())
        Function.currFunction.commands.clear()
        val temporary = assertNotNull(value.loadWork())
        val machine = ScoreCommandExecutor(listOf("data modify storage mcfpp:system stack_frame prepend value {}") + Function.currFunction.commands.analyzeAll())
        for ((score, expected) in assertNotNull(StorageAccess.legacyFloatRegisters(temporary)).zip(parts)) {
            assertEquals("int", score.objective)
            assertEquals(expected, machine.values.getValue("${score.player} ${score.objective}"))
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun legacyArithmeticNeverReusesAHostConstantForWorkRegisters() {
        reset("1.20.2")
        for ((operation, expected) in listOf("+" to "_add", "-" to "_rmv", "*" to "_mult", "/" to "_div")) {
            Function.currFunction.commands.clear()
            val left = MCFloat(1.25f)
            val right = MCFloat(1.0f)
            val result = left.binaryComputation(right, operation)
            assertNull(StorageAccess.snapshot(result), operation)
            assertEquals(ValueState.INITIALIZED, StorageAccess.ensure(result).let { it.data.facts.read(it.place)?.state })
            assertEquals(1.25f, float(left))
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
                val source = MCInt(input)
                val constant = NumericConversions.convert(source, width)
                val dynamic = runtime(source) as MCInt
                val result = NumericConversions.convert(dynamic, width) as MCInt
                val commands = Function.currFunction.commands.analyzeAll()
                val machine = ScoreCommandExecutor(listOf("data modify storage mcfpp:system stack_frame prepend value {}") + commands)
                val expected = if (width == MCFPPNBTType.Byte) input.toByte().toInt() else input.toShort().toInt()
                val folded = integral(constant).toInt()
                assertEquals(width.typeId, assertIs<CompilerValue.Typed>(StorageAccess.snapshot(constant)).type)
                assertEquals(expected, folded)
                assertEquals(expected, machine.read(result))
                assertEquals(input, machine.read(dynamic), "conversion must not modify its input")
                assertEquals(width, result.type)
            }
        }
    }

    @Test fun nbtMappedInputsBecomeArithmeticOnlyThroughConversions() {
        reset()
        assertEquals(-7L, integral(NumericConversions.convert(MCByte(-7), MCFPPBaseType.Int)))
        assertEquals(32000L, integral(NumericConversions.convert(MCShort(32000), MCFPPBaseType.Int)))
        assertEquals(32000f, float(NumericConversions.convert(MCShort(32000), MCFPPBaseType.Float)))
        assertEquals(LongTag(32000L), StorageAccess.constantEncoding(NumericConversions.convert(MCShort(32000), MCFPPNBTType.Long)))
        assertEquals(DoubleTag(32000.0), StorageAccess.constantEncoding(NumericConversions.convert(MCShort(32000), MCFPPNBTType.Double)))
        assertEquals(0, Project.errorCount)
    }

    @Test fun unknownNbtRoundingAlwaysUsesBackendDataRead() {
        reset()
        val result = NumericConversions.convert(MCDouble(DoubleTag(-1.8)), MCFPPBaseType.Int)
        assertNull(StorageAccess.snapshot(result))
        val commands = Function.currFunction.commands.analyzeAll().joinToString("\n")
        assertTrue(commands.contains("set value -1.8d"), commands)
        assertTrue(commands.contains("run data get"), commands)
        assertEquals(0, Project.errorCount)
    }

    @Test fun legacyFloatConversionDoesNotFoldWithHostRounding() {
        reset("1.20.2")
        val result = NumericConversions.convert(MCFloat(-1.8f), MCFPPBaseType.Int)
        assertNull(StorageAccess.snapshot(result))
        assertTrue(Function.currFunction.commands.analyzeAll().any { it.contains("math.float:hpo/float/_toscore") })
        assertTrue(Function.currFunction.commands.analyzeAll().any { it.endsWith("= res int") })
        reset("1.20.2")
        val promoted = NumericConversions.convert(MCInt(16777217), MCFPPBaseType.Float)
        assertNull(StorageAccess.snapshot(promoted))
        assertTrue(Function.currFunction.commands.analyzeAll().any { it.contains("math.float:hpo/float/_scoreto") })
        assertTrue(Function.currFunction.commands.analyzeAll().any { it.startsWith("scoreboard players operation inp int =") })
    }

    @Test fun unsupportedRuntimeConversionsReportAnErrorForConstantsToo() {
        reset()
        assertTrue(NumericConversions.convert(MCLong(LongTag(7)), MCFPPNBTType.Byte).isError)
        assertEquals(1, Project.errorCount)
        reset()
        assertTrue(NumericConversions.toNBT(MCFloat(Float.NaN)).isError)
        assertEquals(1, Project.errorCount)
    }

    @Test fun nbtEncodingRetainsSourceTagIdentity() {
        reset()
        assertEquals(ByteTag(7), StorageAccess.constantEncoding(NumericConversions.toNBT(MCByte(7))))
        assertEquals(ShortTag(7), StorageAccess.constantEncoding(NumericConversions.toNBT(MCShort(7))))
        assertEquals(IntTag(7), StorageAccess.constantEncoding(NumericConversions.toNBT(MCInt(7))))
        assertEquals(FloatTag(7f), StorageAccess.constantEncoding(NumericConversions.toNBT(MCFloat(7f))))
        val runtime = runtime(MCShort(7)) as MCShort
        val encoded = NumericConversions.toNBT(runtime)
        assertNotEquals(StorageAccess.ensure(runtime).place, StorageAccess.ensure(encoded).place)
        Function.addCommand(top.mcfpp.command.Command.buildAll("data modify storage fixture:conversion encoded set from", StorageAccess.ensure(encoded).path.toCommandPart()))
        val machine = ScoreCommandExecutor(listOf("data modify storage mcfpp:system stack_frame prepend value {}") + Function.currFunction.commands.analyzeAll())
        assertEquals(ShortTag(7), machine.readNbt("fixture:conversion", "encoded"))
    }

    @Test fun identityConversionsKeepRuntimeMappingTypesWithoutCommands() {
        reset()
        for (type in listOf(MCFPPNBTType.Byte, MCFPPNBTType.Short, MCFPPNBTType.Long, MCFPPNBTType.Double)) {
            val source = type.buildUnConcrete("runtime")
            StorageAccess.bindIncomingParameter(source)
            val prepared = StorageAccess.read(source)
            val before = Function.currFunction.commands.size
            val converted = NumericConversions.convert(prepared, type)
            assertEquals(type, converted.type)
            assertSame(StorageAccess.ensure(source).data, StorageAccess.ensure(converted).data)
            assertEquals(StorageAccess.ensure(source).location, StorageAccess.ensure(converted).location)
            assertNull(StorageAccess.snapshot(converted))
            assertEquals(before, Function.currFunction.commands.size, "Identity conversion adds no commands after its input is read")
        }
    }

    @Test fun runtimePredicateEncodingMaterializesABooleanByteAtARealPath() {
        reset()
        val input = top.mcfpp.core.lang.bool.ScoreBool("input")
        Function.addCommand("scoreboard players set ${input.name} ${input.boolObject} 1")
        StorageAccess.publishBoolean(input, StorageLayout.Scoreboard(input.name, input.boolObject.toString()))
        val predicate = input.negation()
        val encoded = NumericConversions.toNBT(predicate)
        val address = StorageAccess.ensure(encoded).path.toCommandPart().toString()
        val commands = Function.currFunction.commands.analyzeAll()
        assertTrue(commands.any { it.startsWith("execute unless score input ") && it.contains("matches 1 run scoreboard players set") }, commands.toString())
        assertTrue(commands.any { it.contains("byte 1 run scoreboard players get") }, commands.toString())
        val machine = ScoreCommandExecutor(listOf("data modify storage mcfpp:system stack_frame prepend value {}") + commands)
        val sourceAndPath = address.removePrefix("storage ").split(' ', limit = 2)
        assertEquals(ByteTag(0), machine.readNbt(sourceAndPath[0], sourceAndPath[1]))
        assertEquals(1, machine.values.getValue("${input.name} ${input.boolObject}"))
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
        assertEquals(9L, integral(assertNotNull(function.scope.getVar("result"))))
        assertEquals(MCFPPNBTType.Long, function.scope.getVar("wide")!!.type)
        assertEquals(MCFPPBaseType.Float, function.scope.getVar("promoted")!!.type)
        MCFPPStringTest.readFromString("func arithmetic(){ var erased as any; var result = toInt(erased); }", version = "26.3")
        assertTrue(Project.errorCount > 0)
    }
}
