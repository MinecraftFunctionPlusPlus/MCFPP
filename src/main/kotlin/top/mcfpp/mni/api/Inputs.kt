package top.mcfpp.mni.api

/** A macro argument bound by the compiler at the MCFPP call site. */
sealed interface MacroInput : Code
/** A VALUE argument. The compiler evaluates the caller's expression once and supplies its value. */
sealed interface Value<T> : MacroInput {
    /** Returns the argument's MCFPP type. */
    fun type(): TypeRef<T>
    /** Returns an expression for the bound value. Repeated calls reuse that value. */
    fun expr(): Expr<T>
}
/** A REF argument that identifies a writable MCFPP location. Assign through [CodeBuilder.assign]. */
sealed interface Ref<T> : MacroInput {
    /** Returns the MCFPP type stored at this location. */
    fun type(): TypeRef<T>
    /** Creates an expression that reads this location when the generated code runs. */
    fun load(): Expr<T>
}
/** A CONST argument whose value the compiler knows during macro expansion. */
sealed interface Const<T> : Value<T> {
    /** Returns the immutable value available to the provider during expansion. */
    fun value(): ConstantValue
    /** Returns an MCFPP int constant as a JVM Int. Other constant types raise [CodeModelException]. */
    fun intValue(): Int
    /** Returns an MCFPP bool constant as a JVM Boolean. Other constant types raise [CodeModelException]. */
    fun boolValue(): Boolean
}
/** An INLINE argument containing a source callback to insert with [CodeBuilder.inline]. */
sealed interface InlineFn<S> : MacroInput {
    /** Returns the callback's parameter modes, parameter types, and result type. */
    fun signature(): InlineSignature<*>
}

/** An argument for [CodeBuilder.call]. The factory selects how the generated call passes the argument. */
class CallArgument internal constructor(internal val payload: ArgumentPayload) {
    /** Returns the selected argument mode. */
    fun mode(): ParamMode = payload.mode
    /** Returns the source location of the supplied expression, reference, constant, or callback. */
    fun source(): SourceSpan = payload.code.source()

    companion object {
        /** Passes the result of [expression] by value. */
        @JvmStatic fun <T> value(expression: Expr<T>): CallArgument = CallArgument(ArgumentPayload.Value(expression))
        /** Passes [reference] as a writable location. Aliases retain their shared location. */
        @JvmStatic fun <T> ref(reference: Ref<T>): CallArgument = CallArgument(ArgumentPayload.Reference(reference))
        /** Passes a value known during expansion to a CONST parameter. */
        @JvmStatic fun <T> constant(constant: Const<T>): CallArgument = CallArgument(ArgumentPayload.Constant(constant))
        /** Passes a source callback to an INLINE parameter. */
        @JvmStatic fun inline(callback: InlineFn<*>): CallArgument = CallArgument(ArgumentPayload.Inline(callback))
    }
}

/** An argument for an inserted source callback. Use the factory that matches the callback parameter mode. */
class InlineArgument internal constructor(internal val payload: ArgumentPayload) {
    /** Returns the selected argument mode. */
    fun mode(): ParamMode = payload.mode
    /** Returns the source location of the supplied argument. */
    fun source(): SourceSpan = payload.code.source()

    companion object {
        /** Passes the result of [expression] by value. */
        @JvmStatic fun <T> value(expression: Expr<T>): InlineArgument = InlineArgument(ArgumentPayload.Value(expression))
        /** Passes the writable location identified by [reference]. */
        @JvmStatic fun <T> ref(reference: Ref<T>): InlineArgument = InlineArgument(ArgumentPayload.Reference(reference))
        /** Supplies a constant for the compiler to use while inserting the callback. */
        @JvmStatic fun <T> constant(constant: Const<T>): InlineArgument = InlineArgument(ArgumentPayload.Constant(constant))
    }
}

internal sealed interface ArgumentPayload {
    val code: Code
    val mode: ParamMode
    data class Value(val expression: Expr<*>) : ArgumentPayload {
        override val code: Code get() = expression
        override val mode: ParamMode get() = ParamMode.VALUE
    }
    data class Reference(val reference: Ref<*>) : ArgumentPayload {
        override val code: Code get() = reference
        override val mode: ParamMode get() = ParamMode.REF
    }
    data class Constant(val constant: Const<*>) : ArgumentPayload {
        override val code: Code get() = constant
        override val mode: ParamMode get() = ParamMode.CONST
    }
    data class Inline(val callback: InlineFn<*>) : ArgumentPayload {
        override val code: Code get() = callback
        override val mode: ParamMode get() = ParamMode.INLINE
    }
}

internal class ImmutableRef<T>(
    override val metadata: CodeMetadata,
    private val referenceType: TypeRef<T>,
    val location: NodeLocation
) : Ref<T>,
    OwnedCode {
    private val read: Expr<T> = ImmutableExpr(metadata, referenceType, ExprNode.Read(location))
    init {
        require(location.root.session == metadata.sessionId) { "Reference belongs to another expansion session" }
    }
    override fun type(): TypeRef<T> = referenceType
    override fun load(): Expr<T> = read
}

/** Repeated expr() calls return exactly the bound logical value, rather than its source expression. */
internal class ImmutableValue<T>(private val expression: Expr<T>) :
    Value<T>,
    OwnedCode {
    override val metadata: CodeMetadata = (expression as OwnedCode).metadata
    override fun type(): TypeRef<T> = expression.type()
    override fun expr(): Expr<T> = expression
}

internal class ImmutableConst<T>(
    private val expression: Expr<T>,
    private val constant: ConstantValue
) : Const<T>,
    OwnedCode {
    override val metadata: CodeMetadata = (expression as OwnedCode).metadata
    init {
        val node = (expression as? ImmutableExpr<*>)?.node
        if (node !is ExprNode.Literal || node.value != constant)
            throw CodeModelException(source(), "A constant requires an identical literal expression")
    }
    override fun type(): TypeRef<T> = expression.type()
    override fun expr(): Expr<T> = expression
    override fun value(): ConstantValue = constant
    override fun intValue(): Int {
        val integral = constant as? ConstantValue.Integral
        if ((type() as? ImmutableTypeRef<*>)?.kind != TypeKind.INT || integral == null || integral.value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
            throw CodeModelException(source(), "Expected an int constant")
        return integral.value.toInt()
    }
    override fun boolValue(): Boolean {
        if ((type() as? ImmutableTypeRef<*>)?.kind != TypeKind.BOOL || constant !is ConstantValue.Bool)
            throw CodeModelException(source(), "Expected a bool constant")
        return constant.value
    }
}

/**
 * A source-body template whose actual CONST facts are supplied when its [InlineTarget] is inserted.
 *
 * The interface lives in the public code domain so a sealed inline target can carry the deferral
 * without importing the source-binding package. Concrete implementations remain internal to the
 * expansion pipeline; code trees never retain a parser node, host lambda or compiler object.
 */
internal interface DeferredCallbackBody {
    val signature: InlineSignature<*>
    val source: SourceSpan
}

internal sealed interface InlineTarget {
    data class Declared(val function: FunctionKey) : InlineTarget
    /**
     * [captureReferences] is the actual capability behind each free capture root. It preserves the
     * caller's type, path, execution context, read-only flag and captured address, so later copy,
     * import and insertion never have to synthesize a writable root from a guessed leaf type.
     * Legacy descriptors may omit it only while [captures] is empty; a real free capture must carry
     * its capability through every session boundary.
     *
     * When [deferred] is present, [block]/[returnRegion] are an opaque placeholder only: the body
     * has not been bound and the placeholder carries no target statements. Insertion must rebind
     * the request with that insertion's actual CONST facts before cloning; copy/import/print may
     * carry the descriptor but must never treat the placeholder as a bound body. [deferredCaptures]
     * is keyed by the original source symbol identity and is remapped together with the enclosing
     * capability so enclosing owned locals stay fresh while free locations keep their origin.
     */
    class Body(
        parameters: List<SymbolId>,
        val block: Block,
        captures: Set<SymbolId>,
        val returnRegion: RegionId,
        captureReferences: Map<SymbolId, Ref<*>> = emptyMap(),
        val deferred: DeferredCallbackBody? = null,
        deferredCaptures: Map<SymbolId, Ref<*>> = emptyMap()
    ) : InlineTarget {
        val parameters: List<SymbolId> = immutableList(parameters)
        val captures: Set<SymbolId> = immutableSet(captures)
        val captureReferences: Map<SymbolId, Ref<*>> = immutableMap(captureReferences)
        val deferredCaptures: Map<SymbolId, Ref<*>> = immutableMap(deferredCaptures)
        init {
            require(this.captureReferences.keys.all { it in this.captures }) {
                "Inline capture metadata must describe a declared capture root"
            }
            require(this.deferredCaptures.isEmpty() || this.deferred != null) {
                "Inline deferred captures require a deferred source body"
            }
        }
    }
}

internal class ImmutableInlineFn<S>(
    override val metadata: CodeMetadata,
    private val callbackSignature: InlineSignature<*>,
    val callbackId: CallbackId,
    val target: InlineTarget
) : InlineFn<S>,
    OwnedCode {
    override fun signature(): InlineSignature<*> = callbackSignature
}
