package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements entity mounting and dismounting. */
object EntityRideData {
    /**
     * Makes the receiver mount the supplied entity. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun ride(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityRide(context)
    }

    /**
     * Dismounts the receiver from its vehicle. Publishes the command result and success status through context.
     */
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun stopRide(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityStopRide(context)
    }
}
