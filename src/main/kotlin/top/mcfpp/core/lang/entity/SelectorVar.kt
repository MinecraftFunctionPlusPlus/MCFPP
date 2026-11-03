package top.mcfpp.core.lang.entity

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntityFacts
import top.mcfpp.lib.EntityTarget
import top.mcfpp.lib.EntitySource
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.SelectorData
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
 * 普通实体值在使用点解析目标；数量与种类事实属于值，不参与公开类型身份。
 *
 * @see top.mcfpp.core.lang.nbt.EntityUUIDVar
 */
open class SelectorVar : Var<SelectorVar>, EntityTargetAccess {

    @Suppress("SuspiciousVarProperty")
    override var type: MCFPPType = MCFPPEntityType.NormalSelector

    /**
     * 创建一个目标选择器。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(selector: EntitySelector, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.updateSelector(this, selector)
    }
    constructor(target: EntityTarget, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.updateEntityTarget(this, target)
    }

    /** Missing or incomplete payloads carry no selector or cardinality guarantee. */
    open fun entityTargetOrNull(): EntityTarget? {
        val binding = storageBinding ?: return null
        return StorageAccess.entityTarget(binding)
    }

    override val entityFacts: EntityFacts get() = entityTargetOrNull()?.facts ?: EntityFacts.UNKNOWN

    val value: EntitySelector get() {
        val selector = (entityTargetOrNull() as? EntityTarget.Selector)?.selector
            ?: error("Entity has no complete accessible selector program")
        return selector.apply { onChange = { StorageAccess.updateSelector(this@SelectorVar, it) } }
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

    fun isPlayer(): Boolean = entityFacts.playersOnly

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
        return EntityMembers.member(this, key, accessModifier)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return EntityMembers.data(this).getFunction(key, readOnlyArgs, normalArgs) to true
    }

    override fun clone(): SelectorVar {
        return SelectorVar(this)
    }

    override fun getTempVar(): SelectorVar {
        return StorageAccess.capturePayload(type, this, TempPool.getVarIdentify()) as SelectorVar
    }

    override fun toCommandPart(): Command {
        val loaded = StorageAccess.read(this)
        return if (loaded is EntityTargetAccess) loaded.toEntityTargetCommand() else Command("")
    }

    override fun toEntityTargetCommand(): Command {
        val target = entityTargetOrNull()
        if (target != null) return target.toCommandPart()
        LogProcessor.error("Entity target has no complete accessible payload; cannot resolve '$identifier'")
        return Command("")
    }

    interface SelectorParamMap: Indexable {
         var selector: SelectorVar
    }

    companion object {

        internal fun declarationData(@Suppress("UNUSED_PARAMETER") type: MCFPPEntityType): DataTemplate {
            return AllEntityDataTemplate().apply {
                extends(MCFPPEntityType.data)
            }
        }

        private class AllEntityDataTemplate: DataTemplate("AllEntity","mcfpp"){
            init {
                GlobalScope.getUnsolvedImportNamespace("mcfpp.minecraft.entity")?.scope?.getTemplate("EntityData")?.getType()?.let {
                    it.tryResolve()
                    extends(it.template)
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

        /** Recognizes only selector-owned declarations without resolving or inspecting their contents. */
        internal fun isOwnedDeclaration(compound: CompoundData): Boolean =
            compound.javaClass === AllEntityDataTemplate::class.java

    }
}
