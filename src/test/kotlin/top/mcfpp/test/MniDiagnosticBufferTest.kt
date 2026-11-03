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
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Command
import top.mcfpp.command.CommandList
import top.mcfpp.command.Comment
import top.mcfpp.command.CommentLevel
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.expand.FunctionStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import kotlin.test.*
import kotlin.test.Test

class MniDiagnosticBufferTest {
    private class RecordingAppender :
        AbstractAppender(
            "mni-diagnostic-buffer",
            null,
            PatternLayout.createDefaultLayout(),
            false,
            Property.EMPTY_ARRAY
        ) {
        val events = arrayListOf<LogEvent>()
        override fun append(event: LogEvent) {
            events.add(event.toImmutable())
        }
    }

    private class Probe(val function: Function, val appender: RecordingAppender) {
        val events get() = appender.events
        val messages get() = events.map { it.message.formattedMessage }
        fun context(source: String = "func original() {}\n"): mcfppParser {
            val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
            parser.removeErrorListeners()
            val context = parser.compilationUnit()
            assertEquals(0, parser.numberOfSyntaxErrors)
            Project.ctx.clear()
            Project.ctx.addFirst(context)
            MCFPPFile.currFile = MCFPPFile(Namespace("diagnostics.original"))
            return parser
        }
    }

    private fun withProbe(action: Probe.() -> Unit) {
        Project.init()
        val oldLogger = LogProcessor.logger
        val oldLevel = LogProcessor.level
        val oldDebug = CompileSettings.isDebug
        val oldFunction = Function.currFunction
        val oldForced = Function.forcedField
        val oldFile = MCFPPFile.currFile
        val project = ProjectStateJournal.checkpoint()
        val logger = LogManager.getLogger("mni.diagnostic.buffer.tests") as org.apache.logging.log4j.core.Logger
        val level = logger.level
        val additive = logger.isAdditive
        val appender = RecordingAppender().apply { start() }
        logger.level = Level.ALL
        logger.isAdditive = false
        logger.addAppender(appender)
        LogProcessor.logger = logger
        LogProcessor.level = LogLevel.DEBUG
        CompileSettings.isDebug = false
        Project.config.commentLevel = CommentLevel.DEBUG
        val function = Function("diagnostics", "test", null)
        Function.currFunction = function
        try {
            Probe(function, appender).action()
        } finally {
            project.restore()
            Function.currFunction = oldFunction
            Function.forcedField = oldForced
            MCFPPFile.currFile = oldFile
            LogProcessor.logger = oldLogger
            LogProcessor.level = oldLevel
            CompileSettings.isDebug = oldDebug
            logger.removeAppender(appender)
            appender.stop()
            logger.level = level
            logger.isAdditive = additive
        }
    }

    @Test fun everyEntryPointBuffersItsRealLoggerOutputAndKeepsCompilerErrorsVisible() = withProbe {
        val parser = context()
        val failure = IllegalArgumentException("original exception")
        val capture = LogProcessor.beginDiagnosticCapture()
        try {
            LogProcessor.debug("debug")
            LogProcessor.debug("debug exception", failure)
            LogProcessor.info("info")
            LogProcessor.info("info exception", failure)
            LogProcessor.warn("warn")
            LogProcessor.warn("warn exception", failure)
            LogProcessor.error("error")
            LogProcessor.error("error exception", failure)
            LogProcessor.castError("left", "right")
            LogProcessor.syntaxError(parser, "bad syntax", 1, 5)
            assertTrue(events.isEmpty())
            assertEquals(2, Project.warningCount)
            assertEquals(4, Project.errorCount)
            assertTrue(MCFPPFile.currFile!!.syntaxError)
            capture.commit()
            assertEquals(
                listOf(
                    Level.DEBUG, Level.DEBUG, Level.INFO, Level.INFO, Level.WARN,
                    Level.WARN, Level.ERROR, Level.ERROR, Level.ERROR, Level.ERROR
                ),
                events.map { it.level }
            )
            listOf(1, 3, 5, 7).forEach { assertSame(failure, events[it].thrown) }
            assertTrue(messages[8].contains("Cannot cast [left] to [right]"))
            assertTrue(messages[9].contains("1 | func original() {}\n  |      ^"))
        } finally {
            capture.close()
        }
    }

    @Test fun discardRemovesOnlyDiagnosticDeltasAndIdentityOwnedComments() = withProbe {
        context()
        val sameText = Comment("#bad", CommentLevel.ERROR)
        function.commands.add(sameText)
        val capture = LogProcessor.beginDiagnosticCapture()
        LogProcessor.error("bad")
        LogProcessor.warn("warning")
        function.commands.add(Command("say native mutation"))
        Project.errorCount += 7
        capture.discard()
        capture.close()
        assertEquals(7, Project.errorCount)
        assertEquals(0, Project.warningCount)
        assertSame(sameText, function.commands.first())
        assertEquals(listOf("#bad", "say native mutation"), function.commands.map { it.toString() })
        assertTrue(events.isEmpty())
    }

    @Test fun nestedSuccessfulCaptureTransfersEffectsAndLogsToItsParent() = withProbe {
        context()
        val outer = LogProcessor.beginDiagnosticCapture()
        LogProcessor.debug("first")
        LogProcessor.beginDiagnosticCapture().use { inner ->
            LogProcessor.warn("child")
            inner.commit()
        }
        LogProcessor.info("last")
        assertEquals(1, Project.warningCount)
        assertTrue(events.isEmpty())
        outer.discard()
        assertEquals(0, Project.warningCount)
        assertTrue(function.commands.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test fun discardedChildDoesNotDiscardParentDiagnosticsAndOrderIsPreserved() = withProbe {
        context()
        LogProcessor.beginDiagnosticCapture().use { outer ->
            LogProcessor.debug("first")
            LogProcessor.beginDiagnosticCapture().use { inner ->
                LogProcessor.info("second")
                inner.commit()
            }
            LogProcessor.warn("third")
            LogProcessor.beginDiagnosticCapture().use { LogProcessor.error("discarded") }
            LogProcessor.info("fourth")
            assertEquals(0, Project.errorCount)
            assertEquals(1, Project.warningCount)
            outer.commit()
        }
        assertEquals(4, events.size)
        assertEquals("first", messages[0])
        assertEquals("second", messages[1])
        assertTrue(messages[2].contains("third"))
        assertEquals("fourth", messages[3])
        assertEquals(listOf("#third"), function.commands.map { it.toString() })
    }

    @Test fun sourceAndOriginalExceptionsAreCapturedBeforeContextChanges() = withProbe {
        context()
        val file = MCFPPFile.currFile!!
        val failure = IllegalStateException("original")
        val capture = LogProcessor.beginDiagnosticCapture()
        LogProcessor.error("contextual", failure)
        val publication = capture.seal()
        Project.ctx.clear()
        MCFPPFile.currFile = null
        val diagnostic = publication.diagnostics.single()
        assertSame(failure, diagnostic.originalThrowable)
        assertEquals(file.absolutePath, diagnostic.source!!.file)
        assertEquals(1, diagnostic.source.line)
        publication.publishLogs()
        assertSame(failure, events.single().thrown)
        assertTrue(messages.single().contains("func original() {}"))
        assertTrue(messages.single().contains(file.absolutePath))
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (publication.diagnostics as MutableList<Any>).clear()
        }
    }

    @Test fun noContextExceptionFormattingRetainsTheLegacyLoggerOverload() = withProbe {
        val failure = IllegalStateException("retained")
        val capture = LogProcessor.beginDiagnosticCapture()
        LogProcessor.warn("warning", failure)
        LogProcessor.error("error", failure)
        val publication = capture.seal()
        assertTrue(publication.diagnostics.all { it.originalThrowable === failure })
        publication.publishLogs()
        assertTrue(events.all { it.thrown == null })
        assertTrue(messages[0].startsWith("warning\njava.lang.IllegalStateException: retained"))
        assertEquals(listOf("#warning", "#error"), function.commands.map { it.toString() })
    }

    @Test fun afterStateIsCapturedBeforeSealAndPublicationCannotDoubleCountOrAppendComments() = withProbe {
        context()
        val beforeProject = ProjectStateJournal.checkpoint()
        val beforeFunction = FunctionStateJournal.checkpoint(listOf(function))
        val commands = function.commands
        val capture = LogProcessor.beginDiagnosticCapture()
        function.commands.add(Command("say before"))
        LogProcessor.warn("between")
        function.commands.add(Command("say after"))
        val afterProject = beforeProject.captureCurrent()
        val afterFunction = beforeFunction.captureCurrent()
        val publication = capture.seal()
        beforeFunction.restore()
        beforeProject.restore()
        assertSame(commands, function.commands)
        assertTrue(commands.isEmpty())
        assertEquals(0, Project.warningCount)
        assertTrue(events.isEmpty())
        afterProject.restore()
        afterFunction.restore()
        publication.publishLogs()
        assertSame(commands, function.commands)
        assertEquals(listOf("say before", "#between", "say after"), commands.map { it.toString() })
        assertEquals(1, Project.warningCount)
        assertEquals(1, events.size)
        assertFailsWith<IllegalStateException> { publication.publishLogs() }
        assertFailsWith<IllegalStateException> { capture.commit() }
        capture.close()
        assertEquals(1, Project.warningCount)
        assertEquals(3, commands.size)
        assertEquals(1, events.size)
    }

    @Test fun failedPreparationComposesWithRealJournalsWithoutLeakingCommentsOrFlags() = withProbe {
        val parser = context()
        val file = MCFPPFile.currFile!!
        val project = ProjectStateJournal.checkpoint()
        val functions = FunctionStateJournal.checkpoint(listOf(function))
        var calls = 0
        val failure = IllegalStateException("prepare failed")
        val caught = assertFailsWith<IllegalStateException> {
            LogProcessor.beginDiagnosticCapture().use {
                try {
                    calls++
                    function.commands.add(Command("say native work"))
                    LogProcessor.warn("unpublished")
                    LogProcessor.syntaxError(parser, "bad", 1, 0)
                    assertEquals(1, Project.errorCount)
                    throw failure
                } finally {
                    // Discard diagnostic deltas before restoring the surrounding snapshots.
                    it.discard()
                    functions.restore()
                    project.restore()
                }
            }
        }
        assertSame(failure, caught)
        assertEquals(1, calls)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
        assertFalse(file.syntaxError)
        assertTrue(function.commands.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test fun discardFollowsCommentsAcrossFunctionsAndReplacedCommandLists() = withProbe {
        context()
        val first = function.commands
        val second = Function("other", "test", null)
        val capture = LogProcessor.beginDiagnosticCapture()
        LogProcessor.warn("first")
        val diagnostic = first.single()
        function.commands = CommandList().apply {
            add(diagnostic)
            add(Command("say keep"))
        }
        Function.currFunction = second
        LogProcessor.error("second")
        second.commands.removeAt(0) // Already detached comments must not remove another command.
        val keep = Comment("#second", CommentLevel.ERROR)
        second.commands.add(keep)
        capture.discard()
        assertTrue(first.isEmpty())
        assertEquals(listOf("say keep"), function.commands.map { it.toString() })
        assertSame(keep, second.commands.single())
        assertSame(second, Function.currFunction)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }

    @Test fun syntaxFlagsRespectNestedOwnershipAndPreviouslyFailedFiles() = withProbe {
        val parser = context()
        val file = MCFPPFile.currFile!!
        LogProcessor.beginDiagnosticCapture().use { outer ->
            LogProcessor.syntaxError(parser, "outer", 1, 0)
            LogProcessor.beginDiagnosticCapture().use { LogProcessor.syntaxError(parser, "inner", 1, 1) }
            assertTrue(file.syntaxError)
            assertEquals(1, Project.errorCount)
            outer.discard()
        }
        assertFalse(file.syntaxError)
        assertEquals(0, Project.errorCount)
        file.syntaxError = true
        LogProcessor.beginDiagnosticCapture().use { LogProcessor.syntaxError(parser, "again", 1, 2) }
        assertTrue(file.syntaxError)
        assertTrue(events.isEmpty())
    }

    @Test fun normalLoggingAndLegacyExceptionCommentsAreUnchanged() = withProbe {
        context()
        val failure = IllegalStateException("legacy")
        LogProcessor.warn("warning", failure)
        LogProcessor.error("error", failure)
        assertEquals(1, Project.warningCount)
        assertEquals(1, Project.errorCount)
        assertEquals(listOf("#warning", "#warning", "#error", "#error"), function.commands.map { it.toString() })
        assertEquals(
            listOf(CommentLevel.ERROR, CommentLevel.WARN, CommentLevel.ERROR, CommentLevel.ERROR),
            function.commands.map { (it as Comment).level }
        )
        assertEquals(2, events.size)
        assertTrue(events.all { it.thrown === failure })
    }

    @Test fun logLevelFilteringAndSyntaxErrorsKeepTheirExistingSemantics() = withProbe {
        val parser = context()
        LogProcessor.level = LogLevel.NONE
        LogProcessor.beginDiagnosticCapture().use { capture ->
            LogProcessor.debug("hidden")
            LogProcessor.info("hidden")
            LogProcessor.warn("hidden")
            LogProcessor.error("hidden")
            LogProcessor.castError("hidden", "hidden")
            LogProcessor.syntaxError(parser, "syntax still counts", 1, 0)
            assertEquals(3, Project.errorCount)
            assertEquals(1, Project.warningCount)
            assertEquals(listOf(CommentLevel.WARN, CommentLevel.ERROR, CommentLevel.ERROR),
                function.commands.map { (it as Comment).level })
            assertTrue(MCFPPFile.currFile!!.syntaxError)
            capture.commit()
        }
        assertTrue(events.isEmpty())
    }

    @Test fun debugStackTracesAreBufferedAtTheDiagnosticCallSite() = withProbe {
        CompileSettings.isDebug = true
        LogProcessor.beginDiagnosticCapture().use { capture ->
            LogProcessor.warn("warning")
            LogProcessor.error("error")
            assertTrue(events.isEmpty())
            capture.commit()
        }
        assertEquals(4, events.size)
        assertTrue(messages[1].startsWith("Compiler Stack trace:"))
        assertTrue(messages[3].startsWith("Compiler Stack trace:"))
        assertFalse(messages[1].contains("Delivery.publish"))
        assertEquals(1, Project.warningCount)
        assertEquals(1, Project.errorCount)
    }

    @Test fun lifecycleRejectsOutOfOrderFinishAndMakesCloseIdempotent() = withProbe {
        val outer = LogProcessor.beginDiagnosticCapture()
        val inner = LogProcessor.beginDiagnosticCapture()
        assertFailsWith<IllegalStateException> { outer.commit() }
        assertFailsWith<IllegalStateException> { outer.discard() }
        inner.discard()
        inner.close()
        outer.commit()
        outer.close()
        assertFailsWith<IllegalStateException> { outer.commit() }
        assertFailsWith<IllegalStateException> { outer.seal() }
        assertTrue(events.isEmpty())
    }

    @Test fun detachedPublicationJoinsAnActiveCaptureAndIsConsumedEvenIfThatCaptureDiscards() = withProbe {
        val capture = LogProcessor.beginDiagnosticCapture()
        LogProcessor.info("saved")
        val publication = capture.seal()
        LogProcessor.beginDiagnosticCapture().use {
            publication.publishLogs()
            assertTrue(events.isEmpty())
        }
        capture.close()
        assertTrue(events.isEmpty())
        assertFailsWith<IllegalStateException> { publication.publishLogs() }
    }

    @Test fun nestedStagedAfterStatePublishesAtApplyTimeAndOuterSuccessPublishesOnce() = withProbe {
        context()
        LogProcessor.beginDiagnosticCapture().use { outer ->
            LogProcessor.info("outer before")
            function.commands.add(Command("say before"))
            val beforeProject = ProjectStateJournal.checkpoint()
            val beforeFunction = FunctionStateJournal.checkpoint(listOf(function))
            val inner = LogProcessor.beginDiagnosticCapture()
            LogProcessor.warn("inner warning")
            LogProcessor.error("inner error")
            val afterProject = beforeProject.captureCurrent()
            val afterFunction = beforeFunction.captureCurrent()
            val publication = inner.seal()
            inner.close()
            beforeFunction.restore()
            beforeProject.restore()
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
            assertEquals(listOf("say before"), function.commands.map { it.toString() })
            LogProcessor.info("inner prepared")
            afterProject.restore()
            afterFunction.restore()
            publication.publishLogs()
            assertTrue(events.isEmpty())
            assertEquals(1, Project.errorCount)
            assertEquals(1, Project.warningCount)
            assertFailsWith<IllegalStateException> { publication.publishLogs() }
            function.commands.add(Command("say after"))
            LogProcessor.info("outer after")
            outer.commit()
        }
        assertEquals(5, events.size)
        assertEquals("outer before", messages[0])
        assertEquals("inner prepared", messages[1])
        assertTrue(messages[2].contains("inner warning"))
        assertTrue(messages[3].contains("inner error"))
        assertEquals("outer after", messages[4])
        assertEquals(1, Project.errorCount)
        assertEquals(1, Project.warningCount)
        assertEquals(
            listOf("say before", "#inner warning", "#inner error", "say after"),
            function.commands.map { it.toString() }
        )
    }

    @Test fun nestedStagedApplyIsStillInvisibleWhenTheOuterCompilerTransactionFails() = withProbe {
        context()
        val rootProject = ProjectStateJournal.checkpoint()
        val rootFunction = FunctionStateJournal.checkpoint(listOf(function))
        val failure = IllegalStateException("outer prepare failed")
        val actual = assertFailsWith<IllegalStateException> {
            LogProcessor.beginDiagnosticCapture().use { outer ->
                try {
                    LogProcessor.warn("outer warning")
                    val innerProject = ProjectStateJournal.checkpoint()
                    val innerFunction = FunctionStateJournal.checkpoint(listOf(function))
                    val inner = LogProcessor.beginDiagnosticCapture()
                    LogProcessor.warn("inner warning")
                    LogProcessor.error("inner error")
                    function.commands.add(Command("say inner mutation"))
                    val afterProject = innerProject.captureCurrent()
                    val afterFunction = innerFunction.captureCurrent()
                    val publication = inner.seal()
                    innerFunction.restore()
                    innerProject.restore()
                    afterProject.restore()
                    afterFunction.restore()
                    publication.publishLogs()
                    assertEquals(2, Project.warningCount)
                    assertEquals(1, Project.errorCount)
                    assertTrue(events.isEmpty())
                    throw failure
                } finally {
                    // Sealed effects belong to these journals; discard only owns direct diagnostics.
                    outer.discard()
                    rootFunction.restore()
                    rootProject.restore()
                }
            }
        }
        assertSame(failure, actual)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
        assertTrue(function.commands.isEmpty())
        assertTrue(events.isEmpty())
    }
}
