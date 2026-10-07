package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.EnumVarConcrete
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
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
    fun entityGetAttributeBase(context: NativeCallContext) = entityAttribute(context) { receiver, args ->
        Command.buildAll("attribute", receiver, args[0], "base get", args[1])
    }
    fun entityGetAttribute(context: NativeCallContext) = entityAttribute(context) { receiver, args ->
        Command.buildAll("attribute", receiver, args[0], "get", args[1])
    }
    fun entityRemoveAttributeModifier(context: NativeCallContext) = entityAttribute(context) { receiver, args ->
        Command.buildAll("attribute", receiver, args[0], "modifier remove", templateField(args[1] as DataTemplateObject, "id"))
    }
    fun entityGetAttributeModifier(context: NativeCallContext) = entityAttribute(context) { receiver, args ->
        Command.buildAll("attribute", receiver, args[0], "modifier value get", templateField(args[1] as DataTemplateObject, "id"), args[2])
    }

    private fun entityAttribute(context: NativeCallContext, build: (SelectorVar, List<Var<*>>) -> Command) = context.withAdapters { receiver, _ ->
        if (receiver !is SelectorVar || !receiver.value.selectingSingleEntity()) {
            LogProcessor.error("Entity attribute commands require a single-entity selector receiver")
            return@withAdapters
        }
        captureCommandResult(context) { args -> build(receiver, args) }
    }

    fun playerTell(context: NativeCallContext) = playerMessage(context, "tell")
    fun playerWhisper(context: NativeCallContext) = playerMessage(context, "w")

    private fun playerMessage(context: NativeCallContext, verb: String) = context.withAdapters { receiver, args ->
        val sender = playerSelector(receiver) ?: return@withAdapters
        val targets = playerSelector(args[0]) ?: return@withAdapters
        val message = args[1] as MCString
        captureCommandResult(context) { Command.buildAll("execute as", sender, "run", verb, targets, message) }
    }

    fun entityTeleportToEntity(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (receiver !is SelectorVar) {
            LogProcessor.error("Entity teleport commands require a selector receiver")
            return@withAdapters
        }
        val destination = args[0]
        if (destination !is SelectorVar || !destination.value.selectingSingleEntity()) {
            LogProcessor.error("Entity teleport commands require a single-entity selector destination")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("tp", receiver, destination) }
    }

    fun playerClearAll(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val players = playerSelector(receiver) ?: return@withAdapters
        captureCommandResult(context) { Command.buildAll("clear", players) }
    }
    fun playerClearItems(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(receiver) ?: return@withAdapters
        captureCommandResult(context) { Command.buildAll("clear", players, args[0], args[1]) }
    }
    fun playerSetGamemode(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(receiver) ?: return@withAdapters
        val mode = args[0] as? EnumVarConcrete ?: run {
            LogProcessor.error("Gamemode requires a compile-time enum value")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("gamemode", mode.value.identifier, players) }
    }
    fun entityRide(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val target = args[0]
        if (receiver !is SelectorVar || target !is SelectorVar || !receiver.value.selectingSingleEntity() || !target.value.selectingSingleEntity()) {
            LogProcessor.error("Entity mount commands require single-entity selector receiver and target")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("ride", receiver, "mount", target) }
    }

    fun playerGrant(context: NativeCallContext) = playerAdvancement(context, "grant", "only")
    fun playerGrantAll(context: NativeCallContext) = playerAdvancement(context, "grant", "everything")
    fun playerGrantFrom(context: NativeCallContext) = playerAdvancement(context, "grant", "from")
    fun playerGrantThrough(context: NativeCallContext) = playerAdvancement(context, "grant", "through")
    fun playerGrantUntil(context: NativeCallContext) = playerAdvancement(context, "grant", "until")
    fun playerRevoke(context: NativeCallContext) = playerAdvancement(context, "revoke", "only")
    fun playerRevokeAll(context: NativeCallContext) = playerAdvancement(context, "revoke", "everything")
    fun playerRevokeFrom(context: NativeCallContext) = playerAdvancement(context, "revoke", "from")
    fun playerRevokeThrough(context: NativeCallContext) = playerAdvancement(context, "revoke", "through")
    fun playerRevokeUntil(context: NativeCallContext) = playerAdvancement(context, "revoke", "until")

    private fun playerAdvancement(context: NativeCallContext, operation: String, mode: String) = context.withAdapters { receiver, args ->
        val players = playerSelector(receiver) ?: return@withAdapters
        captureCommandResult(context) {
            if (mode == "everything") Command.buildAll("advancement", operation, players, mode)
            else Command.buildAll("advancement", operation, players, mode, templateField(args[0] as DataTemplateObject, "id"))
        }
    }

    fun playerAddXpPoints(context: NativeCallContext) = playerXpWrite(context, "add", "points")
    fun playerAddXpLevels(context: NativeCallContext) = playerXpWrite(context, "add", "levels")
    fun playerSetXpPoints(context: NativeCallContext) = playerXpWrite(context, "set", "points")
    fun playerSetXpLevels(context: NativeCallContext) = playerXpWrite(context, "set", "levels")
    fun playerQueryXpPoints(context: NativeCallContext) = playerXpQuery(context, "points")
    fun playerQueryXpLevels(context: NativeCallContext) = playerXpQuery(context, "levels")

    private fun playerXpWrite(context: NativeCallContext, operation: String, unit: String) = context.withAdapters { receiver, args ->
        val players = playerSelector(receiver) ?: return@withAdapters
        captureCommandResult(context) { Command.buildAll("xp", operation, players, args[0], unit) }
    }
    private fun playerXpQuery(context: NativeCallContext, unit: String) = context.withAdapters { receiver, _ ->
        val player = playerSelector(receiver) ?: return@withAdapters
        if (!player.value.selectingSingleEntity()) {
            LogProcessor.error("Experience queries require a single-player selector")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("xp query", player, unit) }
    }

    fun entityStopRide(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        if (receiver !is SelectorVar || !receiver.value.selectingSingleEntity()) {
            LogProcessor.error("Entity dismount commands require a single-entity selector receiver")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("ride", receiver, "dismount") }
    }

    fun entityEffect(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (receiver !is SelectorVar) {
            LogProcessor.error("Entity effect commands require a selector receiver")
            return@withAdapters
        }
        val hide = effectBooleanWord(context, args[3], 3)
        captureCommandResult(context) { Command.buildAll("effect give", receiver, templateField(args[0] as DataTemplateObject, "id"), args[1], args[2], hide) }
    }
    fun entityEffectInfinite(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (receiver !is SelectorVar) {
            LogProcessor.error("Entity effect commands require a selector receiver")
            return@withAdapters
        }
        val hide = effectBooleanWord(context, args[2], 2)
        captureCommandResult(context) { Command.buildAll("effect give", receiver, templateField(args[0] as DataTemplateObject, "id"), "infinite", args[1], hide) }
    }

    private fun effectBooleanWord(context: NativeCallContext, value: Var<*>, index: Int): MCString {
        var snapshot = context.argumentSnapshot(index)
        while (snapshot is CompilerValue.Typed) snapshot = snapshot.payload
        if (snapshot is CompilerValue.Bool) return MCStringConcrete(StringTag(snapshot.value.toString()))
        val score = StorageAccess.read(value) as ScoreBool
        val word = MCString(TempPool.getVarIdentify()).apply { isTemp = true }
        val binding = StorageAccess.bindIncomingParameter(word)
        Function.addCommand(Commands.dataSetValue(binding.path, StringTag("false")))
        Function.addCommand(Command("execute if").build(score.toCommandPart()).build("run").build(Commands.dataSetValue(binding.path, StringTag("true"))))
        return word
    }

    fun entityClearAllEffects(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        if (receiver !is SelectorVar) {
            LogProcessor.error("Entity effect commands require a selector receiver")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("effect clear", receiver) }
    }
    fun entityClearEffect(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (receiver !is SelectorVar) {
            LogProcessor.error("Entity effect commands require a selector receiver")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("effect clear", receiver, templateField(args[0] as DataTemplateObject, "id")) }
    }

    fun entityJoinTeam(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (receiver !is SelectorVar) {
            LogProcessor.error("Entity team commands require a selector receiver")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("team join", templateField(args[0] as DataTemplateObject, "id"), receiver) }
    }
    fun entityLeaveTeam(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        if (receiver !is SelectorVar) {
            LogProcessor.error("Entity team commands require a selector receiver")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("team leave", receiver) }
    }

    fun entityAddTag(context: NativeCallContext) = entityTag(context, "add")
    fun entityRemoveTag(context: NativeCallContext) = entityTag(context, "remove")
    fun entityListTag(context: NativeCallContext) = entityTag(context, "list")

    private fun entityTag(context: NativeCallContext, operation: String) = context.withAdapters { receiver, args ->
        if (receiver !is SelectorVar) {
            LogProcessor.error("Entity tag commands require a selector receiver")
            return@withAdapters
        }
        captureCommandResult(context) {
            if (operation == "list") Command.buildAll("tag", receiver, operation)
            else Command.buildAll("tag", receiver, operation, args[0])
        }
    }

    fun randomReset(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val flags = randomFlags(context) ?: return@withAdapters
        captureCommandResult(context) { Command.buildAll("random reset", templateField(receiver as DataTemplateObject, "id"), args[2], flags.first, flags.second) }
    }
    fun randomResetAllSequences(context: NativeCallContext) = context.withArguments { args ->
        val flags = randomFlags(context) ?: return@withArguments
        captureCommandResult(context) { Command.buildAll("random reset *", args[2], flags.first, flags.second) }
    }
    fun randomResetAll(context: NativeCallContext) = captureCommandResult(context) { Command("random reset *") }

    private fun randomFlags(context: NativeCallContext): Pair<Boolean, Boolean>? {
        fun flag(index: Int): Boolean? {
            var snapshot = context.argumentSnapshot(index)
            while (snapshot is CompilerValue.Typed) snapshot = snapshot.payload
            return (snapshot as? CompilerValue.Bool)?.value
        }
        val world = flag(0)
        val sequence = flag(1)
        if (world == null || sequence == null) {
            LogProcessor.error("Random reset flags require complete boolean values")
            return null
        }
        return world to sequence
    }

    fun worldSetDifficulty(context: NativeCallContext) = context.withArguments { args ->
        val difficulty = args[0] as? EnumVarConcrete ?: run {
            LogProcessor.error("Difficulty requires a compile-time enum value")
            return@withArguments
        }
        captureCommandResult(context) { Command.buildAll("difficulty", difficulty.value.identifier) }
    }
    fun worldSetWeather(context: NativeCallContext) = context.withArguments { args ->
        val weather = args[0] as? EnumVarConcrete ?: run {
            LogProcessor.error("Weather requires a compile-time enum value")
            return@withArguments
        }
        captureCommandResult(context) { Command.buildAll("weather", weather.value.identifier, args[1]) }
    }

    fun bossbarAdd(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { Command.buildAll("bossbar add", templateField(receiver as DataTemplateObject, "id"), templateField(receiver, "name")) }
    }
    fun bossbarRemove(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { Command.buildAll("bossbar remove", templateField(receiver as DataTemplateObject, "id")) }
    }
    fun bossbarList(context: NativeCallContext) = context.withAdapters { _, _ ->
        captureCommandResult(context) { Command("bossbar list") }
    }
    fun bossbarSetColor(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val color = args[0] as? EnumVarConcrete ?: run {
            LogProcessor.error("Bossbar color/style requires a compile-time enum value")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("bossbar set", templateField(receiver as DataTemplateObject, "id"), "color", color.value.dataAsString()) }
    }
    fun bossbarSetName(context: NativeCallContext) = context.withAdapters { receiver, args ->
        captureCommandResult(context) { Command.buildAll("bossbar set", templateField(receiver as DataTemplateObject, "id"), "name", args[0]) }
    }
    fun bossbarSetVisiblePlayers(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(args[0]) ?: return@withAdapters
        captureCommandResult(context) { Command.buildAll("bossbar set", templateField(receiver as DataTemplateObject, "id"), "players", players) }
    }
    fun bossbarSetStyle(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val style = args[0] as? EnumVarConcrete ?: run {
            LogProcessor.error("Bossbar color/style requires a compile-time enum value")
            return@withAdapters
        }
        captureCommandResult(context) { Command.buildAll("bossbar set", templateField(receiver as DataTemplateObject, "id"), "style", style.value.dataAsString()) }
    }

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
        if (value is SelectorVar && value.isPlayer()) return value
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
