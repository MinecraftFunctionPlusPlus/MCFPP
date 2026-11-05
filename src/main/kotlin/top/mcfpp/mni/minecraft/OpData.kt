package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements server operator status changes. */
object OpData {
    /**
     * Removes server operator status from the supplied players. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["entity"],returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun deop(context: NativeCallContext) {
        NativeMinecraftCommandOperations.deop(context)
    }
    /**
     * Grants server operator status to the supplied players. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["entity"],returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun op(context: NativeCallContext) {
        NativeMinecraftCommandOperations.op(context)
    }
}
