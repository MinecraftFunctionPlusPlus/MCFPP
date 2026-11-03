package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class TriggerData {
    @MNIFunction(identifier = "enable", readOnlyParams = "entity", caller = "mcfpp.minecraft.other:Trigger")
    public static void enable(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.triggerEnable(context);
    }
}
