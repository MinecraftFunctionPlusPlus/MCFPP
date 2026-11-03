package top.mcfpp.test.context

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.analysis.lambda.LambdaCaptureRules
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.core.lang.LambdaCaptureResult
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.context.*
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.io.KryoManager
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import kotlin.test.*

class ContextBorrowingTest {
    private fun parser(source: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source, "context-borrow.mcfpp"))))

    private class Fixture {
        val namespace = Namespace("context-borrow")
        val file = MCFPPFile(namespace)
        val caller = Function("caller", namespace.identifier, null)
        init {
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            caller.declarationFile = file
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            MCFPPFile.currFile = file
            Function.currFunction = caller
        }
        fun put(value: Var<*>): Var<*> {
            value.bindDeclaration()
            caller.scope.putVar(value.identifier, value, true)
            return value
        }
        fun observe(): NativeFunction = NativeFunction("observe", namespace.identifier,
            ContextBorrowProvider::class.java.getMethod("observe", NativeCallContext::class.java)).apply {
            declarationFile = file
            returnType = MCFPPPrivateType.Void
            appendNormalParam(MCFPPEntityType.NormalSelector, "target")
            appendNormalParam(ContextTypes.Position, "position")
            this@Fixture.namespace.scope.functions[identifier] = arrayListOf(this)
        }
        fun helper(): Function = Function("helper", namespace.identifier,
            parser("{ var alias = target; observe(alias, position); }").curlBlock()).apply {
            declarationFile = file
            returnType = MCFPPPrivateType.Void
            appendNormalParam(MCFPPEntityType.NormalSelector, "target")
            appendNormalParam(ContextTypes.Position, "position")
            this@Fixture.namespace.scope.functions[identifier] = arrayListOf(this)
        }
        private fun parser(source: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source, "context-helper.mcfpp"))))
    }

    private fun isolated(action: Fixture.() -> Unit) {
        val before = CompilerStateJournal.checkpoint()
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            Function.forcedField = null
            ContextBorrowProvider.seen.clear()
            Fixture().action()
        } finally {
            before.restore()
        }
    }

    private fun encoder() = DirectContextCommandEncoder { requireNotNull(ContextBorrowing.currentContext) }

    @Test fun sourceLocalAliasesAndCapturesKeepExecutorAndPositionPayloads() = isolated {
        ContextSession().use { session ->
            val position = session.root().withPosContext(session.openScope())
            lateinit var saved: Var<*>
            ContextBorrowing.withContext(position) {
                put(ExecutionContextVar("ctx", position, encoder = encoder()))
                val visitor = MCFPPImVisitor()
                for (source in listOf("var local = ctx", "var saved: entity = local.executor", "var destination = local.pos"))
                    visitor.visitStatement(parser(source).statement())
                assertEquals(0, Project.errorCount)
                saved = StorageAccess.read(assertNotNull(caller.scope.getVar("saved")))
                val target = assertIs<BoundExecutorVar>(saved)
                val destination = assertIs<BoundPositionVar>(StorageAccess.read(assertNotNull(caller.scope.getVar("destination"))))
                assertSame(position.executor, target.binding)
                assertSame(position.pos, destination.binding)
                val copy = assertIs<BoundExecutorVar>(StorageAccess.capture(target))
                assertSame(target.binding, copy.binding)
                assertSame(target.contextScope, copy.contextScope)
                assertEquals(target.binding.provenance, copy.binding.provenance)
                assertIs<SpecializationArgument.Borrow>(SpecializationKeys.argument(copy))
                assertNull(StorageAccess.snapshot(copy))
                assertFalse(StorageAccess.hasRuntimeRepresentation(copy))
                assertTrue(StorageAccess.hasActualPayload(copy))
            }
            assertTrue(StorageAccess.read(saved).isError, "A closed callback cannot be used through a saved adapter")
        }
    }

    @Test fun ordinarySynchronousHelperReceivesOriginalHandlesAndActiveDescendantContext() = isolated {
        observe()
        val helper = helper()
        ContextSession().use { session ->
            val position = session.root().withPosContext(session.openScope())
            ContextBorrowing.withContext(position) {
                val passengers = position.onRelation(ContextRelation.PASSENGERS, session.openScope(position.scope))
                ContextBorrowing.withContext(passengers) {
                    val self = BoundExecutorVar("self", passengers.executor, encoder())
                    val pos = BoundPositionVar("position", position.pos, encoder())
                    val returned = helper.invoke(listOf(self, pos), null)
                    assertFalse(returned.isError)
                    assertEquals(0, Project.errorCount)
                    assertEquals(listOf(passengers.source to position.source), ContextBorrowProvider.seen)
                    assertSame(passengers, ContextBorrowing.currentContext)
                    assertTrue(caller.child.any { it.actualCallBody })
                }
                assertSame(position, ContextBorrowing.currentContext)
            }
        }
        assertNull(ContextBorrowing.currentContext)
    }

    private fun Fixture.projectAndEmit(source: String) {
        val preparation = CompilerBindingEnvironment.prepareCall(parser(source).functionCall(), caller)
        val ready = assertIs<CompilerCallPreparationResult.Ready>(preparation,
            (preparation as? CompilerCallPreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty())
        ExpansionSession(ExpansionSite(ready.call.source, "context-borrow-test"), ExpansionBudget(),
            CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = namespace.identifier),
            CompilerTransaction(CompilerTransactionBackend {
                object : PreparedCompilerCommit { override fun apply() = Unit; override fun rollback() = Unit }
            })).use { expansion ->
            val bindings = CompilerSourceBindings.capture(ready.environment, expansion, caller)
            val environment = SourceProjectionEnvironment(bindings)
            val arguments = SourceCodeProjector(environment).arguments(ready.call)
            val arena = expansion.arena
            val metadata = arena.metadata(arguments.scope, expansion.origin, bindings.loweringTarget.executionContext)
            val invocation = arena.expr(metadata, ready.call.target.function.returnType(),
                ExprNode.Call(ready.call.target.function, arguments.arguments, arguments.readonlyArguments))
            val code = arena.block(metadata, arguments.orderedPrelude.statements() + arena.stmt(metadata, StmtNode.Eval(invocation)))
            val plan = environment.lower(code)
            CompilerBridge(bindings).prepare(plan).prepare().apply()
        }
    }

    @Test fun detachedSourceAndNativeArgumentsUseBorrowRegistersWithoutRuntimeSerialization() = isolated {
        observe()
        helper()
        ContextSession().use { session ->
            val position = session.root().withPosContext(session.openScope())
            ContextBorrowing.withContext(position) {
                val passengers = position.onRelation(ContextRelation.PASSENGERS, session.openScope(position.scope))
                ContextBorrowing.withContext(passengers) {
                    put(ExecutionContextVar("ctx", passengers, encoder = encoder()))
                    put(BoundPositionVar("position", position.pos, encoder()))
                    projectAndEmit("observe(ctx.executor, position)")
                    projectAndEmit("helper(ctx.executor, position)")
                    assertEquals(0, Project.errorCount)
                    assertEquals(List(2) { passengers.source to position.source }, ContextBorrowProvider.seen)
                    assertTrue(caller.commands.none { "scoped-borrow" in it.analyze() || "borrowKind" in it.analyze() })
                }
            }
        }
    }

    @Test fun actualReturnClosureGlobalContainerAndErasedStorageEdgesRejectEscape() = isolated {
        val outer = MCFPPEntityType.NormalSelector.buildUnConcrete("outer").also { it.bindDeclaration() }
        ContextSession().use { session ->
            val position = session.root().withPosContext(session.openScope())
            ContextBorrowing.withContext(position) {
                val ctx = put(ExecutionContextVar("ctx", position, encoder = encoder()))
                val self = put(BoundExecutorVar("self", position.executor, encoder()))
                val global = MCFPPEntityType.NormalSelector.buildUnConcrete("global").apply { isStatic = true; bindDeclaration() }
                assertTrue(StorageAccess.write(global, self).isError)
                assertTrue(StorageAccess.write(outer, self).isError)
                assertTrue(StorageAccess.view(self, MCFPPBaseType.Any).isError)
                assertTrue(StorageAccess.listLiteral(MCFPPListType(MCFPPEntityType.NormalSelector), listOf(self)).isError)
                val returnedErrors = Project.errorCount
                val returner = Function("returner", namespace.identifier, null).apply { returnType = MCFPPEntityType.NormalSelector }
                returner.assignReturnVar(self)
                assertTrue(Project.errorCount > returnedErrors)
                val captures = LambdaCaptureRules.capture(ctx)
                assertContains(assertIs<LambdaCaptureResult.Rejected>(captures).diagnostic.message, "ordinary closure")
                val closureErrors = Project.errorCount
                MCFPPExprVisitor().visitExpression(parser("{ var stolen = ctx; }").expression(),
                    MCFPPFunctionType(emptyList(), MCFPPPrivateType.Void))
                assertTrue(Project.errorCount > closureErrors, "The real lambda lowering boundary must reject the capture")
            }
        }
    }

    @Test fun distinctBorrowOriginsLoseUsableFlowFactsInsteadOfRebinding() = isolated {
        ContextSession().use { session ->
            val first = session.root()
            val second = session.root()
            ContextBorrowing.withContext(first) {
                val alias = MCFPPEntityType.NormalSelector.buildUnConcrete("alias")
                alias.bindDeclaration()
                val binding = StorageAccess.ensure(alias)
                fun branch(context: ExecutionContext) = FlowFacts().apply {
                    write(binding.place, ValueFacts(TypeKnowledge.Exact(alias.type.typeId),
                        ValueKnowledge.Borrow(ContextBorrowPayload.Executor(context.executor, encoder()))))
                }
                binding.data.retainCompilerValue()
                binding.data.facts.replaceWith(branch(first).join(branch(second)))
                assertEquals(ValueKnowledge.Unknown, binding.data.facts.read(binding.place)!!.value)
                assertTrue(StorageAccess.read(alias).isError)
            }
        }
    }

    @Test fun ordinarySelfSelectorInsideControlledScopeKeepsItsExistingEscapeAndCaptureContract() = isolated {
        lateinit var ordinary: SelectorVar
        lateinit var copied: Var<*>
        ContextSession().use { session ->
            val position = session.root().withPosContext(session.openScope())
            ContextBorrowing.withContext(position) {
                ordinary = SelectorVar(EntitySelector('s'), "ordinary")
                ordinary.bindDeclaration()
                copied = StorageAccess.capture(ordinary)
                assertNull(ContextBorrowing.payload(ordinary))
                assertNull(ContextBorrowing.payload(copied))
                assertIs<LambdaCaptureResult.Captured>(LambdaCaptureRules.capture(ordinary))
            }
        }
        assertEquals("@s", assertIs<SelectorVar>(StorageAccess.read(copied)).toCommandPart().analyze())
        assertEquals(0, Project.errorCount)
    }

    @Test fun controlledLifetimeRestoresCallerOnExceptionsAndRejectsForeignSessions() = isolated {
        ContextSession().use { session ->
            val parent = session.root().withPosContext(session.openScope())
            ContextBorrowing.withContext(parent) {
                val child = parent.onRelation(ContextRelation.PASSENGERS, session.openScope(parent.scope))
                assertFailsWith<UnsupportedOperationException> {
                    ContextBorrowing.withContext(child) { throw UnsupportedOperationException("callback exit") }
                }
                assertSame(parent, ContextBorrowing.currentContext)
                assertFalse(child.scope.isActive)
                ContextSession().use { foreign ->
                    assertFailsWith<ContextAccessException> { ContextBorrowing.withContext(foreign.root()) {} }
                }
                assertSame(parent, ContextBorrowing.currentContext)
            }
        }
        assertNull(ContextBorrowing.currentContext)
    }

    @Test fun publicTypesRoundTripWhileBorrowCapabilitiesCannotBecomeLibraryValues() = isolated {
        for (type in listOf(ContextTypes.ExecutionContext, ContextTypes.PosContext, ContextTypes.EntityContext,
            ContextTypes.RelationContext, ContextTypes.Position)) {
            assertEquals(type, MCFPPType.parseFromString(type.typeName, caller.scope))
            val output = Output(4096, -1)
            KryoManager.kryo.writeClassAndObject(output, type)
            assertEquals(type, KryoManager.kryo.readClassAndObject(Input(output.toBytes())))
        }
        ContextSession().use { session ->
            val context = session.root().withPosContext(session.openScope())
            ContextBorrowing.withContext(context) {
                val value = put(BoundExecutorVar("self", context.executor, encoder()))
                val key = SpecializationKeys.argument(value)
                assertIs<SpecializationArgument.Borrow>(key)
                assertFalse(SpecializationKeys.isConstant(value))
                assertNull(StorageAccess.snapshot(value))
                assertFails { KryoManager.kryo.writeClassAndObject(Output(4096, -1), value) }
                assertFails { KryoManager.kryo.writeClassAndObject(Output(4096, -1), assertNotNull(ContextBorrowing.payload(value))) }
                assertFalse(TypeRelations.isSubtype(ContextTypes.PosContext, MCFPPBaseType.Object))
            }
        }
    }
}

object ContextBorrowProvider {
    val seen = arrayListOf<Pair<ContextSourceId, ContextSourceId>>()

    @JvmStatic @NoExternalWrites
    fun observe(context: NativeCallContext) {
        context.withArguments { arguments ->
            val target = assertIs<BoundExecutorVar>(arguments[0])
            val position = assertIs<BoundPositionVar>(arguments[1])
            assertEquals("@s", target.toEntityTargetCommand().analyze())
            assertEquals("~ ~ ~", position.toCommandPart().analyze())
            seen += target.binding.source to position.requireBinding().source
        }
    }
}
