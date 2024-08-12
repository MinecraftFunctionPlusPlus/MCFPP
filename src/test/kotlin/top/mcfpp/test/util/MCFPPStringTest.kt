package top.mcfpp.test.util

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.antlr.*
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.field.GlobalField
import top.mcfpp.util.LogProcessor
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path

object MCFPPStringTest {
    fun readFromString(str: String, targetPath: String = "null"){
        val source: ConfigurationSource
        source = ConfigurationSource(FileInputStream("log4j2.xml"))
        Configurator.initialize(null,source)
        //读取json
        LogProcessor.debug("Generate debug project for a string")
        Project.config.root = Path.of("./")
        Project.config.name = "debug"
        //版本77
        Project.config.version = "1.20"
        //描述
        Project.config.description = "debug datapacks"
        //默认命名空间
        Project.config.rootNamespace = "default"
        //输出目录
        Project.config.targetPath = targetPath
        Project.init() //初始化
        Project.readLib() //读取引用的库的索引
        //解析文件
        val charStream: CharStream = CharStreams.fromString(str)
        val tokens = CommonTokenStream(mcfppLexer(charStream))
        val parser = mcfppParser(tokens)
        val context = parser.compilationUnit()
        MCFPPFile.currFile = MCFPPFile("./test.mcfpp")
        LogProcessor.debug("Generate Type Index...")
        McfppTypeVisitor().visit(context)
        LogProcessor.debug("Generate Function Index...")
        McfppFieldVisitor().visit(context)
        GlobalField.importedLibNamespaces.clear()
        val visitor = McfppImVisitor()
        LogProcessor.debug("Compiling mcfpp code...")
        visitor.visit(context)
        Project.optimization() //优化
        Project.genIndex() //生成索引
        Project.ctx = null
        if(Project.config.targetPath != "null"){
            try{
                DatapackCreator.createDatapack(Project.config.targetPath) //生成数据包
            }catch (e: Exception){
                LogProcessor.error("Cannot create datapack in path: ${Project.config.targetPath}")
            }
        }
        Project.ctx = null
        GlobalField.printAll()
    }

    fun readFromSingleFile(path: String){
        val source:ConfigurationSource
        try {
            source = ConfigurationSource(FileInputStream("log4j2.xml"))
            Configurator.initialize(null,source)
        }catch (e:Exception){
            println("Failed to load log4j2.xml")
        }
        try {
            //读取json
            LogProcessor.debug("Generate debug project for single file \"$path\"")
            val qwq = File(path)
            Project.config.root = Path.of(path).parent
            Project.config.name = qwq.name.substring(0, qwq.name.lastIndexOf('.'))
            val code = qwq.readText()
            //版本
            Project.config.version = "1.20"
            //描述
            Project.config.description = "debug datapacks"
            //默认命名空间
            Project.config.rootNamespace = "default"
            //输出目录
            Project.config.targetPath = "null"
            Project.readLib() //读取引用的库的索引
            Project.init() //初始化
            LogProcessor.debug("Analysing project...")
            //解析文件
            //添加默认库的域
            if(!CompileSettings.ignoreStdLib){
                GlobalField.importedLibNamespaces["mcfpp.sys"] = GlobalField.libNamespaces["mcfpp.sys"]
            }
            var charStream: CharStream = CharStreams.fromString(code)
            var tokens = CommonTokenStream(mcfppLexer(charStream))
            val parser = mcfppParser(tokens)
            val context = parser.compilationUnit()
            McfppFieldVisitor().visit(context)
            GlobalField.importedLibNamespaces.clear()
            //添加默认库域
            if(!CompileSettings.ignoreStdLib){
                GlobalField.importedLibNamespaces["mcfpp.sys"] = GlobalField.libNamespaces["mcfpp.sys"]
            }
            val visitor = McfppImVisitor()
            visitor.visit(context)
            Project.optimization() //优化
            Project.genIndex() //生成索引
            Project.ctx = null
            GlobalField.printAll()
        } catch (e: Exception) {
            LogProcessor.error("Error while reading project from file \"$path\"")
            e.printStackTrace()
        }
    }
}