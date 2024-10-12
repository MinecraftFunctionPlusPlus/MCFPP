package top.mcfpp.antlr

import top.mcfpp.Project
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
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

open class McfppLeftExprVisitor : mcfppParserBaseVisitor<Var<*>>(){
    private var currSelector : CanSelectMember? = null

    override fun visitBasicExpression(ctx: mcfppParser.BasicExpressionContext): Var<*> {
        Project.ctx = ctx
        return if (ctx.primary() != null) {
            visit(ctx.primary())
        } else {
            visit(ctx.varWithSelector())
        }
    }

    override fun visitVarWithSelector(ctx: mcfppParser.VarWithSelectorContext): Var<*> {
        Project.ctx = ctx
        if(ctx.primary() != null){
            currSelector = visit(ctx.primary())
        }
        if(currSelector is UnknownVar){
            val typeStr = ctx.primary()?.text?:ctx.type().text
            val type = MCFPPType.parseFromIdentifier(typeStr, Function.currFunction.field)
            if(type == null){
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(typeStr))
                currSelector = UnknownVar("unknown_" + UUID.randomUUID())
            }else{
                currSelector = ObjectVar(type)
            }
        }
        for (selector in ctx.selector()){
            visit(selector)
        }
        return currSelector as Var<*>
    }

    override fun visitSelector(ctx: mcfppParser.SelectorContext?): Var<*> {
        //进入visitVar，currSelector作为成员选择的上下文
        currSelector = visit(ctx!!.`var`())
        return currSelector as Var<*>
    }

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
                    left?.let { range.left.assignedBy(it) }
                    right?.let { range.right.assignedBy(it) }
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

    override fun visitVar(ctx: mcfppParser.VarContext): Var<*> {
        Project.ctx = ctx
        return if (ctx.varWithSuffix() != null) {
            visit(ctx.varWithSuffix())
        } else if (ctx.bucketExpression() != null) {
            // '(' expression ')'
            visit(ctx.bucketExpression())
        } else {
            //函数调用
            visit(ctx.functionCall())
        }
    }

    override fun visitBucketExpression(ctx: mcfppParser.BucketExpressionContext): Var<*> {
        return MCFPPExprVisitor().visit(ctx.expression())
    }

    override fun visitFunctionCall(ctx: mcfppParser.FunctionCallContext): Var<*> {
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
            GlobalField.getFunction(p.first, p.second, readOnlyArgs, normalArgs)
        }else{
            if(p.first != null){
                LogProcessor.warn("Invalid namespace usage ${p.first} in function call ")
            }
            MCFPPFuncManager().getFunction(currSelector!!,p.second, readOnlyArgs, normalArgs)
        }
        //调用函数
        if (func !is UnknownFunction) {
            if(func is Generic<*>){
                func.invoke(readOnlyArgs, normalArgs, currSelector)
            }else{
                func.invoke(normalArgs,currSelector)
            }
            //函数树
            Function.currFunction.child.add(func)
            func.parent.add(Function.currFunction)
            return func.returnVar
        }
        //可能是类的构造函数
        var cls: Class? = if(ctx.arguments().readOnlyArgs() != null){
            GlobalField.getClass(p.first, p.second ,readOnlyArgs.map { it.type })
        }else{
            GlobalField.getClass(p.first, p.second)
        }
        if (cls != null) {
            if (cls is GenericClass) {
                //实例化泛型函数
                cls = cls.compile(readOnlyArgs)
            }
            //获取对象
            val ptr = cls.newPointer()
            //调用构造函数
            val constructor = cls.getConstructorByString(FunctionParam.getArgTypeNames(normalArgs))
            if (constructor == null) {
                LogProcessor.error("No constructor like: " + FunctionParam.getArgTypeNames(normalArgs) + " defined in class " + ctx.namespaceID().text)
                Function.addComment("[Failed to compile]${ctx.text}")
            } else {
                constructor.invoke(normalArgs, ptr)
            }
            return ptr
        }
        //可能是模板的构造函数
        val template: DataTemplate? = GlobalField.getTemplate(p.first, p.second)
        if(template != null) {
            val init = DataTemplateObjectConcrete(template, template.getType().defaultValue())
            val constructor = template.getConstructorByString(FunctionParam.getArgTypeNames(normalArgs))
            if (constructor == null) {
                LogProcessor.error("No constructor like: " + FunctionParam.getArgTypeNames(normalArgs) + " defined in class " + ctx.namespaceID().text)
                Function.addComment("[Failed to compile]${ctx.text}")
            } else {
                constructor.invoke(normalArgs, init)
            }
            //可能会对init进行替换
            return Function.currFunction.field.getVar(init.identifier) ?: init
        }
        //没有找到函数
        LogProcessor.error("Function ${func.identifier}<${readOnlyArgs.joinToString(",") { it.type.typeName }}>(${normalArgs.map { it.type.typeName }.joinToString(",")}) not defined")
        Function.addComment("[Failed to Compile]${ctx.text}")
        func.invoke(normalArgs,currSelector)
        return func.returnVar
    }

    override fun visitVarWithSuffix(ctx: mcfppParser.VarWithSuffixContext): Var<*> {
        //变量
        //没有数组选取
        val qwq: String = ctx.Identifier().text
        var re = if(currSelector == null) {
            val pwp = Function.currFunction.field.getVar(qwq)
            if(pwp != null) {
                if(MCFPPImVisitor.inLoopStatement(ctx) && pwp is MCFPPValue<*>){
                    pwp.toDynamic(true)
                }else{
                    pwp
                }
            }else{
                UnknownVar(qwq)
            }
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
                        m.replacedBy(m.assignedBy(v))
                    }
                }
            }
            return re
        }
    }
}