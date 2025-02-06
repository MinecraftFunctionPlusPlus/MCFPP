@file:Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")

package top.mcfpp.lib

import net.querz.nbt.io.SNBTUtil
import top.mcfpp.command.Command
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.core.lang.resource.Advancement
import top.mcfpp.core.lang.resource.EntityType
import top.mcfpp.core.lang.resource.LootTablePredicate
import top.mcfpp.util.StringHelper.toRangeStr

abstract class EntitySelectorPredicate {

    abstract val identifier: String

    abstract val v: Var<*>

    open fun valueString(): String{
        return (v as MCFPPValue<*>).value.toString()
    }

    open fun toCommandPart(): Command {
        return if(v is MCFPPValue<*>){
            Command.build("$identifier=${valueString()}")
        }else{
            Command.build("$identifier=").buildMacro(v, false)
        }
    }

    override fun toString(): String {
        return if(v is MCFPPValue<*>){
            "$identifier=${valueString()}"
        }else{
            "$identifier=$v"
        }
    }

    open fun isConcrete(): Boolean = v is MCFPPValue<*>
}

abstract class CanReverseEntitySelectorPredicate(val reverse: Boolean): EntitySelectorPredicate(){
    override fun toCommandPart(): Command {
        val re = Command.build("tag=")
        if(reverse) re.build("!", false)
        return if(v is MCFPPValue<*>){
            re.build(valueString(), false)
        }else{
            re.buildMacro(v, false)
        }
    }

    override fun toString(): String {
        return buildString {
            append("tag=")
            if(reverse) append("!")
            if(v is MCFPPValue<*>){
                append(valueString())
            }else{
                append(v)
            }
        }
    }
}

class XPredicate(val x: MCInt): EntitySelectorPredicate(){
    override val identifier: String = "x"
    override val v: Var<*> = x
}

class YPredicate(val y: MCInt): EntitySelectorPredicate(){
    override val identifier: String = "y"
    override val v: Var<*> = y
}

class ZPredicate(val z: MCInt): EntitySelectorPredicate() {
    override val identifier: String = "z"
    override val v: Var<*> = z
}

class DistancePredicate(val distance: RangeVar): EntitySelectorPredicate() {
    override val identifier: String = "distance"
    override val v: Var<*> = distance
}

class DXPredicate(val dx: MCInt): EntitySelectorPredicate() {
    override val identifier: String = "dx"
    override val v: Var<*> = dx
}

class DYPredicate(val dy: MCInt): EntitySelectorPredicate() {
    override val identifier: String = "dy"
    override val v: Var<*> = dy
}

class DZPredicate(val dz: MCInt): EntitySelectorPredicate() {
    override val identifier: String = "dz"
    override val v: Var<*> = dz
}

class ScoresPredicate(val scores: Map<String, RangeVar>): EntitySelectorPredicate() {

    override val identifier: String = "scores"
    override val v: Var<*> = Void

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

    override fun toString(): String {
        if(scores.isEmpty()) return ""
        return buildString {
            append("scores={")
            scores.forEach { (t, u) ->
                if(u is RangeVarConcrete){
                    append(t).append('=').append(u.value)
                }else{
                    append(t).append('=').append(u)
                }
                append(',')
            }
            append('}')
        }
    }
}

class TagPredicate(val tag: MCString, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "tag"
    override val v: Var<*> = tag
    override fun valueString(): String = (tag as MCStringConcrete).value.value
}

class TeamPredicate(val team: MCString, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "team"
    override val v: Var<*> = team
    override fun valueString(): String = (team as MCStringConcrete).value.value
}

class NamePredicate(val name: MCString, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "team"
    override val v: Var<*> = name
    override fun valueString(): String = (name as MCStringConcrete).value.value
}
class TypePredicate(val type: EntityType, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "type"
    override val v: Var<*> = type
}

class PredicatePredicate(val predicate: LootTablePredicate, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "predicate"
    override val v: Var<*> = predicate
}

class XRotationPredicate(val xRotation: RangeVar): EntitySelectorPredicate() {
    override val identifier: String = "x_rotation"
    override val v: Var<*> = xRotation
    override fun toString(): String = (xRotation as RangeVarConcrete).value.toRangeStr()
}

class YRotationPredicate(val yRotation: RangeVar): EntitySelectorPredicate() {
    override val identifier: String = "y_rotation"
    override val v: Var<*> = yRotation
    override fun toString(): String = (yRotation as RangeVarConcrete).value.toRangeStr()
}

class NBTPredicate(val nbt: NBTBasedData): EntitySelectorPredicate() {
    override val identifier: String = "nbt"
    override val v: Var<*> = nbt
    override fun valueString(): String = SNBTUtil.toSNBT((nbt as NBTBasedDataConcrete).value)
}

class LevelPredicate(val level: RangeVar): EntitySelectorPredicate() {
    override val identifier: String = "level"
    override val v: Var<*> = level
    override fun valueString(): String = (level as RangeVarConcrete).value.toRangeStr()
}

class GamemodePredicate(val gamemode: MCString, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "gamemode"
    override val v: Var<*> = gamemode
    override fun valueString(): String = (gamemode as MCStringConcrete).value.value
}

class AdvancementsPredicate(val advancements: Advancement, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "advancements"
    override val v: Var<*> = advancements
}

class LimitPredicate(val limit: MCInt): EntitySelectorPredicate() {
    override val identifier: String = "limit"
    override val v: Var<*> = limit
}

class SortPredicate(val sort: MCString): EntitySelectorPredicate() {
    override val identifier: String = "sort"
    override val v: Var<*> = sort

    override fun valueString(): String = (v as MCStringConcrete).value.value
}

