package top.mcfpp.lib

import top.mcfpp.command.Command
import top.mcfpp.lang.*
import top.mcfpp.lang.resource.*

interface EntitySelectorPredicate {
    fun toCommandPart(): Command

    fun isConcrete(): Boolean

    companion object {

    }
}

class XPredicate(val x: MCInt): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return if(x is MCIntConcrete){
            Command.build("x=${x.value}")
        }else{
            Command.build("x=").buildMacro(x.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = x is MCIntConcrete
}

class YPredicate(val y: MCInt): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return if(y is MCIntConcrete){
            Command.build("y=${y.value}")
        }else{
            Command.build("y=").buildMacro(y.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = y is MCIntConcrete
}

class ZPredicate(val z: MCInt): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return if(z is MCIntConcrete){
            Command.build("z=${z.value}")
        }else{
            Command.build("z=").buildMacro(z.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = z is MCIntConcrete
}

class DistancePredicate(val distance: RangeVar): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return Command.build("distance=").build(distance.toCommandPart(), false)
    }

    override fun isConcrete(): Boolean = distance is RangeVarConcrete
}

class DXPredicate(val dx: MCInt): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return if(dx is MCIntConcrete){
            Command.build("dx=${dx.value}")
        }else{
            Command.build("dx=").buildMacro(dx.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = dx is MCIntConcrete
}

class DYPredicate(val dy: MCInt): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return if(dy is MCIntConcrete){
            Command.build("dy=${dy.value}")
        }else{
            Command.build("dy=").buildMacro(dy.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = dy is MCIntConcrete
}

class DZPredicate(val dz: MCInt): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return if(dz is MCIntConcrete){
            Command.build("dz=${dz.value}")
        }else{
            Command.build("dz=").buildMacro(dz.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = dz is MCIntConcrete
}

class ScoresPredicate(val scores: Map<String, RangeVar>): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        if(scores.isEmpty()) return Command.build("")
        val re = Command.build("scores={")
        scores.forEach { (t, u) ->
            re.build(t, false).build("=", false).build(u.toCommandPart(), false)
            re.build(", ", false)
        }
        re.build("}", false)
        return re
    }

    override fun isConcrete(): Boolean = scores.all { it.value is RangeVarConcrete }
}

class TagPredicate(val tag: MCString,val reverse: Boolean): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        val re = Command.build("tag=")
        if(reverse) re.build("!", false)
        return if(tag is MCStringConcrete){
            re.build(tag.value.valueToString(), false)
        }else{
            re.buildMacro(tag.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = tag is MCStringConcrete
}

class TeamPredicate(val team: MCString,val reverse: Boolean): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        val re = Command.build("team=")
        if(reverse) re.build("!", false)
        return if(team is MCStringConcrete){
            re.build(team.value.valueToString(), false)
        }else{
            re.buildMacro(team.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = team is MCStringConcrete
}

class NamePredicate(val name: MCString,val reverse: Boolean): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        val re = Command.build("name=")
        if(reverse) re.build("!", false)
        return if(name is MCStringConcrete){
            re.build(name.value.valueToString(), false)
        }else{
            re.buildMacro(name.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = name is MCStringConcrete
}

class TypePredicate(val type: EntityType, val reverse: Boolean): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        val re = Command.build("type=")
        if(reverse) re.build("!", false)
        return if(type is EntityTypeConcrete){
            re.build(type.value, false)
        }else{
            re.buildMacro(type.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = type is EntityTypeConcrete
}

class PredicatePredicate(val predicate: LootTablePredicate, val reverse: Boolean): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        val re = Command.build("predicate=")
        if(reverse) re.build("!", false)
        return if(predicate is LootTablePredicateConcrete){
            re.build(predicate.value, false)
        }else{
            re.buildMacro(predicate.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = predicate is LootTablePredicateConcrete
}

class XRotationPredicate(val x_rotation: RangeVar): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return Command.build("x_rotation=").build(x_rotation.toCommandPart(), false)
    }

    override fun isConcrete(): Boolean = x_rotation is RangeVarConcrete
}

class YRotationPredicate(val y_rotation: RangeVar): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return Command.build("y_rotation=").build(y_rotation.toCommandPart(), false)
    }

    override fun isConcrete(): Boolean = y_rotation is RangeVarConcrete
}

class NBTPredicate(val nbt: NBTBasedData<*>): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return if(nbt is NBTBasedDataConcrete){
            Command.build("nbt=").build(nbt.value.valueToString())
        }else{
            Command.build("nbt=").buildMacro(nbt.identifier)
        }
    }

    override fun isConcrete(): Boolean = nbt is NBTBasedDataConcrete
}

class LevelPredicate(val level: RangeVar): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return Command.build("level=").build(level.toCommandPart(), false)
    }

    override fun isConcrete(): Boolean = level is RangeVarConcrete
}

class GamemodePredicate(val gamemode: MCString, val reverse: Boolean): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        val c = Command.build("gamemode=")
        if(reverse){
            c.build("!", false)
        }
        return if(gamemode is MCStringConcrete){
            c.build(gamemode.value.valueToString())
        }else{
            c.buildMacro(gamemode.identifier)
        }
    }

    override fun isConcrete(): Boolean = gamemode is MCStringConcrete
}

class AdvancementsPredicate(val advancements: Advancement, val reverse: Boolean): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        val re = Command.build("advancements=")
        if(reverse) re.build("!", false)
        return if(advancements is AdvancementConcrete){
            re.build(advancements.value, false)
        }else{
            re.buildMacro(advancements.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = advancements is AdvancementConcrete
}

class LimitPredicate(val limit: MCInt): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return if(limit is MCIntConcrete){
            Command.build("limit=${limit.value}")
        }else{
            Command.build("limit=").buildMacro(limit.identifier, false)
        }
    }

    override fun isConcrete(): Boolean = limit is MCIntConcrete
}

class SortPredicate(val sort: MCString): EntitySelectorPredicate {
    override fun toCommandPart(): Command {
        return if(sort is MCStringConcrete){
            Command.build("sort=").build(sort.value.valueToString())
        }else{
            Command.build("sort=").buildMacro(sort.identifier)
        }
    }

    override fun isConcrete(): Boolean = sort is MCStringConcrete
}

