package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.core.lang.*
import top.mcfpp.type.MCFPPType
import top.mcfpp.core.lang.MCFPPValue
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
        if(ctx.primary() != null){
            currSelector = visit(ctx.primary())
        }
        if(currSelector is UnknownVar){
            val type = MCFPPType.parseFromIdentifier(ctx.type().text, Function.currFunction.field)
            currSelector = type
        }
        for (selector in ctx.selector()){
            visit(selector)
        }
        return currSelector as Var<*>
    }

    @Override
    override fun visitSelector(ctx: mcfppParser.SelectorContext?): Var<*> {
        //进入visitVar，currSelector作为成员选择的上下文
        currSelector = visit(ctx!!.`var`())
        return currSelector as Var<*>
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
        } else if (ctx.range() != null){
            //是范围
            val left = ctx.range().num1?.let { visit(it) }
            val right = ctx.range().num2?.let { visit(it) }
            if(left is MCNumber<*>? && right is MCNumber<*>?){
                if(left is MCFPPValue<*>? && right is MCFPPValue<*>?){
                    val leftValue = left?.value.toString().toFloatOrNull()
                    val rightValue = right?.value.toString().toFloatOrNull()
                    return RangeVarConcrete(leftValue to rightValue)
                }else{
                    val range = RangeVar()
                    if(left is MCInt){
                        range.left = MCFloat(range.identifier + "_left")
                    }
                    if(right is MCInt){
                        range.right = MCFloat(range.identifier + "_right")
                    }
                    left?.let { range.left.assign(it) }
                    right?.let { range.right.assign(it) }
                    return range
                }
            }else{
                LogProcessor.error("Range sides should be a number: ${left?.type} and ${right?.type}")
                return UnknownVar("range_" + UUID.randomUUID())
            }
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
        if (ctx.Identifier() != null && ctx.arguments() == null) {
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
                for (value in ctx.identifierSuffix()) {
                    if(value.conditionalExpression() != null){
                        if(re !is Indexable){
                            LogProcessor.error("Cannot index ${re.type}")
                            return UnknownVar("${re.identifier}_index_" + UUID.randomUUID())
                        }
                        //索引
                        val index = visit(value.conditionalExpression())!!
                        re = (re as Indexable).getByIndex(index)
                    }else{
                        if(!re.isTemp) re = re.getTempVar()
                        //初始化
                        for (initializer in value.objectInitializer()){
                            val id = initializer.Identifier().text
                            val v = visit(initializer.expression())
                            val (m, b) = re.getMemberVar(id, re.getAccess(Function.currFunction))
                            if(!b){
                                LogProcessor.error("Cannot access member $id")
                            }
                            if(m == null) {
                                LogProcessor.error("Member $id not found")
                                continue
                            }
                            m.replacedBy(m.assign(v))
                        }
                    }
                }
                return re
            }
        } else if (ctx.expression() != null) {
            // '(' expression ')'
            return McfppLeftExprVisitor().visit(ctx.expression())
        } else {
            //函数的调用
            Function.addComment(ctx.text)
            //参数获取
            val normalArgs: ArrayList<Var<*>> = ArrayList()
            val readOnlyArgs: ArrayList<Var<*>> = ArrayList()
            val exprVisitor = MCFPPExprVisitor()
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
                MCFPPFuncManager().getFunction(currSelector!!,p.second,
                    FunctionParam.getArgTypes(readOnlyArgs),
                    FunctionParam.getArgTypes(normalArgs))
            }
            //调用函数
            return if (func is UnknownFunction) {
                var cls: Class? = if(ctx.arguments().readOnlyArgs() != null){
                    GlobalField.getClass(p.first, p.second ,readOnlyArgs.map { it.type })
                 }else{
                     GlobalField.getClass(p.first, p.second)
                 }
                 //可能是构造函数
                 if (cls == null) {
                     val template: DataTemplate? = GlobalField.getTemplate(p.first, p.second)
                     if(template == null){
                         LogProcessor.error("Function ${func.identifier}<${readOnlyArgs.joinToString(",") { it.type.typeName }}>(${normalArgs.map { it.type.typeName }.joinToString(",")}) not defined")
                         Function.addComment("[Failed to Compile]${ctx.text}")
                         func.invoke(normalArgs,currSelector)
                         return func.returnVar
                     }
                     //模板默认构造函数
                     if(readOnlyArgs.isNotEmpty() || normalArgs.isNotEmpty()){
                         LogProcessor.error("Template constructor ${template.identifier} cannot have arguments")
                         return UnknownVar("${template.identifier}_type_" + UUID.randomUUID())
                     }
                     return DataTemplateObject(template)
                 }
                 if(cls is GenericClass){
                    //实例化泛型函数
                    cls = cls.compile(readOnlyArgs)
                }
                //获取对象
                val ptr = cls.newInstance()
                //调用构造函数
                val constructor = cls.getConstructorByString(FunctionParam.getArgTypeNames(normalArgs))
                if (constructor == null) {
                    LogProcessor.error("No constructor like: " + FunctionParam.getArgTypeNames(normalArgs) + " defined in class " + ctx.namespaceID().text)
                    Function.addComment("[Failed to compile]${ctx.text}")
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