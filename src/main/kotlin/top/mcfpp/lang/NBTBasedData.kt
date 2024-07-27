package top.mcfpp.lang

import net.querz.nbt.io.SNBTUtil
import java.util.*
import net.querz.nbt.tag.*
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.*
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.NBTSource
import top.mcfpp.lib.Storage
import top.mcfpp.lib.StorageSource
import top.mcfpp.mni.NBTBasedDataData
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
import top.mcfpp.util.AnyTag
import top.mcfpp.util.LogProcessor
import kotlin.collections.ArrayList


/**
 * 一个nbt数据
 *
 * @constructor Create empty Nbt
 */
open class NBTBasedData<T : Tag<*>> : Var<T>, Indexable<NBTBasedData<*>>{

    open var nbtType: NBTTypeWithTag = NBTTypeWithTag.ANY

    override var type: MCFPPType = MCFPPNBTType.NBT

    /**
     * 创建一个nbt类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个nbt值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: NBTBasedData<T>) : super(b)

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var<*>) : NBTBasedData<T> {
        hasAssigned = true
        return when(b){
            is NBTBasedData<*> -> assignCommand(b as NBTBasedData<T>)
            is MCIntConcrete -> assignCommand(NBTBasedDataConcrete(IntTag(b.value)) as NBTBasedData<T>)
            else -> throw VariableConverseException()
        }
    }

    @InsertCommand
    protected open fun assignCommand(a: NBTBasedData<T>) : NBTBasedData<T>{
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                b as NBTBasedDataConcrete
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetValue(nbtPath, b.value as Tag<*>))
                if(final.last().isMacro){
                    Function.addCommand(Commands.buildMacroCommand(final.last()))
                }else{
                    Function.addCommand(final.last())
                }
                NBTBasedData(this)
            },
            ifThisIsClassMemberAndAIsNotConcrete = {b, final ->
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetFrom(nbtPath, b.nbtPath))
                if(final.last().isMacro){
                    Function.addCommand(Commands.buildMacroCommand(final.last()))
                }else{
                    Function.addCommand(final.last())
                }
                NBTBasedData(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b, _ ->
                NBTBasedDataConcrete(this, (b as NBTBasedDataConcrete<T>).value)
            },
            ifThisIsNormalVarAndAIsClassMember = {b, final ->
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetFrom(nbtPath, b.nbtPath))
                if(final.last().isMacro){
                    Function.addCommand(Commands.buildMacroCommand(final.last()))
                }else{
                    Function.addCommand(final.last())
                }
                NBTBasedData(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b, _ ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                NBTBasedData(this)
            }) as NBTBasedData<T>
        }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPNBTType.NBT -> this
            is MCFPPListType -> this
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> throw VariableConverseException()
        }
    }

    override fun clone(): NBTBasedData<T> {
        return NBTBasedData(this)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): Var<*> {
        val temp = NBTBasedData<T>()
        return temp.assignCommand(this)
    }

    override fun storeToStack() {
        //什么都不用做哦
        return
    }

    override fun getFromStack() {
        //什么都不用做哦
        return
    }

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
     * @param readOnlyParams 成员方法的只读参数
     * @param normalParams 成员方法的普通参数
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

    override fun getByIndex(index: Var<*>): NBTBasedData<*> {
        return when(index){
            is MCInt -> getByIntIndex(index)
            is MCString -> getByStringIndex(index)
            is NBTBasedData<*> -> getByNBTIndex(index as NBTBasedData<Tag<*>>)
            else -> throw IllegalArgumentException("Invalid index type ${index.type}")
        }
    }

    protected fun getByNBTIndex(index: NBTBasedData<Tag<*>>): NBTBasedData<*>{
        if(nbtType != NBTTypeWithTag.LIST && nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        if(index.nbtType != NBTTypeWithTag.COMPOUND && index.nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        val re = NBTBasedData(this)
        re.nbtPath.nbtIndex(index)
        return re
    }

    protected fun getByStringIndex(index: MCString): NBTBasedData<*> {
        if(nbtType != NBTTypeWithTag.COMPOUND && nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        val re = NBTBasedData(this)
        re.nbtPath.memberIndex(index)
        return re
    }

    open protected fun getByIntIndex(index: MCInt): NBTBasedData<*> {
        if(nbtType != NBTTypeWithTag.LIST && nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        val re = NBTBasedData(this)
        re.nbtPath.intIndex(index)
        return re
    }

    fun toJson(): NBTBasedData<*> {
        when(nbtType){
            NBTTypeWithTag.INT_ARRAY -> {
                TODO()
            }
            else -> TODO()
        }
    }


    override fun toNBTVar(): NBTBasedData<*> {
        return this
    }

    //TODO 逻辑待优化。这里的处理不是很优雅
    companion object {

        /**
         * 获取一个NBT列表的MCFPP类型
         */
        fun ListTag<*>.getListType(): MCFPPListType{
            if(this.size() != 0){
                val t = NBTTypeWithTag.getTagType(this[0])
                if(t == NBTTypeWithTag.LIST) {
                    val elementType = (this[0] as ListTag<*>).getListType()
                    for (i in this.drop(1)) {
                        if (elementType != (i as ListTag<*>).getListType()) {
                            return MCFPPListType(MCFPPNBTType.NBT)
                        }
                    }
                    return MCFPPListType(elementType)
                    //是复合标签
                }
                if(t == NBTTypeWithTag.COMPOUND){
                    val elementType = (this[0] as CompoundTag).getCompoundType()
                    for (i in this.drop(1)) {
                        if (elementType != (i as CompoundTag).getCompoundType()) {
                            return MCFPPListType(MCFPPNBTType.NBT)
                        }
                    }
                    return MCFPPListType(elementType)
                }
                return MCFPPListType(t.getMCFPPType())
            }
            //列表为空
            return MCFPPListType(MCFPPBaseType.Any)
        }

        /**
         * 获取一个NBT复合标签的MCFPP类型
         */
        fun CompoundTag.getCompoundType(): MCFPPCompoundType{
            val values = this.values()
            //复合标签为空，用any标记
            if(values.isEmpty()){
                return MCFPPCompoundType(MCFPPBaseType.Any)
            }
            //判断所有元素是不是和第一个元素一样的
            val t = NBTTypeWithTag.getTagType(values.first())
            //如果是列表
            if(t == NBTTypeWithTag.LIST){
                val listType = (values.first() as ListTag<*>).getListType()
                for (value in values.drop(1)){
                    //并不全是列表
                    if(NBTTypeWithTag.getTagType(value) != NBTTypeWithTag.LIST){
                        return MCFPPCompoundType(MCFPPNBTType.NBT)
                    }
                    if((value as ListTag<*>).getListType() != listType){
                        return MCFPPCompoundType(MCFPPListType(MCFPPNBTType.NBT))
                    }
                }
            }
            //如果是复合标签
            if(t == NBTTypeWithTag.COMPOUND){
                val compoundType = (values.first() as CompoundTag).getCompoundType()
                for (value in values.drop(1)){
                    //并不全是复合标签
                    if(NBTTypeWithTag.getTagType(value) != NBTTypeWithTag.COMPOUND){
                        return MCFPPCompoundType(MCFPPNBTType.NBT)
                    }
                    if((value as CompoundTag).getCompoundType() != compoundType){
                        return MCFPPCompoundType(MCFPPCompoundType(MCFPPNBTType.NBT))
                    }
                }
            }
            //不是复杂类型
            for (value in values.drop(1)){
                if(NBTTypeWithTag.getTagType(value) != t){
                    return MCFPPCompoundType(MCFPPNBTType.NBT)
                }
            }
            return MCFPPCompoundType(t.getMCFPPType())
        }

        val data = CompoundData("nbt","mcfpp")

        init {
            data.extends(MCAny.data)
            data.getNativeFunctionFromClass(NBTBasedDataData::class.java)
        }

        enum class NBTTypeWithTag(val type: NBTType){
            BYTE(NBTType.VALUE),
            BOOL(NBTType.VALUE),
            SHORT(NBTType.VALUE),
            INT(NBTType.VALUE),
            LONG(NBTType.VALUE),
            FLOAT(NBTType.VALUE),
            DOUBLE(NBTType.VALUE),
            STRING(NBTType.VALUE),
            BYTE_ARRAY(NBTType.ARRAY),
            INT_ARRAY(NBTType.ARRAY),
            LONG_ARRAY(NBTType.ARRAY),
            COMPOUND(NBTType.COMPOUND),
            LIST(NBTType.LIST),
            ANY(NBTType.ANY);

            fun getMCFPPType(): MCFPPType{
                return when(this){
                    BYTE -> MCFPPBaseType.Int
                    BOOL -> MCFPPBaseType.Bool
                    SHORT -> MCFPPBaseType.Int
                    INT -> MCFPPBaseType.Int
                    LONG -> MCFPPBaseType.Int
                    FLOAT -> MCFPPBaseType.Float
                    DOUBLE -> MCFPPBaseType.Float
                    STRING -> MCFPPBaseType.String
                    BYTE_ARRAY -> MCFPPBaseType.Any
                    INT_ARRAY -> MCFPPBaseType.Any
                    LONG_ARRAY -> MCFPPBaseType.Any
                    COMPOUND -> MCFPPNBTType.NBT
                    LIST -> {
                        throw Exception("不应该运行到这个地方吧")
                    }
                    ANY -> MCFPPBaseType.Any
                }
            }

            companion object{
                fun getTagType(tag: Tag<*>): NBTTypeWithTag{
                    return when(tag){
                        is ByteTag -> BYTE
                        is ShortTag -> SHORT
                        is IntTag -> INT
                        is LongTag -> LONG
                        is FloatTag -> FLOAT
                        is DoubleTag -> DOUBLE
                        is StringTag -> STRING
                        is ByteArrayTag -> BYTE_ARRAY
                        is IntArrayTag -> INT_ARRAY
                        is LongArrayTag -> LONG_ARRAY
                        is CompoundTag -> COMPOUND
                        is ListTag<*> -> LIST
                        else -> ANY
                    }
                }
            }
        }

        enum class NBTType {
            COMPOUND, LIST, VALUE, ANY, ARRAY
        }
    }
}

class NBTBasedDataConcrete<T: Tag<*>> : NBTBasedData<T>, MCFPPValue<T> {

    override var value : T

    /**
     * 创建一个固定的int
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: T,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
        //记录nbt字面量类型
        nbtType = NBTBasedData.Companion.NBTTypeWithTag.getTagType(value)
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: T, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
        //记录nbt字面量类型
        nbtType = NBTBasedData.Companion.NBTTypeWithTag.getTagType(value)
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(data: NBTBasedData<T>, value: T) : super(data) {
        this.value = value
    }

    constructor(v: NBTBasedDataConcrete<T>) : super(v){
        this.value = v.value
    }

    override fun clone(): NBTBasedDataConcrete<T> {
        return NBTBasedDataConcrete(this)
    }

    override fun getTempVar(): Var<*> {
        return NBTBasedDataConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parent != null) {
            val cmd = when(parent){
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
                }
                else -> TODO()
            }
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build(
                "data modify entity @s data.${identifier} set value ${SNBTUtil.toSNBT(value)}")
            )
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value ${SNBTUtil.toSNBT(value)}")
            Function.addCommand(cmd)
        }
        val re = NBTBasedData(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    override fun toString(): String {
        return "[$type,value=${SNBTUtil.toSNBT(value)}]"
    }

    companion object {
        val data = CompoundData("nbt","mcfpp")

        init {
            data.extends(MCAnyConcrete.data)
            data.getNativeFunctionFromClass(NBTBasedDataData::class.java)
        }
    }
}