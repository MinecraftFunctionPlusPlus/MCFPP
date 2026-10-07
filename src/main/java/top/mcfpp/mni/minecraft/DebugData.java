package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class DebugData {
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void start(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.debugStart(context);
    }
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void stop(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.debugStop(context);
    }

}
