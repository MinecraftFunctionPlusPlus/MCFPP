package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.FunctionVar;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class DebugData {
    @MNIFunction(returnType = "CommandReturn")
    public static void start(ValueWrapper<CommandReturn> re){
        var command = new Command("debug start");
        Commands.method3(re, command);
    }
    @MNIFunction(returnType = "CommandReturn")
    public static void stop(ValueWrapper<CommandReturn> re){
        var command = new Command("debug stop");
        Commands.method3(re, command);
    }

    //TODO函数类型
    @MNIFunction(normalParams = "Function", returnType = "CommandReturn")
    public static void function(FunctionVar function, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("debug function", function);
        Commands.method3(re, command);
    }
}
