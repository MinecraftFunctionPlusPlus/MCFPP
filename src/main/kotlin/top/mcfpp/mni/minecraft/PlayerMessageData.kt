package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements private messages to player targets. */
object PlayerMessageData {
    /**
     * Sends a private message to the receiver players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity", "string"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun tell(context: NativeCallContext) {
        NativeMinecraftCommandOperations.playerTell(context)
    }

    /**
     * Sends a private message to the receiver players using the w command. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(normalParams = ["entity", "string"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun w(context: NativeCallContext) {
        NativeMinecraftCommandOperations.playerWhisper(context)
    }
}
