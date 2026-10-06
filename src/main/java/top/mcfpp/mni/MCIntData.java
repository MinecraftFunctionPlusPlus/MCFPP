package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.backend.NativeOperatorOperations;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.JsonTextConcrete;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.MCIntConcrete;
import top.mcfpp.lib.ListChatComponent;
import top.mcfpp.lib.ScoreChatComponent;
import top.mcfpp.lib.PlainChatComponent;
import top.mcfpp.util.ValueWrapper;

@top.mcfpp.mni.annotation.NoExternalWrites
public class MCIntData {

    @MNIFunction(caller = "int", returnType = "text", override = true)
    public static void toText(MCInt caller, ValueWrapper<JsonTextConcrete> returnValue) {
        if(caller instanceof MCIntConcrete intConcrete){
            var text = new ListChatComponent();
            text.append(new PlainChatComponent(intConcrete.getValue().toString()));
            returnValue.setValue(new JsonTextConcrete(text, "re"));
            return;
        }
        var l = new ListChatComponent();
        l.append(new ScoreChatComponent(caller));
        returnValue.setValue(new JsonTextConcrete(l, "re"));
    }

    @MNIOperator(operator = "+",paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void plus(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, "+");
    }
    
    @MNIOperator(operator = "-", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void minus(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, "-");
    }

    @MNIOperator(operator = "*", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void times(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, "*");
    }

    @MNIOperator(operator = "/", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void div(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, "/");
    }

    @MNIOperator(operator = "%", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    public static void rem(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, "%");
    }

    @MNIOperator(operator = ">", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isBigger(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, ">");
    }

    @MNIOperator(operator = "<", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isSmaller(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, "<");
    }

    @MNIOperator(operator = "<=", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isSmallerOrEqual(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, "<=");
    }

    @MNIOperator(operator = ">=", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isBiggerOrEqual(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, ">=");
    }

    @MNIOperator(operator = "==", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isEqual(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, "==");
    }

    @MNIOperator(operator = "!=", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isNotEqual(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.integer(context, "!=");
    }

    @MNIOperator(operator = "~=", paramType = "range", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void inRange(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.inRange(context);
    }

}
