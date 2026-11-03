package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.Project
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class LambdaCallTest {
    @Test
    fun nonCapturingFunctionsCompileAndExportForRealExecution() {
        val source = Path.of("examples/lambda/basic/main.mcfpp")
        MCFPPStringTest.readFromString(Files.readString(source), version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertTrue(main.commands.analyzeAll().any { it.contains("function default.test:lambda") }, "Must invoke emitted lambda code")
        export("basic", mapOf("first" to 5, "second" to 9, "referenced" to 7))
    }

    @Test
    fun callbackArgumentsAndLambdaControlFlowExportForExecution() {
        MCFPPStringTest.readFromString(Files.readString(Path.of("examples/lambda/basic/control.mcfpp")), version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertFalse(main.scope.getVar("compound")!!.type is top.mcfpp.type.MCFPPFunctionType)
        export("control", mapOf("trailing" to 5, "argument" to 6, "reference" to 7, "generic" to 10,
            "order" to 12, "calls" to 2, "effects" to 3, "copyResult" to 34,
            "local" to 7, "after" to 42, "loopResult" to 4, "parameter" to 8, "effectsAfterParameter" to 4))
    }

    @Test
    fun invalidLambdaResultsAndCrossLambdaLoopControlAreRejected() {
        val bodies = listOf(
            "var bad: () -> int = { 1; };",
            "var bad: () -> void = { return 1; };",
            "var bad: () -> int = { \"x\" };",
            "var bad: (int) -> int = { value -> if (value > 0) { return 1; } };",
            "external again: bool = true; while (again) { var bad: () -> void = { break; }; break; }",
            "external again: bool = true; while (again) { var bad: () -> void = { continue; }; break; }"
        )
        for (body in bodies) {
            MCFPPStringTest.readFromString("func main(){ $body }", version = "26.3")
            assertTrue(Project.errorCount > 0, "Expected a diagnostic for $body")
        }
    }

    @Test
    fun callableCandidateCopiesAndJournalsDoNotRestoreInvalidatedConstants() {
        MCFPPStringTest.readFromString("""
            func first(value:int):int{return value;}
            func second(value:int):int{return value+1;}
            func main(){}
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        val first = functions.getValue("first").single()
        val second = functions.getValue("second").single()
        val signature = top.mcfpp.type.MCFPPFunctionType(listOf(top.mcfpp.type.MCFPPBaseType.Int), top.mcfpp.type.MCFPPBaseType.Int)
        functions.getValue("main").single().runInFunction {
            val copy = top.mcfpp.core.lang.FunctionValue("copy", signature)
            copy.nbtPath = top.mcfpp.lib.NBTPath.stack.intIndex(0).memberIndex("copy")
            copy.bindDeclaration()
            copy.assignedBy(top.mcfpp.core.lang.FunctionValue("firstValue", signature, first))
            top.mcfpp.analysis.StorageAccess.barrier(listOf(copy))
            assertNull(top.mcfpp.analysis.StorageAccess.snapshot(copy))
            assertSame(first, copy.knownTarget())
            val checkpoint = top.mcfpp.analysis.StorageAccess.checkpointGraph(listOf(copy))
            copy.assignedBy(top.mcfpp.core.lang.FunctionValue("secondValue", signature, second))
            top.mcfpp.analysis.StorageAccess.barrier(listOf(copy))
            assertNull(copy.knownTarget(), "Several runtime targets require the later dispatch boundary")
            checkpoint.restore()
            assertNull(top.mcfpp.analysis.StorageAccess.snapshot(copy))
            assertSame(first, copy.knownTarget())
        }
    }

    internal fun export(name: String, expected: Map<String, Int>) {
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val root = Path.of("build/lambda/sync-$name")
        val pack = root.resolve("pack")
        if (Files.exists(pack)) {
            val actual = pack.toRealPath()
            require(actual.startsWith(Path.of("build/lambda").toRealPath()) && actual.fileName.toString() == "pack")
            Files.walk(actual).use { paths -> paths.sorted(java.util.Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
        Files.createDirectories(pack)
        Files.writeString(pack.resolve("pack.mcmeta"), JSON.toJSONString(mapOf("pack" to mapOf(
            "description" to "Compiled synchronous function values", "min_format" to listOf(121, 0), "max_format" to listOf(121, 0)))))
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .flatMap { listOf(it) + it.compiledFunctions.values }.distinct().forEach { function ->
            val id = function.namespaceID.toString()
            val file = pack.resolve("data/${id.substringBefore(':')}/function/${id.substringAfter(':')}.mcfunction")
            Files.createDirectories(file.parent)
            Files.writeString(file, function.commands.analyzeAll().joinToString("\n") + "\n")
        }
        val entry = pack.resolve("data/lambda/function/run.mcfunction")
        Files.createDirectories(entry.parent)
        val initializer = main.declarationFile?.topFunction?.takeIf { function ->
            GlobalScope.localNamespaces.values.any { namespace -> namespace.scope.functions.values.any { function in it } }
        }
        Files.writeString(entry, (GlobalScope.scoreboards.values.map { "scoreboard objectives add ${it.name} ${it.criterion}" } +
            listOf("data modify storage mcfpp:system stack_frame set value [{}]") +
            listOfNotNull(initializer?.let { "function ${it.namespaceID}" }) +
            listOf("function ${main.namespaceID}",
                "data remove storage mcfpp:system stack_frame[0]")).joinToString("\n") + "\n")
        val manifest = mapOf("version" to "26.3", "unsupported" to "error", "failOnMissingResources" to true,
            "packs" to listOf("./pack"), "steps" to listOf(mapOf("commands" to listOf("function lambda:run"))),
            "assertions" to expected.map { (name, value) ->
                mapOf("storage" to mapOf("id" to "lambda:results", "path" to name, "equals" to value))
            } + mapOf("storage" to mapOf("id" to "mcfpp:system", "path" to "stack_frame", "equals" to emptyList<Any>())))
        Files.writeString(root.resolve("$name.dps.json"), JSON.toJSONString(manifest, JSONWriter.Feature.PrettyFormat))
    }
}
