package top.alumopper.mcfpp.type

import java.util.Arrays

class NBTTag {
    val valueString: String
        get() = ""

    companion object {
        fun Prase(nbt: String?): NBTTag? {
            return null
        }

        var attributes: List<String> = Arrays.asList(
            "generic.max_health",
            "generic.follow_range",
            "generic.knockback_resistance",
            "generic.movement_speed",
            "generic.attack_damage",
            "generic.attack_knockback",
            "generic.armor",
            "generic.armor_toughness"
        )
    }
}