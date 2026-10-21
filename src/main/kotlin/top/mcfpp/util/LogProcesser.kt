@file:Suppress("NOTHING_TO_INLINE")

package top.mcfpp.util

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.misc.Interval
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.message.MessageFactory
import org.apache.logging.log4j.spi.ExtendedLogger
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.command.CommentLevel
import top.mcfpp.command.Comment
import top.mcfpp.command.Command
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.function.Function
import kotlin.math.max
import java.util.Collections
import java.util.IdentityHashMap

object LogProcessor {

    var level: LogLevel = LogLevel.DEBUG

    var logger: Logger = LogManager.getLogger("mcfpp")

    private val captures = ThreadLocal.withInitial { ArrayDeque<DiagnosticCapture>() }

    internal data class DiagnosticSource(val file: String?, val line: Int?, val column: Int?)
    internal data class CapturedDiagnostic(
        val level: LogLevel, val message: String, val originalThrowable: Throwable?,
        val source: DiagnosticSource?
    )
    internal sealed interface PublicationEvent { fun publish() }
    internal data class Delivery(val logger: Logger, val diagnostic: CapturedDiagnostic, val attachThrowable: Boolean) : PublicationEvent {
        override fun publish() {
            val event = diagnostic
            val thrown = event.originalThrowable.takeIf { attachThrowable }
            when (event.level) {
                LogLevel.DEBUG -> if (thrown == null) logger.debug(event.message) else logger.debug(event.message, thrown)
                LogLevel.INFO -> if (thrown == null) logger.info(event.message) else logger.info(event.message, thrown)
                LogLevel.WARN -> if (thrown == null) logger.warn(event.message) else logger.warn(event.message, thrown)
                LogLevel.ERROR -> if (thrown == null) logger.error(event.message) else logger.error(event.message, thrown)
                LogLevel.NONE -> throw IllegalStateException("NONE is not a diagnostic severity")
            }
        }
    }
    private data class RawLoggerEvent(
        val logger: Logger, val level: Level, val message: String, val originalThrowable: Throwable?
    ) : PublicationEvent {
        override fun publish() {
            if (originalThrowable == null) logger.log(level, message)
            else logger.log(level, message, originalThrowable)
        }
    }
    private data class StderrEvent(val text: String) : PublicationEvent {
        override fun publish() { System.err.print(text) }
    }
    private data class AddedComment(val function: Function, val commands: MutableList<Command>, val comment: Comment)

    /** Compiler effects remain immediate, so preparation can observe errors and command order.
     * For a global transaction: capture before, begin, prepare, capture after WHILE OPEN,
     * seal, restore before; then restore after and publishLogs. Discard restores only diagnostic
     * deltas/comments/flags; composing journals own all other compiler mutations.
     */
    internal fun beginDiagnosticCapture(): DiagnosticCapture = DiagnosticCapture().also { captures.get().addLast(it) }

    internal class DiagnosticCapture internal constructor() : AutoCloseable {
        private val owner = Thread.currentThread()
        private var open = true
        private val deliveries = arrayListOf<PublicationEvent>()
        private val comments = arrayListOf<AddedComment>()
        private val syntaxFlags = IdentityHashMap<MCFPPFile, Boolean>()
        private var errors = 0
        private var warnings = 0

        private fun requireTop() {
            check(Thread.currentThread() === owner) { "Diagnostic capture belongs to another thread" }
            check(open && captures.get().lastOrNull() === this) { "Diagnostic captures must finish once in LIFO order" }
        }
        /** Nested success transfers ownership; only the outermost success publishes logs. */
        fun commit() {
            requireTop()
            captures.get().removeLast(); open = false
            val parent = captures.get().lastOrNull()
            if (parent == null) deliveries.forEach { it.publish() }
            else {
                parent.deliveries.addAll(deliveries)
                parent.comments.addAll(comments)
                parent.errors += errors; parent.warnings += warnings
                syntaxFlags.forEach { (file, flag) -> parent.syntaxFlags.putIfAbsent(file, flag) }
            }
        }
        /** Detaches logs only, including in a nested prepare. The caller's journals own restoration
         * of the sealed compiler effects; publication never transfers compiler-effect rollback hooks.
         */
        fun seal(): DiagnosticPublication {
            requireTop()
            captures.get().removeLast(); open = false
            return DiagnosticPublication(deliveries.toList())
        }
        fun discard() {
            check(Thread.currentThread() === owner) { "Diagnostic capture belongs to another thread" }
            if (!open) return
            requireTop()
            captures.get().removeLast(); open = false
            Project.errorCount -= errors; Project.warningCount -= warnings
            syntaxFlags.forEach { (file, flag) -> file.syntaxError = flag }
            comments.asReversed().forEach { added ->
                added.commands.removeAll { it === added.comment }
                if (added.function.commands !== added.commands) {
                    added.function.commands.removeAll { it === added.comment }
                }
            }
        }
        override fun close() = discard()

        internal fun recordError() { errors++ }
        internal fun recordWarning() { warnings++ }
        internal fun recordSyntax(file: MCFPPFile) { syntaxFlags.putIfAbsent(file, file.syntaxError) }
        internal fun acceptPublication(items: List<PublicationEvent>) { deliveries.addAll(items) }
        private fun record(delivery: PublicationEvent) { deliveries.add(delivery) }
        private fun record(added: AddedComment) { comments.add(added) }

        companion object {
            internal fun emit(severity: LogLevel, message: String, thrown: Throwable?, attach: Boolean, source: DiagnosticSource?) {
                val delivery = Delivery(logger, CapturedDiagnostic(severity, message, thrown, source), attach)
                val capture = captures.get().lastOrNull()
                if (capture == null) delivery.publish() else capture.record(delivery)
            }
            internal fun emitRaw(logger: Logger, severity: LogLevel, message: String, arguments: List<Any?>) {
                val level = when (severity) {
                    LogLevel.DEBUG -> Level.DEBUG
                    LogLevel.INFO -> Level.INFO
                    LogLevel.WARN -> Level.WARN
                    LogLevel.ERROR -> Level.ERROR
                    LogLevel.NONE -> throw IllegalStateException("NONE is not a diagnostic severity")
                }
                val args = arguments.toTypedArray()
                val capture = captures.get().lastOrNull()
                if (capture == null) {
                    logger.log(level, message, *args)
                    return
                }
                // Match Log4j's parameterized enablement check before calling a parameter's
                // formatter. A staged event must never retain compiler objects for later formatting.
                val enabled = if (logger is ExtendedLogger) logger.isEnabled(level, null, message, *args)
                    else logger.isEnabled(level)
                if (!enabled) return
                val formatted = logger.getMessageFactory<MessageFactory>().newMessage(message, *args)
                capture.record(RawLoggerEvent(logger, level, formatted.formattedMessage, formatted.throwable))
            }
            internal fun emitStackTrace(throwable: Throwable) {
                val capture = captures.get().lastOrNull()
                if (capture == null) throwable.printStackTrace()
                else capture.record(StderrEvent(throwable.stackTraceToString()))
            }
            internal fun comment(message: String, level: CommentLevel) {
                val function = Function.currFunction
                val commands = function.commands
                val size = commands.size
                Function.addComment(message, level)
                if (commands.size > size) {
                    val added = commands.last()
                    if (added is Comment) captures.get().lastOrNull()?.record(AddedComment(function, commands, added))
                }
            }
        }
    }

    internal class DiagnosticPublication internal constructor(private val deliveries: List<PublicationEvent>) {
        val diagnostics: List<CapturedDiagnostic> = Collections.unmodifiableList(deliveries.filterIsInstance<Delivery>().map { it.diagnostic })
        private var consumed = false
        /** Logger-only publication: never changes compiler counts, syntax flags or commands.
         * Nested staged commits join the active capture at apply time and remain discardable.
         */
        @Synchronized fun publishLogs() {
            check(!consumed) { "Diagnostic publication has already been consumed" }
            consumed = true
            val parent = captures.get().lastOrNull()
            if (parent == null) deliveries.forEach { it.publish() } else parent.acceptPublication(deliveries)
        }
    }

    /** Emits through a caller-owned Log4j logger without changing LogProcessor filtering or comments. */
    internal fun rawDebug(logger: Logger, message: String, vararg arguments: Any?) =
        DiagnosticCapture.emitRaw(logger, LogLevel.DEBUG, message, arguments.toList())
    internal fun rawInfo(logger: Logger, message: String, vararg arguments: Any?) =
        DiagnosticCapture.emitRaw(logger, LogLevel.INFO, message, arguments.toList())
    internal fun rawWarn(logger: Logger, message: String, vararg arguments: Any?) =
        DiagnosticCapture.emitRaw(logger, LogLevel.WARN, message, arguments.toList())
    internal fun rawError(logger: Logger, message: String, vararg arguments: Any?) =
        DiagnosticCapture.emitRaw(logger, LogLevel.ERROR, message, arguments.toList())
    internal fun captureStackTrace(throwable: Throwable) = DiagnosticCapture.emitStackTrace(throwable)

    /** Mirrors Project's direct counters while making only this delta discardable. */
    internal fun recordProjectError() { captures.get().lastOrNull()?.recordError(); Project.errorCount++ }
    internal fun recordProjectWarning() { captures.get().lastOrNull()?.recordWarning(); Project.warningCount++ }

    @PublishedApi internal fun emitDiagnostic(severity: LogLevel, message: String, thrown: Throwable? = null, attach: Boolean = true) {
        val ctx = Project.ctx.firstOrNull()
        val source = if (ctx == null) null else DiagnosticSource(MCFPPFile.currFile?.absolutePath, ctx.start?.line, ctx.start?.charPositionInLine)
        DiagnosticCapture.emit(severity, message, thrown, attach, source)
    }
    @PublishedApi internal fun diagnosticComment(message: String, level: CommentLevel) = DiagnosticCapture.comment(message, level)
    @PublishedApi internal fun diagnosticError() { captures.get().lastOrNull()?.recordError(); Project.errorCount++ }
    @PublishedApi internal fun diagnosticWarning() { captures.get().lastOrNull()?.recordWarning(); Project.warningCount++ }

    fun getCtxText(): String{
        if(Project.ctx.isNotEmpty()){
            val text = Project.ctx.first().text
            return if(text.length > 20){
                text.substring(0, 20) + "..."
            }else{
                text
            }
        }
        return ""
    }

    @JvmStatic
    inline fun debug(msg: String){
        if(level > LogLevel.DEBUG) return
        emitDiagnostic(LogLevel.DEBUG, msg)
    }

    @JvmStatic
    inline fun debug(msg: String, e: Throwable){
        if(level > LogLevel.DEBUG) return
        emitDiagnostic(LogLevel.DEBUG, msg, e)
    }

    @JvmStatic
    inline fun info(msg: String){
        if(level > LogLevel.INFO) return
        emitDiagnostic(LogLevel.INFO, msg)
    }

    @JvmStatic
    inline fun info(msg: String, e: Throwable){
        if(level > LogLevel.INFO) return
        emitDiagnostic(LogLevel.INFO, msg, e)
    }

    @JvmStatic
    inline fun warn(msg: String){
        if(level > LogLevel.WARN) return
        if(Project.ctx.isNotEmpty()){
            emitDiagnostic(LogLevel.WARN,
                "Warning while compiling \n" +
                        MCFPPFile.currFile!!.absolutePath + ">>" + msg
                        + Project.ctx.first().let { "\n" + getLineInfo(it) }
            )
            diagnosticComment(msg, CommentLevel.WARN)
        }else{
            emitDiagnostic(LogLevel.WARN, msg)
        }
        diagnosticWarning()
        if(CompileSettings.isDebug){
            emitDiagnostic(LogLevel.WARN, getStackTrace())
        }
    }

    @JvmStatic
    inline fun warn(msg: String, e: Throwable){
        if(level > LogLevel.WARN) return
        if(Project.ctx.isNotEmpty()){
            emitDiagnostic(LogLevel.WARN,
                "Error while compiling " +
                        MCFPPFile.currFile!!.absolutePath + ">>\n" + msg
                        + Project.ctx.first().let { "\n" + getLineInfo(it) }
                ,e
            )
            diagnosticComment(msg, CommentLevel.ERROR)
        }else{
            emitDiagnostic(LogLevel.WARN, "$msg\n${e.stackTraceToString()}", e, false)
        }
        diagnosticComment(msg, CommentLevel.WARN)
        diagnosticWarning()
    }

    inline fun getStackTrace(): String{
        val stackTrace = Thread.currentThread().stackTrace
        val sb = StringBuilder("Compiler Stack trace:")
        var i = 1
        var lines = 0
        var seenExpr = false

        while (i < stackTrace.size && lines < 8) {
            val frame = stackTrace[i]
            if (frame.className == "top.mcfpp.antlr.MCFPPExprVisitor") {
                if (!seenExpr) {
                    sb.append("\n    at ").append(frame)
                    seenExpr = true
                    i++
                    lines++
                } else {
                    sb.append("\n    (skip stacks in MCFPPExprVisitor)")
                    while (i < stackTrace.size && stackTrace[i].className == "top.mcfpp.antlr.MCFPPExprVisitor") {
                        i++
                    }
                }
            } else {
                sb.append("\n    at ").append(frame)
                i++
                lines++
            }
        }

        if (i < stackTrace.size) {
            sb.append("\n    ...")
        }
        return sb.toString()
    }

    @JvmStatic
    inline fun error(msg: String){
        if(level > LogLevel.ERROR) return
        if(Project.ctx.isNotEmpty()){
            emitDiagnostic(LogLevel.ERROR,
                "Error while compiling " +
                        MCFPPFile.currFile!!.absolutePath + ">>\n" + msg
                        + Project.ctx.first().let { "\n" + getLineInfo(it) }
            )
            diagnosticComment(msg, CommentLevel.ERROR)
        }else{
            emitDiagnostic(LogLevel.ERROR, msg)
        }
        diagnosticError()
        if(CompileSettings.isDebug){
            emitDiagnostic(LogLevel.ERROR, getStackTrace())
        }
    }

    @JvmStatic
    inline fun error(msg: String, e: Throwable){
        if(level > LogLevel.ERROR) return
        if(Project.ctx.isNotEmpty()){
            emitDiagnostic(LogLevel.ERROR,
                "Error while compiling " +
                        MCFPPFile.currFile!!.absolutePath + ">>\n" + msg
                        + Project.ctx.first().let { "\n" + getLineInfo(it) }
                ,e
            )
            diagnosticComment(msg, CommentLevel.ERROR)
        }else{
            emitDiagnostic(LogLevel.ERROR, "$msg\n${e.stackTraceToString()}", e, false)
        }
        diagnosticComment(msg, CommentLevel.ERROR)
        diagnosticError()
    }

    @JvmStatic
    inline fun castError(type1: String, type2: String){
        error("Cannot cast [$type1] to [$type2]")
    }

    @JvmStatic
    fun syntaxError(
        recognizer: Recognizer<*, *>,
        msg: String,
        line: Int,
        charPositionInLine: Int
    ){
        MCFPPFile.currFile?.let { file ->
            captures.get().lastOrNull()?.recordSyntax(file)
            file.syntaxError = true
        }
        DiagnosticCapture.emit(LogLevel.ERROR,
            "Syntax Error in " +
                    MCFPPFile.currFile!!.absolutePath + ">>\n" + msg + "\n"
                    + getLineInfo(recognizer, line, charPositionInLine),
            null, true, DiagnosticSource(MCFPPFile.currFile?.absolutePath, line, charPositionInLine)
        )
        diagnosticError()
    }

    fun getLineInfo(recognizer: Recognizer<*, *>, line: Int, charPositionInLine: Int): String{
        val tokens = recognizer.inputStream as CommonTokenStream
        val input = tokens.tokenSource.inputStream.toString()
        val errorLine = input.split("\n")[line - 1]

        //构建上下文指示
        val indicator = " ".repeat(charPositionInLine) + "^"
        return "$line | $errorLine\n${" ".repeat(line.toString().length)} | $indicator"
    }

    fun getLineInfo(ctx: ParserRuleContext): String {
        val startToken = ctx.start
        val stopToken = ctx.stop
        if(startToken.tokenSource != null){
            val tokenStream = startToken.tokenSource.inputStream

            val lineNumber = startToken.line
            val startColumn = startToken.charPositionInLine
            val stopColumn = stopToken.charPositionInLine + (stopToken.text?.length ?: 0)

            // 获取该行的所有文本
            val lineStartIndex = tokenStream.lastIndexOf("\n", startToken.startIndex) + 1
            val lineStopIndex = tokenStream.indexOf("\n", startToken.startIndex)
            val lineText = tokenStream.getText(Interval.of(lineStartIndex, lineStopIndex))

            // 构建上下文位置指示
            val indicator = " ".repeat(max(0, startColumn)) + "^" + "~".repeat(max(stopColumn - startColumn - 1, 0))
            return if(lineText.endsWith("\n")){
                "$lineNumber | $lineText${" ".repeat(lineNumber.toString().length)} | $indicator"
            }else{
                "$lineNumber | $lineText\n${" ".repeat(lineNumber.toString().length)} | $indicator"
            }
        }else{
            val lineNumber = startToken.line
            // 获取该行的所有文本
            val lineText = ctx.text
            // 构建上下文位置指示
            return if(lineText.endsWith("\n")){
                "$lineNumber | $lineText${" ".repeat(lineNumber.toString().length)}"
            }else{
                "$lineNumber | $lineText\n${" ".repeat(lineNumber.toString().length)}"
            }
        }
    }

    // 扩展方法：查找字符串在输入流中的索引
    private fun CharStream.indexOf(char: String, fromIndex: Int): Int {
        for (i in fromIndex..<this.size()) {
            if (this.getText(Interval.of(i, i)) == char) {
                return i
            }
        }
        return this.size() // 如果找不到，则返回流的末尾
    }

    private fun CharStream.lastIndexOf(char: String, fromIndex: Int): Int {
        for (i in fromIndex downTo 0) {
            if (this.getText(Interval.of(i, i)) == char) {
                return i
            }
        }
        return -1 // 如果找不到，则返回-1
    }
}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR, NONE
}
