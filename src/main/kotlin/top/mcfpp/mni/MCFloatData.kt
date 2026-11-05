package top.mcfpp.mni

import top.mcfpp.annotations.MNIOperator
import top.mcfpp.backend.NativeOperatorOperations

/** Implements MCFPP float arithmetic and comparisons. */
@top.mcfpp.mni.annotation.NoExternalWrites
object MCFloatData {
    /** Adds the receiver and operand. */
    @MNIOperator(paramType = "float", operator = "+", returnType = "float", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun plus(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, "+")
    }

    /** Subtracts the operand from the receiver. */
    @MNIOperator(paramType = "float", operator = "-", returnType = "float", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun minus(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, "-")
    }

    /** Multiplies the receiver by the operand. */
    @MNIOperator(paramType = "float", operator = "*", returnType = "float", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun times(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, "*")
    }

    /** Divides the receiver by the operand using its MCFPP numeric type. */
    @MNIOperator(paramType = "float", operator = "/", returnType = "float", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun div(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, "/")
    }

    /** Computes the receiver remainder after division by the operand. */
    @MNIOperator(paramType = "float", operator = "%", returnType = "float", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun rem(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, "%")
    }

    /** Tests whether the receiver is greater than the operand. */
    @MNIOperator(paramType = "float", operator = ">", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isBigger(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, ">")
    }

    /** Tests whether the receiver is less than the operand. */
    @MNIOperator(paramType = "float", operator = "<", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isSmaller(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, "<")
    }

    /** Tests whether the receiver is less than or equal to the operand. */
    @MNIOperator(paramType = "float", operator = "<=", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isSmallerOrEqual(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, "<=")
    }

    /** Tests whether the receiver is greater than or equal to the operand. */
    @MNIOperator(paramType = "float", operator = ">=", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isBiggerOrEqual(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, ">=")
    }

    /** Tests whether the receiver equals the operand. */
    @MNIOperator(paramType = "float", operator = "==", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isEqual(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, "==")
    }

    /** Tests whether the receiver differs from the operand. */
    @MNIOperator(paramType = "float", operator = "!=", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isNotEqual(context: NativeCallContext) {
        NativeOperatorOperations.floating(context, "!=")
    }
}
