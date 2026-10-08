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
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.CommentLevel
import top.mcfpp.command.FloatProviders
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.Var
import top.mcfpp.io.LibBinReader
import top.mcfpp.io.LibBinWriter
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.LibraryMetadata
import top.mcfpp.lib.SbObject
import top.mcfpp.model.Namespace
import top.mcfpp.model.Native
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.Module
import top.mcfpp.util.ModuleType
import top.mcfpp.util.Utils
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.URLClassLoader
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
            LogProcessor.error("Fatal error. Caused by: ", e)
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

    private val anonymousTemplates = arrayListOf<DataTemplate>()
    internal var templateDeclarationsReady = false
        private set

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
        preparedObjectInitializers.clear()
        objectInitializerBodies.clear()
        objectInitializerDependencies.clear()
        activeObjectInitializers.clear()
        compileStage = CompileStage.INIT
        //全局缓存初始化
        MCFPPFile.currFile = null
        Function.currFunction = Function.nullFunction
        Function.forcedField = null
        DataTemplate.currTemplate = null
        GlobalScope.init()
        LibraryMetadata.reset()
        ctx.clear()
        trees.clear()
        tokens.clear()
        currNamespace = config.rootNamespace
        errorCount = 0
        warningCount = 0
        constants.clear()
        macroFunction.clear()
        anonymousTemplates.clear()
        templateDeclarationsReady = false
        modules.clear()
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
            val qwq = File(path).absoluteFile
            config.name = qwq.name.substring(0, qwq.name.lastIndexOf('.'))
            config.root = qwq.parentFile.toPath()
            val json = reader.readText()
            //解析json
            val jsonObject: JSONObject = JSONObject.parse(json) as JSONObject

            //根目录
            if(jsonObject.containsKey("root")){
                config.root = Path(jsonObject.getString("root"))
                jsonObject.remove("root")
            }

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

            if(jsonObject.containsKey("copyImport")){
                config.copyImport = jsonObject.getBoolean("copyImport")
                jsonObject.remove("copyImport")
            }

            //编译参数
            if(jsonObject.containsKey("args")){
                val compileArgsJson = jsonObject.getJSONArray("args")
                parseArgs(compileArgsJson.toList(String::class.java))
                jsonObject.remove("args")
            }

            //额外数据
            if(jsonObject.containsKey("dataPath")){
                config.dataPath = config.root.resolve(Path(jsonObject.getString("dataPath")))
                jsonObject.remove("dataPath")
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
        if (TargetCapabilities.forVersion(config.version) == null){
            LogProcessor.error("Unsupported version: ${config.version}")
            return false
        }
        if(config.targetPath == null){
            LogProcessor.warn("Set target path default to \"${config.root.pathString}/build/\"")
            config.targetPath = Path(config.root.pathString,"build/")
        }
        if(config.sourcePath == null){
//            LogProcessor.warn("Set source path default to \"${config.root.pathString}\"")
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
        GlobalScope.localNamespaces[config.rootNamespace] = Namespace(config.rootNamespace)

        //生成tick/load/init函数
        projectTick = Function("tick", config.rootNamespace, null)
        projectLoad = Function("load", config.rootNamespace, null)
        projectInit = Function("init", config.rootNamespace, null)
        GlobalScope.localNamespaces[config.rootNamespace]!!.scope.addFunction(projectTick, true)
        GlobalScope.localNamespaces[config.rootNamespace]!!.scope.addFunction(projectLoad, true)
        GlobalScope.localNamespaces[config.rootNamespace]!!.scope.addFunction(projectInit, true)
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
            if(!Path(path).exists()){
                LogProcessor.warn("Cannot find jar at: $path")
                return
            }
            JarFile(path).use { jarFile ->
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
                        modules += Module.fromJson(jsonObject).onEach {
                            it.type = ModuleType.JAR
                            it.resourcePath = Path(path)
                        }
                    }
                }else{
                    LogProcessor.warn("Cannot find module.json at: ${jarFile.name}")
                }
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
                    modules += Module.fromJson(jsonObject).onEach {
                        it.type = ModuleType.DIR
                        it.resourcePath = Path(directory)
                    }
                }
            }else{
                LogProcessor.warn("Cannot find module.json at: $directory")
            }
        }

        fun readFromZip(path: String){
            if(!Path(path).exists()){
                LogProcessor.warn("Cannot find zip at: $path")
                return
            }
            ZipFile(path).use { zipFile ->
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
                        modules += Module.fromJson(jsonObject).onEach {
                            it.type = ModuleType.ZIP
                            it.resourcePath = Path(path)
                        }
                    }
                }else{
                    LogProcessor.warn("Cannot find module.json at: ${zipFile.name}")
                }
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
        restoreDeclarationEnvironments()
        //函数参数解析
        GlobalScope.importedLibNamespaces.clear()
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

    /** Bind declaration imports after the last include, before any body or call graph is analyzed. */
    private fun restoreDeclarationEnvironments() {
        val functions = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        val compounds = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<top.mcfpp.model.compound.CompoundData, Boolean>()
        )
        fun restoreFunction(function: Function) {
            if (functions.add(function)) function.restoreDeclarationEnvironment()
        }
        fun restoreCompound(compound: top.mcfpp.model.compound.CompoundData) {
            if (!compounds.add(compound)) return
            if (compound is DataTemplate) {
                compound.restoreDeclarationEnvironment()
                compound.constructors.forEach(::restoreFunction)
                compound.companionObject?.let(::restoreCompound)
            }
            compound.scope.forEachFunction(::restoreFunction)
        }
        val namespaces = GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
        for (namespace in namespaces) {
            val scope = namespace.scope
            scope.forEachFunction(::restoreFunction)
            scope.template.values.forEach(::restoreCompound)
            scope.genericTemplate.values.forEach(::restoreCompound)
            scope.interfaces.values.forEach(::restoreCompound)
            scope.genericInterfaces.values.forEach(::restoreCompound)
            scope.objects.forEach(::restoreCompound)
        }
    }

    internal fun resolveImportedTemplateParents() {
        for (namespace in GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values) {
            val scope = namespace.scope
            for (template in scope.template.values + scope.interfaces.values + scope.objects.filterIsInstance<DataTemplate>()) {
                if (template !is GenericDataTemplate && template.parentID.isNotEmpty()) {
                    template.resolveDeclaredParents()
                    template.flatExtends()
                }
            }
        }
    }

    /**
     * 编制类型索引
     */
    fun indexType(){
        compileStage = CompileStage.INDEX_TYPE
        logger.debug("Generate Type Index...")
        resolveImportedTemplateParents()
        //解析文件
        for (file in files) {
            try {
                file.indexType()
            } catch (e: Exception) {
                logger.error("Error while generate type index in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalScope.importedLibNamespaces.clear()
        }
        pairTemplateCompanions()
        //运行命令
        for (file in files) {
            try {
                file.runCommand()
            } catch (e: Exception) {
                logger.error("Error while generate run command in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalScope.importedLibNamespaces.clear()
        }
        //解析所有泛型类的泛型参数类型
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    /** Pair declarations after every include and source type header is available. */
    fun pairTemplateCompanions() {
        (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values).forEach { namespace ->
            namespace.scope.template.values.forEach { template ->
                top.mcfpp.model.compound.GenericDeclarationContract.pair(template,
                    namespace.scope.getObject(template.identifier) as? DataTemplate)
            }
        }
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
            GlobalScope.importedLibNamespaces.clear()
        }
        //继承解析
        GlobalScope.localNamespaces.values.flatMap { it.scope.template.values }.forEach {
            if(it.parent.isEmpty()) it.extends(DataTemplate.baseDataTemplate)
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
            GlobalScope.importedLibNamespaces.clear()
        }
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    fun completeTemplateDeclarations() {
        val namespaces = GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
        val declarations = namespaces.flatMap { namespace ->
            val scope = namespace.scope
            scope.template.values + scope.genericTemplate.values + scope.interfaces.values +
                scope.genericInterfaces.values + scope.objects.filterIsInstance<DataTemplate>()
        }
        val functions = namespaces.flatMap { it.scope.functions.values.flatten() } +
            declarations.flatMap { it.constructors + it.scope.functions.values.flatten() }
        declarations.filterIsInstance<GenericDataTemplate>().forEach { declaration ->
            val file = declaration.restoreDeclarationEnvironment()
            if (file == null) declaration.prepareHeader()
            else file.withDeclarationContext { declaration.prepareHeader() }
        }
        functions.forEach { function ->
            function.normalParams.forEach { it.type.tryResolve() }
            function.returnType.tryResolve()
        }
        val templates = GlobalScope.localNamespaces.values.flatMap { it.scope.template.values + it.scope.objects.filterIsInstance<DataTemplate>() }.filterNot { it is GenericDataTemplate } +
            (GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                .flatMap { it.scope.template.values + it.scope.interfaces.values + it.scope.objects.filterIsInstance<DataTemplate>() }
                .filter { it !is GenericDataTemplate && it.parentID.isNotEmpty() } +
            declarations.filterIsInstance<GenericDataTemplate>().flatMap { it.compiledTemplates.values.toList() }
        val completed = hashSetOf<DataTemplate>()
        fun complete(template: DataTemplate) {
            if (!completed.add(template)) return
            template.parent.filterIsInstance<DataTemplate>().filter { it in templates || it in anonymousTemplates }.forEach(::complete)
            template.flatExtends()
            MCFPPFieldVisitor().completeTemplateFields(template)
            template.applyDeclarationAnnotations()
        }
        templates.forEach(::complete)
        var index = 0
        while (index < anonymousTemplates.size) complete(anonymousTemplates[index++])
        (functions + completed.flatMap { it.constructors + it.scope.functions.values.flatten() }).forEach {
            it.normalParams.forEach { param -> param.type.tryResolve() }
            it.returnType.tryResolve()
            it.refreshTemplateSignature()
            it.validateDefaultDeclarations()
        }
        templateDeclarationsReady = true
    }

    fun registerAnonymousTemplate(template: DataTemplate) {
        anonymousTemplates.add(template)
        if (templateDeclarationsReady) {
            template.flatExtends()
            MCFPPFieldVisitor().completeTemplateFields(template)
            template.applyDeclarationAnnotations()
            (template.constructors + template.scope.functions.values.flatten()).forEach { it.refreshTemplateSignature() }
        }
    }

    private val preparedObjectInitializers = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
    private val objectInitializerBodies = java.util.IdentityHashMap<DataTemplate, Function>()
    private val objectInitializerDependencies = java.util.IdentityHashMap<DataTemplate, MutableSet<DataTemplate>>()
    private val activeObjectInitializers = arrayListOf<DataTemplate>()

    private fun actualObjects(): List<DataTemplate> {
        val declarations = linkedMapOf<Pair<String, String>, DataTemplate>()
        for (namespace in GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values) {
            namespace.scope.objects.filterIsInstance<DataTemplate>().forEach { template ->
                declarations.putIfAbsent(template.namespace to template.identifier, template)
            }
        }
        val actual = linkedMapOf<top.mcfpp.type.TypeId, DataTemplate>()
        declarations.values.forEach { template ->
            val owners = if (template is GenericDataTemplate) template.compiledTemplates.values.toList() else listOf(template)
            owners.forEach { actual.putIfAbsent(it.getType().typeId, it) }
        }
        return actual.values.toList()
    }

    internal fun prepareObjectInitializer(template: DataTemplate) {
        if (template !is top.mcfpp.model.compound.ObjectCompoundData || template is GenericDataTemplate) return
        val dependent = activeObjectInitializers.lastOrNull()
        if (dependent != null && dependent !== template) {
            objectInitializerDependencies.getOrPut(dependent) {
                java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
            }.add(template)
            if (activeObjectInitializers.any { it === template }) {
                LogProcessor.error("Cyclic object initialization dependency: '${dependent.identifier}' and '${template.identifier}'")
                return
            }
        }
        if (!preparedObjectInitializers.add(template)) return
        val constructors = template.constructors.filter { it.normalParams.isEmpty() }
        if (constructors.size != 1) {
            LogProcessor.error("Object '${template.identifier}' requires one unambiguous startup constructor")
            return
        }
        val constructor = constructors.single()
        activeObjectInitializers.add(template)
        try {
            val body = if (constructor is Native) constructor else constructor.prepareInitializerBody()
            if (body != null) objectInitializerBodies[template] = body
        } finally { activeObjectInitializers.removeAt(activeObjectInitializers.lastIndex) }
    }

    fun prepareObjectInitializers() = actualObjects().forEach(::prepareObjectInitializer)

    private fun orderedObjectInitializers(): List<DataTemplate> {
        val result = arrayListOf<DataTemplate>()
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
        fun visit(template: DataTemplate) {
            if (!seen.add(template)) return
            prepareObjectInitializer(template)
            objectInitializerDependencies[template].orEmpty().forEach(::visit)
            result.add(template)
        }
        actualObjects().forEach(::visit)
        return result
    }

    fun compile() {
        compileStage = CompileStage.COMPILE
        completeTemplateDeclarations()
        prepareObjectInitializers()
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
            for (scoreboard in GlobalScope.scoreboards.values){
                Function.addCommand("scoreboard objectives add ${scoreboard.name} ${scoreboard.criterion}")
            }

            Function.addComment("math:_init", CommentLevel.INFO)

            //向load函数中添加库初始化命令
            Function.addCommand("execute unless score math mcfpp_init matches 1 run function math:_init")

            //向load函数中添加实体初始化命令
            Function.addCommand("summon item 0 0 0 {" +
                    "Tags:[\"mcfpp_ptr_marker\"]," +
                    "UUID:${config.tempItemEntityUUID.uuidSNBT}, " +
                    "Age:-32768, " +
                    "NoGravity: true, " +
                    "Item:{id:\"stone\"}, " +
                    "Invulnerable: true" +
                    "}"
            )

            Function.addComment("class init", CommentLevel.INFO)

            //浮点数临时marker实体
            if (!FloatProviders.enabled) {
                Function.addCommand("summon marker 0 0 0 {" +
                        "Tags:[\"mcfpp_float_marker\"]," +
                        "UUID:${MCFloat.tempFloatEntityUUIDNBT}}"
                )
            }

            //execute object constructor
            for(obj in orderedObjectInitializers()){
                prepareObjectInitializer(obj)
                objectInitializerBodies[obj]?.invoke(emptyList(), null)
            }
        }

        //寻找入口函数
        var hasEntrance = false
        for(field in GlobalScope.localNamespaces.values){
            field.scope.forEachFunction { f->
                run {
                    if (f.parent.size == 0 && f !is Native) {
                        //找到了入口函数
                        hasEntrance = true
                        if (f.frameExits.isEmpty()) {
                            f.commands.add(Commands.stackOut())
                        } else {
                            f.frameExits.forEach { exit ->
                                exit.function.commands.add(exit.commandIndex, Commands.stackOut())
                            }
                        }
                        f.commands.add(0, Commands.stackIn())
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
        LogProcessor.debug("Writing lib file to ${config.targetPath!!.absolutePathString()}")
        compileStage = CompileStage.GEN_INDEX
        LibBinWriter.write(config.targetPath!!.absolutePathString())
        stageProcessor[compileStage.ordinal].forEach { it() }
    }
}
