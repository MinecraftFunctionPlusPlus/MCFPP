package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements the server debug profiler commands. */
object DebugData {
    /** Starts the server debug profiler. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun start(context: NativeCallContext) {
        NativeMinecraftCommandOperations.debugStart(context)
    }
    /**
     * Stops the server debug profiler and writes its report. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun stop(context: NativeCallContext) {
        NativeMinecraftCommandOperations.debugStop(context)
    }

}
