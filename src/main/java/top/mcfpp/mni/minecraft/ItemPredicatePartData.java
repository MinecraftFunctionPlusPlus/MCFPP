package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.backend.NativePredicateOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.mni.annotation.NoExternalWrites;

public class ItemPredicatePartData {

    @MNIOperator(paramType = "ItemPredicatePart", operator = "|", returnType = "ItemPredicatePart")
    @NoExternalWrites
    public static void Or(NativeCallContext context){
        NativePredicateOperations.INSTANCE.orPart(context);
    }

}
