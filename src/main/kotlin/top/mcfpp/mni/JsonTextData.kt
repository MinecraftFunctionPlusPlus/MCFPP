package top.mcfpp.mni

import top.mcfpp.annotations.MNIOperator
import top.mcfpp.backend.NativeTextOperations

/** Implements joining MCFPP text components and strings. */
object JsonTextData {
    /** Joins the receiver text with the supplied text components. */
    @MNIOperator(paramType = "text", returnType = "text", operator = "+", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun plusText(context: NativeCallContext) {
        NativeTextOperations.plusText(context)
    }

    /** Appends the supplied string to the receiver text. */
    @MNIOperator(paramType = "string", returnType = "text", operator = "+", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun plusString(context: NativeCallContext) {
        NativeTextOperations.plusString(context)
    }
}
