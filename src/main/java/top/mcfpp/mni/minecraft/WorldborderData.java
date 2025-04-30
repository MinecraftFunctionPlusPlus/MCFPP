package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIAccessor;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.lib.SbObject;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class WorldborderData {

    @MNIFunction(normalParams = {"float", "int = 0"}, returnType = "CommandReturn")
    public static void add(MCFloat distance, MCInt time, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("worldborder add", distance, time);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"pos2 = 0 0"}, returnType = "CommandReturn")
    public static void setCenter(Pos2Var pos, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("worldborder center", pos);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"float = 0.2"}, returnType = "CommandReturn")
    public static void setDamageAmount(MCFloat damageAmount, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("worldborder damage amount", damageAmount);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"float = 5.0"}, returnType = "CommandReturn")
    public static void setDamageBuffer(MCFloat damageBuffer, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("worldborder damage buffer", damageBuffer);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIAccessor("size")
    public static void getSize(NormalCompoundDataObject size, ValueWrapper<MCInt> re){
        var t = new MCInt("size");
        t.setSbObject(SbObject.Companion.getMCFPP_TEMP());
        Function.addCommand("execute store result score size " + SbObject.Companion.getMCFPP_TEMP() + " run worldborder get");
        re.setValue(t);
    }

    @MNIMutator("size")
    public static void setSize(MCInt size, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("worldborder set", size);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"float = 29999984", "int = 0"}, returnType = "CommandReturn")
    public static void setSize(MCFloat size, MCInt time, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("worldborder set", size, time);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"int = 5"}, returnType = "CommandReturn")
    public static void setWarningDistance(MCInt warningDistance, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("worldborder warning distance", warningDistance);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(normalParams = {"int = 15"}, returnType = "CommandReturn")
    public static void setWarningTime(MCInt warningTime, ValueWrapper<CommandReturn> re){
        var command = Command.Companion.buildAll("worldborder warning time", warningTime);
        Commands.processMacroCommandReturn(re, command);
    }

}
