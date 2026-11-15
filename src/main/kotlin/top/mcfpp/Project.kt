package top.mcfpp

import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ServiceLoader
import kotlin.io.path.*
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import top.mcfpp.analysis.TemplateDeclarations
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.backend.RuntimeInitialization
import top.mcfpp.command.Command
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.Var
import top.mcfpp.io.LibBinReader
import top.mcfpp.io.LibBinWriter
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.LibraryMetadata
import top.mcfpp.lib.SbObject
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.library.compiler.LibraryInputs
import top.mcfpp.library.compiler.ManagedNativeBindings
import top.mcfpp.library.compiler.PortableAnnotationCodec
import top.mcfpp.library.compiler.PortableAnnotationCodecs
import top.mcfpp.library.model.LibraryCompatibility
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.mni.expand.ExpansionBudget
import top.mcfpp.mni.expand.TemplateProcessorRunner
import top.mcfpp.mni.expand.invalidateExpansionSessions
import top.mcfpp.model.Namespace
import top.mcfpp.model.Native
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.pairCompanion
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.optimization.EntrypointFrames
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.Module

/**
 * 当前编译单元的状态与阶段入口。CLI 和嵌入式 API 通过这里调度各编译阶段。
 *
 * 库输入处理、运行时初始化命令和入口栈帧处理分别委托给对应模块。
 * 可变状态仍由工程统一持有，使初始化、MNI 保存点和重复编译使用同一份状态。
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

    fun enableModulePackage(packageName: String, moduleName: String? = null) {
        for (module in modules) {
            if (moduleName != null && module.id != moduleName) continue
            for (packageKey in module.packages.keys) {
                if (packageKey.id == packageName) module.packages[packageKey] = true
            }
        }
    }

    inline fun <T> withCompilationContext(ctx: ParserRuleContext, block: () -> T): T {
        Project.ctx.addFirst(ctx)
        try {
            return block()
        } catch (e: Exception) {
            LogProcessor.error("Fatal error. Caused by: ", e)
            throw e
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

    private val templateDeclarations = TemplateDeclarations(object : TemplateDeclarations.Context {
        override val readingLibraries get() = compileStage == CompileStage.READ_LIB
        override val runProcessors get() = compileStage == CompileStage.RUN_ANNOTATION || compileStage == CompileStage.COMPILE
        override val errorCount get() = Project.errorCount
        override fun preparedBuiltinSignatures() = libraryImportSession?.preparedBuiltinSignatures().orEmpty()
    })

    internal val templateDeclarationsReady get() = templateDeclarations.templateDeclarationsReady
    internal val shouldDeferTypeCompositions get() = templateDeclarations.shouldDeferTypeCompositions

    internal fun invalidateTemplateInheritanceHeaders() = templateDeclarations.invalidateTemplateInheritanceHeaders()

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

    /** One batch owns every standard, local and installed library in this compilation. */
    var libraryImportSession: LibraryImportSession? = null
    var libraryNativeBindings: ManagedNativeBindings? = null
    var librarySourceProviders: Map<String, PackageKey?> = emptyMap()
    var loadedLibraryCount = 0
        private set
    private val libraryClassLoaders = arrayListOf<URLClassLoader>()

    /** Close compiler-owned inputs; the host class loader and Mcfpm cache remain caller-owned. */
    fun closeLibraryImports() {
        val failures = arrayListOf<Throwable>()
        libraryImportSession?.let { runCatching { it.close() }.exceptionOrNull()?.let(failures::add) }
        libraryImportSession = null
        libraryNativeBindings = null
        librarySourceProviders = emptyMap()
        libraryClassLoaders.asReversed().forEach { loader -> runCatching { loader.close() }.exceptionOrNull()?.let(failures::add) }
        libraryClassLoaders.clear()
        modules.clear()
        loadedLibraryCount = 0
        classLoader = Thread.currentThread().contextClassLoader
        if (failures.isNotEmpty()) throw IllegalStateException("Unable to close compiler library inputs", failures.first()).also {
            failures.drop(1).forEach(it::addSuppressed)
        }
    }

    /** Release the completed API compilation without running initialization hooks again. */
    fun closeCompilation() {
        try {
            closeLibraryImports()
        } finally {
            ctx.clear(); trees.clear(); tokens.clear(); files.clear()
            constants.clear(); macroFunction.clear()
            templateDeclarations.reset()
            preparedObjectInitializers.clear(); objectInitializerBodies.clear()
            objectInitializerDependencies.clear(); activeObjectInitializers.clear()
            projectTickSlot = null; projectLoadSlot = null; projectInitSlot = null
            macroExpansionBudgetSlot = null
            MCFPPFile.currFile = null
            Function.currFunction = Function.nullFunction; Function.forcedField = null
            DataTemplate.currTemplate = null
            GlobalScope.localNamespaces.clear(); GlobalScope.libNamespaces.clear()
            GlobalScope.stdNamespaces.clear(); GlobalScope.importedLibNamespaces.clear()
            GlobalScope.functionTags.clear(); GlobalScope.scoreboards.clear()
            FunctionTag.TICK.functions.clear(); FunctionTag.LOAD.functions.clear()
            LibraryMetadata.reset()
            top.mcfpp.backend.ReferenceAccessHelpers.reset()
            invalidateExpansionSessions(); TemplateProcessorRunner.reset()
        }
    }

    /** Called again after body compilation so regenerated functions suppress their saved resources. */
    fun refreshLibraryResources() {
        val session = libraryImportSession ?: return
        val active = session.resources()
        modules.forEach { it.resourceSnapshot = active }
    }

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
        top.mcfpp.io.FunctionInventory.clearCallGraph()
        closeLibraryImports()
        top.mcfpp.io.PackEntrypoints.reset()
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
        top.mcfpp.backend.ReferenceAccessHelpers.reset()
        templateDeclarations.reset()
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

    internal fun readConfig(options: CompilerOptions): ProjectConfig = readProjectConfig(options)

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
    fun readProject(installedLibraries: List<LibBinReader.Payload> = emptyList()) =
        readProjectInputs(installedLibraries, readSources = true)

    /** A prepared kernel loads the library without manufacturing a placeholder source file. */
    internal fun prepareLibraryKernel() = readProjectInputs(emptyList(), readSources = false)

    private fun readProjectInputs(installedLibraries: List<LibBinReader.Payload>, readSources: Boolean) {
        check(libraryImportSession == null) {
            "Library inputs are already open; call Project.init() or Project.closeCompilation() before reading another project"
        }
        compileStage = CompileStage.READ_LIB
        //生成默认命名空间
        GlobalScope.localNamespaces[config.rootNamespace] = Namespace(config.rootNamespace)

        //生成tick/load/init函数
        // Keep the generated game entrypoint distinct from the public tick(callback) API.
        projectTick = Function("__tick", config.rootNamespace, null)
        projectLoad = Function("load", config.rootNamespace, null)
        projectInit = Function("init", config.rootNamespace, null)
        GlobalScope.localNamespaces[config.rootNamespace]!!.scope.addFunction(projectTick, true)
        GlobalScope.localNamespaces[config.rootNamespace]!!.scope.addFunction(projectLoad, true)
        GlobalScope.localNamespaces[config.rootNamespace]!!.scope.addFunction(projectInit, true)
        FunctionTag.TICK.functions.add(projectTick)
        FunctionTag.LOAD.functions.add(projectLoad)
        FunctionTag.LOAD.functions.add(projectInit)

        try {
            for (jar in config.jars) {
                val path = Paths.get(jar)
                require(Files.isRegularFile(path)) { "Cannot find jar at: $jar" }
                URLClassLoader(arrayOf(path.toUri().toURL()), classLoader).also {
                    libraryClassLoaders += it
                    classLoader = it
                }
            }
            val managed = libraryNativeBindings ?: object : ManagedNativeBindings {
                override fun loader(provider: PackageKey?): ClassLoader {
                    require(provider == null) { "No trusted plugin registered for $provider" }
                    return classLoader
                }
                override fun provider(owner: java.lang.Class<*>): PackageKey? = null
            }
            val codecs = PortableAnnotationCodecs.builtins()
            try {
                ServiceLoader.load(PortableAnnotationCodec::class.java, classLoader).forEach { provider ->
                    codecs.register(provider, managed.provider(provider.annotationClass), managed)
                }
                val session = LibraryImportSession(LibraryCompatibility(minecraftVersion = config.version))
                libraryImportSession = session
                session.configureNative(managed, codecs, librarySourceProviders)
                if (!readSources) session.enablePreparedBuiltin()
            } catch (failure: Throwable) {
                codecs.close()
                throw failure
            }
            val sources = LibraryInputs.collect(
                config, CompileSettings.ignoreStdLib, Project::class.java.classLoader, installedLibraries,
            )
            modules.addAll(LibraryInputs.bind(requireNotNull(libraryImportSession), sources))
            loadedLibraryCount = sources.size
            refreshLibraryResources()
        } catch (failure: Throwable) {
            runCatching { closeLibraryImports() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
        LibraryInputs.restoreDeclarationEnvironments(GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
        //函数参数解析
        GlobalScope.importedLibNamespaces.clear()
        //读取所有文件
        if (readSources && config.sourcePath != null) {
            MCFPPFile.findFiles(config.sourcePath!!.absolutePathString()).forEach {
                files.add(MCFPPFile(it.toFile()))
            }
        }
        if(readSources && files.isEmpty() && config.sourcePath != null){
            LogProcessor.error("Cannot find any mcfpp file in path: ${config.sourcePath}")
        }
        stageProcessor[compileStage.ordinal].forEach { it() }
    }

    /** Install only the request's inputs after the kernel has restored its library baseline. */
    internal fun beginKernelCompilation(configuration: ProjectConfig, source: String) {
        check(libraryImportSession != null) { "The prepared library is unavailable" }
        invalidateExpansionSessions()
        TemplateProcessorRunner.reset()
        top.mcfpp.io.PackEntrypoints.reset()
        config = configuration
        macroExpansionBudgetSlot = configuration.newExpansionBudget()
        currNamespace = configuration.rootNamespace
        files.clear()
        val directory = requireNotNull(configuration.sourcePath)
        Files.createDirectories(directory)
        val path = directory.resolve("main.mcfpp")
        Files.writeString(path, source)
        files.add(MCFPPFile(path.toFile()))
    }

    internal fun resolveImportedTemplateParents() = templateDeclarations.resolveImportedTemplateParents()

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
                pairCompanion(template, namespace.scope.getObject(template.identifier) as? DataTemplate)
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

    // 保留工程入口，所有声明算法与状态统一交给同一协调器，供旧调用方和 MNI 保存点使用。
    fun completeTemplateDeclarations() = templateDeclarations.completeTemplateDeclarations()

    internal fun <T> collectingTemplateSignatures(action: () -> T): T =
        templateDeclarations.collectingTemplateSignatures(action)

    internal fun registerTemplateContract(template: DataTemplate) = templateDeclarations.registerTemplateContract(template)

    internal fun completePreparedTemplateDeclarations(collect: () -> List<DataTemplate>) =
        templateDeclarations.completePreparedTemplateDeclarations(collect)

    internal fun isTemplateCompletionQueued(template: DataTemplate): Boolean =
        templateDeclarations.isTemplateCompletionQueued(template)

    fun registerAnonymousTemplate(template: DataTemplate) = templateDeclarations.registerAnonymousTemplate(template)

    internal fun findRegisteredAnonymousTemplate(identity: TypeId): DataTemplate? =
        templateDeclarations.findRegisteredAnonymousTemplate(identity)

    internal fun prepareTemplateInheritanceHeaders() = templateDeclarations.prepareTemplateInheritanceHeaders()

    internal fun hasValidTemplateInheritance(template: DataTemplate): Boolean =
        templateDeclarations.hasValidTemplateInheritance(template)

    internal fun invalidateTemplateParentBinding(template: DataTemplate) =
        templateDeclarations.invalidateTemplateParentBinding(template)

    internal fun resolveTemplateInheritance(template: DataTemplate) = templateDeclarations.resolveTemplateInheritance(template)

    internal fun completeTemplateInheritance(roots: List<DataTemplate>, previous: List<DataTemplate> = emptyList()): ArrayList<DataTemplate> =
        templateDeclarations.completeTemplateInheritance(roots, previous)

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
        val declarations = templateDeclarations.journalState()
        val tick = projectTickSlot
        val load = projectLoadSlot
        val init = projectInitSlot
        val expansionBudget = macroExpansionBudgetSlot
        val savedLogger = logger
        return JournalState(declarations.anonymous, declarations.pending, declarations.signatureDepth,
            declarations.completing, declarations.headers, declarations.invalidHeaders, declarations.boundParents,
            declarations.ready, preparedObjectInitializers,
            objectInitializerBodies, objectInitializerDependencies, activeObjectInitializers, files,
            listOfNotNull(tick, load, init), expansionBudget) {
            declarations.restore()
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
        return actual.values.filter { it.namespace in GlobalScope.localNamespaces || preparedObjectInitializers.contains(it) }
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
        libraryImportSession?.prepareInitializers()
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
        top.mcfpp.io.PackEntrypoints.prepare()
        stageProcessor[compileStage.ordinal].forEach { it() }
        top.mcfpp.io.IrTextDump.snapshot("01-compiled.ir", "compiled")
    }

    /**
     * 整理并优化工程
     */
    @InsertCommand
    fun optimization() {
        compileStage = CompileStage.OPTIMIZATION
        projectLoad.runInFunction {
            for (obj in orderedObjectInitializers()) {
                prepareObjectInitializer(obj)
                objectInitializerBodies[obj]?.invoke(emptyList(), null)
            }
        }
        top.mcfpp.optimization.OptimizationPipeline.finish(cleanup = false)
        val runtimeCommands = top.mcfpp.io.DatapackLinker.runtimeCommands()
        val initializers = projectLoad.commands.toList()
        projectLoad.commands.clear()
        LogProcessor.rawDebug(logger, "Optimizing...")
        LogProcessor.rawDebug(logger, "Adding scoreboards declare in mcfpp:load function")

        // 运行时依赖必须先于之前收集的对象初始化函数体执行。
        projectLoad.runInFunction {
            RuntimeInitialization.emit(config, GlobalScope.scoreboards.values, runtimeCommands)
            projectLoad.commands.addAll(initializers)
        }

        //寻找入口函数
        var hasEntrance = false
        for (namespace in GlobalScope.localNamespaces.values) {
            namespace.scope.forEachFunction { function ->
                if (function.parent.isEmpty() && function !is Native) {
                    hasEntrance = true
                    EntrypointFrames.prepare(function, logger)
                }
            }
        }
        if (!hasEntrance && !CompileSettings.isLib) {
            LogProcessor.rawWarn(logger, "No valid entrance function in Project ${config.rootNamespace}")
            LogProcessor.recordProjectWarning()
        }
        LogProcessor.rawInfo(logger, "Complete compiling project " + config.root.name + " with [$errorCount] error and [$warningCount] warning")
        refreshLibraryResources()
        stageProcessor[compileStage.ordinal].forEach { it() }
        top.mcfpp.optimization.OptimizationPipeline.finish()
        top.mcfpp.io.IrTextDump.snapshot("02-optimized.ir", "optimized")
    }

    /**
     * 生成库索引
     * 在和工程信息json文件的同一个目录下生成一个.mclib文件
     */
    fun genIndex() {
        LogProcessor.debug("Writing lib file to ${config.targetPath!!.absolutePathString()}")
        compileStage = CompileStage.GEN_INDEX
        libraryImportSession?.prepareInitializers()
        LibBinWriter.write(config.targetPath!!.absolutePathString())
        stageProcessor[compileStage.ordinal].forEach { it() }
    }
}
