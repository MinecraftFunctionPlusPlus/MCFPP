package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.FunctionVar;
import top.mcfpp.util.ValueWrapper;

public class DebugData {
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void start(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.debugStart(context);
    }
    @MNIFunction(returnType = "mcfpp.minecraft.std:CommandResult")
    public static void stop(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.debugStop(context);
    }

    //TODO函数类型
    @MNIFunction(normalParams = "Function", returnType = "CommandReturn")
    public static void function(FunctionVar function, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("debug function", function);
        Commands.processMacroCommandReturn(re, command);
    }
}
