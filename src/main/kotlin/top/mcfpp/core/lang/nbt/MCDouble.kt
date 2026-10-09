package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.Var
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class MCDouble : NBTBasedData {
    override var type: MCFPPType = MCFPPNBTType.Double
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: MCDouble) : super(source)
    constructor(value: DoubleTag, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.DoubleBits(value.value.toRawBits()))
    }
    override val value: DoubleTag
        get() {
            val closed = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.DoubleBits
                ?: error("Double has no complete immutable value")
            return DoubleTag(Double.fromBits(closed.bits))
        }
    override fun doAssignedBy(source: Var<*>): MCDouble = StorageAccess.write(this, source) as MCDouble
    override fun assignCommand(source: NBTBasedData): MCDouble = StorageAccess.write(this, source) as MCDouble
    override fun explicitCast(type: MCFPPType): Var<*> = StorageAccess.view(this, type)
    override fun clone(): MCDouble = MCDouble(this)
    override fun getTempVar(): MCDouble = StorageAccess.capture(this) as MCDouble
}
