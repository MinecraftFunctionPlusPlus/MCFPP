package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIAccessor
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIMutator
import top.mcfpp.backend.NativeMinecraftCommandOperations
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites

/** Implements boss bar creation, appearance, visibility, and value access. */
@Suppress("DataFlowIssue")
object BossBarData {

    /** Creates the receiver boss bar. Publishes the command result and success status through context. */
    @MNIFunction(caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun add(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarAdd(context)
    }

    /** Removes the receiver boss bar. Publishes the command result and success status through context. */
    @MNIFunction(caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun remove(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarRemove(context)
    }

    /** Lists existing boss bars. Publishes the command result and success status through context. */
    @MNIFunction(caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun listAll(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarList(context)
    }

    /** Reads the boss bar maximum value. */
    @NoExternalWrites
    @MNIAccessor(value = "max")
    @JvmStatic
    fun getMax(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarGetMax(context)
    }

    /** Sets the boss bar maximum value. */
    @MNIMutator(value = "max")
    @JvmStatic
    fun setMax(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarSetMax(context)
    }

    /** Reads the boss bar current value. */
    @NoExternalWrites
    @MNIAccessor(value = "value")
    @JvmStatic
    fun getValue(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarGetValue(context)
    }

    /** Sets the boss bar current value. */
    @MNIMutator(value = "value")
    @JvmStatic
    fun setValue(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarSetValue(context)
    }

    /** Reads whether the boss bar is visible. */
    @NoExternalWrites
    @MNIAccessor(value = "visible")
    @JvmStatic
    fun getVisible(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarGetVisible(context)
    }

    /** Sets whether the boss bar is visible. */
    @MNIMutator(value = "visible")
    @JvmStatic
    fun setVisible(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarSetVisible(context)
    }

    /** Sets the boss bar color. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["BossBarColor"], caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setColor(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarSetColor(context)
    }

    /** Sets the boss bar display text. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["text"], caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setName(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarSetName(context)
    }

    /**
     * Selects the players who can see the boss bar. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity"], caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setVisiblePlayers(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarSetVisiblePlayers(context)
    }

    /** Sets the boss bar segment style. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["BossBarStyle"], caller = "BossBar", returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setStyle(context: NativeCallContext) {
        NativeMinecraftCommandOperations.bossbarSetStyle(context)
    }
}
