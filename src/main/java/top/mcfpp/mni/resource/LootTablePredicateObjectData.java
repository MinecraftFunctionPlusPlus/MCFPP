package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativePredicateOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.mni.annotation.NoExternalWrites;

@NoExternalWrites
public class LootTablePredicateObjectData {

    @MNIFunction(normalParams = {"string"}, returnType = "Predicate")
    public static void of(NativeCallContext context) {
        NativePredicateOperations.INSTANCE.resourcePredicate(context);
    }

}
