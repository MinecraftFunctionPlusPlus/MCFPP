package top.mcfpp.antlr

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import top.mcfpp.util.LogProcessor

class MCFPPErrorListener : BaseErrorListener() {

    override fun syntaxError(
        recognizer: Recognizer<*, *>,
        offendingSymbol: Any,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?
    ) {
        val diagnostic = if ((offendingSymbol as? org.antlr.v4.runtime.Token)?.type == mcfppLexer.REF)
            LegacyReferenceSyntax.MESSAGE
        else if ((offendingSymbol as? org.antlr.v4.runtime.Token)?.type == mcfppLexer.DOUBLE_ARROW)
            "Legacy lambda '(args) => { ... }' is no longer supported; use '{ args -> ... }'. " +
                "Captured locals are read-only values; return an explicit accumulated result."
        else msg
        LogProcessor.syntaxError(recognizer, diagnostic, line, charPositionInLine)
    }

}
