package top.mcfpp.mni;

import kotlin.Pair;
import top.mcfpp.annotations.MNIBinaryOperator;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.bool.ScoreBoolConcrete;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.util.TempPool;
import top.mcfpp.util.ValueWrapper;

public class MCIntConcreteData {

    @MNIFunction(caller = "int", returnType = "text", override = true)
    public static void toText(MCIntConcrete caller, ValueWrapper<JsonTextConcrete> returnValue) {
        var l = new ListChatComponent();
        l.getComponents().add(new PlainChatComponent(caller.getValue().toString()));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

    // 算术运算方法
    @MNIBinaryOperator(operator = "+", paramType = "int", returnType = "int")
    public static void plus(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.plus(a));
    }

    @MNIBinaryOperator(operator = "-", paramType = "int", returnType = "int")
    public static void minus(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.minus(a));
    }

    @MNIBinaryOperator(operator = "*", paramType = "int", returnType = "int")
    public static void times(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.times(a));
    }

    @MNIBinaryOperator(operator = "/", paramType = "int", returnType = "int")
    public static void div(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.div(a));
    }

    @MNIBinaryOperator(operator = "%", paramType = "int", returnType = "int")
    public static void rem(MCInt a, MCIntConcrete caller, ValueWrapper<MCInt> returnValue) {
        returnValue.setValue((MCInt) caller.rem(a));
    }

    // 比较运算方法
    @MNIBinaryOperator(operator = ">", paramType = "int", returnType = "bool")
    public static void isBigger(MCInt a, MCIntConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isBigger(a));
    }

    @MNIBinaryOperator(operator = "<", paramType = "int", returnType = "bool")
    public static void isSmaller(MCInt a, MCIntConcrete caller, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) caller.isSmaller(a));
    }

    // 范围检查方法
    @MNIBinaryOperator(operator = "~=", paramType = "range", returnType = "bool")
    public static void inRange(MCInt a, RangeVar b, ValueWrapper<BaseBool> returnValue) {
        returnValue.setValue((BaseBool) b.inRange(a));
    }

}
