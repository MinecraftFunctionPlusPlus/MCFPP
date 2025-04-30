package top.mcfpp.mni.minecraft;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMember;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.entity.PlayerVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.obj.EnumVar;
import top.mcfpp.mni.hidden.AttributeData;
import top.mcfpp.model.compound.CompoundData;
import top.mcfpp.model.function.Function;
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

    @MNIFunction(normalParams = {"Advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grant(DataTemplateObject advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "only", advancement);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void grantAll(PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "everything");
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantFrom(DataTemplateObject advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "from", advancement);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantThrough(DataTemplateObject advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "through", advancement);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantUntil(DataTemplateObject advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement grant", caller, "until", advancement);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revoke(DataTemplateObject advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "only", advancement);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void revokeAll(PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "everything");
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeFrom(DataTemplateObject advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "from", advancement);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeThrough(DataTemplateObject advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "through", advancement);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"Advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeUntil(DataTemplateObject advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        Command command = Command.Companion.buildAll("advancement revoke", caller, "until", advancement);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    //region clear
    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void clear(PlayerVar caller, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("clear", caller);
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"string", "ItemPredicate"}, caller = "Player", returnType = "CommandReturn")
    public static void clear(MCString id, DataTemplateObject predicate, PlayerVar caller, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("clear", caller, id, predicate);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    @MNIFunction(normalParams = {"string", "int = 1"}, caller = "Player", returnType = "CommandReturn")
    public static void clear(MCString id, MCInt count, PlayerVar caller, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("clear", caller, id, count);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    //TODO check(Item item)->bool
    //endregion

    //region xp
    @MNIFunction(normalParams = {"int"}, caller = "Player", returnType = "CommandReturn")
    public static void addXpPoints(MCInt points, PlayerVar player, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("xp add", player, points, "points");
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"int"}, caller = "Player", returnType = "CommandReturn")
    public static void addXpLevels(MCInt levels, PlayerVar player, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("xp add", player, levels, "levels");
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"int"}, caller = "Player", returnType = "CommandReturn")
    public static void setXpPoints(MCInt points, PlayerVar player, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("xp set", player, points, "points");
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(normalParams = {"int"}, caller = "Player", returnType = "CommandReturn")
    public static void setXpLevels(MCInt levels, PlayerVar player, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("xp set", player, levels, "levels");
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void queryXpPoints(PlayerVar player, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("xp query", player, "points");
        Commands.processMacroCommandReturn(returnValue, command);
    }

    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void queryXpLevels(PlayerVar player, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("xp query", player, "levels");
        Commands.processMacroCommandReturn(returnValue, command);
    }
    //endregion

    //region gameMode
    @MNIFunction(normalParams = {"Gamemode"}, caller = "Player", returnType = "CommandReturn")
    public static void setGamemode(EnumVar mode, PlayerVar player, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("gamemode", mode, player);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    //endregion

    //region spawnpoint
    @MNIFunction(normalParams = {"pos3 = pos3.RELATIVE", "pos2 = pos2.RELATIVE"} ,caller = "Player", returnType = "CommandReturn")
    public static void setSpawnpoint(Pos3Var pos3, Pos2Var pos2, PlayerVar player, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("spawnpoint", player, pos3, pos2);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    //endregion

    //region tell
    @MNIFunction(normalParams = {"Player" , "string"}, caller = "Player", returnType = "CommandReturn")
    public static void tell(PlayerVar player, MCString message, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("tell", player, message);
        Function.addCommands(Commands.runAsEntity(player.getEntityVar(), command));
    }
    //endregion
}
