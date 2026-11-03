package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.util.IdentityHashMap

internal enum class ValidationPhase { PRE_EXPANSION, FINAL }
internal data class ValidationRegion(val id: RegionId, val type: TypeRef<*>, val callback: Boolean = false)

/** Bindings are compiler-issued capabilities, never inferred from a symbol's display name. */
internal class ValidationEnvironment(
    val arena: CodeArena,
    externalBindings: List<Ref<*>> = emptyList(),
    functions: List<FunctionRef<*>> = emptyList(),
    inlineTargets: List<FunctionRef<*>> = emptyList(),
    val resultRegion: ValidationRegion? = null,
    types: List<TypeRef<*>> = emptyList(),
    fieldCapabilities: Set<FieldKey>? = null
) {
    val externalBindings = immutableList(externalBindings)
    val functions = immutableList(functions)
    val inlineTargets = immutableList(inlineTargets)
    val types = immutableList(types)
    val fieldCapabilities = fieldCapabilities?.let(::immutableSet)
}

internal sealed interface ValidationResult {
    val code: Code
    val environment: ValidationEnvironment
}
internal sealed class PrevalidatedCode : ValidationResult
internal sealed class ValidatedCode : ValidationResult

/** No compiler state is changed by either validation phase. */
internal class CodeValidator {
    private class Pre(override val code: Code, override val environment: ValidationEnvironment) : PrevalidatedCode()
    private class Final(override val code: Code, override val environment: ValidationEnvironment) : ValidatedCode()

    fun validate(code: Code, environment: ValidationEnvironment, phase: ValidationPhase): ValidationResult {
        Check(environment, phase).validate(code)
        return if (phase == ValidationPhase.FINAL) Final(code, environment) else Pre(code, environment)
    }

    private class Check(val env: ValidationEnvironment, val phase: ValidationPhase) {
        private data class Binding(val location: NodeLocation, val type: TypeRef<*>, val scope: ScopeId)
        private data class Address(
            val type: TypeRef<*>,
            val writable: Boolean,
            val root: SymbolId,
            val scope: ScopeId,
            val context: ExecutionContextId?
        )
        private data class Position(val type: TypeRef<*>, val writable: Boolean)
        private enum class ExitKind { YIELD, RETURN, BREAK, CONTINUE }
        private data class Exit(val kind: ExitKind, val region: RegionId)
        // Only normal paths reach the next node. Terminal exits survive until their owning
        // loop, expression or callback consumes them, so nested returns cannot escape.
        private data class Flow(val normal: Boolean = true, val exits: Set<Exit> = emptySet()) {
            fun then(next: Flow) = Flow(normal && next.normal, exits + if (normal) next.exits else emptySet())
            fun or(other: Flow) = Flow(normal || other.normal, exits + other.exits)
        }
        private data class State(
            val scope: ScopeId,
            val context: ExecutionContextId?,
            val bindings: MutableList<Binding>,
            val yields: List<ValidationRegion> = emptyList(),
            val callback: ValidationRegion? = null,
            val loops: List<RegionId> = emptyList(),
            val addresses: MutableMap<AddressId, Address> = linkedMapOf()
        ) {
            fun child(scope: ScopeId) = copy(scope = scope, bindings = bindings.toMutableList(), addresses = addresses.toMutableMap())
        }
        private val types = linkedMapOf<TypeKey, ImmutableTypeRef<*>>()
        private val declarations = linkedMapOf<SymbolId, ImmutableStmt>()
        private val addressDeclarations = linkedMapOf<AddressId, ImmutableStmt>()
        private var graph: FrozenTypeGraph? = null
        private val regions = linkedMapOf<RegionId, Any>()
        private val activeCallbacks = hashSetOf<CallbackId>()
        private val comparedSignatures = IdentityHashMap<InlineSignature<*>, MutableSet<InlineSignature<*>>>()
        private val visiting = java.util.Collections.newSetFromMap(IdentityHashMap<Code, Boolean>())
        private val contexts = ContextValidator(env.arena)

        private fun fail(code: Code, message: String): Nothing = throw CodeModelException((code as OwnedCode).metadata.origin.callSite, message)

        fun validate(code: Code) {
            val meta = (code as OwnedCode).metadata
            if (meta.sessionId != env.arena.sessionId) fail(code, "Code belongs to another expansion session")
            try {
                env.arena.metadata(meta.scopeId, meta.origin, meta.executionContextId)
            } catch (error: IllegalArgumentException) {
                fail(code, error.message ?: "Code arena is unavailable")
            }
            val bindings = arrayListOf<Binding>()
            env.types.forEach { type(it, code) }
            env.externalBindings.forEach { ref ->
                val owner = (ref as OwnedCode).metadata
                if (owner.sessionId != env.arena.sessionId || !env.arena.containsScope(owner.scopeId)) fail(code, "External binding belongs to another session or scope")
                if (ref is ImmutableMinecraftScoreRef) {
                    if (type(ref.type(), ref).kind != TypeKind.INT) fail(ref, "Score requires actual int type")
                    contexts.score(ref.target, ref, owner.scopeId, owner.executionContextId)
                    return@forEach
                }
                val location = location(ref)
                if (location.capturedBase != null) fail(ref, "Captured address cannot be registered as an external binding")
                if (location.root.session != env.arena.sessionId) fail(code, "External location belongs to another session")
                if (location.executionContextId != null && owner.executionContextId != location.executionContextId)
                    fail(code, "Reference lost its bound execution context")
                type(ref.type(), code)
                bindings += Binding(location, ref.type(), owner.scopeId)
            }
            val result = env.resultRegion
            if (result != null) {
                bindRegion(result.id, result, code)
                type(result.type, code)
            }
            val state = State(
                meta.scopeId,
                meta.executionContextId,
                bindings,
                yields = if (result != null && !result.callback) listOf(result) else emptyList(),
                callback = result?.takeIf { it.callback }
            )
            // Graph locations are derived capabilities, never a shortcut around the root schema.
            env.externalBindings.forEach { ref ->
                if (ref !is ImmutableMinecraftScoreRef && (ref.type() as ImmutableTypeRef<*>).graph != null && location(ref).steps.isNotEmpty())
                    position(location(ref), ref.type(), state, ref, externalDeclaration = true)
            }
            val flow = when (code) {
                is Expr<*> -> {
                    nonVoid(code.type(), code)
                    expression(code, state)
                }
                is Block -> block(code, state, nested = false)
                is Stmt -> statement(code as ImmutableStmt, state)
                else -> fail(code, "An input handle cannot escape as generated code")
            }
            if (result != null) {
                if (flow.normal && type(result.type, code).kind != TypeKind.VOID) fail(code, "Every normal exit must produce a result")
                val expected = Exit(if (result.callback) ExitKind.RETURN else ExitKind.YIELD, result.id)
                if (flow.exits.any { it != expected }) fail(code, "Control exit escapes its result region")
            } else if (flow.exits.isNotEmpty()) fail(code, "Control exit escapes generated code")
        }

        private fun type(ref: TypeRef<*>, code: Code): ImmutableTypeRef<*> {
            val descriptor = ref as ImmutableTypeRef<*>
            val owner = descriptor.graph
            if (owner != null) {
                if (!owner.owns(descriptor)) fail(code, "Type is not a canonical frozen handle")
                if (graph != null && graph !== owner) fail(code, "Type belongs to another frozen domain")
                if (types.values.any { it.graph == null }) fail(code, "Type belongs to another frozen domain")
                graph = owner
            } else if (graph != null) fail(code, "Type belongs to another frozen domain")
            val previous = types.putIfAbsent(descriptor.key, descriptor)
            if (previous != null && previous.kind != descriptor.kind) fail(code, "Conflicting descriptors for one actual type")
            return previous ?: descriptor
        }

        private fun nonVoid(ref: TypeRef<*>, code: Code) {
            if (type(ref, code).kind == TypeKind.VOID) fail(code, "A value cannot have void type")
        }

        private fun same(expected: TypeRef<*>, actual: TypeRef<*>, code: Code) {
            type(expected, code)
            type(actual, code)
            if (!expected.sameType(actual)) fail(code, "Actual type mismatch: expected ${expected.name()}, got ${actual.name()}")
        }

        private fun assignable(expected: TypeRef<*>, actual: TypeRef<*>, code: Code) {
            type(actual, code)
            if (!type(expected, code).isAssignableFrom(actual)) fail(code, "Actual type is not assignable to ${expected.name()}")
        }

        private fun metadata(code: Code, state: State, nested: Boolean = false) {
            val meta = (code as OwnedCode).metadata
            if (meta.sessionId != env.arena.sessionId || !env.arena.containsScope(meta.scopeId)) fail(code, "Code belongs to another session or unknown scope")
            val visible = if (nested) env.arena.isVisible(state.scope, meta.scopeId) else env.arena.isVisible(meta.scopeId, state.scope)
            if (!visible) fail(code, "Code is outside its dominating lexical scope")
            contexts.metadata(code, state.scope)
            if (meta.executionContextId != null &&
                meta.executionContextId != state.context &&
                !contexts.dependencyMetadata(code, state.context, state.scope)
            ) fail(code, "Code belongs to another execution context")
        }

        private inline fun guard(code: Code, action: () -> Flow): Flow {
            if (!visiting.add(code)) fail(code, "Cyclic code graph")
            try {
                return action()
            } finally {
                visiting.remove(code)
            }
        }

        private fun bindRegion(region: RegionId, owner: Any, code: Code) {
            if (region.session != env.arena.sessionId) fail(code, "Control region belongs to another session")
            val previous = regions.putIfAbsent(region, owner)
            if (previous != null && previous !== owner) fail(code, "Control region is reused by another owner")
        }

        private fun location(ref: Ref<*>): NodeLocation = when (ref) {
            is ImmutableRef<*> -> ref.location
            is ImmutableLocal<*> -> ref.location
            is ImmutableMinecraftScoreRef -> throw CodeModelException(ref.source(), "Unsupported reference node '${ref.javaClass.name}'")
        }

        private fun field(receiver: TypeRef<*>, field: FieldRef<*>, code: Code): FieldRef<*> {
            val key = (field as ImmutableFieldRef<*>).key
            val canonical = type(receiver, code).fields().filterIsInstance<ImmutableFieldRef<*>>().firstOrNull { it.key == key }
                ?: fail(code, "Field does not belong to the actual receiver type")
            if ((receiver as ImmutableTypeRef<*>).graph != null && canonical !== field)
                fail(code, "Field is not the canonical frozen descriptor")
            if (env.fieldCapabilities?.contains(key) == false) fail(code, "Field capability is not authorized")
            same(canonical.type(), field.type(), code)
            if (canonical.writable() != field.writable()) fail(code, "Field capability does not match its descriptor")
            return canonical
        }

        private fun position(
            location: NodeLocation,
            expected: TypeRef<*>,
            state: State,
            code: Code,
            write: Boolean = false,
            externalDeclaration: Boolean = false
        ): Position {
            if (location.root.session != env.arena.sessionId) fail(code, "Location belongs to another expansion session")
            if (!contexts.sameExecutor(location.executionContextId, state.context, state.scope)) fail(code, "Location cannot be rebound to a different execution context")
            val captured = location.capturedBase?.let { id ->
                if (id.session != env.arena.sessionId) fail(code, "Captured address belongs to another session")
                val address = state.addresses[id] ?: fail(code, "Captured address has no dominating declaration")
                if (!env.arena.isVisible(address.scope, state.scope)) fail(code, "Captured address is outside its lexical scope")
                if (!contexts.sameExecutor(address.context, state.context, state.scope) || (location.executionContextId != null && address.context != location.executionContextId))
                    fail(code, "Captured address cannot change execution context")
                if (address.root != location.root) fail(code, "Captured address provenance does not match its original root")
                address
            }
            val binding = if (captured == null) state.bindings.filter { candidate ->
                candidate.location.capturedBase == null &&
                    candidate.location.root == location.root &&
                    candidate.location.executionContextId == location.executionContextId &&
                    location.steps.take(candidate.location.steps.size) == candidate.location.steps &&
                    (!externalDeclaration || candidate.location.steps.size < location.steps.size) &&
                    env.arena.isVisible(candidate.scope, state.scope)
            }.maxWithOrNull(compareBy<Binding> { it.location.steps.size }.thenBy { it.location.writable })
                ?: fail(code, "Location has no dominating declaration or explicit external binding") else null
            var resolved = captured?.type ?: requireNotNull(binding).type
            var writable = captured?.writable ?: requireNotNull(binding).location.writable
            val consumed = if (captured != null) 0 else requireNotNull(binding).location.steps.size
            location.steps.drop(consumed).forEach { step ->
                when (step) {
                    is LocationStep.Field -> {
                        val member = field(resolved, step.field, code)
                        resolved = member.type()
                        writable = writable && member.writable()
                    }
                    is LocationStep.Index, is LocationStep.CapturedIndex -> {
                        val receiver = type(resolved, code)
                        val schema = receiver.graph?.index(receiver.key) ?: fail(code, "Indexed location is not supported by its actual type schema")
                        resolved = requireNotNull(receiver.graph).type(schema.element)
                        writable = writable && schema.writable
                    }
                    else -> fail(code, "Keyed location is not supported by its actual type schema")
                }
            }
            location.steps.forEach { step ->
                val symbol = when (step) {
                    is LocationStep.CapturedIndex -> step.symbol
                    is LocationStep.CapturedKey -> step.symbol
                    else -> null
                }
                if (symbol != null) {
                    val captured = state.bindings.firstOrNull { it.location.root == symbol && it.location.steps.isEmpty() && env.arena.isVisible(it.scope, state.scope) }
                        ?: fail(code, "Captured location component has no dominating binding")
                    if (!contexts.sameExecutor(captured.location.executionContextId, state.context, state.scope))
                        fail(code, "Captured location component belongs to another execution context")
                    val expectedKind = if (step is LocationStep.CapturedIndex) TypeKind.INT else TypeKind.STRING
                    if (type(captured.type, code).kind != expectedKind) fail(code, "Captured location component has the wrong actual type")
                }
            }
            same(resolved, expected, code)
            if (location.writable && !writable) fail(code, "Reference widens a read-only capability")
            if (write && (!writable || !location.writable)) fail(code, "Reference is read-only")
            return Position(resolved, writable && location.writable)
        }

        private fun reference(ref: Ref<*>, state: State, write: Boolean = false) {
            metadata(ref, state)
            nonVoid(ref.type(), ref)
            if (ref is ImmutableMinecraftScoreRef) {
                if (type(ref.type(), ref).kind != TypeKind.INT) fail(ref, "Score requires actual int type")
                contexts.score(ref.target, ref, state.scope, state.context)
                return
            }
            val refLocation = location(ref)
            if (refLocation.executionContextId != null && (ref as OwnedCode).metadata.executionContextId != refLocation.executionContextId)
                fail(ref, "Reference lost its bound execution context")
            position(refLocation, ref.type(), state, ref, write)
        }

        private fun boolean(expr: Expr<*>, state: State): Flow {
            val flow = expression(expr, state)
            if (type(expr.type(), expr).kind != TypeKind.BOOL) fail(expr, "Condition requires actual bool type")
            return flow
        }

        private fun constantBoolean(expr: Expr<*>): Boolean? = when (val node = (expr as ImmutableExpr<*>).node) {
            is ExprNode.Literal -> (node.value as? ConstantValue.Bool)?.value
            is ExprNode.ExprBlock -> {
                val values = arrayListOf<Boolean?>()
                fun collect(block: Block) {
                    block.statements().forEach { stmt ->
                        when (val exit = (stmt as ImmutableStmt).node) {
                            is StmtNode.Yield -> if (exit.region == node.region) values += constantBoolean(exit.value)
                            is StmtNode.If -> {
                                collect(exit.thenBlock)
                                exit.elseBlock?.let(::collect)
                            }
                            is StmtNode.While -> collect(exit.body)
                            is StmtNode.ScopeBlock -> collect(exit.block)
                            is StmtNode.ExecuteAs -> collect(exit.body)
                            is StmtNode.At -> collect(exit.body)
                            is StmtNode.Rotated -> collect(exit.body)
                            is StmtNode.LocalDecl, is StmtNode.Assign, is StmtNode.Eval,
                            is StmtNode.CallbackReturn, is StmtNode.Break, is StmtNode.Continue, is StmtNode.CaptureAddress -> Unit
                        }
                    }
                }
                collect(node.block)
                values.firstOrNull()?.takeIf { candidate -> values.all { it == candidate } }
            }
            else -> null
        }

        private fun literal(value: ConstantValue, result: TypeRef<*>, code: Code) {
            val descriptor = type(result, code)
            val owner = descriptor.graph
            if (owner != null) {
                when (
                    val schema = owner.constantSchema(descriptor.key)
                        ?: fail(code, "This literal payload has no supported actual type schema")
                ) {
                    is ConstantSchema.Sequence -> {
                        val sequence = value as? ConstantValue.Sequence ?: fail(code, "Literal payload does not match its sequence schema")
                        sequence.elements.forEach { literal(it, owner.type(schema.element), code) }
                    }
                    is ConstantSchema.Record -> {
                        val record = value as? ConstantValue.Record ?: fail(code, "Literal payload does not match its record schema")
                        if (record.fields.keys != schema.fields.keys) fail(code, "Literal record fields do not match its actual type schema")
                        schema.fields.forEach { (name, key) -> literal(record.fields.getValue(name), owner.type(key), code) }
                    }
                    is ConstantSchema.Scalar -> {
                        val matches = when (schema.shape) {
                            ConstantShape.INT32 -> value is ConstantValue.Integral && value.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                            ConstantShape.BOOL -> value is ConstantValue.Bool
                            ConstantShape.FLOAT32 -> value is ConstantValue.FloatBits
                            ConstantShape.FLOAT64 -> value is ConstantValue.DoubleBits
                            ConstantShape.STRING -> value is ConstantValue.Text
                            ConstantShape.NULL -> value === ConstantValue.NullValue
                            ConstantShape.TYPE ->
                                value is ConstantValue.TypeValue &&
                                    run {
                                        type(value.type, code)
                                        true
                                    }
                            ConstantShape.NBT ->
                                value is ConstantValue.Nbt &&
                                    run {
                                        try {
                                            top.mcfpp.nbt.tags.Tag.toNBT(value.snbt)
                                            true
                                        } catch (_: Exception) {
                                            fail(code, "Literal NBT payload is malformed")
                                        }
                                    }
                        }
                        if (!matches) fail(code, "Literal payload does not match its actual type schema")
                    }
                }
                return
            }
            val expected = when (value) {
                is ConstantValue.Integral -> {
                    if (value.value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) fail(code, "Integer literal is outside int range")
                    TypeKind.INT
                }
                is ConstantValue.Bool -> TypeKind.BOOL
                is ConstantValue.FloatBits, is ConstantValue.DoubleBits -> TypeKind.FLOAT
                is ConstantValue.Text -> TypeKind.STRING
                else -> fail(code, "This literal payload has no supported actual type schema")
            }
            if (type(result, code).kind != expected) fail(code, "Literal payload does not match its actual type")
        }

        private fun expression(expr: Expr<*>, state: State): Flow = guard(expr) {
            metadata(expr, state)
            type(expr.type(), expr)
            when (val node = (expr as ImmutableExpr<*>).node) {
                is ExprNode.Literal -> {
                    literal(node.value, expr.type(), expr)
                    Flow()
                }
                is ExprNode.Read -> {
                    nonVoid(expr.type(), expr)
                    position(node.location, expr.type(), state, expr)
                    Flow()
                }
                is ExprNode.ScoreRead -> {
                    if (type(expr.type(), expr).kind != TypeKind.INT) fail(expr, "Score read requires actual int type")
                    contexts.score(node.target, expr, state.scope, state.context)
                    Flow()
                }
                is ExprNode.BlockMatches -> {
                    if (type(expr.type(), expr).kind != TypeKind.BOOL) fail(expr, "Block test requires actual bool type")
                    contexts.block(node.block, state.scope, state.context)
                    Flow()
                }
                is ExprNode.Unary -> {
                    val flow = expression(node.operand, state)
                    val kind = type(node.operand.type(), expr).kind
                    if (node.operator == UnaryOperator.NOT) {
                        if (kind != TypeKind.BOOL || type(expr.type(), expr).kind != TypeKind.BOOL) fail(expr, "Logical negation requires actual bool type")
                    } else {
                        if (kind !in setOf(TypeKind.INT, TypeKind.FLOAT)) fail(expr, "Numeric negation requires a numeric actual type")
                        same(node.operand.type(), expr.type(), expr)
                    }
                    flow
                }
                is ExprNode.Binary -> {
                    val leftFlow = expression(node.left, state)
                    val rightFlow = expression(node.right, state)
                    same(node.left.type(), node.right.type(), expr)
                    val kind = type(node.left.type(), expr).kind
                    val arithmetic = node.operator in setOf(BinaryOperator.ADD, BinaryOperator.SUBTRACT, BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.MODULO)
                    val logical = node.operator in setOf(BinaryOperator.AND, BinaryOperator.OR)
                    val equality = node.operator in setOf(BinaryOperator.EQ, BinaryOperator.NE)
                    val flow = if (logical) {
                        val left = constantBoolean(node.left)
                        val evaluatesRight = if (node.operator == BinaryOperator.AND) left else left?.not()
                        leftFlow.then(
                            when (evaluatesRight) {
                                true -> rightFlow
                                false -> Flow()
                                null -> Flow().or(rightFlow)
                            }
                        )
                    } else leftFlow.then(rightFlow)
                    val allowed = when {
                        logical -> setOf(TypeKind.BOOL)
                        equality -> setOf(TypeKind.INT, TypeKind.FLOAT, TypeKind.BOOL, TypeKind.STRING)
                        node.operator == BinaryOperator.ADD -> setOf(TypeKind.INT, TypeKind.FLOAT, TypeKind.STRING)
                        else -> setOf(TypeKind.INT, TypeKind.FLOAT)
                    }
                    if (kind !in allowed) fail(expr, "Operator does not support this actual type")
                    if (arithmetic) same(node.left.type(), expr.type(), expr)
                    else if (type(expr.type(), expr).kind != TypeKind.BOOL) fail(expr, "Comparison or logical result requires actual bool type")
                    flow
                }
                is ExprNode.Convert -> {
                    val flow = expression(node.value, state)
                    same(node.target, expr.type(), expr)
                    val from = type(node.value.type(), expr)
                    val to = type(node.target, expr)
                    val compatible = if (to.graph != null) to.graph.permits(from.key, to.key, node.kind) else
                        node.target.isAssignableFrom(node.value.type()) ||
                            (node.kind == ConversionKind.EXPLICIT && from.kind in setOf(TypeKind.INT, TypeKind.FLOAT) && to.kind in setOf(TypeKind.INT, TypeKind.FLOAT))
                    if (!compatible || to.kind == TypeKind.VOID || from.kind == TypeKind.VOID) fail(expr, "Unsupported actual type conversion")
                    flow
                }
                is ExprNode.Field -> {
                    val flow = expression(node.receiver, state)
                    same(field(node.receiver.type(), node.field, expr).type(), expr.type(), expr)
                    flow
                }
                is ExprNode.Index -> {
                    val receiver = expression(node.receiver, state)
                    val index = expression(node.index, state)
                    if (type(node.index.type(), expr).kind != TypeKind.INT) fail(expr, "Index requires actual int type")
                    val descriptor = type(node.receiver.type(), expr)
                    val schema = descriptor.graph?.index(descriptor.key) ?: fail(expr, "Index is not supported by its actual type schema")
                    same(requireNotNull(descriptor.graph).type(schema.element), expr.type(), expr)
                    receiver.then(index)
                }
                is ExprNode.Call -> call(
                    node.function,
                    node.arguments.map { it.payload },
                    expr,
                    state,
                    macro = false,
                    readonlyArguments = node.readonlyArguments,
                    receiver = node.receiver
                )
                is ExprNode.MacroCall -> {
                    if (phase == ValidationPhase.FINAL) fail(expr, "MacroCall remains after recursive expansion")
                    call(node.function, node.arguments.map { it.payload }, expr, state, macro = true)
                }
                is ExprNode.ExprBlock -> {
                    nonVoid(node.resultType, expr)
                    same(node.resultType, expr.type(), expr)
                    bindRegion(node.region, node, expr)
                    val target = ValidationRegion(node.region, node.resultType)
                    val flow = block(node.block, state.copy(yields = state.yields + target))
                    if (flow.normal) fail(expr, "Expression region has a normal exit without yield")
                    val exit = Exit(ExitKind.YIELD, node.region)
                    Flow(exit in flow.exits, flow.exits - exit)
                }
                is ExprNode.InlineCall -> {
                    if (phase == ValidationPhase.FINAL) fail(expr, "InlineCall remains after recursive expansion")
                    callback(node.callback, state)
                    same(node.callback.signature().returnType(), expr.type(), expr)
                    arguments(node.callback.signature().parameters(), node.arguments.map { it.payload }, state, expr)
                }
            }
        }

        private fun signature(expected: InlineSignature<*>, actual: InlineSignature<*>, code: Code) {
            val compared = comparedSignatures.getOrPut(expected) {
                java.util.Collections.newSetFromMap(IdentityHashMap<InlineSignature<*>, Boolean>())
            }
            if (!compared.add(actual)) return
            same(expected.returnType(), actual.returnType(), code)
            parameters(expected.parameters(), actual.parameters(), code)
        }

        private fun parameters(expected: List<ParameterView>, actual: List<ParameterView>, code: Code) {
            if (expected.size != actual.size) fail(code, "Inline signature parameter count differs")
            expected.zip(actual).forEach { (left, right) ->
                if (left.mode() != right.mode()) fail(code, "Inline signature parameter mode differs")
                same(left.type(), right.type(), code)
                if (left.mode() == ParamMode.INLINE) {
                    val required = left.inlineSignature() ?: fail(code, "INLINE parameter has no nested signature descriptor")
                    val provided = right.inlineSignature() ?: fail(code, "INLINE parameter has no nested signature descriptor")
                    signature(required, provided, code)
                }
            }
        }

        private fun arguments(parameters: List<ParameterView>, supplied: List<ArgumentPayload>, state: State, code: Code): Flow {
            if (parameters.size != supplied.size) fail(code, "Argument count differs from the actual signature")
            var flow = Flow()
            parameters.zip(supplied).forEach { (parameter, payload) ->
                if (parameter.mode() != payload.mode) fail(code, "Argument mode differs from the actual signature")
                type(parameter.type(), code)
                flow = flow.then(
                    when (payload) {
                        is ArgumentPayload.Value -> {
                            nonVoid(payload.expression.type(), code)
                            assignable(parameter.type(), payload.expression.type(), code)
                            expression(payload.expression, state)
                        }
                        is ArgumentPayload.Reference -> {
                            reference(payload.reference, state, write = true)
                            same(parameter.type(), payload.reference.type(), code)
                            Flow()
                        }
                        is ArgumentPayload.Constant -> {
                            assignable(parameter.type(), payload.constant.type(), code)
                            metadata(payload.constant, state)
                            literal(payload.constant.value(), payload.constant.type(), payload.constant)
                            expression(payload.constant.expr(), state)
                        }
                        is ArgumentPayload.Inline -> {
                            val required = parameter.inlineSignature() ?: fail(code, "INLINE parameter has no nested signature descriptor")
                            signature(required, payload.callback.signature(), code)
                            callback(payload.callback, state)
                            Flow()
                        }
                    }
                )
            }
            return flow
        }

        private fun boundTarget(function: FunctionRef<*>, available: List<FunctionRef<*>>, code: Code): ImmutableFunctionRef<*> {
            val descriptor = function as ImmutableFunctionRef<*>
            if (descriptor.sessionId != null && descriptor.sessionId != env.arena.sessionId) fail(code, "Function belongs to another expansion session")
            val bound = available.filterIsInstance<ImmutableFunctionRef<*>>().firstOrNull { it.key == descriptor.key }
                ?: fail(code, "Function target is not bound in the validation environment")
            if (bound.kind != descriptor.kind || bound.sessionId != descriptor.sessionId) fail(code, "Function target descriptor is inconsistent")
            signature(bound.signature, descriptor.signature, code)
            parameters(bound.readonlyParameters(), descriptor.readonlyParameters(), code)
            return bound
        }

        private fun call(
            function: FunctionRef<*>,
            supplied: List<ArgumentPayload>,
            code: Expr<*>,
            state: State,
            macro: Boolean,
            readonlyArguments: List<TypedConstant> = emptyList(),
            receiver: Ref<*>? = null
        ): Flow {
            val bound = boundTarget(function, env.functions, code)
            if ((bound.kind == FunctionKind.MACRO) != macro) fail(code, "Call node does not match the actual target kind")
            // The actual receiver is a REF capability: it must belong to this session and lexical
            // scope, keep its declared actual type/context, and denote a dominating location.
            receiver?.let { reference(it, state) }
            val readonly = bound.readonlyParameters()
            if (readonlyArguments.size != readonly.size) {
                val source = readonlyArguments.getOrNull(readonly.size)?.source ?: code.source()
                throw CodeModelException(source, "Readonly argument count differs from the actual signature")
            }
            if (bound.kind in setOf(FunctionKind.HELPER, FunctionKind.MACRO) && readonly.isNotEmpty())
                fail(code, "This target kind cannot have readonly parameters")
            readonly.zip(readonlyArguments).forEach { (parameter, argument) ->
                try {
                    if (parameter.mode() !in setOf(ParamMode.VALUE, ParamMode.CONST)) fail(code, "Readonly parameter requires a closed value mode")
                    nonVoid(parameter.type(), code)
                    same(parameter.type(), argument.type, code)
                    literal(argument.value, argument.type, code)
                } catch (error: CodeModelException) {
                    throw CodeModelException(argument.source, error.message ?: "Invalid readonly argument")
                }
            }
            val allowed = when (bound.kind) {
                FunctionKind.ORDINARY, FunctionKind.NATIVE -> setOf(ParamMode.VALUE)
                FunctionKind.HELPER -> setOf(ParamMode.VALUE, ParamMode.REF)
                FunctionKind.MACRO -> ParamMode.entries.toSet()
            }
            if (bound.parameters().any { it.mode() !in allowed }) fail(code, "Target kind does not support these parameter modes")
            same(bound.returnType(), code.type(), code)
            return arguments(bound.parameters(), supplied, state, code)
        }

        private fun callback(callback: InlineFn<*>, state: State) {
            metadata(callback, state)
            val descriptor = callback as ImmutableInlineFn<*>
            if (callback.signature().parameters().any { it.mode() == ParamMode.INLINE }) fail(callback, "A callback cannot accept another inline callback")
            if (!activeCallbacks.add(descriptor.callbackId)) fail(callback, "Recursive inline callback body")
            try {
                when (val target = descriptor.target) {
                    is InlineTarget.Declared -> {
                        val function = env.inlineTargets.filterIsInstance<ImmutableFunctionRef<*>>().firstOrNull { it.key == target.function }
                            ?: fail(callback, "Inline target has no registered static body")
                        boundTarget(function, env.inlineTargets, callback)
                        if (function.readonlyParameters().isNotEmpty())
                            fail(callback, "Readonly function parameters are not supported by validation")
                        if (function.kind !in setOf(FunctionKind.ORDINARY, FunctionKind.HELPER)) fail(callback, "Native or macro target cannot be used as a static inline body")
                        signature(function.signature, callback.signature(), callback)
                    }
                    is InlineTarget.Body -> {
                        if (target.deferred != null) {
                            if (phase == ValidationPhase.FINAL)
                                fail(callback, "Deferred inline source body remains after recursive expansion")
                            // The deferred body is intentionally opaque until an actual insertion
                            // supplies its CONST facts. Validate the descriptor capability graph,
                            // never a guessed or partially bound statement tree.
                            signature(target.deferred.signature, callback.signature(), callback)
                            if (target.parameters.size != callback.signature().parameters().size ||
                                target.parameters.distinct().size != target.parameters.size
                            )
                                fail(callback, "Deferred callback parameter identities do not match its signature")
                            if (target.parameters.any { it in target.captures })
                                fail(callback, "A callback parameter cannot also be a capture")
                            if (target.parameters.any { it.session != env.arena.sessionId })
                                fail(callback, "Deferred callback parameter belongs to another session")
                            if (target.captureReferences.keys != target.captures)
                                fail(callback, "Deferred inline capture capability is missing or incomplete")
                            val bindings = state.bindings.filter { it.location.root in target.captures }.toMutableList()
                            target.captures.forEach { symbol ->
                                if (bindings.none { it.location.root == symbol && env.arena.isVisible(it.scope, state.scope) })
                                    fail(callback, "Callback captures an unavailable symbol")
                            }
                            target.deferredCaptures.values.forEach { reference(it, state) }
                            bindRegion(target.returnRegion, target, callback)
                            return
                        }
                        if (target.parameters.size != callback.signature().parameters().size || target.parameters.distinct().size != target.parameters.size)
                            fail(callback, "Callback parameter identities do not match its signature")
                        if (target.parameters.any { it in target.captures }) fail(callback, "A callback parameter cannot also be a capture")
                        val bodyScope = (target.block as OwnedCode).metadata.scopeId
                        val bindings = state.bindings.filter { it.location.root in target.captures }.toMutableList()
                        target.captures.forEach { symbol ->
                            if (bindings.none { it.location.root == symbol && env.arena.isVisible(it.scope, state.scope) }) fail(callback, "Callback captures an unavailable symbol")
                        }
                        callback.signature().parameters().zip(target.parameters).forEach { (parameter, symbol) ->
                            if (symbol.session != env.arena.sessionId) fail(callback, "Callback parameter belongs to another session")
                            nonVoid(parameter.type(), callback)
                            bindings += Binding(NodeLocation(symbol, executionContextId = state.context, writable = parameter.mode() != ParamMode.CONST), parameter.type(), bodyScope)
                        }
                        bindRegion(target.returnRegion, target, callback)
                        val returnTarget = ValidationRegion(target.returnRegion, callback.signature().returnType(), callback = true)
                        val flow = block(
                            target.block,
                            state.copy(
                                scope = descriptor.metadata.scopeId,
                                bindings = bindings,
                                yields = emptyList(),
                                callback = returnTarget,
                                loops = emptyList(),
                                addresses = state.addresses.filterValues { it.root in target.captures }.toMutableMap()
                            )
                        )
                        if (flow.normal && type(returnTarget.type, callback).kind != TypeKind.VOID) fail(callback, "Non-void callback has a normal exit without return")
                        if (flow.exits.any { it != Exit(ExitKind.RETURN, target.returnRegion) }) fail(callback, "Control exit crosses a callback boundary")
                    }
                }
            } finally {
                activeCallbacks.remove(descriptor.callbackId)
            }
        }

        private fun block(block: Block, parent: State, nested: Boolean = true): Flow = guard(block) {
            metadata(block, parent, nested)
            val scope = (block as OwnedCode).metadata.scopeId
            val state = parent.child(scope)
            var flow = Flow()
            block.statements().forEach { child ->
                if (!flow.normal) fail(child, "Code follows a terminal control exit")
                flow = flow.then(statement(child as ImmutableStmt, state))
            }
            flow
        }

        private fun statement(stmt: ImmutableStmt, state: State): Flow = guard(stmt) {
            metadata(stmt, state)
            if (stmt.metadata.scopeId != state.scope) fail(stmt, "Statement is not in its owning lexical block")
            when (val node = stmt.node) {
                is StmtNode.ExecuteAs, is StmtNode.At, is StmtNode.Rotated -> {
                    val child = contexts.transition(stmt, state.context, state.scope)
                    val body = when (node) {
                        is StmtNode.ExecuteAs -> node.body
                        is StmtNode.At -> node.body
                        is StmtNode.Rotated -> node.body
                        else -> error("Context statement")
                    }
                    val flow = block(body, state.copy(context = child.id))
                    if (node is StmtNode.ExecuteAs) Flow(true, flow.exits) else flow
                }
                is StmtNode.CaptureAddress -> {
                    if (node.address.session != env.arena.sessionId) fail(stmt, "Captured address belongs to another session")
                    if (node.address in state.addresses) fail(stmt, "Duplicate captured address declaration")
                    val previous = addressDeclarations.putIfAbsent(node.address, stmt)
                    if (previous != null && previous !== stmt) fail(stmt, "Duplicate captured address declaration")
                    nonVoid(node.type, stmt)
                    val resolved = position(node.location, node.type, state, stmt)
                    state.addresses[node.address] = Address(resolved.type, resolved.writable, node.location.root, state.scope, node.location.executionContextId)
                    Flow()
                }
                is StmtNode.LocalDecl -> {
                    val local = node.local as ImmutableLocal<*>
                    metadata(local, state)
                    if (local.metadata.scopeId != state.scope) fail(stmt, "Local declaration is outside its owning block")
                    if (local.location.capturedBase != null) fail(stmt, "Captured base addresses are not supported by validation")
                    if (state.bindings.any { it.location.root == local.symbol }) fail(stmt, "Duplicate local declaration")
                    val previous = declarations.putIfAbsent(local.symbol, stmt)
                    if (previous != null && previous !== stmt) fail(stmt, "Local identity is reused by another declaration")
                    nonVoid(local.type(), stmt)
                    assignable(local.type(), node.initialValue.type(), stmt)
                    val flow = expression(node.initialValue, state)
                    state.bindings += Binding(local.location, local.type(), state.scope)
                    flow
                }
                is StmtNode.Assign -> {
                    reference(node.destination, state, write = true)
                    nonVoid(node.expression.type(), stmt)
                    assignable(node.destination.type(), node.expression.type(), stmt)
                    expression(node.expression, state)
                }
                is StmtNode.Eval -> expression(node.expression, state)
                is StmtNode.If -> {
                    val condition = boolean(node.condition, state)
                    val yes = block(node.thenBlock, state)
                    val no = node.elseBlock?.let { block(it, state) } ?: Flow()
                    condition.then(
                        when (constantBoolean(node.condition)) {
                            true -> yes
                            false -> no
                            null -> yes.or(no)
                        }
                    )
                }
                is StmtNode.While -> {
                    bindRegion(node.region, node, stmt)
                    val condition = boolean(node.condition, state)
                    val body = block(node.body, state.copy(loops = state.loops + node.region))
                    val breakExit = Exit(ExitKind.BREAK, node.region)
                    val continueExit = Exit(ExitKind.CONTINUE, node.region)
                    val runs = constantBoolean(node.condition) != false
                    Flow(
                        condition.normal && (constantBoolean(node.condition) != true || breakExit in body.exits),
                        condition.exits + if (runs) body.exits - breakExit - continueExit else emptySet()
                    )
                }
                is StmtNode.ScopeBlock -> block(node.block, state)
                is StmtNode.Yield -> {
                    val target = state.yields.lastOrNull() ?: fail(stmt, "Yield is outside an expression result region")
                    if (target.id != node.region) fail(stmt, "Yield crosses an expression region boundary")
                    nonVoid(node.value.type(), stmt)
                    assignable(target.type, node.value.type(), stmt)
                    expression(node.value, state).then(Flow(false, setOf(Exit(ExitKind.YIELD, node.region))))
                }
                is StmtNode.CallbackReturn -> {
                    val target = state.callback ?: fail(stmt, "Callback return is outside a callback region")
                    if (target.id != node.region) fail(stmt, "Return crosses a callback boundary")
                    val void = type(target.type, stmt).kind == TypeKind.VOID
                    if (void != (node.value == null)) fail(stmt, "Callback return value does not match the actual result type")
                    val value = node.value?.let {
                        assignable(target.type, it.type(), stmt)
                        expression(it, state)
                    } ?: Flow()
                    value.then(Flow(false, setOf(Exit(ExitKind.RETURN, node.region))))
                }
                is StmtNode.Break -> {
                    if (state.loops.lastOrNull() != node.region) fail(stmt, "Break escapes its current callback loop")
                    Flow(false, setOf(Exit(ExitKind.BREAK, node.region)))
                }
                is StmtNode.Continue -> {
                    if (state.loops.lastOrNull() != node.region) fail(stmt, "Continue escapes its current callback loop")
                    Flow(false, setOf(Exit(ExitKind.CONTINUE, node.region)))
                }
            }
        }
    }
}
