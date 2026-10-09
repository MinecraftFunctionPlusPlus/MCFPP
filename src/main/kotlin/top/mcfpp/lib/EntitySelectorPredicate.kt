@file:Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")

package top.mcfpp.lib

import top.mcfpp.command.Command
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.util.StringHelper.toRangeStr

abstract class EntitySelectorPredicate {

    abstract val identifier: String

    abstract val v: Var<*>

    open fun valueString(): String{
        if (v is RangeVar) return (v as RangeVar).value.toRangeStr()
        return v.toCommandPart().toString()
    }

    open fun toCommandPart(): Command {
        if (v is RangeVar) return Command.build("$identifier=").build(v.toCommandPart(), false)
        return if(StorageAccess.snapshot(v) != null){
            Command.build("$identifier=${valueString()}")
        }else{
            Command.build("$identifier=").buildMacro(v, false)
        }
    }

    override fun toString(): String {
        return if(StorageAccess.snapshot(v) != null){
            "$identifier=${valueString()}"
        }else{
            "$identifier=$v"
        }
    }

    open fun isConcrete(): Boolean = StorageAccess.snapshot(v) != null
}

abstract class CanReverseEntitySelectorPredicate(val reverse: Boolean): EntitySelectorPredicate(){
    override fun toCommandPart(): Command {
        val re = Command.build("${identifier}=")
        if(reverse) re.build("!", false)
        return if(StorageAccess.snapshot(v) != null){
            re.build(valueString(), false)
        }else{
            re.buildMacro(v, false)
        }
    }

    override fun toString(): String {
        return buildString {
            append("${identifier}=")
            if(reverse) append("!")
            if(StorageAccess.snapshot(v) != null){
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

    override fun isConcrete(): Boolean = scores.all { top.mcfpp.analysis.StorageAccess.snapshot(it.value) != null }

    override fun toString(): String {
        if(scores.isEmpty()) return ""
        return buildString {
            append("scores={")
            scores.forEach { (t, u) ->
                if(u is RangeVar){
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
    override fun valueString(): String = (tag as MCString).value.value
    override fun toCommandPart(): Command = if (tag is MCString && top.mcfpp.analysis.StorageAccess.snapshot(tag) != null) super.toCommandPart()
        else Command("tag=${if (reverse) "!" else ""}").selectorStringGuard(tag, true).buildMacro(tag, false)
}

class TeamPredicate(val team: MCString, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "team"
    override val v: Var<*> = team
    override fun valueString(): String = (team as MCString).value.value
    override fun toCommandPart(): Command = if (team is MCString && top.mcfpp.analysis.StorageAccess.snapshot(team) != null) super.toCommandPart()
        else Command("team=${if (reverse) "!" else ""}").selectorStringGuard(team, true).buildMacro(team, false)
}

class NamePredicate(val name: MCString, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "name"
    override val v: Var<*> = name
    override fun valueString(): String = "\"" + (name as MCString).value.value
        .replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    override fun toCommandPart(): Command = if (name is MCString && top.mcfpp.analysis.StorageAccess.snapshot(name) != null) super.toCommandPart()
        else Command("name=${if (reverse) "!" else ""}\"").selectorStringGuard(name, false).buildMacro(name, false).build("\"", false)
}
class TypePredicate(val type: DataTemplateObject, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "type"
    override val v: Var<*> = type
    override fun toCommandPart(): Command = resourcePredicate(identifier, reverse, type)
}

class PredicatePredicate(val predicate: DataTemplateObject, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "predicate"
    override val v: Var<*> = predicate
    override fun toCommandPart(): Command = resourcePredicate(identifier, reverse, predicate)
}

class XRotationPredicate(val xRotation: RangeVar): EntitySelectorPredicate() {
    override val identifier: String = "x_rotation"
    override val v: Var<*> = xRotation
    override fun toString(): String = (xRotation as RangeVar).value.toRangeStr()
}

class YRotationPredicate(val yRotation: RangeVar): EntitySelectorPredicate() {
    override val identifier: String = "y_rotation"
    override val v: Var<*> = yRotation
    override fun toString(): String = (yRotation as RangeVar).value.toRangeStr()
}

class NBTPredicate(val nbt: NBTBasedData): EntitySelectorPredicate() {
    override val identifier: String = "nbt"
    override val v: Var<*> = nbt
    override fun valueString(): String = Tag.toSNBT((nbt as NBTBasedData).value)
}

class LevelPredicate(val level: RangeVar): EntitySelectorPredicate() {
    override val identifier: String = "level"
    override val v: Var<*> = level
    override fun valueString(): String = (level as RangeVar).value.toRangeStr()
}

class GamemodePredicate(val gamemode: MCString, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "gamemode"
    override val v: Var<*> = gamemode
    override fun valueString(): String = (gamemode as MCString).value.value
}

class AdvancementsPredicate(val advancements: DataTemplateObject, reverse: Boolean): CanReverseEntitySelectorPredicate(reverse) {
    override val identifier: String = "advancements"
    override val v: Var<*> = advancements
    override fun toCommandPart(): Command = Command("advancements={")
        .build(resourceId(advancements).toCommandPart(), false).build(if (reverse) "=false}" else "=true}", false)
}

private fun resourceId(value: DataTemplateObject): Var<*> {
    val field = DataTemplate.getField(value, "id")!!
    return if (field is PropertyVar) field.get() else field
}

private fun resourcePredicate(name: String, reverse: Boolean, value: DataTemplateObject): Command =
    Command("$name=${if (reverse) "!" else ""}").build(resourceId(value).toCommandPart(), false)

class LimitPredicate(val limit: MCInt): EntitySelectorPredicate() {
    override val identifier: String = "limit"
    override val v: Var<*> = limit
}

class SortPredicate(val sort: MCString): EntitySelectorPredicate() {
    override val identifier: String = "sort"
    override val v: Var<*> = sort

    override fun valueString(): String = (v as MCString).value.value
}

