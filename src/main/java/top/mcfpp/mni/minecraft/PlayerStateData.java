package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class PlayerStateData {
    @MNIFunction(identifier = "clear", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void clearAll(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerClearAll(context); }
    @MNIFunction(identifier = "clear", normalParams = {"string", "int = 1"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void clearItems(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerClearItems(context); }
    @MNIFunction(normalParams = "mcfpp.minecraft.other:Gamemode", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setGamemode(NativeCallContext context) { NativeMinecraftCommandOperations.INSTANCE.playerSetGamemode(context); }
}
