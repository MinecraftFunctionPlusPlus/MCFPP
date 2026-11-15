package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.*
import top.mcfpp.command.Command
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.util.LogProcessor

/** Objective administration complements the ScoreRef value API. */
object ScoreboardData {
    @JvmStatic @MNIFunction(identifier = "addObjective", normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun addDummyObjective(context: NativeCallContext) = captureCommandResult(context) { a -> Command("scoreboard objectives add ${word(a[0])} dummy") }
    private fun word(value: Var<*>): String {
        fun payload(v: CompilerValue?): CompilerValue? = if (v is CompilerValue.Typed) payload(v.payload) else v
        val text = (payload(StorageAccess.snapshot(value)) as? CompilerValue.Text)?.value
        if (text == null || text.isEmpty() || text.any { it.isWhitespace() || it.isISOControl() }) {
            LogProcessor.error("Scoreboard identifiers and criteria must be compile-time command words")
            return "invalid"
        }
        return text
    }
    @JvmStatic @MNIFunction(normalParams = ["string", "string"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun addObjective(context: NativeCallContext) = captureCommandResult(context) { a -> Command("scoreboard objectives add ${word(a[0])} ${word(a[1])}") }
    @JvmStatic @MNIFunction(normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun removeObjective(context: NativeCallContext) = captureCommandResult(context) { a -> Command("scoreboard objectives remove ${word(a[0])}") }
    @JvmStatic @MNIFunction(normalParams = ["string", "string"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun setDisplay(context: NativeCallContext) = captureCommandResult(context) { a -> Command("scoreboard objectives setdisplay ${word(a[0])} ${word(a[1])}") }
    @JvmStatic @MNIFunction(normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun clearDisplay(context: NativeCallContext) = captureCommandResult(context) { a -> Command("scoreboard objectives setdisplay ${word(a[0])}") }
    @JvmStatic @MNIFunction(normalParams = ["entity", "string"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun reset(context: NativeCallContext) = captureCommandResult(context) { a -> nativeEntityCommand("scoreboard players reset", a[0], word(a[1])) }
    @JvmStatic @MNIFunction(normalParams = ["entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun resetAll(context: NativeCallContext) = captureCommandResult(context) { a -> nativeEntityCommand("scoreboard players reset", a[0]) }
}
