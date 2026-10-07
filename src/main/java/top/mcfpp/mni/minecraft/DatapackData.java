package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class DatapackData {
    @MNIFunction(normalParams = "string",returnType = "mcfpp.minecraft.std:CommandResult")
    public static void disable(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.datapackDisable(context);
    }
    @MNIFunction(normalParams = "string",returnType = "mcfpp.minecraft.std:CommandResult")
    public static void enable(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.datapackEnable(context);
    }
    @MNIFunction(normalParams = "string",returnType = "mcfpp.minecraft.std:CommandResult")
    public static void enableFirst(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.datapackEnableFirst(context);
    }
    @MNIFunction(normalParams = "string",returnType = "mcfpp.minecraft.std:CommandResult")
    public static void enableLast(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.datapackEnableLast(context);
    }
    @MNIFunction(normalParams = {"string", "string"},returnType = "mcfpp.minecraft.std:CommandResult")
    public static void enableBefore(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.datapackEnableBefore(context);
    }
    @MNIFunction(normalParams = {"string", "string"},returnType = "mcfpp.minecraft.std:CommandResult")
    public static void enableAfter(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.datapackEnableAfter(context);
    }
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void listAll(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.datapackListAll(context);
    }
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void listEnabled(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.datapackListEnabled(context);
    }
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void listAvailable(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.datapackListAvailable(context);
    }
}
