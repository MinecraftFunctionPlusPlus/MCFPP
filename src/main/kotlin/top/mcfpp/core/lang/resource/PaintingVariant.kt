package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.PaintingVariantConcreteData
import top.mcfpp.mni.resource.PaintingVariantData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class PaintingVariant: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.PaintingVariant

    /**
     * 创建一个PaintingVariant类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个PaintingVariant值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个PaintingVariant
     * @param b 被复制的PaintingVariant值
     */
    constructor(b: PaintingVariant) : super(b)

    companion object {
        val data = CompoundData("PaintingVariant","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(PaintingVariantData::class.java)
        }
    }
}

class PaintingVariantConcrete: MCFPPValue<String>, PaintingVariant{

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

    constructor(id: PaintingVariant, value: String) : super(id){
        this.value = value
    }

    constructor(id: PaintingVariantConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): PaintingVariantConcrete {
        return PaintingVariantConcrete(this)
    }

    override fun getTempVar(): PaintingVariantConcrete {
        return PaintingVariantConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return PaintingVariant(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("PaintingVariant","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(PaintingVariantConcreteData::class.java)
        }
    }
    
}        
