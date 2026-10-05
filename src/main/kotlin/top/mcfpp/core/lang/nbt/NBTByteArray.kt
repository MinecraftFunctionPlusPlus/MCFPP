package top.mcfpp.core.lang.nbt

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class NBTByteArray : NBTArray {
    override var type: MCFPPType = MCFPPNBTType.ByteArray
    override val arrayType: MCFPPType = MCFPPNBTType.Byte
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: NBTArray) : super(source)
    override fun clone() = NBTByteArray(this)
}

class NBTByteArrayConcrete : NBTByteArray, MCFPPValue<ByteArrayTag> {
    override var value: ByteArrayTag
    constructor(value: ByteArrayTag, identifier: String = TempPool.getVarIdentify()) : super(identifier) { this.value = value.copy() }
    constructor(source: NBTArray, value: ByteArrayTag) : super(source) { this.value = value.copy() }
    override fun clone() = NBTByteArrayConcrete(this, value)
    override fun toDynamic(replace: Boolean): Var<*> = dynamicArray(replace)
}
