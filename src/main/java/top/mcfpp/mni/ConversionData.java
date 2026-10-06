package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NumericConversions;
import top.mcfpp.type.*;

/** Concrete language overloads; unknown any never has a conversion fallback. */
@top.mcfpp.mni.annotation.NoExternalWrites
public class ConversionData {
    @MNIFunction(identifier = "toInt", normalParams = {"int"}, returnType = "int")
    public static void toIntFromInt(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Int.INSTANCE);
    }

    @MNIFunction(identifier = "toInt", normalParams = {"float"}, returnType = "int")
    public static void toIntFromFloat(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Int.INSTANCE);
    }

    @MNIFunction(identifier = "toInt", normalParams = {"byte"}, returnType = "int")
    public static void toIntFromByte(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Int.INSTANCE);
    }

    @MNIFunction(identifier = "toInt", normalParams = {"short"}, returnType = "int")
    public static void toIntFromShort(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Int.INSTANCE);
    }

    @MNIFunction(identifier = "toInt", normalParams = {"long"}, returnType = "int")
    public static void toIntFromLong(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Int.INSTANCE);
    }

    @MNIFunction(identifier = "toInt", normalParams = {"double"}, returnType = "int")
    public static void toIntFromDouble(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Int.INSTANCE);
    }

    @MNIFunction(identifier = "toFloat", normalParams = {"int"}, returnType = "float")
    public static void toFloatFromInt(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Float.INSTANCE);
    }

    @MNIFunction(identifier = "toFloat", normalParams = {"float"}, returnType = "float")
    public static void toFloatFromFloat(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Float.INSTANCE);
    }

    @MNIFunction(identifier = "toFloat", normalParams = {"byte"}, returnType = "float")
    public static void toFloatFromByte(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Float.INSTANCE);
    }

    @MNIFunction(identifier = "toFloat", normalParams = {"short"}, returnType = "float")
    public static void toFloatFromShort(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Float.INSTANCE);
    }

    @MNIFunction(identifier = "toFloat", normalParams = {"long"}, returnType = "float")
    public static void toFloatFromLong(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Float.INSTANCE);
    }

    @MNIFunction(identifier = "toFloat", normalParams = {"double"}, returnType = "float")
    public static void toFloatFromDouble(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPBaseType.Float.INSTANCE);
    }

    @MNIFunction(identifier = "toByte", normalParams = {"int"}, returnType = "byte")
    public static void toByteFromInt(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Byte.INSTANCE);
    }

    @MNIFunction(identifier = "toByte", normalParams = {"float"}, returnType = "byte")
    public static void toByteFromFloat(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Byte.INSTANCE);
    }

    @MNIFunction(identifier = "toByte", normalParams = {"byte"}, returnType = "byte")
    public static void toByteFromByte(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Byte.INSTANCE);
    }

    @MNIFunction(identifier = "toByte", normalParams = {"short"}, returnType = "byte")
    public static void toByteFromShort(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Byte.INSTANCE);
    }

    @MNIFunction(identifier = "toByte", normalParams = {"long"}, returnType = "byte")
    public static void toByteFromLong(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Byte.INSTANCE);
    }

    @MNIFunction(identifier = "toByte", normalParams = {"double"}, returnType = "byte")
    public static void toByteFromDouble(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Byte.INSTANCE);
    }

    @MNIFunction(identifier = "toShort", normalParams = {"int"}, returnType = "short")
    public static void toShortFromInt(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Short.INSTANCE);
    }

    @MNIFunction(identifier = "toShort", normalParams = {"float"}, returnType = "short")
    public static void toShortFromFloat(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Short.INSTANCE);
    }

    @MNIFunction(identifier = "toShort", normalParams = {"byte"}, returnType = "short")
    public static void toShortFromByte(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Short.INSTANCE);
    }

    @MNIFunction(identifier = "toShort", normalParams = {"short"}, returnType = "short")
    public static void toShortFromShort(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Short.INSTANCE);
    }

    @MNIFunction(identifier = "toShort", normalParams = {"long"}, returnType = "short")
    public static void toShortFromLong(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Short.INSTANCE);
    }

    @MNIFunction(identifier = "toShort", normalParams = {"double"}, returnType = "short")
    public static void toShortFromDouble(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Short.INSTANCE);
    }

    @MNIFunction(identifier = "toLong", normalParams = {"int"}, returnType = "long")
    public static void toLongFromInt(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Long.INSTANCE);
    }

    @MNIFunction(identifier = "toLong", normalParams = {"float"}, returnType = "long")
    public static void toLongFromFloat(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Long.INSTANCE);
    }

    @MNIFunction(identifier = "toLong", normalParams = {"byte"}, returnType = "long")
    public static void toLongFromByte(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Long.INSTANCE);
    }

    @MNIFunction(identifier = "toLong", normalParams = {"short"}, returnType = "long")
    public static void toLongFromShort(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Long.INSTANCE);
    }

    @MNIFunction(identifier = "toLong", normalParams = {"long"}, returnType = "long")
    public static void toLongFromLong(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Long.INSTANCE);
    }

    @MNIFunction(identifier = "toLong", normalParams = {"double"}, returnType = "long")
    public static void toLongFromDouble(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Long.INSTANCE);
    }

    @MNIFunction(identifier = "toDouble", normalParams = {"int"}, returnType = "double")
    public static void toDoubleFromInt(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Double.INSTANCE);
    }

    @MNIFunction(identifier = "toDouble", normalParams = {"float"}, returnType = "double")
    public static void toDoubleFromFloat(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Double.INSTANCE);
    }

    @MNIFunction(identifier = "toDouble", normalParams = {"byte"}, returnType = "double")
    public static void toDoubleFromByte(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Double.INSTANCE);
    }

    @MNIFunction(identifier = "toDouble", normalParams = {"short"}, returnType = "double")
    public static void toDoubleFromShort(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Double.INSTANCE);
    }

    @MNIFunction(identifier = "toDouble", normalParams = {"long"}, returnType = "double")
    public static void toDoubleFromLong(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Double.INSTANCE);
    }

    @MNIFunction(identifier = "toDouble", normalParams = {"double"}, returnType = "double")
    public static void toDoubleFromDouble(NativeCallContext context) {
        NumericConversions.INSTANCE.convert(context, MCFPPNBTType.Double.INSTANCE);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"int"}, returnType = "nbt")
    public static void toNBTFromInt(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"float"}, returnType = "nbt")
    public static void toNBTFromFloat(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"byte"}, returnType = "nbt")
    public static void toNBTFromByte(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"short"}, returnType = "nbt")
    public static void toNBTFromShort(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"long"}, returnType = "nbt")
    public static void toNBTFromLong(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"double"}, returnType = "nbt")
    public static void toNBTFromDouble(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"bool"}, returnType = "nbt")
    public static void toNBTFromBool(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"string"}, returnType = "nbt")
    public static void toNBTFromString(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"nbt"}, returnType = "nbt")
    public static void toNBTFromNBT(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"DataObject"}, returnType = "nbt")
    public static void toNBTFromDataObject(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"ByteArray"}, returnType = "nbt")
    public static void toNBTFromByteArray(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"IntArray"}, returnType = "nbt")
    public static void toNBTFromIntArray(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

    @MNIFunction(identifier = "toNBT", normalParams = {"LongArray"}, returnType = "nbt")
    public static void toNBTFromLongArray(NativeCallContext context) {
        NumericConversions.INSTANCE.toNBT(context);
    }

}
