package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Implements player advancement grants and revocations. */
object PlayerAdvancementData {
    /**
     * Grants the selected advancement to the receiver players. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun grant(context: NativeCallContext) { NativeMinecraftCommandOperations.playerGrant(context); }
    /**
     * Grants all advancements to the receiver players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun grantAll(context: NativeCallContext) { NativeMinecraftCommandOperations.playerGrantAll(context); }
    /**
     * Grants the selected advancement and its descendants. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun grantFrom(context: NativeCallContext) { NativeMinecraftCommandOperations.playerGrantFrom(context); }
    /**
     * Grants the selected advancement, its ancestors, and its descendants. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun grantThrough(context: NativeCallContext) { NativeMinecraftCommandOperations.playerGrantThrough(context); }
    /**
     * Grants the selected advancement and its ancestors. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun grantUntil(context: NativeCallContext) { NativeMinecraftCommandOperations.playerGrantUntil(context); }
    /**
     * Revokes the selected advancement from the receiver players. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun revoke(context: NativeCallContext) { NativeMinecraftCommandOperations.playerRevoke(context); }
    /**
     * Revokes all advancements from the receiver players. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun revokeAll(context: NativeCallContext) { NativeMinecraftCommandOperations.playerRevokeAll(context); }
    /**
     * Revokes the selected advancement and its descendants. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun revokeFrom(context: NativeCallContext) { NativeMinecraftCommandOperations.playerRevokeFrom(context); }
    /**
     * Revokes the selected advancement, its ancestors, and its descendants. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun revokeThrough(context: NativeCallContext) { NativeMinecraftCommandOperations.playerRevokeThrough(context); }
    /**
     * Revokes the selected advancement and its ancestors. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["mcfpp.minecraft.resource:Advancement"], caller = "entity", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun revokeUntil(context: NativeCallContext) { NativeMinecraftCommandOperations.playerRevokeUntil(context); }
}
