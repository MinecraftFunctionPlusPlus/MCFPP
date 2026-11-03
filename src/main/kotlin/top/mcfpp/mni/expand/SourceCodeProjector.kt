package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.util.IdentityHashMap

internal class ProjectedLocation(statements: List<Stmt>, val reference: Ref<*>) {
    val statements = immutableList(statements)
}

/**
 * The prelude and its argument handles must be imported as one lexical unit.
 *
 * [receiver] is the already-captured actual member receiver. It is null for global/
 * static calls; a member call must keep it separate from the normal argument list.
 */
internal class ProjectedCallArguments(
    val orderedPrelude: Block,
    arguments: List<CallArgument>,
    readonlyArguments: List<TypedConstant>,
    val scope: ScopeId,
    val receiver: Ref<*>? = null
) {
    val arguments = immutableList(arguments)
    val readonlyArguments = immutableList(readonlyArguments)
}

/** Pure projection of an already selected source tree. No compiler expression visitor is used. */
internal class SourceCodeProjector(val environment: SourceProjectionEnvironment) {
    private val session get() = environment.session
    private val arena get() = session.arena
    private val types get() = environment.types
    private val defaults = SourceDefaultBinder()

    private data class Frame(
        val lexical: BindingEnvironment,
        val formals: Map<SymbolId, SourceSymbol> = emptyMap(),
        val available: Set<SymbolId> = emptySet(),
        val references: Map<SymbolId, Ref<*>> = emptyMap(),
        val values: Map<SymbolId, Expr<*>> = emptyMap(),
        val proofs: Map<SymbolId, BoundExpr> = emptyMap()
    )

    fun expression(value: BoundExpr, scope: ScopeId = environment.rootScope): Expr<*> = environment.checked(value.source) {
        scope(scope, value.source)
        Preflight().expression(value, Frame(environment.binding))
        expression(value, scope, Frame(environment.binding))
    }

    fun captureLocation(location: BoundLocation, scope: ScopeId = environment.rootScope): ProjectedLocation = environment.checked(location.source) {
        scope(scope, location.source)
        Preflight().location(location, Frame(environment.binding), writable = true)
        capture(location, scope, Frame(environment.binding))
    }

    fun arguments(call: SourceCall, scope: ScopeId = environment.rootScope): ProjectedCallArguments = environment.checked(call.source) {
        scope(scope, call.source)
        Preflight().call(call, Frame(environment.binding))
        arguments(call, scope, Frame(environment.binding))
    }

    fun call(call: SourceCall, scope: ScopeId = environment.rootScope): Code = environment.checked(call.source) {
        scope(scope, call.source)
        Preflight().call(call, Frame(environment.binding))
        call(call, scope, Frame(environment.binding))
    }

    private fun fail(source: SourceSpan, message: String): Nothing = throw SourceBindingFailure(source, message)
    /** Same effect join used by the source binder, retaining its conservative ordering. */
    private fun combine(left: BoundEffect, right: BoundEffect): BoundEffect = when {
        left == BoundEffect.UNKNOWN || right == BoundEffect.UNKNOWN -> BoundEffect.UNKNOWN
        left == BoundEffect.WRITE || right == BoundEffect.WRITE -> BoundEffect.WRITE
        left == BoundEffect.CALL || right == BoundEffect.CALL -> BoundEffect.CALL
        left == BoundEffect.READ || right == BoundEffect.READ -> BoundEffect.READ
        else -> BoundEffect.PURE
    }
    private fun scope(scope: ScopeId, source: SourceSpan) {
        if (scope.session != session.id || !arena.containsScope(scope)) fail(source, "Projection scope belongs to another session")
    }
    private fun metadata(scope: ScopeId, source: SourceSpan) = arena.metadata(
        scope,
        Origin(session.origin.callSite, source, session.origin.expansionFrames),
        environment.executionContext
    )
    private fun kind(type: TypeRef<*>) = (type as ImmutableTypeRef<*>).kind
    private fun type(type: TypeRef<*>, source: SourceSpan) {
        if (!types.owns(type)) fail(source, "Projection type belongs to another canonical source catalog")
    }
    private fun same(actual: TypeRef<*>, expected: TypeRef<*>, source: SourceSpan) {
        type(actual, source)
        type(expected, source)
        if (!actual.sameType(expected)) fail(source, "Projected type differs from the bound source type")
    }
    private fun symbol(symbol: SourceSymbol, frame: Frame, source: SourceSpan) {
        type(symbol.type, source)
        val formal = frame.formals[symbol.symbol]
        if (formal != null) {
            if (formal != symbol || symbol.symbol !in frame.available) fail(source, "Default refers to an unavailable declaration formal")
        } else if (frame.lexical.symbols.values.none { it == symbol })
            fail(source, "Source symbol is outside its original declaration environment")
        if (symbol.symbol !in frame.available && symbol.symbol !in environment.roots && symbol.constant == null)
            fail(source, "Source symbol has no enrolled physical location")
    }
    private fun declarationFrame(function: SourceFunction): Frame = Frame(
        environment.declaration(function),
        (function.readonlySymbols + function.normalSymbols).associateBy { it.symbol }
    )

    private fun readonly(call: SourceCall, frame: Frame): List<BoundExpr> {
        val explicit = call.readonlyArguments.map { closed(it, frame) }
        val values = defaults.completeReadonly(call.target, explicit, types, call.source)
        var declared = declarationFrame(call.target)
        values.forEachIndexed { index, value ->
            if (index >= explicit.size) call.target.readonlyDefaultExpressions[index]?.let { closed(it, declared) }
            call.target.readonlySymbols.getOrNull(index)?.let { formal ->
                declared = declared.copy(
                    available = declared.available + formal.symbol,
                    proofs = declared.proofs + (formal.symbol to value)
                )
            }
        }
        val prepared = call.target.preparedReadonly
        if (prepared != null &&
            (
                prepared.arguments.size != values.size ||
                    prepared.arguments.zip(values).any { (a, b) ->
                        !a.type.sameType(b.type) || a.value != b.constant
                    }
                )
        ) fail(call.source, "Readonly facts differ from the prepared concrete target")
        return values
    }

    /** Evaluate only immutable constant proofs. A constant annotation never authorizes a call. */
    private fun closed(value: BoundExpr, frame: Frame): BoundExpr {
        type(value.type, value.source)
        if (value.effect in setOf(BoundEffect.CALL, BoundEffect.WRITE, BoundEffect.UNKNOWN))
            fail(value.source, "Closed argument has an executable effect")
        val proof = when (val node = value.node) {
            is BoundExprNode.Literal -> node.value
            is BoundExprNode.Read -> {
                symbol(node.location.root, frame, value.source)
                if (node.location.path.isNotEmpty()) fail(value.source, "Closed argument cannot read a dynamic path")
                frame.proofs[node.location.root.symbol]?.constant ?: node.location.root.constant
            }
            is BoundExprNode.Unary -> SourceConstantFacts.unary(node.operator, closed(node.operand, frame).constant)
            is BoundExprNode.Binary -> {
                // Inspect both operands even for a short circuit: contradictory effect proofs are invalid.
                val left = closed(node.left, frame)
                val right = closed(node.right, frame)
                SourceConstantFacts.binary(node.operator, types.facts(value.type).id, left.constant, right.constant)
            }
            is BoundExprNode.Convert -> SourceConstantFacts.convert(
                closed(node.value, frame).constant,
                node.value.type,
                node.target,
                types,
                widening = false
            )
            else -> fail(value.source, "Argument has no closed constant proof")
        } ?: fail(value.source, "Argument has no closed constant proof")
        if (value.constant != null && value.constant != proof) fail(value.source, "Constant proof contradicts its bound expression")
        SourceConstantFacts.validateClosed(value.type, proof, types, value.source)
        return BoundExpr(BoundExprNode.Literal(proof), value.type, proof, BoundEffect.PURE, value.source)
    }

    private fun constant(value: BoundExpr, expected: TypeRef<*>, frame: Frame): BoundExpr {
        val proof = closed(value, frame)
        if (types.conversionRank(proof.type, expected) == null) fail(value.source, "Closed argument type differs from its parameter")
        val result = SourceConstantFacts.convert(proof.constant, proof.type, expected, types)
            ?: fail(value.source, "Closed argument conversion has no constant proof")
        SourceConstantFacts.validateClosed(expected, result, types, value.source)
        return BoundExpr(BoundExprNode.Literal(result), expected, result, BoundEffect.PURE, value.source)
    }

    /** Validate dependencies, defaults and modes before allocating any part of a projection. */
    private inner class Preflight {
        private val active = java.util.Collections.newSetFromMap(IdentityHashMap<BoundExpr, Boolean>())
        private val defaultStack = hashSetOf<Pair<FunctionKey, Int>>()

        fun expression(value: BoundExpr, frame: Frame) {
            if (!active.add(value)) fail(value.source, "Cyclic bound source expression")
            try {
                type(value.type, value.source)
                if (kind(value.type) == TypeKind.VOID) fail(value.source, "Void call cannot be used as a source value")
                when (val node = value.node) {
                    is BoundExprNode.Literal -> SourceConstantFacts.validateClosed(value.type, node.value, types, value.source)
                    is BoundExprNode.Read -> {
                        location(node.location, frame)
                        same(node.location.type, value.type, value.source)
                    }
                    is BoundExprNode.Unary -> expression(node.operand, frame)
                    is BoundExprNode.Binary -> {
                        expression(node.left, frame)
                        expression(node.right, frame)
                    }
                    is BoundExprNode.Convert -> {
                        expression(node.value, frame)
                        same(node.target, value.type, value.source)
                        if (!types.permits(node.value.type, node.target, ConversionKind.EXPLICIT)) fail(value.source, "Unproven explicit conversion")
                    }
                    is BoundExprNode.Field -> {
                        expression(node.receiver, frame)
                        same(environment.field(node.field, node.receiver.type, frame.lexical, value.source).type(), value.type, value.source)
                    }
                    is BoundExprNode.Index -> {
                        expression(node.receiver, frame)
                        expression(node.index, frame)
                        same(index(node.receiver.type, node.index, value.source).first, value.type, value.source)
                    }
                    is BoundExprNode.Call -> {
                        call(node.sourceCall, frame)
                        same(node.sourceCall.target.function.returnType(), value.type, value.source)
                    }
                    is BoundExprNode.Unsupported -> fail(value.source, "Unsupported bound source expression '${node.sourceText}'")
                }
            } finally {
                active.remove(value)
            }
        }

        fun location(location: BoundLocation, frame: Frame, writable: Boolean = false) {
            symbol(location.root, frame, location.source)
            if (location.executionContextId != (location.root.executionContextId ?: frame.lexical.executionContextId))
                fail(location.source, "Source location changes its original execution context")
            var actual = location.root.type
            var mutable = location.root.writable
            for (step in location.path) when (step) {
                is BoundLocationStep.Field -> {
                    val field = environment.field(step.declaration, actual, frame.lexical, location.source)
                    actual = field.type()
                    mutable = mutable && field.writable()
                }
                is BoundLocationStep.Index -> {
                    expression(step.expression, frame)
                    val schema = index(actual, step.expression, location.source)
                    actual = schema.first
                    mutable = mutable && schema.second
                    step.constantIndex?.let { constant ->
                        if (step.expression.constant != ConstantValue.Integral(constant.toLong()))
                            fail(step.expression.source, "Constant index contradicts the bound expression")
                    }
                }
            }
            same(actual, location.type, location.source)
            if (location.writable && !mutable) fail(location.source, "Location widens a read-only source capability")
            if (writable && (!location.writable || !mutable || location.root.symbol in frame.values))
                fail(location.source, "REF requires an original writable source location")
        }

        fun call(call: SourceCall, frame: Frame) {
            val target = environment.target(call, frame.lexical)
            val function = target.function as ImmutableFunctionRef<*>
            val parameters = function.parameters()
            type(function.returnType(), call.source)
            parameters.forEach { type(it.type(), call.source) }
            order(call)
            if (function.kind !in setOf(FunctionKind.ORDINARY, FunctionKind.NATIVE, FunctionKind.MACRO))
                fail(call.source, "Source call has no prepared compiler target")
            if (function.kind != FunctionKind.MACRO && parameters.any { it.mode() != ParamMode.VALUE })
                fail(call.source, "Ordinary and native calls require the VALUE argument protocol")
            call.receiver?.let { receiver ->
                if (function.kind == FunctionKind.MACRO)
                    fail(call.source, "Member macro calls are not registered by the source declaration view")
                location(receiver, frame)
            }
            if (call.arguments.size > parameters.size) fail(call.source, "Too many normal arguments")
            var declared = declarationFrame(target)
            readonly(call, frame).forEachIndexed { i, value ->
                target.readonlySymbols.getOrNull(i)?.let { formal ->
                    declared = declared.copy(
                        available = declared.available + formal.symbol,
                        proofs = declared.proofs + (formal.symbol to value)
                    )
                }
            }
            parameters.forEachIndexed { i, parameter ->
                val explicit = call.arguments.getOrNull(i)
                val argument = explicit ?: target.normalDefaultExpressions[i]?.let { BoundCallArgument.Value(it, parameter.mode()) }
                    ?: fail(call.source, "Normal parameter $i has no bound default template")
                val input = if (explicit == null) declared else frame
                val key = function.key to i
                if (explicit == null && !defaultStack.add(key)) fail(argument.source, "Cyclic declaration default expansion")
                try {
                    var proof: BoundExpr? = null
                    when (argument) {
                        is BoundCallArgument.Value -> {
                            if (argument.mode != parameter.mode() || parameter.mode() !in setOf(ParamMode.VALUE, ParamMode.CONST))
                                fail(argument.source, "Argument mode differs from the selected signature")
                            expression(argument.expression, input)
                            if (types.conversionRank(argument.expression.type, parameter.type()) == null)
                                fail(argument.source, "Argument type differs from the selected signature")
                            if (parameter.mode() == ParamMode.CONST)
                                proof = constant(argument.expression, parameter.type(), input)
                            else if (argument.expression.constant != null && argument.expression.effect <= BoundEffect.READ)
                                proof = constant(argument.expression, parameter.type(), input)
                        }
                        is BoundCallArgument.Reference -> {
                            if (parameter.mode() != ParamMode.REF || function.kind != FunctionKind.MACRO)
                                fail(argument.source, "Only macro REF parameters accept source references")
                            location(argument.location, input, writable = true)
                            same(argument.location.type, parameter.type(), argument.source)
                        }
                        is BoundCallArgument.Callback -> {
                            if (parameter.mode() != ParamMode.INLINE || function.kind != FunctionKind.MACRO)
                                fail(argument.source, "Only macro INLINE parameters accept source callbacks")
                            val expected = parameter.inlineSignature() ?: parameter.type().inlineSignature()
                                ?: fail(argument.source, "Inline parameter '${parameter.name()}' has no nested signature")
                            val expectedType = types.inlineType(expected)
                                ?: fail(argument.source, "Inline parameter signature is outside the frozen type graph")
                            type(argument.callback.callbackType, argument.source)
                            if (!expectedType.sameType(argument.callback.callbackType) ||
                                types.inlineType(argument.callback.signature)?.sameType(argument.callback.callbackType) != true
                            )
                                fail(argument.source, "Callback signature differs from the selected inline parameter")
                        }
                    }
                    target.normalSymbols.getOrNull(i)?.let { formal ->
                        declared = declared.copy(
                            available = declared.available + formal.symbol,
                            proofs = proof?.let { declared.proofs + (formal.symbol to it) } ?: declared.proofs
                        )
                    }
                } finally {
                    if (explicit == null) defaultStack.remove(key)
                }
            }
        }
    }

    private fun index(receiver: TypeRef<*>, expression: BoundExpr, source: SourceSpan): Pair<TypeRef<*>, Boolean> {
        type(receiver, source)
        type(expression.type, expression.source)
        if (kind(expression.type) != TypeKind.INT) fail(expression.source, "Index requires the canonical int type")
        val schema = types.indexSchema(receiver) ?: fail(source, "Receiver has no frozen index schema")
        return types.graph.type(schema.element) to schema.writable
    }

    private fun order(call: SourceCall) {
        val expected = call.readonlyArguments.map(SourceEvaluationArgument::Readonly) + call.arguments.map(SourceEvaluationArgument::Normal)
        if (expected.size != call.evaluationOrder.size ||
            expected.zip(call.evaluationOrder).any { (a, b) ->
                when {
                    a is SourceEvaluationArgument.Readonly && b is SourceEvaluationArgument.Readonly -> a.expression !== b.expression
                    a is SourceEvaluationArgument.Normal && b is SourceEvaluationArgument.Normal -> a.argument !== b.argument
                    else -> true
                }
            }
        ) fail(call.source, "Source evaluation order omits, duplicates or reorders an original argument")
        val effects = buildList {
            call.receiver?.let { add(it.effectOrder.fold(BoundEffect.READ, ::combine)) }
            addAll(call.readonlyArguments.map { it.effect })
            addAll(
                call.arguments.flatMap {
                    when (it) {
                        is BoundCallArgument.Value -> listOf(it.expression.effect)
                        is BoundCallArgument.Reference -> it.location.effectOrder
                        is BoundCallArgument.Callback -> emptyList()
                    }
                }
            )
            add(call.target.effect)
        }
        if (call.effectOrder != effects || call.effect != effects.maxByOrNull { it.ordinal })
            fail(call.source, "Source evaluation effects differ from the selected call")
    }

    private fun literal(value: BoundExpr, scope: ScopeId): Expr<*> = arena.expr(metadata(scope, value.source), value.type, ExprNode.Literal(requireNotNull(value.constant)))

    private fun convert(value: Expr<*>, expected: TypeRef<*>, scope: ScopeId, source: SourceSpan): Expr<*> = if (value.type().sameType(expected)) value else
        arena.expr(metadata(scope, source), expected, ExprNode.Convert(value, expected, ConversionKind.IMPLICIT))

    private fun expression(value: BoundExpr, scope: ScopeId, frame: Frame): Expr<*> = when (val node = value.node) {
        is BoundExprNode.Literal -> arena.expr(metadata(scope, value.source), value.type, ExprNode.Literal(node.value))
        is BoundExprNode.Read -> read(node.location, scope, frame, value.source)
        is BoundExprNode.Unary -> arena.expr(
            metadata(scope, value.source),
            value.type,
            ExprNode.Unary(node.operator, expression(node.operand, scope, frame))
        )
        is BoundExprNode.Binary -> {
            var left = expression(node.left, scope, frame)
            var right = expression(node.right, scope, frame)
            if (kind(left.type()) == TypeKind.INT && kind(right.type()) == TypeKind.FLOAT)
                left = convert(left, right.type(), scope, node.left.source)
            else if (kind(left.type()) == TypeKind.FLOAT && kind(right.type()) == TypeKind.INT)
                right = convert(right, left.type(), scope, node.right.source)
            arena.expr(metadata(scope, value.source), value.type, ExprNode.Binary(node.operator, left, right))
        }
        is BoundExprNode.Convert -> arena.expr(
            metadata(scope, value.source),
            value.type,
            ExprNode.Convert(expression(node.value, scope, frame), node.target, ConversionKind.EXPLICIT)
        )
        is BoundExprNode.Field -> arena.expr(
            metadata(scope, value.source),
            value.type,
            ExprNode.Field(expression(node.receiver, scope, frame), environment.field(node.field, node.receiver.type, frame.lexical, value.source))
        )
        is BoundExprNode.Index -> arena.expr(
            metadata(scope, value.source),
            value.type,
            ExprNode.Index(expression(node.receiver, scope, frame), expression(node.index, scope, frame))
        )
        is BoundExprNode.Call -> call(node.sourceCall, scope, frame) as Expr<*>
        is BoundExprNode.Unsupported -> fail(value.source, "Unsupported bound expression '${node.sourceText}'")
    }

    private fun nodeLocation(reference: Ref<*>): NodeLocation = when (reference) {
        is ImmutableRef<*> -> reference.location
        is ImmutableLocal<*> -> reference.location
        is ImmutableMinecraftScoreRef -> fail(reference.source(), "Source binding must enroll its original score position")
    }

    private fun read(location: BoundLocation, scope: ScopeId, frame: Frame, source: SourceSpan): Expr<*> {
        val replacement = frame.values[location.root.symbol]
            ?: location.root.constant?.takeIf { location.root.symbol !in environment.roots && location.root.symbol !in frame.references }
                ?.let { arena.expr(metadata(scope, source), location.root.type, ExprNode.Literal(it)) }
        if (replacement != null) {
            var value: Expr<*> = arena.expr(metadata(scope, source), replacement.type(), (replacement as ImmutableExpr<*>).node)
            var actual = location.root.type
            for (step in location.path) value = when (step) {
                is BoundLocationStep.Field -> {
                    val field = environment.field(step.declaration, actual, frame.lexical, source)
                    actual = field.type()
                    arena.expr(metadata(scope, source), actual, ExprNode.Field(value, field))
                }
                is BoundLocationStep.Index -> {
                    actual = index(actual, step.expression, source).first
                    arena.expr(metadata(scope, source), actual, ExprNode.Index(value, expression(step.expression, scope, frame)))
                }
            }
            return value
        }
        if (location.path.isEmpty()) {
            val root = frame.references[location.root.symbol] ?: environment.roots.getValue(location.root.symbol)
            val position = nodeLocation(root)
            return arena.expr(
                metadata(scope, source),
                location.type,
                ExprNode.Read(
                    NodeLocation(
                        position.root,
                        position.steps,
                        position.executionContextId,
                        location.writable && position.writable,
                        position.capturedBase
                    )
                )
            )
        }
        val nested = arena.childScope(scope)
        val captured = capture(location, nested, frame)
        return valueBlock(captured.statements, captured.reference.load(), scope, nested, source)
    }

    private fun capture(location: BoundLocation, scope: ScopeId, frame: Frame): ProjectedLocation {
        val root = frame.references[location.root.symbol] ?: environment.roots[location.root.symbol]
            ?: fail(location.source, "Source location has no enrolled physical root")
        var position = nodeLocation(root)
        var actual = root.type()
        var writable = position.writable && location.root.writable
        val statements = arrayListOf<Stmt>()
        fun freeze() {
            val address = arena.freshAddress()
            statements += arena.stmt(metadata(scope, location.source), StmtNode.CaptureAddress(address, position, actual))
            position = NodeLocation(
                position.root,
                executionContextId = position.executionContextId,
                writable = writable,
                capturedBase = address
            )
        }
        freeze()
        for (step in location.path) {
            val path = when (step) {
                is BoundLocationStep.Field -> {
                    val field = environment.field(step.declaration, actual, frame.lexical, location.source)
                    actual = field.type()
                    writable = writable && field.writable()
                    LocationStep.Field(field)
                }
                is BoundLocationStep.Index -> {
                    val schema = index(actual, step.expression, location.source)
                    val initializer = expression(step.expression, scope, frame)
                    val index = arena.local(metadata(scope, step.expression.source), initializer.type(), "sourceIndex")
                    statements += arena.stmt(metadata(scope, step.expression.source), StmtNode.LocalDecl(index, initializer))
                    actual = schema.first
                    writable = writable && schema.second
                    LocationStep.CapturedIndex(index.symbol)
                }
            }
            position = NodeLocation(position.root, listOf(path), position.executionContextId, writable, position.capturedBase)
            freeze()
        }
        position = NodeLocation(
            position.root,
            executionContextId = position.executionContextId,
            writable = writable && location.writable,
            capturedBase = position.capturedBase
        )
        return ProjectedLocation(statements, arena.ref(metadata(scope, location.source), location.type, position))
    }

    private fun arguments(call: SourceCall, scope: ScopeId, caller: Frame): ProjectedCallArguments {
        val target = call.target
        // A member receiver is evaluated once, before readonly/normal arguments, and its
        // frozen address is carried alongside the argument prelude.
        val receiver = call.receiver?.let { capture(it, scope, caller) }
        val readonly = readonly(call, caller)
        var declared = declarationFrame(target)
        readonly.forEachIndexed { i, value ->
            target.readonlySymbols.getOrNull(i)?.let { formal ->
                declared = declared.copy(
                    available = declared.available + formal.symbol,
                    values = declared.values + (formal.symbol to literal(value, scope)),
                    proofs = declared.proofs + (formal.symbol to value)
                )
            }
        }
        val statements = arrayListOf<Stmt>()
        receiver?.statements?.let { statements.addAll(it) }
        val arguments = target.function.parameters().mapIndexed { i, parameter ->
            val explicit = call.arguments.getOrNull(i)
            val argument = explicit ?: BoundCallArgument.Value(target.normalDefaultExpressions.getValue(i), parameter.mode())
            val input = if (explicit == null) declared else caller
            var reference: Ref<*>? = null
            var value: Expr<*>? = null
            var proof: BoundExpr? = null
            val projected = when (argument) {
                is BoundCallArgument.Value -> if (parameter.mode() == ParamMode.CONST) {
                    val closed = constant(argument.expression, parameter.type(), input)
                    val expression = literal(closed, scope)
                    proof = closed
                    value = expression
                    CallArgument.constant(arena.constant(expression, requireNotNull(closed.constant)))
                } else {
                    val initializer = convert(expression(argument.expression, scope, input), parameter.type(), scope, argument.source)
                    val local = arena.local(metadata(scope, argument.source), parameter.type(), "sourceArgument$i")
                    statements += arena.stmt(metadata(scope, argument.source), StmtNode.LocalDecl(local, initializer))
                    reference = local
                    if (argument.expression.constant != null && argument.expression.effect <= BoundEffect.READ)
                        proof = constant(argument.expression, parameter.type(), input)
                    CallArgument.value(local.load())
                }
                is BoundCallArgument.Reference -> {
                    val captured = capture(argument.location, scope, input)
                    statements.addAll(captured.statements)
                    reference = captured.reference
                    CallArgument.ref(captured.reference)
                }
                is BoundCallArgument.Callback -> {
                    val function = target.function as? ImmutableFunctionRef<*>
                        ?: fail(argument.source, "Only macro INLINE parameters accept source callbacks")
                    if (parameter.mode() != ParamMode.INLINE || function.kind != FunctionKind.MACRO)
                        fail(argument.source, "Only macro INLINE parameters accept source callbacks")
                    CallArgument.inline(callback(argument.callback, parameter, scope, statements))
                }
            }
            target.normalSymbols.getOrNull(i)?.let { formal ->
                declared = declared.copy(
                    available = declared.available + formal.symbol,
                    references = reference?.let { declared.references + (formal.symbol to it) } ?: declared.references,
                    values = value?.let { declared.values + (formal.symbol to it) } ?: declared.values,
                    proofs = proof?.let { declared.proofs + (formal.symbol to it) } ?: declared.proofs
                )
            }
            projected
        }
        return ProjectedCallArguments(
            arena.block(metadata(scope, call.source), statements),
            arguments,
            readonly.mapIndexed { i, value ->
                val closed = constant(value, target.readonlyParameters[i].type, Frame(environment.binding))
                TypedConstant(closed.type, requireNotNull(closed.constant), value.source)
            },
            scope,
            receiver?.reference
        )
    }

    /**
     * The body of an INLINE argument is never projected or executed here. The descriptor carries
     * the source request and the original capture capabilities; [InlineExpander] rebinds the body
     * with the actual CONST facts of each insertion.
     */
    private fun callback(
        value: SourceCallbackSyntax,
        parameter: ParameterView,
        scope: ScopeId,
        prelude: MutableList<Stmt>
    ): ImmutableInlineFn<*> {
        val expected = parameter.inlineSignature() ?: parameter.type().inlineSignature()
            ?: fail(value.source, "Inline parameter '${parameter.name()}' has no nested signature")
        val expectedType = types.inlineType(expected)
            ?: fail(value.source, "Inline parameter signature is outside the frozen type graph")
        type(value.callbackType, value.source)
        if (!expectedType.sameType(value.callbackType) ||
            types.inlineType(value.signature)?.sameType(value.callbackType) != true
        )
            fail(value.source, "Callback signature differs from the selected inline parameter")
        val request = SourceCallbackBodyRequest(value, environment.binding, environment.declarations)
        // Discover capture identities from the original syntax only. No statement is bound, no
        // host is invoked and no CONST formal is assumed or folded.
        val captures = LinkedHashMap<SymbolId, Ref<*>>()
        sourceCaptureManifest(request).forEach { (symbolId, symbol) ->
            // Symbols owned by the callback itself (including nested callback captures of its
            // parameters/locals) have no caller root; they are freshly bound at insertion. Only
            // genuinely free caller locations carry a capability through this descriptor.
            environment.roots[symbolId]?.let { reference ->
                if (symbol.global) captures[symbolId] = reference else {
                    val contract = types.facts(symbol.type).captureContract
                    if (contract == top.mcfpp.core.lang.LambdaCopyContract.NOT_COPYABLE ||
                        contract == top.mcfpp.core.lang.LambdaCopyContract.REFERENCE_DEFERRED)
                        fail(value.source, "Captured local '${symbol.name}' has no independent copy contract")
                    if (contract == top.mcfpp.core.lang.LambdaCopyContract.COMPILER_VALUE_SNAPSHOT) {
                        if (symbol.constant == null) fail(value.source, "Captured compiler value '${symbol.name}' requires a closed snapshot")
                    } else {
                        val local = arena.local(metadata(scope, value.source), symbol.type, "capture_${symbol.name}")
                        prelude += arena.stmt(metadata(scope, value.source), StmtNode.LocalDecl(local, reference.load()))
                        captures[symbolId] = local
                    }
                }
            }
        }
        return deferredInline(session, scope, metadata(scope, value.source), request, captures)
    }

    private fun call(call: SourceCall, scope: ScopeId, frame: Frame): Code {
        val nested = arena.childScope(scope)
        val prepared = arguments(call, nested, frame)
        val function = call.target.function as ImmutableFunctionRef<*>
        val node = if (function.kind == FunctionKind.MACRO) ExprNode.MacroCall(function, prepared.arguments)
        else ExprNode.Call(function, prepared.arguments, prepared.readonlyArguments, prepared.receiver)
        val invocation = arena.expr(metadata(nested, call.source), function.returnType(), node)
        return if (kind(function.returnType()) == TypeKind.VOID)
            arena.block(
                metadata(nested, call.source),
                prepared.orderedPrelude.statements() +
                    arena.stmt(metadata(nested, call.source), StmtNode.Eval(invocation))
            )
        else valueBlock(prepared.orderedPrelude.statements(), invocation, scope, nested, call.source)
    }

    private fun valueBlock(
        statements: List<Stmt>,
        result: Expr<*>,
        scope: ScopeId,
        nested: ScopeId,
        source: SourceSpan
    ): Expr<*> {
        val region = arena.freshRegion()
        val body = arena.block(metadata(nested, source), statements + arena.stmt(metadata(nested, source), StmtNode.Yield(region, result)))
        return arena.expr(metadata(scope, source), result.type(), ExprNode.ExprBlock(body, result.type(), region))
    }
}
