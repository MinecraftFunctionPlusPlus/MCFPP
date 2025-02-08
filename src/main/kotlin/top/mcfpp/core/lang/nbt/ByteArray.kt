package top.mcfpp.core.lang.nbt

import net.querz.nbt.tag.ByteArrayTag
import top.mcfpp.core.lang.*
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

open class ByteArray: NBTArray {

    override var type: MCFPPType = MCFPPNBTType.ByteArray

    override val arrayType: MCFPPType = MCFPPNBTType.Byte

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(b: NBTArray) : super(b)
}

class ByteArrayConcrete: ByteArray, MCFPPValue<ByteArrayTag>{

    override var value: ByteArrayTag

    constructor(value: ByteArrayTag, identifier: String = TempPool.getVarIdentify()) : super(identifier){
        this.value = value
    }

    constructor(b: NBTArray, value: ByteArrayTag) : super(b){
        this.value = value
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, value).toDynamic(replace)
        return ByteArray(this)
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        if(index is MCInt){
            val v = arrayType.build(TempPool.getVarIdentify())
            v.nbtPath = nbtPath.intIndex(index)
            v.parent = this
            return PropertyVar(Property.buildSimpleProperty(v), v,this)
        }else{
            LogProcessor.error("Index must be a int")
            val re = UnknownVar("error_${identifier}_index_${index.identifier}")
            return PropertyVar(Property.buildSimpleProperty(re), re, this)
        }
    }

}