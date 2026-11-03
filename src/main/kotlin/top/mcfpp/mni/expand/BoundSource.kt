package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import top.mcfpp.type.TypeId

/** Prepared declarations use source identities, never compiler field instances or allocated graph keys. */
internal data class SourceFieldId(val owner: TypeId, val declarationKey: String) {
    init {
        require(declarationKey.isNotBlank())
    }
}
internal data class SourceFieldDeclaration(
    val declaration: SourceFieldId,
    val owner: TypeId,
    val name: String,
    val valueType: TypeId,
    val writable: Boolean,
    val source: SourceSpan
) {
    init {
        require(declaration.owner == owner && name.isNotBlank())
    }
}
/** Visibility/hiding has already been resolved by the declaration phase. */
internal data class SourceFieldView(val receiver: TypeId, val declaration: SourceFieldId)

/** Optional proofs for prepared closed values, with all edges in the same source identity domain. */
internal sealed interface SourceConstantSchema {
    data class Scalar(val shape: ConstantShape) : SourceConstantSchema
    data class Sequence(val element: TypeId) : SourceConstantSchema
    class Record(fields: Map<String, TypeId>) : SourceConstantSchema {
        val fields: Map<String, TypeId> = immutableMap(fields)
    }
}

internal enum class BoundEffect { PURE, READ, CALL, WRITE, UNKNOWN }

internal sealed interface BoundExprNode {
    data class Literal(val value: ConstantValue) : BoundExprNode
    data class Read(val location: BoundLocation) : BoundExprNode
    data class Unary(val operator: UnaryOperator, val operand: BoundExpr) : BoundExprNode
    data class Binary(val operator: BinaryOperator, val left: BoundExpr, val right: BoundExpr) : BoundExprNode
    data class Convert(val value: BoundExpr, val target: TypeRef<*>) : BoundExprNode
    data class Field(val receiver: BoundExpr, val field: SourceField) : BoundExprNode
    data class Index(val receiver: BoundExpr, val index: BoundExpr) : BoundExprNode
    data class Call(val sourceCall: SourceCall) : BoundExprNode
    data class Unsupported(val sourceText: String) : BoundExprNode
}

internal data class BoundExpr(
    val node: BoundExprNode,
    val type: TypeRef<*>,
    val constant: ConstantValue?,
    val effect: BoundEffect,
    val source: SourceSpan
)

internal data class BoundLocation(
    val root: SourceSymbol,
    val path: List<BoundLocationStep>,
    val type: TypeRef<*>,
    val writable: Boolean,
    val executionContextId: ExecutionContextId?,
    val source: SourceSpan
) {
    init {
        require(root.symbol.session == (executionContextId?.session ?: root.symbol.session))
    }
    val effectOrder: List<BoundEffect> get() = immutableList(path.mapNotNull { (it as? BoundLocationStep.Index)?.expression?.effect })
}

internal sealed interface BoundLocationStep {
    data class Field(val declaration: SourceField) : BoundLocationStep
    data class Index(val expression: BoundExpr, val constantIndex: Int?) : BoundLocationStep
}

internal sealed interface BoundCallArgument {
    val source: SourceSpan
    data class Value(
        val expression: BoundExpr,
        val mode: ParamMode = ParamMode.VALUE,
        override val source: SourceSpan = expression.source
    ) : BoundCallArgument
    data class Reference(val location: BoundLocation, override val source: SourceSpan = location.source) : BoundCallArgument
    data class Callback(val callback: SourceCallbackSyntax, override val source: SourceSpan = callback.source) : BoundCallArgument
}

/** Original argument steps only; a [SourceCall.receiver] is evaluated before this list. */
internal sealed interface SourceEvaluationArgument {
    data class Readonly(val expression: BoundExpr) : SourceEvaluationArgument
    data class Normal(val argument: BoundCallArgument) : SourceEvaluationArgument
}

internal data class SourceCall(
    val name: String,
    val target: SourceFunction,
    val readonlyArguments: List<BoundExpr>,
    val arguments: List<BoundCallArgument>,
    val source: SourceSpan,
    /**
     * Original left-to-right syntax order of readonly and normal arguments. A member
     * [receiver], when present, is evaluated once before this list.
     */
    val evaluationOrder: List<SourceEvaluationArgument>,
    /** Per-step effects in source order; a member receiver step precedes the arguments and
     *  the final entry is the selected call's effect. */
    val effectOrder: List<BoundEffect>,
    val effect: BoundEffect,
    /**
     * The actual member receiver as an original writable source location. It retains the
     * receiver's physical path, execution context and type identity without evaluating its
     * value, invoking an accessor or compiling the selected member. Null keeps global/static
     * and all existing receiverless calls source-compatible.
     */
    val receiver: BoundLocation? = null
)

internal data class SourceCallbackSyntax(
    val parameterNames: List<String>,
    val signature: InlineSignature<*>,
    val callbackType: TypeRef<*>,
    val sourceText: String,
    val source: SourceSpan,
    val functionTarget: SourceFunction? = null,
    val emptyBody: Boolean = false,
    val frozenSyntax: FrozenCallbackSyntax? = null,
    val structuredSyntax: StructuredCallbackSyntax? = null,
    val structuredRequired: Boolean = false
)

/** Original body syntax carried for a later, separate source-body binding pass. */
internal data class SourceFunctionBody(
    val sourceText: String,
    val source: SourceSpan,
    val structuredSyntax: StructuredCallbackSyntax? = null,
    val sourceBuffer: String? = null,
    val structuredRequired: Boolean = false
)

/** Freeze template containers while retaining canonical handles and formal symbol identities. */
internal class BoundExpressionSnapshot {
    private val expressions = java.util.IdentityHashMap<BoundExpr, BoundExpr>()
    private val locations = java.util.IdentityHashMap<BoundLocation, BoundLocation>()
    private val arguments = java.util.IdentityHashMap<BoundCallArgument, BoundCallArgument>()
    private val calls = java.util.IdentityHashMap<SourceCall, SourceCall>()
    private val active = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<BoundExpr, Boolean>())

    fun expression(value: BoundExpr): BoundExpr {
        expressions[value]?.let { return it }
        if (!active.add(value)) throw SourceBindingFailure(value.source, "Cyclic default expression template")
        try {
            val node = when (val node = value.node) {
                is BoundExprNode.Literal, is BoundExprNode.Unsupported -> node
                is BoundExprNode.Read -> node.copy(location = location(node.location))
                is BoundExprNode.Unary -> node.copy(operand = expression(node.operand))
                is BoundExprNode.Binary -> node.copy(left = expression(node.left), right = expression(node.right))
                is BoundExprNode.Convert -> node.copy(value = expression(node.value))
                is BoundExprNode.Field -> node.copy(receiver = expression(node.receiver))
                is BoundExprNode.Index -> node.copy(receiver = expression(node.receiver), index = expression(node.index))
                is BoundExprNode.Call -> node.copy(sourceCall = call(node.sourceCall))
            }
            return value.copy(node = node).also { expressions[value] = it }
        } finally {
            active.remove(value)
        }
    }

    private fun location(value: BoundLocation): BoundLocation = locations[value] ?: value.copy(
        path = immutableList(
            value.path.map { step ->
                when (step) {
                    is BoundLocationStep.Field -> step
                    is BoundLocationStep.Index -> step.copy(expression = expression(step.expression))
                }
            }
        )
    ).also { locations[value] = it }

    private fun argument(value: BoundCallArgument): BoundCallArgument = arguments[value] ?: when (value) {
        is BoundCallArgument.Value -> value.copy(expression = expression(value.expression))
        is BoundCallArgument.Reference -> value.copy(location = location(value.location))
        is BoundCallArgument.Callback -> value.copy(
            callback = value.callback.copy(
                parameterNames = immutableList(value.callback.parameterNames)
            )
        )
    }.also { arguments[value] = it }

    private fun call(value: SourceCall): SourceCall = calls[value] ?: value.copy(
        readonlyArguments = immutableList(value.readonlyArguments.map(::expression)),
        arguments = immutableList(value.arguments.map(::argument)),
        evaluationOrder = immutableList(
            value.evaluationOrder.map { step ->
                when (step) {
                    is SourceEvaluationArgument.Readonly -> step.copy(expression = expression(step.expression))
                    is SourceEvaluationArgument.Normal -> step.copy(argument = argument(step.argument))
                }
            }
        ),
        effectOrder = immutableList(value.effectOrder),
        receiver = value.receiver?.let(::location)
    ).also { calls[value] = it }
}
