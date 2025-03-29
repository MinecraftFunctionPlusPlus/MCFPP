package top.mcfpp.antlr

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.DoubleTag
import net.querz.nbt.tag.LongTag
import net.querz.nbt.tag.StringTag
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.Class
import top.mcfpp.model.DataTemplate
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.MCFPPFuncGetter
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.generic.Generic
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.type.MCFPPGenericClassType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.BoolTag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil.toNBTByte
import top.mcfpp.util.NBTUtil.toNBTDouble
import top.mcfpp.util.NBTUtil.toNBTFloat
import top.mcfpp.util.NBTUtil.toNBTLong
import top.mcfpp.util.NBTUtil.toNBTShort
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

/**
 * 获取表达式结果用的visitor。解析并计算一个形如a+b*c的表达式。
 */
class MCFPPExprVisitor(private var defaultGenericClassType : MCFPPGenericClassType? = null, private var enumType: MCFPPEnumType? = null): mcfppParserBaseVisitor<Var<*>>() {

    private val tempVarCommandCache = HashMap<Var<*>, String>()

    var processVarCache : ArrayList<Var<*>> = ArrayList()
    fun clearCache(){ processVarCache.clear() }

    private var currSelector : Var<*>? = null

    /**
     * 计算一个复杂表达式
     * @param ctx the parse tree
     * @return 表达式的结果
     */
    @Override
    override fun visitExpression(ctx: mcfppParser.ExpressionContext): Var<*> {
        Project.ctx = ctx
        val l = Function.currFunction
        val f = NoStackFunction(TempPool.getFunctionIdentify("expression"),Function.currFunction)
        Function.currFunction = f
        return if(ctx.primary() != null){
            currSelector = null
            val q = visitPrimary(ctx.primary())
            Function.currFunction = l
            l.commands.addAll(f.commands)
            q
        }else{
            val q = visitCommonBinaryOperatorExpression(ctx.commonBinaryOperatorExpression())
            Function.currFunction = l
            l.commands.addAll(f.commands)
            q
        }
    }

    private var visitCommonBinaryOperatorExpressionRe : Var<*>? = null
    /**
     * 计算其他运算符，例如 a | b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    override fun visitCommonBinaryOperatorExpression(ctx: mcfppParser.CommonBinaryOperatorExpressionContext): Var<*> {
        Project.ctx = ctx
        visitConditionalOrExpressionRe = visitConditionalOrExpression(ctx.conditionalOrExpression(0))
        processVarCache.add(visitConditionalOrExpressionRe!!)
        for (i in 1..<ctx.conditionalOrExpression().size) {
            var b: Var<*>? = visitConditionalOrExpression(ctx.conditionalOrExpression(i))
            if(b is MCFloat) b = b.toTempEntity()
            if(visitConditionalOrExpressionRe!! != MCFloat.ssObj){
                visitConditionalOrExpressionRe = visitConditionalOrExpressionRe!!.getTempVar()
            }
            visitConditionalOrExpressionRe = visitConditionalOrExpressionRe!!.binaryComputation(b!!, ctx.commonBinaryOperator(i - 1).text)
            processVarCache[processVarCache.size - 1] = visitConditionalOrExpressionRe!!
        }
        processVarCache.remove(visitCommonBinaryOperatorExpressionRe!!)
        return visitConditionalOrExpressionRe!!
    }

    private var visitConditionalOrExpressionRe : Var<*>? = null
    /**
     * 计算一个或表达式。例如 a || b。
     * @param ctx the parse tree
     * @return 表达式的值
     */
    override fun visitConditionalOrExpression(ctx: mcfppParser.ConditionalOrExpressionContext): Var<*> {
        Project.ctx = ctx
        visitConditionalOrExpressionRe = visitConditionalAndExpression(ctx.conditionalAndExpression(0))
        processVarCache.add(visitConditionalOrExpressionRe!!)
        for (i in 1..<ctx.conditionalAndExpression().size) {
            var b: Var<*>? = visitConditionalAndExpression(ctx.conditionalAndExpression(i))
            if(b is MCFloat) b = b.toTempEntity()
            if(visitConditionalOrExpressionRe!! != MCFloat.ssObj){
                visitConditionalOrExpressionRe = visitConditionalOrExpressionRe!!.getTempVar()
            }
            visitConditionalOrExpressionRe = visitConditionalOrExpressionRe!!.binaryComputation(b!!, "||")
            processVarCache[processVarCache.size - 1] = visitConditionalOrExpressionRe!!
        }
        processVarCache.remove(visitConditionalOrExpressionRe!!)
        return visitConditionalOrExpressionRe!!
    }

    private var visitConditionalAndExpressionRe : Var<*>? = null
    /**
     * 计算一个与表达式。例如a && b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    //和
    @Override
    override fun visitConditionalAndExpression(ctx: mcfppParser.ConditionalAndExpressionContext): Var<*> {
        Project.ctx = ctx
        visitConditionalAndExpressionRe = visitEqualityExpression(ctx.equalityExpression(0))
        processVarCache.add(visitConditionalAndExpressionRe!!)
        for (i in 1..<ctx.equalityExpression().size) {
            val b: Var<*> = visitEqualityExpression(ctx.equalityExpression(i))
            visitConditionalAndExpressionRe = visitConditionalAndExpressionRe!!.binaryComputation(b, "&&")
            processVarCache[processVarCache.size - 1] = visitConditionalAndExpressionRe!!
        }
        processVarCache.remove(visitConditionalAndExpressionRe!!)
        return visitConditionalAndExpressionRe!!
    }

    /**
     * 计算一个等于或不等于表达式，例如a == b和a != b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitEqualityExpression(ctx: mcfppParser.EqualityExpressionContext): Var<*> {
        Project.ctx = ctx
        var re: Var<*> = visitRelationalExpression(ctx.relationalExpression(0))
        if (ctx.relationalExpression().size != 1) {
            val b: Var<*> = visitRelationalExpression(ctx.relationalExpression(1))
            if(!re.isTemp){
                re = re.getTempVar()
            }
            re = re.binaryComputation(b, ctx.op.text)
        }
        return re
    }

    /**
     * 计算一个比较表达式，例如a > b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitRelationalExpression(ctx: mcfppParser.RelationalExpressionContext): Var<*> {
        Project.ctx = ctx
        var re: Var<*> = visitAdditiveExpression(ctx.additiveExpression(0))
        if (ctx.additiveExpression().size != 1) {
            val b: Var<*> = visitAdditiveExpression(ctx.additiveExpression(1))
            re = re.binaryComputation(b, ctx.relationalOp().text)
        }
        return re
    }

    private var visitAdditiveExpressionRe : Var<*>? = null
    /**
     * 计算一个加减法表达式，例如a + b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitAdditiveExpression(ctx: mcfppParser.AdditiveExpressionContext): Var<*> {
        Project.ctx = ctx
        visitAdditiveExpressionRe = visitMultiplicativeExpression(ctx.multiplicativeExpression(0))
        processVarCache.add(visitAdditiveExpressionRe!!)
        for (i in 1..<ctx.multiplicativeExpression().size) {
            var b: Var<*>? = visitMultiplicativeExpression(ctx.multiplicativeExpression(i))
            if(b is MCFloat) {
                b = b.toTempEntity()
                if(visitAdditiveExpressionRe!! != MCFloat.ssObj){
                    visitAdditiveExpressionRe = visitAdditiveExpressionRe!!.getTempVar()
                }
            }
            visitAdditiveExpressionRe = visitAdditiveExpressionRe!!.binaryComputation(b!!, ctx.op.text)
            processVarCache[processVarCache.size - 1] = visitAdditiveExpressionRe!!
        }
        processVarCache.remove(visitAdditiveExpressionRe!!)
        return visitAdditiveExpressionRe!!
    }

    private var visitMultiplicativeExpressionRe : Var<*>? = null
    /**
     * 计算一个乘除法表达式，例如a * b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    //乘法
    @Override
    override fun visitMultiplicativeExpression(ctx: mcfppParser.MultiplicativeExpressionContext): Var<*> {
        Project.ctx = ctx
        visitMultiplicativeExpressionRe = visitUnaryExpression(ctx.unaryExpression(0))
        processVarCache.add(visitMultiplicativeExpressionRe!!)
        for (i in 1..<ctx.unaryExpression().size) {
            var b: Var<*>? = visitUnaryExpression(ctx.unaryExpression(i))
            if(b is MCFloat) b = b.toTempEntity()
            if(visitMultiplicativeExpressionRe != MCFloat.ssObj){
                visitMultiplicativeExpressionRe = visitMultiplicativeExpressionRe!!.getTempVar()
            }
            visitAdditiveExpressionRe = visitAdditiveExpressionRe!!.binaryComputation(b!!, ctx.op.text)
            processVarCache[processVarCache.size - 1] = visitMultiplicativeExpressionRe!!
        }
        processVarCache.remove(visitMultiplicativeExpressionRe!!)
        return visitMultiplicativeExpressionRe!!
    }

    /**
     * 计算一个单目表达式。比如!a 或者 (int)a
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitUnaryExpression(ctx: mcfppParser.UnaryExpressionContext): Var<*> {
        Project.ctx = ctx
        return if (ctx.rightVarExpression() != null) {
            visitRightVarExpression(ctx.rightVarExpression())
        } else if (ctx.unaryExpression() != null) {
            val a: Var<*> = visitUnaryExpression(ctx.unaryExpression())
            a.unaryComputation("!")
        } else {
            //类型强制转换
            visitCastExpression(ctx.castExpression())
        }
    }


    /**
     * 计算一个强制转换表达式。
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitCastExpression(ctx: mcfppParser.CastExpressionContext): Var<*> {
        Project.ctx = ctx
        val a: Var<*> = visitUnaryExpression(ctx.unaryExpression())
        return a.explicitCast(MCFPPType.parseFromString(ctx.type().text, Function.currFunction.field)?: run {
            LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
            MCFPPBaseType.Any
        })
    }

    /**
     * 对获取到的变量进行包装处理
     *
     * @param ctx
     * @return
     */
    @Override
    override fun visitRightVarExpression(ctx: mcfppParser.RightVarExpressionContext): Var<*> {
        val qwq = visitVarWithSelector(ctx.varWithSelector())
        return if(qwq is PropertyVar){
            qwq.getter()
        }else{
            qwq
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
        currSelector = null
        if(ctx.jvmAccessExpression() != null){
            currSelector = visitJvmAccessExpression(ctx.jvmAccessExpression())
            if(currSelector is UnknownVar){
                val typeStr = ctx.jvmAccessExpression().text
                val type = MCFPPType.parseFromString(typeStr, Function.currFunction.field)
                if(type == null){
                    LogProcessor.error(TextTranslator.VARIABLE_NOT_DEFINED.translate(currSelector!!.identifier))
                }else{
                    currSelector = ObjectVar(type)
                }
            }
        }else{
            val typeStr = ctx.type().text
            val type = MCFPPType.parseFromString(typeStr, Function.currFunction.field)
            if(type == null){
                if(ctx.selector().size == 0){
                    LogProcessor.error(TextTranslator.VARIABLE_NOT_DEFINED.translate(currSelector!!.identifier))
                }else{
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(typeStr))
                    currSelector = UnknownVar(TempPool.getFunctionIdentify("unknown"))
                }
            }else{
                currSelector = ObjectVar(type)
            }
        }
        for (selector in ctx.selector()){
            visitSelector(selector)
        }
        return currSelector!!
    }

    override fun visitJvmAccessExpression(ctx: mcfppParser.JvmAccessExpressionContext): Var<*> {
        val re = visitPropertyOperator(ctx.propertyOperator())
        return if(ctx.Identifier() != null){
            re.getJVM(ctx.Identifier().text)
        }else{
            re
        }
    }

    override fun visitPropertyOperator(ctx: mcfppParser.PropertyOperatorContext): Var<*> {
        val re = visitPrimary(ctx.primary())
        for (operator in ctx.propertyOperatorExpression()){
            val identifier = operator.Identifier().text
            val value = visitExpression(operator.expression())
            val member = re.getMemberVar(identifier, re.getAccess(Function.currFunction))
            val field = Var.checkMember(member, identifier)
            field.replacedBy(field.assignedBy(value))
        }
        return re
    }

    override fun visitSelector(ctx: mcfppParser.SelectorContext?): Var<*> {
        //进入visitVar，currSelector作为成员选择的上下文
        currSelector = visitVar(ctx!!.`var`())
        return currSelector!!
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
            val qwq = visitVar(ctx.`var`())
            if(qwq is UnknownVar && ctx.parent.parent !is mcfppParser.VarWithSelectorContext){
                LogProcessor.error(TextTranslator.VARIABLE_NOT_DEFINED.translate(qwq.identifier))
            }
            return qwq
        } else if (ctx.value() != null) {
            return visitValue(ctx.value())
        } else if (ctx.range() != null){
            //是范围
            val left = ctx.range().num1?.let { visitVar(it) }
            val right = ctx.range().num2?.let { visitVar(it) }
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
        } else if (ctx.type() != null){
            return MCFPPTypeVar(MCFPPType.parseFromString(ctx.type().text, Function.currFunction.field)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                MCFPPBaseType.Any
            })
        } else {
            //this或者super
            val re: Var<*>? = Function.currField.getVar(ctx.text)
            if (re == null) {
                LogProcessor.error("${ctx.text} can only be used in member functions.")
                return UnknownVar("error_" + ctx.text)
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
        return if (ctx.varWithSuffix() != null) {
            visitVarWithSuffix(ctx.varWithSuffix())
        } else if (ctx.bucketExpression() != null) {
            // '(' expression ')'
            visitBucketExpression(ctx.bucketExpression())
        } else {
            //函数调用
            visitFunctionCall(ctx.functionCall())
        }
    }

    override fun visitBucketExpression(ctx: mcfppParser.BucketExpressionContext): Var<*> {
        return MCFPPExprVisitor().visit(ctx.expression())
    }

    override fun visitFunctionCall(ctx: mcfppParser.FunctionCallContext): Var<*> {
        //是函数调用，将已经计算好的中间量存储到栈中
        for (v in processVarCache){
            v.storeToStack()
        }
        //函数的调用
        Function.addComment(ctx.text)
        //参数获取
        val normalArgs: ArrayList<Var<*>> = ArrayList()
        val readOnlyArgs: ArrayList<Var<*>> = ArrayList()
        val exprVisitor = MCFPPExprVisitor()
        for (expr in ctx.arguments().readOnlyArgs()?.expressionList()?.expression()?: emptyList()) {
            val arg = exprVisitor.visit(expr)!!
            if(arg is UnknownVar){
                LogProcessor.error(TextTranslator.VARIABLE_NOT_DEFINED.translate(arg.identifier))
            }
            readOnlyArgs.add(arg)
        }
        for (expr in ctx.arguments().normalArgs().expressionList()?.expression()?: emptyList()) {
            val arg = exprVisitor.visit(expr)!!
            if(arg is UnknownVar){
                LogProcessor.error(TextTranslator.VARIABLE_NOT_DEFINED.translate(arg.identifier))
            }
            normalArgs.add(arg)
        }
        //获取函数
        val p = ctx.namespaceID().text.splitNamespaceID()
        val func = if(currSelector == null){
            GlobalField.getFunction(p.first, p.second, readOnlyArgs, normalArgs)
        }else{
            if(p.first != null){
                LogProcessor.warn("Invalid namespace usage ${p.first} in function call ")
            }
            MCFPPFuncGetter().getFunction(currSelector!!,p.second, readOnlyArgs, normalArgs)
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
            ptr.isNull = false
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
        if(enumType != null && currSelector == null){
            currSelector = ObjectVar(enumType!!)
        }
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
                LogProcessor.error("Cannot get member $qwq")
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
            if(re is UnknownVar){
                LogProcessor.error("${re.identifier} is not defined")
                return UnknownVar("${re.identifier}_member_" + UUID.randomUUID())
            }
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

    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
    override fun visitValue(ctx: mcfppParser.ValueContext): Var<*> {
        //常量
        if (ctx.LineString() != null) {
            val r: String = ctx.LineString().text
            return MCStringConcrete(StringTag(r.substring(1, r.length - 1)))
        } else if (ctx.multiLineStringLiteral()!=null){
            val stringArray = mutableListOf<String>()
            var isConcrete = true
            for(stringContext in ctx.multiLineStringLiteral().multiLineStringContent()){
                var r:String
                if(stringContext.MultiLineStrText()!=null) r= stringContext.MultiLineStrText().text
                else if(stringContext.MultiLineStringQuote()!=null) r= stringContext.MultiLineStringQuote().text
                else {
                    val expressionContext = stringContext.multiLineStringExpression().expression()
                    //TODO: 这边只是简单写了一下有解析值的情况
                    val res = visit(expressionContext) //没有解析值的话，应该变成text
                    if(res!=null && res !is MCFPPValue<*>){ isConcrete = false } //这个条件就是说，整个模版中出现没有解析值的情况了
                    r = if(res is MCIntConcrete){
                        res.value.toString()
                    } else{
                        res.toString()
                    }
                }
                stringArray.add(r)
            }
            val tailQuote = ctx.multiLineStringLiteral().TRIPLE_QUOTE_CLOSE().text
            if(tailQuote.length>3) {
                stringArray.add(tailQuote.substring(3,tailQuote.length))
            }
            return MCStringConcrete(StringTag(stringArray.joinToString("")) ) //没有解析值就变不了MCString了
        } else if (ctx.nbtValue() != null){
            return visit(ctx.nbtValue())
        } else if (ctx.TargetSelector() != null){
            return SelectorVar(EntitySelector(ctx.TargetSelector()!!.text[1]))
        } else if(ctx.coordinate() != null){
            val dimensions = ctx.coordinate().coordinateDimension().map { visit(it) }
            if(dimensions.size == 3){
                return Pos3Var().apply {
                    x.assignedBy(dimensions[0])
                    y.assignedBy(dimensions[1])
                    z.assignedBy(dimensions[2])
                }
            }
            return Pos2Var().apply {
                x.assignedBy(dimensions[0])
                z.assignedBy(dimensions[1])
            }
        }
        throw IllegalArgumentException("value_" + ctx.text)
    }

    override fun visitCoordinateDimension(ctx: mcfppParser.CoordinateDimensionContext): Var<*> {
        if(ctx.nbtInt() != null){
            return PosDimension("", ctx.nbtInt().text.toInt())
        }else if(ctx.nbtFloat() != null) {
            return PosDimension("", ctx.nbtFloat().text.toFloat())
        }else if(ctx.nbtDouble() != null){
            return PosDimension("", ctx.nbtDouble().text.toDouble())
        }else{
            //RelativeValue
            val str = ctx.RelativeValue().text
            if(str.length == 1){
                return PosDimension(str, 0)
            }
            val expr = str.substring(1)
            //尝试转换为数字
            var num: Number? = expr.toIntOrNull()
            if(num != null){
                return PosDimension(str[0].toString(), num)
            }
            num = expr.toFloatOrNull()
            if(num != null){
                return PosDimension(str[0].toString(), num)
            }
            LogProcessor.error("Invalid relative value: $expr")
            return PosDimension(str[0].toString(), 0)
        }
    }

    private lateinit var path : NBTPath


    override fun visitNbtValue(ctx: mcfppParser.NbtValueContext): Var<*> {
        if(ctx.LineString() != null) {
            return MCStringConcrete(StringTag(ctx.LineString().text))
        }else if(ctx.nbtBool() != null){
            return NBTBasedDataConcrete(BoolTag(ctx.nbtBool().text == "true"))
        }else if(ctx.nbtByte() != null){
            return MCByteConcrete(ctx.nbtByte().text.toNBTByte())
        }else if(ctx.nbtShort() != null){
            return MCShortConcrete(ctx.nbtShort().text.toNBTShort())
        }else if(ctx.nbtInt() != null) {
            return MCIntConcrete(ctx.nbtInt().text.toInt())
        }else if(ctx.nbtLong() != null){
            return MCLongConcrete(LongTag(ctx.nbtLong().text.toNBTLong()))
        }else if(ctx.nbtFloat() != null){
            return MCFloatConcrete(ctx.nbtFloat().text.toNBTFloat())
        }else if(ctx.nbtDouble() != null) {
            return MCDoubleConcrete(DoubleTag(ctx.nbtDouble().text.toNBTDouble()))
        }else if(ctx.nbtCompound() != null){
            val compound = NBTDictionaryConcrete(HashMap())
            for (kv in ctx.nbtCompound().nbtKeyValuePair()){
                val key = kv.Identifier().text
                val value = visit(kv.expression())
                val v = value.type.buildUnConcrete(key)
                compound.value[key] = v.assignedBy(value)
            }
            return compound
        }else if(ctx.nbtList() != null){
            val valueList = ArrayList<Var<*>>()
            for (expr in ctx.nbtList().expression()){
                valueList.add(visit(expr))
            }
            val re = if(valueList.isEmpty()){
                NBTListConcrete.getEmpty()
            }else{
                NBTListConcrete(valueList, "", valueList.first().type)
            }
            return if(re.value.all { it is MCFPPValue<*> }){
                re
            }else{
                re.toDynamic(false)
            }
        }else if(ctx.nbtByteArray() != null){
            return NBTBasedDataConcrete(SNBTUtil.fromSNBT(ctx.nbtByteArray().text))
        }else if(ctx.nbtIntArray() != null) {
            return NBTBasedDataConcrete(SNBTUtil.fromSNBT(ctx.nbtIntArray().text))
        }else if(ctx.nbtLongArray() != null) {
            return NBTBasedDataConcrete(SNBTUtil.fromSNBT(ctx.nbtLongArray().text))
        }else {
            LogProcessor.error("Invalid NBT value")
            throw IllegalArgumentException("nbt:" + ctx.text)
        }
    }

}