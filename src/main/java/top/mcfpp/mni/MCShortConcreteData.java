package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.nbt.MCShort;
import top.mcfpp.core.lang.nbt.MCShortConcrete;
import top.mcfpp.util.ValueWrapper;

public class MCShortConcreteData {
    @MNIOperator(paramType = "short", returnType = "short", operator = "+")
    public static void plus(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.plus(b));
    }

    @MNIOperator(paramType = "short", returnType = "short", operator = "-")
    public static void minus(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.minus(b));
    }

    @MNIOperator(paramType = "short", returnType = "short", operator = "*")
    public static void times(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.times(b));
    }

    @MNIOperator(paramType = "short", returnType = "short", operator = "/")
    public static void div(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.div(b));
    }

    @MNIOperator(paramType = "short", returnType = "short", operator = "%")
    public static void rem(MCShort b, MCShortConcrete caller, ValueWrapper<MCShort> re){
        re.setValue((MCShort) caller.rem(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = ">")
    public static void isBigger(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isBigger(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = "<")
    public static void isSmaller(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isSmaller(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = "<=")
    public static void isSmallerOrEqual(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isSmallerOrEqual(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = ">=")
    public static void isBiggerOrEqual(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isBiggerOrEqual(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = "==")
    public static void isEqual(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isEqual(b));
    }

    @MNIOperator(paramType = "short", returnType = "bool", operator = "!=")
    public static void isNotEqual(MCShort b, MCShortConcrete caller, ValueWrapper<BaseBool> re){
        re.setValue((BaseBool) caller.isNotEqual(b));
    }
}
