package top.mcfpp.test.library

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.compiletime.CompileTimeFunction
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.Generic
import top.mcfpp.model.Member
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import java.lang.reflect.Method
import kotlin.test.*

class LibraryFunctionTest {
    private class Arena : FunctionCaptureBindings, CompilerTypeBindings {
        var currentOwner = DeclarationId(1)
        private var currentScope = ScopeId(1)
        private var nextScope = 1L
        private var nextDeclaration = 1L
        private val visible = mutableListOf(linkedMapOf<String, DeclarationId>())
        val nodes = mutableListOf<TypeSyntax>()
        val bodies = mutableListOf<SyntaxBodySource>()
        val sources = java.util.IdentityHashMap<Function, FunctionSourceSignature>()
        val parameterTypes = mutableMapOf<top.mcfpp.type.TypeId.Opaque, LibraryType.Parameter>()
        val receiver = DataTemplate("Receiver", "fixture.author")
        val packageKey = PackageKey("fixture:types", "1")
        val receiverRef = DeclarationReference.Dependency(1,
            DeclarationKey("fixture.author", emptyList(), 3, "Receiver"))
        val types = CompilerTypeTableCapture(this)
        override fun declaration(id: top.mcfpp.type.TypeId): DeclarationReference? = if (id == receiver.getType().typeId) receiverRef else null
        override fun parameter(id: top.mcfpp.type.TypeId.Opaque) = parameterTypes[id]
        override fun environment(function: Function) = EnvironmentId(1)
        override fun key(function: Function) = DeclarationKey(function.namespace, emptyList(), 2, function.identifier,
            DeclarationSignature((when (function) { is NativeFunction -> function.readOnlyParams; is Generic<*> -> function.readOnlyParams; else -> emptyList() }).map { KeyType.Builtin("Type") },
                function.normalParams.map { if (it.type is MCFPPGenericParamType) KeyType.Parameter(0, ParameterRole.READONLY, 0) else KeyType.Builtin("int") },
                if (function is ExtensionFunction) KeyType.Nominal(packageKey, "main", "fixture.author", listOf("Receiver")) else null))
        override fun declarationOwner(function: Function): DeclarationId? = null
        override fun reserveParameter(owner: DeclarationId, parameter: FunctionParam, readonly: Boolean, index: Int) = DeclarationId(++nextDeclaration)
        override fun <T> inFunction(function: Function, owner: DeclarationId, environment: EnvironmentId, action: () -> T): T {
            currentOwner = owner
            return action()
        }
        override fun publishParameter(id: DeclarationId, parameter: FunctionParam, readonly: Boolean, index: Int) {
            visible.last()[parameter.identifier] = id
            if (readonly && parameter.type == MCFPPConcreteType.Type) {
                val view = parameter.function.scope.getType(parameter.identifier) as? MCFPPGenericParamType
                if (view != null) parameterTypes[view.typeId as top.mcfpp.type.TypeId.Opaque] =
                    LibraryType.Parameter(DeclarationReference.Local(currentOwner), ParameterRole.READONLY, index.toLong())
            }
        }
        override fun type(value: MCFPPType, owner: DeclarationId, declaredSyntax: NodeRef?) = types.type(value)
        override fun constant(value: CompilerValue, types: Map<top.mcfpp.type.TypeId, MCFPPType>) = this.types.constant(value)
        override fun storeNode(owner: DeclarationId, node: TypeSyntax) = NodeRef(nodes.size + 1L).also { nodes += node }
        override fun storeBody(body: SyntaxBodySource) = BodyId(bodies.size + 1L).also { bodies += body }
        override fun source(function: Function) = sources[function] ?: FunctionDeclarations.sourceSignature(function)
        override fun scope(site: ParserRuleContext) = currentScope
        override fun <T> withScope(site: ParserRuleContext, owner: DeclarationId?, action: (ScopeId) -> T): T {
            val previous = currentScope
            currentScope = ScopeId(++nextScope)
            visible += linkedMapOf<String, DeclarationId>()
            return try { action(currentScope) } finally { visible.removeAt(visible.lastIndex); currentScope = previous }
        }
        override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse {
            val target = visible.asReversed().firstNotNullOfOrNull { it[name] }
            return if (target != null) NameUse.Local(target)
            else NameUse.Declaration(DeclarationReference.Dependency(1, DeclarationKey(namespace ?: "builtin", emptyList(), 3, name)))
        }
        override fun builtinType(key: String, site: ParserRuleContext) = types.type(LibraryBuiltinTypes.restore(key))
        override fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef = when (value) {
            is ConstantValue.Integral -> types.constant(CompilerValue.Integral(value.value))
            is ConstantValue.Typed -> types.constant(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(3)))
            else -> error("Fixture uses only integral defaults")
        }
        override fun reserveLocal(site: Parser.FieldDeclarationContext) = DeclarationId(++nextDeclaration)
        override fun completeLocal(declaration: DeclarationId, facts: LocalDeclarationFacts) {}
        override fun publishLocal(declaration: DeclarationId, name: String, site: ParserRuleContext) { visible.last()[name] = declaration }
        override fun reserveIterationVariable(site: Parser.ForeachStatementContext, iterable: Expression) = DeclarationId(++nextDeclaration)
        override fun destination(name: String, site: Parser.TryStoreStatementContext) = this.name(name, null, NameRole.VALUE, site)
        override fun executePathPart(site: Parser.VarContext, capture: StatementBridge): Expression = error("No execute fixture")
        override fun reserveLambda(site: Parser.InlineLambdaContext): LambdaAllocation = error("No lambda fixture")
        override fun completeLambdaParameters(allocation: LambdaAllocation, parameters: List<LambdaParameterSyntax>, explicit: Boolean) {}
        override fun completeLambda(allocation: LambdaAllocation, body: LambdaBodyCapture) {}
        override fun receiverCapture(owner: DeclarationReference): DeclarationReference? = null
        override fun templateMember(site: Parser.TemplateMemberDeclarationContext, capture: StatementBridge): TemplateMember = error("No template fixture")
    }

    private class Restore(val model: LibraryDeclarations, tables: LibraryTables, val method: Method) : FunctionRestoreBindings {
        val file = MCFPPFile(Namespace("fixture.author"))
        val receiver = DataTemplate("Receiver", "fixture.author")
        val views = mutableMapOf<Pair<DeclarationId, Int>, MCFPPGenericParamType>()
        val retainedDefaults = linkedMapOf<DeclarationId, BodyId?>()
        private val types = CompilerTypeTableRestore(tables, object : CompilerTypeBindings {
            override fun restoreDeclaration(reference: DeclarationReference): MCFPPType = receiver.getType()
            override fun restoreParameter(value: LibraryType.Parameter): MCFPPType = views.getValue(
                (value.owner as DeclarationReference.Local).target to value.index.toInt())
        })
        override fun type(reference: TypeRef) = types.type(reference)
        override fun constant(reference: ConstantRef) = RestoredDefault(types.constant(reference))
        override fun environment(reference: EnvironmentId) = file
        override fun owner(reference: DeclarationId): DataTemplate? = null
        override fun nativeMethod(binding: NativeBinding) = method
        override fun readonlyType(owner: DeclarationId, index: Int, parameter: FunctionParam, type: MCFPPGenericParamType) { views[owner to index] = type }
        override fun parameterRestored(id: DeclarationId, parameter: FunctionParam, record: ValueDeclaration) { retainedDefaults[id] = record.defaultBody }
    }

    companion object {
        @JvmStatic fun nativeFixture(context: NativeCallContext) { assertNotNull(context) }
        @JvmStatic fun wrongNativeFixture(value: Int) { assertTrue(value >= 0) }
        private fun method() = LibraryFunctionTest::class.java.getDeclaredMethod("nativeFixture", NativeCallContext::class.java)
        private fun parse(source: String): Parser.CompilationUnitContext {
            val parser = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
            val result = parser.compilationUnit()
            assertEquals(0, parser.numberOfSyntaxErrors, source)
            assertEquals(Token.EOF, parser.inputStream.LA(1))
            return result
        }
        private fun model(captured: CapturedFunction): LibraryDeclarations = LibraryDeclarations(
            listOf(captured.declaration) + captured.parameters.toSortedMap(compareBy { it.value }).values,
            listOf(EnvironmentRecord("fixture.author", emptyList())))
    }

    private fun fixture(kind: String, arena: Arena): Function {
        val readonly = if (kind == "generic" || kind == "generic-extension" || kind == "native") "<T>" else ""
        val prefix = when (kind) { "inline" -> "inline "; "const" -> "const "; else -> "" }
        val receiver = if (kind.endsWith("extension")) "Receiver." else ""
        val ending = if (kind == "native") "= Host.nativeFixture" else "{ return value; }"
        val source = parse("${prefix}func ${receiver}sample$readonly(value: int = 3): int $ending").typeDeclaration().single().declarations()
        val body = source.functionDeclaration()?.curlBlock() ?: source.inlineFunctionDeclaration()?.curlBlock()
            ?: source.compileTimeFuncDeclaration()?.curlBlock() ?: source.extensionFunctionDeclaration()?.curlBlock()
        val function: Function = when (kind) {
            "ordinary" -> Function("sample", "fixture.author", body)
            "generic" -> GenericFunction("sample", "fixture.author", assertNotNull(body))
            "inline" -> InlineFunction("sample", "fixture.author", assertNotNull(body))
            "const" -> CompileTimeFunction("sample", "fixture.author", assertNotNull(body))
            "extension" -> ExtensionFunction("sample", arena.receiver, "fixture.author", assertNotNull(body))
            "generic-extension" -> GenericExtensionFunction("sample", arena.receiver, "fixture.author", assertNotNull(body))
            else -> NativeFunction("sample", "fixture.author", method())
        }
        val signature = if (kind == "native") source.nativeFuncDeclaration().functionDeclarationPart().let {
            FunctionSourceSignature(it.functionParams(), it.functionReturnType())
        } else assertNotNull(FunctionDeclarations.sourceSignature(function))
        arena.sources[function] = signature
        if (readonly.isNotEmpty()) {
            val parameter = FunctionParam(MCFPPConcreteType.Type, "T", function, isReadOnly = true)
            when (function) { is NativeFunction -> function.readOnlyParams += parameter; is Generic<*> -> function.readOnlyParams += parameter }
        }
        function.normalParams += FunctionParam(MCFPPBaseType.Int, "value", function, true).apply {
            defaultContext = signature.parameters.normalParams().parameterList().parameter().single().value()
            defaultValue = CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(3))
            defaultTypes = mapOf(MCFPPBaseType.Int.typeId to MCFPPBaseType.Int)
        }
        function.returnType = MCFPPBaseType.Int
        function.accessModifier = Member.AccessModifier.PROTECTED
        function.isFinal = true
        function.isOverride = true
        function.addTag(FunctionTag("fixture.author", "tag"))
        return function
    }

    @Test
    fun realFunctionCategoriesAndSignaturesSurviveCodecAndFreshHeadRestoration() {
        for (kind in listOf("ordinary", "generic", "inline", "const", "extension", "generic-extension", "native")) {
            val arena = Arena()
            val original = fixture(kind, arena)
            val captured = FunctionDeclarations.capture(original, DeclarationId(1), arena)
            val model = model(captured)
            val strings = LibraryStrings.of(FunctionCodec.collectStrings(model))
            val decoded = LibraryDeclarations(FunctionCodec.decodeDeclarations(FunctionCodec.encodeDeclarations(model.declarations, strings), strings),
                FunctionCodec.decodeEnvironments(FunctionCodec.encodeEnvironments(model.environments, strings), strings))
            FunctionCodec.validate(decoded)
            assertEquals(model.declarations, decoded.declarations)
            val restore = Restore(decoded, arena.types.snapshot(), method())
            val head = FunctionDeclarations.restoreHead(DeclarationId(1), decoded.declaration(DeclarationId(1)) as FunctionDeclaration,
                { decoded.declaration(it) as ValueDeclaration }, restore)
            assertEquals(original.javaClass, head.function.javaClass, kind)
            assertEquals(MCFPPBaseType.Int, head.function.normalParams.single().type)
            assertEquals(MCFPPBaseType.Int, head.function.returnType)
            assertEquals(Member.AccessModifier.PROTECTED, head.function.accessModifier)
            assertTrue(head.function.isFinal && head.function.isOverride)
            assertSame(restore.file, head.function.declarationFile)
            assertSame(head.function, head.function.normalParams.single().function)
            assertEquals(original.normalParams.single().defaultValue, head.function.normalParams.single().defaultValue)
            assertNotNull(restore.retainedDefaults.getValue(captured.declaration.normal.single()))
            captured.declaration.readonly.forEach { assertNull(restore.retainedDefaults.getValue(it)) }
            assertNull(head.function.ast, "No body projection provider was installed")
            if (kind != "native") {
                val body = assertNotNull(head.body)
                val root = arena.bodies[(body.value - 1).toInt()].root
                assertIs<Statement.Return>(assertIs<Statement.Block>(root).statements.single())
            }
            if (head.function is ExtensionFunction) assertSame(restore.receiver, head.function.owner)
        }
    }

    @Test
    fun readonlyTypeReferencesRemainBoundToTheirRestoredOwnerAndIndex() {
        val arena = Arena()
        val function = fixture("generic", arena) as GenericFunction
        val source = parse("func sample<T>(value: T): T { return value; }").typeDeclaration().single().declarations().functionDeclaration()
        function.ast = source.curlBlock()
        arena.sources[function] = source.functionDeclarationPart().let { FunctionSourceSignature(it.functionParams(), it.functionReturnType()) }
        val type = MCFPPGenericParamType("T", arrayListOf())
        function.scope.putType("T", type)
        function.normalParams.single().apply { this.type = type; hasDefault = false; defaultValue = null; defaultContext = null }
        function.returnType = type
        val captured = FunctionDeclarations.capture(function, DeclarationId(1), arena)
        val model = model(captured)
        val restore = Restore(model, arena.types.snapshot(), method())
        val head = FunctionDeclarations.restoreHead(DeclarationId(1), captured.declaration, { model.declaration(it) as ValueDeclaration }, restore)
        val restored = head.function as GenericFunction
        assertTrue(restored.readOnlyParams.single().isReadOnly)
        assertSame(restore.views.getValue(DeclarationId(1) to 0), restored.normalParams.single().type)
        assertSame(restored.normalParams.single().type, restored.returnType)
        assertNotEquals(type.typeId, restored.returnType.typeId)
    }

    @Test
    fun importsKeepOrderWildcardAliasFromAndResolvedPackages() {
        val source = parse("import first.ns:*\nimport second.ns:Thing as Alias from pkg\nfunc sample() {}")
        val environment = FunctionDeclarations.environment("author", source.importDeclaration()) { listOf(PackageKey("fixture:pkg", "2")) }
        assertEquals(listOf("first.ns", "second.ns"), environment.imports.map { it.targetNamespace })
        assertNull(environment.imports[0].member)
        assertEquals("Alias", environment.imports[1].alias)
        assertEquals("pkg", environment.imports[1].from)
        assertEquals(listOf(PackageKey("fixture:pkg", "2")), environment.imports[1].resolvedPackages)
        val model = LibraryDeclarations(emptyList(), listOf(environment))
        val strings = LibraryStrings.of(FunctionCodec.collectStrings(model))
        assertEquals(model.environments, FunctionCodec.decodeEnvironments(FunctionCodec.encodeEnvironments(model.environments, strings), strings))
    }

    @Test
    fun nonAbstractOrdinaryBodiesAndNativeDescriptorsCannotBeSilentlyDroppedOrRebound() {
        val arena = Arena()
        val ordinary = fixture("ordinary", arena).apply { ast = null }
        assertFailsWith<SyntaxCaptureException> { FunctionDeclarations.capture(ordinary, DeclarationId(1), arena) }
        val nativeArena = Arena()
        val native = FunctionDeclarations.capture(fixture("native", nativeArena), DeclarationId(1), nativeArena)
        val model = model(native)
        val wrong = LibraryFunctionTest::class.java.getDeclaredMethod("wrongNativeFixture", Int::class.javaPrimitiveType)
        assertFailsWith<IllegalArgumentException> {
            FunctionDeclarations.restoreHead(DeclarationId(1), native.declaration, { model.declaration(it) as ValueDeclaration },
                Restore(model, nativeArena.types.snapshot(), wrong))
        }
        val malformed = native.declaration.copy(execution = FunctionExecutionKind.ORDINARY, native = null, body = null)
        val strings = LibraryStrings.of(FunctionCodec.collectStrings(model))
        assertFailsWith<IllegalArgumentException> { FunctionCodec.encodeDeclarations(listOf(malformed), strings) }
    }
}
