package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativePredicateOperations
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.WritesReceiver

/** Adds component and count conditions to an existing ItemPredicate. */
object ItemPredicateData {
    /** Adds a condition that requires the named item component. */
    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", normalParams = ["string"], returnType = "ItemPredicate")
    @JvmStatic
    fun hasComponent(context: NativeCallContext) {
        NativePredicateOperations.appendContainPart(context)
    }

    /** Adds a condition that compares the named component with the supplied NBT value. */
    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", normalParams = ["string", "nbt"], returnType = "ItemPredicate")
    @JvmStatic
    fun componentMatches(context: NativeCallContext) {
        NativePredicateOperations.appendMatchPart(context)
    }

    /** Adds a condition that tests the named component with a component predicate. */
    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", normalParams = ["string", "ItemSubPredicate"], returnType = "ItemPredicate")
    @JvmStatic
    fun subPredicate(context: NativeCallContext) {
        NativePredicateOperations.appendSubPredicatePart(context)
    }

    /** Adds an item count condition. */
    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", returnType = "ItemPredicate")
    @JvmStatic
    fun hasCount(context: NativeCallContext) {
        NativePredicateOperations.appendCountPart(context)
    }

    /** Adds a condition for an exact item count. */
    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", normalParams = ["int"], returnType = "ItemPredicate")
    @JvmStatic
    fun count(context: NativeCallContext) {
        NativePredicateOperations.appendCountMatchPart(context)
    }

    /** Adds a condition for an item count range. */
    @WritesReceiver
    @MNIFunction(identifier = "count", caller = "ItemPredicate", normalParams = ["range"], returnType = "ItemPredicate")
    @JvmStatic
    fun countRange(context: NativeCallContext) {
        NativePredicateOperations.appendCountRangePart(context)
    }

}
