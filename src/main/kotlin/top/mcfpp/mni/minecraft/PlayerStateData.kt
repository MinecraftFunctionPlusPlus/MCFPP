package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements player inventory clearing and game mode changes. */
object PlayerStateData {
    /**
     * Removes all inventory items from the receiver players. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(identifier = "clear", caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun clearAll(context: NativeCallContext) { NativeMinecraftCommandOperations.playerClearAll(context); }
    /**
     * Removes items matching the supplied predicate, up to the supplied count. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(identifier = "clear", normalParams = ["string", "int = 1"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun clearItems(context: NativeCallContext) { NativeMinecraftCommandOperations.playerClearItems(context); }
    /** Sets the receiver players' game mode. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["mcfpp.minecraft.other:Gamemode"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setGamemode(context: NativeCallContext) { NativeMinecraftCommandOperations.playerSetGamemode(context); }
}
