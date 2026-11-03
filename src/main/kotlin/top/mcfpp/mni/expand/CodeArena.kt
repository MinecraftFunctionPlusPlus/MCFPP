package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.util.IdentityHashMap

/** Owns allocation and hygiene, independently of compiler globals and expansion transactions. */
internal class CodeArena private constructor(
    val sessionId: SessionId,
    val rootScope: ScopeId,
    private val accountNodes: (Int, Origin) -> Unit,
    private val family: Family,
    private val parentArena: CodeArena?
) : AutoCloseable {
    constructor(sessionId: SessionId, rootScope: ScopeId, accountNodes: (Int, Origin) -> Unit) :
        this(sessionId, rootScope, accountNodes, Family(), null)

    private class Family {
        val arenas = linkedMapOf<SessionId, CodeArena>()
        var open = true
    }

    private val scopes = linkedMapOf<ScopeId, ScopeId?>(rootScope to null)
    private val symbolScopes = linkedMapOf<SymbolId, ScopeId>()
    private val addressScopes = linkedMapOf<AddressId, ScopeId>()
    private val allocatedAddresses = hashSetOf<AddressId>()
    private val contextFrames = linkedMapOf<ExecutionContextId, ExecutionContextFrame>()
    private val contextScopes = linkedMapOf<ExecutionContextId, ScopeId>()
    private val allocatedHandles = java.util.Collections.newSetFromMap(IdentityHashMap<Code, Boolean>())
    private val checkedDependencies = java.util.Collections.newSetFromMap(IdentityHashMap<Code, Boolean>())
    private val checkingDependencies = java.util.Collections.newSetFromMap(IdentityHashMap<Code, Boolean>())
    private var scopeSequence = rootScope.value
    private var symbolSequence = 0L
    private var regionSequence = 0L
    private var contextSequence = 0L
    private var addressSequence = 0L
    private var open = true

    init {
        require(rootScope.session == sessionId) { "Root scope belongs to another session" }
        require(sessionId !in family.arenas) { "An arena already owns this session" }
        family.arenas[sessionId] = this
    }

    private fun checkOpen(source: SourceSpan = SourceSpan.unknown()) {
        var ancestor: CodeArena? = this
        while (ancestor != null) {
            if (!ancestor.open || !family.open) throw CodeModelException(source, "Code arena is closed")
            ancestor = ancestor.parentArena
        }
    }

    private fun useType(type: TypeRef<*>, source: SourceSpan) {
        val ref = type as ImmutableTypeRef<*>
        if (ref.graph?.owns(ref) == false) throw CodeModelException(source, "Type is not a canonical frozen handle")
    }

    private fun useConstant(value: ConstantValue, source: SourceSpan) {
        when (value) {
            is ConstantValue.TypeValue -> useType(value.type, source)
            is ConstantValue.Sequence -> value.elements.forEach { useConstant(it, source) }
            is ConstantValue.Record -> value.fields.values.forEach { useConstant(it, source) }
            else -> Unit
        }
    }

    private fun useReadonly(arguments: List<TypedConstant>) = arguments.forEach {
        useType(it.type, it.source)
        useConstant(it.value, it.source)
    }

    private fun requireScope(scope: ScopeId) {
        checkOpen()
        require(scope in scopes) { "Scope belongs to another code arena" }
    }

    fun containsScope(scope: ScopeId): Boolean = scope in scopes
    fun scopeParent(scope: ScopeId): ScopeId? {
        requireScope(scope)
        return scopes[scope]
    }
    fun childScope(parent: ScopeId): ScopeId {
        requireScope(parent)
        return ScopeId(sessionId, ++scopeSequence).also { scopes[it] = parent }
    }

    fun isVisible(declaration: ScopeId, use: ScopeId): Boolean {
        requireScope(use)
        var current: ScopeId? = use
        while (current != null) {
            if (current == declaration) return true
            current = scopes[current]
        }
        return false
    }

    fun freshSymbol(): SymbolId {
        checkOpen()
        return SymbolId(sessionId, ++symbolSequence)
    }
    fun freshRegion(): RegionId {
        checkOpen()
        return RegionId(sessionId, ++regionSequence)
    }
    fun freshExecutionContext(): ExecutionContextId {
        checkOpen()
        return ExecutionContextId(sessionId, ++contextSequence)
    }
    fun rootContext(scope: ScopeId): ExecutionContextFrame {
        requireScope(scope)
        return registerContext(ExecutionContextFrame.root(freshExecutionContext()), scope)
    }

    /** Registration and lexical visibility are separate facts; this query does not grant use. */
    fun hasContextFrame(id: ExecutionContextId, source: SourceSpan): Boolean {
        checkOpen(source)
        if (id.session != sessionId) throw CodeModelException(source, "Execution context belongs to another session")
        return id in contextFrames
    }

    /** A frame is an arena-owned capability; an existing ID never implies root components. */
    fun contextFrame(id: ExecutionContextId, scope: ScopeId, source: SourceSpan): ExecutionContextFrame {
        checkOpen(source)
        requireScope(scope)
        if (id.session != sessionId) throw CodeModelException(source, "Execution context belongs to another session")
        useContext(id, scope, source)
        return contextFrames.getValue(id)
    }

    fun executorContext(parent: ExecutionContextFrame, scope: ScopeId): ExecutionContextFrame = childContext(parent, scope) { parent.withExecutor(it) }
    fun positionContext(parent: ExecutionContextFrame, scope: ScopeId): ExecutionContextFrame = childContext(parent, scope) { parent.withPosition(it) }
    fun rotationContext(parent: ExecutionContextFrame, scope: ScopeId): ExecutionContextFrame = childContext(parent, scope) { parent.withRotation(it) }

    private fun childContext(
        parent: ExecutionContextFrame,
        scope: ScopeId,
        create: (ExecutionContextId) -> ExecutionContextFrame
    ): ExecutionContextFrame {
        useContext(parent.id, scope, SourceSpan.unknown())
        require(contextFrames[parent.id] == parent) { "Execution frame was not allocated by this arena" }
        require(contextScopes[parent.id] != scope) { "A context requires its own child scope" }
        return registerContext(create(freshExecutionContext()), scope)
    }

    private fun registerContext(frame: ExecutionContextFrame, scope: ScopeId): ExecutionContextFrame {
        requireScope(scope)
        require(frame.id.session == sessionId && frame.id !in contextFrames) { "Duplicate or foreign execution context" }
        contextFrames[frame.id] = frame
        contextScopes[frame.id] = scope
        return frame
    }

    private fun useContext(id: ExecutionContextId, scope: ScopeId, source: SourceSpan) {
        val declaration = contextScopes[id]
            ?: throw CodeModelException(source, "Execution context was not allocated by this arena")
        if (!isVisible(declaration, scope)) throw CodeModelException(source, "Execution context is outside its lexical scope")
    }

    private fun useFrame(frame: ExecutionContextFrame, scope: ScopeId, source: SourceSpan) {
        useContext(frame.id, scope, source)
        if (contextFrames[frame.id] != frame) throw CodeModelException(source, "Execution frame differs from its allocated components")
    }

    private fun useScore(target: MinecraftScoreTarget, scope: ScopeId) {
        if (target is MinecraftScoreTarget.Entity) use(target.entity, scope)
    }

    private fun usePosition(node: PositionNode, scope: ScopeId, source: SourceSpan) {
        when (node) {
            is PositionNode.Absolute -> Unit
            is PositionNode.Current -> useContext(node.context, scope, source)
            is PositionNode.Entity -> use(node.entity, scope)
            is PositionNode.Offset -> use(node.base, scope)
            is PositionNode.LocalOffset -> {
                use(node.base, scope)
                use(node.rotation, scope)
            }
        }
    }

    private fun useRotation(node: RotationNode, scope: ScopeId, source: SourceSpan) {
        when (node) {
            is RotationNode.Absolute -> Unit
            is RotationNode.Current -> useContext(node.context, scope, source)
            is RotationNode.Entity -> use(node.entity, scope)
        }
    }

    private fun useContextBody(
        metadata: CodeMetadata,
        parent: ExecutionContextFrame,
        context: ExecutionContextFrame,
        body: Block
    ) {
        val source = metadata.origin.generatedFrom ?: metadata.origin.callSite
        useFrame(parent, metadata.scopeId, source)
        if (metadata.executionContextId != parent.id) throw CodeModelException(source, "Context statement requires its parent execution context")
        val bodyScope = (body as OwnedCode).metadata.scopeId
        useFrame(context, bodyScope, source)
        if (bodyScope == metadata.scopeId || contextScopes[context.id] != bodyScope)
            throw CodeModelException(source, "Context body requires its own declared scope")
        if (body.statements().any { (it as OwnedCode).metadata.executionContextId != context.id })
            throw CodeModelException(source, "Context body statement belongs to another execution context")
        use(body, metadata.scopeId, true)
    }

    private fun <C : OwnedCode> handle(code: C): C {
        own(code.metadata)
        useProjectionFields(code, code.metadata.scopeId)
        accountNodes(1, code.metadata.origin)
        allocatedHandles.add(code)
        return code
    }

    fun entitySet(metadata: CodeMetadata, query: EntityQuery): ImmutableEntitySet = handle(ImmutableEntitySet(metadata, query))
    fun entityHandle(metadata: CodeMetadata, frame: ExecutionContextFrame): ImmutableEntityHandle {
        useFrame(frame, metadata.scopeId, metadata.origin.callSite)
        require(metadata.executionContextId == frame.id) { "Entity metadata requires its execution frame" }
        return handle(ImmutableEntityHandle(metadata, frame.executor))
    }
    fun position(metadata: CodeMetadata, node: PositionNode): ImmutablePositionHandle = handle(ImmutablePositionHandle(metadata, node))
    fun rotation(metadata: CodeMetadata, node: RotationNode): ImmutableRotationHandle = handle(ImmutableRotationHandle(metadata, node))
    fun blockHandle(metadata: CodeMetadata, position: PositionHandle): ImmutableBlockHandle = handle(ImmutableBlockHandle(metadata, position))
    fun scoreRef(
        metadata: CodeMetadata,
        type: TypeRef<IntT>,
        target: MinecraftScoreTarget,
        objective: String
    ): ImmutableMinecraftScoreRef = handle(ImmutableMinecraftScoreRef(metadata, type, target, objective))
    fun freshAddress(): AddressId {
        checkOpen()
        return AddressId(sessionId, ++addressSequence).also { allocatedAddresses.add(it) }
    }

    /** Same-root child sessions share budget accounting, but never allocation sequences. */
    fun childArena(session: SessionId, scope: ScopeId): CodeArena {
        checkOpen()
        return CodeArena(session, scope, accountNodes, family, this)
    }

    fun metadata(scope: ScopeId, origin: Origin, executionContextId: ExecutionContextId? = null): CodeMetadata {
        requireScope(scope)
        return CodeMetadata(sessionId, scope, origin, executionContextId)
    }

    private fun own(metadata: CodeMetadata) {
        checkOpen(metadata.origin.callSite)
        require(metadata.sessionId == sessionId) { "Node belongs to another expansion session" }
        requireScope(metadata.scopeId)
    }

    private fun use(code: Code, scope: ScopeId, nestedBlock: Boolean = false) {
        val owned = code as OwnedCode
        own(owned.metadata)
        val visible = if (nestedBlock) isVisible(scope, owned.metadata.scopeId) else isVisible(owned.metadata.scopeId, scope)
        if (!visible) throw CodeModelException(code.source(), "Code is outside its lexical scope")
        if (code !in checkedDependencies) {
            if (!checkingDependencies.add(code)) throw CodeModelException(code.source(), "Cyclic code dependency")
            try {
                useProjectionFields(code, owned.metadata.scopeId)
                checkedDependencies.add(code)
            } finally {
                checkingDependencies.remove(code)
            }
        }
    }

    private fun use(location: NodeLocation, scope: ScopeId, source: SourceSpan = SourceSpan.unknown()) {
        require(location.root.session == sessionId) { "Location belongs to another expansion session" }
        location.capturedBase?.let { address ->
            val declaration = addressScopes[address]
                ?: throw CodeModelException(source, "Captured address has no declaration in this arena")
            if (!isVisible(declaration, scope)) throw CodeModelException(source, "Captured address is outside its lexical scope")
        }
        val symbols = listOf(location.root) + location.steps.mapNotNull {
            when (it) {
                is LocationStep.CapturedIndex -> it.symbol
                is LocationStep.CapturedKey -> it.symbol
                else -> null
            }
        }
        symbols.forEach { symbol ->
            symbolScopes[symbol]?.let { declaration ->
                if (!isVisible(declaration, scope)) throw CodeModelException(source, "Location is outside its lexical scope")
            }
        }
    }

    private fun useProjectionFields(code: Code, scope: ScopeId) {
        val source = code.source()
        if (code is Expr<*>) useType(code.type(), source)
        if (code is Ref<*>) useType(code.type(), source)
        if (code is Value<*>) useType(code.type(), source)
        if (code is EntitySet ||
            code is EntityHandle ||
            code is PositionHandle ||
            code is RotationHandle ||
            code is BlockHandle ||
            code is ImmutableMinecraftScoreRef
        ) {
            (code as OwnedCode).metadata.executionContextId?.let { useContext(it, scope, source) }
        }
        when (code) {
            is ImmutableExpr<*> -> when (val node = code.node) {
                is ExprNode.Read -> use(node.location, scope, source)
                is ExprNode.ScoreRead -> {
                    code.metadata.executionContextId?.let { useContext(it, scope, source) }
                    useScore(node.target, scope)
                }
                is ExprNode.BlockMatches -> {
                    code.metadata.executionContextId?.let { useContext(it, scope, source) }
                    use(node.block, scope)
                }
                is ExprNode.Literal -> useConstant(node.value, source)
                is ExprNode.Index -> {
                    use(node.receiver, scope)
                    use(node.index, scope)
                }
                is ExprNode.Unary -> use(node.operand, scope)
                is ExprNode.Binary -> {
                    use(node.left, scope)
                    use(node.right, scope)
                }
                is ExprNode.Convert -> {
                    use(node.value, scope)
                    useType(node.target, source)
                }
                is ExprNode.Field -> use(node.receiver, scope)
                is ExprNode.Call -> {
                    node.receiver?.let { use(it, scope) }
                    use(node.function, source)
                    useReadonly(node.readonlyArguments)
                    node.arguments.forEach { use(it.payload, scope) }
                }
                is ExprNode.MacroCall -> {
                    use(node.function, source)
                    node.arguments.forEach { use(it.payload, scope) }
                }
                is ExprNode.ExprBlock -> use(node.block, scope, true)
                is ExprNode.InlineCall -> {
                    use(node.callback, scope)
                    node.arguments.forEach { use(it.payload, scope) }
                }
            }
            is ImmutableRef<*> -> use(code.location, scope, source)
            is ImmutableLocal<*> -> use(code.location, scope, source)
            is ImmutableMinecraftScoreRef -> useScore(code.target, scope)
            is ImmutableEntitySet -> Unit
            is ImmutableEntityHandle -> {
                val context = requireNotNull(code.metadata.executionContextId)
                useContext(context, scope, source)
                if (contextFrames.getValue(context).executor != code.executor)
                    throw CodeModelException(source, "Entity executor differs from its execution frame")
            }
            is ImmutablePositionHandle -> usePosition(code.node, scope, source)
            is ImmutableRotationHandle -> useRotation(code.node, scope, source)
            is ImmutableBlockHandle -> use(code.position, scope)
            is ImmutableConst<*> -> {
                useConstant(code.value(), source)
                use(code.expr(), scope)
            }
            is ImmutableValue<*> -> use(code.expr(), scope)
            is ImmutableBlock -> code.statements().forEach { use(it, scope) }
            is ImmutableInlineFn<*> -> (code.target as? InlineTarget.Body)?.let { body ->
                use(body.block, scope, true)
                body.captureReferences.values.forEach { capture -> use(capture, scope) }
                body.deferredCaptures.values.forEach { capture -> use(capture, scope) }
            }
            is ImmutableStmt -> when (val node = code.node) {
                is StmtNode.ExecuteAs -> {
                    use(node.targets, scope)
                    useContextBody(code.metadata, node.parentContext, node.context, node.body)
                    use(node.self, (node.body as OwnedCode).metadata.scopeId)
                }
                is StmtNode.At -> {
                    use(node.position, scope)
                    useContextBody(code.metadata, node.parentContext, node.context, node.body)
                }
                is StmtNode.Rotated -> {
                    use(node.rotation, scope)
                    useContextBody(code.metadata, node.parentContext, node.context, node.body)
                }
                is StmtNode.CaptureAddress -> {
                    if (addressScopes[node.address] != code.metadata.scopeId)
                        throw CodeModelException(source, "Captured address has no declaration in this arena")
                    use(node.location, scope, source)
                    useType(node.type, source)
                }
                is StmtNode.LocalDecl -> {
                    use(node.local, scope)
                    use(node.initialValue, scope)
                }
                is StmtNode.Assign -> {
                    use(node.destination, scope)
                    use(node.expression, scope)
                }
                is StmtNode.Eval -> use(node.expression, scope)
                is StmtNode.If -> {
                    use(node.condition, scope)
                    use(node.thenBlock, scope, true)
                    node.elseBlock?.let { use(it, scope, true) }
                }
                is StmtNode.While -> {
                    use(node.condition, scope)
                    use(node.body, scope, true)
                }
                is StmtNode.ScopeBlock -> use(node.block, scope, true)
                is StmtNode.CallbackReturn -> node.value?.let { use(it, scope) }
                is StmtNode.Yield -> use(node.value, scope)
                is StmtNode.Break, is StmtNode.Continue -> Unit
            }
        }
    }

    private fun locationOf(reference: Ref<*>): NodeLocation = when (reference) {
        is ImmutableRef<*> -> reference.location
        is ImmutableLocal<*> -> reference.location
        else -> throw CodeModelException(
            reference.source(),
            "An inline capture must be an addressable local or reference"
        )
    }

    private fun use(function: FunctionRef<*>, source: SourceSpan = SourceSpan.unknown()) {
        val owner = (function as ImmutableFunctionRef<*>).sessionId
        require(owner == null || owner == sessionId) { "Function belongs to another expansion session" }
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<InlineSignature<*>, Boolean>())
        fun signature(value: InlineSignature<*>) {
            if (!seen.add(value)) return
            useType(value.returnType(), source)
            value.parameters().forEach { parameter ->
                useType(parameter.type(), source)
                parameter.inlineSignature()?.let(::signature)
            }
        }
        signature(function.signature)
        function.readonlyParameters().forEach { parameter ->
            useType(parameter.type(), source)
            parameter.inlineSignature()?.let(::signature)
        }
    }

    private fun use(payload: ArgumentPayload, scope: ScopeId) = use(payload.code, scope)

    fun <T> expr(metadata: CodeMetadata, type: TypeRef<T>, node: ExprNode): ImmutableExpr<T> {
        own(metadata)
        useType(type, metadata.origin.callSite)
        val scope = metadata.scopeId
        when (node) {
            is ExprNode.Literal -> useConstant(node.value, metadata.origin.callSite)
            is ExprNode.Read -> use(node.location, scope, metadata.origin.callSite)
            is ExprNode.ScoreRead -> {
                metadata.executionContextId?.let { useContext(it, scope, metadata.origin.callSite) }
                useScore(node.target, scope)
            }
            is ExprNode.BlockMatches -> {
                metadata.executionContextId?.let { useContext(it, scope, metadata.origin.callSite) }
                use(node.block, scope)
            }
            is ExprNode.Unary -> use(node.operand, scope)
            is ExprNode.Binary -> {
                use(node.left, scope)
                use(node.right, scope)
            }
            is ExprNode.Convert -> {
                use(node.value, scope)
                useType(node.target, metadata.origin.callSite)
            }
            is ExprNode.Field -> use(node.receiver, scope)
            is ExprNode.Index -> {
                use(node.receiver, scope)
                use(node.index, scope)
            }
            is ExprNode.Call -> {
                node.receiver?.let { use(it, scope) }
                useReadonly(node.readonlyArguments)
                use(node.function, metadata.origin.callSite)
                node.arguments.forEach { use(it.payload, scope) }
            }
            is ExprNode.MacroCall -> {
                use(node.function, metadata.origin.callSite)
                node.arguments.forEach { use(it.payload, scope) }
            }
            is ExprNode.ExprBlock -> {
                use(node.block, scope, true)
                require(node.region.session == sessionId)
            }
            is ExprNode.InlineCall -> {
                use(node.callback, scope)
                node.arguments.forEach { use(it.payload, scope) }
            }
        }
        accountNodes(1, metadata.origin)
        return ImmutableExpr(metadata, type, node)
    }

    fun stmt(metadata: CodeMetadata, node: StmtNode): ImmutableStmt {
        own(metadata)
        val scope = metadata.scopeId
        when (node) {
            is StmtNode.ExecuteAs -> {
                use(node.targets, scope)
                useContextBody(metadata, node.parentContext, node.context, node.body)
                use(node.self, (node.body as OwnedCode).metadata.scopeId)
            }
            is StmtNode.At -> {
                use(node.position, scope)
                useContextBody(metadata, node.parentContext, node.context, node.body)
            }
            is StmtNode.Rotated -> {
                use(node.rotation, scope)
                useContextBody(metadata, node.parentContext, node.context, node.body)
            }
            is StmtNode.CaptureAddress -> {
                if (node.address.session != sessionId || node.address !in allocatedAddresses)
                    throw CodeModelException(metadata.origin.callSite, "Captured address was not allocated by this arena")
                if (node.address in addressScopes) throw CodeModelException(metadata.origin.callSite, "Duplicate captured address declaration")
                use(node.location, scope, metadata.origin.callSite)
                useType(node.type, metadata.origin.callSite)
            }
            is StmtNode.LocalDecl -> {
                use(node.local, scope)
                use(node.initialValue, scope)
            }
            is StmtNode.Assign -> {
                use(node.destination, scope)
                use(node.expression, scope)
            }
            is StmtNode.Eval -> use(node.expression, scope)
            is StmtNode.If -> {
                use(node.condition, scope)
                use(node.thenBlock, scope, true)
                node.elseBlock?.let { use(it, scope, true) }
            }
            is StmtNode.While -> {
                use(node.condition, scope)
                use(node.body, scope, true)
                require(node.region.session == sessionId)
            }
            is StmtNode.ScopeBlock -> use(node.block, scope, true)
            is StmtNode.CallbackReturn -> {
                require(node.region.session == sessionId)
                node.value?.let { use(it, scope) }
            }
            is StmtNode.Yield -> {
                require(node.region.session == sessionId)
                use(node.value, scope)
            }
            is StmtNode.Break -> require(node.region.session == sessionId)
            is StmtNode.Continue -> require(node.region.session == sessionId)
        }
        accountNodes(1, metadata.origin)
        if (node is StmtNode.CaptureAddress) addressScopes[node.address] = scope
        return ImmutableStmt(metadata, node)
    }

    fun block(metadata: CodeMetadata, statements: List<Stmt>): ImmutableBlock {
        own(metadata)
        statements.forEach {
            use(it, metadata.scopeId)
            require((it as OwnedCode).metadata.scopeId == metadata.scopeId) { "Statement requires its own lexical block" }
        }
        accountNodes(1, metadata.origin)
        return ImmutableBlock(metadata, statements)
    }

    fun <T> local(metadata: CodeMetadata, type: TypeRef<T>, hint: String, symbol: SymbolId = freshSymbol()): ImmutableLocal<T> {
        own(metadata)
        useType(type, metadata.origin.callSite)
        require(symbol.session == sessionId) { "Local belongs to another expansion session" }
        require(symbol !in symbolScopes) { "Symbol already has a storage binding" }
        accountNodes(1, metadata.origin) // ImmutableLocal creates one cached Read.
        symbolScopes[symbol] = metadata.scopeId
        return ImmutableLocal(metadata, type, symbol, hint)
    }

    fun <T> ref(metadata: CodeMetadata, type: TypeRef<T>, location: NodeLocation): ImmutableRef<T> {
        own(metadata)
        useType(type, metadata.origin.callSite)
        use(location, metadata.scopeId, metadata.origin.callSite)
        accountNodes(1, metadata.origin) // ImmutableRef creates one cached Read.
        if (location.capturedBase == null) symbolScopes.putIfAbsent(location.root, metadata.scopeId)
        return ImmutableRef(metadata, type, location)
    }

    fun <T> value(expression: Expr<T>): ImmutableValue<T> {
        own((expression as OwnedCode).metadata)
        return ImmutableValue(expression)
    }

    fun <T> constant(expression: Expr<T>, constant: ConstantValue): ImmutableConst<T> {
        own((expression as OwnedCode).metadata)
        useConstant(constant, expression.source())
        return ImmutableConst(expression, constant)
    }

    fun <S> inline(metadata: CodeMetadata, signature: InlineSignature<*>, id: CallbackId, target: InlineTarget): ImmutableInlineFn<S> {
        own(metadata)
        if (target is InlineTarget.Body) {
            use(target.block, metadata.scopeId, true)
            require(target.returnRegion.session == sessionId)
            require((target.parameters + target.captures).all { it.session == sessionId })
            require(target.parameters.size == signature.parameters().size && target.parameters.distinct().size == target.parameters.size) {
                "Callback parameters do not match its signature"
            }
            require(target.parameters.none { it in target.captures }) { "Callback parameter cannot also be a capture" }
            target.captures.forEach { use(NodeLocation(it), metadata.scopeId) }
            if (target.captureReferences.isNotEmpty()) {
                require(target.captureReferences.keys == target.captures) {
                    "Inline capture capability does not describe every declared capture"
                }
                target.captureReferences.forEach { (root, reference) ->
                    val owned = reference as? OwnedCode
                        ?: throw CodeModelException(reference.source(), "Inline capture has no expansion-session owner")
                    require(owned.metadata.sessionId == sessionId) {
                        "Inline capture belongs to another expansion session"
                    }
                    require(locationOf(reference).root == root) {
                        "Inline capture metadata root differs from its declared capture"
                    }
                    use(reference, metadata.scopeId)
                }
            }
            if (target.deferredCaptures.isNotEmpty()) {
                require(target.deferred != null) { "Inline deferred captures require a deferred body" }
                target.deferredCaptures.forEach { (_, reference) ->
                    val owned = reference as? OwnedCode
                        ?: throw CodeModelException(reference.source(), "Inline deferred capture has no expansion-session owner")
                    require(owned.metadata.sessionId == sessionId) {
                        "Inline deferred capture belongs to another expansion session"
                    }
                    require(locationOf(reference).root in target.captures) {
                        "Inline deferred capture is not declared by the preview body"
                    }
                    use(reference, metadata.scopeId)
                }
            }
            val bodyScope = (target.block as OwnedCode).metadata.scopeId
            target.parameters.forEach { parameter ->
                require(symbolScopes[parameter] == null || symbolScopes[parameter] == bodyScope) { "Callback parameter belongs to another scope" }
            }
            target.parameters.forEach { symbolScopes.putIfAbsent(it, bodyScope) }
        }
        return ImmutableInlineFn(metadata, signature, id, target)
    }

    /** Each invocation clones internal bindings once; free bindings retain their original handles. */
    @Suppress("UNCHECKED_CAST")
    fun <C : Code> importTree(
        code: C,
        targetScope: ScopeId,
        origin: Origin? = null,
        externalBindings: Map<SymbolId, Ref<*>> = emptyMap(),
        contextBindings: Map<ExecutionContextId, ExecutionContextFrame> = emptyMap()
    ): C {
        requireScope(targetScope)
        if ((code as OwnedCode).metadata.sessionId != sessionId)
            throw CodeModelException(code.source(), "Cross-session code requires an explicit child-tree import")
        return importFrom(
            this,
            code,
            targetScope,
            origin,
            externalBindings,
            contextBindings,
            allowCapturedExternalBindings = false
        )
    }

    /** The compiler exports a live same-root session, explicitly binding ambient frames and free storage independently. */
    fun <C : Code> importChildTree(
        sourceArena: CodeArena,
        code: C,
        targetScope: ScopeId,
        origin: Origin? = null,
        externalBindings: Map<SymbolId, Ref<*>> = emptyMap(),
        contextBindings: Map<ExecutionContextId, ExecutionContextFrame> = emptyMap()
    ): C = importOwnedChildTree(
        sourceArena,
        code,
        targetScope,
        origin,
        externalBindings,
        contextBindings,
        allowCapturedExternalBindings = false
    )

    /**
     * Explicit macro-child export. This is the only import path that may carry an external binding
     * whose reference already owns a captured address; only [ExpansionSession.importChildResult]
     * calls it, after proving the source is an adopted direct child. Same-family and source-arena
     * ownership are still checked here, and every alias then passes the normal use/location/address
     * validation in [Import].
     */
    internal fun <C : Code> importMacroChildTree(
        sourceArena: CodeArena,
        code: C,
        targetScope: ScopeId,
        origin: Origin? = null,
        externalBindings: Map<SymbolId, Ref<*>> = emptyMap(),
        contextBindings: Map<ExecutionContextId, ExecutionContextFrame> = emptyMap()
    ): C = importOwnedChildTree(
        sourceArena,
        code,
        targetScope,
        origin,
        externalBindings,
        contextBindings,
        allowCapturedExternalBindings = true
    )

    private fun <C : Code> importOwnedChildTree(
        sourceArena: CodeArena,
        code: C,
        targetScope: ScopeId,
        origin: Origin?,
        externalBindings: Map<SymbolId, Ref<*>>,
        contextBindings: Map<ExecutionContextId, ExecutionContextFrame>,
        allowCapturedExternalBindings: Boolean
    ): C {
        requireScope(targetScope)
        if (sourceArena.family !== family)
            throw CodeModelException(code.source(), "Cannot import code from another root session")
        require((code as OwnedCode).metadata.sessionId == sourceArena.sessionId) { "Source arena does not own this tree" }
        return importFrom(
            sourceArena,
            code,
            targetScope,
            origin,
            externalBindings,
            contextBindings,
            allowCapturedExternalBindings
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <C : Code> importFrom(
        sourceArena: CodeArena,
        code: C,
        targetScope: ScopeId,
        origin: Origin?,
        externalBindings: Map<SymbolId, Ref<*>>,
        contextBindings: Map<ExecutionContextId, ExecutionContextFrame>,
        allowCapturedExternalBindings: Boolean
    ): C {
        sourceArena.checkOpen(code.source())
        require(externalBindings.keys.all { it.session == sourceArena.sessionId }) { "Binding key belongs to another session" }
        if (!allowCapturedExternalBindings) {
            externalBindings.values.forEach { binding ->
                val captured = (binding is ImmutableRef<*> && binding.location.capturedBase != null) ||
                    (binding is ImmutableLocal<*> && binding.location.capturedBase != null)
                if (captured)
                    throw CodeModelException(binding.source(), "External binding cannot export a captured address")
            }
        }
        // An external binding may already denote an address frozen by an enclosing expansion. It is
        // an alias capability, not a declaration: use() proves the address is declared and visible
        // here, and location() preserves that address (and its relative steps) instead of recapturing.
        externalBindings.values.forEach { use(it, targetScope) }
        return Import(sourceArena, (code as OwnedCode).metadata.scopeId, targetScope, origin, externalBindings, contextBindings).copy(code) as C
    }

    private inner class Import(
        val source: CodeArena,
        val sourceScope: ScopeId,
        val targetScope: ScopeId,
        val origin: Origin?,
        val external: Map<SymbolId, Ref<*>>,
        val externalContexts: Map<ExecutionContextId, ExecutionContextFrame>
    ) {
        val copied = IdentityHashMap<Code, Code>()
        val readOwners = IdentityHashMap<Code, Ref<*>>()
        val symbols = linkedMapOf<SymbolId, SymbolId>()
        val regions = linkedMapOf<RegionId, RegionId>()
        val addresses = linkedMapOf<AddressId, AddressId>()
        val addressUses = linkedMapOf<AddressId, SourceSpan>()
        val mappedScopes = linkedMapOf(sourceScope to targetScope)
        val contexts = linkedMapOf<ExecutionContextId, ExecutionContextId>()
        val executors = linkedMapOf<ExecutionContextId, ExecutionContextId>()
        val positions = linkedMapOf<ExecutionContextId, ExecutionContextId>()
        val rotations = linkedMapOf<ExecutionContextId, ExecutionContextId>()
        val dimensions = linkedMapOf<ExecutionContextId, ExecutionContextId>()
        val scanned = java.util.Collections.newSetFromMap(IdentityHashMap<Code, Boolean>())
        val addressDependencies = IdentityHashMap<Code, Boolean>()
        var prepared = false

        init {
            externalContexts.forEach { (old, target) ->
                val original = source.contextFrames[old]
                    ?: throw CodeModelException(origin?.callSite ?: SourceSpan.unknown(), "Context binding has no source frame")
                source.useFrame(original, sourceScope, origin?.callSite ?: SourceSpan.unknown())
                useFrame(target, targetScope, origin?.callSite ?: SourceSpan.unknown())
                bindComponent(contexts, original.id, target.id)
                bindComponent(executors, original.id, target.executor)
                bindComponent(executors, original.executor, target.executor)
                bindComponent(positions, original.id, target.position)
                bindComponent(positions, original.position, target.position)
                bindComponent(rotations, original.id, target.rotation)
                bindComponent(rotations, original.rotation, target.rotation)
                bindComponent(dimensions, original.id, target.dimension)
                bindComponent(dimensions, original.dimension, target.dimension)
            }
        }

        fun bindComponent(bindings: MutableMap<ExecutionContextId, ExecutionContextId>, old: ExecutionContextId, target: ExecutionContextId) {
            val previous = bindings[old]
            require(previous == null || previous == target) { "Context binding changes an already bound component" }
            bindings[old] = target
        }

        fun component(old: ExecutionContextId, bindings: Map<ExecutionContextId, ExecutionContextId>): ExecutionContextId = bindings[old] ?: old.also { require(it.session == sessionId) { "Free execution component requires an explicit target frame" } }

        fun frame(old: ExecutionContextFrame): ExecutionContextFrame = ExecutionContextFrame(
            requireNotNull(context(old.id)),
            component(old.executor, executors),
            component(old.position, positions),
            component(old.rotation, rotations),
            component(old.dimension, dimensions)
        )

        fun prepareContext(parent: ExecutionContextFrame, child: ExecutionContextFrame, body: Block) {
            if (child.id in contexts) throw CodeModelException(body.source(), "Duplicate or externally replaced context declaration")
            // Parent components are captured before allocating the newly established component.
            val mappedParent = frame(parent)
            val next = freshExecutionContext()
            contexts[child.id] = next
            executors[child.id] = if (child.executor == child.id) next else mappedParent.executor
            positions[child.id] = if (child.position == child.id) next else mappedParent.position
            rotations[child.id] = if (child.rotation == child.id) next else mappedParent.rotation
            dimensions[child.id] = if (child.dimension == child.id) next else mappedParent.dimension
            registerContext(frame(child), scope((body as OwnedCode).metadata.scopeId))
        }

        /** A subtree declaring addresses or execution contexts cannot be shared as an ordinary free value. */
        fun hasAddress(code: Code): Boolean = addressDependencies[code] ?: when (code) {
            is ImmutableExpr<*> -> when (val n = code.node) {
                is ExprNode.Read -> n.location.capturedBase != null
                is ExprNode.ScoreRead -> (n.target as? MinecraftScoreTarget.Entity)?.let { hasAddress(it.entity) } == true
                is ExprNode.BlockMatches -> hasAddress(n.block)
                is ExprNode.Literal -> false
                is ExprNode.Unary -> hasAddress(n.operand)
                is ExprNode.Binary -> hasAddress(n.left) || hasAddress(n.right)
                is ExprNode.Convert -> hasAddress(n.value)
                is ExprNode.Field -> hasAddress(n.receiver)
                is ExprNode.Index -> hasAddress(n.receiver) || hasAddress(n.index)
                is ExprNode.Call -> n.receiver?.let(::hasAddress) == true || n.arguments.any { hasAddress(it.payload.code) }
                is ExprNode.MacroCall -> n.arguments.any { hasAddress(it.payload.code) }
                is ExprNode.ExprBlock -> hasAddress(n.block)
                is ExprNode.InlineCall -> hasAddress(n.callback) || n.arguments.any { hasAddress(it.payload.code) }
            }
            is ImmutableStmt -> when (val n = code.node) {
                is StmtNode.CaptureAddress -> true
                is StmtNode.ExecuteAs, is StmtNode.At, is StmtNode.Rotated -> true
                is StmtNode.LocalDecl -> hasAddress(n.initialValue)
                is StmtNode.Assign -> hasAddress(n.destination) || hasAddress(n.expression)
                is StmtNode.Eval -> hasAddress(n.expression)
                is StmtNode.If -> hasAddress(n.condition) || hasAddress(n.thenBlock) || n.elseBlock?.let(::hasAddress) == true
                is StmtNode.While -> hasAddress(n.condition) || hasAddress(n.body)
                is StmtNode.ScopeBlock -> hasAddress(n.block)
                is StmtNode.CallbackReturn -> n.value?.let(::hasAddress) == true
                is StmtNode.Yield -> hasAddress(n.value)
                is StmtNode.Break, is StmtNode.Continue -> false
            }
            is ImmutableBlock -> code.statements().any(::hasAddress)
            is ImmutableValue<*> -> hasAddress(code.expr())
            is ImmutableConst<*> -> hasAddress(code.expr())
            is ImmutableRef<*> -> code.location.capturedBase != null
            is ImmutableLocal<*> -> false
            is ImmutableMinecraftScoreRef -> (code.target as? MinecraftScoreTarget.Entity)?.let { hasAddress(it.entity) } == true
            is ImmutableEntitySet, is ImmutableEntityHandle -> false
            is ImmutablePositionHandle -> when (val n = code.node) {
                is PositionNode.Absolute, is PositionNode.Current -> false
                is PositionNode.Entity -> hasAddress(n.entity)
                is PositionNode.Offset -> hasAddress(n.base)
                is PositionNode.LocalOffset -> hasAddress(n.base) || hasAddress(n.rotation)
            }
            is ImmutableRotationHandle -> (code.node as? RotationNode.Entity)?.let { hasAddress(it.entity) } == true
            is ImmutableBlockHandle -> hasAddress(code.position)
            is ImmutableInlineFn<*> -> (code.target as? InlineTarget.Body)?.let { body ->
                hasAddress(body.block) ||
                    body.captureReferences.values.any { hasAddress(it) } ||
                    body.deferredCaptures.values.any { hasAddress(it) }
            } == true
        }.also { addressDependencies[code] = it }

        fun externalHandle(code: Code): Boolean {
            val owner = (code as OwnedCode).metadata
            if ((
                    code is EntitySet ||
                        code is EntityHandle ||
                        code is PositionHandle ||
                        code is RotationHandle ||
                        code is BlockHandle ||
                        code is ImmutableMinecraftScoreRef
                    ) &&
                code !in allocatedHandles
            ) return false
            return source === this@CodeArena &&
                external.isEmpty() &&
                externalContexts.isEmpty() &&
                (
                    code is Expr<*> ||
                        code is MacroInput ||
                        code is EntitySet ||
                        code is EntityHandle ||
                        code is PositionHandle ||
                        code is RotationHandle ||
                        code is BlockHandle
                    ) &&
                !source.isVisible(sourceScope, owner.scopeId) &&
                isVisible(owner.scopeId, targetScope) &&
                !hasAddress(code)
        }

        fun prepareSymbolBinding(symbol: SymbolId, span: SourceSpan) {
            when (val reference = external[symbol]) {
                is ImmutableMinecraftScoreRef -> throw CodeModelException(
                    span,
                    "A score reference cannot replace a captured index or key symbol"
                )
                is ImmutableRef<*> -> require(
                    reference.location.steps.isEmpty() &&
                        reference.location.executionContextId == null &&
                        reference.location.capturedBase == null
                ) {
                    "Captured binding requires a root reference without an execution context"
                }
                is ImmutableLocal<*>, null -> Unit
            }
        }

        fun prepareLocation(location: NodeLocation, code: OwnedCode) {
            source.use(location, code.metadata.scopeId, code.source())
            location.steps.forEach { step ->
                when (step) {
                    is LocationStep.CapturedIndex -> prepareSymbolBinding(step.symbol, code.source())
                    is LocationStep.CapturedKey -> prepareSymbolBinding(step.symbol, code.source())
                    else -> Unit
                }
            }
            val type = when (code) {
                is Ref<*> -> code.type()
                is Expr<*> -> code.type()
                else -> null
            }
            scoreBinding(location, code.source(), type, code is Ref<*>)?.let {
                if (code is ImmutableStmt && code.node is StmtNode.CaptureAddress)
                    throw CodeModelException(code.source(), "A score reference cannot replace a captured symbol address")
            }
            location.capturedBase?.let { addressUses.putIfAbsent(it, code.source()) }
        }

        /** A whole REF parameter can retain the actual score address; symbol paths cannot represent that address. */
        fun scoreBinding(location: NodeLocation, span: SourceSpan, type: TypeRef<*>?, reference: Boolean = false): ImmutableMinecraftScoreRef? {
            val score = external[location.root] as? ImmutableMinecraftScoreRef ?: return null
            if (location.steps.isNotEmpty() || location.capturedBase != null)
                throw CodeModelException(span, "A score reference binding requires a root location without suffix or captured address")
            if (reference && !location.writable)
                throw CodeModelException(span, "A score reference binding cannot preserve a read-only reference capability")
            if (type != null && !type.sameType(score.type()))
                throw CodeModelException(span, "External score reference binding has another type")
            return score
        }

        fun prepare(code: Code) {
            if (!scanned.add(code)) return
            val owner = (code as OwnedCode).metadata
            val arena = family.arenas[owner.sessionId] ?: throw CodeModelException(code.source(), "Foreign root session")
            arena.checkOpen(code.source())
            arena.useProjectionFields(code, owner.scopeId)
            if (externalHandle(code)) return
            when (code) {
                is ImmutableExpr<*> -> when (val n = code.node) {
                    is ExprNode.Unary -> prepare(n.operand)
                    is ExprNode.Binary -> {
                        prepare(n.left)
                        prepare(n.right)
                    }
                    is ExprNode.Convert -> prepare(n.value)
                    is ExprNode.Field -> prepare(n.receiver)
                    is ExprNode.Index -> {
                        prepare(n.receiver)
                        prepare(n.index)
                    }
                    is ExprNode.Call -> {
                        n.receiver?.let(::prepare)
                        n.arguments.forEach { prepare(it.payload.code) }
                    }
                    is ExprNode.MacroCall -> n.arguments.forEach { prepare(it.payload.code) }
                    is ExprNode.ExprBlock -> {
                        regions.getOrPut(n.region, ::freshRegion)
                        prepare(n.block)
                    }
                    is ExprNode.InlineCall -> {
                        prepare(n.callback)
                        n.arguments.forEach { prepare(it.payload.code) }
                    }
                    is ExprNode.Read -> prepareLocation(n.location, code)
                    is ExprNode.ScoreRead -> (n.target as? MinecraftScoreTarget.Entity)?.let { prepare(it.entity) }
                    is ExprNode.BlockMatches -> prepare(n.block)
                    is ExprNode.Literal -> Unit
                }
                is ImmutableStmt -> when (val n = code.node) {
                    is StmtNode.ExecuteAs -> {
                        prepareContext(n.parentContext, n.context, n.body)
                        prepare(n.targets)
                        prepare(n.self)
                        prepare(n.body)
                    }
                    is StmtNode.At -> {
                        prepareContext(n.parentContext, n.context, n.body)
                        prepare(n.position)
                        prepare(n.body)
                    }
                    is StmtNode.Rotated -> {
                        prepareContext(n.parentContext, n.context, n.body)
                        prepare(n.rotation)
                        prepare(n.body)
                    }
                    is StmtNode.CaptureAddress -> {
                        if (n.address.session != source.sessionId || n.address in addresses)
                            throw CodeModelException(code.source(), "Duplicate or foreign captured address declaration")
                        addresses[n.address] = freshAddress()
                        prepareLocation(n.location, code)
                    }
                    is StmtNode.LocalDecl -> {
                        val symbol = (n.local as ImmutableLocal<*>).symbol
                        require(symbol !in symbols) { "Duplicate local declaration" }
                        symbols[symbol] = freshSymbol()
                        prepare(n.local)
                        prepare(n.initialValue)
                    }
                    is StmtNode.Assign -> {
                        prepare(n.destination)
                        prepare(n.expression)
                    }
                    is StmtNode.Eval -> prepare(n.expression)
                    is StmtNode.If -> {
                        prepare(n.condition)
                        prepare(n.thenBlock)
                        n.elseBlock?.let(::prepare)
                    }
                    is StmtNode.While -> {
                        regions.getOrPut(n.region, ::freshRegion)
                        prepare(n.condition)
                        prepare(n.body)
                    }
                    is StmtNode.ScopeBlock -> prepare(n.block)
                    is StmtNode.CallbackReturn -> n.value?.let(::prepare)
                    is StmtNode.Yield -> prepare(n.value)
                    is StmtNode.Break, is StmtNode.Continue -> Unit
                }
                is ImmutableBlock -> code.statements().forEach(::prepare)
                is ImmutableValue<*> -> prepare(code.expr())
                is ImmutableConst<*> -> prepare(code.expr())
                is ImmutableInlineFn<*> -> (code.target as? InlineTarget.Body)?.let { body ->
                    body.parameters.forEach { symbols.getOrPut(it, ::freshSymbol) }
                    body.captures.forEach { prepareSymbolBinding(it, code.source()) }
                    body.captureReferences.values.forEach { capture -> prepare(capture) }
                    body.deferredCaptures.values.forEach { capture -> prepare(capture) }
                    regions.getOrPut(body.returnRegion, ::freshRegion)
                    prepare(body.block)
                }
                is ImmutableLocal<*> -> {
                    prepareLocation(code.location, code)
                    readOwners[code.load()] = code
                }
                is ImmutableRef<*> -> {
                    prepareLocation(code.location, code)
                    readOwners[code.load()] = code
                }
                is ImmutableMinecraftScoreRef -> {
                    (code.target as? MinecraftScoreTarget.Entity)?.let { prepare(it.entity) }
                    readOwners[code.load()] = code
                }
                is ImmutableEntitySet, is ImmutableEntityHandle -> Unit
                is ImmutablePositionHandle -> when (val n = code.node) {
                    is PositionNode.Absolute, is PositionNode.Current -> Unit
                    is PositionNode.Entity -> prepare(n.entity)
                    is PositionNode.Offset -> prepare(n.base)
                    is PositionNode.LocalOffset -> {
                        prepare(n.base)
                        prepare(n.rotation)
                    }
                }
                is ImmutableRotationHandle -> (code.node as? RotationNode.Entity)?.let { prepare(it.entity) }
                is ImmutableBlockHandle -> prepare(code.position)
            }
        }

        fun scope(old: ScopeId): ScopeId = mappedScopes.getOrPut(old) {
            require(old.session == source.sessionId && source.isVisible(sourceScope, old)) { "Scope escapes imported tree" }
            childScope(scope(requireNotNull(source.scopeParent(old))))
        }

        fun context(old: ExecutionContextId?): ExecutionContextId? = old?.let {
            contexts[it] ?: it.also {
                require(it.session == sessionId) { "Free execution context requires an explicit target frame" }
            }
        }

        fun meta(code: OwnedCode): CodeMetadata {
            val oldScope = code.metadata.scopeId
            val destination = if (source.isVisible(sourceScope, oldScope)) scope(oldScope) else {
                require(
                    code is Expr<*> ||
                        code is MacroInput ||
                        code is EntitySet ||
                        code is EntityHandle ||
                        code is PositionHandle ||
                        code is RotationHandle ||
                        code is BlockHandle
                ) { "Scope escapes imported tree" }
                targetScope
            }
            return metadata(destination, origin ?: code.metadata.origin, context(code.metadata.executionContextId))
        }

        fun referenceScope(code: OwnedCode): ScopeId = if (source.isVisible(sourceScope, code.metadata.scopeId))
            scope(code.metadata.scopeId) else targetScope

        fun sameLocation(first: NodeLocation, second: NodeLocation): Boolean = first == second && first.writable == second.writable

        fun symbol(old: SymbolId): SymbolId = symbols[old] ?: external[old]?.let { reference ->
            when (reference) {
                is ImmutableRef<*> -> {
                    require(reference.location.steps.isEmpty() && reference.location.executionContextId == null && reference.location.capturedBase == null) {
                        "Captured binding requires a root reference without an execution context"
                    }
                    reference.location.root
                }
                is ImmutableLocal<*> -> reference.symbol
                is ImmutableMinecraftScoreRef -> throw CodeModelException(reference.source(), "A score reference cannot replace a captured index or key symbol")
            }
        } ?: old.also { require(it.session == sessionId) { "Free binding requires an explicit target reference" } }

        fun region(old: RegionId): RegionId = regions[old] ?: old.also {
            require(it.session == sessionId) { "Control region escapes imported tree" }
        }

        fun address(old: AddressId, span: SourceSpan): AddressId = addresses[old]
            ?: throw CodeModelException(span, "Captured address declaration is outside the imported tree")

        fun location(old: NodeLocation, span: SourceSpan): NodeLocation {
            val binding = external[old.root]
            val base = when (binding) {
                is ImmutableRef<*> -> binding.location
                is ImmutableLocal<*> -> binding.location
                is ImmutableMinecraftScoreRef -> throw CodeModelException(span, "A score reference cannot be represented by a symbol location")
                null -> null
            }
            if (base != null && old.executionContextId != null && old.executionContextId !in externalContexts)
                require(base.executionContextId == context(old.executionContextId)) { "External binding changes execution context" }
            val steps = old.steps.map {
                when (it) {
                    is LocationStep.CapturedIndex -> LocationStep.CapturedIndex(symbol(it.symbol))
                    is LocationStep.CapturedKey -> LocationStep.CapturedKey(symbol(it.symbol))
                    else -> it
                }
            }
            val executionContext = when {
                base != null -> base.executionContextId
                old.executionContextId != null && old.executionContextId in contexts && old.executionContextId !in externalContexts -> context(old.executionContextId)
                old.root !in symbols && old.root.session == sessionId -> old.executionContextId
                else -> context(old.executionContextId)
            }
            // A free alias may stand for an already captured physical location. Preserve that
            // frozen base instead of re-deriving it; relative steps compose on top of it.
            val capturedBase = old.capturedBase?.let { address(it, span) } ?: base?.capturedBase
            return NodeLocation(
                base?.root ?: symbol(old.root),
                (if (old.capturedBase == null) base?.steps.orEmpty() else emptyList()) + steps,
                executionContext,
                old.writable && (base?.writable ?: true),
                capturedBase
            )
        }

        fun argument(old: ArgumentPayload): ArgumentPayload = when (old) {
            is ArgumentPayload.Value -> ArgumentPayload.Value(copy(old.expression) as Expr<*>)
            is ArgumentPayload.Reference -> ArgumentPayload.Reference(copy(old.reference) as Ref<*>)
            is ArgumentPayload.Constant -> ArgumentPayload.Constant(copy(old.constant) as Const<*>)
            is ArgumentPayload.Inline -> ArgumentPayload.Inline(copy(old.callback) as InlineFn<*>)
        }

        fun scoreTarget(old: MinecraftScoreTarget): MinecraftScoreTarget = when (old) {
            is MinecraftScoreTarget.Fixed -> old
            is MinecraftScoreTarget.Entity -> MinecraftScoreTarget.Entity(copy(old.entity) as EntityHandle)
        }

        @Suppress("UNCHECKED_CAST")
        fun copy(code: Code): Code {
            if (!prepared) {
                prepare(code)
                require(symbols.keys.none { it in external }) { "Internal declaration cannot be replaced by an external binding" }
                addressUses.forEach { (old, span) -> address(old, span) }
                prepared = true
            }
            copied[code]?.let { return it }
            if (externalHandle(code)) {
                use(code, targetScope)
                copied[code] = code
                return code
            }
            readOwners[code]?.let { owner ->
                copy(owner)
                return requireNotNull(copied[code])
            }
            // Immutable free handles denote captured addresses/components, not new declarations.
            if ((code is EntitySet || code is EntityHandle || code is PositionHandle || code is RotationHandle || code is BlockHandle) &&
                code in allocatedHandles &&
                source === this@CodeArena
            ) {
                val owner = code as OwnedCode
                if (isVisible(owner.metadata.scopeId, referenceScope(owner)) &&
                    context(owner.metadata.executionContextId) == owner.metadata.executionContextId
                ) {
                    copied[code] = code
                    return code
                }
            }
            val result: Code = when (code) {
                is ImmutableLocal<*> -> {
                    val mapped = symbols[code.symbol]
                    if (mapped == null) freeRef(code, code.location) else
                        local(meta(code), code.type(), code.hint(), mapped).also { copied[code.load()] = it.load() }
                }
                is ImmutableRef<*> -> freeRef(code, code.location)
                is ImmutableMinecraftScoreRef -> {
                    val target = scoreTarget(code.target)
                    val mapped = meta(code)
                    val unchangedTarget = target === code.target ||
                        (
                            target is MinecraftScoreTarget.Entity &&
                                code.target is MinecraftScoreTarget.Entity &&
                                target.entity === code.target.entity
                            )
                    val result = if (code in allocatedHandles &&
                        unchangedTarget &&
                        code.metadata.sessionId == sessionId &&
                        mapped.executionContextId == code.metadata.executionContextId &&
                        isVisible(code.metadata.scopeId, mapped.scopeId)
                    ) code
                    else scoreRef(mapped, code.type(), target, code.objective)
                    copied[code.load()] = result.load()
                    result
                }
                is ImmutableEntitySet -> entitySet(meta(code), code.query)
                is ImmutableEntityHandle -> handle(ImmutableEntityHandle(meta(code), component(code.executor, executors)))
                is ImmutablePositionHandle -> position(
                    meta(code),
                    when (val n = code.node) {
                        is PositionNode.Absolute -> n
                        is PositionNode.Current -> PositionNode.Current(component(n.context, positions))
                        is PositionNode.Entity -> PositionNode.Entity(copy(n.entity) as EntityHandle)
                        is PositionNode.Offset -> PositionNode.Offset(copy(n.base) as PositionHandle, n.x, n.y, n.z)
                        is PositionNode.LocalOffset -> PositionNode.LocalOffset(
                            copy(n.base) as PositionHandle,
                            copy(n.rotation) as RotationHandle,
                            n.left,
                            n.up,
                            n.forward
                        )
                    }
                )
                is ImmutableRotationHandle -> rotation(
                    meta(code),
                    when (val n = code.node) {
                        is RotationNode.Absolute -> n
                        is RotationNode.Current -> RotationNode.Current(component(n.context, rotations))
                        is RotationNode.Entity -> RotationNode.Entity(copy(n.entity) as EntityHandle)
                    }
                )
                is ImmutableBlockHandle -> blockHandle(meta(code), copy(code.position) as PositionHandle)
                is ImmutableValue<*> -> {
                    val expression = copy(code.expr()) as Expr<Any?>
                    if (expression === code.expr()) code else value(expression)
                }
                is ImmutableConst<*> -> constant(copy(code.expr()) as Expr<Any?>, code.value())
                is ImmutableInlineFn<*> -> {
                    val target = when (val body = code.target) {
                        is InlineTarget.Declared -> body
                        is InlineTarget.Body -> {
                            fun copyCapture(reference: Ref<*>): Ref<*> {
                                val location = this@CodeArena.locationOf(reference)
                                val externalBinding = external[location.root]
                                return when (externalBinding) {
                                    is ImmutableRef<*>, is ImmutableLocal<*> -> {
                                        // A cross-session alias stands for the caller's whole
                                        // capability. Keep the child metadata at its root only;
                                        // the parent alias map reapplies steps/context/address on
                                        // export, so they must not be duplicated into child paths.
                                        val base = this@CodeArena.locationOf(externalBinding)
                                        ref(
                                            meta(code),
                                            reference.type(),
                                            NodeLocation(base.root, writable = location.writable && base.writable)
                                        )
                                    }
                                    is ImmutableMinecraftScoreRef -> throw CodeModelException(
                                        reference.source(),
                                        "A score capture cannot be represented by a symbol alias"
                                    )
                                    null -> copy(reference) as Ref<*>
                                }
                            }
                            val copiedReferences = LinkedHashMap<SymbolId, Ref<*>>()
                            body.captureReferences.forEach { (_, reference) ->
                                val copiedReference = copyCapture(reference)
                                copiedReferences[this@CodeArena.locationOf(copiedReference).root] = copiedReference
                            }
                            val copiedDeferred = LinkedHashMap<SymbolId, Ref<*>>()
                            body.deferredCaptures.forEach { (symbol, reference) ->
                                // Keys are original source identities; only the capability value is remapped.
                                copiedDeferred[symbol] = copyCapture(reference)
                            }
                            val copiedCaptures = body.captures.map(::symbol).toSet()
                            if (copiedReferences.isNotEmpty() || copiedDeferred.isNotEmpty())
                                require(
                                    copiedReferences.keys == copiedCaptures &&
                                        copiedDeferred.values.all { this@CodeArena.locationOf(it).root in copiedCaptures }
                                ) {
                                    "Imported inline capture capability differs from its declared captures"
                                }
                            InlineTarget.Body(
                                body.parameters.map(::symbol),
                                copy(body.block) as Block,
                                copiedCaptures,
                                region(body.returnRegion),
                                copiedReferences,
                                body.deferred,
                                copiedDeferred
                            )
                        }
                    }
                    inline<Any?>(meta(code), code.signature(), code.callbackId, target)
                }
                is ImmutableExpr<*> -> {
                    val n = code.node
                    if (n is ExprNode.Read) scoreBinding(n.location, code.source(), code.type())?.let { binding ->
                        copied[code] = binding.load()
                        return binding.load()
                    }
                    val mappedLocation = (n as? ExprNode.Read)?.let { location(it.location, code.source()) }
                    val mappedScore = (n as? ExprNode.ScoreRead)?.let { scoreTarget(it.target) }
                    if (n is ExprNode.Read &&
                        sameLocation(n.location, requireNotNull(mappedLocation)) &&
                        code.metadata.sessionId == sessionId &&
                        isVisible(code.metadata.scopeId, referenceScope(code))
                    ) {
                        use(code, referenceScope(code))
                        code
                    } else if (n is ExprNode.ScoreRead &&
                        code.metadata.sessionId == sessionId &&
                        isVisible(code.metadata.scopeId, referenceScope(code)) &&
                        context(code.metadata.executionContextId) == code.metadata.executionContextId &&
                        (
                            mappedScore === n.target ||
                                (
                                    mappedScore is MinecraftScoreTarget.Entity &&
                                        n.target is MinecraftScoreTarget.Entity &&
                                        mappedScore.entity === n.target.entity
                                    )
                            )
                    ) {
                        use(code, referenceScope(code))
                        code
                    } else expr(
                        meta(code),
                        code.type(),
                        when (n) {
                            is ExprNode.Literal -> n
                            is ExprNode.Read -> ExprNode.Read(location(n.location, code.source()))
                            is ExprNode.ScoreRead -> ExprNode.ScoreRead(requireNotNull(mappedScore), n.objective)
                            is ExprNode.BlockMatches -> ExprNode.BlockMatches(copy(n.block) as BlockHandle, n.predicate)
                            is ExprNode.Unary -> ExprNode.Unary(n.operator, copy(n.operand) as Expr<*>)
                            is ExprNode.Binary -> ExprNode.Binary(n.operator, copy(n.left) as Expr<*>, copy(n.right) as Expr<*>)
                            is ExprNode.Convert -> ExprNode.Convert(copy(n.value) as Expr<*>, n.target, n.kind)
                            is ExprNode.Field -> ExprNode.Field(copy(n.receiver) as Expr<*>, n.field)
                            is ExprNode.Index -> ExprNode.Index(copy(n.receiver) as Expr<*>, copy(n.index) as Expr<*>)
                            is ExprNode.Call -> {
                                val receiver = n.receiver?.let { copy(it) as Ref<*> }
                                ExprNode.Call(
                                    n.function,
                                    n.arguments.map { CallArgument(argument(it.payload)) },
                                    n.readonlyArguments,
                                    receiver
                                )
                            }
                            is ExprNode.MacroCall -> ExprNode.MacroCall(n.function, n.arguments.map { CallArgument(argument(it.payload)) })
                            is ExprNode.ExprBlock -> ExprNode.ExprBlock(copy(n.block) as Block, n.resultType, region(n.region))
                            is ExprNode.InlineCall -> ExprNode.InlineCall(copy(n.callback) as InlineFn<*>, n.arguments.map { InlineArgument(argument(it.payload)) })
                        }
                    )
                }
                is ImmutableStmt -> stmt(
                    meta(code),
                    when (val n = code.node) {
                        is StmtNode.ExecuteAs -> StmtNode.ExecuteAs(
                            copy(n.targets) as EntitySet,
                            copy(n.self) as EntityHandle,
                            copy(n.body) as Block,
                            frame(n.parentContext),
                            frame(n.context)
                        )
                        is StmtNode.At -> StmtNode.At(copy(n.position) as PositionHandle, copy(n.body) as Block, frame(n.parentContext), frame(n.context))
                        is StmtNode.Rotated -> StmtNode.Rotated(copy(n.rotation) as RotationHandle, copy(n.body) as Block, frame(n.parentContext), frame(n.context))
                        is StmtNode.CaptureAddress -> StmtNode.CaptureAddress(address(n.address, code.source()), location(n.location, code.source()), n.type)
                        is StmtNode.LocalDecl -> StmtNode.LocalDecl(copy(n.local) as Local<*>, copy(n.initialValue) as Expr<*>)
                        is StmtNode.Assign -> StmtNode.Assign(copy(n.destination) as Ref<*>, copy(n.expression) as Expr<*>)
                        is StmtNode.Eval -> StmtNode.Eval(copy(n.expression) as Expr<*>)
                        is StmtNode.If -> StmtNode.If(copy(n.condition) as Expr<*>, copy(n.thenBlock) as Block, n.elseBlock?.let { copy(it) as Block })
                        is StmtNode.While -> StmtNode.While(copy(n.condition) as Expr<*>, copy(n.body) as Block, region(n.region))
                        is StmtNode.ScopeBlock -> StmtNode.ScopeBlock(copy(n.block) as Block)
                        is StmtNode.CallbackReturn -> StmtNode.CallbackReturn(region(n.region), n.value?.let { copy(it) as Expr<*> })
                        is StmtNode.Yield -> StmtNode.Yield(region(n.region), copy(n.value) as Expr<*>)
                        is StmtNode.Break -> StmtNode.Break(region(n.region))
                        is StmtNode.Continue -> StmtNode.Continue(region(n.region))
                    }
                )
                is ImmutableBlock -> block(meta(code), code.statements().map { copy(it) as Stmt })
            }
            copied[code] = result
            return result
        }

        fun freeRef(code: Ref<*>, oldLocation: NodeLocation): Code {
            val owned = code as OwnedCode
            scoreBinding(oldLocation, code.source(), code.type(), reference = true)?.let { binding ->
                copied[code.load()] = binding.load()
                return binding
            }
            val mapped = location(oldLocation, code.source())
            val scope = referenceScope(owned)
            val binding = external[oldLocation.root]
            val bindingLocation = when (binding) {
                is ImmutableRef<*> -> binding.location
                is ImmutableLocal<*> -> binding.location
                is ImmutableMinecraftScoreRef -> throw CodeModelException(code.source(), "A score reference cannot be represented by a symbol location")
                null -> null
            }
            if (binding != null && oldLocation.capturedBase == null && oldLocation.steps.isEmpty())
                require(code.type().sameType(binding.type())) { "External reference binding has another type" }
            val result = if (binding != null && oldLocation.capturedBase == null && oldLocation.steps.isEmpty() && sameLocation(mapped, requireNotNull(bindingLocation))) {
                binding
            } else if (owned.metadata.sessionId == sessionId &&
                sameLocation(oldLocation, mapped) &&
                isVisible(owned.metadata.scopeId, scope)
            ) {
                use(code, scope)
                code
            } else ref(metadata(scope, origin ?: owned.metadata.origin, mapped.executionContextId), code.type(), mapped)
            copied[code.load()] = result.load()
            return result
        }
    }

    override fun close() {
        open = false
        if (parentArena == null) family.open = false
    }
}
