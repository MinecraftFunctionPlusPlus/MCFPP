package top.mcfpp.test.library

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.library.syntax.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryExpressionTest {
    private class Capture : ExpressionCaptureBindings {
        val constants = linkedMapOf<ConstantRef, ConstantValue>()
        val builtins = linkedMapOf<String, TypeRef>()
        val names = linkedMapOf<Pair<NameRole, String>, DeclarationId>()
        val lambdaParameterTypes = mutableListOf<TypeSyntax>()
        var lambdaCount = 0L
        var anonymousCount = 0
        val bridge = ExpressionBridge(this)

        override fun scope(site: ParserRuleContext) = ScopeId(7)
        override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse =
            NameUse.Local(names.getOrPut(role to "${namespace.orEmpty()}:$name") { DeclarationId(names.size + 1L) })
        override fun builtinType(key: String, site: ParserRuleContext): TypeRef =
            builtins.getOrPut(key) { TypeRef(builtins.size + 1L) }
        override fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef =
            constants.entries.firstOrNull { it.value == value }?.key
                ?: ConstantRef(constants.size + 1L).also { constants[it] = value }
        override fun resolvedType(site: ParserRuleContext) = TypeRef(800)
        override fun owner(site: ParserRuleContext) = DeclarationReference.Local(DeclarationId(500))
        override fun lambda(site: Parser.InlineLambdaContext, capture: ExpressionBridge): Expression.Lambda {
            val index = ++lambdaCount
            val params = site.lambdaParameterList()?.lambdaParameter().orEmpty().mapIndexed { parameterIndex, parameter ->
                parameter.type()?.let { lambdaParameterTypes += capture.type(it) }
                DeclarationId(1000 + index * 10 + parameterIndex)
            }
            return Expression.Lambda(scope(site), DeclarationId(100 + index), params,
                site.lambdaParameterList() != null, BodyId(200 + index),
                site.lambdaBody().lambdaTailExpression()?.expression()?.let(capture::expression))
        }
        override fun anonymousType(site: Parser.AnonymousTemplateTypeContext, capture: ExpressionBridge): DeclarationId {
            anonymousCount++
            return DeclarationId(700)
        }
        fun payload(reference: ConstantRef): ConstantValue = when (val value = constants.getValue(reference)) {
            is ConstantValue.Typed -> payload(value.value)
            else -> value
        }
        fun expression(source: String) = bridge.expression(parse(source) { expression() })
        fun type(source: String) = bridge.type(parse(source) { type() })
    }

    companion object {
        private fun <T : ParserRuleContext> parse(source: String, rule: Parser.() -> T): T {
            val errors = mutableListOf<String>()
            val listener = object : BaseErrorListener() {
                override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                         charPositionInLine: Int, msg: String?, e: RecognitionException?) {
                    errors += "$line:$charPositionInLine $msg"
                }
            }
            val lexer = mcfppLexer(CharStreams.fromString(source)).apply {
                removeErrorListeners()
                addErrorListener(listener)
            }
            val tokens = CommonTokenStream(lexer)
            val parser = Parser(tokens).apply { removeErrorListeners(); addErrorListener(listener) }
            val result = parser.rule()
            assertTrue(errors.isEmpty(), "$source: $errors")
            assertEquals(Token.EOF, tokens.LA(1), "Source must be consumed completely: $source")
            return result
        }
    }

    @Test
    fun binaryTreeRetainsPrecedenceAssociativityAndShortCircuitOperations() {
        val capture = Capture()
        val pipe = assertIs<Expression.Binary>(capture.expression("a + b * c < d && e || f | g"))
        assertEquals("|", pipe.operator)
        val or = assertIs<Expression.Binary>(pipe.left)
        assertEquals("||", or.operator)
        val and = assertIs<Expression.Binary>(or.left)
        assertEquals("&&", and.operator)
        val comparison = assertIs<Expression.Binary>(and.left)
        assertEquals("<", comparison.operator)
        val sum = assertIs<Expression.Binary>(comparison.left)
        assertEquals("+", sum.operator)
        assertEquals("*", assertIs<Expression.Binary>(sum.right).operator)
        val subtraction = assertIs<Expression.Binary>(capture.expression("a - b - c"))
        assertEquals("-", assertIs<Expression.Binary>(subtraction.left).operator)
        assertIs<Expression.Name>(subtraction.right)
        assertIs<Expression.Group>(assertIs<Expression.Binary>(capture.expression("(a + b) * c")).left)
        val cast = assertIs<Expression.Cast>(capture.expression("!a as bool"))
        assertEquals("!", assertIs<Expression.Unary>(cast.value).operator)
        assertIs<TypeSyntax.Name>(cast.target)
        assertNull(assertIs<Expression.Group>(capture.expression("()")).value)
        assertEquals(ScopeId(7), pipe.scope)
        assertEquals(0x0108, pipe.tag)
    }

    @Test
    fun namesAreBoundByAuthorEnvironmentAndDynamicMembersKeepNames() {
        val capture = Capture()
        val call = assertIs<Expression.Call>(capture.expression("author.lib:take<int>(value)"))
        val name = assertIs<Expression.Name>(call.callee)
        assertIs<NameUse.Local>(name.use)
        assertTrue((NameRole.VALUE to "author.lib:take") in capture.names)
        assertIs<Expression.TypeValue>(call.readonly.single())
        val indexed = assertIs<Expression.Index>(capture.expression("receiver.member[]"))
        val member = assertIs<Expression.Member>(indexed.receiver)
        assertEquals("member", member.name)
        assertEquals(MemberKeyword.IDENTIFIER, member.keyword)
        assertNull(indexed.index)
        assertFalse((NameRole.VALUE to ":member") in capture.names)
        assertEquals(DeclarationReference.Local(DeclarationId(500)), assertIs<Expression.This>(capture.expression("this")).owner)
        assertIs<Expression.Super>(capture.expression("super"))
    }

    @Test
    fun mixedArgumentsAndTrailingLambdaKeepTheirDistinctPositionsAndBodyReferences() {
        val capture = Capture()
        val call = assertIs<Expression.Call>(capture.expression("take(1, { x: int -> x }, 2)"))
        assertEquals(3, call.normal.size)
        assertIs<Expression.Literal>(call.normal[0].expression)
        val lambda = assertIs<Expression.Lambda>(call.normal[1].expression)
        assertTrue(lambda.explicitParameters)
        assertEquals(listOf(DeclarationId(1010)), lambda.parameters)
        assertEquals(DeclarationId(101), lambda.declaration)
        assertEquals(BodyId(201), lambda.body)
        assertIs<Expression.Name>(lambda.tail)
        assertIs<TypeSyntax.Name>(capture.lambdaParameterTypes.single())
        assertIs<Expression.Literal>(call.normal[2].expression)
        assertNull(call.trailingLambda)

        val member = assertIs<Expression.Member>(capture.expression("items.map { item -> item }.size"))
        assertEquals("size", member.name)
        val trailingCall = assertIs<Expression.Call>(member.receiver)
        assertEquals(MemberKeyword.MAP, assertIs<Expression.Member>(trailingCall.callee).keyword)
        assertNotNull(trailingCall.trailingLambda)
        val implicitCall = assertIs<Expression.Call>(capture.expression("take { it }"))
        assertFalse(assertIs<Expression.Lambda>(implicitCall.trailingLambda).explicitParameters)
        assertNull(assertIs<Expression.Lambda>(capture.expression("{ x -> x; }")).tail)
    }

    @Test
    fun containersAndTextRetainExpressionsOrderDuplicatesAndDecodedContent() {
        val capture = Capture()
        val compound = assertIs<Expression.Compound>(capture.expression("{first: effect(1), first: effect(2)}"))
        assertEquals(listOf("first", "first"), compound.entries.map { it.key })
        compound.entries.forEach { assertIs<Expression.Call>(it.value) }
        val list = assertIs<Expression.ListValue>(capture.expression("[1, effect(2)]"))
        assertIs<Expression.Call>(list.elements[1])
        val quoted = assertIs<Expression.Literal>(capture.expression("\"a\\\"b\\\\c\""))
        assertEquals(ConstantValue.Text("a\"b\\c"), capture.payload(quoted.constant))
        val string = assertIs<Expression.InterpolatedString>(capture.expression("\"\"\"hello \${effect(1)}!\"\"\""))
        assertEquals(TextPart.Text("hello "), string.parts[0])
        assertIs<Expression.Call>(assertIs<TextPart.Interpolation>(string.parts[1]).expression)
        assertEquals(TextPart.Text("!"), string.parts[2])
        val command = capture.bridge.commandParts(parse("/say hi \${value}") { orgCommand() })
        assertEquals(TextPart.Text("say hi "), command[0])
        assertIs<TextPart.Interpolation>(command[1])
        val filter = assertIs<Expression.PropertyFilter>(capture.expression("@e[tag = effect(1), tag = effect(2)]"))
        assertEquals(listOf("tag", "tag"), filter.entries.map { it.name })
    }

    @Test
    fun numericFactsPreserveTypesFloatBitsCoordinatesAndArrayKinds() {
        val capture = Capture()
        val number = assertIs<Expression.Literal>(capture.expression("-0.0f"))
        assertEquals(ConstantValue.FloatBits((-0.0f).toRawBits()), capture.payload(number.constant))
        val typed = assertIs<ConstantValue.Typed>(capture.constants.getValue(number.constant))
        assertEquals(capture.builtins.getValue("float"), typed.type)
        val coordinate = assertIs<Expression.Coordinate>(capture.expression("~ ^1 2"))
        assertEquals(listOf(DimensionMode.RELATIVE, DimensionMode.LOCAL, DimensionMode.ABSOLUTE), coordinate.dimensions.map { it.mode })
        assertNull(coordinate.dimensions[0].value)
        assertEquals(ConstantValue.Integral(1), capture.payload(assertNotNull(coordinate.dimensions[1].value)))
        val array = assertIs<Expression.NbtArray>(capture.expression("[L;1L,-2L]"))
        assertEquals(NbtArrayElement.LONG, array.element)
        assertEquals(listOf(ConstantValue.Integral(1), ConstantValue.Integral(-2)), array.values.map(capture::payload))
        val range = assertIs<Expression.Range>(capture.expression("..limit"))
        assertNull(range.lower)
        assertIs<Expression.Name>(range.upper)
    }

    @Test
    fun typeCaptureKeepsReadonlyOperationsAndAllStructuredTypeForms() {
        val capture = Capture()
        val applied = assertIs<TypeSyntax.Apply>(capture.type("author:Box<int, n + 1>"))
        assertEquals(TypeRef(800), applied.resolved)
        assertIs<TypeSyntax.Name>(applied.base)
        assertIs<Expression.TypeValue>(applied.readonly[0])
        assertIs<Expression.Binary>(applied.readonly[1])
        val function = assertIs<TypeSyntax.Function>(capture.type("(list<vec3>, (int | string)) -> void"))
        val container = assertIs<TypeSyntax.Container>(function.parameters[0])
        assertEquals(ContainerKind.LIST, container.kind)
        assertEquals(3L, assertIs<TypeSyntax.Vector>(container.argument).dimensions)
        assertIs<TypeSyntax.Union>(function.parameters[1])
        assertIs<TypeSyntax.Void>(function.result)
        assertEquals(FunctionExecution.NORMAL, function.execution)
        assertEquals(2, assertIs<TypeSyntax.Intersection>(capture.type("(Left & Right)")).components.size)
        val entity = assertIs<TypeSyntax.Entity>(capture.type("entity<2, \"pig\", 'cow'>"))
        assertEquals(ConstantValue.Integral(2), capture.payload(assertNotNull(entity.limit)))
        assertEquals(listOf("pig", "cow"), entity.names)
        val anonymous = assertIs<TypeSyntax.AnonymousData>(capture.type("data { var x: int = 1 }"))
        assertEquals(DeclarationId(700), anonymous.declaration)
        assertEquals(1, capture.anonymousCount)
    }

    @Test
    fun missingBodyCapturerFailsExplicitlyInsteadOfSavingSource() {
        val delegate = Capture()
        val bindings = object : ExpressionCaptureBindings by delegate {
            override fun lambda(site: Parser.InlineLambdaContext, capture: ExpressionBridge): Expression.Lambda =
                throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", "No statement capturer")
        }
        val failure = assertFailsWith<SyntaxCaptureException> {
            ExpressionBridge(bindings).expression(parse("{ x -> x }") { expression() })
        }
        assertEquals("LIB_BODY_UNSUPPORTED", failure.code)
        assertFailsWith<IllegalArgumentException> { TypeRef(0) }
        assertFailsWith<IllegalArgumentException> { Expression.Range(ScopeId(1), null, null) }
        assertFailsWith<IllegalArgumentException> { Dimension(DimensionMode.ABSOLUTE, null) }
    }

    @Test
    fun legacyReferenceArgumentsAreRejectedEvenWhenParserBuildsTheirDiagnosticRule() {
        val parser = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString("take(ref value)")))).apply {
            removeErrorListeners()
        }
        val source = parser.expression()
        assertTrue(parser.numberOfSyntaxErrors > 0)
        val failure = assertFailsWith<SyntaxCaptureException> { Capture().bridge.expression(source) }
        assertEquals("LIB_BODY_UNSUPPORTED", failure.code)
    }
}
