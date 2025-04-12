package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.LiquidTagConcreteData
import top.mcfpp.mni.resource.LiquidTagData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class LiquidTag: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.LiquidTag

    /**
     * 创建一个LiquidTag类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个LiquidTag值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个LiquidTag
     * @param b 被复制的LiquidTag值
     */
    constructor(b: LiquidTag) : super(b)

    companion object {
        val data = CompoundData("LiquidTag","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(LiquidTagData::class.java)
        }
    }
}

class LiquidTagConcrete: MCFPPValue<String>, LiquidTag{

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

    constructor(id: LiquidTag, value: String) : super(id){
        this.value = value
    }

    constructor(id: LiquidTagConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): LiquidTagConcrete {
        return LiquidTagConcrete(this)
    }

    override fun getTempVar(): LiquidTagConcrete {
        return LiquidTagConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return LiquidTag(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("LiquidTag","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(LiquidTagConcreteData::class.java)
        }
    }
    
}        
