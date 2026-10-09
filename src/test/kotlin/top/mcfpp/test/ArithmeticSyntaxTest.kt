package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
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

class ArithmeticSyntaxTest {
    private val mathData = java.nio.file.Path.of("src/main/resources/datapack/stdlib/data")
    private val mathFunctions by lazy {
        java.nio.file.Files.walk(mathData.resolve("math.float/function")).use { paths ->
            paths.filter { it.toString().endsWith(".mcfunction") }.iterator().asSequence().associate { path ->
                "math.float:" + mathData.resolve("math.float/function").relativize(path).toString()
                    .replace('\\', '/').removeSuffix(".mcfunction") to java.nio.file.Files.readAllLines(path)
            }
        }
    }
    private fun execute(function: top.mcfpp.model.function.Function): ScoreCommandExecutor {
        val functions = mathFunctions + mapOf("fixture:arithmetic" to function.commands.analyzeAll())
        val constants = java.nio.file.Files.readAllLines(mathData.resolve("math/function/_init.mcfunction"))
            .filter { it.startsWith("scoreboard players set ") }
        return ScoreCommandExecutor(constants + "function fixture:arithmetic", functions).also {
            assertEquals(0, it.stackDepth)
        }
    }
    private fun floatRegisters(function: top.mcfpp.model.function.Function, name: String): List<top.mcfpp.analysis.StorageLayout.Scoreboard> {
        val originalSize = function.commands.size
        val registers = function.runInFunction {
            assertNotNull(top.mcfpp.analysis.StorageAccess.legacyFloatRegisters(function.scope.getVar(name) as top.mcfpp.core.lang.MCFloat))
        }
        val loads = function.commands.subList(originalSize, function.commands.size).toList()
        function.commands.subList(originalSize, function.commands.size).clear()
        val exits = function.commands.indices.filter {
            function.commands[it].analyze() == top.mcfpp.command.Commands.stackOut().analyze()
        }
        assertEquals(1, exits.size, "These void arithmetic entries have one actual frame exit")
        function.commands.addAll(exits.single(), loads)
        return registers
    }
    private fun assertFloat(machine: ScoreCommandExecutor, registers: List<top.mcfpp.analysis.StorageLayout.Scoreboard>, expected: Float) {
        assertEquals(top.mcfpp.core.lang.MCFloat.floatToMCFloat(expected).toList(),
            registers.map { machine.values.getValue("${it.player} ${it.objective}") })
    }
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
        assertEquals(2, (function.scope.getVar("a") as top.mcfpp.core.lang.MCInt).value)
        assertEquals(-2, (function.scope.getVar("b") as top.mcfpp.core.lang.MCInt).value)
        assertEquals(-5, (function.scope.getVar("c") as top.mcfpp.core.lang.MCInt).value)
        val g = floatRegisters(function, "g")
        val h = floatRegisters(function, "h")
        val machine = execute(function)
        assertFloat(machine, g, 1.5f)
        assertFloat(machine, h, -1.5f)
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
        assertTrue(commands.contains("stack_frame[0].a set value 4"), commands)
        for (operator in listOf("+=", "-=", "*=", "/=", "%=")) {
            assertTrue(commands.contains(" $operator "), commands)
        }
        assertTrue(commands.contains("stack_frame[0].b set from "), commands)
        val machine = execute(function)
        assertEquals(1, machine.read(function.scope.getVar("a") as MCInt))
        assertEquals(-1, machine.read(function.scope.getVar("b") as MCInt))
        assertEquals(0, machine.stackDepth)
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
        val function = arithmeticFunction()
        val f = floatRegisters(function, "f")
        val negated = floatRegisters(function, "negated")
        val commands = function.commands.analyzeAll().joinToString("\n")
        assertTrue(commands.contains("function math.float:hpo/float/_add"), commands)
        val machine = execute(function)
        assertFloat(machine, f, 2f)
        assertFloat(machine, negated, -2f)
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
        val function = arithmeticFunction()
        val f = floatRegisters(function, "f")
        val negated = floatRegisters(function, "expressionNegated")
        val commands = function.commands.analyzeAll().joinToString("\n")
        assertTrue(commands.contains("function math.float:hpo/float/_add"), commands)
        val machine = execute(function)
        assertFloat(machine, f, 1.5f)
        assertFloat(machine, negated, -2f)
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
        assertEquals(1, (function.scope.getVar("count") as top.mcfpp.core.lang.MCInt).value)
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
