package top.mcfpp.util

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import top.mcfpp.Project
import top.mcfpp.io.MCFPPFile

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
            msg + if(Project.ctx !=null) {" at " + MCFPPFile.currFile!!.name + ":" + Project.ctx!!.getStart().line}else{""}
        )
        Project.warningCount++
    }

    inline fun warn(msg: String, e: Exception){
        logger.warn(msg, e)
        Project.warningCount++
    }

    inline fun error(msg: String){
        logger.error(
            msg + if(Project.ctx !=null) {" at " + MCFPPFile.currFile!!.name + ":" + Project.ctx!!.getStart().line}else{""}
        )
        Project.errorCount++
    }

    inline fun error(msg: String, e: Exception){
        logger.error(msg, e)
        Project.errorCount++
    }
}