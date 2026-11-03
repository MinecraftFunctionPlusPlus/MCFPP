package example.mnirepeat

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MacroParam
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.Expr
import top.mcfpp.mni.api.InlineArgument
import top.mcfpp.mni.api.InlineFn
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.VoidT
import top.mcfpp.mni.api.kotlin

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
    @MNIMacro(identifier = "repeat", params = [
        MacroParam(name = "times", type = "int", mode = ParamMode.VALUE),
        MacroParam(name = "initial", type = "int", mode = ParamMode.VALUE),
        MacroParam(name = "body", type = "(int,int)->int", mode = ParamMode.INLINE)
    ], returnType = "int")
    fun repeatFold(context: MacroContext, times: Value<IntT>, initial: Value<IntT>, body: InlineFn<IntT>): Expr<IntT> =
        context.quote().kotlin().expression(context.types().intType()) {
            val iteration = local("i", context.types().intType(), intLiteral(0))
            val limit = local("limit", context.types().intType(), times.expr())
            val accumulator = local("accumulator", context.types().intType(), initial.expr())
            whileLoop({ lt(iteration.load(), limit.load()) }, {
                assign(accumulator, inline(body, listOf(InlineArgument.value(accumulator.load()), InlineArgument.value(iteration.load()))))
                assign(iteration, add(iteration.load(), intLiteral(1)))
            })
            yield(accumulator.load())
        }

    @JvmStatic
    @MNIMacro(
        identifier = "repeat",
        params = [
            MacroParam(name = "times", type = "int", mode = ParamMode.VALUE),
            MacroParam(name = "body", type = "(int)->void", mode = ParamMode.INLINE)
        ],
        returnType = "void"
    )
    fun repeat(context: MacroContext, times: Value<IntT>, body: InlineFn<VoidT>): Block =
        context.quote().kotlin().block {
            val iteration = local("i", context.types().intType(), intLiteral(0))
            val limit = local("limit", context.types().intType(), times.expr())
            whileLoop(
                { lt(iteration.load(), limit.load()) },
                {
                    inlineVoid(body, listOf(InlineArgument.value(iteration.load())))
                    assign(iteration, add(iteration.load(), intLiteral(1)))
                }
            )
        }
}
