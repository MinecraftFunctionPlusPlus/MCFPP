package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIOperator
import top.mcfpp.backend.NativeTimeOperations
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites

/** Implements arithmetic and comparisons for Time values stored in game ticks. */
@NoExternalWrites
object TimeData {

    /** Adds the receiver and operand. */
    @MNIOperator(operator = "+",paramType = "Time", returnType = "Time")
    @JvmStatic
    fun plus(context: NativeCallContext) {
        NativeTimeOperations.arithmetic(context, "+")
    }

    /** Subtracts the operand from the receiver. */
    @MNIOperator(operator = "-", paramType = "Time", returnType = "Time")
    @JvmStatic
    fun minus(context: NativeCallContext) {
        NativeTimeOperations.arithmetic(context, "-")
    }

    /** Multiplies the receiver by the operand. */
    @MNIOperator(operator = "*", paramType = "Time", returnType = "Time")
    @JvmStatic
    fun times(context: NativeCallContext) {
        NativeTimeOperations.arithmetic(context, "*")
    }

    /** Divides the receiver by the operand using its MCFPP numeric type. */
    @MNIOperator(operator = "/", paramType = "Time", returnType = "Time")
    @JvmStatic
    fun div(context: NativeCallContext) {
        NativeTimeOperations.arithmetic(context, "/")
    }

    /** Computes the receiver remainder after division by the operand. */
    @MNIOperator(operator = "%", paramType = "Time", returnType = "Time")
    @JvmStatic
    fun rem(context: NativeCallContext) {
        NativeTimeOperations.arithmetic(context, "%")
    }

    /** Tests whether the receiver is greater than the operand. */
    @MNIOperator(operator = ">", paramType = "Time", returnType = "bool")
    @JvmStatic
    fun isBigger(context: NativeCallContext) {
        NativeTimeOperations.comparison(context, ">")
    }

    /** Tests whether the receiver is less than the operand. */
    @MNIOperator(operator = "<", paramType = "Time", returnType = "bool")
    @JvmStatic
    fun isSmaller(context: NativeCallContext) {
        NativeTimeOperations.comparison(context, "<")
    }

    /** Tests whether the receiver is less than or equal to the operand. */
    @MNIOperator(operator = "<=", paramType = "Time", returnType = "bool")
    @JvmStatic
    fun isSmallerOrEqual(context: NativeCallContext) {
        NativeTimeOperations.comparison(context, "<=")
    }

    /** Tests whether the receiver is greater than or equal to the operand. */
    @MNIOperator(operator = ">=", paramType = "Time", returnType = "bool")
    @JvmStatic
    fun isBiggerOrEqual(context: NativeCallContext) {
        NativeTimeOperations.comparison(context, ">=")
    }

    /** Tests whether the receiver equals the operand. */
    @MNIOperator(operator = "==", paramType = "Time", returnType = "bool")
    @JvmStatic
    fun isEqual(context: NativeCallContext) {
        NativeTimeOperations.comparison(context, "==")
    }

    /** Tests whether the receiver differs from the operand. */
    @MNIOperator(operator = "!=", paramType = "Time", returnType = "bool")
    @JvmStatic
    fun isNotEqual(context: NativeCallContext) {
        NativeTimeOperations.comparison(context, "!=")
    }

    /** Tests whether the receiver belongs to the supplied numeric range. */
    @MNIOperator(operator = "~=", paramType = "range", returnType = "bool")
    @JvmStatic
    fun inRange(context: NativeCallContext) {
        NativeTimeOperations.inRange(context)
    }
}
