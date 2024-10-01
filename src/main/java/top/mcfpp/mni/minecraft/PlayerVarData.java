package top.mcfpp.mni.minecraft;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMember;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.resource.Advancement;
import top.mcfpp.core.lang.resource.AdvancementConcrete;
import top.mcfpp.core.minecraft.PlayerVar;
import top.mcfpp.mni.hidden.AttributeData;
import top.mcfpp.model.CompoundData;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

import java.util.ArrayList;
import java.util.Arrays;
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
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.grant(advancement, (PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.grant(advancement, (PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.grant(advancement, ((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void grantAll(PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.grantAll((PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.grantAll((PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.grantAll(((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantFrom(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.grantFrom(advancement, (PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.grantFrom(advancement, (PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.grantFrom(advancement, ((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantThrough(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.grantThrough(advancement, (PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.grantThrough(advancement, (PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.grantThrough(advancement, ((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void grantUntil(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.grantUntil(advancement, (PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.grantUntil(advancement, (PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.grantUntil(advancement, ((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revoke(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.revoke(advancement, (PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.revoke(advancement, (PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.revoke(advancement, ((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(caller = "Player", returnType = "CommandReturn")
    public static void revokeAll(PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.revokeAll((PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.revokeAll((PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.revokeAll(((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeFrom(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.revokeFrom(advancement, (PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.revokeFrom(advancement, (PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.revokeFrom(advancement, ((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeThrough(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.revokeThrough(advancement, (PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.revokeThrough(advancement, (PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.revokeThrough(advancement, ((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(normalParams = {"Advancement advancement"}, caller = "Player", returnType = "CommandReturn")
    public static void revokeUntil(Advancement advancement, PlayerVar caller, ValueWrapper<CommandReturn> returnValue) {
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.revokeUntil(advancement, (PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.revokeUntil(advancement, (PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.revokeUntil(advancement, ((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }

    @MNIFunction(normalParams = {"string key, float scale"}, caller = "Player", returnType = "int")
    public static void getAttribute(MCString id, MCFloat scale, PlayerVar caller, ValueWrapper<CommandReturn> returnValue){
        if(caller instanceof PlayerVar.PlayerEntityVarConcrete) {
            PlayerEntityConcreteData.getAttribute(id, scale, (PlayerVar.PlayerEntityVarConcrete) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerSelectorVar) {
            PlayerSelectorData.getAttribute(id, scale, (PlayerVar.PlayerSelectorVar) caller, returnValue);
        }else if(caller instanceof PlayerVar.PlayerEntityVar) {
            PlayerEntityData.getAttribute(id, scale, ((PlayerVar.PlayerEntityVar) caller), returnValue);
        }
    }
}
