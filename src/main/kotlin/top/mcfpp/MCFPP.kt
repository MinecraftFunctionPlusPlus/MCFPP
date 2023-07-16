package top.mcfpp

import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.io.DatapackCreator
import top.mcfpp.lib.*
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
    if (args.size == 1) {
        val start: Long = System.currentTimeMillis()
        Project.info("Tips: " + UwU.tip) //生成tips
        val path = args[0]
        Project.readProject(path) //读取
        Project.init() //初始化
        Project.analyse() //解析
        Project.compile() //编译
        Project.optimization() //优化
        Project.genIndex() //生成索引
        Project.ctx = null
        DatapackCreator.createDatapack(Project.targetPath) //生成数据包
        Project.info("Finished in " + (System.currentTimeMillis() - start) + "ms")
        Cache.printAll()
    }
}