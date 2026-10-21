package top.mcfpp.io

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.getString
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.antlr.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.scope.FileScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.pathToNamespace
import top.mcfpp.util.StringHelper.toSnakeCase
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * 表示一个MCFPP文件
 */
class MCFPPFile : File {

    val field: FileScope = FileScope()

    val unsolvedImports = hashMapOf<String, String>()

    /**
     * 此文件对应的命名空间，默认为文件的父目录和源代码目录的相对路径
     */
    var namespace: Namespace

    val topFunction: Function = Function(this.name.toSnakeCase() + "__top__", context = null).apply {
        tags.add(FunctionTag.LOAD)
    }

    var syntaxError = false

    constructor(path: String) : super(path) {
        val relativePath = Project.config.sourcePath!!.toAbsolutePath()
            .relativize(toPath().toAbsolutePath().parent).toString()
        val namespaceName = if (relativePath.isEmpty()) {
            Project.config.rootNamespace
        } else {
            Project.config.rootNamespace + "." + relativePath.pathToNamespace().toSnakeCase()
        }
        namespace = GlobalScope.getOrCreateNamespace(namespaceName)
    }

    constructor(file: File) : this(file.absolutePath)

    /** A loaded declaration has a namespace and imports, but no source file to reopen. */
    internal constructor(namespace: Namespace): super(".") {
        this.namespace = namespace
        field.namespaceField = namespace.scope
    }

    internal fun <T> withDeclarationContext(action: () -> T): T {
        val callerFile = currFile
        val callerNamespace = Project.currNamespace
        try {
            currFile = this
            Project.currNamespace = namespace.identifier
            return action()
        } finally {
            currFile = callerFile
            Project.currNamespace = callerNamespace
        }
    }

    internal constructor(): super("."){
        val str = Project.config.rootNamespace + ".test"
        namespace = GlobalScope.getOrCreateNamespace(str)
    }

    fun token(): CommonTokenStream = Project.tokens.getOrPut(this) {
        val source = try {
            VersionPreprocessor.process(readText(Charsets.UTF_8), Project.config.version)
        } catch (e: VersionPreprocessor.Error) {
            syntaxError = true
            LogProcessor.error("Preprocessor error in $absolutePath:${e.line}: ${e.message}")
            ""
        }
        CommonTokenStream(mcfppLexer(CharStreams.fromString(source, absolutePath)))
    }

    fun tree(): ParseTree = Project.trees.getOrPut(this) {
        val parser = mcfppParser(token())
        parser.removeErrorListeners()
        parser.addErrorListener(MCFPPErrorListener())
        parser.compilationUnit()
    }

    /**
     * 编制类型索引
     */
    fun indexType(){
        currFile = this
        Project.currNamespace = namespace.identifier
        val parsedTree = tree()
        if (syntaxError) {
            Project.currNamespace = Project.config.rootNamespace
            currFile = null
            return
        }
        MCFPPTypeVisitor().visit(parsedTree)
        field.namespaceField = namespace.scope
        Project.currNamespace = Project.config.rootNamespace
        currFile = null
    }

    fun runCommand() = withDeclarationContext {
        if (nameWithoutExtension.isNotEmpty()) return@withDeclarationContext
        useLines { lines ->
            for (line in lines) {
                if (!line.startsWith("//>")) return@useLines
                val command = line.substring(3).trim()
                val parse = dispatcher.parse(command, this)
                try {
                    dispatcher.execute(parse)
                } catch (e: Exception) {
                    LogProcessor.error("Failed to execute command: $command", e)
                }
            }
        }
    }

    /**
     * 编制函数索引
     */
    fun resolveField() {
        if(syntaxError) return
        currFile = this
        Project.currNamespace = namespace.identifier
        resolveImports()
        //检查索引
        field.checkIndex()
        //编译
        MCFPPFieldVisitor().visit(tree())
        Project.currNamespace = Project.config.rootNamespace
        currFile = null
    }

    internal fun resolveImports() {
        for (entry in unsolvedImports) {
            val importedNamespace = GlobalScope.getUnsolvedImportNamespace(entry.key)
            if (importedNamespace == null) {
                LogProcessor.error("Namespace '$entry' not found")
                continue
            }
            if (entry.value == "*") {
                field.importedNamespaceField[importedNamespace.identifier] = importedNamespace.scope
                continue
            }
            val declaredType = importedNamespace.scope.getDeclaredType(entry.value)
            if (declaredType == null) {
                LogProcessor.error("Declared type '${entry.key}.${entry.value}' not found")
                continue
            }
            val importScope = field.importField.getOrPut(declaredType.namespace) { NamespaceScope(declaredType.namespace) }
            val added = importScope.addDeclaredType(declaredType)
            if (declaredType is DataTemplate) {
                declaredType.companionObject?.let {
                    importScope.addObject(it.identifier, it)
                }
            }
            if (!added) {
                LogProcessor.error("Already have import '${declaredType.namespaceID}")
            }
        }
    }

    fun runAnnotation(){
        if(syntaxError) return
        currFile = this
        Project.currNamespace = namespace.identifier
        MCFPPAnnotationVisitor().visit(tree())
        Project.currNamespace = Project.config.rootNamespace
        currFile = null
    }

    /**
     * 编译这个文件
     */
    fun compile() {
        if(syntaxError) return
        currFile = this
        Project.currNamespace = namespace.identifier
        //创建默认函数
        val func = Function(
            (nameWithoutExtension + "_default").toSnakeCase(), Project.currNamespace,
            context = null
        )
        Function.currFunction = func
        MCFPPImVisitor().visit(tree())
        Project.currNamespace = Project.config.rootNamespace
        currFile = null
    }

    companion object{

        var currFile : MCFPPFile? = null

        private val dispatcher = CommandDispatcher<MCFPPFile>().apply {
            register(
                literal<MCFPPFile>("injectedBy")
                    .then(
                        argument<MCFPPFile, String>("className", string())
                            .executes {
                                try {
                                    val clazz = Class.forName(getString(it, "className"), false, Project.classLoader)
                                    LogProcessor.debug("Injecting class $clazz to namespace ${it.source.namespace.identifier}")
                                    it.source.namespace.injectedBy(clazz)
                                    return@executes 1
                                }catch (e: ClassNotFoundException){
                                    LogProcessor.error("Class not found: ${getString(it, "className")}")
                                    return@executes 0
                                }
                            }
                    )
            )
        }

        fun findFiles(inputPath: String): List<Path> {
            val fileList = ArrayList<Path>()
            val matcher = FileSystems.getDefault().getPathMatcher("glob:*.mcfpp")

            val startPath: Path = Paths.get(inputPath.replaceFirst("[*?].*".toRegex(), ""))

            try {
                Files.walkFileTree(startPath, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (matcher.matches(file.fileName)) {
                            fileList.add(file)
                        }
                        return FileVisitResult.CONTINUE
                    }
                })
            } catch (e: IOException) {
                LogProcessor.error("Error while searching for files: $e")
            }

            // Stable multi-file order: declaration headers, processors and body checks must not
            // depend on the host directory iteration order.
            fileList.sortBy { it.toString() }
            return fileList
        }

    }

}
