package top.mcfpp.mni.resource

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativePredicateOperations
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites

/** Implements MCFPP LootTablePredicate resource operations. */
@NoExternalWrites
object LootTablePredicateObjectData {

    /** Creates a predicate resource value from its identifier. */
    @MNIFunction(normalParams = ["string"], returnType = "Predicate")
    @JvmStatic
    fun of(context: NativeCallContext) {
        NativePredicateOperations.resourcePredicate(context)
    }

}
