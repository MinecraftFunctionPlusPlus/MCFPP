package top.mcfpp.test

import top.mcfpp.Project
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class LambdaHigherOrderTest {
    @Test
    fun runtimeListsMapFilterFoldAndForeachExportForExecution() {
        MCFPPStringTest.readFromString(Files.readString(Path.of("examples/lambda/collections/main.mcfpp")), version = "26.3")
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions.values.flatten()
        val commands = functions.flatMap { it.commands.analyzeAll() }
        assertTrue(commands.any { it.contains("function default.test:lambda_") }, "Callbacks must execute actual function code")
        assertTrue(functions.any { it.identifier.startsWith("while") || it.identifier.startsWith("loop") }, "A runtime list must use existing traversal")
        LambdaCallTest().export("collections", linkedMapOf(
            "mappedFirst" to 11, "mappedLast" to 14, "selectedFirst" to 2, "selectedLast" to 4,
            "selectedSize" to 2, "folded" to 15, "skipped" to 13, "referencedLast" to 8,
            "flagLast" to 1, "result" to 40, "sourceCalls" to 1, "visits" to 4, "sum" to 8, "emptyFold" to 17
        ))
        val manifestFile = Path.of("build/lambda/sync-collections/collections.dps.json")
        val manifest = JSON.parseObject(Files.readString(manifestFile))
        for ((name, values) in mapOf("mapped" to listOf(11, 12, 13, 14), "selected" to listOf(2, 4),
                "original" to listOf(1, 2, 3, 4), "emptyMapped" to emptyList()))
            manifest.getJSONArray("assertions").add(mapOf("storage" to mapOf("id" to "lambda:results", "path" to name, "equals" to values)))
        Files.writeString(manifestFile, JSON.toJSONString(manifest, JSONWriter.Feature.PrettyFormat))
        // Runtime indexing uses real compiler-generated macros, exported by the production
        // DatapackCreator too; the older scalar-only lambda fixture does not need these.
        Project.macroFunction.forEach { (name, command) ->
            val file = Path.of("build/lambda/sync-collections/pack/data/mcfpp/function/dynamic/$name.mcfunction")
            Files.createDirectories(file.parent)
            Files.writeString(file, command)
        }
    }

    @Test
    fun incompatibleCallbacksHaveSourceDiagnostics() {
        val cases = listOf(
            "values.filter { it + 1 }" to "not assignable to 'bool'",
            "values.fold(0) { accumulator, element -> \"wrong\" }" to "not assignable to 'int'",
            "values.map { var discarded = it; }" to "map callback must produce a value"
        )
        for ((expression, expected) in cases) {
            MCFPPStringTest.readFromString("func main() { var values: list<int> = [1]; var result = $expression; }", version = "26.3")
            assertTrue(Project.errorCount > 0, expression)
            val diagnostics = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .flatMap { it.commands.analyzeAll() }.joinToString("\n")
            assertTrue(diagnostics.contains(expected), "$expression\n$diagnostics")
        }
    }
}
