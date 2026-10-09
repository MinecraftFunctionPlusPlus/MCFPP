package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

class NBTLongArray : NBTArray {
    override var type: MCFPPType = MCFPPNBTType.LongArray
    override val arrayType: MCFPPType = MCFPPNBTType.Long
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: NBTArray) : super(source)
    constructor(value: LongArrayTag, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Sequence(value.value.map { CompilerValue.Typed(arrayType.typeId, CompilerValue.Integral(it)) }))
    }
    constructor(source: NBTArray, value: LongArrayTag) : this(source) {
        StorageAccess.initializeLiteral(this, CompilerValue.Sequence(value.value.map { CompilerValue.Typed(arrayType.typeId, CompilerValue.Integral(it)) }))
    }
    override val value: LongArrayTag get() = super.value as LongArrayTag
    override fun clone() = NBTLongArray(this)
}
