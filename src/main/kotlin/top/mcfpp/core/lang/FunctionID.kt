package top.mcfpp.core.lang
            
import net.querz.nbt.tag.StringTag
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.core.lang.resource.ResourceID
import top.mcfpp.mni.resource.FunctionIDConcreteData
import top.mcfpp.mni.resource.FunctionIDData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class FunctionID: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.FunctionID

    /**
     * 创建一个FunctionID类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = TempPool.getVarIdentify()
    ) : super(curr, identifier) {
        this.identifier = identifier
    }

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
    constructor(b: FunctionID) : super(b)

    companion object {
        val data = CompoundData("FunctionID","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(FunctionIDData::class.java)
        }
    }
}

class FunctionIDConcrete: MCFPPValue<String>, FunctionID {

    override var value: String

    constructor(
        curr: FieldContainer,
        value: String,
        identifier: String = TempPool.getVarIdentify()
    ) : super(curr, identifier) {
        this.value = value
    }

    constructor(value: String, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(id: FunctionID, value: String) : super(id){
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
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return FunctionID(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
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
