package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.ScoreChatComponent;
import top.mcfpp.util.ValueWrapper;

public class MCIntData {

    @MNIFunction(caller = "int", returnType = "text", override = true)
    public static void toText(MCInt caller, ValueWrapper<JsonTextConcrete> returnValue) {
        if(caller instanceof MCIntConcrete intConcrete){
            MCIntConcreteData.toText(intConcrete, returnValue);
            return;
        }
        var l = new ListChatComponent();
        l.append(new ScoreChatComponent(caller));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

    @MNIOperator(operator = "+",paramType = "int", returnType = "int")
    public static void plus(MCInt a, MCInt caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.plus(a));
    }
    
    @MNIOperator(operator = "-", paramType = "int", returnType = "int")
    public static void minus(MCInt a, MCInt caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.minus(a));
    }

    @MNIOperator(operator = "*", paramType = "int", returnType = "int")
    public static void times(MCInt a, MCInt caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.times(a));
    }

    @MNIOperator(operator = "/", paramType = "int", returnType = "int")
    public static void div(MCInt a, MCInt caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.div(a));
    }

    @MNIOperator(operator = "%", paramType = "int", returnType = "int")
    public static void rem(MCInt a, MCInt caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.rem(a));
    }

    @MNIOperator(operator = ">", paramType = "int", returnType = "bool")
    public static void isBigger(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isBigger(a));
    }

    @MNIOperator(operator = "<", paramType = "int", returnType = "bool")
    public static void isSmaller(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isSmaller(a));
    }

    @MNIOperator(operator = "<=", paramType = "int", returnType = "bool")
    public static void isSmallerOrEqual(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isSmallerOrEqual(a));
    }

    @MNIOperator(operator = ">=", paramType = "int", returnType = "bool")
    public static void isBiggerOrEqual(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isBiggerOrEqual(a));
    }

    @MNIOperator(operator = "==", paramType = "int", returnType = "bool")
    public static void isEqual(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isEqual(a));
    }

    @MNIOperator(operator = "!=", paramType = "int", returnType = "bool")
    public static void isNotEqual(MCInt a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isNotEqual(a));
    }

    @MNIOperator(operator = "~=", paramType = "range", returnType = "bool")
    public static void inRange(RangeVar a, MCInt caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.inRange(a));
    }

}
