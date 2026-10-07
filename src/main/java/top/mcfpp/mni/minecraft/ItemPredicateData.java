package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativePredicateOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.mni.annotation.WritesReceiver;

public class ItemPredicateData {
    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", normalParams = {"string"}, returnType = "ItemPredicate")
    public static void hasComponent(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.appendContainPart(context);
    }

    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", normalParams = {"string", "nbt"}, returnType = "ItemPredicate")
    public static void componentMatches(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.appendMatchPart(context);
    }

    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", normalParams = {"string", "ItemSubPredicate"}, returnType = "ItemPredicate")
    public static void subPredicate(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.appendSubPredicatePart(context);
    }

    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", returnType = "ItemPredicate")
    public static void hasCount(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.appendCountPart(context);
    }

    @WritesReceiver
    @MNIFunction(caller = "ItemPredicate", normalParams = {"int"}, returnType = "ItemPredicate")
    public static void count(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.appendCountMatchPart(context);
    }

    @WritesReceiver
    @MNIFunction(identifier = "count", caller = "ItemPredicate", normalParams = {"range"}, returnType = "ItemPredicate")
    public static void countRange(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.appendCountRangePart(context);
    }

}
