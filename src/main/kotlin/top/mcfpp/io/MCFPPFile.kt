package top.mcfpp.io

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.antlr.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.Class
import top.mcfpp.model.field.FileField
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
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

    val field: FileField = FileField()

    val unsolvedImports = hashMapOf<String, String>()

    val inputStream : FileInputStream by lazy { FileInputStream(this) }

    /**
     * 此文件对应的命名空间，默认为文件的父目录和源代码目录的相对路径
     */
    var namespace: Namespace

    //TODO 同名文件的顶级函数之间的命名冲突
    val topFunction: Function = Function(this.name.toSnakeCase(), context = null)

    var syntaxError = false

    constructor(path: String) : super(path) {
        val n = Project.config.sourcePath!!.toAbsolutePath().relativize(this.toPath().toAbsolutePath().parent).toString()
        val str = Project.config.rootNamespace + "." + n.pathToNamespace().toSnakeCase()
        namespace = GlobalField.getOrCreateNamespace(str)
    }

    constructor(file: File) : this(file.absolutePath)

    internal constructor(): super("."){
        val str = Project.config.rootNamespace + ".test"
        namespace = GlobalField.getOrCreateNamespace(str)
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
        field.namespaceField = namespace.field
        Project.currNamespace = Project.config.rootNamespace
        currFile = null
        Project.ctx = null
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
            val qwq = GlobalField.getNamespace(n.key)
            if(qwq == null){
                LogProcessor.error("Namespace '$n' not found")
                continue
            }
            if(n.value == "*"){
                field.importedNamespaceField.add(qwq.field)
            }else{
                val owo = qwq.field.getDeclaredType(n.value)
                if(owo == null){
                    LogProcessor.error("Declared type '${n.key}.${n.value}' not found")
                    continue
                }
                val result = field.importField.addDeclaredType(owo)
                if(!result){
                    val pwp = field.importField.getDeclaredType(n.value)
                    LogProcessor.error("Already have import '${pwp!!.namespaceID}")
                }
            }
        }
        //类是否有空继承，以及实例化每个泛型类的类型
        field.namespaceField.forEachClass { c ->
            for (p in c.parent){
                if(p is Class.Companion.UndefinedClassOrInterface){
                    val r = p.getDefinedClassOrInterface()
                    if(r == null){
                        LogProcessor.error("Undefined class or interface: ${p.namespaceID}")
                        continue
                    }
                    c.unExtends(p)
                    c.extends(r)
                }
            }
        }
        //编译
        MCFPPFieldVisitor().visit(tree())
        Project.currNamespace = Project.config.rootNamespace
        currFile = null
        Project.ctx = null
    }

    fun runAnnotation(){
        if(syntaxError) return
        currFile = this
        Project.currNamespace = namespace.identifier
        MCFPPAnnotationVisitor().visit(tree())
        Project.currNamespace = Project.config.rootNamespace
        currFile = null
        Project.ctx = null
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
        Project.ctx = null
    }

    companion object{

        var currFile : MCFPPFile? = null

        /**
         * 获得targetPath相对于sourcePath的相对路径
         * @param sourcePath    : 原文件路径
         * @param targetPath    : 目标文件路径
         * @return 返回相对路径
         */
        private fun getRelativePath(sourcePath: String, targetPath: String): String {
            val pathSB: StringBuilder = StringBuilder()
            if (targetPath.indexOf(sourcePath) == 0) {
                pathSB.append(targetPath.replace(sourcePath, ""))
            } else {
                val sourcePathArray: List<String> = sourcePath.split("/")
                val targetPathArray: List<String> = targetPath.split("/")
                if (targetPathArray.size >= sourcePathArray.size) {
                    var i = 0
                    while (i < targetPathArray.size) {
                        if (!(sourcePathArray.size > i && targetPathArray[i] == sourcePathArray[i])) {
                            pathSB.append("../".repeat(0.coerceAtLeast(sourcePathArray.size - i)))
                            while (i < targetPathArray.size) {
                                pathSB.append(targetPathArray[i]).append("/")
                                i++
                            }
                            break
                        }
                        i++
                    }
                } else {
                    for (i in sourcePathArray.indices) {
                        if (!(targetPathArray.size > i && targetPathArray[i] == sourcePathArray[i])) {
                            pathSB.append("../".repeat(sourcePathArray.size - i))
                            break
                        }
                    }
                }
            }
            return pathSB.toString()
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