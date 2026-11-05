package top.mcfpp.mni

import top.mcfpp.annotations.MNIOperator
import top.mcfpp.backend.NativeOperatorOperations
import top.mcfpp.mni.annotation.NoExternalWrites

/** Implements union types for MCFPP type values. */
@NoExternalWrites
object MCFPPTypeData {

    /** Returns a type that accepts values of either operand type. */
    @MNIOperator(operator = "|", paramType = "type", returnType = "type", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun union(context: NativeCallContext) {
        NativeOperatorOperations.unionType(context)
    }
}
