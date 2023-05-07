package top.mcfpp.io

import mcfppLexer
import mcfppParser
import org.antlr.v4.runtime.*
import top.mcfpp.Project
import top.mcfpp.lib.*
import java.io.*

/**
 * 用于读取和分析mcfpp代码。
 */
class McfppFileReader(path: String?) : McfppReader() {
    /**
     * 从指定路径读取mcfpp文件
     * @param path mcfpp文件的路径
     */
    init {
        this.path = path
        rpath = getRelativePath(Project.root.absolutePath, File(path!!).parentFile.absolutePath)
        currPath = rpath
        input = FileInputStream(path)
    }

    /**
     * 解析这个文件
     */
    @Throws(IOException::class)
    fun analyse() {
        Project.currFile = File(path!!)
        val charStream: CharStream = CharStreams.fromStream(input)
        val tokens = CommonTokenStream(mcfppLexer(charStream))
        McfppFileVisitor().visit(mcfppParser(tokens).compilationUnit())
    }

    /**
     * 编译这个文件
     */
    @Throws(IOException::class)
    fun compile() {
        Project.currNamespace = Project.defaultNamespace
        Project.currFile = File(path!!)
        val charStream: CharStream = CharStreams.fromStream(input)
        val lexer = mcfppLexer(charStream)
        val tokens = CommonTokenStream(lexer)
        val parser = mcfppParser(tokens)
        parser.addParseListener(McfppImListener())
        parser.compilationUnit()
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