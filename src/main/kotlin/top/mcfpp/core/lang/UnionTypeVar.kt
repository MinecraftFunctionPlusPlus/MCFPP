package top.mcfpp.core.lang

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
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
        else StorageAccess.actualView(this).implicitCast(type)
    override fun canImplicitCast(type: MCFPPType) = type == this.type || unionTypes.all { it.isSubOf(type) }
    override fun canExplicitCast(type: MCFPPType) = true
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> =
        StorageAccess.actualView(this).getMemberVar(key, accessModifier)
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>,
                                  accessModifier: Member.AccessModifier): Pair<Function, Boolean> =
        StorageAccess.actualView(this).getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
}
