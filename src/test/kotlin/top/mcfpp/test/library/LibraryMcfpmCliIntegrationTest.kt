package top.mcfpp.test.library

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.runCompiler
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.test.util.writeProjectFixture
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryMcfpmCliIntegrationTest {
    @Test
    fun unpublishedSessionExceptionIsPrintedOnceAndHonorsTheMergedLogLevel() = isolated { root ->
        val source = Files.createDirectories(root.resolve("src")).resolve("main.mcfpp")
        Files.writeString(source, "func main(){}")
        val configuration = writeProjectFixture(root.resolve("mcfpp.toml"), """{
            "sourcePath":"src", "targetPath":"out", "noMcLib":true,
            "args":["-ignoreStdLib","-level=NONE"]
        }""")
        val failure: () -> Unit = { throw IllegalStateException("unpublished session failure") }
        val hooks = Project.stageProcessor[Project.CompileStage.COMPILE.ordinal]
        hooks.add(failure)
        try {
            val visible = invoke(arrayOf(configuration.toString(), "-level=ERROR"))
            assertTrue(visible.first != 0)
            assertEquals("MCFPP_COMPILATION_FAILED: unpublished session failure", visible.second.trim())

            val quiet = invoke(arrayOf(configuration.toString()))
            assertTrue(quiet.first != 0)
            assertEquals("", quiet.second, "File-level NONE must suppress unpublished exceptions too")
        } finally {
            hooks.remove(failure)
        }
    }

    @Test
    fun ordinaryCliPreservesExplicitOverridesAndFailsQuietlyThenReleasesTheSession() = isolated { root ->
        val source = Files.createDirectories(root.resolve("src")).resolve("main.mcfpp")
        Files.writeString(source, "func main(){}")
        val configuration = writeProjectFixture(root.resolve("mcfpp.toml"), """{
            "sourcePath":"src", "targetPath":"out", "namespace":"cli.fixture", "noMcLib":true,
            "args":["-ignoreStdLib","-maxWhileInline=1","-level=ERROR"]
        }""")
        val observed = arrayListOf<Pair<Int, LogLevel>>()
        val hook: () -> Unit = { observed += CompileSettings.maxWhileInline to LogProcessor.level }
        val hooks = Project.stageProcessor[Project.CompileStage.PRE_INIT.ordinal]
        hooks.add(hook)
        try {
            val arguments = arrayOf(configuration.toString(), "-maxWhileInline=7", "-level=NONE")
            assertEquals(0, invoke(arguments).first)
            assertTrue(Files.isRegularFile(root.resolve("out/mcfpp/data/cli.fixture/function/main.mcfunction")))
            assertEquals(listOf(7 to LogLevel.NONE), observed)

            Files.writeString(source, "func main(){var value:int=missingValue}")
            val failed = invoke(arguments)
            assertTrue(failed.first != 0, "NONE must still return compilation failure")
            assertEquals("", failed.second, "Quiet compiler diagnostics must not be repeated on stderr")

            Files.writeString(source, "func main(){}")
            assertEquals(0, invoke(arguments).first, "A failed invocation must release the compiler session")
            assertEquals(List(3) { 7 to LogLevel.NONE }, observed)
            assertTrue(Files.exists(root.resolve("mcfpp.lock")))
        } finally {
            hooks.remove(hook)
        }
    }

    @Test
    fun missingInstalledLockOrCacheReportsInstallGuidanceAndNeverRewritesTheLock() = isolated { root ->
        val configuration = writeProjectFixture(root.resolve("mcfpp.toml"), """{"targetPath":"out","mcfpm":{"default-repository":"private","repositories":{"private":"https://unreachable.invalid/maven/"},"bindings":{"fixture":"private"}}}""")
        Files.writeString(root.resolve("mcfpp-packages.json"), """{"schema":1,"mcfpp":{"fixture:library":"1.0.0"}}""")
        val lock = root.resolve("mcfpp.lock")
        val missingLock = invoke(arrayOf(configuration.toString(), "--locked", "--offline"))
        assertTrue(missingLock.first != 0)
        assertTrue(missingLock.second.contains("missing or out of date"), missingLock.second)
        assertTrue(missingLock.second.contains("mcfpp install"), missingLock.second)
        assertFalse(Files.exists(lock))

        // A negative installed-cache fixture: the locked digest is deliberately absent.
        // Successful installation is exercised separately through the real mcfpm executable.
        Files.writeString(lock, """
            lock-version = 1
            resolver-version = "1"
            roots = ["fixture:library"]
            minecraft-version = "1.21.8"
            [[packages]]
            id = "fixture:library"
            version = "1.0.0"
            repository = "https://unreachable.invalid/maven/"
            descriptor-sha256 = "${"a".repeat(64)}"
            features = []
            artifacts = [{ type = "compiler.mcfpp.library", classifier = "mcfpp", extension = "zip", sha256 = "${"b".repeat(64)}", size = 10, executable = false }]
            [[load-order]]
            package = "fixture:library"
            type = "compiler.mcfpp.library"
            classifier = "mcfpp"
        """.trimIndent())
        val before = Files.readAllBytes(lock)
        val missingCache = invoke(arrayOf(configuration.toString(), "--offline"))
        assertTrue(missingCache.first != 0)
        assertTrue(missingCache.second.contains("MCFPM-CACHE-001"), missingCache.second)
        assertTrue(missingCache.second.contains("fixture:library"), missingCache.second)
        assertTrue(missingCache.second.contains("fixture:library"), missingCache.second)
        assertContentEquals(before, Files.readAllBytes(lock))
        assertFalse(Files.exists(root.resolve("out")))
    }

    private fun invoke(arguments: Array<String>): Pair<Int, String> {
        val out = StringWriter()
        val err = StringWriter()
        val exit = runCompiler(arrayOf("build", "--project", *arguments), PrintWriter(out, true), PrintWriter(err, true))
        return exit to err.toString()
    }

    private fun isolated(action: (Path) -> Unit) {
        val config = Project.config
        val flags = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib, CompileSettings.printAll)
        val inline = CompileSettings.maxWhileInline
        val level = LogProcessor.level
        val errors = Project.errorCount
        val warnings = Project.warningCount
        val home = System.getProperty("user.home")
        val root = Files.createTempDirectory("mcfpp-cli-installed-")
        try {
            System.setProperty("user.home", root.resolve("home").toString())
            action(root)
        } finally {
            Project.closeCompilation()
            Project.config = config
            CompileSettings.isDebug = flags[0]
            CompileSettings.ignoreStdLib = flags[1]
            CompileSettings.isLib = flags[2]
            CompileSettings.printAll = flags[3]
            CompileSettings.maxWhileInline = inline
            LogProcessor.level = level
            Project.errorCount = errors
            Project.warningCount = warnings
            System.setProperty("user.home", home)
            root.toFile().deleteRecursively()
        }
    }
}
