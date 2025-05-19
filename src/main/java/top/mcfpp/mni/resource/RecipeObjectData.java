package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.entity.PlayerVar;
import top.mcfpp.util.ValueWrapper;

public class RecipeObjectData {
    @MNIFunction(normalParams = "Player!", returnType = "CommandReturn")
    public static void giveAll(PlayerVar player, ValueWrapper<CommandReturn> re) {
        var command = Command.Companion.buildAll("recipe give", player, "*");
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = "Player!", returnType = "CommandReturn")
    public static void takeAll(PlayerVar player, ValueWrapper<CommandReturn> re) {
        var command = Command.Companion.buildAll("recipe take", player, "*");
        Commands.processMacroCommandReturn(re, command);
    }
}
