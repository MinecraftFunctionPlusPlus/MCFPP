package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.backend.NativeTextOperations;

public class JsonTextData {
    @MNIOperator(paramType = "text", returnType = "text", operator = "+", returnsConstWhenArgsConst = true)
    public static void plusText(NativeCallContext context){
        NativeTextOperations.INSTANCE.plusText(context);
    }

    @MNIOperator(paramType = "string", returnType = "text", operator = "+", returnsConstWhenArgsConst = true)
    public static void plusString(NativeCallContext context){
        NativeTextOperations.INSTANCE.plusString(context);
    }
}
