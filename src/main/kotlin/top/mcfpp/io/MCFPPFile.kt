package top.mcfpp.io

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.antlr.*
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.model.Class
import top.mcfpp.model.function.Function
import top.mcfpp.model.field.FileField
import top.mcfpp.model.field.GlobalField
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class MCFPPFile(path : String) : File(path) {

    constructor(file: File) : this(file.absolutePath)

    val field = FileField()

    val inputStream : FileInputStream by lazy { FileInputStream(this) }

    //TODO 同名文件的顶级函数之间的命名冲突
    val topFunction = Function(StringHelper.toLowerCase(this.name))

    @Throws(IOException::class)
    fun tree(): ParseTree {
        if(!Project.trees.contains(this)){
            val charStream: CharStream = CharStreams.fromStream(inputStream)
            val tokens = CommonTokenStream(mcfppLexer(charStream))
            val parser = mcfppParser(tokens)
            Project.trees[this] = parser.compilationUnit()
        }
        return Project.trees[this]!!
    }

    /**
     * 编制类型索引
     */
    fun indexType(){
        currFile = this
        McfppTypeVisitor().visit(tree())
        //类是否有空继承
        GlobalField.localNamespaces.forEach { _, u ->
            u.field.forEachClass { c ->
                for ((index,p) in c.parent.withIndex()){
                    if(p is Class.Companion.UndefinedClassOrInterface){
                        val r = p.getDefinedClassOrInterface()
                        if(r == null){
                            LogProcessor.error("Undefined class or interface: ${p.namespaceID}")
                            continue
                        }
                        c.parent.remove(p)
                        c.parent.add(index,r)
                    }
                }
            }
        }
        currFile = null
    }

    /**
     * 编制函数索引
     */
    fun indexFunction() {
        currFile = this
        McfppFieldVisitor().visit(tree())
        currFile = null
    }

    /**
     * 编译这个文件
     */
    fun compile() {
        currFile = this
        Project.currNamespace = Project.config.defaultNamespace
        //创建默认函数
        val func = Function(
            StringHelper.toLowerCase(nameWithoutExtension + "_default"), Project.currNamespace,
            MCFPPBaseType.Void
        )
        Function.currFunction = func
        McfppImVisitor().visit(tree())
        currFile = null
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
    }

}