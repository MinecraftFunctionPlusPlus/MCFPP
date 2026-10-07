package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeStdCommandOperations;
import top.mcfpp.mni.NativeCallContext;

public class StdCommands {

    @SuppressWarnings("unused")
    public static String[] importToMNI(){
        return new String[]{
                "mcfpp.minecraft:*",
                "mcfpp.minecraft.entity:*",
                "mcfpp.minecraft.item:*",
                "mcfpp.minecraft.other:*",
                "mcfpp.minecraft.predicates:*",
                "mcfpp.minecraft.resource:*",
                "mcfpp.math:*"
        };
    }

    //region clone
    @MNIFunction(identifier = "clone", normalParams = {"Area", "pos3", "CloneMaskMode = replace", "CloneOperation = normal"})
    public static void cloneArea(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.cloneArea(context);
    }

    @MNIFunction(identifier = "cloneStrict", normalParams = {"Area", "pos3", "CloneMaskMode = replace", "CloneOperation = normal"})
    public static void cloneStrictArea(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.cloneStrictArea(context);
    }

    @MNIFunction(identifier = "clone", normalParams = {"Area", "pos3", "BlockPredicate", "CloneOperation = normal"})
    public static void cloneFiltered(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.cloneFiltered(context);
    }

    @MNIFunction(identifier = "cloneStrict", normalParams = {"Area", "pos3", "BlockPredicate", "CloneOperation = normal"})
    public static void cloneStrictFiltered(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.cloneStrictFiltered(context);
    }
    //endregion

    //region damage
    @MNIFunction(normalParams = {"entity", "float", "DamageType = GENERIC"})
    public static void damage(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.damage(context);
    }

    @MNIFunction(normalParams = {"entity", "float", "pos3", "DamageType = GENERIC"})
    public static void damageAt(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.damageAt(context);
    }

    @MNIFunction(identifier = "damage", normalParams = {"entity", "float", "entity<1>" , "DamageType = GENERIC"})
    public static void damageBy(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.damageBy(context);
    }

    @MNIFunction(identifier = "damage", normalParams = {"entity", "float", "entity<1>", "entity<1>" , "DamageType = GENERIC"})
    public static void damageFrom(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.damageFrom(context);
    }
    //endregion

    //region enchant
    //TODO 更高等级的附魔支持
    @MNIFunction(normalParams = {"entity", "Enchant", "int = 1", "Slot = weapon_mainhand"})
    public static void enchant(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.enchant(context);
    }
    //endregion

    //region fill
    @MNIFunction(normalParams = {"Area", "BlockState"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void fillKeep(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.fillKeep(context);
    }

    @MNIFunction(normalParams = {"Area", "BlockState"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void fillReplace(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.fillReplace(context);
    }

    @MNIFunction(identifier = "fillReplace", normalParams = {"Area", "BlockState", "BlockPredicate"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void fillReplaceFiltered(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.fillReplaceFiltered(context);
    }

    @MNIFunction(identifier = "fillReplace", normalParams = {"Area", "BlockState", "BlockPredicate", "FillMode"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void fillReplaceFilteredMode(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.fillReplaceFilteredMode(context);
    }
    //endregion

    //region fillBiome
    @MNIFunction(normalParams = {"Area", "Biome"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void fillBiome(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.fillBiome(context);
    }

    @MNIFunction(identifier = "fillBiome", normalParams = {"Area", "Biome", "Biome"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void fillBiomeReplace(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.fillBiomeReplace(context);
    }
    //endregion

    //region forceload
    @MNIFunction(normalParams = {"pos2", "pos2"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void forceload(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.forceload(context);
    }

    @MNIFunction(normalParams = {"pos2", "pos2"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void forceloadRemove(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.forceloadRemove(context);
    }

    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void forceloadRemoveAll(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.forceloadRemoveAll(context);
    }

    @MNIFunction(normalParams = {"pos2", "pos2"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void forceloadQuery(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.forceloadQuery(context);
    }

    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void forceloadQueryAll(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.forceloadQueryAll(context);
    }
    //endregion

    //region give
    @MNIFunction(normalParams = {"Player", "Item"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void give(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.give(context);
    }

    @MNIFunction(identifier = "give", normalParams = {"Player", "Item", "int"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void giveCount(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.giveCount(context);
    }
    //endregion

    //region help
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void help(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.help(context);
    }

    @MNIFunction(identifier = "help", normalParams = {"string"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void helpCommand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.helpCommand(context);
    }
    //endregion

    //region jfr
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void jfrStart(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.jfrStart(context);
    }

    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void jfrStop(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.jfrStop(context);
    }
    //endregion

    //region kick
    @MNIFunction(normalParams = {"Player", "string = \"\""}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void kick(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.kick(context);
    }
    //endregion

    //region kill
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void kill(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.kill(context);
    }

    @MNIFunction(identifier = "kill", normalParams = {"entity"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void killTargets(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.killTargets(context);
    }
    //endregion

    //region list
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void list(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.list(context);
    }
    //endregion

    //region locate
    @MNIFunction(identifier = "locate", normalParams = "Structure", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void locateStructure(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.locateStructure(context);
    }

    @MNIFunction(identifier = "locate", normalParams = "Biome", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void locateBiome(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.locateBiome(context);
    }

    @MNIFunction(identifier = "locate", normalParams = "Poi", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void locatePoi(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.locatePoi(context);
    }
    //endregion

    //region
    @MNIFunction(normalParams = {"Player", "LootTable"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGive(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGive(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsert(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsert(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawn(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawn(context);
    }

    @MNIFunction(identifier = "lootReplace", normalParams = {"pos3", "Slot", "LootTable"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceBlock(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceBlock(context);
    }

    @MNIFunction(identifier = "lootReplace", normalParams = {"entity", "Slot", "LootTable"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceEntity(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceEntity(context);
    }

    @MNIFunction(identifier = "lootReplace", normalParams = {"pos3", "Slot", "LootTable", "int"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceBlockCount(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceBlockCount(context);
    }

    @MNIFunction(identifier = "lootReplace", normalParams = {"entity", "Slot", "LootTable", "int"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceEntityCount(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceEntityCount(context);
    }

    @MNIFunction(normalParams = {"Player", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGiveFish(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGiveFish(context);
    }

    @MNIFunction(normalParams = {"entity", "LootTable", "pos3", "Item"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGiveFishUsing(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGiveFishUsing(context);
    }
    @MNIFunction(normalParams = {"entity", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGiveFishUsingMainHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGiveFishUsingMainHand(context);
    }

    @MNIFunction(normalParams = {"entity", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGiveFishUsingOffHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGiveFishUsingOffHand(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsertFish(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsertFish(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3", "Item"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsertFishUsing(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsertFishUsing(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsertFishUsingMainHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsertFishUsingMainHand(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsertFishUsingOffHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsertFishUsingOffHand(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawnFish(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawnFish(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3", "Item"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawnFishUsing(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawnFishUsing(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawnFishUsingMainHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawnFishUsingMainHand(context);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawnFishUsingOffHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawnFishUsingOffHand(context);
    }

    @MNIFunction(identifier = "lootReplaceFish", normalParams = {"pos3", "Slot", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceBlockFish(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceBlockFish(context);
    }

    @MNIFunction(identifier = "lootReplaceFish", normalParams = {"entity", "Slot", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceEntityFish(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceEntityFish(context);
    }

    @MNIFunction(identifier = "lootReplaceFish", normalParams = {"pos3", "Slot", "int", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceBlockCountFish(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceBlockCountFish(context);
    }

    @MNIFunction(identifier = "lootReplaceFish", normalParams = {"entity", "Slot", "int", "LootTable", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceEntityCountFish(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceEntityCountFish(context);
    }

    @MNIFunction(normalParams = {"Player", "entity<1>"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGiveKill(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGiveKill(context);
    }

    @MNIFunction(identifier = "lootInsert", normalParams = {"pos3", "entity<1>"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsertKill(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsertKill(context);
    }

    @MNIFunction(identifier = "lootSpawn", normalParams = {"pos3", "entity<1>"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawnKill(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawnKill(context);
    }

    @MNIFunction(identifier = "lootReplace", normalParams = {"pos3", "Slot", "entity<1>"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceBlockKill(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceBlockKill(context);
    }

    @MNIFunction(identifier = "lootReplace", normalParams = {"entity", "Slot", "entity<1>"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceEntityKill(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceEntityKill(context);
    }

    @MNIFunction(identifier = "lootReplace", normalParams = {"pos3", "Slot", "int", "entity<1>"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceBlockCountKill(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceBlockCountKill(context);
    }

    @MNIFunction(identifier = "lootReplace", normalParams = {"entity", "Slot", "int", "entity<1>"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceEntityCountKill(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceEntityCountKill(context);
    }

    @MNIFunction(normalParams = {"Player", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGiveMine(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGiveMine(context);
    }

    @MNIFunction(normalParams = {"Player", "pos3", "Item"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGiveMineUsing(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGiveMineUsing(context);
    }

    @MNIFunction(normalParams = {"Player", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGiveMineUsingMainHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGiveMineUsingMainHand(context);
    }

    @MNIFunction(normalParams = {"Player", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootGiveMineUsingOffHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootGiveMineUsingOffHand(context);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsertMine(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsertMine(context);
    }

    @MNIFunction(normalParams = {"pos3", "pos3", "Item"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsertMineUsing(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsertMineUsing(context);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsertMineUsingMainHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsertMineUsingMainHand(context);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootInsertMineUsingOffHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootInsertMineUsingOffHand(context);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawnMine(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawnMine(context);
    }

    @MNIFunction(normalParams = {"pos3", "pos3", "Item"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawnMineUsing(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawnMineUsing(context);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawnMineUsingMainHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawnMineUsingMainHand(context);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootSpawnMineUsingOffHand(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootSpawnMineUsingOffHand(context);
    }

    @MNIFunction(identifier = "lootReplaceMine", normalParams = {"pos3", "Slot", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceBlockMine(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceBlockMine(context);
    }

    @MNIFunction(identifier = "lootReplaceMine", normalParams = {"entity", "Slot", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceEntityMine(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceEntityMine(context);
    }

    @MNIFunction(identifier = "lootReplaceMine", normalParams = {"pos3", "Slot", "int", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceBlockCountMine(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceBlockCountMine(context);
    }


    @MNIFunction(identifier = "lootReplaceMine", normalParams = {"entity", "Slot", "int", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void lootReplaceEntityCountMine(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.lootReplaceEntityCountMine(context);
    }
    //endregion

    //region me
    @MNIFunction(normalParams = "string", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void me(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.me(context);
    }
    //endregion

    //region pardon
    @MNIFunction(normalParams = "string", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void pardon(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.pardon(context);
    }
    //endregion

    //region pardon-ip
    @MNIFunction(normalParams = "string", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void pardonIp(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.pardonIp(context);
    }
    //endregion

    //particle
    @MNIFunction(normalParams = {"Particle"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void particle(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.particle(context);
    }

    @MNIFunction(identifier = "particle", normalParams = {"Particle", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void particleAt(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.particleAt(context);
    }

    @MNIFunction(identifier = "particle", normalParams = {"Particle", "pos3", "pos3", "float", "int"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void particleMoving(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.particleMoving(context);
    }

    @MNIFunction(normalParams = {"Particle", "pos3", "pos3", "float", "int"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void particleForce(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.particleForce(context);
    }

    @MNIFunction(identifier = "particle", normalParams = {"Particle", "pos3", "pos3", "float", "int", "Player"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void particleForPlayers(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.particleForPlayers(context);
    }

    @MNIFunction(identifier = "particleForce", normalParams = {"Particle", "pos3", "pos3", "float", "int", "Player"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void particleForceForPlayers(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.particleForceForPlayers(context);
    }
    //endregion

    //region perf
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void perfStart(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.perfStart(context);
    }

    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void perfStop(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.perfStop(context);
    }
    //endregion

    //region place
    @MNIFunction(identifier = "place",normalParams = {"ConfiguredFeature"})
    public static void placeFeature(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.placeFeature(context);
    }

    @MNIFunction(identifier = "place",normalParams = {"ConfiguredFeature", "pos3"})
    public static void placeFeatureAt(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.placeFeatureAt(context);
    }

    @MNIFunction(identifier = "place",normalParams = {"TemplatePool", "string", "int"})
    public static void placeJigsaw(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.placeJigsaw(context);
    }

    @MNIFunction(identifier = "place",normalParams = {"TemplatePool", "string", "int", "pos3"})
    public static void placeJigsawAt(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.placeJigsawAt(context);
    }

    @MNIFunction(identifier = "place",normalParams = {"Structure"})
    public static void placeStructure(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.placeStructure(context);
    }

    @MNIFunction(identifier = "place",normalParams = {"Structure", "pos3"})
    public static void placeStructureAt(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.placeStructureAt(context);
    }

    @MNIFunction(identifier = "place", normalParams = {"string", "pos3 = pos3.RELATIVE", "PlaceRotation = none", "PlaceMirror = none", "float = 1.0", "int = System.randInt()"})
    public static void placeTemplate(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.placeTemplate(context);
    }

    @MNIFunction(identifier = "placeStrict" ,normalParams = {"string", "pos3 = pos3.HERE", "PlaceRotation = none", "PlaceMirror = none", "float = 1.0", "int = System.randInt()"})
    public static void placeTemplateStrict(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.placeTemplateStrict(context);
    }
    //endregion

    //region playsound
    @MNIFunction(normalParams = {"Sound", "SoundType", "Player = @s", "pos3 = pos3.HERE", "float = 1.0", "float = 1.0", "float = 0.0"})
    public static void playsound(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.playsound(context);
    }
    //endregion

    //region publish
    @MNIFunction(normalParams = {"bool = false", "Gamemode = survival", "int = System.randInt(1025,65536)"} ,returnType = "mcfpp.minecraft.std:CommandResult")
    public static void publish(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.publish(context);
    }
    //endregion

    //region reload
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void reload(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.reload(context);
    }
    //endregion

    //region save save-all save-off save-on
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void save(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.save(context);
    }

    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void saveAll(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.saveAll(context);
    }

    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void saveOff(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.saveOff(context);
    }

    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void saveOn(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.saveOn(context);
    }
    //endregion

    //region say
    @MNIFunction(normalParams = "string", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void say(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.say(context);
    }
    //endregion

    //TODO schedule

    //region seed
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void seed(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.seed(context);
    }
    //endregion

    //region setblock
    @MNIFunction(normalParams = {"pos3", "BlockState", "SetBlockMode = replace"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setblock(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.setblock(context);
    }
    //endregion

    //region setidletimeout
    @MNIFunction(normalParams = "int", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setidletimeout(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.setidletimeout(context);
    }
    //endregion

    //region setworldspawn
    @MNIFunction(normalParams = {"pos3 = pos3.RELATIVE", "pos2 = pos2.RELATIVE"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setworldspawn(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.setworldspawn(context);
    }
    //endregion

    //region spreadplayers
    @MNIFunction(normalParams = {"pos2", "float", "float", "bool", "entity"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void spreadplayers(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.spreadplayers(context);
    }

    @MNIFunction(identifier = "spreadplayers", normalParams = {"pos2", "float", "float", "int", "bool", "entity"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void spreadplayersUnder(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.spreadplayersUnder(context);
    }
    //endregion

    //region stop
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void stop(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.stop(context);
    }
    //endregion

    //region stopsound
    @MNIFunction(normalParams = {"entity"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void stopAllSound(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.stopAllSound(context);
    }

    @MNIFunction(normalParams = {"entity", "SoundType"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void stopSound(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.stopSound(context);
    }

    @MNIFunction(identifier = "stopSound", normalParams = {"entity", "Sound"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void stopSoundNamed(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.stopSoundNamed(context);
    }

    @MNIFunction(identifier = "stopSound", normalParams = {"entity", "SoundType", "Sound"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void stopSoundTyped(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.stopSoundTyped(context);
    }
    //endregion

    //region summon
    @MNIFunction(normalParams = {"EntityType", "pos3"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void summon(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.summon(context);
    }

    @MNIFunction(identifier = "summon", normalParams = {"EntityType", "pos3", "nbt"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void summonWithNbt(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.summonWithNbt(context);
    }
    //endregion

    //region teammsg
    @MNIFunction(normalParams = "string", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void teammsg(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.teammsg(context);
    }
    //endregion

    //region tellraw
    @MNIFunction(normalParams = {"Player", "text"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void tellraw(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.tellraw(context);
    }
    //endregion

    // tick 不能调用

    //region titile
    @MNIFunction(normalParams = "Player", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void titleClear(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.titleClear(context);
    }

    @MNIFunction(normalParams = "Player", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void titleReset(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.titleReset(context);
    }

    @MNIFunction(normalParams = {"Player", "text", "TitlePos"})
    public static void titleTitle(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.titleTitle(context);
    }

    @MNIFunction(normalParams = {"Player", "Time", "Time", "Time"})
    public static void titleSet(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.titleSet(context);
    }
    //endregion

    //region tm
    @MNIFunction(normalParams = "string", returnType = "mcfpp.minecraft.std:CommandResult")
    public static void tm(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.tm(context);
    }
    //endregion

    //region transfer
    @MNIFunction(normalParams = {"string", "int = 25565", "Player = @s"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void transfer(NativeCallContext context){
        NativeStdCommandOperations.INSTANCE.transfer(context);
    }
    //endregion


}
