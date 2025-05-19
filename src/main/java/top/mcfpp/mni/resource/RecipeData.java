package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.entity.PlayerVar;
import top.mcfpp.util.ValueWrapper;

public class RecipeData {
    @MNIFunction(normalParams = "Player!", caller = "Recipe", returnType = "CommandReturn")
    public static void give(PlayerVar player, DataTemplateObject caller, ValueWrapper<CommandReturn> re) {
        var command = Command.Companion.buildAll("recipe give", player, caller);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = "Player!", caller = "Recipe", returnType = "CommandReturn")
    public static void take(PlayerVar player, DataTemplateObject caller, ValueWrapper<CommandReturn> re) {
        var command = Command.Companion.buildAll("recipe take", player, caller);
        Commands.processMacroCommandReturn(re, command);
    }
}        
