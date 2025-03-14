package top.mcfpp.mni.minecraft;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIMember;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.entity.EntityVar;
import top.mcfpp.core.lang.nbt.MCStringConcrete;
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

    public static void setAttributeBase(MCFloat value, String attribute, EntityVar caller, ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = new Command("execute as").build(caller.toCommandPart()).build("run attribute @s " + AttributeData.attributeMap.get(attribute) + " base set");
        }else {
            command = new Command("attribute").build(caller.toCommandPart()).build(AttributeData.attributeMap.get(attribute) + " base set");
        }
        if(value instanceof MCFloatConcrete valueC){
            command.build(valueC.getValue().toString());
        }else {
            command.buildMacro(value);
        }
        Commands.INSTANCE.method3(returnValue, command);
    }

    public static void getAttributeBase(String attribute, EntityVar caller, MCFloat scale, @NotNull ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = new Command("execute as").build(caller.toCommandPart()).build("run attribute @s " + AttributeData.attributeMap.get(attribute) + " base get");
        }else {
            command = new Command("attribute").build(caller.toCommandPart()).build(AttributeData.attributeMap.get(attribute) + " base get");
        }
        if(scale instanceof MCFloatConcrete valueC){
            command.build(valueC.getValue().toString());
        }else {
            command.buildMacro(scale);
        }
        Commands.INSTANCE.method3(returnValue, command);
    }

    public static void getAttribute(String attribute, EntityVar caller, MCFloat scale, @NotNull ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = new Command("execute as").build(caller.toCommandPart()).build("run attribute @s " + AttributeData.attributeMap.get(attribute) + " get");
        }else {
            command = new Command("attribute").build(caller.toCommandPart()).build(AttributeData.attributeMap.get(attribute) + " get");
        }
        if(scale instanceof MCFloatConcrete valueC){
            command.build(valueC.getValue().toString());
        }else {
            command.buildMacro(scale);
        }
        Commands.INSTANCE.method3(returnValue, command);
    }

    public static void addAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = new Command("execute as").build(caller.toCommandPart()).build("attribute @s");
        }else {
            command = new Command("attribute").build(caller.toCommandPart());
        }
        if(modifier instanceof DataTemplateObjectConcrete modifierC) {
            command.build(AttributeData.attributeMap.get(attribute) + " modifier add "
                    + modifierC.getTagStr("id") + " "
                    + modifierC.getTagStr("amount") + " "
                    + modifierC.getTagStr("operation")
            );
        } else {
            command.build(AttributeData.attributeMap.get(attribute) + " modifier add");
            var id = modifier.getMemberVar("id", Member.AccessModifier.PUBLIC).getFirst();
            if(id instanceof MCStringConcrete idC){
                command = command.build(idC.getValue().getValue());
            }else {
                command = command.buildMacro(id);
            }
            var value = modifier.getMemberVar("amount", Member.AccessModifier.PUBLIC).getFirst();
            if(value instanceof MCFloatConcrete valueC){
                command = command.build(valueC.getValue().toString());
            }else {
                command = command.buildMacro(value);
            }
            var operation = modifier.getMemberVar("operation", Member.AccessModifier.PUBLIC).getFirst();
            if(operation instanceof MCStringConcrete operationC){
                command = command.build(operationC.getValue().getValue());
            }else {
                command = command.buildMacro(operation);
            }
        }
        Commands.INSTANCE.method3(returnValue, command);
    }

    public static void removeAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = new Command("execute as").build(caller.toCommandPart()).build("attribute @s " + AttributeData.attributeMap.get(attribute) + " modifier remove");
        }else {
            command = new Command("attribute").build(caller.toCommandPart()).build(AttributeData.attributeMap.get(attribute) + " modifier remove");
        }
        if(modifier instanceof DataTemplateObjectConcrete modifierC) {
            command.build(modifierC.getTagStr("id"));
        } else {
            var id = modifier.getMemberVar("id", Member.AccessModifier.PUBLIC).getFirst();
            if(id instanceof MCStringConcrete idC){
                command = command.build(idC.getValue().getValue());
            }else {
                command = command.buildMacro(id);
            }
        }
        Commands.INSTANCE.method3(returnValue, command);
    }

    public static void getAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, MCFloat scale, ValueWrapper<CommandReturn> returnValue){
        Command command;
        if(caller.isMulti()){
            command = new Command("execute as").build(caller.toCommandPart()).build("attribute @s " + AttributeData.attributeMap.get(attribute) + " modifier value get");
        }else {
            command = new Command("attribute").build(caller.toCommandPart()).build(AttributeData.attributeMap.get(attribute) + " modifier value get");
        }

        var id = modifier.getMemberVar("id", Member.AccessModifier.PUBLIC).getFirst();
        if(id instanceof MCStringConcrete idC){
            command = command.build(idC.getValue().getValue(), true);
        }else {
            command = command.buildMacro(id, true);
        }

        if(scale instanceof MCFloatConcrete scaleC){
            command = command.build(scaleC.getValue().toString(), true);
        }else {
            command = command.buildMacro(scale, true);
            Function.Companion.addCommand(new Command("data modify").build(modifier.nbtPath.memberIndex("scale").toCommandPart(), true).build("set from", true).build(scale.nbtPath.toCommandPart(), true));
        }

        Commands.INSTANCE.method3(returnValue, command);
    }
}
