package top.mcfpp.type

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.nbt.tags.collection.IntArrayTag

object MCFPPUUIDType : MCFPPType(arrayListOf(MCFPPBaseType.Object)) {
    override val typeId = TypeId.Builtin("UUID")
    override val typeName = "UUID"
    override val nbtType = IntArrayTag::class.java
    override fun buildUnConcrete(identifier: String): Var<*> = UUIDVar(identifier)
    override fun build(identifier: String, value: Any?): Var<*> =
        StorageAccess.initializeLiteral(UUIDVar(identifier), value as CompilerValue)
}

class UUIDVar(identifier: String) : NBTBasedData(identifier) {
    init { type = MCFPPUUIDType }
    override fun clone(): UUIDVar = UUIDVar(identifier).also { it.setAs(this) }
    override fun getTempVar(): NBTBasedData = StorageAccess.capture(this) as NBTBasedData
}
