package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPPrivateType
import java.util.function.Consumer
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/**
 * C_INLINE: source callbacks are bound to an arena template once, then every insertion clones its
 * locals, captures and return region. These tests use the real expansion session, code arena,
 * validator and tree expander; they never execute a compiler backend.
 */
class MniInlineCallbackTest {
    private val catalog = SourceTypeCatalog.snapshot(
        listOf(
            MCFPPBaseType.Int,
            MCFPPBaseType.Bool,
            MCFPPBaseType.Float,
            MCFPPBaseType.String,
            MCFPPPrivateType.Void,
            MCFPPListType(MCFPPBaseType.Int)
        ),
        callbacks = listOf(
            callbackShape(listOf(ParamMode.VALUE), MCFPPBaseType.Int),
            callbackShape(listOf(ParamMode.CONST), MCFPPBaseType.Int),
            callbackShape(emptyList(), MCFPPBaseType.Int)
        )
    )
    private val intType get() = catalog.ref(MCFPPBaseType.Int)
    private val intListType get() = catalog.ref(MCFPPListType(MCFPPBaseType.Int))
    private val voidType get() = catalog.ref(MCFPPPrivateType.Void)
    private val sourceSession = SessionId(700)

    private fun callbackShape(modes: List<ParamMode>, result: top.mcfpp.type.MCFPPType) = SourceCallbackSignature(
        modes.map { SourceCallbackParameter(SourceCallbackType.Declared(MCFPPBaseType.Int.typeId), it) },
        SourceCallbackType.Declared(result.typeId)
    )

    private class Types(private val catalog: SourceTypeCatalog) : TypeService {
        private fun <T> ref(type: top.mcfpp.type.MCFPPType) = catalog.ref(type) as TypeRef<T>
        override fun intType(): TypeRef<IntT> = ref(MCFPPBaseType.Int)
        override fun boolType(): TypeRef<BoolT> = ref(MCFPPBaseType.Bool)
        override fun floatType(): TypeRef<FloatT> = ref(MCFPPBaseType.Float)
        override fun stringType(): TypeRef<StringT> = ref(MCFPPBaseType.String)
        override fun voidType(): TypeRef<VoidT> = ref(MCFPPPrivateType.Void)
        override fun lookup(name: String): TypeRef<*> = when (name) {
            "int" -> ref<Any>(MCFPPBaseType.Int)
            "bool" -> ref<Any>(MCFPPBaseType.Bool)
            "float" -> ref<Any>(MCFPPBaseType.Float)
            "string" -> ref<Any>(MCFPPBaseType.String)
            "void" -> ref<Any>(MCFPPPrivateType.Void)
            else -> error("Unknown inline test type: $name")
        }
        override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("Inline tests do not resolve functions through TypeService")
    }

    private object UnusedMacroEntry {
        @JvmStatic
        fun unused(context: MacroContext): Block = error("The inline test macro is never invoked")
    }

    object NestedInlineEntries {
        var innerRef: FunctionRef<*>? = null

        @JvmStatic
        fun forward(context: MacroContext, callback: InlineFn<IntT>): Expr<IntT> {
            val inner = requireNotNull(innerRef)
            @Suppress("UNCHECKED_CAST")
            val function = inner as FunctionRef<IntT>
            return context.quote().expression(
                context.types().intType(),
                Consumer { builder ->
                    builder.yield(builder.call(function, listOf(CallArgument.inline(callback))))
                }
            )
        }

        @JvmStatic
        fun inner(context: MacroContext, callback: InlineFn<IntT>): Expr<IntT> = context.quote().expression(
            context.types().intType(),
            Consumer { builder ->
                builder.yield(
                    builder.inline<IntT>(
                        callback,
                        listOf(InlineArgument.value(builder.intLiteral(7)))
                    )
                )
            }
        )
    }

    private fun newSession(functions: List<FunctionRef<*>> = emptyList()): ExpansionSession {
        val types = Types(catalog)
        val environment = ExpansionEnvironment(types, functions, configurationKey = "inline-test")
        val transaction = CompilerTransaction(
            CompilerTransactionBackend {
                object : PreparedCompilerCommit {
                    override fun apply() { }
                    override fun rollback() { }
                }
            }
        )
        return ExpansionSession(
            ExpansionSite(SPAN, "inline::test"),
            ExpansionBudget(maxNodes = 1_000_000),
            environment,
            transaction
        )
    }

    private fun expander(session: ExpansionSession): MacroExpander {
        val method = UnusedMacroEntry::class.java.getMethod("unused", MacroContext::class.java)
        val reference = ImmutableFunctionRef(
            FunctionKey(701, "inlineUnused"),
            "inlineUnused",
            FunctionSignature.of(emptyList(), voidType),
            FunctionKind.MACRO
        )
        val prepared = PreparedMacroSignature(
            "inlineUnused",
            method,
            emptyList(),
            PreparedMacroType.Source("void", SPAN),
            SPAN
        )
        val macro = MacroFunction("inline", "unused", method, reference, true, prepared)
        return MacroExpander(session, listOf(macro), "inline-test")
    }

    private fun nestedMacro(name: String, reference: ImmutableFunctionRef<*>): MacroFunction {
        val method = NestedInlineEntries::class.java.getMethod(
            name,
            MacroContext::class.java,
            InlineFn::class.java
        )
        val prepared = PreparedMacroSignature(
            name,
            method,
            emptyList(),
            PreparedMacroType.Source("int", SPAN),
            SPAN
        )
        return MacroFunction("inline", name, method, reference, false, prepared)
    }

    private fun symbol(
        name: String,
        id: Long,
        type: TypeRef<*> = intType,
        writable: Boolean = true,
        constant: ConstantValue? = null
    ) = SourceSymbol(name, type, SymbolId(sourceSession, id), writable, constant)

    private fun location(symbol: SourceSymbol) = BoundLocation(symbol, emptyList(), symbol.type, symbol.writable, null, SPAN)

    private fun read(symbol: SourceSymbol) = BoundExpr(BoundExprNode.Read(location(symbol)), symbol.type, symbol.constant, BoundEffect.READ, SPAN)

    private fun literal(value: Int) = BoundExpr(
        BoundExprNode.Literal(ConstantValue.Integral(value.toLong())),
        intType,
        ConstantValue.Integral(value.toLong()),
        BoundEffect.PURE,
        SPAN
    )

    private fun callback(
        signature: InlineSignature<*>,
        parameters: List<BoundCallbackParameter>,
        captures: Map<SymbolId, SourceSymbol>,
        body: BoundSourceBlock,
        returnRegion: RegionId = RegionId(sourceSession, 90)
    ): BoundCallback {
        val type = requireNotNull(catalog.inlineType(signature))
        return BoundCallback(
            CallbackId(702, "inline-test-callback"), type, signature, parameters,
            emptyList(), captures, body, returnRegion, SPAN, SPAN, "", null, emptyList()
        )
    }

    private fun block(
        statements: List<BoundSourceStatement>,
        scope: ScopeId = ScopeId(sourceSession, 1)
    ): BoundSourceBlock = BoundSourceBlock(scope, ScopeId(sourceSession, 0), statements, SPAN)

    private fun code(session: ExpansionSession): CodeMetadata {
        val arena = session.arena
        return arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
    }

    private fun inlineCall(
        session: ExpansionSession,
        callback: InlineFn<*>,
        arguments: List<InlineArgument>
    ): Expr<*> = session.arena.expr(code(session), intType, ExprNode.InlineCall(callback, arguments))

    private fun expand(session: ExpansionSession, expression: Code): Code = MacroTreeExpander(expander(session)).expand(session, expression)

    private fun locals(code: Code): List<ImmutableLocal<*>> {
        val result = arrayListOf<ImmutableLocal<*>>()
        fun visit(value: Code) {
            when (value) {
                is ImmutableStmt -> when (val node = value.node) {
                    is StmtNode.LocalDecl -> {
                        result += node.local as ImmutableLocal<*>
                        visit(node.initialValue)
                    }
                    is StmtNode.Assign -> {
                        visit(node.destination)
                        visit(node.expression)
                    }
                    is StmtNode.Eval -> visit(node.expression)
                    is StmtNode.If -> {
                        visit(node.condition)
                        visit(node.thenBlock)
                        node.elseBlock?.let(::visit)
                    }
                    is StmtNode.While -> {
                        visit(node.condition)
                        visit(node.body)
                    }
                    is StmtNode.ScopeBlock -> visit(node.block)
                    is StmtNode.Yield -> visit(node.value)
                    is StmtNode.CallbackReturn -> node.value?.let(::visit)
                    else -> Unit
                }
                is ImmutableExpr<*> -> when (val node = value.node) {
                    is ExprNode.Unary -> visit(node.operand)
                    is ExprNode.Binary -> {
                        visit(node.left)
                        visit(node.right)
                    }
                    is ExprNode.Convert -> visit(node.value)
                    is ExprNode.Field -> visit(node.receiver)
                    is ExprNode.Index -> {
                        visit(node.receiver)
                        visit(node.index)
                    }
                    is ExprNode.Call -> node.arguments.forEach { visit(it.payload.code) }
                    is ExprNode.MacroCall -> node.arguments.forEach { visit(it.payload.code) }
                    is ExprNode.ExprBlock -> visit(node.block)
                    is ExprNode.InlineCall -> {
                        visit(node.callback)
                        node.arguments.forEach { visit(it.payload.code) }
                    }
                    else -> Unit
                }
                is ImmutableBlock -> value.statements().forEach(::visit)
                else -> Unit
            }
        }
        visit(code)
        return result
    }

    private fun assignments(code: Code): List<StmtNode.Assign> {
        val result = arrayListOf<StmtNode.Assign>()
        fun visit(value: Code) {
            when (value) {
                is ImmutableStmt -> when (val node = value.node) {
                    is StmtNode.Assign -> {
                        result += node
                        visit(node.expression)
                    }
                    is StmtNode.LocalDecl -> visit(node.initialValue)
                    is StmtNode.Eval -> visit(node.expression)
                    is StmtNode.If -> {
                        visit(node.condition)
                        visit(node.thenBlock)
                        node.elseBlock?.let(::visit)
                    }
                    is StmtNode.While -> {
                        visit(node.condition)
                        visit(node.body)
                    }
                    is StmtNode.ScopeBlock -> visit(node.block)
                    is StmtNode.Yield -> visit(node.value)
                    is StmtNode.CallbackReturn -> node.value?.let(::visit)
                    else -> Unit
                }
                is ImmutableExpr<*> -> when (val node = value.node) {
                    is ExprNode.Unary -> visit(node.operand)
                    is ExprNode.Binary -> {
                        visit(node.left)
                        visit(node.right)
                    }
                    is ExprNode.Convert -> visit(node.value)
                    is ExprNode.Field -> visit(node.receiver)
                    is ExprNode.Index -> {
                        visit(node.receiver)
                        visit(node.index)
                    }
                    is ExprNode.Call -> node.arguments.forEach { visit(it.payload.code) }
                    is ExprNode.MacroCall -> node.arguments.forEach { visit(it.payload.code) }
                    is ExprNode.ExprBlock -> visit(node.block)
                    is ExprNode.InlineCall -> {
                        visit(node.callback)
                        node.arguments.forEach { visit(it.payload.code) }
                    }
                    else -> Unit
                }
                is ImmutableBlock -> value.statements().forEach(::visit)
                else -> Unit
            }
        }
        visit(code)
        return result
    }

    private fun readLocations(code: Code): List<NodeLocation> {
        val result = arrayListOf<NodeLocation>()
        fun visit(value: Code) {
            when (value) {
                is ImmutableExpr<*> -> when (val node = value.node) {
                    is ExprNode.Read -> result += node.location
                    is ExprNode.Unary -> visit(node.operand)
                    is ExprNode.Binary -> {
                        visit(node.left)
                        visit(node.right)
                    }
                    is ExprNode.Convert -> visit(node.value)
                    is ExprNode.Field -> visit(node.receiver)
                    is ExprNode.Index -> {
                        visit(node.receiver)
                        visit(node.index)
                    }
                    is ExprNode.Call -> node.arguments.forEach { visit(it.payload.code) }
                    is ExprNode.MacroCall -> node.arguments.forEach { visit(it.payload.code) }
                    is ExprNode.ExprBlock -> visit(node.block)
                    is ExprNode.InlineCall -> {
                        visit(node.callback)
                        node.arguments.forEach { visit(it.payload.code) }
                    }
                    else -> Unit
                }
                is ImmutableStmt -> when (val node = value.node) {
                    is StmtNode.LocalDecl -> visit(node.initialValue)
                    is StmtNode.Assign -> visit(node.expression)
                    is StmtNode.Eval -> visit(node.expression)
                    is StmtNode.If -> {
                        visit(node.condition)
                        visit(node.thenBlock)
                        node.elseBlock?.let { visit(it) }
                    }
                    is StmtNode.While -> {
                        visit(node.condition)
                        visit(node.body)
                    }
                    is StmtNode.ScopeBlock -> visit(node.block)
                    is StmtNode.Yield -> visit(node.value)
                    is StmtNode.CallbackReturn -> node.value?.let { visit(it) }
                    else -> Unit
                }
                is ImmutableBlock -> value.statements().forEach { visit(it) }
                else -> Unit
            }
        }
        visit(code)
        return result
    }

    private fun yields(code: Code): List<StmtNode.Yield> {
        val result = arrayListOf<StmtNode.Yield>()
        fun visit(value: Code) {
            when (value) {
                is ImmutableStmt -> when (val node = value.node) {
                    is StmtNode.Yield -> {
                        result += node
                        visit(node.value)
                    }
                    is StmtNode.Eval -> visit(node.expression)
                    is StmtNode.If -> {
                        visit(node.condition)
                        visit(node.thenBlock)
                        node.elseBlock?.let(::visit)
                    }
                    is StmtNode.ScopeBlock -> visit(node.block)
                    else -> Unit
                }
                is ImmutableExpr<*> -> when (val node = value.node) {
                    is ExprNode.ExprBlock -> visit(node.block)
                    else -> Unit
                }
                is ImmutableBlock -> value.statements().forEach(::visit)
                else -> Unit
            }
        }
        visit(code)
        return result
    }

    @Test
    fun repeatedInsertionUsesIndependentLocalsAndWritesTheCallerCapture() {
        newSession().use { session ->
            val arena = session.arena
            val metadata = code(session)
            val captured = symbol("captured", 10)
            val parameter = symbol("value", 11)
            val local = symbol("local", 12)
            val capturedRef = arena.ref(metadata, intType, NodeLocation(arena.freshSymbol(), writable = true))
            session.registerBinding(capturedRef)
            val body = block(
                listOf(
                    BoundSourceStatement.Local(local, read(parameter), false, SPAN),
                    BoundSourceStatement.Assign(location(captured), read(local), BinaryOperator.ADD, SPAN),
                    BoundSourceStatement.Return(RegionId(sourceSession, 90), read(captured), SPAN)
                )
            )
            val signature = FunctionSignature.of(
                listOf(ParameterView.of("value", intType, ParamMode.VALUE)),
                intType
            )
            val bound = InlineBinder(
                session,
                arena.rootScope,
                freeCaptures = mapOf(captured.symbol to capturedRef)
            )
                .bind(
                    callback(
                        signature,
                        listOf(BoundCallbackParameter(parameter, ParamMode.VALUE)),
                        mapOf(captured.symbol to captured),
                        body
                    )
                )
            val tree = MacroTreeExpander(expander(session))
            fun call(value: Int) = inlineCall(
                session,
                bound,
                listOf(
                    InlineArgument.value(
                        arena.expr(
                            metadata,
                            intType,
                            ExprNode.Literal(ConstantValue.Integral(value.toLong()))
                        )
                    )
                )
            )
            val sum = arena.expr(
                metadata,
                intType,
                ExprNode.Binary(BinaryOperator.ADD, call(1), call(2))
            )
            val expanded = tree.expand(session, sum)
            CodeValidator().validate(expanded, session.validationEnvironment(), ValidationPhase.FINAL)

            val bodyLocals = locals(expanded).filter { it.hint() == "local" }
            assertEquals(2, bodyLocals.size)
            assertEquals(2, bodyLocals.map { it.symbol }.distinct().size)
            val writes = assignments(expanded)
            assertEquals(2, writes.size)
            writes.forEach {
                assertSame(
                    capturedRef.location.root,
                    it.destination.let {
                        (it as ImmutableRef<*>).location.root
                    }
                )
            }
        }
    }

    @Test
    fun constantParameterIsSubstitutedWithoutCreatingARuntimeLocal() {
        newSession().use { session ->
            val arena = session.arena
            val metadata = code(session)
            val parameter = symbol("fixed", 20, writable = false, constant = ConstantValue.Integral(7))
            val body = block(
                listOf(
                    BoundSourceStatement.Return(RegionId(sourceSession, 91), read(parameter), SPAN)
                )
            )
            val signature = FunctionSignature.of(
                listOf(ParameterView.of("fixed", intType, ParamMode.CONST)),
                intType
            )
            val bound = InlineBinder(session, arena.rootScope)
                .bind(
                    callback(
                        signature,
                        listOf(BoundCallbackParameter(parameter, ParamMode.CONST)),
                        emptyMap(),
                        body
                    )
                )
            val expression = arena.expr(metadata, intType, ExprNode.Literal(ConstantValue.Integral(11)))
            val argument = arena.constant(expression, ConstantValue.Integral(11))
            val expanded = expand(session, inlineCall(session, bound, listOf(InlineArgument.constant(argument))))
            CodeValidator().validate(expanded, session.validationEnvironment(), ValidationPhase.FINAL)
            assertTrue(locals(expanded).isEmpty())
            val yielded = yields(expanded).single().value as ImmutableExpr<*>
            assertEquals(ExprNode.Literal(ConstantValue.Integral(11)), yielded.node)
        }
    }

    @Test
    fun recursiveStaticIdentityIsRejectedBeforeTheBodyCanRecurse() {
        newSession().use { session ->
            val arena = session.arena
            val metadata = code(session)
            val signature = FunctionSignature.of(emptyList(), intType)
            val empty = arena.block(metadata, emptyList())
            val first = arena.inline<Any?>(
                metadata,
                signature,
                CallbackId(703, "recursive"),
                InlineTarget.Body(emptyList(), empty, emptySet(), arena.freshRegion())
            )
            val recursiveBody = arena.block(
                metadata,
                listOf(
                    arena.stmt(
                        metadata,
                        StmtNode.Eval(
                            arena.expr(
                                metadata,
                                intType,
                                ExprNode.InlineCall(first, emptyList())
                            )
                        )
                    )
                )
            )
            val recursive = arena.inline<Any?>(
                metadata,
                signature,
                CallbackId(703, "recursive"),
                InlineTarget.Body(emptyList(), recursiveBody, emptySet(), arena.freshRegion())
            )
            val failure = assertFailsWith<ExpansionFailure> {
                expand(session, inlineCall(session, recursive, emptyList()))
            }
            assertTrue(failure.message!!.contains("Recursive inline callback"))
        }
    }

    @Test
    fun unboundStaticTargetAndDynamicFunctionValuesAreRejected() {
        newSession().use { session ->
            val arena = session.arena
            val metadata = code(session)
            val signature = FunctionSignature.of(emptyList(), intType)
            val declared = arena.inline<Any?>(
                metadata,
                signature,
                CallbackId(704, "declared"),
                InlineTarget.Declared(FunctionKey(705, "staticBody"))
            )
            val failure = assertFailsWith<ExpansionFailure> {
                expand(session, inlineCall(session, declared, emptyList()))
            }
            assertTrue(failure.message!!.contains("no bound source body"))
        }
    }

    @Test
    fun deferredSourceRequestIsBoundOnlyWithItsActualConstantFacts() {
        newSession().use { session ->
            val arena = session.arena
            val source = "func main(){ consume({ fixed -> return fixed; }); }"
            val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
            val tree = parser.compilationUnit()
            assertEquals(0, parser.numberOfSyntaxErrors)
            val lambda = collect(tree, P.InlineLambdaContext::class.java).first()
            val signature = FunctionSignature.of(
                listOf(ParameterView.of("fixed", intType, ParamMode.CONST)),
                intType
            )
            val environment = BindingEnvironment(
                catalog,
                emptyList(),
                emptyList(),
                sourceText = source,
                sourceFile = "inline.mcfpp"
            )
            val syntax = SnapshotSourceCodeBinder().callback(lambda, signature, environment)
            val request = SourceCallbackBodyRequest(syntax, environment, emptyMap())
            val bound = InlineBinder(
                session,
                arena.rootScope,
                declarationEnvironment = request.environment,
                declarations = request.declarations
            )
                .bind(
                    request,
                    mapOf(
                        0 to TypedConstant(
                            intType,
                            ConstantValue.Integral(13),
                            syntax.source
                        )
                    )
                )
            val metadata = arena.metadata(
                arena.rootScope,
                Origin(SPAN, syntax.source, session.origin.expansionFrames)
            )
            val literal = arena.expr(metadata, intType, ExprNode.Literal(ConstantValue.Integral(13)))
            val expanded = expand(
                session,
                arena.expr(
                    metadata,
                    intType,
                    ExprNode.InlineCall(
                        bound,
                        listOf(
                            InlineArgument.constant(
                                arena.constant(
                                    literal,
                                    ConstantValue.Integral(13)
                                )
                            )
                        )
                    )
                )
            )
            CodeValidator().validate(expanded, session.validationEnvironment(), ValidationPhase.FINAL)
            val yielded = yields(expanded).single().value as ImmutableExpr<*>
            assertEquals(ExprNode.Literal(ConstantValue.Integral(13)), yielded.node)
        }
    }

    @Test
    fun nestedMacroCallPreservesTheCallersCapturedPositionAndReadonlyFlag() {
        val callbackSignature = FunctionSignature.of(
            listOf(ParameterView.of("value", intType, ParamMode.VALUE)),
            intType
        )
        val callbackType = requireNotNull(catalog.inlineType(callbackSignature))
        val forwardRef = ImmutableFunctionRef(
            FunctionKey(991, "forward"),
            "forward",
            FunctionSignature.of(
                listOf(
                    ParameterView.of(
                        "callback",
                        callbackType,
                        ParamMode.INLINE,
                        callbackSignature
                    )
                ),
                intType
            ),
            FunctionKind.MACRO
        )
        val innerRef = ImmutableFunctionRef(
            FunctionKey(992, "inner"),
            "inner",
            FunctionSignature.of(
                listOf(
                    ParameterView.of(
                        "callback",
                        callbackType,
                        ParamMode.INLINE,
                        callbackSignature
                    )
                ),
                intType
            ),
            FunctionKind.MACRO
        )
        NestedInlineEntries.innerRef = innerRef
        val forward = nestedMacro("forward", forwardRef)
        val inner = nestedMacro("inner", innerRef)
        newSession(listOf(forwardRef, innerRef)).use { session ->
            val arena = session.arena
            val metadata = code(session)
            val captured = symbol("position", 50)
            val readonly = symbol("readonly", 53, writable = false)
            val parameter = symbol("value", 51)
            val local = symbol("local", 52)
            // The captured index is a derived capability: its external declaration is only valid
            // against the canonical list root that actually owns the Index(0) element schema.
            val listRoot = arena.freshSymbol()
            val listRootRef = arena.ref(metadata, intListType, NodeLocation(listRoot, writable = true))
            val capturedRef = arena.ref(
                metadata,
                intType,
                NodeLocation(listRoot, listOf(LocationStep.Index(0)), writable = true)
            )
            val readonlyRef = arena.ref(metadata, intType, NodeLocation(arena.freshSymbol(), writable = false))
            session.registerBinding(listRootRef)
            session.registerBinding(capturedRef)
            session.registerBinding(readonlyRef)
            val body = block(
                listOf(
                    BoundSourceStatement.Local(local, read(parameter), false, SPAN),
                    BoundSourceStatement.Evaluate(read(readonly), SPAN),
                    BoundSourceStatement.Assign(location(captured), read(local), BinaryOperator.ADD, SPAN),
                    BoundSourceStatement.Return(RegionId(sourceSession, 93), read(captured), SPAN)
                )
            )
            val bound = InlineBinder(
                session,
                arena.rootScope,
                freeCaptures = mapOf(captured.symbol to capturedRef, readonly.symbol to readonlyRef)
            )
                .bind(
                    callback(
                        callbackSignature,
                        listOf(BoundCallbackParameter(parameter, ParamMode.VALUE)),
                        mapOf(captured.symbol to captured, readonly.symbol to readonly),
                        body
                    )
                )
            val prelude = arena.block(metadata, emptyList())
            val result = MacroExpander(session, listOf(forward, inner), "inline-test")
                .expandRoot(BoundMacroCall(forward, SPAN, prelude, listOf(bound), emptyList(), arena.rootScope))
            val validated = result.validate()
            val write = assignments(validated.code).single()
            val destination = write.destination as ImmutableRef<*>
            assertEquals(capturedRef.location.root, destination.location.root)
            assertEquals(listOf(LocationStep.Index(0)), destination.location.steps)
            assertTrue(destination.location.writable)
            val readonlyRead = readLocations(validated.code).single { it.root == readonlyRef.location.root }
            assertFalse(readonlyRead.writable)
            assertTrue(intType.sameType(readonlyRef.type()))
        }
    }

    private fun <T> collect(node: ParseTree, type: Class<T>): List<T> {
        val result = arrayListOf<T>()
        fun visit(value: ParseTree) {
            if (type.isInstance(value)) result += type.cast(value)
            for (index in 0 until value.childCount) value.getChild(index)?.let(::visit)
        }
        visit(node)
        return result
    }

    private companion object {
        val SPAN = SourceSpan("inline-callback.mcfpp", 0, 12, 1, 1, 1, 13)
    }
}
