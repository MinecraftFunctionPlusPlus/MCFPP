package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.VillagerProfessionConcreteData
import top.mcfpp.mni.resource.VillagerProfessionData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class VillagerProfession: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.VillagerProfession

    /**
     * 创建一个VillagerProfession类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个VillagerProfession值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个VillagerProfession
     * @param b 被复制的VillagerProfession值
     */
    constructor(b: VillagerProfession) : super(b)

    companion object {
        val data = CompoundData("VillagerProfession","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(VillagerProfessionData::class.java)
        }
    }
}

class VillagerProfessionConcrete: MCFPPValue<String>, VillagerProfession{

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

    constructor(id: VillagerProfession, value: String) : super(id){
        this.value = value
    }

    constructor(id: VillagerProfessionConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): VillagerProfessionConcrete {
        return VillagerProfessionConcrete(this)
    }

    override fun getTempVar(): VillagerProfessionConcrete {
        return VillagerProfessionConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return VillagerProfession(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("VillagerProfession","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(VillagerProfessionConcreteData::class.java)
        }
    }
    
}        
