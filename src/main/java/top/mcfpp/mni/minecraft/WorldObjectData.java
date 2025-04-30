package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.obj.EnumVar;
import top.mcfpp.util.ValueWrapper;

public class WorldObjectData {
    @MNIFunction(normalParams = "Difficulty", returnType = "CommandReturn")
    public static void setDifficulty(EnumVar difficulty, ValueWrapper<CommandReturn> re) {
        var command = Command.Companion.buildAll("difficulty", difficulty);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIMutator("time")
    public static void getTime(){

    }
}
