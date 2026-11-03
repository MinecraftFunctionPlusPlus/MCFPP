package top.mcfpp.mni.api

import java.util.function.Consumer

/** Constructs immutable code in the current expansion. Each body is consumed immediately, once. */
interface Quote {
    /** A statement region cannot yield a result. */
    fun block(body: Consumer<CodeBuilder>): Block

    /** Every reachable normal exit must yield a value assignable to [type]. */
    fun <T> expression(type: TypeRef<T>, body: Consumer<CodeBuilder>): Expr<T>
}
