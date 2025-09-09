package top.mcfpp

import com.ibm.icu.impl.data.ResourceReader
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.UwU
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * 编译器的启动入口
 */
fun main(args: Array<String>) {
    //获取log4j2.xml配置文件
    val source:ConfigurationSource
    try {
        source = ConfigurationSource(ResourceReader::class.java.classLoader.getResourceAsStream("log4j2.xml"))
        Configurator.initialize(null,source)
    }catch (e:Exception){
        println("Failed to load log4j2.xml")
        e.printStackTrace()
    }
    if (args.isNotEmpty()) {
        parseArgs(args.asList().subList(1, args.size))

        LogProcessor.info("Tips: " + UwU.tip) //生成tips

        val path = args[0]
        if(!Files.exists(Path(path))){
            LogProcessor.error("Cannot find file: $path")
            return
        }
        compile(Project.readConfig(path)) //读取配置文件
    }
}

fun compile(config: ProjectConfig){
    val start: Long = System.currentTimeMillis()

    Project.config = config
    if(config.root != null) Project.root = config.root!!
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
    Project.genIndex() //生成索引
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

fun parseArgs(args: List<String>){

    for (arg in args) {
        when (arg) {
            "-debug"
            -> CompileSettings.isDebug = true

            "-ignoreStdLib"
            -> CompileSettings.ignoreStdLib = true

            "-isLib"
            -> CompileSettings.isLib = true

            if (arg.startsWith("-maxWhileInline=")) arg else "$$arg"
            -> CompileSettings.maxWhileInline = arg.split("=")[1].toInt()

            "-printAll"
            -> CompileSettings.printAll = true

            if(arg.startsWith("-level=")) arg else "$$arg"
            -> LogProcessor.level = LogLevel.valueOf(arg.uppercase().split("=")[1])

            else -> {
                LogProcessor.warn("Invalid argument: $arg")
            }
        }
    }

}