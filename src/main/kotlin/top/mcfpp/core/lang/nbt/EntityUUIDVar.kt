package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.util.TempPool

/** UUID storage access without selector members or a fabricated initializer. */
class EntityUUIDVar : NBTBasedData {
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier) { type = MCFPPNBTType.IntArray }
    constructor(source: EntityUUIDVar) : super(source)
    constructor(value: IntArrayTag, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Nbt(Tag.toSNBT(value)))
    }
    override fun doAssignedBy(source: Var<*>): NBTBasedData = StorageAccess.write(this, source) as NBTBasedData
    override fun assignCommand(source: NBTBasedData): NBTBasedData = doAssignedBy(source)
    override fun clone(): EntityUUIDVar = EntityUUIDVar(this)
    override fun getTempVar(): NBTBasedData = StorageAccess.capture(this) as NBTBasedData
    companion object { val data by lazy { CompoundData("uuid", "mcfpp") } }
}
