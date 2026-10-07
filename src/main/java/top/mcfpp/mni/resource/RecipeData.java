package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class RecipeData {
    @MNIFunction(normalParams = "entity", caller = "Recipe", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void give(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.recipeGive(context);
    }

    @MNIFunction(normalParams = "entity", caller = "Recipe", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void take(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.recipeTake(context);
    }
}        
