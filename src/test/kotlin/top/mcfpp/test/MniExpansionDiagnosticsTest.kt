package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.MacroExpansionConfig
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.CodeArena
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.mni.expand.ExpansionDiagnostic
import top.mcfpp.mni.expand.ExpansionDiagnostics
import top.mcfpp.mni.expand.ExpansionFailure
import top.mcfpp.mni.expand.ExpansionFrame
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as P

/**
 * End-to-end diagnostics and canonical dump for the real source macro pipeline.
 *
 * The fixtures compile original MCFPP source through [MCFPPImVisitor.compileFunctionBody] with the
 * production [MniSourceMacroCallTest.Entries] providers, so depth/node failures, nested host
 * exceptions and the configured dump all travel through [top.mcfpp.mni.expand.MacroCallPipeline].
 */
class MniExpansionDiagnosticsTest {

    private class Fixture(source: String) {
        val namespace = Namespace("macro-diagnostics-source")
        val file = MCFPPFile(namespace)
        val caller: Function

        init {
            MniSourceMacroCallTest.Entries.hostCalls = 0
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            namespace.injectedBy(MniSourceMacroCallTest.Entries::class.java)
            val bodySource = "{\n$source\n}"
            val parser = P(
                CommonTokenStream(
                    mcfppLexer(CharStreams.fromString(bodySource, "macro-diagnostics-source.mcfpp"))
                )
            )
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

    private fun sourceSpan(offset: Int = 0) = SourceSpan(
        "macro-diagnostics-source.mcfpp",
        offset,
        offset + 1,
        1,
        offset + 1,
        1,
        offset + 2
    )

    @Test
    fun nestedDepthOverflowPointsAtUserCallAndCompleteChain() = isolated(
        ProjectConfig(version = "26.3", macroExpansion = MacroExpansionConfig(maxDepth = 1))
    ) {
        val messages = diagnostics { Fixture("ignorePair(ignorePair(1, 2), 3);").compile() }
        assertEquals(
            0,
            MniSourceMacroCallTest.Entries.hostCalls,
            "The depth failure must happen before either host runs"
        )
        assertTrue(Project.errorCount > 0)

        val text = messages.joinToString("\n")
        assertTrue(text.contains("exceeds depth 1"), text)
        assertTrue(text.contains("macro-diagnostics-source.mcfpp:2:"), text)
        assertTrue(text.contains("(user call "), text)
        assertTrue(text.contains("Expansion chain:"), text)
        val chain = text.substringAfter("Expansion chain:")
        assertTrue(
            chain.lineSequence().count { it.contains("ignorePair(") } >= 2,
            "The full root-to-leaf chain must be present:\n$text"
        )
    }

    @Test
    fun nodeBudgetOverflowPointsAtUserCallAndCompleteChain() = isolated {
        Project.config.macroExpansion = MacroExpansionConfig(maxNodes = 1)
        Project.init()
        val messages = diagnostics { Fixture("ignorePair(1, 2);").compile() }
        assertTrue(Project.errorCount > 0)

        val text = messages.joinToString("\n")
        assertTrue(text.contains("generated nodes"), text)
        assertTrue(text.contains("macro-diagnostics-source.mcfpp:2:"), text)
        assertTrue(text.contains("Expansion chain:"), text)
        val chain = text.substringAfter("Expansion chain:")
        assertTrue(chain.lineSequence().any { it.contains("ignorePair(") }, chain)
    }

    @Test
    fun nestedHostExceptionKeepsBothCallSitesAndTheWholeChain() = isolated {
        val messages = diagnostics { Fixture("ignorePair(explodeSource(), 2);").compile() }
        assertTrue(Project.errorCount > 0)

        val text = messages.joinToString("\n")
        assertTrue(text.contains("source macro host exploded"), text)
        assertTrue(text.contains("(user call "), text)
        assertTrue(text.contains("Expansion chain:"), text)
        val chain = text.substringAfter("Expansion chain:")
        assertTrue(chain.lineSequence().any { it.contains("ignorePair(") }, chain)
        assertTrue(chain.lineSequence().any { it.contains("explodeSource(") }, chain)
        assertTrue(
            chain.lineSequence().count { it.contains(" at macro-diagnostics-source.mcfpp:2:") } >= 2,
            "Both the root and the failing nested call must carry their source positions:\n$text"
        )
    }

    @Test
    fun dumpIsCanonicalAndStableAcrossRepeatedCompilations() = isolated(
        ProjectConfig(version = "26.3", macroExpansion = MacroExpansionConfig(dump = true))
    ) {
        val first = diagnostics { Fixture("ignorePair(1, 2);").compile() }
            .filter { it.contains("Macro expansion dump") }
        assertEquals(0, Project.errorCount)

        Project.init()
        val second = diagnostics { Fixture("ignorePair(1, 2);").compile() }
            .filter { it.contains("Macro expansion dump") }
        assertEquals(0, Project.errorCount)

        assertEquals(1, first.size)
        assertEquals(first, second, "An unchanged compilation must print an identical canonical dump")
        val dump = first.single()
        assertTrue(dump.contains("Macro expansion dump at macro-diagnostics-source.mcfpp:2:"), dump)
        assertTrue(dump.contains("literal"), dump)
        assertTrue(dump.contains("yield"), dump)
        assertFalse(dump.contains("SessionId") || dump.contains("SymbolId"), dump)
    }

    @Test
    fun canonicalDumpUsesPublicDeclaredNamesNotCatalogKeys() {
        val session = SessionId(17)
        val arena = CodeArena(session, ScopeId(session, 0)) { _, _ -> }
        try {
            val span = SourceSpan("diagnostics.mcfpp", 0, 14, 1, 1, 1, 15)
            val int = ImmutableTypeRef<IntT>(TypeKey(17, 1), "int", kind = TypeKind.INT)
            val metadata = arena.metadata(arena.rootScope, Origin(span))
            val function = ImmutableFunctionRef(
                FunctionKey(17, "prepared:7"),
                "fixture:consume",
                FunctionSignature.of(emptyList(), int),
                FunctionKind.ORDINARY
            )
            val call = arena.expr(metadata, int, ExprNode.Call(function, emptyList()))
            val dump = ExpansionDiagnostics.dump(call, span, "fixture:consume()->int")
            assertTrue(dump.startsWith("Macro expansion dump at $span for fixture:consume()->int:\n"), dump)
            assertTrue(dump.contains("call \"fixture:consume\""), dump)
            assertFalse(dump.contains("prepared:7"), dump)
        } finally {
            arena.close()
        }
    }

    @Test
    fun sourcedDiagnosticPrependsTheUserCallWithoutLosingTheFailingSite() {
        val root = sourceSpan(0)
        val nested = sourceSpan(15)
        val original = ExpansionDiagnostic(
            nested,
            "Macro host exploded",
            listOf(ExpansionFrame("fixture:inner()->int", nested))
        )
        val sourced = ExpansionDiagnostics.sourced(original, root, "fixture:outer()->int")
        assertEquals(nested, sourced.source)
        assertEquals(2, sourced.frames.size)
        assertEquals(root, sourced.frames.first().callSite)
        assertEquals("fixture:outer()->int", sourced.frames.first().macroSignature)
        assertTrue(sourced.message.contains("Macro host exploded"), sourced.message)
        assertTrue(sourced.message.contains("(user call macro-diagnostics-source.mcfpp:1:1)"), sourced.message)
        assertTrue(sourced.toString().contains("Expansion chain:"), sourced.toString())

        val cause = IllegalStateException("host exploded")
        val failure = ExpansionFailure(original).also { it.initCause(cause) }
        val rewritten = ExpansionDiagnostics.sourcedFailure(failure, root, "fixture:outer()->int")
        assertSame(cause, rewritten.cause, "Sourcing a diagnostic must preserve the host cause chain")
        val rendered = ExpansionDiagnostics.render(rewritten)
        assertTrue(rendered.contains("Expansion chain:"), rendered)
        assertTrue(rendered.contains("Caused by: host exploded"), rendered)
    }
}
