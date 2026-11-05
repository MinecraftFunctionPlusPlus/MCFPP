package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements entity status effects. */
object EntityEffectData {
    /**
     * Applies a status effect with the supplied duration, strength, and particle setting. Publishes the command
     * result and success status through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Effect", "int = 30", "int = 0", "bool = false"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun effect(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityEffect(context)
    }

    /**
     * Applies a status effect with an infinite duration. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Effect", "int = 0", "bool = false"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun effectInfinite(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityEffectInfinite(context)
    }

    /**
     * Removes all status effects from the receiver entities. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun clearAllEffects(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityClearAllEffects(context)
    }

    /**
     * Removes the selected status effect from the receiver entities. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Effect"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun clearEffect(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityClearEffect(context)
    }
}
