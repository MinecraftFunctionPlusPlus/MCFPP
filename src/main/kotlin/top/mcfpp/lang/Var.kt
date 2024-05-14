package top.mcfpp.lang

import net.querz.nbt.tag.Tag
import top.mcfpp.exception.OperationNotImplementException
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.*
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
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
 * @param T 变量储存的类型
 */
abstract class Var<T> : Member, Cloneable, CanSelectMember{
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

    var parent : CanSelectMember? = null

    /**
     * 访问修饰符
     */
    override var accessModifier: Member.AccessModifier = Member.AccessModifier.PRIVATE

    /**
     * 变量的类型
     */
    open var type: MCFPPType = MCFPPBaseType.Any

    /**
     * 复制一个变量
     */
    constructor(`var` : Var<*>)  {
        name = `var`.name
        identifier = `var`.identifier
        isStatic = `var`.isStatic
        accessModifier = `var`.accessModifier
        stackIndex = `var`.stackIndex
        isConst = `var`.isConst
    }

    /**
     * 创建一个变量。它的标识符和mc名一致
     *
     * @param identifier 变量的标识符。默认为随机的uuid
     */
    constructor(identifier: String = UUID.randomUUID().toString()){
        this.name = identifier
        this.identifier = identifier
    }

    /**
     * 获取这个成员的父类，可能不存在
     * @return
     */
    override fun parentClass(): Class? {
        return when (val parent = parent) {
            is ClassPointer -> parent.clsType
            is MCFPPClassType -> parent.cls
            else -> null
        }
    }

    /**
     * 获取这个成员的父结构体，可能不存在
     *
     * @return
     */
    override fun parentStruct(): Template? {
        return when (val parent = parent) {
            is IntTemplatePointer -> parent.structType
            is IntTemplateType -> parent.dataType as? Template
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
        return `var`
    }

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    open fun plus(a: Var<*>): Var<*> {
        throw OperationNotImplementException()
    }

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    open fun minus(a: Var<*>): Var<*> {
        throw OperationNotImplementException()
    }

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    open fun multiple(a: Var<*>): Var<*> {
        throw OperationNotImplementException()
    }

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    open fun divide(a: Var<*>): Var<*> {
        throw OperationNotImplementException()
    }

    /**
     * 取余
     * @param a 除数
     * @return 计算的结果
     */
    open fun modular(a: Var<*>): Var<*> {
        throw OperationNotImplementException()
    }


    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isBigger(a: Var<*>): MCBool {
        throw OperationNotImplementException()
    }


    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isSmaller(a: Var<*>): MCBool {
        throw OperationNotImplementException()
    }

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isSmallerOrEqual(a: Var<*>): MCBool {
        throw OperationNotImplementException()
    }

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isGreaterOrEqual(a: Var<*>): MCBool {
        throw OperationNotImplementException()
    }

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isEqual(a: Var<*>): MCBool {
        throw OperationNotImplementException()
    }

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isNotEqual(a: Var<*>): MCBool {
        throw OperationNotImplementException()
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
        return "[$type,value=Unknown]"
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
                is MCFPPListType -> `var` = NBTList<NBTBasedData<*>>(container, identifier, type.generic)
                MCFPPNBTType.Dict -> TODO()
                MCFPPNBTType.Map -> TODO()
                MCFPPNBTType.NBT -> `var` = NBTBasedData<Tag<*>>(container, identifier)
                MCFPPBaseType.JavaVar -> `var` = JavaVar(null,identifier)
                MCFPPBaseType.Any -> `var` = MCAny(container, identifier)
                MCFPPBaseType.Type -> `var` = MCFPPTypeVar(identifier = identifier)
                is MCFPPClassType ->{
                    //TODO: 这里不一定拿得到type.cls!!!可能得从GlobalField拿！
                    //什么意思捏？ - Alumopper 2024.4.14
                    `var` = ClassPointer(type.cls, identifier)
                }
                //还有模板什么的
                else -> {
                    `var` = UnknownVar(identifier)
                }
            }
            return `var`
        }
        /**
         * 解析变量声明上下文，构造上下文声明的变量，作为成员
         * @param identifier 变量标识符
         * @param type 变量类型
         * @param compoundData 成员所在的复合类型
         * @return 这个变量
         */
        fun build(identifier: String, type: MCFPPType, compoundData: CompoundData): Var<*> {
            val `var`: Var<*>
            //普通类型
            when (type) {
                MCFPPBaseType.Int -> {
                    `var` =
                        MCInt("@s").setObj(SbObject(compoundData.prefix + "_int_" + identifier))
                    `var`.identifier = identifier
                }

                MCFPPBaseType.Bool -> {
                    `var` =
                        MCBool("@s").setObj(SbObject(compoundData.prefix + "_bool_" + identifier))
                    `var`.identifier = identifier
                }
                MCFPPBaseType.Selector -> TODO()
                MCFPPBaseType.BaseEntity -> TODO()
                MCFPPBaseType.String -> TODO()
                MCFPPNBTType.NBT -> TODO()
                MCFPPBaseType.Float -> TODO()
                MCFPPBaseType.Any -> TODO()
                is MCFPPTemplateType -> TODO()
                is MCFPPClassType ->{
                    val classPointer = ClassPointer(type.cls,identifier)
                    classPointer.name = identifier
                    `var` = classPointer
                }
                else -> {
                    `var` = UnknownVar(identifier)
                }
            }
            return `var`
        }
    }
}