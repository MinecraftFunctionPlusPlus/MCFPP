package top.mcfpp.mni.expand

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import top.mcfpp.mni.api.*
import top.mcfpp.type.*
import top.mcfpp.antlr.mcfppParser as P

/** Parses against immutable declaration snapshots. This class intentionally has no compiler visitor/backend hooks. */
internal interface SourceCodeBinder {
    fun call(syntax: P.FunctionCallContext, env: BindingEnvironment, trailing: P.InlineLambdaContext? = null): SourceCall
    /** Bind the final function-call selector against the actual receiver location before it. */
    fun memberCall(syntax: P.VarWithSelectorContext, env: BindingEnvironment): SourceCall
    /** Bind only the actual receiver prefix of a member selector, without invoking it. */
    fun receiverLocation(syntax: P.VarWithSelectorContext, env: BindingEnvironment): BoundLocation
    fun expression(syntax: P.ExpressionContext, env: BindingEnvironment): BoundExpr
    fun condition(syntax: P.ConditionExpressionContext, env: BindingEnvironment): BoundExpr
    fun location(syntax: P.ExpressionContext, env: BindingEnvironment): BoundLocation
    fun callback(syntax: P.InlineLambdaContext, signature: InlineSignature<*>, env: BindingEnvironment): SourceCallbackSyntax
}

internal class SourceBindingFailure(val source: SourceSpan, message: String) : IllegalArgumentException("$message at $source")

internal class SnapshotSourceCodeBinder : SourceCodeBinder {
    override fun call(syntax: P.FunctionCallContext, env: BindingEnvironment, trailing: P.InlineLambdaContext?): SourceCall = call(syntax, env, receiver = null, name = syntax.namespaceID().text, source = span(syntax, env), trailing = trailing)

    override fun memberCall(syntax: P.VarWithSelectorContext, env: BindingEnvironment): SourceCall {
        val selectors = syntax.selector()
        val last = selectors.lastOrNull()
            ?: fail(span(syntax, env), "A member call requires a final function-call selector")
        val callSyntax = last.`var`()?.functionCall()
            ?: fail(span(last, env), "The final member selector must be a function call")
        if (callSyntax.namespaceID().namespacePath() != null)
            fail(span(callSyntax, env), "A member call cannot use a namespace qualifier; select it through the receiver")
        val receiver = receiverLocation(syntax, env)
        return call(callSyntax, env, receiver, callSyntax.namespaceID().text, span(syntax, env))
    }

    override fun receiverLocation(syntax: P.VarWithSelectorContext, env: BindingEnvironment): BoundLocation = selectorLocation(syntax.jvmAccessExpression(), syntax.selector().dropLast(1), env)

    private fun call(
        syntax: P.FunctionCallContext,
        env: BindingEnvironment,
        receiver: BoundLocation?,
        name: String,
        source: SourceSpan,
        trailing: P.InlineLambdaContext? = null
    ): SourceCall {
        val args = syntax.arguments()
        val readonly = args.readOnlyArgs()?.expressionList()?.expression()?.map { expression(it, env) }.orEmpty()
        val normal = args.normalArgs()
        val syntaxItems = normal.callArgumentList()?.callArgument()
        val pending: List<ParserRuleContext> = (syntaxItems?.map { it as ParserRuleContext }
            ?: normal.expressionList()?.expression()?.map { it as ParserRuleContext }.orEmpty()) + listOfNotNull(trailing)
        val parsed: List<BoundCallArgument?> = pending.map { item ->
            when (item) {
                is P.InlineLambdaContext -> null
                is P.CallArgumentContext -> when {
                    item.REF() != null -> fail(span(item, env), top.mcfpp.antlr.LegacyReferenceSyntax.MESSAGE)
                    inlineSyntax(item) != null || emptyCallbackExpression(item) != null -> null
                    staticFunctionName(item.expression(), env) != null -> null
                    else -> BoundCallArgument.Value(expression(item.expression(), env), source = span(item, env))
                }
                is P.ExpressionContext -> if (inlineSyntax(item) != null || emptyCallbackExpression(item) != null || staticFunctionName(item, env) != null) null
                else BoundCallArgument.Value(expression(item, env), source = span(item, env))
                else -> error("Unexpected call argument syntax")
            }
        }
        val candidates = if (receiver == null) StructuredSourceFacts.functions(syntax.namespaceID(), env) ?: env.functions(name, source)
        else env.memberFunctions(receiver.type, name, source)
        if (receiver != null && candidates.isEmpty())
            fail(source, "No accessible declared member '$name' on '${receiver.type.name()}' in the immutable declaration snapshot")
        val target = selectTarget(
            candidates,
            readonly,
            parsed.mapIndexed { index, arg ->
                when (arg) {
                    is BoundCallArgument.Value -> arg.expression.type.takeUnless { type ->
                        type.inlineSignature() != null ||
                            (env.types.facts(type).id as? TypeId.Applied)?.constructor == TypeId.Builtin(TypeId.FUNCTION_CONSTRUCTOR)
                    }
                    is BoundCallArgument.Reference -> arg.location.type
                    is BoundCallArgument.Callback -> null
                    null -> staticFunctionType(pending[index], env)
                }
            },
            source,
            env
        )
        val raw = pending.mapIndexed { index, item ->
            if (target.function.parameters().getOrNull(index)?.mode() == ParamMode.INLINE && parsed[index] != null)
                fail(span(item, env), "INLINE requires a static lambda body or ordinary source function; dynamic function values cannot be expanded")
            parsed[index] ?: run {
                val parameter = target.function.parameters().getOrNull(index)
                    ?: fail(span(item, env), "No callback signature is available for this argument")
                val signature = parameter.inlineSignature() ?: parameter.type().inlineSignature()
                    ?: fail(span(item, env), "The selected parameter has no callback signature")
                val lambda = inlineSyntax(item)
                val empty = emptyCallbackExpression(item)
                val callback = if (lambda != null) callback(lambda, signature, env) else if (empty != null) {
                    if (signature.parameters().size > 1) fail(span(empty, env), "An implicit callback can only have zero or one parameter")
                    val site = span(empty, env)
                    val text = env.sourceText?.substring(site.startOffset, site.endOffset)
                        ?: fail(site, "Inline source binding requires the original source buffer")
                    SourceCallbackSyntax(if (signature.parameters().size == 1) listOf("it") else emptyList(), signature,
                        env.types.inlineType(signature) ?: fail(span(empty, env), "Callback signature is outside the type snapshot"),
                        text, site, emptyBody = true, structuredSyntax = StructuredCallbackSyntax.capture(empty),
                        structuredRequired = top.mcfpp.model.function.StructuredBodyContexts.body(empty) != null)
                } else {
                    val expression = when (item) {
                        is P.CallArgumentContext -> item.expression()
                        is P.ExpressionContext -> item
                        else -> null
                    } ?: fail(span(item, env), "Expected an inline lambda or static function symbol")
                    staticCallback(expression, signature, env)
                }
                BoundCallArgument.Callback(callback, span(item, env))
            }
        }
        val checkedRaw = validateSelectedArguments(target, readonly, raw, source, env)
        val order = readonly.map(SourceEvaluationArgument::Readonly) + checkedRaw.map(SourceEvaluationArgument::Normal)
        val effects = arrayListOf<BoundEffect>()
        receiver?.let { effects += it.effectOrder.fold(BoundEffect.READ, ::combine) }
        readonly.forEach { effects += it.effect }
        checkedRaw.forEach { arg ->
            when (arg) {
                is BoundCallArgument.Value -> effects += arg.expression.effect
                is BoundCallArgument.Reference -> effects.addAll(arg.location.effectOrder)
                is BoundCallArgument.Callback -> Unit // Callback bodies execute only when an inline target is expanded.
            }
        }
        effects += target.effect
        val combinedEffect = effects.fold(BoundEffect.PURE, ::combine)
        return SourceCall(
            name, target, immutableList(readonly), immutableList(checkedRaw), source,
            immutableList(order), immutableList(effects), combinedEffect, receiver
        )
    }

    override fun expression(syntax: P.ExpressionContext, env: BindingEnvironment): BoundExpr = bind(syntax, env)

    private fun inlineSyntax(item: ParserRuleContext): P.InlineLambdaContext? = when (item) {
        is P.InlineLambdaContext -> item
        is P.CallArgumentContext -> item.inlineLambda() ?: item.expression()?.primary()?.inlineLambda()
        is P.ExpressionContext -> item.primary()?.inlineLambda()
        else -> null
    }

    private fun emptyCallbackExpression(item: ParserRuleContext): P.ExpressionContext? {
        val expression = when (item) { is P.ExpressionContext -> item; is P.CallArgumentContext -> item.expression(); else -> null }
        fun primary(node: ParserRuleContext): P.PrimaryContext? {
            if (node is P.PrimaryContext) return node
            if (node is P.UnaryExpressionContext && node.rightVarExpression() == null ||
                node is P.CastExpressionContext && node.type() != null) return null
            return node.children.orEmpty().filterIsInstance<ParserRuleContext>().singleOrNull()?.let(::primary)
        }
        return expression?.takeIf { primary(it)?.value()?.nbtValue()?.nbtCompound()?.text?.filterNot { char -> char.isWhitespace() } == "{}" }
    }

    override fun condition(syntax: P.ConditionExpressionContext, env: BindingEnvironment): BoundExpr =
        syntax.primary()?.let { bind(it, env) } ?: bind(syntax.commonBinaryOperatorExpression(), env)

    override fun location(syntax: P.ExpressionContext, env: BindingEnvironment): BoundLocation = locationSyntax(syntax, env)
    fun location(syntax: P.VarWithSelectorContext, env: BindingEnvironment): BoundLocation =
        selectorLocation(syntax.jvmAccessExpression(), syntax.selector(), env)

    /** Only wrappers that preserve a location may be removed; every operator remains significant. */
    private fun locationSyntax(syntax: ParserRuleContext, env: BindingEnvironment): BoundLocation = when (syntax) {
        is P.ExpressionContext -> locationSyntax(syntax.primary() ?: syntax.commonBinaryOperatorExpression(), env)
        is P.PrimaryContext -> syntax.`var`()?.let { variableLocation(it, env) }
            ?: fail(span(syntax, env), "A writable location must start at a source variable")
        is P.CastExpressionContext -> {
            if (syntax.type() != null)
                fail(span(syntax, env), "Explicit casts cannot be captured as writable locations, including same-type casts")
            locationSyntax(syntax.unaryExpression(), env)
        }
        is P.UnaryExpressionContext -> {
            val variable = syntax.rightVarExpression()
                ?: fail(span(syntax, env), "Unary expressions are not writable locations")
            locationSyntax(variable, env)
        }
        is P.RightVarExpressionContext -> selectorLocation(syntax.varWithSelector(), env)
        is P.CommonBinaryOperatorExpressionContext,
        is P.ConditionalOrExpressionContext,
        is P.ConditionalAndExpressionContext,
        is P.EqualityExpressionContext,
        is P.RelationalExpressionContext,
        is P.AdditiveExpressionContext,
        is P.MultiplicativeExpressionContext -> {
            val operands = (0 until syntax.childCount).mapNotNull { syntax.getChild(it) as? ParserRuleContext }
            if (operands.size != 1)
                fail(span(syntax, env), "Binary expressions are not writable locations")
            locationSyntax(operands.single(), env)
        }
        else -> fail(span(syntax, env), "This syntax does not describe a writable source location")
    }

    override fun callback(syntax: P.InlineLambdaContext, signature: InlineSignature<*>, env: BindingEnvironment): SourceCallbackSyntax {
        val parameters = syntax.lambdaParameterList()?.lambdaParameter().orEmpty()
        val names = parameters.map { it.Identifier().text }.ifEmpty {
            if (signature.parameters().size == 1) listOf("it") else emptyList()
        }
        if (names.size != signature.parameters().size)
            fail(span(syntax, env), "Callback parameter count does not match the selected signature")
        if (names.distinct().size != names.size)
            fail(span(syntax, env), "Callback parameter names must be unique")
        parameters.forEachIndexed { index, parameter -> parameter.type()?.let { annotation ->
            val declared = StructuredSourceFacts.type(annotation, env) ?: env.type(annotation.text) ?: fail(span(annotation, env), "Cannot resolve callback parameter type '${annotation.text}'")
            if (env.types.conversionRank(signature.parameters()[index].type(), declared) == null)
                fail(span(parameter, env), "Callback parameter '${parameter.Identifier().text}' does not accept the target parameter type")
        } }
        val source = env.sourceText ?: fail(span(syntax, env), "Inline source binding requires the original source buffer")
        val sourceSpan = span(syntax, env)
        if (sourceSpan.endOffset > source.length) fail(sourceSpan, "Inline source range is outside the original source buffer")
        val callbackType = env.types.inlineType(signature)
            ?: fail(sourceSpan, "The callback signature is outside the current type snapshot")
        val structured = StructuredCallbackSyntax.capture(syntax)
        return SourceCallbackSyntax(
            immutableList(names),
            signature,
            callbackType,
            source.substring(sourceSpan.startOffset, sourceSpan.endOffset),
            sourceSpan,
            frozenSyntax = if (structured == null) FrozenCallbackSyntax.capture(syntax, source.substring(sourceSpan.startOffset, sourceSpan.endOffset)) else null,
            structuredSyntax = structured, structuredRequired = structured != null
        )
    }

    private fun staticFunctionName(syntax: P.ExpressionContext, env: BindingEnvironment): String? {
        fun variable(node: ParserRuleContext): P.VarContext? {
            if (node is P.PrimaryContext) return node.`var`()
            if (node is P.UnaryExpressionContext && node.rightVarExpression() == null ||
                node is P.CastExpressionContext && node.type() != null) return null
            val children = node.children.orEmpty().filterIsInstance<ParserRuleContext>()
            return children.singleOrNull()?.let(::variable)
        }
        val variable = variable(syntax) ?: return null
        if (variable.functionCall() != null) return null
        val suffix = variable.varWithSuffix() ?: return null
        if (suffix.identifierSuffix().isNotEmpty()) return null
        val name = suffix.Identifier()?.text ?: return null
        // A value declaration shadows a source function even when the selected parameter is INLINE.
        val bound = StructuredSourceFacts.functions(suffix, env)
        return name.takeIf { if (bound != null) bound.any { function -> function.sourceBody != null }
            else env.symbol(it) == null && env.functions(it).any { function -> function.sourceBody != null } }
    }

    private fun staticCallback(
        syntax: P.ExpressionContext,
        expected: InlineSignature<*>,
        env: BindingEnvironment
    ): SourceCallbackSyntax {
        val name = staticFunctionName(syntax, env) ?: fail(span(syntax, env), "Expected a static source function symbol")
        val expectedType = env.types.inlineType(expected)
            ?: fail(span(syntax, env), "Expected callback signature is outside the type snapshot")
        val candidates = (StructuredSourceFacts.functions(syntax, env) ?: env.functions(name)).filter { candidate ->
            val target = candidate.function as? ImmutableFunctionRef<*> ?: return@filter false
            if (candidate.sourceBody == null) return@filter false
            val signature = FunctionSignature.of(target.parameters(), target.returnType())
            env.types.inlineType(signature)?.sameType(expectedType) == true
        }
        val target = candidates.singleOrNull() ?: fail(
            span(syntax, env),
            when {
                candidates.isEmpty() -> "No visible source function matches the expected inline signature"
                else -> "The static inline function reference is ambiguous"
            }
        )
        val signature = FunctionSignature.of(target.function.parameters(), target.function.returnType())
        val callbackType = env.types.inlineType(signature)
            ?: fail(span(syntax, env), "Static callback signature is outside the type snapshot")
        val source = span(syntax, env)
        val sourceBuffer = env.sourceText ?: fail(source, "Static inline binding requires the original source buffer")
        if (source.endOffset > sourceBuffer.length) fail(source, "Static function range is outside the original source buffer")
        return SourceCallbackSyntax(
            emptyList(),
            signature,
            callbackType,
            sourceBuffer.substring(source.startOffset, source.endOffset),
            source,
            target
        )
    }

    private fun staticFunctionType(item: ParserRuleContext, env: BindingEnvironment): TypeRef<*>? {
        val syntax = when (item) {
            is P.CallArgumentContext -> item.expression()
            is P.ExpressionContext -> item
            else -> null
        } ?: return null
        val name = staticFunctionName(syntax, env) ?: return null
        val types = (StructuredSourceFacts.functions(syntax, env) ?: env.functions(name)).mapNotNull { candidate ->
            if (candidate.sourceBody == null) return@mapNotNull null
            val function = candidate.function as? ImmutableFunctionRef<*> ?: return@mapNotNull null
            env.types.inlineType(FunctionSignature.of(function.parameters(), function.returnType()))
        }.distinctBy { (it as? ImmutableTypeRef<*>)?.key }
        return types.singleOrNull()
    }

    private fun bind(tree: ParserRuleContext, env: BindingEnvironment): BoundExpr {
        StructuredSourceFacts.literal(tree, env)?.let { return it }
        return when (tree) {
        is P.ExpressionContext -> tree.lambdaCallExpression()?.let { syntax ->
            val function = syntax.functionCall()
                ?: fail(span(syntax, env), "Static INLINE trailing callbacks require a namespace call with argument parentheses")
            if (syntax.selector().isNotEmpty()) fail(span(syntax, env), "Static INLINE calls cannot select from a trailing callback result")
            val call = call(function, env, syntax.inlineLambda())
            BoundExpr(BoundExprNode.Call(call), call.target.function.returnType(), null, call.effect, call.source)
        } ?: tree.primary()?.let { bind(it, env) } ?: bind(tree.commonBinaryOperatorExpression(), env)
        is P.PrimaryContext -> when {
            tree.value() != null -> literal(tree.value(), env)
            tree.`var`() != null -> bind(tree.`var`(), env)
            tree.type() != null -> {
                val target = StructuredSourceFacts.type(tree.type(), env) ?: env.type(tree.type().text)
                    ?: fail(span(tree, env), "Type literal is outside the immutable declaration snapshot")
                val value = ConstantValue.TypeValue(target)
                BoundExpr(
                    BoundExprNode.Literal(value),
                    env.types.ref(top.mcfpp.type.MCFPPConcreteType.Type),
                    value,
                    BoundEffect.PURE,
                    span(tree, env)
                )
            }
            else -> fail(span(tree, env), "This primary expression has no pure source binding")
        }
        is P.VarContext -> when {
            tree.functionCall() != null -> bindCall(tree.functionCall(), env)
            tree.varWithSuffix() != null -> {
                val suffix = tree.varWithSuffix()
                val typeValue = if (suffix.identifierSuffix().isEmpty())
                    (StructuredSourceFacts.symbol(suffix, env) ?: env.symbol(suffix.Identifier().text))?.constant as? ConstantValue.TypeValue else null
                if (typeValue != null) BoundExpr(
                    BoundExprNode.Literal(typeValue),
                    env.types.ref(MCFPPConcreteType.Type),
                    typeValue,
                    BoundEffect.PURE,
                    span(tree, env)
                )
                else read(variableLocation(tree, env), env, span(tree, env))
            }
            tree.bucketExpression()?.expression() != null -> bind(tree.bucketExpression().expression(), env)
            else -> fail(span(tree, env), "This variable form has no pure source binding")
        }
        is P.CommonBinaryOperatorExpressionContext,
        is P.ConditionalOrExpressionContext,
        is P.ConditionalAndExpressionContext,
        is P.EqualityExpressionContext,
        is P.RelationalExpressionContext,
        is P.AdditiveExpressionContext,
        is P.MultiplicativeExpressionContext -> chain(tree, env)
        is P.CastExpressionContext -> {
            val value = bind(tree.unaryExpression(), env)
            val castType = tree.type()
            if (castType == null) value else {
                val target = StructuredSourceFacts.type(castType, env) ?: env.type(castType.text)
                    ?: fail(span(castType, env), "Cannot resolve cast type '${castType.text}' in the immutable type snapshot")
                if (!env.types.canExplicitlyConvert(value.type, target))
                    fail(span(tree, env), "The explicit cast has no proven representation compatibility")
                val convertedConstant = SourceConstantFacts.convert(value.constant, value.type, target, env.types, widening = false)
                BoundExpr(
                    BoundExprNode.Convert(value, target),
                    target,
                    convertedConstant,
                    combine(value.effect, BoundEffect.PURE),
                    span(tree, env)
                )
            }
        }
        is P.UnaryExpressionContext -> if (tree.rightVarExpression() != null) bind(tree.rightVarExpression(), env) else {
            val value = bind(tree.unaryExpression(), env)
            val operator = if (tree.EXCL() != null) UnaryOperator.NOT else UnaryOperator.NEGATE
            val sourceType = env.types.facts(value.type).id
            if (operator == UnaryOperator.NOT && sourceType != top.mcfpp.type.MCFPPBaseType.Bool.typeId)
                fail(span(tree, env), "Logical negation requires bool")
            if (operator == UnaryOperator.NEGATE && sourceType !in setOf(top.mcfpp.type.MCFPPBaseType.Int.typeId, top.mcfpp.type.MCFPPBaseType.Float.typeId))
                fail(span(tree, env), "Numeric negation requires int or float")
            val resultType = if (operator == UnaryOperator.NOT) env.types.ref(top.mcfpp.type.MCFPPBaseType.Bool) else value.type
            val constant = SourceConstantFacts.unary(operator, value.constant)
            BoundExpr(BoundExprNode.Unary(operator, value), resultType, constant, value.effect, span(tree, env))
        }
        is P.RightVarExpressionContext -> bind(tree.varWithSelector(), env)
        is P.VarWithSelectorContext -> bindSelector(tree, env)
        else -> fail(span(tree, env), "Unsupported expression '${tree.text}'")
        }
    }

    private fun bindCall(syntax: P.FunctionCallContext, env: BindingEnvironment): BoundExpr {
        val call = call(syntax, env)
        val target = call.target.function
        val result = target.returnType()
        return BoundExpr(BoundExprNode.Call(call), result, null, call.effect, call.source)
    }

    private fun bindSelector(syntax: P.VarWithSelectorContext, env: BindingEnvironment): BoundExpr {
        // Only explicit snapshot fields are readable here; property accessors are never invoked.
        val property = syntax.jvmAccessExpression().propertyOperator()
        if (property.propertyOperatorExpression().isNotEmpty())
            fail(span(property, env), "Property assignment is outside pure source-expression binding")
        val callSelectors = syntax.selector().withIndex().filter { it.value.`var`()?.functionCall() != null }
        if (callSelectors.isNotEmpty()) {
            if (callSelectors.size != 1 || callSelectors.single().index != syntax.selector().lastIndex)
                fail(span(syntax, env), "A member call result cannot be selected or called further in pure source binding")
            val call = memberCall(syntax, env)
            return BoundExpr(BoundExprNode.Call(call), call.target.function.returnType(), null, call.effect, call.source)
        }
        val primary = property.primary()
        var value = bind(primary, env)
        for (selector in syntax.selector()) {
            val variable = selector.`var`()
            val fieldName = variable.varWithSuffix()?.Identifier()?.text
                ?: fail(span(selector, env), "Only a named snapshot field can be selected")
            val field = env.field(value.type, fieldName)
                ?: fail(span(selector, env), "No declared field '$fieldName' exists in the immutable snapshot")
            value = BoundExpr(
                BoundExprNode.Field(value, field),
                field.type,
                null,
                combine(value.effect, BoundEffect.READ),
                span(selector, env)
            )
            for (suffix in variable.varWithSuffix().identifierSuffix()) {
                val indexSyntax = suffix.expression() ?: fail(span(suffix, env), "An empty field index cannot be bound")
                val index = expression(indexSyntax, env)
                val element = indexedType(value.type, index, span(suffix, env), env)
                value = BoundExpr(
                    BoundExprNode.Index(value, index),
                    element,
                    null,
                    combine(value.effect, index.effect),
                    span(suffix, env)
                )
            }
        }
        return value
    }

    private fun selectorLocation(syntax: P.VarWithSelectorContext, env: BindingEnvironment): BoundLocation = selectorLocation(syntax.jvmAccessExpression(), syntax.selector(), env)

    private fun selectorLocation(
        jvm: P.JvmAccessExpressionContext,
        selectors: List<P.SelectorContext>,
        env: BindingEnvironment
    ): BoundLocation {
        val property = jvm.propertyOperator()
        if (property.propertyOperatorExpression().isNotEmpty())
            fail(span(property, env), "Property assignment cannot be captured as a writable location")
        val base = property.primary().`var`()
            ?: fail(span(property, env), "A writable location must start at a source symbol")
        var location = variableLocation(base, env)
        for (selector in selectors) {
            val selected = selector.`var`()?.varWithSuffix()
                ?: fail(span(selector, env), "A writable location must select a named field")
            val fieldName = selected.Identifier().text
            val field = env.field(location.type, fieldName)
                ?: fail(span(selector, env), "No declared field '$fieldName' exists in the immutable snapshot")
            location = location.copy(
                path = immutableList(location.path + BoundLocationStep.Field(field)),
                type = field.type,
                writable = location.writable && field.writable,
                source = span(selector, env)
            )
            for (suffix in selected.identifierSuffix()) {
                val expressionSyntax = suffix.expression() ?: fail(span(suffix, env), "An empty field index cannot be captured")
                val index = expression(expressionSyntax, env)
                val writable = location.writable && env.types.facts(location.type).indicesWritable
                val element = indexedType(location.type, index, span(suffix, env), env)
                location = location.copy(
                    path = immutableList(
                        location.path + BoundLocationStep.Index(
                            index,
                            intConstant(index.constant)
                        )
                    ),
                    type = element,
                    writable = writable,
                    source = span(suffix, env)
                )
            }
        }
        return location
    }

    private fun chain(tree: ParserRuleContext, env: BindingEnvironment): BoundExpr {
        val parts = (0 until tree.childCount).mapNotNull { index -> tree.getChild(index) as? ParserRuleContext }
        if (parts.size == 1) return bind(parts.single(), env)
        var result = bind(parts.first(), env)
        var childIndex = 1
        for (partIndex in 1 until parts.size) {
            val right = bind(parts[partIndex], env)
            while (childIndex < tree.childCount && binaryOperator(tree.getChild(childIndex).text) == null) childIndex++
            if (childIndex >= tree.childCount) fail(span(tree, env), "Missing binary operator")
            val operator = tree.getChild(childIndex).text
            childIndex++
            val operation = binaryOperator(operator) ?: fail(span(tree, env), "Unsupported operator '$operator'")
            val leftType = env.types.facts(result.type).id
            val rightType = env.types.facts(right.type).id
            val resultLanguageType = if (operation == BinaryOperator.ADD &&
                leftType == top.mcfpp.type.MCFPPBaseType.String.typeId &&
                rightType == top.mcfpp.type.MCFPPBaseType.String.typeId
            ) top.mcfpp.type.MCFPPBaseType.String.typeId
            else top.mcfpp.type.TypeRelations.resolveOperator(operator, leftType, rightType)
                ?: fail(span(tree, env), "Operator '$operator' is not defined for '${result.type.name()}' and '${right.type.name()}'")
            val resultType = env.types.ref(resultLanguageType)
            val constant = fold(operation, resultLanguageType, result.constant, right.constant)
            result = BoundExpr(
                BoundExprNode.Binary(operation, result, right),
                resultType,
                constant,
                combine(result.effect, right.effect),
                span(tree, env)
            )
        }
        return result
    }

    private fun literal(value: P.ValueContext, env: BindingEnvironment): BoundExpr {
        val text = value.text
        val pair = when {
            value.NULL() != null -> env.types.ref(top.mcfpp.type.MCFPPBaseType.Any) to ConstantValue.NullValue
            value.nbtValue()?.nbtBool() != null -> env.types.ref(top.mcfpp.type.MCFPPBaseType.Bool) to ConstantValue.Bool(text == "true")
            value.nbtValue()?.nbtInt() != null -> {
                val number = text.toLongOrNull() ?: fail(span(value, env), "Invalid integer literal '$text'")
                if (number !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                    fail(span(value, env), "Integer literal '$text' is outside the supported int32 range")
                env.types.ref(top.mcfpp.type.MCFPPBaseType.Int) to
                    ConstantValue.Integral(number)
            }
            value.nbtValue()?.nbtFloat() != null -> {
                val numeric = text.trimEnd('f', 'F').toFloatOrNull()
                    ?: fail(span(value, env), "Invalid float literal '$text'")
                if (!numeric.isFinite()) fail(span(value, env), "Float literal '$text' is not finite")
                env.types.ref(top.mcfpp.type.MCFPPBaseType.Float) to
                    ConstantValue.FloatBits(numeric.toRawBits())
            }
            value.LineString() != null -> env.types.ref(top.mcfpp.type.MCFPPBaseType.String) to
                ConstantValue.Text((top.mcfpp.nbt.tags.Tag.toNBT(text) as top.mcfpp.nbt.tags.primitive.StringTag).value)
            else -> fail(span(value, env), "Only closed scalar literals have source binding in this stage")
        }
        val constant = pair.second ?: fail(span(value, env), "This scalar literal has no supported closed representation")
        return BoundExpr(BoundExprNode.Literal(constant), pair.first, constant, BoundEffect.PURE, span(value, env))
    }

    private fun variableLocation(tree: P.VarContext, env: BindingEnvironment): BoundLocation {
        tree.varWithSuffix()?.let { return variableLocation(it, env) }
        tree.functionCall()?.let {
            fail(span(tree, env), "A function result is a temporary value, not a writable source location")
        }
        tree.bucketExpression()?.let { bucket ->
            val inner = bucket.expression() ?: fail(span(tree, env), "An empty expression is not a writable source location")
            // Parentheses may preserve a location, but never turn a computed value into one.
            return location(inner, env).copy(source = span(tree, env))
        }
        fail(span(tree, env), "A writable location must be a source variable or explicit field path")
    }

    private fun variableLocation(tree: P.VarWithSuffixContext, env: BindingEnvironment): BoundLocation {
        val name = tree.Identifier().text
        val root = StructuredSourceFacts.symbol(tree, env) ?: env.symbol(name) ?: fail(span(tree, env), "Unknown source symbol '$name'")
        var type = root.type
        var writable = root.writable
        val steps = arrayListOf<BoundLocationStep>()
        for (suffix in tree.identifierSuffix()) {
            val indexSyntax = suffix.expression() ?: fail(span(suffix, env), "An empty index is not a writable location")
            val index = expression(indexSyntax, env)
            val constant = intConstant(index.constant)
            steps += BoundLocationStep.Index(index, constant)
            writable = writable && env.types.facts(type).indicesWritable
            type = indexedType(type, index, span(suffix, env), env)
        }
        return BoundLocation(
            root,
            immutableList(steps),
            type,
            writable,
            root.executionContextId ?: env.executionContextId,
            span(tree, env)
        )
    }

    private fun indexedType(
        receiver: TypeRef<*>,
        index: BoundExpr,
        source: SourceSpan,
        env: BindingEnvironment
    ): TypeRef<*> {
        if (env.types.facts(index.type).id != MCFPPBaseType.Int.typeId)
            fail(index.source, "List indices must have type int")
        val element = env.types.facts(receiver).elementType
            ?: fail(source, "The indexed type has no statically declared element type")
        return env.types.ref(element)
    }

    private fun read(location: BoundLocation, env: BindingEnvironment, source: SourceSpan): BoundExpr = BoundExpr(
        BoundExprNode.Read(location),
        location.type,
        location.root.constant.takeIf { location.path.isEmpty() },
        location.effectOrder.fold(BoundEffect.READ, ::combine),
        source
    )

    private fun intConstant(value: ConstantValue?): Int? = (value as? ConstantValue.Integral)?.value
        ?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()

    private data class CallMatch(
        val declaration: SourceFunction,
        val ranks: List<Int>,
        val targets: List<TypeRef<*>>,
        val defaults: Int
    )

    private fun selectTarget(
        candidates: List<SourceFunction>,
        readonly: List<BoundExpr>,
        argumentTypes: List<TypeRef<*>?>,
        source: SourceSpan,
        env: BindingEnvironment
    ): SourceFunction {
        fun match(candidate: SourceFunction): CallMatch? {
            val function = candidate.function as? ImmutableFunctionRef<*> ?: return null
            val params = function.parameters()
            if (readonly.size > candidate.readonlyParameters.size || argumentTypes.size > params.size) return null
            val missingReadonly = (readonly.size until candidate.readonlyParameters.size).toSet()
            val missingNormal = (argumentTypes.size until params.size).toSet()
            if (!candidate.defaultedReadonlyParameters.containsAll(missingReadonly) ||
                !candidate.defaultedNormalParameters.containsAll(missingNormal)
            ) return null
            if (readonly.any { it.constant == null }) return null
            if (!SourceDefaultBinder.acceptsPrepared(candidate, readonly, env.types)) return null
            val targetTypes = candidate.readonlyParameters.take(readonly.size).map { it.type } +
                params.take(argumentTypes.size).map { it.type() }
            val ranks = arrayListOf<Int>()
            for ((actual, target) in readonly.map { it.type }.zip(candidate.readonlyParameters.map { it.type })) {
                ranks += env.types.conversionRank(actual, target) ?: return null
            }
            for ((param, actual) in params.take(argumentTypes.size).zip(argumentTypes)) {
                if (actual == null) {
                    ranks += 0
                } else {
                    ranks += env.types.conversionRank(actual, param.type()) ?: return null
                }
            }
            return CallMatch(
                candidate,
                immutableList(ranks),
                immutableList(targetTypes),
                missingReadonly.size + missingNormal.size
            )
        }
        val matches = candidates.mapNotNull(::match)
        fun better(a: CallMatch, b: CallMatch): Boolean {
            if (a.ranks.size == b.ranks.size &&
                a.ranks.zip(b.ranks).all { it.first <= it.second } &&
                a.ranks.zip(b.ranks).any { it.first < it.second }
            ) return true
            if (a.ranks != b.ranks) return false
            if (a.targets.size == b.targets.size &&
                a.targets.zip(b.targets).all { (x, y) -> env.types.isSubtype(x, y) } &&
                a.targets.zip(b.targets).any { (x, y) -> !env.types.isSubtype(y, x) }
            ) return true
            return a.targets == b.targets && a.defaults < b.defaults
        }
        val best = matches.filter { candidate -> matches.none { other -> other !== candidate && better(other, candidate) } }
        return best.singleOrNull()?.declaration ?: fail(
            source,
            when {
                candidates.isEmpty() -> "No declaration snapshot contains this function"
                matches.isEmpty() -> "No overload accepts these source argument types and defaults"
                else -> "The source call is ambiguous"
            }
        )
    }

    private fun validateSelectedArguments(
        target: SourceFunction,
        readonly: List<BoundExpr>,
        arguments: List<BoundCallArgument>,
        source: SourceSpan,
        env: BindingEnvironment
    ): List<BoundCallArgument> {
        if (readonly.size > target.readonlyParameters.size)
            fail(source, "Too many readonly arguments for the selected declaration")
        for ((value, parameter) in readonly.zip(target.readonlyParameters)) {
            if (value.constant == null) fail(value.source, "Readonly arguments require a proven closed constant")
            if (env.types.conversionRank(value.type, parameter.type) == null)
                fail(value.source, "Readonly argument type does not match the selected declaration")
        }
        val function = target.function as? ImmutableFunctionRef<*>
            ?: fail(source, "Call target is not a compiler-issued immutable function snapshot")
        if (arguments.size > function.parameters().size) fail(source, "Too many normal arguments")
        return arguments.mapIndexed { index, argument ->
            val parameter = function.parameters()[index]
            when (argument) {
                is BoundCallArgument.Value -> when (parameter.mode()) {
                    ParamMode.VALUE -> {
                        if (env.types.conversionRank(argument.expression.type, parameter.type()) == null)
                            fail(argument.source, "Argument type does not match the selected function")
                        argument
                    }
                    ParamMode.CONST -> {
                        if (function.kind != FunctionKind.MACRO || argument.expression.constant == null)
                            fail(argument.source, "CONST requires a proven constant argument to a macro")
                        if (env.types.conversionRank(argument.expression.type, parameter.type()) == null)
                            fail(argument.source, "CONST argument type does not match the selected macro")
                        argument.copy(mode = ParamMode.CONST)
                    }
                    ParamMode.REF -> fail(argument.source, "Compiler-only REF parameters require compiler-issued location handles; public source calls pass ScoreRef or NBTRef<T> as VALUE")
                    ParamMode.INLINE -> fail(argument.source, "INLINE parameters require a direct lambda")
                }
                is BoundCallArgument.Reference -> {
                    if (function.kind != FunctionKind.MACRO || parameter.mode() != ParamMode.REF)
                        fail(argument.source, "Only macro REF parameters accept explicit ref arguments")
                    if (!argument.location.writable || !parameter.type().sameType(argument.location.type))
                        fail(argument.source, "REF requires a writable location with the exact parameter type")
                    argument
                }
                is BoundCallArgument.Callback -> {
                    if (function.kind != FunctionKind.MACRO || parameter.mode() != ParamMode.INLINE)
                        fail(argument.source, "Only macro INLINE parameters accept callback syntax")
                    val expected = parameter.inlineSignature() ?: parameter.type().inlineSignature()
                        ?: fail(argument.source, "Selected INLINE parameter has no callback signature")
                    val expectedType = env.types.inlineType(expected)
                        ?: fail(argument.source, "Selected callback signature is outside the type snapshot")
                    if (!expectedType.sameType(argument.callback.callbackType))
                        fail(argument.source, "Callback signature does not match the selected macro parameter")
                    argument
                }
            }
        }
    }

    private fun fold(op: BinaryOperator, resultType: TypeId, left: ConstantValue?, right: ConstantValue?): ConstantValue? = SourceConstantFacts.binary(op, resultType, left, right)

    private fun binaryOperator(text: String): BinaryOperator? = when (text) {
        "+" -> BinaryOperator.ADD
        "-" -> BinaryOperator.SUBTRACT
        "*" -> BinaryOperator.MULTIPLY
        "/" -> BinaryOperator.DIVIDE
        "%" -> BinaryOperator.MODULO
        "==" -> BinaryOperator.EQ
        "!=" -> BinaryOperator.NE
        "<" -> BinaryOperator.LT
        "<=" -> BinaryOperator.LE
        ">" -> BinaryOperator.GT
        ">=" -> BinaryOperator.GE
        "&&" -> BinaryOperator.AND
        "||" -> BinaryOperator.OR
        else -> null
    }

    private fun combine(left: BoundEffect, right: BoundEffect): BoundEffect = when {
        left == BoundEffect.UNKNOWN || right == BoundEffect.UNKNOWN -> BoundEffect.UNKNOWN
        left == BoundEffect.WRITE || right == BoundEffect.WRITE -> BoundEffect.WRITE
        left == BoundEffect.CALL || right == BoundEffect.CALL -> BoundEffect.CALL
        left == BoundEffect.READ || right == BoundEffect.READ -> BoundEffect.READ
        else -> BoundEffect.PURE
    }

    private fun span(context: ParserRuleContext, env: BindingEnvironment): SourceSpan = span(context.start, context.stop ?: context.start, env)
    private fun span(start: Token, end: Token, env: BindingEnvironment): SourceSpan {
        val endText = end.text.orEmpty()
        val lines = endText.split('\n')
        val endLine = end.line + lines.size - 1
        val endColumn = if (lines.size == 1) end.charPositionInLine + endText.length + 1 else lines.last().length + 1
        val startOffset = start.startIndex.coerceAtLeast(0)
        val endOffset = (end.stopIndex + 1).coerceAtLeast(startOffset)
        return SourceSpan(env.sourceFile, startOffset, endOffset, start.line, start.charPositionInLine + 1, endLine, endColumn)
    }

    private fun fail(source: SourceSpan, message: String): Nothing = throw SourceBindingFailure(source, message)
}
