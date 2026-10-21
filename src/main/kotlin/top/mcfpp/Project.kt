package top.mcfpp

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.google.gson.JsonObject
import com.ibm.icu.impl.data.ResourceReader
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.tools.zip.ZipFile
import org.stringtemplate.v4.compiler.STParser
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
import top.mcfpp.mni.expand.ExpansionBudget
import top.mcfpp.mni.expand.TemplateProcessorRunner
import top.mcfpp.mni.expand.invalidateExpansionSessions
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID
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

    /**
     * The single macro-expansion budget of the current compilation unit.
     *
     * Every source macro call in one [init] cycle shares this object, including nested child
     * sessions. [init] replaces it with a fresh budget, so repeated compilations never reuse
     * counts. Project savepoints retain the owning object itself; allocations consumed by a
     * failed expansion are deliberately not refunded.
     */
    private var macroExpansionBudgetSlot: ExpansionBudget? = null

    /** Create the compilation-unit budget lazily only for callers that did not run [init]. */
    internal fun macroExpansionBudget(): ExpansionBudget =
        macroExpansionBudgetSlot ?: config.newExpansionBudget().also { macroExpansionBudgetSlot = it }

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

    // Nullable backing slots let a compiler savepoint restore the pre-readProject state too.
    private var projectTickSlot: Function? = null
    private var projectLoadSlot: Function? = null
    private var projectInitSlot: Function? = null
    var projectTick: Function
        get() = projectTickSlot ?: throw UninitializedPropertyAccessException("lateinit property projectTick has not been initialized")
        set(value) { projectTickSlot = value }
    var projectLoad: Function
        get() = projectLoadSlot ?: throw UninitializedPropertyAccessException("lateinit property projectLoad has not been initialized")
        set(value) { projectLoadSlot = value }
    var projectInit: Function
        get() = projectInitSlot ?: throw UninitializedPropertyAccessException("lateinit property projectInit has not been initialized")
        set(value) { projectInitSlot = value }

    /**
     * 常量池
     */
    val constants : HashMap<Any, Var<*>> = HashMap()

    /**
     * 宏命令
     */
    val macroFunction : LinkedHashMap<String, String> = LinkedHashMap()

    private val anonymousTemplates = arrayListOf<DataTemplate>()
    private val pendingTemplateContracts = arrayListOf<DataTemplate>()
    private var templateSignatureCollectionDepth = 0
    private var completingTemplateContracts = false
    private var inheritanceHeaderSnapshot = emptySet<TypeId>()
    private val invalidInheritanceHeaders = hashSetOf<TypeId>()
    private val boundTemplateParents = hashSetOf<TypeId>()
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
        pendingTemplateContracts.clear()
        templateSignatureCollectionDepth = 0
        completingTemplateContracts = false
        inheritanceHeaderSnapshot = emptySet()
        invalidInheritanceHeaders.clear()
        boundTemplateParents.clear()
        templateDeclarationsReady = false
        modules.clear()
        classLoader = Thread.currentThread().contextClassLoader
        files.clear()
        // Invalidate expansions from the previous initialization directly. A transactional
        // ProjectStateJournal may restore stageProcessor's callback lists, so relying on one
        // lazily installed INIT callback would allow stale sessions to survive a later init.
        invalidateExpansionSessions()
        // Processor demand and metadata policy belong to one compilation unit too.
        TemplateProcessorRunner.reset()
        // A compilation unit owns exactly one budget. Reinitialization starts a new unit even
        // when Project.config keeps the same limits, so old node/depth counts cannot leak in.
        macroExpansionBudgetSlot = config.newExpansionBudget()
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    /** Macro precedence: JSON, config args, then this invocation's CLI overrides.
     * Legacy flags retain CLI-before-config-args ordering in main/parseCompilerArgs.
     */
    internal fun readConfig(options: CompilerOptions): ProjectConfig{
        val config = ProjectConfig()
        //工程信息读取
        try {
            val qwq = options.file
            //读取json
            LogProcessor.rawDebug(logger, "Reading project from file \"${qwq.absolutePath}\"")
            config.name = qwq.name.substring(0, qwq.name.lastIndexOf('.'))
            config.root = qwq.parentFile.toPath()
            val json = qwq.readText()
            // 解析json
            val jsonObject: JSONObject = JSONObject.parse(json) as JSONObject

            // 根目录
            (jsonObject.remove("root") as? String)?.let { config.root = Path(it) }

            // 源代码根目录
            (jsonObject.remove("sourcePath") as? String)?.let { config.sourcePath = Path(it) }

            // 版本
            (jsonObject.remove("version") as? String)?.let { config.version = it }

            // 描述
            (jsonObject.remove("description") as? String)?.let { config.description = it }

            // 默认命名空间
            (jsonObject.remove("namespace") as? String)?.let { config.rootNamespace = it }

            //调用库
            jsonObject.getJSONArray("jars")?.let {
                for (i in it.indices) {
                    it.getString(i)?.let(config.jars::add)
                }
                jsonObject.remove("jars")
            }

            //输出目录
            (jsonObject.remove("targetPath") as? String)?.let { config.targetPath = Path(it) }

            //是否生成数据包
            (jsonObject.remove("noDatapack") as? Boolean)?.let { config.noDatapack = it }

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

            if (jsonObject.containsKey("macroExpansion")) {
                val options = jsonObject.getJSONObject("macroExpansion")
                options.getInteger("maxDepth")?.let {
                    config.macroExpansion = config.macroExpansion.copy(maxDepth = it)
                }
                options.getInteger("maxNodes")?.let {
                    config.macroExpansion = config.macroExpansion.copy(maxNodes = it)
                }
                options.getBoolean("dump")?.let {
                    config.macroExpansion = config.macroExpansion.copy(dump = it)
                }
                jsonObject.remove("macroExpansion")
            }

            //编译参数
            if(jsonObject.containsKey("args")){
                val compileArgsJson = jsonObject.getJSONArray("args")
                // TODO json里面能不能定义编译器参数呢。。。
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
            LogProcessor.error("Error while reading project from file \"${options.file.absolutePath}\"")
            LogProcessor.captureStackTrace(e)
        }

        // 编译器cli参数中的 marco 配置会覆写项目配置
        config.macroExpansion = MacroExpansionConfig(options.macroMaxDepth, options.macroMaxNodes, options.dumpMacroExpansion)
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
            scope.objects.forEach(::restoreCompound)
        }
    }

    internal fun resolveImportedTemplateParents() {
        for (namespace in GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values) {
            val scope = namespace.scope
            for (template in scope.template.values + scope.genericTemplate.values +
                scope.objects.filterIsInstance<DataTemplate>()) {
                if (template.hasRestoredParentTypes || template !is GenericDataTemplate && template.parentID.isNotEmpty()) {
                    resolveTemplateInheritance(template)
                }
            }
        }
    }

    /**
     * 编制类型索引
     */
    fun indexType(){
        compileStage = CompileStage.INDEX_TYPE
        LogProcessor.rawDebug(logger, "Generate Type Index...")
        resolveImportedTemplateParents()
        //解析文件
        for (file in files) {
            try {
                file.indexType()
            } catch (e: Exception) {
                LogProcessor.rawError(logger, "Error while generate type index in file \"$file\"")
                LogProcessor.recordProjectError()
                LogProcessor.captureStackTrace(e)
            }
            GlobalScope.importedLibNamespaces.clear()
        }
        pairTemplateCompanions()
        //运行命令
        for (file in files) {
            try {
                file.runCommand()
            } catch (e: Exception) {
                LogProcessor.rawError(logger, "Error while generate run command in file \"$file\"")
                LogProcessor.recordProjectError()
                LogProcessor.captureStackTrace(e)
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
        LogProcessor.rawDebug(logger, "Generate Function Index...")
        //解析文件
        for (file in files) {
            try {
                file.resolveField()
            } catch (e: IOException) {
                LogProcessor.rawError(logger, "Error while generate function index in file \"$file\"")
                LogProcessor.recordProjectError()
                LogProcessor.captureStackTrace(e)
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
        LogProcessor.rawDebug(logger, "Run Annotation...")
        //解析文件
        for (file in files) {
            try {
                file.runAnnotation()
            } catch (e: IOException) {
                LogProcessor.rawError(logger, "Error while run annotation in file \"$file\"")
                LogProcessor.recordProjectError()
                LogProcessor.captureStackTrace(e)
            }
            GlobalScope.importedLibNamespaces.clear()
        }
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    fun completeTemplateDeclarations() {
        if (compileStage == CompileStage.READ_LIB) return
        val namespaces = GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
        val declarations = namespaces.flatMap { namespace ->
            val scope = namespace.scope
            scope.template.values + scope.genericTemplate.values +
                scope.objects.filterIsInstance<DataTemplate>()
        }
        val functions = namespaces.flatMap { it.scope.functions.values.flatten() } +
            declarations.flatMap { it.constructors + it.scope.functions.values.flatten() }
        // Revoke old evidence at the start of this explicit declaration pass, including
        // instances already materialized while loading a library or resolving a signature.
        (declarations.filterNot { it is GenericDataTemplate } +
            declarations.filterIsInstance<GenericDataTemplate>().flatMap { it.materializedInstances() } +
            anonymousTemplates).forEach(::requestTemplateCompletion)
        prepareTemplateInheritanceHeaders()
        declarations.forEach(::resolveTemplateInheritance)
        declarations.filterIsInstance<GenericDataTemplate>().forEach { declaration ->
            templateCompletionStage(declaration) {
                val file = declaration.restoreDeclarationEnvironment()
                if (file == null) declaration.prepareHeader()
                else file.withDeclarationContext { declaration.prepareHeader() }
            }
        }
        functions.forEach { function ->
            templateCompletionStage(function.owner as? DataTemplate) {
                function.normalParams.forEach { it.type.tryResolve() }
                function.returnType.tryResolve()
            }
        }
        val templates = GlobalScope.localNamespaces.values.flatMap { it.scope.template.values + it.scope.objects.filterIsInstance<DataTemplate>() }.filterNot { it is GenericDataTemplate } +
            (GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                .flatMap { it.scope.template.values + it.scope.objects.filterIsInstance<DataTemplate>() }
                .filter { it !is GenericDataTemplate && (it.parentID.isNotEmpty() || it.hasRestoredParentTypes) } +
            declarations.filterIsInstance<GenericDataTemplate>().flatMap { it.materializedInstances() }
        (templates + anonymousTemplates).forEach { template ->
            pendingTemplateContracts.add(template)
        }
        completePendingTemplateContracts(functions)
        templateDeclarationsReady = true
        // A caller that has already entered the annotation/source compilation stages owns the
        // same barrier: processors run once here, after every declaration signature is complete.
        // Declaration-only checks never execute annotation processors.
        if (compileStage == CompileStage.RUN_ANNOTATION || compileStage == CompileStage.COMPILE)
            TemplateProcessorRunner.run()
    }

    /** Nested anonymous/generic bodies must finish collecting declarations before any contract check. */
    internal fun <T> collectingTemplateSignatures(action: () -> T): T {
        templateSignatureCollectionDepth++
        var collected = false
        try {
            val result = action()
            collected = true
            return result
        } finally {
            templateSignatureCollectionDepth--
            if (collected && templateDeclarationsReady && templateSignatureCollectionDepth == 0)
                completePendingTemplateContracts()
        }
    }

    internal fun registerTemplateContract(template: DataTemplate) {
        requestTemplateCompletion(template)
        enqueueTemplateContract(template)
    }

    /** Explicit preparation owns this declaration-only barrier, including detached canonical roots.
     * Initial projects have not run the global declaration pass yet; queue registration alone is
     * therefore not completion evidence. Keep collection closed until every requested shell exists.
     */
    internal fun completePreparedTemplateDeclarations(collect: () -> List<DataTemplate>) {
        check(compileStage != CompileStage.READ_LIB) { "Declaration completion cannot run while reading a library" }
        check(!completingTemplateContracts && templateSignatureCollectionDepth == 0) {
            "Declaration completion requires the outer declaration barrier"
        }
        val roots: List<DataTemplate>
        templateSignatureCollectionDepth++
        try {
            roots = collect()
            roots.forEach { template ->
                check(template !is GenericDataTemplate) { "Complete a concrete template binding, not its generic prototype" }
                if (!template.hasCompletedDeclaration && pendingTemplateContracts.none { it === template })
                    registerTemplateContract(template)
            }
        } finally { templateSignatureCollectionDepth-- }
        completePendingTemplateContracts()
        check(roots.all { it.hasCompletedDeclaration && !it.hasDeclarationCompletionFailure }) {
            "The declaration barrier did not complete every requested template"
        }
    }

    private fun requestTemplateCompletion(template: DataTemplate) {
        (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { it.originTemplate.revokeCompletedMaterialization(it) }
        template.requestDeclarationCompletion()
    }

    /** Recursive signature resolution may reuse an in-flight canonical model, never its cache entry. */
    internal fun isTemplateCompletionQueued(template: DataTemplate): Boolean =
        completingTemplateContracts || pendingTemplateContracts.any { it === template }

    private fun enqueueTemplateContract(template: DataTemplate) {
        pendingTemplateContracts.add(template)
        if (templateDeclarationsReady && templateSignatureCollectionDepth == 0)
            completePendingTemplateContracts()
    }

    /** One barrier for source declarations and concrete bindings created after the source pass. */
    private fun completePendingTemplateContracts(extraFunctions: List<Function> = emptyList()) {
        if (compileStage == CompileStage.READ_LIB || completingTemplateContracts || templateSignatureCollectionDepth != 0) return
        completingTemplateContracts = true
        val attempts = java.util.IdentityHashMap<DataTemplate, Long>()
        var succeeded = false
        try {
            val completed = arrayListOf<DataTemplate>()
            val completedIds = hashSetOf<TypeId>()
            val functions = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
            val resolved = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
            val refreshed = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
            functions.addAll(extraFunctions)
            while (true) {
                val roots = pendingTemplateContracts.filter { it.inheritanceIdentity !in completedIds }
                    .distinctBy { it.inheritanceIdentity }
                pendingTemplateContracts.clear()
                val additions = completeTemplateInheritance(roots, completed, attempts)
                completed.addAll(additions)
                completedIds.addAll(additions.map { it.inheritanceIdentity })
                additions.forEach { functions.addAll(it.constructors + it.scope.functions.values.flatten()) }
                functions.filter { resolved.add(it) }.forEach { function ->
                    templateCompletionStage(function.owner as? DataTemplate) {
                        function.normalParams.forEach { it.type.tryResolve() }
                        function.returnType.tryResolve()
                    }
                }
                // Resolving a signature or initializer can introduce another anonymous type
                // or concrete generic binding. Complete those before refreshing/checking.
                if (pendingTemplateContracts.any { it.inheritanceIdentity !in completedIds }) continue
                functions.filter { refreshed.add(it) }.forEach { function ->
                    templateCompletionStage(function.owner as? DataTemplate) {
                        function.refreshTemplateSignature()
                        function.validateDefaultDeclarations()
                    }
                }
                if (pendingTemplateContracts.none { it.inheritanceIdentity !in completedIds }) break
            }
            var contractsValid = true
            completed.forEach { template ->
                if (template.hasDeclarationCompletionFailure) {
                    contractsValid = false
                    return@forEach
                }
                (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { instance ->
                    templateCompletionStage(instance) { instance.originTemplate.bindMaterializedOrigins(instance) }
                }
                val file = template.restoreDeclarationEnvironment()
                val valid = if (file == null) template.validateInheritance()
                else file.withDeclarationContext { template.validateInheritance() }
                // Diagnostics are deduplicated by the model. Recompute validity on every attempt.
                if (!valid) contractsValid = false
            }
            // A specialization can already have entered the legacy map before an earlier stage
            // reported an error. An otherwise empty pending queue is not a completion proof.
            // Invalid raw parent headers deliberately never materialize a parent graph. Its
            // otherwise valid empty graph must not erase the header's failed validity evidence.
            val headersValid = attempts.keys.all(::hasValidTemplateInheritance)
            succeeded = headersValid && contractsValid && errorCount == 0 &&
                attempts.all { (template, generation) -> template.canCompleteDeclaration(generation) }
        } finally {
            attempts.forEach { (template, generation) ->
                template.finishDeclarationCompletion(generation, succeeded)
            }
            attempts.keys.filterIsInstance<top.mcfpp.model.compound.CompiledGenericDataTemplate>().forEach { instance ->
                instance.originTemplate.finishMaterialization(instance)
            }
            completingTemplateContracts = false
        }
    }

    /** A failed barrier withholds every proof, but only the failing stage's owner needs rollback. */
    private fun templateCompletionStage(owner: DataTemplate?, action: () -> Unit) {
        // A failed stage can leave its pending annotations and adapters partially consumed.
        // Only restoring the owner's checkpoint authorizes another mutating attempt.
        if (owner?.hasDeclarationCompletionFailure == true) return
        if (owner == null) action() else owner.declarationCompletionStage(action)
    }

    fun registerAnonymousTemplate(template: DataTemplate) {
        requestTemplateCompletion(template)
        anonymousTemplates.add(template)
        resolveTemplateInheritance(template)
        enqueueTemplateContract(template)
    }

    /** Returns only an anonymous template already registered with this project. */
    internal fun findRegisteredAnonymousTemplate(identity: TypeId): DataTemplate? =
        anonymousTemplates.firstOrNull { it.inheritanceIdentity == identity }

    private fun templateDeclarations(): List<DataTemplate> =
        (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values).flatMap {
            it.scope.template.values + it.scope.genericTemplate.values +
                it.scope.objects.filterIsInstance<DataTemplate>()
        }.distinctBy { it.inheritanceIdentity }

    /** Check declaration heads before resolving arguments, which may instantiate another generic head. */
    internal fun prepareTemplateInheritanceHeaders() {
        val declarations = templateDeclarations() + anonymousTemplates
        val snapshot = declarations.mapTo(hashSetOf()) { it.inheritanceIdentity }
        if (snapshot == inheritanceHeaderSnapshot) return
        inheritanceHeaderSnapshot = snapshot
        val edges = declarations.associate { declaration ->
            val file = declaration.restoreDeclarationEnvironment()
            val resolve = {
                declaration.parentID.mapNotNull { name ->
                    val (namespace, identifier) = name.substringBefore('<').splitNamespaceID()
                    GlobalScope.getData(namespace, identifier) ?: file?.unsolvedImports?.entries?.firstNotNullOfOrNull { (importedNamespace, importedName) ->
                        if ((namespace == null || namespace == importedNamespace) && (importedName == "*" || importedName == identifier))
                            GlobalScope.getUnsolvedImportNamespace(importedNamespace)?.scope?.getDeclaredType(identifier) as? DataTemplate
                        else null
                    }
                }
            }
            val parents = if (file == null) resolve() else file.withDeclarationContext(resolve)
            declaration.inheritanceIdentity to parents
        }
        val states = hashMapOf<TypeId, Int>()
        for (root in declarations) {
            if (states[root.inheritanceIdentity] == 2) continue
            val stack = arrayListOf(root to edges[root.inheritanceIdentity].orEmpty().iterator())
            states[root.inheritanceIdentity] = 1
            while (stack.isNotEmpty()) {
                val (current, parents) = stack.last()
                if (!parents.hasNext()) {
                    states[current.inheritanceIdentity] = 2
                    stack.removeAt(stack.lastIndex)
                    continue
                }
                val parent = parents.next()
                when (states[parent.inheritanceIdentity]) {
                    1 -> {
                        val start = stack.indexOfFirst { it.first.inheritanceIdentity == parent.inheritanceIdentity }
                        val cycle = stack.drop(start).map { it.first } + parent
                        if (cycle.any { it.inheritanceIdentity !in invalidInheritanceHeaders }) {
                            LogProcessor.error("Cyclic inheritance: " + cycle.joinToString(" -> ") { it.namespaceID })
                        }
                        invalidInheritanceHeaders.addAll(cycle.map { it.inheritanceIdentity })
                    }
                    2 -> Unit
                    else -> {
                        states[parent.inheritanceIdentity] = 1
                        stack.add(parent to edges[parent.inheritanceIdentity].orEmpty().iterator())
                    }
                }
            }
        }
    }

    internal fun hasValidTemplateInheritance(template: DataTemplate): Boolean {
        prepareTemplateInheritanceHeaders()
        val declaration = (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.originTemplate ?: template
        return declaration.inheritanceIdentity !in invalidInheritanceHeaders
    }

    internal fun invalidateTemplateParentBinding(template: DataTemplate) {
        boundTemplateParents.remove(template.inheritanceIdentity)
    }

    internal fun resolveTemplateInheritance(template: DataTemplate) {
        // Includes may refer to declarations restored by a later include. Even an empty
        // early graph is not evidence that this declaration's parents have been bound.
        if (compileStage == CompileStage.READ_LIB) return
        // Requeueing and resetting diagnostics do not undo a failed mutating stage. Its
        // private checkpoint must restore the owner before any parent resolution is retried.
        if (template.hasDeclarationCompletionFailure) return
        if (!hasValidTemplateInheritance(template)) {
            requestTemplateCompletion(template)
            return
        }
        if (template.inheritanceIdentity in boundTemplateParents && !template.hasPendingParentTypes) return
        // Restoring a specialized parent may queue a freshly materialized declaration. Its
        // barrier must wait until this complete parent list has either committed or failed.
        collectingTemplateSignatures {
            (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { it.originTemplate.revokeCompletedMaterialization(it) }
            val errorsBefore = errorCount
            val resolved = template.declarationCompletionStage { resolveTemplateParents(template) }
            if (resolved && errorCount == errorsBefore) boundTemplateParents.add(template.inheritanceIdentity)
        }
    }

    private fun resolveTemplateParents(template: DataTemplate): Boolean {
        val errorsBefore = errorCount
        val restoredParents = template.hasPendingParentTypes
        if (restoredParents && !template.resolveDeclaredParents()) return false
        if (template is GenericDataTemplate) {
            val file = template.restoreDeclarationEnvironment()
            if (file == null) template.prepareHeader() else file.withDeclarationContext { template.prepareHeader() }
        } else if (!restoredParents && template.parentID.isNotEmpty() && !template.resolveDeclaredParents()) return false
        return errorCount == errorsBefore
    }

    /** Complete parent fields before children without depending on source order or recursion depth. */
    internal fun completeTemplateInheritance(roots: List<DataTemplate>, previous: List<DataTemplate> = emptyList()): ArrayList<DataTemplate> {
        return completeTemplateInheritance(roots, previous, null)
    }

    private fun completeTemplateInheritance(roots: List<DataTemplate>, previous: List<DataTemplate>,
                                            attempts: java.util.IdentityHashMap<DataTemplate, Long>?): ArrayList<DataTemplate> {
        val completed = arrayListOf<DataTemplate>()
        val states = previous.associate { it.inheritanceIdentity to 2 }.toMutableMap()
        for (root in roots) {
            if (root is GenericDataTemplate || states[root.inheritanceIdentity] == 2) continue
            if (attempts != null && !attempts.containsKey(root)) {
                (root as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { it.originTemplate.revokeCompletedMaterialization(it) }
                attempts[root] = root.beginDeclarationCompletion()
            }
            resolveTemplateInheritance(root)
            val stack = arrayListOf(root to root.parent.filterIsInstance<DataTemplate>().iterator())
            states[root.inheritanceIdentity] = 1
            while (stack.isNotEmpty()) {
                val (current, parents) = stack.last()
                if (!parents.hasNext()) {
                    templateCompletionStage(current) {
                        current.flatExtends()
                        MCFPPFieldVisitor().completeTemplateFields(current)
                        current.applyDeclarationAnnotations()
                    }
                    completed.add(current)
                    states[current.inheritanceIdentity] = 2
                    stack.removeAt(stack.lastIndex)
                    continue
                }
                val parent = parents.next()
                if (parent is GenericDataTemplate) continue
                when (states[parent.inheritanceIdentity]) {
                    1 -> {
                        val start = stack.indexOfFirst { it.first.inheritanceIdentity == parent.inheritanceIdentity }
                        templateCompletionStage(current) {
                            LogProcessor.error("Cyclic inheritance: " + (stack.drop(start).map { it.first } + parent).joinToString(" -> ") { it.namespaceID })
                            current.unExtends(parent)
                        }
                    }
                    2 -> Unit
                    else -> {
                        if (attempts != null && !attempts.containsKey(parent)) {
                            (parent as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { it.originTemplate.revokeCompletedMaterialization(it) }
                            attempts[parent] = parent.beginDeclarationCompletion()
                        }
                        resolveTemplateInheritance(parent)
                        states[parent.inheritanceIdentity] = 1
                        stack.add(parent to parent.parent.filterIsInstance<DataTemplate>().iterator())
                    }
                }
            }
        }
        return completed
    }

    /** Known final identity collection: a savepoint can inspect it without host collection callbacks. */
    internal class InitializerSet : AbstractMutableSet<DataTemplate>() {
        private val members = java.util.IdentityHashMap<DataTemplate, Boolean>()
        override val size: Int get() = members.size
        override fun iterator(): MutableIterator<DataTemplate> = members.keys.iterator()
        override fun add(element: DataTemplate): Boolean = members.put(element, true) == null
        override fun contains(element: DataTemplate): Boolean = members.containsKey(element)
        override fun remove(element: DataTemplate): Boolean = members.remove(element) != null
        override fun clear() = members.clear()
    }

    private val preparedObjectInitializers = InitializerSet()
    private val objectInitializerBodies = java.util.IdentityHashMap<DataTemplate, Function>()
    private val objectInitializerDependencies = java.util.IdentityHashMap<DataTemplate, MutableSet<DataTemplate>>()
    private val activeObjectInitializers = arrayListOf<DataTemplate>()

    /** Raw compiler-owned relationships only. Reading this hook never completes a declaration. */
    internal class JournalState internal constructor(
        val anonymous: ArrayList<DataTemplate>,
        val pending: ArrayList<DataTemplate>,
        val signatureDepth: Int,
        val completing: Boolean,
        val headers: Set<TypeId>,
        val invalidHeaders: HashSet<TypeId>,
        val boundParents: HashSet<TypeId>,
        val ready: Boolean,
        val preparedObjects: MutableSet<DataTemplate>,
        val initializerBodies: java.util.IdentityHashMap<DataTemplate, Function>,
        val initializerDependencies: java.util.IdentityHashMap<DataTemplate, MutableSet<DataTemplate>>,
        val activeObjects: ArrayList<DataTemplate>,
        val sourceFiles: ArrayList<MCFPPFile>,
        val entryFunctions: List<Function>,
        /** The compilation-unit budget owner; its consumed counts belong to the budget itself. */
        val expansionBudget: ExpansionBudget?,
        private val restoreSlots: () -> Unit
    ) {
        fun restore() = restoreSlots()
    }

    internal fun journalState(): JournalState {
        val depth = templateSignatureCollectionDepth
        val completing = completingTemplateContracts
        val headers = inheritanceHeaderSnapshot
        val ready = templateDeclarationsReady
        val tick = projectTickSlot
        val load = projectLoadSlot
        val init = projectInitSlot
        val expansionBudget = macroExpansionBudgetSlot
        val savedLogger = logger
        return JournalState(anonymousTemplates, pendingTemplateContracts, depth, completing, headers,
            invalidInheritanceHeaders, boundTemplateParents, ready, preparedObjectInitializers,
            objectInitializerBodies, objectInitializerDependencies, activeObjectInitializers, files,
            listOfNotNull(tick, load, init), expansionBudget) {
            templateSignatureCollectionDepth = depth
            completingTemplateContracts = completing
            inheritanceHeaderSnapshot = headers
            templateDeclarationsReady = ready
            projectTickSlot = tick; projectLoadSlot = load; projectInitSlot = init
            macroExpansionBudgetSlot = expansionBudget
            logger = savedLogger
        }
    }

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
                InitializerSet()
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
        // Every field and inheritance signature in every source file is complete. Processors now
        // run once in original source order; generated signatures enter the ordinary call index
        // before any source or generated body is checked.
        if (templateDeclarationsReady) TemplateProcessorRunner.run()
        prepareObjectInitializers()
        //工程文件编译
        //解析文件
        for (file in files) {
            LogProcessor.debug("Compiling mcfpp code in \"$file\"")
            try {
                file.compile()
            } catch (e: IOException) {
                LogProcessor.rawError(logger, "Error while compiling file \"$file\"")
                LogProcessor.recordProjectError()
                LogProcessor.captureStackTrace(e)
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
        LogProcessor.rawDebug(logger, "Optimizing...")
        LogProcessor.rawDebug(logger, "Adding scoreboards declare in mcfpp:load function")

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
                        LogProcessor.rawDebug(logger, "Find entrance function: {} {}", f.tags, f.identifier)
                    }
                }
            }
        }
        if (!hasEntrance && !CompileSettings.isLib) {
            LogProcessor.rawWarn(logger, "No valid entrance function in Project ${config.rootNamespace}")
            LogProcessor.recordProjectWarning()
        }
        LogProcessor.rawInfo(logger, "Complete compiling project " + config.root.name + " with [$errorCount] error and [$warningCount] warning")
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
