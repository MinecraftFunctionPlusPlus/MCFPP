package top.mcfpp.test

import top.mcfpp.io.MCFPPFile.Companion.findFiles
import java.nio.file.Path
import kotlin.test.Test

class FilePathTest {
    @Test
    fun main() {
        val inputPath = "/src/**" // 示例路径
        val result: List<Path> = findFiles(inputPath)

        for (path in result) {
            println(path)
        }
    }
}