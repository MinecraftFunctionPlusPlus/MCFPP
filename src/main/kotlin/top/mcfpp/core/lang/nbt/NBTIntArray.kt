package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

class NBTIntArray : NBTArray {
    override var type: MCFPPType = MCFPPNBTType.IntArray
    override val arrayType: MCFPPType = MCFPPBaseType.Int
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: NBTArray) : super(source)
    constructor(value: IntArrayTag, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Sequence(value.value.map { CompilerValue.Typed(arrayType.typeId, CompilerValue.Integral(it.toLong())) }))
    }
    constructor(source: NBTArray, value: IntArrayTag) : this(source) {
        StorageAccess.initializeLiteral(this, CompilerValue.Sequence(value.value.map { CompilerValue.Typed(arrayType.typeId, CompilerValue.Integral(it.toLong())) }))
    }
    override val value: IntArrayTag get() = super.value as IntArrayTag
    override fun clone() = NBTIntArray(this)
}
