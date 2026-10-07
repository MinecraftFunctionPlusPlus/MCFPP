package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class WorldObjectData {

    @MNIFunction(normalParams = "Difficulty", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setDifficulty(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.worldSetDifficulty(context);
    }

    @MNIFunction(normalParams = {"Weather", "int"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setWeather(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldSetWeather(context);
    }



}
