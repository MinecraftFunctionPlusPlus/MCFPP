package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

/**
 * Inserts one already-bound inline callback at its call position.
 *
 * Every insertion allocates fresh parameter/local symbols, control regions, captured addresses and
 * a fresh return region, so the same callback can be inserted repeatedly without sharing any local.
 * Genuinely free captures are not copied: they keep the caller's runtime symbol identity, so writes
 * through the callback remain visible in the caller. Nested inline callback descriptors are cloned
 * with the same symbol remapping while their own bodies stay ready for a later insertion.
 *
 * The active static callback identity chain rejects direct and mutual recursion. The expander adds
 * no independent hard depth limit; every inserted node is accounted by the shared expansion budget.
 */
internal class InlineExpander(private val expander: MacroExpander) {
    private val active = LinkedHashSet<CallbackId>()
    private val cloning = LinkedHashSet<CallbackId>()

    fun expand(session: ExpansionSession, call: ExprNode.InlineCall, expression: Expr<*>): Code {
        val metadata = (expression as OwnedCode).metadata
        val callback = call.callback as? ImmutableInlineFn<*>
            ?: expander.fail(
                session,
                expression.source(),
                "Dynamic inline function values cannot be expanded"
            )
        val container = callback.target as? InlineTarget.Body
            ?: expander.fail(
                session,
                expression.source(),
                "Static inline target '${callback.callbackId.declaration}' has no bound source body"
            )
        val signature = callback.signature()
        if (!signature.returnType().sameType(expression.type()))
            expander.fail(
                session,
                expression.source(),
                "Inline callback result type differs from its call expression"
            )
        val parameters = signature.parameters()
        if (container.parameters.size != parameters.size || call.arguments.size != parameters.size)
            expander.fail(
                session,
                expression.source(),
                "Inline callback argument count differs from its actual signature"
            )
        val target = if (container.deferred == null) container else
            bindDeferred(session, callback, container, call, parameters, expression)
        if (!active.add(callback.callbackId))
            expander.fail(
                session,
                expression.source(),
                "Recursive inline callback identity '${callback.callbackId.declaration}'"
            )
        try {
            return insert(session, target, signature, parameters, call, expression, metadata)
        } finally {
            active.remove(callback.callbackId)
        }
    }

    /**
     * Bind the source body exactly once for this actual insertion. The descriptor's placeholder
     * exists only to carry capture capabilities through copy/import/print; CONST facts always come
     * from the `InlineCall` arguments that are being expanded here.
     */
    private fun bindDeferred(
        session: ExpansionSession,
        callback: ImmutableInlineFn<*>,
        container: InlineTarget.Body,
        call: ExprNode.InlineCall,
        parameters: List<ParameterView>,
        expression: Expr<*>
    ): InlineTarget.Body {
        val deferred = container.deferred
            ?: return container
        val request = (deferred as? DeferredSourceCallback)?.request
            ?: expander.fail(
                session,
                expression.source(),
                "Deferred inline target is not a source callback body"
            )
        val signature = request.syntax.signature
        val constants = LinkedHashMap<Int, TypedConstant>()
        call.arguments.forEachIndexed { index, argument ->
            val parameter = parameters[index]
            if (parameter.mode() == ParamMode.CONST) {
                val payload = argument.payload
                if (payload !is ArgumentPayload.Constant)
                    expander.fail(
                        session,
                        argument.source(),
                        "CONST inline parameter '${parameter.name()}' requires an actual constant argument"
                    )
                if (!parameter.type().sameType(payload.constant.type()))
                    expander.fail(
                        session,
                        argument.source(),
                        "CONST inline argument type differs from its parameter"
                    )
                constants[index] = TypedConstant(parameter.type(), payload.constant.value(), argument.source())
            }
        }
        val metadata = (call.callback as OwnedCode).metadata
        val bound = try {
            InlineBinder(
                session,
                metadata.scopeId,
                container.deferredCaptures,
                metadata.executionContextId,
                request.environment,
                request.declarations
            ).bind(request, constants)
        } catch (failure: SourceBindingFailure) {
            expander.fail(session, failure.source, failure.message ?: "Deferred inline body binding failed")
        }
        if (bound.callbackId != callback.callbackId)
            expander.fail(session, expression.source(), "Deferred inline callback identity changed during binding")
        if (bound.signature().parameters().size != signature.parameters().size)
            expander.fail(session, expression.source(), "Deferred inline callback signature changed during binding")
        return bound.target as? InlineTarget.Body
            ?: expander.fail(session, expression.source(), "Deferred inline callback did not bind a body target")
    }

    private fun insert(
        session: ExpansionSession,
        target: InlineTarget.Body,
        signature: InlineSignature<*>,
        parameters: List<ParameterView>,
        call: ExprNode.InlineCall,
        expression: Expr<*>,
        metadata: CodeMetadata
    ): Code {
        val context = metadata.executionContextId
        val bodyScope = session.arena.childScope(metadata.scopeId)
        val argumentMetadata = session.arena.metadata(bodyScope, metadata.origin, context)
        val prelude = ArrayList<Stmt>()
        val bindings = LinkedHashMap<SymbolId, SymbolBinding>()
        if (target.captureReferences.isNotEmpty() && target.captureReferences.keys != target.captures)
            expander.fail(
                session,
                expression.source(),
                "Inline callback capture capability is incomplete"
            )
        target.captureReferences.forEach { (root, reference) ->
            val owned = reference as? OwnedCode
                ?: expander.fail(
                    session,
                    expression.source(),
                    "INLINE capture reference has no expansion-session owner"
                )
            if (owned.metadata.sessionId != session.id)
                expander.fail(
                    session,
                    expression.source(),
                    "Inline capture reference belongs to another expansion session"
                )
            if (referenceLocation(reference).root != root)
                expander.fail(
                    session,
                    expression.source(),
                    "Inline capture metadata root differs from its declared capture"
                )
            bindings[root] = SymbolBinding.Reference(reference)
        }
        call.arguments.forEachIndexed { index, argument ->
            val parameter = parameters[index]
            if (argument.mode() != parameter.mode())
                expander.fail(
                    session,
                    argument.source(),
                    "Inline argument mode differs from the actual callback signature"
                )
            val symbol = target.parameters[index]
            bindings[symbol] = when (val payload = argument.payload) {
                is ArgumentPayload.Value -> {
                    val value = expander.expandInlineTree(session, payload.expression) as? Expr<*>
                        ?: expander.fail(
                            session,
                            payload.expression.source(),
                            "A void argument expansion cannot initialize an inline parameter"
                        )
                    val local = session.arena.local(argumentMetadata, parameter.type(), "inlineArgument$index")
                    prelude += session.arena.stmt(
                        argumentMetadata,
                        StmtNode.LocalDecl(local, convert(session, value, parameter.type(), payload.code.source(), argumentMetadata))
                    )
                    SymbolBinding.Reference(local)
                }
                is ArgumentPayload.Constant -> {
                    val constant = payload.constant
                    if (!parameter.type().sameType(constant.type()))
                        expander.fail(
                            session,
                            payload.code.source(),
                            "Inline CONST argument type differs from its actual parameter"
                        )
                    SymbolBinding.Value(
                        session.arena.expr(
                            argumentMetadata,
                            parameter.type(),
                            ExprNode.Literal(constant.value())
                        )
                    )
                }
                is ArgumentPayload.Reference -> {
                    val reference = payload.reference
                    if (!parameter.type().sameType(reference.type()))
                        expander.fail(
                            session,
                            payload.code.source(),
                            "Inline REF argument type differs from its actual parameter"
                        )
                    SymbolBinding.Reference(reference)
                }
                is ArgumentPayload.Inline -> expander.fail(
                    session,
                    payload.code.source(),
                    "A callback cannot accept another inline callback"
                )
            }
        }
        val returnType = signature.returnType()
        val isVoid = (returnType as? ImmutableTypeRef<*>)?.kind == TypeKind.VOID
        val yieldRegion = session.arena.freshRegion()
        val contexts = linkedMapOf<ExecutionContextId, ExecutionContextId>()
        val contextFrames = linkedMapOf<ExecutionContextId, ExecutionContextFrame>()
        if (context != null) {
            val frame = session.arena.contextFrame(context, metadata.scopeId, expression.source())
            contexts[context] = context
            contextFrames[context] = frame
            // The callback body's template root executes at the insertion context. Remap its
            // original root identity so self/position handles created at bind time follow it.
            (target.block as OwnedCode).metadata.executionContextId?.let { template ->
                if (template != context) {
                    contexts[template] = context
                    contextFrames[template] = frame
                }
            }
        }
        val frame = CloneFrame(
            arena = session.arena,
            scopes = linkedMapOf((target.block as OwnedCode).metadata.scopeId to bodyScope),
            symbols = bindings,
            regions = linkedMapOf(),
            addresses = linkedMapOf(),
            origin = metadata.origin,
            context = context,
            contexts = contexts,
            contextFrames = contextFrames,
            transformReturn = target.returnRegion,
            returnMode = if (isVoid) ReturnMode.VoidYield(yieldRegion)
            else ReturnMode.Yield(yieldRegion)
        )
        val cloned = cloneBlock(session, target.block, bodyScope, frame)
        val expanded = expander.expandInlineTree(session, cloned) as? Block
            ?: expander.fail(
                session,
                expression.source(),
                "An inline callback body expansion must remain a block"
            )
        val statements = ArrayList<Stmt>(prelude)
        statements += expanded.statements()
        // The inserted ExprBlock replaces the call expression at its original position, so the
        // wrapper itself must stay visible from the caller's scope. Only the callback body, its
        // locals and the region exit live in the fresh child scope.
        val callSiteMetadata = session.arena.metadata(metadata.scopeId, metadata.origin, context)
        return if (isVoid) {
            // A void callback still needs a real region exit so a return inside one of its own
            // loops can leave the callback without becoming a cross-loop Break. The unused bool
            // result is only an internal region value; inlining remains a statement.
            val bool = session.environment.types.boolType()
            if (normalExit(expanded)) statements += session.arena.stmt(
                clonedMetadata(session, expanded, frame),
                StmtNode.Yield(
                    yieldRegion,
                    session.arena.expr(
                        clonedMetadata(session, expanded, frame),
                        bool,
                        ExprNode.Literal(ConstantValue.Bool(false))
                    )
                )
            )
            val body = session.arena.block(clonedMetadata(session, expanded, frame), statements)
            session.arena.expr(
                callSiteMetadata,
                bool,
                ExprNode.ExprBlock(body, bool, yieldRegion)
            )
        } else {
            if (normalExit(expanded))
                expander.fail(
                    session,
                    expression.source(),
                    "Non-void inline callback body has a normal exit without return"
                )
            val body = session.arena.block(session.arena.metadata(bodyScope, metadata.origin, context), statements)
            session.arena.expr(
                callSiteMetadata,
                returnType,
                ExprNode.ExprBlock(body, returnType, yieldRegion)
            )
        }
    }

    private fun clonedMetadata(session: ExpansionSession, block: Block, frame: CloneFrame): CodeMetadata {
        val metadata = (block as OwnedCode).metadata
        return session.arena.metadata(metadata.scopeId, frame.origin, mappedContext(metadata.executionContextId, frame))
    }

    private fun convert(
        session: ExpansionSession,
        value: Expr<*>,
        target: TypeRef<*>,
        source: SourceSpan,
        metadata: CodeMetadata
    ): Expr<*> {
        if (value.type().sameType(target)) return value
        if (!target.isAssignableFrom(value.type()))
            expander.fail(session, source, "Inline argument type is not assignable to its parameter")
        return session.arena.expr(metadata, target, ExprNode.Convert(value, target, ConversionKind.IMPLICIT))
    }

    private sealed interface SymbolBinding {
        data class Reference(val ref: Ref<*>) : SymbolBinding
        data class Value(val expression: Expr<*>) : SymbolBinding
    }

    private sealed interface ReturnMode {
        data class Yield(val region: RegionId) : ReturnMode
        data class VoidYield(val region: RegionId) : ReturnMode
    }

    private class CloneFrame(
        val arena: CodeArena,
        val scopes: MutableMap<ScopeId, ScopeId>,
        val symbols: MutableMap<SymbolId, SymbolBinding>,
        val regions: MutableMap<RegionId, RegionId>,
        val addresses: MutableMap<AddressId, AddressId>,
        val origin: Origin,
        var context: ExecutionContextId?,
        val contexts: MutableMap<ExecutionContextId, ExecutionContextId>,
        val contextFrames: MutableMap<ExecutionContextId, ExecutionContextFrame>,
        val transformReturn: RegionId,
        val returnMode: ReturnMode
    )

    private fun metadata(session: ExpansionSession, scope: ScopeId, frame: CloneFrame): CodeMetadata = session.arena.metadata(scope, frame.origin, frame.context)

    private fun mappedContext(context: ExecutionContextId?, frame: CloneFrame): ExecutionContextId? = context?.let { frame.contexts[it] ?: it }

    private fun cloneBlock(
        session: ExpansionSession,
        template: Block,
        parent: ScopeId,
        frame: CloneFrame
    ): ImmutableBlock {
        val templateScope = (template as OwnedCode).metadata.scopeId
        val scope = frame.scopes.getOrPut(templateScope) { session.arena.childScope(parent) }
        val statements = template.statements().map {
            cloneStatement(session, it as ImmutableStmt, scope, frame)
        }
        return session.arena.block(metadata(session, scope, frame), statements)
    }

    private fun cloneStatement(
        session: ExpansionSession,
        statement: ImmutableStmt,
        scope: ScopeId,
        frame: CloneFrame
    ): ImmutableStmt {
        val node = statement.node
        val metadata = metadata(session, scope, frame)
        return when (node) {
            is StmtNode.LocalDecl -> {
                val local = node.local as ImmutableLocal<*>
                val initial = cloneExpression(session, node.initialValue, scope, frame)
                val fresh = session.arena.local(metadata, local.type(), local.hint(), session.arena.freshSymbol())
                frame.symbols[local.symbol] = SymbolBinding.Reference(fresh)
                session.arena.stmt(metadata, StmtNode.LocalDecl(fresh, initial))
            }
            is StmtNode.Assign -> session.arena.stmt(
                metadata,
                StmtNode.Assign(
                    cloneRef(session, node.destination, scope, frame),
                    cloneExpression(session, node.expression, scope, frame)
                )
            )
            is StmtNode.Eval -> session.arena.stmt(
                metadata,
                StmtNode.Eval(cloneExpression(session, node.expression, scope, frame))
            )
            is StmtNode.If -> session.arena.stmt(
                metadata,
                StmtNode.If(
                    cloneExpression(session, node.condition, scope, frame),
                    cloneBlock(session, node.thenBlock, scope, frame),
                    node.elseBlock?.let { cloneBlock(session, it, scope, frame) }
                )
            )
            is StmtNode.While -> session.arena.stmt(
                metadata,
                StmtNode.While(
                    cloneExpression(session, node.condition, scope, frame),
                    cloneBlock(session, node.body, scope, frame),
                    region(node.region, frame)
                )
            )
            is StmtNode.ScopeBlock -> session.arena.stmt(
                metadata,
                StmtNode.ScopeBlock(cloneBlock(session, node.block, scope, frame))
            )
            is StmtNode.Yield -> session.arena.stmt(
                metadata,
                StmtNode.Yield(region(node.region, frame), cloneExpression(session, node.value, scope, frame))
            )
            is StmtNode.CallbackReturn -> {
                if (node.region == frame.transformReturn) {
                    val value = node.value?.let { cloneExpression(session, it, scope, frame) }
                    when (val mode = frame.returnMode) {
                        is ReturnMode.Yield -> {
                            val result = value ?: expander.fail(
                                session,
                                statement.source(),
                                "Inline value return requires an expression"
                            )
                            session.arena.stmt(metadata, StmtNode.Yield(mode.region, result))
                        }
                        is ReturnMode.VoidYield -> {
                            if (value != null) expander.fail(
                                session,
                                statement.source(),
                                "Void inline callback cannot return a value"
                            )
                            val bool = session.environment.types.boolType()
                            session.arena.stmt(
                                metadata,
                                StmtNode.Yield(
                                    mode.region,
                                    session.arena.expr(metadata, bool, ExprNode.Literal(ConstantValue.Bool(false)))
                                )
                            )
                        }
                    }
                } else session.arena.stmt(
                    metadata,
                    StmtNode.CallbackReturn(
                        region(node.region, frame),
                        node.value?.let { cloneExpression(session, it, scope, frame) }
                    )
                )
            }
            is StmtNode.Break -> session.arena.stmt(metadata, StmtNode.Break(region(node.region, frame)))
            is StmtNode.Continue -> session.arena.stmt(metadata, StmtNode.Continue(region(node.region, frame)))
            is StmtNode.CaptureAddress -> session.arena.stmt(
                metadata,
                StmtNode.CaptureAddress(
                    addresses(node.address, frame),
                    cloneLocation(node.location, frame),
                    node.type
                )
            )
            is StmtNode.ExecuteAs -> cloneExecuteAs(session, node, scope, frame, metadata)
            is StmtNode.At -> cloneAt(session, node, scope, frame, metadata)
            is StmtNode.Rotated -> cloneRotated(session, node, scope, frame, metadata)
        }
    }

    /**
     * Context statements allocate a fresh body scope and a fresh execution-context child, exactly
     * like a source insertion. Cloning keeps the handle graph inside the callback body; it never
     * copies the descriptor's context IDs or lets a self/position escape the cloned scope.
     */
    private fun cloneExecuteAs(
        session: ExpansionSession,
        node: StmtNode.ExecuteAs,
        scope: ScopeId,
        frame: CloneFrame,
        statementMetadata: CodeMetadata
    ): ImmutableStmt {
        val parent = currentContextFrame(session, scope, frame, node.parentContext)
        val bodyScope = session.arena.childScope(scope)
        val child = session.arena.executorContext(parent, bodyScope)
        val targets = cloneEntitySet(session, node.targets, scope, frame)
        seedContext(session, node, bodyScope, child, frame)
        val previous = frame.context
        val body = try {
            frame.context = child.id
            cloneBlock(session, node.body, scope, frame)
        } finally {
            frame.context = previous
        }
        val childMetadata = session.arena.metadata(bodyScope, frame.origin, child.id)
        val self = ImmutableEntityHandle(childMetadata, child.executor)
        return session.arena.stmt(
            statementMetadata,
            StmtNode.ExecuteAs(targets, self, body, parent, child)
        )
    }

    private fun cloneAt(
        session: ExpansionSession,
        node: StmtNode.At,
        scope: ScopeId,
        frame: CloneFrame,
        statementMetadata: CodeMetadata
    ): ImmutableStmt {
        val parent = currentContextFrame(session, scope, frame, node.parentContext)
        val position = clonePosition(session, node.position, scope, frame)
        val bodyScope = session.arena.childScope(scope)
        val child = session.arena.positionContext(parent, bodyScope)
        seedContext(session, node, bodyScope, child, frame)
        val previous = frame.context
        val body = try {
            frame.context = child.id
            cloneBlock(session, node.body, scope, frame)
        } finally {
            frame.context = previous
        }
        return session.arena.stmt(statementMetadata, StmtNode.At(position, body, parent, child))
    }

    private fun cloneRotated(
        session: ExpansionSession,
        node: StmtNode.Rotated,
        scope: ScopeId,
        frame: CloneFrame,
        statementMetadata: CodeMetadata
    ): ImmutableStmt {
        val parent = currentContextFrame(session, scope, frame, node.parentContext)
        val rotation = cloneRotation(session, node.rotation, scope, frame)
        val bodyScope = session.arena.childScope(scope)
        val child = session.arena.rotationContext(parent, bodyScope)
        seedContext(session, node, bodyScope, child, frame)
        val previous = frame.context
        val body = try {
            frame.context = child.id
            cloneBlock(session, node.body, scope, frame)
        } finally {
            frame.context = previous
        }
        return session.arena.stmt(statementMetadata, StmtNode.Rotated(rotation, body, parent, child))
    }

    private fun currentContextFrame(
        session: ExpansionSession,
        scope: ScopeId,
        frame: CloneFrame,
        original: ExecutionContextFrame
    ): ExecutionContextFrame {
        val current = frame.context
            ?: expander.fail(
                session,
                SourceSpan.unknown(),
                "An inline context statement has no parent execution context"
            )
        frame.contextFrames[original.id]?.let { return it }
        return try {
            session.arena.contextFrame(current, scope, SourceSpan.unknown())
        } catch (failure: CodeModelException) {
            expander.fail(
                session,
                failure.source,
                "An inline context statement cannot reconstruct its parent execution frame: ${failure.message}"
            )
        }
    }

    private fun seedContext(
        session: ExpansionSession,
        node: Any,
        bodyScope: ScopeId,
        child: ExecutionContextFrame,
        frame: CloneFrame
    ) {
        val body = when (node) {
            is StmtNode.ExecuteAs -> node.body
            is StmtNode.At -> node.body
            is StmtNode.Rotated -> node.body
            else -> throw IllegalStateException("Not an execution-context statement")
        }
        frame.contexts[
            when (node) {
                is StmtNode.ExecuteAs -> node.context.id
                is StmtNode.At -> node.context.id
                is StmtNode.Rotated -> node.context.id
                else -> throw IllegalStateException("Not an execution-context statement")
            }
        ] = child.id
        frame.contextFrames[
            when (node) {
                is StmtNode.ExecuteAs -> node.context.id
                is StmtNode.At -> node.context.id
                is StmtNode.Rotated -> node.context.id
                else -> throw IllegalStateException("Not an execution-context statement")
            }
        ] = child
        frame.scopes[(body as OwnedCode).metadata.scopeId] = bodyScope
    }

    private fun cloneExpression(
        session: ExpansionSession,
        expression: Expr<*>,
        scope: ScopeId,
        frame: CloneFrame
    ): Expr<*> {
        val metadata = metadata(session, scope, frame)
        val type = expression.type()
        return when (val node = (expression as ImmutableExpr<*>).node) {
            is ExprNode.Literal -> session.arena.expr(metadata, type, ExprNode.Literal(node.value))
            is ExprNode.Read -> cloneRead(session, node.location, type, scope, frame)
            is ExprNode.ScoreRead -> session.arena.expr(
                metadata,
                type,
                ExprNode.ScoreRead(cloneScoreTarget(session, node.target, scope, frame), node.objective)
            )
            is ExprNode.BlockMatches -> session.arena.expr(
                metadata,
                type,
                ExprNode.BlockMatches(cloneBlockHandle(session, node.block, scope, frame), node.predicate)
            )
            is ExprNode.Unary -> session.arena.expr(
                metadata,
                type,
                ExprNode.Unary(node.operator, cloneExpression(session, node.operand, scope, frame))
            )
            is ExprNode.Binary -> session.arena.expr(
                metadata,
                type,
                ExprNode.Binary(
                    node.operator,
                    cloneExpression(session, node.left, scope, frame),
                    cloneExpression(session, node.right, scope, frame)
                )
            )
            is ExprNode.Convert -> session.arena.expr(
                metadata,
                type,
                ExprNode.Convert(
                    cloneExpression(session, node.value, scope, frame),
                    node.target,
                    node.kind
                )
            )
            is ExprNode.Field -> session.arena.expr(
                metadata,
                type,
                ExprNode.Field(
                    cloneExpression(session, node.receiver, scope, frame),
                    node.field
                )
            )
            is ExprNode.Index -> session.arena.expr(
                metadata,
                type,
                ExprNode.Index(
                    cloneExpression(session, node.receiver, scope, frame),
                    cloneExpression(session, node.index, scope, frame)
                )
            )
            is ExprNode.Call -> session.arena.expr(
                metadata,
                type,
                ExprNode.Call(
                    node.function,
                    node.arguments.map { cloneArgument(session, it, scope, frame) },
                    node.readonlyArguments.map { TypedConstant(it.type, it.value, it.source) },
                    node.receiver?.let { cloneRef(session, it, scope, frame) }
                )
            )
            is ExprNode.MacroCall -> session.arena.expr(
                metadata,
                type,
                ExprNode.MacroCall(
                    node.function,
                    node.arguments.map { cloneArgument(session, it, scope, frame) }
                )
            )
            is ExprNode.ExprBlock -> {
                val target = region(node.region, frame)
                session.arena.expr(
                    metadata,
                    type,
                    ExprNode.ExprBlock(
                        cloneBlock(session, node.block, scope, frame),
                        node.resultType,
                        target
                    )
                )
            }
            is ExprNode.InlineCall -> session.arena.expr(
                metadata,
                type,
                ExprNode.InlineCall(
                    cloneCallback(session, node.callback, scope, frame),
                    node.arguments.map { InlineArgument(cloneArgumentPayload(session, it.payload, scope, frame)) }
                )
            )
        }
    }

    private fun cloneRead(
        session: ExpansionSession,
        location: NodeLocation,
        type: TypeRef<*>,
        scope: ScopeId,
        frame: CloneFrame
    ): Expr<*> {
        return when (val binding = frame.symbols[location.root]) {
            is SymbolBinding.Value -> {
                if (location.steps.isNotEmpty())
                    expander.fail(
                        session,
                        SourceSpan.unknown(),
                        "A compile-time inline value cannot carry a runtime location path"
                    )
                binding.expression
            }
            is SymbolBinding.Reference, null -> session.arena.expr(
                metadata(session, scope, frame),
                type,
                ExprNode.Read(cloneLocation(location, frame))
            )
        }
    }

    private fun cloneArgument(
        session: ExpansionSession,
        argument: CallArgument,
        scope: ScopeId,
        frame: CloneFrame
    ): CallArgument = CallArgument(cloneArgumentPayload(session, argument.payload, scope, frame))

    private fun cloneArgumentPayload(
        session: ExpansionSession,
        payload: ArgumentPayload,
        scope: ScopeId,
        frame: CloneFrame
    ): ArgumentPayload = when (payload) {
        is ArgumentPayload.Value -> ArgumentPayload.Value(cloneExpression(session, payload.expression, scope, frame))
        is ArgumentPayload.Reference -> ArgumentPayload.Reference(cloneRef(session, payload.reference, scope, frame))
        is ArgumentPayload.Constant -> ArgumentPayload.Constant(
            session.arena.constant(cloneExpression(session, payload.constant.expr(), scope, frame), payload.constant.value())
        )
        is ArgumentPayload.Inline -> ArgumentPayload.Inline(cloneCallback(session, payload.callback, scope, frame))
    }

    private fun cloneRef(
        session: ExpansionSession,
        reference: Ref<*>,
        scope: ScopeId,
        frame: CloneFrame
    ): Ref<*> {
        val metadata = metadata(session, scope, frame)
        return when (reference) {
            is ImmutableMinecraftScoreRef -> session.arena.scoreRef(
                metadata,
                reference.type(),
                cloneScoreTarget(session, reference.target, scope, frame),
                reference.objective
            )
            is ImmutableRef<*> -> session.arena.ref(metadata, reference.type(), cloneLocation(reference.location, frame))
            is ImmutableLocal<*> -> session.arena.ref(metadata, reference.type(), cloneLocation(reference.location, frame))
        }
    }

    private fun cloneMetadata(session: ExpansionSession, old: CodeMetadata, frame: CloneFrame): CodeMetadata = session.arena.metadata(
        frame.scopes[old.scopeId] ?: old.scopeId,
        frame.origin,
        mappedContext(old.executionContextId, frame)
    )

    private fun cloneEntitySet(
        session: ExpansionSession,
        targets: EntitySet,
        scope: ScopeId,
        frame: CloneFrame
    ): EntitySet {
        val descriptor = targets as? ImmutableEntitySet
            ?: expander.fail(session, targets.source(), "Unsupported inline entity collection")
        return session.arena.entitySet(metadata(session, scope, frame), descriptor.query)
    }

    private fun cloneEntity(
        session: ExpansionSession,
        entity: EntityHandle,
        scope: ScopeId,
        frame: CloneFrame
    ): EntityHandle {
        val descriptor = entity as? ImmutableEntityHandle
            ?: expander.fail(session, entity.source(), "Unsupported inline entity handle")
        val metadata = cloneMetadata(session, descriptor.metadata, frame)
        val executor = frame.contexts[descriptor.executor] ?: descriptor.executor
        return ImmutableEntityHandle(metadata, executor)
    }

    private fun cloneScoreTarget(
        session: ExpansionSession,
        target: MinecraftScoreTarget,
        scope: ScopeId,
        frame: CloneFrame
    ): MinecraftScoreTarget = when (target) {
        is MinecraftScoreTarget.Fixed -> target
        is MinecraftScoreTarget.Entity -> MinecraftScoreTarget.Entity(
            cloneEntity(session, target.entity, scope, frame)
        )
    }

    private fun clonePosition(
        session: ExpansionSession,
        position: PositionHandle,
        scope: ScopeId,
        frame: CloneFrame
    ): PositionHandle {
        val descriptor = position as? ImmutablePositionHandle
            ?: expander.fail(session, position.source(), "Unsupported inline position handle")
        val metadata = cloneMetadata(session, descriptor.metadata, frame)
        val node = when (val node = descriptor.node) {
            is PositionNode.Absolute -> node
            is PositionNode.Current -> PositionNode.Current(
                mappedContext(node.context, frame)
                    ?: expander.fail(session, position.source(), "An inline current position lost its context")
            )
            is PositionNode.Entity -> PositionNode.Entity(cloneEntity(session, node.entity, scope, frame))
            is PositionNode.Offset -> PositionNode.Offset(
                clonePosition(session, node.base, scope, frame),
                node.x,
                node.y,
                node.z
            )
            is PositionNode.LocalOffset -> PositionNode.LocalOffset(
                clonePosition(session, node.base, scope, frame),
                cloneRotation(session, node.rotation, scope, frame),
                node.left,
                node.up,
                node.forward
            )
        }
        return session.arena.position(metadata, node)
    }

    private fun cloneRotation(
        session: ExpansionSession,
        rotation: RotationHandle,
        scope: ScopeId,
        frame: CloneFrame
    ): RotationHandle {
        val descriptor = rotation as? ImmutableRotationHandle
            ?: expander.fail(session, rotation.source(), "Unsupported inline rotation handle")
        val metadata = cloneMetadata(session, descriptor.metadata, frame)
        val node = when (val node = descriptor.node) {
            is RotationNode.Absolute -> node
            is RotationNode.Current -> RotationNode.Current(
                mappedContext(node.context, frame)
                    ?: expander.fail(session, rotation.source(), "An inline current rotation lost its context")
            )
            is RotationNode.Entity -> RotationNode.Entity(cloneEntity(session, node.entity, scope, frame))
        }
        return session.arena.rotation(metadata, node)
    }

    private fun cloneBlockHandle(
        session: ExpansionSession,
        block: BlockHandle,
        scope: ScopeId,
        frame: CloneFrame
    ): BlockHandle {
        val descriptor = block as? ImmutableBlockHandle
            ?: expander.fail(session, block.source(), "Unsupported inline block handle")
        val metadata = cloneMetadata(session, descriptor.metadata, frame)
        return session.arena.blockHandle(metadata, clonePosition(session, descriptor.position, scope, frame))
    }

    private fun cloneCallback(
        session: ExpansionSession,
        callback: InlineFn<*>,
        parent: ScopeId,
        frame: CloneFrame
    ): ImmutableInlineFn<*> {
        val descriptor = callback as? ImmutableInlineFn<*>
            ?: expander.fail(session, callback.source(), "Dynamic inline function values cannot be cloned")
        val target = descriptor.target as? InlineTarget.Body
            ?: expander.fail(
                session,
                callback.source(),
                "Static inline target '${descriptor.callbackId.declaration}' has no bound source body"
            )
        if (!cloning.add(descriptor.callbackId))
            expander.fail(
                session,
                callback.source(),
                "Recursive inline callback identity '${descriptor.callbackId.declaration}'"
            )
        try {
            val signature = descriptor.signature()
            if (target.parameters.size != signature.parameters().size)
                expander.fail(
                    session,
                    callback.source(),
                    "Nested inline callback parameters differ from its actual signature"
                )
            if (target.captureReferences.isNotEmpty() && target.captureReferences.keys != target.captures)
                expander.fail(
                    session,
                    callback.source(),
                    "Nested inline callback capture capability is incomplete"
                )
            val scope = session.arena.childScope(parent)
            val bodyMetadata = metadata(session, scope, frame)
            // A nested callback descriptor is a value used at [parent]; its cloned body and
            // parameters live in the child scope, but the descriptor itself must remain visible
            // from the Insertion scope or a nested InlineCall fails lexical visibility.
            val descriptorMetadata = metadata(session, parent, frame)
            val parameters = target.parameters.mapIndexed { index, symbol ->
                val local = session.arena.local(
                    bodyMetadata,
                    signature.parameters()[index].type(),
                    "inlineCallbackParameter$index",
                    session.arena.freshSymbol()
                )
                frame.symbols[symbol] = SymbolBinding.Reference(local)
                local.symbol
            }
            frame.scopes[(target.block as OwnedCode).metadata.scopeId] = scope
            val block = cloneBlock(session, target.block, scope, frame)
            val captureReferences = LinkedHashMap<SymbolId, Ref<*>>()
            target.captureReferences.forEach { (_, reference) ->
                // Capture capability belongs to the descriptor's use scope, not to the nested
                // body scope; otherwise the descriptor cannot carry it back to its InlineCall.
                val cloned = cloneRef(session, reference, parent, frame)
                captureReferences[referenceLocation(cloned).root] = cloned
            }
            val deferredCaptures = LinkedHashMap<SymbolId, Ref<*>>()
            target.deferredCaptures.forEach { (symbol, reference) ->
                // These keys are original source identities, not arena roots. The value is the
                // enclosing insertion's actual capability and must be remapped with the clone.
                deferredCaptures[symbol] = cloneRef(session, reference, parent, frame)
            }
            val captures = if (target.captureReferences.isEmpty() && target.deferredCaptures.isEmpty())
                target.captures.map { root(it, frame) }.toSet()
            else (captureReferences.keys + deferredCaptures.values.map { referenceLocation(it).root }).toSet()
            val returnRegion = region(target.returnRegion, frame)
            return session.arena.inline<Any?>(
                descriptorMetadata,
                signature,
                descriptor.callbackId,
                InlineTarget.Body(
                    parameters,
                    block,
                    captures,
                    returnRegion,
                    captureReferences,
                    target.deferred,
                    deferredCaptures
                )
            )
        } finally {
            cloning.remove(descriptor.callbackId)
        }
    }

    private fun referenceLocation(reference: Ref<*>): NodeLocation = when (reference) {
        is ImmutableRef<*> -> reference.location
        is ImmutableLocal<*> -> reference.location
        else -> throw IllegalStateException("An inline score reference has no symbol location")
    }

    private fun root(symbol: SymbolId, frame: CloneFrame): SymbolId = when (val binding = frame.symbols[symbol]) {
        is SymbolBinding.Reference -> referenceLocation(binding.ref).root
        is SymbolBinding.Value -> throw IllegalStateException("A compile-time inline value cannot be captured as a location")
        null -> symbol
    }

    private fun cloneLocation(location: NodeLocation, frame: CloneFrame): NodeLocation {
        val binding = frame.symbols[location.root]
        val base = when (binding) {
            is SymbolBinding.Reference -> referenceLocation(binding.ref)
            is SymbolBinding.Value -> throw IllegalStateException("A compile-time inline value has no location")
            null -> null
        }
        val steps = location.steps.map {
            when (it) {
                is LocationStep.CapturedIndex -> LocationStep.CapturedIndex(symbol(it.symbol, frame))
                is LocationStep.CapturedKey -> LocationStep.CapturedKey(symbol(it.symbol, frame))
                else -> it
            }
        }
        val prefix = if (base != null && location.capturedBase == null) base.steps else emptyList()
        val captured = location.capturedBase?.let { addresses(it, frame) } ?: base?.capturedBase
        return NodeLocation(
            base?.root ?: location.root,
            prefix + steps,
            mappedContext(base?.executionContextId ?: location.executionContextId, frame),
            location.writable && (base?.writable ?: true),
            captured
        )
    }

    private fun symbol(symbol: SymbolId, frame: CloneFrame): SymbolId = when (val binding = frame.symbols[symbol]) {
        is SymbolBinding.Reference -> {
            require(referenceLocation(binding.ref).steps.isEmpty()) { "A captured location component must be a root symbol" }
            referenceLocation(binding.ref).root
        }
        is SymbolBinding.Value -> throw IllegalStateException("A compile-time inline value cannot be a location component")
        null -> symbol
    }

    private fun region(region: RegionId, frame: CloneFrame): RegionId = frame.regions.getOrPut(region) { frame.arena.freshRegion() }

    private fun addresses(address: AddressId, frame: CloneFrame): AddressId = frame.addresses.getOrPut(address) { frame.arena.freshAddress() }

    private fun normalExit(block: Block): Boolean = block.statements().all { normalExit(it as ImmutableStmt) }

    private fun normalExit(statement: ImmutableStmt): Boolean = when (val node = statement.node) {
        is StmtNode.Break, is StmtNode.Continue, is StmtNode.Yield, is StmtNode.CallbackReturn -> false
        is StmtNode.If -> normalExit(node.thenBlock) || (node.elseBlock?.let(::normalExit) ?: true)
        is StmtNode.ScopeBlock -> normalExit(node.block)
        is StmtNode.While -> {
            val condition = (node.condition as? ImmutableExpr<*>)?.node
            if (condition is ExprNode.Literal && condition.value == ConstantValue.Bool(true))
                containsBreak(node.body, node.region)
            else true
        }
        else -> true
    }

    private fun containsBreak(block: Block, region: RegionId): Boolean = block.statements().any { statement ->
        when (val node = (statement as ImmutableStmt).node) {
            is StmtNode.Break -> node.region == region
            is StmtNode.If -> containsBreak(node.thenBlock, region) ||
                (node.elseBlock?.let { containsBreak(it, region) } ?: false)
            is StmtNode.ScopeBlock -> containsBreak(node.block, region)
            else -> false
        }
    }
}
