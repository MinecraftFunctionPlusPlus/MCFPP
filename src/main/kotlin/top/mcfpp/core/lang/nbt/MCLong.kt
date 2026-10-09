package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.Var
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class MCLong : NBTBasedData {
    override var type: MCFPPType = MCFPPNBTType.Long
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: MCLong) : super(source)
    constructor(value: LongTag, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Integral(value.value))
    }
    override val value: LongTag
        get() {
            val closed = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Integral
                ?: error("Long has no complete immutable value")
            return LongTag(closed.value)
        }
    override fun doAssignedBy(source: Var<*>): MCLong = StorageAccess.write(this, source) as MCLong
    override fun assignCommand(source: NBTBasedData): MCLong = StorageAccess.write(this, source) as MCLong
    override fun explicitCast(type: MCFPPType): Var<*> = StorageAccess.view(this, type)
    override fun clone(): MCLong = MCLong(this)
    override fun getTempVar(): MCLong = StorageAccess.capture(this) as MCLong
}
