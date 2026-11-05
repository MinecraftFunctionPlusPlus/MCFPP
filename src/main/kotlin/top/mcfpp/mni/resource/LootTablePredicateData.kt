package top.mcfpp.mni.resource

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativePredicateOperations
import top.mcfpp.mni.NativeCallContext

/** Implements MCFPP LootTablePredicate resource operations. */
object LootTablePredicateData {

    /** Tests whether the resource predicate passes at the current execution context. */
    @MNIFunction(caller = "Predicate", returnType = "bool")
    @JvmStatic
    fun pass(context: NativeCallContext) {
        NativePredicateOperations.pass(context)
    }

    /** Tests whether the resource predicate fails at the current execution context. */
    @MNIFunction(caller = "Predicate", returnType = "bool")
    @JvmStatic
    fun fail(context: NativeCallContext) {
        NativePredicateOperations.fail(context)
    }
}
