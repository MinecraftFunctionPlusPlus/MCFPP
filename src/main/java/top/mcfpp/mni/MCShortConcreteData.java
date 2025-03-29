package top.mcfpp.mni;

import top.mcfpp.annotations.MNIBinaryOperator;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.nbt.MCShort;
import top.mcfpp.core.lang.nbt.MCShortConcrete;
import top.mcfpp.util.ValueWrapper;

public class MCShortConcreteData {
    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "+")
    public static void plus(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.plus(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "-")
    public static void minus(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.minus(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "*")
    public static void times(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.times(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "/")
    public static void div(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.div(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "short", operator = "%")
    public static void rem(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.rem(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = ">")
    public static void isBigger(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isBigger(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = "<")
    public static void isSmaller(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isSmaller(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = "<=")
    public static void isSmallerOrEqual(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isSmallerOrEqual(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = ">=")
    public static void isBiggerOrEqual(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isBiggerOrEqual(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = "==")
    public static void isEqual(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isEqual(b));
    }

    @MNIBinaryOperator(paramType = "short", returnType = "bool", operator = "!=")
    public static void isNotEqual(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isNotEqual(b));
    }
}
