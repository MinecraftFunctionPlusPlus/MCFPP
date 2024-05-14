package top.mcfpp.lang

import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import net.querz.nbt.tag.Tag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import top.mcfpp.util.LogProcessor
import java.util.*
import kotlin.collections.HashMap


/**
 * 目标选择器（Target Selector）可在无需指定确切的玩家名称或UUID的情况下在命令中选择任意玩家与实体。目标选择器变量可以选择一个或多个实体，目标选择器参数可以根据特定条件筛选目标。
 *
 * 在mcfpp中你可以直接让目标选择器作为命令函数的参数，也可以让目标选择器选择实体，得到一个实体或者一个实体列表，之后再对它们进行操作
 *
 * 在构造一个目标选择器示例的时候，目标选择器的类型是必然确定的，因此只有用于构造确定变量的构造函数
 *
 * @see Entity
 */
class Selector : NBTBasedData<ListTag<StringTag>>() {
//
//    override var javaValue: ListTag<StringTag>? = null
//    override var type: MCFPPType = MCFPPBaseType.Selector
//
//    var text: String? = null
//
//    /**
//     * 目标选择器的类型
//     */
//    var selectorType: SelectorType = SelectorType.ALL_ENTITIES
//
//    /**
//     * 目标选择器的参数
//     */
//    var selectorArgs = HashMap<String, Any>()
//
//    /**
//     * 是否选择的单个实体
//     */
//    var singleEntity = false
//
//    /**
//     * 创建一个list类型的变量。它的mc名和变量所在的域容器有关。
//     *
//     * @param identifier 标识符。默认为
//     */
//    constructor(
//        curr: FieldContainer,
//        identifier: String = UUID.randomUUID().toString()
//    ) : this(curr.prefix + identifier) {
//        this.identifier = identifier
//    }
//
//    /**
//     * 创建一个list值。它的标识符和mc名相同。
//     * @param identifier identifier
//     */
//    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)
//
//    /**
//     * 创建一个固定的list
//     *
//     * @param identifier 标识符
//     * @param curr 域容器
//     * @param type 类型
//     */
//    constructor(
//        curr: FieldContainer,
//        type: SelectorType,
//        identifier: String = UUID.randomUUID().toString()
//    ) : super(curr.prefix + identifier) {
//        isConcrete = true
//        val value = ListTag(StringTag::class.java)
//        value.add(StringTag("@${toSelectorTypeString(type)}"))
//        this.javaValue = value
//    }
//
//    /**
//     * 创建一个固定的list。它的标识符和mc名一致/
//     * @param identifier 标识符。如不指定，则为随机uuid
//     *
//     */
//    constructor(type: SelectorType, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
//        isConcrete = true
//        val value = ListTag(StringTag::class.java)
//        value.add(StringTag("@${toSelectorTypeString(type)}"))
//        this.javaValue = value
//    }
//
//    /**
//     * 复制一个list
//     * @param b 被复制的list值
//     */
//    constructor(b: Selector) : super(b)
//
//
//    /**
//     * 将b中的值赋值给此变量
//     * @param b 变量的对象
//     */
//    override fun assign(b: Var<*>?) {
//        if(b is Selector){
//            assignCommand(b)
//        }else{
//            throw VariableConverseException()
//        }
//    }
//
//    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
//        TODO()
//    }
//
//    override fun getMemberFunction(
//        key: String,
//        readOnlyParams: List<MCFPPType>,
//        normalParams: List<MCFPPType>,
//        accessModifier: Member.AccessModifier
//    ): Pair<Function, Boolean> {
//        TODO("Not yet implemented")
//    }
//
//    override fun cast(type: MCFPPType): Var<*> {
//        return when(type){
//            MCFPPBaseType.Selector -> this
//            MCFPPNBTType.NBT -> NBTBasedData(javaValue!!)
//            else -> throw VariableConverseException()
//        }
//    }
//
//    override fun createTempVar(): Var<*> = Selector()
//    override fun createTempVar(value: Tag<*>): Var<*> {
//        val re = Selector(this.selectorType)
//        re.javaValue = this.javaValue
//        re.selectorArgs = this.selectorArgs
//        return re
//    }
//
//    companion object {
//
//        val data = CompoundData("selector","mcfpp")
//
//        fun getSelectorType(char: Char): SelectorType{
//            return when(char){
//                'a' -> SelectorType.ALL_PLAYERS
//                'e' -> SelectorType.ALL_ENTITIES
//                'p' -> SelectorType.NEAREST_PLAYER
//                'r' -> SelectorType.RANDOM_PLAYER
//                's' -> SelectorType.SELF
//                else -> {
//                    LogProcessor.error("Invalid selector type: @$char")
//                    SelectorType.ALL_ENTITIES
//                }
//            }
//        }
//
//        fun toSelectorTypeString(type: SelectorType): Char{
//            return when(type){
//                SelectorType.ALL_PLAYERS -> 'a'
//                SelectorType.ALL_ENTITIES -> 'e'
//                SelectorType.NEAREST_PLAYER -> 'p'
//                SelectorType.RANDOM_PLAYER -> 'r'
//                SelectorType.SELF -> 's'
//            }
//        }
//
//        enum class SelectorType {
//            ALL_PLAYERS, ALL_ENTITIES, NEAREST_PLAYER, RANDOM_PLAYER, SELF
//        }
//    }
}