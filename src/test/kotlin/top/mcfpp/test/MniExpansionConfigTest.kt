package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.MacroExpansionConfig
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.Origin
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.test.util.writeProjectFixture
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as P

/**
 * Production wiring of [MacroExpansionConfig] into the live source macro pipeline.
 *
 * The tests compile original MCFPP source through [MCFPPImVisitor.compileFunctionBody] and use
 * real `@MNIMacro` providers from [MniSourceMacroCallTest.Entries]; no code tree is built by hand
 * for the source-call cases.
 */
class MniExpansionConfigTest {

    private class Fixture(source: String) {
        val namespace = Namespace("macro-config-source")
        val file = MCFPPFile(namespace)
        val caller: Function

        init {
            MniSourceMacroCallTest.Entries.hostCalls = 0
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            namespace.injectedBy(MniSourceMacroCallTest.Entries::class.java)
            val bodySource = "{\n$source\n}"
            val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(bodySource, "macro-config-source.mcfpp"))))
            val body = parser.curlBlock()
            assertEquals(0, parser.numberOfSyntaxErrors, bodySource)
            caller = Function("caller", namespace.identifier, body).apply {
                declarationFile = file
                returnType = MCFPPPrivateType.Void
            }
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
        }

        fun compile() {
            MCFPPFile.currFile = file
            Project.currNamespace = namespace.identifier
            Function.currFunction = caller
            MCFPPImVisitor().compileFunctionBody(caller.ast)
        }
    }

    private fun isolated(config: ProjectConfig = ProjectConfig(version = "26.3"), action: () -> Unit) {
        val before = CompilerStateJournal.checkpoint()
        try {
            Project.config = config
            Project.init()
            LogProcessor.level = LogLevel.DEBUG
            Project.projectLoad = Function("load", config.rootNamespace, null)
            MCFPPFile.currFile = null
            Function.forcedField = null
            action()
        } finally {
            before.restore()
        }
    }

    private fun diagnostics(action: () -> Unit): List<String> {
        val capture = LogProcessor.beginDiagnosticCapture()
        try {
            action()
            return capture.seal().diagnostics.map { it.message }
        } catch (failure: Throwable) {
            capture.discard()
            throw failure
        }
    }

    private fun span(offset: Int = 0) = SourceSpan("macro-config.mcfpp", offset, offset + 1, 1, offset + 1, 1, offset + 2)

    @Test
    fun configFileAndArgumentsCreateTheProductionBudget() = isolated {
        val defaults = Project.macroExpansionBudget()
        assertEquals(64, defaults.maxDepth)
        assertEquals(100_000, defaults.maxNodes)

        val root = Files.createTempDirectory("macro-pipe-config-")
        try {
            val file = root.resolve("project.toml")
            writeProjectFixture(
                file,
                """{"version":"26.3",
                "macroExpansion":{"maxDepth":2,"maxNodes":7,"dump":false},
                "args":["-macroMaxDepth=3","-macroMaxNodes=11","-dumpMacroExpansion=true"]}"""
            )
            val parsed = Project.readConfig(top.mcfpp.CompilerOptions().apply { this.file = file.toFile() })
            assertEquals(MacroExpansionConfig(3, 11, true), parsed.macroExpansion)
            assertEquals(0, Project.errorCount)

            Project.config = parsed
            Project.init()
            val budget = Project.macroExpansionBudget()
            assertEquals(3, budget.maxDepth)
            assertEquals(11, budget.maxNodes)
            assertEquals(0L, budget.usedNodes)
        } finally {
            Files.walk(root).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun sourceCallsShareTheCompilationBudgetAndOverflowIsNotRefunded() = isolated {
        val probe = Fixture("ignorePair(1, 2);")
        probe.compile()
        assertEquals(0, Project.errorCount)
        val probeBudget = Project.macroExpansionBudget()
        val perCall = probeBudget.usedNodes
        assertTrue(perCall > 0L)
        assertEquals(0, probeBudget.depth)

        Project.config.macroExpansion = MacroExpansionConfig(maxNodes = (perCall * 2 - 1).toInt())
        Project.init()
        val strict = Project.macroExpansionBudget()
        assertTrue(probeBudget !== strict)

        val first = Fixture("ignorePair(1, 2);")
        first.compile()
        assertEquals(0, Project.errorCount)
        assertEquals(perCall, strict.usedNodes)

        val second = Fixture("ignorePair(1, 2);")
        second.compile()
        assertTrue(Project.errorCount > 0, "The second call must observe the first call's nodes")
        assertTrue(strict.usedNodes > perCall, "A failed call must retain the nodes it already allocated")
        assertEquals(0, strict.depth, "Every expansion frame must be closed after a failure")
    }

    @Test
    fun configuredDepthAppliesToNestedSourceMacroCalls() = isolated(
        ProjectConfig(version = "26.3", macroExpansion = MacroExpansionConfig(maxDepth = 1))
    ) {
        val rejected = Fixture("ignorePair(ignorePair(1, 2), 3);")
        rejected.compile()
        assertTrue(Project.errorCount > 0, "A nested call must exceed a depth of one")
        assertEquals(
            0,
            MniSourceMacroCallTest.Entries.hostCalls,
            "The depth failure must happen before either macro host runs"
        )
        assertEquals(0, Project.macroExpansionBudget().depth)

        Project.config.macroExpansion = MacroExpansionConfig(maxDepth = 2)
        Project.init()
        val accepted = Fixture("ignorePair(ignorePair(1, 2), 3);")
        accepted.compile()
        assertEquals(0, Project.errorCount)
        assertEquals(2, MniSourceMacroCallTest.Entries.hostCalls)
        assertEquals(0, Project.macroExpansionBudget().depth)
    }

    @Test
    fun dumpFlagPrintsTheCanonicalTreeOnlyWhenEnabled() = isolated {
        val withoutDump = diagnostics { Fixture("ignorePair(1, 2);").compile() }
        assertTrue(withoutDump.none { it.contains("Macro expansion dump") })
        assertEquals(0, Project.errorCount)

        Project.config.macroExpansion = MacroExpansionConfig(dump = true)
        Project.init()
        val withDump = diagnostics { Fixture("ignorePair(1, 2);").compile() }
        val dumps = withDump.filter { it.contains("Macro expansion dump") }
        assertEquals(1, dumps.size)
        assertTrue(dumps.single().contains("literal"), dumps.single())
        assertTrue(dumps.single().contains("yield"), dumps.single())
        assertEquals(0, Project.errorCount)
    }

    @Test
    fun repeatedInitIsolatesCountsAndProjectJournalRestoresTheBudgetOwner() {
        val outer = CompilerStateJournal.checkpoint()
        try {
            Project.config = ProjectConfig(
                version = "26.3",
                macroExpansion = MacroExpansionConfig(maxDepth = 2, maxNodes = 5, dump = false)
            )
            Project.init()
            val first = Project.macroExpansionBudget()
            assertEquals(2, first.maxDepth)
            assertEquals(5, first.maxNodes)
            first.allocate(1, Origin(span()))
            assertEquals(1L, first.usedNodes)

            val generation = ProjectStateJournal.checkpoint()
            Project.config.macroExpansion = MacroExpansionConfig(maxDepth = 3, maxNodes = 11, dump = true)
            Project.init()
            val second = Project.macroExpansionBudget()
            assertTrue(first !== second)
            assertEquals(3, second.maxDepth)
            assertEquals(11, second.maxNodes)
            assertEquals(0L, second.usedNodes)

            // Even an unchanged configuration starts a new compilation-unit budget.
            Project.init()
            val third = Project.macroExpansionBudget()
            assertTrue(second !== third)
            assertEquals(0L, third.usedNodes)

            generation.restore()
            assertSame(first, Project.macroExpansionBudget())
            assertEquals(1L, first.usedNodes, "Restoring the owner must not refund consumed nodes")
            assertEquals(MacroExpansionConfig(2, 5, false), Project.config.macroExpansion)
        } finally {
            outer.restore()
        }
    }
}
