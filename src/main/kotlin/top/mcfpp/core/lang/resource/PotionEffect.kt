package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.PotionEffectConcreteData
import top.mcfpp.mni.resource.PotionEffectData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class PotionEffect: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.PotionEffect

    /**
     * 创建一个PotionEffect类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个PotionEffect值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个PotionEffect
     * @param b 被复制的PotionEffect值
     */
    constructor(b: PotionEffect) : super(b)

    companion object {
        val data = CompoundData("PotionEffect","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(PotionEffectData::class.java)
        }
    }
}

class PotionEffectConcrete: MCFPPValue<String>, PotionEffect{

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

    constructor(id: PotionEffect, value: String) : super(id){
        this.value = value
    }

    constructor(id: PotionEffectConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): PotionEffectConcrete {
        return PotionEffectConcrete(this)
    }

    override fun getTempVar(): PotionEffectConcrete {
        return PotionEffectConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return PotionEffect(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("PotionEffect","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(PotionEffectConcreteData::class.java)
        }
    }
    
}        
