package top.mcfpp.mni.minecraft;

import net.querz.nbt.tag.DoubleTag;
import net.querz.nbt.tag.StringTag;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIMember;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.mni.hidden.AttributeData;
import top.mcfpp.model.CompoundData;
import top.mcfpp.model.Member;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

import java.util.ArrayList;
import java.util.Arrays;
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
        Command[] commands;
        if(value instanceof MCFloatConcrete valueC){
            commands = Commands.INSTANCE.runAsEntity(caller,
                    new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " base set " + valueC.getValue())
            );
        }else {
            commands = Commands.INSTANCE.runAsEntity(caller,
                    new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " base set").buildMacro(value, true)
            );
        }
        ArrayList<Command> commandList = new ArrayList<>(Arrays.asList(commands));
        var last = commandList.get(commandList.size() - 1);
        if(last.isMacro()){
            commandList.remove(commandList.size() - 1);
            commandList.addAll(Arrays.asList(last.buildMacroFunction()));
            returnValue.setValue(new CommandReturn(commandList.get(commandList.size() - 1), "return"));
        }else {
            returnValue.setValue(new CommandReturn(last, "return"));
        }
        Function.Companion.addCommands(commandList.toArray(new Command[0]));
    }

    public static void getAttributeBase(String attribute, EntityVar caller, MCFloat scale, @NotNull ValueWrapper<CommandReturn> returnValue){
        var buildingCommand = new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " base get");
        if(scale instanceof MCFloatConcrete scaleC){
            buildingCommand = buildingCommand.build(scaleC.getValue().toString(), true);
        }else {
            buildingCommand = buildingCommand.buildMacro(scale, true);
        }
        ArrayList<Command> commands = new ArrayList<>(Arrays.stream(Commands.INSTANCE.runAsEntity(caller, buildingCommand)).toList());
        var last = commands.get(commands.size() - 1);
        if(last.isMacro()){
            commands.remove(commands.size() - 1);
            commands.addAll(Arrays.stream(last.buildMacroFunction()).toList());
            returnValue.setValue(new CommandReturn(commands.get(commands.size() - 1), "return"));
        }else {
            returnValue.setValue(new CommandReturn(last, "return"));
        }
        Function.Companion.addCommands(commands.toArray(new Command[0]));
    }

    public static void getAttribute(String attribute, EntityVar caller, MCFloat scale, @NotNull ValueWrapper<CommandReturn> returnValue){
        var buildingCommand = new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " get");
        if(scale instanceof MCFloatConcrete scaleC){
            buildingCommand = buildingCommand.build(scaleC.getValue().toString(), true);
        }else {
            buildingCommand = buildingCommand.buildMacro(scale, true);
        }
        ArrayList<Command> commands = new ArrayList<>(Arrays.stream(Commands.INSTANCE.runAsEntity(caller, buildingCommand)).toList());
        var last = commands.get(commands.size() - 1);
        if(last.isMacro()){
            commands.remove(commands.size() - 1);
            commands.addAll(Arrays.stream(last.buildMacroFunction()).toList());
            returnValue.setValue(new CommandReturn(commands.get(commands.size() - 1), "return"));
        }else {
            returnValue.setValue(new CommandReturn(last, "return"));
        }
        Function.Companion.addCommands(commands.toArray(new Command[0]));
    }

    public static void addAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, ValueWrapper<CommandReturn> returnValue){
        Command[] commands;
        if(modifier instanceof DataTemplateObjectConcrete modifierC) {
            commands = Commands.INSTANCE.runAsEntity(caller,
                    new Command("return run attribute @s " + AttributeData.attributeMap.get(attribute) + " modifier add "
                            + modifierC.getValue().get("id", StringTag.class).getValue() + " "
                            + modifierC.getValue().get("value", DoubleTag.class).asDouble() + " "
                            + modifierC.getValue().get("operation", StringTag.class).getValue() + " "
                            + modifierC.getValue().get("mode", StringTag.class).getValue())
            );
        } else {
            var buildingCommand = new Command("return run attribute @s " + AttributeData.attributeMap.get(attribute) + " modifier add");
            var id = modifier.getMemberVar("id", Member.AccessModifier.PUBLIC).getFirst();
            if(id instanceof MCStringConcrete idC){
                buildingCommand = buildingCommand.build(idC.getValue().getValue(), true);
            }else {
                buildingCommand = buildingCommand.buildMacro(id, true);
            }
            var value = modifier.getMemberVar("value", Member.AccessModifier.PUBLIC).getFirst();
            if(value instanceof MCFloatConcrete valueC){
                buildingCommand = buildingCommand.build(valueC.getValue().toString(), true);
            }else {
                buildingCommand = buildingCommand.buildMacro(value, true);
            }
            var operation = modifier.getMemberVar("operation", Member.AccessModifier.PUBLIC).getFirst();
            if(operation instanceof MCStringConcrete operationC){
                buildingCommand = buildingCommand.build(operationC.getValue().getValue(), true);
            }else {
                buildingCommand = buildingCommand.buildMacro(operation, true);
            }
            var mode = modifier.getMemberVar("mode", Member.AccessModifier.PUBLIC).getFirst();
            if(mode instanceof MCStringConcrete modeC){
                buildingCommand = buildingCommand.build(modeC.getValue().getValue(), true);
            }else {
                buildingCommand = buildingCommand.buildMacro(mode, true);
            }
            ArrayList<Command> commandArrayList = new ArrayList<>(Arrays.stream(Commands.INSTANCE.runAsEntity(caller, buildingCommand)).toList());
            var last = commandArrayList.get(commandArrayList.size() - 1);
            if(last.isMacro()){
                commandArrayList.remove(commandArrayList.size() - 1);
                commandArrayList.addAll(Arrays.stream(last.buildMacroFunction()).toList());
                returnValue.setValue(new CommandReturn(commandArrayList.get(commandArrayList.size() - 1), "return"));
            }else {
                returnValue.setValue(new CommandReturn(last, "return"));
            }
            commands = commandArrayList.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void removeAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, ValueWrapper<CommandReturn> returnValue){
        Command[] commands;
        if(modifier instanceof DataTemplateObjectConcrete modifierC) {
            commands = Commands.INSTANCE.runAsEntity(caller,
                    new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " modifier remove "
                            + modifierC.getValue().get("id", StringTag.class).getValue())
            );
        } else {
            var buildingCommand = new Command("return run attribute @s " + AttributeData.attributeMap.get(attribute) + " modifier remove");
            var id = modifier.getMemberVar("id", Member.AccessModifier.PUBLIC).getFirst();
            if(id instanceof MCStringConcrete idC){
                buildingCommand = buildingCommand.build(idC.getValue().getValue(), true);
            }else {
                buildingCommand = buildingCommand.buildMacro(id, true);
            }
            ArrayList<Command> commandArrayList = new ArrayList<>(Arrays.stream(Commands.INSTANCE.runAsEntity(caller, buildingCommand)).toList());
            var last = commandArrayList.get(commandArrayList.size() - 1);
            if(last.isMacro()){
                commandArrayList.remove(commandArrayList.size() - 1);
                commandArrayList.addAll(Arrays.stream(last.buildMacroFunction()).toList());
                returnValue.setValue(new CommandReturn(commandArrayList.get(commandArrayList.size() - 1), "return"));
            }else {
                returnValue.setValue(new CommandReturn(last, "return"));
            }
            commands = commandArrayList.toArray(new Command[0]);
        }
        returnValue.setValue(new CommandReturn(commands[commands.length - 1], "return"));
        Function.Companion.addCommands(commands);
    }

    public static void getAttributeModifier(String attribute, EntityVar caller, DataTemplateObject modifier, MCFloat scale, ValueWrapper<CommandReturn> returnValue){
        Command[] commands;
        var buildingCommand = new Command("attribute @s " + AttributeData.attributeMap.get(attribute) + " modifier value get");
        var id = modifier.getMemberVar("id", Member.AccessModifier.PUBLIC).getFirst();

        if(id instanceof MCStringConcrete idC){
            buildingCommand = buildingCommand.build(idC.getValue().getValue(), true);
        }else {
            buildingCommand = buildingCommand.buildMacro(id, true);
        }

        if(scale instanceof MCFloatConcrete scaleC){
            buildingCommand = buildingCommand.build(scaleC.getValue().toString(), true);
        }else {
            buildingCommand = buildingCommand.buildMacro(scale, true);
            Function.Companion.addCommand(new Command("data modify").build(modifier.nbtPath.memberIndex("scale").toCommandPart(), true).build("set from", true).build(scale.nbtPath.toCommandPart(), true));
        }

        if(buildingCommand.isMacro()){
            buildingCommand.prepend("return run", true);
        }
        var commandArrayList = new ArrayList<>(Arrays.asList(Commands.INSTANCE.runAsEntity(caller, buildingCommand)));
        var last = commandArrayList.get(commandArrayList.size() - 1);
        if(last.isMacro()){
            commandArrayList.remove(commandArrayList.size() - 1);
            commandArrayList.addAll(Arrays.stream(last.buildMacroFunction()).toList());
        }
        returnValue.setValue(new CommandReturn(commandArrayList.get(commandArrayList.size() - 1), "return"));
        Function.Companion.addCommands(commandArrayList.toArray(new Command[0]));
    }
}
