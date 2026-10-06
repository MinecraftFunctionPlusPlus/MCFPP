package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativePredicateOperations;
import top.mcfpp.mni.NativeCallContext;

public class LootTablePredicateData {

    @MNIFunction(caller = "Predicate", returnType = "bool")
    public static void pass(NativeCallContext context){
        NativePredicateOperations.INSTANCE.pass(context);
    }

    @MNIFunction(caller = "Predicate", returnType = "bool")
    public static void fail(NativeCallContext context){
        NativePredicateOperations.INSTANCE.fail(context);
    }
}        
