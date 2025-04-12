package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.ItemTagConcreteData
import top.mcfpp.mni.resource.ItemTagData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class ItemTag: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.ItemTag

    /**
     * 创建一个ItemTag类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个ItemTag值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个ItemTag
     * @param b 被复制的ItemTag值
     */
    constructor(b: ItemTag) : super(b)

    companion object {
        val data = CompoundData("ItemTag","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(ItemTagData::class.java)
        }
    }
}

class ItemTagConcrete: MCFPPValue<String>, ItemTag{

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

    constructor(id: ItemTag, value: String) : super(id){
        this.value = value
    }

    constructor(id: ItemTagConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): ItemTagConcrete {
        return ItemTagConcrete(this)
    }

    override fun getTempVar(): ItemTagConcrete {
        return ItemTagConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return ItemTag(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("ItemTag","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(ItemTagConcreteData::class.java)
        }
    }
    
}        
