package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.MCFloat;
import top.mcfpp.core.lang.MCFloatConcrete;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.util.ValueWrapper;

public class MCFloatConcreteData {
    @MNIOperator(paramType = "float", operator = "+", returnType = "float", returnsConstWhenArgsConst = true)
    public static void plus(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.plus(a));
    }

    @MNIOperator(paramType = "float", operator = "-", returnType = "float", returnsConstWhenArgsConst = true)
    public static void minus(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.minus(a));
    }

    @MNIOperator(paramType = "float", operator = "*", returnType = "float", returnsConstWhenArgsConst = true)
    public static void times(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.times(a));
    }

    @MNIOperator(paramType = "float", operator = "/", returnType = "float", returnsConstWhenArgsConst = true)
    public static void div(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.div(a));
    }

    @MNIOperator(paramType = "float", operator = "%", returnType = "float", returnsConstWhenArgsConst = true)
    public static void rem(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.rem(a));
    }

    @MNIOperator(paramType = "float", operator = ">", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isBigger(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isBigger(a));
    }

    @MNIOperator(paramType = "float", operator = "<", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isSmaller(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isSmaller(a));
    }

    @MNIOperator(paramType = "float", operator = "<=", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isSmallerOrEqual(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isSmallerOrEqual(a));
    }

    @MNIOperator(paramType = "float", operator = ">=", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isBiggerOrEqual(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isBiggerOrEqual(a));
    }

    @MNIOperator(paramType = "float", operator = "==", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isEqual(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isEqual(a));
    }

    @MNIOperator(paramType = "float", operator = "!=", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isNotEqual(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isNotEqual(a));
    }
}
