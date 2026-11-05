package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements random draws and resets for a named random sequence. */
object RandomData {

    /**
     * Resets the random sequence with the supplied seed and seed options. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(readOnlyParams = ["bool", "bool"], normalParams = ["int"], caller = "Random", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun reset(context: NativeCallContext) {
        NativeMinecraftCommandOperations.randomReset(context)
    }

    /** Returns a random integer in the supplied range. */
    @MNIFunction(normalParams = ["range"], caller = "Random", returnType = "int")
    @JvmStatic
    fun rand(context: NativeCallContext) {
        NativeMinecraftCommandOperations.randomSequenceValue(context)
    }

    /** Returns a random integer in the supplied range and displays the roll in chat. */
    @MNIFunction(normalParams = ["range"], caller = "Random", returnType = "int")
    @JvmStatic
    fun roll(context: NativeCallContext) {
        NativeMinecraftCommandOperations.randomSequenceRoll(context)
    }

}
