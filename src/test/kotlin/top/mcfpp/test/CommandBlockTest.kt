package top.mcfpp.test

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandBlockTest {
    private fun parse(source: String): mcfppParser.CompilationUnitContext {
        val errors = mutableListOf<String>()
        val listener = object : BaseErrorListener() {
            override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                     charPositionInLine: Int, msg: String?, e: RecognitionException?) {
                errors += "$line:$charPositionInLine $msg"
            }
        }
        val lexer = mcfppLexer(CharStreams.fromString(source)).apply { removeErrorListeners(); addErrorListener(listener) }
        val parser = mcfppParser(CommonTokenStream(lexer)).apply { removeErrorListeners(); addErrorListener(listener) }
        return parser.compilationUnit().also { assertTrue(errors.isEmpty(), errors.joinToString("\n")) }
    }

    @Test fun commandLinesPreserveTextAndEscapedStatementsRestoreLexing() {
        val source = """
            func main(){
                /{
                    say hello; var text is a command
                    data merge storage demo:data {value:1,nested:{text:"}"}}
                    /var value = 12 / 3
                    say ${'$'}{value}

                    /if value > 0 {
                        /say ordinary nested command
                    }
                    say after branch
                    /value += 1; value += 2
                }
                /say outside
                var result = 6 / 2
                /{}
            }
        """.trimIndent()
        for (text in listOf(source, source.replace("\n", "\r\n"))) {
            val statements = parse(text).typeDeclaration(0).declarations().functionDeclaration().curlBlock().statement()
            assertEquals(4, statements.size)
            val block = assertNotNull(statements[0].commandBlock()).statement()
            assertEquals(8, block.size)
            assertEquals("say hello; var text is a command", block[0].orgCommand().orgCommandContent(0).text)
            assertEquals("data merge storage demo:data {value:1,nested:{text:\"}\"}}", block[1].orgCommand().orgCommandContent(0).text)
            assertNotNull(block[2].fieldDeclaration())
            assertNotNull(block[3].orgCommand().orgCommandContent(1).orgCommandExpression())
            assertNotNull(block[4].ifStatement())
            assertEquals("say after branch", block[5].orgCommand().orgCommandContent(0).text)
            assertNotNull(statements[1].orgCommand())
            assertTrue(statements[3].commandBlock().statement().isEmpty())
        }
    }

    @Test fun commandBlockCompilesToOrderedCommandsAndOrdinaryStatements() {
        MCFPPStringTest.readFromString("""
            func main(){
                external value = 2
                /{
                    say first
                    /value += 3
                    /if value > 0 {
                        /say branch
                    }
                    say last
                }
                external result = value
                /say outside
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertNotNull(main.typedIR)
        val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() }
        val machine = ScoreCommandExecutor(main.commands.analyzeAll(), functions)
        assertEquals(listOf("first", "branch", "last", "outside"), machine.messages)
        assertEquals(5, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun commandBlockInterpolationUsesOrdinaryCommandEmission() {
        MCFPPStringTest.readFromString("""
            func main(){
                var value = 4
                /{
                    say ${'$'}{value}
                    /value += 1
                    say ${'$'}{value}
                }
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val macros = Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { it.value.lines() }
        val machine = ScoreCommandExecutor(main.commands.analyzeAll(), macros)
        assertEquals(listOf("4", "5"), machine.messages)
    }

    @Test fun commandBlockInLambdaBindsDeclarationsAndReturnValues() {
        MCFPPStringTest.readFromString("""
            func main(){
                var callback: () -> int = {
                    /{
                        /var value = 4
                        /return value
                    }
                }
                external result = callback()
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() }
        val machine = ScoreCommandExecutor(main.commands.analyzeAll(), functions)
        assertEquals(4, machine.read(main.scope.getVar("result") as MCInt))
    }
}
