package top.mcfpp.core.lang

import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeRelations
import top.mcfpp.util.TempPool

/** The shared erased payload is accessible only through object's declared public signature. */
class MCObject : MCAny {
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: MCObject) : super(source)
    override var type: MCFPPType = MCFPPBaseType.Object
    override fun doAssignedBy(b: Var<*>): MCObject = super.doAssignedBy(b) as MCObject
    override fun canImplicitCast(type: MCFPPType) = TypeRelations.resolveImplicitConversion(this.type, type) != null
    override fun implicitCast(type: MCFPPType): Var<*> = when (type) {
        MCFPPBaseType.Object -> this
        MCFPPBaseType.Any -> top.mcfpp.analysis.StorageAccess.view(this, type)
        else -> Var.buildCastErrorVar(type)
    }
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier) = type.getMemberVar(key, accessModifier)
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, accessModifier: Member.AccessModifier) =
        type.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
    override fun clone() = MCObject(this)
    override fun getTempVar(): Var<*> = top.mcfpp.analysis.StorageAccess.capture(this)
}
