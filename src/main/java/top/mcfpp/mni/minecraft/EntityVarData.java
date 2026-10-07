package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.entity.EntityVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.obj.DataTemplateObjectConcrete;
import top.mcfpp.core.lang.obj.EnumVar;
import top.mcfpp.util.ValueWrapper;
public class EntityVarData {

    @MNIFunction(normalParams = {"float", "string"}, caller = "entity", returnType = "CommandReturn")
    public static void setAttributeBase(MCFloat value, String attribute, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "run attribute @s", attribute, "base set", value);
        }else {
            command = Command.Companion.buildAll("attribute", caller, attribute, "base set", value);
        }
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"string", "float"}, caller = "entity", returnType = "CommandReturn")
    public static void addAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "attribute @s");
        }else {
            command = Command.Companion.buildAll("attribute", caller);
        }
        if(modifier instanceof DataTemplateObjectConcrete modifierC) {
            command.build(attribute + " modifier add "
                    + modifierC.getTagStr("id") + " "
                    + modifierC.getTagStr("amount") + " "
                    + modifierC.getTagStr("operation")
            );
        } else {
            command.buildAll(
                    attribute + " modifier add",
                    modifier.getMemberVarWithT("id", MCString.class),
                    modifier.getMemberVarWithT("amount", MCFloat.class),
                    modifier.getMemberVarWithT("operation", MCString.class)
            );
        }
        Commands.processMacroCommandReturn(returnValue, command);
    }

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
