package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NumericConversions;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.nbt.*;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.type.*;
import top.mcfpp.util.ValueWrapper;

/** Concrete language overloads; unknown any never has a conversion fallback. */
@top.mcfpp.mni.annotation.NoExternalWrites
public class ConversionData {
    @MNIFunction(normalParams = {"int"}, returnType = "int")
    public static void toInt(MCInt value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Int.INSTANCE));
    }

    @MNIFunction(normalParams = {"float"}, returnType = "int")
    public static void toInt(MCFloat value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Int.INSTANCE));
    }

    @MNIFunction(normalParams = {"byte"}, returnType = "int")
    public static void toInt(MCByte value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Int.INSTANCE));
    }

    @MNIFunction(normalParams = {"short"}, returnType = "int")
    public static void toInt(MCShort value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Int.INSTANCE));
    }

    @MNIFunction(normalParams = {"long"}, returnType = "int")
    public static void toInt(MCLong value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Int.INSTANCE));
    }

    @MNIFunction(normalParams = {"double"}, returnType = "int")
    public static void toInt(MCDouble value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Int.INSTANCE));
    }

    @MNIFunction(normalParams = {"int"}, returnType = "float")
    public static void toFloat(MCInt value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Float.INSTANCE));
    }

    @MNIFunction(normalParams = {"float"}, returnType = "float")
    public static void toFloat(MCFloat value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Float.INSTANCE));
    }

    @MNIFunction(normalParams = {"byte"}, returnType = "float")
    public static void toFloat(MCByte value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Float.INSTANCE));
    }

    @MNIFunction(normalParams = {"short"}, returnType = "float")
    public static void toFloat(MCShort value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Float.INSTANCE));
    }

    @MNIFunction(normalParams = {"long"}, returnType = "float")
    public static void toFloat(MCLong value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Float.INSTANCE));
    }

    @MNIFunction(normalParams = {"double"}, returnType = "float")
    public static void toFloat(MCDouble value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPBaseType.Float.INSTANCE));
    }

    @MNIFunction(normalParams = {"int"}, returnType = "byte")
    public static void toByte(MCInt value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Byte.INSTANCE));
    }

    @MNIFunction(normalParams = {"float"}, returnType = "byte")
    public static void toByte(MCFloat value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Byte.INSTANCE));
    }

    @MNIFunction(normalParams = {"byte"}, returnType = "byte")
    public static void toByte(MCByte value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Byte.INSTANCE));
    }

    @MNIFunction(normalParams = {"short"}, returnType = "byte")
    public static void toByte(MCShort value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Byte.INSTANCE));
    }

    @MNIFunction(normalParams = {"long"}, returnType = "byte")
    public static void toByte(MCLong value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Byte.INSTANCE));
    }

    @MNIFunction(normalParams = {"double"}, returnType = "byte")
    public static void toByte(MCDouble value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Byte.INSTANCE));
    }

    @MNIFunction(normalParams = {"int"}, returnType = "short")
    public static void toShort(MCInt value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Short.INSTANCE));
    }

    @MNIFunction(normalParams = {"float"}, returnType = "short")
    public static void toShort(MCFloat value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Short.INSTANCE));
    }

    @MNIFunction(normalParams = {"byte"}, returnType = "short")
    public static void toShort(MCByte value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Short.INSTANCE));
    }

    @MNIFunction(normalParams = {"short"}, returnType = "short")
    public static void toShort(MCShort value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Short.INSTANCE));
    }

    @MNIFunction(normalParams = {"long"}, returnType = "short")
    public static void toShort(MCLong value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Short.INSTANCE));
    }

    @MNIFunction(normalParams = {"double"}, returnType = "short")
    public static void toShort(MCDouble value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Short.INSTANCE));
    }

    @MNIFunction(normalParams = {"int"}, returnType = "long")
    public static void toLong(MCInt value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Long.INSTANCE));
    }

    @MNIFunction(normalParams = {"float"}, returnType = "long")
    public static void toLong(MCFloat value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Long.INSTANCE));
    }

    @MNIFunction(normalParams = {"byte"}, returnType = "long")
    public static void toLong(MCByte value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Long.INSTANCE));
    }

    @MNIFunction(normalParams = {"short"}, returnType = "long")
    public static void toLong(MCShort value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Long.INSTANCE));
    }

    @MNIFunction(normalParams = {"long"}, returnType = "long")
    public static void toLong(MCLong value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Long.INSTANCE));
    }

    @MNIFunction(normalParams = {"double"}, returnType = "long")
    public static void toLong(MCDouble value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Long.INSTANCE));
    }

    @MNIFunction(normalParams = {"int"}, returnType = "double")
    public static void toDouble(MCInt value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Double.INSTANCE));
    }

    @MNIFunction(normalParams = {"float"}, returnType = "double")
    public static void toDouble(MCFloat value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Double.INSTANCE));
    }

    @MNIFunction(normalParams = {"byte"}, returnType = "double")
    public static void toDouble(MCByte value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Double.INSTANCE));
    }

    @MNIFunction(normalParams = {"short"}, returnType = "double")
    public static void toDouble(MCShort value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Double.INSTANCE));
    }

    @MNIFunction(normalParams = {"long"}, returnType = "double")
    public static void toDouble(MCLong value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Double.INSTANCE));
    }

    @MNIFunction(normalParams = {"double"}, returnType = "double")
    public static void toDouble(MCDouble value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.convert(value, MCFPPNBTType.Double.INSTANCE));
    }

    @MNIFunction(normalParams = {"int"}, returnType = "nbt")
    public static void toNBT(MCInt value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"float"}, returnType = "nbt")
    public static void toNBT(MCFloat value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"byte"}, returnType = "nbt")
    public static void toNBT(MCByte value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"short"}, returnType = "nbt")
    public static void toNBT(MCShort value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"long"}, returnType = "nbt")
    public static void toNBT(MCLong value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"double"}, returnType = "nbt")
    public static void toNBT(MCDouble value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"bool"}, returnType = "nbt")
    public static void toNBT(BaseBool value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"string"}, returnType = "nbt")
    public static void toNBT(MCString value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"nbt"}, returnType = "nbt")
    public static void toNBT(NBTBasedData value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"DataObject"}, returnType = "nbt")
    public static void toNBT(DataTemplateObject value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"ByteArray"}, returnType = "nbt")
    public static void toNBT(NBTByteArray value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"IntArray"}, returnType = "nbt")
    public static void toNBT(NBTIntArray value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

    @MNIFunction(normalParams = {"LongArray"}, returnType = "nbt")
    public static void toNBT(NBTLongArray value, ValueWrapper<Var<?>> result) {
        result.setValue(NumericConversions.toNBT(value));
    }

}
