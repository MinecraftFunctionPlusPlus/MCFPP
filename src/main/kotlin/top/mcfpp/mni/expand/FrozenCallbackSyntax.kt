package top.mcfpp.mni.expand

import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode
import org.antlr.v4.runtime.tree.TerminalNodeImpl
import top.mcfpp.antlr.mcfppParser as P

/** Immutable syntax values only; every binding restores fresh contexts, children and tokens. */
internal class FrozenCallbackSyntax private constructor(private val text: String, private val root: Context) {
    private data class Leaf(val type: Int, val text: String, val start: Int, val stop: Int,
                            val line: Int, val column: Int, val index: Int, val channel: Int) {
        fun restore() = CommonToken(type, text).also {
            it.startIndex = start; it.stopIndex = stop; it.line = line
            it.charPositionInLine = column; it.tokenIndex = index; it.channel = channel
        }
    }
    private class Context(val type: Class<out ParserRuleContext>, val state: Int,
                          val start: Leaf?, val stop: Leaf?, val children: List<Any>) {
        fun restore(parent: ParserRuleContext?): ParserRuleContext {
            val result = type.getConstructor(ParserRuleContext::class.java, Integer.TYPE).newInstance(parent, state)
            result.start = start?.restore(); result.stop = stop?.restore()
            children.forEach { child -> when (child) {
                is Context -> result.addChild(child.restore(result))
                is Leaf -> result.addChild(TerminalNodeImpl(child.restore()))
            } }
            return result
        }
    }
    fun restore(expected: String, start: Int, end: Int): P.InlineLambdaContext {
        require(expected == text && root.start?.start == start && root.stop?.stop?.plus(1) == end) {
            "Frozen callback syntax differs from its source text or positions"
        }
        return root.restore(null) as P.InlineLambdaContext
    }
    companion object {
        fun capture(syntax: P.InlineLambdaContext, text: String): FrozenCallbackSyntax {
            fun leaf(token: Token) = Leaf(token.type, token.text, token.startIndex, token.stopIndex,
                token.line, token.charPositionInLine, token.tokenIndex, token.channel)
            fun context(node: ParserRuleContext): Context {
                require(node.javaClass.enclosingClass == P::class.java)
                return Context(node.javaClass, node.invokingState, node.start?.let(::leaf), node.stop?.let(::leaf),
                    (0 until node.childCount).map { index -> when (val child = node.getChild(index)) {
                        is ParserRuleContext -> context(child)
                        is TerminalNode -> leaf(child.symbol)
                        else -> error("Unsupported callback syntax node")
                    } })
            }
            return FrozenCallbackSyntax(text, context(syntax))
        }
    }
}
