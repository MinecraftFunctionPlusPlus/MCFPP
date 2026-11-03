package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.Void
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.function.Function
import kotlin.test.assertEquals
import top.mcfpp.antlr.mcfppParser as P

/** Test-only construction of compiler location capabilities. No public `ref` syntax is accepted. */
internal object InternalReferenceCalls {
    fun prepare(text: String, caller: Function, positions: Set<Int>): CompilerCallPreparationResult.Ready {
        val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, "internal-positions.mcfpp"))))
        val syntax = parser.functionCall()
        assertEquals(0, parser.numberOfSyntaxErrors)
        assertEquals(Token.EOF, parser.currentToken.type)
        return prepare(syntax, caller, positions)
    }

    fun prepare(syntax: P.FunctionCallContext, caller: Function, positions: Set<Int>): CompilerCallPreparationResult.Ready {
        val environment = CompilerSourceIndex(caller).prepareDeclarationEnvironment(syntax)
        return CompilerCallPreparationResult.Ready(environment, bind(syntax, environment.binding, positions))
    }

    fun bind(syntax: P.FunctionCallContext, environment: BindingEnvironment, positions: Set<Int>): SourceCall {
        val binder = SnapshotSourceCodeBinder()
        val source = span(syntax, environment)
        val readonly = syntax.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty()
            .map { binder.expression(it, environment) }
        val normal = syntax.arguments().normalArgs()
        val nodes: List<ParserRuleContext> = normal.callArgumentList()?.callArgument()?.map { it }
            ?: normal.expressionList()?.expression().orEmpty()
        require(nodes.none { it is P.CallArgumentContext && it.REF() != null }) { "Internal fixtures use explicit position indices, never ref syntax" }
        val candidates = environment.functions(syntax.namespaceID().text, source).filter {
            it.function.parameters().size == nodes.size && it.readonlyParameters.size == readonly.size
        }
        var locationFailure: SourceBindingFailure? = null
        val calls = candidates.mapNotNull { target ->
            try {
                val arguments = nodes.mapIndexed { index, node ->
                    val expression = when (node) { is P.CallArgumentContext -> node.expression(); is P.ExpressionContext -> node; else -> null }
                    val parameter = target.function.parameters()[index]
                    when {
                        index in positions -> {
                            require(target.function is ImmutableFunctionRef<*> && (target.function as ImmutableFunctionRef<*>).kind == FunctionKind.MACRO)
                            require(parameter.mode() == ParamMode.REF)
                            val location = binder.location(requireNotNull(expression), environment)
                            if (!location.writable || !parameter.type().sameType(location.type))
                                throw SourceBindingFailure(span(node, environment), "REF requires a writable location with the exact parameter type")
                            BoundCallArgument.Reference(location, span(node, environment))
                        }
                        parameter.mode() == ParamMode.INLINE -> {
                            val lambda = (node as? P.CallArgumentContext)?.inlineLambda() ?: expression?.primary()?.inlineLambda()
                                ?: throw SourceBindingFailure(span(node, environment), "Internal callback fixture requires a direct lambda")
                            val signature = parameter.inlineSignature() ?: parameter.type().inlineSignature()!!
                            BoundCallArgument.Callback(binder.callback(lambda, signature, environment), span(node, environment))
                        }
                        else -> {
                            require(parameter.mode() != ParamMode.REF) { "An internal REF capability must be supplied explicitly" }
                            val value = binder.expression(requireNotNull(expression), environment)
                            require(environment.types.conversionRank(value.type, parameter.type()) != null)
                            if (parameter.mode() == ParamMode.CONST) require(value.constant != null)
                            BoundCallArgument.Value(value, parameter.mode())
                        }
                    }
                }
                val effects = readonly.map { it.effect } + arguments.flatMap { argument -> when (argument) {
                    is BoundCallArgument.Reference -> argument.location.effectOrder
                    is BoundCallArgument.Value -> listOf(argument.expression.effect)
                    is BoundCallArgument.Callback -> emptyList()
                } } + target.effect
                SourceCall(syntax.namespaceID().text, target, readonly, arguments, source,
                    readonly.map(SourceEvaluationArgument::Readonly) + arguments.map(SourceEvaluationArgument::Normal),
                    effects, effects.maxByOrNull { it.ordinal } ?: BoundEffect.PURE)
            } catch (failure: SourceBindingFailure) { locationFailure = failure; null
            } catch (_: IllegalArgumentException) { null }
        }
        if (calls.isEmpty()) locationFailure?.let { throw it }
        if (calls.size != 1) throw SourceBindingFailure(source, "Internal compiler position arguments do not select one exact writable signature")
        return calls.single()
    }

    fun expand(syntax: P.FunctionCallContext, caller: Function, positions: Set<Int>): Var<*> {
        val prepared = prepare(syntax, caller, positions)
        val backend = CompilerCommitBackend()
        ExpansionSession(ExpansionSite(prepared.call.source, "internal-reference-fixture"), Project.macroExpansionBudget(),
            CompilerSourceBindings.expansionEnvironment(prepared.environment, namespace = caller.namespace),
            CompilerTransaction(backend)).use { session ->
            val bindings = CompilerSourceBindings.capture(prepared.environment, session, caller)
            backend.attach(bindings)
            val environment = SourceProjectionEnvironment(bindings)
            val expansion = MacroExpander(environment).expandSource(prepared.call).validate()
            session.finalizeHelpers()
            val bridge = CompilerBridge(bindings)
            session.complete(expansion, bridge.prepare(environment.lower(expansion.code)))
            return bridge.result() ?: Void
        }
    }

    private fun span(syntax: ParserRuleContext, environment: BindingEnvironment): SourceSpan {
        val first = syntax.start
        val last = syntax.stop ?: first
        val lines = last.text.orEmpty().split('\n')
        return SourceSpan(environment.sourceFile, first.startIndex, last.stopIndex + 1,
            first.line, first.charPositionInLine + 1, last.line + lines.size - 1,
            if (lines.size == 1) last.charPositionInLine + lines.single().length + 1 else lines.last().length + 1)
    }
}
