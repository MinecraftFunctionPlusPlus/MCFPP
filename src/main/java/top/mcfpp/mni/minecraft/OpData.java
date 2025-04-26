package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.entity.PlayerVar;
import top.mcfpp.util.ValueWrapper;

public class OpData {
    @MNIFunction(normalParams = "Player",returnType = "CommandReturn")
    public static void deop(PlayerVar player, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("op", player);
        Commands.method3(re, command);
    }
    @MNIFunction(normalParams = "Player",returnType = "CommandReturn")
    public static void op(PlayerVar player, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("op", player);
        Commands.method3(re, command);
    }
}
