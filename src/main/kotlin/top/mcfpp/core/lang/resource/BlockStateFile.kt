package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.BlockStateFileConcreteData
import top.mcfpp.mni.resource.BlockStateFileData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class BlockStateFile: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.BlockStateFile

    /**
     * 创建一个BlockStateFile类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个BlockStateFile值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个BlockStateFile
     * @param b 被复制的BlockStateFile值
     */
    constructor(b: BlockStateFile) : super(b)

    companion object {
        val data = CompoundData("BlockStateFile","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BlockStateFileData::class.java)
        }
    }
}

class BlockStateFileConcrete: MCFPPValue<String>, BlockStateFile{

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

    constructor(id: BlockStateFile, value: String) : super(id){
        this.value = value
    }

    constructor(id: BlockStateFileConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): BlockStateFileConcrete {
        return BlockStateFileConcrete(this)
    }

    override fun getTempVar(): BlockStateFileConcrete {
        return BlockStateFileConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return BlockStateFile(this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("BlockStateFile","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(BlockStateFileConcreteData::class.java)
        }
    }
    
}        
