package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.analysis.TypeUsage
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.command.Commands

import top.mcfpp.command.FloatProviders

import top.mcfpp.Project.withCompilationContext
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.mcfppParser.Range1Context
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.StaticMemberView
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.Generic
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.MCFPPFuncGetter
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPType
import org.antlr.v4.runtime.ParserRuleContext
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

class MCFPPExprVisitor(
    private var enumType: MCFPPEnumType? = null,
    var processVarCache: ArrayList<Var<*>> = ArrayList()
): mcfppParserBaseVisitor<Var<*>>() {

    private var currSelector : Var<*>? = null
    private var expectedLiteral: Pair<Int, MCFPPType>? = null

    fun visitExpression(ctx: mcfppParser.ExpressionContext, expectedType: MCFPPType?): Var<*> {
        fun literalToken(node: ParserRuleContext): Int? = when {
            node is mcfppParser.NbtValueContext && (node.nbtList() != null || node.nbtCompound() != null) -> node.start.tokenIndex
            else -> node.children.orEmpty().filterIsInstance<ParserRuleContext>().singleOrNull()?.let(::literalToken)
        }
        val previous = expectedLiteral
        expectedLiteral = expectedType?.let { type -> literalToken(ctx)?.let { it to type } }
        return try { visitExpression(ctx) } finally { expectedLiteral = previous }
    }

    /**
     * 计算一个复杂表达式
     * @param ctx the parse tree
     * @return 表达式的结果
     */
    @Override
    override fun visitExpression(ctx: mcfppParser.ExpressionContext): Var<*> = withCompilationContext(ctx) {
        val l = Function.currFunction
        val f = NoStackFunction(TempPool.getFunctionIdentify("expression"),Function.currFunction)
        Function.currFunction = f
        val result = if(ctx.primary() != null){
            currSelector = null
            val q = visitPrimary(ctx.primary()).let { if (it is PropertyVar) it.get() else it }
            Function.currFunction = l
            l.commands.addAll(f.commands)
            q
        }else{
            currSelector = null
            val q = visitCommonBinaryOperatorExpression(ctx.commonBinaryOperatorExpression())
            Function.currFunction = l
            l.commands.addAll(f.commands)
            q
        }
        return if (result is StaticMemberView && result.declaration is MCFPPType) {
            val descriptor = result.declaration as MCFPPType
            top.mcfpp.analysis.StorageAccess.literal(top.mcfpp.type.MCFPPConcreteType.Type,
                top.mcfpp.analysis.CompilerValue.TypeValue(descriptor.typeId), types = mapOf(descriptor.typeId to descriptor))
        } else result
    }

    private var visitCommonBinaryOperatorExpressionRe : Var<*>? = null
    /**
     * 计算其他运算符，例如 a | b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    override fun visitCommonBinaryOperatorExpression(ctx: mcfppParser.CommonBinaryOperatorExpressionContext): Var<*> = withCompilationContext(ctx) {
        visitCommonBinaryOperatorExpressionRe = visitConditionalOrExpression(ctx.conditionalOrExpression(0))
        processVarCache.add(visitCommonBinaryOperatorExpressionRe!!)
        for (i in 1..<ctx.conditionalOrExpression().size) {
            visitCommonBinaryOperatorExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitCommonBinaryOperatorExpressionRe!!)
            processVarCache[processVarCache.lastIndex] = visitCommonBinaryOperatorExpressionRe!!
            var b: Var<*>? = visitConditionalOrExpression(ctx.conditionalOrExpression(i))
            if(b is MCFloat && !FloatProviders.enabled) b = b.toTempEntity()
            if(visitCommonBinaryOperatorExpressionRe!! !== MCFloat.ssObj){
                visitCommonBinaryOperatorExpressionRe = visitCommonBinaryOperatorExpressionRe!!.getTempVar()
            }
            visitCommonBinaryOperatorExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitCommonBinaryOperatorExpressionRe!!, b!!, ctx.op[i-1].text)
            processVarCache[processVarCache.size - 1] = visitCommonBinaryOperatorExpressionRe!!
        }
        processVarCache.remove(visitCommonBinaryOperatorExpressionRe!!)
        return visitCommonBinaryOperatorExpressionRe!!
    }

    /**
     * 计算一个或表达式。例如 a || b。
     * @param ctx the parse tree
     * @return 表达式的值
     */
    override fun visitConditionalOrExpression(ctx: mcfppParser.ConditionalOrExpressionContext): Var<*> = withCompilationContext(ctx) {
        var result = visitConditionalAndExpression(ctx.conditionalAndExpression(0))
        for (i in 1..<ctx.conditionalAndExpression().size) {
            result = shortCircuit(result, false) { visitConditionalAndExpression(ctx.conditionalAndExpression(i)) }
        }
        return result
    }

    /**
     * 计算一个与表达式。例如a && b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    //和
    @Override
    override fun visitConditionalAndExpression(ctx: mcfppParser.ConditionalAndExpressionContext): Var<*> = withCompilationContext(ctx) {
        var result = visitEqualityExpression(ctx.equalityExpression(0))
        for (i in 1..<ctx.equalityExpression().size) {
            result = shortCircuit(result, true) { visitEqualityExpression(ctx.equalityExpression(i)) }
        }
        return result
    }

    private fun shortCircuit(left: Var<*>, evaluateWhen: Boolean, right: () -> Var<*>): Var<*> {
        var constant = StorageAccess.snapshot(left)
        while (constant is CompilerValue.Typed) constant = constant.payload
        if (constant is CompilerValue.Bool) {
            if (constant.value != evaluateWhen) return left
            val value = right()
            if (value.isError || value.type.isSubOf(MCFPPBaseType.Bool)) return value
            LogProcessor.error("Logical operands must have boolean type")
            return UnknownVar("logical_operand")
        }
        val captured = StorageAccess.capture(left)
        if (captured.isError) return captured
        if (!captured.type.isSubOf(MCFPPBaseType.Bool)) {
            LogProcessor.error("Logical operands must have boolean type")
            return UnknownVar("logical_operand")
        }
        val condition = if (evaluateWhen) captured else StorageAccess.unary(captured, "!")
        if (condition.isError) return condition
        val result = MCFPPBaseType.Bool.buildUnConcrete(TempPool.getVarIdentify())
        StorageAccess.ensure(result)
        StorageAccess.write(result, captured)
        StorageAccess.materialize(result)
        val caller = Function.currFunction
        val values = StorageAccess.visibleValues(caller.scope) + result
        val before = StorageAccess.flowSnapshot(values)
        var branchName: String
        do {
            branchName = TempPool.getFunctionIdentify("logical_rhs")
        } while (listOf(GlobalScope.localNamespaces, GlobalScope.libNamespaces, GlobalScope.stdNamespaces)
                .any { namespaces -> namespaces[caller.namespace]?.scope?.getFunctionCandidates(branchName)?.isNotEmpty() == true })
        val branch = top.mcfpp.model.function.NoStackFunction(branchName, caller)
        caller.child.add(branch)
        GlobalScope.localNamespaces.getOrPut(branch.namespace) { top.mcfpp.model.Namespace(branch.namespace) }
            .scope.addFunction(branch, false)
        branch.runInFunction {
            val value = right()
            if (!value.isError && value.type.isSubOf(MCFPPBaseType.Bool)) StorageAccess.write(result, value)
            else if (!value.isError) LogProcessor.error("Logical operands must have boolean type")
            Unit
        }
        val evaluated = StorageAccess.flowSnapshot(values)
        StorageAccess.restoreFlow(before)
        val command = when (condition) {
            is BaseBool -> Command("execute if").build(condition.toCommandPart())
            else -> {
                LogProcessor.error("Logical operands must have boolean type")
                return UnknownVar("logical_operand")
            }
        }
        Function.addCommand(command.build("run").build(Commands.function(branch)))
        StorageAccess.restoreFlow(StorageAccess.joinFlow(listOf(before, evaluated)))
        return StorageAccess.read(result)
    }

    private var visitEqualityExpressionRe : Var<*>? = null
    /**
     * 计算一个等于或不等于表达式，例如a == b和a != b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitEqualityExpression(ctx: mcfppParser.EqualityExpressionContext): Var<*> = withCompilationContext(ctx)  {
        visitEqualityExpressionRe = visitRelationalExpression(ctx.relationalExpression(0))
        processVarCache.add(visitEqualityExpressionRe!!)
        for (i in 1..<ctx.relationalExpression().size) {
            visitEqualityExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitEqualityExpressionRe!!)
            processVarCache[processVarCache.lastIndex] = visitEqualityExpressionRe!!
            val b: Var<*> = visitRelationalExpression(ctx.relationalExpression(i))
            visitEqualityExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitEqualityExpressionRe!!, b, ctx.op[i-1].text)
            processVarCache[processVarCache.size - 1] = visitEqualityExpressionRe!!
        }
        processVarCache.remove(visitEqualityExpressionRe!!)
        return visitEqualityExpressionRe!!
    }

    private var visitRelationalExpressionRe : Var<*>? = null
    /**
     * 计算一个比较表达式，例如a > b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitRelationalExpression(ctx: mcfppParser.RelationalExpressionContext): Var<*> = withCompilationContext(ctx) {
        visitRelationalExpressionRe = visitAdditiveExpression(ctx.additiveExpression(0))
        processVarCache.add(visitRelationalExpressionRe!!)
        for (i in 1..<ctx.additiveExpression().size) {
            visitRelationalExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitRelationalExpressionRe!!)
            processVarCache[processVarCache.lastIndex] = visitRelationalExpressionRe!!
            val b: Var<*> = visitAdditiveExpression(ctx.additiveExpression(i))
            visitRelationalExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitRelationalExpressionRe!!, b, ctx.op[i-1].text)
            processVarCache[processVarCache.size - 1] = visitRelationalExpressionRe!!
        }
        processVarCache.remove(visitRelationalExpressionRe!!)
        return visitRelationalExpressionRe!!
    }

    private var visitAdditiveExpressionRe : Var<*>? = null
    /**
     * 计算一个加减法表达式，例如a + b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitAdditiveExpression(ctx: mcfppParser.AdditiveExpressionContext): Var<*> = withCompilationContext(ctx) {
        visitAdditiveExpressionRe = visitMultiplicativeExpression(ctx.multiplicativeExpression(0))
        processVarCache.add(visitAdditiveExpressionRe!!)
        for (i in 1..<ctx.multiplicativeExpression().size) {
            visitAdditiveExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitAdditiveExpressionRe!!)
            processVarCache[processVarCache.lastIndex] = visitAdditiveExpressionRe!!
            var b: Var<*>? = visitMultiplicativeExpression(ctx.multiplicativeExpression(i))
            if(b is MCFloat && !FloatProviders.enabled) {
                b = b.toTempEntity()
                if(visitAdditiveExpressionRe!! !== MCFloat.ssObj){
                    visitAdditiveExpressionRe = visitAdditiveExpressionRe!!.getTempVar()
                }
            }
            visitAdditiveExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitAdditiveExpressionRe!!, b!!, ctx.op[i-1].text)
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
    override fun visitMultiplicativeExpression(ctx: mcfppParser.MultiplicativeExpressionContext): Var<*> = withCompilationContext(ctx) {
        visitMultiplicativeExpressionRe = visitCastExpression(ctx.castExpression(0))
        processVarCache.add(visitMultiplicativeExpressionRe!!)
        for (i in 1..<ctx.castExpression().size) {
            visitMultiplicativeExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitMultiplicativeExpressionRe!!)
            processVarCache[processVarCache.lastIndex] = visitMultiplicativeExpressionRe!!
            var b: Var<*>? = visitCastExpression(ctx.castExpression(i))
            if(b is MCFloat && !FloatProviders.enabled) b = b.toTempEntity()
            if((!FloatProviders.enabled || visitMultiplicativeExpressionRe !is MCFloat) && visitMultiplicativeExpressionRe !== MCFloat.ssObj){
                visitMultiplicativeExpressionRe = visitMultiplicativeExpressionRe!!.getTempVar()
            }
            visitMultiplicativeExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitMultiplicativeExpressionRe!!, b!!, ctx.op[i-1].text)
            processVarCache[processVarCache.size - 1] = visitMultiplicativeExpressionRe!!
        }
        processVarCache.remove(visitMultiplicativeExpressionRe!!)
        return visitMultiplicativeExpressionRe!!
    }

    /**
     * 计算一个强制转换表达式。
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitCastExpression(ctx: mcfppParser.CastExpressionContext): Var<*> = withCompilationContext(ctx) {
        val a: Var<*> = visitUnaryExpression(ctx.unaryExpression())
        if(ctx.type() != null){
            return top.mcfpp.analysis.StorageAccess.view(a, MCFPPType.parseFromContextNotNull(ctx.type(), Function.currFunction.scope))
        }else{
            return a
        }
    }

    /**
     * 计算一个单目表达式。比如!a 或者 (int)a
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitUnaryExpression(ctx: mcfppParser.UnaryExpressionContext): Var<*> = withCompilationContext(ctx) {
        return if (ctx.rightVarExpression() != null) {
            visitRightVarExpression(ctx.rightVarExpression())
        } else {
            val a: Var<*> = visitUnaryExpression(ctx.unaryExpression())
            top.mcfpp.analysis.StorageAccess.unary(a, if (ctx.SUB() != null) "-" else "!")
        }
    }

    /**
     * 对获取到的变量进行包装处理
     *
     * @param ctx
     * @return
     */
    @Override
    override fun visitRightVarExpression(ctx: mcfppParser.RightVarExpressionContext): Var<*> = withCompilationContext(ctx) {
        return visitVarWithSelector(ctx.varWithSelector())
    }

    /**
     * 从类中选择一个成员。返回的成员包含了它所在的对象的信息
     *
     * @param ctx
     * @return
     */
    @Override
    override fun visitVarWithSelector(ctx: mcfppParser.VarWithSelectorContext): Var<*> = withCompilationContext(ctx) {
        resolveVarWithSelector(ctx, false)
    }

    /** Resolve an assignment target without calling its final property getter. */
    private var assignmentTarget: mcfppParser.VarWithSuffixContext? = null

    fun visitAssignableVarWithSelector(ctx: mcfppParser.VarWithSelectorContext): Var<*> = withCompilationContext(ctx) {
        val previous = assignmentTarget
        assignmentTarget = if (ctx.selector().isNotEmpty()) ctx.selector().last().`var`().varWithSuffix()
            else ctx.jvmAccessExpression().propertyOperator().primary().`var`()?.varWithSuffix()
        try {
            resolveVarWithSelector(ctx, true)
        } finally {
            assignmentTarget = previous
        }
    }

    private fun resolveVarWithSelector(ctx: mcfppParser.VarWithSelectorContext, preserveFinalProperty: Boolean): Var<*> {
        currSelector = null
        currSelector = visitJvmAccessExpression(ctx.jvmAccessExpression())
        if(currSelector is PropertyVar && (!preserveFinalProperty || ctx.selector().isNotEmpty())){
            currSelector = (currSelector as PropertyVar).get();
        }
        if(currSelector is UnknownVar && !currSelector!!.isError){
            val typeStr = ctx.jvmAccessExpression().text
            val type = MCFPPType.parseExpressionType(typeStr, Function.currFunction.scope, Function.currFunction)
            if(type == null){
                LogProcessor.error(TextTranslator.SYMBOL_NOT_DEFINED.translate(currSelector!!.identifier))
            }else{
                currSelector = StaticMemberView(type)
            }
        }
        val initial = currSelector
        if (ctx.selector().isNotEmpty() && initial is MCFPPTypeVar) {
            val type = top.mcfpp.analysis.StorageAccess.resolveTypeValue(initial)
            if (type == null) return UnknownVar("unbound_type").apply { isError = true }
            currSelector = StaticMemberView(type)
        }
        for (selector in ctx.selector()){
            visitSelector(selector)
        }
        if (!preserveFinalProperty && currSelector is PropertyVar) currSelector = (currSelector as PropertyVar).get()
        return currSelector!!
    }

    override fun visitJvmAccessExpression(ctx: mcfppParser.JvmAccessExpressionContext): Var<*> = withCompilationContext(ctx) {
        visitPropertyOperator(ctx.propertyOperator())
    }

    //字段操作器
    override fun visitPropertyOperator(ctx: mcfppParser.PropertyOperatorContext): Var<*> = withCompilationContext(ctx) {
        val re = visitPrimary(ctx.primary())
        for (operator in ctx.propertyOperatorExpression()){
            val identifier = operator.Identifier().text //要操作的字段名
            val value = visitExpression(operator.expression())
            val member = re.getMemberVar(identifier, Function.currFunction)   //获取字段
            val field = Var.checkMember(member, identifier)
            if (field.isError || field is UnknownVar || value.isError) return@withCompilationContext UnknownVar(identifier).apply { isError = true }
            field.replacedBy(field.assignedBy(value))
        }
        return re
    }

    override fun visitSelector(ctx: mcfppParser.SelectorContext): Var<*> = withCompilationContext(ctx) {
        if (currSelector is PropertyVar) currSelector = (currSelector as PropertyVar).get()
        //进入visitVar，currSelector作为成员选择的上下文
        currSelector = visitVar(ctx.`var`())
        return currSelector!!
    }

    /**
     * 一个初级表达式，可能是一个变量，也可能是一个数值
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitPrimary(ctx: mcfppParser.PrimaryContext): Var<*> = withCompilationContext(ctx) {
        if (ctx.`var`() != null) {
            //变量
            val qwq = visitVar(ctx.`var`())
            if(qwq is UnknownVar && !qwq.isError && ctx.parent.parent !is mcfppParser.VarWithSelectorContext){
                LogProcessor.error(TextTranslator.SYMBOL_NOT_DEFINED.translate(qwq.identifier))
            }
            return qwq
        } else if (ctx.value() != null) {
            return visitValue(ctx.value())
        } else if (ctx.range() != null){
            //是范围
            fun qwq(ctx: Range1Context): Var<*> {
                return if(ctx.`var`() != null){
                    visitVar(ctx.`var`())
                }else{
                    visitValue(ctx.value())
                }
            }
            val left = ctx.range().num1?.let { top.mcfpp.analysis.StorageAccess.capture(qwq(it)) }
            val pending = processVarCache.size
            left?.let(processVarCache::add)
            val right = try { ctx.range().num2?.let { qwq(it) } }
                finally { if (left != null) processVarCache.removeAt(pending) }
            if(left is MCNumber<*>? && right is MCNumber<*>?){
                return RangeVar.fromBounds(left, right)
            }else{
                LogProcessor.error("Range sides should be a number: ${left?.type} and ${right?.type}")
                return UnknownVar("range_" + UUID.randomUUID())
            }
        } else if (ctx.type() != null){
            return MCFPPTypeVar(MCFPPType.parseExpressionType(ctx.type().text, Function.currFunction.scope, Function.currFunction)?: run {
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
    override fun visitVar(ctx: mcfppParser.VarContext): Var<*> = withCompilationContext(ctx) {
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

    override fun visitBucketExpression(ctx: mcfppParser.BucketExpressionContext): Var<*> = withCompilationContext(ctx) {
        // Keep separate expression result fields while preserving outer values across nested calls.
        return MCFPPExprVisitor(processVarCache = processVarCache).visit(ctx.expression())
    }

    override fun visitFunctionCall(ctx: mcfppParser.FunctionCallContext): Var<*> = withCompilationContext(ctx) {
        //是函数调用，将已经计算好的中间量存储到栈中
        val spills = top.mcfpp.analysis.StorageAccess.spill(processVarCache)
        //函数的调用
        Function.addComment(ctx.text)
        //参数获取
        val normalArgs: ArrayList<Var<*>> = ArrayList()
        val originalArgs: ArrayList<Var<*>> = ArrayList()
        val readOnlyArgs: ArrayList<Var<*>> = ArrayList()
        val exprVisitor = MCFPPExprVisitor()
        val concreteExprVisitor = MCFPPReadonlyExprVisitor()
        for (expr in ctx.arguments().readOnlyArgs()?.expressionList()?.expression()?: emptyList()) {
            val arg = concreteExprVisitor.visit(expr)
            if(arg is UnknownVar){
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return UnknownVar("error_" + ctx.text).apply { isError = true }
            }else if(arg == null){
                LogProcessor.error("ReadOnly argument should be concrete: ${expr.text}")
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return UnknownVar("error_" + ctx.text).apply { isError = true }
            }
            readOnlyArgs.add(arg)
        }
        for (expr in ctx.arguments().normalArgs().expressionList()?.expression()?: emptyList()) {
            val arg = exprVisitor.visit(expr)!!
            TypeUsage.ordinaryDiagnostic(arg.type, top.mcfpp.analysis.StorageAccess.snapshot(arg))?.let {
                LogProcessor.error(it)
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return UnknownVar("error_" + ctx.text).apply { isError = true }
            }
            if(arg is UnknownVar){
                LogProcessor.error(TextTranslator.SYMBOL_NOT_DEFINED.translate(arg.identifier))
                return UnknownVar("error_" + ctx.text)
            }
            val captured = top.mcfpp.analysis.StorageAccess.capture(arg)
            if (captured.isError) {
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return captured
            }
            originalArgs.add(arg)
            normalArgs.add(captured)
            exprVisitor.processVarCache.add(captured)
        }
        //Try to get function
        val p = ctx.namespaceID().text.splitNamespaceID()
        val func = if(currSelector == null){
            GlobalScope.getFunction(p.first, p.second, readOnlyArgs, normalArgs)
        }else{
            if(p.first != null){
                LogProcessor.warn("Invalid namespace usage ${p.first} in function call ")
            }
            MCFPPFuncGetter.getFunction(currSelector!!,p.second, readOnlyArgs, normalArgs)
        }
        //Function invoke
        if (func !is UnknownFunction) {
            val passedArgs = normalArgs.mapIndexed { index, value ->
                if (func.normalParams.getOrNull(index)?.isStatic == true) originalArgs[index] else value
            }
            val returnVar = if(func is Generic<*>){
                if(readOnlyArgs.any { it is UnknownVar } || normalArgs.any { it is UnknownVar }){
                    UnknownVar("re")
                }else{
                    func.invoke(readOnlyArgs, passedArgs, currSelector)
                }
            }else if(func is NativeFunction){
                if(readOnlyArgs.any { it is UnknownVar } || normalArgs.any { it is UnknownVar }){
                    UnknownVar("re")
                }else{
                    func.invoke(readOnlyArgs, passedArgs, currSelector)
                }
            }else{
                if(normalArgs.any { it is UnknownVar }){
                    UnknownVar("re")
                }else {
                    func.invoke(passedArgs, currSelector)
                }
            }
            //函数树
            Function.currFunction.child.add(func)
            func.parent.add(Function.currFunction)
            top.mcfpp.analysis.StorageAccess.restore(spills)
            return if (returnVar.isError || returnVar.type == top.mcfpp.type.MCFPPPrivateType.Void) returnVar
                else top.mcfpp.analysis.StorageAccess.capture(returnVar)
        }
        //可能是模板的构造函数
        val declaration = GlobalScope.getTemplate(p.first, p.second)
        if(declaration != null) {
            fun failedTemplate(message: String? = null): UnknownVar {
                if (message != null) LogProcessor.error(message)
                Function.addComment("[Failed to compile]${ctx.text}")
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return UnknownVar("error_${ctx.text}").apply { isError = true }
            }
            val declarationErrors = Project.errorCount
            val template = if (declaration is GenericDataTemplate) {
                declaration.compile(readOnlyArgs) ?: return failedTemplate()
            } else {
                if (ctx.arguments().readOnlyArgs() != null)
                    return failedTemplate("Ordinary template '${declaration.identifier}' does not accept readonly arguments")
                declaration
            }
            if (Project.errorCount != declarationErrors) return failedTemplate()
            val selection = template.resolveConstructor(normalArgs)
            if (selection !is ParameterMatcher.TypeSelection.Selected) {
                when (selection) {
                    is ParameterMatcher.TypeSelection.Ambiguous -> LogProcessor.error("Ambiguous constructor '${ctx.namespaceID().text}': ${selection.functions.joinToString()}")
                    else -> LogProcessor.error("No constructor like: " + FunctionParam.getArgTypeNames(normalArgs) + " defined in class " + ctx.namespaceID().text)
                }
                Function.addComment("[Failed to compile]${ctx.text}")
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return UnknownVar("error_${ctx.text}").apply { isError = true }
            }
            val init = if (template is top.mcfpp.model.compound.TypeDataTemplate) {
                template.getType().buildUnConcrete(TempPool.getVarIdentify())
            } else {
                val receiver = template.getType().buildUnConcrete(TempPool.getVarIdentify()) as? DataTemplateObject
                if (receiver == null) {
                    top.mcfpp.analysis.StorageAccess.restore(spills)
                    return UnknownVar("error_${ctx.text}").apply { isError = true }
                }
                top.mcfpp.analysis.StorageAccess.initializeTemplateReceiver(receiver)
                receiver
            }
            selection.function.invoke(normalArgs, init)
            top.mcfpp.analysis.StorageAccess.restore(spills)
            //可能会对init进行替换
            return Function.currFunction.scope.getVar(init.identifier) ?: init
        }
        //没有找到函数
        LogProcessor.error("Function ${func.identifier}<${readOnlyArgs.joinToString(",") { it.type.typeName }}>(${normalArgs.map { it.type.typeName }.joinToString(",")}) not defined")
        Function.addComment("[Failed to Compile]${ctx.text}")
        func.invoke(normalArgs,currSelector)
        top.mcfpp.analysis.StorageAccess.restore(spills)
        return func.returnVar
    }

    override fun visitVarWithSuffix(ctx: mcfppParser.VarWithSuffixContext): Var<*> = withCompilationContext(ctx) {
        //变量
        //没有数组选取
        val qwq: String = ctx.Identifier().text
        var re = if(currSelector == null) {
            val member = Function.currFunction.scope.getVar(qwq, Function.currFunction)
            if (!member.second) {
                LogProcessor.error("Cannot access member $qwq")
                return@withCompilationContext UnknownVar(qwq).apply { isError = true }
            }
            val pwp = member.first
            if(pwp != null) {
                if (ctx === assignmentTarget) {
                    pwp
                }else{
                    top.mcfpp.analysis.StorageAccess.read(pwp)
                }
            }else{
                UnknownVar(qwq)
            }
        }else{
            if(currSelector is PropertyVar){
                currSelector = (currSelector as PropertyVar).get()
            }
            //获取成员
            val re  = currSelector!!.getMemberVar(qwq, Function.currFunction)
            if (re.first == null) {
                LogProcessor.error("Cannot get member $qwq")
                UnknownVar(qwq)
            }else if (!re.second){
                LogProcessor.error("Cannot access member $qwq")
                UnknownVar(qwq)
            }else{
                if (ctx === assignmentTarget) re.first!! else top.mcfpp.analysis.StorageAccess.read(re.first!!)
            }
        }
        if(re is UnknownVar && currSelector == null){
            //从类型获取
            val typeStr = ctx.Identifier().text
            val type = MCFPPType.parseExpressionType(typeStr, Function.currFunction.scope, Function.currFunction)
            if(type != null){
                re = StaticMemberView(type)
            }
        }
        if(re is UnknownVar && enumType != null && currSelector == null){
            //从枚举获取
            currSelector = StaticMemberView(enumType!!)
            val re2  = currSelector!!.getMemberVar(qwq, Function.currFunction)
            if (re2.first == null) {
                LogProcessor.error("Cannot get member ${enumType!!.simpleName}.$qwq")
            }else if (!re2.second){
                LogProcessor.error("Cannot access member ${enumType!!.simpleName}.$qwq")
            }else{
                re = re2.first!!
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
                if(re is PropertyVar){
                    re = re.get()
                }
                if (re is MCAny && re !is MCObject) re = StorageAccess.actualView(re)
                if(value.expression() != null){
                    if(re !is Indexable){
                        LogProcessor.error("Cannot index ${re.type}")
                        return UnknownVar("${re.identifier}_index_" + UUID.randomUUID())
                    }
                    //索引
                    val index = visit(value.expression())!!
                    re = (re as Indexable).getByIndex(index)
                }else {
                    if(re !is Indexable){
                        LogProcessor.error("Cannot index ${re.type}")
                        return UnknownVar("${re.identifier}_index_" + UUID.randomUUID())
                    }
                    //TODO 遍历索引
                }
            }
            return re
        }
    }

    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
    private fun closedLiteral(bound: top.mcfpp.analysis.DeclarationBinding.Bound): Var<*> {
        val value = bound.constant ?: run {
            LogProcessor.error("Literal '${bound.type}' requires a complete value")
            return UnknownVar("invalid_literal").apply { isError = true }
        }
        return top.mcfpp.analysis.StorageAccess.literal(bound.type, value, types = bound.descriptors)
    }

    override fun visitValue(ctx: mcfppParser.ValueContext): Var<*> = withCompilationContext(ctx) {
        if (ctx.LineString() != null || ctx.coordinate() != null || ctx.NULL() != null) {
            return closedLiteral(top.mcfpp.analysis.DeclarationBinding(Function.currFunction, emptyMap()).value(ctx))
        }
        if (ctx.multiLineStringLiteral()!=null){
            val stringArray = mutableListOf<String>()
            for(stringContext in ctx.multiLineStringLiteral().multiLineStringContent()){
                var r:String
                if(stringContext.MultiLineStrText()!=null) r= stringContext.MultiLineStrText().text
                else if(stringContext.MultiLineStringQuote()!=null) r= stringContext.MultiLineStringQuote().text
                else {
                    val expressionContext = stringContext.multiLineStringExpression().expression()
                    val res = top.mcfpp.analysis.StorageAccess.capture(visit(expressionContext))
                    var frozen = top.mcfpp.analysis.StorageAccess.snapshot(res)
                    while (frozen is top.mcfpp.analysis.CompilerValue.Typed) frozen = frozen.payload
                    r = when (frozen) {
                        is top.mcfpp.analysis.CompilerValue.Text -> frozen.value
                        is top.mcfpp.analysis.CompilerValue.Integral -> frozen.value.toString()
                        is top.mcfpp.analysis.CompilerValue.Bool -> frozen.value.toString()
                        is top.mcfpp.analysis.CompilerValue.FloatBits -> Float.fromBits(frozen.bits).toString()
                        is top.mcfpp.analysis.CompilerValue.DoubleBits -> Double.fromBits(frozen.bits).toString()
                        else -> {
                            LogProcessor.error("String interpolation requires an encodable scalar value")
                            return UnknownVar("invalid_string_interpolation").apply { isError = true }
                        }
                    }
                }
                stringArray.add(r)
            }
            val tailQuote = ctx.multiLineStringLiteral().TRIPLE_QUOTE_CLOSE().text
            if(tailQuote.length>3) {
                stringArray.add(tailQuote.substring(3,tailQuote.length))
            }
            return top.mcfpp.analysis.StorageAccess.literal(MCFPPBaseType.String,
                top.mcfpp.analysis.CompilerValue.Typed(MCFPPBaseType.String.typeId,
                    top.mcfpp.analysis.CompilerValue.Text(stringArray.joinToString(""))))
        } else if (ctx.nbtValue() != null){
            return visit(ctx.nbtValue())
        } else if (ctx.TargetSelector() != null){
            return SelectorVar(EntitySelector(ctx.TargetSelector()!!.text[1]))
        }
        throw IllegalArgumentException("value_" + ctx.text)
    }

    override fun visitCoordinateDimension(ctx: mcfppParser.CoordinateDimensionContext): Var<*> = withCompilationContext(ctx) {
        val relative = ctx.RelativeValue()?.text
        val prefix = relative?.take(1).orEmpty()
        val payload: top.mcfpp.analysis.CompilerValue = when {
            ctx.nbtInt() != null -> top.mcfpp.analysis.CompilerValue.Integral(ctx.nbtInt().text.toLong())
            ctx.nbtFloat() != null -> top.mcfpp.analysis.CompilerValue.FloatBits(ctx.nbtFloat().text.toNBTFloat().toRawBits())
            ctx.nbtDouble() != null -> top.mcfpp.analysis.CompilerValue.DoubleBits(ctx.nbtDouble().text.toNBTDouble().toRawBits())
            relative?.length == 1 -> top.mcfpp.analysis.CompilerValue.Integral(0)
            else -> relative?.drop(1)?.toLongOrNull()?.let { top.mcfpp.analysis.CompilerValue.Integral(it) }
                ?: relative?.drop(1)?.toFloatOrNull()?.let { top.mcfpp.analysis.CompilerValue.FloatBits(it.toRawBits()) }
                ?: run {
                    LogProcessor.error("Invalid relative value: ${ctx.text}")
                    return UnknownVar("invalid_coordinate").apply { isError = true }
                }
        }
        val type = top.mcfpp.type.MCFPPPrivateType.MCFPPCoordinateDimension
        top.mcfpp.analysis.StorageAccess.literal(type, top.mcfpp.analysis.CompilerValue.Typed(type.typeId,
            top.mcfpp.analysis.CompilerValue.Sequence(listOf(top.mcfpp.analysis.CompilerValue.Text(prefix), payload))))
    }

    override fun visitNbtValue(ctx: mcfppParser.NbtValueContext): Var<*> = withCompilationContext(ctx) {
        if (ctx.nbtCompound() == null && ctx.nbtList() == null) {
            return closedLiteral(top.mcfpp.analysis.DeclarationBinding(Function.currFunction, emptyMap()).nbtLiteral(ctx))
        }
        if(ctx.nbtCompound() != null){
            val fields = linkedMapOf<String, Var<*>>()
            for (kv in ctx.nbtCompound().nbtKeyValuePair()){
                val sourceKey = kv.key.text
                val key = if (sourceKey.startsWith("\"") || sourceKey.startsWith("'"))
                    (Tag.toNBT(sourceKey) as StringTag).value else sourceKey
                val value = top.mcfpp.analysis.StorageAccess.capture(visit(kv.expression())).also { processVarCache.add(it) }
                if (value.isError) return value
                fields[key] = value
            }
            val types = fields.values.map { it.type }.distinctBy { it.typeId }
            val element = when (types.size) {
                0 -> MCFPPBaseType.Any
                1 -> types.single()
                else -> top.mcfpp.type.MCFPPUnionType(*types.toTypedArray())
            }
            val expected = expectedLiteral?.takeIf { it.first == ctx.start.tokenIndex }?.second as? top.mcfpp.type.MCFPPDictType
            val contextual = expected?.takeIf { target -> fields.values.all {
                top.mcfpp.model.function.ParameterMatcher.accepts(it, target.generic.single())
            } }
            val typedFields = if (contextual == null) fields else fields.mapValues { (_, value) ->
                value.implicitCast(contextual.generic.single())
            }
            return top.mcfpp.analysis.StorageAccess.dictionaryLiteral(contextual ?: top.mcfpp.type.MCFPPDictType(element), typedFields)
        }else if(ctx.nbtList() != null){
            val valueList = ArrayList<Var<*>>()
            for (expr in ctx.nbtList().expression()){
                val value = top.mcfpp.analysis.StorageAccess.capture(visit(expr)).also { processVarCache.add(it) }
                if (value.isError) return value
                valueList.add(value)
            }
            val re = if(valueList.isEmpty()){
                val expected = expectedLiteral?.takeIf { it.first == ctx.start.tokenIndex }?.second as? MCFPPListType
                expected ?: MCFPPListType(top.mcfpp.type.MCFPPPrivateType.Wildcard)
            }else{
                val types = valueList.map { it.type }.distinctBy { it.typeId }
                val elementType = if (types.size == 1) types.single() else top.mcfpp.type.MCFPPUnionType(*types.toTypedArray())
                MCFPPListType(elementType)
            }
            return top.mcfpp.analysis.StorageAccess.listLiteral(re, valueList)
        }else {
            LogProcessor.error("Invalid NBT value")
            throw IllegalArgumentException("nbt:" + ctx.text)
        }
    }
}
