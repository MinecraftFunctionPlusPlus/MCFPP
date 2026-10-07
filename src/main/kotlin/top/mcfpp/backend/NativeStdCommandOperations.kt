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
import top.mcfpp.util.TempPool
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.nbt.tags.CompoundTag

object NativeStdCommandOperations {
    fun fillKeep(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("fill", args[0], args[1], "keep")
    }

    fun fillReplace(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("fill", args[0], args[1])
    }

    fun fillReplaceFiltered(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("fill", args[0], args[1], "replace", args[2])
    }

    fun fillReplaceFilteredMode(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("fill", args[0], args[1], "replace", args[2], args[3])
    }

    fun fillBiome(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("fillbiome", args[0], args[1])
    }

    fun fillBiomeReplace(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("fillbiome", args[0], args[1], "replace", args[2])
    }

    fun forceload(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("forceload", "add", args[0], args[1])
    }

    fun forceloadRemove(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("forceload", "remove", args[0], args[1])
    }

    fun forceloadRemoveAll(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("forceload remove all")
    }

    fun forceloadQuery(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("forceload query", args[0], args[1])
    }

    fun forceloadQueryAll(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("forceload query")
    }

    fun give(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("give", args[0], args[1])
    }

    fun giveCount(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("give", args[0], args[1], args[2])
    }

    fun help(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("help")
    }

    fun helpCommand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("help", args[0])
    }

    fun jfrStart(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("jfr start")
    }

    fun jfrStop(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("jfr stop")
    }

    fun kick(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("kick", args[0], args[1])
    }

    fun kill(context: NativeCallContext) = captureResult(context) { args ->
        Command("kill")
    }

    fun killTargets(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("kill", args[0])
    }

    fun list(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("list")
    }

    fun locateStructure(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("locate", args[0])
    }

    fun locateBiome(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("locate", args[0])
    }

    fun locatePoi(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("locate", args[0])
    }

    fun lootGive(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "loot", args[1])
    }

    fun lootInsert(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "loot", args[1])
    }

    fun lootSpawn(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "loot", args[1])
    }

    fun lootReplaceBlock(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], "loot", args[2])
    }

    fun lootReplaceEntity(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], "loot", args[2])
    }

    fun lootReplaceBlockCount(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], args[2], "loot", args[3])
    }

    fun lootReplaceEntityCount(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], args[2], "loot", args[3])
    }

    fun lootGiveFish(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "fish", args[1], args[2])
    }

    fun lootGiveFishUsing(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "fish", args[1], args[2], args[3])
    }

    fun lootGiveFishUsingMainHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "fish", args[1], args[2], "mainhand")
    }

    fun lootGiveFishUsingOffHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "fish", args[1], args[2], "offhand")
    }

    fun lootInsertFish(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "fish", args[1], args[2])
    }

    fun lootInsertFishUsing(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "fish", args[1], args[2], args[3])
    }

    fun lootInsertFishUsingMainHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "fish", args[1], args[2], "mainhand")
    }

    fun lootInsertFishUsingOffHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "fish", args[1], args[2], "offhand")
    }

    fun lootSpawnFish(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "fish", args[1], args[2])
    }

    fun lootSpawnFishUsing(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "fish", args[1], args[2], args[3])
    }

    fun lootSpawnFishUsingMainHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "fish", args[1], args[2], "mainhand")
    }

    fun lootSpawnFishUsingOffHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "fish", args[1], args[2], "offhand")
    }

    fun lootReplaceBlockFish(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], "fish", args[2], args[3])
    }

    fun lootReplaceEntityFish(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], "fish", args[2], args[3])
    }

    fun lootReplaceBlockCountFish(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], args[2], "fish", args[3], args[4], "loot")
    }

    fun lootReplaceEntityCountFish(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], args[2], "fish", args[3], args[4], "loot")
    }

    fun lootGiveKill(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "kill", args[1])
    }

    fun lootInsertKill(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "kill", args[1])
    }

    fun lootSpawnKill(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "kill", args[1])
    }

    fun lootReplaceBlockKill(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], "kill", args[2])
    }

    fun lootReplaceEntityKill(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], "kill", args[2])
    }

    fun lootReplaceBlockCountKill(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], args[2], "kill", args[3])
    }

    fun lootReplaceEntityCountKill(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], args[2], "kill", args[3])
    }

    fun lootGiveMine(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "mine", args[1])
    }

    fun lootGiveMineUsing(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "mine", args[1], args[2])
    }

    fun lootGiveMineUsingMainHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "mine", args[1], "mainhand")
    }

    fun lootGiveMineUsingOffHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot give", args[0], "mine", args[1], "offhand")
    }

    fun lootInsertMine(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "mine", args[1])
    }

    fun lootInsertMineUsing(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "mine", args[1], args[2])
    }

    fun lootInsertMineUsingMainHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "mine", args[1], "mainhand")
    }

    fun lootInsertMineUsingOffHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot insert", args[0], "mine", args[1], "offhand")
    }

    fun lootSpawnMine(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "mine", args[1])
    }

    fun lootSpawnMineUsing(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "mine", args[1], args[2])
    }

    fun lootSpawnMineUsingMainHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "mine", args[1], "mainhand")
    }

    fun lootSpawnMineUsingOffHand(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot spawn", args[0], "mine", args[1], "offhand")
    }

    fun lootReplaceBlockMine(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], "mine", args[2])
    }

    fun lootReplaceEntityMine(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], "mine", args[2])
    }

    fun lootReplaceBlockCountMine(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace block", args[0], args[1], args[2], "mine", args[3])
    }

    fun lootReplaceEntityCountMine(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("loot replace entity", args[0], args[1], args[2], "mine", args[3])
    }

    fun me(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("me", args[0])
    }

    fun pardon(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("pardon", args[0])
    }

    fun pardonIp(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("pardon-ip", args[0])
    }

    fun particle(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("particle", args[0])
    }

    fun particleAt(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("particle", args[0], args[1])
    }

    fun particleMoving(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("particle", args[0], args[1], args[2], args[3], args[4])
    }

    fun particleForce(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("particle", args[0], args[1], args[2], args[3], args[4], "force")
    }

    fun particleForPlayers(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("particle", args[0], args[1], args[2], args[3], args[4], "normal", args[5])
    }

    fun particleForceForPlayers(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("particle", args[0], args[1], args[2], args[3], args[4], "force", args[5])
    }

    fun perfStart(context: NativeCallContext) = captureResult(context) { args ->
        Command("perf start")
    }

    fun perfStop(context: NativeCallContext) = captureResult(context) { args ->
        Command("perf stop")
    }

    fun publish(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("publish", args[0], args[1], args[2])
    }

    fun reload(context: NativeCallContext) = captureResult(context) { args ->
        Command("reload")
    }

    fun save(context: NativeCallContext) = captureResult(context) { args ->
        Command("save")
    }

    fun saveAll(context: NativeCallContext) = captureResult(context) { args ->
        Command("save-all")
    }

    fun saveOff(context: NativeCallContext) = captureResult(context) { args ->
        Command("save-off")
    }

    fun saveOn(context: NativeCallContext) = captureResult(context) { args ->
        Command("save-on")
    }

    fun say(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("say", args[0])
    }

    fun setblock(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("setblock", args[0], args[1], args[2])
    }

    fun setidletimeout(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("setidletimeout", args[0])
    }

    fun setworldspawn(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("setworldspawn", args[0], args[1])
    }

    fun spreadplayers(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("spreadplayers", args[0], args[1], args[2], args[3], args[4])
    }

    fun spreadplayersUnder(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("spreadplayers", args[0], args[1], "under", args[2], args[3], args[4], args[5])
    }

    fun stop(context: NativeCallContext) = captureResult(context) { args ->
        Command("stop")
    }

    fun stopAllSound(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("stopsound", args[0])
    }

    fun stopSound(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("stopsound", args[0], args[1])
    }

    fun stopSoundNamed(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("stopsound", args[0], "*", args[1])
    }

    fun stopSoundTyped(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("stopsound", args[0], args[1], args[2])
    }

    fun summon(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("summon", args[0], args[1])
    }

    fun summonWithNbt(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("summon", args[0], args[1], args[2])
    }

    fun teammsg(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("teammsg", args[0])
    }

    fun tellraw(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("tellraw", args[0], args[1])
    }

    fun titleClear(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("title", args[0], "clear")
    }

    fun titleReset(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("title", args[0], "reset")
    }

    fun tm(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("tm", args[0])
    }

    fun transfer(context: NativeCallContext) = captureResult(context) { args ->
        Command.buildAll("transfer", args[0], args[1], args[2])
    }

    fun seed(context: NativeCallContext) = captureResult(context) { Command("seed") }

    private fun captureResult(context: NativeCallContext, build: (List<Var<*>>) -> Command) = context.withArguments { args ->
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
