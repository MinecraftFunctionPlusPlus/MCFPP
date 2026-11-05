package top.mcfpp.mni.minecraft

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeStdCommandOperations
import top.mcfpp.mni.NativeCallContext

/** Builds Minecraft commands for MCFPP standard-library functions. */
object StdCommands {

    /** Returns the MCFPP namespace imports used when registering these command implementations. */
    @Suppress("unused")
    @JvmStatic
    fun importToMNI(): Array<String> {
        return arrayOf(
                "mcfpp.minecraft:*",
                "mcfpp.minecraft.entity:*",
                "mcfpp.minecraft.item:*",
                "mcfpp.minecraft.other:*",
                "mcfpp.minecraft.predicates:*",
                "mcfpp.minecraft.resource:*",
                "mcfpp.math:*"
        )
    }

    //region clone
    /** Copies a source area to the destination using the selected mask and overlap rules. */
    @MNIFunction(identifier = "clone", normalParams = ["Area", "pos3", "CloneMaskMode = replace", "CloneOperation = normal"])
    @JvmStatic
    fun cloneArea(context: NativeCallContext) {
        NativeStdCommandOperations.cloneArea(context)
    }

    /** Copies a source area with the strict clone mode. */
    @MNIFunction(identifier = "cloneStrict", normalParams = ["Area", "pos3", "CloneMaskMode = replace", "CloneOperation = normal"])
    @JvmStatic
    fun cloneStrictArea(context: NativeCallContext) {
        NativeStdCommandOperations.cloneStrictArea(context)
    }

    /** Copies matching source blocks to the destination. */
    @MNIFunction(identifier = "clone", normalParams = ["Area", "pos3", "BlockPredicate", "CloneOperation = normal"])
    @JvmStatic
    fun cloneFiltered(context: NativeCallContext) {
        NativeStdCommandOperations.cloneFiltered(context)
    }

    /** Copies matching source blocks with the strict clone mode. */
    @MNIFunction(identifier = "cloneStrict", normalParams = ["Area", "pos3", "BlockPredicate", "CloneOperation = normal"])
    @JvmStatic
    fun cloneStrictFiltered(context: NativeCallContext) {
        NativeStdCommandOperations.cloneStrictFiltered(context)
    }
    //endregion

    //region damage
    /** Deals the supplied amount and type of damage to the target. */
    @MNIFunction(normalParams = ["entity", "float", "DamageType = GENERIC"])
    @JvmStatic
    fun damage(context: NativeCallContext) {
        NativeStdCommandOperations.damage(context)
    }

    /** Deals damage with the supplied source position. */
    @MNIFunction(normalParams = ["entity", "float", "pos3", "DamageType = GENERIC"])
    @JvmStatic
    fun damageAt(context: NativeCallContext) {
        NativeStdCommandOperations.damageAt(context)
    }

    /** Deals damage attributed to the supplied source entity. */
    @MNIFunction(identifier = "damage", normalParams = ["entity", "float", "entity" , "DamageType = GENERIC"])
    @JvmStatic
    fun damageBy(context: NativeCallContext) {
        NativeStdCommandOperations.damageBy(context)
    }

    /** Deals damage with separate direct and responsible source entities. */
    @MNIFunction(identifier = "damage", normalParams = ["entity", "float", "entity", "entity" , "DamageType = GENERIC"])
    @JvmStatic
    fun damageFrom(context: NativeCallContext) {
        NativeStdCommandOperations.damageFrom(context)
    }
    //endregion

    //region enchant
    //TODO 更高等级的附魔支持
    /** Applies an enchantment and level to the selected equipment slot. */
    @MNIFunction(normalParams = ["entity", "Enchant", "int = 1", "Slot = weapon_mainhand"])
    @JvmStatic
    fun enchant(context: NativeCallContext) {
        NativeStdCommandOperations.enchant(context)
    }
    //endregion

    //region fill
    /** Fills air blocks in the supplied area. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["Area", "BlockState"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun fillKeep(context: NativeCallContext) {
        NativeStdCommandOperations.fillKeep(context)
    }

    /**
     * Fills the supplied area with the selected block state. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["Area", "BlockState"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun fillReplace(context: NativeCallContext) {
        NativeStdCommandOperations.fillReplace(context)
    }

    /**
     * Replaces blocks matching the supplied block predicate. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(identifier = "fillReplace", normalParams = ["Area", "BlockState", "BlockPredicate"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun fillReplaceFiltered(context: NativeCallContext) {
        NativeStdCommandOperations.fillReplaceFiltered(context)
    }

    /**
     * Replaces matching blocks using the supplied fill mode. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(identifier = "fillReplace", normalParams = ["Area", "BlockState", "BlockPredicate", "FillMode"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun fillReplaceFilteredMode(context: NativeCallContext) {
        NativeStdCommandOperations.fillReplaceFilteredMode(context)
    }
    //endregion

    //region fillBiome
    /** Sets the biome in the supplied area. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["Area", "Biome"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun fillBiome(context: NativeCallContext) {
        NativeStdCommandOperations.fillBiome(context)
    }

    /**
     * Replaces the selected biome within the supplied area. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(identifier = "fillBiome", normalParams = ["Area", "Biome", "Biome"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun fillBiomeReplace(context: NativeCallContext) {
        NativeStdCommandOperations.fillBiomeReplace(context)
    }
    //endregion

    //region forceload
    /**
     * Marks the chunks covered by the two positions for forced loading. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(normalParams = ["pos2", "pos2"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun forceload(context: NativeCallContext) {
        NativeStdCommandOperations.forceload(context)
    }

    /**
     * Removes forced loading from the chunks covered by the two positions. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(normalParams = ["pos2", "pos2"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun forceloadRemove(context: NativeCallContext) {
        NativeStdCommandOperations.forceloadRemove(context)
    }

    /**
     * Removes all forced chunk loading in the current dimension. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun forceloadRemoveAll(context: NativeCallContext) {
        NativeStdCommandOperations.forceloadRemoveAll(context)
    }

    /**
     * Queries forced loading for the supplied positions. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["pos2", "pos2"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun forceloadQuery(context: NativeCallContext) {
        NativeStdCommandOperations.forceloadQuery(context)
    }

    /**
     * Lists forced chunks in the current dimension. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun forceloadQueryAll(context: NativeCallContext) {
        NativeStdCommandOperations.forceloadQueryAll(context)
    }
    //endregion

    //region give
    /**
     * Gives the selected item to the target players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity", "Item"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun give(context: NativeCallContext) {
        NativeStdCommandOperations.give(context)
    }

    /**
     * Gives the supplied item count to the target players. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(identifier = "give", normalParams = ["entity", "Item", "int"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun giveCount(context: NativeCallContext) {
        NativeStdCommandOperations.giveCount(context)
    }
    //endregion

    //region help
    /** Displays command help. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun help(context: NativeCallContext) {
        NativeStdCommandOperations.help(context)
    }

    /** Displays help for the supplied command. Publishes the command result and success status through context. */
    @MNIFunction(identifier = "help", normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun helpCommand(context: NativeCallContext) {
        NativeStdCommandOperations.helpCommand(context)
    }
    //endregion

    //region jfr
    /**
     * Starts a Java Flight Recorder recording on the server. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun jfrStart(context: NativeCallContext) {
        NativeStdCommandOperations.jfrStart(context)
    }

    /**
     * Stops the server Java Flight Recorder recording. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun jfrStop(context: NativeCallContext) {
        NativeStdCommandOperations.jfrStop(context)
    }
    //endregion

    //region kick
    /**
     * Disconnects the target players with the supplied reason. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["entity", "string = \"\""], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun kick(context: NativeCallContext) {
        NativeStdCommandOperations.kick(context)
    }
    //endregion

    //region kill
    /** Kills the current command executor. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun kill(context: NativeCallContext) {
        NativeStdCommandOperations.kill(context)
    }

    /** Kills the supplied target entities. Publishes the command result and success status through context. */
    @MNIFunction(identifier = "kill", normalParams = ["entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun killTargets(context: NativeCallContext) {
        NativeStdCommandOperations.killTargets(context)
    }
    //endregion

    //region list
    /** Lists connected players. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun list(context: NativeCallContext) {
        NativeStdCommandOperations.list(context)
    }
    //endregion

    //region locate
    /** Searches for the supplied structure. Publishes the command result and success status through context. */
    @MNIFunction(identifier = "locate", normalParams = ["Structure"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun locateStructure(context: NativeCallContext) {
        NativeStdCommandOperations.locateStructure(context)
    }

    /** Searches for the supplied biome. Publishes the command result and success status through context. */
    @MNIFunction(identifier = "locate", normalParams = ["Biome"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun locateBiome(context: NativeCallContext) {
        NativeStdCommandOperations.locateBiome(context)
    }

    /**
     * Searches for the supplied point of interest. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "locate", normalParams = ["Poi"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun locatePoi(context: NativeCallContext) {
        NativeStdCommandOperations.locatePoi(context)
    }
    //endregion

    //region
    /**
     * Generates loot from the supplied table and gives the items to the target players. Publishes the command
     * result and success status through context.
     */
    @MNIFunction(normalParams = ["entity", "LootTable"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGive(context: NativeCallContext) {
        NativeStdCommandOperations.lootGive(context)
    }

    /**
     * Generates loot from the supplied table and inserts the items into the target block container. Publishes
     * the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsert(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsert(context)
    }

    /**
     * Generates loot from the supplied table and spawns the items at the target position. Publishes the command
     * result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawn(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawn(context)
    }

    /**
     * Generates loot from the supplied table and writes the items to the target block slots. Publishes the
     * command result and success status through context.
     */
    @MNIFunction(identifier = "lootReplace", normalParams = ["pos3", "Slot", "LootTable"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceBlock(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceBlock(context)
    }

    /**
     * Generates loot from the supplied table and writes the items to the target entity slots. Publishes the
     * command result and success status through context.
     */
    @MNIFunction(identifier = "lootReplace", normalParams = ["entity", "Slot", "LootTable"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceEntity(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceEntity(context)
    }

    /**
     * Generates loot from the supplied table and writes the items to the target block slots. Limits the number
     * of destination slots to the supplied count. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "lootReplace", normalParams = ["pos3", "Slot", "LootTable", "int"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceBlockCount(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceBlockCount(context)
    }

    /**
     * Generates loot from the supplied table and writes the items to the target entity slots. Limits the number
     * of destination slots to the supplied count. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "lootReplace", normalParams = ["entity", "Slot", "LootTable", "int"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceEntityCount(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceEntityCount(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and gives the items to the target players.
     * Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["entity", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGiveFish(context: NativeCallContext) {
        NativeStdCommandOperations.lootGiveFish(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and gives the items to the target players.
     * Uses the supplied item as the tool. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["entity", "LootTable", "pos3", "Item"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGiveFishUsing(context: NativeCallContext) {
        NativeStdCommandOperations.lootGiveFishUsing(context)
    }
    /**
     * Generates fishing loot from the supplied table and position and gives the items to the target players.
     * Uses the command executor's main-hand item. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGiveFishUsingMainHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootGiveFishUsingMainHand(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and gives the items to the target players.
     * Uses the command executor's off-hand item. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGiveFishUsingOffHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootGiveFishUsingOffHand(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and inserts the items into the target block
     * container. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsertFish(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsertFish(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and inserts the items into the target block
     * container. Uses the supplied item as the tool. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable", "pos3", "Item"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsertFishUsing(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsertFishUsing(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and inserts the items into the target block
     * container. Uses the command executor's main-hand item. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsertFishUsingMainHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsertFishUsingMainHand(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and inserts the items into the target block
     * container. Uses the command executor's off-hand item. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsertFishUsingOffHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsertFishUsingOffHand(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and spawns the items at the target position.
     * Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawnFish(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawnFish(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and spawns the items at the target position.
     * Uses the supplied item as the tool. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable", "pos3", "Item"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawnFishUsing(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawnFishUsing(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and spawns the items at the target position.
     * Uses the command executor's main-hand item. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawnFishUsingMainHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawnFishUsingMainHand(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and spawns the items at the target position.
     * Uses the command executor's off-hand item. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["pos3", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawnFishUsingOffHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawnFishUsingOffHand(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and writes the items to the target block
     * slots. Publishes the command result and success status through context.
     */
    @MNIFunction(identifier = "lootReplaceFish", normalParams = ["pos3", "Slot", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceBlockFish(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceBlockFish(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and writes the items to the target entity
     * slots. Publishes the command result and success status through context.
     */
    @MNIFunction(identifier = "lootReplaceFish", normalParams = ["entity", "Slot", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceEntityFish(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceEntityFish(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and writes the items to the target block
     * slots. Limits the number of destination slots to the supplied count. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(identifier = "lootReplaceFish", normalParams = ["pos3", "Slot", "int", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceBlockCountFish(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceBlockCountFish(context)
    }

    /**
     * Generates fishing loot from the supplied table and position and writes the items to the target entity
     * slots. Limits the number of destination slots to the supplied count. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(identifier = "lootReplaceFish", normalParams = ["entity", "Slot", "int", "LootTable", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceEntityCountFish(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceEntityCountFish(context)
    }

    /**
     * Generates loot for the supplied entity and gives the items to the target players. Publishes the command
     * result and success status through context.
     */
    @MNIFunction(normalParams = ["entity", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGiveKill(context: NativeCallContext) {
        NativeStdCommandOperations.lootGiveKill(context)
    }

    /**
     * Generates loot for the supplied entity and inserts the items into the target block container. Publishes
     * the command result and success status through context.
     */
    @MNIFunction(identifier = "lootInsert", normalParams = ["pos3", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsertKill(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsertKill(context)
    }

    /**
     * Generates loot for the supplied entity and spawns the items at the target position. Publishes the command
     * result and success status through context.
     */
    @MNIFunction(identifier = "lootSpawn", normalParams = ["pos3", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawnKill(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawnKill(context)
    }

    /**
     * Generates loot for the supplied entity and writes the items to the target block slots. Publishes the
     * command result and success status through context.
     */
    @MNIFunction(identifier = "lootReplace", normalParams = ["pos3", "Slot", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceBlockKill(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceBlockKill(context)
    }

    /**
     * Generates loot for the supplied entity and writes the items to the target entity slots. Publishes the
     * command result and success status through context.
     */
    @MNIFunction(identifier = "lootReplace", normalParams = ["entity", "Slot", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceEntityKill(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceEntityKill(context)
    }

    /**
     * Generates loot for the supplied entity and writes the items to the target block slots. Limits the number
     * of destination slots to the supplied count. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "lootReplace", normalParams = ["pos3", "Slot", "int", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceBlockCountKill(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceBlockCountKill(context)
    }

    /**
     * Generates loot for the supplied entity and writes the items to the target entity slots. Limits the number
     * of destination slots to the supplied count. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "lootReplace", normalParams = ["entity", "Slot", "int", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceEntityCountKill(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceEntityCountKill(context)
    }

    /**
     * Generates block drops from the supplied position and gives the items to the target players. Publishes the
     * command result and success status through context.
     */
    @MNIFunction(normalParams = ["entity", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGiveMine(context: NativeCallContext) {
        NativeStdCommandOperations.lootGiveMine(context)
    }

    /**
     * Generates block drops from the supplied position and gives the items to the target players. Uses the
     * supplied item as the tool. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["entity", "pos3", "Item"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGiveMineUsing(context: NativeCallContext) {
        NativeStdCommandOperations.lootGiveMineUsing(context)
    }

    /**
     * Generates block drops from the supplied position and gives the items to the target players. Uses the
     * command executor's main-hand item. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["entity", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGiveMineUsingMainHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootGiveMineUsingMainHand(context)
    }

    /**
     * Generates block drops from the supplied position and gives the items to the target players. Uses the
     * command executor's off-hand item. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["entity", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootGiveMineUsingOffHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootGiveMineUsingOffHand(context)
    }

    /**
     * Generates block drops from the supplied position and inserts the items into the target block container.
     * Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsertMine(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsertMine(context)
    }

    /**
     * Generates block drops from the supplied position and inserts the items into the target block container.
     * Uses the supplied item as the tool. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "pos3", "Item"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsertMineUsing(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsertMineUsing(context)
    }

    /**
     * Generates block drops from the supplied position and inserts the items into the target block container.
     * Uses the command executor's main-hand item. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["pos3", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsertMineUsingMainHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsertMineUsingMainHand(context)
    }

    /**
     * Generates block drops from the supplied position and inserts the items into the target block container.
     * Uses the command executor's off-hand item. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["pos3", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootInsertMineUsingOffHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootInsertMineUsingOffHand(context)
    }

    /**
     * Generates block drops from the supplied position and spawns the items at the target position. Publishes
     * the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawnMine(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawnMine(context)
    }

    /**
     * Generates block drops from the supplied position and spawns the items at the target position. Uses the
     * supplied item as the tool. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "pos3", "Item"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawnMineUsing(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawnMineUsing(context)
    }

    /**
     * Generates block drops from the supplied position and spawns the items at the target position. Uses the
     * command executor's main-hand item. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawnMineUsingMainHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawnMineUsingMainHand(context)
    }

    /**
     * Generates block drops from the supplied position and spawns the items at the target position. Uses the
     * command executor's off-hand item. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootSpawnMineUsingOffHand(context: NativeCallContext) {
        NativeStdCommandOperations.lootSpawnMineUsingOffHand(context)
    }

    /**
     * Generates block drops from the supplied position and writes the items to the target block slots.
     * Publishes the command result and success status through context.
     */
    @MNIFunction(identifier = "lootReplaceMine", normalParams = ["pos3", "Slot", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceBlockMine(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceBlockMine(context)
    }

    /**
     * Generates block drops from the supplied position and writes the items to the target entity slots.
     * Publishes the command result and success status through context.
     */
    @MNIFunction(identifier = "lootReplaceMine", normalParams = ["entity", "Slot", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceEntityMine(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceEntityMine(context)
    }

    /**
     * Generates block drops from the supplied position and writes the items to the target block slots. Limits
     * the number of destination slots to the supplied count. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(identifier = "lootReplaceMine", normalParams = ["pos3", "Slot", "int", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceBlockCountMine(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceBlockCountMine(context)
    }

    /**
     * Generates block drops from the supplied position and writes the items to the target entity slots. Limits
     * the number of destination slots to the supplied count. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(identifier = "lootReplaceMine", normalParams = ["entity", "Slot", "int", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun lootReplaceEntityCountMine(context: NativeCallContext) {
        NativeStdCommandOperations.lootReplaceEntityCountMine(context)
    }
    //endregion

    //region me
    /**
     * Broadcasts an action message from the command executor. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun me(context: NativeCallContext) {
        NativeStdCommandOperations.me(context)
    }
    //endregion

    //region pardon
    /**
     * Removes the named player from the server ban list. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun pardon(context: NativeCallContext) {
        NativeStdCommandOperations.pardon(context)
    }
    //endregion

    //region pardon-ip
    /**
     * Removes the supplied address from the server IP ban list. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun pardonIp(context: NativeCallContext) {
        NativeStdCommandOperations.pardonIp(context)
    }
    //endregion

    //particle
    /**
     * Creates particles at the current execution position. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["Particle"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun particle(context: NativeCallContext) {
        NativeStdCommandOperations.particle(context)
    }

    /**
     * Creates particles at the supplied position. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "particle", normalParams = ["Particle", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun particleAt(context: NativeCallContext) {
        NativeStdCommandOperations.particleAt(context)
    }

    /**
     * Creates particles with the supplied spread, speed, and count. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(identifier = "particle", normalParams = ["Particle", "pos3", "pos3", "float", "int"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun particleMoving(context: NativeCallContext) {
        NativeStdCommandOperations.particleMoving(context)
    }

    /**
     * Creates particles using force visibility mode. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["Particle", "pos3", "pos3", "float", "int"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun particleForce(context: NativeCallContext) {
        NativeStdCommandOperations.particleForce(context)
    }

    /**
     * Creates particles for the selected players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "particle", normalParams = ["Particle", "pos3", "pos3", "float", "int", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun particleForPlayers(context: NativeCallContext) {
        NativeStdCommandOperations.particleForPlayers(context)
    }

    /**
     * Creates particles for the selected players using force visibility mode. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(identifier = "particleForce", normalParams = ["Particle", "pos3", "pos3", "float", "int", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun particleForceForPlayers(context: NativeCallContext) {
        NativeStdCommandOperations.particleForceForPlayers(context)
    }
    //endregion

    //region perf
    /**
     * Starts the server performance recording. Publishes the command result and success status through context.
     */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun perfStart(context: NativeCallContext) {
        NativeStdCommandOperations.perfStart(context)
    }

    /** Stops the server performance recording. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun perfStop(context: NativeCallContext) {
        NativeStdCommandOperations.perfStop(context)
    }
    //endregion

    //region place
    /** Places a configured feature at the current execution position. */
    @MNIFunction(identifier = "place",normalParams = ["ConfiguredFeature"])
    @JvmStatic
    fun placeFeature(context: NativeCallContext) {
        NativeStdCommandOperations.placeFeature(context)
    }

    /** Places a configured feature at the supplied position. */
    @MNIFunction(identifier = "place",normalParams = ["ConfiguredFeature", "pos3"])
    @JvmStatic
    fun placeFeatureAt(context: NativeCallContext) {
        NativeStdCommandOperations.placeFeatureAt(context)
    }

    /** Places a structure from a template pool with the supplied target and depth. */
    @MNIFunction(identifier = "place",normalParams = ["TemplatePool", "string", "int"])
    @JvmStatic
    fun placeJigsaw(context: NativeCallContext) {
        NativeStdCommandOperations.placeJigsaw(context)
    }

    /** Places a structure from a template pool at the supplied position. */
    @MNIFunction(identifier = "place",normalParams = ["TemplatePool", "string", "int", "pos3"])
    @JvmStatic
    fun placeJigsawAt(context: NativeCallContext) {
        NativeStdCommandOperations.placeJigsawAt(context)
    }

    /** Places the selected structure at the current execution position. */
    @MNIFunction(identifier = "place",normalParams = ["Structure"])
    @JvmStatic
    fun placeStructure(context: NativeCallContext) {
        NativeStdCommandOperations.placeStructure(context)
    }

    /** Places the selected structure at the supplied position. */
    @MNIFunction(identifier = "place",normalParams = ["Structure", "pos3"])
    @JvmStatic
    fun placeStructureAt(context: NativeCallContext) {
        NativeStdCommandOperations.placeStructureAt(context)
    }

    /** Places a structure template with the supplied rotation, mirror, integrity, and seed. */
    @MNIFunction(identifier = "place", normalParams = ["string", "pos3 = pos3.RELATIVE", "PlaceRotation = none", "PlaceMirror = none", "float = 1.0", "int = System.randInt()"])
    @JvmStatic
    fun placeTemplate(context: NativeCallContext) {
        NativeStdCommandOperations.placeTemplate(context)
    }

    /** Places a structure template using strict placement mode. */
    @MNIFunction(identifier = "placeStrict" ,normalParams = ["string", "pos3 = pos3.HERE", "PlaceRotation = none", "PlaceMirror = none", "float = 1.0", "int = System.randInt()"])
    @JvmStatic
    fun placeTemplateStrict(context: NativeCallContext) {
        NativeStdCommandOperations.placeTemplateStrict(context)
    }
    //endregion

    //region playsound
    /** Plays a sound with the supplied category, targets, position, volume, pitch, and minimum volume. */
    @MNIFunction(normalParams = ["Sound", "SoundType", "entity = @s", "pos3 = pos3.HERE", "float = 1.0", "float = 1.0", "float = 0.0"])
    @JvmStatic
    fun playsound(context: NativeCallContext) {
        NativeStdCommandOperations.playsound(context)
    }
    //endregion

    //region publish
    /**
     * Opens the world to LAN players with the supplied permissions, game mode, and port. Publishes the command
     * result and success status through context.
     */
    @MNIFunction(normalParams = ["bool = false", "Gamemode = survival", "int = System.randInt(1025,65536)"] ,returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun publish(context: NativeCallContext) {
        NativeStdCommandOperations.publish(context)
    }
    //endregion

    //region reload
    /** Reloads enabled data packs. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun reload(context: NativeCallContext) {
        NativeStdCommandOperations.reload(context)
    }
    //endregion

    //region save save-all save-off save-on
    /** Saves world data. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun save(context: NativeCallContext) {
        NativeStdCommandOperations.save(context)
    }

    /**
     * Saves world data and flushes it to disk. Publishes the command result and success status through context.
     */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun saveAll(context: NativeCallContext) {
        NativeStdCommandOperations.saveAll(context)
    }

    /** Disables automatic world saving. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun saveOff(context: NativeCallContext) {
        NativeStdCommandOperations.saveOff(context)
    }

    /** Enables automatic world saving. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun saveOn(context: NativeCallContext) {
        NativeStdCommandOperations.saveOn(context)
    }
    //endregion

    //region say
    /** Broadcasts a chat message. Publishes the command result and success status through context. */
    @MNIFunction(normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun say(context: NativeCallContext) {
        NativeStdCommandOperations.say(context)
    }
    //endregion

    //TODO schedule

    //region seed
    /** Displays the world seed. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun seed(context: NativeCallContext) {
        NativeStdCommandOperations.seed(context)
    }
    //endregion

    //region setblock
    /**
     * Places a block at the supplied position using the selected replacement mode. Publishes the command result
     * and success status through context.
     */
    @MNIFunction(normalParams = ["pos3", "BlockState", "SetBlockMode = replace"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setblock(context: NativeCallContext) {
        NativeStdCommandOperations.setblock(context)
    }
    //endregion

    //region setidletimeout
    /**
     * Sets the player inactivity timeout in minutes. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["int"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setidletimeout(context: NativeCallContext) {
        NativeStdCommandOperations.setidletimeout(context)
    }
    //endregion

    //region setworldspawn
    /**
     * Sets the world spawn position and angle. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos3 = pos3.RELATIVE", "pos2 = pos2.RELATIVE"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun setworldspawn(context: NativeCallContext) {
        NativeStdCommandOperations.setworldspawn(context)
    }
    //endregion

    //region spreadplayers
    /**
     * Distributes the target entities around a center with the supplied spacing and range. Publishes the
     * command result and success status through context.
     */
    @MNIFunction(normalParams = ["pos2", "float", "float", "bool", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun spreadplayers(context: NativeCallContext) {
        NativeStdCommandOperations.spreadplayers(context)
    }

    /**
     * Distributes target entities below the supplied maximum height. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(identifier = "spreadplayers", normalParams = ["pos2", "float", "float", "int", "bool", "entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun spreadplayersUnder(context: NativeCallContext) {
        NativeStdCommandOperations.spreadplayersUnder(context)
    }
    //endregion

    //region stop
    /** Stops the server. Publishes the command result and success status through context. */
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun stop(context: NativeCallContext) {
        NativeStdCommandOperations.stop(context)
    }
    //endregion

    //region stopsound
    /**
     * Stops all sounds for the target players. Publishes the command result and success status through context.
     */
    @MNIFunction(normalParams = ["entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun stopAllSound(context: NativeCallContext) {
        NativeStdCommandOperations.stopAllSound(context)
    }

    /**
     * Stops sounds in the selected category for the target players. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(normalParams = ["entity", "SoundType"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun stopSound(context: NativeCallContext) {
        NativeStdCommandOperations.stopSound(context)
    }

    /**
     * Stops the selected sound for the target players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(identifier = "stopSound", normalParams = ["entity", "Sound"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun stopSoundNamed(context: NativeCallContext) {
        NativeStdCommandOperations.stopSoundNamed(context)
    }

    /**
     * Stops the selected sound and category for the target players. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(identifier = "stopSound", normalParams = ["entity", "SoundType", "Sound"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun stopSoundTyped(context: NativeCallContext) {
        NativeStdCommandOperations.stopSoundTyped(context)
    }
    //endregion

    //region summon
    /**
     * Summons an entity at the supplied position. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["EntityType", "pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun summon(context: NativeCallContext) {
        NativeStdCommandOperations.summon(context)
    }

    /**
     * Summons an entity with the supplied position and NBT data. Publishes the command result and success
     * status through context.
     */
    @MNIFunction(identifier = "summon", normalParams = ["EntityType", "pos3", "nbt"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun summonWithNbt(context: NativeCallContext) {
        NativeStdCommandOperations.summonWithNbt(context)
    }
    //endregion

    //region teammsg
    /**
     * Sends a message to the command executor's team. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun teammsg(context: NativeCallContext) {
        NativeStdCommandOperations.teammsg(context)
    }
    //endregion

    //region tellraw
    /**
     * Sends formatted text to the target players. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity", "text"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun tellraw(context: NativeCallContext) {
        NativeStdCommandOperations.tellraw(context)
    }
    //endregion

    // tick 不能调用

    //region titile
    /**
     * Clears the target players' current titles. Publishes the command result and success status through
     * context.
     */
    @MNIFunction(normalParams = ["entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun titleClear(context: NativeCallContext) {
        NativeStdCommandOperations.titleClear(context)
    }

    /**
     * Resets the target players' titles and timing settings. Publishes the command result and success status
     * through context.
     */
    @MNIFunction(normalParams = ["entity"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun titleReset(context: NativeCallContext) {
        NativeStdCommandOperations.titleReset(context)
    }

    /** Displays formatted text in the selected title position. */
    @MNIFunction(normalParams = ["entity", "text", "TitlePos"])
    @JvmStatic
    fun titleTitle(context: NativeCallContext) {
        NativeStdCommandOperations.titleTitle(context)
    }

    /** Sets title fade-in, display, and fade-out durations. */
    @MNIFunction(normalParams = ["entity", "Time", "Time", "Time"])
    @JvmStatic
    fun titleSet(context: NativeCallContext) {
        NativeStdCommandOperations.titleSet(context)
    }
    //endregion

    //region tm
    /**
     * Sends a message to the command executor's team using the tm command. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(normalParams = ["string"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun tm(context: NativeCallContext) {
        NativeStdCommandOperations.tm(context)
    }
    //endregion

    //region transfer
    /**
     * Connects the target players to the supplied server address and port. Publishes the command result and
     * success status through context.
     */
    @MNIFunction(normalParams = ["string", "int = 25565", "entity = @s"], returnType = "mcfpp.minecraft.std:CommandResult")
    @JvmStatic
    fun transfer(context: NativeCallContext) {
        NativeStdCommandOperations.transfer(context)
    }
    //endregion

}
