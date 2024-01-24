package top.mcfpp.io

import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.lib.*
import top.mcfpp.antlr.McfppFileVisitor
import top.mcfpp.antlr.McfppImVisitor
import java.io.*
import kotlin.io.path.absolutePathString

/**
 * 用于读取和分析mcfpp代码。
 * @param path mcfpp文件的路径
 */
class McfppFileReader(path: String?) : McfppReader() {
    init {
        this.path = path
        rpath = getRelativePath(Project.root.absolutePathString(), File(path!!).parentFile.absolutePath)
        currPath = rpath
        input = FileInputStream(path)
    }

    @Throws(IOException::class)
    fun tree():ParseTree{
        Project.currFile = File(path!!)
        if(!Project.trees.contains(Project.currFile)){
            val charStream: CharStream = CharStreams.fromStream(input)
            val tokens = CommonTokenStream(mcfppLexer(charStream))
            val parser = mcfppParser(tokens)
            Project.trees[Project.currFile] = parser.compilationUnit()
        }
        return Project.trees[Project.currFile]!!
    }

    /**
     * 解析这个文件
     */

    fun analyse() {
        McfppFileVisitor().visit(tree())
    }

    /**
     * 编译这个文件
     */
    fun compile() {
        Project.currNamespace = Project.defaultNamespace
        McfppImVisitor().visit(tree())
    }

    companion object {
        /**
         * 当前McfppFileReader读取的文件的相对路径
         */
        var currPath: String? = null

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