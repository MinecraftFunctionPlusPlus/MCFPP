package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;

public class MCAnyData {
    @MNIFunction(caller = "any", returnType = "text")
    public static void toText(NativeCallContext context) {
        top.mcfpp.backend.NativeTextOperations.INSTANCE.representation(context);
    }

}
