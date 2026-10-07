package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIAccessor;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.backend.NativeMinecraftCommandOperations;
import top.mcfpp.mni.NativeCallContext;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.lib.SbObject;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class WorldborderData {

    @MNIFunction(normalParams = {"float", "int = 0"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void add(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderAdd(context);
    }

    @MNIFunction(normalParams = {"pos2 = 0 0"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setCenter(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetCenter(context);
    }

    @MNIFunction(normalParams = {"float = 0.2"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setDamageAmount(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetDamageAmount(context);
    }

    @MNIFunction(normalParams = {"float = 5.0"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setDamageBuffer(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetDamageBuffer(context);
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

    @MNIFunction(normalParams = {"float = 29999984", "int = 0"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setSize(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetSize(context);
    }

    @MNIFunction(normalParams = {"int = 5"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setWarningDistance(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetWarningDistance(context);
    }

    @MNIFunction(normalParams = {"int = 15"}, returnType = "mcfpp.minecraft.std:CommandResult")
    public static void setWarningTime(NativeCallContext context){
        NativeMinecraftCommandOperations.INSTANCE.worldborderSetWarningTime(context);
    }

}
