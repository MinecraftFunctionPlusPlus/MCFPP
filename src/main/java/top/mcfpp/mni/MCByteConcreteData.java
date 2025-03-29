package top.mcfpp.mni;

import top.mcfpp.annotations.MNIBinaryOperator;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.nbt.MCByte;
import top.mcfpp.core.lang.nbt.MCByteConcrete;
import top.mcfpp.util.ValueWrapper;

public class MCByteConcreteData {
    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "+")
    public static void plus(MCByte b, MCByteConcrete caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.plus(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "-")
    public static void minus(MCByte b, MCByteConcrete caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.minus(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "*")
    public static void times(MCByte b, MCByteConcrete caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.times(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "/")
    public static void div(MCByte b, MCByteConcrete caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.div(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "%")
    public static void rem(MCByte b, MCByteConcrete caller, ValueWrapper<MCByte> re){
        re.setValue((MCByte) caller.rem(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = ">")
    public static void isBigger(MCByte b, MCByteConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isBigger(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = "<")
    public static void isSmaller(MCByte b, MCByteConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isSmaller(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = "<=")
    public static void isSmallerOrEqual(MCByte b, MCByteConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isSmallerOrEqual(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = ">=")
    public static void isBiggerOrEqual(MCByte b, MCByteConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isBiggerOrEqual(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = "==")
    public static void isEqual(MCByte b, MCByteConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isEqual(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = "!=")
    public static void isNotEqual(MCByte b, MCByteConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isNotEqual(b));
    }
}
