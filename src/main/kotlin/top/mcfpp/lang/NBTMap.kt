package top.mcfpp.lang

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.Tag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.CompoundData
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.NativeFunction
import java.util.*
import kotlin.reflect.jvm.javaMethod

class NBTMap : NBTDictionary{

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

    override fun cast(type: String): Var {
        return when(type){
            "map" -> this
            "any" -> MCAny(this)
            else -> throw VariableConverseException()
        }
    }

    override fun createTempVar(): Var = NBTMap()
    override fun createTempVar(value: Tag<*>): Var = NBTMap(value as CompoundTag)

    companion object{
        val data = CompoundData("map","mcfpp")

        init {
            data.initialize()
            data.field.addFunction(NativeFunction(NBTMapData::remove.javaMethod!!,"void","mcfpp").appendParam("string","key"),false)
            data.field.addFunction(NativeFunction(NBTMapData::containsKey.javaMethod!!,"bool","mcfpp").appendParam("string","key"),false)
            data.field.addFunction(NativeFunction(NBTMapData::containsValue.javaMethod!!,"bool","mcfpp").appendParam("nbt","value"),false)
            data.field.addFunction(NativeFunction(NBTMapData::isEmpty.javaMethod!!,"bool","mcfpp"),false)
            data.field.addFunction(NativeFunction(NBTMapData::getKeys.javaMethod!!,"list","mcfpp"),false)
            data.field.addFunction(NativeFunction(NBTMapData::getValues.javaMethod!!,"list","mcfpp"),false)
            data.field.addFunction(NativeFunction(NBTMapData::remove.javaMethod!!,"void","mcfpp").appendParam("string","key"),false)
            data.field.addFunction(NativeFunction(NBTMapData::merge.javaMethod!!,"void","mcfpp").appendParam("map","m"),false)
            data.field.addFunction(NativeFunction(NBTMapData::size.javaMethod!!,"int","mcfpp"),false)
        }
    }
}