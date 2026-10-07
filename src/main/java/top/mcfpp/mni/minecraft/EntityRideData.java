package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class EntityRideData {
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void stopRide(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityStopRide(context);
    }
}
