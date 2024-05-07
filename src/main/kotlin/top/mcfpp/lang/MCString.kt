package top.mcfpp.lang

import net.querz.nbt.tag.StringTag
import net.querz.nbt.tag.Tag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.CompoundData
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.function.Function
import top.mcfpp.lib.Member
import java.util.*

/**
 * string表示一个字符串。要声明一个字符串，应该使用string类型，例如string abc = "abc"。
 *
 * string重写了+运算符，因此能够对字符串进行加运算。例如string ps = "abc" + "def"，结果
 *得到ps为”abcdef”。
 *
 * 和MCFPP中所有变量一样，string类型有编译时静态和动态两种方式。类似上文中的声明方式，编译器
 *能发现自己能够跟踪到这个字符串的内容，因此在编译过程中会进行简单的替换。而动态则不同，动态字符
 *串通常是由于将一个jstring转换成string类型而导致的。动态字符串储存在nbt中，以供操作。
 *
 * 得益于宏和data string命令，让字符串的动态操作成为了可能。在1.19.4-的版本中，MCFPP只支持静
 *态的字符串和原始JSON文本功能。
 *
 */
class MCString : NBTAny<StringTag> {

    override var javaValue: StringTag? = null

    override var type: MCFPPType = MCFPPBaseType.String

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
        value: StringTag,
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
    constructor(value: StringTag, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        this.javaValue = value
    }

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: MCString) : super(b)

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var<*>?) {
        hasAssigned = true
        if(b is MCString){
            assignCommand(b)
        }else{
            throw VariableConverseException()
        }
    }

    @Override
    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.String -> this
            MCFPPNBTType.NBT -> NBTBasedData(javaValue!!)
            MCFPPBaseType.Any -> MCAny(this)
            else -> throw VariableConverseException()
        }
    }

    override fun createTempVar(): Var<*> = MCString()

    override fun createTempVar(value: Tag<*>): Var<*> = MCString(value as StringTag)

    companion object {
        val data = CompoundData("string","mcfpp")
    }

}