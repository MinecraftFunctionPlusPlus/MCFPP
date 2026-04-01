package top.mcfpp.test.util

import com.ibm.icu.impl.data.ResourceReader
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.Project.compileStage
import top.mcfpp.Project.stageProcessor
import top.mcfpp.antlr.*
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.parseArgs
import top.mcfpp.util.LogProcessor
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

object MCFPPStringTest {
    fun readFromString(str: String, args: Array<String> = arrayOf(), targetPath: String? = null){
        val source = ConfigurationSource(ResourceReader::class.java.classLoader.getResourceAsStream("log4j2.xml"))
        Configurator.initialize(null,source)
        //编译参数
        parseArgs(args.asList())
        Project.compileStage = Project.CompileStage.PRE_INIT
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
        Project.config.targetPath = targetPath?.let { Path(it) }
        CompileSettings.isDebug = true
        Project.init() //初始化
        Project.readProject() //读取引用的库的索引
        //解析文件
        val charStream: CharStream = CharStreams.fromString(str)
        val tokens = CommonTokenStream(mcfppLexer(charStream))
        val parser = mcfppParser(tokens)
        val context = parser.compilationUnit()
        MCFPPFile.currFile = MCFPPFile()
        LogProcessor.debug("Generate Type Index...")
        Project.currNamespace = MCFPPFile.currFile!!.namespace.identifier
        MCFPPTypeVisitor().visitCompilationUnit(context)
        MCFPPFile.currFile!!.field.namespaceField = GlobalScope.localNamespaces[Project.currNamespace]!!.scope

        //匹配伴随对象
        GlobalScope.localNamespaces.values.flatMap { it.scope.template.values }.forEach {
            GlobalScope.localNamespaces[it.namespace]?.scope?.getObject(it.identifier)?.let { obj ->
                it.companionObject = obj as? ObjectDataTemplate
            }
        }
        //解析所有泛型类的泛型参数类型
        stageProcessor[compileStage.ordinal].forEach { it() }

        LogProcessor.debug("Generate Function Index...")
        MCFPPFieldVisitor().visit(context)
        GlobalScope.importedLibNamespaces.clear()

        //继承解析
        GlobalScope.localNamespaces.values.flatMap { it.scope.template.values }.forEach {
            if(it.parent.isNotEmpty()){
                it.flatExtends()
            }else{
                (it.extends(DataTemplate.baseDataTemplate) as DataTemplate).flatExtends()
            }
        }
        
        val visitor = MCFPPImVisitor()
        LogProcessor.debug("Compiling mcfpp code...")
        visitor.visit(context)
        Project.optimization() //优化
        if(targetPath != null) Project.genIndex() //生成索引
        Project.ctx.clear()
        if(Project.config.targetPath != null){
            try{
                DatapackCreator.createDatapack(Project.config.targetPath!!.absolutePathString()) //生成数据包
            }catch (e: Exception){
                LogProcessor.error("Cannot create datapack in path: ${Project.config.targetPath}")
            }
        }
        Project.ctx.clear()
        GlobalScope.printAll()
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
            Project.config.targetPath = null
            Project.readProject() //读取引用的库的索引
            Project.init() //初始化
            LogProcessor.debug("Analysing project...")
            //解析文件
            //添加默认库的域
            if(!CompileSettings.ignoreStdLib){
                GlobalScope.importedLibNamespaces["mcfpp.sys"] = GlobalScope.libNamespaces["mcfpp.sys"]!!
            }
            val charStream: CharStream = CharStreams.fromString(code)
            val tokens = CommonTokenStream(mcfppLexer(charStream))
            val parser = mcfppParser(tokens)
            val context = parser.compilationUnit()
            MCFPPFieldVisitor().visit(context)
            GlobalScope.importedLibNamespaces.clear()
            //添加默认库域
            if(!CompileSettings.ignoreStdLib){
                GlobalScope.importedLibNamespaces["mcfpp.sys"] = GlobalScope.libNamespaces["mcfpp.sys"]!!
            }
            val visitor = MCFPPImVisitor()
            visitor.visit(context)
            Project.optimization() //优化
            Project.genIndex() //生成索引
            Project.ctx.clear()
            GlobalScope.printAll()
        } catch (e: Exception) {
            LogProcessor.error("Error while reading project from file \"$path\"")
            e.printStackTrace()
        }
    }
}