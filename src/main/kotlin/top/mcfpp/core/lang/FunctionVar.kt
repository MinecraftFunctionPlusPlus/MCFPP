package top.mcfpp.core.lang
            
import net.querz.nbt.tag.StringTag
import top.mcfpp.command.Command
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.core.lang.resource.ResourceID
import top.mcfpp.mni.resource.FunctionIDConcreteData
import top.mcfpp.mni.resource.FunctionIDData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class FunctionVar: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.FunctionID

    /**
     * 创建一个FunctionID值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个FunctionID
     * @param b 被复制的FunctionID值
     */
    constructor(b: FunctionVar) : super(b)

    companion object {
        val data = CompoundData("FunctionID","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(FunctionIDData::class.java)
        }
    }
}

class FunctionIDConcrete: MCFPPValue<Function>, FunctionVar {

    override var value: Function

    constructor(value: Function, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(id: FunctionVar, value: Function) : super(id){
        this.value = value
    }

    constructor(id: FunctionIDConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): FunctionIDConcrete {
        return FunctionIDConcrete(this)
    }

    override fun getTempVar(): FunctionIDConcrete {
        return FunctionIDConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value.namespaceID.toString())).toDynamic(replace)
        return FunctionVar(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }

    override fun toCommandPart(): Command {
        return Command(value.namespaceID.toString())
    }
    
    companion object {
        val data = CompoundData("FunctionID","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(FunctionIDConcreteData::class.java)
        }
    }
    
}        
