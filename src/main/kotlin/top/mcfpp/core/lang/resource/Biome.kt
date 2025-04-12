package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.BiomeConcreteData
import top.mcfpp.mni.resource.BiomeData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class Biome: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.Biome

    /**
     * 创建一个Biome类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个Biome值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个Biome
     * @param b 被复制的Biome值
     */
    constructor(b: Biome) : super(b)

    companion object {
        val data = CompoundData("Biome","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BiomeData::class.java)
        }
    }
}

class BiomeConcrete: MCFPPValue<String>, Biome{

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

    constructor(id: Biome, value: String) : super(id){
        this.value = value
    }

    constructor(id: BiomeConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): BiomeConcrete {
        return BiomeConcrete(this)
    }

    override fun getTempVar(): BiomeConcrete {
        return BiomeConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return Biome(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("Biome","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BiomeConcreteData::class.java)
        }
    }
    
}        
