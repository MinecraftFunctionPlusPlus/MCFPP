package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements world border size, damage, and warning settings. */
object WorldborderData {

    /**
     * Changes the world border size by the supplied amount over the supplied duration. Publishes the command
     * result and success status through context.
     */
    @MNIFunction(normalParams = ["float", "int = 0"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun add(context: NativeCallContext) {
        NativeMinecraftCommandOperations.worldborderAdd(context)
    }

    /** Sets the world border center. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["pos2 = 0 0"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setCenter(context: NativeCallContext) {
        NativeMinecraftCommandOperations.worldborderSetCenter(context)
    }

    /**
     * Sets damage per block outside the world border buffer. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["float = 0.2"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setDamageAmount(context: NativeCallContext) {
        NativeMinecraftCommandOperations.worldborderSetDamageAmount(context)
    }

    /**
     * Sets the distance outside the world border before damage starts. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["float = 5.0"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setDamageBuffer(context: NativeCallContext) {
        NativeMinecraftCommandOperations.worldborderSetDamageBuffer(context)
    }

    /**
     * Sets the world border size, using the supplied transition duration. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(normalParams = ["float = 29999984", "int = 0"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setSize(context: NativeCallContext) {
        NativeMinecraftCommandOperations.worldborderSetSize(context)
    }

    /**
     * Sets the distance from the world border that triggers a warning. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["int = 5"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setWarningDistance(context: NativeCallContext) {
        NativeMinecraftCommandOperations.worldborderSetWarningDistance(context)
    }

    /**
     * Sets how early an approaching world border triggers a warning. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["int = 15"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setWarningTime(context: NativeCallContext) {
        NativeMinecraftCommandOperations.worldborderSetWarningTime(context)
    }

}
