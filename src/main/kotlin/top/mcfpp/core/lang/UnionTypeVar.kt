package top.mcfpp.core.lang

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPUnionType

/** Public union access; the selected producer belongs to the same Place facts. */
class UnionTypeVar : Var<UnionTypeVar> {
    constructor(identifier: String, vararg types: MCFPPType) : super(identifier) {
        type = MCFPPUnionType(*types)
    }
    constructor(other: UnionTypeVar) : super(other)
    val unionTypes: Array<out MCFPPType> get() = (type as MCFPPUnionType).types
    override fun doAssignedBy(b: Var<*>): UnionTypeVar { StorageAccess.write(this, b); return this }
    override fun clone() = UnionTypeVar(this)
    override fun getTempVar() = StorageAccess.capture(this) as UnionTypeVar
    override fun storeToStack() { StorageAccess.materialize(this) }
    override fun getFromStack() { StorageAccess.invalidateReads(listOf(this)) }
    override fun explicitCast(type: MCFPPType) = StorageAccess.view(this, type)
    override fun implicitCast(type: MCFPPType): Var<*> = if (type == this.type) this
        else if (canImplicitCast(type)) StorageAccess.view(this, type, diagnose = false) else buildCastErrorVar(type)
    override fun canImplicitCast(type: MCFPPType) = top.mcfpp.type.TypeRelations.resolveImplicitConversion(this.type, type) != null
    override fun canExplicitCast(type: MCFPPType) = true
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> =
        StorageAccess.unionMember(this, key, accessModifier)
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>,
                                  accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        val members = unionTypes.map { it.buildUnConcrete(identifier).getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier) }
        val first = members.firstOrNull() ?: return UnknownFunction(key) to true
        if (first.first is UnknownFunction || members.any { it.first !== first.first }) return UnknownFunction(key) to true
        return first.first to members.all { it.second }
    }
}
