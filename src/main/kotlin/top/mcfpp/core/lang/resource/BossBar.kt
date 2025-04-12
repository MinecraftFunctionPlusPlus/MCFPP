package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.BossBarConcreteData
import top.mcfpp.mni.resource.BossBarData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class BossBar: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.BossBar

    /**
     * 创建一个BossBar类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个BossBar值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个BossBar
     * @param b 被复制的BossBar值
     */
    constructor(b: BossBar) : super(b)

    companion object {
        val data = CompoundData("BossBar","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BossBarData::class.java)
        }
    }
}

class BossBarConcrete: MCFPPValue<String>, BossBar{

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

    constructor(id: BossBar, value: String) : super(id){
        this.value = value
    }

    constructor(id: BossBarConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): BossBarConcrete {
        return BossBarConcrete(this)
    }

    override fun getTempVar(): BossBarConcrete {
        return BossBarConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return BossBar(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("BossBar","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BossBarConcreteData::class.java)
        }
    }
    
}        
