package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.DataTemplateObject;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.minecraft.PlayerInventory;
import top.mcfpp.util.ValueWrapper;

public class PlayerInventoryData {

    @MNIFunction(caller = "PlayerInventory")
    public static void clear(PlayerInventory caller, ValueWrapper<CommandReturn> re){
        var player = caller.getPlayer();
        var command = Command.Companion.buildAll("clear", player);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(caller = "PlayerInventory", normalParams = "string")
    public static void clear(PlayerInventory caller, MCString itemID, ValueWrapper<CommandReturn> re){
        var player = caller.getPlayer();
        var command = Command.Companion.buildAll("clear", player, itemID);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(caller = "PlayerInventory", normalParams = "ItemPredicate")
    public static void clear(PlayerInventory caller, DataTemplateObject itemPredicate, ValueWrapper<CommandReturn> re){
        var player = caller.getPlayer();
        var command = Command.Companion.buildAll("clear", player, itemPredicate);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(caller = "PlayerInventory", normalParams = {"string", "int"})
    public static void clear(PlayerInventory caller, MCString itemID, MCInt count, ValueWrapper<CommandReturn> re){
        var player = caller.getPlayer();
        var command = Command.Companion.buildAll("clear", player, itemID, count);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(caller = "PlayerInventory", normalParams = {"ItemPredicate", "int"})
    public static void clear(PlayerInventory caller, DataTemplateObject itemPredicate, MCInt count, ValueWrapper<CommandReturn> re){
        var player = caller.getPlayer();
        var command = Command.Companion.buildAll("clear", player, itemPredicate, count);
        Commands.processMacroCommandReturn(re, command);
    }

    //TODO check(Item) -> bool



}
