package top.mcfpp.core.lang

import top.mcfpp.analysis.*
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

/** An erased public access; the producer's type and payload belong to its Place. */
open class MCAny : Var<MCAny> {
    override var type: MCFPPType = MCFPPBaseType.Any
    var container: FieldContainer? = null
    val typeKnowledge: TypeKnowledge
        get() = storageBinding?.let { it.data.facts.read(it.place)?.type } ?: TypeKnowledge.Unknown
    val inferredType: MCFPPType?
        get() = (typeKnowledge as? TypeKnowledge.Exact)?.type?.let { id -> storageBinding?.data?.types?.get(id) }
            ?.takeUnless { it == MCFPPBaseType.Any || it == MCFPPBaseType.Object }
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: MCAny) : super(source) { container = source.container }
    fun bindPayload(source: Var<*>): MCAny {
        val access = StorageAccess.view(source, type, diagnose = false)
        storageBinding = access.storageBinding
        symbol = access.symbol
        return this
    }
    override fun doAssignedBy(source: Var<*>): MCAny = StorageAccess.write(this, source) as MCAny
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier) =
        StorageAccess.actualView(this).getMemberVar(key, accessModifier)
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, accessModifier: Member.AccessModifier) =
        StorageAccess.actualView(this).getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
    override fun explicitCast(type: MCFPPType): Var<*> = StorageAccess.view(this, type)
    override fun canExplicitCast(type: MCFPPType) = true
    override fun implicitCast(type: MCFPPType): Var<*> =
        if (canImplicitCast(type)) StorageAccess.view(this, type) else Var.buildCastErrorVar(type)
    override fun canImplicitCast(type: MCFPPType) = top.mcfpp.model.function.ParameterMatcher.accepts(this, type)
    override fun clone(): MCAny = MCAny(this)
    override fun getTempVar(): Var<*> = StorageAccess.capture(this)
    override fun storeToStack() = StorageAccess.materialize(this)
    override fun getFromStack() {}
    fun buildInferredVar(type: MCFPPType): Var<*> = StorageAccess.read(StorageAccess.view(this, type, diagnose = false))
}
