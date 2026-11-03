package top.mcfpp.test.library

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.TerminalNode
import top.mcfpp.antlr.mcfppLexer as L
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.library.compiler.*
import top.mcfpp.library.syntax.*
import kotlin.test.*

class LibraryExpressionProjectionTest {
    private class Tables : ExpressionCaptureBindings, ProjectionBindings {
        val constants = linkedMapOf<ConstantRef, ConstantValue>()
        val types = linkedMapOf<String, TypeRef>()
        val names = linkedMapOf<Pair<NameRole, ProjectedName>, DeclarationId>()
        var projection: ExpressionProjection? = null
        val capture = ExpressionBridge(this)
        override fun scope(site: ParserRuleContext) = projection?.origin(site)?.node?.scope ?: ScopeId(7)
        override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse {
            projection?.origin(site)?.nameUse?.let { return it }
            check(projection == null) { "A projected bound name fell back to caller lookup: $name" }
            return NameUse.Local(names.getOrPut(role to ProjectedName(name, namespace)) { DeclarationId(names.size + 1L) })
        }
        override fun name(use: NameUse, role: NameRole): ProjectedName = when (use) {
            is NameUse.Local -> names.entries.single { it.value == use.target && it.key.first == role }.key.second.copy(binding = use.target)
            is NameUse.Lookup -> ProjectedName(use.name, use.namespace, use.candidates)
            is NameUse.Declaration -> ProjectedName("boundDeclaration", binding = use.target)
        }
        override fun builtinType(key: String, site: ParserRuleContext) = types.getOrPut(key) { TypeRef(types.size + 1L) }
        override fun internConstant(value: ConstantValue, site: ParserRuleContext) = intern(value)
        fun intern(value: ConstantValue): ConstantRef = constants.entries.firstOrNull { it.value == value }?.key
            ?: ConstantRef(constants.size + 1L).also { constants[it] = value }
        override fun constant(reference: ConstantRef) = constants.getValue(reference)
        override fun builtinKey(reference: TypeRef) = types.entries.singleOrNull { it.value == reference }?.key
        override fun type(reference: TypeRef): Any = reference
        override fun resolvedType(site: ParserRuleContext) = projection?.origin(site)?.typeReference ?: TypeRef(800)
        override fun owner(site: ParserRuleContext) = DeclarationReference.Local(DeclarationId(500))
        override fun lambda(site: P.InlineLambdaContext, capture: ExpressionBridge): Expression.Lambda =
            projection?.origin(site)?.node as? Expression.Lambda ?: Expression.Lambda(ScopeId(7), DeclarationId(90),
                site.lambdaParameterList()?.lambdaParameter()?.mapIndexed { index, _ -> DeclarationId(100L + index) }.orEmpty(),
                site.lambdaParameterList() != null, BodyId(10), site.lambdaBody().lambdaTailExpression()?.expression()?.let(capture::expression))
        override fun anonymousType(site: P.AnonymousTemplateTypeContext, capture: ExpressionBridge) = DeclarationId(900)
    }

    private fun <T : ParserRuleContext> parse(text: String, rule: P.() -> T): T {
        val errors = mutableListOf<String>()
        val listener = object : BaseErrorListener() {
            override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                     charPositionInLine: Int, msg: String?, e: RecognitionException?) { errors += "$line:$charPositionInLine $msg" }
        }
        val lexer = L(CharStreams.fromString(text)).apply { removeErrorListeners(); addErrorListener(listener) }
        val tokens = CommonTokenStream(lexer)
        val parser = P(tokens).apply { removeErrorListeners(); addErrorListener(listener) }
        return parser.rule().also {
            assertEquals(emptyList(), errors, text)
            assertEquals(Token.EOF, tokens.LA(1), text)
        }
    }

    private fun project(tables: Tables, structures: ProjectionStructures = object : ProjectionStructures {}) =
        ExpressionProjection(tables, structures).also { tables.projection = it }

    private fun roundtrip(source: String): Pair<Expression, P.ExpressionContext> {
        val tables = Tables()
        val original = tables.capture.expression(parse(source) { expression() })
        val projection = project(tables)
        val context = projection.expression(original)
        assertEquals(original, tables.capture.expression(context), source)
        assertEquals(original, projection.origin(context)?.node)
        checkTree(context, projection)
        return original to context
    }

    private fun checkTree(context: ParserRuleContext, projection: ExpressionProjection) {
        context.children.orEmpty().forEach { child ->
            assertSame(context, child.parent)
            when (child) {
                is ParserRuleContext -> checkTree(child, projection)
                is TerminalNode -> {
                    val token = child.symbol
                    assertSame(projection.source, token.inputStream)
                    assertSame(projection.source, token.tokenSource)
                    assertEquals(token.text, token.inputStream.getText(Interval(token.startIndex, token.stopIndex)))
                }
            }
        }
    }

    @Test
    fun binaryPrecedenceExplicitFieldsAndBoundNamesRoundtrip() {
        val (_, context) = roundtrip("a + b * c < d && e || f | g")
        val pipe = context.commonBinaryOperatorExpression()
        assertEquals(listOf("|"), pipe.op.map { it.text })
        val or = pipe.conditionalOrExpression(0)
        assertEquals(listOf("||"), or.op.map { it.text })
        assertEquals(listOf("&&"), or.conditionalAndExpression(0).op.map { it.text })
        roundtrip("a - b - c")
        roundtrip("a - (b - c)")
        roundtrip("!a as bool")
        roundtrip("(a + b) * c")
        roundtrip("()")
        roundtrip("this")
        roundtrip("super")
        roundtrip("author.lib:take<int>(value)")
    }

    @Test
    fun valuesSelectorsContainersStringsAndRangesKeepCurrentGrammarFields() {
        roundtrip("receiver.member[][index].next(1)")
        roundtrip("@e[tag = effect(1), tag = effect(2)]")
        val (_, compound) = roundtrip("{first: effect(1), first: effect(2)}")
        fun all(site: ParserRuleContext): List<ParserRuleContext> = listOf(site) + site.children.orEmpty()
            .filterIsInstance<ParserRuleContext>().flatMap(::all)
        assertEquals(listOf("first", "first"), all(compound).filterIsInstance<P.NbtKeyValuePairContext>().map { it.key.text })
        roundtrip("[1, effect(2), \"text\"]")
        roundtrip("\"a\\\"b\\\\c\"")
        roundtrip("\"\"\"hello \${effect(1)}!\"\"\"")
        roundtrip("-0.0f")
        roundtrip("[B;1b,-2b]")
        roundtrip("[I;1,-2]")
        roundtrip("[L;1L,-2L]")
        roundtrip("~ ^-0.5 1.0d")
        val (_, range) = roundtrip("start..end")
        val projectedRange = all(range).filterIsInstance<P.RangeContext>().single()
        assertNotNull(projectedRange.num1)
        assertNotNull(projectedRange.num2)
        roundtrip("..end")
        roundtrip("start..")
    }

    @Test
    fun typesKeepApplicationsFunctionResultsEntityConstraintsAndBindings() {
        listOf("list<int>", "ImmutableList<dict<string>>", "author.lib:Box<int, value>",
            "vec3", "entity<2, \"minecraft:pig\", \"minecraft:cow\">", "(int|string)", "(First & Second)",
            "(int,list<string>)->void", "()->int").forEach { source ->
            val tables = Tables()
            val original = tables.capture.type(parse(source) { type() })
            val projection = project(tables)
            val context = projection.type(original)
            assertEquals(original, tables.capture.type(context), source)
            checkTree(context, projection)
        }
    }

    @Test
    fun sourceCoordinatesUseUtf16AndEveryAccessGetsFreshContextsAndTokens() {
        val tables = Tables()
        val original = tables.capture.expression(parse("[\"😀\",\"line\"]") { expression() })
        val projection = project(tables)
        val first = projection.expression(original)
        val firstText = projection.source.slice(first)
        val second = projection.expression(original)
        assertNotSame(first, second)
        assertNotSame(first.start, second.start)
        assertEquals(firstText, projection.source.slice(second))
        assertEquals(firstText.length, first.stop.stopIndex - first.start.startIndex + 1)
        assertEquals(firstText.length, second.start.startIndex)
        checkTree(first, projection)
        checkTree(second, projection)
    }

    @Test
    fun constantsWithoutLiteralSpellingsKeepExactBitsAndDefaultValueChecksItsCategory() {
        val tables = Tables()
        val ref = tables.intern(ConstantValue.DoubleBits(0x7ff8000000001234L))
        val original = Expression.Literal(ScopeId(7), ref)
        val projection = project(tables)
        val context = projection.value(original)
        assertEquals("null", context.text)
        assertEquals(ConstantValue.DoubleBits(0x7ff8000000001234L), projection.origin(context)?.constant)
        assertEquals(ref, projection.origin(context)?.constantReference)
        assertFailsWith<SyntaxProjectionException> { projection.value(Expression.Binary(ScopeId(7), "+", original, original)) }
        assertFailsWith<SyntaxProjectionException> { projection.type(TypeSyntax.Void(ScopeId(7))) }
    }

    @Test
    fun lambdaProviderReceivesDeclarationBodyAndDistinctTailAndCallsPreserveArgumentCategories() {
        val tables = Tables()
        val original = tables.capture.expression(parse("take(1, { x -> x }, 2) { it }") { expression() })
        var calls = 0
        val provider = object : ProjectionStructures {
            override fun lambda(node: Expression.Lambda, projection: ExpressionProjection): P.InlineLambdaContext {
                calls++
                assertEquals(DeclarationId(90), node.declaration)
                assertEquals(BodyId(10), node.body)
                return projection.node(P.InlineLambdaContext(null, -1), node) {
                    projection.token(this, L.LCURL, "{")
                    if (node.explicitParameters) {
                        projection.child(this, projection.node(P.LambdaParameterListContext(null, -1), node) {
                            node.parameters.forEachIndexed { index, _ ->
                                if (index != 0) projection.token(this, L.COMMA, ",")
                                projection.child(this, projection.node(P.LambdaParameterContext(null, -1), node) {
                                    projection.token(this, L.Identifier, "parameter$index")
                                })
                            }
                        })
                        projection.token(this, L.ARROW, "->")
                    }
                    projection.child(this, projection.node(P.LambdaBodyContext(null, -1), node) {
                        node.tail?.let { tail ->
                            projection.child(this, projection.node(P.LambdaTailExpressionContext(null, -1), tail) {
                                projection.child(this, projection.expression(tail))
                            })
                        }
                    })
                    projection.token(this, L.RCURL, "}")
                }
            }
        }
        val projection = project(tables, provider)
        val context = projection.expression(original)
        assertEquals(2, calls)
        assertEquals(original, tables.capture.expression(context))
        val call = context.lambdaCallExpression().functionCall()
        val arguments = call.arguments().normalArgs().callArgumentList().callArgument()
        assertEquals(3, arguments.size)
        assertNotNull(arguments[1].inlineLambda())
        assertNotNull(context.lambdaCallExpression().inlineLambda())
        checkTree(context, projection)
        listOf("items.map { item -> item }.size", "handlers[0] { it }").forEach { source ->
            val chainTables = Tables()
            val chain = chainTables.capture.expression(parse(source) { expression() })
            val chainProjection = project(chainTables, provider)
            val projected = chainProjection.expression(chain)
            assertEquals(chain, chainTables.capture.expression(projected), source)
            checkTree(projected, chainProjection)
        }
    }
}
