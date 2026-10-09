package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.nbt.tags.primitive.*
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil.toJava
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate


/**
 * 一个nbt数据
 *
 * @constructor Create empty Nbt
 */
open class NBTBasedData : Var<NBTBasedData>, Indexable {

    open val nbtType: NBTTypeWithTag get() = top.mcfpp.analysis.StorageAccess.constantEncoding(this)
        ?.let(NBTTypeWithTag::getTagType) ?: NBTTypeWithTag.ANY

    override var type: MCFPPType = MCFPPNBTType.NBT

    /**
     * 创建一个nbt值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: NBTBasedData) : super(b)

    constructor(b: EnumVar): super(b)

    constructor(value: Tag<*>, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        top.mcfpp.analysis.StorageAccess.initializeLiteral(this,
            top.mcfpp.analysis.CompilerValue.Nbt(top.mcfpp.backend.NbtEncoding.snbt(value)))
    }

    constructor(curr: FieldContainer, value: Tag<*>, identifier: String = TempPool.getVarIdentify()) :
        this(value, curr.prefix + identifier)

    constructor(data: NBTBasedData, value: Tag<*>) : this(data) {
        top.mcfpp.analysis.StorageAccess.initializeLiteral(this,
            top.mcfpp.analysis.CompilerValue.Nbt(top.mcfpp.backend.NbtEncoding.snbt(value)))
    }

    open val value: Tag<*> get() = top.mcfpp.analysis.StorageAccess.constantEncoding(this)
        ?: error("NBT value has no complete compile-time payload")

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun doAssignedBy(b: Var<*>) : NBTBasedData {
        top.mcfpp.analysis.StorageAccess.write(this, b)
        return this
    }

    @InsertCommand
    protected open fun assignCommand(a: NBTBasedData) : NBTBasedData {
        top.mcfpp.analysis.StorageAccess.write(this, a)
        return this
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun explicitCast(type: MCFPPType): Var<*> {
        return top.mcfpp.analysis.StorageAccess.view(this, type)
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
        return top.mcfpp.analysis.StorageAccess.capture(this) as? NBTBasedData ?: NBTBasedData().apply { isError = true }
    }

    override fun storeToStack() {
        //什么都不用做哦
    }

    override fun getFromStack() {
        //什么都不用做哦
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        val v = when (index) {
            is MCInt -> getByIntIndex(index)
            is MCString -> getByStringIndex(index)
            is NBTBasedData -> getByNBTIndex(index)
            else -> throw IllegalArgumentException("Invalid index type ${index.type}")
        }
        return PropertyVar(Property.buildSimpleProperty(v), v,this)
    }

    protected fun getByNBTIndex(index: NBTBasedData): NBTBasedData {
        if(nbtType != NBTTypeWithTag.LIST && nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
            return NBTBasedData().apply { isError = true }
        }
        if(index.nbtType != NBTTypeWithTag.COMPOUND && index.nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
            return NBTBasedData().apply { isError = true }
        }
        val captured = top.mcfpp.analysis.StorageAccess.capture(index)
        if (captured.isError || captured !is NBTBasedData) return NBTBasedData().apply { isError = true }
        val root = top.mcfpp.analysis.StorageAccess.ensure(this)
        if (root.data.facts.read(root.place)?.state != top.mcfpp.analysis.ValueState.INITIALIZED) {
            LogProcessor.error("Cannot access an uninitialized NBT value")
            return NBTBasedData().apply { isError = true }
        }
        val binding = root.copy(place = root.place.unknownIndex(), path = root.path.nbtIndex(captured))
        root.data.facts.refine(binding.place, top.mcfpp.analysis.ValueFacts(
            top.mcfpp.analysis.TypeKnowledge.Exact(MCFPPNBTType.NBT.typeId), top.mcfpp.analysis.ValueKnowledge.Unknown))
        return (top.mcfpp.analysis.StorageAccess.adapter(MCFPPNBTType.NBT, TempPool.getVarIdentify(), binding) as NBTBasedData)
            .apply { parent = this@NBTBasedData }
    }

    protected fun getByStringIndex(index: MCString): NBTBasedData {
        if(nbtType != NBTTypeWithTag.COMPOUND && nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        return top.mcfpp.analysis.StorageAccess.element(this, index, MCFPPNBTType.NBT) as? NBTBasedData ?: NBTBasedData().apply { isError = true }
    }

    protected open fun getByIntIndex(index: MCInt): NBTBasedData {
        if(nbtType != NBTTypeWithTag.LIST && nbtType != NBTTypeWithTag.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        return top.mcfpp.analysis.StorageAccess.element(this, index, MCFPPNBTType.NBT) as? NBTBasedData ?: NBTBasedData().apply { isError = true }
    }

    fun toJson(): NBTBasedData {
        LogProcessor.error("NBT to JSON conversion has no supported backend encoding")
        return NBTBasedData().apply { isError = true }
    }


    override fun toNBTVar(): NBTBasedData {
        return this
    }

    fun simpleIndex(index: String): NBTBasedData{
        return top.mcfpp.analysis.StorageAccess.element(this,
            MCString(StringTag(index)), MCFPPNBTType.NBT) as? NBTBasedData ?: NBTBasedData().apply { isError = true }
    }

    //TODO 逻辑待优化。这里的处理不是很优雅
    companion object {

        /**
         * 获取一个NBT列表的MCFPP类型
         */
        fun ListTag.getListType(): MCFPPListType {
            if(this.size != 0){
                val t = NBTTypeWithTag.getTagType(this[0])
                if(t == NBTTypeWithTag.LIST) {
                    val elementType = (this[0] as ListTag).getListType()
                    for (i in this.drop(1)) {
                        if (elementType != (i as ListTag).getListType()) {
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
                val listType = (values.first() as ListTag).getListType()
                for (value in values.drop(1)){
                    //并不全是列表
                    if(NBTTypeWithTag.getTagType(value) != NBTTypeWithTag.LIST){
                        return MCFPPCompoundType(MCFPPNBTType.NBT)
                    }
                    if((value as ListTag).getListType() != listType){
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
                    BYTE -> MCFPPNBTType.Byte
                    BOOL -> MCFPPBaseType.Bool
                    SHORT -> MCFPPNBTType.Short
                    INT -> MCFPPBaseType.Int
                    LONG -> MCFPPNBTType.Long
                    FLOAT -> MCFPPBaseType.Float
                    DOUBLE -> MCFPPNBTType.Double
                    STRING -> MCFPPBaseType.String
                    BYTE_ARRAY -> MCFPPNBTType.ByteArray
                    INT_ARRAY -> MCFPPNBTType.IntArray
                    LONG_ARRAY -> MCFPPNBTType.LongArray
                    COMPOUND -> MCFPPNBTType.NBT
                    LIST -> MCFPPListType(MCFPPNBTType.NBT)
                    ANY -> MCFPPBaseType.Any
                }
            }

            fun toTagType(): Class<out Tag<*>>{
                return when(this){
                    BYTE -> ByteTag::class.java
                    BOOL -> ByteTag::class.java
                    SHORT -> ShortTag::class.java
                    INT -> IntTag::class.java
                    LONG -> LongTag::class.java
                    FLOAT -> FloatTag::class.java
                    DOUBLE -> DoubleTag::class.java
                    STRING -> StringTag::class.java
                    BYTE_ARRAY -> ByteArrayTag::class.java
                    INT_ARRAY -> IntArrayTag::class.java
                    LONG_ARRAY -> LongArrayTag::class.java
                    COMPOUND -> CompoundTag::class.java
                    LIST -> ListTag::class.java
                    ANY -> Tag::class.java
                }
            }

            companion object{
                fun getTagType(tag: Tag<*>): NBTTypeWithTag {
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
                        is ListTag -> LIST
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
