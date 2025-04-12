package top.mcfpp.core.lang.resource
            
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.BlockEntityConcreteData
import top.mcfpp.mni.resource.BlockEntityData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class BlockEntity: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.BlockEntity

    /**
     * 创建一个BlockEntity类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个BlockEntity值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个BlockEntity
     * @param b 被复制的BlockEntity值
     */
    constructor(b: BlockEntity) : super(b)

    companion object {
        val data = CompoundData("BlockEntity","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BlockEntityData::class.java)
        }
    }
}

class BlockEntityConcrete: MCFPPValue<String>, BlockEntity{

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

    constructor(id: BlockEntity, value: String) : super(id){
        this.value = value
    }

    constructor(id: BlockEntityConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): BlockEntityConcrete {
        return BlockEntityConcrete(this)
    }

    override fun getTempVar(): BlockEntityConcrete {
        return BlockEntityConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return BlockEntity(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("BlockEntity","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BlockEntityConcreteData::class.java)
        }
    }
    
}        
