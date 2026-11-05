package top.mcfpp.mni

import top.mcfpp.annotations.MNIOperator
import top.mcfpp.backend.NativeOperatorOperations

/** Implements MCFPP Boolean operators. */
@top.mcfpp.mni.annotation.NoExternalWrites
object MCBoolData {

    /** Tests whether the receiver equals the operand. */
    @MNIOperator(operator = "==", paramType = "bool", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isEqual(context: NativeCallContext) {
        NativeOperatorOperations.logical(context, "==")
    }

    /** Tests whether the receiver differs from the operand. */
    @MNIOperator(operator = "!=" ,paramType = "bool", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun isNotEqual(context: NativeCallContext) {
        NativeOperatorOperations.logical(context, "!=")
    }

    /** Computes Boolean OR for the receiver and operand. */
    @MNIOperator(operator = "||", paramType = "bool", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun or(context: NativeCallContext) {
        NativeOperatorOperations.logical(context, "||")
    }

    /** Computes Boolean AND for the receiver and operand. */
    @MNIOperator(operator = "&&", paramType = "bool", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun and(context: NativeCallContext) {
        NativeOperatorOperations.logical(context, "&&")
    }

    /** Reverses the receiver Boolean value. */
    @MNIOperator(operator = "!", returnType = "bool", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun negation(context: NativeCallContext) {
        NativeOperatorOperations.logical(context, "!")
    }
}
