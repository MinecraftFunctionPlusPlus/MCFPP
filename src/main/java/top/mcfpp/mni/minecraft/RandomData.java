package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class RandomData {

    @MNIFunction(readOnlyParams = {"bool", "bool"}, normalParams = "int", caller = "Random", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void reset(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomReset(context);
    }

    @MNIFunction(normalParams = "range", caller = "Random", returnType = "int")
    public static void rand(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomSequenceValue(context);
    }

    @MNIFunction(normalParams = "range", caller = "Random", returnType = "int")
    public static void roll(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.randomSequenceRoll(context);
    }

}
