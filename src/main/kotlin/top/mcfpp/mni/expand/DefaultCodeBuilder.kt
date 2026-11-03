package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.util.function.Consumer
import java.util.function.Function

internal class BuilderAccess {
    var active: DefaultCodeBuilder? = null
    private val roots = java.util.IdentityHashMap<CodeArena, ExecutionContextFrame>()

    internal fun rootContextOrNull(arena: CodeArena): ExecutionContextFrame? = roots[arena]

    internal fun rootContext(arena: CodeArena, source: SourceSpan): ExecutionContextFrame {
        val frame = roots[arena] ?: arena.rootContext(arena.rootScope).also { roots[arena] = it }
        return arena.contextFrame(frame.id, arena.rootScope, source)
    }
}

internal data class YieldTarget(val type: TypeRef<*>, val region: RegionId)

/** Mutable construction is confined to an immediately consumed host callback. */
internal class DefaultCodeBuilder(
    internal val arena: CodeArena,
    private val types: TypeService,
    internal val origin: Origin,
    internal val scope: ScopeId,
    private val access: BuilderAccess,
    private val result: YieldTarget? = null,
    executionContext: ExecutionContextId? = null,
    private val minecraftFactory: ((CodeBuilder) -> MinecraftBuilder)? = null
) : CodeBuilder {
    private val inheritedExecutionContext = executionContext
    internal val executionContext: ExecutionContextId?
        get() = inheritedExecutionContext ?: access.rootContextOrNull(arena)?.id
    private val statements = arrayListOf<Stmt>()
    private var closed = false
    internal var completesResult = false
        private set

    private fun fail(message: String, site: SourceSpan = origin.callSite): Nothing = throw CodeModelException(site, message)

    private fun active() {
        if (closed) fail("Code builder is closed")
        if (access.active !== this) fail("Use the active child builder inside its callback")
        if (completesResult) fail("Code follows a completed expression result")
        arena.metadata(scope, origin, executionContext)
    }

    /** Ordinary builders retain null metadata until a Minecraft operation requests a frame. */
    internal fun contextFrame(): ExecutionContextFrame {
        active()
        val id = executionContext
        return if (id == null) access.rootContext(arena, origin.callSite)
        else arena.contextFrame(id, scope, origin.callSite)
    }

    internal fun <R> consume(body: (CodeBuilder) -> R): R {
        if (closed) fail("Code builder is closed")
        val previous = access.active
        access.active = this
        try {
            return body(this)
        } finally {
            closed = true
            access.active = previous
        }
    }

    internal fun finish(requireResult: Boolean = false): ImmutableBlock {
        if (!closed) fail("Code builder callback is still active")
        if (requireResult && !completesResult) fail("Every normal exit must yield a result")
        return arena.block(metadata(), statements)
    }

    private fun metadata() = arena.metadata(scope, origin, executionContext)

    private fun visible(code: Code) {
        val owner = (code as OwnedCode).metadata
        if (owner.sessionId != arena.sessionId) fail("Code belongs to another expansion session", code.source())
        if (!arena.isVisible(owner.scopeId, scope)) fail("Code is outside its lexical scope", code.source())
    }

    private fun actual(type: TypeRef<*>): ImmutableTypeRef<*> = type as ImmutableTypeRef<*>

    private fun expression(expression: Expr<*>) {
        visible(expression)
        actual(expression.type())
    }

    private fun assignable(expected: TypeRef<*>, value: Expr<*>) {
        expression(value)
        if (!expected.isAssignableFrom(value.type())) fail("Expected ${expected.name()}, got ${value.type().name()}", value.source())
    }

    private fun kind(type: TypeRef<*>, expected: TypeKind) {
        if (actual(type).kind != expected) fail("Unsupported actual type ${type.name()}")
    }

    private fun boolean(value: Expr<*>) {
        expression(value)
        kind(value.type(), TypeKind.BOOL)
    }

    private fun constantBoolean(value: Expr<*>): Boolean? = when (val node = (value as ImmutableExpr<*>).node) {
        is ExprNode.Literal -> (node.value as? ConstantValue.Bool)?.value
        is ExprNode.ExprBlock -> {
            val results = arrayListOf<Boolean?>()
            fun exits(block: Block) {
                block.statements().forEach { statement ->
                    when (val exit = (statement as ImmutableStmt).node) {
                        is StmtNode.Yield -> if (exit.region == node.region) results += constantBoolean(exit.value)
                        is StmtNode.If -> {
                            exits(exit.thenBlock)
                            exit.elseBlock?.let(::exits)
                        }
                        is StmtNode.While -> exits(exit.body)
                        is StmtNode.ScopeBlock -> exits(exit.block)
                        else -> Unit
                    }
                }
            }
            exits(node.block)
            results.firstOrNull()?.takeIf { candidate -> results.all { it == candidate } }
        }
        else -> null
    }

    private fun location(reference: Ref<*>): NodeLocation = when (reference) {
        is ImmutableLocal<*> -> reference.location
        is ImmutableRef<*> -> reference.location
        is ImmutableMinecraftScoreRef -> throw CodeModelException(reference.source(), "Unsupported reference node '${reference.javaClass.name}'")
    }

    private fun writable(reference: Ref<*>) {
        visible(reference)
        if (reference !is ImmutableMinecraftScoreRef && !location(reference).writable)
            fail("Reference is read-only", reference.source())
    }

    private fun addStatement(node: StmtNode) {
        statements += arena.stmt(metadata(), node)
    }

    override fun <T> local(type: TypeRef<T>, initialValue: Expr<T>): Local<T> = local("local", type, initialValue)

    override fun <T> local(hint: String, type: TypeRef<T>, initialValue: Expr<T>): Local<T> {
        active()
        if (hint.isBlank()) fail("Local hint must not be blank")
        if (actual(type).kind == TypeKind.VOID) fail("A local cannot have void type")
        assignable(type, initialValue)
        return arena.local(metadata(), type, hint).also { addStatement(StmtNode.LocalDecl(it, initialValue)) }
    }

    override fun <T> assign(destination: Ref<T>, expression: Expr<T>) {
        active()
        writable(destination)
        assignable(destination.type(), expression)
        addStatement(StmtNode.Assign(destination, expression))
    }

    override fun evaluate(expression: Expr<*>) {
        active()
        expression(expression)
        addStatement(StmtNode.Eval(expression))
    }

    override fun append(block: Block) {
        active()
        val sourceContext = (block as OwnedCode).metadata.executionContextId
        val contexts = sourceContext?.takeIf { it != executionContext }?.let { mapOf(it to contextFrame()) }.orEmpty()
        val imported = arena.importTree(block, arena.childScope(scope), origin, contextBindings = contexts)
        validateControlExits(imported)
        addStatement(StmtNode.ScopeBlock(imported))
        completesResult = !fallsThrough(imported)
    }

    private fun containsBreak(block: Block, region: RegionId): Boolean = block.statements().any { statement ->
        when (val node = (statement as ImmutableStmt).node) {
            is StmtNode.Break -> node.region == region
            is StmtNode.If -> containsBreak(node.thenBlock, region) || node.elseBlock?.let { containsBreak(it, region) } == true
            is StmtNode.While -> containsBreak(node.body, region)
            is StmtNode.ScopeBlock -> containsBreak(node.block, region)
            is StmtNode.ExecuteAs -> containsBreak(node.body, region)
            is StmtNode.At -> containsBreak(node.body, region)
            is StmtNode.Rotated -> containsBreak(node.body, region)
            else -> false
        }
    }

    private fun fallsThrough(block: Block): Boolean {
        for (statement in block.statements()) {
            val normal = when (val node = (statement as ImmutableStmt).node) {
                is StmtNode.Yield, is StmtNode.CallbackReturn, is StmtNode.Break, is StmtNode.Continue -> false
                is StmtNode.ScopeBlock -> fallsThrough(node.block)
                is StmtNode.At -> fallsThrough(node.body)
                is StmtNode.Rotated -> fallsThrough(node.body)
                is StmtNode.If -> when (constantBoolean(node.condition)) {
                    true -> fallsThrough(node.thenBlock)
                    false -> node.elseBlock?.let(::fallsThrough) ?: true
                    null -> fallsThrough(node.thenBlock) || (node.elseBlock?.let(::fallsThrough) ?: true)
                }
                is StmtNode.While -> constantBoolean(node.condition) != true || containsBreak(node.body, node.region)
                else -> true
            }
            if (!normal) return false
        }
        return true
    }

    private fun validateControlExits(block: Block, loops: Set<RegionId> = emptySet()) {
        block.statements().forEach { statement ->
            when (val node = (statement as ImmutableStmt).node) {
                is StmtNode.Yield, is StmtNode.CallbackReturn ->
                    fail("Appended block contains a control exit owned by another region", statement.source())
                is StmtNode.Break -> if (node.region !in loops) fail("Break escapes appended loop", statement.source())
                is StmtNode.Continue -> if (node.region !in loops) fail("Continue escapes appended loop", statement.source())
                is StmtNode.If -> {
                    validateControlExits(node.thenBlock, loops)
                    node.elseBlock?.let { validateControlExits(it, loops) }
                }
                is StmtNode.While -> validateControlExits(node.body, loops + node.region)
                is StmtNode.ScopeBlock -> validateControlExits(node.block, loops)
                is StmtNode.ExecuteAs -> validateControlExits(node.body, loops)
                is StmtNode.At -> validateControlExits(node.body, loops)
                is StmtNode.Rotated -> validateControlExits(node.body, loops)
                else -> Unit
            }
        }
    }

    private fun child(target: YieldTarget? = result): DefaultCodeBuilder = DefaultCodeBuilder(
        arena,
        types,
        origin,
        arena.childScope(scope),
        access,
        target,
        executionContext,
        minecraftFactory
    )

    private fun branch(body: Consumer<CodeBuilder>): Pair<ImmutableBlock, Boolean> {
        val builder = child()
        builder.consume { body.accept(it) }
        return builder.finish() to builder.completesResult
    }

    override fun ifThen(condition: Expr<BoolT>, thenBody: Consumer<CodeBuilder>) {
        active()
        boolean(condition)
        val thenBranch = branch(thenBody)
        addStatement(StmtNode.If(condition, thenBranch.first, null))
        completesResult = constantBoolean(condition) == true && thenBranch.second
    }

    override fun ifElse(condition: Expr<BoolT>, thenBody: Consumer<CodeBuilder>, elseBody: Consumer<CodeBuilder>) {
        active()
        boolean(condition)
        val thenBranch = branch(thenBody)
        val elseBranch = branch(elseBody)
        addStatement(StmtNode.If(condition, thenBranch.first, elseBranch.first))
        completesResult = when (constantBoolean(condition)) {
            true -> thenBranch.second
            false -> elseBranch.second
            null -> thenBranch.second && elseBranch.second
        }
    }

    override fun whileLoop(condition: Function<CodeBuilder, Expr<BoolT>>, body: Consumer<CodeBuilder>) {
        active()
        val conditionBuilder = child(null)
        val test = conditionBuilder.consume { condition.apply(it) }
        conditionBuilder.boolean(test)
        val conditionRegion = arena.freshRegion()
        if (!conditionBuilder.completesResult) conditionBuilder.addStatement(StmtNode.Yield(conditionRegion, test))
        val conditionBlock = conditionBuilder.finish()
        val boolType = types.boolType().also { kind(it, TypeKind.BOOL) }
        val runtimeTest = arena.expr(metadata(), boolType, ExprNode.ExprBlock(conditionBlock, boolType, conditionRegion))
        val loopRegion = arena.freshRegion()
        addStatement(StmtNode.While(runtimeTest, branch(body).first, loopRegion))
        // The public builder has no exit from this loop other than yielding the enclosing result.
        completesResult = conditionBuilder.completesResult || constantBoolean(test) == true
    }

    private fun argumentType(payload: ArgumentPayload): TypeRef<*>? = when (payload) {
        is ArgumentPayload.Value -> payload.expression.type()
        is ArgumentPayload.Reference -> payload.reference.type()
        is ArgumentPayload.Constant -> payload.constant.type()
        is ArgumentPayload.Inline -> null // Nested signatures are retained/checked by the target descriptor validator.
    }

    private fun arguments(parameters: List<ParameterView>, supplied: List<ArgumentPayload>) {
        if (parameters.size != supplied.size) fail("Expected ${parameters.size} arguments, got ${supplied.size}")
        parameters.zip(supplied).forEach { (parameter, value) ->
            if (parameter.mode() != value.mode) fail("Argument ${parameter.name()} requires ${parameter.mode()}, got ${value.mode}", value.code.source())
            visible(value.code)
            argumentType(value)?.let { type ->
                val compatible = if (value is ArgumentPayload.Reference) parameter.type().sameType(type)
                else parameter.type().isAssignableFrom(type)
                if (!compatible) fail("Argument ${parameter.name()} has incompatible actual type", value.code.source())
            }
            if (value is ArgumentPayload.Reference) writable(value.reference)
        }
    }

    private fun function(function: FunctionRef<*>, supplied: List<CallArgument>, void: Boolean): ImmutableFunctionRef<*> {
        val descriptor = function as ImmutableFunctionRef<*>
        if (descriptor.sessionId != null && descriptor.sessionId != arena.sessionId) fail("Function belongs to another expansion session")
        if ((actual(function.returnType()).kind == TypeKind.VOID) != void) fail(if (void) "Expected a void function" else "Use callVoid for a void function")
        val supported = when (descriptor.kind) {
            FunctionKind.ORDINARY, FunctionKind.NATIVE -> setOf(ParamMode.VALUE)
            FunctionKind.HELPER -> setOf(ParamMode.VALUE, ParamMode.REF)
            FunctionKind.MACRO -> ParamMode.entries.toSet()
        }
        if (function.parameters().any { it.mode() !in supported }) fail("Target kind does not support these parameter modes")
        arguments(function.parameters(), supplied.map { it.payload })
        return descriptor
    }

    private fun <R> callExpression(function: FunctionRef<R>, supplied: List<CallArgument>, void: Boolean): Expr<R> {
        val descriptor = function(function, supplied, void)
        val node = if (descriptor.kind == FunctionKind.MACRO) ExprNode.MacroCall(function, supplied) else ExprNode.Call(function, supplied)
        return arena.expr(metadata(), function.returnType(), node)
    }

    override fun <R> call(function: FunctionRef<R>, arguments: List<CallArgument>): Expr<R> {
        active()
        return callExpression(function, arguments, false)
    }

    override fun callVoid(function: FunctionRef<VoidT>, arguments: List<CallArgument>) {
        active()
        addStatement(StmtNode.Eval(callExpression(function, arguments, true)))
    }

    private fun <R> inlineExpression(callback: InlineFn<*>, supplied: List<InlineArgument>, void: Boolean): Expr<R> {
        visible(callback)
        val signature = callback.signature()
        if ((actual(signature.returnType()).kind == TypeKind.VOID) != void) fail(if (void) "Expected a void callback" else "Use inlineVoid for a void callback")
        if (signature.parameters().any { it.mode() == ParamMode.INLINE }) fail("A callback cannot accept another inline callback")
        arguments(signature.parameters(), supplied.map { it.payload })
        @Suppress("UNCHECKED_CAST")
        return arena.expr(metadata(), signature.returnType() as TypeRef<R>, ExprNode.InlineCall(callback, supplied))
    }

    override fun <R> inline(callback: InlineFn<*>, arguments: List<InlineArgument>): Expr<R> {
        active()
        return inlineExpression(callback, arguments, false)
    }

    override fun inlineVoid(callback: InlineFn<*>, arguments: List<InlineArgument>) {
        active()
        addStatement(StmtNode.Eval(inlineExpression<VoidT>(callback, arguments, true)))
    }

    override fun <T> yield(expression: Expr<T>) {
        active()
        val target = result ?: fail("yield requires an expression region")
        assignable(target.type, expression)
        addStatement(StmtNode.Yield(target.region, expression))
        completesResult = true
    }

    private fun <T> literal(type: TypeRef<T>, expected: TypeKind, value: ConstantValue): Expr<T> {
        active()
        kind(type, expected)
        return arena.expr(metadata(), type, ExprNode.Literal(value))
    }

    override fun intLiteral(value: Int): Expr<IntT> = literal(types.intType(), TypeKind.INT, ConstantValue.Integral(value.toLong()))
    override fun boolLiteral(value: Boolean): Expr<BoolT> = literal(types.boolType(), TypeKind.BOOL, ConstantValue.Bool(value))
    override fun floatLiteral(value: Float): Expr<FloatT> = literal(types.floatType(), TypeKind.FLOAT, ConstantValue.FloatBits(value.toRawBits()))
    override fun stringLiteral(value: String): Expr<StringT> = literal(types.stringType(), TypeKind.STRING, ConstantValue.Text(value))

    private fun pair(left: Expr<*>, right: Expr<*>, allowed: Set<TypeKind>) {
        expression(left)
        expression(right)
        if (!left.type().sameType(right.type()) || actual(left.type()).kind !in allowed)
            fail("Operation requires matching supported actual types", left.source())
    }

    private fun <T> numeric(left: Expr<T>, right: Expr<T>, operator: BinaryOperator): Expr<T> {
        active()
        pair(left, right, setOf(TypeKind.INT, TypeKind.FLOAT))
        return arena.expr(metadata(), left.type(), ExprNode.Binary(operator, left, right))
    }

    override fun <T> add(left: Expr<T>, right: Expr<T>): Expr<T> = numeric(left, right, BinaryOperator.ADD)
    override fun <T> sub(left: Expr<T>, right: Expr<T>): Expr<T> = numeric(left, right, BinaryOperator.SUBTRACT)
    override fun <T> mul(left: Expr<T>, right: Expr<T>): Expr<T> = numeric(left, right, BinaryOperator.MULTIPLY)
    override fun <T> div(left: Expr<T>, right: Expr<T>): Expr<T> = numeric(left, right, BinaryOperator.DIVIDE)
    override fun <T> mod(left: Expr<T>, right: Expr<T>): Expr<T> = numeric(left, right, BinaryOperator.MODULO)

    override fun <T> negate(value: Expr<T>): Expr<T> {
        active()
        pair(value, value, setOf(TypeKind.INT, TypeKind.FLOAT))
        return arena.expr(metadata(), value.type(), ExprNode.Unary(UnaryOperator.NEGATE, value))
    }

    private fun <T> compare(left: Expr<T>, right: Expr<T>, operator: BinaryOperator, equality: Boolean = false): Expr<BoolT> {
        active()
        pair(
            left,
            right,
            if (equality) setOf(TypeKind.INT, TypeKind.FLOAT, TypeKind.BOOL, TypeKind.STRING)
            else setOf(TypeKind.INT, TypeKind.FLOAT)
        )
        val bool = types.boolType().also { kind(it, TypeKind.BOOL) }
        return arena.expr(metadata(), bool, ExprNode.Binary(operator, left, right))
    }

    override fun <T> lt(left: Expr<T>, right: Expr<T>): Expr<BoolT> = compare(left, right, BinaryOperator.LT)
    override fun <T> le(left: Expr<T>, right: Expr<T>): Expr<BoolT> = compare(left, right, BinaryOperator.LE)
    override fun <T> gt(left: Expr<T>, right: Expr<T>): Expr<BoolT> = compare(left, right, BinaryOperator.GT)
    override fun <T> ge(left: Expr<T>, right: Expr<T>): Expr<BoolT> = compare(left, right, BinaryOperator.GE)
    override fun <T> eq(left: Expr<T>, right: Expr<T>): Expr<BoolT> = compare(left, right, BinaryOperator.EQ, true)
    override fun <T> ne(left: Expr<T>, right: Expr<T>): Expr<BoolT> = compare(left, right, BinaryOperator.NE, true)

    override fun not(value: Expr<BoolT>): Expr<BoolT> {
        active()
        boolean(value)
        return arena.expr(metadata(), value.type(), ExprNode.Unary(UnaryOperator.NOT, value))
    }

    private fun logical(left: Expr<BoolT>, right: Expr<BoolT>, operator: BinaryOperator): Expr<BoolT> {
        active()
        boolean(left)
        boolean(right)
        pair(left, right, setOf(TypeKind.BOOL))
        return arena.expr(metadata(), left.type(), ExprNode.Binary(operator, left, right))
    }

    override fun and(left: Expr<BoolT>, right: Expr<BoolT>): Expr<BoolT> = logical(left, right, BinaryOperator.AND)
    override fun or(left: Expr<BoolT>, right: Expr<BoolT>): Expr<BoolT> = logical(left, right, BinaryOperator.OR)

    private fun field(receiver: TypeRef<*>, field: FieldRef<*>) {
        val descriptor = field as ImmutableFieldRef<*>
        val member = receiver.fields().filterIsInstance<ImmutableFieldRef<*>>().firstOrNull { it.key == descriptor.key }
            ?: fail("Field does not belong to the receiver type", field.source())
        if (!member.type().sameType(field.type()) || member.writable() != field.writable()) fail("Field descriptor is inconsistent", field.source())
    }

    override fun <T> field(receiver: Expr<*>, field: FieldRef<T>): Expr<T> {
        active()
        expression(receiver)
        field(receiver.type(), field)
        return arena.expr(metadata(), field.type(), ExprNode.Field(receiver, field))
    }

    override fun <T> fieldRef(receiver: Ref<*>, field: FieldRef<T>): Ref<T> {
        active()
        visible(receiver)
        field(receiver.type(), field)
        val base = location(receiver)
        return arena.ref(
            metadata(),
            field.type(),
            NodeLocation(
                base.root,
                base.steps + LocationStep.Field(field),
                base.executionContextId,
                base.writable && field.writable()
            )
        )
    }

    override fun minecraft(): MinecraftBuilder {
        active()
        return minecraftFactory?.invoke(this) ?: DefaultMinecraftBuilder(this).also { contextFrame() }
    }

    internal fun minecraftMetadata(): CodeMetadata {
        contextFrame()
        return metadata()
    }

    internal fun minecraftIntType(): TypeRef<IntT> = types.intType().also { kind(it, TypeKind.INT) }
    internal fun minecraftBoolType(): TypeRef<BoolT> = types.boolType().also { kind(it, TypeKind.BOOL) }

    /** Consumes the host callback immediately; only the resulting immutable child tree is kept. */
    internal fun minecraftChild(
        transition: (ExecutionContextFrame, ScopeId) -> ExecutionContextFrame,
        body: (DefaultCodeBuilder, ExecutionContextFrame) -> Unit,
        statement: (ImmutableBlock, ExecutionContextFrame, ExecutionContextFrame) -> StmtNode,
        alwaysRuns: Boolean
    ) {
        val parent = contextFrame()
        val childScope = arena.childScope(scope)
        val frame = transition(parent, childScope)
        val builder = DefaultCodeBuilder(arena, types, origin, childScope, access, result, frame.id, minecraftFactory)
        builder.consume { body(builder, frame) }
        addStatement(statement(builder.finish(), parent, frame))
        if (alwaysRuns) completesResult = builder.completesResult
    }
}
