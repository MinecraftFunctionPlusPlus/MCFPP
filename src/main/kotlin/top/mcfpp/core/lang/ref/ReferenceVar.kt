package top.mcfpp.core.lang.ref

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPReferenceType
import top.mcfpp.type.MCFPPType

/** Ordinary NBT value adapter. Assignment copies the descriptor; target access is explicit. */
class ReferenceVar : Var<ReferenceVar> {
    constructor(identifier: String, type: MCFPPReferenceType) : super(identifier) { this.type = type }
    private constructor(other: ReferenceVar) : super(other) { type = other.type }
    val referenceType: MCFPPReferenceType get() = type as MCFPPReferenceType
    override var type: MCFPPType = top.mcfpp.type.MCFPPScoreRefType
    override fun doAssignedBy(b: Var<*>): ReferenceVar = StorageAccess.write(this, b) as ReferenceVar
    override fun clone(): ReferenceVar = ReferenceVar(this)
    override fun getTempVar(): ReferenceVar = StorageAccess.capture(this) as ReferenceVar
}
