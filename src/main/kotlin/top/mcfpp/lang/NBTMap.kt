package top.mcfpp.lang

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.Tag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.*
import top.mcfpp.lib.CompoundData
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.function.NativeFunction
import java.util.*
import kotlin.reflect.jvm.javaMethod

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

    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPNBTType.Map -> this
            MCFPPBaseType.Any -> MCAny(this)
            else -> throw VariableConverseException()
        }
    }

    override fun createTempVar(): Var<*> = NBTMap()
    override fun createTempVar(value: Tag<*>): Var<*> = NBTMap(value as CompoundTag)

    companion object{
        val data = CompoundData("map","mcfpp")

        init {
            data.initialize()
            data.field.addFunction(
                NativeFunction("remove" ,NBTMapData(), MCFPPBaseType.Void,"mcfpp")
                    .appendReadOnlyParam(MCFPPBaseType.String,"key"),
                false
            )
            data.field.addFunction(
                NativeFunction("containsKey", NBTMapData(), MCFPPBaseType.Bool,"mcfpp")
                    .appendReadOnlyParam(MCFPPBaseType.String,"key"),
                false
            )
            data.field.addFunction(
                NativeFunction("containsValue", NBTMapData(), MCFPPBaseType.Bool,"mcfpp")
                    .appendReadOnlyParam(MCFPPNBTType.NBT,"value"),
                false
            )
            data.field.addFunction(
                NativeFunction("isEmpty", NBTMapData(), MCFPPBaseType.Bool,"mcfpp"),
                false
            )
            /* TODO
            data.field.addFunction(
                NativeFunction("getKeys" ,NBTMapData(), MCFPPNBTType.BaseList,"mcfpp"),
                false
            )
            data.field.addFunction(
                NativeFunction("getValues" ,NBTMapData(), MCFPPNBTType.BaseList,"mcfpp"),
                false
            )
             */
            data.field.addFunction(
                NativeFunction("merge", NBTMapData(), MCFPPBaseType.Void,"mcfpp")
                    .appendReadOnlyParam(MCFPPNBTType.Map,"m"),
                false
            )
            data.field.addFunction(
                NativeFunction("size", NBTMapData(), MCFPPBaseType.Int,"mcfpp"),
                false
            )
        }
    }
}