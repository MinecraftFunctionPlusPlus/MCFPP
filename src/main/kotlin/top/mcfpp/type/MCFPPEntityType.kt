package top.mcfpp.type

import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.property.AnonymousNativeMutator
import top.mcfpp.model.property.Property
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

class MCFPPEntityType(val limit: Int? = null, val types: List<String>? = null, val isName: Boolean = false) : MCFPPConcreteType(arrayListOf(MCFPPBaseType.Object)) {

    /** A compiler-owned selector member, with its receiver slot kept separate from a dictionary value. */
    private class ScoresDictionary : NBTDictionary("scores"), SelectorVar.SelectorParamMap {
        private var selectorSlot: SelectorVar? = null

        override var selector: SelectorVar
            get() = selectorSlot ?: throw UninitializedPropertyAccessException("lateinit property selector has not been initialized")
            set(value) { selectorSlot = value }

        override var type: MCFPPType = ScoresDictionaryType(this)

        fun checkpoint(retained: List<Any>): ScoresStateCheckpoint {
            val receiver = selectorSlot
            val declaredType = type
            val owner = scoresTypeOwner(declaredType)
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
            val elements = (retained + listOfNotNull(this, declaredType, owner, receiver)).filter { seen.add(it) }
            return ScoresStateCheckpoint(elements, {
                selectorSlot = receiver
                type = declaredType
            }, { checkpoint(elements) })
        }

        override fun getByIndex(index: Var<*>): PropertyVar {
            if (index !is MCString) {
                LogProcessor.error("Index must be concrete string")
                return PropertyVar(Property.buildSimpleProperty(UnknownVar("error")), Void, this)
            }
            val str = index.value.value
            return PropertyVar(Property("type", null, AnonymousNativeMutator { caller, v ->
                val selector = (caller as SelectorVar).value
                val value = checkParamType(v, MCFPPBaseType.Range)
                if (value is RangeVar) selector.scores(mapOf(str to value))
                Void
            }), Void, selector)
        }
    }

    /** The original anonymous type captures this exact adapter, including its current identifier. */
    private class ScoresDictionaryType(val owner: ScoresDictionary) : MCFPPPrivateType() {
        override val typeName: String = "ScoreDictionary"
        override fun buildReturnVar(): Var<*> {
            LogProcessor.error("Cannot build var for type: $typeName")
            return UnknownVar(owner.identifier)
        }
    }

    /** Raw relationships only; declaration/type/annotation internals belong to the deep journal. */
    internal class ScoresStateCheckpoint internal constructor(
        elements: List<Any>, private val restoreState: () -> Unit,
        private val recapture: () -> ScoresStateCheckpoint
    ) {
        val elements: List<Any> = java.util.Collections.unmodifiableList(ArrayList(elements))
        fun restore() = restoreState()
        fun captureCurrent(): ScoresStateCheckpoint = recapture()
    }

    override fun buildUnConcrete(identifier: String): Var<*> = SelectorVar(identifier).apply { type = this@MCFPPEntityType }

    override val typeId: TypeId get() = TypeId.Selector(limit, types?.toList(), isName)

    override val instanceData: CompoundData
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
        return buildUnConcrete("default")
    }

    override fun build(identifier: String, value: Any?): Var<*> {
        return top.mcfpp.analysis.StorageAccess.literal(this, value as top.mcfpp.analysis.CompilerValue, identifier)
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

        internal fun newScoresDeclaration(): NBTDictionary = ScoresDictionary()

        internal fun isOwnedScores(value: Var<*>): Boolean = value.javaClass === ScoresDictionary::class.java

        internal fun scoresTypeOwner(type: MCFPPType): Var<*>? =
            if (type.javaClass === ScoresDictionaryType::class.java) (type as ScoresDictionaryType).owner else null

        internal fun checkpointScores(value: Var<*>): ScoresStateCheckpoint {
            check(isOwnedScores(value)) { "Selector scores checkpoint cannot capture ${value.javaClass.name}" }
            return (value as ScoresDictionary).checkpoint(emptyList())
        }

        private fun checkParamType(v: Var<*>, type: MCFPPType): Var<*>? {
            val value = v.implicitCast(type)
            if (value.isError) {
                LogProcessor.error(TextTranslator.CAST_ERROR.translate(value.type.typeName, type.typeName))
                return null
            }
            return value
        }

        val data by lazy {
            fun stringParam(op: (EntitySelector, MCString) -> Unit): (CanSelectMember, Var<*>) -> Var<*> = { caller, v ->
                val selector = (caller as SelectorVar).value
                val value = checkParamType(v, MCFPPBaseType.String)
                if(value != null && value !is MCString){
                    LogProcessor.error("Must be concrete")
                    Void
                }else if(value == null){
                    Void
                }
                op(selector, value as MCString)
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
                addMember(newScoresDeclaration())
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
                    val value = checkParamType(v, GlobalScope.getTemplate("mcfpp.minecraft.resource", "LootTablePredicate")!!.getType())
                    if(value is DataTemplateObject){
                        selector.predicate(value, false)
                    }
                    return@AnonymousNativeMutator Void
                }))
                addMember(Property("predicateN", null, AnonymousNativeMutator { caller, v ->
                    val selector = (caller as SelectorVar).value
                    val value = checkParamType(v, GlobalScope.getTemplate("mcfpp.minecraft.resource", "LootTablePredicate")!!.getType())
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
        override val typeId: TypeId = TypeId.Builtin("player")

        override val typeName: String
            get() = "Player"

        override fun defaultValueVar(): Var<*> {
            return buildUnConcrete("default")
        }

        override fun build(identifier: String, value: Any?): Var<*> {
            return top.mcfpp.analysis.StorageAccess.literal(this, value as top.mcfpp.analysis.CompilerValue, identifier)
        }
        override fun buildUnConcrete(identifier: String): Var<*> = SelectorVar(identifier).apply { type = this@Player }
    }

}
