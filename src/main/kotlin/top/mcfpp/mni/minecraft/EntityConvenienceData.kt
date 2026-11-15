package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.*
import top.mcfpp.mni.NativeCallContext

/** Entity and player conveniences preserve native command results. */
object EntityConvenienceData {
    @JvmStatic @MNIFunction(identifier = "title", caller = "entity", normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun titleString(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver, players = true) != null) captureCommandResult(context) {
            nativeEntityCommand("title", receiver, "title", args[0].implicitCast(top.mcfpp.type.MCFPPBaseType.JsonText))
        }
    }
    @JvmStatic @MNIFunction(identifier = "subtitle", caller = "entity", normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun subtitleString(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver, players = true) != null) captureCommandResult(context) {
            nativeEntityCommand("title", receiver, "subtitle", args[0].implicitCast(top.mcfpp.type.MCFPPBaseType.JsonText))
        }
    }
    @JvmStatic @MNIFunction(identifier = "actionbar", caller = "entity", normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun actionbarString(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver, players = true) != null) captureCommandResult(context) {
            nativeEntityCommand("title", receiver, "actionbar", args[0].implicitCast(top.mcfpp.type.MCFPPBaseType.JsonText))
        }
    }
    @JvmStatic @MNIFunction(identifier = "damage", caller = "entity", normalParams = ["float"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun damageDefault(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (!StandardCommandOperations.requireDamageCommand()) return@withAdapters
        if (requireEntityTarget(receiver, single = true) != null)
            captureCommandResult(context) { nativeEntityCommand("damage", receiver, args[0], "minecraft:generic") }
    }
    @JvmStatic @MNIFunction(caller = "entity", normalParams = [], returnType = "mcfpp.minecraft.std:CommandResult")
    fun kill(context: NativeCallContext) = context.withAdapters { receiver, args -> captureCommandResult(context) { nativeEntityCommand("kill", receiver) } }
    @JvmStatic @MNIFunction(caller = "entity", normalParams = ["float", "mcfpp.minecraft.resource:DamageType"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun damage(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (!StandardCommandOperations.requireDamageCommand()) return@withAdapters
        if (requireEntityTarget(receiver, single = true) != null) captureCommandResult(context) { nativeEntityCommand("damage", receiver, args[0], args[1]) }
    }
    @JvmStatic @MNIFunction(caller = "entity", normalParams = ["text"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun title(context: NativeCallContext) = context.withAdapters { receiver, args -> if (requireEntityTarget(receiver, players = true) != null) captureCommandResult(context) { nativeEntityCommand("title", receiver, "title", args[0]) } }
    @JvmStatic @MNIFunction(caller = "entity", normalParams = ["text"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun subtitle(context: NativeCallContext) = context.withAdapters { receiver, args -> if (requireEntityTarget(receiver, players = true) != null) captureCommandResult(context) { nativeEntityCommand("title", receiver, "subtitle", args[0]) } }
    @JvmStatic @MNIFunction(caller = "entity", normalParams = ["text"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun actionbar(context: NativeCallContext) = context.withAdapters { receiver, args -> if (requireEntityTarget(receiver, players = true) != null) captureCommandResult(context) { nativeEntityCommand("title", receiver, "actionbar", args[0]) } }
    @JvmStatic @MNIFunction(caller = "entity", normalParams = ["int", "int", "int"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun titleTimes(context: NativeCallContext) = context.withAdapters { receiver, args -> if (requireEntityTarget(receiver, players = true) != null) captureCommandResult(context) { nativeEntityCommand("title", receiver, "times", args[0], args[1], args[2]) } }
}
