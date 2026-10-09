package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

class NBTByteArray : NBTArray {
    override var type: MCFPPType = MCFPPNBTType.ByteArray
    override val arrayType: MCFPPType = MCFPPNBTType.Byte
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: NBTArray) : super(source)
    constructor(value: ByteArrayTag, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Sequence(value.value.map { CompilerValue.Typed(arrayType.typeId, CompilerValue.Integral(it.toLong())) }))
    }
    constructor(source: NBTArray, value: ByteArrayTag) : this(source) {
        StorageAccess.initializeLiteral(this, CompilerValue.Sequence(value.value.map { CompilerValue.Typed(arrayType.typeId, CompilerValue.Integral(it.toLong())) }))
    }
    override val value: ByteArrayTag get() = super.value as ByteArrayTag
    override fun clone() = NBTByteArray(this)
}
