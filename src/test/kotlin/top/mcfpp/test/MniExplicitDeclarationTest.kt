package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.parseCompilationUnit
import top.mcfpp.mni.api.*
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.util.LogProcessor
import kotlin.test.*
import kotlin.test.Test

class MniExplicitDeclarationTest {
    @Test fun callbackBindingsKeepTheSameSyntaxTreeWithSllAndLlPrediction() {
        val source = """
            func repeat(times: int, body: (int)->void): void = example.macros.Repeat.repeat
            func fold(times: int, initial: int, body: (int,int)->int): int = example.macros.Repeat.fold
            func main() { repeat(2) { index -> print(index); }; }
        """.trimIndent()
        fun parser() = mcfppParser(org.antlr.v4.runtime.CommonTokenStream(
            mcfppLexer(org.antlr.v4.runtime.CharStreams.fromString(source))))
        val fast = parser()
        val tree = fast.parseCompilationUnit().toStringTree(fast)
        val full = parser()
        assertEquals(full.compilationUnit().toStringTree(full), tree)
        assertEquals(0, fast.numberOfSyntaxErrors)
        assertEquals(0, full.numberOfSyntaxErrors)
    }

    @Test fun invalidSourceStillReportsErrorsAfterSllFallback() {
        val parser = mcfppParser(org.antlr.v4.runtime.CommonTokenStream(
            mcfppLexer(org.antlr.v4.runtime.CharStreams.fromString("func missing(value: int): int ="))))
        parser.removeErrorListeners()
        var reports = 0
        parser.addErrorListener(object : org.antlr.v4.runtime.BaseErrorListener() {
            override fun syntaxError(recognizer: org.antlr.v4.runtime.Recognizer<*, *>?, offendingSymbol: Any?,
                                     line: Int, charPositionInLine: Int, msg: String?, e: org.antlr.v4.runtime.RecognitionException?) {
                reports++
            }
        })
        parser.parseCompilationUnit()
        assertTrue(reports > 0)
        assertEquals(reports, parser.numberOfSyntaxErrors)
    }

    private fun compile(source: String, inject: Boolean = false): List<String> {
        ExplicitMacroEntries.calls = 0
        return LogProcessor.beginDiagnosticCapture().use { capture ->
            MCFPPStringTest.readFromString(source, version = "26.3", afterFileCreated = { file ->
                if (inject) file.namespace.injectedBy(ExplicitMacroEntries::class.java)
            })
            capture.seal().diagnostics.map { it.message }
        }
    }

    @Test fun explicitDeclarationsSupplyNamesAndCanAliasTheSameJvmMethod() {
        val diagnostics = compile("""
            func increment(input: int): int = top.mcfpp.test.ExplicitMacroEntries.addOne
            func another(number: int): int = top.mcfpp.test.ExplicitMacroEntries.addOne
            func main() {
                external first: int = increment(3)
                external second: int = another(first)
            }
        """.trimIndent())
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        assertEquals(2, ExplicitMacroEntries.calls)
        val namespace = GlobalScope.localNamespaces.getValue("default.test")
        assertEquals(mapOf("increment" to "input", "another" to "number"),
            namespace.macroDeclarations().associate { it.identifier to it.parameters.single().name })
        assertFalse(namespace.scope.functions.containsKey("addOne"))
    }

    @Test fun injectedProvidersDoNotExposeUndeclaredMacros() {
        val diagnostics = compile("func main() { var value = addOne(3); }", inject = true)
        assertTrue(Project.errorCount > 0, diagnostics.joinToString("\n"))
        assertEquals(0, ExplicitMacroEntries.calls)
        assertTrue(GlobalScope.localNamespaces.getValue("default.test").macroDeclarations().isEmpty())
    }

    @Test fun mismatchedParametersAndResultAreRejectedBeforeCallingTheProvider() {
        val diagnostics = compile("""
            func wrongParameter(input: string): int = top.mcfpp.test.ExplicitMacroEntries.addOne
            func wrongResult(input: int): bool = top.mcfpp.test.ExplicitMacroEntries.addOne
        """.trimIndent())
        assertEquals(2, Project.errorCount, diagnostics.joinToString("\n"))
        assertEquals(2, diagnostics.count { it.contains("no implementation matches") })
        assertEquals(0, ExplicitMacroEntries.calls)
        assertTrue(GlobalScope.localNamespaces.getValue("default.test").macroDeclarations().isEmpty())
    }

    @Test fun duplicateExplicitDeclarationsAreDiagnosed() {
        val diagnostics = compile("""
            func same(input: int): int = top.mcfpp.test.ExplicitMacroEntries.addOne
            func same(input: int): int = top.mcfpp.test.ExplicitMacroEntries.addOne
        """.trimIndent())
        assertTrue(diagnostics.any { it.contains("Already defined macro") }, diagnostics.joinToString("\n"))
        assertEquals(1, GlobalScope.localNamespaces.getValue("default.test").macroDeclarations().size)
        assertEquals(0, ExplicitMacroEntries.calls)
    }
}

object ExplicitMacroEntries {
    var calls = 0
    @JvmStatic
    @MNIMacro(normalParams = ["int"], returnType = "int")
    fun addOne(context: MacroContext, value: Value<IntT>): Expr<IntT> {
        calls++
        return context.quote.expression(context.types().intType()) { yield(value.value + 1) }
    }
}
