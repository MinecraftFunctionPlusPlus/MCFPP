package top.mcfpp.mni

import top.mcfpp.annotations.MNIOperator
import top.mcfpp.backend.NativeOperatorOperations
import top.mcfpp.annotations.MNIFunction

/** Implements MCFPP int arithmetic, comparisons, range tests, and text conversion. */
@top.mcfpp.mni.annotation.NoExternalWrites
object MCIntData {

    /** Creates a text component that displays the receiver value. */
    @MNIFunction(caller = "int", returnType = "text", `override` = true)
    @JvmStatic
    fun toText(context: NativeCallContext) {
        top.mcfpp.backend.NativeTextOperations.integer(context)
    }

    /** Adds the receiver and operand. */
    @MNIOperator(operator = "+",paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun plus(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, "+")
    }

    /** Subtracts the operand from the receiver. */
    @MNIOperator(operator = "-", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun minus(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, "-")
    }

    /** Multiplies the receiver by the operand. */
    @MNIOperator(operator = "*", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun times(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, "*")
    }

    /** Divides the receiver by the operand using its MCFPP numeric type. */
    @MNIOperator(operator = "/", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun div(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, "/")
    }

    /** Computes the receiver remainder after division by the operand. */
    @MNIOperator(operator = "%", paramType = "int", returnType = "int", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun rem(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, "%")
    }

    /** Tests whether the receiver is greater than the operand. */
    @MNIOperator(operator = ">", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isBigger(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, ">")
    }

    /** Tests whether the receiver is less than the operand. */
    @MNIOperator(operator = "<", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isSmaller(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, "<")
    }

    /** Tests whether the receiver is less than or equal to the operand. */
    @MNIOperator(operator = "<=", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isSmallerOrEqual(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, "<=")
    }

    /** Tests whether the receiver is greater than or equal to the operand. */
    @MNIOperator(operator = ">=", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isBiggerOrEqual(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, ">=")
    }

    /** Tests whether the receiver equals the operand. */
    @MNIOperator(operator = "==", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isEqual(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, "==")
    }

    /** Tests whether the receiver differs from the operand. */
    @MNIOperator(operator = "!=", paramType = "int", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isNotEqual(context: NativeCallContext) {
        NativeOperatorOperations.integer(context, "!=")
    }

    /** Tests whether the receiver belongs to the supplied numeric range. */
    @MNIOperator(operator = "~=", paramType = "range", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun inRange(context: NativeCallContext) {
        NativeOperatorOperations.inRange(context)
    }

}
