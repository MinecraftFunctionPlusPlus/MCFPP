package top.mcfpp.core.lang.entity

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntitySource
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.SelectorData
import top.mcfpp.mni.annotation.MCFPPEntity
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.toCamelCase
import top.mcfpp.util.TempPool


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
open class SelectorVar : Var<SelectorVar> {

    @Suppress("SuspiciousVarProperty")
    override var type: MCFPPType = MCFPPEntityType()

    /**
     * 创建一个目标选择器。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(selector: EntitySelector, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        type = MCFPPEntityType(selector.getLimit().takeUnless { it == Int.MAX_VALUE },
            selector.getType().takeUnless { it.isEmpty() }?.map { if (it.value) "!${it.key}" else it.key.toString() })
        StorageAccess.updateSelector(this, selector)
    }

    val value: EntitySelector get() {
        val binding = storageBinding ?: error("Selector has no initialized program")
        return StorageAccess.selectorProgram(binding)?.selector(binding.data.types, binding)
            ?: error("Selector has no complete accessible program")
    }

    /**
     * 复制一个目标选择器
     * @param b 被复制的目标选择器值
     */
    constructor(b: SelectorVar) : super(b)

    override fun doAssignedBy(b: Var<*>): SelectorVar {
        if (b is SelectorVar) {
            StorageAccess.ensure(this)
            return StorageAccess.write(this, b) as SelectorVar
        }
        LogProcessor.error("Cannot assign ${b.type} to entity")
        return this
    }

    fun isPlayer(): Boolean {
        return value.onlyIncludingPlayers()
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

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return type is MCFPPEntityType && (this.type as MCFPPEntityType).canCastTo(type) || super.canExplicitCast(type)
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

    override fun canImplicitCast(type: MCFPPType): Boolean {
        return type is MCFPPEntityType && (this.type as MCFPPEntityType).canCastTo(type) || super.canImplicitCast(type)
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        if(key == "dat"){
            val data = DataTemplateObject(getData(), "dat")
            data.nbtPath = NBTPath(EntitySource(this))
            return data to true
        }
        val p = getData().scope.getProperty(key)
        if(p != null) return PropertyVar(p, Void, this) to true
        val v = getData().scope.getVar(key)
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
        var selected: DataTemplate? = null
        for ((type, reverse) in types){
            val name = type.identifier.toCamelCase(true) + "Data"
            if(!reverse){
                if (type.namespace == "minecraft") {
                    GlobalScope.getUnsolvedImportNamespace("mcfpp.minecraft.entity")?.scope?.getTemplate(name)?.getType()?.let {
                        it.tryResolve()
                        selected = it.template
                    }
                }
            }else{
                if (type.namespace == "minecraft") excluded.add(name)
            }
        }
        val data = AllEntityDataTemplate(excluded)
        selected?.let { data.extends(it) }
        data.extends(MCFPPEntityType.data)
        return data
    }

    override fun clone(): SelectorVar {
        return SelectorVar(this)
    }

    override fun getTempVar(): SelectorVar {
        return StorageAccess.capturePayload(type, this, TempPool.getVarIdentify()) as SelectorVar
    }

    override fun toCommandPart(): Command {
        val loaded = StorageAccess.read(this)
        return if (loaded is SelectorVar) loaded.value.toCommandPart() else Command("")
    }

    interface SelectorParamMap: Indexable {
         var selector: SelectorVar
    }

    companion object {

        internal fun declarationData(type: MCFPPEntityType): DataTemplate {
            val excluded = type.types.orEmpty().filter { it.startsWith("!minecraft:") }
                .map { it.substringAfter(':').toCamelCase(true) + "Data" }
            return AllEntityDataTemplate(excluded).apply {
                type.types.orEmpty().filter { it.startsWith("minecraft:") }.forEach { name ->
                    GlobalScope.getCanonicalTemplate("mcfpp.minecraft.entity", name.substringAfter(':').toCamelCase(true) + "Data")
                        ?.let { extends(it) }
                }
                extends(MCFPPEntityType.data)
            }
        }

        private val cache = HashMap<List<String>, AllEntityDataTemplate>()

        private class AllEntityDataTemplate(excluded: List<String>): DataTemplate("AllEntity","mcfpp"){
            init {
                GlobalScope.getUnsolvedImportNamespace("mcfpp.minecraft.entity")?.scope?.getTemplate("EntityData")?.getType()?.let {
                    it.tryResolve()
                    extends(it.template)
                }
                GlobalScope.getTemplate { data ->
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
                scope.parent.add(compoundData.scope)
                //把所有成员都塞进去
                compoundData.scope.forEachVar {
                    scope.putVar(it.identifier, it, true)
                }
                compoundData.scope.forEachProperty {
                    scope.putProperty(it.identifier, it, true)
                }
                return this
            }

        }


    }
}
