package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.util.ValueWrapper;

public class DatapackData {
    @MNIFunction(normalParams = "string s",returnType = "CommandReturn")
    public static void disable(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("datapack", "disable", s);
        Commands.method3(re, command);
    }
    @MNIFunction(normalParams = "string s",returnType = "CommandReturn")
    public static void enable(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("datapack enable", s);
        Commands.method3(re, command);
    }
    @MNIFunction(normalParams = "string s",returnType = "CommandReturn")
    public static void enableFirst(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("datapack enable", s, "first");
        Commands.method3(re, command);
    }
    @MNIFunction(normalParams = "string s",returnType = "CommandReturn")
    public static void enableLast(MCString s, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("datapack enable", s, "last");
        Commands.method3(re, command);
    }
    @MNIFunction(normalParams = {"string d1", "string d2"},returnType = "CommandReturn")
    public static void enableBefore(MCString d1, MCString d2, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("datapack enable", d1, "before", d2);
        Commands.method3(re, command);
    }
    @MNIFunction(normalParams = {"string d1", "string d2"},returnType = "CommandReturn")
    public static void enableAfter(MCString d1, MCString d2, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("datapack enable", d1, "after", d2);
        Commands.method3(re, command);
    }
    @MNIFunction(returnType = "CommandReturn")
    public static void listAll(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("datapack list");
        Commands.method3(re, command);
    }
    @MNIFunction(returnType = "CommandReturn")
    public static void listEnabled(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("datapack list enabled");
        Commands.method3(re, command);
    }
    @MNIFunction(returnType = "CommandReturn")
    public static void listAvailable(ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("datapack list available");
        Commands.method3(re, command);
    }
}
