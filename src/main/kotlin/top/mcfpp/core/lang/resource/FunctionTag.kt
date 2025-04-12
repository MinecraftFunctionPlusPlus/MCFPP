package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.FunctionTagConcreteData
import top.mcfpp.mni.resource.FunctionTagData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class FunctionTag: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.FunctionTag

    /**
     * 创建一个FunctionTag类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个FunctionTag值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个FunctionTag
     * @param b 被复制的FunctionTag值
     */
    constructor(b: FunctionTag) : super(b)

    companion object {
        val data = CompoundData("FunctionTag","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(FunctionTagData::class.java)
        }
    }
}

class FunctionTagConcrete: MCFPPValue<String>, FunctionTag{

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

    constructor(id: FunctionTag, value: String) : super(id){
        this.value = value
    }

    constructor(id: FunctionTagConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): FunctionTagConcrete {
        return FunctionTagConcrete(this)
    }

    override fun getTempVar(): FunctionTagConcrete {
        return FunctionTagConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return FunctionTag(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("FunctionTag","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(FunctionTagConcreteData::class.java)
        }
    }
    
}        
