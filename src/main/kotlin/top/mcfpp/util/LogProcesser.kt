@file:Suppress("NOTHING_TO_INLINE")

package top.mcfpp.util

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.command.CommentLevel
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.function.Function
import kotlin.math.min

object LogProcessor {

    fun getCtxText(): String{
        if(Project.ctx != null){
            val text = Project.ctx!!.text
            if(text.length > 20){
                return text.substring(0, 20) + "..."
            }else{
                return text
            }
        }
        return ""
    }

    var logger: Logger = LogManager.getLogger("mcfpp")

    inline fun debug(msg: String){
        logger.debug(msg)
    }

    inline fun debug(msg: String, e: Exception){
        logger.debug(msg, e)
    }

    inline fun info(msg: String){
        logger.info(msg)
    }

    inline fun info(msg: String, e: Exception){
        logger.info(msg, e)
    }

    inline fun warn(msg: String){
        if(Project.ctx != null){
            logger.warn(
                "Warning while compiling \n" +
                        MCFPPFile.currFile!!.absolutePath + ">>" + msg
                        + Project.ctx?.let { "\n" + getLineInfo(it) }
            )
            Function.addComment(msg, CommentLevel.WARN)
        }else{
            logger.warn(msg)
        }
        Project.warningCount++
        if(CompileSettings.isDebug){
            val stackTrace = Thread.currentThread().stackTrace
            val sb = StringBuilder("Compiler Stack trace:")
            for (i in 1 until  min(stackTrace.size, 8)) {
                sb.append("\n    at " + stackTrace[i].toString())
            }
            if(stackTrace.size > 6){
                sb.append("\n    ...")
            }
            logger.warn(sb.toString())
        }
    }

    inline fun warn(msg: String, e: Exception){
        logger.warn(msg, e)
        Function.addComment(msg, CommentLevel.WARN)
        Project.warningCount++
        if(CompileSettings.isDebug){
            val stackTrace = Thread.currentThread().stackTrace
            val sb = StringBuilder("Compiler Stack trace:")
            for (i in 1 until  min(stackTrace.size, 8)) {
                sb.append("\n    at " + stackTrace[i].toString())
            }
            if(stackTrace.size > 6){
                sb.append("\n    ...")
            }
            logger.warn(sb.toString())
        }
    }

    inline fun error(msg: String){
        if(Project.ctx != null){
            logger.error(
                "Error while compiling " +
                        MCFPPFile.currFile!!.absolutePath + ">>\n" + msg
                        + Project.ctx?.let { "\n" + getLineInfo(it) }
            )
            Function.addComment(msg, CommentLevel.ERROR)
        }else{
            logger.warn(msg)
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

    inline fun error(msg: String, e: Exception){
        logger.error("$msg\n${e.javaClass}: ${e.message}")
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

    inline fun castError(type1: String, type2: String){
        error("Cannot cast [$type1] to [$type2]")
    }

    fun syntaxError(
        recognizer: Recognizer<*, *>,
        msg: String,
        offendingSymbol: Token,
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
        val tokenStream = startToken.tokenSource.inputStream

        val lineNumber = startToken.line
        val startColumn = startToken.charPositionInLine
        val stopColumn = stopToken.charPositionInLine + (stopToken.text?.length ?: 0)

        // 获取该行的所有文本
        val lineStartIndex = tokenStream.lastIndexOf("\n", startToken.startIndex) + 1
        val lineStopIndex = tokenStream.indexOf("\n", startToken.startIndex)
        val lineText = tokenStream.getText(Interval.of(lineStartIndex, lineStopIndex))

        // 构建上下文位置指示
        val indicator = " ".repeat(startColumn) + "^" + "~".repeat(stopColumn - startColumn - 1)
        return if(lineText.endsWith("\n")){
            "$lineNumber | $lineText${" ".repeat(lineNumber.toString().length)} | $indicator"
        }else{
            "$lineNumber | $lineText\n${" ".repeat(lineNumber.toString().length)} | $indicator"
        }
    }

    // 扩展方法：查找字符串在输入流中的索引
    fun CharStream.indexOf(char: String, fromIndex: Int): Int {
        for (i in fromIndex until this.size()) {
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