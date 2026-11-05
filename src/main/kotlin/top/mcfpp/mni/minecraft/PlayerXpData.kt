package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements player experience changes and queries. */
object PlayerXpData {
    /**
     * Adds experience points to the receiver players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun addXpPoints(context: NativeCallContext) { NativeMinecraftCommandOperations.playerAddXpPoints(context); }
    /**
     * Adds experience levels to the receiver players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun addXpLevels(context: NativeCallContext) { NativeMinecraftCommandOperations.playerAddXpLevels(context); }
    /**
     * Sets experience points within the receiver players' current level. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setXpPoints(context: NativeCallContext) { NativeMinecraftCommandOperations.playerSetXpPoints(context); }
    /**
     * Sets the receiver players' experience levels. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["int"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setXpLevels(context: NativeCallContext) { NativeMinecraftCommandOperations.playerSetXpLevels(context); }
    /**
     * Queries experience points within the receiver player's current level. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun queryXpPoints(context: NativeCallContext) { NativeMinecraftCommandOperations.playerQueryXpPoints(context); }
    /**
     * Queries the receiver player's experience level. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun queryXpLevels(context: NativeCallContext) { NativeMinecraftCommandOperations.playerQueryXpLevels(context); }
}
