package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;

public class NBTBasedDataConcreteData {

    @MNIFunction(caller = "nbt", returnType = "text")
    public static void toText(NativeCallContext context) {
        top.mcfpp.backend.NativeTextOperations.INSTANCE.nbtValue(context);
    }

}
