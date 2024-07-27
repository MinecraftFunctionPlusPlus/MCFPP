package top.mcfpp.lang

import net.querz.nbt.tag.ByteTag
import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.resource.StorageConcrete
import top.mcfpp.lang.type.*
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.lib.*
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
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
 *
 * @param T 已弃用。变量储存的类型。
 */
abstract class Var<T> : Member, Cloneable, CanSelectMember, Serializable{
    /**
     * 在Minecraft中的标识符
     */
    var name: String

    /**
     * 在mcfpp中的标识符，在域中的键名
     */
    var identifier: String

    /**
     * 变量在栈里面的位置
     */
    var stackIndex: Int = 0

    /**
     * 是否是静态的成员
     */
    @get:Override
    @set:Override
    override var isStatic = false

    /**
     * 这个变量是否是常量。对应const关键字
     */
    var isConst = false
    var hasAssigned = false

    /**
     * 这个变量是否是引入的。对应import关键字
     */
    var isImport = false

    /**
     * 是否是临时变量
     */
    var isTemp = false

    /**
     * 这个变量是否已经被初始化
     */
    var hasInit = false

    open var parent : CanSelectMember? = null

    /**
     * 访问修饰符
     */
    override var accessModifier: Member.AccessModifier = Member.AccessModifier.PRIVATE

    /**
     * 变量的类型
     */
    open var type: MCFPPType = MCFPPBaseType.Any

    /**
     * 变量是存在列表里面还是复合标签里面的
     */
    var inList = false

    /**
     *
     */
    open var nbtPath = NBTPath(StorageSource(StorageConcrete("system")))
        .memberIndex(Project.currNamespace)
        .memberIndex("stack_frame[$stackIndex]")

    /**
     * 复制一个变量
     */
    constructor(`var` : Var<*>)  {
        name = `var`.name
        identifier = `var`.identifier
        isStatic = `var`.isStatic
        accessModifier = `var`.accessModifier
        isTemp = `var`.isTemp
        stackIndex = `var`.stackIndex
        isConst = `var`.isConst
        nbtPath = `var`.nbtPath
    }

    /**
     * 创建一个变量。它的标识符和mc名一致
     *
     * @param identifier 变量的标识符。默认为随机的uuid
     */
    constructor(identifier: String = UUID.randomUUID().toString()){
        this.name = identifier
        this.identifier = identifier
        nbtPath.memberIndex(identifier)
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
            else -> null
        }
    }

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    @Throws(VariableConverseException::class)
    abstract fun assign(b: Var<*>) : Var<T>

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    abstract fun cast(type: MCFPPType): Var<*>

    @Override
    public abstract override fun clone(): Var<*>

    fun clone(pointer: ClassPointer): Var<*>{
        val `var`: Var<*> = this.clone()
        if(pointer.identifier != "this"){
            //不是this指针才需要额外指定引用者
            `var`.parent = pointer
        }
        `var`.nbtPath = NBTPath(EntitySource(SelectorVarConcrete(EntitySelector('s'))))
            .memberIndex("data")
            .memberIndex( identifier)
        return `var`
    }

    fun clone(obj: DataTemplateObject): Var<*>{
        val `var`: Var<*> = this.clone()
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
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
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
                        is MCFPPClassType -> {
                            arrayOf(Command.build("execute as ${parent.cls.uuid}").build("run","run"))
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

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    open fun plus(a: Var<*>): Var<*> {
        LogProcessor.error("type ${type.typeName} not support operation '+'")
        return UnknownVar("")
    }

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    open fun minus(a: Var<*>): Var<*> {
        LogProcessor.error("type ${type.typeName} not support operation '-'")
        return UnknownVar("")
    }

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    open fun multiple(a: Var<*>): Var<*> {
        LogProcessor.error("type ${type.typeName} not support operation '*'")
        return UnknownVar("")
    }

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    open fun divide(a: Var<*>): Var<*> {
        LogProcessor.error("type ${type.typeName} not support operation '/'")
        return UnknownVar("")
    }

    /**
     * 取余
     * @param a 除数
     * @return 计算的结果
     */
    open fun modular(a: Var<*>): Var<*> {
        LogProcessor.error("type ${type.typeName} not support operation '%'")
        return UnknownVar("")
    }


    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isBigger(a: Var<*>): MCBool {
        LogProcessor.error("type ${type.typeName} not support operation '>'")
        return MCBool("")
    }


    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isSmaller(a: Var<*>): MCBool {
        LogProcessor.error("type ${type.typeName} not support operation '<'")
        return MCBool("")
    }

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isSmallerOrEqual(a: Var<*>): MCBool {
        LogProcessor.error("type ${type.typeName} not support operation '<='")
        return MCBool("")
    }

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isGreaterOrEqual(a: Var<*>): MCBool {
        LogProcessor.error("type ${type.typeName} not support operation '>='")
        return MCBool("")
    }

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isEqual(a: Var<*>): MCBool {
        LogProcessor.error("type ${type.typeName} not support operation '=='")
        return MCBool("")
    }

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isNotEqual(a: Var<*>): MCBool {
        LogProcessor.error("type ${type.typeName} not support operation '!='")
        return MCBool("")
    }

    open fun toNBTVar(): NBTBasedData<*> {
        val n = NBTBasedData<ByteTag>()
        n.name = name
        n.identifier = identifier
        n.isStatic = isStatic
        n.accessModifier = accessModifier
        n.isTemp = isTemp
        n.stackIndex = stackIndex
        n.isConst = isConst
        n.nbtPath = nbtPath
        return n
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    abstract fun getTempVar(): Var<*>

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
        if(parent == null){
            Function.currFunction.field.putVar(v.identifier, v, true)
        }else{
            v.parent = this.parent
            //TODO
            when (val parent = parent){
                is ClassPointer -> {
                    parent.clazz.field.putVar(v.identifier, v, true)
                }
                is MCFPPTypeVar -> {
                    TODO()
                    when(val type = parent.type){
                        is MCFPPClassType ->{
                            type.cls.field.putVar(v.identifier, v, true)
                        }
                        is MCFPPCompoundType -> {
                            type.compoundData.field.putVar(v.identifier, v, true)
                        }
                        else -> TODO()
                    }
                }
                is DataTemplateObject -> {
                    parent.instanceField.putVar(v.identifier, v, true)
                }
                else -> {}
            }
        }
    }

    companion object {
        /**
         * 根据所给的类型、标识符和域构造一个变量
         * @param identifier 标识符
         * @param type 变量的类型
         * @param container 变量所在的域
         * @return
         */
        fun build(identifier: String, type: MCFPPType, container: FieldContainer): Var<*>{
            val `var`: Var<*>
            when (type) {
                MCFPPBaseType.Int -> `var` = MCInt(container,identifier)
                MCFPPBaseType.Bool -> `var` = MCBool(container, identifier)
                MCFPPBaseType.Selector -> TODO()
                MCFPPBaseType.BaseEntity -> TODO()
                MCFPPBaseType.String -> `var` = MCString(container, identifier)
                MCFPPBaseType.Float -> TODO()
                is MCFPPListType -> `var` = NBTList(container, identifier, type.generic)
                MCFPPNBTType.Dict -> TODO()
                MCFPPNBTType.Map -> TODO()
                MCFPPNBTType.NBT -> `var` = NBTBasedData<Tag<*>>(container, identifier)
                MCFPPBaseType.JavaVar -> `var` = JavaVar(null,identifier)
                MCFPPBaseType.Any -> `var` = MCAny(container, identifier)
                MCFPPBaseType.Type -> `var` = MCFPPTypeVar(identifier = identifier)
                is MCFPPGenericClassType -> {
                    `var` = ClassPointer(type.cls, identifier)
                }
                is MCFPPClassType ->{
                    //TODO: 这里不一定拿得到type.cls!!!可能得从GlobalField拿！
                    //什么意思捏？ - Alumopper 2024.4.14
                    `var` = ClassPointer(type.cls, identifier)
                }
                is MCFPPDataTemplateType -> {
                    //数据模板
                    `var` = DataTemplateObject(type.template, identifier)
                }
                is MCFPPEnumType -> {
                    `var` = EnumVar(type.enum, container, identifier)
                }
                //还有模板什么的
                else -> {
                    LogProcessor.error("Unknown type: $type")
                    `var` = UnknownVar(identifier)
                }
            }
            return `var`
        }

        /**
         * 根据所给的类型、标识符和域构造一个变量
         * @param identifier 标识符
         * @param type 变量的类型
         * @param container 变量所在的域
         * @return
         */
        fun build(identifier: String, type: MCFPPType): Var<*>{
            val `var`: Var<*>
            when (type) {
                MCFPPBaseType.Int -> `var` = MCInt(identifier)
                MCFPPBaseType.Bool -> `var` = MCBool(identifier)
                MCFPPBaseType.Selector -> TODO()
                MCFPPBaseType.BaseEntity -> TODO()
                MCFPPBaseType.String -> `var` = MCString(identifier)
                MCFPPBaseType.Float -> TODO()
                is MCFPPListType -> `var` = NBTList(identifier, type.generic)
                MCFPPNBTType.Dict -> TODO()
                MCFPPNBTType.Map -> TODO()
                MCFPPNBTType.NBT -> `var` = NBTBasedData<Tag<*>>(identifier)
                MCFPPBaseType.JavaVar -> `var` = JavaVar(null,identifier)
                MCFPPBaseType.Any -> `var` = MCAny(identifier)
                MCFPPBaseType.Type -> `var` = MCFPPTypeVar(identifier = identifier)
                is MCFPPGenericClassType -> {
                    `var` = ClassPointer(type.cls, identifier)
                }
                is MCFPPClassType ->{
                    `var` = ClassPointer(type.cls, identifier)
                }
                is MCFPPDataTemplateType -> {
                    //数据模板
                    `var` = DataTemplateObject(type.template, identifier)
                }
                is MCFPPEnumType -> {
                    `var` = EnumVar(type.enum, identifier)
                }
                //还有模板什么的
                else -> {
                    LogProcessor.error("Unknown type: $type")
                    `var` = UnknownVar(identifier)
                }
            }
            return `var`
        }

        /**
         * 解析变量声明上下文，构造上下文声明的变量，作为成员
         * @param identifier 变量标识符
         * @param type 变量类型
         * @param clazz 成员所在的复合类型
         * @return 这个变量
         */
        fun build(identifier: String, type: MCFPPType, clazz: Class): Var<*> {
            val `var`: Var<*>
            //普通类型
            when (type) {
                MCFPPBaseType.Int -> {
                    `var` =
                        MCInt("@s").setObj(SbObject(clazz.prefix + "_int_" + identifier))
                    `var`.identifier = identifier
                }

                MCFPPBaseType.Bool -> {
                    `var` =
                        MCBool("@s").setObj(SbObject(clazz.prefix + "_bool_" + identifier))
                    `var`.identifier = identifier
                }
                MCFPPBaseType.Selector -> TODO()
                MCFPPBaseType.BaseEntity -> TODO()
                MCFPPBaseType.String -> TODO()
                MCFPPNBTType.NBT -> TODO()
                MCFPPBaseType.Float -> TODO()
                MCFPPBaseType.Any -> TODO()
                is MCFPPDataTemplateType -> TODO()
                is MCFPPClassType ->{
                    val classPointer = ClassPointer(type.cls,identifier)
                    classPointer.name = identifier
                    `var` = classPointer
                }
                is MCFPPEnumType -> {
                    `var` = EnumVar(type.enum, identifier)
                }
                //还有模板什么的
                else -> {
                    LogProcessor.error("Unknown type: $type")
                    `var` = UnknownVar(identifier)
                }
            }
            return `var`
        }
    }
}