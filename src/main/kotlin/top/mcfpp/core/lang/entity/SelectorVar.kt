package top.mcfpp.core.lang.entity

import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntitySource
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.SelectorData
import top.mcfpp.mni.annotation.MCFPPEntity
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.AnonymousNativeMutator
import top.mcfpp.model.property.Property
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.toCamelCase
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate


/**
 * 目标选择器（Target Selector）可在无需指定确切的玩家名称或UUID的情况下在命令中选择任意玩家与实体。目标选择器变量可以选择一个或多个实体，
 * 目标选择器参数可以根据特定条件筛选目标。
 *
 * selector拥有一个可选泛型参数limit，用于限制目标选择器选择的实体数量。例如selector<1>将会选择一个实体。
 *
 * 在mcfpp中你可以直接让目标选择器作为命令函数的参数，也可以让目标选择器选择实体，得到一个实体或者一个实体列表，之后再对它们进行操作
 *
 * 在构造一个目标选择器示例的时候，目标选择器的类型是必然确定的，因此只有用于构造确定变量的构造函数
 *
 * @see top.mcfpp.core.lang.nbt.EntityUUIDVar
 */
open class SelectorVar : ConcreteVar<SelectorVar, EntitySelector> {

    @Suppress("SuspiciousVarProperty")
    override var type: MCFPPType = MCFPPEntityType()
        get() {
            if(value.getLimit() == Int.MAX_VALUE && value.getType().isEmpty()){
                return MCFPPEntityType()
            }
            if(value.getLimit() != Int.MAX_VALUE && value.getType().isEmpty()){
                return MCFPPEntityType(value.getLimit())
            }
            if(value.getLimit() == Int.MAX_VALUE){
                return MCFPPEntityType(null, value.getType().map { if(it.value) "!${it.key}" else it.key.toString() })
            }
            return MCFPPEntityType(value.getLimit(), value.getType().map { if(it.value) "!${it.key}" else it.key.toString() })
        }

    /**
     * 创建一个目标选择器。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(selector: EntitySelector, identifier: String = TempPool.getVarIdentify()) : super(identifier, selector)

    /**
     * 复制一个目标选择器
     * @param b 被复制的目标选择器值
     */
    constructor(b: SelectorVar) : super(b)

    fun isPlayer(): Boolean {
        return value.onlyIncludingPlayers()
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        val qwq = super.explicitCast(type)
        if(!qwq.isError) return qwq
        return when(type){
            is MCFPPEntityType -> {
                if((this.type as MCFPPEntityType).canCastTo(type)){
                    this.type.build("").setAs(this)
                }else qwq
            }

            else -> qwq
        }
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        val qwq = super.implicitCast(type)
        if(!qwq.isError) return qwq
        return when(type){
            is MCFPPEntityType -> {
                if((this.type as MCFPPEntityType).canCastTo(type)){
                    this.type.build("").setAs(this)
                }else qwq
            }

            else -> qwq
        }
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        if(key == "dat"){
            val data = DataTemplateObject(getData(), "dat")
            data.nbtPath = NBTPath(EntitySource(this))
            return data to true
        }
        val p = getData().field.getProperty(key)
        if(p != null) return PropertyVar(p, Void, this) to true
        val v = getData().field.getVar(key)
        if(v is SelectorParamMap) {
            v.selector = this
            return v to true
        }
        return null to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return getData().getFunction(key, readOnlyArgs, normalArgs) to true
    }

    private fun getData(): DataTemplate {
        val types = value.getType()
        val excluded = ArrayList<String>()
        for ((type, reverse) in types){
            if(!reverse){
                val d = GlobalField.getTemplate("mcfpp.minecraft.entity", type.toString().toCamelCase(true))
                if(d == null){
                    LogProcessor.error("Undefined entity: $type (${type.toString().toCamelCase(true)})")
                }else{
                    d.alwaysDynamic = true
                    return d
                }
            }else{
                excluded.add(type.toString().toCamelCase(true))
            }
        }
        val data = AllEntityDataTemplate(excluded)
        data.extends(Companion.data)
        return data
    }

    override fun clone(): SelectorVar {
        return SelectorVar(this)
    }

    override fun getTempVar(): SelectorVar {
        return SelectorVar(value.clone())
    }

    private interface SelectorParamMap: Indexable {
         var selector: SelectorVar
    }

    companion object {

        private val cache = HashMap<List<String>, AllEntityDataTemplate>()

        private class AllEntityDataTemplate(excluded: List<String>): DataTemplate("AllEntity","mcfpp"){
            init {
                GlobalField.getTemplate { data ->
                    data.annotations.any { it is MCFPPEntity } && data.identifier !in excluded
                }.forEach {
                    extends(it)
                }
                injectedBy(SelectorData::class.java)
                alwaysDynamic = true
                //单实体的方法
            }

            override fun extends(compoundData: CompoundData): CompoundData {
                if(parent.contains(compoundData)){
                    LogProcessor.warn("Already extends template '${compoundData.identifier}'")
                    return this
                }
                parent.add(compoundData)
                compoundData.children.add(this)
                field.parent.add(compoundData.field)
                //把所有成员都塞进去
                compoundData.field.forEachVar {
                    field.putVar(it.identifier, it, true)
                }
                compoundData.field.forEachProperty {
                    field.putProperty(it.identifier, it, true)
                }
                return this
            }

        }


        val data: CompoundData
            get() {

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

            return CompoundData("selector","mcfpp").apply {
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
                addMember(object : NBTDictionary("scores"), SelectorParamMap {

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
}