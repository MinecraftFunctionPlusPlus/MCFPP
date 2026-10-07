package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class EntityRideData {
    @MNIFunction(normalParams = "entity", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void ride(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityRide(context);
    }

    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void stopRide(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityStopRide(context);
    }
}
