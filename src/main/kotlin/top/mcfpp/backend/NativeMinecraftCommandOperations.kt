package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

internal fun captureCommandResult(context: NativeCallContext, build: (List<Var<*>>) -> Command) = context.withArguments { args ->
    if (!FloatProviders.enabled && args.any { it is MCFloat && it !is MCFPPValue<*> }) {
        LogProcessor.error("Dynamic float command arguments require a number-provider target")
        return@withArguments
    }
    val result = (context.declaredReturnType.buildUnConcrete(TempPool.getVarIdentify()) as DataTemplateObject).apply { isTemp = true }
    val binding = StorageAccess.bindIncomingParameter(result)
    Function.addCommand(Commands.dataSetValue(binding.path, CompoundTag()))
    val command = Command("execute store result").build(binding.path.memberIndex("result").toCommandPart())
        .build("int 1 store success").build(binding.path.memberIndex("success").toCommandPart()).build("byte 1 run").build(build(args))
    Function.addCommands(command.buildMacroFunction())
    context.publishResult(result)
}

object NativeMinecraftCommandOperations {
    fun teamRegister(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { Command.buildAll("team add", teamField(receiver as DataTemplateObject, "id"), teamField(receiver, "displayName")) }
    }
    fun teamUnregister(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { Command.buildAll("team remove", teamField(receiver as DataTemplateObject, "id")) }
    }
    fun teamClear(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { Command.buildAll("team empty", teamField(receiver as DataTemplateObject, "id")) }
    }
    private fun teamField(receiver: DataTemplateObject, name: String): Var<*> {
        val field = DataTemplate.getField(receiver, name)!!
        return if (field is PropertyVar) field.get() else field
    }

    fun datapackDisable(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("datapack", "disable", args[0]) }
    fun datapackEnable(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("datapack enable", args[0]) }
    fun datapackEnableFirst(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("datapack enable", args[0], "first") }
    fun datapackEnableLast(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("datapack enable", args[0], "last") }
    fun datapackEnableBefore(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("datapack enable", args[0], "before", args[1]) }
    fun datapackEnableAfter(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("datapack enable", args[0], "after", args[1]) }
    fun datapackListAll(context: NativeCallContext) = captureCommandResult(context) { Command.buildAll("datapack list") }
    fun datapackListEnabled(context: NativeCallContext) = captureCommandResult(context) { Command.buildAll("datapack list enabled") }
    fun datapackListAvailable(context: NativeCallContext) = captureCommandResult(context) { Command.buildAll("datapack list available") }
    fun debugStart(context: NativeCallContext) = captureCommandResult(context) { Command("debug start") }
    fun debugStop(context: NativeCallContext) = captureCommandResult(context) { Command("debug stop") }
}
