package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniExpansionSessionTest {
    private class Types : TypeService {
        val int = ImmutableTypeRef<IntT>(TypeKey(51, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(51, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(51, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(51, 4), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(51, 5), "void", kind = TypeKind.VOID)
        override fun intType(): TypeRef<IntT> = int
        override fun boolType(): TypeRef<BoolT> = bool
        override fun floatType(): TypeRef<FloatT> = float
        override fun stringType(): TypeRef<StringT> = string
        override fun voidType(): TypeRef<VoidT> = void
        override fun lookup(name: String): TypeRef<*> = when (name) {
            "int" -> int
            else -> error("Unknown type: $name")
        }
        override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("Unknown function: $name")
    }

    /** Independent mutable domains stand in for the compiler bridge's real journals. */
    private class CompilerState {
        val declarations = arrayListOf("existing declaration")
        val resources = arrayListOf("existing resource")
        val names = arrayListOf("existing name")
        val diagnostics = arrayListOf("existing warning")
        val commands = arrayListOf("existing command")
        val callGraph = arrayListOf("existing edge")
        val scopes = arrayListOf("existing scope")
        val flow = arrayListOf("existing flow")
        var preparations = 0
        var publications = 0
        fun snapshot(): List<List<String>> = listOf(declarations, resources, names, diagnostics, commands, callGraph, scopes, flow).map { it.toList() }

        val backend = CompilerTransactionBackend { changes ->
            preparations++
            val old = listOf(declarations, resources, names, diagnostics).map { it.toList() }
            object : PreparedCompilerCommit {
                override fun apply() {
                    publications++
                    declarations += changes.helpers.map { it.function.name() }
                    resources += changes.resources.map { it.name }
                    names += changes.names.map { it.name }
                    diagnostics += changes.warnings.map { it.message }
                }
                override fun rollback() {
                    listOf(declarations, resources, names, diagnostics).zip(old).forEach { (target, values) ->
                        target.clear()
                        target.addAll(values)
                    }
                }
            }
        }
        fun lowering(fail: Boolean = false, failPrepare: Boolean = false): CompilerJournalParticipant = object : CompilerJournalParticipant {
            override val domains = setOf(CompilerDomain.COMMANDS, CompilerDomain.CALL_GRAPH, CompilerDomain.SCOPES, CompilerDomain.FLOW)
            override fun prepare(): PreparedCompilerCommit {
                if (failPrepare) error("lowering preparation failed")
                val old = listOf(commands, callGraph, scopes, flow).map { it.toList() }
                return object : PreparedCompilerCommit {
                    override fun apply() {
                        commands += "generated command"
                        callGraph += "generated edge"
                        scopes += "generated scope"
                        flow += "generated flow"
                        if (fail) error("lowering apply failed halfway")
                    }
                    override fun rollback() {
                        listOf(commands, callGraph, scopes, flow).zip(old).forEach { (target, values) ->
                            target.clear()
                            target.addAll(values)
                        }
                    }
                }
            }
        }
    }

    private class Fixture(val budget: ExpansionBudget = ExpansionBudget()) {
        val types = Types()
        val environment = ExpansionEnvironment(types, configurationKey = "test configuration")
        val compiler = CompilerState()
        fun session(site: ExpansionSite = site()): ExpansionSession = ExpansionSession(site, budget, environment, CompilerTransaction(compiler.backend))
    }

    companion object {
        private fun span(offset: Int = 0) = SourceSpan("src/macro.mcfpp", offset, offset + 1, 1, offset + 1, 1, offset + 2)
        private fun site(offset: Int = 0) = ExpansionSite(span(offset), "demo::macro(VALUE int)->void")
        private fun objective() = ResourceData.of(ConstantValue.Record(mapOf("criteria" to ConstantValue.Text("dummy"))))
        private fun storage() = ResourceData.of(ConstantValue.Record(mapOf("initial" to ConstantValue.Record(mapOf("count" to ConstantValue.Integral(0))))))
        private fun finalized(session: ExpansionSession, code: Code): ValidatedCode = CodeValidator().validate(code, session.validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode
        private fun empty(session: ExpansionSession): Block = session.context().quote().block(Consumer { })
        private fun helper(session: ExpansionSession, types: Types, hint: String = "do work"): FunctionRef<VoidT> = session.context().helpers().define(hint, FunctionSignature.of(emptyList(), types.void), GeneratedBody { _, _ -> })
        private fun stageArtifacts(session: ExpansionSession, types: Types): Pair<FunctionRef<VoidT>, String> {
            val function = helper(session, types)
            val name = session.context().resources().reserve(ResourceKind.OBJECTIVE, "Counter", objective()).name()
            session.context().diagnostics().warning(span(), "generated warning")
            return function to name
        }
        private fun withCallbacks(root: ExpansionSession, nested: Boolean, body: (ExpansionSession) -> Unit) {
            root.budget.enter(ExpansionFrame("outer callback", span(20), CallbackId(81, "outer"))).use {
                if (nested) root.child(site(10)).use { child ->
                    root.budget.enter(ExpansionFrame("inner callback", span(21), CallbackId(81, "inner"))).use { body(child) }
                } else body(root)
            }
        }
    }

    @Test
    fun helperBodiesAreImmediateTypedAndCannotPublishBeforeRootCompletion() {
        val f = Fixture()
        f.session().use { session ->
            val ctx = session.context()
            var hostInvocations = 0
            val signature = FunctionSignature.of(
                listOf(
                    ParameterView.of("value", f.types.int, ParamMode.VALUE),
                    ParameterView.of("destination", f.types.int, ParamMode.REF)
                ),
                f.types.int
            )
            val function = ctx.helpers().define(
                "increment",
                signature,
                GeneratedBody { b, parameters ->
                    hostInvocations++
                    val value = assertIs<Value<*>>(parameters[0])
                    val ref = assertIs<Ref<*>>(parameters[1])
                    @Suppress("UNCHECKED_CAST")
                    val input = value as Value<IntT>
                    @Suppress("UNCHECKED_CAST")
                    val output = ref as Ref<IntT>
                    b.assign(output, b.add(output.load(), input.expr()))
                    b.yield(output.load())
                }
            )
            val output = session.arena.ref(
                session.arena.metadata(session.arena.rootScope, session.origin),
                f.types.int,
                NodeLocation(session.arena.freshSymbol())
            )
            session.registerBinding(output)
            val code = ctx.quote().block(
                Consumer { b ->
                    b.evaluate(
                        b.call(
                            function,
                            listOf(CallArgument.value(b.intLiteral(3)), CallArgument.ref(output))
                        )
                    )
                }
            )
            ctx.resources().reserve(ResourceKind.STORAGE, "State", storage())
            assertEquals(1, hostInvocations)
            assertEquals(0, f.compiler.preparations)
            assertEquals(listOf("existing declaration"), f.compiler.declarations)
            session.stage(code)
            session.finalizeHelpers()
            assertEquals(0, f.compiler.publications)
            session.complete(finalized(session, code), f.compiler.lowering())
            assertEquals(listOf("existing declaration", function.name()), f.compiler.declarations)
            assertEquals(2, f.compiler.resources.size)
            assertEquals(2, f.compiler.commands.size)
            assertEquals(1, f.compiler.publications)
            assertEquals(0, f.budget.depth)
        }
    }

    @Test
    fun partialCommitFailureRestoresEveryEnlistedDomainAndClosesCapabilities() {
        val f = Fixture()
        val before = f.compiler.snapshot()
        f.session().use { session ->
            stageArtifacts(session, f.types)
            val quote = session.context().quote()
            val code = empty(session)
            session.stage(code)
            session.finalizeHelpers()
            val error = assertFailsWith<ExpansionFailure> { session.complete(finalized(session, code), f.compiler.lowering(fail = true)) }
            assertTrue(error.diagnostic.message.contains("halfway"))
            assertEquals(before, f.compiler.snapshot())
            assertEquals(1, f.compiler.publications) // An apply really started; rollback repaired it.
            assertFailsWith<ExpansionFailure> { quote.block(Consumer { }) }
            assertFalse(session.transaction.isOpen)
            assertEquals(0, f.budget.depth)
        }
    }

    @Test
    fun cancellationDuringEveryPrepareAndApplyBoundaryPreventsPublicationOrRollsItBack() {
        listOf("backend.prepare", "participant.prepare", "backend.apply", "participant.apply").forEach { boundary ->
            val types = Types()
            val compiler = CompilerState()
            val before = compiler.snapshot()
            lateinit var transaction: CompilerTransaction
            val backend = CompilerTransactionBackend { changes ->
                val prepared = compiler.backend.prepare(changes)
                if (boundary == "backend.prepare") transaction.discard()
                object : PreparedCompilerCommit {
                    override fun apply() {
                        prepared.apply()
                        if (boundary == "backend.apply") transaction.discard()
                    }
                    override fun rollback() = prepared.rollback()
                }
            }
            transaction = CompilerTransaction(backend)
            val participant = object : CompilerJournalParticipant {
                private val lowering = compiler.lowering()
                override val domains = lowering.domains
                override fun prepare(): PreparedCompilerCommit {
                    val prepared = lowering.prepare()
                    if (boundary == "participant.prepare") transaction.discard()
                    return object : PreparedCompilerCommit {
                        override fun apply() {
                            prepared.apply()
                            if (boundary == "participant.apply") transaction.discard()
                        }
                        override fun rollback() = prepared.rollback()
                    }
                }
            }
            ExpansionSession(site(), ExpansionBudget(), ExpansionEnvironment(types), transaction).use { session ->
                stageArtifacts(session, types)
                val code = empty(session)
                session.stage(code)
                session.finalizeHelpers()
                val error = assertFailsWith<ExpansionFailure>(boundary) { session.complete(finalized(session, code), participant) }
                assertTrue(error.diagnostic.message.contains("transaction is closed"), boundary)
                assertEquals(before, compiler.snapshot(), boundary)
                assertEquals(if (boundary.endsWith("prepare")) 0 else 1, compiler.publications, boundary)
                assertFalse(transaction.isOpen, boundary)
                assertEquals(0, session.budget.depth, boundary)
            }
        }
    }

    @Test
    fun failedPreparationNeverPublishesAndUnfinalizedHelperCannotCommit() {
        val f = Fixture()
        val before = f.compiler.snapshot()
        f.session().use { session ->
            helper(session, f.types)
            val code = empty(session)
            session.stage(code)
            assertFailsWith<ExpansionFailure> { session.complete(finalized(session, code), f.compiler.lowering()) }
            assertEquals(0, f.compiler.preparations)
        }
        f.session().use { session ->
            stageArtifacts(session, f.types)
            val code = empty(session)
            session.stage(code)
            session.finalizeHelpers()
            assertFailsWith<ExpansionFailure> { session.complete(finalized(session, code), f.compiler.lowering(failPrepare = true)) }
            assertEquals(0, f.compiler.publications)
            assertEquals(before, f.compiler.snapshot())
        }
    }

    @Test
    fun validationAndChangedReturnTypeCannotPublishPreviouslyStagedArtifacts() {
        val f = Fixture()
        val before = f.compiler.snapshot()
        f.session().use { session ->
            stageArtifacts(session, f.types)
            val actualOrigin = Origin(span(8))
            val invalid = session.arena.expr(
                session.arena.metadata(session.arena.rootScope, actualOrigin),
                f.types.int,
                ExprNode.Read(NodeLocation(session.arena.freshSymbol()))
            )
            val error = assertFailsWith<ExpansionFailure> { session.stage(invalid) }
            assertEquals(span(8), error.diagnostic.source)
            assertEquals(before, f.compiler.snapshot())
        }
        f.session().use { session ->
            stageArtifacts(session, f.types)
            val code = session.context().quote().expression(f.types.int, Consumer { it.yield(it.intLiteral(1)) })
            session.stage(code)
            session.finalizeHelpers()
            val changed = session.arena.expr(
                session.arena.metadata(session.arena.rootScope, session.origin),
                f.types.bool,
                ExprNode.Literal(ConstantValue.Bool(true))
            )
            val error = assertFailsWith<ExpansionFailure> { session.complete(finalized(session, changed), f.compiler.lowering()) }
            assertTrue(error.diagnostic.message.contains("actual type"))
            assertEquals(before, f.compiler.snapshot())
            assertEquals(0, f.compiler.preparations)
        }
    }

    @Test
    fun hostFailureAndCaughtErrorDiscardArtifactsAndRevokeCachedServices() {
        val f = Fixture()
        val before = f.compiler.snapshot()
        f.session().use { session ->
            val ctx = session.context()
            val quote = ctx.quote()
            val types = ctx.types()
            val helpers = ctx.helpers()
            val resources = ctx.resources()
            val diagnostics = ctx.diagnostics()
            val reserved = resources.reserve(ResourceKind.OBJECTIVE, "count", objective())
            helper(session, f.types)
            val failure = assertFailsWith<ExpansionFailure> { diagnostics.error(span(2), "stop expansion") }
            assertEquals(span(2), failure.diagnostic.source)
            assertFailsWith<ExpansionFailure> { ctx.callSite() }
            assertFailsWith<ExpansionFailure> { quote.block(Consumer { }) }
            assertFailsWith<ExpansionFailure> { types.intType() }
            assertFailsWith<ExpansionFailure> { helpers.define("late", FunctionSignature.of(emptyList(), f.types.void), GeneratedBody { _, _ -> }) }
            assertFailsWith<ExpansionFailure> { resources.reserve(ResourceKind.STORAGE, "late", storage()) }
            assertFailsWith<ExpansionFailure> { reserved.name() }
            assertFailsWith<ExpansionFailure> { diagnostics.warning(span(), "late warning") }
            assertEquals(before, f.compiler.snapshot())
        }
        f.session().use { session ->
            val failure = assertFailsWith<ExpansionFailure> {
                session.context().quote().block(
                    Consumer {
                        stageArtifacts(session, f.types)
                        error("host exception")
                    }
                )
            }
            assertTrue(failure.diagnostic.message.contains("host exception"))
            assertEquals(before, f.compiler.snapshot())
            assertFalse(session.transaction.isOpen)
        }
    }

    @Test
    fun adoptedChildLosesHostServicesWhileExplicitlyImportedCodeAndHelpersRemainValid() {
        val f = Fixture()
        lateinit var cachedHelper: FunctionRef<VoidT>
        f.session().use { root ->
            val child = root.child(site(10))
            val ctx = child.context()
            val quote = ctx.quote()
            val types = ctx.types()
            val resources = ctx.resources()
            val helpers = ctx.helpers()
            val diagnostics = ctx.diagnostics()
            cachedHelper = helper(child, f.types)
            val resource = resources.reserve(ResourceKind.OBJECTIVE, "child count", objective())
            val code = quote.block(Consumer { it.callVoid(cachedHelper, emptyList()) })
            child.stage(code)
            child.acceptChild(finalized(child, code))
            child.close() // Closing a successful host scope must not destroy root-owned helper IR.
            assertFailsWith<ExpansionFailure> { quote.block(Consumer { }) }
            assertFailsWith<ExpansionFailure> { types.lookup("int") }
            assertFailsWith<ExpansionFailure> { helpers.define("late", FunctionSignature.of(emptyList(), f.types.void), GeneratedBody { _, _ -> }) }
            assertFailsWith<ExpansionFailure> { resources.reserve(ResourceKind.STORAGE, "late", storage()) }
            assertFailsWith<ExpansionFailure> { diagnostics.warning(span(), "late") }
            assertFailsWith<ExpansionFailure> { resource.kind() }
            assertFailsWith<ExpansionFailure> { resource.name() }
            assertFailsWith<ExpansionFailure> { resource.source() }
            assertFailsWith<CodeModelException> { CodeValidator().validate(code, root.validationEnvironment(), ValidationPhase.FINAL) }
            val exported = root.importChild(child, code)
            assertNotEquals((code as OwnedCode).metadata.sessionId, (exported as OwnedCode).metadata.sessionId)
            root.stage(exported)
            root.finalizeHelpers()
            root.complete(finalized(root, exported), f.compiler.lowering())
            assertEquals(listOf("existing declaration", cachedHelper.name()), f.compiler.declarations)
            assertFailsWith<CodeModelException> { child.arena.freshSymbol() }
            assertEquals(0, f.budget.depth)
        }
        // The immutable function handle alone grants no binding capability in a different root.
        f.session().use { next ->
            val code = next.context().quote().block(Consumer { it.callVoid(cachedHelper, emptyList()) })
            val failure = assertFailsWith<ExpansionFailure> { next.stage(code) }
            assertTrue(failure.diagnostic.message.contains("not bound"))
        }
    }

    @Test
    fun helperScopeRejectsOuterLocalsAndUndeclaredParameterCaptures() {
        val f = Fixture()
        f.session().use { session ->
            val error = assertFailsWith<ExpansionFailure> {
                session.context().quote().block(
                    Consumer { outer ->
                        val local = outer.local(f.types.int, outer.intLiteral(1))
                        session.context().helpers().define(
                            "illegal capture",
                            FunctionSignature.of(emptyList(), f.types.void),
                            GeneratedBody { body, _ -> body.evaluate(local.load()) }
                        )
                    }
                )
            }
            assertTrue(error.diagnostic.message.contains("scope"))
        }
        f.session().use { session ->
            val parameter = session.arena.ref(
                session.arena.metadata(session.arena.rootScope, session.origin),
                f.types.int,
                NodeLocation(session.arena.freshSymbol())
            )
            session.registerBinding(parameter)
            val error = assertFailsWith<ExpansionFailure> {
                session.context().helpers().define(
                    "implicit input",
                    FunctionSignature.of(emptyList(), f.types.void),
                    GeneratedBody { body, _ -> body.evaluate(parameter.load()) }
                )
            }
            assertTrue(error.diagnostic.message.contains("no dominating declaration"))
        }
        f.session().use { session ->
            val global = session.arena.ref(
                session.arena.metadata(session.arena.rootScope, session.origin),
                f.types.int,
                NodeLocation(session.arena.freshSymbol())
            )
            session.registerBinding(global, global = true)
            session.context().helpers().define(
                "explicit global",
                FunctionSignature.of(emptyList(), f.types.void),
                GeneratedBody { body, _ -> body.evaluate(global.load()) }
            )
            session.finalizeHelpers()
            assertEquals(1, session.transaction.snapshot().helpers.size)
        }
    }

    @Test
    fun allQuotesAndHelpersShareTheBuilderAccessGate() {
        val f = Fixture()
        f.session().use { session ->
            val ctx = session.context()
            val error = assertFailsWith<ExpansionFailure> {
                ctx.quote().block(
                    Consumer { outer ->
                        ctx.helpers().define(
                            "nested helper",
                            FunctionSignature.of(emptyList(), f.types.void),
                            GeneratedBody { _, _ -> outer.intLiteral(9) }
                        )
                    }
                )
            }
            assertTrue(error.diagnostic.message.contains("active"))
        }
        f.session().use { session ->
            val ctx = session.context()
            val code = ctx.quote().block(
                Consumer { outer ->
                    val nested = helper(session, f.types)
                    outer.callVoid(nested, emptyList())
                }
            )
            session.stage(code)
            session.finalizeHelpers()
            session.complete(finalized(session, code), f.compiler.lowering())
            assertEquals(2, f.compiler.declarations.size)
        }
    }

    @Test
    fun deterministicNamesIgnorePhysicalIdsAndFailureRetriesReuseTheirLogicalInstance() {
        fun names(session: ExpansionSession, types: Types): List<String> {
            val helper = helper(session, types, "Process Values")
            val objective = session.context().resources().reserve(ResourceKind.OBJECTIVE, "Count", objective()).name()
            val storage = session.context().resources().reserve(ResourceKind.STORAGE, "Values", storage()).name()
            assertEquals(16, objective.length)
            assertTrue(storage.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")))
            return listOf(helper.name(), objective, storage)
        }
        val f = Fixture()
        val failed = f.session()
        val first = names(failed, f.types)
        assertFailsWith<ExpansionFailure> { failed.context().diagnostics().error(span(), "retry") }
        f.session().use { retry ->
            repeat(29) {
                retry.arena.freshRegion()
                retry.arena.freshSymbol()
            }
            assertNotEquals(failed.id, retry.id)
            assertEquals(first, names(retry, f.types))
            val code = empty(retry)
            retry.stage(code)
            retry.finalizeHelpers()
            retry.complete(finalized(retry, code), f.compiler.lowering())
        }
        f.session().use { anotherInstance -> assertNotEquals(first, names(anotherInstance, f.types)) }
        val clean = Fixture()
        clean.session().use { session -> assertEquals(first, names(session, clean.types)) }
        val changedEnvironment = ExpansionEnvironment(clean.types, configurationKey = "different config")
        ExpansionSession(site(), clean.budget, changedEnvironment, CompilerTransaction(clean.compiler.backend)).use {
            assertNotEquals(first, names(it, clean.types))
        }
    }

    @Test
    fun diagnosticCancellationRevokesCallbackFramesAndNestedSessionsWithoutMaskingTheError() {
        listOf(false, true).forEach { nested ->
            val f = Fixture()
            f.session().use { root ->
                val before = f.compiler.snapshot()
                root.context().resources().reserve(ResourceKind.OBJECTIVE, "pending", objective())
                withCallbacks(root, nested) { target ->
                    val failure = assertFailsWith<ExpansionFailure> { target.context().diagnostics().error(span(7), "cancel macro") }
                    assertEquals("cancel macro", failure.diagnostic.message)
                    assertEquals(span(7), failure.diagnostic.source)
                    assertEquals(if (nested) 4 else 2, failure.diagnostic.frames.size)
                    assertFalse(root.transaction.isOpen)
                    assertFalse(target.transaction.isOpen)
                    assertEquals(0, f.budget.depth) // Cancellation happens before callback finally blocks run.
                    assertEquals(before, f.compiler.snapshot())
                    root.close()
                    target.close()
                }
            }
            assertEquals(0, f.budget.depth)
            f.session().use { next ->
                assertEquals(1, f.budget.depth)
                withCallbacks(next, false) { empty(it) }
            }
            assertEquals(0, f.budget.depth)
        }
    }

    @Test
    fun projectInitializationCancelsCallbackExtentsAndAllowsTheSameBudgetToEnterAgain() {
        listOf(false, true).forEach { nested ->
            val budget = ExpansionBudget()
            val f = Fixture(budget)
            f.session().use { root ->
                val before = f.compiler.snapshot()
                root.context().resources().reserve(ResourceKind.OBJECTIVE, "pending", objective())
                withCallbacks(root, nested) { target ->
                    val cached = target.context()
                    Project.init() // This lifecycle operation itself must not throw a LIFO exception.
                    assertFailsWith<ExpansionFailure> { cached.callSite() }
                    assertFalse(root.transaction.isOpen)
                    assertFalse(target.transaction.isOpen)
                    assertEquals(0, budget.depth)
                    assertEquals(before, f.compiler.snapshot())
                    root.close()
                    target.close()
                }
            }
            assertEquals(0, budget.depth)
            Fixture(budget).session().use { next ->
                assertEquals(1, budget.depth)
                withCallbacks(next, false) { empty(it) }
            }
            assertEquals(0, budget.depth)
        }
    }

    @Test
    fun nodeLimitCancelsCallbackExtentsAndNestedSessionsWithoutRefundingTheBudget() {
        listOf(false, true).forEach { nested ->
            val budget = ExpansionBudget(maxNodes = 1)
            val f = Fixture(budget)
            f.session().use { root ->
                val before = f.compiler.snapshot()
                root.context().resources().reserve(ResourceKind.OBJECTIVE, "pending", objective())
                empty(root)
                withCallbacks(root, nested) { target ->
                    val failure = assertFailsWith<ExpansionFailure> { empty(target) }
                    assertTrue(failure.diagnostic.message.contains("1 generated nodes"))
                    assertEquals(target.site.span, failure.diagnostic.source)
                    assertEquals(if (nested) 4 else 2, failure.diagnostic.frames.size)
                    assertFalse(root.transaction.isOpen)
                    assertFalse(target.transaction.isOpen)
                    assertEquals(0, budget.depth)
                    assertEquals(2L, budget.usedNodes)
                    assertEquals(before, f.compiler.snapshot())
                    root.close()
                    target.close()
                }
            }
            assertEquals(0, budget.depth)
            f.session().use { next ->
                assertEquals(1, budget.depth)
                assertEquals(f.types.int, next.context().types().intType()) // Frame entry and nonallocating work remain usable.
                assertFailsWith<ExpansionFailure> { empty(next) }
                assertEquals(3L, budget.usedNodes) // The exhausted allowance was not reset by cancellation.
            }
            assertEquals(0, budget.depth)
        }
    }

    @Test
    fun cancellationPreservesAnAncestorAndEveryRevokedFinallyHandleIsIdempotent() {
        val f = Fixture()
        val ancestor = ExpansionFrame("outside the session", span(30))
        f.budget.enter(ancestor).use {
            f.session().use { session ->
                val callback = f.budget.enter(ExpansionFrame("callback", span(31), CallbackId(82, "body")))
                session.close()
                assertEquals(listOf(ancestor), f.budget.frames())
                callback.close()
                callback.close()
                session.close()
                assertEquals(listOf(ancestor), f.budget.frames())
                assertFalse(session.transaction.isOpen)
            }
            f.session().use { next ->
                assertEquals(2, f.budget.depth)
                empty(next)
            }
            assertEquals(listOf(ancestor), f.budget.frames())
        }
        assertEquals(0, f.budget.depth)
    }

    @Test
    fun ordinaryFrameCloseStillRequiresTheExactTopEntryEvenForIdenticalFrameObjects() {
        val budget = ExpansionBudget()
        val reused = ExpansionFrame("same description", span())
        val outer = budget.enter(reused)
        val inner = budget.enter(reused)
        assertFailsWith<IllegalStateException> { outer.close() }
        assertEquals(2, budget.depth)
        inner.close()
        outer.close()
        inner.close()
        outer.close()
        assertEquals(0, budget.depth)
    }

    @Test
    fun completionWithAnUnclosedCallbackRejectsBeforePublishingAndCancelsItsFrames() {
        val f = Fixture()
        f.session().use { session ->
            stageArtifacts(session, f.types)
            val code = empty(session)
            session.stage(code)
            session.finalizeHelpers()
            val proof = finalized(session, code)
            val before = f.compiler.snapshot()
            f.budget.enter(ExpansionFrame("unfinished callback", span(40), CallbackId(83, "body"))).use {
                val failure = assertFailsWith<ExpansionFailure> { session.complete(proof, f.compiler.lowering()) }
                assertTrue(failure.diagnostic.message.contains("reverse order"))
                assertEquals(0, f.compiler.preparations)
                assertEquals(before, f.compiler.snapshot())
                assertFalse(session.transaction.isOpen)
                assertEquals(0, f.budget.depth)
            }
        }
        assertEquals(0, f.budget.depth)
        f.session().use { empty(it) }
        assertEquals(0, f.budget.depth)
    }

    @Test
    fun defaultDepthAccepts64AndRejects65WithTheCompleteCallChain() {
        val f = Fixture()
        f.session().use { root ->
            var current = root
            repeat(63) { current = current.child(site(it + 1)) }
            assertEquals(64, f.budget.depth)
            val failure = assertFailsWith<ExpansionFailure> { current.child(site(64)) }
            assertEquals(65, failure.diagnostic.frames.size)
            assertEquals(span(), failure.diagnostic.frames.first().callSite)
            assertEquals(span(64), failure.diagnostic.frames.last().callSite)
            assertEquals(0, f.budget.depth)
            assertFalse(root.transaction.isOpen)
        }
        assertEquals(0, f.budget.depth)
    }

    @Test
    fun nodeBudgetIsSharedCountsImportsAndNeverRefundsFailedSavepoints() {
        val budget = ExpansionBudget(maxNodes = 6)
        val f = Fixture(budget)
        f.session().use { root ->
            val child = root.child(site(10))
            val input = child.arena.ref(
                child.arena.metadata(child.arena.rootScope, child.origin),
                f.types.int,
                NodeLocation(child.arena.freshSymbol())
            )
            assertEquals(1L, budget.usedNodes)
            repeat(20) { assertSame(input.load(), input.load()) }
            assertEquals(1L, budget.usedNodes)
            child.close()
            assertEquals(1L, budget.usedNodes)
            val success = root.child(site(11))
            val code = empty(success) // one block
            success.stage(code)
            success.acceptChild(finalized(success, code))
            root.importChild(success, code) // one cloned block
            assertEquals(3L, budget.usedNodes)
            val own = empty(root)
            root.arena.importTree(own, root.arena.rootScope)
            root.arena.importTree(own, root.arena.rootScope)
            assertEquals(6L, budget.usedNodes)
            val failure = assertFailsWith<ExpansionFailure> { root.arena.importTree(own, root.arena.rootScope) }
            assertEquals(7L, budget.usedNodes)
            assertTrue(failure.diagnostic.message.contains("6 generated nodes"))
            assertFalse(root.transaction.isOpen)
            assertEquals(0, budget.depth)
        }
        f.session().use { next ->
            assertFailsWith<ExpansionFailure> { empty(next) }
            assertEquals(8L, budget.usedNodes)
        }
    }

    @Test
    fun defaultNodeBoundaryAndCallbackRecursionDiagnosticsUseTheActiveOrigin() {
        val budget = ExpansionBudget()
        val root = ExpansionFrame("outer", span())
        budget.enter(root).use {
            budget.allocate(100_000, Origin(span(5)))
            assertEquals(100_000L, budget.usedNodes)
            val failure = assertFailsWith<ExpansionFailure> { budget.allocate(1, Origin(span(5))) }
            assertEquals(span(5), failure.diagnostic.source)
            assertEquals(listOf(root), failure.diagnostic.frames)
            assertEquals(100_001L, budget.usedNodes)
        }
        val callback = CallbackId(19, "body")
        budget.enter(ExpansionFrame("callback", span(1), callback)).use {
            val failure = assertFailsWith<ExpansionFailure> { budget.enter(ExpansionFrame("callback", span(2), callback)) }
            assertTrue(failure.diagnostic.message.contains("Recursive inline callback"))
            assertEquals(listOf(span(1), span(2)), failure.diagnostic.frames.map { it.callSite })
            assertEquals(1, budget.depth)
        }
        assertEquals(0, budget.depth)
    }

    @Test
    fun resourceDescriptorsRejectRawPayloadsAndUnsupportedSchemasBeforePublication() {
        listOf(
            ResourceKind.OBJECTIVE to ResourceData.of(ConstantValue.Record(mapOf("criteria" to ConstantValue.Text("arbitrary command")))),
            ResourceKind.STORAGE to ResourceData.of(ConstantValue.Record(mapOf("initial" to ConstantValue.Nbt("raw payload")))),
            ResourceKind.INTERNAL_FUNCTION to ResourceData.of(ConstantValue.Record(emptyMap())),
            ResourceKind.OBJECTIVE to ResourceData.of(ConstantValue.Text("unstructured"))
        ).forEach { (kind, descriptor) ->
            val f = Fixture()
            f.session().use { session ->
                assertFailsWith<ExpansionFailure> { session.context().resources().reserve(kind, "invalid", descriptor) }
                assertEquals(listOf("existing resource"), f.compiler.resources)
                assertFalse(session.transaction.isOpen)
            }
        }
    }

    @Test
    fun projectInitializationInvalidatesOldArenasEnvironmentsAndEveryCachedService() {
        val f = Fixture()
        val old = f.session()
        val ctx = old.context()
        val quote = ctx.quote()
        val types = ctx.types()
        val resource = ctx.resources().reserve(ResourceKind.OBJECTIVE, "old", objective())
        Project.init()
        assertFailsWith<ExpansionFailure> { ctx.callSite() }
        assertFailsWith<ExpansionFailure> { quote.block(Consumer { }) }
        assertFailsWith<ExpansionFailure> { types.intType() }
        assertFailsWith<ExpansionFailure> { resource.name() }
        assertFailsWith<CodeModelException> { old.arena.freshSymbol() }
        assertFailsWith<ExpansionFailure> { f.session() }
        assertEquals(0, f.budget.depth)
        val fresh = Fixture()
        fresh.session().use { next ->
            assertTrue(next.id.value > old.id.value)
            assertEquals(fresh.types.int, next.context().types().intType())
        }
        old.close()
    }
}
