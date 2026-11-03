package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.CompilerOptions
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.CommentLevel
import top.mcfpp.parseCompilerArgs
import top.mcfpp.runCompiler
import top.mcfpp.util.LogProcessor
import top.mcfpp.test.util.writeProjectFixture
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectConfigInputTest {
    private fun isolated(action: (Path) -> Unit) {
        val config = Project.config
        val errors = Project.errorCount
        val warnings = Project.warningCount
        val flags = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib, CompileSettings.printAll)
        val inline = CompileSettings.maxWhileInline
        val level = LogProcessor.level
        val root = Files.createTempDirectory("mcfpp-config-input-")
        try {
            Project.errorCount = 0
            Project.warningCount = 0
            action(root)
        } finally {
            Project.config = config
            Project.errorCount = errors
            Project.warningCount = warnings
            CompileSettings.isDebug = flags[0]
            CompileSettings.ignoreStdLib = flags[1]
            CompileSettings.isLib = flags[2]
            CompileSettings.printAll = flags[3]
            CompileSettings.maxWhileInline = inline
            LogProcessor.level = level
            root.toFile().deleteRecursively()
        }
    }

    private fun read(file: Path, json: String): ProjectConfig {
        writeProjectFixture(file, json)
        return Project.readConfig(CompilerOptions().apply { this.file = file.toFile() })
    }

    @Test fun deserializesProjectFieldsAndResolvesPathsFromTheProjectRoot() = isolated { directory ->
        val config = read(directory.resolve("project.toml"), """
            {"root":"app","sourcePath":"src","targetPath":"out","dataPath":"assets",
             "namespace":"example","version":"26.3","description":"Test datapack",
             "stdLib":["custom"],"stdNamespace":["example.lang"],"includes":["lib"],
             "jars":["providers.jar"],"commentLevel":"warn","noDatapack":true,
             "noMcLib":true,"copyImport":false}
        """.trimIndent())
        val root = directory.resolve("app")
        assertEquals(root, config.root)
        assertEquals(root.resolve("src"), config.sourcePath)
        assertEquals(root.resolve("out"), config.targetPath)
        assertEquals(root.resolve("assets"), config.dataPath)
        assertEquals(listOf(root.resolve("lib").toString()), config.includes)
        assertEquals(listOf(root.resolve("providers.jar").toString()), config.jars)
        assertEquals(listOf("custom"), config.stdLib)
        assertEquals(listOf("example.lang"), config.stdNamespace)
        assertEquals("example", config.rootNamespace)
        assertEquals("26.3", config.version)
        assertEquals("Test datapack", config.description)
        assertEquals(CommentLevel.WARN, config.commentLevel)
        assertTrue(config.noDatapack)
        assertTrue(config.noMcLib)
        assertFalse(config.copyImport)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }

    @Test fun projectFilesRetainDefaults() = isolated { directory ->
        val config = read(directory.resolve("project.toml"), "{}")
        assertEquals("project", config.name)
        assertEquals(directory, config.root)
        assertEquals(ProjectConfig().version, config.version)
        assertEquals(ProjectConfig().stdLib, config.stdLib)
        assertEquals(ProjectConfig().commentLevel, config.commentLevel)
        assertEquals(0, Project.errorCount)
    }

    @Test fun recognizesNamespaceAliasAndWarnsAboutUnknownFields() = isolated { directory ->
        val config = read(directory.resolve("project.toml"), """{"rootNamespace":"alias","unknown":1}""")
        assertEquals("alias", config.rootNamespace)
        assertEquals(0, Project.errorCount)
        assertEquals(1, Project.warningCount)
    }

    @Test fun helpAndVersionCommandsDoNotNeedAProjectFile() {
        for (args in listOf(emptyArray(), arrayOf("--help"), arrayOf("--version"))) {
            val output = StringWriter()
            assertEquals(0, runCompiler(args, PrintWriter(output, true)))
            assertTrue(output.toString().contains("MCFPP"))
        }
        val errors = StringWriter()
        assertEquals(2, runCompiler(arrayOf("-debug"), err = PrintWriter(errors, true)))
        assertTrue(errors.toString().contains("Unknown option"))
    }

    @Test fun missingAndMalformedFilesAreInputErrorsEvenWithLoggingDisabled() = isolated { directory ->
        val missing = directory.resolve("missing.toml")
        Project.readConfig(parseCompilerArgs(arrayOf(missing.toString(), "-level=NONE")))
        assertEquals(1, Project.errorCount)
        for (toml in listOf("schema = 2", "schema = 1\njars = [", "schema = 1\nversion = {", "schema = 1\njars = [\"\\u0000\"]", "schema = 1\nname = \"../outside\"")) {
            val before = Project.errorCount
            val file = Files.writeString(directory.resolve("invalid.toml"), toml)
            Project.readConfig(CompilerOptions().apply { this.file = file.toFile() })
            assertEquals(before + 1, Project.errorCount, toml)
        }
    }
}
