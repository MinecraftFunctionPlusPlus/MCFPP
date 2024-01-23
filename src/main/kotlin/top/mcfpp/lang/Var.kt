package top.mcfpp.lang

import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.exception.ClassNotDefineException
import top.mcfpp.exception.OperationNotImplementException
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.*
import top.mcfpp.lib.Function
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
 */
abstract class Var : Member, Cloneable, CanSelectMember {
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
     * 这个变量是否是已知的（固定的）。对应dynamic关键字
     */
    var isConcrete = false

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

    //val isClassMember: Boolean
    //    get() = parentClass() != null

    /**
     * 复制一个变量
     */
    constructor(`var` : Var)  {
        name = `var`.name
        identifier = `var`.identifier
        isStatic = `var`.isStatic
        accessModifier = `var`.accessModifier
        isConcrete = `var`.isConcrete
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

    open val type: String
        /**
         * 获取变量的类型
         * @return 变量类型的字符串
         */
        get() = "var"

    /**
     * 获取这个成员的父类，可能不存在
     * @return
     */
    override fun parentClass(): Class? {
        return when (val parent = parent) {
            is ClassPointer -> parent.clsType
            is ClassType -> parent.dataType as? Class
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
    abstract fun assign(b: Var?)

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    abstract fun cast(type: String): Var?

    @Override
    public abstract override fun clone(): Any

    fun clone(pointer: ClassBase): Var{
        val `var`: Var = this.clone() as Var
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
    open fun plus(a: Var): Var? {
        throw OperationNotImplementException()
    }

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    open fun minus(a: Var): Var? {
        throw OperationNotImplementException()
    }

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    open fun multiple(a: Var): Var? {
        throw OperationNotImplementException()
    }

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    open fun divide(a: Var): Var? {
        throw OperationNotImplementException()
    }

    /**
     * 取余
     * @param a 除数
     * @return 计算的结果
     */
    open fun modular(a: Var): Var? {
        throw OperationNotImplementException()
    }


    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isGreater(a: Var): MCBool? {
        throw OperationNotImplementException()
    }


    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isLess(a: Var): MCBool? {
        throw OperationNotImplementException()
    }

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isLessOrEqual(a: Var): MCBool? {
        throw OperationNotImplementException()
    }

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isGreaterOrEqual(a: Var): MCBool? {
        throw OperationNotImplementException()
    }

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isEqual(a: Var): MCBool? {
        throw OperationNotImplementException()
    }

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun notEqual(a: Var): MCBool? {
        throw OperationNotImplementException()
    }


    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    abstract fun getTempVar(): Var

    abstract fun storeToStack()

    abstract fun getFromStack()

    abstract fun toDynamic()

    override fun getAccess(function: Function): Member.AccessModifier {
        return Member.AccessModifier.PUBLIC
    }

    companion object {
        /**
         * 根据所给的类型、标识符和域构造一个变量
         * @param identifier 标识符
         * @param type 变量的类型
         * @param container 变量所在的域
         * @return
         */
        fun build(identifier: String, type: String, container: FieldContainer): Var{
            val `var`: Var
            when (type) {
                "int" -> `var` = MCInt(container,identifier)
                "bool" -> `var` = MCBool(container, identifier)
                "selector" -> `var` = Selector(identifier)
                "entity" -> `var` = MCString(container, identifier)
                "string" -> TODO()
                "float" -> TODO()
                "list" -> `var` = NBTList<Tag<*>>(container, identifier)
                "dict" -> TODO()
                "map" -> TODO()
                "nbt" -> `var` = NBT(container, identifier)
                "JavaVar" -> `var` = JavaVar(null,identifier)
                else -> {
                    //自定义的类的类型
                    val c = type.split(":")
                    //取出类
                    val clsType: Class? = if(c.size == 1){
                        GlobalField.getClass(null,c[0])
                    }else{
                        GlobalField.getClass(c[0],c[1])
                    }
                    if (clsType == null) {
                        Project.error("Undefined class:$c")
                        throw ClassNotDefineException()
                    }
                    `var` = ClassPointer(clsType, identifier)
                }
            }
            return `var`
        }

        /**
         * 解析变量上下文，构造上下文声明的变量
         * @param ctx 变量声明的上下文
         * @param container 变量所在缓存
         * @return 声明的变量
         */
        fun build(ctx: mcfppParser.FieldDeclarationExpressionContext, container: FieldContainer): Var {
            val `var`: Var
            val typeContext = (ctx.parent as mcfppParser.FieldDeclarationContext).type()
            if (typeContext.className() == null) {
                //普通类型
                `var` = when (typeContext.text) {
                    "int" -> MCInt(container, ctx.Identifier().text)
                    "bool" -> MCBool(container, ctx.Identifier().text)
                    "string" -> MCString(container, ctx.Identifier().text)
                    "float" -> MCFloat(container, ctx.Identifier().text)
                    "nbt" -> NBT(container, ctx.Identifier().text)
                    else -> TODO()
                }
            } else {
                //自定义的类的类型
                val cls = typeContext.className().text.split(":")
                //取出类
                val type: Class? = if(cls.size == 1){
                    GlobalField.getClass(null,cls[0])
                }else{
                    GlobalField.getClass(cls[0],cls[1])
                }
                if (type == null) {
                    Project.error("Undefined class:$cls")
                    throw ClassNotDefineException()
                }
                `var` = ClassPointer(type, ctx.Identifier().text)
            }
            return `var`
        }

        /**
         * 解析变量声明上下文，构造上下文声明的变量，作为类的成员
         * @param ctx 变量声明上下文
         * @param compoundData 成员所在的复合类型
         * @return 这个变量
         */
        fun build(ctx: mcfppParser.FieldDeclarationExpressionContext, compoundData: CompoundData): Var {
            //TODO 浮点数
            val `var`: Var
            val typeContext = (ctx.parent as mcfppParser.FieldDeclarationContext).type()
            if (typeContext.className() == null) {
                //普通类型
                when (typeContext.text) {
                    "int" -> {
                        `var` =
                            MCInt("@s").setObj(SbObject(compoundData.prefix + "_int_" + ctx.Identifier()))
                        `var`.identifier = ctx.Identifier().text
                    }

                    "bool" -> {
                        `var` =
                            MCBool("@s").setObj(SbObject(compoundData.prefix + "_bool_" + ctx.Identifier()))
                        `var`.identifier = ctx.Identifier().text
                    }
                    "selector" -> `var` = Selector(ctx.Identifier().text)
                    "entity" -> TODO()
                    "string" -> TODO()
                    "nbt" -> TODO()
                    "float" -> TODO()
                    else -> TODO()
                }
            } else {
                //TODO 不支持复合类型
                //自定义的类的类型
                val clsType = typeContext.className().text.split(":")
                //取出类
                val type: Class? = if(clsType.size == 1){
                    GlobalField.getClass(null,clsType[0])
                }else{
                    GlobalField.getClass(clsType[0],clsType[1])
                }
                if (type == null) {
                    Project.error("Undefined class:$clsType")
                }
                val classPointer = ClassPointer(type!!, ctx.Identifier().text)
                classPointer.name = ctx.Identifier().text
                `var` = classPointer
            }
            return `var`
        }
    }
}