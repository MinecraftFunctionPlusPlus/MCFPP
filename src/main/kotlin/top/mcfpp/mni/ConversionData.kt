package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NumericConversions
import top.mcfpp.core.lang.MCInt
import top.mcfpp.type.*

/** Concrete language overloads; unknown any never has a conversion fallback. */
@top.mcfpp.mni.annotation.NoExternalWrites
object ConversionData {
    /** Converts an MCFPP Int value to Int and publishes the converted result. */
    @MNIFunction(identifier = "toInt", normalParams = ["int"], returnType = "int")
    @JvmStatic
    fun toIntFromInt(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Int)
    }

    /** Converts an MCFPP Float value to Int and publishes the converted result. */
    @MNIFunction(identifier = "toInt", normalParams = ["float"], returnType = "int")
    @JvmStatic
    fun toIntFromFloat(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Int)
    }

    /** Converts an MCFPP Byte value to Int and publishes the converted result. */
    @MNIFunction(identifier = "toInt", normalParams = ["byte"], returnType = "int")
    @JvmStatic
    fun toIntFromByte(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Int)
    }

    /** Converts an MCFPP Short value to Int and publishes the converted result. */
    @MNIFunction(identifier = "toInt", normalParams = ["short"], returnType = "int")
    @JvmStatic
    fun toIntFromShort(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Int)
    }

    /** Converts an MCFPP Long value to Int and publishes the converted result. */
    @MNIFunction(identifier = "toInt", normalParams = ["long"], returnType = "int")
    @JvmStatic
    fun toIntFromLong(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Int)
    }

    /** Converts an MCFPP Double value to Int and publishes the converted result. */
    @MNIFunction(identifier = "toInt", normalParams = ["double"], returnType = "int")
    @JvmStatic
    fun toIntFromDouble(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Int)
    }

    /** Converts an MCFPP Int value to Float and publishes the converted result. */
    @MNIFunction(identifier = "toFloat", normalParams = ["int"], returnType = "float")
    @JvmStatic
    fun toFloatFromInt(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Float)
    }

    /** Converts an MCFPP Float value to Float and publishes the converted result. */
    @MNIFunction(identifier = "toFloat", normalParams = ["float"], returnType = "float")
    @JvmStatic
    fun toFloatFromFloat(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Float)
    }

    /** Converts an MCFPP Byte value to Float and publishes the converted result. */
    @MNIFunction(identifier = "toFloat", normalParams = ["byte"], returnType = "float")
    @JvmStatic
    fun toFloatFromByte(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Float)
    }

    /** Converts an MCFPP Short value to Float and publishes the converted result. */
    @MNIFunction(identifier = "toFloat", normalParams = ["short"], returnType = "float")
    @JvmStatic
    fun toFloatFromShort(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Float)
    }

    /** Converts an MCFPP Long value to Float and publishes the converted result. */
    @MNIFunction(identifier = "toFloat", normalParams = ["long"], returnType = "float")
    @JvmStatic
    fun toFloatFromLong(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Float)
    }

    /** Converts an MCFPP Double value to Float and publishes the converted result. */
    @MNIFunction(identifier = "toFloat", normalParams = ["double"], returnType = "float")
    @JvmStatic
    fun toFloatFromDouble(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPBaseType.Float)
    }

    /** Converts an MCFPP Int value to Byte and publishes the converted result. */
    @MNIFunction(identifier = "toByte", normalParams = ["int"], returnType = "byte")
    @JvmStatic
    fun toByteFromInt(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Byte)
    }

    /** Converts an MCFPP Float value to Byte and publishes the converted result. */
    @MNIFunction(identifier = "toByte", normalParams = ["float"], returnType = "byte")
    @JvmStatic
    fun toByteFromFloat(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Byte)
    }

    /** Converts an MCFPP Byte value to Byte and publishes the converted result. */
    @MNIFunction(identifier = "toByte", normalParams = ["byte"], returnType = "byte")
    @JvmStatic
    fun toByteFromByte(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Byte)
    }

    /** Converts an MCFPP Short value to Byte and publishes the converted result. */
    @MNIFunction(identifier = "toByte", normalParams = ["short"], returnType = "byte")
    @JvmStatic
    fun toByteFromShort(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Byte)
    }

    /** Converts an MCFPP Long value to Byte and publishes the converted result. */
    @MNIFunction(identifier = "toByte", normalParams = ["long"], returnType = "byte")
    @JvmStatic
    fun toByteFromLong(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Byte)
    }

    /** Converts an MCFPP Double value to Byte and publishes the converted result. */
    @MNIFunction(identifier = "toByte", normalParams = ["double"], returnType = "byte")
    @JvmStatic
    fun toByteFromDouble(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Byte)
    }

    /** Converts an MCFPP Int value to Short and publishes the converted result. */
    @MNIFunction(identifier = "toShort", normalParams = ["int"], returnType = "short")
    @JvmStatic
    fun toShortFromInt(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Short)
    }

    /** Converts an MCFPP Float value to Short and publishes the converted result. */
    @MNIFunction(identifier = "toShort", normalParams = ["float"], returnType = "short")
    @JvmStatic
    fun toShortFromFloat(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Short)
    }

    /** Converts an MCFPP Byte value to Short and publishes the converted result. */
    @MNIFunction(identifier = "toShort", normalParams = ["byte"], returnType = "short")
    @JvmStatic
    fun toShortFromByte(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Short)
    }

    /** Converts an MCFPP Short value to Short and publishes the converted result. */
    @MNIFunction(identifier = "toShort", normalParams = ["short"], returnType = "short")
    @JvmStatic
    fun toShortFromShort(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Short)
    }

    /** Converts an MCFPP Long value to Short and publishes the converted result. */
    @MNIFunction(identifier = "toShort", normalParams = ["long"], returnType = "short")
    @JvmStatic
    fun toShortFromLong(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Short)
    }

    /** Converts an MCFPP Double value to Short and publishes the converted result. */
    @MNIFunction(identifier = "toShort", normalParams = ["double"], returnType = "short")
    @JvmStatic
    fun toShortFromDouble(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Short)
    }

    /** Converts an MCFPP Int value to Long and publishes the converted result. */
    @MNIFunction(identifier = "toLong", normalParams = ["int"], returnType = "long")
    @JvmStatic
    fun toLongFromInt(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Long)
    }

    /** Converts an MCFPP Float value to Long and publishes the converted result. */
    @MNIFunction(identifier = "toLong", normalParams = ["float"], returnType = "long")
    @JvmStatic
    fun toLongFromFloat(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Long)
    }

    /** Converts an MCFPP Byte value to Long and publishes the converted result. */
    @MNIFunction(identifier = "toLong", normalParams = ["byte"], returnType = "long")
    @JvmStatic
    fun toLongFromByte(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Long)
    }

    /** Converts an MCFPP Short value to Long and publishes the converted result. */
    @MNIFunction(identifier = "toLong", normalParams = ["short"], returnType = "long")
    @JvmStatic
    fun toLongFromShort(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Long)
    }

    /** Converts an MCFPP Long value to Long and publishes the converted result. */
    @MNIFunction(identifier = "toLong", normalParams = ["long"], returnType = "long")
    @JvmStatic
    fun toLongFromLong(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Long)
    }

    /** Converts an MCFPP Double value to Long and publishes the converted result. */
    @MNIFunction(identifier = "toLong", normalParams = ["double"], returnType = "long")
    @JvmStatic
    fun toLongFromDouble(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Long)
    }

    /** Converts an MCFPP Int value to Double and publishes the converted result. */
    @MNIFunction(identifier = "toDouble", normalParams = ["int"], returnType = "double")
    @JvmStatic
    fun toDoubleFromInt(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Double)
    }

    /** Converts an MCFPP Float value to Double and publishes the converted result. */
    @MNIFunction(identifier = "toDouble", normalParams = ["float"], returnType = "double")
    @JvmStatic
    fun toDoubleFromFloat(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Double)
    }

    /** Converts an MCFPP Byte value to Double and publishes the converted result. */
    @MNIFunction(identifier = "toDouble", normalParams = ["byte"], returnType = "double")
    @JvmStatic
    fun toDoubleFromByte(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Double)
    }

    /** Converts an MCFPP Short value to Double and publishes the converted result. */
    @MNIFunction(identifier = "toDouble", normalParams = ["short"], returnType = "double")
    @JvmStatic
    fun toDoubleFromShort(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Double)
    }

    /** Converts an MCFPP Long value to Double and publishes the converted result. */
    @MNIFunction(identifier = "toDouble", normalParams = ["long"], returnType = "double")
    @JvmStatic
    fun toDoubleFromLong(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Double)
    }

    /** Converts an MCFPP Double value to Double and publishes the converted result. */
    @MNIFunction(identifier = "toDouble", normalParams = ["double"], returnType = "double")
    @JvmStatic
    fun toDoubleFromDouble(context: NativeCallContext) {
        NumericConversions.convert(context, MCFPPNBTType.Double)
    }

    /** Converts an MCFPP Int value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["int"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromInt(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP Float value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["float"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromFloat(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP Byte value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["byte"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromByte(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP Short value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["short"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromShort(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP Long value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["long"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromLong(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP Double value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["double"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromDouble(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP Bool value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["bool"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromBool(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP String value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["string"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromString(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP NBT value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["nbt"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromNBT(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP DataObject value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["DataObject"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromDataObject(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP ByteArray value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["ByteArray"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromByteArray(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP IntArray value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["IntArray"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromIntArray(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

    /** Converts an MCFPP LongArray value to NBT and publishes the converted result. */
    @MNIFunction(identifier = "toNBT", normalParams = ["LongArray"], returnType = "nbt")
    @JvmStatic
    fun toNBTFromLongArray(context: NativeCallContext) {
        NumericConversions.toNBT(context)
    }

}
