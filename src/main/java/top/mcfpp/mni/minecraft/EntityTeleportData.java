package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class EntityTeleportData {
    @MNIFunction(identifier = "tp", normalParams = "pos3", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void teleportToPosition(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityTeleportToPosition(context);
    }

    @MNIFunction(identifier = "tp", normalParams = {"pos3", "pos2"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void teleportWithRotation(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityTeleportWithRotation(context);
    }

    @MNIFunction(identifier = "tp", normalParams = {"pos3", "pos3"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void teleportFacingPosition(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityTeleportFacingPosition(context);
    }

    @MNIFunction(identifier = "tp", normalParams = {"pos3", "entity", "mcfpp.minecraft.other:Anchor"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void teleportFacingEntity(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityTeleportFacingEntity(context);
    }

    @MNIFunction(identifier = "tp", normalParams = "entity", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void teleportToEntity(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.entityTeleportToEntity(context);
    }
}
