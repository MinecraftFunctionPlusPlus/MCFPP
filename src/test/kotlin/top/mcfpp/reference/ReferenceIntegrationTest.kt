package top.mcfpp.reference

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.command.Commands
import top.mcfpp.runCompiler
import top.mcfpp.test.util.ContextCommandExecutor
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReferenceIntegrationTest {
    @Test fun normalCliExampleExportsAndExecutesRuntimeReferencesAndMissingReads() {
        val previousConfig = Project.config
        val previousIgnore = CompileSettings.ignoreStdLib
        val previousDebug = CompileSettings.isDebug
        val previousLibrary = CompileSettings.isLib
        val previousPrint = CompileSettings.printAll
        val previousInline = CompileSettings.maxWhileInline
        val previousLevel = LogProcessor.level
        val temporary = Files.createTempDirectory("mcfpp-reference-example-")
        try {
            Files.createDirectories(temporary.resolve("src"))
            Files.copy(Path.of("examples/references/src/main.mcfpp"), temporary.resolve("src/main.mcfpp"))
            Files.copy(Path.of("examples/references/mcfpp.toml"), temporary.resolve("mcfpp.toml"))
            assertEquals(0, runCompiler(arrayOf("build", "--project", temporary.resolve("mcfpp.toml").toString())))
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
            assertFalse(CompileSettings.ignoreStdLib)
            assertEquals("mcfpp", Project.config.name)
            val output = temporary.resolve("build")
            assertTrue(Files.isRegularFile(output.resolve("mcfpp/data/example.references/function/main.mcfunction")))
            assertFalse(Files.exists(output.resolve("Imports")))
            val functions = exportedReferenceFunctions(output.resolve("mcfpp/data"))
            val machine = ContextCommandExecutor(listOf(Commands.stackIn().analyze(), "function example.references:main"),
                functions, macros = exportedReferenceMacros(functions))
            assertEquals(listOf("shared-score", "saved-storage", "runtime-value", "runtime-missing"), machine.messages)
            assertEquals(1, machine.score("#example", "reference_points"))
            assertEquals(10, (machine.readStorage("example:references", "first").value as Number).toInt())
            assertEquals(99, (machine.readStorage("example:references", "second").value as Number).toInt())
            assertEquals("runtimeValue", machine.readStorage("example:references", "selectedPath").value)
            assertFailsWith<IllegalStateException> { machine.readStorage("example:references", "runtimeValue") }
            assertFailsWith<IllegalStateException> { machine.readStorage("mcfpp:system", "reference_access") }
            assertEquals(2, machine.executed.count {
                it.startsWith("data modify storage ") && it.endsWith("set from storage example:references runtimeValue")
            })
        } finally {
            Project.config = previousConfig
            CompileSettings.ignoreStdLib = previousIgnore
            CompileSettings.isDebug = previousDebug
            CompileSettings.isLib = previousLibrary
            CompileSettings.printAll = previousPrint
            CompileSettings.maxWhileInline = previousInline
            LogProcessor.level = previousLevel
            temporary.toFile().deleteRecursively()
        }
    }
}
