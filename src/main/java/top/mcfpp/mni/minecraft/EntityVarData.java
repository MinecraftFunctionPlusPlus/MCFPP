package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.entity.EntityVar;
import top.mcfpp.core.lang.obj.EnumVar;
import top.mcfpp.util.ValueWrapper;
public class EntityVarData {

    //region tp
    @MNIFunction(normalParams = "pos3", returnType = "CommandReturn")
    public static void tp(Pos3Var pos, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("tp", caller, pos);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos2"}, returnType = "CommandReturn")
    public static void tp(Pos3Var pos, Pos2Var rotation, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("tp", caller, pos, rotation);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"pos3", "pos3"}, returnType = "CommandReturn")
    public static void tp(Pos3Var pos, Pos3Var faceLocation, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("tp", caller, pos, "facing", faceLocation);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"pos3", "entity<1>", "Anchor = eyes"}, returnType = "CommandReturn")
    public static void tp(Pos3Var pos, EntityVar entity, EnumVar anchor, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("tp", caller, pos, "facing entity", entity, anchor);
        Commands.processMacroCommandReturn(returnValue, command);
    }


}
