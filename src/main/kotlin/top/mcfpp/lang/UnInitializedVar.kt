package top.mcfpp.lang

import top.mcfpp.Project
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.exception.VariableNotResolvedException
import top.mcfpp.lib.Class
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member
import top.mcfpp.lib.antlr.McfppExprVisitor

class UnInitializedVar: Var {

    val ctx: mcfppParser.FieldDeclarationContext

    /**
     * 创建一个未被解析的变量，它有指定的标识符和类型
     */
    constructor(ctx: mcfppParser.FieldDeclarationContext){
        this.ctx = ctx
        this.identifier = ctx.Identifier().text
    }

    override val type: String
        get() = ctx.type().text

    fun initialize(cls: Class): Var{
        //变量的初始化
        val `var`: Var = Var.build(ctx, compoundData = cls)
        if (ctx.expression() != null) {
            Function.currFunction = Class.currClass!!.classPreInit
            //是类的成员
            Function.addCommand("#" + ctx.text)
            val init: Var = McfppExprVisitor().visit(ctx.expression())!!
            try {
                `var`.assign(init)
            } catch (e: VariableConverseException) {
                Project.error("Cannot convert " + init.javaClass + " to " + `var`.javaClass)
                Function.currFunction = Function.nullFunction
                throw VariableConverseException()
            }
            Function.currFunction = Function.nullFunction
        }
        return `var`
    }

    /**
     * 赋值。总是不能进行，因为这个变量未解析
     *
     * @param b
     *
     * @throws VariableNotResolvedException 调用此方法就会抛出此异常
     */
    override fun assign(b: Var?) {
        throw VariableNotResolvedException()
    }

    /**
     * 类型的强制转换。总是不能进行，因为这个变量未解析
     *
     * @param type
     *
     * @throws VariableNotResolvedException 调用此方法就会抛出此异常
     */
    override fun cast(type: String): Var? {
        throw VariableNotResolvedException()
    }

    /**
     * 复制一个有相同类型和标识符未解析变量
     *
     * @return 复制的结果
     */
    override fun clone(): Any {
        return UnInitializedVar(ctx)
    }

    /**
     * 获取临时变量。总是不能进行，因为这个变量未解析
     *
     * @throws VariableNotResolvedException 调用此方法就会抛出此异常
     */
    override fun getTempVar(): Var {
        throw VariableNotResolvedException()
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
        Project.error("UnresolvedVar.getMemberVar() is called")
        throw VariableNotResolvedException()
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        params: List<String>,
        accessModifier: Member.AccessModifier
    ): Pair<Function?, Boolean> {
        Project.error("UnresolvedVar.getMemberFunction() is called")
        throw VariableNotResolvedException()
    }
}