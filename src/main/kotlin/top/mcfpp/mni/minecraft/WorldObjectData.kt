package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements world difficulty and weather changes. */
object WorldObjectData {

    /** Sets the world difficulty. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["Difficulty"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setDifficulty(context: NativeCallContext) {
        NativeMinecraftCommandOperations.worldSetDifficulty(context)
    }

    /**
     * Sets the world weather for the supplied duration. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["Weather", "int"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setWeather(context: NativeCallContext) {
        NativeMinecraftCommandOperations.worldSetWeather(context)
    }

}
