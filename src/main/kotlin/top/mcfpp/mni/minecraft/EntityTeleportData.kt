package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements global and entity member teleport functions. */
object EntityTeleportData {
    /**
     * Teleports the supplied entities to the position. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "tp", normalParams = ["entity", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun teleportEntitiesToPosition(context: NativeCallContext) {
        NativeMinecraftCommandOperations.teleportEntities(context)
    }

    /**
     * Teleports the supplied entities to the saved position and dimension. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(identifier = "tp", normalParams = ["entity", "ContextPosition"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun teleportEntitiesToContextPosition(context: NativeCallContext) {
        NativeMinecraftCommandOperations.teleportEntities(context)
    }

    /**
     * Teleports the receiver to the supplied position. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "tp", normalParams = ["pos3"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun teleportToPosition(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityTeleportToPosition(context)
    }

    /**
     * Teleports the receiver and sets its yaw and pitch. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(identifier = "tp", normalParams = ["pos3", "pos2"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun teleportWithRotation(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityTeleportWithRotation(context)
    }

    /**
     * Teleports the receiver and turns it toward the supplied position. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(identifier = "tp", normalParams = ["pos3", "pos3"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun teleportFacingPosition(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityTeleportFacingPosition(context)
    }

    /**
     * Teleports the receiver and turns it toward the target entity anchor. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(identifier = "tp", normalParams = ["pos3", "entity", "mcfpp.minecraft.other:Anchor"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun teleportFacingEntity(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityTeleportFacingEntity(context)
    }

    /**
     * Teleports the receiver to the target entity. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "tp", normalParams = ["entity"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun teleportToEntity(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityTeleportToEntity(context)
    }
}
