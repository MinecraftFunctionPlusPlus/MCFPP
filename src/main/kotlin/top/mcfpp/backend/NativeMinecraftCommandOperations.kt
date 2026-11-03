package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.RangeVar
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.Pos2Var
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.core.lang.context.BoundPositionVar
import top.mcfpp.core.lang.context.ContextAccessException
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.lib.SbObject

internal fun captureCommandResult(context: NativeCallContext, build: (List<Var<*>>) -> Command) = context.withArguments { args ->
    if (!FloatProviders.enabled && args.any { it is MCFloat && StorageAccess.snapshot(it) == null }) {
        LogProcessor.error("Dynamic float command arguments require a number-provider target")
        return@withArguments
    }
    val errors = top.mcfpp.Project.errorCount
    val operation = build(args)
    if (top.mcfpp.Project.errorCount != errors || args.any { it.isError }) return@withArguments
    val result = (context.declaredReturnType.buildUnConcrete(TempPool.getVarIdentify()) as DataTemplateObject).apply { isTemp = true }
    val binding = StorageAccess.ensure(result)
    Function.addCommand(Commands.dataSetValue(binding.path, CompoundTag("result" to IntTag(0), "success" to ByteTag(0))))
    StorageAccess.publishNbt(result)
    val command = Command("execute store result").build(binding.path.memberIndex("result").toCommandPart())
        .build("int 1 store success").build(binding.path.memberIndex("success").toCommandPart()).build("byte 1 run").build(operation)
    Function.addCommands(command.buildMacroFunction())
    binding.data.facts.initialize(binding.place.field("result"), top.mcfpp.analysis.ValueFacts(top.mcfpp.analysis.TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), top.mcfpp.analysis.ValueKnowledge.Unknown))
    binding.data.facts.initialize(binding.place.field("success"), top.mcfpp.analysis.ValueFacts(top.mcfpp.analysis.TypeKnowledge.Exact(MCFPPBaseType.Bool.typeId), top.mcfpp.analysis.ValueKnowledge.Unknown))
    context.publishResult(result)
}

object NativeMinecraftCommandOperations {
    fun triggerEnable(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = requireEntityTarget(args[0], players = true) ?: return@withAdapters
        Function.addCommands(nativeEntityCommand("scoreboard players enable", players,
            templateField(receiver as DataTemplateObject, "id")).buildMacroFunction())
    }

    fun entitySetAttributeBase(context: NativeCallContext) = entityAttribute(context) { receiver, args ->
        nativeEntityCommand("attribute", receiver, args[0], "base set").buildMacro(args[1])
    }
    fun entityAddAttributeModifier(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val target = attributeSelector(receiver) ?: return@withAdapters
        val mode = context.argumentSnapshot(0)?.let { StorageAccess.restore(args[0].type, it, args[0].identifier) } as? EnumVar
        if (mode == null) {
            LogProcessor.error("Attribute modifier operations require a complete compile-time enum value")
            return@withAdapters
        }
        val modifier = args[2] as DataTemplateObject
        captureCommandResult(context) {
            nativeEntityCommand("attribute", target, args[1], "modifier add", templateField(modifier, "id"))
                .buildMacro(templateField(modifier, "amount")).build(mode.value.identifier)
        }
    }

    fun entityGetAttributeBase(context: NativeCallContext) = entityAttribute(context) { receiver, args ->
        nativeEntityCommand("attribute", receiver, args[0], "base get", args[1])
    }
    fun entityGetAttribute(context: NativeCallContext) = entityAttribute(context) { receiver, args ->
        nativeEntityCommand("attribute", receiver, args[0], "get", args[1])
    }
    fun entityRemoveAttributeModifier(context: NativeCallContext) = entityAttribute(context) { receiver, args ->
        nativeEntityCommand("attribute", receiver, args[0], "modifier remove", templateField(args[1] as DataTemplateObject, "id"))
    }
    fun entityGetAttributeModifier(context: NativeCallContext) = entityAttribute(context) { receiver, args ->
        nativeEntityCommand("attribute", receiver, args[0], "modifier value get", templateField(args[1] as DataTemplateObject, "id"), args[2])
    }

    private fun entityAttribute(context: NativeCallContext, build: (Var<*>, List<Var<*>>) -> Command) = context.withAdapters { receiver, _ ->
        val target = attributeSelector(receiver) ?: return@withAdapters
        captureCommandResult(context) { args -> build(target, args) }
    }

    private fun attributeSelector(receiver: Var<*>): Var<*>? = requireEntityTarget(receiver, single = true)

    fun playerTell(context: NativeCallContext) = playerMessage(context, "tell")
    fun playerWhisper(context: NativeCallContext) = playerMessage(context, "w")

    private fun playerMessage(context: NativeCallContext, verb: String) = context.withAdapters { receiver, args ->
        val sender = playerSelector(receiver) ?: return@withAdapters
        val targets = playerSelector(args[0]) ?: return@withAdapters
        val message = args[1] as MCString
        captureCommandResult(context) { nativeEntityCommand("execute as", sender, "run", verb, targets, message, changesExecutor = true) }
    }

    fun entityTeleportToEntity(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver) == null) return@withAdapters
        val destination = requireEntityTarget(args[0], single = true) ?: return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("tp", receiver, destination) }
    }

    /** Public free tp preserves ordinary targets and scoped handles at one operand boundary. */
    fun teleportEntities(context: NativeCallContext) = captureCommandResult(context) { args ->
        val target = args.getOrNull(0)
        if (target !is EntityTargetAccess) {
            LogProcessor.error("tp requires an entity target")
            return@captureCommandResult Command("")
        }
        val position = args.getOrNull(1)
        val destination = if (position is BoundPositionVar) position else {
            val coordinate = position?.let {
                context.argumentSnapshot(1)?.let { snapshot -> StorageAccess.restore(it.type, snapshot, it.identifier) }
            } as? Pos3Var
            if (coordinate == null) {
                LogProcessor.error("tp requires a complete pos3 coordinate or a bound context position")
                return@captureCommandResult Command("")
            }
            if (coordinate.value.any { it.prefix == "^" } && coordinate.value.any { it.prefix != "^" }) {
                LogProcessor.error("tp coordinates cannot mix local and world-coordinate prefixes")
                return@captureCommandResult Command("")
            }
            coordinate
        }
        try {
            EntityContextCommandOperations.command(listOf(args[0], destination)) { parts ->
                nativeEntityCommand("tp", parts[0], parts[1])
            }
        } catch (failure: ContextAccessException) {
            LogProcessor.error(failure.message ?: "Cannot resolve tp context operands")
            Command("")
        }
    }

    fun entityTeleportToPosition(context: NativeCallContext) = teleportCoordinates(context) { receiver, args ->
        nativeEntityCommand("tp", receiver, args[0])
    }
    fun entityTeleportWithRotation(context: NativeCallContext) = teleportCoordinates(context) { receiver, args ->
        nativeEntityCommand("tp", receiver, args[0], args[1])
    }
    fun entityTeleportFacingPosition(context: NativeCallContext) = teleportCoordinates(context) { receiver, args ->
        nativeEntityCommand("tp", receiver, args[0], "facing", args[1])
    }
    fun entityTeleportFacingEntity(context: NativeCallContext) = context.withAdapters { _, args ->
        val destination = requireEntityTarget(args[1], single = true) ?: return@withAdapters
        val anchor = context.argumentSnapshot(2)?.let { StorageAccess.restore(args[2].type, it, args[2].identifier) } as? EnumVar
        if (anchor == null) {
            LogProcessor.error("Entity teleport anchor requires a complete compile-time enum value")
            return@withAdapters
        }
        teleportCoordinates(context) { receiver, restored ->
            nativeEntityCommand("tp", receiver, restored[0], "facing entity", destination, anchor.value.identifier)
        }
    }

    private fun teleportCoordinates(context: NativeCallContext, build: (Var<*>, List<Var<*>>) -> Command) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver) == null) return@withAdapters
        val restored = args.toMutableList()
        for ((index, argument) in args.withIndex()) {
            if (argument.type != MCFPPBaseType.Pos2 && argument.type != MCFPPBaseType.Pos3) continue
            val coordinate = context.argumentSnapshot(index)?.let { StorageAccess.restore(argument.type, it, argument.identifier) }
            if (coordinate == null) {
                LogProcessor.error("Entity teleport coordinates require complete compile-time values")
                return@withAdapters
            }
            val invalid = when (coordinate) {
                is Pos2Var -> coordinate.value.any { it.prefix == "^" }
                is Pos3Var -> coordinate.value.any { it.prefix == "^" } && coordinate.value.any { it.prefix != "^" }
                else -> true
            }
            if (invalid) {
                LogProcessor.error("Entity teleport coordinates have invalid local-coordinate prefixes")
                return@withAdapters
            }
            restored[index] = coordinate
        }
        captureCommandResult(context) { build(receiver, restored) }
    }

    fun playerClearAll(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val players = playerSelector(receiver) ?: return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("clear", players) }
    }
    fun playerClearItems(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(receiver) ?: return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("clear", players, args[0], args[1]) }
    }
    fun playerSetGamemode(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(receiver) ?: return@withAdapters
        val mode = (args[0] as? EnumVar)?.takeIf { StorageAccess.snapshot(it) != null } ?: run {
            LogProcessor.error("Gamemode requires a compile-time enum value")
            return@withAdapters
        }
        captureCommandResult(context) { nativeEntityCommand("gamemode", mode.value.identifier, players) }
    }
    fun entityRide(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver, single = true) == null) return@withAdapters
        val target = requireEntityTarget(args[0], single = true) ?: return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("ride", receiver, "mount", target) }
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
            if (mode == "everything") nativeEntityCommand("advancement", operation, players, mode)
            else nativeEntityCommand("advancement", operation, players, mode, templateField(args[0] as DataTemplateObject, "id"))
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
        captureCommandResult(context) { nativeEntityCommand("xp", operation, players, args[0], unit) }
    }
    private fun playerXpQuery(context: NativeCallContext, unit: String) = context.withAdapters { receiver, _ ->
        val player = playerSelector(receiver) ?: return@withAdapters
        if (requireEntityTarget(player, single = true) == null) return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("xp query", player, unit) }
    }

    fun entityStopRide(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        if (requireEntityTarget(receiver, single = true) == null) return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("ride", receiver, "dismount") }
    }

    fun entityEffect(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver) == null) return@withAdapters
        val hide = effectBooleanWord(context, args[3], 3)
        captureCommandResult(context) { nativeEntityCommand("effect give", receiver, templateField(args[0] as DataTemplateObject, "id"), args[1], args[2], hide) }
    }
    fun entityEffectInfinite(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver) == null) return@withAdapters
        val hide = effectBooleanWord(context, args[2], 2)
        captureCommandResult(context) { nativeEntityCommand("effect give", receiver, templateField(args[0] as DataTemplateObject, "id"), "infinite", args[1], hide) }
    }

    private fun effectBooleanWord(context: NativeCallContext, value: Var<*>, index: Int): MCString {
        var snapshot = context.argumentSnapshot(index)
        while (snapshot is CompilerValue.Typed) snapshot = snapshot.payload
        if (snapshot is CompilerValue.Bool) return MCString(StringTag(snapshot.value.toString()))
        val score = StorageAccess.read(value) as ScoreBool
        val word = MCString(TempPool.getVarIdentify()).apply { isTemp = true }
        val binding = StorageAccess.bindIncomingParameter(word)
        Function.addCommand(Commands.dataSetValue(binding.path, StringTag("false")))
        Function.addCommand(Command("execute if").build(score.toCommandPart()).build("run").build(Commands.dataSetValue(binding.path, StringTag("true"))))
        return word
    }

    fun entityClearAllEffects(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        if (requireEntityTarget(receiver) == null) return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("effect clear", receiver) }
    }
    fun entityClearEffect(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver) == null) return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("effect clear", receiver, templateField(args[0] as DataTemplateObject, "id")) }
    }

    fun entityJoinTeam(context: NativeCallContext) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver) == null) return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("team join", templateField(args[0] as DataTemplateObject, "id"), receiver) }
    }
    fun entityLeaveTeam(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        if (requireEntityTarget(receiver) == null) return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("team leave", receiver) }
    }

    fun entityAddTag(context: NativeCallContext) = entityTag(context, "add")
    fun entityRemoveTag(context: NativeCallContext) = entityTag(context, "remove")
    fun entityListTag(context: NativeCallContext) = entityTag(context, "list")

    private fun entityTag(context: NativeCallContext, operation: String) = context.withAdapters { receiver, args ->
        if (requireEntityTarget(receiver) == null) return@withAdapters
        captureCommandResult(context) {
            if (operation == "list") nativeEntityCommand("tag", receiver, operation)
            else nativeEntityCommand("tag", receiver, operation, args[0])
        }
    }

    fun randomValue(context: NativeCallContext) = randomNumber(context, "value")
    fun randomRoll(context: NativeCallContext) = randomNumber(context, "roll")
    fun randomSequenceValue(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        randomNumber(context, "value", templateField(receiver as DataTemplateObject, "id"))
    }
    fun randomSequenceRoll(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        randomNumber(context, "roll", templateField(receiver as DataTemplateObject, "id"))
    }

    private fun randomNumber(context: NativeCallContext, mode: String, sequence: Var<*>? = null) = context.withArguments { args ->
        val range = context.argumentSnapshot(0)?.let { StorageAccess.restore(args[0].type, it, args[0].identifier) } as? RangeVar
        if (range == null || range.value.first !is Int || range.value.second !is Int) {
            LogProcessor.error("Random commands require a complete range with integer endpoints")
            return@withArguments
        }
        val result = MCInt().apply { sbObject = SbObject.MCFPP_TEMP; isTemp = true }
        val command = nativeEntityCommand("execute store result score", result.name, result.sbObject, "run random", mode, range)
        if (sequence != null) command.build(sequence.toCommandPart())
        Function.addCommands(command.buildMacroFunction())
        context.publishResult(StorageAccess.publishScore(result, top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.sbObject.toString())))
    }

    fun randomReset(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val flags = randomFlags(context) ?: return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("random reset", templateField(receiver as DataTemplateObject, "id"), args[2], flags.first, flags.second) }
    }
    fun randomResetAllSequences(context: NativeCallContext) = context.withArguments { args ->
        val flags = randomFlags(context) ?: return@withArguments
        captureCommandResult(context) { nativeEntityCommand("random reset *", args[2], flags.first, flags.second) }
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
        val difficulty = (args[0] as? EnumVar)?.takeIf { StorageAccess.snapshot(it) != null } ?: run {
            LogProcessor.error("Difficulty requires a compile-time enum value")
            return@withArguments
        }
        captureCommandResult(context) { nativeEntityCommand("difficulty", difficulty.value.identifier) }
    }
    fun worldSetWeather(context: NativeCallContext) = context.withArguments { args ->
        val weather = (args[0] as? EnumVar)?.takeIf { StorageAccess.snapshot(it) != null } ?: run {
            LogProcessor.error("Weather requires a compile-time enum value")
            return@withArguments
        }
        captureCommandResult(context) { nativeEntityCommand("weather", weather.value.identifier, args[1]) }
    }

    fun bossbarAdd(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { nativeEntityCommand("bossbar add", templateField(receiver as DataTemplateObject, "id"), templateField(receiver, "name")) }
    }
    fun bossbarRemove(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { nativeEntityCommand("bossbar remove", templateField(receiver as DataTemplateObject, "id")) }
    }
    fun bossbarList(context: NativeCallContext) = context.withAdapters { _, _ ->
        captureCommandResult(context) { Command("bossbar list") }
    }
    fun bossbarSetColor(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val color = (args[0] as? EnumVar)?.takeIf { StorageAccess.snapshot(it) != null } ?: run {
            LogProcessor.error("Bossbar color/style requires a compile-time enum value")
            return@withAdapters
        }
        captureCommandResult(context) { nativeEntityCommand("bossbar set", templateField(receiver as DataTemplateObject, "id"), "color", color.value.dataAsString()) }
    }
    fun bossbarSetName(context: NativeCallContext) = context.withAdapters { receiver, args ->
        captureCommandResult(context) { nativeEntityCommand("bossbar set", templateField(receiver as DataTemplateObject, "id"), "name", args[0]) }
    }
    fun bossbarSetVisiblePlayers(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(args[0]) ?: return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("bossbar set", templateField(receiver as DataTemplateObject, "id"), "players", players) }
    }
    fun bossbarSetStyle(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val style = (args[0] as? EnumVar)?.takeIf { StorageAccess.snapshot(it) != null } ?: run {
            LogProcessor.error("Bossbar color/style requires a compile-time enum value")
            return@withAdapters
        }
        captureCommandResult(context) { nativeEntityCommand("bossbar set", templateField(receiver as DataTemplateObject, "id"), "style", style.value.dataAsString()) }
    }

    fun bossbarGetMax(context: NativeCallContext) = bossbarRead(context, "max")
    fun bossbarGetValue(context: NativeCallContext) = bossbarRead(context, "value")
    fun bossbarGetVisible(context: NativeCallContext) = bossbarRead(context, "visible")
    fun bossbarSetMax(context: NativeCallContext) = bossbarWriteInt(context, "max")
    fun bossbarSetValue(context: NativeCallContext) = bossbarWriteInt(context, "value")

    private fun bossbarRead(context: NativeCallContext, attribute: String) = context.withAdapters { receiver, _ ->
        val id = templateField(receiver as DataTemplateObject, "id")
        val result = context.declaredReturnType.buildUnConcrete(TempPool.getVarIdentify()).apply { isTemp = true }
        val score = when (result) {
            is MCInt -> "${result.name} ${result.sbObject}"
            is ScoreBool -> "${result.name} ${result.boolObject}"
            else -> error("Unsupported BossBar property type")
        }
        Function.addCommands(Command("execute store result score $score run bossbar get").build(id.toCommandPart())
            .build(attribute).buildMacroFunction())
        when (result) {
            is MCInt -> StorageAccess.publishScore(result, top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
            is ScoreBool -> StorageAccess.publishBoolean(result, top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.boolObject.toString()))
        }
        context.publishResult(result)
    }

    private fun bossbarWriteInt(context: NativeCallContext, attribute: String) = context.withAdapters { receiver, args ->
        val id = templateField(receiver as DataTemplateObject, "id")
        val value = StorageAccess.read(args.single()) as MCInt
        var snapshot = context.argumentSnapshot(0)
        while (snapshot is CompilerValue.Typed) snapshot = snapshot.payload
        val constant = snapshot as? CompilerValue.Integral
        val command = if (constant != null) Command("bossbar set").build(id.toCommandPart()).build(attribute).build(constant.value.toString())
        else Command("execute store result bossbar").build(id.toCommandPart()).build(attribute).build("run").build(Commands.sbPlayerGet(value))
        Function.addCommands(command.buildMacroFunction())
        context.publishResult(args.single())
    }

    fun bossbarSetVisible(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val id = templateField(receiver as DataTemplateObject, "id")
        val value = StorageAccess.read(args.single()) as ScoreBool
        var snapshot = context.argumentSnapshot(0)
        while (snapshot is CompilerValue.Typed) snapshot = snapshot.payload
        val constant = snapshot as? CompilerValue.Bool
        fun operation(flag: Boolean) = Command("bossbar set").build(id.toCommandPart()).build("visible $flag")
        if (constant != null) Function.addCommands(operation(constant.value).buildMacroFunction())
        else for (flag in listOf(true, false)) {
            Function.addCommands(Command("execute ${if (flag) "if" else "unless"} score ${value.name} ${value.boolObject} matches 1 run")
                .build(operation(flag)).buildMacroFunction())
        }
        context.publishResult(args.single())
    }

    fun worldborderAdd(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("worldborder add", args[0], args[1]) }
    fun worldborderSetCenter(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("worldborder center", args[0]) }
    fun worldborderSetDamageAmount(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("worldborder damage amount", args[0]) }
    fun worldborderSetDamageBuffer(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("worldborder damage buffer", args[0]) }
    fun worldborderSetSize(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("worldborder set", args[0], args[1]) }
    fun worldborderSetWarningDistance(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("worldborder warning distance", args[0]) }
    fun worldborderSetWarningTime(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("worldborder warning time", args[0]) }

    fun op(context: NativeCallContext) = context.withArguments { args ->
        val players = playerSelector(args[0]) ?: return@withArguments
        captureCommandResult(context) { nativeEntityCommand("op", players) }
    }
    fun deop(context: NativeCallContext) = context.withArguments { args ->
        val players = playerSelector(args[0]) ?: return@withArguments
        captureCommandResult(context) { nativeEntityCommand("deop", players) }
    }
    fun recipeGiveAll(context: NativeCallContext) = context.withArguments { args ->
        val players = playerSelector(args[0]) ?: return@withArguments
        captureCommandResult(context) { nativeEntityCommand("recipe give", players, "*") }
    }
    fun recipeTakeAll(context: NativeCallContext) = context.withArguments { args ->
        val players = playerSelector(args[0]) ?: return@withArguments
        captureCommandResult(context) { nativeEntityCommand("recipe take", players, "*") }
    }
    fun recipeGive(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(args[0]) ?: return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("recipe give", players, templateField(receiver as DataTemplateObject, "id")) }
    }
    fun recipeTake(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val players = playerSelector(args[0]) ?: return@withAdapters
        captureCommandResult(context) { nativeEntityCommand("recipe take", players, templateField(receiver as DataTemplateObject, "id")) }
    }
    private fun playerSelector(value: Var<*>): Var<*>? = requireEntityTarget(value, players = true)

    fun teamRegister(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { nativeEntityCommand("team add", templateField(receiver as DataTemplateObject, "id"), templateField(receiver, "displayName")) }
    }
    fun teamUnregister(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { nativeEntityCommand("team remove", templateField(receiver as DataTemplateObject, "id")) }
    }
    fun teamClear(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        captureCommandResult(context) { nativeEntityCommand("team empty", templateField(receiver as DataTemplateObject, "id")) }
    }
    private fun templateField(receiver: DataTemplateObject, name: String): Var<*> {
        val field = DataTemplate.getField(receiver, name)!!
        return if (field is PropertyVar) field.get() else field
    }

    fun datapackDisable(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("datapack", "disable", args[0]) }
    fun datapackEnable(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("datapack enable", args[0]) }
    fun datapackEnableFirst(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("datapack enable", args[0], "first") }
    fun datapackEnableLast(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("datapack enable", args[0], "last") }
    fun datapackEnableBefore(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("datapack enable", args[0], "before", args[1]) }
    fun datapackEnableAfter(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("datapack enable", args[0], "after", args[1]) }
    fun datapackListAll(context: NativeCallContext) = captureCommandResult(context) { nativeEntityCommand("datapack list") }
    fun datapackListEnabled(context: NativeCallContext) = captureCommandResult(context) { nativeEntityCommand("datapack list enabled") }
    fun datapackListAvailable(context: NativeCallContext) = captureCommandResult(context) { nativeEntityCommand("datapack list available") }
    fun debugStart(context: NativeCallContext) = captureCommandResult(context) { Command("debug start") }
    fun debugStop(context: NativeCallContext) = captureCommandResult(context) { Command("debug stop") }
}
