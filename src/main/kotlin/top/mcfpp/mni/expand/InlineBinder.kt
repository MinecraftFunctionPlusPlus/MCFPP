package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import top.mcfpp.type.MCFPPBaseType

/**
 * Converts one already-decided source callback into an arena-owned inline template.
 *
 * The source body is never bound before its actual CONST facts are known: callers either pass a
 * [SourceCallbackBodyRequest] plus the facts supplied by the actual INLINE insertion, or an
 * already bound [BoundCallback]. Every parameter and every local declared by the callback receives
 * a fresh arena identity. A genuinely free capture is resolved to the caller's runtime reference,
 * so a write through that capture remains visible at the original caller position. A nested
 * callback request is rebound from the same lexical snapshot only when its enclosing body is bound;
 * it never reuses the source environment as a runtime location.
 *
 * This class deliberately applies no extra hard depth limit. Recursive identities are rejected at
 * insertion time by [InlineExpander], and all allocations share the expansion's unified budget.
 */
internal class InlineBinder(
    private val session: ExpansionSession,
    private val scope: ScopeId,
    private val freeCaptures: Map<SymbolId, Ref<*>> = emptyMap(),
    private val executionContextId: ExecutionContextId? = null,
    private val declarationEnvironment: BindingEnvironment? = null,
    private val declarations: Map<FunctionKey, BindingEnvironment> = emptyMap()
) {
    private val arena get() = session.arena

    /** Bind a deferred source request exactly when the actual CONST facts are known. */
    fun bind(request: SourceCallbackBodyRequest, constants: Map<Int, TypedConstant> = emptyMap()): ImmutableInlineFn<*> = bind(request.bind(constants), request.environment, request.declarations)

    /** Bind an already materialized source template with this binder's declaration snapshot. */
    fun bind(callback: BoundCallback): ImmutableInlineFn<*> = bind(callback, declarationEnvironment, declarations)

    private fun bind(
        callback: BoundCallback,
        environment: BindingEnvironment?,
        declarations: Map<FunctionKey, BindingEnvironment>
    ): ImmutableInlineFn<*> = State(callback, environment, declarations).build()

    private sealed interface SymbolBinding {
        data class Reference(val ref: Ref<*>) : SymbolBinding
        data class Value(val expression: Expr<*>) : SymbolBinding
    }

    private inner class State(
        private val callback: BoundCallback,
        private val environment: BindingEnvironment?,
        private val declarations: Map<FunctionKey, BindingEnvironment>
    ) {
        private val bodyScope = arena.childScope(scope)
        private val bodyMetadata = metadata(bodyScope, callback.body.source)
        private val bindings = LinkedHashMap<SymbolId, SymbolBinding>()
        private val captures = LinkedHashSet<SymbolId>()
        private val captureReferences = LinkedHashMap<SymbolId, Ref<*>>()
        private val ownedRoots = LinkedHashSet<SymbolId>()
        private val loops = LinkedHashMap<RegionId, RegionId>()
        private val stack = ArrayDeque<MutableList<Stmt>>()
        private val requests = LinkedHashMap<SourceCallbackSyntax, SourceCallbackBodyRequest>()
        private val returnRegion = arena.freshRegion()

        init {
            freeCaptures.forEach { (symbol, reference) -> bindings[symbol] = SymbolBinding.Reference(reference) }
            callback.nestedCallbacks.forEach { request -> requests[request.syntax] = request }
        }

        fun build(): ImmutableInlineFn<*> {
            val parameters = callback.parameters.map { parameter ->
                when (parameter.mode) {
                    // A descriptor may carry a stale declaration/previous-insertion fact for a
                    // CONST formal. The actual fact belongs to the InlineCall that inserts this
                    // callback, so keep the body's parameter read symbolic and let InlineExpander
                    // substitute the insertion argument. Literalizing here would silently pin the
                    // prior fact (for example descriptor 7 instead of actual 11).
                    ParamMode.CONST -> createLocal(parameter.symbol, bodyMetadata).symbol
                    ParamMode.VALUE, ParamMode.REF -> createLocal(parameter.symbol, bodyMetadata).symbol
                    ParamMode.INLINE -> fail(callback.source, "A callback cannot accept another inline callback")
                }
            }
            callback.readonlyParameters.forEach { symbol ->
                val value = symbol.constant
                    ?: throw SourceBindingFailure(
                        callback.source,
                        "Inline readonly formal '${symbol.name}' has no closed value"
                    )
                bindings[symbol.symbol] = SymbolBinding.Value(
                    arena.expr(bodyMetadata, symbol.type, ExprNode.Literal(value))
                )
            }
            val statements = ArrayList<Stmt>()
            stack.addLast(statements)
            try {
                callback.body.statements.forEach { statements += statement(it, bodyScope) }
            } finally {
                stack.removeLast()
            }
            val block = arena.block(bodyMetadata, statements)
            // The descriptor value belongs to the call site; only its body/target is nested.
            // Declaring it in the internal body scope would make a direct InlineCall at the
            // caller scope fail the arena's strict lexical visibility check.
            return arena.inline<Any?>(
                metadata(scope, callback.source),
                callback.signature,
                callback.identity,
                InlineTarget.Body(parameters, block, captures, returnRegion, captureReferences)
            )
        }

        private fun metadata(scope: ScopeId, source: SourceSpan): CodeMetadata = arena.metadata(
            scope,
            Origin(session.origin.callSite, source, session.origin.expansionFrames),
            executionContextId
        )

        private fun emit(statement: Stmt) {
            val current = stack.lastOrNull()
                ?: throw SourceBindingFailure(statement.source(), "Inline body statement has no owning block")
            current += statement
        }

        private fun createLocal(symbol: SourceSymbol, metadata: CodeMetadata): ImmutableLocal<*> {
            val local = arena.local(metadata, symbol.type, symbol.name, arena.freshSymbol())
            ownedRoots.add(local.symbol)
            bindings[symbol.symbol] = SymbolBinding.Reference(local)
            return local
        }

        private fun fail(source: SourceSpan, message: String): Nothing = throw SourceBindingFailure(source, message)

        private fun block(value: BoundSourceBlock, parent: ScopeId): ImmutableBlock {
            val scope = arena.childScope(parent)
            val metadata = metadata(scope, value.source)
            val statements = ArrayList<Stmt>()
            stack.addLast(statements)
            try {
                value.statements.forEach { statements += statement(it, scope) }
            } finally {
                stack.removeLast()
            }
            return arena.block(metadata, statements)
        }

        private fun statement(value: BoundSourceStatement, scope: ScopeId): Stmt {
            val metadata = metadata(scope, value.source)
            return when (value) {
                is BoundSourceStatement.Local -> {
                    val initial = value.initialValue?.let { expression(it, scope) }
                        ?: fail(value.source, "Inline local '${value.symbol.name}' requires an initializer")
                    val checked = convert(initial, value.symbol.type, value.source)
                    val local = arena.local(metadata, value.symbol.type, value.symbol.name, arena.freshSymbol())
                    ownedRoots.add(local.symbol)
                    bindings[value.symbol.symbol] = SymbolBinding.Reference(local)
                    arena.stmt(metadata, StmtNode.LocalDecl(local, checked))
                }
                is BoundSourceStatement.Assign -> {
                    val destination = reference(value.location, scope)
                    val assigned = value.operator?.let { operator ->
                        arena.expr(
                            metadata,
                            destination.type(),
                            ExprNode.Binary(
                                operator,
                                destination.load(),
                                expression(value.value, scope)
                            )
                        )
                    } ?: expression(value.value, scope)
                    arena.stmt(
                        metadata,
                        StmtNode.Assign(
                            destination,
                            convert(assigned, destination.type(), value.source)
                        )
                    )
                }
                is BoundSourceStatement.Evaluate ->
                    arena.stmt(metadata, StmtNode.Eval(expression(value.expression, scope)))
                is BoundSourceStatement.Conditional -> {
                    val condition = boolean(value.condition, scope)
                    val thenBlock = block(value.whenTrue, scope)
                    val elseBlock = value.whenFalse?.let { block(it, scope) }
                    arena.stmt(metadata, StmtNode.If(condition, thenBlock, elseBlock))
                }
                is BoundSourceStatement.Loop -> loop(value, scope)
                is BoundSourceStatement.Return -> {
                    val result = value.value?.let {
                        convert(expression(it, scope), callback.signature.returnType(), it.source)
                    }
                    arena.stmt(metadata, StmtNode.CallbackReturn(returnRegion, result))
                }
                is BoundSourceStatement.Break -> arena.stmt(
                    metadata,
                    StmtNode.Break(loops[value.region] ?: fail(value.source, "Break has no inline loop region"))
                )
                is BoundSourceStatement.Continue -> arena.stmt(
                    metadata,
                    StmtNode.Continue(loops[value.region] ?: fail(value.source, "Continue has no inline loop region"))
                )
            }
        }

        private fun loop(value: BoundSourceStatement.Loop, scope: ScopeId): Stmt {
            val region = arena.freshRegion()
            loops[value.region] = region
            val metadata = metadata(scope, value.source)
            val condition = boolean(value.condition, scope)
            val body = block(value.body, scope)
            if (!value.testAfter) return arena.stmt(metadata, StmtNode.While(condition, body, region))
            // The internal model has only a pre-test loop. A do/while body keeps its own source
            // region for break/continue while one explicit flag performs the first iteration.
            val catalog = environment?.types ?: fail(
                value.source,
                "Inline do/while requires the original type catalog"
            )
            val bool = catalog.ref(MCFPPBaseType.Bool)
            val first = arena.local(metadata, bool, "inlineDoWhileFirst", arena.freshSymbol())
            emit(
                arena.stmt(
                    metadata,
                    StmtNode.LocalDecl(
                        first,
                        arena.expr(metadata, bool, ExprNode.Literal(ConstantValue.Bool(true)))
                    )
                )
            )
            val combined = arena.expr(
                metadata,
                bool,
                ExprNode.Binary(
                    BinaryOperator.OR,
                    first.load(),
                    condition
                )
            )
            val bodyStatements = arrayListOf<Stmt>(
                arena.stmt(
                    (body as OwnedCode).metadata,
                    StmtNode.Assign(
                        first,
                        arena.expr(
                            (body as OwnedCode).metadata,
                            bool,
                            ExprNode.Literal(ConstantValue.Bool(false))
                        )
                    )
                )
            )
            bodyStatements += body.statements()
            val firstBody = arena.block((body as OwnedCode).metadata, bodyStatements)
            return arena.stmt(metadata, StmtNode.While(combined, firstBody, region))
        }

        private fun boolean(value: BoundExpr, scope: ScopeId): Expr<*> {
            val expression = expression(value, scope)
            if (!expression.type().sameType(boolType(value.source))) fail(value.source, "Inline condition requires bool")
            return expression
        }

        private fun boolType(source: SourceSpan): TypeRef<*> = environment?.types?.ref(MCFPPBaseType.Bool) ?: fail(source, "Inline bool requires the original type catalog")

        private fun convert(value: Expr<*>, target: TypeRef<*>, source: SourceSpan): Expr<*> {
            if (value.type().sameType(target)) return value
            val catalog = environment?.types ?: fail(source, "Inline conversion requires the original type catalog")
            if (catalog.conversionRank(value.type(), target) == null)
                fail(source, "Inline value does not match its destination type")
            val valueScope = (value as OwnedCode).metadata.scopeId
            return arena.expr(
                metadata(valueScope, source),
                target,
                ExprNode.Convert(value, target, ConversionKind.IMPLICIT)
            )
        }

        private fun expression(value: BoundExpr, scope: ScopeId): Expr<*> = when (val node = value.node) {
            is BoundExprNode.Literal -> arena.expr(metadata(scope, value.source), value.type, ExprNode.Literal(node.value))
            is BoundExprNode.Read -> read(node.location, scope)
            is BoundExprNode.Unary -> arena.expr(
                metadata(scope, value.source),
                value.type,
                ExprNode.Unary(node.operator, expression(node.operand, scope))
            )
            is BoundExprNode.Binary -> arena.expr(
                metadata(scope, value.source),
                value.type,
                ExprNode.Binary(node.operator, expression(node.left, scope), expression(node.right, scope))
            )
            is BoundExprNode.Convert -> arena.expr(
                metadata(scope, value.source),
                value.type,
                ExprNode.Convert(expression(node.value, scope), node.target, ConversionKind.IMPLICIT)
            )
            is BoundExprNode.Field -> {
                val receiver = expression(node.receiver, scope)
                val field = field(node.field, receiver.type(), value.source)
                arena.expr(metadata(scope, value.source), value.type, ExprNode.Field(receiver, field))
            }
            is BoundExprNode.Index -> {
                val receiver = expression(node.receiver, scope)
                val index = expression(node.index, scope)
                arena.expr(metadata(scope, value.source), value.type, ExprNode.Index(receiver, index))
            }
            is BoundExprNode.Call -> call(node.sourceCall, scope)
            is BoundExprNode.Unsupported -> fail(value.source, "Unsupported inline source expression")
        }

        private fun read(location: BoundLocation, scope: ScopeId): Expr<*> {
            val binding = binding(location.root, location.source, scope)
            return when (binding) {
                is SymbolBinding.Reference -> reference(location, scope).load()
                is SymbolBinding.Value -> path(binding.expression, location, scope)
            }
        }

        private fun path(base: Expr<*>, location: BoundLocation, scope: ScopeId): Expr<*> {
            var result = base
            var actual = base.type()
            location.path.forEach { step ->
                result = when (step) {
                    is BoundLocationStep.Field -> {
                        val field = field(step.declaration, actual, location.source)
                        actual = field.type()
                        arena.expr(metadata(scope, location.source), actual, ExprNode.Field(result, field))
                    }
                    is BoundLocationStep.Index -> {
                        val (element, _) = index(actual, location.source)
                        val index = expression(step.expression, scope)
                        actual = element
                        arena.expr(metadata(scope, location.source), actual, ExprNode.Index(result, index))
                    }
                }
            }
            return result
        }

        private fun reference(location: BoundLocation, scope: ScopeId): Ref<*> {
            val binding = binding(location.root, location.source, scope)
            if (binding !is SymbolBinding.Reference)
                fail(location.source, "Compile-time inline value '${location.root.name}' is not a writable location")
            // The template names the capture root and its body-relative path only. The actual
            // reference capability (base path, context, read-only flag and captured address) is
            // supplied again at each insertion, so it must not be baked into the body twice.
            val position = referenceLocation(binding.ref)
            var actual = binding.ref.type()
            var writable = position.writable
            val steps = ArrayList<LocationStep>()
            location.path.forEach { step ->
                when (step) {
                    is BoundLocationStep.Field -> {
                        val field = field(step.declaration, actual, location.source)
                        actual = field.type()
                        writable = writable && field.writable()
                        steps += LocationStep.Field(field)
                    }
                    is BoundLocationStep.Index -> {
                        val (element, indexWritable) = index(actual, location.source)
                        val index = step.constantIndex?.let { LocationStep.Index(it) } ?: run {
                            val value = expression(step.expression, scope)
                            val local = arena.local(metadata(scope, step.expression.source), value.type(), "inlineIndex")
                            ownedRoots.add(local.symbol)
                            emit(arena.stmt(metadata(scope, step.expression.source), StmtNode.LocalDecl(local, value)))
                            LocationStep.CapturedIndex(local.symbol)
                        }
                        actual = element
                        writable = writable && indexWritable
                        steps += index
                    }
                }
            }
            return arena.ref(
                metadata(scope, location.source),
                location.type,
                NodeLocation(
                    position.root,
                    steps,
                    position.executionContextId,
                    writable && location.writable
                )
            )
        }

        private fun referenceLocation(reference: Ref<*>): NodeLocation = when (reference) {
            is ImmutableRef<*> -> reference.location
            is ImmutableLocal<*> -> reference.location
            else -> throw SourceBindingFailure(
                reference.source(),
                "An inline score reference cannot be represented as a symbol location"
            )
        }

        /**
         * Record one capability actually used by this body. The root key and reference value must
         * stay identical so [MacroExpander] can alias exactly these symbols into a nested session
         * and replay the original path, context and readonly flag on export.
         */
        private fun registerCapture(symbol: SourceSymbol, reference: Ref<*>, source: SourceSpan) {
            if (!reference.type().sameType(symbol.type))
                fail(source, "Inline capture '${symbol.name}' has another runtime type")
            val root = referenceLocation(reference).root
            captures.add(root)
            captureReferences.putIfAbsent(root, reference)
        }

        private fun binding(symbol: SourceSymbol, source: SourceSpan, scope: ScopeId): SymbolBinding {
            // Free captures are pre-bound so a nested callback can forward them even when this
            // body never reads them. Direct reads still have to record the actual capability:
            // cross-session transport only aliases symbols present in captureReferences.
            bindings[symbol.symbol]?.let { existing ->
                val free = freeCaptures[symbol.symbol]
                if (free != null && existing is SymbolBinding.Reference)
                    registerCapture(symbol, existing.ref, source)
                return existing
            }
            val reference = freeCaptures[symbol.symbol]
            if (reference != null) {
                registerCapture(symbol, reference, source)
                return SymbolBinding.Reference(reference).also { bindings[symbol.symbol] = it }
            }
            val constant = symbol.constant
            if (constant != null) {
                val value = arena.expr(metadata(scope, source), symbol.type, ExprNode.Literal(constant))
                return SymbolBinding.Value(value).also { bindings[symbol.symbol] = it }
            }
            fail(source, "Inline capture '${symbol.name}' has no runtime location in the calling scope")
        }

        private fun field(declaration: SourceField, receiver: TypeRef<*>, source: SourceSpan): FieldRef<*> {
            val view = environment?.field(receiver, declaration.name)
            val canonical = declaration.canonical ?: view?.canonical
                ?: fail(source, "Inline field '${declaration.name}' is outside the canonical declaration environment")
            if (view != null && !view.type.sameType(declaration.type))
                fail(source, "Inline field '${declaration.name}' changed its declaration type")
            return canonical
        }

        private fun index(type: TypeRef<*>, source: SourceSpan): Pair<TypeRef<*>, Boolean> {
            val catalog = environment?.types ?: fail(source, "Inline index requires the original type catalog")
            val schema = catalog.indexSchema(type) ?: fail(source, "Inline receiver has no frozen index schema")
            return catalog.graph.type(schema.element) to schema.writable
        }

        private fun call(call: SourceCall, scope: ScopeId): Expr<*> {
            val function = call.target.function as? ImmutableFunctionRef<*>
                ?: fail(call.source, "Inline call target is not a canonical function snapshot")
            val metadata = metadata(scope, call.source)
            val parameters = function.parameters()
            if (call.arguments.size > parameters.size)
                fail(call.source, "Inline call has more arguments than its signature")
            val readonly = if (function.readonlyParameters().isEmpty()) emptyList() else {
                val types = environment?.types ?: fail(
                    call.source,
                    "Inline readonly call requires the original type catalog"
                )
                SourceDefaultBinder().completeReadonly(call.target, call.readonlyArguments, types, call.source)
                    .mapIndexed { index, expression ->
                        typedConstant(expression, function.readonlyParameters()[index].type(), scope)
                    }
            }
            val receiver = call.receiver?.let { reference(it, scope) }
            if (receiver != null && function.kind == FunctionKind.MACRO)
                fail(call.source, "A macro callback cannot be selected as a member call")
            val arguments = parameters.mapIndexed { index, parameter ->
                val argument = call.arguments.getOrNull(index)
                    ?: fail(call.source, "Inline call argument $index has no bound default template")
                when (argument) {
                    is BoundCallArgument.Value -> {
                        if (parameter.mode() !in setOf(ParamMode.VALUE, ParamMode.CONST))
                            fail(argument.source, "Inline call argument mode differs from its signature")
                        if (parameter.mode() == ParamMode.CONST) constantArgument(argument.expression, parameter.type(), scope)
                        else valueArgument(argument.expression, parameter.type(), index, scope, argument.source)
                    }
                    is BoundCallArgument.Reference -> {
                        if (parameter.mode() != ParamMode.REF)
                            fail(argument.source, "Inline call argument mode differs from its signature")
                        val value = reference(argument.location, scope)
                        if (!value.type().sameType(parameter.type()))
                            fail(argument.source, "Inline REF argument has another type")
                        CallArgument.ref(value)
                    }
                    is BoundCallArgument.Callback -> {
                        if (parameter.mode() != ParamMode.INLINE)
                            fail(argument.source, "Inline call argument mode differs from its signature")
                        CallArgument.inline(inlineCallback(argument.callback, argument.source, scope))
                    }
                }
            }
            val node = if (function.kind == FunctionKind.MACRO) ExprNode.MacroCall(function, arguments)
            else ExprNode.Call(function, arguments, readonly, receiver)
            return arena.expr(metadata, function.returnType(), node)
        }

        private fun inlineCallback(
            syntax: SourceCallbackSyntax,
            source: SourceSpan,
            scope: ScopeId
        ): ImmutableInlineFn<*> {
            val request = requests[syntax]
                ?: callback.nestedCallbacks.firstOrNull { it.syntax.source == syntax.source }
                ?: fail(source, "Nested inline callback has no request in its lexical snapshot")
            // CONST formals are supplied by the actual InlineCall, which may live in another macro
            // host. Keep this nested body deferred and only record the capabilities it will need
            // from the enclosing insertion. Capture discovery walks the original syntax only, so no
            // statement is executed and no CONST-dependent expression is folded under a guess.
            val refs = LinkedHashMap<SymbolId, Ref<*>>()
            sourceCaptureManifest(request).forEach { (symbolId, symbol) ->
                when (val binding = bindings[symbolId]) {
                    is SymbolBinding.Reference -> {
                        if (!binding.ref.type().sameType(symbol.type))
                            fail(source, "Nested inline capture '${symbol.name}' has another runtime type")
                        val reference = if (symbol.global) binding.ref else {
                            val contract = request.environment.types.facts(symbol.type).captureContract
                            if (contract == top.mcfpp.core.lang.LambdaCopyContract.NOT_COPYABLE ||
                                contract == top.mcfpp.core.lang.LambdaCopyContract.REFERENCE_DEFERRED)
                                fail(source, "Captured local '${symbol.name}' has no independent copy contract")
                            val local = arena.local(metadata(scope, source), symbol.type, "capture_${symbol.name}")
                            ownedRoots.add(local.symbol)
                            emit(arena.stmt(metadata(scope, source), StmtNode.LocalDecl(local, binding.ref.load())))
                            local
                        }
                        refs[symbolId] = reference
                        val root = referenceLocation(binding.ref).root
                        if (root !in ownedRoots) {
                            captures.add(root)
                            captureReferences.putIfAbsent(root, binding.ref)
                        }
                    }
                    is SymbolBinding.Value -> Unit
                    null -> if (symbol.constant == null)
                        fail(source, "Nested inline callback capture '${symbol.name}' has no runtime location")
                }
            }
            return deferredInline(session, scope, metadata(scope, source), request, refs)
        }

        private fun valueArgument(
            value: BoundExpr,
            type: TypeRef<*>,
            index: Int,
            scope: ScopeId,
            source: SourceSpan
        ): CallArgument {
            val expression = convert(expression(value, scope), type, source)
            val local = arena.local(metadata(scope, source), type, "inlineArgument$index")
            emit(arena.stmt(metadata(scope, source), StmtNode.LocalDecl(local, expression)))
            return CallArgument.value(local.load())
        }

        private fun constantArgument(value: BoundExpr, type: TypeRef<*>, scope: ScopeId): CallArgument {
            val constant = typedConstant(value, type, scope)
            val literal = arena.expr(metadata(scope, constant.source), type, ExprNode.Literal(constant.value))
            return CallArgument.constant(arena.constant(literal, constant.value))
        }

        private fun typedConstant(value: BoundExpr, type: TypeRef<*>, scope: ScopeId): TypedConstant {
            val catalog = environment?.types ?: fail(
                value.source,
                "Inline CONST argument requires the original type catalog"
            )
            val constant = value.constant
                ?: fail(value.source, "Inline CONST argument is not a closed value")
            if (value.effect > BoundEffect.READ)
                fail(value.source, "Inline CONST argument has executable effects")
            val converted = SourceConstantFacts.convert(constant, value.type, type, catalog)
                ?: fail(value.source, "Inline CONST argument has no closed destination representation")
            SourceConstantFacts.validateClosed(type, converted, catalog, value.source)
            return TypedConstant(type, converted, value.source)
        }
    }
}

/** A source-body request carried inside an [InlineTarget.Body] until actual insertion facts exist. */
internal class DeferredSourceCallback(val request: SourceCallbackBodyRequest) : DeferredCallbackBody {
    override val signature: InlineSignature<*> get() = request.syntax.signature
    override val source: SourceSpan get() = request.syntax.functionTarget?.sourceBody?.source ?: request.syntax.source
}

/**
 * Build one genuinely opaque inline descriptor. No source statement is bound here: the actual
 * body is produced only when an `InlineCall` supplies that insertion's CONST facts. The descriptor
 * carries the capture capabilities needed from the enclosing scope, a private signature-shaped
 * parameter list and an empty body placeholder for copy/import/print only. Final validation still
 * requires every deferred descriptor to be consumed by an insertion.
 */
internal fun deferredInline(
    session: ExpansionSession,
    scope: ScopeId,
    metadata: CodeMetadata,
    request: SourceCallbackBodyRequest,
    freeCaptures: Map<SymbolId, Ref<*>>
): ImmutableInlineFn<*> {
    val arena = session.arena
    val bodyScope = arena.childScope(scope)
    val bodyMetadata = arena.metadata(bodyScope, metadata.origin, metadata.executionContextId)
    val parameters = request.syntax.signature.parameters().map { arena.freshSymbol() }
    val block = arena.block(bodyMetadata, emptyList())
    val captureReferences = LinkedHashMap<SymbolId, Ref<*>>()
    freeCaptures.forEach { (_, reference) ->
        val root = when (reference) {
            is ImmutableRef<*> -> reference.location.root
            is ImmutableLocal<*> -> reference.location.root
            else -> throw SourceBindingFailure(
                reference.source(),
                "An inline capture capability must be an addressable local or reference"
            )
        }
        captureReferences.putIfAbsent(root, reference)
    }
    return arena.inline<Any?>(
        metadata,
        request.syntax.signature,
        request.identity,
        InlineTarget.Body(
            parameters,
            block,
            captureReferences.keys,
            arena.freshRegion(),
            captureReferences,
            DeferredSourceCallback(request),
            freeCaptures
        )
    )
}
