package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class OpData {
    @MNIFunction(normalParams = "entity",returnType = "mcfpp.minecraft.std:CommandResult")
    public static void deop(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.deop(context);
    }
    @MNIFunction(normalParams = "entity",returnType = "mcfpp.minecraft.std:CommandResult")
    public static void op(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.op(context);
    }
}
