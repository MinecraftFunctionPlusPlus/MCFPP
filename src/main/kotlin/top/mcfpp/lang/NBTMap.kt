package top.mcfpp.lang

import net.querz.nbt.tag.CompoundTag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.*
import top.mcfpp.mni.NBTListData
import top.mcfpp.mni.NBTMapData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import java.util.*

class NBTMap : NBTDictionary{

    override var type: MCFPPType = MCFPPNBTType.Map
    /**
     * 创建一个list类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr, identifier)

    /**
     * 创建一个list值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPNBTType.Map -> this
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> throw VariableConverseException()
        }
    }

    /*
    override fun createTempVar(): Var<*> = NBTMap()
    override fun createTempVar(value: Tag<*>): Var<*> = NBTMap(value as CompoundTag)

     */

    companion object{
        val data = CompoundData("map","mcfpp.lang")

        init {
            data.initialize()
            data.parent.add(MCAny.data)
            data.getNativeFunctionFromClass(NBTMapData::class.java)
        }
    }
}

class NBTMapConcrete : NBTDictionaryConcrete{

    override var type: MCFPPType = MCFPPNBTType.Map
    /**
     * 创建一个固定的list
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: CompoundTag,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr, value, identifier)

    /**
     * 创建一个固定的list。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: CompoundTag, identifier: String = UUID.randomUUID().toString()) : super(value, identifier)

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: CompoundTag) : super(b)

    constructor(v: NBTMapConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): NBTMapConcrete {
        return NBTMapConcrete(this)
    }
}