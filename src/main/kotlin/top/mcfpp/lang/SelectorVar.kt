package top.mcfpp.lang

import net.querz.nbt.tag.StringTag
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntitySelector.Companion.SelectorType
import top.mcfpp.mni.SelectorData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*


/**
 * 目标选择器（Target Selector）可在无需指定确切的玩家名称或UUID的情况下在命令中选择任意玩家与实体。目标选择器变量可以选择一个或多个实体，
 * 目标选择器参数可以根据特定条件筛选目标。
 *
 * selector拥有一个可选泛型参数limit，用于限制目标选择器选择的实体数量。例如selector<1>将会选择一个实体。
 *
 * 在mcfpp中你可以直接让目标选择器作为命令函数的参数，也可以让目标选择器选择实体，得到一个实体或者一个实体列表，之后再对它们进行操作
 *
 * 在构造一个目标选择器示例的时候，目标选择器的类型是必然确定的，因此只有用于构造确定变量的构造函数
 *
 * @see EntityVar
 */
open class SelectorVar : NBTBasedData<StringTag> {

    override var type: MCFPPType = MCFPPBaseType.Selector

    open var value: EntitySelector

    var text: String? = null

    /**
     * 创建一个目标选择器类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        selectorType: SelectorType,
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(selectorType, curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个目标选择器。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(selectorType: SelectorType, identifier: String = UUID.randomUUID().toString()) : super(identifier){
        value = EntitySelector(selectorType)
    }

    /**
     * 复制一个目标选择器
     * @param b 被复制的目标选择器值
     */
    constructor(b: SelectorVar) : super(b){
        this.value = b.value
    }

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun doAssign(b: Var<*>): SelectorVar {
        when (b) {
            is SelectorVarConcrete -> {
                replacedBy(SelectorVarConcrete(this, b.value))
            }

            is SelectorVar -> {
                assignCommand(b)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
            }
        }
        return this
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return Pair(data.getVar(key), false)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.getFunction(key, readOnlyParams, normalParams) to true
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        val re = super.explicitCast(type)
        if(!re.isError) return re
        return when(type){
            MCFPPNBTType.NBT -> NBTBasedData(this)
            else -> re
        }
    }

    override fun clone(): SelectorVar {
        return SelectorVar(this)
    }


    companion object {

        val data = CompoundData("selector","mcfpp")

        fun getSelectorType(char: Char): SelectorType{
            return when(char){
                'a' -> SelectorType.ALL_PLAYERS
                'e' -> SelectorType.ALL_ENTITIES
                'p' -> SelectorType.NEAREST_PLAYER
                'r' -> SelectorType.RANDOM_PLAYER
                's' -> SelectorType.SELF
                else -> {
                    LogProcessor.error("Invalid selector type: @$char")
                    SelectorType.ALL_ENTITIES
                }
            }
        }

        fun toSelectorTypeString(type: SelectorType): Char{
            return when(type){
                SelectorType.ALL_PLAYERS -> 'a'
                SelectorType.ALL_ENTITIES -> 'e'
                SelectorType.NEAREST_PLAYER -> 'p'
                SelectorType.RANDOM_PLAYER -> 'r'
                SelectorType.SELF -> 's'
                SelectorType.NEAREST_ENTITY -> 'n'
            }
        }

    }
}

class SelectorVarConcrete : MCFPPValue<EntitySelector>, SelectorVar{

    override var value: EntitySelector

    override fun defaultValue(): EntitySelector {
        return EntitySelector(SelectorType.ALL_ENTITIES)
    }

    /**
     * 创建一个固定的目标选择器
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        value: EntitySelector,
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(value.selectorType, curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的目标选择器。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: EntitySelector, identifier: String = UUID.randomUUID().toString()) : super(value.selectorType, identifier) {
        this.value = value
    }

    constructor(v: SelectorVar, value: EntitySelector) : super(v){
        this.value = value
    }

    override fun doAssign(b: Var<*>): SelectorVarConcrete {
        when (b) {
            is SelectorVarConcrete -> {
                this.value = b.value
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
            }
        }
        return this
    }

    override fun clone(): SelectorVar = SelectorVarConcrete(this, value.clone())

    override fun getTempVar(): SelectorVar = SelectorVarConcrete(value.clone(), identifier)

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return Pair(SelectorVar.data.getVar(key), false)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return SelectorVar.data.getFunction(key, readOnlyParams, normalParams) to true
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(StringTag(this.value.toString()), this.identifier).toDynamic(false)
        val v = SelectorVar(this)
        if(replace){
            replacedBy(v)
        }
        return v
    }

    companion object {
        val data = CompoundData("selector","mcfpp.lang")

        init {
            data.extends(MCAny.data)
            data.getNativeFunctionFromClass(SelectorData::class.java)
        }

    }
}