package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.EnumVar;
import top.mcfpp.util.ValueWrapper;

public class WorldData {
    @MNIFunction(normalParams = "Difficulty difficulty", returnType = "CommandReturn")
    public static void setDifficulty(EnumVar difficulty, ValueWrapper<CommandReturn> re) {
        var command = Command.Companion.buildAll("difficulty", difficulty);
        Commands.INSTANCE.method3(re, command);
    }
}
