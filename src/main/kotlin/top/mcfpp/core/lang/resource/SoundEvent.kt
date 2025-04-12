package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.SoundEventConcreteData
import top.mcfpp.mni.resource.SoundEventData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class SoundEvent: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.SoundEvent

    /**
     * 创建一个SoundEvent类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个SoundEvent值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个SoundEvent
     * @param b 被复制的SoundEvent值
     */
    constructor(b: SoundEvent) : super(b)

    companion object {
        val data = CompoundData("SoundEvent","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(SoundEventData::class.java)
        }
    }
}

class SoundEventConcrete: MCFPPValue<String>, SoundEvent{

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

    constructor(id: SoundEvent, value: String) : super(id){
        this.value = value
    }

    constructor(id: SoundEventConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): SoundEventConcrete {
        return SoundEventConcrete(this)
    }

    override fun getTempVar(): SoundEventConcrete {
        return SoundEventConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return SoundEvent(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("SoundEvent","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(SoundEventConcreteData::class.java)
        }
    }
    
}        
