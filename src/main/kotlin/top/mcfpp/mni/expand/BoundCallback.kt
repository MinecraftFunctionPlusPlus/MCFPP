package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

/** A source template, not an executable/insertable Code handle. C_INLINE allocates fresh
 * parameter/local identities when it consumes this template; captures retain their identities. */
internal class BoundCallback(
    val identity: CallbackId,
    val callbackType: TypeRef<*>,
    val signature: InlineSignature<*>,
    parameters: List<BoundCallbackParameter>,
    readonlyParameters: List<SourceSymbol>,
    captures: Map<SymbolId, SourceSymbol>,
    val body: BoundSourceBlock,
    val returnRegion: RegionId,
    val source: SourceSpan,
    val bodySource: SourceSpan,
    val sourceText: String,
    val functionTarget: SourceFunction?,
    nestedCallbacks: List<SourceCallbackBodyRequest>
) {
    val parameters = immutableList(parameters)
    val readonlyParameters = immutableList(readonlyParameters)
    /** Direct body reads. Deferred nested bodies retain their own original lexical snapshots. */
    val captures = immutableMap(captures)
    val nestedCallbacks = immutableList(nestedCallbacks)
}

/** A nested callback is bound only when its actual INLINE invocation supplies CONST facts.
 * Its lexical snapshot retains original symbol identities, including enclosing locals. The
 * inline cloner must remap those owned locals in this snapshot when it clones the parent. */
internal class SourceCallbackBodyRequest(
    syntax: SourceCallbackSyntax,
    val environment: BindingEnvironment,
    declarations: Map<FunctionKey, BindingEnvironment>
) {
    private val type = environment.types.inlineType(syntax.signature)
        ?.takeIf { it.sameType(syntax.callbackType) }
        ?: throw SourceBindingFailure(syntax.source, "Nested callback signature is outside its lexical snapshot")
    val syntax = syntax.copy(
        parameterNames = immutableList(syntax.parameterNames),
        signature = requireNotNull(type.inlineSignature()),
        callbackType = type
    )
    val identity = sourceCallbackIdentity(this.syntax)
    val declarations = immutableMap(declarations)

    fun bind(constParameters: Map<Int, TypedConstant> = emptyMap()): BoundCallback = SnapshotSourceBodyBinder().bind(syntax, environment, declarations, constParameters)
}

internal fun sourceCallbackIdentity(syntax: SourceCallbackSyntax): CallbackId {
    syntax.functionTarget?.declarationIdentity?.let { return CallbackId(it.universe, "function:${it.declaration}") }
    val type = syntax.callbackType as? ImmutableTypeRef<*>
        ?: throw SourceBindingFailure(syntax.source, "Callback identity requires a canonical type")
    return CallbackId(type.key.universe, "lambda:${syntax.source.file}:${syntax.source.startOffset}:${syntax.source.endOffset}")
}

internal data class BoundCallbackParameter(val symbol: SourceSymbol, val mode: ParamMode)

internal class BoundSourceBlock(
    val scope: ScopeId,
    val parent: ScopeId?,
    statements: List<BoundSourceStatement>,
    val source: SourceSpan
) {
    val statements = immutableList(statements)
}

internal sealed interface BoundSourceStatement {
    val source: SourceSpan
    data class Local(
        val symbol: SourceSymbol,
        val initialValue: BoundExpr?,
        val constant: Boolean,
        override val source: SourceSpan
    ) : BoundSourceStatement
    /** Compound assignment retains its original location and operator; no duplicated evaluation. */
    data class Assign(
        val location: BoundLocation,
        val value: BoundExpr,
        val operator: BinaryOperator?,
        override val source: SourceSpan
    ) : BoundSourceStatement
    data class Evaluate(val expression: BoundExpr, override val source: SourceSpan) : BoundSourceStatement
    data class Conditional(
        val condition: BoundExpr,
        val whenTrue: BoundSourceBlock,
        val whenFalse: BoundSourceBlock?,
        override val source: SourceSpan
    ) : BoundSourceStatement
    data class Loop(
        val region: RegionId,
        val condition: BoundExpr,
        val body: BoundSourceBlock,
        val testAfter: Boolean,
        override val source: SourceSpan
    ) : BoundSourceStatement
    data class Return(val region: RegionId, val value: BoundExpr?, override val source: SourceSpan) : BoundSourceStatement
    data class Break(val region: RegionId, override val source: SourceSpan) : BoundSourceStatement
    data class Continue(val region: RegionId, override val source: SourceSpan) : BoundSourceStatement
}
