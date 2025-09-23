package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class NBTIntArray: NBTArray {

    override var type: MCFPPType = MCFPPNBTType.IntArray

    override val arrayType: MCFPPType = MCFPPBaseType.Int

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(b: NBTArray) : super(b)

    override fun clone(): NBTIntArray {
        return NBTIntArray(this)
    }

    override fun doAssignedBy(b: Var<*>) : NBTIntArray {
        return when (b) {
            is NBTIntArray -> assignCommand(b)
            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    @InsertCommand
    protected open fun assignCommand(a: NBTIntArray) : NBTIntArray {
        nbtType = a.nbtType
        return if(a is NBTIntArrayConcrete){
            NBTIntArrayConcrete(this, a.value)
        } else {
            Function.addCommand(Commands.dataSetFrom(nbtPath, a.nbtPath))
            NBTIntArray(this)
        }
    }

}

class NBTIntArrayConcrete: NBTIntArray, MCFPPValue<IntArrayTag>{

    override var value: IntArrayTag

    constructor(value: IntArrayTag, identifier: String = TempPool.getVarIdentify()) : super(identifier){
        this.value = value
    }

    constructor(b: NBTArray, value: IntArrayTag) : super(b){
        this.value = value
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, value).toDynamic(replace)
        return NBTIntArray(this)
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

    override fun clone(): NBTIntArrayConcrete {
        return NBTIntArrayConcrete(this, this.value)
    }


}