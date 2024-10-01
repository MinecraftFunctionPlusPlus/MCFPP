package top.mcfpp.lib

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.StringTag
import top.mcfpp.command.Command
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.resource.EntityTypeConcrete
import top.mcfpp.util.LogProcessor
import top.mcfpp.core.lang.resource.Advancement
import top.mcfpp.core.lang.resource.LootTablePredicate
import java.io.Serializable

@Suppress("MemberVisibilityCanBePrivate", "unused")
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

    val predicates: ArrayList<EntitySelectorPredicate> = ArrayList()

    constructor(char: Char):this(fromSelectorTypeString(char))

    fun getLimit(): Int{
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

    fun getType(): Pair<EntityTypeConcrete, Boolean>?{
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

    fun addPredicate(predicate: EntitySelectorPredicate): EntitySelector{
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
        return this
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

    fun x(value: MCInt) = addPredicate(XPredicate(value))
    fun x(value: Int) = addPredicate(XPredicate(MCIntConcrete(value)))
    fun y(value: MCInt) = addPredicate(YPredicate(value))
    fun y(value: Int) = addPredicate(YPredicate(MCIntConcrete(value)))
    fun z(value: MCInt) = addPredicate(ZPredicate(value))
    fun z(value: Int) = addPredicate(ZPredicate(MCIntConcrete(value)))
    fun distance(value: RangeVar) = addPredicate(DistancePredicate(value))
    fun distance(value: Pair<Float?, Float?>) = addPredicate(DistancePredicate(RangeVarConcrete(value)))
    fun dx(value: MCInt) = addPredicate(DXPredicate(value))
    fun dx(value: Int) = addPredicate(DXPredicate(MCIntConcrete(value)))
    fun dy(value: MCInt) = addPredicate(DYPredicate(value))
    fun dy(value: Int) = addPredicate(DYPredicate(MCIntConcrete(value)))
    fun dz(value: MCInt) = addPredicate(DZPredicate(value))
    fun dz(value: Int) = addPredicate(DZPredicate(MCIntConcrete(value)))
    fun scores(value: Map<String, RangeVar>) = addPredicate(ScoresPredicate(value))
    fun tag(value: MCString, reverse: Boolean) = addPredicate(TagPredicate(value, reverse))
    fun tag(value: String, reverse: Boolean) = addPredicate(TagPredicate(MCStringConcrete(StringTag(value)), reverse))
    fun team(value: MCString, reverse: Boolean) = addPredicate(TeamPredicate(value, reverse))
    fun teams(value: String, reverse: Boolean) = addPredicate(TeamPredicate(MCStringConcrete(StringTag(value)), reverse))
    fun name(value: MCString, reverse: Boolean) = addPredicate(NamePredicate(value, reverse))
    fun name(value: String, reverse: Boolean) = addPredicate(NamePredicate(MCStringConcrete(StringTag(value)), reverse))
    fun type(value: EntityTypeConcrete, reverse: Boolean) = addPredicate(TypePredicate(value, reverse))
    fun type(value: String, reverse: Boolean) = addPredicate(TypePredicate(EntityTypeConcrete(value), reverse))
    fun predicate(value: LootTablePredicate, reverse: Boolean) = addPredicate(PredicatePredicate(value, reverse))
    fun predicate(value: String, reverse: Boolean) = addPredicate(PredicatePredicate(LootTablePredicate(value), reverse))
    fun xRotation(value: RangeVar) = addPredicate(XRotationPredicate(value))
    fun xRotation(value: Pair<Float?, Float?>) = addPredicate(XRotationPredicate(RangeVarConcrete(value)))
    fun yRotation(value: RangeVar) = addPredicate(YRotationPredicate(value))
    fun yRotation(value: Pair<Float?, Float?>) = addPredicate(YRotationPredicate(RangeVarConcrete(value)))
    fun nbt(value: NBTBasedData) = addPredicate(NBTPredicate(value))
    fun nbt(value: CompoundTag) = addPredicate(NBTPredicate(NBTBasedDataConcrete(value)))
    fun level(value: RangeVar) = addPredicate(LevelPredicate(value))
    fun level(value: Pair<Float?, Float?>) = addPredicate(LevelPredicate(RangeVarConcrete(value)))
    fun gamemode(value: MCString, reverse: Boolean) = addPredicate(GamemodePredicate(value, reverse))
    fun gamemode(value: String, reverse: Boolean) = addPredicate(GamemodePredicate(MCStringConcrete(StringTag(value)), reverse))
    fun advancement(value: Advancement, reverse: Boolean) = addPredicate(AdvancementsPredicate(value, reverse))
    fun advancement(value: String, reverse: Boolean) = addPredicate(AdvancementsPredicate(Advancement(value), reverse))
    fun limit(value: MCInt) = addPredicate(LimitPredicate(value))
    fun limit(value: Int) = addPredicate(LimitPredicate(MCIntConcrete(value)))
    fun sort(value: MCString) = addPredicate(SortPredicate(value))
    fun sort(value: String) = addPredicate(SortPredicate(MCStringConcrete(StringTag(value))))

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