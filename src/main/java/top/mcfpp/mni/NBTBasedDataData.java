package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;

public class NBTBasedDataData {

    @MNIFunction(caller = "nbt", returnType = "text")
    public static void toText(NativeCallContext context) {
        top.mcfpp.backend.NativeTextOperations.INSTANCE.nbt(context);
    }

}
