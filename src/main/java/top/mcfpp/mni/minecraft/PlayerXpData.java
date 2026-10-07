package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class PlayerXpData {
    @MNIFunction(normalParams = "int", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void addXpPoints(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerAddXpPoints(context); }
    @MNIFunction(normalParams = "int", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void addXpLevels(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerAddXpLevels(context); }
    @MNIFunction(normalParams = "int", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setXpPoints(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerSetXpPoints(context); }
    @MNIFunction(normalParams = "int", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setXpLevels(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerSetXpLevels(context); }
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void queryXpPoints(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerQueryXpPoints(context); }
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void queryXpLevels(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerQueryXpLevels(context); }
}
