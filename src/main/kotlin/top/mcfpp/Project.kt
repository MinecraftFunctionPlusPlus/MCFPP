package top.mcfpp

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.ibm.icu.impl.data.ResourceReader
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.tools.zip.ZipFile
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.CommentLevel
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.UnresolvedVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.ClassPointer
import top.mcfpp.io.LibBinReader
import top.mcfpp.io.LibBinWriter
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.SbObject
import top.mcfpp.model.Namespace
import top.mcfpp.model.Native
import top.mcfpp.model.compound.ObjectClass
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.Module
import top.mcfpp.util.ModuleType
import top.mcfpp.util.Utils
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile
import kotlin.io.path.*
import kotlin.system.exitProcess


/**
 * 一个工程。工程文件包含了这个mcfpp工程编译需要的所有信息。编译器将会以这个文件为入口开始编译。
 * 同时，这个工程文件的名字也是此文件编译生成的数据包的命名空间。
 */
object Project {

    private var logger: Logger = LogManager.getLogger("mcfpp")

    var config = ProjectConfig()

    val ctx: ArrayDeque<ParserRuleContext> = ArrayDeque()

    var modules = ArrayList<Module>()

    fun enableModulePackage(packageName: String, moduleName: String? = null){
        if(moduleName == null){
            for (module in modules){
                for (p in module.packages){
                    if(p.key.id == packageName) module.packages[p.key] = true
                }
            }
        }else{
            modules.filter { it.id == moduleName }.forEach {
                for (p in it.packages){
                    if(p.key.id == packageName) it.packages[p.key] = true
                }
            }
        }
    }

    inline fun <T> withCompilationContext(ctx: ParserRuleContext, block: () -> T): T {
        Project.ctx.addFirst(ctx)
        try {
            return block()
        } catch (e: Exception) {
            LogProcessor.error("Fatal error")
            LogProcessor.error("Caused by: ", e)
            exitProcess(1)
        } finally {
            Project.ctx.removeFirst()
        }
    }


    /**
     * 当前解析文件的语法树
     */
    var trees:MutableMap<MCFPPFile,ParseTree> = mutableMapOf()
    var tokens:MutableMap<MCFPPFile,CommonTokenStream> = mutableMapOf()

    /**
     * 当前的命名空间
     */
    var currNamespace = config.rootNamespace

    /**
     * 工程中的总错误数量
     */
    var errorCount = 0

    /**
     * 工程中的总警告数量
     */
    var warningCount = 0

    lateinit var projectTick : Function

    lateinit var projectLoad : Function

    lateinit var projectInit : Function

    /**
     * 常量池
     */
    val constants : HashMap<Any, Var<*>> = HashMap()

    /**
     * 宏命令
     */
    val macroFunction : LinkedHashMap<String, String> = LinkedHashMap()

    var compileStage = CompileStage.PRE_INIT
    enum class CompileStage {
        PRE_INIT,
        INIT,
        READ_LIB,
        INDEX_TYPE,
        RESOLVE_FIELD,
        RUN_ANNOTATION,
        COMPILE,
        OPTIMIZATION,
        GEN_INDEX,
        GEN_DATAPACK
    }

    /**
     * 编译阶段处理器。每个阶段的处理器都会在对应的阶段被调用。
     */
    val stageProcessor = Array(CompileStage.entries.size) { ArrayList<()->Unit>() }

    var classLoader: ClassLoader = Thread.currentThread().contextClassLoader

    private val files = ArrayList<MCFPPFile>()

    val mcfppSystemTick: Function = Function("sys.tick","mcfpp", null).apply {
        commands.addAll(
            arrayOf(
                //指针清理
                Command("execute as @e[type=marker,tag=mcfpp_gc] if score @s ${SbObject.MCFPP_POINTER_COUNTER} matches ..0 run kill @s"),
                //内存泄露检查
                Command("execute if data storage mcfpp:system stack_frame[0] run tellraw @a {\"text\":\"[MCFPP]Stack Leak\"}"),
                Command("execute if data storage mcfpp:system stack_frame[0] run data modify storage mcfpp:system stack_frame set value []"),
            )
        )
    }

    /**
     * 初始化
     */
    fun init() {
        compileStage = CompileStage.INIT
        //全局缓存初始化
        GlobalField.init()
        ctx.clear()
        trees.clear()
        currNamespace = config.rootNamespace
        errorCount = 0
        warningCount = 0
        constants.clear()
        macroFunction.clear()
        classLoader = Thread.currentThread().contextClassLoader
        files.clear()
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    fun readConfig(path: String): ProjectConfig{
        val config = ProjectConfig()
        //工程信息读取
        try {
            //读取json
            logger.debug("Reading project from file \"$path\"")
            val reader = FileReader(path)
            val qwq = File(path)
            config.root = Path.of(path).toAbsolutePath().parent
            config.name = qwq.name.substring(0, qwq.name.lastIndexOf('.'))
            val json = reader.readText()

            //解析json
            val jsonObject: JSONObject = JSONObject.parse(json) as JSONObject

            //源代码根目录
            if(jsonObject.containsKey("sourcePath")){
                config.sourcePath = Path(jsonObject.getString("sourcePath"))
                jsonObject.remove("sourcePath")
            }

            //版本
            if(jsonObject.containsKey("version")){
                config.version = jsonObject.getString("version")
                jsonObject.remove("version")
            }

            //描述
            if(jsonObject.containsKey("description")){
                config.description = jsonObject.getString("description")
                jsonObject.remove("description")
            }

            //默认命名空间
            if(jsonObject.containsKey("namespace")){
                config.rootNamespace = jsonObject.getString("namespace")
                jsonObject.remove("namespace")
            }

            //调用库
            if(jsonObject.containsKey("jars")){
                val jarsJson: JSONArray = jsonObject.getJSONArray("jars")
                for (i in 0..<jarsJson.size) {
                    config.jars.add(jarsJson.getString(i))
                }
                jsonObject.remove("jars")
            }

            //输出目录
            if(jsonObject.containsKey("targetPath")){
                config.targetPath = Path(jsonObject.getString("targetPath"))
                jsonObject.remove("targetPath")
            }

            //是否生成数据包
            if(jsonObject.containsKey("noDatapack")){
                config.noDatapack = jsonObject.getBoolean("noDatapack")
                jsonObject.remove("noDatapack")
            }

            //注释等级
            if(jsonObject.containsKey("commentLevel")){
                val str = jsonObject.getString("commentLevel")
                config.commentLevel = try {
                    CommentLevel.valueOf(str.uppercase())
                }catch (e: Exception){
                    LogProcessor.error("Unsupported comment level: $str, using default value \"DEBUG\"")
                    CommentLevel.DEBUG
                }
                jsonObject.remove("commentLevel")
            }

            //编译参数
            if(jsonObject.containsKey("args")){
                val compileArgsJson = jsonObject.getJSONArray("args")
                parseArgs(compileArgsJson.toList(String::class.java))
                jsonObject.remove("args")
            }

            for (key in jsonObject.keys) {
                LogProcessor.warn("Unsupported config item: $key")
            }

        } catch (e: Exception) {
            LogProcessor.error("Error while reading project from file \"$path\"")
            e.printStackTrace()
        }

        return config
    }

    fun checkConfig(): Boolean{
        if (!Utils.version.contains(config.version)){
            LogProcessor.warn("Unsupported version: ${config.version}")
            config.version = Utils.version[0]
        }
        if(config.targetPath == null){
            LogProcessor.warn("Set target path default to \"${config.root.pathString}/build/\"")
            config.targetPath = Path(config.root.pathString,"build/")
        }
        if(config.sourcePath == null){
            LogProcessor.warn("Set source path default to \"${config.root.pathString}\"")
            config.sourcePath = Path(config.root.pathString)
        }
        if(config.sourcePath!!.notExists()){
            LogProcessor.error("Invalid source path: ${config.sourcePath}")
            return false
        }
        return true
    }

    /**
     * 读取库文件，并将库写入缓存
     */
    fun readProject(){
        compileStage = CompileStage.READ_LIB
        //生成默认命名空间
        GlobalField.localNamespaces[config.rootNamespace] = Namespace(config.rootNamespace)

        //生成tick/load/init函数
        projectTick = Function("tick", config.rootNamespace, null)
        projectLoad = Function("load", config.rootNamespace, null)
        projectInit = Function("init", config.rootNamespace, null)
        GlobalField.localNamespaces[config.rootNamespace]!!.field.addFunction(projectTick, true)
        GlobalField.localNamespaces[config.rootNamespace]!!.field.addFunction(projectLoad, true)
        GlobalField.localNamespaces[config.rootNamespace]!!.field.addFunction(projectInit, true)
        FunctionTag.TICK.functions.add(projectTick)
        FunctionTag.LOAD.functions.add(projectLoad)
        FunctionTag.LOAD.functions.add(projectInit)


        //读取所有jar
        for (jar in config.jars){
            if(Paths.get(jar).notExists()){
                LogProcessor.error("Cannot find jar at: $jar")
                continue
            }
            val url = Paths.get(jar).toUri().toURL()
            classLoader = URLClassLoader(arrayOf(url), classLoader)
        }


        fun readFromJar(path: String){
            val jarFile = JarFile(path)
            if(!Path(path).exists()){
                LogProcessor.warn("Cannot find jar at: $path")
                return
            }
            val jarEntry = jarFile.getJarEntry("datapack/bin.mclib")
            if (jarEntry != null) {
                jarFile.getInputStream(jarEntry).use {
                    LibBinReader.readFromStream(it)
                }
            }else{
                LogProcessor.warn("Cannot find lib file at: ${jarFile.name}")
            }
            val moduleEntry = jarFile.getJarEntry("datapack/module.json")
            if (moduleEntry != null) {
                jarFile.getInputStream(moduleEntry).use {stream ->
                    val json = stream.reader().readText()
                    val jsonObject: JSONObject = JSONObject.parse(json) as JSONObject
                    modules += Module.fromJson(jsonObject).onEach { it.type = ModuleType.JAR }
                }
            }else{
                LogProcessor.warn("Cannot find module.json at: ${jarFile.name}")
            }
        }

        fun readFromDirectory(directory: String){
            val libFile = File(directory, "bin.mclib")
            if (libFile.exists()) {
                libFile.inputStream().use {
                    LibBinReader.readFromStream(it)
                }
            }else{
                LogProcessor.warn("Cannot find lib file at: $directory")
            }
            val moduleFile = File(directory, "module.json")
            if (moduleFile.exists()) {
                moduleFile.inputStream().use {stream ->
                    val json = stream.reader().readText()
                    val jsonObject: JSONObject = JSONObject.parse(json) as JSONObject
                    modules += Module.fromJson(jsonObject).onEach { it.type = ModuleType.DIR }
                }
            }else{
                LogProcessor.warn("Cannot find module.json at: $directory")
            }
        }

        fun readFromZip(path: String){
            val zipFile = ZipFile(path)
            if(!Path(path).exists()){
                LogProcessor.warn("Cannot find zip at: $path")
                return
            }
            val zipEntry = zipFile.getEntry("datapack/bin.mclib")
            if (zipEntry!= null) {
                zipFile.getInputStream(zipEntry).use {
                    LibBinReader.readFromStream(it)
                }
            }
            val moduleEntry = zipFile.getEntry("datapack/module.json")
            if (moduleEntry != null) {
                zipFile.getInputStream(moduleEntry).use {stream ->
                    val json = stream.reader().readText()
                    val jsonObject: JSONObject = JSONObject.parse(json) as JSONObject
                    modules += Module.fromJson(jsonObject).onEach { it.type = ModuleType.ZIP }
                }
            }else{
                LogProcessor.warn("Cannot find module.json at: ${zipFile.name}")
            }
        }

        //默认的
        if(!CompileSettings.ignoreStdLib){
            LogProcessor.info("Reading lib file at: datapack/bin.mclib")
            val inputStream = ResourceReader::class.java.classLoader.getResourceAsStream("datapack/bin.mclib")

            if (inputStream == null) {
                LogProcessor.error("Cannot find lib file at: datapack/bin.mclib")
                return
            }
            LibBinReader.readFromStream(inputStream)

            //模块信息读取
            val jsonStream = ResourceReader::class.java.classLoader.getResourceAsStream("datapack/module.json")
            if (jsonStream == null) {
                LogProcessor.error("Cannot find module file at: datapack/module.json")
                return
            }
            val json = jsonStream.reader().readText()
            val jsonObject: JSONObject = JSONObject.parse(json) as JSONObject
            modules += Module.fromJson(jsonObject).onEach { it.type = ModuleType.INNER }

        }
        //写入缓存
        for (include in config.includes) {
            LogProcessor.info("Reading lib at: $include")
            try {
                if(include.endsWith(".jar")){
                    readFromJar(include)
                }else if(include.endsWith(".zip")){
                    readFromZip(include)
                }else{
                    readFromDirectory(include)
                }
            } catch (e: IOException) {
                LogProcessor.error("Error while reading lib file at $include: $e")
            }
        }
        //实例化所有类中的成员字段
        for(namespace in GlobalField.libNamespaces.values){
            namespace.field.forEachClass { c ->
                run {
                    for (v in c.field.allVars){
                        if(v is UnresolvedVar){
                            c.field.putVar(c.identifier, v.resolve(c), true)
                        }
                    }
                }
            }
        }
        //实例化所有类中的成员字段
        for(namespace in GlobalField.stdNamespaces.values){
            namespace.field.forEachClass { c ->
                run {
                    for (v in c.field.allVars){
                        if(v is UnresolvedVar){
                            c.field.putVar(c.identifier, v.resolve(c), true)
                        }
                    }
                }
            }
        }
        //函数参数解析
        GlobalField.importedLibNamespaces.clear()
        //读取所有文件
        if (config.sourcePath != null) {
            MCFPPFile.findFiles(config.sourcePath!!.absolutePathString()).forEach {
                files.add(MCFPPFile(it.toFile()))
            }
        }
        if(files.isEmpty() && config.sourcePath != null){
            LogProcessor.error("Cannot find any mcfpp file in path: ${config.sourcePath}")
        }
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    /**
     * 编制类型索引
     */
    fun indexType(){
        compileStage = CompileStage.INDEX_TYPE
        logger.debug("Generate Type Index...")
        //解析文件
        for (file in files) {
            try {
                file.indexType()
            } catch (e: Exception) {
                logger.error("Error while generate type index in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalField.importedLibNamespaces.clear()
        }
        //运行命令
        for (file in files) {
            try {
                file.runCommand()
            } catch (e: Exception) {
                logger.error("Error while generate run command in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalField.importedLibNamespaces.clear()
        }
        //解析所有泛型类的泛型参数类型
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    /**
     * 编制函数索引，解析类/模板成员
     */
    fun resolveField() {
        compileStage = CompileStage.RESOLVE_FIELD
        logger.debug("Generate Function Index...")
        //解析文件
        for (file in files) {
            try {
                file.resolveField()
            } catch (e: IOException) {
                logger.error("Error while generate function index in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalField.importedLibNamespaces.clear()
        }
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    fun runAnnotation(){
        compileStage = CompileStage.RUN_ANNOTATION
        logger.debug("Run Annotation...")
        //解析文件
        for (file in files) {
            try {
                file.runAnnotation()
            } catch (e: IOException) {
                logger.error("Error while run annotation in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalField.importedLibNamespaces.clear()
        }
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    /**
     * 编译工程
     */
    fun compile() {
        compileStage = CompileStage.COMPILE
        //工程文件编译
        //解析文件
        for (file in files) {
            LogProcessor.debug("Compiling mcfpp code in \"$file\"")
            try {
                file.compile()
            } catch (e: IOException) {
                logger.error("Error while compiling file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
        }
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    /**
     * 整理并优化工程
     */
    @InsertCommand
    fun optimization() {
        compileStage = CompileStage.OPTIMIZATION
        logger.debug("Optimizing...")
        logger.debug("Adding scoreboards declare in mcfpp:load function")

        //向load函数中添加记分板初始化命令
        projectLoad.runInFunction {
            for (scoreboard in GlobalField.scoreboards.values){
                Function.addCommand("scoreboard objectives add ${scoreboard.name} ${scoreboard.criterion}")
            }

            Function.addComment("math:_init", CommentLevel.INFO)

            //向load函数中添加库初始化命令
            Function.addCommand("execute unless score math mcfpp_init matches 1 run function math:_init")
            //向load函数中添加实体初始化命令
            Function.addCommand("summon item 0 0 0 {" +
                    "Tags:[\"mcfpp_ptr_marker\"]," +
                    "UUID:${ClassPointer.tempItemEntityUUIDNBT}, " +
                    "Age:-32768, " +
                    "NoGravity: true, " +
                    "Item:{id:\"stone\"}, " +
                    "Invulnerable: true" +
                    "}"
            )

            Function.addComment("class init", CommentLevel.INFO)
            //向load中添加类初始化命令
            for (n in GlobalField.localNamespaces.values){
                n.field.forEachObject { c->
                    if(c is ObjectClass){
                        //单例实体
                        Function.addCommand("summon marker 0 0 0 {" +
                                "Tags:[${c.tag}]," +
                                "UUID:${c.mcuuid.uuidSNBT}}"
                        )
                        c.classPreInit.invoke(LinkedHashMap(), null)
                    }
                }
            }
            //向load中添加类的load函数
            for (n in GlobalField.localNamespaces.values){
                n.field.forEachClass { c ->
                    val qwq = c.field.getFunction("load", ArrayList(), ArrayList())
                    if(qwq is NativeFunction){
                        qwq.invoke(emptyList(), null)
                    }else{
                        Function.addCommand("execute as @e[tag=${c.tag}] at @s run function ${qwq.namespaceID}")
                    }
                }
                n.field.forEachObject { o ->
                    if(o !is ObjectClass) return@forEachObject
                    val qwq = o.field.getFunction("load", ArrayList(), ArrayList())
                    if(qwq is NativeFunction){
                        qwq.invoke(emptyList(), null)
                    }else{
                        Function.addCommand("execute as ${o.mcuuid.uuid} at @s run function ${qwq.namespaceID}")
                    }
                }
            }

            //浮点数临时marker实体
            Function.addCommand("summon marker 0 0 0 {" +
                    "Tags:[\"mcfpp_float_marker\"]," +
                    "UUID:${MCFloat.tempFloatEntityUUIDNBT}}"
            )
        }

        //向tick中添加类的tick函数
        for (n in GlobalField.localNamespaces.values){
            n.field.forEachClass { c -> projectTick.runInFunction {
                val qwq = c.field.getFunction("tick",ArrayList(), ArrayList())
                if(qwq is NativeFunction){
                    qwq.invoke(emptyList(), null)
                }else{
                    Function.addCommand("execute as @e[tag=${c.tag}] at @s run function ${qwq.namespaceID}")
                }
            }  }
            n.field.forEachObject { o -> projectTick.runInFunction {
                if(o !is ObjectClass) return@runInFunction
                val qwq = o.field.getFunction("tick",ArrayList(), ArrayList())
                if(qwq is NativeFunction){
                    qwq.invoke(emptyList(), null)
                }else{
                    Function.addCommand("execute as ${o.mcuuid.uuid} at @s run function ${qwq.namespaceID}")
                }
            } }
        }

        //寻找入口函数
        var hasEntrance = false
        for(field in GlobalField.localNamespaces.values){
            field.field.forEachFunction { f->
                run {
                    if (f.parent.size == 0 && f !is Native) {
                        //找到了入口函数
                        hasEntrance = true
                        f.commands.add(0, Commands.stackIn())
                        f.commands.add(Commands.stackOut())
                        logger.debug("Find entrance function: {} {}", f.tags, f.identifier)
                    }
                }
            }
        }
        if (!hasEntrance && !CompileSettings.isLib) {
            logger.warn("No valid entrance function in Project ${config.rootNamespace}")
            warningCount++
        }
        logger.info("Complete compiling project " + config.root.name + " with [$errorCount] error and [$warningCount] warning")
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    /**
     * 生成库索引
     * 在和工程信息json文件的同一个目录下生成一个.mclib文件
     */
    fun genIndex() {
        compileStage = CompileStage.GEN_INDEX
        LibBinWriter.write(config.targetPath!!.absolutePathString())
        stageProcessor[compileStage.ordinal].forEach { it() }
    }
}

