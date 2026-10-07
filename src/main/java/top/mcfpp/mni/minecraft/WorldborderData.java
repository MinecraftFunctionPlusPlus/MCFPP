package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class WorldborderData {

    @MNIFunction(normalParams = {"float", "int = 0"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void add(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderAdd(context);
    }

    @MNIFunction(normalParams = {"pos2 = 0 0"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setCenter(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetCenter(context);
    }

    @MNIFunction(normalParams = {"float = 0.2"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setDamageAmount(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetDamageAmount(context);
    }

    @MNIFunction(normalParams = {"float = 5.0"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setDamageBuffer(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetDamageBuffer(context);
    }

    @MNIFunction(normalParams = {"float = 29999984", "int = 0"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setSize(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetSize(context);
    }

    @MNIFunction(normalParams = {"int = 5"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setWarningDistance(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetWarningDistance(context);
    }

    @MNIFunction(normalParams = {"int = 15"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setWarningTime(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetWarningTime(context);
    }

}
