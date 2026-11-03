package top.mcfpp.test.library

import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import kotlin.test.*

class LibrarySyntaxCodecTest {
    private val q = ScopeId(1)
    private val environment = EnvironmentId(1)
    private val local = DeclarationReference.Local(DeclarationId(1))

    private class References : SyntaxReferenceValidator {
        val declarations = arrayListOf<DeclarationReference>()
        val environments = arrayListOf<EnvironmentId>()
        val types = arrayListOf<TypeRef>()
        val constants = arrayListOf<ConstantRef>()
        val annotations = arrayListOf<AnnotationId>()
        override fun declaration(reference: DeclarationReference) { declarations.add(reference) }
        override fun environment(reference: EnvironmentId) { environments.add(reference) }
        override fun type(reference: TypeRef) { types.add(reference) }
        override fun constant(reference: ConstantRef) { constants.add(reference) }
        override fun annotation(reference: AnnotationId) { annotations.add(reference) }
    }

    private fun scope(owner: Long = 1, parent: ScopeId? = null) = ScopeRecord(DeclarationId(owner), parent, emptyList())
    private fun strings(syntax: LibrarySyntax) = LibraryStrings.of(SyntaxCodec.collectStrings(syntax))

    @Test fun allFixedNodeKindsAndOutsideReferenceCategoriesRoundTrip() {
        val literal = Expression.Literal(q, ConstantRef(1))
        val name = Expression.Name(q, NameUse.Lookup("run", "library", environment, listOf(local)))
        val type = TypeSyntax.Name(q, NameUse.Local(DeclarationId(3)), TypeRef(1))
        val lambda = Expression.Lambda(q, DeclarationId(2), listOf(DeclarationId(20)), true, BodyId(2), literal)
        val expressions = listOf<Expression>(
            literal, name, Expression.This(q, local), Expression.Super(q, local), Expression.TypeValue(q, type),
            Expression.Group(q, null), Expression.Unary(q, "-", literal), Expression.Binary(q, "&&", literal, name),
            Expression.Cast(q, literal, type), Expression.Member(q, name, "map", MemberKeyword.MAP),
            Expression.Index(q, name, null), Expression.PropertyFilter(q, name, listOf(PropertyEntry("limit", literal))),
            Expression.Call(q, name, listOf(literal), listOf(Argument(lambda)), lambda), lambda,
            Expression.Range(q, literal, null),
            Expression.Coordinate(q, listOf(Dimension(DimensionMode.ABSOLUTE, ConstantRef(1)), Dimension(DimensionMode.RELATIVE, null))),
            Expression.Selector(q, SelectorKind.SELF), Expression.ListValue(q, listOf(literal, name)),
            Expression.Compound(q, listOf(CompoundEntry("z", literal), CompoundEntry("a", name))),
            Expression.NbtArray(q, NbtArrayElement.INT, listOf(ConstantRef(1))),
            Expression.InterpolatedString(q, listOf(TextPart.Text(" text\n"), TextPart.Interpolation(name))),
        )
        val types = listOf<TypeSyntax>(type, TypeSyntax.Apply(q, type, listOf(literal), TypeRef(2)),
            TypeSyntax.Container(q, ContainerKind.DICT, type), TypeSyntax.Vector(q, 3),
            TypeSyntax.Entity(q, ConstantRef(1), listOf("minecraft:pig")), TypeSyntax.Union(q, listOf(type)),
            TypeSyntax.Intersection(q, listOf(type)), TypeSyntax.AnonymousData(q, DeclarationId(4)),
            TypeSyntax.Function(q, listOf(type), TypeSyntax.Void(q), FunctionExecution.PAUSED), TypeSyntax.Void(q))
        val empty = Statement.Empty(q)
        val statements = listOf<Statement>(
            Statement.Block(q, listOf(empty)), Statement.Local(q, DeclarationId(10), literal),
            Statement.Evaluate(q, name), Statement.Assign(q, name, "+=", literal),
            Statement.If(q, listOf(ConditionalBranch(literal, empty)), Statement.Return(q, null)),
            Statement.While(q, literal, Statement.Continue(q)), Statement.DoWhile(q, Statement.Break(q), literal),
            Statement.Foreach(q, DeclarationId(11), name, empty), Statement.TryStore(q, empty, NameUse.Declaration(local)),
            Statement.Execute(q, listOf(ExecuteContext(ExecutePath.As, name),
                ExecuteContext(ExecutePath.Members(listOf(name)), literal)), empty),
            Statement.Command(q, listOf(TextPart.Text("say "), TextPart.Interpolation(name))),
            Statement.Break(q), Statement.Continue(q), Statement.Return(q, literal), empty,
        )
        val template = TemplateBody(q, listOf(TemplateMember.Declaration(q, DeclarationId(4)),
            TemplateMember.AnnotationApplication(q, AnnotationId(1), listOf(DeclarationId(4)))))
        val main = Statement.Block(q, statements + expressions.map { Statement.Evaluate(q, it) })
        val original = SyntaxCodec.flatten(listOf(scope(), scope(2, q)), listOf(
            SyntaxBodySource(DeclarationId(1), environment, BodyRole.FUNCTION, main, listOf(local)),
            SyntaxBodySource(DeclarationId(2), environment, BodyRole.LAMBDA, Statement.Block(ScopeId(2), emptyList())),
        ), types.mapIndexed { index, node -> SyntaxRoot(DeclarationId(1), listOf(index.toLong()), node) } +
            SyntaxRoot(DeclarationId(1), listOf(100), template))
        val references = References()
        val encoded = SyntaxCodec.encode(original, strings(original), references)
        val decoded = SyntaxCodec.decode(encoded, strings(original), references)
        assertTrue(decoded.externalReferencesValidated)
        assertEquals(original.nodes, decoded.nodes)
        assertEquals(original.scopes, decoded.scopes)
        assertEquals(original.bodies, decoded.bodies)
        val expectedTags = (0x0101..0x0115).toSet() + (0x0201..0x020a).toSet() +
            (0x0301..0x030f).toSet() + (0x0401..0x0403).toSet()
        assertEquals(expectedTags, decoded.nodes.map { it.tag }.toSet())
        assertTrue(references.declarations.isNotEmpty())
        assertTrue(references.environments.isNotEmpty())
        assertTrue(references.constants.isNotEmpty())
        assertTrue(references.types.isNotEmpty())
        assertEquals(AnnotationId(1), references.annotations.first())
        val reencoded = SyntaxCodec.encode(decoded, strings(original), References())
        assertContentEquals(encoded.nodes, reencoded.nodes)
        assertContentEquals(encoded.scopes, reencoded.scopes)
        assertContentEquals(encoded.bodies, reencoded.bodies)
    }

    @Test fun deterministicRootOrderingSharedDagAndStatementOrderAreRetained() {
        fun source(reverse: Boolean): Pair<LibrarySyntax, Expression.Binary> {
            val shared = Expression.Literal(q, ConstantRef(1))
            val binary = Expression.Binary(q, "+", shared, shared)
            val roots = listOf(SyntaxRoot(DeclarationId(1), listOf(1), binary),
                SyntaxRoot(DeclarationId(1), listOf(0), TypeSyntax.Void(q)))
            val body = Statement.Block(q, listOf(Statement.Return(q, binary), Statement.Command(q, listOf(TextPart.Text("say after")))))
            return SyntaxCodec.flatten(listOf(scope()), listOf(SyntaxBodySource(DeclarationId(1), environment,
                BodyRole.FUNCTION, body)), if (reverse) roots.reversed() else roots) to binary
        }
        val (first, binary) = source(false)
        val second = source(true).first
        val strings = strings(first)
        val bytes = SyntaxCodec.encode(first, strings, References())
        assertContentEquals(bytes.nodes, SyntaxCodec.encode(second, strings, References()).nodes)
        val restored = SyntaxCodec.decode(bytes, strings, References())
        val restoredBinary = restored.node(first.reference(binary)) as Expression.Binary
        assertSame(restoredBinary.left, restoredBinary.right)
        val root = restored.node(restored.body(BodyId(1)).root) as Statement.Block
        assertIs<Statement.Return>(root.statements[0])
        assertIs<Statement.Command>(root.statements[1])
    }

    @Test fun parameterTypesAndInitializerRootsCanBeRegisteredWithoutAFunctionBody() {
        val type = TypeSyntax.Function(q, listOf(TypeSyntax.Vector(q, 3)), TypeSyntax.Void(q))
        val initial = Expression.Literal(q, ConstantRef(1))
        val source = SyntaxCodec.flatten(listOf(scope()), listOf(
            SyntaxBodySource(DeclarationId(2), environment, BodyRole.INITIALIZER, initial),
            SyntaxBodySource(DeclarationId(3), environment, BodyRole.DEFAULT, initial),
        ), listOf(SyntaxRoot(DeclarationId(1), listOf(0, 1), type)))
        val restored = SyntaxCodec.decode(SyntaxCodec.encode(source, strings(source), References()), strings(source), References())
        assertEquals(type, restored.node(source.reference(type)))
        assertEquals(BodyRole.INITIALIZER, restored.body(BodyId(1)).role)
        assertEquals(BodyRole.DEFAULT, restored.body(BodyId(2)).role)
        assertEquals(restored.body(BodyId(1)).root, restored.body(BodyId(2)).root)
    }

    @Test fun lambdaTailIsDistinctAndStagingDoesNotPretendExternalReferencesAreBound() {
        val tail = Expression.Literal(q, ConstantRef(1))
        val lambda = Expression.Lambda(q, DeclarationId(2), emptyList(), false, BodyId(1), tail)
        val source = SyntaxCodec.flatten(listOf(scope()), listOf(
            SyntaxBodySource(DeclarationId(2), environment, BodyRole.LAMBDA, Statement.Block(q, emptyList())),
        ), listOf(SyntaxRoot(DeclarationId(1), listOf(0), lambda)))
        val encoded = SyntaxCodec.encode(source, strings(source), References())
        val staged = SyntaxCodec.decodeUnbound(encoded, strings(source))
        assertFalse(staged.externalReferencesValidated)
        val decodedLambda = staged.node(source.reference(lambda)) as Expression.Lambda
        assertEquals(tail, decodedLambda.tail)
        assertTrue((staged.node(staged.body(BodyId(1)).root) as Statement.Block).statements.isEmpty())
        val failing = object : SyntaxReferenceValidator {
            override fun declaration(reference: DeclarationReference) { error("Declaration shell has not been bound") }
            override fun environment(reference: EnvironmentId) { error("Environment is not bound") }
            override fun type(reference: TypeRef) { error("Type is not bound") }
            override fun constant(reference: ConstantRef) { error("Constant is not bound") }
            override fun annotation(reference: AnnotationId) { error("Annotation is not bound") }
        }
        assertFailsWith<IllegalStateException> { SyntaxCodec.validateAll(staged, failing) }
        assertFalse(staged.externalReferencesValidated)
        assertFailsWith<IllegalStateException> { SyntaxCodec.decode(encoded, strings(source), failing) }
        SyntaxCodec.validateAll(staged, References())
        assertTrue(staged.externalReferencesValidated)
        assertFailsWith<LibraryFormatException> {
            SyntaxCodec.flatten(listOf(scope()), listOf(
                SyntaxBodySource(DeclarationId(2), environment, BodyRole.LAMBDA, Statement.Block(q, listOf(Statement.Evaluate(q, tail)))),
            ), listOf(SyntaxRoot(DeclarationId(1), listOf(0), lambda)))
        }
    }

    @Test fun fixedMinimalNodeRecordUsesProtocolTagAndScopeInsteadOfParserNumbers() {
        val expected = hex("00000001 0000000a 030f 00000001 00000000")
        val source = LibrarySyntax(listOf(Statement.Empty(q)), listOf(scope()), emptyList())
        assertContentEquals(expected, SyntaxCodec.encodeNodes(source, LibraryStrings.of(emptyList())))
        val decoded = SyntaxCodec.decode(EncodedSyntax(expected, SyntaxCodec.encodeScopes(source.scopes),
            SyntaxCodec.encodeBodies(emptyList())), LibraryStrings.of(emptyList()), References())
        assertEquals(Statement.Empty(q), decoded.node(NodeRef(1)))
    }

    @Test fun malformedNodeReferencesKindsCyclesAndPayloadBoundariesAreRejected() {
        fun malformed(nodes: ByteArray): LibraryFormatException = assertFailsWith {
            SyntaxCodec.decodeUnbound(EncodedSyntax(nodes, SyntaxCodec.encodeScopes(listOf(scope())),
                SyntaxCodec.encodeBodies(emptyList())), LibraryStrings.of(emptyList()))
        }
        val unknown = malformed(rawNodes(listOf(65535 to byteArrayOf())))
        assertEquals(LibraryFormatCode.UNSUPPORTED, unknown.code)
        assertEquals(LibrarySectionId.NODES, unknown.sectionId)
        assertEquals(1L, unknown.recordIndex)
        val self = LibraryBinaryWriter().apply { bool(true); u32(1) }.toByteArray()
        assertEquals(LibraryFormatCode.CORRUPT, malformed(rawNodes(listOf(SyntaxTags.GROUP to self))).code)
        val dangling = LibraryBinaryWriter().apply { bool(true); u32(2) }.toByteArray()
        malformed(rawNodes(listOf(SyntaxTags.GROUP to dangling)))
        val evaluateType = LibraryBinaryWriter().apply { u32(2) }.toByteArray()
        malformed(rawNodes(listOf(SyntaxTags.EVALUATE to evaluateType, SyntaxTags.VOID_TYPE to byteArrayOf())))
        malformed(rawNodes(listOf(SyntaxTags.EMPTY to byteArrayOf(9))))
        malformed(rawNodes(listOf(SyntaxTags.LITERAL to byteArrayOf(0, 0, 0, 0))))
        val valid = rawNodes(listOf(SyntaxTags.EMPTY to byteArrayOf()))
        malformed(valid.copyOf(valid.size - 1))
    }

    @Test fun scopesBodiesAndCrossBodyLambdaCyclesAreCheckedBeforeUse() {
        val node = Statement.Empty(q)
        val scopeCycle = LibrarySyntax(listOf(node), listOf(scope(parent = q)), emptyList())
        assertEquals(LibrarySectionId.SCOPES, assertFailsWith<LibraryFormatException> { SyntaxCodec.validateDomain(scopeCycle) }.sectionId)
        val missingScope = LibrarySyntax(listOf(Statement.Empty(ScopeId(2))), listOf(scope()), emptyList())
        assertFailsWith<LibraryFormatException> { SyntaxCodec.validateDomain(missingScope) }
        val badBody = LibrarySyntax(listOf(node), listOf(scope()), listOf(BodyRecord(DeclarationId(1), environment,
            BodyRole.DEFAULT, NodeRef(1))))
        assertEquals(LibrarySectionId.BODIES, assertFailsWith<LibraryFormatException> { SyntaxCodec.validateDomain(badBody) }.sectionId)
        val lambda = Expression.Lambda(q, DeclarationId(2), emptyList(), false, BodyId(1), null)
        val cyclicBody = Statement.Block(q, listOf(Statement.Evaluate(q, lambda)))
        assertFailsWith<LibraryFormatException> { SyntaxCodec.flatten(listOf(scope()), listOf(
            SyntaxBodySource(DeclarationId(2), environment, BodyRole.LAMBDA, cyclicBody))) }
        val statements = arrayListOf<Statement>()
        val directCycle = Statement.Block(q, statements)
        statements.add(directCycle)
        assertFailsWith<IllegalArgumentException> { SyntaxCodec.flatten(listOf(scope()), listOf(
            SyntaxBodySource(DeclarationId(1), environment, BodyRole.FUNCTION, directCycle))) }
    }

    private fun rawNodes(records: List<Pair<Int, ByteArray>>): ByteArray = LibraryBinaryWriter().apply {
        list(records) { (tag, payload) -> record { u16(tag); u32(1); record { rawBytes(payload) } } }
    }.toByteArray()

    private fun hex(value: String) = value.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
