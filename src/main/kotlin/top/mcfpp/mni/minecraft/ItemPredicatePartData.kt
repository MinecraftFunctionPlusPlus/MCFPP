package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIOperator
import top.mcfpp.backend.NativePredicateOperations
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites

/** Combines item predicate parts with Boolean OR. */
object ItemPredicatePartData {

    /** Combines two item predicate parts with Boolean OR. */
    @MNIOperator(paramType = "ItemPredicatePart", operator = "|", returnType = "ItemPredicatePart")
    @NoExternalWrites
    @JvmStatic
    fun Or(context: NativeCallContext) {
        NativePredicateOperations.orPart(context)
    }

}
