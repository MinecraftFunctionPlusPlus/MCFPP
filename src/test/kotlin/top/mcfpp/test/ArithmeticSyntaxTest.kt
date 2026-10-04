package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCFloatConcrete
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArithmeticSyntaxTest {
    private fun arithmeticFunction() =
        GlobalScope.localNamespaces["default.test"]!!.scope.functions["arithmetic"]!!.first()

    private fun parse(source: String): mcfppParser {
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors, source)
        return parser
    }

    @Test
    fun lineStartSlashIsCommandAndTrailingOperatorsContinue() {
        val source = """
            func test(){
                var x = 12 /
                    3
                /say hello
                x +=
                    2;
            }
        """.trimIndent()
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        val tree = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val statements = tree.typeDeclaration(0).declarations().functionDeclaration().curlBlock().statement()
        assertEquals(3, statements.size)
        assertNotNull(statements[1].orgCommand())
        assertEquals("+=", statements[2].statementExpression().assignmentOperator().text)
    }

    @Test
    fun unaryMinusAndEveryCompoundAssignmentParse() {
        parse("""
            func test(){
                var a = 10;
                a += 1; a -= 1; a *= 2; a /= 2; a %= 3;
                var b = -a;
                var c = -(a + 1);
            }
        """.trimIndent())
    }

    @Test
    fun constantArithmeticHasExpectedValues() {
        MCFPPStringTest.readFromString("""
            func arithmetic(){
                var a = 20;
                a += 3;
                a -= 5;
                a *= 2;
                a /= 3;
                a %= 5;
                var b = -a;
                var c = -(2 + 3);
                var g = 1.5;
                var h = -g;
            }
        """.trimIndent())
        assertEquals(0, Project.errorCount)
        val function = arithmeticFunction()
        assertEquals(2, (function.scope.getVar("a") as MCIntConcrete).value)
        assertEquals(-2, (function.scope.getVar("b") as MCIntConcrete).value)
        assertEquals(-5, (function.scope.getVar("c") as MCIntConcrete).value)
        fun floatValue(name: String) = (function.scope.getVar(name) as MCFloatConcrete).value
        assertEquals(1.5f, floatValue("g"))
        assertEquals(-1.5f, floatValue("h"))
    }

    @Test
    fun dynamicAssignmentWritesCalculatedValue() {
        MCFPPStringTest.readFromString("""
            func arithmetic(){
                dynamic var a as int = 4;
                a += 2;
                a -= 1;
                a *= 3;
                a /= 3;
                a %= 4;
                var b = -a;
            }
        """.trimIndent())
        assertEquals(0, Project.errorCount)
        val function = arithmeticFunction()
        val commands = function.commands.analyzeAll().joinToString("\n")
        assertTrue(commands.contains("scoreboard players set default.test_func_arithmetic_a mcfpp_default 4"), commands)
        assertTrue(commands.contains("scoreboard players add "), commands)
        assertTrue(commands.contains("scoreboard players remove "), commands)
        for (operator in listOf("*=", "/=", "%=")) {
            assertTrue(commands.contains(" $operator "), commands)
        }
        assertTrue(Regex("scoreboard players set temp_\\d+ mcfpp_default -1").containsMatchIn(commands), commands)
    }

    @Test
    fun dynamicFloatCanUseCompoundAssignmentAndUnaryMinus() {
        MCFPPStringTest.readFromString("""
            func arithmetic(){
                dynamic var f as float = 1.5;
                f += 0.5;
                var negated = -f;
            }
        """.trimIndent())
        assertEquals(0, Project.errorCount)
        val commands = arithmeticFunction().commands.analyzeAll().joinToString("\n")
        assertTrue(commands.contains("function math.float:hpo/float/_add"), commands)
        assertEquals(1, Regex("scoreboard players operation temp_\\d+ mcs_float_sign \\*= temp_\\d+ mcfpp_default").findAll(commands).count(), commands)
    }

    @Test
    fun nestedDynamicFloatExpressionCompiles() {
        MCFPPStringTest.readFromString("""
            func arithmetic(){
                dynamic var f as float = 1.5;
                var expressionNegated = -(f + 0.5);
            }
        """.trimIndent())
        assertEquals(0, Project.errorCount)
        val commands = arithmeticFunction().commands.analyzeAll().joinToString("\n")
        assertTrue(commands.contains("function math.float:hpo/float/_add"), commands)
        assertEquals(4, commands.lines().count {
            it.startsWith("execute store result storage") && it.contains(" int 1 run scoreboard players operation ")
        }, commands)
    }

    @Test
    fun invalidCompoundAssignmentsProduceDiagnostics() {
        MCFPPStringTest.readFromString("""
            func invalid(){
                const var count = 1;
                count += 1;
                var flag = true;
                flag += true;
            }
        """.trimIndent())
        assertTrue(Project.errorCount >= 2)
        val function = GlobalScope.localNamespaces["default.test"]!!.scope.functions["invalid"]!!.first()
        assertTrue(function.scope.getVar("count")!!.isConst)
        assertEquals(1, (function.scope.getVar("count") as MCIntConcrete).value)
    }

    @Test
    fun indexedAssignmentWritesBackThroughProperty() {
        MCFPPStringTest.readFromString("""
            func arithmetic(){
                var values = [1, 2];
                values[0] += 3;
                dynamic var updated = values[0];
                dynamic var preserved = values[1];
            }
        """.trimIndent())
        assertEquals(0, Project.errorCount)
        val function = arithmeticFunction()
        val machine = ScoreCommandExecutor(function.commands.analyzeAll())
        assertEquals(4, machine.read(function.scope.getVar("updated") as MCInt))
        assertEquals(2, machine.read(function.scope.getVar("preserved") as MCInt))
        assertEquals(0, machine.stackDepth)
    }
}
