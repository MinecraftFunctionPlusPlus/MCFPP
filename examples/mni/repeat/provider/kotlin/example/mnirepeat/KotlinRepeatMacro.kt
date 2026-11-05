package example.mnirepeat

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.Expr
import top.mcfpp.mni.api.InlineFn
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.VoidT
import top.mcfpp.mni.api.quote

/**
 * Kotlin provider for the MNI `repeat` macro. `@JvmStatic` is required because registration
 * discovers a public static JVM entry point.
 *
 * `times` is VALUE: the source argument runs once in the caller-side prelude. This provider binds
 * it to its own generated `limit` local and drives a `whileLoop` with a separate generated `i`
 * counter. `inlineVoid` inserts the source callback at the call site and passes the current
 * iteration as a VALUE argument. A callback `return` leaves only that insertion; the counter
 * increment and following caller statements continue.
 */
object KotlinRepeatMacro {
    @JvmStatic
    @MNIMacro(normalParams = [
        "int",
        "int",
        "(int,int)->int"
    ], returnType = "int")
    fun repeatFold(context: MacroContext, times: Value<IntT>, initial: Value<IntT>, body: InlineFn<IntT>): Expr<IntT> =
        context.quote.expression(context.types().intType()) {
            val iteration = local("i", literal(0))
            val limit = local("limit", times.value)
            val accumulator = local("accumulator", initial.value)
            whileLoop({ iteration.value lt limit.value }) {
                accumulator.value = inline(body, accumulator.value, iteration.value)
                iteration += 1
            }
            yield(accumulator.value)
        }

    @JvmStatic
    @MNIMacro(
        normalParams = [
            "int",
            "(int)->void"
        ],
        returnType = "void"
    )
    fun repeat(context: MacroContext, times: Value<IntT>, body: InlineFn<VoidT>): Block =
        context.quote.block {
            val iteration = local("i", literal(0))
            val limit = local("limit", times.value)
            whileLoop({ iteration.value lt limit.value }) {
                inlineVoid(body, iteration.value)
                iteration += 1
            }
        }
}
