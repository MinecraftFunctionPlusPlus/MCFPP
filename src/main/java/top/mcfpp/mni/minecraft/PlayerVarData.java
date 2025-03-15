package top.mcfpp.mni.minecraft;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMember;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.NormalCompoundDataObject;
import top.mcfpp.core.lang.Var;
import top.mcfpp.core.lang.entity.PlayerVar;
import top.mcfpp.core.lang.resource.Advancement;
import top.mcfpp.core.lang.resource.AdvancementConcrete;
import top.mcfpp.mni.hidden.AttributeData;
import top.mcfpp.model.CompoundData;
import top.mcfpp.util.ValueWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerVarData {
    @MNIMember
    @NotNull
    public static ArrayList<Var<?>> getMembers() {
        NormalCompoundDataObject attributes = new NormalCompoundDataObject("attribute", Map.of());
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
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "block_break_speed", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "block_interaction_range", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "entity_interaction_range", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "mining_efficiency", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "sneaking_speed", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "submerged_mining_speed", Map.of()));
        attributes.getData().addMember(new NormalCompoundDataObject(attributeData, "sweeping_damage_ratio", Map.of()));
        //属性
        return new ArrayList<>(List.of(attributes));
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grant(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "only", advancement);
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void grantAll(PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "everything");
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantFrom(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "from", advancement);
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantThrough(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "through", advancement);
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantUntil(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "until", advancement);
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revoke(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "only", advancement);
        Commands.INSTANCE.method3(returnValue, command);
    }
    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void revokeAll(PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "everything");
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeFrom(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "from", advancement);
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeThrough(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "through", advancement);
        Commands.INSTANCE.method3(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeUntil(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "until", advancement);
        Commands.INSTANCE.method3(returnValue, command);
    }

    public static void clear(PlayerVar caller, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("clear", caller);
        Commands.INSTANCE.method3(returnValue, command);
    }
}
