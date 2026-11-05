package top.mcfpp.mni.api

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.BiConsumer

/** Keeps nested code callbacks focused on their current [CodeScope] receiver. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class MniCodeDsl

/** Kotlin receiver for building code with [KotlinQuote]. Nested callbacks receive a scope for their generated body. */
@MniCodeDsl
class CodeScope internal constructor(private val builder: CodeBuilder) : MinecraftBuilder by builder.minecraft() {
    /** Builds a branch that runs when [condition] is true. */
    fun ifThen(condition: Expr<BoolT>, thenBody: CodeScope.() -> Unit) = builder.ifThen(condition, Consumer { CodeScope(it).thenBody() })

    /** Builds a branch for each Boolean result of [condition]. */
    fun ifElse(condition: Expr<BoolT>, thenBody: CodeScope.() -> Unit, elseBody: CodeScope.() -> Unit) = builder.ifElse(condition, Consumer { CodeScope(it).thenBody() }, Consumer { CodeScope(it).elseBody() })

    /** Builds a loop whose generated condition runs before each iteration. Both Kotlin callbacks run during construction. */
    fun whileLoop(condition: CodeScope.() -> Expr<BoolT>, body: CodeScope.() -> Unit) = builder.whileLoop(Function { CodeScope(it).condition() }, Consumer { CodeScope(it).body() })

    /** Returns this scope with Minecraft operations available. */
    override fun minecraft(): CodeScope {
        builder.minecraft()
        return this
    }

    /** Builds code that executes as each selected entity. [body] receives the current entity. */
    fun executeAs(targets: EntitySet, body: CodeScope.(EntityHandle) -> Unit) =
        builder.minecraft().executeAs(targets, BiConsumer { child, self -> CodeScope(child).body(self) })

    /** Builds code at [position], using [MinecraftBuilder.at]'s position and dimension rules. */
    fun at(position: PositionHandle, body: CodeScope.() -> Unit) =
        builder.minecraft().at(position, Consumer { CodeScope(it).body() })

    /** Builds code with the supplied execution rotation. */
    fun rotated(rotation: RotationHandle, body: CodeScope.() -> Unit) =
        builder.minecraft().rotated(rotation, Consumer { CodeScope(it).body() })

    /** Creates an MCFPP int literal. */
    fun literal(value: Int): Expr<IntT> = builder.intLiteral(value)
    /** Creates an MCFPP float literal. */
    fun literal(value: Float): Expr<FloatT> = builder.floatLiteral(value)
    /** Creates an MCFPP bool literal. */
    fun literal(value: Boolean): Expr<BoolT> = builder.boolLiteral(value)
    /** Creates an MCFPP string literal with compiler-managed escaping. */
    fun literal(value: String): Expr<StringT> = builder.stringLiteral(value)

    /** Declares a local variable with its type inferred from [initialValue]. */
    fun <T> local(initialValue: Expr<T>): Local<T> = builder.local(initialValue.type(), initialValue)
    /** Declares an initialized local variable with a suggested generated name. */
    fun <T> local(hint: String, initialValue: Expr<T>): Local<T> = builder.local(hint, initialValue.type(), initialValue)

    /** Reads the macro argument's bound value. Repeated reads use the same value. */
    val <T> Value<T>.value: Expr<T> get() = expr()
    /** Reads a reference or adds an assignment to it. The generated code performs the read or write. */
    var <T> Ref<T>.value: Expr<T>
        get() = load()
        set(expression) { builder.assign(this, expression) }

    /** Adds two int expressions or two float expressions. */
    operator fun <T> Expr<T>.plus(other: Expr<T>): Expr<T> = builder.add(this, other)
    /** Subtracts [other] using the expressions' MCFPP numeric type. */
    operator fun <T> Expr<T>.minus(other: Expr<T>): Expr<T> = builder.sub(this, other)
    /** Multiplies matching numeric expressions. */
    operator fun <T> Expr<T>.times(other: Expr<T>): Expr<T> = builder.mul(this, other)
    /** Divides this expression by [other] using MCFPP numeric rules. */
    @JvmName("divideExpression")
    operator fun <T> Expr<T>.div(other: Expr<T>): Expr<T> = builder.div(this, other)
    /** Computes the numeric remainder after division by [other]. */
    operator fun <T> Expr<T>.rem(other: Expr<T>): Expr<T> = builder.mod(this, other)
    /** Creates the arithmetic negative of this expression. */
    operator fun <T> Expr<T>.unaryMinus(): Expr<T> = builder.negate(this)
    /** Reverses this expression's Boolean result. */
    @JvmName("notExpression")
    operator fun Expr<BoolT>.not(): Expr<BoolT> = builder.not(this)

    /** Adds an int literal to this expression. */
    operator fun Expr<IntT>.plus(other: Int): Expr<IntT> = this + literal(other)
    /** Subtracts an int literal from this expression. */
    operator fun Expr<IntT>.minus(other: Int): Expr<IntT> = this - literal(other)
    /** Multiplies this expression by an int literal. */
    operator fun Expr<IntT>.times(other: Int): Expr<IntT> = this * literal(other)
    /** Divides this expression by an int literal. */
    operator fun Expr<IntT>.div(other: Int): Expr<IntT> = this / literal(other)
    /** Computes the remainder after division by an int literal. */
    operator fun Expr<IntT>.rem(other: Int): Expr<IntT> = this % literal(other)
    /** Adds a float literal to this expression. */
    operator fun Expr<FloatT>.plus(other: Float): Expr<FloatT> = this + literal(other)
    /** Subtracts a float literal from this expression. */
    operator fun Expr<FloatT>.minus(other: Float): Expr<FloatT> = this - literal(other)
    /** Multiplies this expression by a float literal. */
    operator fun Expr<FloatT>.times(other: Float): Expr<FloatT> = this * literal(other)
    /** Divides this expression by a float literal. */
    operator fun Expr<FloatT>.div(other: Float): Expr<FloatT> = this / literal(other)
    /** Computes the remainder after division by a float literal. */
    operator fun Expr<FloatT>.rem(other: Float): Expr<FloatT> = this % literal(other)
    /** Delegates string addition to [CodeBuilder.add], which currently reports [CodeModelException] for string operands. */
    operator fun Expr<StringT>.plus(other: String): Expr<StringT> = this + literal(other)

    /** Adds [other] to this location's value and writes the result to the location. */
    operator fun <T> Ref<T>.plusAssign(other: Expr<T>) { value = value + other }
    /** Subtracts [other] from this location's value and writes the result to the location. */
    operator fun <T> Ref<T>.minusAssign(other: Expr<T>) { value = value - other }
    /** Increases an int location by a literal amount. */
    operator fun Ref<IntT>.plusAssign(other: Int) { this += literal(other) }
    /** Decreases an int location by a literal amount. */
    operator fun Ref<IntT>.minusAssign(other: Int) { this -= literal(other) }

    /** Tests whether this expression is less than [other]. */
    @JvmName("lessThanExpression")
    infix fun <T> Expr<T>.lt(other: Expr<T>): Expr<BoolT> = builder.lt(this, other)
    /** Tests whether this expression is less than or equal to [other]. */
    @JvmName("lessOrEqualExpression")
    infix fun <T> Expr<T>.le(other: Expr<T>): Expr<BoolT> = builder.le(this, other)
    /** Tests whether this expression is greater than [other]. */
    @JvmName("greaterThanExpression")
    infix fun <T> Expr<T>.gt(other: Expr<T>): Expr<BoolT> = builder.gt(this, other)
    /** Tests whether this expression is greater than or equal to [other]. */
    @JvmName("greaterOrEqualExpression")
    infix fun <T> Expr<T>.ge(other: Expr<T>): Expr<BoolT> = builder.ge(this, other)
    /** Compares compatible expressions using their MCFPP equality operation. */
    @JvmName("equalExpression")
    infix fun <T> Expr<T>.eq(other: Expr<T>): Expr<BoolT> = builder.eq(this, other)
    /** Tests whether compatible expressions have different values. */
    @JvmName("notEqualExpression")
    infix fun <T> Expr<T>.ne(other: Expr<T>): Expr<BoolT> = builder.ne(this, other)
    /** Creates a short-circuit Boolean AND expression. */
    @JvmName("andExpression")
    infix fun Expr<BoolT>.and(other: Expr<BoolT>): Expr<BoolT> = builder.and(this, other)
    /** Creates a short-circuit Boolean OR expression. */
    @JvmName("orExpression")
    infix fun Expr<BoolT>.or(other: Expr<BoolT>): Expr<BoolT> = builder.or(this, other)

    /** Builds a function call with VALUE arguments. Use [CallArgument] lists for other modes. */
    fun <R> call(function: FunctionRef<R>, vararg arguments: Expr<*>): Expr<R> =
        builder.call(function, arguments.map { CallArgument.value(it) })
    /** Adds a void function call with VALUE arguments. */
    fun callVoid(function: FunctionRef<VoidT>, vararg arguments: Expr<*>) =
        builder.callVoid(function, arguments.map { CallArgument.value(it) })
    /** Inserts a callback with VALUE arguments as a result expression. */
    fun <R> inline(callback: InlineFn<*>, vararg arguments: Expr<*>): Expr<R> =
        builder.inline(callback, arguments.map { InlineArgument.value(it) })
    /** Inserts a void callback with VALUE arguments as a statement. */
    fun inlineVoid(callback: InlineFn<*>, vararg arguments: Expr<*>) =
        builder.inlineVoid(callback, arguments.map { InlineArgument.value(it) })
}

/** Creates generated code with Kotlin receiver callbacks. Each callback runs once during construction. */
class KotlinQuote internal constructor(private val quote: Quote) {
    /** Builds an ordered group of statements in a [CodeScope]. */
    fun block(body: CodeScope.() -> Unit): Block = quote.block(Consumer { CodeScope(it).body() })

    /** Builds a value expression. Complete each normal execution path with [CodeBuilder.yield]. */
    fun <T> expression(type: TypeRef<T>, body: CodeScope.() -> Unit): Expr<T> = quote.expression(type, Consumer { CodeScope(it).body() })
}

/** Adds Kotlin receiver callbacks to this quote service. */
fun Quote.kotlin(): KotlinQuote = KotlinQuote(this)
/** The current macro's quote service with Kotlin receiver callbacks. */
val MacroContext.quote: KotlinQuote get() = quote().kotlin()
/** The current declaration processor's quote service with Kotlin receiver callbacks. */
val DeclarationContext.quote: KotlinQuote get() = quote().kotlin()

/** Runs [body] immediately with a Kotlin receiver for the current generated method or helper scope. */
fun CodeBuilder.kotlin(body: CodeScope.() -> Unit) { CodeScope(this).body() }
