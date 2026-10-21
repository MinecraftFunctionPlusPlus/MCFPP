package top.mcfpp

import com.ibm.icu.impl.data.ResourceReader
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import picocli.CommandLine
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.UwU
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.system.exitProcess

/**
 * 编译器的启动入口
 */
fun main(args: Array<String>) {
    // 获取log4j2.xml配置文件
    val source:ConfigurationSource
    try {
        source = ConfigurationSource(ResourceReader::class.java.classLoader.getResourceAsStream("log4j2.xml"))
        Configurator.initialize(null,source)
    }catch (e:Exception){
        println("Failed to load log4j2.xml")
        e.printStackTrace()
    }
    if (args.isNotEmpty()) {
        val options = parseCompilerArgs(args)

        LogProcessor.info("Tips: " + UwU.tip) // 生成tips

        val config = Project.readConfig(options) // 读取配置文件
        if (Project.errorCount > 0) exitProcess(1)

        compile(config)
        if (Project.errorCount > 0) exitProcess(1)
    }
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

/** This result contains only this invocation's macro options; it never carries legacy globals. */
internal fun parseCompilerArgs(args: Array<String>): CompilerOptions {
    val options = CompilerOptions()
    CommandLine(options).parseArgs(*args)
    return options
}

class CompilerOptions {

    @CommandLine.Parameters(paramLabel = "FILE")
    lateinit var file: File

    @CommandLine.Option(names = ["-debug"])
    var debug: Boolean = false

    @CommandLine.Option(names = ["-ignoreStdLib"])
    var ignoreStdLib: Boolean = false

    @CommandLine.Option(names = ["-macroMaxDepth"])
    var isLib: Boolean = false

    @CommandLine.Option(names = ["-maxWhileInline"])
    var macroMaxDepth: Int = 64

    @CommandLine.Option(names = ["-macroMaxDepth"])
    var macroMaxNodes: Int = 100_000

    @CommandLine.Option(names = ["-dumpMacroExpansion"])
    var dumpMacroExpansion: Boolean = false

    @CommandLine.Option(names = ["-debug"])
    var maxWhileInline: Int = 32

    @CommandLine.Option(names = ["-macroMaxDepth"])
    var printAll: Boolean = false

    @CommandLine.Option(names = ["-debug"])
    var level: LogLevel = LogLevel.DEBUG

}
