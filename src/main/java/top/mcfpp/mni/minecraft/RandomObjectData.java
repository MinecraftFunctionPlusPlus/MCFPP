package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class RandomObjectData {
    @MNIFunction(normalParams = "range",  returnType = "int")
    public static void rand(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomValue(context);
    }

    @MNIFunction(normalParams = "range", returnType = "int")
    public static void roll(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomRoll(context);
    }

    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void resetAll(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomResetAll(context);
    }

    @MNIFunction(readOnlyParams = {"bool", "bool"}, normalParams = "int", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void reset(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomResetAllSequences(context);
    }
}
