package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements entity team membership changes. */
object EntityTeamData {
    /**
     * Adds the receiver entities to the supplied team. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft:Team"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun joinTeam(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityJoinTeam(context)
    }

    /**
     * Removes the receiver entities from their teams. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun leaveTeam(context: NativeCallContext) {
        NativeMinecraftCommandOperations.entityLeaveTeam(context)
    }
}
