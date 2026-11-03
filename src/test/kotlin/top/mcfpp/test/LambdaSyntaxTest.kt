package top.mcfpp.test

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Grammar-only coverage for the Kotlin-style lambda and function type syntax.
 *
 * These tests intentionally parse source text directly and never enter the compiler/backend.
 */
class LambdaSyntaxTest {
    private data class Parsed(
        val tree: mcfppParser.CompilationUnitContext,
        val errors: List<String>
    )

    private fun parse(source: String): Parsed {
        val errors = mutableListOf<String>()
        val listener = object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?
            ) {
                errors += "line $line:$charPositionInLine: $msg"
            }
        }
        val lexer = mcfppLexer(CharStreams.fromString(source)).apply {
            removeErrorListeners()
            addErrorListener(listener)
        }
        val parser = mcfppParser(CommonTokenStream(lexer)).apply {
            removeErrorListeners()
            addErrorListener(listener)
        }
        return Parsed(parser.compilationUnit(), errors)
    }

    private fun parseOk(source: String): mcfppParser.CompilationUnitContext {
        val parsed = parse(source)
        assertTrue(
            parsed.errors.isEmpty(),
            "Unexpected syntax errors:\n${parsed.errors.joinToString("\n")}\nSource:\n$source"
        )
        return parsed.tree
    }

    private fun <T : ParseTree> collect(root: ParseTree, type: Class<T>): List<T> {
        val found = mutableListOf<T>()
        fun walk(node: ParseTree) {
            if (type.isInstance(node)) found += type.cast(node)
            for (index in 0 until node.childCount) walk(node.getChild(index))
        }
        walk(root)
        return found
    }

    private fun functionNamed(tree: ParseTree, name: String): mcfppParser.FunctionDeclarationContext =
        collect(tree, mcfppParser.FunctionDeclarationContext::class.java)
            .single { it.functionDeclarationPart().Identifier().text == name }

    @Test
    fun explicitAndInferredLambdaParametersKeepTypesAndNames() {
        val tree = parseOk(
            """
            func main(){
                var typed = { a: int, b: string -> a };
                var inferred = { a, b -> a };
            }
            """.trimIndent()
        )
        val lambdas = collect(tree, mcfppParser.InlineLambdaContext::class.java)
        assertEquals(2, lambdas.size)

        val typed = lambdas[0].lambdaParameterList().lambdaParameter()
        assertEquals(listOf("a", "b"), typed.map { it.Identifier().text })
        assertEquals(listOf("int", "string"), typed.map { assertNotNull(it.type()).text })
        typed.forEach { assertNotNull(it.COLON()) }
        assertNotNull(lambdas[0].ARROW())
        assertEquals("a", lambdas[0].lambdaBody().lambdaTailExpression()?.text)

        val inferred = lambdas[1].lambdaParameterList().lambdaParameter()
        assertEquals(listOf("a", "b"), inferred.map { it.Identifier().text })
        inferred.forEach {
            assertNull(it.COLON())
            assertNull(it.type())
        }
    }

    @Test
    fun parameterlessLambdaBodiesKeepStatementsAndTailExpressionsSeparate() {
        val tree = parseOk(
            """
            func main(){
                var statements = { print("Hello"); };
                var tail = { 1 + 2 };
                var mixed = {
                    var x = 1
                    x + 1
                };
            }
            """.trimIndent()
        )
        val lambdas = collect(tree, mcfppParser.InlineLambdaContext::class.java)
        assertEquals(3, lambdas.size)

        assertNull(lambdas[0].lambdaParameterList())
        assertNull(lambdas[0].lambdaBody().lambdaTailExpression())
        assertEquals(1, lambdas[0].lambdaBody().statement().size)
        assertEquals("print(\"Hello\")", lambdas[0].lambdaBody().statement().single().text)

        assertEquals("1+2", lambdas[1].lambdaBody().lambdaTailExpression()?.text)

        assertNull(lambdas[2].lambdaParameterList())
        assertEquals(1, lambdas[2].lambdaBody().statement().size)
        assertEquals("varx=1", lambdas[2].lambdaBody().statement().single().text)
        assertEquals("x+1", lambdas[2].lambdaBody().lambdaTailExpression()?.text)
    }

    @Test
    fun trailingLambdasAttachToMemberAndParenthesizedCallTargets() {
        val tree = parseOk(
            """
            func main(){
                items.map { item -> item + 1 };
                items.filter { item -> item > 0 };
                items.fold(0) { sum, item -> sum + item };
                consume(1) { value -> value };
            }
            """.trimIndent()
        )
        val calls = collect(tree, mcfppParser.LambdaCallExpressionContext::class.java)
        assertEquals(4, calls.size)
        assertEquals(
            listOf("item+1", "item>0", "sum+item", "value"),
            calls.map { it.inlineLambda().lambdaBody().lambdaTailExpression()?.text }
        )

        // "map" is a keyword, so it arrives through memberKeyword instead of a normal var.
        assertEquals("items", calls[0].jvmAccessExpression()?.text)
        assertEquals("map", calls[0].selector().first().memberKeyword()?.MAP()?.text)
        assertEquals("filter", calls[1].selector().first().`var`()?.varWithSuffix()?.Identifier()?.text)
        assertEquals("fold", calls[2].selector().first().`var`()?.functionCall()?.namespaceID()?.text)
        assertEquals("consume", calls[3].functionCall()?.namespaceID()?.text)
    }

    @Test
    fun trailingLambdaIsAlsoAValueExpression() {
        val tree = parseOk(
            """
            func main(){
                var filtered = items.filter { item -> item > 0 };
                consume(items.fold(0) { sum, item -> sum + item });
            }
            """.trimIndent()
        )
        val calls = collect(tree, mcfppParser.LambdaCallExpressionContext::class.java)
        assertEquals(2, calls.size)
        assertEquals("items.filter", calls[0].jvmAccessExpression()!!.text + calls[0].selector().first().text)
        assertEquals("fold", calls[1].selector().first().`var`()?.functionCall()?.namespaceID()?.text)
    }

    @Test
    fun functionTypesExposeParameterAndResultTypes() {
        val tree = parseOk(
            """
            func apply(callback: (int) -> int): (int) -> int {
                return callback;
            }
            func main(){
                var f: (int, string) -> int = { a, b -> a };
                var g: () -> void = { ; };
                var h: ((int) -> int) -> void = { x -> x(1); };
            }
            """.trimIndent()
        )

        val declaration = functionNamed(tree, "apply").functionDeclarationPart()
        val parameterType = assertNotNull(
            declaration.functionParams().normalParams().parameterList().parameter().single().type()
        ).typeBody().functionType()
        assertEquals(listOf("int"), parameterType?.type()?.map { it.text })
        assertEquals("int", assertNotNull(parameterType).functionReturnType().type()?.text)

        val resultType = assertNotNull(declaration.functionReturnType()).type().typeBody().functionType()
        assertEquals(listOf("int"), assertNotNull(resultType).type().map { it.text })
        assertEquals("int", resultType.functionReturnType().type()?.text)

        val fields = collect(tree, mcfppParser.FieldDeclarationContext::class.java)
            .associateBy { it.Identifier().text }

        val fType = assertNotNull(assertNotNull(fields.getValue("f").type()).typeBody().functionType())
        assertEquals(listOf("int", "string"), fType.type().map { it.text })
        assertNotNull(fType.ARROW())
        assertEquals("int", fType.functionReturnType().type()?.text)

        val gType = assertNotNull(assertNotNull(fields.getValue("g").type()).typeBody().functionType())
        assertTrue(gType.type().isEmpty())
        assertNotNull(gType.functionReturnType().VOID())

        val hType = assertNotNull(assertNotNull(fields.getValue("h").type()).typeBody().functionType())
        val nested = assertNotNull(hType.type().single().typeBody().functionType())
        assertEquals(listOf("int"), nested.type().map { it.text })
        assertEquals("int", nested.functionReturnType().type()?.text)
    }

    @Test
    fun functionTypeInsideLambdaParameterStopsAtTheLambdaArrow() {
        val tree = parseOk("func main(){ var f = { g: (int) -> int -> g(1) }; }")
        val lambda = collect(tree, mcfppParser.InlineLambdaContext::class.java).single()
        val parameter = lambda.lambdaParameterList().lambdaParameter().single()
        assertEquals("g", parameter.Identifier().text)
        assertEquals("(int)->int", assertNotNull(parameter.type()).text)
        assertEquals("g(1)", lambda.lambdaBody().lambdaTailExpression()?.text)
    }

    @Test
    fun functionTypesNestAndAssociateRightWithoutSwallowingParenthesizedExpressions() {
        val tree = parseOk("""
            func main(){
                var nested: ((int) -> int) -> int;
                var returned: (int) -> () -> int;
                var grouped = (1 + 2) * 3;
                var name = 1;
                var groupedName = (name);
            }
        """.trimIndent())
        val fields = collect(tree, mcfppParser.FieldDeclarationContext::class.java).associateBy { it.Identifier().text }
        val nested = fields.getValue("nested").type().typeBody().functionType()
        assertNotNull(nested.type().single().typeBody().functionType())
        val returned = fields.getValue("returned").type().typeBody().functionType()
        assertTrue(returned.functionReturnType().type().typeBody().functionType().type().isEmpty())
        assertEquals(2, collect(tree, mcfppParser.BucketExpressionContext::class.java).size)
        assertTrue(parse("func main(){ var old: func(int) -> int; }").errors.isNotEmpty())
    }

    @Test
    fun trailingExpressionAndSemicolonTerminatedStatementAreDifferentTrees() {
        val tree = parseOk(
            """
            func main(){
                var tail = { 1 + 2 };
                var statement = { 1 + 2; };
                var lastTail = {
                    var x = 1
                    x
                };
                var lastStatement = {
                    var x = 1; x;
                };
            }
            """.trimIndent()
        )
        val lambdas = collect(tree, mcfppParser.InlineLambdaContext::class.java)
        assertEquals(4, lambdas.size)

        assertEquals("1+2", lambdas[0].lambdaBody().lambdaTailExpression()?.text)
        assertNull(lambdas[1].lambdaBody().lambdaTailExpression())
        assertEquals(1, lambdas[1].lambdaBody().statement().size)

        assertEquals(1, lambdas[2].lambdaBody().statement().size)
        assertEquals("x", lambdas[2].lambdaBody().lambdaTailExpression()?.text?.trim())

        assertEquals(2, lambdas[3].lambdaBody().statement().size)
        assertNull(lambdas[3].lambdaBody().lambdaTailExpression())
    }

    @Test
    fun newSyntaxDoesNotSwallowNbtCompoundsControlBlocksOrOrdinaryCalls() {
        val tree = parseOk(
            """
            func main(){
                var compound = {first: 1, nested: {value: 2}};
                var empty = {};
                if flag { var inside = 1; }
                while flag { break; }
                target(1, 2);
                target({a: 1});
                target({});
            }
            """.trimIndent()
        )

        assertTrue(collect(tree, mcfppParser.InlineLambdaContext::class.java).isEmpty())
        assertEquals(5, collect(tree, mcfppParser.NbtCompoundContext::class.java).size)

        val branch = collect(tree, mcfppParser.IfStatementContext::class.java).single()
        assertEquals("flag", branch.conditionExpression()?.text)
        assertNull(branch.expression()) // legacy accessor shim, not a parsed condition
        assertNotNull(branch.block())

        val loop = collect(tree, mcfppParser.WhileStatementContext::class.java).single()
        assertEquals("flag", loop.conditionExpression()?.text)
        assertNull(loop.expression())
        assertNotNull(loop.block())

        val targets = collect(tree, mcfppParser.FunctionCallContext::class.java)
            .filter { it.namespaceID().text == "target" }
        assertEquals(3, targets.size)
        assertEquals(2, targets[0].arguments().normalArgs().expressionList().expression().size)
        assertEquals(1, targets[1].arguments().normalArgs().expressionList().expression().size)
        assertEquals(1, targets[2].arguments().normalArgs().expressionList().expression().size)
        targets.forEach { assertNull(it.arguments().normalArgs().callArgumentList()) }
    }

    @Test
    fun trailingLambdaCallInAConditionRequiresParentheses() {
        val tree = parseOk("func main(){ if (check { true }) { print(1); } }")
        val branch = collect(tree, mcfppParser.IfStatementContext::class.java).single()
        assertEquals("(check{true})", branch.conditionExpression()?.text)
        assertEquals(1, collect(tree, mcfppParser.InlineLambdaContext::class.java).size)

        val whileTree = parseOk("func main(){ while (check { true }) { break; } }")
        val loop = collect(whileTree, mcfppParser.WhileStatementContext::class.java).single()
        assertEquals("(check{true})", loop.conditionExpression()?.text)

        assertTrue(parse("func main(){ if check { true } { print(1); } }").errors.isNotEmpty())
        assertTrue(parse("func main(){ while check { true } { break; } }").errors.isNotEmpty())
    }

    @Test
    fun doWhileConditionCanUseATrailingLambdaWithoutAnAmbiguousBlock() {
        val tree = parseOk("func main(){ do { break; } while check { true }; }")
        assertEquals(1, collect(tree, mcfppParser.LambdaCallExpressionContext::class.java).size)
        val loop = collect(tree, mcfppParser.DoWhileStatementContext::class.java).single()
        assertEquals("check{true}", loop.expression()?.text)
    }

    @Test
    fun legacyArrowIsLexedButNoLongerAValidSourceEntry() {
        val arrow = mcfppLexer(CharStreams.fromString("=>")).allTokens
            .filter { it.type != Token.EOF }
        assertEquals(listOf(mcfppLexer.DOUBLE_ARROW), arrow.map { it.type })

        for (source in listOf(
            "func main(){ repeat(5, (i) => { target(i); }); }",
            "func main(){ var f = () => { return 1; }; }",
            "func main(){ var f = (i: int) => {}; }",
            "func main(){ var f = { x => x }; }"
        )) {
            assertTrue(parse(source).errors.isNotEmpty(), "Expected a syntax error for: $source")
        }
    }

    @Test
    fun legacyArrowRuleRemainsAvailableForMigrationDiagnostics() {
        val parser = mcfppParser(
            CommonTokenStream(mcfppLexer(CharStreams.fromString("(i, j) => { target(i, j); }")))
        )
        val legacy = parser.legacyLambdaSyntax()
        assertEquals(0, parser.numberOfSyntaxErrors)
        assertEquals(listOf("i", "j"), legacy.Identifier().map { it.text })
        assertEquals("{target(i,j);}", legacy.curlBlock().text)
    }
}
