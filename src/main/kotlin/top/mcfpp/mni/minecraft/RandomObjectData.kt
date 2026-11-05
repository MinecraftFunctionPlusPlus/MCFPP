package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements random draws and resets through the Random shared entry point. */
object RandomObjectData {
    /** Returns a random integer in the supplied range. */
    @MNIFunction(normalParams = ["range"],  returnType = "int")
    @JvmStatic
    fun rand(context: NativeCallContext) {
        NativeMinecraftCommandOperations.randomValue(context)
    }

    /** Returns a random integer in the supplied range and displays the roll in chat. */
    @MNIFunction(normalParams = ["range"], returnType = "int")
    @JvmStatic
    fun roll(context: NativeCallContext) {
        NativeMinecraftCommandOperations.randomRoll(context)
    }

    /**
     * Resets all random sequences using the command defaults. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun resetAll(context: NativeCallContext) {
        NativeMinecraftCommandOperations.randomResetAll(context)
    }

    /**
     * Resets the random sequence with the supplied seed and seed options. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(readOnlyParams = ["bool", "bool"], normalParams = ["int"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun reset(context: NativeCallContext) {
        NativeMinecraftCommandOperations.randomResetAllSequences(context)
    }
}
