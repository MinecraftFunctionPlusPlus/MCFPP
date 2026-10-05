package top.mcfpp.core.lang.nbt

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class NBTLongArray : NBTArray {
    override var type: MCFPPType = MCFPPNBTType.LongArray
    override val arrayType: MCFPPType = MCFPPNBTType.Long
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: NBTArray) : super(source)
    override fun clone() = NBTLongArray(this)
}

class NBTLongArrayConcrete : NBTLongArray, MCFPPValue<LongArrayTag> {
    override var value: LongArrayTag
    constructor(value: LongArrayTag, identifier: String = TempPool.getVarIdentify()) : super(identifier) { this.value = value.copy() }
    constructor(source: NBTArray, value: LongArrayTag) : super(source) { this.value = value.copy() }
    override fun clone() = NBTLongArrayConcrete(this, value)
    override fun toDynamic(replace: Boolean): Var<*> = dynamicArray(replace)
}
