package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.lang.*
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.generic.Generic
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper
import java.util.*
import kotlin.collections.ArrayList

class McfppLeftExprVisitor : mcfppParserBaseVisitor<Var<*>>(){
    private var currSelector : CanSelectMember? = null
    /**
     * 计算一个基本的表达式。可能是一个变量，也可能是一个数值
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitBasicExpression(ctx: mcfppParser.BasicExpressionContext): Var<*> {
        Project.ctx = ctx
        return if (ctx.primary() != null) {
            visit(ctx.primary())
        } else {
            visit(ctx.varWithSelector())
        }
    }

    /**
     * 从类中选择一个成员。返回的成员包含了它所在的对象的信息
     *
     * @param ctx
     * @return
     */
    @Override
    override fun visitVarWithSelector(ctx: mcfppParser.VarWithSelectorContext): Var<*> {
        Project.ctx = ctx
        val namespaceID : Pair<String?, String>
        if(ctx.primary() != null){
            currSelector = visit(ctx.primary())
        }
        if(currSelector is UnknownVar){
            if(ctx.primary() != null || ctx.type().className() != null){
                namespaceID = if(ctx.primary() != null){
                    null to ctx.primary().text
                } else{
                    StringHelper.splitNamespaceID(ctx.type().text)
                }
                val o = GlobalField.getObject(namespaceID.first, namespaceID.second)
                if(o != null) {
                    currSelector = o.getType()
                } else{
                    LogProcessor.error("Undefined type: ${namespaceID.second}")
                    currSelector = UnknownVar("${ctx.type().className().text}_type_" + UUID.randomUUID())
                }
            }else{
                currSelector = CompoundDataCompanion(
                    //基本类型
                    when(ctx.type().text){
                        "int" -> MCInt.data
                        else -> TODO()
                    }
                )
            }
        }
        for (selector in ctx.selector()){
            visit(selector)
        }
        return currSelector as Var<*>
    }

    @Override
    override fun visitSelector(ctx: mcfppParser.SelectorContext?): Var<*> {
        currSelector = visit(ctx!!.`var`())!!.getTempVar()
        return (currSelector as Var<*>)
    }

    /**
     * 一个初级表达式，可能是一个变量，也可能是一个数值
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitPrimary(ctx: mcfppParser.PrimaryContext): Var<*> {
        Project.ctx = ctx
        if (ctx.`var`() != null) {
            //变量
            return visit(ctx.`var`())
        } else if (ctx.value() != null) {
            //数字
            return visit(ctx.value())
        } else {
            //this或者super
            val s = if(ctx.SUPER() != null){
                "super"
            }else{
                "this"
            }
            val re: Var<*>? = Function.field.getVar(s)
            if (re == null) {
                LogProcessor.error("$s can only be used in member functions.")
                return UnknownVar("error_this")
            }
            return re
        }
    }

    /**
     * 变量
     * @param ctx the parse tree
     * @return 变量
     */
    @Override
    @InsertCommand
    override fun visitVar(ctx: mcfppParser.VarContext): Var<*> {
        Project.ctx = ctx
        return if (ctx.Identifier() != null && ctx.arguments() == null) {
            //变量
            //没有数组选取
            val qwq: String = ctx.Identifier().text
            var re = if(currSelector == null) {
                Function.currFunction.field.getVar(qwq) ?: UnknownVar(qwq)
            }else{
                //获取成员
                val re  = currSelector!!.getMemberVar(qwq, currSelector!!.getAccess(Function.currFunction))
                if (re.first == null) {
                    UnknownVar(qwq)
                }else if (!re.second){
                    LogProcessor.error("Cannot access member $qwq")
                    UnknownVar(qwq)
                }else{
                    re.first!!
                }
            }
            // Identifier identifierSuffix*
            if (ctx.identifierSuffix() == null || ctx.identifierSuffix().size == 0) {
                return re
            } else {
                if(re is Indexable<*>){
                    for (value in ctx.identifierSuffix()) {
                        val index = visit(value.conditionalExpression())!!
                        re = (re as Indexable<*>).getByIndex(index)
                    }
                }else{
                    throw IllegalArgumentException("Cannot index ${re.type}")
                }
                return re
            }
        } else if (ctx.expression() != null) {
            // '(' expression ')'
            visit(ctx.expression())
        } else {
            //函数的调用
            Function.addCommand("#" + ctx.text)
            //参数获取
            val normalArgs: ArrayList<Var<*>> = ArrayList()
            val readOnlyArgs: ArrayList<Var<*>> = ArrayList()
            val exprVisitor = McfppExprVisitor()
            for (expr in ctx.arguments().readOnlyArgs()?.expressionList()?.expression()?: emptyList()) {
                val arg = exprVisitor.visit(expr)!!
                readOnlyArgs.add(arg)
            }
            for (expr in ctx.arguments().normalArgs().expressionList()?.expression()?: emptyList()) {
                val arg = exprVisitor.visit(expr)!!
                normalArgs.add(arg)
            }
            //获取函数
            val p = StringHelper.splitNamespaceID(ctx.namespaceID().text)
            val func = if(currSelector == null){
                GlobalField.getFunction(p.first, p.second, FunctionParam.getArgTypes(readOnlyArgs), FunctionParam.getArgTypes(normalArgs))
            }else{
                if(p.first != null){
                    LogProcessor.warn("Invalid namespace usage ${p.first} in function call ")
                }
                McfppFuncManager().getFunction(currSelector!!,p.second,
                    FunctionParam.getArgTypes(readOnlyArgs),
                    FunctionParam.getArgTypes(normalArgs))
            }
            //调用函数
            return if (func is UnknownFunction) {
                //可能是构造函数
                var cls: Class? = GlobalField.getClass(p.first, p.second)
                if (cls == null) {
                    LogProcessor.error("Function " + ctx.text + " not defined")
                    Function.addCommand("[Failed to Compile]${ctx.text}")
                    func.invoke(normalArgs,currSelector)
                    return func.returnVar
                }
                if(cls is GenericClass){
                    //实例化泛型函数
                    cls = cls.compile(readOnlyArgs)
                }
                //获取对象
                val ptr = cls.newInstance()
                //调用构造函数
                val constructor = cls.getConstructor(FunctionParam.getArgTypeNames(normalArgs))
                if (constructor == null) {
                    LogProcessor.error("No constructor like: " + FunctionParam.getArgTypeNames(normalArgs) + " defined in class " + ctx.namespaceID().text)
                    Function.addCommand("[Failed to compile]${ctx.text}")
                }else{
                    constructor.invoke(normalArgs, callerClassP = ptr)
                }
                return ptr
            }else{
                if(func is Generic<*>){
                    func.invoke(readOnlyArgs, normalArgs, currSelector)
                }else{
                    func.invoke(normalArgs,currSelector)
                }
                //函数树
                Function.currFunction.child.add(func)
                func.parent.add(Function.currFunction)
                func.returnVar
            }
        }
    }
}