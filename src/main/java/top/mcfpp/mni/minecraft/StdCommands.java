package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.entity.EntityVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.resource.Biome;
import top.mcfpp.util.ValueWrapper;

public class StdCommands {

    //region clone
    @MNIFunction(normalParams = {"Area source", "Pos destination", "CloneMaskMode mode = replace", "CloneOperation op = normal"})
    public static void clone(DataTemplateObject source, Pos3Var destination, EnumVar mode, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, mode, op);
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area source", "Pos destination", "CloneMaskMode mode = replace", "CloneOperation op = normal"})
    public static void cloneStrict(DataTemplateObject source, Pos3Var destination, EnumVar mode, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, "strict", mode, op);
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area source", "Pos destination", "BlockPredicate filter", "CloneOperation op = normal"})
    public static void clone(DataTemplateObject source, Pos3Var destination, DataTemplateObject filter, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, "filtered", filter, op);
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area source", "Pos destination", "BlockPredicate filter", "CloneOperation op = normal"})
    public static void cloneStrict(DataTemplateObject source, Pos3Var destination, DataTemplateObject filter, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, "strict filtered", filter, op);
        Commands.method3(re, command);
    }
    //endregion

    //region damage
    @MNIFunction(normalParams = {"entity target", "float amount", "DamageType type = DamageType.GENERIC"})
    public static void damage(EntityVar target, float amount, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type);
        }
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"entity target", "float amount", "Pos location", "DamageType type = DamageType.GENERIC"})
    public static void damageAt(EntityVar target, float amount, Pos3Var location, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type, "at", location);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type, "at", location);
        }
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"entity target", "float amount", "entity<1> by" , "DamageType type = DamageType.GENERIC"})
    public static void damage(EntityVar target, float amount, EntityVar by, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type, "by", by);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type, "by", by);
        }
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"entity target", "float amount", "entity<1> by", "entity<1> source" , "DamageType type = DamageType.GENERIC"})
    public static void damage(EntityVar target, float amount, EntityVar by, EntityVar source, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type, "by", by, "from", source);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type, "by", by, "from", source);
        }
        Commands.method3(re, command);
    }
    //endregion

    //region enchant
    //TODO 更高等级的附魔支持
    @MNIFunction(normalParams = {"entity target", "Enchantment enchantment", "int level = 1", "Slot slot = Slot.weapon_mainhand"})
    public static void enchant(EntityVar target, DataTemplateObject enchantment, MCInt level, EnumVar slot, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("enchant", target, enchantment, level, slot);
        Commands.method3(re, command);
    }
    //endregion

    //region fill
    @MNIFunction(normalParams = {"Area area", "BlockState block"}, returnType = "CommandReturn")
    public static void fillKeep(DataTemplateObject area, DataTemplateObject block, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fill", area, block, "keep");
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area area", "BlockState block"}, returnType = "CommandReturn")
    public static void fillReplace(DataTemplateObject area, DataTemplateObject block, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fill", area, block);
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area area", "BlockState block", "BlockPredicate filter"}, returnType = "CommandReturn")
    public static void fillReplace(DataTemplateObject area, DataTemplateObject block, DataTemplateObject filter, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fill", area, block, "replace", filter);
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area area", "BlockState block", "BlockPredicate filter", "FillMode mode"}, returnType = "CommandReturn")
    public static void fillReplace(DataTemplateObject area, DataTemplateObject block, DataTemplateObject filter, EnumVar mode, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fill", area, block, "replace", filter, mode);
        Commands.method3(re, command);
    }
    //endregion

    //region fillBiome
    @MNIFunction(normalParams = {"Area area", "Biome biome"}, returnType = "CommandReturn")
    public static void fillBiome(DataTemplateObject area, Biome biome, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fillbiome", area, biome);
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area area", "Biome biome", "Biome replaceBiome"}, returnType = "CommandReturn")
    public static void fillBiome(DataTemplateObject area, Biome biome, Biome replaceBiome, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("fillbiome", area, biome, "replace", replaceBiome);
        Commands.method3(re, command);
    }
    //endregion

    //region forceload
    @MNIFunction(normalParams = {"pos2 from", "pos2 to"}, returnType = "CommandReturn")
    public static void forceload(Pos2Var from, Pos2Var to, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload", "add", from, to);
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"pos2 from", "pos2 to"}, returnType = "CommandReturn")
    public static void forceloadRemove(Pos2Var from, Pos2Var to, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload", "remove", from, to);
        Commands.method3(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void forceloadRemoveAll(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload remove all");
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"pos2 from", "pos2 to"}, returnType = "CommandReturn")
    public static void forceloadQuery(Pos2Var from, Pos2Var to, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload query", from, to);
        Commands.method3(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void forceloadQueryAll(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("forceload query");
        Commands.method3(re, command);
    }
    //endregion

    //region help
    @MNIFunction(returnType = "CommandReturn")
    public static void help(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("help");
        Commands.method3(re, command);
    }

    @MNIFunction(normalParams = {"string command"}, returnType = "CommandReturn")
    public static void help(MCString command, ValueWrapper<CommandReturn> re){
        var command1 = Command.Companion.buildAll("help", command);
        Commands.method3(re, command1);
    }
    //endregion

    //region jfr
    @MNIFunction(returnType = "CommandReturn")
    public static void jfrStart(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("jfr start");
        Commands.method3(re, command);
    }

    @MNIFunction(returnType = "CommandReturn")
    public static void jfrStop(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("jfr stop");
        Commands.method3(re, command);
    }
    //endregion


}
