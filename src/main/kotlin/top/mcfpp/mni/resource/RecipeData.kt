package top.mcfpp.mni.resource

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements MCFPP Recipe resource operations. */
object RecipeData {
    /**
     * Unlocks this recipe for the supplied players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity"], caller = "Recipe", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun give(context: NativeCallContext) {
        NativeMinecraftCommandOperations.recipeGive(context)
    }

    /**
     * Removes this recipe from the supplied players' recipe books. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["entity"], caller = "Recipe", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun take(context: NativeCallContext) {
        NativeMinecraftCommandOperations.recipeTake(context)
    }
}
