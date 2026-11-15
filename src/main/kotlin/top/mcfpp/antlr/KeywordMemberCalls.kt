package top.mcfpp.antlr

import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.tree.TerminalNodeImpl

/** Keep keyword-named methods on the ordinary call binder and native invocation path. */
internal object KeywordMemberCalls {
    fun call(selector: mcfppParser.SelectorContext): mcfppParser.FunctionCallContext? {
        val keyword = selector.memberKeyword() ?: return null
        val arguments = selector.arguments() ?: return null
        if (selector.inlineLambda() != null) return null
        val call = mcfppParser.FunctionCallContext(selector, -1).apply { start = keyword.start; stop = selector.stop }
        val name = mcfppParser.NamespaceIDContext(call, -1).apply {
            start = keyword.start; stop = keyword.stop
            addChild(TerminalNodeImpl(CommonToken(keyword.start).apply { type = mcfppLexer.Identifier }))
        }
        call.addChild(name)
        // Preserve the original argument parents: source capture still owns the selector tree.
        call.addAnyChild(arguments)
        return call
    }
}
