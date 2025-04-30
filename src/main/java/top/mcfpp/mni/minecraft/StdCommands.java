package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.core.lang.entity.EntityVar;
import top.mcfpp.core.lang.entity.PlayerVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.NBTBasedData;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.obj.EnumVar;
import top.mcfpp.core.lang.obj.TypeDataTemplateObject;
import top.mcfpp.util.ValueWrapper;

public class StdCommands {

    //region clone
    @MNIFunction(normalParams = {"Area", "pos3", "CloneMaskMode = replace", "CloneOperation = normal"})
    public static void clone(DataTemplateObject source, Pos3Var destination, EnumVar mode, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, mode, op);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Area", "pos3", "CloneMaskMode = replace", "CloneOperation = normal"})
    public static void cloneStrict(DataTemplateObject source, Pos3Var destination, EnumVar mode, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, "strict", mode, op);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Area", "pos3", "BlockPredicate", "CloneOperation = normal"})
    public static void clone(DataTemplateObject source, Pos3Var destination, DataTemplateObject filter, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, "filtered", filter, op);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Area", "pos3", "BlockPredicate", "CloneOperation = normal"})
    public static void cloneStrict(DataTemplateObject source, Pos3Var destination, DataTemplateObject filter, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, "strict filtered", filter, op);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region damage
    @MNIFunction(normalParams = {"entity", "float", "DamageType = GENERIC"})
    public static void damage(EntityVar target, float amount, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type);
        }
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "float", "pos3 location", "DamageType = GENERIC"})
    public static void damageAt(EntityVar target, float amount, Pos3Var location, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type, "at", location);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type, "at", location);
        }
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "float", "entity<1>" , "DamageType = GENERIC"})
    public static void damage(EntityVar target, float amount, EntityVar by, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type, "by", by);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type, "by", by);
        }
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "float", "entity<1>", "entity<1> source" , "DamageType = GENERIC"})
    public static void damage(EntityVar target, float amount, EntityVar by, EntityVar source, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type, "by", by, "from", source);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type, "by", by, "from", source);
        }
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region enchant
    //TODO 更高等级的附魔支持
    @MNIFunction(normalParams = {"entity", "Enchantment", "int level = 1", "Slot = weapon_mainhand"})
    public static void enchant(EntityVar target, DataTemplateObject enchantment, MCInt level, EnumVar slot, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("enchant", target, enchantment, level, slot);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region fill
    @MNIFunction(normalParams = {"Area", "BlockState"}, returnType = "CommandReturn")
    public static void fillKeep(DataTemplateObject area, DataTemplateObject block, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fill", area, block, "keep");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Area", "BlockState"}, returnType = "CommandReturn")
    public static void fillReplace(DataTemplateObject area, DataTemplateObject block, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fill", area, block);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Area", "BlockState", "BlockPredicate"}, returnType = "CommandReturn")
    public static void fillReplace(DataTemplateObject area, DataTemplateObject block, DataTemplateObject filter, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fill", area, block, "replace", filter);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Area", "BlockState", "BlockPredicate", "FillMode"}, returnType = "CommandReturn")
    public static void fillReplace(DataTemplateObject area, DataTemplateObject block, DataTemplateObject filter, EnumVar mode, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fill", area, block, "replace", filter, mode);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region fillBiome
    @MNIFunction(normalParams = {"Area", "Biome"}, returnType = "CommandReturn")
    public static void fillBiome(DataTemplateObject area, DataTemplateObject biome, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fillbiome", area, biome);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Area", "Biome", "Biome"}, returnType = "CommandReturn")
    public static void fillBiome(DataTemplateObject area, DataTemplateObject biome, DataTemplateObject replaceBiome, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fillbiome", area, biome, "replace", replaceBiome);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region forceload
    @MNIFunction(normalParams = {"pos2", "pos2"}, returnType = "CommandReturn")
    public static void forceload(Pos2Var from, Pos2Var to, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload", "add", from, to);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos2", "pos2"}, returnType = "CommandReturn")
    public static void forceloadRemove(Pos2Var from, Pos2Var to, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload", "remove", from, to);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void forceloadRemoveAll(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload remove all");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos2", "pos2"}, returnType = "CommandReturn")
    public static void forceloadQuery(Pos2Var from, Pos2Var to, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload query", from, to);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void forceloadQueryAll(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload query");
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region give
    @MNIFunction(normalParams = {"player", "Item"}, returnType = "CommandReturn")
    public static void give(PlayerVar player, DataTemplateObject item, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("give", player, item);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"player", "Item", "int"}, returnType = "CommandReturn")
    public static void give(PlayerVar player, DataTemplateObject item, MCInt count, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("give", player, item, count);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region help
    @MNIFunction(returnType = "CommandReturn")
    public static void help(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("help");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "CommandReturn")
    public static void help(MCString command, ValueWrapper<CommandReturn> re){
        var command1 = Command.Companion.buildAll("help", command);
        Commands.processMacroCommandReturn(re, command1);
    }
    //endregion

    //region jfr
    @MNIFunction(returnType = "CommandReturn")
    public static void jfrStart(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("jfr start");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void jfrStop(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("jfr stop");
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region kick
    @MNIFunction(normalParams = {"Player", "string = \"\""}, returnType = "CommandReturn")
    public static void kick(PlayerVar player, MCString reason, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("kick", player, reason);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region kill
    @MNIFunction(returnType = "CommandReturn")
    public static void kill(ValueWrapper<CommandReturn> re){
        var command = new Command("kill");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity"}, returnType = "CommandReturn")
    public static void kill(EntityVar target, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("kill", target);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region list
    @MNIFunction(returnType = "CommandReturn")
    public static void list(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("list");
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region locate
    @MNIFunction(identifier = "locate", normalParams = "Structure", returnType = "CommandReturn")
    public static void locateStructure(DataTemplateObject structure, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("locate", structure);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(identifier = "locate", normalParams = "Biome", returnType = "CommandReturn")
    public static void locateBiome(DataTemplateObject biome, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("locate", biome);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(identifier = "locate", normalParams = "Poi", returnType = "CommandReturn")
    public static void locatePoi(DataTemplateObject poi, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("locate", poi);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region
    @MNIFunction(normalParams = {"Player", "LootTable"}, returnType = "CommandReturn")
    public static void lootGive(PlayerVar player, DataTemplateObject lootSource, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", player, "loot", lootSource);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable"}, returnType = "CommandReturn")
    public static void lootInsert(Pos3Var pos, DataTemplateObject lootSource, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", pos, "loot", lootSource);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable"}, returnType = "CommandReturn")
    public static void lootSpawn(Pos3Var pos, DataTemplateObject lootSource, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", pos, "loot", lootSource);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "Slot", "LootTable"}, returnType = "CommandReturn")
    public static void lootReplace(Pos3Var pos, EnumVar slot, DataTemplateObject lootSource, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace block", pos, slot, "loot", lootSource);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "Slot", "LootTable"}, returnType = "CommandReturn")
    public static void lootReplace(EntityVar entity, EnumVar slot, DataTemplateObject lootSource, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace entity", entity, slot, "loot", lootSource);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "Slot", "LootTable", "int"}, returnType = "CommandReturn")
    public static void lootReplace(Pos3Var pos, EnumVar slot, DataTemplateObject lootSource, MCInt count, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace block", pos, slot, lootSource, "loot", count);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "Slot", "LootTable", "int"}, returnType = "CommandReturn")
    public static void lootReplace(EntityVar entity, EnumVar slot, DataTemplateObject lootSource, MCInt count, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace entity", entity, slot, lootSource, "loot", count);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Player", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootGiveFish(PlayerVar player, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", player, "fish", lootSource, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "LootTable", "pos3", "Item"}, returnType = "CommandReturn")
    public static void lootGiveFishUsing(EntityVar entity, DataTemplateObject lootSource, Pos3Var pos, DataTemplateObject item, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", entity, "fish", lootSource, pos, item);
        Commands.processMacroCommandReturn(re, command);
    }
    @MNIFunction(normalParams = {"entity", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootGiveFishUsingMainHand(EntityVar entity, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", entity, "fish", lootSource, pos, "mainhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootGiveFishUsingOffHand(EntityVar entity, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", entity, "fish", lootSource, pos, "offhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootInsertFish(Pos3Var targetPos, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", targetPos, "fish", lootSource, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3", "Item"}, returnType = "CommandReturn")
    public static void lootInsertFishUsing(Pos3Var targetPos, DataTemplateObject lootSource, Pos3Var pos, DataTemplateObject item, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", targetPos, "fish", lootSource, pos, item);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootInsertFishUsingMainHand(Pos3Var targetPos, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", targetPos, "fish", lootSource, pos, "mainhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootInsertFishUsingOffHand(Pos3Var targetPos, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", targetPos, "fish", lootSource, pos, "offhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootSpawnFish(Pos3Var targetPos, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", targetPos, "fish", lootSource, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3", "Item"}, returnType = "CommandReturn")
    public static void lootSpawnFishUsing(Pos3Var targetPos, DataTemplateObject lootSource, Pos3Var pos, DataTemplateObject item, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", targetPos, "fish", lootSource, pos, item);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootSpawnFishUsingMainHand(Pos3Var targetPos, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", targetPos, "fish", lootSource, pos, "mainhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootSpawnFishUsingOffHand(Pos3Var targetPos, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", targetPos, "fish", lootSource, pos, "offhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "Slot", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootReplaceFish(Pos3Var targetPos, EnumVar slot, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace block", targetPos, slot, "fish", lootSource, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "Slot", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootReplaceFish(EntityVar entity, EnumVar slot, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace entity", entity, slot, "fish", lootSource, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "Slot", "int", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootReplaceFish(Pos3Var targetPos, EnumVar slot, MCInt count, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace block", targetPos, slot, count, "fish", lootSource, pos, "loot");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "Slot", "int", "LootTable", "pos3"}, returnType = "CommandReturn")
    public static void lootReplaceFish(EntityVar entity, EnumVar slot, MCInt count, DataTemplateObject lootSource, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace entity", entity, slot, count, "fish", lootSource, pos, "loot");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Player", "entity<1>"}, returnType = "CommandReturn")
    public static void lootGiveKill(PlayerVar player, EntityVar entityVar, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", player, "kill", entityVar);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "entity<1>"}, returnType = "CommandReturn")
    public static void lootInsert(Pos3Var pos, EntityVar entityVar, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", pos, "kill", entityVar);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "entity<1>"}, returnType = "CommandReturn")
    public static void lootSpawn(Pos3Var pos, EntityVar entityVar, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", pos, "kill", entityVar);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "Slot", "entity<1>"}, returnType = "CommandReturn")
    public static void lootReplace(Pos3Var pos, EnumVar slot, EntityVar entityVar, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace block", pos, slot, "kill", entityVar);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "Slot", "entity<1>"}, returnType = "CommandReturn")
    public static void lootReplace(EntityVar entity, EnumVar slot, EntityVar entityVar, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace entity", entity, slot, "kill", entityVar);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "Slot", "int", "entity<1>"}, returnType = "CommandReturn")
    public static void lootReplace(Pos3Var pos, EnumVar slot, MCInt count, EntityVar entityVar, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace block", pos, slot, count, "kill", entityVar);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "Slot", "int", "entity<1>"}, returnType = "CommandReturn")
    public static void lootReplace(EntityVar entity, EnumVar slot, MCInt count, EntityVar entityVar, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace entity", entity, slot, count, "kill", entityVar);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Player", "pos3"}, returnType = "CommandReturn")
    public static void lootGiveMine(PlayerVar player, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", player, "mine", pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Player", "pos3", "Item"}, returnType = "CommandReturn")
    public static void lootGiveMineUsing(PlayerVar player, Pos3Var pos, DataTemplateObject item, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", player, "mine", pos, item);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Player", "pos3"}, returnType = "CommandReturn")
    public static void lootGiveMineUsingMainHand(PlayerVar player, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", player, "mine", pos, "mainhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Player", "pos3"}, returnType = "CommandReturn")
    public static void lootGiveMineUsingOffHand(PlayerVar player, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot give", player, "mine", pos, "offhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "CommandReturn")
    public static void lootInsertMine(Pos3Var targetPos, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", targetPos, "mine", pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos3", "Item"}, returnType = "CommandReturn")
    public static void lootInsertMineUsing(Pos3Var targetPos, Pos3Var pos, DataTemplateObject item, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", targetPos, "mine", pos, item);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "CommandReturn")
    public static void lootInsertMineUsingMainHand(Pos3Var targetPos, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", targetPos, "mine", pos, "mainhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "CommandReturn")
    public static void lootInsertMineUsingOffHand(Pos3Var targetPos, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot insert", targetPos, "mine", pos, "offhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "CommandReturn")
    public static void lootSpawnMine(Pos3Var targetPos, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", targetPos, "mine", pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos3", "Item"}, returnType = "CommandReturn")
    public static void lootSpawnMineUsing(Pos3Var targetPos, Pos3Var pos, DataTemplateObject item, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", targetPos, "mine", pos, item);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "CommandReturn")
    public static void lootSpawnMineUsingMainHand(Pos3Var targetPos, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", targetPos, "mine", pos, "mainhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "CommandReturn")
    public static void lootSpawnMineUsingOffHand(Pos3Var targetPos, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot spawn", targetPos, "mine", pos, "offhand");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "Slot", "pos3"}, returnType = "CommandReturn")
    public static void lootReplaceMine(Pos3Var targetPos, EnumVar slot, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace block", targetPos, slot, "mine", pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "Slot", "pos3"}, returnType = "CommandReturn")
    public static void lootReplaceMine(EntityVar entity, EnumVar slot, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace entity", entity, slot, "mine", pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos3", "Slot", "int", "pos3"}, returnType = "CommandReturn")
    public static void lootReplaceMine(Pos3Var targetPos, EnumVar slot, MCInt count, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace block", targetPos, slot, count, "mine", pos);
        Commands.processMacroCommandReturn(re, command);
    }


    @MNIFunction(normalParams = {"entity", "Slot", "int", "pos3"}, returnType = "CommandReturn")
    public static void lootReplaceMine(EntityVar entity, EnumVar slot, MCInt count, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("loot replace entity", entity, slot, count, "mine", pos);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region me
    @MNIFunction(normalParams = "string", returnType = "CommandReturn")
    public static void me(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("me", s);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region pardon
    @MNIFunction(normalParams = "string", returnType = "CommandReturn")
    public static void pardon(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("pardon", s);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region pardon-ip
    @MNIFunction(normalParams = "string", returnType = "CommandReturn")
    public static void pardonIp(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("pardon-ip", s);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //particle
    @MNIFunction(normalParams = {"Particle"}, returnType = "CommandReturn")
    public static void particle(EnumVar particle, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("particle", particle);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Particle", "pos3"}, returnType = "CommandReturn")
    public static void particle(EnumVar particle, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("particle", particle, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Particle", "pos3", "pos3", "float", "int"}, returnType = "CommandReturn")
    public static void particle(EnumVar particle, Pos3Var pos, Pos3Var delta, MCFloat speed, MCInt count, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("particle", particle, pos, delta, speed, count);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Particle", "pos3", "pos3", "float", "int"}, returnType = "CommandReturn")
    public static void particleForce(EnumVar particle, Pos3Var pos, Pos3Var delta, MCFloat speed, MCInt count, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("particle", particle, pos, delta, speed, count, "force");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Particle", "pos3", "pos3", "float", "int", "Player"}, returnType = "CommandReturn")
    public static void particle(EnumVar particle, Pos3Var pos, Pos3Var delta, MCFloat speed, MCInt count, PlayerVar player, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("particle", particle, pos, delta, speed, count, "normal", player);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Particle", "pos3", "pos3", "float", "int", "Player"}, returnType = "CommandReturn")
    public static void particleForce(EnumVar particle, Pos3Var pos, Pos3Var delta, MCFloat speed, MCInt count, PlayerVar player, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("particle", particle, pos, delta, speed, count, "force", player);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region perf
    @MNIFunction(returnType = "CommandReturn")
    public static void perfStart(ValueWrapper<CommandReturn> re){
        var command = new Command("perf start");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void perfStop(ValueWrapper<CommandReturn> re){
        var command = new Command("perf stop");
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region place
    @MNIFunction(identifier = "place",normalParams = {"Feature"})
    public static void placeFeature(DataTemplateObject feature, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("place feature", feature);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(identifier = "place",normalParams = {"Feature", "pos3"})
    public static void placeFeature(DataTemplateObject feature, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("place feature", feature, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(identifier = "place",normalParams = {"TemplatePool", "string", "int"})
    public static void placeJigsaw(DataTemplateObject pool, MCString target, MCInt maxDepth, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("place jigsaw", pool, target, maxDepth);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(identifier = "place",normalParams = {"TemplatePool", "string", "int", "pos3"})
    public static void placeJigsaw(DataTemplateObject pool, MCString target, MCInt maxDepth, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("place jigsaw", pool, target, maxDepth, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(identifier = "place",normalParams = {"Structure"})
    public static void placeStructure(DataTemplateObject structure, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("place structure", structure);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(identifier = "place",normalParams = {"Structure", "pos3"})
    public static void placeStructure(DataTemplateObject structure, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("place structure", structure, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(identifier = "place", normalParams = {"string", "pos3 = pos3.RELATIVE", "PlaceRotation = none", "PlaceMirror = none", "float = 1.0", "int = System.randInt()"})
    public static void placeTemplate(MCString name, Pos3Var pos, EnumVar rotation, EnumVar mirror, MCFloat integrity, MCInt seed, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("place", name, pos, rotation, mirror, integrity, seed);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(identifier = "placeStrict" ,normalParams = {"string", "pos3 = pos3.HERE", "PlaceRotation = none", "PlaceMirror = none", "float = 1.0", "int = System.randInt()"})
    public static void placeTemplateStrict(MCString name, Pos3Var pos, EnumVar rotation, EnumVar mirror, MCFloat integrity, MCInt seed, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("place", name, pos, rotation, mirror, integrity, seed, "strict");
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region playsound
    @MNIFunction(normalParams = {"Sound", "SoundType", "Player = @s", "pos3 = pos3.HERE", "float = 1.0", "float = 1.0", "float = 0.0"})
    public static void playsound(DataTemplateObject sound, EnumVar type, PlayerVar player, Pos3Var pos, MCFloat volume, MCFloat pitch, MCFloat distance, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("playsound", sound, type, player, pos, volume, pitch, distance);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region publish
    @MNIFunction(normalParams = {"bool = false", "Gamemode = survival", "int = System.randInt(1025,65536)"} ,returnType = "CommandReturn")
    public static void publish(BaseBool allowCommands, EnumVar gamemode, MCInt port, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("publish", allowCommands, gamemode, port);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region reload
    @MNIFunction(returnType = "CommandReturn")
    public static void reload(ValueWrapper<CommandReturn> re){
        var command = new Command("reload");
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region save save-all save-off save-on
    @MNIFunction(returnType = "CommandReturn")
    public static void save(ValueWrapper<CommandReturn> re){
        var command = new Command("save");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void saveAll(ValueWrapper<CommandReturn> re){
        var command = new Command("save-all");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void saveOff(ValueWrapper<CommandReturn> re){
        var command = new Command("save-off");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void saveOn(ValueWrapper<CommandReturn> re){
        var command = new Command("save-on");
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region say
    @MNIFunction(normalParams = "string", returnType = "CommandReturn")
    public static void say(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("say", s);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //TODO schedule

    //region seed
    @MNIFunction(returnType = "CommandReturn")
    public static void seed(ValueWrapper<CommandReturn> re){
        var command = new Command("seed");
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region setblock
    @MNIFunction(normalParams = {"pos3", "BlockState", "SetBlockMode = replace"}, returnType = "CommandReturn")
    public static void setblock(Pos3Var pos, DataTemplateObject block, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("setblock", pos, block);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region setidletimeout
    @MNIFunction(normalParams = "int", returnType = "CommandReturn")
    public static void setidletimeout(MCInt timeout, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("setidletimeout", timeout);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region setworldspawn
    @MNIFunction(normalParams = {"pos3 = pos3.RELATIVE", "pos2.RELATIVE"}, returnType = "CommandReturn")
    public static void setworldspawn(Pos3Var pos, Pos2Var rotation, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("setworldspawn", pos, rotation);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region spreadplayers
    @MNIFunction(normalParams = {"pos2", "float", "float", "bool", "entity"}, returnType = "CommandReturn")
    public static void spreadplayers(Pos2Var pos, MCFloat xz, MCFloat y, BaseBool force, EntityVar entity, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("spreadplayers", pos, xz, y, force, entity);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos2", "float", "float", "int", "bool", "entity"}, returnType = "CommandReturn")
    public static void spreadplayers(Pos2Var pos, MCFloat xz, MCFloat y, MCInt count, BaseBool force, EntityVar entity, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("spreadplayers", pos, xz, "under", y, count, force, entity);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region stop
    @MNIFunction(returnType = "CommandReturn")
    public static void stop(ValueWrapper<CommandReturn> re){
        var command = new Command("stop");
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region stopsound
    @MNIFunction(normalParams = {"entity"}, returnType = "CommandReturn")
    public static void stopAllSound(EntityVar entity, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("stopsound", entity);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "SoundType"}, returnType = "CommandReturn")
    public static void stopSound(EntityVar entity, EnumVar type, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("stopsound", entity, type);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "Sound"}, returnType = "CommandReturn")
    public static void stopSound(EntityVar entity, DataTemplateObject sound, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("stopsound", entity, "*", sound);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"entity", "SoundType", "Sound"}, returnType = "CommandReturn")
    public static void stopSound(EntityVar entity, EnumVar type, DataTemplateObject sound, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("stopsound", entity, type, sound);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region summon
    @MNIFunction(normalParams = {"EntityType", "pos3"}, returnType = "CommandReturn")
    public static void summon(DataTemplateObject entity, Pos3Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("summon", entity, pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"EntityType", "pos3", "nbt"}, returnType = "CommandReturn")
    public static void summon(DataTemplateObject entity, Pos3Var pos, NBTBasedData nbt, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("summon", entity, pos, nbt);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region teammsg
    @MNIFunction(normalParams = "string", returnType = "CommandReturn")
    public static void teammsg(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("teammsg", s);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region tellraw
    @MNIFunction(normalParams = {"Player", "text"}, returnType = "CommandReturn")
    public static void tellraw(PlayerVar player, JsonText text, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("tellraw", player, text);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    // tick 不能调用

    //region titile
    @MNIFunction(normalParams = "Player", returnType = "CommandReturn")
    public static void titleClear(PlayerVar player, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("title", player, "clear");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = "Player", returnType = "CommandReturn")
    public static void titleReset(PlayerVar player, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("title", player, "reset");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Player", "text", "TitlePos"})
    public static void titleTitle(PlayerVar player, JsonText text, EnumVar pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("title", player, pos, text);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"Player", "Time", "Time", "Time"})
    public static void titleSet(PlayerVar player, TypeDataTemplateObject fadeIn, TypeDataTemplateObject stay, TypeDataTemplateObject fadeOut, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("title", player, "times", fadeIn, stay, fadeOut);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region tm
    @MNIFunction(normalParams = "string", returnType = "CommandReturn")
    public static void tm(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("tm", s);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion

    //region transfer
    @MNIFunction(normalParams = {"string", "int = 25565", "Player = @s"}, returnType = "CommandReturn")
    public static void transfer(MCString hostname, MCInt port, PlayerVar target, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("transfer", hostname, port, target);
        Commands.processMacroCommandReturn(re, command);
    }
    //endregion


}
