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
import top.mcfpp.core.lang.entity.SelectorVar
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
    fun worldborderAdd(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("worldborder add", args[0], args[1]) }
    fun worldborderSetCenter(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("worldborder center", args[0]) }
    fun worldborderSetDamageAmount(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("worldborder damage amount", args[0]) }
    fun worldborderSetDamageBuffer(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("worldborder damage buffer", args[0]) }
    fun worldborderSetSize(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("worldborder set", args[0], args[1]) }
    fun worldborderSetWarningDistance(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("worldborder warning distance", args[0]) }
    fun worldborderSetWarningTime(context: NativeCallContext) = captureCommandResult(context) { args -> Command.buildAll("worldborder warning time", args[0]) }

    fun op(context: NativeCallContext) = context.withArguments { args ->
        val players = playerSelector(args[0]) ?: return@withArguments
        captureCommandResult(context) { Command.buildAll("op", players) }
    }
    fun deop(context: NativeCallContext) = context.withArguments { args ->
        val players = playerSelector(args[0]) ?: return@withArguments
        captureCommandResult(context) { Command.buildAll("deop", players) }
    }
    fun recipeGiveAll(context: NativeCallContext) = context.withArguments { args ->
        val players = playerSelector(args[0]) ?: return@withArguments
        captureCommandResult(context) { Command.buildAll("recipe give", players, "*") }
    }
    fun recipeTakeAll(context: NativeCallContext) = context.withArguments { args ->
        val players = playerSelector(args[0]) ?: return@withArguments
        captureCommandResult(context) { Command.buildAll("recipe take", players, "*") }
    }
    fun recipeGive(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(args[0]) ?: return@withAdapters
        captureCommandResult(context) { Command.buildAll("recipe give", players, templateField(receiver as DataTemplateObject, "id")) }
    }
    fun recipeTake(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(args[0]) ?: return@withAdapters
        captureCommandResult(context) { Command.buildAll("recipe take", players, templateField(receiver as DataTemplateObject, "id")) }
    }
    private fun playerSelector(value: Var<*>): SelectorVar? {
        val selector = value as SelectorVar
        if (selector.isPlayer()) return selector
        LogProcessor.error("Command requires a selector that only includes players")
        return null
    }

    fun teamRegister(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { Command.buildAll("team add", templateField(receiver as DataTemplateObject, "id"), templateField(receiver, "displayName")) }
    }
    fun teamUnregister(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { Command.buildAll("team remove", templateField(receiver as DataTemplateObject, "id")) }
    }
    fun teamClear(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { Command.buildAll("team empty", templateField(receiver as DataTemplateObject, "id")) }
    }
    private fun templateField(receiver: DataTemplateObject, name: String): Var<*> {
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
