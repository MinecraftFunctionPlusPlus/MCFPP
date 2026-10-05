package top.mcfpp.core.lang.nbt

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class NBTIntArray : NBTArray {
    override var type: MCFPPType = MCFPPNBTType.IntArray
    override val arrayType: MCFPPType = MCFPPBaseType.Int
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(source: NBTArray) : super(source)
    override fun clone() = NBTIntArray(this)
}

class NBTIntArrayConcrete : NBTIntArray, MCFPPValue<IntArrayTag> {
    override var value: IntArrayTag
    constructor(value: IntArrayTag, identifier: String = TempPool.getVarIdentify()) : super(identifier) { this.value = value.copy() }
    constructor(source: NBTArray, value: IntArrayTag) : super(source) { this.value = value.copy() }
    override fun clone() = NBTIntArrayConcrete(this, value)
    override fun toDynamic(replace: Boolean): Var<*> = dynamicArray(replace)
}
