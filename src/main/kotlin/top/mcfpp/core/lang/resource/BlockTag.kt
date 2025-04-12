package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.BlockTagConcreteData
import top.mcfpp.mni.resource.BlockTagData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class BlockTag: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.BlockTag

    /**
     * 创建一个BlockTag类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个BlockTag值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个BlockTag
     * @param b 被复制的BlockTag值
     */
    constructor(b: BlockTag) : super(b)

    companion object {
        val data = CompoundData("BlockTag","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BlockTagData::class.java)
        }
    }
}

class BlockTagConcrete: MCFPPValue<String>, BlockTag{

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

    constructor(id: BlockTag, value: String) : super(id){
        this.value = value
    }

    constructor(id: BlockTagConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): BlockTagConcrete {
        return BlockTagConcrete(this)
    }

    override fun getTempVar(): BlockTagConcrete {
        return BlockTagConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return BlockTag(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("BlockTag","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BlockTagConcreteData::class.java)
        }
    }
    
}        
