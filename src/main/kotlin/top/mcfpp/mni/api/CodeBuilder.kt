package top.mcfpp.mni.api

import java.util.function.Consumer
import java.util.function.Function

/**
 * A scoped code-construction capability, valid only during its host callback.
 * Operations validate actual MCFPP types, session ownership and lexical visibility;
 * Java generic arguments alone are not proof of any of these properties.
 */
interface CodeBuilder {
    fun <T> local(type: TypeRef<T>, initialValue: Expr<T>): Local<T>
    fun <T> local(hint: String, type: TypeRef<T>, initialValue: Expr<T>): Local<T>
    fun <T> assign(destination: Ref<T>, expression: Expr<T>)
    fun evaluate(expression: Expr<*>)

    /** Hygienically imports the block, freshening its declarations and their bound references. */
    fun append(block: Block)

    fun ifThen(condition: Expr<BoolT>, thenBody: Consumer<CodeBuilder>)
    fun ifElse(condition: Expr<BoolT>, thenBody: Consumer<CodeBuilder>, elseBody: Consumer<CodeBuilder>)

    /** Builds the condition once; the generated condition region runs before every iteration. */
    fun whileLoop(condition: Function<CodeBuilder, Expr<BoolT>>, body: Consumer<CodeBuilder>)

    /** Builds an expression; execution occurs only where that expression is used. */
    fun <R> call(function: FunctionRef<R>, arguments: List<CallArgument>): Expr<R>
    fun callVoid(function: FunctionRef<VoidT>, arguments: List<CallArgument>)
    fun <R> inline(callback: InlineFn<*>, arguments: List<InlineArgument>): Expr<R>
    fun inlineVoid(callback: InlineFn<*>, arguments: List<InlineArgument>)

    /** Completes the current expression region, including a non-void helper body. */
    fun <T> yield(expression: Expr<T>)

    fun intLiteral(value: Int): Expr<IntT>
    fun boolLiteral(value: Boolean): Expr<BoolT>
    fun floatLiteral(value: Float): Expr<FloatT>
    fun stringLiteral(value: String): Expr<StringT>

    /** Numeric operations accept supported int/float operands of matching actual types. */
    fun <T> add(left: Expr<T>, right: Expr<T>): Expr<T>
    fun <T> sub(left: Expr<T>, right: Expr<T>): Expr<T>
    fun <T> mul(left: Expr<T>, right: Expr<T>): Expr<T>
    fun <T> div(left: Expr<T>, right: Expr<T>): Expr<T>
    fun <T> mod(left: Expr<T>, right: Expr<T>): Expr<T>
    fun <T> negate(value: Expr<T>): Expr<T>
    fun <T> lt(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    fun <T> le(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    fun <T> gt(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    fun <T> ge(left: Expr<T>, right: Expr<T>): Expr<BoolT>

    /** Equality requires compatible actual types with a supported equality operation. */
    fun <T> eq(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    fun <T> ne(left: Expr<T>, right: Expr<T>): Expr<BoolT>
    fun not(value: Expr<BoolT>): Expr<BoolT>
    fun and(left: Expr<BoolT>, right: Expr<BoolT>): Expr<BoolT>
    fun or(left: Expr<BoolT>, right: Expr<BoolT>): Expr<BoolT>

    fun <T> field(receiver: Expr<*>, field: FieldRef<T>): Expr<T>
    fun <T> fieldRef(receiver: Ref<*>, field: FieldRef<T>): Ref<T>
    fun minecraft(): MinecraftBuilder
}
