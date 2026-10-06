package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.backend.NativeOperatorOperations;

@top.mcfpp.mni.annotation.NoExternalWrites
public class MCFloatData {
    @MNIOperator(paramType = "float", operator = "+", returnType = "float", returnsConstWhenArgsConst = true)
    public static void plus(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, "+");
    }

    @MNIOperator(paramType = "float", operator = "-", returnType = "float", returnsConstWhenArgsConst = true)
    public static void minus(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, "-");
    }

    @MNIOperator(paramType = "float", operator = "*", returnType = "float", returnsConstWhenArgsConst = true)
    public static void times(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, "*");
    }

    @MNIOperator(paramType = "float", operator = "/", returnType = "float", returnsConstWhenArgsConst = true)
    public static void div(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, "/");
    }

    @MNIOperator(paramType = "float", operator = "%", returnType = "float", returnsConstWhenArgsConst = true)
    public static void rem(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, "%");
    }

    @MNIOperator(paramType = "float", operator = ">", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isBigger(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, ">");
    }

    @MNIOperator(paramType = "float", operator = "<", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isSmaller(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, "<");
    }

    @MNIOperator(paramType = "float", operator = "<=", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isSmallerOrEqual(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, "<=");
    }

    @MNIOperator(paramType = "float", operator = ">=", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isBiggerOrEqual(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, ">=");
    }

    @MNIOperator(paramType = "float", operator = "==", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isEqual(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, "==");
    }

    @MNIOperator(paramType = "float", operator = "!=", returnType = "bool", returnsConstWhenArgsConst = true)
    public static void isNotEqual(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.floating(context, "!=");
    }
}
