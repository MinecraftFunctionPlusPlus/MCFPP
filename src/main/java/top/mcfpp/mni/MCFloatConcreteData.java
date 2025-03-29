package top.mcfpp.mni;

import top.mcfpp.annotations.MNIBinaryOperator;
import top.mcfpp.core.lang.MCFloat;
import top.mcfpp.core.lang.MCFloatConcrete;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.util.ValueWrapper;

public class MCFloatConcreteData {
    @MNIBinaryOperator(paramType = "float", operator = "+", returnType = "float")
    public static void plus(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.plus(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = "-", returnType = "float")
    public static void minus(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.minus(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = "*", returnType = "float")
    public static void times(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.times(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = "/", returnType = "float")
    public static void div(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.div(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = "%", returnType = "float")
    public static void rem(MCFloat a, MCFloatConcrete caller, ValueWrapper<MCFloat> returnValue) {
        returnValue.setValue((MCFloat) caller.rem(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = ">", returnType = "bool")
    public static void isBigger(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isBigger(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = "<", returnType = "bool")
    public static void isSmaller(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isSmaller(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = "<=", returnType = "bool")
    public static void isSmallerOrEqual(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isSmallerOrEqual(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = ">=", returnType = "bool")
    public static void isBiggerOrEqual(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isBiggerOrEqual(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = "==", returnType = "bool")
    public static void isEqual(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isEqual(a));
    }

    @MNIBinaryOperator(paramType = "float", operator = "!=", returnType = "bool")
    public static void isNotEqual(MCFloat a, MCFloatConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isNotEqual(a));
    }
}
