package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.RecipeSerializerConcreteData
import top.mcfpp.mni.resource.RecipeSerializerData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class RecipeSerializer: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.RecipeSerializer

    /**
     * 创建一个RecipeSerializer类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个RecipeSerializer值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个RecipeSerializer
     * @param b 被复制的RecipeSerializer值
     */
    constructor(b: RecipeSerializer) : super(b)

    companion object {
        val data = CompoundData("RecipeSerializer","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(RecipeSerializerData::class.java)
        }
    }
}

class RecipeSerializerConcrete: MCFPPValue<String>, RecipeSerializer{

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

    constructor(id: RecipeSerializer, value: String) : super(id){
        this.value = value
    }

    constructor(id: RecipeSerializerConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): RecipeSerializerConcrete {
        return RecipeSerializerConcrete(this)
    }

    override fun getTempVar(): RecipeSerializerConcrete {
        return RecipeSerializerConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return RecipeSerializer(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("RecipeSerializer","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(RecipeSerializerConcreteData::class.java)
        }
    }
    
}        
