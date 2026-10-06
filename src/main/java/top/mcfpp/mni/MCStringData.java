package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;

public class MCStringData {

    @MNIFunction(caller = "string", returnType = "text", override = true)
    public static void toText(NativeCallContext context) {
        top.mcfpp.backend.NativeTextOperations.INSTANCE.nbt(context);
    }
}
