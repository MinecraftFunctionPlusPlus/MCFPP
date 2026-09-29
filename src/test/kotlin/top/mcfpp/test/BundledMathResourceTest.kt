package top.mcfpp.test

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class BundledMathResourceTest {
    @Test
    fun floatLibrariesReferenceExistingFunctions() {
        val data = Path.of("src/main/resources/datapack/stdlib/data")
        val pattern = Regex("""\bfunction\s+([a-z0-9_.-]+):([a-z0-9_./-]+)""")
        val missing = mutableListOf<String>()
        var checked = 0
        for (namespace in listOf("math.float", "math.3vec_float")) {
            Files.walk(data.resolve("$namespace/function")).use { files ->
                files.filter { it.toString().endsWith(".mcfunction") }.forEach { source ->
                    Files.readAllLines(source).forEachIndexed { lineNumber, line ->
                        if (line.trimStart().startsWith("#")) return@forEachIndexed
                        pattern.findAll(line).forEach { match ->
                            checked++
                            val target = data.resolve(match.groupValues[1])
                                .resolve("function")
                                .resolve("${match.groupValues[2]}.mcfunction")
                            if (!Files.isRegularFile(target)) {
                                missing += "$source:${lineNumber + 1} -> ${match.value}"
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked > 0)
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
    }
}
