package top.mcfpp

import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.io.DatapackCreator
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.GlobalField
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.UwU
import java.io.FileInputStream

/**
 * 编译器的启动入口
 */
fun main(args: Array<String>) {
    val source:ConfigurationSource
    try {
        source = ConfigurationSource(FileInputStream("log4j2.xml"))
        Configurator.initialize(null,source)
    }catch (e:Exception){
        println("Failed to load log4j2.xml")
    }
    if (args.isNotEmpty()) {
        if(args.size > 1){
            if(args.contains("debug")){
                CompileSettings.isDebug = true
                LogProcessor.warn("Compiling in debug mode.")
            }
            if(args.contains("ignoreStdLib")){
                CompileSettings.ignoreStdLib = true
                LogProcessor.warn("Ignoring standard library.")
            }
        }
        val start: Long = System.currentTimeMillis()
        LogProcessor.info("Tips: " + UwU.tip) //生成tips
        val path = args[0]
        Project.readProject(path) //读取
        Project.readLib() //读取引用的库的索引
        Project.init() //初始化
        Project.analyse() //解析
        Project.compile() //编译
        Project.optimization() //优化
        Project.genIndex() //生成索引
        Project.ctx = null
        if(Project.targetPath != "null"){
            try{
                DatapackCreator.createDatapack(Project.targetPath) //生成数据包
            }catch (e: Exception){
                LogProcessor.error("Cannot create datapack in path: ${Project.targetPath}")
            }
        }
        LogProcessor.info("Finished in " + (System.currentTimeMillis() - start) + "ms")
        GlobalField.printAll()
    }
}

object MCFPP {
    val version = "0.1.0"
}