package top.mcfpp.analysis

import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.TypeId

/** Explicit conversion rules. Long/double reads deliberately have no host-side rounding rule. */
object NumericConversion {
    private val int = MCFPPBaseType.Int.typeId
    private val float = MCFPPBaseType.Float.typeId
    private val byte = MCFPPNBTType.Byte.typeId
    private val short = MCFPPNBTType.Short.typeId
    private val long = MCFPPNBTType.Long.typeId
    private val double = MCFPPNBTType.Double.typeId
    private val integers = setOf(int, byte, short)

    fun supported(source: TypeId, target: TypeId) = source == target || target == MCFPPNBTType.NBT.typeId ||
        source in integers && target in integers + setOf(float, long, double) ||
        source in setOf(float, long, double) && target == int

    fun floatToIntError(value: Float): String? = when {
        !value.isFinite() -> "Float-to-int conversion requires a finite input"
        value.toDouble() < Int.MIN_VALUE.toDouble() || value.toDouble() >= 2147483648.0 ->
            "Float-to-int conversion is outside the 32-bit integer range"
        else -> null
    }

    fun fold(source: TypeId, target: TypeId, value: CompilerValue): CompilerValue? {
        if (source == target) return value
        if (target == MCFPPNBTType.NBT.typeId) return CompilerValue.Typed(target, CompilerValue.Typed(source, value))
        if (!top.mcfpp.command.FloatProviders.enabled && (source == float || target == float)) return null
        if (value is CompilerValue.Typed) return fold(source, target, value.payload)
        if (source in integers && value is CompilerValue.Integral) return when (target) {
            int, long -> value
            byte -> CompilerValue.Integral(value.value.toByte().toLong())
            short -> CompilerValue.Integral(value.value.toShort().toLong())
            float -> CompilerValue.FloatBits(value.value.toFloat().toRawBits())
            double -> CompilerValue.DoubleBits(value.value.toDouble().toRawBits())
            else -> null
        }
        if (source == float && target == int && value is CompilerValue.FloatBits) {
            val number = Float.fromBits(value.bits)
            return if (floatToIntError(number) == null) CompilerValue.Integral(number.toInt().toLong()) else null
        }
        return null
    }
}
