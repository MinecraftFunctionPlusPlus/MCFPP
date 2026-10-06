package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.backend.NativeOperatorOperations;

@top.mcfpp.mni.annotation.NoExternalWrites
public class MCBoolData {

    @MNIOperator(operator = "==", paramType = "bool", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isEqual(NativeCallContext context){
        NativeOperatorOperations.INSTANCE.logical(context, "==");
    }

    @MNIOperator(operator = "!=" ,paramType = "bool", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isNotEqual(NativeCallContext context){
        NativeOperatorOperations.INSTANCE.logical(context, "!=");
    }

    @MNIOperator(operator = "||", paramType = "bool", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void or(NativeCallContext context){
        NativeOperatorOperations.INSTANCE.logical(context, "||");
    }

    @MNIOperator(operator = "&&", paramType = "bool", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void and(NativeCallContext context){
        NativeOperatorOperations.INSTANCE.logical(context, "&&");
    }

    @MNIOperator(operator = "!", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void negation(NativeCallContext context){
        NativeOperatorOperations.INSTANCE.logical(context, "!");
    }
}
