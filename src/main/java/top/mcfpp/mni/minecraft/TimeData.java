package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.backend.NativeTimeOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.mni.annotation.NoExternalWrites;

@NoExternalWrites
public class TimeData {
    
    @MNIOperator(operator = "+",paramType = "Time", returnType = "Time")
    public static void plus(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.arithmetic(context, "+");
    }

    @MNIOperator(operator = "-", paramType = "Time", returnType = "Time")
    public static void minus(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.arithmetic(context, "-");
    }

    @MNIOperator(operator = "*", paramType = "Time", returnType = "Time")
    public static void times(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.arithmetic(context, "*");
    }

    @MNIOperator(operator = "/", paramType = "Time", returnType = "Time")
    public static void div(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.arithmetic(context, "/");
    }

    @MNIOperator(operator = "%", paramType = "Time", returnType = "Time")
    public static void rem(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.arithmetic(context, "%");
    }

    @MNIOperator(operator = ">", paramType = "Time", returnType = "bool")
    public static void isBigger(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.comparison(context, ">");
    }

    @MNIOperator(operator = "<", paramType = "Time", returnType = "bool")
    public static void isSmaller(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.comparison(context, "<");
    }

    @MNIOperator(operator = "<=", paramType = "Time", returnType = "bool")
    public static void isSmallerOrEqual(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.comparison(context, "<=");
    }

    @MNIOperator(operator = ">=", paramType = "Time", returnType = "bool")
    public static void isBiggerOrEqual(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.comparison(context, ">=");
    }

    @MNIOperator(operator = "==", paramType = "Time", returnType = "bool")
    public static void isEqual(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.comparison(context, "==");
    }

    @MNIOperator(operator = "!=", paramType = "Time", returnType = "bool")
    public static void isNotEqual(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.comparison(context, "!=");
    }

    @MNIOperator(operator = "~=", paramType = "range", returnType = "bool")
    public static void inRange(NativeCallContext context) {
        NativeTimeOperations.INSTANCE.inRange(context);
    }
}
