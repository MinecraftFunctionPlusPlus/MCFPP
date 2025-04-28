package top.mcfpp.mni.minecraft;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMember;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.entity.EntityVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.mni.hidden.AttributeData;
import top.mcfpp.model.CompoundData;
import top.mcfpp.util.ValueWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EntityVarData {

    @MNIMember
    public static ArrayList<Var<?>> getMembers() {
        NormalCompoundDataObject attributes = new NormalCompoundDataObject("attributes", Map.of());
        CompoundData attributeData = new CompoundData("attribute", "mcfpp.hidden");
        attributeData.getNativeFromClass(AttributeData.class);
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "armor", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "armor_toughness", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "attack_damage", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "attack_speed", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "burning_time", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "explosion_knockback_resistance", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "fall_damage_multiplier", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "flying_speed", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "follow_range", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "gravity", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "jump_strength", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "knockback_resistance", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "luck", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "max_absorption", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "max_health", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "movement_efficiency", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "movement_speed", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "oxygen_bonus", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "safe_fall_distance", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "scale", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "step_height", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "water_movement_efficiency", Map.of()));
        //TODO 这个是僵尸独有的
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "spawn_reinforcements", Map.of()));
        //属性
        return new ArrayList<>(List.of(attributes));
    }

    @MNIFunction(normalParams = {"float", "string"}, caller = "entity", returnType = "CommandReturn")
    public static void setAttributeBase(MCFloat value, String attribute, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "run attribute @s", AttributeData.attributeMap.get(attribute), "base set", value);
        }else {
            command = Command.Companion.buildAll("attribute", caller, AttributeData.attributeMap.get(attribute), "base set", value);
        }
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"string", "float"}, caller = "entity", returnType = "CommandReturn")
    public static void getAttributeBase(String attribute, EntityVar caller, MCFloat scale, @NotNull ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "run attribute @s", AttributeData.attributeMap.get(attribute), "base get", scale);
        }else {
            command = Command.Companion.buildAll("attribute", caller, AttributeData.attributeMap.get(attribute), "base get", scale);
        }
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"string", "float"}, caller = "entity", returnType = "CommandReturn")
    public static void getAttribute(String attribute, EntityVar caller, MCFloat scale, @NotNull ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "run attribute @s", AttributeData.attributeMap.get(attribute), "get", scale);
        }else {
            command = Command.Companion.buildAll("attribute", caller, AttributeData.attributeMap.get(attribute), "get", scale);
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
            command.build(AttributeData.attributeMap.get(attribute) + " modifier add "
                    + modifierC.getTagStr("id") + " "
                    + modifierC.getTagStr("amount") + " "
                    + modifierC.getTagStr("operation")
            );
        } else {
            command.buildAll(
                    AttributeData.attributeMap.get(attribute) + " modifier add",
                    modifier.getMemberVarWithT("id", MCString.class),
                    modifier.getMemberVarWithT("amount", MCFloat.class),
                    modifier.getMemberVarWithT("operation", MCString.class)
            );
        }
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"string", "float"}, caller = "entity", returnType = "CommandReturn")
    public static void removeAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "attribute @s", AttributeData.attributeMap.get(attribute), "modifier remove");
        }else {
            command = Command.Companion.buildAll("attribute", caller, AttributeData.attributeMap.get(attribute), "modifier remove");
        }
        if(modifier instanceof DataTemplateObjectConcrete modifierC) {
            command.build(modifierC.getTagStr("id"));
        } else {
            command.buildAll(modifier.getMemberVarWithT("id", MCString.class));
        }
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"string", "float"}, caller = "entity", returnType = "CommandReturn")
    public static void getAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, MCFloat scale, ValueWrapper<CommandReturn> returnValue){
        Command command;
        var id = modifier.getMemberVarWithT("id", MCString.class);
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "attribute @s", AttributeData.attributeMap.get(attribute), "modifier value get", id, scale);
        }else {
            command = Command.Companion.buildAll("attribute", caller, AttributeData.attributeMap.get(attribute), "modifier value get", id, scale);
        }
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"Effect", "int = 30", "int = 0", "bool hideParticles = false"}, caller = "entity", returnType = "CommandReturn")
    public static void effect(DataTemplateObject effect, int time, int amplifier, boolean hideParticles, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("effect give", caller, effect, time, amplifier, hideParticles);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"Effect", "int = 0", "bool = false"}, caller = "entity", returnType = "CommandReturn")
    public static void effectInfinite(DataTemplateObject effect, int amplifier, boolean hideParticles, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("effect give", caller, effect, "infinite", amplifier, hideParticles);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(caller = "entity", returnType = "CommandReturn")
    public static void clearAllEffects(EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("effect clear", caller);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = "Effect", caller = "entity", returnType = "CommandReturn")
    public static void clearEffect(DataTemplateObject effect, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("effect clear", caller, effect);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    //region ride
    @MNIFunction(normalParams = "entity<1>", caller = "entity", returnType = "CommandReturn")
    public static void ride(EntityVar entity, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("ride", caller, "mount", entity);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(caller = "entity", returnType = "CommandReturn")
    public static void stopRide(EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("ride", caller, "dismount");
        Commands.processMacroCommandReturn(returnValue, command);
    }
    //endregion

    //region tag
    @MNIFunction(normalParams = "string" ,caller = "entity", returnType = "CommandReturn")
    public static void addTag(MCString tag, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("tag", caller, "add", tag);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    @MNIFunction(normalParams = "string",caller = "entity", returnType = "CommandReturn")
    public static void removeTag(MCString tag, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("tag", caller, "remove", tag);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    @MNIFunction(caller = "entity", returnType = "CommandReturn")
    public static void listTag(EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("tag", caller, "list");
        Commands.processMacroCommandReturn(returnValue, command);
    }
    //endregion

    //region team
    @MNIFunction(normalParams = "Team",caller = "entity", returnType = "CommandReturn")
    public static void joinTeam(DataTemplateObject team, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("team join", caller, team);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = "Team",caller = "entity", returnType = "CommandReturn")
    public static void leaveTeam(DataTemplateObject team, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("team leave", caller, team);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    //endregion

    //region tp
    @MNIFunction(normalParams = "pos3", returnType = "CommandReturn")
    public static void tp(Pos3Var pos, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("tp", caller, pos);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = "entity<1>", returnType = "CommandReturn")
    public static void tp(EntityVar entity, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("tp", caller, entity);
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
