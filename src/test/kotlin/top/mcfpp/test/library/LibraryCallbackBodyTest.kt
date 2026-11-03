package top.mcfpp.test.library

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.StructuredNameBinding
import top.mcfpp.analysis.StructuredSessionBindings
import top.mcfpp.analysis.SignaturePreparationRequest
import top.mcfpp.analysis.SignaturePreparationResult
import top.mcfpp.analysis.SignaturePreparationSession
import top.mcfpp.analysis.preparationSpan
import top.mcfpp.analysis.lambda.LambdaLowering
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.model.LibraryTarget
import top.mcfpp.library.syntax.*
import top.mcfpp.mni.api.FunctionSignature
import top.mcfpp.mni.api.FunctionKey
import top.mcfpp.mni.api.ImmutableFunctionRef
import top.mcfpp.mni.api.ImmutableParameterView
import top.mcfpp.mni.api.FunctionKind
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.SessionBodyRepository
import top.mcfpp.model.function.StructuredBodyContexts
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*

class LibraryCallbackBodyTest {
    private fun call(source: String): P.FunctionCallContext {
        val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source, "consumer"))))
        val context = parser.functionCall()
        assertEquals(0, parser.numberOfSyntaxErrors)
        return context
    }

    @Test
    fun importedNamedCallbackUsesPreparedPrivateDirectoryAndFreshStructuredBodies() {
        val config = Project.config
        val ignore = top.mcfpp.CompileSettings.ignoreStdLib
        val debug = top.mcfpp.CompileSettings.isDebug
        var session: LibraryImportSession? = null
        try {
            Project.config = ProjectConfig()
            MCFPPStringTest.readFromString("""
                namespace published.callback
                func hidden(value:int):int{return value+1}
                func entry(value:int):int{return hidden(value)}
            """.trimIndent(), arrayOf("-ignoreStdLib"), version = "26.3")
            assertEquals(0, Project.errorCount)
            val author = GlobalScope.localNamespaces.getValue("published.callback").scope.functions.values.flatten()
            author.single { it.identifier == "hidden" }.accessModifier = Member.AccessModifier.PRIVATE
            val identity = LibraryIdentity(PackageKey("fixture:callback", "1"), "main")
            val selected = OwnedLibrarySelection(identity, LibraryTarget("26.3"), listOf(author.single { it.identifier == "entry" }),
                author.map { requireNotNull(it.declarationFile) }.distinct())
            val bytes = LibraryModelCodec().encode(LibraryExporter.capture(selected))
            Project.init()
            session = LibraryImportSession()
            session.stage(bytes, identity); session.bindAll(); session.commit()
            val consumer = MCFPPFile(GlobalScope.getOrCreateNamespace("consumer"))
            consumer.field.importedNamespaceField["published.callback"] = GlobalScope.libNamespaces.getValue("published.callback").scope
            MCFPPFile.currFile = consumer
            Project.currNamespace = consumer.namespace.identifier
            val caller = Function("caller", consumer.namespace.identifier, null).apply { declarationFile = consumer }
            val syntax = call("entry(2)")
            val hidden = session.functions().single { it.identifier.contains("hidden") }
            assertIs<SignaturePreparationResult.Rejected>(SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(hidden), emptyList(), preparationSpan(syntax), caller = caller)))
            val result = CompilerBindingEnvironment.prepareCall(syntax, caller)
            val prepared = assertIs<CompilerCallPreparationResult.Ready>(result,
                (result as? CompilerCallPreparationResult.Rejected)?.diagnostics?.joinToString("\n") { it.message })
            val target = prepared.call.target
            assertNotNull(target.sourceBody?.structuredSyntax)
            assertTrue(target.sourceBody!!.structuredRequired)
            assertTrue(session.functions().all { it.ast == null && it.commands.isEmpty() })
            val signature = FunctionSignature.of(target.function.parameters(), target.function.returnType())
            val callback = SourceCallbackSyntax(emptyList(), signature, prepared.environment.binding.types.inlineType(signature)!!,
                "entry", SourceSpan("consumer", 0, 5, 1, 1, 1, 6), functionTarget = target)
            val bound = SnapshotSourceBodyBinder().bind(callback, prepared.environment.binding, prepared.environment.declarationEnvironments)
            val returned = assertIs<BoundSourceStatement.Return>(bound.body.statements.single())
            val invocation = assertIs<BoundExprNode.Call>(returned.value!!.node).sourceCall
            assertEquals(hidden.declarationId.value, invocation.target.compilerDeclarationId)
            assertEquals(bound.parameters.single().symbol.symbol,
                assertIs<BoundExprNode.Read>(assertIs<BoundCallArgument.Value>(invocation.arguments.single()).expression.node).location.root.symbol)
            val first = SnapshotSourceBodyBinder.parseCallbackBlock(callback)
            val second = SnapshotSourceBodyBinder.parseCallbackBlock(callback)
            assertNotSame(first.syntax, second.syntax)
            assertNotSame(first.syntax.start, second.syntax.start)
            assertNotNull(StructuredBodyContexts.origin(first.syntax))
            session.close()
            assertFailsWith<IllegalStateException> { SnapshotSourceBodyBinder.parseCallbackBlock(callback) }
        } finally {
            session?.close(); MCFPPFile.currFile = null; Project.config = config
            top.mcfpp.CompileSettings.ignoreStdLib = ignore
            top.mcfpp.CompileSettings.isDebug = debug
        }
    }

    private class LambdaFixture : StructuredSessionBindings, StatementProjectionBindings {
        private val q = ScopeId(1)
        val repository = SessionBodyRepository()
        val run = Function("run", "fixture", null)
        val inner = Expression.Lambda(ScopeId(2), DeclarationId(2), emptyList(), false, BodyId(2),
            Expression.Name(ScopeId(2), NameUse.Local(DeclarationId(10))))
        val outer = Expression.Lambda(q, DeclarationId(1), emptyList(), false, BodyId(1), null)
        val executable = Expression.Lambda(q, DeclarationId(3), emptyList(), false, BodyId(3), Expression.Literal(q, ConstantRef(4)))
        val library = repository.register(this, this)
        private val integer = TypeSyntax.Name(q, NameUse.Declaration(DeclarationReference.Local(DeclarationId(90))), TypeRef(1))
        private val float = TypeSyntax.Name(q, NameUse.Declaration(DeclarationReference.Local(DeclarationId(91))), TypeRef(2))
        override fun name(use: NameUse, role: NameRole): ProjectedName = when (use) {
            is NameUse.Local -> ProjectedName(if (use.target == DeclarationId(10)) "saved" else "zero", binding = StructuredNameBinding.Frame(use.target, "saved"))
            is NameUse.Declaration -> when ((use.target as DeclarationReference.Local).target.value) {
                90L -> ProjectedName("int", binding = StructuredNameBinding.Type(MCFPPBaseType.Int))
                91L -> ProjectedName("float", binding = StructuredNameBinding.Type(MCFPPBaseType.Float))
                else -> ProjectedName("run", binding = StructuredNameBinding.Functions(listOf(run)))
            }
            else -> error("No unresolved fixture lookup")
        }
        override fun declaration(reference: DeclarationReference) = StructuredNameBinding.Functions(listOf(run))
        override fun constant(reference: ConstantRef): ConstantValue = when (reference.value) {
            1L -> ConstantValue.Integral(1)
            2L -> ConstantValue.Typed(TypeRef(1), ConstantRef(1))
            3L -> ConstantValue.Integral(7)
            4L -> ConstantValue.Typed(TypeRef(1), ConstantRef(3))
            5L -> ConstantValue.FloatBits((-0.0f).toRawBits())
            6L -> ConstantValue.Typed(TypeRef(2), ConstantRef(5))
            else -> error("Unknown constant")
        }
        override fun type(reference: TypeRef): Any = if (reference == TypeRef(1)) MCFPPBaseType.Int else MCFPPBaseType.Float
        override fun builtinKey(reference: TypeRef) = if (reference == TypeRef(1)) "int" else "float"
        override fun name(declaration: DeclarationId) = if (declaration == DeclarationId(10)) "saved" else "zero"
        override fun local(declaration: DeclarationId) = LocalDeclarationFacts(name(declaration), LocalStorage.VAR,
            if (declaration == DeclarationId(10)) integer else float, null)
        override fun lambdaParameter(declaration: DeclarationId): LambdaParameterSyntax = error("No parameters")
        override fun body(reference: BodyId): SemanticNode = when (reference.value) {
            1L -> Statement.Block(q, listOf(Statement.Local(q, DeclarationId(10), Expression.Literal(q, ConstantRef(2))),
                Statement.Local(q, DeclarationId(11), Expression.Literal(q, ConstantRef(6))),
                Statement.Evaluate(q, Expression.Call(q,
                    Expression.Name(q, NameUse.Declaration(DeclarationReference.Local(DeclarationId(50)))), emptyList(), listOf(Argument(inner))))))
            2L -> Statement.Block(ScopeId(2), emptyList())
            3L -> Statement.Block(q, listOf(Statement.Command(q, listOf(TextPart.Text("say lambda")))))
            else -> error("Unknown body")
        }
        fun project(lambda: Expression.Lambda): P.InlineLambdaContext {
            val projection = CurrentSyntaxProjection(this, this)
            val context = projection.lambda(lambda)
            repository.registerProjection(repository.handle(library, lambda.body), projection, context)
            return context
        }
    }

    @Test
    fun nestedCallbacksKeepChildKeysLocalCapturesTailAndLiteralFacts() {
        Project.init()
        val fixture = LambdaFixture()
        try {
            val catalog = SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPPrivateType.Void), callbacks = listOf(
                SourceCallbackSignature(emptyList(), SourceCallbackType.Declared(MCFPPBaseType.Int.typeId)),
                SourceCallbackSignature(emptyList(), SourceCallbackType.Declared(MCFPPPrivateType.Void.typeId))))
            val innerSignature = FunctionSignature.of(emptyList(), catalog.ref(MCFPPBaseType.Int))
            val outerSignature = FunctionSignature.of(emptyList(), catalog.ref(MCFPPPrivateType.Void))
            val callbackType = catalog.inlineType(innerSignature)!!
            val run = SourceFunction("run", ImmutableFunctionRef(FunctionKey(77, "run"), "run",
                FunctionSignature.of(listOf(ImmutableParameterView("callback", callbackType, ParamMode.INLINE, innerSignature)), catalog.ref(MCFPPPrivateType.Void)),
                FunctionKind.MACRO), compilerDeclarationId = fixture.run.declarationId.value)
            val syntax = fixture.project(fixture.outer)
            val sourceBuffer = syntax.start.inputStream.getText(org.antlr.v4.runtime.misc.Interval(0, syntax.stop.stopIndex))
            val actual = BindingEnvironment(catalog, emptyList(), listOf(run), sourceText = sourceBuffer, sourceFile = "library-lambda")
            val prepared = SnapshotSourceCodeBinder().callback(syntax, outerSignature, actual)
            assertNotNull(prepared.structuredSyntax)
            assertNull(prepared.frozenSyntax)
            val bound = SnapshotSourceBodyBinder().bind(prepared, actual)
            val saved = assertIs<BoundSourceStatement.Local>(bound.body.statements[0]).symbol
            val zero = assertIs<BoundSourceStatement.Local>(bound.body.statements[1])
            assertEquals(top.mcfpp.mni.api.ConstantValue.FloatBits((-0.0f).toRawBits()), zero.initialValue!!.constant)
            val request = bound.nestedCallbacks.single()
            assertNotNull(request.syntax.structuredSyntax)
            val nested = request.bind()
            assertEquals(saved.symbol, nested.captures.getValue(saved.symbol).symbol)
            assertFalse(nested.captures.getValue(saved.symbol).writable)
            assertEquals(saved.symbol, assertIs<BoundExprNode.Read>(assertIs<BoundSourceStatement.Return>(nested.body.statements.single()).value!!.node).location.root.symbol)
            assertNotEquals(bound.returnRegion, nested.returnRegion)
            assertFailsWith<SourceBindingFailure> { SnapshotSourceBodyBinder.parseCallbackBlock(prepared.copy(structuredSyntax = null, structuredRequired = true)) }
        } finally { fixture.repository.close() }
    }

    @Test
    fun structuredLambdaLowersDistinctTailOnceAndKeepsRuntimeSessionOwnership() {
        Project.config.version = "26.3"
        Project.init()
        val fixture = LambdaFixture()
        val file = MCFPPFile(GlobalScope.getOrCreateNamespace("fixture"))
        MCFPPFile.currFile = file
        try {
            val caller = Function("caller", "fixture", null).apply { declarationFile = file }
            val signature = MCFPPFunctionType(emptyList(), MCFPPBaseType.Int)
            val value = caller.runInFunction { LambdaLowering.literal(fixture.project(fixture.executable), signature) }
            val target = assertNotNull((value as FunctionValue).knownTarget())
            val handle = assertNotNull(target.bodyHandle)
            assertSame(fixture.repository, handle.repository)
            val lowered = handle.repository.body(handle).root as Statement.Block
            assertEquals(1, lowered.statements.filterIsInstance<Statement.Return>().size)
            val returned = caller.runInFunction { LambdaLowering.invokeKnown(value, emptyList(), target) } as MCInt
            top.mcfpp.optimization.OptimizationPipeline.finish()
            assertEquals(0, Project.errorCount)
            val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .associate { it.namespaceID.toString() to it.commands.analyzeAll() }
            val machine = ScoreCommandExecutor(listOf(Commands.stackIn().analyze()) + caller.commands.analyzeAll() + Commands.stackOut().analyze(), functions)
            assertEquals(7, machine.read(returned))
            assertEquals(listOf("lambda"), machine.messages)
            assertEquals(0, machine.stackDepth)
            fixture.repository.close()
            assertFailsWith<IllegalStateException> { handle.project() }
        } finally { fixture.repository.close(); MCFPPFile.currFile = null }
    }
}
