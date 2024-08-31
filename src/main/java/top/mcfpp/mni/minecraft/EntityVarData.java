package top.mcfpp.mni.minecraft;

import net.querz.nbt.tag.DoubleTag;
import net.querz.nbt.tag.StringTag;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.mni.hidden.AttributeData;
import top.mcfpp.model.CompoundData;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EntityVarData {

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
        Command[] commands;
        if(value instanceof MCFloatConcrete valueC){
            commands = Commands.INSTANCE.runAsEntity(caller,
                    new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " base set " + valueC.getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller,
                    new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " base set").buildMacro(value.getIdentifier(), true)
            );
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void getAttributeBase(String attribute, EntityVar caller, @NotNull ValueWrapper<CommandReturn> returnValue){
        Command[] commands = Commands.INSTANCE.runAsEntity(caller,
                new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " base get")
        );
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void addAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, ValueWrapper<CommandReturn> returnValue){
        Command[] commands;
        if(modifier instanceof DataTemplateObjectConcrete modifierC) {
            commands = Commands.INSTANCE.runAsEntity(caller,
                    new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " modifier add " + modifierC.getValue().get("id", StringTag.class).getValue() + " " + modifierC.getValue().get("value", DoubleTag.class).asDouble() + " " + modifierC.getValue().get("operation", StringTag.class).getValue() + " " + modifierC.getValue().get("mode", StringTag.class).getValue())
            );
        } else {
            
        }
    }
}
