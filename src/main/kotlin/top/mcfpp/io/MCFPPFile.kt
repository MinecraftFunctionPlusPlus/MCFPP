package top.mcfpp.io

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.getString
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import org.antlr.v4.runtime.CharStream
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
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * 表示一个MCFPP文件
 */
class MCFPPFile : File {

    val field: FileScope = FileScope()

    val unsolvedImports = hashMapOf<String, String>()

    private val inputStream : FileInputStream by lazy { FileInputStream(this) }

    /**
     * 此文件对应的命名空间，默认为文件的父目录和源代码目录的相对路径
     */
    var namespace: Namespace

    val topFunction: Function = Function(this.name.toSnakeCase() + "__top__", context = null).apply {
        tags.add(FunctionTag.LOAD)
    }

    var syntaxError = false

    constructor(path: String) : super(path) {
        val n = Project.config.sourcePath!!.toAbsolutePath().relativize(this.toPath().toAbsolutePath().parent).toString()
        val str = if(n.isEmpty()){
            Project.config.rootNamespace
        }else{
            Project.config.rootNamespace + "." + n.pathToNamespace().toSnakeCase()
        }
        namespace = GlobalScope.getOrCreateNamespace(str)
    }

    constructor(file: File) : this(file.absolutePath)

    internal constructor(): super("."){
        val str = Project.config.rootNamespace + ".test"
        namespace = GlobalScope.getOrCreateNamespace(str)
    }

    fun token(): CommonTokenStream {
        if(!Project.tokens.contains(this)){
            val charStream: CharStream = CharStreams.fromStream(inputStream)
            val tokens = CommonTokenStream(mcfppLexer(charStream))
            Project.tokens[this] = tokens
        }
        return Project.tokens[this]!!
    }

    fun tree(): ParseTree {
        if(!Project.trees.contains(this)){
            val tokens = token()
            val parser = mcfppParser(tokens)
            parser.removeErrorListeners()
            parser.addErrorListener(MCFPPErrorListener())
            Project.trees[this] = parser.compilationUnit()
        }
        return Project.trees[this]!!
    }

    /**
     * 编制类型索引
     */
    fun indexType(){
        currFile = this
        Project.currNamespace = namespace.identifier
        MCFPPTypeVisitor().visit(tree())
        field.namespaceField = namespace.scope
        Project.currNamespace = Project.config.rootNamespace
        currFile = null
    }

    fun runCommand(){
        if(nameWithoutExtension.isEmpty()){
            useLines {
                for (i in it){
                    if(i.startsWith("#>")){
                        val n = i.substring(2).trim()
                        val parse = dispatcher.parse(n, this)
                        try {
                            dispatcher.execute(parse)
                        }catch (e: Exception){
                            LogProcessor.error("Failed to execute command: $n", e)
                        }
                    }else{
                        return@useLines
                    }
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
        //引用
        for (n in unsolvedImports){
            val qwq = GlobalScope.getUnsolvedImportNamespace(n.key)
            if(qwq == null){
                LogProcessor.error("Namespace '$n' not found")
                continue
            }
            if(n.value == "*"){
                field.importedNamespaceField[qwq.identifier] = qwq.scope
            }else{
                val owo = qwq.scope.getDeclaredType(n.value)
                if(owo == null){
                    LogProcessor.error("Declared type '${n.key}.${n.value}' not found")
                    continue
                }
                val result = field.importField.getOrPut(owo.namespace) { NamespaceScope(owo.namespace) }
                    .addDeclaredType(owo)
                if(owo is DataTemplate){
                    owo.companionObject?.let {
                        field.importField[owo.namespace]!!.addObject(it.identifier, it)
                    }
                }
                if(!result){
                    LogProcessor.error("Already have import '${owo.namespaceID}")
                }
            }
        }
        //检查索引
        field.checkIndex()
        //编译
        MCFPPFieldVisitor().visit(tree())
        Project.currNamespace = Project.config.rootNamespace
        currFile = null
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
                                    val clazz = Class.forName(getString(it, "className"))
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

            return fileList
        }

    }

}