package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.entity.EntityVar
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

object NativeStdCommandOperations {
    fun fillKeep(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("fill", args[0], args[1], "keep")
    }

    fun fillReplace(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("fill", args[0], args[1])
    }

    fun fillReplaceFiltered(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("fill", args[0], args[1], "replace", args[2])
    }

    fun fillReplaceFilteredMode(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("fill", args[0], args[1], "replace", args[2], args[3])
    }

    fun fillBiome(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("fillbiome", args[0], args[1])
    }

    fun fillBiomeReplace(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("fillbiome", args[0], args[1], "replace", args[2])
    }

    fun forceload(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("forceload", "add", args[0], args[1])
    }

    fun forceloadRemove(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("forceload", "remove", args[0], args[1])
    }

    fun forceloadRemoveAll(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("forceload remove all")
    }

    fun forceloadQuery(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("forceload query", args[0], args[1])
    }

    fun forceloadQueryAll(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("forceload query")
    }

    fun give(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("give", args[0], args[1])
    }

    fun giveCount(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("give", args[0], args[1], args[2])
    }

    fun help(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("help")
    }

    fun helpCommand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("help", args[0])
    }

    fun jfrStart(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("jfr start")
    }

    fun jfrStop(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("jfr stop")
    }

    fun kick(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("kick", args[0], args[1])
    }

    fun kill(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("kill")
    }

    fun killTargets(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("kill", args[0])
    }

    fun list(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("list")
    }

    fun locateStructure(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("locate", args[0])
    }

    fun locateBiome(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("locate", args[0])
    }

    fun locatePoi(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("locate", args[0])
    }

    fun lootGive(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "loot", args[1])
    }

    fun lootInsert(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "loot", args[1])
    }

    fun lootSpawn(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "loot", args[1])
    }

    fun lootReplaceBlock(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], "loot", args[2])
    }

    fun lootReplaceEntity(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], "loot", args[2])
    }

    fun lootReplaceBlockCount(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], args[2], "loot", args[3])
    }

    fun lootReplaceEntityCount(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], args[2], "loot", args[3])
    }

    fun lootGiveFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "fish", args[1], args[2])
    }

    fun lootGiveFishUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "fish", args[1], args[2], args[3])
    }

    fun lootGiveFishUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "fish", args[1], args[2], "mainhand")
    }

    fun lootGiveFishUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "fish", args[1], args[2], "offhand")
    }

    fun lootInsertFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "fish", args[1], args[2])
    }

    fun lootInsertFishUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "fish", args[1], args[2], args[3])
    }

    fun lootInsertFishUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "fish", args[1], args[2], "mainhand")
    }

    fun lootInsertFishUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "fish", args[1], args[2], "offhand")
    }

    fun lootSpawnFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "fish", args[1], args[2])
    }

    fun lootSpawnFishUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "fish", args[1], args[2], args[3])
    }

    fun lootSpawnFishUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "fish", args[1], args[2], "mainhand")
    }

    fun lootSpawnFishUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "fish", args[1], args[2], "offhand")
    }

    fun lootReplaceBlockFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], "fish", args[2], args[3])
    }

    fun lootReplaceEntityFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], "fish", args[2], args[3])
    }

    fun lootReplaceBlockCountFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], args[2], "fish", args[3], args[4], "loot")
    }

    fun lootReplaceEntityCountFish(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], args[2], "fish", args[3], args[4], "loot")
    }

    fun lootGiveKill(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "kill", args[1])
    }

    fun lootInsertKill(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "kill", args[1])
    }

    fun lootSpawnKill(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "kill", args[1])
    }

    fun lootReplaceBlockKill(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], "kill", args[2])
    }

    fun lootReplaceEntityKill(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], "kill", args[2])
    }

    fun lootReplaceBlockCountKill(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], args[2], "kill", args[3])
    }

    fun lootReplaceEntityCountKill(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], args[2], "kill", args[3])
    }

    fun lootGiveMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "mine", args[1])
    }

    fun lootGiveMineUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "mine", args[1], args[2])
    }

    fun lootGiveMineUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "mine", args[1], "mainhand")
    }

    fun lootGiveMineUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot give", args[0], "mine", args[1], "offhand")
    }

    fun lootInsertMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "mine", args[1])
    }

    fun lootInsertMineUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "mine", args[1], args[2])
    }

    fun lootInsertMineUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "mine", args[1], "mainhand")
    }

    fun lootInsertMineUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot insert", args[0], "mine", args[1], "offhand")
    }

    fun lootSpawnMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "mine", args[1])
    }

    fun lootSpawnMineUsing(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "mine", args[1], args[2])
    }

    fun lootSpawnMineUsingMainHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "mine", args[1], "mainhand")
    }

    fun lootSpawnMineUsingOffHand(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "mine", args[1], "offhand")
    }

    fun lootReplaceBlockMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], "mine", args[2])
    }

    fun lootReplaceEntityMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], "mine", args[2])
    }

    fun lootReplaceBlockCountMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], args[2], "mine", args[3])
    }

    fun lootReplaceEntityCountMine(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], args[2], "mine", args[3])
    }

    fun me(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("me", args[0])
    }

    fun pardon(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("pardon", args[0])
    }

    fun pardonIp(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("pardon-ip", args[0])
    }

    fun particle(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("particle", args[0])
    }

    fun particleAt(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("particle", args[0], args[1])
    }

    fun particleMoving(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("particle", args[0], args[1], args[2], args[3], args[4])
    }

    fun particleForce(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("particle", args[0], args[1], args[2], args[3], args[4], "force")
    }

    fun particleForPlayers(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("particle", args[0], args[1], args[2], args[3], args[4], "normal", args[5])
    }

    fun particleForceForPlayers(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("particle", args[0], args[1], args[2], args[3], args[4], "force", args[5])
    }

    fun perfStart(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("perf start")
    }

    fun perfStop(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("perf stop")
    }

    fun publish(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("publish", args[0], args[1], args[2])
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
        Command.buildAll("say", args[0])
    }

    fun setblock(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("setblock", args[0], args[1], args[2])
    }

    fun setidletimeout(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("setidletimeout", args[0])
    }

    fun setworldspawn(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("setworldspawn", args[0], args[1])
    }

    fun spreadplayers(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("spreadplayers", args[0], args[1], args[2], args[3], args[4])
    }

    fun spreadplayersUnder(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("spreadplayers", args[0], args[1], "under", args[2], args[3], args[4], args[5])
    }

    fun stop(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command("stop")
    }

    fun stopAllSound(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("stopsound", args[0])
    }

    fun stopSound(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("stopsound", args[0], args[1])
    }

    fun stopSoundNamed(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("stopsound", args[0], "*", args[1])
    }

    fun stopSoundTyped(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("stopsound", args[0], args[1], args[2])
    }

    fun summon(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("summon", args[0], args[1])
    }

    fun summonWithNbt(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("summon", args[0], args[1], args[2])
    }

    fun teammsg(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("teammsg", args[0])
    }

    fun tellraw(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("tellraw", args[0], args[1])
    }

    fun titleClear(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("title", args[0], "clear")
    }

    fun titleReset(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("title", args[0], "reset")
    }

    fun tm(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("tm", args[0])
    }

    fun transfer(context: NativeCallContext) = captureCommandResult(context) { args ->
        Command.buildAll("transfer", args[0], args[1], args[2])
    }

    fun seed(context: NativeCallContext) = captureCommandResult(context) { Command("seed") }

    fun damage(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[2])
    }

    fun damageAt(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[3], "at", args[2])
    }

    fun damageBy(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[3], "by", args[2])
    }

    fun damageFrom(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[4], "by", args[2], "from", args[3])
    }

    private fun damageCommand(target: Var<*>, amount: Var<*>, kind: Var<*>, vararg extras: Any): Command {
        val multi = if (target is SelectorVar) !target.value.selectingSingleEntity() else (target as EntityVar).isMulti()
        return if (multi) Command.buildAll("execute as", target, "run damage @s", amount, kind, *extras)
        else Command.buildAll("damage", target, amount, kind, *extras)
    }

    fun cloneArea(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("clone", args[0], args[1], args[2], args[3])
    }

    fun cloneStrictArea(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("clone", args[0], args[1], "strict", args[2], args[3])
    }

    fun cloneFiltered(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("clone", args[0], args[1], "filtered", args[2], args[3])
    }

    fun cloneStrictFiltered(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("clone", args[0], args[1], "strict filtered", args[2], args[3])
    }

    fun enchant(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("enchant", args[0], args[1], args[2], args[3])
    }

    fun placeFeature(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place feature", args[0])
    }

    fun placeFeatureAt(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place feature", args[0], args[1])
    }

    fun placeJigsaw(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place jigsaw", args[0], args[1], args[2])
    }

    fun placeJigsawAt(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place jigsaw", args[0], args[1], args[2], args[3])
    }

    fun placeStructure(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place structure", args[0])
    }

    fun placeStructureAt(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place structure", args[0], args[1])
    }

    fun placeTemplate(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place", args[0], args[1], args[2], args[3], args[4], args[5])
    }

    fun placeTemplateStrict(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place", args[0], args[1], args[2], args[3], args[4], args[5], "strict")
    }

    fun playsound(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("playsound", args[0], args[1], args[2], args[3], args[4], args[5], args[6])
    }

    fun titleTitle(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("title", args[0], args[2], args[1])
    }

    fun titleSet(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("title", args[0], "times", args[1], args[2], args[3])
    }

    private fun emit(context: NativeCallContext, build: (List<Var<*>>) -> Command) = context.withArguments { args ->
        if (!FloatProviders.enabled && args.any { it is MCFloat && it !is MCFPPValue<*> }) {
            LogProcessor.error("Dynamic float command arguments require a number-provider target")
            return@withArguments
        }
        build(args).buildMacroFunction().forEach { Function.addCommand(it) }
    }
}
