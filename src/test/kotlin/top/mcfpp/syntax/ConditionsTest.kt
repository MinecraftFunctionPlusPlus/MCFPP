package top.mcfpp.syntax

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.compiletime.CompileTimeFunctionScope
import top.mcfpp.compiletime.MCFPPCompileTimeVisitor
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class ConditionsTest {
    @Test fun acceptsBothConditionFormsAndFinalStatementsWithoutSeparators() {
        assertEquals(emptyList(), errors("""
            func one(): int { return 1 }
            func main() {
                var flag = true
                if flag { var nested = {first: 1, nested: {value: 2}} }
                else if (false) { flag = false }
                else { flag = true }
                if
                    flag && (!false || false)
                {
                    while (flag) { flag = false }
                }
                do { flag = false } while flag
                while false { if true { break } }
                if true flag = false
            }
        """.trimIndent()))
        assertEquals(emptyList(), errors("func sample(){var value=1; value=2;}"))
        assertEquals(emptyList(), errors("func sample(){var value=1; value=2}"))
    }

    @Test fun distinctStatementsStillRequireSeparatorsAndConditionsRequireExpressions() {
        for (source in listOf(
            "func sample(){var value=1 value=2}",
            "func sample(){return 1 return 2}",
            "func sample(){if {return 1}}",
            "func sample(){while {break}}",
            "func sample(){do {} while}"
        )) assertTrue(errors(source).isNotEmpty(), source)
    }

    @Test fun conditionFormsExecuteTheSameBranchesLoopsBreaksAndContinues() = isolated {
        for (fold in listOf(true, false)) {
            CompileSettings.foldIRConstants = fold
            for (parentheses in listOf(true, false)) {
                fun condition(value: String) = if (parentheses) "($value)" else value
                val main = compile("""
                    func choose(value: int): int {
                        if ${condition("value > 2 && value < 5")} { return 1 }
                        else if ${condition("value == 2")} { return 2 }
                        else { return 3 }
                    }
                    func sum(limit: int): int {
                        var count = 0
                        var total = 0
                        while ${condition("count < limit")} {
                            count += 1
                            if ${condition("count == 2")} { continue }
                            total += count
                        }
                        do {
                            total += 1
                            if ${condition("total == 7")} { break }
                        } while ${condition("true")}
                        return total
                    }
                    func main() {
                        dynamic first = choose(3)
                        dynamic second = choose(2)
                        dynamic third = choose(1)
                        dynamic result = sum(3)
                    }
                """.trimIndent())
                val machine = execute(main)
                assertEquals(1, machine.read(main.scope.getVar("first") as MCInt))
                assertEquals(2, machine.read(main.scope.getVar("second") as MCInt))
                assertEquals(3, machine.read(main.scope.getVar("third") as MCInt))
                assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
                assertNotNull(main.typedIR)
            }
        }
    }

    @Test fun groupingNewlineConditionsAndNestedBlocksPreserveRuntimeValues() = isolated {
        val main = compile("""
            func probe(value: int): int {
                var result = (value + 1) * 2
                if
                    (value == 2) && !(value > 2)
                {
                    if true { result += 1 }
                }
                else if (value == 3) { result += 2 }
                while
                    result < 8
                { result += 1 }
                do { result += 1 } while
                    result < 9
                return result
            }
            func main() { dynamic result = probe(2) }
        """.trimIndent())
        assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun singleStatementLoopsKeepTheFollowingDeclarationsOutsideTheirBodies() = isolated {
        val source = """
            func main() {
                var flag = true
                while flag
                    flag = false
                var after = 1
                do flag = false while flag
                var tail = 2
                dynamic result = after + tail
            }
        """.trimIndent()
        val parser = parser(source)
        val unit = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val body = unit.typeDeclaration().single().declarations().functionDeclaration().curlBlock()
        assertEquals(6, body.statement().size)
        assertNotNull(body.statement()[1].whileStatement().block().statement().statementExpression())
        assertNotNull(body.statement()[3].doWhileStatement().block().statement().statementExpression())
        assertEquals("after", body.statement()[2].fieldDeclaration().Identifier().text)
        assertEquals("tail", body.statement()[4].fieldDeclaration().Identifier().text)
        val main = compile(source)
        assertEquals(3, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun rejectsNonBooleanAndEmptyConditionsThroughBothCompilerPaths() = isolated {
        for (source in listOf(
            "func main(){if 1 {}}",
            "func main(){if (1) {}}",
            "func main(){if false {} else if 1 {}}",
            "func main(){while 1 {break}}",
            "func main(){do {} while 1}",
            "func main(){if () {}}",
            "func main(){while () {break}}"
        )) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0, source)
        }
        MCFPPStringTest.readFromString("func invalid(value: int){if value {}}\nfunc main(){invalid(1)}", version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun compileTimeVisitorSelectsTheSameBranchesAndLoopExitForBothForms() = isolated {
        for (parentheses in listOf(true, false)) {
            fun condition(value: String) = if (parentheses) "($value)" else value
            for ((body, expected) in listOf(
                "if ${condition("false")} { return 1 } else if ${condition("true")} { return 2 } else { return 3 }" to 2,
                "while ${condition("false")} { return 1 }; return 4" to 4,
                "while ${condition("true")} { return 5 }" to 5,
                "do { if ${condition("true")} { return 6 } } while ${condition("false")}" to 6
            )) {
                val function = compile("func main(){}")
                val previous = Function.currFunction
                val field = CompileTimeFunctionScope(function.scope)
                function.scope = field
                Function.currFunction = function
                try {
                    val parser = parser("{$body}")
                    val block = parser.curlBlock()
                    assertEquals(0, parser.numberOfSyntaxErrors)
                    val visitor = MCFPPCompileTimeVisitor(field)
                    visitor.visitCurlBlock(block)
                    assertEquals(0, Project.errorCount)
                    var value = StorageAccess.snapshot(assertNotNull(visitor.returnValue))
                    while (value is CompilerValue.Typed) value = value.payload
                    assertEquals(expected.toLong(), assertIs<CompilerValue.Integral>(value).value)
                } finally {
                    Function.forcedField = null
                    Function.currFunction = previous
                }
            }
        }
    }

    private fun compile(source: String): Function {
        assertEquals(emptyList(), errors(source))
        MCFPPStringTest.readFromString(source, version = "26.3")
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(main: Function): ScoreCommandExecutor = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        .also { assertEquals(0, it.stackDepth); assertTrue(it.branchGuards.isEmpty()) }

    private fun parser(source: String): mcfppParser =
        mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))

    private fun errors(source: String): List<String> {
        val result = mutableListOf<String>()
        val listener = object : BaseErrorListener() {
            override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                     charPositionInLine: Int, msg: String?, e: RecognitionException?) {
                result += "$line:$charPositionInLine $msg"
            }
        }
        val lexer = mcfppLexer(CharStreams.fromString(source)).apply { removeErrorListeners(); addErrorListener(listener) }
        mcfppParser(CommonTokenStream(lexer)).apply { removeErrorListeners(); addErrorListener(listener) }.compilationUnit()
        return result
    }

    private fun isolated(action: () -> Unit) {
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        val fold = CompileSettings.foldIRConstants
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            action()
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignore
            CompileSettings.foldIRConstants = fold
        }
    }
}
