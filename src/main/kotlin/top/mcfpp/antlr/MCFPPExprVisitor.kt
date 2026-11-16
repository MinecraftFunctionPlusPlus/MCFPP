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
import top.mcfpp.mni.expand.MacroCallPipeline
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.MCFPPFuncGetter
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPDictType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPReferenceType
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
    private var superSelector: DataTemplate? = null
    private var expectedLiteral: Pair<Int, MCFPPType>? = null
    private var expectedFunction: Pair<Int, top.mcfpp.type.MCFPPFunctionType>? = null

    private fun integralLiteral(ctx: ParserRuleContext): Var<*>? =
        when (val parsed = top.mcfpp.analysis.IntegralLiteralSyntax.parse(ctx)) {
            is top.mcfpp.analysis.IntegralLiteralSyntax.Result.Value -> StorageAccess.literal(parsed.type, parsed.value)
            is top.mcfpp.analysis.IntegralLiteralSyntax.Result.Invalid -> {
                LogProcessor.error(parsed.diagnostic)
                UnknownVar("invalid_integer_literal").apply { isError = true }
            }
            null -> null
        }

    fun visitExpression(ctx: mcfppParser.ExpressionContext, expectedType: MCFPPType?): Var<*> {
        fun literalToken(node: ParserRuleContext): Int? = when {
            node is mcfppParser.NbtValueContext && (node.nbtList() != null || node.nbtCompound() != null) -> node.start.tokenIndex
            node is mcfppParser.InlineLambdaContext && top.mcfpp.analysis.TemplateLiteralSupport.ambiguousObjectEntries(node) != null -> node.start.tokenIndex
            else -> node.children.orEmpty().filterIsInstance<ParserRuleContext>().singleOrNull()?.let(::literalToken)
        }
        val previous = expectedLiteral
        val previousFunction = expectedFunction
        expectedLiteral = expectedType?.let { type -> literalToken(ctx)?.let { it to type } }
        expectedFunction = (expectedType as? top.mcfpp.type.MCFPPFunctionType)?.let { ctx.start.tokenIndex to it }
        return try { visitExpression(ctx) } finally { expectedLiteral = previous; expectedFunction = previousFunction }
    }

    fun visitValue(ctx: mcfppParser.ValueContext, expectedType: MCFPPType?): Var<*> {
        val nbt = ctx.nbtValue()?.takeIf { it.nbtList() != null || it.nbtCompound() != null }
        val previous = expectedLiteral
        expectedLiteral = expectedType?.let { type -> nbt?.let { it.start.tokenIndex to type } }
        return try { visitValue(ctx) } finally { expectedLiteral = previous }
    }

    /**
     * 计算一个复杂表达式
     * @param ctx the parse tree
     * @return 表达式的结果
     */
    @Override
    override fun visitExpression(ctx: mcfppParser.ExpressionContext): Var<*> = withCompilationContext(ctx) {
        top.mcfpp.analysis.StructuredBindings.literal(ctx)?.let { return@withCompilationContext it }
        val lambdaCall = ctx.lambdaCallExpression()
        if (lambdaCall != null) return@withCompilationContext evaluateInTemporary { visitLambdaCall(lambdaCall) }
        val result = evaluateInTemporary {
            if(ctx.primary() != null){
                currSelector = null
                visitPrimary(ctx.primary()).let { if (it is PropertyVar) it.get() else it }
            }else{
                currSelector = null
                visitCommonBinaryOperatorExpression(ctx.commonBinaryOperatorExpression())
            }
        }
        return@withCompilationContext if (result is StaticMemberView && result.declaration is MCFPPType) {
            val descriptor = result.declaration as MCFPPType
            top.mcfpp.analysis.StorageAccess.literal(top.mcfpp.type.MCFPPConcreteType.Type,
                top.mcfpp.analysis.CompilerValue.TypeValue(descriptor.typeId), types = mapOf(descriptor.typeId to descriptor))
        } else top.mcfpp.core.lang.entity.EntityDataAddress.selected(result)?.let(StorageAccess::read) ?: result
    }

    /** Real condition accessor: if/else-if/while no longer expose their condition as `expression()`. */
    override fun visitConditionExpression(ctx: mcfppParser.ConditionExpressionContext): Var<*> = withCompilationContext(ctx) {
        top.mcfpp.analysis.StructuredBindings.literal(ctx)?.let { return@withCompilationContext it }
        val result = evaluateInTemporary {
            currSelector = null
            ctx.primary()?.let { primary ->
                visitPrimary(primary).let { if (it is PropertyVar) it.get() else it }
            } ?: visitCommonBinaryOperatorExpression(ctx.commonBinaryOperatorExpression())
        }
        top.mcfpp.core.lang.entity.EntityDataAddress.selected(result)?.let(StorageAccess::read) ?: result
    }

    private fun evaluateInTemporary(block: () -> Var<*>): Var<*> {
        val caller = Function.currFunction
        val temporary = NoStackFunction(TempPool.getFunctionIdentify("expression"), caller)
        Function.currFunction = temporary
        return try {
            block()
        } finally {
            Function.currFunction = caller
            caller.commands.addAll(temporary.commands)
            caller.nativeResultSlots += temporary.nativeResultSlots
        }
    }

    private fun visitLambdaCall(ctx: mcfppParser.LambdaCallExpressionContext): Var<*> {
        if (ctx.functionCall() != null && ctx.selector().isEmpty()) {
            val binding = top.mcfpp.analysis.StructuredBindings.name(ctx.functionCall().namespaceID(), Function.currFunction)
            if (binding is top.mcfpp.analysis.StructuredNameBinding.Macros)
                return MacroCallPipeline.visitFunctionCall(ctx.functionCall(), Function.currFunction, ctx.inlineLambda(), binding.candidates)
                    ?: UnknownVar("error_macro").apply { isError = true }
            if (binding == null)
                MacroCallPipeline.visitFunctionCall(ctx.functionCall(), Function.currFunction, ctx.inlineLambda())?.let { return it }
        }
        top.mcfpp.analysis.lambda.LambdaCollections.global(ctx.functionCall(), ctx.inlineLambda(), ctx.varWithSuffix())?.let {
            currSelector = top.mcfpp.analysis.lambda.LambdaCollections.invoke(null, it, processVarCache)
            for (selector in ctx.selector()) visitSelector(selector)
            return currSelector!!
        }
        top.mcfpp.core.lang.context.ContextCallbacks.global(ctx.functionCall(), ctx.inlineLambda(), ctx.varWithSuffix())?.let {
            return top.mcfpp.core.lang.context.ContextCallbackLowering.invoke(Void, it, processVarCache)
        }
        if (ctx.jvmAccessExpression() != null) {
            currSelector = null
            currSelector = visitJvmAccessExpression(ctx.jvmAccessExpression())
            if (currSelector is PropertyVar) currSelector = (currSelector as PropertyVar).get()
            val before = ctx.selector().filter { it.start.tokenIndex < ctx.inlineLambda().start.tokenIndex }
            for (selector in before.dropLast(1)) visitSelector(selector)
            val read = before.lastOrNull()?.let {
                top.mcfpp.core.lang.entity.EntityReadCallbacks.selector(it, ctx.inlineLambda())
            }
            if (read != null) {
                currSelector = top.mcfpp.core.lang.ref.ReferenceReadCallbacks.invoke(selectedReference(currSelector!!), read, processVarCache)
                for (selector in ctx.selector().drop(before.size)) visitSelector(selector)
                return currSelector!!
            }
            val context = before.lastOrNull()?.let {
                top.mcfpp.core.lang.context.ContextCallbacks.selector(it, ctx.inlineLambda(), currSelector!!.type)
            }
            if (context != null) {
                if (ctx.selector().size != before.size) {
                    LogProcessor.error("Context '${context.name}' returns void; its callback result cannot be selected")
                    return UnknownVar("context_result").apply { isError = true }
                }
                currSelector = top.mcfpp.core.lang.context.ContextCallbackLowering.invoke(currSelector!!, context, processVarCache)
                return currSelector!!
            }
            val collection = before.lastOrNull()?.let {
                top.mcfpp.analysis.lambda.LambdaCollections.selector(it, ctx.inlineLambda())
            }
            if (collection != null && top.mcfpp.analysis.lambda.LambdaCollections.supports(currSelector!!.type, collection.name)) {
                currSelector = top.mcfpp.analysis.lambda.LambdaCollections.invoke(currSelector!!, collection, processVarCache)
                for (selector in ctx.selector().drop(before.size)) visitSelector(selector)
                return currSelector!!
            }
            LogProcessor.error("Trailing lambdas on this receiver have no collection target")
            return UnknownVar("lambda_call_${ctx.start.tokenIndex}").apply { isError = true }
        }
        val call = ctx.functionCall()
        val name = call?.namespaceID()?.text ?: ctx.varWithSuffix()?.takeIf { it.identifierSuffix().isEmpty() }?.Identifier()?.text
        if (name == null || ctx.selector().isNotEmpty()) {
            LogProcessor.error("Trailing callback calls on receivers require receiver binding")
            return UnknownVar("lambda_call_${ctx.start.tokenIndex}")
        }
        return top.mcfpp.analysis.lambda.LambdaLowering.call(name, call?.arguments(), ctx.inlineLambda(), processVarCache,
            call?.namespaceID() ?: ctx.varWithSuffix())
    }

    /**
     * 计算其他运算符，例如 a | b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    override fun visitCommonBinaryOperatorExpression(ctx: mcfppParser.CommonBinaryOperatorExpressionContext): Var<*> = withCompilationContext(ctx) {
        var visitCommonBinaryOperatorExpressionRe = visitConditionalOrExpression(ctx.conditionalOrExpression(0))
        processVarCache.add(visitCommonBinaryOperatorExpressionRe)
        for (i in 1..<ctx.conditionalOrExpression().size) {
            visitCommonBinaryOperatorExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitCommonBinaryOperatorExpressionRe)
            processVarCache[processVarCache.lastIndex] = visitCommonBinaryOperatorExpressionRe
            var b: Var<*>? = visitConditionalOrExpression(ctx.conditionalOrExpression(i))
            if(b is MCFloat && !FloatProviders.enabled) b = b.toTempEntity()
            if(visitCommonBinaryOperatorExpressionRe !== MCFloat.ssObj){
                visitCommonBinaryOperatorExpressionRe = visitCommonBinaryOperatorExpressionRe.getTempVar()
            }
            visitCommonBinaryOperatorExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitCommonBinaryOperatorExpressionRe, b!!, ctx.op[i-1].text)
            processVarCache[processVarCache.size - 1] = visitCommonBinaryOperatorExpressionRe
        }
        processVarCache.remove(visitCommonBinaryOperatorExpressionRe)
        return visitCommonBinaryOperatorExpressionRe
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
            (left as? top.mcfpp.core.lang.bool.PresenceBool)?.assume(evaluateWhen)
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

    /**
     * 计算一个等于或不等于表达式，例如a == b和a != b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitEqualityExpression(ctx: mcfppParser.EqualityExpressionContext): Var<*> = withCompilationContext(ctx)  {
        if (ctx.relationalExpression().size == 2 && ctx.op.single().text in setOf("==", "!=")) {
            val operands = ctx.relationalExpression()
            val nullIndex = operands.indexOfFirst { it.text == "null" }
            if (nullIndex >= 0) {
                var node: org.antlr.v4.runtime.tree.ParseTree = operands[1 - nullIndex]
                while (node !is mcfppParser.VarWithSelectorContext && node.childCount == 1) node = node.getChild(0)
                if (node is mcfppParser.VarWithSelectorContext) {
                    val address = visitAssignableVarWithSelector(node)
                    if (address is PropertyVar) return@withCompilationContext StorageAccess.presence(address, ctx.op.single().text == "!=")
                    return@withCompilationContext StorageAccess.binary(address,
                        visitRelationalExpression(operands[nullIndex]), ctx.op.single().text)
                }
            }
        }
        var visitEqualityExpressionRe = visitRelationalExpression(ctx.relationalExpression(0))
        processVarCache.add(visitEqualityExpressionRe)
        for (i in 1..<ctx.relationalExpression().size) {
            visitEqualityExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitEqualityExpressionRe)
            processVarCache[processVarCache.lastIndex] = visitEqualityExpressionRe
            val b: Var<*> = visitRelationalExpression(ctx.relationalExpression(i))
            visitEqualityExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitEqualityExpressionRe, b, ctx.op[i-1].text)
            processVarCache[processVarCache.size - 1] = visitEqualityExpressionRe
        }
        processVarCache.remove(visitEqualityExpressionRe)
        return visitEqualityExpressionRe
    }

    /**
     * 计算一个比较表达式，例如a > b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitRelationalExpression(ctx: mcfppParser.RelationalExpressionContext): Var<*> = withCompilationContext(ctx) {
        var visitRelationalExpressionRe = visitAdditiveExpression(ctx.additiveExpression(0))
        processVarCache.add(visitRelationalExpressionRe)
        for (i in 1..<ctx.additiveExpression().size) {
            visitRelationalExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitRelationalExpressionRe)
            processVarCache[processVarCache.lastIndex] = visitRelationalExpressionRe
            val b: Var<*> = visitAdditiveExpression(ctx.additiveExpression(i))
            visitRelationalExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitRelationalExpressionRe, b, ctx.op[i-1].text)
            processVarCache[processVarCache.size - 1] = visitRelationalExpressionRe
        }
        processVarCache.remove(visitRelationalExpressionRe)
        return visitRelationalExpressionRe
    }

    /**
     * 计算一个加减法表达式，例如a + b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitAdditiveExpression(ctx: mcfppParser.AdditiveExpressionContext): Var<*> = withCompilationContext(ctx) {
        var visitAdditiveExpressionRe = visitMultiplicativeExpression(ctx.multiplicativeExpression(0))
        processVarCache.add(visitAdditiveExpressionRe)
        for (i in 1..<ctx.multiplicativeExpression().size) {
            visitAdditiveExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitAdditiveExpressionRe)
            processVarCache[processVarCache.lastIndex] = visitAdditiveExpressionRe
            var b: Var<*>? = visitMultiplicativeExpression(ctx.multiplicativeExpression(i))
            if(b is MCFloat && !FloatProviders.enabled) {
                b = b.toTempEntity()
                if(visitAdditiveExpressionRe !== MCFloat.ssObj){
                    visitAdditiveExpressionRe = visitAdditiveExpressionRe.getTempVar()
                }
            }
            visitAdditiveExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitAdditiveExpressionRe, b!!, ctx.op[i-1].text)
            processVarCache[processVarCache.size - 1] = visitAdditiveExpressionRe
        }
        processVarCache.remove(visitAdditiveExpressionRe)
        return visitAdditiveExpressionRe
    }

    /**
     * 计算一个乘除法表达式，例如a * b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    //乘法
    @Override
    override fun visitMultiplicativeExpression(ctx: mcfppParser.MultiplicativeExpressionContext): Var<*> = withCompilationContext(ctx) {
        var visitMultiplicativeExpressionRe = visitCastExpression(ctx.castExpression(0))
        processVarCache.add(visitMultiplicativeExpressionRe)
        for (i in 1..<ctx.castExpression().size) {
            visitMultiplicativeExpressionRe = top.mcfpp.analysis.StorageAccess.capture(visitMultiplicativeExpressionRe)
            processVarCache[processVarCache.lastIndex] = visitMultiplicativeExpressionRe
            var b: Var<*>? = visitCastExpression(ctx.castExpression(i))
            if(b is MCFloat && !FloatProviders.enabled) b = b.toTempEntity()
            if((!FloatProviders.enabled || visitMultiplicativeExpressionRe !is MCFloat) && visitMultiplicativeExpressionRe !== MCFloat.ssObj){
                visitMultiplicativeExpressionRe = visitMultiplicativeExpressionRe.getTempVar()
            }
            visitMultiplicativeExpressionRe = top.mcfpp.analysis.StorageAccess.binary(visitMultiplicativeExpressionRe, b!!, ctx.op[i-1].text)
            processVarCache[processVarCache.size - 1] = visitMultiplicativeExpressionRe
        }
        processVarCache.remove(visitMultiplicativeExpressionRe)
        return visitMultiplicativeExpressionRe
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
        integralLiteral(ctx)?.let { return@withCompilationContext it }
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
        assignmentTarget = if (ctx.selector().isNotEmpty()) ctx.selector().last().`var`()?.varWithSuffix()
            else ctx.jvmAccessExpression().propertyOperator().primary().`var`()?.varWithSuffix()
        try {
            resolveVarWithSelector(ctx, true)
        } finally {
            assignmentTarget = previous
        }
    }

    private fun resolveVarWithSelector(ctx: mcfppParser.VarWithSelectorContext, preserveFinalProperty: Boolean): Var<*> {
        currSelector = null
        superSelector = null
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
        for ((index, selector) in ctx.selector().withIndex()){
            val expected = expectedFunction?.takeIf { it.first == ctx.start.tokenIndex }?.second
            val member = selector.`var`()?.varWithSuffix()?.takeIf { it.identifierSuffix().isEmpty() }
            if (!preserveFinalProperty && index == ctx.selector().lastIndex && expected != null && member != null) {
                val receiver = if (currSelector is PropertyVar) (currSelector as PropertyVar).get() else currSelector!!
                if (top.mcfpp.analysis.lambda.LambdaMemberReference.hasMethod(receiver.type, member.Identifier().text))
                    return top.mcfpp.analysis.lambda.LambdaMemberReference.create(receiver, member.Identifier().text, expected, selector)
            }
            visitSelector(selector)
        }
        if (!preserveFinalProperty && currSelector is PropertyVar) currSelector = selectedMember(currSelector!!)
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
        val arguments = ctx.`var`()?.functionCall()?.arguments() ?: ctx.arguments()
        arguments?.let(LegacyReferenceSyntax::argument)?.let {
            LogProcessor.error(LegacyReferenceSyntax.MESSAGE)
            return@withCompilationContext UnknownVar("legacy_reference").apply { isError = true }
        }
        if (currSelector is PropertyVar) currSelector = selectedMember(currSelector!!)
        top.mcfpp.core.lang.entity.EntityReadCallbacks.selector(ctx)?.let { read ->
            currSelector = top.mcfpp.core.lang.ref.ReferenceReadCallbacks.invoke(selectedReference(currSelector!!), read, processVarCache)
            return@withCompilationContext currSelector!!
        }
        top.mcfpp.core.lang.context.ContextCallbacks.selector(ctx, receiver = currSelector!!.type)?.let { context ->
            currSelector = top.mcfpp.core.lang.context.ContextCallbackLowering.invoke(currSelector!!, context, processVarCache)
            return@withCompilationContext currSelector!!
        }
        val collection = top.mcfpp.analysis.lambda.LambdaCollections.selector(ctx)
        if (collection != null && top.mcfpp.analysis.lambda.LambdaCollections.supports(currSelector!!.type, collection.name)) {
            currSelector = top.mcfpp.analysis.lambda.LambdaCollections.invoke(currSelector!!, collection, processVarCache)
            return@withCompilationContext currSelector!!
        }
        val variable = ctx.`var`()
        if (variable == null) {
            val keyword = ctx.memberKeyword()?.text ?: ctx.text
            if (keyword == "text" && ctx.arguments() == null) {
                currSelector = Var.checkMember(currSelector!!.getMemberVar(keyword, Function.currFunction), keyword)
                return@withCompilationContext currSelector!!
            }
            if (keyword == "set" && currSelector!!.type is top.mcfpp.type.MCFPPReferenceType) {
                currSelector = invokeReferenceSet(currSelector!!, ctx)
                return@withCompilationContext currSelector!!
            }
            KeywordMemberCalls.call(ctx)?.let { call ->
                currSelector = visitFunctionCall(call)
                return@withCompilationContext currSelector!!
            }
            LogProcessor.error("Member function '$keyword' requires a function-value call binding, which is not executable yet")
            return@withCompilationContext UnknownVar("member_${ctx.start.tokenIndex}").apply { isError = true }
        }
        //进入visitVar，currSelector作为成员选择的上下文
        currSelector = try { visitVar(variable) } finally { superSelector = null }
        return@withCompilationContext currSelector!!
    }

    /**
     * 一个初级表达式，可能是一个变量，也可能是一个数值
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitPrimary(ctx: mcfppParser.PrimaryContext): Var<*> = withCompilationContext(ctx) {
        if (ctx.inlineLambda() != null) {
            val syntax = ctx.inlineLambda()
            val expected = expectedFunction?.takeIf { it.first == ctx.start.tokenIndex }?.second
            if (expected == null && top.mcfpp.analysis.TemplateLiteralSupport.ambiguousObjectEntries(syntax) != null)
                return@withCompilationContext visitAmbiguousObject(syntax,
                    expectedLiteral?.takeIf { it.first == ctx.start.tokenIndex }?.second)
            return@withCompilationContext top.mcfpp.analysis.lambda.LambdaLowering.literal(ctx.inlineLambda(),
                expected)
        }
        val reference = ctx.`var`()?.varWithSuffix()
        val expected = expectedFunction?.takeIf { it.first == ctx.start.tokenIndex }?.second
        if (expected != null && ctx.value()?.nbtValue()?.nbtCompound()?.text == "{}")
            return@withCompilationContext top.mcfpp.analysis.lambda.LambdaLowering.empty(expected)
        if (expected != null && reference != null && reference.identifierSuffix().isEmpty() &&
            (top.mcfpp.analysis.StructuredBindings.functions(reference, Function.currFunction)?.isNotEmpty() == true ||
                !top.mcfpp.analysis.StructuredBindings.isBound(reference) && Function.currFunction.scope.getVar(reference.Identifier().text) == null))
            return@withCompilationContext top.mcfpp.analysis.lambda.LambdaLowering.reference(reference.Identifier().text, expected, reference)
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
            return MCFPPTypeVar(MCFPPType.parseFromContext(ctx.type(), Function.currFunction.scope, Function.currFunction)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                MCFPPBaseType.Any
            })
        } else {
            //this或者super
            if (ctx.SUPER() != null) {
                LogProcessor.error("'super' dispatch is unavailable; template functions bind an ordinary receiver")
                return UnknownVar("super").apply { isError = true }
            }
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
        if (ctx.expression() == null) {
            LogProcessor.error("An empty expression cannot produce a value")
            return UnknownVar("expression").apply { isError = true }
        }
        // Keep separate expression result fields while preserving outer values across nested calls.
        return MCFPPExprVisitor(processVarCache = processVarCache).visit(ctx.expression())
    }

    override fun visitFunctionCall(ctx: mcfppParser.FunctionCallContext): Var<*> = withCompilationContext(ctx) {
        if (ctx.namespaceID().text == "new" && currSelector != null && currSelector !is StaticMemberView) {
            LogProcessor.error("Call a factory through its type namespace")
            return@withCompilationContext UnknownVar("factory").apply { isError = true }
        }
        val staticFactory = if (ctx.namespaceID().text == "new")
            ((currSelector as? StaticMemberView)?.declaration as? top.mcfpp.type.MCFPPDataTemplateType)?.template
                ?.takeUnless { it is top.mcfpp.model.compound.TypeDataTemplate } else null
        val contextReceiver = currSelector
        if (contextReceiver != null && top.mcfpp.core.lang.context.ContextPositions.recognizes(contextReceiver.type, ctx.namespaceID().text)) {
            try { return@withCompilationContext top.mcfpp.core.lang.context.ContextPositions.invoke(contextReceiver, ctx, processVarCache) }
            catch (failure: top.mcfpp.core.lang.context.ContextAccessException) {
                LogProcessor.error(failure.message.orEmpty()); return@withCompilationContext UnknownVar("context_position").apply { isError = true }
            }
        }
        val boundName = if (currSelector == null && superSelector == null)
            top.mcfpp.analysis.StructuredBindings.name(ctx.namespaceID(), Function.currFunction) else null
        if (boundName is top.mcfpp.analysis.StructuredNameBinding.Macros)
            return@withCompilationContext MacroCallPipeline.visitFunctionCall(ctx, Function.currFunction,
                frozenCandidates = boundName.candidates) ?: UnknownVar("error_macro").apply { isError = true }
        val memberCandidates = if (superSelector == null) currSelector?.let {
            top.mcfpp.analysis.StructuredBindings.memberFunctions(ctx.namespaceID(), it, Function.currFunction)
        } else null
        val boundCandidates = if (currSelector == null && superSelector == null)
            top.mcfpp.analysis.StructuredBindings.functions(ctx.namespaceID(), Function.currFunction) else memberCandidates
        if (currSelector == null && superSelector == null && boundCandidates != null && (ctx.arguments().normalArgs().callArgumentList() != null ||
                boundCandidates.any { candidate -> candidate.normalParams.any { it.type is MCFPPFunctionType } } ||
                boundName is top.mcfpp.analysis.StructuredNameBinding.Frame || boundName is top.mcfpp.analysis.StructuredNameBinding.Value))
            return@withCompilationContext top.mcfpp.analysis.lambda.LambdaLowering.call(ctx.namespaceID().text,
                ctx.arguments(), null, processVarCache, ctx.namespaceID())
        LegacyReferenceSyntax.argument(ctx.arguments())?.let {
            LogProcessor.error(LegacyReferenceSyntax.MESSAGE)
            return@withCompilationContext UnknownVar("legacy_reference").apply { isError = true }
        }
        if (currSelector == null && superSelector == null && boundCandidates == null) {
            val (namespace, identifier) = ctx.namespaceID().text.splitNamespaceID()
            val callable = if (namespace == null) Function.currFunction.scope.getVar(identifier) as? FunctionValue else null
            if (callable != null) return@withCompilationContext top.mcfpp.analysis.lambda.LambdaLowering.call(
                ctx.namespaceID().text, ctx.arguments(), null, processVarCache)
        }
        // Namespace macros must be intercepted before spills, comments or any argument is visited.
        // A member/static/super call can never select a namespace macro, so those keep the old path.
        if (currSelector == null && superSelector == null && boundCandidates == null) {
            val macro = MacroCallPipeline.visitFunctionCall(ctx, Function.currFunction)
            if (macro != null) return@withCompilationContext macro
            top.mcfpp.analysis.lambda.LambdaCollections.global(ctx)?.let {
                return@withCompilationContext top.mcfpp.analysis.lambda.LambdaCollections.invoke(null, it, processVarCache)
            }
            top.mcfpp.core.lang.context.ContextCallbacks.global(ctx)?.let {
                return@withCompilationContext top.mcfpp.core.lang.context.ContextCallbackLowering.invoke(Void, it, processVarCache)
            }
            if (ctx.arguments().normalArgs().callArgumentList() != null)
                return@withCompilationContext top.mcfpp.analysis.lambda.LambdaLowering.call(
                    ctx.namespaceID().text, ctx.arguments(), null, processVarCache)
            if (top.mcfpp.analysis.lambda.LambdaLowering.hasCallbackTarget(ctx.namespaceID().text))
                return@withCompilationContext top.mcfpp.analysis.lambda.LambdaLowering.call(
                    ctx.namespaceID().text, ctx.arguments(), null, processVarCache)
        }
        //是函数调用，将已经计算好的中间量存储到栈中
        val spills = top.mcfpp.analysis.StorageAccess.spill(processVarCache)
        //函数的调用
        Function.addComment(ctx.text)
        //参数获取
        val normalArgs: ArrayList<Var<*>> = ArrayList()
        val readOnlyArgs: ArrayList<Var<*>> = ArrayList()
        val explicitContexts = linkedMapOf<String, Var<*>>()
        val exprVisitor = MCFPPExprVisitor()
        val p = ctx.namespaceID().text.splitNamespaceID()
        val declaration = ((boundName as? top.mcfpp.analysis.StructuredNameBinding.Type)?.type as? top.mcfpp.type.MCFPPDataTemplateType)?.template
            ?: if (currSelector == null && superSelector == null && boundCandidates == null) GlobalScope.getTemplate(p.first, p.second) else null
            ?: (((currSelector as? StaticMemberView)?.declaration as? top.mcfpp.type.MCFPPDataTemplateType)?.template
                ?.let { top.mcfpp.analysis.NestedTemplates.child(it, p.second) })
        val readonlyCandidates = boundCandidates ?: when {
            staticFactory != null -> staticFactory.factories
            superSelector != null -> superSelector!!.scope.getFunctionCandidates(p.second, Function.currFunction)
            currSelector == null -> GlobalScope.getFunctionCandidates(p.first, p.second, top.mcfpp.io.MCFPPFile.currFile?.field)
            currSelector is DataTemplateObject -> (currSelector as DataTemplateObject).templateType.scope
                .getFunctionCandidates(p.second, Function.currFunction).filterNot { it.isEntityMember }
            currSelector is StaticMemberView -> ((currSelector as StaticMemberView).declaration as? MCFPPType)
                ?.objectData?.scope?.getFunctionCandidates(p.second).orEmpty()
            else -> currSelector!!.type.instanceData.scope.getFunctionCandidates(p.second)
        }
        for ((index, expr) in ctx.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().withIndex()) {
            val expectedReadonly = (declaration as? GenericDataTemplate)?.readOnlyParams?.getOrNull(index)?.type
                ?: readonlyCandidates.mapNotNull { candidate ->
                    val parameters = (candidate as? Generic<*>)?.readOnlyParams
                        ?: (candidate as? NativeFunction)?.readOnlyParams.orEmpty()
                    parameters.getOrNull(index)?.type
                }.distinctBy { it.typeId }.singleOrNull()
            val arg = MCFPPReadonlyExprVisitor(expectedType = expectedReadonly).visit(expr)
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
        val mixedArguments = ctx.arguments().normalArgs().callArgumentList()?.callArgument()
        val orderedExpressions = mixedArguments?.map { (if (it.ASSIGNMENT() != null) it.Identifier().text else null) to it.expression() }
            ?: ctx.arguments().normalArgs().expressionList()?.expression().orEmpty().map { null to it }
        val earlyFunctions: List<Function> = when {
            staticFactory != null -> staticFactory.factories
            declaration != null && currSelector != null -> if (declaration is top.mcfpp.model.compound.TypeDataTemplate)
                declaration.constructors else declaration.factories
            currSelector?.isError == true -> emptyList()
            superSelector != null -> superSelector!!.scope.getFunctionCandidates(p.second, Function.currFunction)
            currSelector == null -> (boundCandidates ?: GlobalScope.getFunctionCandidates(p.first, p.second, top.mcfpp.io.MCFPPFile.currFile?.field)).ifEmpty {
                if (declaration != null) {
                    try {
                        val specialized = if (declaration is GenericDataTemplate) declaration.compile(readOnlyArgs) else if (readOnlyArgs.isEmpty()) declaration else null
                        if (specialized == null) emptyList() else if (specialized.isEntityTemplate || specialized is top.mcfpp.model.compound.TypeDataTemplate)
                            specialized.constructors else specialized.factories
                    } catch (_: Throwable) { emptyList() }
                } else emptyList()
            }
            memberCandidates != null -> memberCandidates
            currSelector is DataTemplateObject -> (currSelector as DataTemplateObject).templateType.scope.getFunctionCandidates(p.second, Function.currFunction).filterNot { it.isEntityMember }
            currSelector is StaticMemberView -> ((currSelector as StaticMemberView).declaration as? MCFPPType)?.objectData?.scope?.getFunctionCandidates(p.second).orEmpty()
            else -> currSelector!!.type.instanceData.scope.getFunctionCandidates(p.second)
        }
        val readonlyBounds = readOnlyArgs.map {
            top.mcfpp.analysis.DeclarationBinding.Bound(ParameterMatcher.argumentType(it), StorageAccess.snapshot(it), descriptors = StorageAccess.boundTypes(it))
        }
        val receiverTypeMap = (currSelector?.type as? top.mcfpp.type.MCFPPTypeWithGeneric)?.generic?.firstOrNull()?.let { mapOf("E" to it) }.orEmpty()
        val positionalCount = orderedExpressions.count { it.first == null } + normalArgs.size
        val candidateSignatures: List<List<MCFPPType>> = earlyFunctions.mapNotNull { candidate ->
            val readonlyParams = (candidate as? Generic<*>)?.readOnlyParams ?: (candidate as? NativeFunction)?.readOnlyParams.orEmpty()
            val sig = top.mcfpp.model.function.SpecializationPolicy.resolveDeclaredSignature(candidate, readonlyParams, readonlyBounds, receiverTypeMap) ?: return@mapNotNull null
            if (positionalCount > sig.normalTypes.size || candidate.normalParams.drop(positionalCount).any { !it.hasDefault }) null else sig.normalTypes
        }
        val contextTargetByName = earlyFunctions.flatMap { it.contextParams }.groupBy { it.identifier }.mapValues { (_, list) ->
            list.map { it.type }.distinctBy { it.typeId }.singleOrNull()
        }
        for ((contextName, expr) in orderedExpressions) {
            if (contextName != null && contextName in explicitContexts) {
                LogProcessor.error("Duplicate context argument '$contextName'")
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return UnknownVar(contextName).apply { isError = true }
            }
            val expectedArgType = if (contextName != null) contextTargetByName[contextName] else {
                val argIndex = normalArgs.size
                val targets = candidateSignatures.filter { sig ->
                    normalArgs.indices.all { i -> ParameterMatcher.accepts(normalArgs[i], sig[i]) }
                }.mapNotNull { it.getOrNull(argIndex) }.distinctBy { it.typeId }
                top.mcfpp.analysis.TemplateLiteralSupport.selectExpectedLiteralType(expr, targets) { candidate ->
                    try { top.mcfpp.analysis.DeclarationBinding(Function.currFunction, emptyMap()).compoundLiteral(expr, candidate); true }
                    catch (_: top.mcfpp.analysis.DeclarationBinding.Failure) { false }
                }
            }
            val arg = exprVisitor.visitExpression(expr, expectedArgType)
            if (arg.isError) {
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return arg
            }
            TypeUsage.ordinaryDiagnostic(arg.type, top.mcfpp.analysis.StorageAccess.snapshot(arg))?.let {
                LogProcessor.error(it)
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return UnknownVar("error_" + ctx.text).apply { isError = true }
            }
            if(arg is UnknownVar){
                LogProcessor.error(TextTranslator.SYMBOL_NOT_DEFINED.translate(arg.identifier))
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return UnknownVar("error_" + ctx.text)
            }
            val captured = top.mcfpp.analysis.StorageAccess.capture(arg)
            if (captured.isError) {
                top.mcfpp.analysis.StorageAccess.restore(spills)
                return captured
            }
            if (contextName == null) normalArgs.add(captured) else explicitContexts[contextName] = captured
            exprVisitor.processVarCache.add(captured)
        }
        //Try to get function
        val func = if (staticFactory != null) {
            val selection = staticFactory.resolveFactory(normalArgs)
            (selection as? ParameterMatcher.TypeSelection.Selected)?.function ?: UnknownFunction("new")
        } else if (declaration != null && currSelector != null) UnknownFunction(p.second) else if (superSelector != null) {
            try {
                Function.currFunction.selectSuper(superSelector!!, p.second, readOnlyArgs.map {
                    top.mcfpp.analysis.DeclarationBinding.Bound(ParameterMatcher.argumentType(it),
                        StorageAccess.snapshot(it), descriptors = StorageAccess.boundTypes(it))
                }, normalArgs.map(ParameterMatcher::argumentType))
            } catch (failure: Function.SuperFailure) {
                LogProcessor.error(failure.message ?: "Cannot bind super member")
                StorageAccess.restore(spills)
                return UnknownVar(p.second).apply { isError = true }
            }
        } else if (currSelector?.isError == true) {
            StorageAccess.restore(spills)
            return currSelector!!
        } else if(currSelector == null){
            if (boundCandidates != null) ParameterMatcher.select(boundCandidates, boundCandidates.firstOrNull()?.identifier ?: p.second,
                readOnlyArgs, normalArgs) ?: UnknownFunction(p.second)
            else GlobalScope.getFunction(p.first, p.second, readOnlyArgs, normalArgs)
        }else{
            if(p.first != null){
                LogProcessor.warn("Invalid namespace usage ${p.first} in function call ")
            }
            if (memberCandidates != null) ParameterMatcher.select(memberCandidates,
                memberCandidates.firstOrNull()?.identifier ?: p.second, readOnlyArgs, normalArgs, currSelector?.type) ?: UnknownFunction(p.second)
            else MCFPPFuncGetter.getFunction(currSelector!!,p.second, readOnlyArgs, normalArgs)
        }
        //Function invoke
        if (func !is UnknownFunction) {
            if (func is top.mcfpp.model.function.TemplateFactory && Function.currFunction.accessTo(func.template) < func.accessModifier) {
                LogProcessor.error("Cannot access factory '${func.template.identifier}.new'")
                StorageAccess.restore(spills)
                return UnknownVar("factory").apply { isError = true }
            }
            top.mcfpp.mni.ReferenceData.sourceFactory(func, readOnlyArgs, normalArgs)?.let {
                StorageAccess.restore(spills)
                return it
            }
            val returnVar = top.mcfpp.core.lang.context.ContextParameters.withExplicit(explicitContexts) { if(func is Generic<*>){
                if(readOnlyArgs.any { it is UnknownVar } || normalArgs.any { it is UnknownVar }){
                    UnknownVar("re")
                }else{
                    func.invoke(readOnlyArgs, normalArgs, currSelector)
                }
            }else if(func is NativeFunction){
                if(readOnlyArgs.any { it is UnknownVar } || normalArgs.any { it is UnknownVar }){
                    UnknownVar("re")
                }else{
                    func.invoke(readOnlyArgs, normalArgs, currSelector)
                }
            }else{
                if(normalArgs.any { it is UnknownVar }){
                    UnknownVar("re")
                }else {
                    func.invoke(normalArgs, if (func is top.mcfpp.model.function.TemplateFactory) null else currSelector)
                }
            } }
            //函数树
            Function.currFunction.recordCall(func)
            top.mcfpp.analysis.StorageAccess.restore(spills)
            return if (returnVar.isError || returnVar.type == top.mcfpp.type.MCFPPPrivateType.Void) returnVar
                else top.mcfpp.analysis.StorageAccess.capture(returnVar)
        }
        //可能是模板的构造函数
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
            if (!template.isEntityTemplate && template !is top.mcfpp.model.compound.TypeDataTemplate) {
                val selection = template.resolveFactory(normalArgs)
                if (selection !is ParameterMatcher.TypeSelection.Selected)
                    return failedTemplate(if (selection is ParameterMatcher.TypeSelection.Ambiguous)
                        "Ambiguous factory '${ctx.namespaceID().text}': ${selection.functions.joinToString()}"
                    else "No factory '${ctx.namespaceID().text}.new(${FunctionParam.getArgTypeNames(normalArgs)})'")
                if (Function.currFunction.accessTo(template) < selection.function.accessModifier)
                    return failedTemplate("Cannot access factory '${template.identifier}.new'")
                val result = selection.function.invoke(normalArgs, null)
                Function.currFunction.recordCall(selection.function)
                StorageAccess.restore(spills)
                return if (result.isError) result else StorageAccess.capture(result)
            }
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
        val bound = if (currSelector == null) top.mcfpp.analysis.StructuredBindings.name(ctx, Function.currFunction) else null
        var re = if (bound is top.mcfpp.analysis.StructuredNameBinding.Type) {
            StaticMemberView(bound.type)
        } else if (bound != null) {
            val value = top.mcfpp.analysis.StructuredBindings.variable(ctx, Function.currFunction)
                ?: throw top.mcfpp.analysis.DeclarationBinding.Failure("Bound name '$qwq' is not a variable")
            if (ctx === assignmentTarget) value else StorageAccess.read(value)
        } else if(currSelector == null) {
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
            if(currSelector is PropertyVar) currSelector = selectedMember(currSelector!!)
            //获取成员
            val re = if (superSelector != null && currSelector is DataTemplateObject) {
                try {
                    Function.currFunction.superMember(currSelector as DataTemplateObject, superSelector!!, qwq) to true
                } catch (failure: Function.SuperFailure) {
                    LogProcessor.error(failure.message ?: "Cannot bind super field")
                    return@withCompilationContext UnknownVar(qwq).apply { isError = true }
                }
            } else if (currSelector!!.isError) return@withCompilationContext currSelector!!
            else currSelector!!.getMemberVar(qwq, Function.currFunction)
            if (re.first == null) {
                LogProcessor.error("Cannot get member $qwq")
                UnknownVar(qwq)
            }else if (!re.second){
                LogProcessor.error("Cannot access member $qwq")
                UnknownVar(qwq)
            }else{
                if (ctx === assignmentTarget) re.first!!
                else if (re.first is PropertyVar && (re.first as PropertyVar).property.accessor !is top.mcfpp.model.property.SimpleAccessor)
                    (re.first as PropertyVar).get()
                else top.mcfpp.core.lang.entity.EntityDataAddress.selected(re.first!!) ?: top.mcfpp.analysis.StorageAccess.read(re.first!!)
            }
        }
        if(re is UnknownVar && currSelector == null){
            //从类型获取
            val typeStr = ctx.Identifier().text
            val chain = generateSequence(ctx.parent as org.antlr.v4.runtime.tree.ParseTree?) { it.parent }
                .filterIsInstance<mcfppParser.VarWithSelectorContext>().firstOrNull()
            val owner = if (chain?.start?.tokenIndex == ctx.start.tokenIndex && chain.selector().isNotEmpty())
                GlobalScope.getTemplate(null, typeStr) else null
            val type = owner?.getType() ?: MCFPPType.parseExpressionType(typeStr, Function.currFunction.scope, Function.currFunction)
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
                    re = selectedMember(re)
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

    private fun selectedMember(value: Var<*>): Var<*> =
        top.mcfpp.core.lang.entity.EntityDataAddress.selected(value) ?: (value as? PropertyVar)?.get() ?: value

    private fun selectedReference(value: Var<*>): Var<*> =
        top.mcfpp.core.lang.entity.EntityDataAddress.selected(value)?.bareRead() ?: (value as? PropertyVar)?.get() ?: value

    private fun invokeReferenceSet(receiver: Var<*>, selector: mcfppParser.SelectorContext): Var<*> {
        fun failed(message: String): Var<*> {
            LogProcessor.error(message)
            return UnknownVar("reference_set").apply { isError = true }
        }
        val source = selectedReference(receiver)
        val type = source.type as? MCFPPReferenceType ?: return failed("set requires ScoreRef or NBTRef<T>")
        val arguments = selector.arguments() ?: return failed("set expects one value argument")
        if (arguments.readOnlyArgs() != null) return failed("set does not accept readonly arguments")
        val normal = arguments.normalArgs()
        val mixed = normal.callArgumentList()?.callArgument()
        if (mixed?.any { it.REF() != null } == true) return failed(LegacyReferenceSyntax.MESSAGE)
        val expressions = normal.expressionList()?.expression().orEmpty()
        if (mixed != null && mixed.size != 1 || mixed == null && expressions.size != 1)
            return failed("set expects one value argument")
        val owned = StorageAccess.capture(source)
        if (owned.isError) return owned
        val pending = processVarCache.size
        processVarCache.add(owned)
        try {
            val visitor = MCFPPExprVisitor(processVarCache = processVarCache)
            val argument = mixed?.single()
            val value = if (argument?.inlineLambda() != null)
                top.mcfpp.analysis.lambda.LambdaLowering.literal(argument.inlineLambda(), type.valueType as? MCFPPFunctionType)
            else visitor.visitExpression(argument?.expression() ?: expressions.single(), type.valueType)
            return top.mcfpp.core.lang.ref.ReferenceAccess.set(owned, value)
        } finally { processVarCache.removeAt(pending) }
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
        top.mcfpp.analysis.StructuredBindings.literal(ctx)?.let { return@withCompilationContext it }
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
        top.mcfpp.analysis.StructuredBindings.literal(ctx)?.let { return@withCompilationContext it }
        integralLiteral(ctx)?.let { return@withCompilationContext it }
        if (ctx.nbtCompound() == null && ctx.nbtList() == null) {
            return closedLiteral(top.mcfpp.analysis.DeclarationBinding(Function.currFunction, emptyMap()).nbtLiteral(ctx))
        }
        val rawExpected = expectedLiteral?.takeIf { it.first == ctx.start.tokenIndex }?.second
        if(ctx.nbtCompound() != null){
            val compoundExpected = when (rawExpected) {
                is top.mcfpp.type.MCFPPUnionType -> top.mcfpp.analysis.TemplateLiteralSupport.resolveCompoundUnionTarget(rawExpected) { candidate ->
                    try { top.mcfpp.analysis.DeclarationBinding(Function.currFunction, emptyMap()).nbtLiteral(ctx, candidate); true }
                    catch (_: top.mcfpp.analysis.DeclarationBinding.Failure) { false }
                } ?: rawExpected
                else -> rawExpected
            }
            if (compoundExpected != null && top.mcfpp.analysis.TemplateLiteralSupport.isTemplateLiteralType(compoundExpected)) {
                return visitObjectCompound(ctx, compoundExpected)
            }
            if (compoundExpected !is top.mcfpp.type.MCFPPDictType && compoundExpected != top.mcfpp.type.MCFPPNBTType.NBT)
                return visitObjectCompound(ctx, compoundExpected)
            val expectedDict = compoundExpected as? top.mcfpp.type.MCFPPDictType
            val expectedValueType = expectedDict?.generic?.single()
            val fields = linkedMapOf<String, Var<*>>()
            val entries = try { top.mcfpp.analysis.TemplateLiteralSupport.compoundEntries(ctx.nbtCompound()) }
                catch (failure: top.mcfpp.analysis.DeclarationBinding.Failure) {
                    LogProcessor.error(failure.message.orEmpty())
                    return UnknownVar("object_literal").apply { isError = true }
                }
            for ((key, kv) in entries){
                val value = top.mcfpp.analysis.StorageAccess.capture(
                    kv.expression()?.let { MCFPPExprVisitor(enumType = expectedValueType as? MCFPPEnumType, processVarCache = processVarCache)
                        .visitExpression(it, expectedValueType) } ?: lexicalObjectField(key)
                ).also { processVarCache.add(it) }
                if (value.isError) return value
                fields[key] = value
            }
            val types = fields.values.map { it.type }.distinctBy { it.typeId }
            if (compoundExpected == top.mcfpp.type.MCFPPNBTType.NBT)
                return top.mcfpp.analysis.StorageAccess.dictionaryLiteral(top.mcfpp.type.MCFPPNBTType.NBT, fields)
            val element = when (types.size) {
                0 -> MCFPPBaseType.Any
                1 -> types.single()
                else -> top.mcfpp.type.MCFPPUnionType(*types.toTypedArray())
            }
            val contextual = expectedDict?.takeIf { target -> fields.values.all {
                top.mcfpp.model.function.ParameterMatcher.accepts(it, target.generic.single())
            } }
            val typedFields = if (contextual == null) fields else fields.mapValues { (_, value) ->
                value.implicitCast(contextual.generic.single())
            }
            return top.mcfpp.analysis.StorageAccess.dictionaryLiteral(contextual ?: top.mcfpp.type.MCFPPDictType(element), typedFields)
        }else if(ctx.nbtList() != null){
            val listExpected = when (rawExpected) {
                is top.mcfpp.type.MCFPPUnionType -> top.mcfpp.analysis.TemplateLiteralSupport.resolveListUnionTarget(rawExpected) ?: rawExpected
                else -> rawExpected
            }
            val expectedList = listExpected?.takeIf { it is MCFPPListType || it is top.mcfpp.type.MCFPPImmutableListType }
            val expectedElementType = (expectedList as? top.mcfpp.type.MCFPPTypeWithGeneric)?.generic?.single()
            val valueList = ArrayList<Var<*>>()
            for (expr in ctx.nbtList().expression()){
                val value = top.mcfpp.analysis.StorageAccess.capture(
                    MCFPPExprVisitor(enumType = expectedElementType as? MCFPPEnumType, processVarCache = processVarCache)
                        .visitExpression(expr, expectedElementType)
                ).also { processVarCache.add(it) }
                if (value.isError) return value
                valueList.add(value)
            }
            val contextual = expectedList?.takeIf { expectedElementType != null && valueList.all {
                ParameterMatcher.accepts(it, expectedElementType)
            } }
            val typedList = if (contextual == null || expectedElementType == null) valueList else valueList.map {
                val cast = it.implicitCast(expectedElementType)
                if (cast.isError) it else top.mcfpp.analysis.StorageAccess.capture(cast)
            }
            val re = if(typedList.isEmpty()){
                expectedList ?: MCFPPListType(top.mcfpp.type.MCFPPPrivateType.Wildcard)
            }else{
                val types = typedList.map { it.type }.distinctBy { it.typeId }
                val elementType = if (types.size == 1) types.single() else top.mcfpp.type.MCFPPUnionType(*types.toTypedArray())
                contextual ?: MCFPPListType(elementType)
            }
            return top.mcfpp.analysis.StorageAccess.listLiteral(re, typedList)
        }else {
            LogProcessor.error("Invalid NBT value")
            throw IllegalArgumentException("nbt:" + ctx.text)
        }
    }

    private fun lexicalObjectField(key: String): Var<*> {
        val (value, accessible) = Function.currFunction.scope.getVar(key, Function.currFunction)
        if (value == null || !accessible) {
            LogProcessor.error(if (value == null) "Undefined shorthand field '$key'" else "Cannot access shorthand field '$key'")
            return UnknownVar(key).apply { isError = true }
        }
        return StorageAccess.read(if (value is PropertyVar) value.get() else value)
    }

    internal fun visitAmbiguousObject(ctx: mcfppParser.InlineLambdaContext, expected: MCFPPType?): Var<*> {
        val binder = top.mcfpp.analysis.DeclarationBinding(Function.currFunction, emptyMap())
        val bound = try { binder.ambiguousObjectLiteral(ctx, expected) }
            catch (failure: top.mcfpp.analysis.DeclarationBinding.Failure) {
                LogProcessor.error(failure.message.orEmpty())
                return UnknownVar("object_literal").apply { isError = true }
            }
        val dictionary = bound.type as? MCFPPDictType
        val contracts = expected?.let(top.mcfpp.type.TypeCompositions::objectShape)?.fieldsByName.orEmpty()
        val structuralContracts = (expected as? top.mcfpp.type.MCFPPObjectType)?.fieldsByName.orEmpty()
        val fields = linkedMapOf<String, Var<*>>()
        val descriptors = arrayListOf<top.mcfpp.type.ObjectFieldContract>()
        for ((key, expression) in checkNotNull(top.mcfpp.analysis.TemplateLiteralSupport.ambiguousObjectEntries(ctx))) {
            val target = dictionary?.generic?.single() ?: contracts[key]?.type
            val value = if (top.mcfpp.analysis.TemplateLiteralSupport.hasInactiveObjectParameter(ctx, key)) lexicalObjectField(key)
                else MCFPPExprVisitor(enumType = target as? MCFPPEnumType, processVarCache = processVarCache)
                    .visitExpression(expression, target)
            if (value.isError || value is UnknownVar) return value
            val contract = structuralContracts[key]
            val converted = when {
                dictionary != null -> value.implicitCast(dictionary.generic.single())
                contract != null && value.type != contract.type &&
                    top.mcfpp.type.TypeRelations.resolveImplicitConversion(value.type, contract.type) != null -> value.implicitCast(contract.type)
                else -> value
            }
            if (converted.isError) return converted
            val field = StorageAccess.capture(converted).also { processVarCache.add(it) }
            val fieldType = StorageAccess.snapshot(field)?.let {
                top.mcfpp.type.TypeCompositions.literalOrNull(field.type, it)
            } ?: field.type
            descriptors.add(contract ?: top.mcfpp.type.ObjectFieldContract(key, fieldType))
            fields[key] = field
        }
        val type = if (dictionary != null || bound.type == MCFPPNBTType.NBT) bound.type
            else top.mcfpp.type.MCFPPObjectType(descriptors)
        return StorageAccess.dictionaryLiteral(type, fields)
    }

    /** A literal owns a complete payload; target declarations do not supply hidden initializers. */
    private fun visitObjectCompound(ctx: mcfppParser.NbtValueContext, expected: MCFPPType?): Var<*> {
        val contracts = expected?.let(top.mcfpp.type.TypeCompositions::objectShape)?.fieldsByName.orEmpty()
        val structuralContracts = (expected as? top.mcfpp.type.MCFPPObjectType)?.fieldsByName.orEmpty()
        val entries = try { top.mcfpp.analysis.TemplateLiteralSupport.compoundEntries(ctx.nbtCompound()) }
            catch (failure: top.mcfpp.analysis.DeclarationBinding.Failure) {
                LogProcessor.error(failure.message.orEmpty())
                return UnknownVar("object_literal").apply { isError = true }
            }
        try {
            top.mcfpp.analysis.DeclarationBinding(Function.currFunction, emptyMap()).nbtLiteral(ctx, expected)
        } catch (failure: top.mcfpp.analysis.DeclarationBinding.Failure) {
            LogProcessor.error(failure.message.orEmpty())
            return UnknownVar("object_literal").apply { isError = true }
        }
        val fields = linkedMapOf<String, Var<*>>()
        val descriptors = arrayListOf<top.mcfpp.type.ObjectFieldContract>()
        for ((key, entry) in entries) {
            val target = contracts[key]?.type
            val evaluated = entry.expression()?.let {
                MCFPPExprVisitor(enumType = target as? MCFPPEnumType, processVarCache = processVarCache)
                    .visitExpression(it, target)
            } ?: lexicalObjectField(key)
            if (evaluated.isError || evaluated is UnknownVar) return evaluated
            if (evaluated is Null) {
                LogProcessor.error("Object field '$key' cannot contain null; omit an optional field instead")
                return UnknownVar(key).apply { isError = true }
            }
            val contract = structuralContracts[key]
            val value = if (contract != null && evaluated.type != contract.type &&
                top.mcfpp.type.TypeRelations.resolveImplicitConversion(evaluated.type, contract.type) != null)
                evaluated.implicitCast(contract.type) else evaluated
            if (value.isError) return value
            val fieldType = StorageAccess.snapshot(value)?.let {
                top.mcfpp.type.TypeCompositions.literalOrNull(value.type, it)
            } ?: value.type
            descriptors.add(contract ?: top.mcfpp.type.ObjectFieldContract(key, fieldType))
            fields[key] = StorageAccess.capture(value).also { processVarCache.add(it) }
        }
        val shape = top.mcfpp.type.MCFPPObjectType(descriptors)
        if (expected != null && top.mcfpp.type.TypeRelations.resolveImplicitConversion(shape, expected) == null) {
            val missing = contracts.values.filter { !it.optional && it.name !in fields }.map { it.name }
            val mismatched = descriptors.filter { supplied -> contracts[supplied.name]?.let {
                !top.mcfpp.type.TypeRelations.isSubtype(supplied.type, it.type)
            } == true }.map { it.name }
            LogProcessor.error(when {
                missing.isNotEmpty() -> "Missing required fields ${missing.joinToString()} for '${expected.typeName}'"
                mismatched.isNotEmpty() -> "Incompatible fields ${mismatched.joinToString()} for '${expected.typeName}'"
                else -> "Object literal '${shape.typeName}' does not satisfy '${expected.typeName}'"
            })
            return UnknownVar("object_literal").apply { isError = true }
        }
        return StorageAccess.dictionaryLiteral(shape, fields)
    }

}
