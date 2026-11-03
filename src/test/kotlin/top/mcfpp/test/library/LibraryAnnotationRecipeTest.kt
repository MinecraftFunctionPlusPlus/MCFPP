package top.mcfpp.test.library

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.core.lang.Var
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import kotlin.test.*

class LibraryAnnotationRecipeTest {
    class Provider(values: ArrayList<*>) : Annotation("Provider", "fixture.recipe") {
        init { constructed++ }
        override fun forFunction(function: Function) = Unit
        override fun forDataTemplate(data: DataTemplate) = Unit
        override fun forField(field: Var<*>) = Unit
    }
    private class Capture(
        private val provider: DeclarationReference, private val publicCallee: DeclarationReference? = null,
        private val members: Map<String, List<DeclarationReference>?> = emptyMap()
    ) : StatementCaptureBindings {
        val constants = arrayListOf<ConstantValue>()
        private val scope = ScopeId(1)
        override fun scope(site: ParserRuleContext) = scope
        override fun <T> withScope(site: ParserRuleContext, owner: DeclarationId?, action: (ScopeId) -> T) = action(scope)
        override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse {
            if (name == "make" && namespace == null && publicCallee != null) return NameUse.Declaration(publicCallee)
            require(name == "N" && namespace == null)
            return NameUse.Local(DeclarationId(4))
        }
        override fun builtinType(key: String, site: ParserRuleContext): TypeRef { require(key == "int"); return TypeRef(2) }
        override fun memberCandidates(receiver: Expression, name: String, site: ParserRuleContext) = members[name]
        override fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef {
            val index = constants.indexOf(value)
            return if (index >= 0) ConstantRef(index + 1L) else ConstantRef(constants.size + 1L).also { constants += value }
        }
        override fun annotationConstruction(site: Parser.AnnotationContext, capture: StatementBridge) =
            TemplateMember.AnnotationConstruction(scope, provider, site.annotationArgs()?.value().orEmpty().map(capture::expression), listOf(DeclarationId(5)))
        override fun templateMember(site: Parser.TemplateMemberDeclarationContext, capture: StatementBridge): TemplateMember {
            check(site.templateMember().templateFieldDeclaration() != null) { "The recipe hook must capture the source annotation" }
            return TemplateMember.Declaration(scope, DeclarationId(5))
        }
        override fun reserveLocal(site: Parser.FieldDeclarationContext): DeclarationId = error("No local statement")
        override fun completeLocal(declaration: DeclarationId, facts: LocalDeclarationFacts) = error("No local statement")
        override fun publishLocal(declaration: DeclarationId, name: String, site: ParserRuleContext) = error("No local statement")
        override fun reserveIterationVariable(site: Parser.ForeachStatementContext, iterable: Expression): DeclarationId = error("No loop")
        override fun destination(name: String, site: Parser.TryStoreStatementContext): NameUse = error("No try store")
        override fun executePathPart(site: Parser.VarContext, capture: StatementBridge): Expression = error("No execute")
        override fun reserveLambda(site: Parser.InlineLambdaContext): LambdaAllocation = error("No lambda")
        override fun completeLambdaParameters(allocation: LambdaAllocation, parameters: List<LambdaParameterSyntax>, explicit: Boolean) = error("No lambda")
        override fun completeLambda(allocation: LambdaAllocation, body: LambdaBodyCapture) = error("No lambda")
        override fun receiverCapture(owner: DeclarationReference): DeclarationReference? = null
    }
    companion object {
        private var constructed = 0
        @JvmStatic
        @MNIFunction(genericType = ["Receiver", "Result"], returnType = "Result", returnsConstWhenArgsConst = false)
        fun nativeTypeFactory(context: NativeCallContext): Unit = error("Protocol decoding must not invoke native code")
        private fun common(name: String, kind: Int, owner: DeclarationId? = DeclarationId(1)) = DeclCommon(
            DeclarationKey("fixture.recipe", if (owner == DeclarationId(2)) listOf(DeclarationPathPart(3, "Box")) else emptyList(), kind, name),
            null, name, owner, EnvironmentId(1), DeclarationAccess.PUBLIC, 0)
        private fun parameter(name: String, type: TypeRef, index: Long) = ValueDeclaration(
            DeclCommon(null, null, name, DeclarationId(2), EnvironmentId(1), DeclarationAccess.PRIVATE, 0),
            ValueRole.READONLY_PARAMETER, ValueStorage.PARAMETER, type, null, null, null, parameterIndex = index)
        private fun model(provider: DeclarationReference = DeclarationReference.Local(DeclarationId(6))): LibraryModel {
            val parser = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString("data Box<T,N:int>{@Provider([N+1]) var value:T;}"))))
            val source = parser.templateDeclaration()
            assertEquals(0, parser.numberOfSyntaxErrors); assertEquals(Token.EOF, parser.currentToken.type)
            val capture = Capture(provider)
            val body = StatementBridge(capture).template(source.templateBody())
            val syntax = SyntaxCodec.flatten(listOf(ScopeRecord(DeclarationId(2), null, listOf(DeclarationId(3), DeclarationId(4)))),
                emptyList(), listOf(SyntaxRoot(DeclarationId(2), listOf(0), body)))
            val declarations = listOf<LibraryDeclaration>(
                NamespaceDeclaration(common("fixture.recipe", 1, null), listOf(DeclarationId(2), DeclarationId(6))),
                CompoundDeclaration(common("Box", 3), CompoundCategory.DATA, listOf(DeclarationId(3), DeclarationId(4)), emptyList(), null,
                    listOf(DeclarationId(5)), listOf(DeclarationId(5)), null, emptyList()),
                parameter("T", TypeRef(1), 0), parameter("N", TypeRef(2), 1),
                ValueDeclaration(common("value", 5, DeclarationId(2)), ValueRole.MEMBER, ValueStorage.VAR, TypeRef(3), null, null, null),
                AnnotationProviderDeclaration(common("Provider", 13), AnnotationProviderBinding(null, Provider::class.java.name,
                    listOf("java.util.ArrayList"), "fixture.recipe.provider", 1)))
            return LibraryModel(LibraryManifest(PackageKey("fixture:recipe", "1"), "main", LibraryTarget("26.3"),
                entryEnvironments = listOf(EnvironmentId(1)), rootDeclarations = listOf(DeclarationId(1))),
                LibraryDeclarations(declarations, listOf(EnvironmentRecord("fixture.recipe", emptyList(), DeclarationId(1)))),
                LibraryTables(listOf(LibraryType.Builtin("Type"), LibraryType.Builtin("int"),
                    LibraryType.Parameter(DeclarationReference.Local(DeclarationId(2)), ParameterRole.READONLY, 0)), capture.constants), syntax)
        }
    }

    @Test
    fun actualReadonlyArgumentTreeRoundTripsAsAConstructionWithoutAllocatingAnAnnotation() {
        constructed = 0
        val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
        val original = model()
        val bytes = codec.encode(original)
        val decoded = codec.decode(bytes)
        assertContentEquals(bytes, codec.encode(decoded))
        val body = decoded.syntax.nodes.filterIsInstance<TemplateBody>().single()
        val recipe = assertIs<TemplateMember.AnnotationConstruction>(body.members[0])
        assertEquals(0x0404, recipe.tag)
        assertEquals(DeclarationReference.Local(DeclarationId(6)), recipe.provider)
        assertEquals(listOf(DeclarationId(5)), recipe.targets)
        assertEquals(DeclarationId(5), assertIs<TemplateMember.Declaration>(body.members[1]).declaration)
        val argument = assertIs<Expression.ListValue>(recipe.arguments.single())
        val sum = assertIs<Expression.Binary>(argument.elements.single())
        assertEquals("+", sum.operator)
        assertEquals(NameUse.Local(DeclarationId(4)), assertIs<Expression.Name>(sum.left).use)
        val typed = assertIs<ConstantValue.Typed>(decoded.tables.constant(assertIs<Expression.Literal>(sum.right).constant))
        assertEquals(TypeRef(2), typed.type)
        assertEquals(1L, assertIs<ConstantValue.Integral>(decoded.tables.constant(typed.value)).value)
        assertEquals(listOf(DeclarationId(3), DeclarationId(4)), decoded.syntax.scope(recipe.scope).bindings)
        assertTrue(decoded.annotations.isEmpty() && decoded.stateCells.isEmpty())
        assertEquals(0, constructed)
    }

    @Test
    fun invalidProvidersAndNonExpressionConstructionArgumentsAreRejectedAtTheProtocolBoundary() {
        val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
        val invalidProviders = listOf(DeclarationReference.Local(DeclarationId(1)), DeclarationReference.Local(DeclarationId(40)),
            DeclarationReference.Dependency(1, DeclarationKey("dependency.recipe", emptyList(), 13, "Provider")))
        invalidProviders.forEach { provider ->
            assertEquals(LibraryFormatCode.CORRUPT, assertFailsWith<LibraryFormatException> { codec.encode(model(provider)) }.code)
        }
        val nodes = LibraryBinaryWriter().apply {
            list(listOf(SyntaxTags.ANNOTATION_CONSTRUCTION, SyntaxTags.MEMBER_DECLARATION)) { tag -> record {
                u16(tag); u32(1)
                record {
                    if (tag == SyntaxTags.ANNOTATION_CONSTRUCTION) {
                        TypeTableCodec.writeDeclarationReference(this, DeclarationReference.Local(DeclarationId(6)))
                        list(listOf(2L)) { u32(it) }; list(listOf(5L)) { u32(it) }
                    } else u32(5)
                }
            } }
        }.toByteArray()
        val encoded = EncodedSyntax(nodes, SyntaxCodec.encodeScopes(listOf(ScopeRecord(DeclarationId(2), null, emptyList()))),
            SyntaxCodec.encodeBodies(emptyList()))
        assertEquals(LibraryFormatCode.CORRUPT, assertFailsWith<LibraryFormatException> {
            SyntaxCodec.decodeUnbound(encoded, LibraryStrings.of(emptyList()))
        }.code)
        assertEquals(0, constructed)
    }

    @Test
    fun inferredTypeSharesTheReadonlyInitializerAndRetainsOnlyItsPublicDependencyCall() {
        val base = model()
        val callee = DeclarationReference.Dependency(1, DeclarationKey("dependency.api", emptyList(), 2, "make",
            DeclarationSignature(listOf(KeyType.Builtin("int")), emptyList())))
        val parser = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString("make<N>()"))))
        val initializer = ExpressionBridge(Capture(DeclarationReference.Local(DeclarationId(6)), callee)).expression(parser.expression())
        assertEquals(0, parser.numberOfSyntaxErrors); assertEquals(Token.EOF, parser.currentToken.type)
        val inferred = TypeSyntax.Inferred(ScopeId(1), initializer)
        val syntax = SyntaxCodec.flatten(base.syntax.scopes,
            listOf(SyntaxBodySource(DeclarationId(5), EnvironmentId(1), BodyRole.INITIALIZER, initializer)),
            listOf(SyntaxRoot(DeclarationId(2), listOf(0), base.syntax.nodes.filterIsInstance<TemplateBody>().single()),
                SyntaxRoot(DeclarationId(5), listOf(0), inferred)))
        val declarations = base.declarations.declarations.mapIndexed { index, declaration ->
            if (index == 4) (declaration as ValueDeclaration).copy(type = TypeRef(4), declaredType = syntax.reference(inferred), defaultBody = BodyId(1))
            else declaration
        }
        val fixture = LibraryModel(base.manifest.copy(dependencies = listOf(LibraryDependency(PackageKey("dependency:api", "1"), "main"))),
            LibraryDeclarations(declarations, base.declarations.environments),
            LibraryTables(base.tables.types + LibraryType.Dependent(syntax.reference(inferred), EnvironmentId(1), DeclarationId(2)), base.tables.constants), syntax)
        val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
        val bytes = codec.encode(fixture)
        val decoded = codec.decode(bytes)
        assertContentEquals(bytes, codec.encode(decoded))
        val dependent = assertIs<LibraryType.Dependent>(decoded.tables.type(TypeRef(4)))
        val restored = assertIs<TypeSyntax.Inferred>(decoded.syntax.node(dependent.syntax))
        assertEquals(0x020b, restored.tag)
        assertSame(restored.expression, decoded.syntax.node(decoded.syntax.body(BodyId(1)).root))
        val call = assertIs<Expression.Call>(restored.expression)
        assertEquals(NameUse.Declaration(callee), assertIs<Expression.Name>(call.callee).use)
        assertEquals(NameUse.Local(DeclarationId(4)), assertIs<Expression.Name>(call.readonly.single()).use)
        assertTrue(decoded.tables.types.none { it is LibraryType.Specialized })
        assertEquals(6, decoded.declarations.declarations.size)
        assertEquals(0, constructed)

        val invalidNodes = LibraryBinaryWriter().apply {
            list(listOf(SyntaxTags.INFERRED_TYPE, SyntaxTags.VOID_TYPE)) { tag -> record {
                u16(tag); u32(1); record { if (tag == SyntaxTags.INFERRED_TYPE) u32(2) }
            } }
        }.toByteArray()
        val invalid = EncodedSyntax(invalidNodes, SyntaxCodec.encodeScopes(base.syntax.scopes), SyntaxCodec.encodeBodies(emptyList()))
        assertEquals(LibraryFormatCode.CORRUPT, assertFailsWith<LibraryFormatException> {
            SyntaxCodec.decodeUnbound(invalid, LibraryStrings.of(emptyList()))
        }.code)
    }

    @Test
    fun capturedMembersKeepFrozenPublicCandidatesEmptySetsAndDynamicLookupDistinct() {
        val base = model()
        val candidate = DeclarationReference.Dependency(1, DeclarationKey("dependency.api", emptyList(), 2, "pick",
            DeclarationSignature(emptyList(), emptyList(), KeyType.Builtin("int"))))
        val capture = Capture(DeclarationReference.Local(DeclarationId(6)), members = mapOf("pick" to listOf(candidate), "absent" to emptyList()))
        val expressions = listOf("N.pick()", "N.absent()", "N.dynamic()").map { source ->
            val parser = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
            ExpressionBridge(capture).expression(parser.expression()).also {
                assertEquals(0, parser.numberOfSyntaxErrors); assertEquals(Token.EOF, parser.currentToken.type)
            }
        }
        val calls = Expression.ListValue(ScopeId(1), expressions)
        val syntax = SyntaxCodec.flatten(base.syntax.scopes, emptyList(),
            listOf(SyntaxRoot(DeclarationId(2), listOf(0), base.syntax.nodes.filterIsInstance<TemplateBody>().single()),
                SyntaxRoot(DeclarationId(5), listOf(0), calls)))
        val method = LibraryAnnotationRecipeTest::class.java.getDeclaredMethod("nativeTypeFactory", NativeCallContext::class.java)
        val nativeSlot = LibraryType.Parameter(DeclarationReference.Local(DeclarationId(7)), ParameterRole.NATIVE_TYPE, 1)
        val native = FunctionDeclaration(common("nativeTypeFactory", 2).copy(flags = DeclarationFlags.CONST), FunctionExecutionKind.NATIVE,
            null, emptyList(), emptyList(), TypeRef(4), null, NativeBinding(null, method.declaringClass.name, method.name,
                method.parameterTypes.map { it.name }, method.returnType.name))
        val declarations = listOf((base.declarations.declarations[0] as NamespaceDeclaration).copy(
            members = (base.declarations.declarations[0] as NamespaceDeclaration).members + DeclarationId(7))) +
            base.declarations.declarations.drop(1) + native
        val fixture = LibraryModel(base.manifest.copy(dependencies = listOf(LibraryDependency(PackageKey("dependency:api", "1"), "main"))),
            LibraryDeclarations(declarations, base.declarations.environments), LibraryTables(base.tables.types + nativeSlot, base.tables.constants), syntax)
        val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
        val bytes = codec.encode(fixture)
        val decoded = codec.decode(bytes)
        assertContentEquals(bytes, codec.encode(decoded))
        val restored = decoded.syntax.nodes.filterIsInstance<Expression.Member>().associateBy { it.name }
        assertEquals(listOf(candidate), restored.getValue("pick").candidates)
        assertNotNull(restored.getValue("absent").candidates)
        assertEquals(emptyList<DeclarationReference>(), restored.getValue("absent").candidates)
        assertNull(restored.getValue("dynamic").candidates)
        restored.values.forEach { member ->
            assertEquals(NameUse.Local(DeclarationId(4)), assertIs<Expression.Name>(member.receiver).use)
            assertEquals(MemberKeyword.IDENTIFIER, member.keyword)
            assertTrue(decoded.syntax.nodes.filterIsInstance<Expression.Call>().any { it.callee === member })
        }
        assertEquals(nativeSlot, decoded.tables.type(TypeRef(4)))
        val restoredNative = assertIs<FunctionDeclaration>(decoded.declarations.declaration(DeclarationId(7)))
        assertTrue(restoredNative.readonly.isEmpty() && restoredNative.normal.isEmpty())
        assertEquals(DeclarationFlags.CONST, restoredNative.common.flags and DeclarationFlags.CONST)
        assertFalse(method.getAnnotation(MNIFunction::class.java).returnsConstWhenArgsConst)
        assertEquals("Result", method.getAnnotation(MNIFunction::class.java).genericType[nativeSlot.index.toInt()])
        val slotKey = KeyType.Parameter(0, ParameterRole.NATIVE_TYPE, 1)
        val writer = LibraryBinaryWriter().apply { TypeTableCodec.writeKeyType(this, slotKey) }
        val reader = LibraryBinaryReader(writer.toByteArray())
        assertEquals(slotKey, TypeTableCodec.readKeyType(reader)); reader.requireEnd()
        val badOwner = LibraryModel(fixture.manifest, fixture.declarations,
            LibraryTables(base.tables.types + nativeSlot.copy(owner = DeclarationReference.Local(DeclarationId(2))), base.tables.constants), syntax)
        assertEquals(LibraryFormatCode.CORRUPT, assertFailsWith<LibraryFormatException> { codec.encode(badOwner) }.code)
        assertEquals(0, constructed)
    }
}
