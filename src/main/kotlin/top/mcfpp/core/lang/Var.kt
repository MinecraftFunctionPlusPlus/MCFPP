package top.mcfpp.core.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.*
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.lib.*
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import top.mcfpp.util.*
import top.mcfpp.util.TextTranslator.translate
import java.io.Serializable
import java.util.*

/**
 * mcfpp所有类型的基类。在mcfpp中，一个变量可以是固定的，也就是mcfpp编译
 * 能知道确切值的变量。例如`int i = 0;`中，编译器可以明确i的值就是
 * 0。另外，还可能有编译器并不知道确切值的变量。例如`int i = e.pos[0]`，
 * 获取了一个实体的x坐标。编译器并不能知道这个实体的坐标会是什么。
 *
 * 对于固定的值，编译器会尽可能计算出他们的值。例如`int i = 6 + 7 + p`，
 * 编译器会提前计算为`int i = 13 + p`，从而减少命令的使用量。
 *
 * 除此之外，变量还有临时变量的区别，对于匿名的变量，编译器一般会默认它为临时
 * 的变量，从而在各种处理上进行优化。当然，匿名变量的声明往往在编译过程中声明。
 * mcfpp本身的语法并不支持匿名变量。
 */
abstract class Var<Self: Var<Self>> : Member, Cloneable, CanSelectMember, Serializable{

    /**
     * 在Minecraft中的标识符
     */
    var name: String

    /**
     * 在mcfpp中的标识符，在域中的键名
     */
    var identifier: String

    private val stackFrameRegex = Regex("^stack_frame\\[\\d+]\$\n")
    /**
     * 变量在栈里面的位置
     */
    var stackIndex: Int = 0
        set(value) {
            field = value
            for (p in nbtPath.pathList){
                if(p is MemberPath && p.value is MCStringConcrete && stackFrameRegex.matches((p.value as MCStringConcrete).value.value)){
                    p.value = MCStringConcrete(StringTag("stack_frame[$stackIndex]"))
                }
            }
        }

    /**
     * 是否是静态的成员
     */
    final override var isStatic = false

    /**
     * 这个变量是否是常量。对应const关键字
     */
    open var isConst = false
    var hasAssigned = false

    /**
     * 是否是临时变量
     */
    var isTemp = false

    open var parent : CanSelectMember? = null

    /**
     * 访问修饰符
     */
    final override var accessModifier: Member.AccessModifier = Member.AccessModifier.PUBLIC

    /**
     * 变量的类型
     */
    open var type: MCFPPType = MCFPPBaseType.Any

    /**
     * 这个变量是否是编译器编译错误的时候生成的用于保证编译器正常运行的变量
     */
    var isError = false

    /**
     * 在mc中的路径
     */
    open lateinit var nbtPath: NBTPath

    /**
     * 在离开作用域后是否会丢失跟踪
     */
    var trackLost = false

    /**
     * 此变量是否可以为空值。仅用于数据模板的成员变量
     */
    val nullable = false

    /**
     * 复制一个变量
     */
    @Suppress("LeakingThis")
    constructor(`var` : Var<*>)  {
        name = `var`.name
        identifier = `var`.identifier
        isStatic = `var`.isStatic
        accessModifier = `var`.accessModifier
        isTemp = `var`.isTemp
        nbtPath = `var`.nbtPath
        stackIndex = `var`.stackIndex
        isConst = `var`.isConst
    }

    /**
     * 创建一个变量。它的标识符和mc名一致
     *
     * @param identifier 变量的标识符。默认为随机的uuid
     */
    @Suppress("LeakingThis")
    constructor(identifier: String = UUID.randomUUID().toString()){
        this.name = identifier
        this.identifier = identifier
        nbtPath = NBTPath(StorageSource("mcfpp:system"))
            .memberIndex(Project.config.rootNamespace)
            .memberIndex("stack_frame[$stackIndex]")
            .memberIndex(identifier)
    }

    /**
     * 获取这个成员的父类，可能不存在
     * @return
     */
    override fun parentClass(): Class? {
        return when (val parent = parent) {
            is ClassPointer -> parent.clazz
            is MCFPPClassType -> parent.cls
            else -> null
        }
    }

    /**
     * 获取这个成员的父结构体，可能不存在
     *
     * @return
     */
    override fun parentTemplate(): DataTemplate? {
        return when (val parent = parent) {
            is DataTemplateObject -> parent.templateType
            is MCFPPDataTemplateType -> parent.template
            else -> null
        }
    }

    /**
     * 将b中的值赋值给此变量。如果b的类型和这个变量的类型不一致，会尝试进行隐式转换。赋值的实际执行过程在[doAssignedBy]中完成
     *
     * 此方法不会修改此变量的值。需要在其后调用[replacedBy]将原来的值覆盖
     *
     * @param b 变量的对象
     */
    fun assignedBy(b: Var<*>): Self {
        var v = b.implicitCast(this.type)
        if(v.isError){
            v = b
        }
        hasAssigned = true
        val re = doAssignedBy(v)
        if(stackIndex != 0) trackLost = true
        return re
    }

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    protected abstract fun doAssignedBy(b: Var<*>) : Self

    /**
     * 判断b变量是否可以赋值给此变量
     */
    abstract fun canAssignedBy(b: Var<*>): Boolean

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    open fun explicitCast(type: MCFPPType): Var<*> {
        if(type == this.type){
            LogProcessor.warn(TextTranslator.REDUNDANT_CAST_WARN.translate(this.type.typeName, type.typeName))
            return this
        }
        return when(type){
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> {
                buildCastErrorVar(type)
            }
        }
    }

    /**
     * 将这个变量隐式转换为一个类型
     */
    open fun implicitCast(type: MCFPPType): Var<*> {
        if(type == this.type){
            return this
        }
        return when(type){
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> {
                buildCastErrorVar(type)
            }
        }
    }

    @Override
    public abstract override fun clone(): Self

    fun clone(pointer: ClassPointer): Self{
        val `var` = this.clone()
        if(pointer.identifier != "this"){
            //不是this指针才需要额外指定引用者
            `var`.parent = pointer
        }
        `var`.nbtPath = NBTPath(EntitySource(SelectorVar(EntitySelector('s'))))
            .memberIndex("data")
            .memberIndex(identifier)
        return `var`
    }

    fun clone(obj: DataTemplateObject): Self{
        val `var` = this.clone()
        if(obj.identifier != "this"){
            `var`.parent = obj
        }
        `var`.nbtPath = obj.nbtPath
        return `var`
    }

    /**
     * @param a 源变量
     * @param ifThisIsClassMemberAndAIsConcrete 如果此变量是类成员，且a是已知的。cmd参数是[Commands.selectRun]生成的访问类成员的命令，需要被续写
     * @param ifThisIsClassMemberAndAIsNotConcrete 如果此变量是成员，且a不是已知的。cmd参数是[Commands.selectRun]生成的访问类成员的命令，需要被续写
     * @param ifThisIsNormalVarAndAIsConcrete 如果此变量不是成员且a是已知的。cmd为空
     * @param ifThisIsNormalVarAndAIsClassMember 如果此变量不是成员且a是成员。cmd参数是[Commands.selectRun]生成的访问类成员的命令，需要被续写
     * @param ifThisIsNormalVarAndAIsNotConcrete 如果此变量不是成员且a也不是。cmd为空
     */
    fun assignCommandLambda(
        a: Var<*>,
        ifThisIsClassMemberAndAIsConcrete: (Var<*>, Array<Command>) -> Var<*>,
        ifThisIsClassMemberAndAIsNotConcrete: (Var<*>, Array<Command>) -> Var<*>,
        ifThisIsNormalVarAndAIsConcrete: (Var<*>, Array<Command>) -> Var<*>,
        ifThisIsNormalVarAndAIsClassMember: (Var<*>, Array<Command>) -> Var<*>,
        ifThisIsNormalVarAndAIsNotConcrete: (Var<*>, Array<Command>) -> Var<*>
    ): Var<*> {
        if (parentClass() != null) {
            val b = if(a.parent != null){
                a.getTempVar()
            }else a
            //是成员
            //类的成员是运行时动态的
            val final = when(val parent = parent){
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                is ObjectClass -> {
                    arrayOf(Command.build("execute as ${parent.uuid} run "))
                }
                else -> TODO()
            }
            return if (b is MCFPPValue<*>) {
                ifThisIsClassMemberAndAIsConcrete(b, final)
            } else {
                ifThisIsClassMemberAndAIsNotConcrete(b, final)
            }
        } else {
            //t = a
            if (a is MCFPPValue<*>) {
                return ifThisIsNormalVarAndAIsConcrete(a, emptyArray())
            } else {
                if(a.parentClass() != null){
                    //是成员
                    val final = when(val parent = a.parent){
                        is ClassPointer -> {
                            Commands.selectRun(parent)
                        }
                        is ObjectClass -> {
                            arrayOf(Command.build("execute as ${parent.uuid}").build("run","run"))
                        }
                        else -> TODO()
                    }
                    return ifThisIsNormalVarAndAIsClassMember(a, final)
                }else{
                    return ifThisIsNormalVarAndAIsNotConcrete(a, emptyArray())
                }
            }
        }
    }

    fun binaryComputation(a: Var<*>, operation: String): Var<*>{
        var qwq = a.implicitCast(this.type)
        if(qwq.isError){
            qwq = a
        }
        val re = when(operation){
            "+" -> plus(qwq)
            "-" -> minus(qwq)
            "*" -> multiple(qwq)
            "/" -> divide(qwq)
            "%" -> modular(qwq)
            ">" -> isBigger(qwq)
            "<" -> isSmaller(qwq)
            ">=" -> isBiggerOrEqual(qwq)
            "<=" -> isSmallerOrEqual(qwq)
            "==" -> isEqual(qwq)
            "!=" -> isNotEqual(qwq)
            "!" -> negation()
            "||" -> or(qwq)
            "&&" -> and(qwq)
            else -> {
                LogProcessor.error("Unknown operation: $operation")
                UnknownVar("error_operation_" + UUID.randomUUID().toString()).apply { isError = true }
            }
        }
        if(re == null){
            LogProcessor.error("Unsupported operation '$operation' between ${type.typeName} and ${a.type.typeName}")
            return UnknownVar("${type.typeName}_$operation{a.type.typeName}" + UUID.randomUUID()).apply { isError = true }
        }else{
            return re
        }
    }

    fun unaryComputation(operation: String): Var<*>{
        val re = when(operation){
            "!" -> negation()
            else -> {
                LogProcessor.error("Unknown operation: $operation")
                return UnknownVar("error_operation_" + UUID.randomUUID().toString())
            }
        }
        if(re == null){
            LogProcessor.error("Unsupported operation '$operation' for ${type.typeName}")
            return UnknownVar("${type.typeName}_$operation" + UUID.randomUUID())
        }else{
            return re
        }
    }

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    open fun plus(a: Var<*>): Var<*>? = null

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    open fun minus(a: Var<*>): Var<*>? = null

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    open fun multiple(a: Var<*>): Var<*>? = null

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    open fun divide(a: Var<*>): Var<*>? = null

    /**
     * 取余
     * @param a 除数
     * @return 计算的结果
     */
    open fun modular(a: Var<*>): Var<*>? = null

    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isBigger(a: Var<*>): Var<*>? = null

    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isSmaller(a: Var<*>): Var<*>? = null

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isSmallerOrEqual(a: Var<*>): Var<*>? = null

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isBiggerOrEqual(a: Var<*>): Var<*>? = null

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isEqual(a: Var<*>): Var<*>? = null

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isNotEqual(a: Var<*>): Var<*>? = null


    @InsertCommand
    open fun negation(): Var<*>? = null

    @InsertCommand
    open fun or(a: Var<*>): Var<*>? = null

    @InsertCommand
    open fun and(a: Var<*>): Var<*>? = null

    open fun toNBTVar(): NBTBasedData {
        val n = NBTBasedData()
        n.name = name
        n.identifier = identifier
        n.isStatic = isStatic
        n.accessModifier = accessModifier
        n.isTemp = isTemp
        n.stackIndex = stackIndex
        n.isConst = isConst
        n.nbtPath = nbtPath.clone()
        return n
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    abstract fun getTempVar(): Self

    abstract fun storeToStack()

    abstract fun getFromStack()

    override fun getAccess(function: Function): Member.AccessModifier {
        return Member.AccessModifier.PUBLIC
    }

    override fun toString(): String {
        return if(this is MCFPPValue<*>){
            "[$type,value=$value]"
        }else{
            "[$type,value=Unknown]"
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other !is Var<*>) return false
        if(this.parent != other.parent) return false
        if(this.name != other.name) return false
        if(this is MCFPPValue<*> != other is MCFPPValue<*>) return false
        if(this is MCFPPValue<*> && other is MCFPPValue<*> && this.value != other.value) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + identifier.hashCode()
        result = 31 * result + stackIndex
        result = 31 * result + hasAssigned.hashCode()
        result = 31 * result + (parent?.hashCode() ?: 0)
        result = 31 * result + accessModifier.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    fun replacedBy(v : Var<*>){
        if(v == this) return
        if(v is MCInt && this is MCInt && holder != null){
            when(val holder = holder){
                is VectorVar -> {
                    holder.components.forEachIndexed { index, varr ->
                        if(varr == this){
                            holder.components[index] = v
                        }
                    }
                    holder.onScoreChange(v)
                }
            }
        }else if(parent == null){
            if(Function.currFunction.field.containVar(identifier)){
                Function.currFunction.field.putVar(identifier, v, true)
            }
        }else{
            v.parent = this.parent
            when (val parent = parent){
                is ClassPointer -> {
                    //TODO 类暂不支持替换
                }
                is MCFPPTypeVar -> {
                    when(val type = parent.type){
                        is MCFPPClassType ->{
                            type.cls.field.putVar(v.identifier, v, true)
                        }
                        is MCFPPCompoundType -> {
                            type.objectData.field.putVar(v.identifier, v, true)
                        }
                        else -> TODO()
                    }
                }
                is DataTemplateObject -> {
                    parent.instanceField.putVar(identifier, v, true)
                }
                else -> {}
            }
            parent?.onMemberVarChanged(v)
        }
    }

    companion object {

        fun getDefaultValue(type: MCFPPType): Tag<*> {
            return when(type){
                MCFPPBaseType.Int -> IntTag(0)
                MCFPPBaseType.Bool -> ByteTag(0)
                MCFPPEntityType.Selector -> StringTag(EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES).toCommandPart().toString())
                MCFPPEntityType.Entity -> IntArrayTag(intArrayOf(0,0,0,0))
                MCFPPBaseType.String -> StringTag("")
                MCFPPBaseType.Float -> FloatTag(0.0f)
                is MCFPPListType -> ListTag(AnyTag::class.java)
                is MCFPPDictType -> CompoundTag()
                is MCFPPMapType -> CompoundTag()
                MCFPPNBTType.NBT -> IntTag(0)
                MCFPPBaseType.JavaVar -> IntTag(0)
                MCFPPBaseType.Any -> IntTag(0)
                MCFPPBaseType.Type -> StringTag("any")
                MCFPPBaseType.JsonText -> SNBTUtil.fromSNBT("{text:\"\"}")
                is MCFPPGenericClassType -> IntArrayTag(intArrayOf(0,0,0,0))
                is MCFPPClassType -> IntArrayTag(intArrayOf(0,0,0,0))
                is MCFPPDataTemplateType -> type.template.getDefaultValue()
                is MCFPPEnumType -> IntTag(0)
                is MCFPPVectorType -> IntArrayTag(Array(type.dimension){0}.toIntArray())
                else -> {
                    LogProcessor.error("Unknown type: $type")
                    IntTag(0)
                }
            }
        }
        fun buildCastErrorVar(type: MCFPPType): Var<*>{
            val qwq = type.build("error_cast_" + UUID.randomUUID().toString(), Function.currFunction)
            qwq.isError = true
            return qwq
        }

    }
}