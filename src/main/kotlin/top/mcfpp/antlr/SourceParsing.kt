package top.mcfpp.antlr

import org.antlr.v4.runtime.BailErrorStrategy
import org.antlr.v4.runtime.atn.PredictionMode
import org.antlr.v4.runtime.misc.ParseCancellationException

/** Callback types share declaration prefixes. Try SLL before the full-context prediction path. */
fun mcfppParser.parseCompilationUnit(): mcfppParser.CompilationUnitContext {
    val strategy = errorHandler
    val mode = interpreter.predictionMode
    val listeners = errorListeners.toList()
    removeErrorListeners()
    errorHandler = BailErrorStrategy()
    interpreter.predictionMode = PredictionMode.SLL
    try {
        return compilationUnit()
    } catch (_: ParseCancellationException) {
        reset()
        errorHandler = strategy
        interpreter.predictionMode = PredictionMode.LL
        listeners.forEach(::addErrorListener)
        return compilationUnit()
    } finally {
        errorHandler = strategy
        interpreter.predictionMode = mode
        removeErrorListeners()
        listeners.forEach(::addErrorListener)
    }
}
