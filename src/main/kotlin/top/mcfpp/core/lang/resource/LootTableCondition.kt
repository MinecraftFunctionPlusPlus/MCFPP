package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.LootTableConditionConcreteData
import top.mcfpp.mni.resource.LootTableConditionData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class LootTableCondition: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.LootTableCondition

    /**
     * 创建一个LootTableCondition类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个LootTableCondition值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个LootTableCondition
     * @param b 被复制的LootTableCondition值
     */
    constructor(b: LootTableCondition) : super(b)

    companion object {
        val data = CompoundData("LootTableCondition","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(LootTableConditionData::class.java)
        }
    }
}

class LootTableConditionConcrete: MCFPPValue<String>, LootTableCondition{

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

    constructor(id: LootTableCondition, value: String) : super(id){
        this.value = value
    }

    constructor(id: LootTableConditionConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): LootTableConditionConcrete {
        return LootTableConditionConcrete(this)
    }

    override fun getTempVar(): LootTableConditionConcrete {
        return LootTableConditionConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return LootTableCondition(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("LootTableCondition","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(LootTableConditionConcreteData::class.java)
        }
    }
    
}        
