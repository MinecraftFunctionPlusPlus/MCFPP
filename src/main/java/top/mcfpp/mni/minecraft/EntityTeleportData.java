package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class EntityTeleportData {
    @MNIFunction(identifier = "tp", normalParams = "entity", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void teleportToEntity(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityTeleportToEntity(context);
    }
}
