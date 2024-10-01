package top.mcfpp.util

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.command.CommentType
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.function.Function
import kotlin.math.min

object LogProcessor {

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
        logger.warn(
            msg + if(Project.ctx !=null) {"\n    at " + MCFPPFile.currFile!!.absolutePath + ":" + Project.ctx!!.getStart().line}else{""}
        )
        Function.addComment(msg, CommentType.WARN)
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
        Function.addComment(msg, CommentType.WARN)
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
        logger.error(
            msg + if(Project.ctx !=null) {"\n    at " + MCFPPFile.currFile!!.absolutePath + ":" + Project.ctx!!.getStart().line}else{""}
        )
        Function.addComment(msg, CommentType.ERROR)
        Project.errorCount++
        if(CompileSettings.isDebug){
            val stackTrace = Thread.currentThread().stackTrace
            val sb = StringBuilder("Compiler Stack trace:")
            for (i in 1 until  min(stackTrace.size, 8)) {
                sb.append("\n    at " + stackTrace[i].toString())
            }
            if(stackTrace.size > 6){
                sb.append("\n    ...")
            }
            logger.error(sb.toString())
        }
    }

    inline fun error(msg: String, e: Exception){
        logger.error(msg, e)
        Function.addComment(msg, CommentType.ERROR)
        Project.errorCount++
        if(CompileSettings.isDebug){
            val stackTrace = Thread.currentThread().stackTrace
            val sb = StringBuilder("Compiler Stack trace:")
            for (i in 1 until  min(stackTrace.size, 8)) {
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
}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR, NONE
}