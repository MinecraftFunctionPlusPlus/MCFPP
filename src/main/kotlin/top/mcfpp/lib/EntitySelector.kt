package top.mcfpp.lib

import top.mcfpp.lang.*
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.util.LogProcessor
import java.util.HashMap

class EntitySelector(var selectorType: SelectorType) {

    var x : MCInt? = null
    var y : MCInt? = null
    var z : MCInt? = null
    var distance : RangeVar? = null
    var dx: MCInt? = null
    var dy: MCInt? = null
    var dz: MCInt? = null

    var scores: HashMap<SbObject, RangeVar> = HashMap()
    var tag: HashMap<MCString, MCBool> = HashMap()
    var team: HashMap<MCString, MCBool> = HashMap()
    var name: Pair<MCString, MCBool>? = null
    var type: Pair<MCString, MCBool>? = null
    var predicate: HashMap<String, MCBool> = HashMap()

    var x_rotation: RangeVar? = null
    var y_rotation: RangeVar? = null
    var nbt: NBTBasedData<*>? = null
    var level : RangeVar? = null
    var gamemode : Pair<String, MCBool>? = null
        set(value){
            if(value == null) {
                field = null
                return
            }
            if(!gamemodeValues.contains(value.first)){
                LogProcessor.error("Invalid gamemode value: ${value.first}")
            }else{
                field = value
            }
        }

    var advancements: HashMap<String, MCBool> = HashMap()

    var limit: MCInt? = null
    var sort: MCString? = null
        set(value) {
            if(value is MCStringConcrete && !sortValues.contains(value.value.valueToString())){
                LogProcessor.error("Invalid sort value: $value")
            }else{
                field = value
            }
        }

    constructor(char: Char):this(fromSelectorTypeString(char))

    fun onlyIncludingPlayers() : Boolean {
        if(selectorType == SelectorType.RANDOM_PLAYER || selectorType == SelectorType.NEAREST_PLAYER || selectorType == SelectorType.ALL_PLAYERS){
            return true
        }
        if(type != null
            && type!!.first is MCStringConcrete && type!!.second is MCBoolConcrete
            && (type!!.first as MCStringConcrete).value.toString() == "player" && (type!!.second as MCBoolConcrete).value
        ){
            return true
        }
        return false
    }

    fun selectingSingleEntity(): Boolean{
        if(selectorType == SelectorType.NEAREST_ENTITY || selectorType == SelectorType.NEAREST_PLAYER || selectorType == SelectorType.SELF || selectorType == SelectorType.RANDOM_PLAYER){
            return true
        }
        if(limit is MCIntConcrete && (limit as MCIntConcrete).value == 1){
            return true
        }
        return false
    }

    fun clone(): EntitySelector{
        val re = EntitySelector(selectorType)
        re.x = x?.clone()
        re.y = y?.clone()
        re.z = z?.clone()
        re.distance = distance?.clone()
        re.dx = dx?.clone()
        re.dy = dy?.clone()
        re.dz = dz?.clone()
        re.scores = HashMap(scores)
        re.tag = HashMap(tag)
        re.team = HashMap(team)
        re.name = name?.copy()
        re.type = type?.copy()
        re.predicate = HashMap(predicate)
        re.x_rotation = x_rotation?.clone()
        re.y_rotation = y_rotation?.clone()
        re.nbt = nbt?.clone()
        re.level = level?.clone()
        re.gamemode = gamemode?.copy()
        re.advancements = HashMap(advancements)
        re.limit = limit?.clone()
        re.sort = sort?.clone() as MCString //TODO
        return re
    }

    fun isConcrete(): Boolean{
        return x is MCFPPValue<*>? || y is MCFPPValue<*>? || z is MCFPPValue<*>?
                || distance is MCFPPValue<*>?
                || dx is MCFPPValue<*>? || dy is MCFPPValue<*>? || dz is MCFPPValue<*>?
                || scores.values.all { it is MCFPPValue<*> }
                || tag.values.all { it is MCFPPValue<*> }
                || team.values.all { it is MCFPPValue<*> }
                || name?.first is MCFPPValue<*> && type?.first is MCFPPValue<*>
                || predicate.values.all { it is MCFPPValue<*> }
                || x_rotation is MCFPPValue<*>? || y_rotation is MCFPPValue<*>?
                || nbt is MCFPPValue<*>?
                || level is MCFPPValue<*>?
                //|| gamemode is MCFPPValue<*>?
                || advancements.values.all { it is MCFPPValue<*> }
                || limit is MCFPPValue<*>?
                || sort is MCFPPValue<*>?
    }

    companion object{

        val sortValues = arrayOf("nearest","furthest","random","arbitrary")
        val gamemodeValues = arrayOf("survival","creative","adventure","spectator")


        fun toSelectorTypeString(type: SelectorType): Char{
            return when(type){
                SelectorType.ALL_PLAYERS -> 'a'
                SelectorType.ALL_ENTITIES -> 'e'
                SelectorType.NEAREST_PLAYER -> 'p'
                SelectorType.RANDOM_PLAYER -> 'r'
                SelectorType.SELF -> 's'
                SelectorType.NEAREST_ENTITY -> 'n'
            }
        }

        fun fromSelectorTypeString(char: Char): SelectorType{
            return when(char){
                'a' -> SelectorType.ALL_PLAYERS
                'e' -> SelectorType.ALL_ENTITIES
                'p' -> SelectorType.NEAREST_PLAYER
                'r' -> SelectorType.RANDOM_PLAYER
                's' -> SelectorType.SELF
                'n' -> SelectorType.NEAREST_ENTITY
                else -> {
                    LogProcessor.error("Invalid selector type: @$char")
                    SelectorType.ALL_ENTITIES
                }
            }
        }

        enum class SelectorType {
            ALL_PLAYERS, ALL_ENTITIES, NEAREST_PLAYER, RANDOM_PLAYER, SELF, NEAREST_ENTITY
        }
    }
}