package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.expand.FunctionStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.logging.log4j.core.Logger as CoreLogger

class MniCompilerDiagnosticRoutingTest {
    private class RecordingAppender(private val order: MutableList<String>) :
        AbstractAppender(
            "mni-compiler-diagnostic-routing",
            null,
            PatternLayout.createDefaultLayout(),
            false,
            Property.EMPTY_ARRAY
        ) {
        val events = arrayListOf<LogEvent>()
        override fun append(event: LogEvent) {
            events.add(event.toImmutable())
            order.add("logger")
        }
    }

    private class TrackingOutputStream(private val order: MutableList<String>) : OutputStream() {
        private val bytes = ByteArrayOutputStream()
        override fun write(value: Int) {
            order.add("stderr")
            bytes.write(value)
        }
        override fun write(value: ByteArray, offset: Int, length: Int) {
            if (length > 0) order.add("stderr")
            bytes.write(value, offset, length)
        }
        fun text() = bytes.toString(Charsets.UTF_8)
    }

    private fun withRouting(action: (CoreLogger, RecordingAppender, MutableList<String>, TrackingOutputStream) -> Unit) {
        Project.init()
        val project = ProjectStateJournal.checkpoint()
        val oldLogger = LogProcessor.logger
        val oldLevel = LogProcessor.level
        val oldFunction = Function.currFunction
        val oldForced = Function.forcedField
        val oldFile = MCFPPFile.currFile
        val oldErr = System.err
        val oldDebug = CompileSettings.isDebug
        val oldIsLib = CompileSettings.isLib
        val logger = LogManager.getLogger("mcfpp") as CoreLogger
        val level = logger.level
        val additive = logger.isAdditive
        val order = arrayListOf<String>()
        val appender = RecordingAppender(order).apply { start() }
        val stderr = TrackingOutputStream(order)
        logger.level = Level.ALL
        logger.isAdditive = false
        logger.addAppender(appender)
        LogProcessor.logger = logger
        LogProcessor.level = LogLevel.DEBUG
        CompileSettings.isDebug = false
        Project.config.commentLevel = top.mcfpp.command.CommentLevel.DEBUG
        System.setErr(PrintStream(stderr, true, Charsets.UTF_8))
        try {
            action(logger, appender, order, stderr)
        } finally {
            try {
                project.restore()
            } finally {
                System.setErr(oldErr)
                Project.ctx.clear()
                Function.currFunction = oldFunction
                Function.forcedField = oldForced
                MCFPPFile.currFile = oldFile
                LogProcessor.logger = oldLogger
                LogProcessor.level = oldLevel
                CompileSettings.isDebug = oldDebug
                CompileSettings.isLib = oldIsLib
                logger.removeAppender(appender)
                appender.stop()
                logger.level = level
                logger.isAdditive = additive
            }
        }
    }

    @Test fun projectOptimizationLoggingAndManualWarningCounterJoinNestedCapturesOnce() = withRouting { _, appender, _, _ ->
        val load = Function("load", "route_test", null)
        Project.projectLoad = load
        Project.config.rootNamespace = "route_test"
        Project.config.name = "route_test"
        Project.config.root = java.nio.file.Paths.get("route_test")
        CompileSettings.isLib = false
        val project = ProjectStateJournal.checkpoint()
        val function = FunctionStateJournal.checkpoint(listOf(load))
        try {
            LogProcessor.beginDiagnosticCapture().use { outer ->
                LogProcessor.beginDiagnosticCapture().use { inner ->
                    Project.optimization()
                    assertEquals(1, Project.warningCount)
                    assertTrue(appender.events.isEmpty())
                    inner.commit()
                }
                assertTrue(appender.events.isEmpty())
                outer.discard()
            }
            function.restore()
            project.restore()
            assertEquals(0, Project.warningCount)
            assertTrue(load.commands.isEmpty())
            assertTrue(appender.events.isEmpty())

            LogProcessor.beginDiagnosticCapture().use { capture ->
                Project.optimization()
                capture.commit()
            }
            val messages = appender.events.map { it.message.formattedMessage }
            assertEquals(
                listOf(
                    "Optimizing...",
                    "Adding scoreboards declare in mcfpp:load function",
                    "No valid entrance function in Project route_test",
                    "Complete compiling project route_test with [0] error and [1] warning"
                ),
                messages
            )
            assertEquals(listOf(Level.DEBUG, Level.DEBUG, Level.WARN, Level.INFO), appender.events.map { it.level })
            assertEquals(1, Project.warningCount)
            assertTrue(load.commands.isNotEmpty())
        } finally {
            function.restore()
            project.restore()
        }
    }

    @Test fun projectCaughtFileFailureStagesLoggerCounterAndOriginalStderrTraceTogether() {
        val originalConfig = Project.config
        val originalSourcePath = originalConfig.sourcePath
        withRouting { _, appender, order, stderr ->
            val root = Files.createTempDirectory("mcfpp-diagnostic-route")
            Project.config.sourcePath = root
            val missing = MCFPPFile(root.resolve("missing.mcfpp").toString())
            val files = Project::class.java.getDeclaredField("files").apply { isAccessible = true }
                .get(Project) as MutableList<MCFPPFile>
            files.add(missing)
            val project = ProjectStateJournal.checkpoint()
            val capture = LogProcessor.beginDiagnosticCapture()
            try {
                Project.indexType()
                assertEquals(1, Project.errorCount)
                assertTrue(appender.events.isEmpty())
                assertEquals("", stderr.text())
                capture.discard()
                project.restore()
                assertEquals(0, Project.errorCount)
                assertTrue(appender.events.isEmpty())
                assertEquals("", stderr.text())
                assertTrue(order.isEmpty())

                val next = LogProcessor.beginDiagnosticCapture()
                Project.indexType()
                next.commit()
                assertEquals(1, Project.errorCount)
                assertEquals(
                    listOf("Generate Type Index...", "Error while generate type index in file \"${missing}\""),
                    appender.events.map { it.message.formattedMessage }
                )
                assertEquals(listOf(Level.DEBUG, Level.ERROR), appender.events.map { it.level })
                assertTrue(stderr.text().contains("java.io.FileNotFoundException"))
                assertEquals(listOf("logger", "stderr"), order.distinct())
            } finally {
                capture.close()
                project.restore()
                files.clear()
                Files.deleteIfExists(root)
            }
        }
        assertSame(originalConfig, Project.config)
        assertSame(
            originalSourcePath,
            Project.config.sourcePath,
            "The deleted diagnostic fixture directory must not become the next compilation's source path"
        )
    }

    @Test fun missingJavaNativeTargetKeepsErrorCommentAndStackTraceOrderedAndDiscardable() = withRouting { _, appender, order, stderr ->
        val parser = mcfppParser(
            CommonTokenStream(
                mcfppLexer(
                    CharStreams.fromString(
                        "func absent() = no.such.NativeTarget.call"
                    )
                )
            )
        )
        parser.removeErrorListeners()
        val declaration = parser.compilationUnit().typeDeclaration(0).declarations().nativeFuncDeclaration()
        assertEquals(0, parser.numberOfSyntaxErrors)
        Project.ctx.clear()
        Project.ctx.addFirst(declaration)
        Function.currFunction = Function("native-route", "route_test", null)
        MCFPPFile.currFile = MCFPPFile(Namespace("route_test"))

        LogProcessor.beginDiagnosticCapture().use { capture ->
            MCFPPFieldVisitor().visitNativeFuncDeclaration(declaration)
            assertEquals(1, Project.errorCount)
            assertTrue(appender.events.isEmpty())
            assertEquals("", stderr.text())
            assertEquals(1, Function.currFunction.commands.size)
            capture.discard()
        }
        assertEquals(0, Project.errorCount)
        assertTrue(Function.currFunction.commands.isEmpty())
        assertTrue(appender.events.isEmpty())
        assertEquals("", stderr.text())

        LogProcessor.beginDiagnosticCapture().use { capture ->
            MCFPPFieldVisitor().visitNativeFuncDeclaration(declaration)
            capture.commit()
        }
        assertEquals(1, Project.errorCount)
        assertEquals(1, appender.events.size)
        assertTrue(appender.events.single().message.formattedMessage.contains("Cannot find java class: no.such.NativeTarget"))
        assertEquals("#Cannot find java class: no.such.NativeTarget", Function.currFunction.commands.single().toString())
        assertTrue(stderr.text().contains("java.lang.ClassNotFoundException: no.such.NativeTarget"))
        assertEquals(listOf("logger", "stderr"), order.distinct())
    }

    @Test fun rawProjectLogsStillHonorTheUnderlyingLog4jLevelWithoutExtraComments() = withRouting { logger, appender, _, _ ->
        Project.projectLoad = Function("load", "route_test", null)
        Project.config.rootNamespace = "route_test"
        Project.config.name = "route_test"
        CompileSettings.isLib = true
        logger.level = Level.ERROR
        var formattingCalls = 0
        val argument = object {
            override fun toString(): String {
                formattingCalls++
                return "disabled argument"
            }
        }
        LogProcessor.rawDebug(logger, "disabled {}", argument)
        LogProcessor.beginDiagnosticCapture().use { capture ->
            LogProcessor.rawDebug(logger, "disabled {}", argument)
            capture.commit()
        }
        assertEquals(0, formattingCalls)
        val commands = Project.projectLoad.commands
        Project.optimization()
        assertTrue(appender.events.isEmpty())
        assertEquals(0, Project.warningCount)
        assertEquals(
            listOf("#class init"),
            commands.filterIsInstance<top.mcfpp.command.Comment>().map { it.toString() }
        )
    }

    @Test fun entranceTagsAreFormattedBeforeMutationOrFunctionJournalRestoration() = withRouting { _, appender, _, _ ->
        val load = Function("load", "route_test", null)
        val entry = Function("entry", "route_test", null)
        val namespace = Namespace("route_test")
        namespace.scope.functions[entry.identifier] = arrayListOf(entry)
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        Project.projectLoad = load
        Project.config.rootNamespace = "route_test"
        Project.config.name = "route_test"
        Project.config.root = java.nio.file.Paths.get("route_test")
        CompileSettings.isLib = true
        assertTrue(entry.parent.isEmpty())
        val project = ProjectStateJournal.checkpoint()
        val function = FunctionStateJournal.checkpoint(listOf(load, entry))
        try {
            for (restoreWithJournal in listOf(false, true)) {
                function.restore()
                project.restore()
                appender.events.clear()
                entry.tags.add(FunctionTag("route_test", "entered"))
                val originalTags = entry.tags.toString()
                assertTrue(originalTags != "[]")
                val publication = LogProcessor.beginDiagnosticCapture().use { capture ->
                    Project.optimization()
                    assertTrue(appender.events.isEmpty())
                    capture.seal()
                }
                if (restoreWithJournal) function.restore() else entry.tags.clear()
                assertTrue(entry.tags.isEmpty())
                publication.publishLogs()
                assertEquals(
                    listOf(
                        "Optimizing...",
                        "Adding scoreboards declare in mcfpp:load function",
                        "Find entrance function: $originalTags entry",
                        "Complete compiling project route_test with [0] error and [0] warning"
                    ),
                    appender.events.map { it.message.formattedMessage }
                )
                assertEquals(listOf(Level.DEBUG, Level.DEBUG, Level.DEBUG, Level.INFO), appender.events.map { it.level })
                assertEquals(0, Project.errorCount)
                assertEquals(0, Project.warningCount)
            }
        } finally {
            function.restore()
            project.restore()
            GlobalScope.localNamespaces.remove(namespace.identifier)
        }
    }

    @Test fun rawThrowableIdentityAndCapturedStderrTextSurviveDeferredPublication() = withRouting { logger, appender, _, stderr ->
        val original = IllegalStateException("original failure")
        original.stackTrace = arrayOf(StackTraceElement("Original", "prepare", "Original.kt", 7))
        val originalTrace = original.stackTraceToString()
        val arguments = arrayListOf("before")
        val publication = LogProcessor.beginDiagnosticCapture().use { capture ->
            LogProcessor.rawError(logger, "failed {}", arguments, original)
            LogProcessor.captureStackTrace(original)
            assertTrue(appender.events.isEmpty())
            assertEquals("", stderr.text())
            capture.seal()
        }
        arguments.clear()
        original.stackTrace = arrayOf(StackTraceElement("Later", "restore", "Later.kt", 99))
        publication.publishLogs()
        val event = appender.events.single()
        assertEquals("failed [before]", event.message.formattedMessage)
        assertEquals(Level.ERROR, event.level)
        assertSame(original, event.thrown)
        assertEquals(originalTrace, stderr.text())
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }
}
