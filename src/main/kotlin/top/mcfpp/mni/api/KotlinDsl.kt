package top.mcfpp.mni.api

import java.util.function.Consumer
import java.util.function.Function

/** Receiver restrictions supplement the same runtime capability checks used by Java callers. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class MniCodeDsl

@MniCodeDsl
class CodeScope internal constructor(private val builder: CodeBuilder) : CodeBuilder by builder {
    fun ifThen(condition: Expr<BoolT>, thenBody: CodeScope.() -> Unit) = builder.ifThen(condition, Consumer { CodeScope(it).thenBody() })

    fun ifElse(condition: Expr<BoolT>, thenBody: CodeScope.() -> Unit, elseBody: CodeScope.() -> Unit) = builder.ifElse(condition, Consumer { CodeScope(it).thenBody() }, Consumer { CodeScope(it).elseBody() })

    fun whileLoop(condition: CodeScope.() -> Expr<BoolT>, body: CodeScope.() -> Unit) = builder.whileLoop(Function { CodeScope(it).condition() }, Consumer { CodeScope(it).body() })
}

class KotlinQuote internal constructor(private val quote: Quote) {
    fun block(body: CodeScope.() -> Unit): Block = quote.block(Consumer { CodeScope(it).body() })

    fun <T> expression(type: TypeRef<T>, body: CodeScope.() -> Unit): Expr<T> = quote.expression(type, Consumer { CodeScope(it).body() })
}

fun Quote.kotlin(): KotlinQuote = KotlinQuote(this)
val MacroContext.quote: KotlinQuote get() = quote().kotlin()
