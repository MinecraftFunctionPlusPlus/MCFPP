package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements team creation, removal, and membership clearing. */
object TeamData {
    /** Removes the receiver team. Publishes the command result and success status through context. */
    @MNIFunction(caller = "Team", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun unregister(context: NativeCallContext) {
        NativeMinecraftCommandOperations.teamUnregister(context)
    }

    /** Creates the receiver team. Publishes the command result and success status through context. */
    @MNIFunction(caller = "Team", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun register(context: NativeCallContext) {
        NativeMinecraftCommandOperations.teamRegister(context)
    }

    /**
     * Removes all members from the receiver team. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(caller = "Team", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun clear(context: NativeCallContext) {
        NativeMinecraftCommandOperations.teamClear(context)
    }

}
