package top.mcfpp.test

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MniArgumentSyntaxTest {
    @Test fun refKeywordKeepsIdentifierPrefixesAndOriginalSourceSpans() {
        val tokens = mcfppLexer(CharStreams.fromString("ref reference ref_value Ref")).allTokens
        assertEquals(
            listOf(mcfppLexer.REF, mcfppLexer.Identifier, mcfppLexer.Identifier, mcfppLexer.Identifier),
            tokens.map { it.type }
        )
        assertEquals(listOf("ref", "reference", "ref_value", "Ref"), tokens.map { it.text })
        assertEquals(0, tokens.first().startIndex)
        assertEquals(2, tokens.first().stopIndex)
    }

    @Test fun diagnosticOnlyRefArgumentsPreserveSourceOrderKindsAndSpans() {
        val source = "func main(){ macro(7, ref slots[index()], { i, j -> target(i, j); }, tail); }"
        val parsed = parse(source)
        assertEquals(1, parsed.errors.size)
        assertTrue(parsed.errors.single().contains("Legacy 'ref expression'"))
        val arguments = parsed.call("macro").arguments().normalArgs()
        assertNull(arguments.expressionList())
        val values = assertNotNull(arguments.callArgumentList()).callArgument()
        assertEquals(4, values.size)
        assertEquals("7", values[0].expression().text)
        assertNull(values[0].REF())
        assertNotNull(values[1].REF())
        assertEquals("slots[index()]", values[1].expression().text)
        assertEquals("ref slots[index()]", source.substring(values[1].start.startIndex, values[1].stop.stopIndex + 1))
        val lambda = assertNotNull(values[2].inlineLambda())
        assertEquals(listOf("i", "j"), lambda.lambdaParameterList().lambdaParameter().map { it.Identifier().text })
        assertEquals("target(i,j);", lambda.lambdaBody().text)
        assertEquals("{ i, j -> target(i, j); }", source.substring(lambda.start.startIndex, lambda.stop.stopIndex + 1))
        assertEquals("tail", values[3].expression().text)
    }

    @Test fun callbacksAcceptZeroOneMultipleAndNestedParametersWithNewlines() {
        val parsed = parse(
            """
            func main(){
                zero({ var local:int = 0; });
                one({ i: int -> return i; });
                many({ i,
                    j ->
                    target(i, j); });
                outer({ i -> inner(state, { j -> target(i, j); }); });
            }
            """.trimIndent()
        )
        assertEquals(emptyList(), parsed.errors)
        fun names(call: String): List<String> {
            fun first(node: ParseTree): mcfppParser.InlineLambdaContext? {
                if (node is mcfppParser.InlineLambdaContext) return node
                return (0 until node.childCount).firstNotNullOfOrNull { first(node.getChild(it)) }
            }
            return assertNotNull(first(parsed.call(call))).lambdaParameterList()?.lambdaParameter()
                .orEmpty().map { it.Identifier().text }
        }
        assertEquals(emptyList(), names("zero"))
        assertEquals(listOf("i"), names("one"))
        assertEquals(listOf("i", "j"), names("many"))
        assertEquals(listOf("i"), names("outer"))
        assertEquals(listOf("j"), names("inner"))
    }

    @Test fun ordinaryAndReadonlyArgumentsKeepTheExistingExpressionListShape() {
        val parsed = parse(
            """
            func main(){
                select<2, int>(1 + 2, (state), nested(3), receiver.field, slots[0]);
                mixed<2, int>(state, { i -> target(i); });
                empty();
            }
            """.trimIndent()
        )
        assertEquals(emptyList(), parsed.errors)
        val select = parsed.call("select").arguments()
        assertEquals(listOf("2", "int"), select.readOnlyArgs().expressionList().expression().map { it.text })
        assertEquals(
            listOf("1+2", "(state)", "nested(3)", "receiver.field", "slots[0]"),
            select.normalArgs().expressionList().expression().map { it.text }
        )
        assertNull(select.normalArgs().callArgumentList())
        val mixed = parsed.call("mixed").arguments()
        assertEquals(listOf("2", "int"), mixed.readOnlyArgs().expressionList().expression().map { it.text })
        assertNotNull(mixed.normalArgs().expressionList())
        assertNull(parsed.call("empty").arguments().normalArgs().expressionList())
        assertNull(parsed.call("empty").arguments().normalArgs().callArgumentList())
    }

    @Test fun refAlwaysReportsLocatedMigrationWhileRetainingTheDiagnosticNode() {
        val source = "func main(){ macro(ref state + 1, 2); }"
        val parsed = parse(source)
        assertEquals(1, parsed.errors.size)
        assertTrue(parsed.errors.single().startsWith("1:${source.indexOf("ref")}"))
        assertTrue(parsed.errors.single().contains("explicit Minecraft address"))
        val argument = parsed.call("macro").arguments().normalArgs().callArgumentList().callArgument().first()
        assertNotNull(argument.REF())
        assertEquals("state+1", argument.expression().text)
    }

    @Test fun refOutsideCallsAndLegacyArrowFormsAreRejected() {
        for (statement in listOf(
            "var callback = (i) => {};",
            "var location = ref state;",
            "macro<ref state>();",
            "macro<(i) => {}>();"
        )) assertTrue(parse("func main(){ $statement }").errors.isNotEmpty(), statement)
    }

    @Test fun malformedCallbacksReferencesAndNamedArgumentsAreRejected() {
        for (arguments in listOf(
            "ref",
            "ref ref state",
            "(i: int) => {}",
            "i => {}",
            "(i,) => {}",
            "(i) => i",
            "name = 1",
            "(i) -> {}"
        )) assertTrue(parse("func main(){ macro($arguments); }").errors.isNotEmpty(), arguments)
    }

    private data class Parsed(val tree: mcfppParser.CompilationUnitContext, val errors: List<String>) {
        fun call(name: String): mcfppParser.FunctionCallContext {
            val calls = arrayListOf<mcfppParser.FunctionCallContext>()
            fun visit(node: ParseTree) {
                if (node is mcfppParser.FunctionCallContext && node.namespaceID().text == name) calls.add(node)
                for (index in 0 until node.childCount) visit(node.getChild(index))
            }
            visit(tree)
            return calls.single()
        }
    }

    private fun parse(source: String): Parsed {
        val errors = arrayListOf<String>()
        val listener = object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?
            ) {
                errors.add("$line:$charPositionInLine $msg")
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
}
