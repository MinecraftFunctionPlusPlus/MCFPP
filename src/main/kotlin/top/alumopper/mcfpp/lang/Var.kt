package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.exception.VariableConverseException
import top.alumopper.mcfpp.lib.CacheContainer
import top.alumopper.mcfpp.lib.Class
import top.alumopper.mcfpp.lib.ClassMember
import java.util.*

/**
 * mcfpp所有类型的基类。在mcfpp中，一个变量可以是固定的，也就是mcfpp编译
 * 能知道确切值的变量。例如`int i = 0;`中，编译器可以明确i的值就是
 * 0。另外，还可能有编译器并不知道确切值的变量。例如`int i = e.pos[0]`，
 * 获取了一个实体的x坐标。编译器并不能知道这个实体的坐标会是什么。
 *
 *
 * 对于固定的值，编译器会尽可能计算出他们的值。例如`int i = 6 + 7 + p`，
 * 编译器会提前计算为`int i = 13 + p`，从而减少命令的使用量。
 *
 *
 *
 * 除此之外，变量还有临时变量的区别，对于匿名的变量，编译器一般会默认它为临时
 * 的变量，从而在各种处理上进行优化。当然，匿名变量的声明往往在编译过程中声明。
 * mcfpp本身的语法并不支持匿名变量。
 *
 */
abstract class Var : ClassMember, Cloneable {
    /**
     * 标识符
     */
    var identifier: String

    /**
     * 在缓存中的名字
     */
    var key: String? = null

    /**
     * 是否是已知的（固定的）
     */
    var isConcrete = false

    /**
     * 是否是临时变量
     */
    var isTemp = false

    /**
     * 变量在栈里面的位置
     */
    var stackIndex: Int = 0

    /**
     *
     */
    @get:Override
    @set:Override
    override var isStatic = false

    /**
     * 这个变量是否是常量
     */
    var isConst = ConstStatus.NOT_CONST

    enum class ConstStatus {
        NOT_CONST, NULL, ASSIGNED
    }

    /**
     * 访问修饰符
     */
    override var accessModifier: ClassMember.AccessModifier = ClassMember.AccessModifier.PRIVATE

    /**
     * 成员所在的类
     */
    open var cls: CanSelectMember? = null

    init {
        identifier = UUID.randomUUID().toString()
    }

    constructor(`var` : Var)  {
        identifier = `var`.identifier
        key = `var`.key
        isStatic = `var`.isStatic
        accessModifier = `var`.accessModifier
        isConcrete = `var`.isConcrete
        isTemp = `var`.isTemp
        stackIndex = `var`.stackIndex
        isConst = `var`.isConst
        cls = `var`.cls
    }

    constructor()

    open val type: String
        /**
         * 获取变量的类型
         * @return 变量类型的字符串
         */
        get() = "var"

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
    override fun Class(): Class? {
        return null
    }

    @Override
    public abstract override fun clone(): Any

    companion object {
        /**
         * 解析变量上下文，构造上下文声明的变量
         * @param ctx 变量声明的上下文
         * @param container 变量所在缓存
         * @return 声明的变量
         */
        fun build(ctx: mcfppParser.FieldDeclarationContext, container: CacheContainer): Var? {
            var `var`: Var? = null
            if (ctx.type().className() == null) {
                //普通类型
                when (ctx.type().text) {
                    "int" -> `var` = MCInt(ctx.Identifier().text, container)
                    "bool" -> `var` = MCBool(ctx.Identifier().text, container)
                }
            } else if (ctx.type().className().InsideClass() != null) {
                when (ctx.type().className().InsideClass().text) {
                    "selector" -> `var` = Selector(ctx.Identifier().text)
                    "entity" -> `var` = null
                    "string" -> `var` = null
                }
            } else {
                //自定义的类的类型
                val cls: String = ctx.type().className().text
                //取出类
                val type: Class? = Project.global.cache.classes.getOrDefault(cls, null)
                if (type == null) {
                    Project.error("Undefined class:$cls")
                }
                assert(type != null)
                `var` = ClassPointer(type!!, container, ctx.Identifier().text)
            }
            return `var`
        }

        /**
         * 解析变量声明上下文，构造上下文声明的变量，作为类的成员
         * @param ctx 变量声明上下文
         * @param cls 成员所在的类
         * @return 这个变量
         */
        fun build(ctx: mcfppParser.FieldDeclarationContext, cls: Class): Var? {
            var `var`: Var? = null
            if (ctx.type().className() == null) {
                //普通类型
                when (ctx.type().text) {
                    "int" -> {
                        `var` =
                            MCInt("@s").setObj(SbObject(cls.namespace + "_class_" + cls.identifier + "_int_" + ctx.Identifier()))
                        `var`.key = ctx.Identifier().text
                    }

                    "bool" -> {
                        `var` =
                            MCBool("@s").setObj(SbObject(cls.namespace + "_class_" + cls.identifier + "_bool_" + ctx.Identifier()))
                        `var`.key = ctx.Identifier().text
                    }
                }
            } else if (ctx.type().className().InsideClass() != null) {
                when (ctx.type().className().InsideClass().text) {
                    "selector" -> `var` = Selector(ctx.Identifier().text)
                    "entity" -> `var` = null
                    "string" -> `var` = null
                }
            } else {
                //自定义的类的类型
                val clsType: String = ctx.type().className().text
                //取出类
                val type: Class? = Project.global.cache.classes.getOrDefault(clsType, null)
                if (type == null) {
                    Project.error("Undefined class:$clsType")
                }
                assert(type != null)
                val classPointer = ClassPointer(type!!, cls, ctx.Identifier().text)
                classPointer.address =
                    (MCInt("@s").setObj(SbObject(cls.namespace + "_class_" + cls.identifier + "_" + clsType + "_" + ctx.Identifier())) as MCInt)
                classPointer.identifier = ctx.Identifier().text
                `var` = classPointer
            }
            //是否是常量
            if (ctx.CONST() != null) {
                `var`!!.isConst = ConstStatus.NULL
                `var`.isConcrete = true
            }
            return `var`
        }
    }
}