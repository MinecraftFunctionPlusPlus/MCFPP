package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.SoundConcreteData
import top.mcfpp.mni.resource.SoundData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class Sound: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.Sound

    /**
     * 创建一个Sound类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个Sound值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个Sound
     * @param b 被复制的Sound值
     */
    constructor(b: Sound) : super(b)

    companion object {
        val data = CompoundData("Sound","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(SoundData::class.java)
        }
    }
}

class SoundConcrete: MCFPPValue<String>, Sound{

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

    constructor(id: Sound, value: String) : super(id){
        this.value = value
    }

    constructor(id: SoundConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): SoundConcrete {
        return SoundConcrete(this)
    }

    override fun getTempVar(): SoundConcrete {
        return SoundConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return Sound(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("Sound","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(SoundConcreteData::class.java)
        }
    }
    
}        
