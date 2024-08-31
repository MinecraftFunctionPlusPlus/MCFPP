package top.mcfpp.mni.hidden;

import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.minecraft.PlayerVar;
import top.mcfpp.mni.minecraft.EntityVarData;
import top.mcfpp.util.LogProcessor;
import top.mcfpp.util.ValueWrapper;

import java.util.Map;

public class AttributeData {

    public static Map<String, String> attributeMap = Map.ofEntries(
            Map.entry("armor",                          "armor"),
            Map.entry("armor_toughness",                "armor_toughness"),
            Map.entry("attack_damage",                  "attack_damage"),
            Map.entry("generic.attack_knockback",       "attack_knockback"),
            Map.entry("attack_speed",                   "attack_speed"),
            Map.entry("burning_time",                   "burning_time"),
            Map.entry("explosion_knockback_resistance", "explosion_knockback_resistance"),
            Map.entry("fall_damage_multiplier",         "fall_damage_multiplier"),
            Map.entry("flying_speed",                   "flying_speed"),
            Map.entry("follow_range",                   "follow_range"),
            Map.entry("gravity",                        "gravity"),
            Map.entry("jump_strength",                  "jump_strength"),
            Map.entry("knockback_resistance",           "knockback_resistance"),
            Map.entry("luck",                           "luck"),
            Map.entry("max_absorption",                 "max_absorption"),
            Map.entry("max_health",                     "max_health"),
            Map.entry("movement_efficiency",            "movement_efficiency"),
            Map.entry("movement_speed",                 "movement_speed"),
            Map.entry("oxygen_bonus",                   "oxygen_bonus"),
            Map.entry("safe_fall_distance",             "safe_fall_distance"),
            Map.entry("scale",                          "scale"),
            Map.entry("step_height",                    "step_height"),
            Map.entry("water_movement_efficiency",      "water_movement_efficiency"),
            Map.entry("block_break_speed",              "block_break_speed"),
            Map.entry("block_interaction_range",        "block_interaction_range"),
            Map.entry("entity_interaction_range",       "entity_interaction_range"),
            Map.entry("mining_efficiency",              "mining_efficiency"),
            Map.entry("sneaking_speed",                 "sneaking_speed"),
            Map.entry("submerged_mining_speed",         "submerged_mining_speed"),
            Map.entry("sweeping_damage_ratio",          "sweeping_damage_ratio"),
            Map.entry("spawn_reinforcements",           "spawn_reinforcements")
    );


    @MNIRegister(normalParams = {"float value"}, caller = "attribute", returnType = "CommandReturn")
    public static void setBase(MCFloat value, NormalCompoundDataObject caller, ValueWrapper<CommandReturn> re){
        Var<?> entity = (Var<?>) ((NormalCompoundDataObject) caller.getParent()).getParent();
        if(entity instanceof PlayerVar.PlayerEntityVar entityVar){
            EntityVarData.setAttributeBase(value, caller.getIdentifier(), entityVar.getEntity(), re);
        }else if(entity instanceof PlayerVar.PlayerSelectorVar selectorVar){

        }else if(entity instanceof EntityVarConcrete entityVarConcrete){

        }else if(entity instanceof EntityVar entityVar){
            EntityVarData.setAttributeBase(value, caller.getIdentifier(), entityVar, re);
        }else if(entity instanceof SelectorVarConcrete selectorVarConcrete){

        }else if(entity instanceof SelectorVar selectorVar){

        }else {
            LogProcessor.INSTANCE.error("Unknown entity type: " + entity.getType().getTypeName());
        }
    }

}
