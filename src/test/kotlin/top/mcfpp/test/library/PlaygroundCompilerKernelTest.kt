package top.mcfpp.test.library

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.mcfpp.api.library.LibraryCompilerApi
import top.mcfpp.api.library.PlaygroundCompilerApi
import top.mcfpp.library.api.PlaygroundCompilerKernel
import top.mcfpp.model.scope.GlobalScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class PlaygroundCompilerKernelTest {
    @TempDir lateinit var root: Path

    private val example = """
        func greet() { print("Hello, MCFPP!") }
        func main() {
            greet()
            var values: list<int> = [10, 20]
            values.add(30)
            print(values.size())
        }
    """.trimIndent()

    @Suppress("UNCHECKED_CAST")
    private fun files(result: Map<String, Any>): Map<String, String> =
        (result.getValue("files") as List<Map<String, String>>).associate { it.getValue("path") to it.getValue("content") }

    private fun successful(kernel: AutoCloseable, source: String = example): Map<String, Any> =
        PlaygroundCompilerApi.compile(kernel, source).also { assertEquals(true, it["success"], it["diagnostics"].toString()) }

    @Test fun `recompilation restores private directories and rejects stale output`() {
        val workspace = root.resolve("kernel")
        (PlaygroundCompilerApi.openKernel(workspace, "1.21.8", "playground") as PlaygroundCompilerKernel).use { kernel ->
            val first = files(successful(kernel))
            val retained = kernel.retainedStateCounts()
            assertTrue(retained.isNotEmpty())
            assertEquals(first, files(successful(kernel)))
            assertEquals(retained, kernel.retainedStateCounts())
            val modified = files(successful(kernel, example.replace("values.add(30)", "values.add(30)\nvalues.add(40)")))
            assertContains(modified.getValue("data/playground/function/main.mcfunction"), "\"text\": \"4\"")
            assertEquals(retained, kernel.retainedStateCounts())
            val broken = PlaygroundCompilerApi.compile(kernel, "func main( {")
            assertEquals(false, broken["success"])
            assertTrue(files(broken).isEmpty())
            assertEquals(retained, kernel.retainedStateCounts())
            assertEquals(first, files(successful(kernel)))
            assertEquals(retained, kernel.retainedStateCounts())
            Files.list(workspace).use { paths -> assertFalse(paths.anyMatch { it.fileName.toString().startsWith("request-") }) }
        }
    }

    @Test fun `kernel output agrees with the one-shot compiler`() {
        val project = root.resolve("ordinary")
        Files.createDirectories(project.resolve("src/main/mcfpp"))
        Files.writeString(project.resolve("src/main/mcfpp/main.mcfpp"), example)
        val configuration = project.resolve("mcfpp.toml")
        Files.writeString(configuration, "schema = 1\nname = \"playground\"\nnamespace = \"playground\"\nversion = \"1.21.8\"\nnoMcLib = true\n")
        val request = mapOf<String, Any>("apiVersion" to 1, "configuration" to configuration,
            "lockedPackages" to emptyList<Any>(), "libraries" to emptyList<Any>(), "plugins" to emptyList<Any>(),
            "resourcePacks" to emptyList<Any>(), "datapacks" to emptyList<Any>())
        val result = LibraryCompilerApi.compile(LibraryCompilerApi.openSession(request))
        assertEquals(true, result["success"], result["diagnostics"].toString())
        val output = project.resolve("build/playground")
        val expected = Files.walk(output).use { stream -> stream.filter { Files.isRegularFile(it) }.toList().associate {
            output.relativize(it).joinToString("/") to Files.readString(it)
        } }
        PlaygroundCompilerApi.openKernel(root.resolve("kernel"), "1.21.8", "playground").use { kernel ->
            assertEquals(expected, files(successful(kernel)))
        }
    }

    @Test fun `new declarations and semantic failures stay inside their request`() {
        PlaygroundCompilerApi.openKernel(root.resolve("kernel"), "1.21.8", "playground").use { kernel ->
            val first = files(successful(kernel, "func extra() { print(1) }\nfunc main() { extra() }"))
            assertTrue(first.keys.any { it.endsWith("/extra.mcfunction") })
            val second = files(successful(kernel, "func main() { print(2) }"))
            assertFalse(second.keys.any { it.endsWith("/extra.mcfunction") })
            val failure = PlaygroundCompilerApi.compile(kernel, "func main() { missingFunction() }")
            assertEquals(false, failure["success"])
            assertTrue(files(failure).isEmpty())
            assertEquals(second, files(successful(kernel, "func main() { print(2) }")))
        }
    }

    @Test fun `kernels share compiler exclusivity and close idempotently`() {
        val kernel = PlaygroundCompilerApi.openKernel(root.resolve("first"), "1.21.8", "playground")
        try {
            assertFailsWith<IllegalStateException> { PlaygroundCompilerApi.openKernel(root.resolve("second"), "1.21.8", "playground") }
            assertFailsWith<IllegalStateException> { LibraryCompilerApi.openSession(emptyMap()) }
        } finally { kernel.close(); kernel.close() }
        assertFailsWith<IllegalStateException> { PlaygroundCompilerApi.compile(kernel, example) }
        PlaygroundCompilerApi.openKernel(root.resolve("third"), "1.21.8", "playground").close()
    }

    @Test fun `unused standard members stay deferred and used inherited layouts reset`() {
        PlaygroundCompilerApi.openKernel(root.resolve("kernel"), "1.21.8", "playground").use { kernel ->
            successful(kernel, "func main() { print(1) }")
            val templates = (GlobalScope.stdNamespaces.values + GlobalScope.libNamespaces.values)
                .flatMap { it.scope.template.values }
            val commandResult = templates.single { it.identifier == "CommandResult" }
            assertTrue(commandResult.hasDeferredLibraryMembers)
            assertTrue(commandResult.parent.isNotEmpty(), "Nominal inheritance is ready before member materialization")
            val deferred = templates.filter { it.hasDeferredLibraryMembers }
            top.mcfpp.io.FunctionInventory.all()
            assertTrue(deferred.all { it.hasDeferredLibraryMembers }, "Output inventory must not load unused library members")
            var checkedEmission = false
            val afterEmission: () -> Unit = {
                checkedEmission = true
                assertTrue(deferred.all { it.hasDeferredLibraryMembers }, "Unexpected member loads: " +
                    deferred.filterNot { it.hasDeferredLibraryMembers }.joinToString { it.namespaceID.toString() })
            }
            val callbacks = top.mcfpp.Project.stageProcessor[top.mcfpp.Project.CompileStage.GEN_DATAPACK.ordinal]
            callbacks.add(afterEmission)
            try { successful(kernel, "func main() { print(2) }") }
            finally { callbacks.remove(afterEmission) }
            assertTrue(checkedEmission)
            val source = """
                import mcfpp.minecraft.std:*
                data Result: CommandResult { constructor() {} }
                func main() { var result = Result(); print(result.result); print(result.success); }
            """.trimIndent()
            val first = files(successful(kernel, source))
            assertTrue(commandResult.hasDeferredLibraryMembers, "The retained baseline must drop request-owned members")
            assertEquals(first, files(successful(kernel, source)))
            assertTrue(commandResult.hasDeferredLibraryMembers)
        }
    }
}
