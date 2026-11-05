package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements data pack activation and listing. */
object DatapackData {
    /** Disables the named data pack. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["string"],returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun disable(context: NativeCallContext) {
        NativeMinecraftCommandOperations.datapackDisable(context)
    }
    /** Enables the named data pack. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["string"],returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun enable(context: NativeCallContext) {
        NativeMinecraftCommandOperations.datapackEnable(context)
    }
    /**
     * Enables the named data pack before the other packs. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["string"],returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun enableFirst(context: NativeCallContext) {
        NativeMinecraftCommandOperations.datapackEnableFirst(context)
    }
    /**
     * Enables the named data pack after the other packs. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["string"],returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun enableLast(context: NativeCallContext) {
        NativeMinecraftCommandOperations.datapackEnableLast(context)
    }
    /**
     * Enables the named data pack before the supplied pack. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["string", "string"],returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun enableBefore(context: NativeCallContext) {
        NativeMinecraftCommandOperations.datapackEnableBefore(context)
    }
    /**
     * Enables the named data pack after the supplied pack. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["string", "string"],returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun enableAfter(context: NativeCallContext) {
        NativeMinecraftCommandOperations.datapackEnableAfter(context)
    }
    /** Lists enabled and available data packs. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun listAll(context: NativeCallContext) {
        NativeMinecraftCommandOperations.datapackListAll(context)
    }
    /** Lists enabled data packs. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun listEnabled(context: NativeCallContext) {
        NativeMinecraftCommandOperations.datapackListEnabled(context)
    }
    /** Lists available data packs. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun listAvailable(context: NativeCallContext) {
        NativeMinecraftCommandOperations.datapackListAvailable(context)
    }
}
