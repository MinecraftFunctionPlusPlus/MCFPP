package top.mcfpp.backend

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SbObject
import top.mcfpp.model.function.Function
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TempPool

/** Explicit value conversions. Reinterpretation must never call this service. */
object NumericConversions {
    fun convert(context: NativeCallContext, target: MCFPPType) = context.withArguments { arguments ->
        context.publishResult(convert(arguments[0], target))
    }

    fun toNBT(context: NativeCallContext) = context.withArguments { arguments ->
        context.publishResult(toNBT(arguments[0]))
    }

    private val scoreTypes get() = setOf(MCFPPBaseType.Int, MCFPPNBTType.Byte, MCFPPNBTType.Short)

    @JvmStatic
    fun convert(value: Var<*>, target: MCFPPType): Var<*> {
        if (value.isError) return target.buildUnConcrete(TempPool.getVarIdentify()).apply { isError = true }
        val loaded = top.mcfpp.analysis.StorageAccess.read(value)
        if (loaded !== value) return convert(loaded, target)
        if (value.isError) return target.buildUnConcrete(TempPool.getVarIdentify()).apply { isError = true }
        if (value.type == target) return value
        if (!top.mcfpp.analysis.NumericConversion.supported(value.type.typeId, target.typeId)) return unsupported(value, target)
        if (target == MCFPPBaseType.Int) return toInt(value)
        if (value.type == MCFPPBaseType.Float && target in setOf(MCFPPNBTType.Byte, MCFPPNBTType.Short)) {
            val integer = toInt(value)
            if (integer.isError) return integer
            return narrow(integer as MCInt, if (target == MCFPPNBTType.Byte) 256 else 65536,
                if (target == MCFPPNBTType.Byte) 128 else 32768, target)
        }
        if (value.type in setOf(MCFPPNBTType.Long, MCFPPNBTType.Double) && target == MCFPPBaseType.Float) {
            val known = top.mcfpp.analysis.StorageAccess.snapshot(value)
            top.mcfpp.analysis.NumericConversion.fold(value.type.typeId, target.typeId, known ?: return FloatProviders.fromStoredNumber(value))
                ?.let { return MCFloat(Float.fromBits((it as CompilerValue.FloatBits).bits)) }
            return FloatProviders.fromStoredNumber(value)
        }
        if (value.type == MCFPPNBTType.Long && target == MCFPPNBTType.Double) {
            val known = top.mcfpp.analysis.StorageAccess.snapshot(value)
            if (known != null) top.mcfpp.analysis.NumericConversion.fold(value.type.typeId, target.typeId, known)
                ?.let { return MCDouble(DoubleTag(Double.fromBits((it as CompilerValue.DoubleBits).bits))) }
            val result = MCDouble().apply { isTemp = true }
            top.mcfpp.analysis.StorageAccess.ensure(result)
            Function.addCommands(Command("data modify").build(result.nbtPath.toCommandPart()).build("set value")
                .buildMacro(value).build("d", false).buildMacroFunction())
            return top.mcfpp.analysis.StorageAccess.publishNbt(result)
        }
        if (value.type in scoreTypes) {
            val source = toInt(value)
            return when (target) {
                MCFPPBaseType.Float -> {
                    promoteToFloat(source as MCInt)
                }
                MCFPPNBTType.Byte -> narrow(source as MCInt, 256, 128, target)
                MCFPPNBTType.Short -> narrow(source as MCInt, 65536, 32768, target)
                MCFPPNBTType.Long, MCFPPNBTType.Double -> widen(source as MCInt, target)
                else -> unsupported(value, target)
            }
        }
        return unsupported(value, target)
    }

    private fun toInt(value: Var<*>): Var<*> {
        if (value.type in scoreTypes) {
            val constant = (top.mcfpp.analysis.StorageAccess.snapshot(value) as? CompilerValue.Typed)?.payload as? CompilerValue.Integral
            if (constant != null) return MCInt(constant.value.toInt())
            val score = top.mcfpp.analysis.StorageAccess.intRegister(value as MCInt)
            val result = MCInt().apply { isTemp = true }
            Function.addCommand("scoreboard players operation ${result.name} ${result.sbObject} = ${score.player} ${score.objective}")
            return top.mcfpp.analysis.StorageAccess.publishScore(result, top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
        }
        if (value is MCFloat && value.type == MCFPPBaseType.Float) {
            if (FloatProviders.enabled) return FloatProviders.toInt(value)
            if (value is MCFloat && top.mcfpp.analysis.StorageAccess.snapshot(value) != null && !value.value.isFinite()) {
                LogProcessor.error("Legacy float-to-int conversion requires a finite input")
                return MCInt().apply { isError = true }
            }
            if (value.loadWork() == null) return MCInt().apply { isError = true }
            Function.addCommand("function math.float:hpo/float/_toscore")
            val result = MCInt()
            Function.addCommand("scoreboard players operation ${result.name} ${result.sbObject} = res int")
            return top.mcfpp.analysis.StorageAccess.publishScore(result, top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
        }
        // data get has backend-defined flooring/range behavior; always emit it, including
        // for NBT constants, instead of silently folding with Kotlin numeric casts.
        if (value.type == MCFPPNBTType.Long || value.type == MCFPPNBTType.Double) {
            val runtime = top.mcfpp.analysis.StorageAccess.read(value)
            top.mcfpp.analysis.StorageAccess.ensure(runtime).data.materialize()
            val result = MCInt().apply { isTemp = true }
            Function.addCommand(Command("execute store result score ${result.name} ${result.sbObject} run")
                .build(Commands.dataGet(runtime.nbtPath)))
            return top.mcfpp.analysis.StorageAccess.publishScore(result,
                top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
        }
        return unsupported(value, MCFPPBaseType.Int)
    }

    /** Shared with implicit int -> float promotion; no host rounding on the old backend. */
    fun promoteToFloat(value: MCInt): MCFloat {
        if (FloatProviders.enabled) return FloatProviders.fromInt(value)
        MCFloat.requireLegacyBackend()
        val source = top.mcfpp.analysis.StorageAccess.intRegister(value)
        Function.addCommand("scoreboard players operation inp int = ${source.player} ${source.objective}")
        Function.addCommand("function math.float:hpo/float/_scoreto")
        return MCFloat.captureWorkResult()
    }

    private fun narrow(source: MCInt, modulus: Int, sign: Int, target: MCFPPType): Var<*> {
        val constant = (top.mcfpp.analysis.StorageAccess.snapshot(source) as? CompilerValue.Typed)?.payload as? CompilerValue.Integral
        if (constant != null) {
            val narrowed = Math.floorMod(constant.value, modulus.toLong()).let { if (it >= sign) it - modulus else it }
            return if (target == MCFPPNBTType.Byte) MCByte(narrowed.toByte()) else MCShort(narrowed.toShort())
        }
        val result = target.buildUnConcrete(TempPool.getVarIdentify()) as MCInt
        val period = MCInt(modulus)
        val threshold = MCInt(sign)
        val periodScore = top.mcfpp.analysis.StorageAccess.intRegister(period)
        val thresholdScore = top.mcfpp.analysis.StorageAccess.intRegister(threshold)
        val sourceScore = top.mcfpp.analysis.StorageAccess.intRegister(source)
        period.name = periodScore.player
        threshold.name = thresholdScore.player
        source.name = sourceScore.player
        Function.addCommand(Commands.sbPlayerOperation(result, "=", source))
        Function.addCommand(Commands.sbPlayerOperation(result, "%=", period))
        // Normalizing a possibly negative remainder makes this independent of the host
        // remainder convention and bounds the score to the signed target width.
        Function.addCommand(Command("execute if score ${result.name} ${result.sbObject} matches ..-1 run")
            .build(Commands.sbPlayerOperation(result, "+=", period)))
        Function.addCommand(Command("execute if score ${result.name} ${result.sbObject} >= ${threshold.name} ${threshold.sbObject} run")
            .build(Commands.sbPlayerOperation(result, "-=", period)))
        result.isTemp = true
        return top.mcfpp.analysis.StorageAccess.publishScore(result,
            top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
    }

    private fun widen(source: MCInt, target: MCFPPType): Var<*> {
        val constant = (top.mcfpp.analysis.StorageAccess.snapshot(source) as? CompilerValue.Typed)?.payload as? CompilerValue.Integral
        if (constant != null) return if (target == MCFPPNBTType.Long) MCLong(LongTag(constant.value))
            else MCDouble(DoubleTag(constant.value.toDouble()))
        val result = target.buildUnConcrete(TempPool.getVarIdentify())
        top.mcfpp.analysis.StorageAccess.ensure(result)
        val register = top.mcfpp.analysis.StorageAccess.intRegister(source)
        val encoding = if (target == MCFPPNBTType.Long) "long" else "double"
        Function.addCommand(Command("execute store result").build(result.nbtPath.toCommandPart())
            .build("$encoding 1 run scoreboard players get ${register.player} ${register.objective}"))
        return top.mcfpp.analysis.StorageAccess.publishNbt(result)
    }

    @JvmStatic
    fun toNBT(value: Var<*>): Var<*> {
        if (value.isError) return value
        if (value is MCFloat && top.mcfpp.analysis.StorageAccess.snapshot(value) != null && !value.value.isFinite()) {
            LogProcessor.error("toNBT(float) requires a finite input")
            return NBTBasedData().apply { isError = true }
        }
        if (!top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(value)) {
            LogProcessor.error("${value.type.typeName} cannot be encoded as a Minecraft NBT payload")
            return NBTBasedData().apply { isError = true }
        }
        top.mcfpp.analysis.StorageAccess.constantEncoding(value)?.let { return NBTBasedData(it) }
        top.mcfpp.analysis.StorageAccess.materialize(value)
        return runtimeNBT(top.mcfpp.analysis.StorageAccess.capture(value))
    }

    private fun runtimeNBT(value: Var<*>): Var<*> {
        val encoded = value.toNBTVar()
        return if (encoded.type == MCFPPNBTType.NBT) encoded
        else top.mcfpp.analysis.StorageAccess.view(encoded, MCFPPNBTType.NBT, diagnose = false)
    }

    private fun unsupported(value: Var<*>, target: MCFPPType): Var<*> {
        LogProcessor.error("to${target.typeName.replaceFirstChar { it.uppercase() }}(${value.type.typeName}) has no runtime implementation for this target; convert explicitly through a supported source type")
        return target.buildUnConcrete(TempPool.getVarIdentify()).apply { isError = true }
    }
}
