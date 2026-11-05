package top.mcfpp.mni.api

import java.util.function.Consumer

/** Builds immutable MCFPP code for the current expansion. Each JVM callback runs once, during construction. */
interface Quote {
    /** Builds a group of statements. Use [expression] when the generated code must produce a value. */
    fun block(body: Consumer<CodeBuilder>): Block

    /**
     * Builds an expression with result type [type].
     * Each execution path that completes normally must produce a compatible value through [CodeBuilder.yield].
     */
    fun <T> expression(type: TypeRef<T>, body: Consumer<CodeBuilder>): Expr<T>
}
