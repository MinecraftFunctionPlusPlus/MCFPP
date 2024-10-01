package top.mcfpp.core.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.*
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.mni.NBTBasedDataData
import top.mcfpp.model.*
import top.mcfpp.model.accessor.Property
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.NBTUtil.toJava
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*


/**
 * 一个nbt数据
 *
 * @constructor Create empty Nbt
 */
open class NBTBasedData : Var<NBTBasedData>, Indexable{

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
    constructor(b: NBTBasedData) : super(b)

    constructor(b: EnumVar): super(b)

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun doAssignedBy(b: Var<*>) : NBTBasedData {
        return when (b) {
            is NBTBasedData -> assignCommand(b)
            is MCFPPValue<*> -> assignCommand(NBTBasedDataConcrete(NBTUtil.toNBT(b)!!) as NBTBasedData)
            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    @InsertCommand
    protected open fun assignCommand(a: NBTBasedData) : NBTBasedData{
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                b as NBTBasedDataConcrete
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetValue(nbtPath, b.value))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
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
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
                NBTBasedData(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b, _ ->
                NBTBasedDataConcrete(this, (b as NBTBasedDataConcrete).value)
            },
            ifThisIsNormalVarAndAIsClassMember = {b, final ->
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetFrom(nbtPath, b.nbtPath))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
                NBTBasedData(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b, _ ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                NBTBasedData(this)
            }
        ) as NBTBasedData
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun explicitCast(type: MCFPPType): Var<*> {
        val re = super.explicitCast(type)
        if(!re.isError) return re
        return when(type){
            MCFPPNBTType.NBT -> this
            else -> re
        }
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun implicitCast(type: MCFPPType): Var<*> {
        val re = super.implicitCast(type)
        if(!re.isError) return re
        return when(type){
            MCFPPNBTType.NBT -> this
            else -> re
        }
    }

    override fun clone(): NBTBasedData {
        return NBTBasedData(this)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): NBTBasedData {
        val temp = NBTBasedData()
        return temp.assignCommand(this)
    }

    override fun storeToStack() {
        //什么都不用做哦
    }

    override fun getFromStack() {
        //什么都不用做哦
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return data.getVar(key) to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.getFunction(key, readOnlyParams, normalParams) to true
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        return PropertyVar(
            Property.buildSimpleProperty (
                when (index) {
                    is MCInt -> getByIntIndex(index)
                    is MCString -> getByStringIndex(index)
                    is NBTBasedData -> getByNBTIndex(index)
                    else -> throw IllegalArgumentException("Invalid index type ${index.type}")
                }
            ),this
        )
    }

    protected fun getByNBTIndex(index: NBTBasedData): NBTBasedData{
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

    protected fun getByStringIndex(index: MCString): NBTBasedData {
        if(nbtType != NBTTypeWithTag.COMPOUND && nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        val re = NBTBasedData(this)
        re.nbtPath.memberIndex(index)
        return re
    }

    open protected fun getByIntIndex(index: MCInt): NBTBasedData {
        if(nbtType != NBTTypeWithTag.LIST && nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        val re = NBTBasedData(this)
        re.nbtPath.intIndex(index)
        return re
    }

    fun toJson(): NBTBasedData {
        when(nbtType){
            NBTTypeWithTag.INT_ARRAY -> {
                TODO()
            }
            else -> TODO()
        }
    }


    override fun toNBTVar(): NBTBasedData {
        return this
    }

    //TODO 逻辑待优化。这里的处理不是很优雅
    companion object {

        /**
         * 获取一个NBT列表的MCFPP类型
         */
        fun ListTag<*>.getListType(): MCFPPListType {
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
        fun CompoundTag.getCompoundType(): MCFPPCompoundType {
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
            data.getNativeFromClass(NBTBasedDataData::class.java)
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

            fun getMCFPPType(): MCFPPType {
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

class NBTBasedDataConcrete : NBTBasedData, MCFPPValue<Tag<*>> {

    override lateinit var value : Tag<*>

    constructor(
        curr: FieldContainer,
        value: Tag<*>,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
        //记录nbt字面量类型
        nbtType = NBTBasedData.Companion.NBTTypeWithTag.getTagType(value)
    }

    constructor(value: Tag<*>, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
        //记录nbt字面量类型
        nbtType = NBTBasedData.Companion.NBTTypeWithTag.getTagType(value)
    }

    constructor(data: NBTBasedData, value: Tag<*>) : super(data) {
        this.value = value
    }

    constructor(v: NBTBasedDataConcrete) : super(v){
        this.value = v.value
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        if(type is MCFPPDataTemplateType && value is CompoundTag){
            return if(type.template.checkCompoundStruct(value as CompoundTag)){
                DataTemplateObjectConcrete(type.template, value as CompoundTag)
            }else{
                buildCastErrorVar(type)
            }
        }
        if((type is MCFPPVectorType)){
            if((value is ListTag<*>) && (type.dimension == (value as ListTag<*>).size())){
                //转换为向量
                val first = (value as ListTag<*>)[0]
                if(first !is IntTag){
                    return buildCastErrorVar(type)
                }
                return VectorVarConcrete((value as ListTag<*>).map { (it as IntTag).asInt() }.toTypedArray())
            }else{
                return buildCastErrorVar(type)
            }
        }
        val t = JavaVar.javaToMC(value.toJava())
        if(t.type == type) return t
        return buildCastErrorVar(type)
    }

    override fun clone(): NBTBasedDataConcrete {
        return NBTBasedDataConcrete(this)
    }

    override fun getTempVar(): NBTBasedDataConcrete {
        return NBTBasedDataConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!, "data modify entity @s data.${identifier} set value ${SNBTUtil.toSNBT(value)}")
            Function.addCommands(cmd)
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value ${SNBTUtil.toSNBT(value)}")
            Function.addCommand(cmd)
        }
        val re = NBTBasedData(this)
        if(replace){
            if(parentTemplate() != null){
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else {
                Function.currFunction.field.putVar(identifier, re, true)
            }
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
            data.getNativeFromClass(NBTBasedDataData::class.java)
        }
    }
}