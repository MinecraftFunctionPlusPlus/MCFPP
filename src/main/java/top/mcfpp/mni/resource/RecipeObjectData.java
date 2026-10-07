package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class RecipeObjectData {
    @MNIFunction(normalParams = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void giveAll(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.recipeGiveAll(context);
    }

    @MNIFunction(normalParams = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void takeAll(NativeCallContext context) {
        NativeMinecraftCommandOperations.INSTANCE.recipeTakeAll(context);
    }
}
