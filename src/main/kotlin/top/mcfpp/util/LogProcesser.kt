@file:Suppress("NOTHING_TO_INLINE")

package top.mcfpp.util

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.misc.Interval
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.command.CommentLevel
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.function.Function
import kotlin.math.max
import kotlin.math.min

object LogProcessor {

    var level: LogLevel = LogLevel.DEBUG

    fun getCtxText(): String{
        if(Project.ctx.isNotEmpty()){
            val text = Project.ctx.first().text
            if(text.length > 20){
                return text.substring(0, 20) + "..."
            }else{
                return text
            }
        }
        return ""
    }

    var logger: Logger = LogManager.getLogger("mcfpp")

    @JvmStatic
    inline fun debug(msg: String){
        if(level > LogLevel.DEBUG) return
        logger.debug(msg)
    }

    @JvmStatic
    inline fun debug(msg: String, e: Exception){
        if(level > LogLevel.DEBUG) return
        logger.debug(msg, e)
    }

    @JvmStatic
    inline fun info(msg: String){
        if(level > LogLevel.INFO) return
        logger.info(msg)
    }

    @JvmStatic
    inline fun info(msg: String, e: Exception){
        if(level > LogLevel.INFO) return
        logger.info(msg, e)
    }

    @JvmStatic
    inline fun warn(msg: String){
        if(level > LogLevel.WARN) return
        logger.warn(msg)
        if(Project.ctx.isNotEmpty()){
            logger.warn(
                "Warning while compiling \n" +
                        MCFPPFile.currFile!!.absolutePath + ">>" + msg
                        + Project.ctx.first().let { "\n" + getLineInfo(it) }
            )
            Function.addComment(msg, CommentLevel.WARN)
        }else{
            logger.warn(msg)
        }
        Project.warningCount++
        if(CompileSettings.isDebug){
            val stackTrace = Thread.currentThread().stackTrace
            val sb = StringBuilder("Compiler Stack trace:")
            for (i in 1..<min(stackTrace.size, 8)) {
                sb.append("\n    at " + stackTrace[i].toString())
            }
            if(stackTrace.size > 6){
                sb.append("\n    ...")
            }
            logger.warn(sb.toString())
        }
    }

    @JvmStatic
    inline fun warn(msg: String, e: Exception){
        if(level > LogLevel.WARN) return
        logger.warn(msg, e)
        Function.addComment(msg, CommentLevel.WARN)
        Project.warningCount++
        if(CompileSettings.isDebug){
            val stackTrace = Thread.currentThread().stackTrace
            val sb = StringBuilder("Compiler Stack trace:")
            for (i in 1..<min(stackTrace.size, 8)) {
                sb.append("\n    at " + stackTrace[i].toString())
            }
            if(stackTrace.size > 6){
                sb.append("\n    ...")
            }
            logger.warn(sb.toString())
        }
    }

    @JvmStatic
    inline fun error(msg: String){
        if(level > LogLevel.ERROR) return
        if(Project.ctx.isNotEmpty()){
            logger.error(
                "Error while compiling " +
                        MCFPPFile.currFile!!.absolutePath + ">>\n" + msg
                        + Project.ctx.first().let { "\n" + getLineInfo(it) }
            )
            Function.addComment(msg, CommentLevel.ERROR)
        }else{
            logger.error(msg)
        }
        Project.errorCount++
        if(CompileSettings.isDebug){
            val stackTrace = Thread.currentThread().stackTrace
            val sb = StringBuilder("Compiler Stack trace:")
            for (i in 1..<min(stackTrace.size, 8)) {
                sb.append("\n    at " + stackTrace[i].toString())
            }
            if(stackTrace.size > 6){
                sb.append("\n    ...")
            }
            logger.error(sb.toString())
        }
    }

    @JvmStatic
    inline fun error(msg: String, e: Exception){
        if(level > LogLevel.ERROR) return
        logger.error("$msg\n${e.stackTraceToString()}")
        Function.addComment(msg, CommentLevel.ERROR)
        Project.errorCount++
        if(CompileSettings.isDebug){
            val stackTrace = Thread.currentThread().stackTrace
            val sb = StringBuilder("Compiler Stack trace:")
            for (i in 1..<min(stackTrace.size, 8)) {
                sb.append("\n    at " + stackTrace[i].toString())
            }
            if(stackTrace.size > 6){
                sb.append("\n    ...")
            }
            logger.error(sb.toString())
        }
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
        MCFPPFile.currFile?.syntaxError = true
        logger.error(
            "Syntax Error in " +
                    MCFPPFile.currFile!!.absolutePath + ">>\n" + msg + "\n"
                    + getLineInfo(recognizer, line, charPositionInLine)
        )
        Project.errorCount++
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
    fun CharStream.indexOf(char: String, fromIndex: Int): Int {
        for (i in fromIndex..<this.size()) {
            if (this.getText(Interval.of(i, i)) == char) {
                return i
            }
        }
        return this.size() // 如果找不到，则返回流的末尾
    }

    fun CharStream.lastIndexOf(char: String, fromIndex: Int): Int {
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