package top.mcfpp.mni.expand

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import top.mcfpp.library.compiler.CurrentSyntaxProjection
import top.mcfpp.library.syntax.Expression
import top.mcfpp.library.syntax.SemanticNode
import top.mcfpp.library.syntax.Statement
import top.mcfpp.model.function.BodyHandle
import top.mcfpp.model.function.StructuredBodyContexts
import top.mcfpp.antlr.mcfppParser as P

/** Opaque session syntax key. Text is a display view; the neutral operation is the only body source. */
internal class StructuredCallbackSyntax private constructor(
    val handle: BodyHandle,
    private val node: SemanticNode,
    private val prefix: String
) {
    fun fresh(): ParserRuleContext {
        val body = handle.repository.body(handle)
        val projection = CurrentSyntaxProjection(body.bindings, body.declarations)
        projection.source.append(prefix)
        val root = when (val value = node) {
            is Expression.Lambda -> projection.lambda(value)
            is Statement.Block -> projection.block(value)
            is Expression.Compound -> {
                check(value.entries.isEmpty()) { "Only an empty compound can represent an empty callback" }
                val expressions = projection.expressions
                expressions.node(P.CurlBlockContext(null, -1), Statement.Block(value.scope, emptyList())) {
                    expressions.token(this, top.mcfpp.antlr.mcfppLexer.LCURL, "{")
                    expressions.token(this, top.mcfpp.antlr.mcfppLexer.RCURL, "}")
                }
            }
            else -> throw IllegalStateException("LIB_BODY_UNSUPPORTED: Callback root is not a lambda or block")
        }
        handle.repository.registerProjection(handle, projection, root)
        return root
    }

    companion object {
        fun capture(context: ParserRuleContext): StructuredCallbackSyntax? {
            val owner = StructuredBodyContexts.body(context) ?: return null
            val node = StructuredBodyContexts.origin(context)?.node
                ?: throw IllegalStateException("LIB_BODY_UNSUPPORTED: Projected callback lost its semantic origin")
            val handle = if (node is Expression.Lambda) owner.repository.handle(owner.library, node.body) else owner
            val prefix = if (context.start.startIndex == 0) "" else context.start.inputStream.getText(Interval(0, context.start.startIndex - 1))
            return StructuredCallbackSyntax(handle, node, prefix)
        }
    }
}
