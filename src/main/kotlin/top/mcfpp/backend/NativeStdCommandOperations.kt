package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.command.FloatProviders
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

object NativeStdCommandOperations {
    fun fillKeep(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("fill", args[0], args[1], "keep")
    }

    fun fillReplace(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("fill", args[0], args[1])
    }

    fun fillReplaceFiltered(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("fill", args[0], args[1], "replace", args[2])
    }

    fun fillReplaceFilteredMode(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("fill", args[0], args[1], "replace", args[2], args[3])
    }

    fun fillBiome(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("fillbiome", args[0], args[1])
    }

    fun fillBiomeReplace(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("fillbiome", args[0], args[1], "replace", args[2])
    }

    fun forceload(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("forceload", "add", args[0], args[1])
    }

    fun forceloadRemove(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("forceload", "remove", args[0], args[1])
    }

    fun forceloadRemoveAll(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("forceload remove all")
    }

    fun forceloadQuery(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("forceload query", args[0], args[1])
    }

    fun forceloadQueryAll(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("forceload query")
    }

    fun give(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("give", args[0], args[1])
    }

    fun giveCount(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("give", args[0], args[1], args[2])
    }

    fun help(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("help")
    }

    fun helpCommand(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("help", args[0])
    }

    fun jfrStart(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("jfr start")
    }

    fun jfrStop(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("jfr stop")
    }

    fun kick(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("kick", args[0], args[1])
    }

    fun kill(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("kill")
    }

    fun killTargets(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("kill", args[0])
    }

    fun list(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("list")
    }

    fun locateStructure(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("locate", args[0])
    }

    fun locateBiome(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("locate", args[0])
    }

    fun locatePoi(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("locate", args[0])
    }

    fun lootGive(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("loot give", args[0], "loot", args[1])
    }

    fun lootInsert(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot insert", args[0], "loot", args[1])
    }

    fun lootSpawn(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot spawn", args[0], "loot", args[1])
    }

    fun lootReplaceBlock(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace block", args[0], args[1], "loot", args[2])
    }

    fun lootReplaceEntity(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace entity", args[0], args[1], "loot", args[2])
    }

    fun lootReplaceBlockCount(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace block", args[0], args[1], args[2], "loot", args[3])
    }

    fun lootReplaceEntityCount(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace entity", args[0], args[1], args[2], "loot", args[3])
    }

    fun lootGiveFish(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("loot give", args[0], "fish", args[1], args[2])
    }

    fun lootGiveFishUsing(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("loot give", args[0], "fish", args[1], args[2], args[3])
    }

    fun lootGiveFishUsingMainHand(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("loot give", args[0], "fish", args[1], args[2], "mainhand")
    }

    fun lootGiveFishUsingOffHand(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("loot give", args[0], "fish", args[1], args[2], "offhand")
    }

    fun lootInsertFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot insert", args[0], "fish", args[1], args[2])
    }

    fun lootInsertFishUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot insert", args[0], "fish", args[1], args[2], args[3])
    }

    fun lootInsertFishUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot insert", args[0], "fish", args[1], args[2], "mainhand")
    }

    fun lootInsertFishUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot insert", args[0], "fish", args[1], args[2], "offhand")
    }

    fun lootSpawnFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot spawn", args[0], "fish", args[1], args[2])
    }

    fun lootSpawnFishUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot spawn", args[0], "fish", args[1], args[2], args[3])
    }

    fun lootSpawnFishUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot spawn", args[0], "fish", args[1], args[2], "mainhand")
    }

    fun lootSpawnFishUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot spawn", args[0], "fish", args[1], args[2], "offhand")
    }

    fun lootReplaceBlockFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace block", args[0], args[1], "fish", args[2], args[3])
    }

    fun lootReplaceEntityFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace entity", args[0], args[1], "fish", args[2], args[3])
    }

    fun lootReplaceBlockCountFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace block", args[0], args[1], args[2], "fish", args[3], args[4], "loot")
    }

    fun lootReplaceEntityCountFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace entity", args[0], args[1], args[2], "fish", args[3], args[4], "loot")
    }

    fun lootGiveKill(context: NativeCallContext) = captureSingleEntityCommandResult(context, 1, playersIndex = 0) { args ->
        nativeEntityCommand("loot give", args[0], "kill", args[1])
    }

    fun lootInsertKill(context: NativeCallContext) = captureSingleEntityCommandResult(context, 1) { args ->
        nativeEntityCommand("loot insert", args[0], "kill", args[1])
    }

    fun lootSpawnKill(context: NativeCallContext) = captureSingleEntityCommandResult(context, 1) { args ->
        nativeEntityCommand("loot spawn", args[0], "kill", args[1])
    }

    fun lootReplaceBlockKill(context: NativeCallContext) = captureSingleEntityCommandResult(context, 2) { args ->
        nativeEntityCommand("loot replace block", args[0], args[1], "kill", args[2])
    }

    fun lootReplaceEntityKill(context: NativeCallContext) = captureSingleEntityCommandResult(context, 2) { args ->
        nativeEntityCommand("loot replace entity", args[0], args[1], "kill", args[2])
    }

    fun lootReplaceBlockCountKill(context: NativeCallContext) = captureSingleEntityCommandResult(context, 3) { args ->
        nativeEntityCommand("loot replace block", args[0], args[1], args[2], "kill", args[3])
    }

    fun lootReplaceEntityCountKill(context: NativeCallContext) = captureSingleEntityCommandResult(context, 3) { args ->
        nativeEntityCommand("loot replace entity", args[0], args[1], args[2], "kill", args[3])
    }

    fun lootGiveMine(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("loot give", args[0], "mine", args[1])
    }

    fun lootGiveMineUsing(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("loot give", args[0], "mine", args[1], args[2])
    }

    fun lootGiveMineUsingMainHand(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("loot give", args[0], "mine", args[1], "mainhand")
    }

    fun lootGiveMineUsingOffHand(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("loot give", args[0], "mine", args[1], "offhand")
    }

    fun lootInsertMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot insert", args[0], "mine", args[1])
    }

    fun lootInsertMineUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot insert", args[0], "mine", args[1], args[2])
    }

    fun lootInsertMineUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot insert", args[0], "mine", args[1], "mainhand")
    }

    fun lootInsertMineUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot insert", args[0], "mine", args[1], "offhand")
    }

    fun lootSpawnMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot spawn", args[0], "mine", args[1])
    }

    fun lootSpawnMineUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot spawn", args[0], "mine", args[1], args[2])
    }

    fun lootSpawnMineUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot spawn", args[0], "mine", args[1], "mainhand")
    }

    fun lootSpawnMineUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot spawn", args[0], "mine", args[1], "offhand")
    }

    fun lootReplaceBlockMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace block", args[0], args[1], "mine", args[2])
    }

    fun lootReplaceEntityMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace entity", args[0], args[1], "mine", args[2])
    }

    fun lootReplaceBlockCountMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace block", args[0], args[1], args[2], "mine", args[3])
    }

    fun lootReplaceEntityCountMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("loot replace entity", args[0], args[1], args[2], "mine", args[3])
    }

    fun me(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("me", args[0])
    }

    fun pardon(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("pardon", args[0])
    }

    fun pardonIp(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("pardon-ip", args[0])
    }

    fun particle(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("particle", args[0])
    }

    fun particleAt(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("particle", args[0], args[1])
    }

    fun particleMoving(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("particle", args[0], args[1], args[2], args[3], args[4])
    }

    fun particleForce(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("particle", args[0], args[1], args[2], args[3], args[4], "force")
    }

    fun particleForPlayers(context: NativeCallContext) = capturePlayerCommandResult(context, 5) { args ->
        nativeEntityCommand("particle", args[0], args[1], args[2], args[3], args[4], "normal", args[5])
    }

    fun particleForceForPlayers(context: NativeCallContext) = capturePlayerCommandResult(context, 5) { args ->
        nativeEntityCommand("particle", args[0], args[1], args[2], args[3], args[4], "force", args[5])
    }

    fun perfStart(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("perf start")
    }

    fun perfStop(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("perf stop")
    }

    fun publish(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("publish", args[0], args[1], args[2])
    }

    fun reload(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("reload")
    }

    fun save(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("save")
    }

    fun saveAll(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("save-all")
    }

    fun saveOff(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("save-off")
    }

    fun saveOn(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("save-on")
    }

    fun say(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("say", args[0])
    }

    fun setblock(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("setblock", args[0], args[1], args[2])
    }

    fun setidletimeout(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("setidletimeout", args[0])
    }

    fun setworldspawn(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("setworldspawn", args[0], args[1])
    }

    fun spreadplayers(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("spreadplayers", args[0], args[1], args[2], args[3], args[4])
    }

    fun spreadplayersUnder(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("spreadplayers", args[0], args[1], "under", args[2], args[3], args[4], args[5])
    }

    fun stop(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("stop")
    }

    fun stopAllSound(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("stopsound", args[0])
    }

    fun stopSound(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("stopsound", args[0], args[1])
    }

    fun stopSoundNamed(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("stopsound", args[0], "*", args[1])
    }

    fun stopSoundTyped(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("stopsound", args[0], args[1], args[2])
    }

    fun summon(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("summon", args[0], args[1])
    }

    fun summonWithNbt(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("summon", args[0], args[1], args[2])
    }

    fun teammsg(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("teammsg", args[0])
    }

    fun tellraw(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("tellraw", args[0], args[1])
    }

    fun titleClear(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("title", args[0], "clear")
    }

    fun titleReset(context: NativeCallContext) = capturePlayerCommandResult(context) { args ->
        nativeEntityCommand("title", args[0], "reset")
    }

    fun tm(context: NativeCallContext) = captureCommandResult(context) { args ->
        nativeEntityCommand("tm", args[0])
    }

    fun transfer(context: NativeCallContext) = capturePlayerCommandResult(context, 2) { args ->
        nativeEntityCommand("transfer", args[0], args[1], args[2])
    }

    fun seed(context: NativeCallContext) = captureCommandResult(context) { Command("seed") }

    fun damage(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[2])
    }

    fun damageAt(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[3], "at", args[2])
    }

    fun damageBy(context: NativeCallContext) = emit(context) { args ->
        val attacker = requireEntityTarget(args[2], single = true) ?: return@emit Command()
        damageCommand(args[0], args[1], args[3], "by", attacker)
    }

    fun damageFrom(context: NativeCallContext) = emit(context) { args ->
        val attacker = requireEntityTarget(args[2], single = true) ?: return@emit Command()
        val origin = requireEntityTarget(args[3], single = true) ?: return@emit Command()
        damageCommand(args[0], args[1], args[4], "by", attacker, "from", origin)
    }

    private fun damageCommand(target: Var<*>, amount: Var<*>, kind: Var<*>, vararg extras: Any): Command {
        val entity = requireEntityTarget(target) ?: return Command()
        val multi = !(entity as EntityTargetAccess).entityFacts.selectingSingleEntity
        return if (multi) nativeEntityCommand("execute as", entity, "run damage @s", amount, kind, *extras, changesExecutor = true)
        else nativeEntityCommand("damage", entity, amount, kind, *extras)
    }

    fun cloneArea(context: NativeCallContext) = emit(context) { args ->
        val mask = cloneEnum(context, args, 2) ?: return@emit Command()
        val operation = cloneEnum(context, args, 3) ?: return@emit Command()
        nativeEntityCommand("clone", args[0], args[1], mask, operation)
    }

    fun cloneStrictArea(context: NativeCallContext) = emit(context) { args ->
        val mask = cloneEnum(context, args, 2) ?: return@emit Command()
        val operation = cloneEnum(context, args, 3) ?: return@emit Command()
        nativeEntityCommand("clone", args[0], args[1], "strict", mask, operation)
    }

    fun cloneFiltered(context: NativeCallContext) = emit(context) { args ->
        val operation = cloneEnum(context, args, 3) ?: return@emit Command()
        nativeEntityCommand("clone", args[0], args[1], "filtered", args[2], operation)
    }

    fun cloneStrictFiltered(context: NativeCallContext) = emit(context) { args ->
        val operation = cloneEnum(context, args, 3) ?: return@emit Command()
        nativeEntityCommand("clone", args[0], args[1], "strict filtered", args[2], operation)
    }

    private fun cloneEnum(context: NativeCallContext, args: List<Var<*>>, index: Int): String? {
        val value = args[index]
        val snapshot = context.argumentSnapshot(index)
        val member = snapshot?.let { StorageAccess.restore(value.type, it, value.identifier) } as? EnumVar
        if (member != null) return member.value.identifier
        LogProcessor.error("Clone enum command arguments require a complete enum value")
        return null
    }

    fun enchant(context: NativeCallContext) = emit(context) { args ->
        nativeEntityCommand("enchant", args[0], args[1], args[2], args[3])
    }

    fun placeFeature(context: NativeCallContext) = emit(context) { args ->
        nativeEntityCommand("place feature", args[0])
    }

    fun placeFeatureAt(context: NativeCallContext) = emit(context) { args ->
        nativeEntityCommand("place feature", args[0], args[1])
    }

    fun placeJigsaw(context: NativeCallContext) = emit(context) { args ->
        nativeEntityCommand("place jigsaw", args[0], args[1], args[2])
    }

    fun placeJigsawAt(context: NativeCallContext) = emit(context) { args ->
        nativeEntityCommand("place jigsaw", args[0], args[1], args[2], args[3])
    }

    fun placeStructure(context: NativeCallContext) = emit(context) { args ->
        nativeEntityCommand("place structure", args[0])
    }

    fun placeStructureAt(context: NativeCallContext) = emit(context) { args ->
        nativeEntityCommand("place structure", args[0], args[1])
    }

    fun placeTemplate(context: NativeCallContext) = emit(context) { args ->
        nativeEntityCommand("place", args[0], args[1], args[2], args[3], args[4], args[5])
    }

    fun placeTemplateStrict(context: NativeCallContext) = emit(context) { args ->
        nativeEntityCommand("place", args[0], args[1], args[2], args[3], args[4], args[5], "strict")
    }

    fun playsound(context: NativeCallContext) = emit(context, playersIndex = 2) { args ->
        nativeEntityCommand("playsound", args[0], args[1], args[2], args[3], args[4], args[5], args[6])
    }

    fun titleTitle(context: NativeCallContext) = emit(context, playersIndex = 0) { args ->
        nativeEntityCommand("title", args[0], args[2], args[1])
    }

    fun titleSet(context: NativeCallContext) = emit(context, playersIndex = 0) { args ->
        nativeEntityCommand("title", args[0], "times", args[1], args[2], args[3])
    }

    private fun emit(context: NativeCallContext, playersIndex: Int? = null,
                     build: (List<Var<*>>) -> Command) = context.withArguments { args ->
        if (playersIndex != null && requireEntityTarget(args[playersIndex], players = true) == null) return@withArguments
        if (!FloatProviders.enabled && args.any { it is MCFloat && StorageAccess.snapshot(it) == null }) {
            LogProcessor.error("Dynamic float command arguments require a number-provider target")
            return@withArguments
        }
        val errors = top.mcfpp.Project.errorCount
        val command = build(args)
        if (top.mcfpp.Project.errorCount == errors && args.none { it.isError })
            command.buildMacroFunction().forEach { Function.addCommand(it) }
    }
}
