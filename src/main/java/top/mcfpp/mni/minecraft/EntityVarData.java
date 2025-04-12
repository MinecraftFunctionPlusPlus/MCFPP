package top.mcfpp.mni.minecraft;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMember;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.entity.EntityVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.MCStringConcrete;
import top.mcfpp.core.lang.resource.Effect;
import top.mcfpp.mni.hidden.AttributeData;
import top.mcfpp.model.CompoundData;
import top.mcfpp.model.Member;
import top.mcfpp.model.function.Function;
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

    @MNIFunction(normalParams = {"float value", "MCString attribute"}, caller = "entity", returnType = "CommandReturn")
    public static void setAttributeBase(MCFloat value, String attribute, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "run attribute @s", AttributeData.attributeMap.get(attribute), "base set", value);
        }else {
            command = Command.Companion.buildAll("attribute", caller, AttributeData.attributeMap.get(attribute), "base set", value);
        }
        Commands.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"MCString attribute", "MCFloat scale"}, caller = "entity", returnType = "CommandReturn")
    public static void getAttributeBase(String attribute, EntityVar caller, MCFloat scale, @NotNull ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "run attribute @s", AttributeData.attributeMap.get(attribute), "base get", scale);
        }else {
            command = Command.Companion.buildAll("attribute", caller, AttributeData.attributeMap.get(attribute), "base get", scale);
        }
        Commands.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"MCString attribute", "MCFloat scale"}, caller = "entity", returnType = "CommandReturn")
    public static void getAttribute(String attribute, EntityVar caller, MCFloat scale, @NotNull ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "run attribute @s", AttributeData.attributeMap.get(attribute), "get", scale);
        }else {
            command = Command.Companion.buildAll("attribute", caller, AttributeData.attributeMap.get(attribute), "get", scale);
        }
        Commands.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"MCString attribute", "MCFloat scale"}, caller = "entity", returnType = "CommandReturn")
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
        Commands.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"MCString attribute", "MCFloat scale"}, caller = "entity", returnType = "CommandReturn")
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
        Commands.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"MCString attribute", "MCFloat scale"}, caller = "entity", returnType = "CommandReturn")
    public static void getAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, MCFloat scale, ValueWrapper<CommandReturn> returnValue){
        Command command;
        var id = modifier.getMemberVarWithT("id", MCString.class);
        if(caller.isMulti()){
            command = Command.Companion.buildAll("execute as", caller, "attribute @s", AttributeData.attributeMap.get(attribute), "modifier value get", id, scale);
        }else {
            command = Command.Companion.buildAll("attribute", caller, AttributeData.attributeMap.get(attribute), "modifier value get", id, scale);
        }
        Commands.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"Effect effect", "int time = 30", "int amplifier = 0", "bool hideParticles = false"}, caller = "entity", returnType = "CommandReturn")
    public static void effect(Effect effect, int time, int amplifier, boolean hideParticles, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("effect give", caller, effect, time, amplifier, hideParticles);
        Commands.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"Effect effect", "int amplifier = 0", "bool hideParticles = false"}, caller = "entity", returnType = "CommandReturn")
    public static void effectInfinite(Effect effect, int amplifier, boolean hideParticles, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("effect give", caller, effect, "infinite", amplifier, hideParticles);
        Commands.method3(returnValue, command);
    }

    @MNIFunction(caller = "entity", returnType = "CommandReturn")
    public static void clearAllEffects(EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("effect clear", caller);
        Commands.method3(returnValue, command);
    }

    @MNIFunction(normalParams = "Effect effect", caller = "entity", returnType = "CommandReturn")
    public static void clearEffect(Effect effect, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        var command = Command.Companion.buildAll("effect clear", caller, effect);
        Commands.method3(returnValue, command);
    }

}
