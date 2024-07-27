package top.mcfpp.lib

import top.mcfpp.command.Command
import top.mcfpp.lang.*
import top.mcfpp.lang.resource.EntityTypeConcrete
import top.mcfpp.util.LogProcessor
import java.io.Serializable

class EntitySelector(var selectorType: SelectorType): Serializable {

    private var hasXPredicate: Boolean = false
    private var hasYPredicate: Boolean = false
    private var hasZPredicate: Boolean = false
    private var hasDistancePredicate: Boolean = false
    private var hasDXPredicate: Boolean = false
    private var hasDYPredicate: Boolean = false
    private var hasDZPredicate: Boolean = false
    private var hasScoresPredicate: Boolean = false
    private var hasNamePredicate: Boolean = false
    private var hasTypePredicate: Boolean = false
    private var hasXRotationPredicate: Boolean = false
    private var hasYRotationPredicate: Boolean = false
    private var hasLevelPredicate: Boolean = false
    private var hasGamemodePredicate: Boolean = false
    private var hasLimitPredicate: Boolean = false
    private var hasSortPredicate: Boolean = false

    private val predicates: ArrayList<EntitySelectorPredicate> = ArrayList()

    val limit: Int
        get() {
            if(hasLimitPredicate){
                for (predicate in predicates) {
                    if(predicate is LimitPredicate){
                        if(predicate.limit is MCIntConcrete){
                            return (predicate.limit).value
                        }
                        break
                    }
                }
            }
            return Int.MAX_VALUE
        }

    val type: Pair<EntityTypeConcrete, Boolean>?
        get() {
            if(hasTypePredicate){
                for (predicate in predicates) {
                    if(predicate is TypePredicate){
                        if(predicate.type is EntityTypeConcrete){
                            return predicate.type to predicate.reverse
                        }
                        break
                    }
                }
            }
            return null
        }

    constructor(char: Char):this(fromSelectorTypeString(char))

    fun addPredicate(predicate: EntitySelectorPredicate){
        when(predicate){
            is XPredicate -> {
                if(hasXPredicate){
                    LogProcessor.error("Duplicate x predicate")
                }else{
                    hasXPredicate = true
                    predicates.add(predicate)
                }
            }
            is YPredicate -> {
                if(hasYPredicate){
                    LogProcessor.error("Duplicate y predicate")
                }else{
                    hasYPredicate = true
                    predicates.add(predicate)
                }
            }
            is ZPredicate -> {
                if(hasZPredicate){
                    LogProcessor.error("Duplicate z predicate")
                }else{
                    hasZPredicate = true
                    predicates.add(predicate)
                }
            }
            is DistancePredicate -> {
                if(hasDistancePredicate){
                    LogProcessor.error("Duplicate distance predicate")
                }else{
                    hasDistancePredicate = true
                    predicates.add(predicate)
                }
            }
            is DXPredicate -> {
                if(hasDXPredicate){
                    LogProcessor.error("Duplicate dx predicate")
                }else{
                    hasDXPredicate = true
                    predicates.add(predicate)
                }
            }
            is DYPredicate -> {
                if(hasDYPredicate){
                    LogProcessor.error("Duplicate dy predicate")
                }else{
                    hasDYPredicate = true
                    predicates.add(predicate)
                }
            }
            is DZPredicate -> {
                if(hasDZPredicate){
                    LogProcessor.error("Duplicate dz predicate")
                }else{
                    hasDZPredicate = true
                    predicates.add(predicate)
                }
            }
            is ScoresPredicate -> {
                if(hasScoresPredicate){
                    LogProcessor.error("Duplicate scores predicate")
                }else{
                    hasScoresPredicate = true
                    predicates.add(predicate)
                }
            }
            is NamePredicate -> {
                if(hasNamePredicate){
                    LogProcessor.error("Duplicate name predicate")
                }else{
                    hasNamePredicate = true
                    predicates.add(predicate)
                }
            }
            is TypePredicate -> {
                if(hasTypePredicate){
                    LogProcessor.error("Duplicate type predicate")
                }else{
                    hasTypePredicate = true
                    predicates.add(predicate)
                }
            }
            is XRotationPredicate -> {
                if(hasXRotationPredicate){
                    LogProcessor.error("Duplicate x_rotation predicate")
                }else{
                    hasXRotationPredicate = true
                    predicates.add(predicate)
                }
            }
            is YRotationPredicate -> {
                if(hasYRotationPredicate){
                    LogProcessor.error("Duplicate y_rotation predicate")
                }else{
                    hasYRotationPredicate = true
                    predicates.add(predicate)
                }
            }
            is LevelPredicate -> {
                if(hasLevelPredicate){
                    LogProcessor.error("Duplicate level predicate")
                }else{
                    hasLevelPredicate = true
                    predicates.add(predicate)
                }
            }
            is GamemodePredicate -> {
                if(hasGamemodePredicate){
                    LogProcessor.error("Duplicate gamemode predicate")
                }else{
                    hasGamemodePredicate = true
                    predicates.add(predicate)
                }
            }
            is LimitPredicate -> {
                if(hasLimitPredicate){
                    LogProcessor.error("Duplicate limit predicate")
                }else{
                    hasLimitPredicate = true
                    predicates.add(predicate)
                }
            }
            is SortPredicate -> {
                if(hasSortPredicate){
                    LogProcessor.error("Duplicate sort predicate")
                }else{
                    hasSortPredicate = true
                    predicates.add(predicate)
                }
            }
            else -> {
                predicates.add(predicate)
            }
        }
    }

    fun onlyIncludingPlayers() : Boolean {
        if(selectorType == SelectorType.RANDOM_PLAYER || selectorType == SelectorType.NEAREST_PLAYER || selectorType == SelectorType.ALL_PLAYERS){
            return true
        }
        if(hasTypePredicate){
            for (predicate in predicates) {
                if(predicate is TypePredicate){
                    if(predicate.type is EntityTypeConcrete && predicate.type.value == "minecraft:player"){
                        return true
                    }
                    break
                }
            }
        }
        return false
    }

    fun selectingSingleEntity(): Boolean{
        if(selectorType == SelectorType.NEAREST_ENTITY || selectorType == SelectorType.NEAREST_PLAYER || selectorType == SelectorType.SELF || selectorType == SelectorType.RANDOM_PLAYER){
            return true
        }
        if(hasLimitPredicate){
            for (predicate in predicates) {
                if(predicate is LimitPredicate){
                    if(predicate.limit is MCIntConcrete && predicate.limit.value == 1){
                        return true
                    }
                    break
                }
            }
        }
        return false
    }

    fun clone(): EntitySelector{
        val re = EntitySelector(selectorType)
        re.predicates.addAll(this.predicates)
        return re
    }

    fun isConcrete(): Boolean = predicates.all { it.isConcrete() }

    fun hasArgument() : Boolean{
        return predicates.isNotEmpty()
    }

    fun toCommandPart(): Command{
        val re = Command.build("@")
        re.build(toSelectorTypeString(selectorType).toString(), false)
        if(hasArgument()){
            re.build("[", false)
            for (predicate in predicates){
                re.build(predicate.toCommandPart(), false)
                if(predicates.last() != predicate) re.build(",", false)
            }
            re.build("]", false)
        }
        return re
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