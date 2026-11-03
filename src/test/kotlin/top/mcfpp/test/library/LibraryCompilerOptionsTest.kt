package top.mcfpp.test.library

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import top.mcfpp.CompileSettings
import top.mcfpp.LibraryPublicationConfig
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.compile
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.parseCompilerArgs
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.test.util.writeProjectFixture
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class LibraryCompilerOptionsTest {
    private fun isolated(action: (Path) -> Unit) {
        val originalConfig = Project.config
        val flags = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib, CompileSettings.printAll)
        val inline = CompileSettings.maxWhileInline
        val level = LogProcessor.level
        val errors = Project.errorCount
        val warnings = Project.warningCount
        val root = Files.createTempDirectory("mcfpp-library-options-")
        try {
            Project.errorCount = 0
            Project.warningCount = 0
            action(root)
        } finally {
            Project.config = originalConfig
            Project.init()
            CompileSettings.isDebug = flags[0]
            CompileSettings.ignoreStdLib = flags[1]
            CompileSettings.isLib = flags[2]
            CompileSettings.printAll = flags[3]
            CompileSettings.maxWhileInline = inline
            LogProcessor.level = level
            Project.errorCount = errors
            Project.warningCount = warnings
            root.toFile().deleteRecursively()
        }
    }

    @Test fun explicitCliFlagsOverrideFileFlagsAndOmittedFlagsRetainTheirValues() = isolated { root ->
        val file = root.resolve("project.toml")
        writeProjectFixture(file, """{"args":["-debug","-ignoreStdLib","-isLib","-printAll","-maxWhileInline=17","-level=WARN"]}""")
        Project.readConfig(parseCompilerArgs(arrayOf(file.toString(), "-debug=false", "-isLib=false", "-maxWhileInline=9", "-level=NONE")))
        assertFalse(CompileSettings.isDebug)
        assertFalse(CompileSettings.isLib)
        assertTrue(CompileSettings.ignoreStdLib)
        assertTrue(CompileSettings.printAll)
        assertEquals(9, CompileSettings.maxWhileInline)
        assertEquals(LogLevel.NONE, LogProcessor.level)

        // Both sides omit these settings, so the caller's established defaults survive.
        writeProjectFixture(file, "{}")
        CompileSettings.maxWhileInline = 32
        CompileSettings.ignoreStdLib = false
        Project.readConfig(parseCompilerArgs(arrayOf(file.toString())))
        assertEquals(32, CompileSettings.maxWhileInline)
        assertFalse(CompileSettings.ignoreStdLib)
        assertEquals(0, Project.errorCount)
    }

    private class RecordingAppender : AbstractAppender(
        "library-options-diagnostics", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY
    ) {
        val events = arrayListOf<LogEvent>()
        override fun append(event: LogEvent) { events.add(event.toImmutable()) }
    }

    @Test fun quietRealCompilationStillFailsAndCollectsDiagnosticsWithoutPublishingThem() = isolated { root ->
        val source = Files.createDirectories(root.resolve("src"))
        Files.writeString(source.resolve("broken.mcfpp"), "func main(){var value:int=missingValue}")
        val originalLogger = LogProcessor.logger
        val logger = LogManager.getLogger("library.options.diagnostics.tests") as org.apache.logging.log4j.core.Logger
        val originalLoggerLevel = logger.level
        val originalAdditive = logger.isAdditive
        val appender = RecordingAppender().apply { start() }
        logger.level = Level.ALL
        logger.isAdditive = false
        logger.addAppender(appender)
        LogProcessor.logger = logger
        LogProcessor.level = LogLevel.NONE
        CompileSettings.isDebug = false
        CompileSettings.ignoreStdLib = true
        CompileSettings.printAll = false
        val capture = LogProcessor.beginDiagnosticCapture()
        try {
            compile(ProjectConfig(root = root, sourcePath = source, targetPath = root.resolve("out"),
                noDatapack = true, noMcLib = true))
            val compilerErrors = Project.errorCount
            assertTrue(compilerErrors > 0, "The real compiler must reject the unresolved source name under NONE")
            val failure = IllegalStateException("retained quiet exception")
            LogProcessor.warn("quiet warning")
            LogProcessor.warn("quiet warning with cause", failure)
            LogProcessor.error("quiet error with cause", failure)
            assertEquals(compilerErrors + 1, Project.errorCount)
            assertEquals(2, Project.warningCount)
            assertEquals(LogLevel.NONE, LogProcessor.level)
            val publication = capture.seal()
            assertTrue(publication.diagnostics.any { it.level == LogLevel.ERROR && it.message.contains("missingValue") })
            assertTrue(publication.diagnostics.any { it.level == LogLevel.WARN && it.message == "quiet warning" })
            assertTrue(publication.diagnostics.any { it.originalThrowable === failure })
            // Deferred delivery retains the original visibility even if the host changes it later.
            LogProcessor.level = LogLevel.DEBUG
            publication.publishLogs()
            assertTrue(appender.events.isEmpty())
        } finally {
            capture.close()
            LogProcessor.logger = originalLogger
            logger.removeAppender(appender)
            appender.stop()
            logger.level = originalLoggerLevel
            logger.isAdditive = originalAdditive
        }
    }

    @Test fun projectSavepointsRestorePublicationMetadataAcrossPreparedChangesAndNull() = isolated { root ->
        Project.config = ProjectConfig(root = root)
        Project.init()
        val original = LibraryPublicationConfig("fixture:original", "1.0.0", "main")
        val replacement = LibraryPublicationConfig("fixture:replacement", "2.0.0", "other")
        Project.config.library = original
        val checkpoint = ProjectStateJournal.checkpoint()
        val prepared = checkpoint.prepare { Project.config.library = replacement }
        assertSame(original, Project.config.library)
        prepared.apply()
        assertSame(replacement, Project.config.library)
        prepared.rollback()
        assertSame(original, Project.config.library)
        Project.config.library = null
        val empty = ProjectStateJournal.checkpoint()
        Project.config.library = replacement
        empty.restore()
        assertNull(Project.config.library)
        checkpoint.restore()
        assertSame(original, Project.config.library)
    }
}
