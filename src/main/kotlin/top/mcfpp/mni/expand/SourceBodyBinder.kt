package top.mcfpp.mni.expand

import org.antlr.v4.runtime.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.mni.api.*
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.TypeRelations
import java.util.concurrent.atomic.AtomicLong
import top.mcfpp.antlr.mcfppParser as P

/** The caller must supply frozen original declaration environments for static functions.
 * No name in a static body is ever resolved against the callback's calling environment. */
internal interface SourceBodyBinder {
    fun bind(
        syntax: SourceCallbackSyntax,
        environment: BindingEnvironment,
        declarations: Map<FunctionKey, BindingEnvironment> = emptyMap(),
        constParameters: Map<Int, TypedConstant> = emptyMap()
    ): BoundCallback
}

internal class SnapshotSourceBodyBinder : SourceBodyBinder {
    override fun bind(
        syntax: SourceCallbackSyntax,
        environment: BindingEnvironment,
        declarations: Map<FunctionKey, BindingEnvironment>,
        constParameters: Map<Int, TypedConstant>
    ): BoundCallback = bindBody(syntax, environment, immutableMap(declarations), immutableMap(constParameters))

    private fun bindBody(
        syntax: SourceCallbackSyntax,
        caller: BindingEnvironment,
        declarations: Map<FunctionKey, BindingEnvironment>,
        constParameters: Map<Int, TypedConstant>
    ): BoundCallback {
        val callbackType = caller.types.inlineType(syntax.signature)
            ?: fail(syntax.source, "Callback signature is outside the immutable type snapshot")
        if (!callbackType.sameType(syntax.callbackType)) fail(syntax.source, "Callback type differs from its selected signature")
        val signature = callbackType.inlineSignature() ?: fail(syntax.source, "Callback type has no canonical signature")
        val target = syntax.functionTarget
        val original: BindingEnvironment
        val text: String
        val site: SourceSpan
        val names: List<String>
        val formalSymbols: List<SourceSymbol>?
        val readonly: List<SourceSymbol>
        val identity: CallbackId
        if (target == null) {
            original = caller
            text = syntax.sourceText
            site = syntax.source
            names = syntax.parameterNames.toList()
            formalSymbols = null
            readonly = emptyList()
            identity = sourceCallbackIdentity(syntax)
        } else {
            if (caller.functions.none { it === target }) fail(syntax.source, "Static callback is not a visible source declaration")
            val function = target.function as? ImmutableFunctionRef<*>
                ?: fail(syntax.source, "Static callback requires a compiler-issued function snapshot")
            if (function.kind != FunctionKind.ORDINARY) fail(syntax.source, "Native, macro and dynamic functions cannot supply a static callback body")
            val body = target.sourceBody ?: fail(syntax.source, "Static callback has no source body")
            original = declarations[target.declarationIdentity]
                ?: fail(syntax.source, "Static callback requires its original declaration environment")
            if (original.types !== caller.types || original.functions.none { it === target } || original.sourceFile != body.source.file)
                fail(syntax.source, "Static body environment does not own the original declaration in this type domain")
            if (original.types.inlineType(function.signature)?.sameType(callbackType) != true)
                fail(syntax.source, "Static body signature differs from the selected callback signature")
            if (target.normalSymbols.size != signature.parameters().size || target.readonlySymbols.size != target.readonlyParameters.size)
                fail(syntax.source, "Static callback body requires original formal symbol identities")
            formalSymbols = target.normalSymbols
            names = formalSymbols.map { it.name }
            readonly = target.readonlySymbols.mapIndexed { index, symbol ->
                val argument = target.preparedReadonly?.arguments?.getOrNull(index)
                val value = if (argument != null) {
                    if (original.types.conversionRank(argument.type, symbol.type) == null)
                        fail(argument.source, "Static callback readonly binding does not match its formal type")
                    SourceConstantFacts.convert(argument.value, argument.type, symbol.type, original.types)
                        ?: fail(argument.source, "Static callback readonly binding has no closed formal representation")
                } else symbol.constant
                    ?: fail(syntax.source, "Static callback readonly formal requires a prepared closed binding")
                SourceConstantFacts.validateClosed(symbol.type, value, original.types, argument?.source ?: syntax.source)
                symbol.copy(writable = false, constant = value)
            }
            text = body.sourceText
            site = body.source
            identity = sourceCallbackIdentity(syntax)
        }
        if (names.size != signature.parameters().size || names.distinct().size != names.size)
            fail(syntax.source, "Callback formal names do not match the selected signature")
        // C_INLINE supplies these facts after binding the actual CONST arguments. They are
        // evidence for this invocation, never a request to execute a source initializer.
        constParameters.forEach { (index, value) ->
            val parameter = signature.parameters().getOrNull(index)
                ?: fail(value.source, "CONST callback binding index is outside the signature")
            if (parameter.mode() != ParamMode.CONST) fail(value.source, "Only CONST callback parameters accept a closed binding")
            if (!caller.types.owns(value.type) || !value.type.sameType(parameter.type()))
                fail(value.source, "CONST callback binding requires the exact canonical parameter type")
            SourceConstantFacts.validateClosed(value.type, value.value, caller.types, value.source)
        }
        if (site.endOffset - site.startOffset != text.length) fail(site, "Callback body text differs from its original source range")
        val tree = parseCallbackBlock(syntax)
        val state = State(original, signature, declarations, site, text)
        val parameters = signature.parameters().mapIndexed { index, parameter ->
            val symbol = formalSymbols?.get(index) ?: SourceSymbol(names[index], parameter.type(), state.symbolId())
            if (!symbol.type.sameType(parameter.type())) fail(syntax.source, "Callback formal type differs from its original declaration")
            BoundCallbackParameter(
                symbol.copy(
                    writable = parameter.mode() in setOf(ParamMode.VALUE, ParamMode.REF),
                    constant = constParameters[index]?.value
                ),
                parameter.mode()
            )
        }
        val body = state.body(tree, parameters.map { it.symbol } + readonly)
        return BoundCallback(
            identity, callbackType, signature, parameters, readonly, state.captures,
            body, state.returnRegion, syntax.source, state.span(tree.syntax), text, target, state.nested
        )
    }

    /** A parsed callback body: a trailing expression is a callback-local return. */
    internal class CallbackBody(
        val statements: List<P.StatementContext>,
        val tailExpression: P.ExpressionContext?,
        val syntax: ParserRuleContext
    )

    private inner class State(
        val original: BindingEnvironment,
        val signature: InlineSignature<*>,
        val declarations: Map<FunctionKey, BindingEnvironment>,
        val site: SourceSpan,
        text: String
    ) {
        private val expressions = SnapshotSourceCodeBinder()
        private val session = original.scopeId.session
        private val used = original.symbols.values.map { it.symbol }.toMutableSet()
        private val owned = hashSetOf<SymbolId>()
        val captures = linkedMapOf<SymbolId, SourceSymbol>()
        val nested = arrayListOf<SourceCallbackBodyRequest>()
        val returnRegion = RegionId(session, next())
        private val sourceText = " ".repeat(site.startOffset) + text
        private val scopes = arrayListOf<LinkedHashMap<String, SourceSymbol>>()
        private val semanticSymbols = LinkedHashMap(original.structuredSymbols)
        private val scopeIds = arrayListOf<ScopeId>()
        private val loops = arrayListOf<RegionId>()

        fun symbolId(): SymbolId {
            var id: SymbolId
            do {
                id = SymbolId(session, next())
            } while (!used.add(id))
            return id
        }

        private fun environment(): BindingEnvironment {
            val visible = LinkedHashMap(original.symbols.mapValues { (_, symbol) ->
                if (symbol.symbol !in owned && !symbol.global) symbol.copy(writable = false) else symbol
            })
            scopes.forEach { visible.putAll(it) }
            return BindingEnvironment(
                original.types,
                visible.values,
                original.functions,
                original.fields.values,
                sourceText,
                scopeIds.last(),
                original.executionContextId,
                site.file,
                structuredSymbols = semanticSymbols.mapValues { (_, symbol) ->
                    if (symbol.symbol !in owned && !symbol.global) symbol.copy(writable = false) else symbol
                }
            )
        }

        fun body(tree: CallbackBody, parameters: List<SourceSymbol>): BoundSourceBlock = scoped(span(tree.syntax), parameters) {
            fun parameterUses(context: ParserRuleContext) {
                val origin = top.mcfpp.model.function.StructuredBodyContexts.origin(context)
                val use = origin?.nameUse
                if (use is top.mcfpp.library.syntax.NameUse.Local) parameters.firstOrNull { it.name == origin?.boundName?.name }?.let {
                    semanticSymbols.putIfAbsent(use, it)
                }
                context.children.orEmpty().filterIsInstance<ParserRuleContext>().forEach(::parameterUses)
            }
            parameterUses(tree.syntax)
            val lambda = (tree.syntax.parent as? P.InlineLambdaContext)?.let {
                top.mcfpp.model.function.StructuredBodyContexts.origin(it)?.node as? top.mcfpp.library.syntax.Expression.Lambda
            }
            lambda?.parameters?.zip(parameters)?.forEach { (declaration, symbol) ->
                semanticSymbols[top.mcfpp.library.syntax.NameUse.Local(declaration)] = symbol
            }
            val statements = tree.statements.flatMap(::statement)
            val tail = tree.tailExpression ?: return@scoped statements
            val value = expression(tail)
            if (signature.returnType().sameType(original.types.ref(MCFPPPrivateType.Void)))
                statements + BoundSourceStatement.Evaluate(value, span(tail))
            else statements + BoundSourceStatement.Return(
                returnRegion, convert(value, signature.returnType()), span(tail))
        }

        private fun block(tree: P.BlockContext): BoundSourceBlock = scoped(span(tree)) {
            tree.curlBlock()?.statement()?.flatMap(::statement) ?: statement(tree.statement())
        }

        private fun scoped(source: SourceSpan, initial: List<SourceSymbol> = emptyList(), action: () -> List<BoundSourceStatement>): BoundSourceBlock {
            val parent = scopeIds.lastOrNull() ?: original.scopeId
            val scope = ScopeId(session, next())
            val symbols = linkedMapOf<String, SourceSymbol>()
            initial.forEach { symbol ->
                if (symbols.put(symbol.name, symbol) != null) fail(source, "Duplicate callback formal name")
                owned.add(symbol.symbol)
                used.add(symbol.symbol)
            }
            scopes.add(symbols)
            scopeIds.add(scope)
            try {
                return BoundSourceBlock(scope, parent, action(), source)
            } finally {
                scopes.removeAt(scopes.lastIndex)
                scopeIds.removeAt(scopeIds.lastIndex)
            }
        }

        private fun expression(tree: P.ExpressionContext): BoundExpr = expressions.expression(tree, environment()).also(::record)

        private fun record(value: BoundExpr) {
            fun capture(symbol: SourceSymbol) {
                if (symbol.symbol !in owned) {
                    val previous = captures.putIfAbsent(symbol.symbol, symbol)
                    if (previous != null && previous != symbol) fail(value.source, "Conflicting captured symbol facts")
                }
            }
            fun location(value: BoundLocation) {
                capture(value.root)
                value.path.forEach { if (it is BoundLocationStep.Index) record(it.expression) }
            }
            when (val node = value.node) {
                is BoundExprNode.Read -> location(node.location)
                is BoundExprNode.Unary -> record(node.operand)
                is BoundExprNode.Binary -> {
                    record(node.left)
                    record(node.right)
                }
                is BoundExprNode.Convert -> record(node.value)
                is BoundExprNode.Field -> record(node.receiver)
                is BoundExprNode.Index -> {
                    record(node.receiver)
                    record(node.index)
                }
                is BoundExprNode.Call -> {
                    node.sourceCall.readonlyArguments.forEach(::record)
                    node.sourceCall.arguments.forEach { argument ->
                        when (argument) {
                            is BoundCallArgument.Value -> record(argument.expression)
                            is BoundCallArgument.Reference -> location(argument.location)
                            is BoundCallArgument.Callback -> {
                                nested.add(SourceCallbackBodyRequest(argument.callback, environment(), declarations))
                            }
                        }
                    }
                }
                is BoundExprNode.Literal -> Unit
                is BoundExprNode.Unsupported -> fail(value.source, "Unsupported bound source expression")
            }
        }

        private fun statement(tree: P.StatementContext): List<BoundSourceStatement> {
            val source = span(tree)
            tree.fieldDeclaration()?.let { declaration ->
                if (declaration.fieldModifier()?.let { it.CONST() == null } == true)
                    fail(source, "This source local modifier has no structured callback representation")
                val constant = declaration.fieldModifier()?.CONST() != null
                val name = declaration.Identifier().text
                if (name in scopes.last()) fail(source, "Duplicate source local '$name'")
                val initial = declaration.expression()?.let(::expression)
                val type = declaration.type()?.let {
                    StructuredSourceFacts.type(it, environment()) ?: environment().type(it.text)
                        ?: fail(span(it), "Local type is outside the immutable declaration snapshot")
                }
                    ?: initial?.type ?: fail(source, "A source local requires a declared or inferred type")
                if (type.inlineSignature() != null) fail(source, "Inline callbacks cannot escape into source locals")
                val checked = initial?.let { convert(it, type) }
                if (constant && checked?.constant == null) fail(source, "CONST source local requires a proven closed value")
                val symbol = SourceSymbol(name, type, symbolId(), !constant, checked?.constant.takeIf { constant }, original.executionContextId)
                scopes.last()[name] = symbol
                top.mcfpp.model.function.StructuredBodyContexts.declaration(declaration)?.let {
                    semanticSymbols[top.mcfpp.library.syntax.NameUse.Local(it)] = symbol
                }
                owned.add(symbol.symbol)
                return listOf(BoundSourceStatement.Local(symbol, checked, constant, source))
            }
            tree.statementExpression()?.let { statement ->
                val value = expression(statement.expression())
                if (value.type.inlineSignature() != null) fail(value.source, "Inline callbacks cannot escape as source values")
                val destination = statement.varWithSelector() ?: return listOf(BoundSourceStatement.Evaluate(value, source))
                val location = expressions.location(destination, environment())
                if (location.root.symbol !in owned && !location.root.global)
                    fail(span(destination), "Captured local '${location.root.name}' is read-only; return an explicit accumulated value instead")
                if (!location.writable) fail(span(destination), "Assignment requires a writable source location")
                record(BoundExpr(BoundExprNode.Read(location), location.type, null, BoundEffect.READ, location.source))
                val operator = when (statement.assignmentOperator().text) {
                    "=" -> null
                    "+=" -> BinaryOperator.ADD
                    "-=" -> BinaryOperator.SUBTRACT
                    "*=" -> BinaryOperator.MULTIPLY
                    "/=" -> BinaryOperator.DIVIDE
                    "%=" -> BinaryOperator.MODULO
                    else -> fail(source, "Unsupported source assignment operator")
                }
                val checked = if (operator == null) convert(value, location.type) else {
                    val spelling = statement.assignmentOperator().text.removeSuffix("=")
                    val result = TypeRelations.resolveOperator(
                        spelling,
                        original.types.facts(location.type).id,
                        original.types.facts(value.type).id
                    ) ?: fail(source, "Compound assignment operator does not accept these types")
                    if (original.types.conversionRank(original.types.ref(result), location.type) == null)
                        fail(source, "Compound assignment result cannot be stored in the original location")
                    value
                }
                return listOf(BoundSourceStatement.Assign(location, checked, operator, source))
            }
            tree.ifStatement()?.let { conditional ->
                val branches = conditional.elseIfStatement()
                fun otherwise(index: Int): BoundSourceBlock? {
                    val branch = branches.getOrNull(index)
                        ?: return conditional.elseStatement()?.block()?.let(::block)
                    return scoped(span(branch)) {
                        listOf(
                            BoundSourceStatement.Conditional(
                                condition(branch.conditionExpression()),
                                block(branch.block()),
                                otherwise(index + 1),
                                span(branch)
                            )
                        )
                    }
                }
                return listOf(BoundSourceStatement.Conditional(condition(conditional.conditionExpression()), block(conditional.block()), otherwise(0), source))
            }
            val whileLoop = tree.whileStatement()
            val doLoop = tree.doWhileStatement()
            if (whileLoop != null || doLoop != null) {
                val region = RegionId(session, next())
                val condition = if (whileLoop != null) condition(whileLoop.conditionExpression())
                    else condition(doLoop!!.expression())
                loops.add(region)
                val body = try {
                    block(whileLoop?.block() ?: doLoop!!.block())
                } finally {
                    loops.removeAt(loops.lastIndex)
                }
                return listOf(BoundSourceStatement.Loop(region, condition, body, doLoop != null, source))
            }
            tree.returnStatement()?.let { statement ->
                val value = statement.expression()?.let(::expression)
                val isVoid = signature.returnType().sameType(original.types.ref(MCFPPPrivateType.Void))
                if (isVoid && value != null || !isVoid && value == null) fail(source, "Callback return value differs from its result signature")
                return listOf(BoundSourceStatement.Return(returnRegion, value?.let { convert(it, signature.returnType()) }, source))
            }
            tree.controlStatement()?.let { statement ->
                val loop = loops.lastOrNull() ?: fail(source, "break/continue cannot leave the current callback's loop region")
                return listOf(
                    if (statement.BREAK() != null) BoundSourceStatement.Break(loop, source)
                    else BoundSourceStatement.Continue(loop, source)
                )
            }
            if (tree.SEMICOLON() != null) return emptyList()
            fail(source, "This source statement has no structured callback binding")
        }

        private fun condition(tree: P.ExpressionContext): BoundExpr = expression(tree).also {
            if (!it.type.sameType(original.types.ref(MCFPPBaseType.Bool))) fail(it.source, "Callback condition requires bool")
        }

        private fun condition(tree: P.ConditionExpressionContext): BoundExpr = expressions.condition(tree, environment()).also {
            if (!it.type.sameType(original.types.ref(MCFPPBaseType.Bool))) fail(it.source, "Callback condition requires bool")
        }

        private fun convert(value: BoundExpr, target: TypeRef<*>): BoundExpr {
            if (value.type.inlineSignature() != null) fail(value.source, "Inline callbacks cannot escape as values")
            if (original.types.conversionRank(value.type, target) == null) fail(value.source, "Source value does not match its destination type")
            return if (value.type.sameType(target)) value else BoundExpr(
                BoundExprNode.Convert(value, target),
                target,
                SourceConstantFacts.convert(value.constant, value.type, target, original.types, widening = true),
                value.effect,
                value.source
            )
        }

        private fun fragment(tree: ParserRuleContext): String = sourceText.substring(tree.start.startIndex, tree.stop.stopIndex + 1)
        fun span(tree: ParserRuleContext): SourceSpan = sourceSpan(tree, site.file)
    }

    companion object {
        // Source template identities are separate from compiler/global temporary counters.
        private val identities = AtomicLong(Long.MIN_VALUE)
        private fun next(): Long = identities.incrementAndGet()
        private fun fail(source: SourceSpan, message: String): Nothing = throw SourceBindingFailure(source, message)

        /**
         * Parse the exact original body text for a callback template. Used both by the real body
         * binder and by pure structural capture discovery; neither path executes a declaration.
         */
        internal fun parseCallbackBlock(syntax: SourceCallbackSyntax): CallbackBody {
            val target = syntax.functionTarget
            // A static callback's syntax only names the original function: its body text and span
            // live on the prepared function snapshot, never in the callback expression text.
            val text = target?.sourceBody?.sourceText ?: syntax.sourceText
            val source = target?.sourceBody?.source ?: syntax.source
            if (target != null && target.sourceBody == null) fail(source, "Static callback has no source body")
            if (source.endOffset - source.startOffset != text.length)
                fail(source, "Callback body text differs from its original source range")
            val structured = target?.sourceBody?.structuredSyntax ?: syntax.structuredSyntax
            if (structured != null) {
                val root = structured.fresh()
                val shown = root.start.inputStream.getText(org.antlr.v4.runtime.misc.Interval(root.start.startIndex, root.stop.stopIndex))
                if (shown != text || root.start.startIndex != source.startOffset || root.stop.stopIndex + 1 != source.endOffset)
                    fail(source, "Structured callback display range differs from its prepared snapshot")
                return when (root) {
                    is P.InlineLambdaContext -> {
                        val names = root.lambdaParameterList()?.lambdaParameter()?.map { it.Identifier().text }.orEmpty().ifEmpty {
                            if (syntax.signature.parameters().size == 1) listOf("it") else emptyList()
                        }
                        if (names != syntax.parameterNames) fail(source, "Callback formal names differ from structured lambda")
                        val body = root.lambdaBody()
                        CallbackBody(body.statement(), body.lambdaTailExpression()?.expression(), body)
                    }
                    is P.CurlBlockContext -> CallbackBody(root.statement(), null, root)
                    else -> fail(source, "LIB_BODY_UNSUPPORTED: Unknown structured callback root")
                }
            }
            if (syntax.structuredRequired || target?.sourceBody?.structuredRequired == true)
                fail(source, "LIB_BODY_UNSUPPORTED: Library callback has no structured session provider")
            if (target == null && syntax.frozenSyntax != null) {
                val lambda = try { syntax.frozenSyntax.restore(text, source.startOffset, source.endOffset) }
                catch (failure: IllegalArgumentException) { fail(source, failure.message ?: "Invalid frozen callback syntax") }
                val names = lambda.lambdaParameterList()?.lambdaParameter()?.map { it.Identifier().text }.orEmpty().ifEmpty {
                    if (syntax.signature.parameters().size == 1) listOf("it") else emptyList()
                }
                if (names != syntax.parameterNames) fail(source, "Callback formal names differ from the frozen syntax")
                val body = lambda.lambdaBody()
                return CallbackBody(body.statement(), body.lambdaTailExpression()?.expression(), body)
            }
            return parse(text, source) { parser ->
                if (target == null) {
                    if (syntax.emptyBody) {
                        val block = parser.curlBlock()
                        return@parse CallbackBody(emptyList(), null, block)
                    }
                    val lambda = parser.inlineLambda()
                    val names = lambda.lambdaParameterList()?.lambdaParameter()?.map { it.Identifier().text }.orEmpty().ifEmpty {
                        if (syntax.signature.parameters().size == 1) listOf("it") else emptyList()
                    }
                    if (names != syntax.parameterNames)
                        fail(source, "Callback formal names differ from the original lambda syntax")
                    val body = lambda.lambdaBody()
                    CallbackBody(body.statement(), body.lambdaTailExpression()?.expression(), body)
                } else {
                    val block = parser.curlBlock()
                    CallbackBody(block.statement(), null, block)
                }
            }
        }

        private fun <T> parse(text: String, source: SourceSpan, rule: (P) -> T): T {
            val listener = object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?
                ) {
                    fail(source, "Invalid callback source: $msg")
                }
            }
            val lexer = mcfppLexer(CharStreams.fromString(text))
            lexer.removeErrorListeners()
            lexer.addErrorListener(listener)
            val tokens = CommonTokenStream(lexer)
            tokens.fill()
            tokens.tokens.forEach { token ->
                val raw = token as CommonToken
                raw.text = raw.text
                raw.startIndex += source.startOffset
                raw.stopIndex += source.startOffset
                if (raw.line == 1) raw.charPositionInLine += source.startColumn - 1
                raw.line += source.startLine - 1
            }
            val parser = P(tokens)
            parser.removeErrorListeners()
            // The selected callback signature already fixes the root syntax. Try the
            // context-free prediction first; nested lambda/value alternatives otherwise
            // cause pathological LL closure work when rebinding a valid static body.
            parser.interpreter.predictionMode = org.antlr.v4.runtime.atn.PredictionMode.SLL
            parser.errorHandler = BailErrorStrategy()
            val result = try {
                rule(parser).also {
                    if (parser.currentToken.type != Token.EOF)
                        throw org.antlr.v4.runtime.misc.ParseCancellationException()
                }
            } catch (_: org.antlr.v4.runtime.misc.ParseCancellationException) {
                parser.reset()
                parser.interpreter.predictionMode = org.antlr.v4.runtime.atn.PredictionMode.LL
                parser.errorHandler = DefaultErrorStrategy()
                parser.addErrorListener(listener)
                rule(parser)
            }
            if (parser.currentToken.type != Token.EOF) fail(source, "Unexpected trailing callback source")
            return result
        }

        private fun sourceSpan(tree: ParserRuleContext, file: String): SourceSpan {
            val start = tree.start
            val end = tree.stop ?: start
            val lines = end.text.orEmpty().split('\n')
            return SourceSpan(
                file,
                start.startIndex,
                end.stopIndex + 1,
                start.line,
                start.charPositionInLine + 1,
                end.line + lines.size - 1,
                if (lines.size == 1) end.charPositionInLine + lines.single().length + 1 else lines.last().length + 1
            )
        }
    }
}

/**
 * Discover the genuinely free source symbols of one deferred body from its original syntax alone.
 *
 * This is deliberately not a binding pass: no expression is converted, folded or evaluated, so a
 * body whose `const` initializer depends on an actual CONST formal is not closed under a guessed
 * value. Names declared anywhere in this body tree (the formals and every local or nested lambda
 * parameter) are owned by the callback; a read that resolves outside that tree is a free capture.
 * Nested callback bodies are traversed too, because the outer descriptor must forward the runtime
 * capabilities their later insertions will need.
 */
internal fun sourceCaptureManifest(request: SourceCallbackBodyRequest): Map<SymbolId, SourceSymbol> = SourceCaptureScanner(request).scan()

private class SourceCaptureScanner(private val request: SourceCallbackBodyRequest) {
    /** A static callback body resolves against its frozen declaration environment, not the caller. */
    private val environment: BindingEnvironment = request.syntax.functionTarget?.let { target ->
        request.declarations[target.declarationIdentity]
            ?: throw SourceBindingFailure(
                request.syntax.source,
                "Static callback requires its original declaration environment"
            )
    } ?: request.environment
    private val syntax = request.syntax
    private val scopes = ArrayList<MutableSet<String>>()
    private val free = LinkedHashMap<SymbolId, SourceSymbol>()

    fun scan(): Map<SymbolId, SourceSymbol> {
        val tree = SnapshotSourceBodyBinder.parseCallbackBlock(syntax)
        val parameters = when (val target = syntax.functionTarget) {
            null -> syntax.parameterNames
            else -> target.readonlySymbols.map { it.name } + target.normalSymbols.map { it.name }
        }
        scopes += LinkedHashSet(parameters)
        block(tree)
        return immutableMap(free)
    }

    private fun block(tree: SnapshotSourceBodyBinder.CallbackBody) {
        scopes.add(LinkedHashSet<String>())
        try {
            tree.statements.forEach(::statement)
            tree.tailExpression?.let(::expression)
        } finally {
            scopes.removeAt(scopes.lastIndex)
        }
    }

    private fun block(tree: P.CurlBlockContext) {
        scopes.add(LinkedHashSet<String>())
        try {
            tree.statement().forEach(::statement)
        } finally {
            scopes.removeAt(scopes.lastIndex)
        }
    }

    private fun block(tree: P.BlockContext) {
        val curl = tree.curlBlock()
        if (curl != null) {
            block(curl)
            return
        }
        scopes.add(LinkedHashSet<String>())
        try {
            tree.statement()?.let(::statement)
        } finally {
            scopes.removeAt(scopes.lastIndex)
        }
    }

    private fun statement(tree: P.StatementContext) {
        tree.fieldDeclaration()?.let { declaration ->
            // The initializer is evaluated before the declared name becomes visible, exactly as in
            // the real body binder (so a declaration cannot capture itself).
            declaration.expression()?.let(::expression)
            scopes.last().add(declaration.Identifier().text)
            return
        }
        tree.statementExpression()?.let { statement ->
            statement.varWithSelector()?.let(::location)
            expression(statement.expression())
            return
        }
        tree.ifStatement()?.let { conditional ->
            condition(conditional.conditionExpression())
            block(conditional.block())
            conditional.elseIfStatement().forEach { branch ->
                condition(branch.conditionExpression())
                block(branch.block())
            }
            conditional.elseStatement()?.block()?.let { block(it) }
            return
        }
        val whileLoop = tree.whileStatement()
        val doLoop = tree.doWhileStatement()
        if (whileLoop != null || doLoop != null) {
            if (whileLoop != null) condition(whileLoop.conditionExpression()) else expression(doLoop!!.expression())
            block(whileLoop?.block() ?: doLoop!!.block())
            return
        }
        tree.returnStatement()?.expression()?.let(::expression)
        // break/continue, empty statements and statement kinds outside the supported callback
        // subset neither declare nor read a source location. The real binder reports those later.
    }

    private fun expression(tree: P.ExpressionContext) {
        tree.primary()?.let(::visit) ?: tree.commonBinaryOperatorExpression()?.let(::visit)
    }

    private fun condition(tree: P.ConditionExpressionContext) {
        tree.primary()?.let(::visit) ?: tree.commonBinaryOperatorExpression()?.let(::visit)
    }

    private fun location(tree: P.VarWithSelectorContext) = selector(tree)

    private fun visit(tree: ParserRuleContext) {
        when (tree) {
            is P.ExpressionContext -> expression(tree)
            is P.PrimaryContext -> {
                val variable = tree.`var`()
                val range = tree.range()
                when {
                    variable != null -> visit(variable)
                    range != null -> visit(range)
                }
            }
            is P.CommonBinaryOperatorExpressionContext,
            is P.ConditionalOrExpressionContext,
            is P.ConditionalAndExpressionContext,
            is P.EqualityExpressionContext,
            is P.RelationalExpressionContext,
            is P.AdditiveExpressionContext,
            is P.MultiplicativeExpressionContext -> visitChildren(tree)
            is P.CastExpressionContext -> visit(tree.unaryExpression())
            is P.UnaryExpressionContext -> {
                val variable = tree.rightVarExpression()
                if (variable != null) visit(variable) else visit(tree.unaryExpression())
            }
            is P.RightVarExpressionContext -> visit(tree.varWithSelector())
            is P.VarWithSelectorContext -> selector(tree)
            is P.JvmAccessExpressionContext -> visit(tree.propertyOperator())
            is P.PropertyOperatorContext -> {
                visit(tree.primary())
                tree.propertyOperatorExpression().forEach { visit(it.expression()) }
            }
            is P.VarContext -> {
                val bucket = tree.bucketExpression()
                val call = tree.functionCall()
                val suffix = tree.varWithSuffix()
                when {
                    bucket != null -> bucket.expression()?.let(::visit)
                    call != null -> visit(call)
                    suffix != null -> read(suffix)
                }
            }
            is P.BucketExpressionContext -> tree.expression()?.let(::visit)
            is P.FunctionCallContext -> call(tree)
            is P.VarWithSuffixContext -> read(tree)
            is P.IdentifierSuffixContext -> tree.expression()?.let(::visit)
            is P.CallArgumentContext -> {
                val lambda = tree.inlineLambda()
                if (lambda != null) visit(lambda) else tree.expression()?.let(::visit)
            }
            is P.InlineLambdaContext -> lambda(tree)
            is P.RangeContext -> tree.range1().forEach(::visit)
            is P.Range1Context -> tree.`var`()?.let(::visit)
            else -> Unit
        }
    }

    private fun visitChildren(tree: ParserRuleContext) {
        for (index in 0 until tree.childCount) {
            (tree.getChild(index) as? ParserRuleContext)?.let(::visit)
        }
    }

    private fun selector(tree: P.VarWithSelectorContext) {
        visit(tree.jvmAccessExpression())
        tree.selector().forEach { selector ->
            val value = selector.`var`() ?: return@forEach
            val call = value.functionCall()
            val suffix = value.varWithSuffix()
            val bucket = value.bucketExpression()
            when {
                // A selector field name or member call is never a free root symbol.
                call != null -> visit(call)
                suffix != null -> suffix.identifierSuffix().forEach { it.expression()?.let(::visit) }
                bucket != null -> bucket.expression()?.let(::visit)
            }
        }
    }

    private fun read(tree: P.VarWithSuffixContext) {
        val name = tree.Identifier().text
        if (scopes.none { name in it }) {
            environment.symbol(name)?.let { symbol -> free.putIfAbsent(symbol.symbol, symbol) }
        }
        tree.identifierSuffix().forEach { it.expression()?.let(::visit) }
    }

    private fun call(tree: P.FunctionCallContext) {
        val arguments = tree.arguments()
        arguments.readOnlyArgs()?.expressionList()?.expression()?.forEach(::visit)
        val normal = arguments.normalArgs()
        normal.expressionList()?.expression()?.forEach(::visit)
        normal.callArgumentList()?.callArgument()?.forEach(::visit)
    }

    private fun lambda(tree: P.InlineLambdaContext) {
        scopes += LinkedHashSet(tree.lambdaParameterList()?.lambdaParameter()?.map { it.Identifier().text }.orEmpty())
        try {
            val body = tree.lambdaBody()
            body.statement().forEach(::statement)
            body.lambdaTailExpression()?.expression()?.let(::expression)
        } finally {
            scopes.removeAt(scopes.lastIndex)
        }
    }
}
