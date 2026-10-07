package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class PlayerAdvancementData {
    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Advancement", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void grant(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerGrant(context); }
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void grantAll(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerGrantAll(context); }
    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Advancement", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void grantFrom(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerGrantFrom(context); }
    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Advancement", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void grantThrough(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerGrantThrough(context); }
    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Advancement", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void grantUntil(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerGrantUntil(context); }
    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Advancement", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void revoke(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerRevoke(context); }
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void revokeAll(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerRevokeAll(context); }
    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Advancement", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void revokeFrom(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerRevokeFrom(context); }
    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Advancement", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void revokeThrough(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerRevokeThrough(context); }
    @MNIFunction(normalParams = "mcfpp.minecraft.resource:Advancement", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void revokeUntil(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerRevokeUntil(context); }
}
