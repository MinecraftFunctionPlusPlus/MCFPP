package top.mcfpp

import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.io.DatapackCreator
import top.mcfpp.library.mcfpm.McfpmCliCompiler
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.io.PrintWriter
import kotlin.io.path.absolutePathString
import kotlin.system.exitProcess

/**
 * 编译器的启动入口
 */
fun main(args: Array<String>) {
    val exitCode = runCompiler(args)
    if (exitCode != 0) exitProcess(exitCode)
}

internal fun runCompiler(
    args: Array<String>,
    out: PrintWriter = PrintWriter(System.out, true),
    err: PrintWriter = PrintWriter(System.err, true),
): Int {
    return top.mcfpp.cli.ProjectCli.run(args, out, err)
}

fun compile(config: ProjectConfig){
    val start: Long = System.currentTimeMillis()

    Project.config = config
    Project.compileStage = Project.CompileStage.PRE_INIT
    Project.stageProcessor[0].forEach { it() }
    Project.init() //初始化
    if(!Project.checkConfig()) return   //检查配置文件
    Project.readProject() //读取引用的库的索引
    Project.indexType() //编制类型索引
    Project.resolveField() //编制函数索引
    Project.runAnnotation() //执行注解
    Project.compile() //编译
    Project.optimization() //优化
    if (Project.errorCount > 0) {
        Project.ctx.clear()
        return
    }
    if(!Project.config.noMcLib) Project.genIndex() //生成索引
    Project.ctx.clear()
    if(!Project.config.noDatapack){
        Project.compileStage = Project.CompileStage.GEN_DATAPACK
        try{
            DatapackCreator.createDatapack(Project.config.targetPath!!.absolutePathString()) //生成数据包
        }catch (e: Exception){
            LogProcessor.error("Cannot create datapack in path: ${Project.config.targetPath}", e)
        }
        Project.stageProcessor[Project.compileStage.ordinal].forEach { it() }
    }

    LogProcessor.info("Finished in " + (System.currentTimeMillis() - start) + "ms")
    if(CompileSettings.printAll) GlobalScope.printAll()
}

object MCFPP {
    const val VERSION = "0.1.0"
}
