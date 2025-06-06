package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.obj.TypeDataTemplateObject;
import top.mcfpp.core.lang.RangeVar;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.util.ValueWrapper;

public class TimeData {
    
    @MNIOperator(operator = "+",paramType = "Time", returnType = "Time")
    public static void plus(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = caller.clone();
        qwq.setDelegateVar(caller.getDelegateVar().plus(a.getDelegateVar()));
        returnValue.setValue(qwq);
    }

    @MNIOperator(operator = "-", paramType = "Time", returnType = "Time")
    public static void minus(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = caller.clone();
        qwq.setDelegateVar(caller.getDelegateVar().minus(a.getDelegateVar()));
        returnValue.setValue(qwq);
    }

    @MNIOperator(operator = "*", paramType = "Time", returnType = "Time")
    public static void times(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = caller.clone();
        qwq.setDelegateVar(caller.getDelegateVar().times(a.getDelegateVar()));
        returnValue.setValue(qwq);
    }

    @MNIOperator(operator = "/", paramType = "Time", returnType = "Time")
    public static void div(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = caller.clone();
        qwq.setDelegateVar(caller.getDelegateVar().div(a.getDelegateVar()));
        returnValue.setValue(qwq);
    }

    @MNIOperator(operator = "%", paramType = "Time", returnType = "Time")
    public static void rem(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<TypeDataTemplateObject> returnValue) {
        var qwq = caller.clone();
        qwq.setDelegateVar(caller.getDelegateVar().rem(a.getDelegateVar()));
        returnValue.setValue(qwq);
    }

    @MNIOperator(operator = ">", paramType = "Time", returnType = "bool")
    public static void isBigger(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.getDelegateVar().isBigger(a.getDelegateVar()));
    }

    @MNIOperator(operator = "<", paramType = "Time", returnType = "bool")
    public static void isSmaller(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.getDelegateVar().isSmaller(a.getDelegateVar()));
    }

    @MNIOperator(operator = "<=", paramType = "Time", returnType = "bool")
    public static void isSmallerOrEqual(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.getDelegateVar().isSmallerOrEqual(a.getDelegateVar()));
    }

    @MNIOperator(operator = ">=", paramType = "Time", returnType = "bool")
    public static void isBiggerOrEqual(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.getDelegateVar().isBiggerOrEqual(a.getDelegateVar()));
    }

    @MNIOperator(operator = "==", paramType = "Time", returnType = "bool")
    public static void isEqual(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.getDelegateVar().isEqual(a.getDelegateVar()));
    }

    @MNIOperator(operator = "!=", paramType = "Time", returnType = "bool")
    public static void isNotEqual(TypeDataTemplateObject a, TypeDataTemplateObject caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.getDelegateVar().isNotEqual(a.getDelegateVar()));
    }

    @MNIOperator(operator = "~=", paramType = "range", returnType = "bool")
    public static void inRange(RangeVar a, TypeDataTemplateObject caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.getDelegateVar().inRange(a));
    }
}
