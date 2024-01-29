package top.mcfpp.test

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.antlr.*
import top.mcfpp.lib.GlobalField
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path

object MCFPPStringTest {
    fun readFromString(str: String){
        val source: ConfigurationSource
        try {
            source = ConfigurationSource(FileInputStream("log4j2.xml"))
            Configurator.initialize(null,source)
        }catch (e:Exception){
            println("Failed to load log4j2.xml")
        }
        try {
            //读取json
            Project.debug("Generate debug project for a string string")
            Project.root = Path.of("./")
            Project.currFile = File("./test.mcfpp")
            Project.name = "debug"
            //版本77
            Project.version = "1.20"
            //描述
            Project.description = "debug datapacks"
            //默认命名空间
            Project.defaultNamespace = "default"
            //输出目录
            Project.targetPath = "null"
            Project.readIndex() //读取引用的库的索引
            Project.init() //初始化
            Project.debug("Analysing project...")
            //解析文件
            //添加默认库的域
            if(!CompileSettings.ignoreStdLib){
                GlobalField.importedLibNamespaces["mcfpp.sys"] = GlobalField.libNamespaces["mcfpp.sys"]
            }
            val charStream: CharStream = CharStreams.fromString(str)
            val tokens = CommonTokenStream(mcfppLexer(charStream))
            val parser = mcfppParser(tokens)
            val context = parser.compilationUnit()
            McfppFileVisitor().visit(context)
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
            Project.error("Error while reading project from string")
            e.printStackTrace()
        }
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
            Project.debug("Generate debug project for single file \"$path\"")
            val qwq = File(path)
            Project.root = Path.of(path).parent
            Project.name = qwq.name.substring(0, qwq.name.lastIndexOf('.'))
            val code = qwq.readText()
            //版本
            Project.version = "1.20"
            //描述
            Project.description = "debug datapacks"
            //默认命名空间
            Project.defaultNamespace = "default"
            //输出目录
            Project.targetPath = "null"
            Project.readIndex() //读取引用的库的索引
            Project.init() //初始化
            Project.debug("Analysing project...")
            //解析文件
            //添加默认库的域
            if(!CompileSettings.ignoreStdLib){
                GlobalField.importedLibNamespaces["mcfpp.sys"] = GlobalField.libNamespaces["mcfpp.sys"]
            }
            var charStream: CharStream = CharStreams.fromString(code)
            var tokens = CommonTokenStream(mcfppLexer(charStream))
            val parser = mcfppParser(tokens)
            val context = parser.compilationUnit()
            McfppFileVisitor().visit(context)
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
            Project.error("Error while reading project from file \"$path\"")
            e.printStackTrace()
        }
    }
}