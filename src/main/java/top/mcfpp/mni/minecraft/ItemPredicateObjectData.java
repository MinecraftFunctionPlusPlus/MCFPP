package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativePredicateOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.mni.annotation.NoExternalWrites;

@NoExternalWrites
public class ItemPredicateObjectData {
    @MNIFunction(normalParams = {"string"}, returnType = "ItemPredicatePart")
    public static void hasComponent(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.containPart(context);
    }

    @MNIFunction(normalParams = {"string", "nbt"}, returnType = "ItemPredicatePart")
    public static void componentMatches(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.matchPart(context);
    }

    @MNIFunction(normalParams = {"string", "ItemSubPredicate"}, returnType = "ItemPredicatePart")
    public static void subPredicate(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.subPredicatePart(context);
    }

    @MNIFunction(returnType = "ItemPredicatePart")
    public static void hasCount(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.countPart(context);
    }

    @MNIFunction(normalParams = {"int"}, returnType = "ItemPredicatePart")
    public static void count(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.countMatchPart(context);
    }

    @MNIFunction(identifier = "count", normalParams = {"range"}, returnType = "ItemPredicatePart")
    public static void countRange(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.countRangePart(context);
    }

}
