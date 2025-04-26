package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.LootTableTypeConcreteData
import top.mcfpp.mni.resource.LootTableTypeData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class LootTableType: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.LootTableType

    /**
     * 创建一个LootTableType类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个LootTableType值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个LootTableType
     * @param b 被复制的LootTableType值
     */
    constructor(b: LootTableType) : super(b)

    companion object {
        val data = CompoundData("LootTableType","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(LootTableTypeData::class.java)
        }
    }
}

class LootTableTypeConcrete: MCFPPValue<String>, LootTableType{

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

    constructor(id: LootTableType, value: String) : super(id){
        this.value = value
    }

    constructor(id: LootTableTypeConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): LootTableTypeConcrete {
        return LootTableTypeConcrete(this)
    }

    override fun getTempVar(): LootTableTypeConcrete {
        return LootTableTypeConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return LootTableType(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("LootTableType","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(LootTableTypeConcreteData::class.java)
        }
    }
    
}        
