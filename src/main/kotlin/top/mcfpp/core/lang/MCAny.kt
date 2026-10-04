package top.mcfpp.core.lang

import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Erased payload with optional actual-type knowledge. Unknown types require `as` for concrete operations. */
open class MCAny : Var<MCAny> {

    override var type: MCFPPType = MCFPPBaseType.Any

    var lastVar : Var<*>? = null

    var container: FieldContainer? = null

    val inferredType: MCFPPType?
        get() = lastVar?.type

    /**
     * 创建一个int值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCAny) : super(b){
        lastVar = b.lastVar
        container = b.container
    }

    /**
     * 将b中的值赋值给此变量。
     *
     * @param b 变量的对象
     *
     * @return 重新获取跟踪的此变量
     */
    override fun doAssignedBy(b: Var<*>): MCAny {
        if (b is MCAny && b.inferredType == null) {
            if (inferredType != null) LogProcessor.warn("Any actual type information is lost at this assignment; use 'as' before concrete operations")
            Function.addCommand(top.mcfpp.command.Commands.dataSetFrom(nbtPath, b.nbtPath))
            return MCAny(this).apply { lastVar = null }
        }
        val source = if (b is MCAny) b.lastVar ?: b.buildInferredVar(b.inferredType!!) else b
        if (!source.type.hasRuntimeRepresentation) return MCAny(this).apply { lastVar = source }
        if (source is MCFPPValue<*> && top.mcfpp.analysis.ValueSnapshot.of(source) != null)
            return MCAnyConcrete(this, source.value).apply { lastVar = source }
        val runtimeSource = if (source is MCFPPValue<*>) source.toDynamic(false) else source
        val target = (container?.let { source.type.buildUnConcrete(identifier, it) } ?: source.type.buildUnConcrete(identifier)).setAs(this)
        val assigned = target.assignedBy(runtimeSource)
        return MCAny(this).apply { lastVar = assigned }
    }

    fun semanticValue(): Var<*> {
        val actual = inferredType
        if (actual == null) {
            LogProcessor.error("Actual type of any '$identifier' is unknown; use 'as' before a concrete operation")
            return top.mcfpp.core.lang.UnknownVar(identifier).apply { isError = true }
        }
        return buildInferredVar(actual)
    }

    override fun getMemberVar(key: String, accessModifier: top.mcfpp.model.Member.AccessModifier): Pair<Var<*>?, Boolean> =
        semanticValue().getMemberVar(key, accessModifier)

    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, accessModifier: top.mcfpp.model.Member.AccessModifier): Pair<Function, Boolean> =
        semanticValue().getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)

    override fun explicitCast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.Any -> this
            else -> {
                buildInferredVar(type)
            }
        }
    }

    override fun canExplicitCast(type: MCFPPType) = true

    override fun implicitCast(type: MCFPPType): Var<*> {
        if (!canImplicitCast(type)) return Var.buildCastErrorVar(type)
        if (type == MCFPPBaseType.Any) return this
        if (type == MCFPPBaseType.Object) return MCObject().setAs(this).apply { (this as MCObject).lastVar = this@MCAny.lastVar }
        return semanticValue().implicitCast(type)
    }

    override fun canImplicitCast(type: MCFPPType) = top.mcfpp.model.function.ParameterMatcher.accepts(this, type)

    override fun clone(): MCAny {
        return MCAny(this)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): MCAny {
        return this
    }

    override fun storeToStack() { lastVar?.storeToStack() }

    override fun getFromStack() { lastVar?.getFromStack() }

    open fun buildInferredVar(type: MCFPPType): Var<*>{
        lastVar?.let { if (it.type == type && !type.hasRuntimeRepresentation) return it }
        val re = if(container != null){
            type.buildUnConcrete(this.identifier, container!!).setAs(this)
        } else{
            type.buildUnConcrete(this.identifier).setAs(this)
        }
        return re
    }
}

class MCAnyConcrete : MCAny, MCFPPValue<Any?> {

    override var value: Any?

    /**
     * 创建一个固定的any。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Any?, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    /**
     * 创建一个MCAny类型的变量。它是v的跟踪版本
     */
    constructor(v : MCAny, value: Any?): super(v){
        this.value = value
    }

    constructor(v: MCAnyConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): MCAnyConcrete {
        return MCAnyConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val actual = inferredType
        val runtime = if (actual != null) (buildInferredVar(actual) as? MCFPPValue<*>)?.toDynamic(false) else null
        val re = MCAny(this).apply { lastVar = runtime }
        if (replace) {
            if (parentTemplate() != null) parentTemplate()!!.scope.putVar(identifier, re, true)
            else Function.currFunction.scope.putVar(identifier, re, true)
        }
        return re
    }

    override fun buildInferredVar(type: MCFPPType): Var<*> {
        val re = if(container != null){
            type.build(this.identifier, container!!, value).setAs(this)
        } else{
            type.build(this.identifier, value).setAs(this)
        }
        return re
    }

}
