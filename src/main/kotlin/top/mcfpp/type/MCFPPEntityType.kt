package top.mcfpp.type

import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.entity.PlayerVar
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.property.AnonymousNativeMutator
import top.mcfpp.model.property.Property
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

class MCFPPEntityType(val limit: Int? = null, val types: List<String>? = null, val isName: Boolean = false) : MCFPPConcreteType(arrayListOf(MCFPPBaseType.Any)) {

    override val concreteInstanceData: CompoundData
        get() = data

    override val typeName: String
        get() {
            val builder = StringBuilder("entity[")
            if (limit != null) builder.append(limit).append(",")
            if (types != null) builder.append(types.joinToString(",")).append(",")
            builder.append(isName)
            builder.append("]")
            return builder.toString()
        }

    override val simpleName: String
        get() = "entity"

    override fun defaultValueVar(): Var<*> {
        return SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.SELF), "default")
    }

    override fun build(identifier: String, value: Any?): Var<*> {
        val qwq = EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES)
        if (limit != null) qwq.limit(limit)
        if (types != null) {
            for (type in types)
                if (type.startsWith('!')) {
                    qwq.type(type.substring(1), true)
                } else {
                    qwq.type(type, false)
                }
        }
        return SelectorVar(qwq, identifier)
    }

    override fun equals(other: Any?): Boolean {
        if (other == this) return true
        if (other !is MCFPPEntityType) return false
        if (limit != other.limit) return false
        if (types == null && other.types == null) return true
        if (types == null) return false
        if (other.types == null) return false
        return types.size == other.types.size && types.zip(other.types).all { it.first == it.second }
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (limit ?: 0)
        result = 31 * result + (types?.hashCode() ?: 0)
        return result
    }

    fun canCastTo(other: MCFPPEntityType): Boolean {
        if (this == other) return true
        if (this.limit != null && other.limit == null) return true
        if (this.types != null && other.types == null) return true
        if (this.types != null && other.types != null && this.types.containsAll(other.types)) return true
        return false
    }

    companion object {
        val NormalSelector = MCFPPEntityType()
        val SpecifiedEntity = MCFPPEntityType(1, null, false)

        val data by lazy {
            fun checkParamType(v: Var<*>, type: MCFPPType): Var<*>?{
                val value = v.implicitCast(type)
                if(value.isError){
                    LogProcessor.error(TextTranslator.CAST_ERROR.translate(value.type.typeName, type.typeName))
                    return null
                }
                return value
            }

            fun stringParam(op: (EntitySelector, MCStringConcrete) -> Unit): (CanSelectMember, Var<*>) -> Var<*> = { caller, v ->
                val selector = (caller as SelectorVar).value
                val value = checkParamType(v, MCFPPBaseType.String)
                if(value != null && value !is MCStringConcrete){
                    LogProcessor.error("Must be concrete")
                    Void
                }else if(value == null){
                    Void
                }
                op(selector, value as MCStringConcrete)
                Void
            }

            CompoundData("selector","mcfpp").apply {
                addMember(Property("x", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Int)
                    if(value is MCInt){
                        selector.x(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("y", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Int)
                    if(value is MCInt){
                        selector.y(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("z", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Int)
                    if(value is MCInt){
                        selector.z(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("distance", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Range)
                    if(value is RangeVar){
                        selector.distance(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("dx", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Int)
                    if(value is MCInt){
                        selector.dx(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("dy", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Int)
                    if (value is MCInt) {
                        selector.dy(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("dz", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Int)
                    if (value is MCInt) {
                        selector.dz(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(object : NBTDictionary("scores"), SelectorVar.SelectorParamMap {

                    override lateinit var selector: SelectorVar

                    override var type: MCFPPType = object :MCFPPPrivateType(){
                        override val typeName: String = "ScoreDictionary"
                        override fun buildReturnVar(): Var<*> {
                            LogProcessor.error("Cannot build var for type: $typeName")
                            return UnknownVar(identifier)
                        }
                    }

                    override fun getByIndex(index: Var<*>): PropertyVar {
                        if(index !is MCStringConcrete){
                            LogProcessor.error("Index must be concrete string")
                            return PropertyVar(Property.buildSimpleProperty(UnknownVar("error")), Void, this)
                        }
                        val str = index.value.value
                        return PropertyVar(Property("type", null, AnonymousNativeMutator { caller, v ->
                            val selector = (caller as SelectorVar).value
                            val value = checkParamType(v, MCFPPBaseType.Range)
                            if(value is RangeVar){
                                selector.scores(mapOf(str to value))
                            }
                            return@AnonymousNativeMutator Void
                        }), Void, selector)
                    }
                })
                addMember(Property("tag", null, AnonymousNativeMutator(stringParam { selector, str->
                    selector.tag(str.value.value, false)
                })))
                addMember(Property("tagN", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.tag(mcStringConcrete.value.value, true)
                })))
                addMember(Property("team", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.team(mcStringConcrete.value.value, false)
                })))
                addMember(Property("teamN", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.team(mcStringConcrete.value.value, true)
                })))
                addMember(Property("name", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.name(mcStringConcrete.value.value, false)
                })))
                addMember(Property("nameN", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.name(mcStringConcrete.value.value, true)
                })))
                addMember(Property("type", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.type(mcStringConcrete.value.value, false)
                })))
                addMember(Property("typeN", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.type(mcStringConcrete.value.value, true)
                })))
                addMember(Property("predicate", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, GlobalField.getTemplate("mcfpp.minecraft.resource", "LootTablePredicate")!!.getType())
                    if(value is DataTemplateObject){
                        selector.predicate(value, false)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("predicateN", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, GlobalField.getTemplate("mcfpp.minecraft.resource", "LootTablePredicate")!!.getType())
                    if(value is DataTemplateObject){
                        selector.predicate(value, true)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("xRotation", null, AnonymousNativeMutator{ caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Range)
                    if(value is RangeVar){
                        selector.xRotation(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("yRotation", null, AnonymousNativeMutator{ caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Range)
                    if(value is RangeVar){
                        selector.yRotation(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("nbt", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPNBTType.NBT)
                    if(value is NBTBasedData){
                        selector.nbt(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("level", null, AnonymousNativeMutator{ caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Range)
                    if(value is RangeVar){
                        selector.level(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("gamemode", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.gamemode(mcStringConcrete.value.value, false)
                })))
                addMember(Property("gamemodeN", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.gamemode(mcStringConcrete.value.value, true)
                })))
                addMember(Property("limit", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, MCFPPBaseType.Int)
                    if(value is MCInt){
                        selector.limit(value)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("sort", null, AnonymousNativeMutator(stringParam { entitySelector, mcStringConcrete ->
                    entitySelector.sort(mcStringConcrete.value.value)
                })))
            }
        }
    }

    object Player: MCFPPConcreteType(arrayListOf(NormalSelector)){
        override val typeName: String
            get() = "Player"

        override fun defaultValueVar(): Var<*> {
            return SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.NEAREST_PLAYER), "default")
        }

        override fun build(identifier: String, value: Any?): Var<*> {
            return PlayerVar(identifier)
        }
    }

}