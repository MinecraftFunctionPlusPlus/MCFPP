package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativePredicateOperations
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites

/** Creates item predicate parts with component or count conditions. */
@NoExternalWrites
object ItemPredicateObjectData {
    /** Creates a condition that requires the named item component. */
    @MNIFunction(normalParams = ["string"], returnType = "ItemPredicatePart")
    @JvmStatic
    fun hasComponent(context: NativeCallContext) {
        NativePredicateOperations.containPart(context)
    }

    /** Creates a condition that compares the named component with the supplied NBT value. */
    @MNIFunction(normalParams = ["string", "nbt"], returnType = "ItemPredicatePart")
    @JvmStatic
    fun componentMatches(context: NativeCallContext) {
        NativePredicateOperations.matchPart(context)
    }

    /** Creates a condition that tests the named component with a component predicate. */
    @MNIFunction(normalParams = ["string", "ItemSubPredicate"], returnType = "ItemPredicatePart")
    @JvmStatic
    fun subPredicate(context: NativeCallContext) {
        NativePredicateOperations.subPredicatePart(context)
    }

    /** Creates an item count condition. */
    @MNIFunction(returnType = "ItemPredicatePart")
    @JvmStatic
    fun hasCount(context: NativeCallContext) {
        NativePredicateOperations.countPart(context)
    }

    /** Creates a condition for an exact item count. */
    @MNIFunction(normalParams = ["int"], returnType = "ItemPredicatePart")
    @JvmStatic
    fun count(context: NativeCallContext) {
        NativePredicateOperations.countMatchPart(context)
    }

    /** Creates a condition for an item count range. */
    @MNIFunction(identifier = "count", normalParams = ["range"], returnType = "ItemPredicatePart")
    @JvmStatic
    fun countRange(context: NativeCallContext) {
        NativePredicateOperations.countRangePart(context)
    }

}
