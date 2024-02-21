package top.mcfpp.lang

import net.querz.nbt.tag.Tag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member
import java.util.*

class JsonString : NBTBasedData<Tag<*>>{
    override var javaValue: Tag<*>? = null
    val jsonText : JsonText? = null

    /**
     * 创建一个list类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier

    }

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
        value: Tag<*>,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        isConcrete = true
        this.javaValue = value
    }

    /**
     * 创建一个固定的list。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Tag<*>, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        this.javaValue = value
    }

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: NBTBasedData<Tag<*>>) : super(b)

    override var type: MCFPPType = MCFPPBaseType.JsonText

    override fun assign(b: Var<*>?) {
        if(b is JsonString){
            assignCommand(b)
        }else{
            throw VariableConverseException()
        }
    }

    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.JsonText -> this
            MCFPPNBTType.NBT -> NBT(javaValue!!)
            MCFPPBaseType.String -> TODO()
            else -> throw VariableConverseException()
        }
    }

    override fun createTempVar(): Var<*> = JsonString()

    override fun createTempVar(value: Tag<*>) = JsonString(value)

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        params: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }
}