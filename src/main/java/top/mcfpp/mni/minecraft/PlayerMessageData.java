package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class PlayerMessageData {
    @MNIFunction(normalParams = {"entity", "string"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void tell(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.playerTell(context);
    }

    @MNIFunction(normalParams = {"entity", "string"}, caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void w(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.playerWhisper(context);
    }
}
