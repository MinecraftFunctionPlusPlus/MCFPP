package top.mcfpp.mni.api

import java.util.function.Consumer
import java.util.function.Function

/**
 * Builds MCFPP expressions and statements during a quote or generated body callback.
 * Use code and type descriptors from the current expansion and variables visible in the current scope.
 * Builder methods check MCFPP types and report invalid code through [CodeModelException].
 */
interface CodeBuilder {
    /** Declares a local variable and initializes it from [initialValue] when execution reaches the declaration. */
    fun <T> local(type: TypeRef<T>, initialValue: Expr<T>): Local<T>
    /** Declares an initialized local variable with [hint] as its suggested generated name. */
    fun <T> local(hint: String, type: TypeRef<T>, initialValue: Expr<T>): Local<T>
    /** Adds a statement that writes [expression] to [destination] when the generated code runs. */
    fun <T> assign(destination: Ref<T>, expression: Expr<T>)
    /** Adds a statement that evaluates [expression] and discards its result. */
    fun evaluate(expression: Expr<*>)

    /** Inserts [block], giving its local declarations new identities and updating their references. */
    fun append(block: Block)

    /** Adds a conditional statement that executes [thenBody]'s generated statements when [condition] is true. */
    fun ifThen(condition: Expr<BoolT>, thenBody: Consumer<CodeBuilder>)
    /** Adds a conditional statement with a branch for each Boolean result. */
    fun ifElse(condition: Expr<BoolT>, thenBody: Consumer<CodeBuilder>, elseBody: Consumer<CodeBuilder>)

    /** Builds the condition and body once. The generated loop evaluates the condition before each iteration. */
    fun whileLoop(condition: Function<CodeBuilder, Expr<BoolT>>, body: Consumer<CodeBuilder>)

    /** Builds a call expression. The call executes where the returned expression is used. */
    fun <R> call(function: FunctionRef<R>, arguments: List<CallArgument>): Expr<R>
    /** Adds a call statement for a function with a void result. */
    fun callVoid(function: FunctionRef<VoidT>, arguments: List<CallArgument>)
    /** Inserts a source callback as an expression. [R] must match the callback's result type. */
    fun <R> inline(callback: InlineFn<*>, arguments: List<InlineArgument>): Expr<R>
    /** Inserts a source callback with a void result as a statement. */
    fun inlineVoid(callback: InlineFn<*>, arguments: List<InlineArgument>)

    /** Completes the current expression or value-returning helper body with [expression] as its result. */
    fun <T> yield(expression: Expr<T>)

    /** Creates an MCFPP int literal from [value]. */
    fun intLiteral(value: Int): Expr<IntT>
    /** Creates an MCFPP bool literal from [value]. */
    fun boolLiteral(value: Boolean): Expr<BoolT>
    /** Creates an MCFPP float literal from [value]. */
    fun floatLiteral(value: Float): Expr<FloatT>
    /** Creates an MCFPP string literal from [value], with escaping handled by the compiler. */
    fun stringLiteral(value: String): Expr<StringT>

    /** Adds two int operands or two float operands. */
    fun <T> add(left: Expr<T>, right: Expr<T>): Expr<T>
    /** Subtracts [right] from [left]. Both operands use the same supported numeric type. */
    fun <T> sub(left: Expr<T>, right: Expr<T>): Expr<T>
    /** Multiplies operands of the same supported numeric type. */
    fun <T> mul(left: Expr<T>, right: Expr<T>): Expr<T>
    /** Divides [left] by [right] using the operands' MCFPP numeric type. */
    fun <T> div(left: Expr<T>, right: Expr<T>): Expr<T>
    /** Computes the remainder of [left] divided by [right] for a supported numeric type. */
    fun <T> mod(left: Expr<T>, right: Expr<T>): Expr<T>
    /** Creates the arithmetic negative of [value]. */
    fun <T> negate(value: Expr<T>): Expr<T>
    /** Tests whether [left] is less than [right]. */
    fun <T> lt(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    /** Tests whether [left] is less than or equal to [right]. */
    fun <T> le(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    /** Tests whether [left] is greater than [right]. */
    fun <T> gt(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    /** Tests whether [left] is greater than or equal to [right]. */
    fun <T> ge(left: Expr<T>, right: Expr<T>): Expr<BoolT>

    /** Equality requires compatible actual types with a supported equality operation. */
    fun <T> eq(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    /** Tests whether compatible operands differ, using their MCFPP equality operation. */
    fun <T> ne(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    /** Reverses the Boolean result of [value]. */
    fun not(value: Expr<BoolT>): Expr<BoolT>
    /** Creates a Boolean AND expression with MCFPP short-circuit evaluation. */
    fun and(left: Expr<BoolT>, right: Expr<BoolT>): Expr<BoolT>
    /** Creates a Boolean OR expression with MCFPP short-circuit evaluation. */
    fun or(left: Expr<BoolT>, right: Expr<BoolT>): Expr<BoolT>

    /** Creates an expression that reads [field] from [receiver]. */
    fun <T> field(receiver: Expr<*>, field: FieldRef<T>): Expr<T>
    /** Returns a writable field location within [receiver]. The field must support writes. */
    fun <T> fieldRef(receiver: Ref<*>, field: FieldRef<T>): Ref<T>
    /** Returns the Minecraft operations available in this construction scope. */
    fun minecraft(): MinecraftBuilder
}
