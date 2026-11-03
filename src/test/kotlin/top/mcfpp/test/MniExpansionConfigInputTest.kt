package top.mcfpp.test

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import top.mcfpp.CompileSettings
import top.mcfpp.MacroExpansionConfig
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.function.Function
import top.mcfpp.newExpansionBudget
import top.mcfpp.parseCompilerArgs
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.logging.log4j.core.Logger as CoreLogger
import top.mcfpp.runCompiler
import top.mcfpp.test.util.writeProjectFixture

class MniExpansionConfigInputTest {
    private lateinit var cliInput: Path
    private fun runProjectCompiler(arguments: Array<String>): Int = runCompiler(arrayOf("build", "--project", *arguments))

    /** Exercise the current invocation API; keep the old behavioral assertions visible. */
    private fun parseArgs(arguments: List<String>) {
        Project.config = Project.readConfig(parseCompilerArgs((listOf(cliInput.toString()) + arguments).toTypedArray()))
    }
    private class Recorder :
        AbstractAppender(
            "macro-config-input",
            null,
            PatternLayout.createDefaultLayout(),
            false,
            Property.EMPTY_ARRAY
        ) {
        val events = arrayListOf<LogEvent>()
        override fun append(event: LogEvent) {
            events += event.toImmutable()
        }
        fun errors() = events.filter { it.level == Level.ERROR }.map { it.message.formattedMessage }
    }

    private class Inputs(val root: Path, val recorder: Recorder) {
        private var serial = 0
        fun json(text: String): String = root.resolve("config${serial++}.toml").also { writeProjectFixture(it, text) }.toString()
        fun read(text: String) = Project.readConfig(top.mcfpp.CompilerOptions().apply { file = java.io.File(json(text)) })
    }

    private fun isolated(action: (Inputs) -> Unit) {
        // Initialize the production INIT listener before saving stageProcessor relationships;
        // restoring a pre-initialization snapshot would otherwise remove the singleton's hook.
        ExpansionEnvironment(Types())
        val project = ProjectStateJournal.checkpoint()
        val oldFunction = Function.currFunction
        val oldFile = MCFPPFile.currFile
        val oldLogger = LogProcessor.logger
        val oldLogLevel = LogProcessor.level
        val oldFlags = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib, CompileSettings.printAll)
        val oldInline = CompileSettings.maxWhileInline
        val context = LogManager.getContext(false) as LoggerContext
        val configuration = context.configuration
        val logger = LogManager.getLogger("mcfpp") as CoreLogger
        val level = logger.level
        val additive = logger.isAdditive
        val recorder = Recorder().apply { start() }
        val root = Files.createTempDirectory("mni-expansion-config-")
        cliInput = root.resolve("cli.toml").also { writeProjectFixture(it, "{}") }
        logger.level = Level.ALL
        logger.isAdditive = false
        logger.addAppender(recorder)
        Project.config = ProjectConfig()
        Project.ctx.clear()
        Project.errorCount = 0
        Project.warningCount = 0
        Function.currFunction = Function.nullFunction
        MCFPPFile.currFile = null
        CompileSettings.isDebug = false
        CompileSettings.ignoreStdLib = false
        CompileSettings.isLib = false
        CompileSettings.printAll = false
        CompileSettings.maxWhileInline = 32
        LogProcessor.logger = logger
        LogProcessor.level = LogLevel.DEBUG
        try {
            action(Inputs(root, recorder))
        } finally {
            project.restore()
            Function.currFunction = oldFunction
            MCFPPFile.currFile = oldFile
            CompileSettings.isDebug = oldFlags[0]
            CompileSettings.ignoreStdLib = oldFlags[1]
            CompileSettings.isLib = oldFlags[2]
            CompileSettings.printAll = oldFlags[3]
            CompileSettings.maxWhileInline = oldInline
            if (context.configuration !== configuration) context.start(configuration)
            logger.removeAppender(recorder)
            recorder.stop()
            logger.level = level
            logger.isAdditive = additive
            LogProcessor.logger = oldLogger
            LogProcessor.level = oldLogLevel
            Files.walk(root).use { files -> files.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    private class Types : TypeService {
        private val int = ImmutableTypeRef<IntT>(TypeKey(1219, 1), "int", kind = TypeKind.INT)
        private val bool = ImmutableTypeRef<BoolT>(TypeKey(1219, 2), "bool", kind = TypeKind.BOOL)
        private val float = ImmutableTypeRef<FloatT>(TypeKey(1219, 3), "float", kind = TypeKind.FLOAT)
        private val string = ImmutableTypeRef<StringT>(TypeKey(1219, 4), "string", kind = TypeKind.STRING)
        private val void = ImmutableTypeRef<VoidT>(TypeKey(1219, 5), "void", kind = TypeKind.VOID)
        override fun intType(): TypeRef<IntT> = int
        override fun boolType(): TypeRef<BoolT> = bool
        override fun floatType(): TypeRef<FloatT> = float
        override fun stringType(): TypeRef<StringT> = string
        override fun voidType(): TypeRef<VoidT> = void
        override fun lookup(name: String): TypeRef<*> = error("No lookup needed")
        override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("No lookup needed")
    }

    private val span = SourceSpan("config-input.mcfpp", 0, 1, 1, 1, 1, 2)
    private fun transaction() = CompilerTransaction(
        CompilerTransactionBackend {
            object : PreparedCompilerCommit {
                override fun apply() = Unit
                override fun rollback() = Unit
            }
        }
    )

    @Test fun emptyJsonAndStandaloneArgumentsUseFreshDefaults() = isolated { inputs ->
        assertEquals(MacroExpansionConfig(64, 100_000, false), inputs.read("{}").macroExpansion)
        parseArgs(listOf("-macroMaxDepth=3", "-macroMaxNodes=7", "-dumpMacroExpansion"))
        assertEquals(MacroExpansionConfig(3, 7, true), Project.config.macroExpansion)
        parseArgs(emptyList())
        assertEquals(MacroExpansionConfig(), Project.config.macroExpansion)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }

    @Test fun jsonSettingsDriveActualNodeAndNestedSessionLimits() = isolated { inputs ->
        Project.config = inputs.read("""{"macroExpansion":{"maxDepth":1,"maxNodes":2,"dump":true}}""")
        assertEquals(MacroExpansionConfig(1, 2, true), Project.config.macroExpansion)
        val budget = Project.config.newExpansionBudget()
        val site = ExpansionSite(span, "input::nodes()->void")
        ExpansionSession(site, budget, ExpansionEnvironment(Types()), transaction()).use { session ->
            session.context().quote().block(Consumer { it.intLiteral(1) })
            assertEquals(2L, budget.usedNodes)
            val failure = assertFailsWith<ExpansionFailure> { session.context().quote().block(Consumer { it.intLiteral(2) }) }
            assertTrue(failure.diagnostic.message.contains("2 generated nodes"))
        }
        assertEquals(3L, budget.usedNodes)
        val fresh = Project.config.newExpansionBudget()
        assertEquals(0L, fresh.usedNodes)
        ExpansionSession(site, fresh, ExpansionEnvironment(Types()), transaction()).use { session ->
            val failure = assertFailsWith<ExpansionFailure> { session.child(ExpansionSite(span, "input::nested()->void")) }
            assertTrue(failure.diagnostic.message.contains("depth 1"))
        }
        assertEquals(0, fresh.depth)
        assertEquals(0, Project.errorCount)
    }

    @Test fun configArgumentsOverrideJsonWithoutMutatingThePreviousProject() = isolated { inputs ->
        val previous = Project.config
        previous.macroExpansion = MacroExpansionConfig(9, 99, false)
        val next = inputs.read(
            """{"macroExpansion":{"maxDepth":2,"maxNodes":8,"dump":false},
            "args":["-macroMaxDepth=4","-dumpMacroExpansion=true","-maxWhileInline=6"]}"""
        )
        assertSame(previous, Project.config)
        assertEquals(MacroExpansionConfig(9, 99, false), previous.macroExpansion)
        assertEquals(MacroExpansionConfig(4, 8, true), next.macroExpansion)
        assertEquals(6, CompileSettings.maxWhileInline)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }

    private class StopBeforeCompile : RuntimeException()

    @Test fun actualCompilerInvocationAppliesExplicitCliOptionsOverFileArguments() = isolated { inputs ->
        // Observe the effective invocation before its session restores the caller's settings.
        val seen = arrayListOf<ProjectConfig>()
        val seenInline = arrayListOf<Int>()
        val seenLevels = arrayListOf<LogLevel>()
        val seenFlags = arrayListOf<List<Boolean>>()
        val seenErrors = arrayListOf<Int>()
        Project.stageProcessor[Project.CompileStage.PRE_INIT.ordinal].clear()
        Project.stageProcessor[Project.CompileStage.PRE_INIT.ordinal].add {
            seen += Project.config
            seenInline += CompileSettings.maxWhileInline
            seenLevels += LogProcessor.level
            seenFlags += listOf(CompileSettings.ignoreStdLib, CompileSettings.isLib, CompileSettings.printAll)
            seenErrors += Project.errorCount
            throw StopBeforeCompile()
        }
        val first = inputs.json(
            """{"macroExpansion":{"maxDepth":2,"maxNodes":20,"dump":false},
            "args":["-macroMaxDepth=3","-macroMaxNodes=5","-dumpMacroExpansion","-maxWhileInline=7","-level=INFO"]}"""
        )
        assertEquals(1,
            runProjectCompiler(
                arrayOf(
                    first,
                    "-macroMaxDepth=4",
                    "-dumpMacroExpansion=false",
                    "-maxWhileInline=19",
                    "-level=DEBUG",
                    "-ignoreStdLib",
                    "-isLib",
                    "-printAll"
                )
            )
        )
        assertEquals(1, seen.size)
        assertEquals(0, seenErrors[0])
        assertEquals(1, Project.errorCount, "The deliberate startup exception is counted once")
        assertEquals(MacroExpansionConfig(4, 5, false), seen[0].macroExpansion)
        assertEquals(19, seenInline[0])
        assertEquals(LogLevel.DEBUG, seenLevels[0])
        assertTrue(seenFlags[0][0])
        assertTrue(seenFlags[0][1])
        assertTrue(seenFlags[0][2])
        val second = inputs.json("{}")
        assertEquals(1, runProjectCompiler(arrayOf(second)))
        assertEquals(2, seen.size)
        assertEquals(0, seenErrors[1])
        assertEquals(MacroExpansionConfig(), seen[1].macroExpansion)
        assertEquals(MacroExpansionConfig(4, 5, false), seen[0].macroExpansion)
        assertEquals(1, Project.errorCount, "The next invocation starts fresh and counts its own startup exception")
    }

    @Test fun sequentialConfigReadsDoNotConsumeEarlierCliOrConfigArgs() = isolated { inputs ->
        parseArgs(listOf("-macroMaxDepth=1", "-macroMaxNodes=2", "-dumpMacroExpansion"))
        val first = inputs.read("""{"args":["-macroMaxNodes=3","-dumpMacroExpansion"]}""")
        val second = inputs.read("""{"macroExpansion":{"maxDepth":5}}""")
        val third = inputs.read("{}")
        assertEquals(MacroExpansionConfig(64, 3, true), first.macroExpansion)
        assertEquals(MacroExpansionConfig(5, 100_000, false), second.macroExpansion)
        assertEquals(MacroExpansionConfig(), third.macroExpansion)
        assertEquals(MacroExpansionConfig(1, 2, true), Project.config.macroExpansion)
        assertEquals(0, Project.errorCount)
    }

    @Test fun cliNodeLimitsAreUsedByTheCurrentConfigAndBudgetsRemainIndependent() = isolated {
        parseArgs(listOf("-macroMaxDepth=2", "-macroMaxNodes=1", "-dumpMacroExpansion=true"))
        val budget = Project.config.newExpansionBudget()
        val next = Project.config.newExpansionBudget()
        val failure = assertFailsWith<ExpansionFailure> { budget.allocate(2, Origin(span)) }
        assertTrue(failure.diagnostic.message.contains("1 generated nodes"))
        assertEquals(2L, budget.usedNodes)
        assertEquals(0L, next.usedNodes)
        val outer = next.enter(ExpansionFrame("outer", span))
        val inner = next.enter(ExpansionFrame("inner", span))
        try {
            assertTrue(assertFailsWith<ExpansionFailure> { next.enter(ExpansionFrame("third", span)) }.diagnostic.message.contains("depth 2"))
        } finally {
            inner.close()
            outer.close()
        }
        assertEquals(0, next.depth)
    }

    @Test fun invalidJsonLimitsEachProduceOneSpecificDiagnosticAndKeepValidDefaults() = isolated { inputs ->
        for (value in listOf("0", "-1", "2147483648", "99999999999999999999999999", "1.5", "\"3\"", "true", "null")) {
            for (name in listOf("maxDepth", "maxNodes")) {
                val errors = Project.errorCount
                val messages = inputs.recorder.errors().size
                val result = inputs.read("""{"macroExpansion":{"$name":$value}}""")
                assertEquals(errors + 1, Project.errorCount, "$name=$value")
                val added = inputs.recorder.errors().drop(messages)
                assertEquals(1, added.size, "$name=$value")
                assertTrue(added.single().contains("macroExpansion.$name"))
                assertTrue(added.single().contains("positive Int"))
                assertEquals(MacroExpansionConfig(), result.macroExpansion)
            }
        }
        assertEquals(0, Project.warningCount)
    }

    @Test fun invalidCliLimitsDoNotThrowOrBecomePendingOptionsForTheNextCall() = isolated { inputs ->
        for (option in listOf("-macroMaxDepth", "-macroMaxNodes")) {
            for (value in listOf("0", "-1", "2147483648", "99999999999999999999999", "1.0", "abc", "", " 2", "2=3")) {
                val errors = Project.errorCount
                val messages = inputs.recorder.errors().size
                parseArgs(listOf("$option=$value"))
                assertEquals(errors + 1, Project.errorCount, "$option=$value")
                assertEquals(MacroExpansionConfig(), Project.config.macroExpansion)
                val added = inputs.recorder.errors().drop(messages)
                assertEquals(1, added.size)
                assertTrue(added.single().contains(option))
                assertTrue(added.single().contains("positive Int"))
                parseArgs(emptyList())
                assertEquals(MacroExpansionConfig(), Project.config.macroExpansion)
            }
            val errors = Project.errorCount
            parseArgs(listOf(option))
            assertEquals(errors + 1, Project.errorCount)
        }
        assertEquals(0, Project.warningCount)
    }

    @Test fun malformedDumpAndMacroObjectShapesHaveExplicitDiagnostics() = isolated { inputs ->
        for (value in listOf("null", "[]", "true", "\"limits\"", "4")) {
            val errors = Project.errorCount
            assertEquals(MacroExpansionConfig(), inputs.read("""{"macroExpansion":$value}""").macroExpansion)
            assertEquals(errors + 1, Project.errorCount)
            assertTrue(inputs.recorder.errors().last().contains("macroExpansion: expected an object"))
        }
        for (value in listOf("null", "1", "\"true\"", "[]")) {
            val errors = Project.errorCount
            assertFalse(inputs.read("""{"macroExpansion":{"dump":$value}}""").macroExpansion.dump)
            assertEquals(errors + 1, Project.errorCount)
            assertTrue(inputs.recorder.errors().last().contains("macroExpansion.dump: expected a boolean"))
        }
        for (value in listOf("", "TRUE", "yes", "1", "false=true")) {
            val errors = Project.errorCount
            parseArgs(listOf("-dumpMacroExpansion=$value"))
            assertEquals(errors + 1, Project.errorCount)
            assertFalse(Project.config.macroExpansion.dump)
            assertTrue(inputs.recorder.errors().last().contains("expected true or false"))
        }
    }

    @Test fun repeatedValidOptionsUseTheirLastValueAndInvalidOnesDoNotEraseIt() = isolated { inputs ->
        parseArgs(
            listOf(
                "-macroMaxDepth=2",
                "-macroMaxDepth=4",
                "-macroMaxDepth=0",
                "-macroMaxNodes=3",
                "-macroMaxNodes=5",
                "-dumpMacroExpansion",
                "-dumpMacroExpansion=false",
                "-dumpMacroExpansion=invalid"
            )
        )
        assertEquals(MacroExpansionConfig(4, 5, false), Project.config.macroExpansion)
        assertEquals(2, Project.errorCount)
        assertEquals(2, inputs.recorder.errors().size)
        parseArgs(emptyList())
        assertEquals(MacroExpansionConfig(), Project.config.macroExpansion)
    }

    @Test fun intMaximumAndExplicitFalseAreAcceptedWithoutJsonCoercion() = isolated { inputs ->
        val limits = inputs.read("""{"macroExpansion":{"maxDepth":2147483647,"maxNodes":2147483647,"dump":false}}""").macroExpansion
        assertEquals(MacroExpansionConfig(Int.MAX_VALUE, Int.MAX_VALUE, false), limits)
        parseArgs(listOf("-macroMaxDepth=2147483647", "-macroMaxNodes=2147483647", "-dumpMacroExpansion=false"))
        assertEquals(limits, Project.config.macroExpansion)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }

    @Test fun oldStandaloneFlagsAndUnknownArgumentWarningsKeepTheirBehavior() = isolated { inputs ->
        parseArgs(listOf("-debug", "-ignoreStdLib", "-isLib", "-maxWhileInline=12", "-printAll", "-level=INFO", "-oldUnknown"))
        assertTrue(CompileSettings.isDebug)
        assertTrue(CompileSettings.ignoreStdLib)
        assertTrue(CompileSettings.isLib)
        assertTrue(CompileSettings.printAll)
        assertEquals(12, CompileSettings.maxWhileInline)
        assertEquals(LogLevel.INFO, LogProcessor.level)
        assertEquals(0, Project.errorCount)
        assertEquals(1, Project.warningCount)
        assertTrue(inputs.recorder.events.any { it.level == Level.WARN && it.message.formattedMessage.contains("Invalid argument: -oldUnknown") })
        assertEquals(MacroExpansionConfig(), Project.config.macroExpansion)
    }

    @Test fun disablingLoggerOutputCannotTurnInvalidLimitsIntoSuccessfulInput() = isolated { inputs ->
        parseArgs(listOf("-level=NONE", "-macroMaxDepth=0", "-dumpMacroExpansion=bad"))
        assertEquals(2, Project.errorCount)
        assertEquals(MacroExpansionConfig(), Project.config.macroExpansion)
        assertTrue(inputs.recorder.errors().isEmpty(), "Retain the requested logger filtering")
        inputs.read("""{"macroExpansion":{"maxNodes":0}}""")
        assertEquals(3, Project.errorCount)
        assertTrue(inputs.recorder.errors().isEmpty())
    }
}
