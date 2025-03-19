package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.DataTemplateObject;
import top.mcfpp.core.lang.EnumVar;
import top.mcfpp.core.lang.Pos3Var;
import top.mcfpp.core.lang.entity.EntityVar;
import top.mcfpp.util.ValueWrapper;

public class StdCommands {

    //region clone
    @MNIFunction(normalParams = {"Area source", "Pos destination", "CloneMaskMode mode = replace", "CloneOperation op = normal"})
    public static void clone(DataTemplateObject source, Pos3Var destination, EnumVar mode, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, mode, op);
        Commands.INSTANCE.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area source", "Pos destination", "CloneMaskMode mode = replace", "CloneOperation op = normal"})
    public static void cloneStrict(DataTemplateObject source, Pos3Var destination, EnumVar mode, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, "strict", mode, op);
        Commands.INSTANCE.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area source", "Pos destination", "BlockPredicate filter", "CloneOperation op = normal"})
    public static void clone(DataTemplateObject source, Pos3Var destination, DataTemplateObject filter, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, "filtered", filter, op);
        Commands.INSTANCE.method3(re, command);
    }

    @MNIFunction(normalParams = {"Area source", "Pos destination", "BlockPredicate filter", "CloneOperation op = normal"})
    public static void cloneStrict(DataTemplateObject source, Pos3Var destination, DataTemplateObject filter, EnumVar op, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("clone", source, destination, "strict filtered", filter, op);
        Commands.INSTANCE.method3(re, command);
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
        Commands.INSTANCE.method3(re, command);
    }

    @MNIFunction(normalParams = {"entity target", "float amount", "Pos location", "DamageType type = DamageType.GENERIC"})
    public static void damageAt(EntityVar target, float amount, Pos3Var location, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type, "at", location);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type, "at", location);
        }
        Commands.INSTANCE.method3(re, command);
    }

    @MNIFunction(normalParams = {"entity target", "float amount", "entity<1> by" , "DamageType type = DamageType.GENERIC"})
    public static void damage(EntityVar target, float amount, EntityVar by, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type, "by", by);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type, "by", by);
        }
        Commands.INSTANCE.method3(re, command);
    }

    @MNIFunction(normalParams = {"entity target", "float amount", "entity<1> by", "entity<1> source" , "DamageType type = DamageType.GENERIC"})
    public static void damage(EntityVar target, float amount, EntityVar by, EntityVar source, EnumVar type, ValueWrapper<CommandReturn> re){
        Command command;
        if(target.isMulti()){
            command = Command.Companion.buildAll("execute as", target, "run damage @s", amount, type, "by", by, "from", source);
        }else {
            command = Command.Companion.buildAll("damage", target, amount, type, "by", by, "from", source);
        }
        Commands.INSTANCE.method3(re, command);
    }
    //endregion

}
