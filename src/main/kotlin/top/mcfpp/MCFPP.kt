package top.mcfpp

import top.mcfpp.compiler.CompilationPipeline
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.scope.GlobalScope
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

/** 直接编译入口；保留逐阶段收集诊断的策略，完成源码阶段后再决定是否生成产物。 */
fun compile(config: ProjectConfig){
    val start: Long = System.currentTimeMillis()

    Project.config = config
    Project.compileStage = Project.CompileStage.PRE_INIT
    Project.stageProcessor[0].forEach { it() }
    Project.init() //初始化
    if(!Project.checkConfig()) return   //检查配置文件
    Project.readProject() //读取引用的库的索引
    CompilationPipeline.compileSources(CompilationPipeline.ErrorPolicy.CONTINUE)
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
