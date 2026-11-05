package top.mcfpp.mni.resource

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements MCFPP Recipe resource operations. */
object RecipeObjectData {
    /**
     * Unlocks all recipes for the supplied players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun giveAll(context: NativeCallContext) {
        NativeMinecraftCommandOperations.recipeGiveAll(context)
    }

    /**
     * Removes all recipes from the supplied players' recipe books. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun takeAll(context: NativeCallContext) {
        NativeMinecraftCommandOperations.recipeTakeAll(context)
    }
}
