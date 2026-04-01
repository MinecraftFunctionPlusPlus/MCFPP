package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.util.ValueWrapper;

public class MCIntConcreteData {

    @MNIFunction(caller = "int", returnType = "text", override = true)
    public static void toText(MCIntConcrete caller, ValueWrapper<JsonTextConcrete> returnValue) {
        var l = new ListChatComponent();
        l.getComponents().add(new PlainChatComponent(caller.getValue().toString()));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

    // 算术运算方法
    @MNIOperator(operator = "+", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void plus(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.plus(a));
    }

    @MNIOperator(operator = "-", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void minus(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.minus(a));
    }

    @MNIOperator(operator = "*", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void times(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.times(a));
    }

    @MNIOperator(operator = "/", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void div(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.div(a));
    }

    @MNIOperator(operator = "%", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void rem(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.rem(a));
    }

    // 比较运算方法
    @MNIOperator(operator = ">", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isBigger(MCInt a, MCIntConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isBigger(a));
    }

    @MNIOperator(operator = "<", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isSmaller(MCInt a, MCIntConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isSmaller(a));
    }

    @MNIOperator(operator = "<=", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isSmallerOrEqual(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isSmallerOrEqual(a));
    }

    @MNIOperator(operator = ">=", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isBiggerOrEqual(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isBiggerOrEqual(a));
    }

    @MNIOperator(operator = "==", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isEqual(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isEqual(a));
    }

    @MNIOperator(operator = "!=", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isNotEqual(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isNotEqual(a));
    }

    // 范围检查方法
    @MNIOperator(operator = "~=", paramType = "range", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void inRange(MCInt a, RangeVar b, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) b.inRange(a));
    }

}
