package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements entity tag management. */
object EntityTagData {
    /** Adds a tag to the receiver entities. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun addTag(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityAddTag(context)
    }

    /**
     * Removes a tag from the receiver entities. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["string"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun removeTag(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityRemoveTag(context)
    }

    /**
     * Lists the tags on the receiver entities. Publishes the command result and success status through context.
     */
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun listTag(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityListTag(context)
    }
}
