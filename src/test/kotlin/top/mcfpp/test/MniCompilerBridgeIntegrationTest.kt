package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.*
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.mnifixtures.TransactionalMniProvider
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.ValueWrapper
import java.util.function.Consumer
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

private fun bridgeSpan(offset: Int = 0) = SourceSpan("bridge-transaction.mcfpp", offset, offset + 1, 1, offset + 1, 1, offset + 2)

/**
 * Production transaction integration for a real legacy MNI host.
 *
 * The host mutates actual compiler commands, scope, functions/helpers, resources, the call graph,
 * generated names, storage flow facts and diagnostics before it either throws or publishes. The
 * cumulative [CompilerCommitBackend] must restore the complete before state on failure and the
 * complete after state on apply, without invoking the host again.
 */
class MniCompilerBridgeIntegrationTest {
    private fun isolated(action: () -> Unit) {
        val before = CompilerStateJournal.checkpoint()
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            Project.projectLoad = Function("load", Project.config.rootNamespace, null)
            MCFPPFile.currFile = null
            Function.forcedField = null
            LogProcessor.level = LogLevel.DEBUG
            action()
        } finally {
            before.restore()
        }
    }

    private class NestedSession(
        val child: ExpansionSession,
        val helper: FunctionRef<IntT>,
        val resource: ResourceRef,
        val resourceName: String
    )

    private class Fixture : AutoCloseable {
        val namespace = Namespace("bridge-transaction")
        val file = MCFPPFile(namespace)
        val caller = Function("caller", namespace.identifier, null)
        val flow = MCInt(5, "flow")
        val flowPlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val flowData = StoredData(
            flowPlace,
            NBTPath(StorageSource("bridge:input")).memberIndex("flow"),
            layout = StorageLayout.Scoreboard(flow.name, flow.sbObject.name)
        )
        val commandsBefore: List<String>
        val factsBefore: FlowFacts
        val versionBefore: Long
        val native: NativeFunction
        val sourceCall: SourceCall
        val session: ExpansionSession
        val bindings: CompilerSourceBindings
        val backend: CompilerCommitBackend
        val environment: SourceProjectionEnvironment
        @Suppress("UNCHECKED_CAST")
        val int: TypeRef<IntT>

        init {
            TransactionalMniProvider.reset()
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            caller.declarationFile = file
            caller.commands.add("say bridge-before")
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            flow.symbol = Symbol(flowPlace.root, "flow", MCFPPBaseType.Int.typeId, true)
            flow.storageBinding = StorageBinding(flowData, flowPlace, flowData.path)
            flowData.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
            flowData.declarations[flowPlace] = flow.symbol!!
            flowData.facts.write(
                flowPlace,
                ValueFacts(
                    TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
                    ValueKnowledge.Constant(CompilerValue.Integral(5))
                )
            )
            Project.constants["bridge-transaction-flow"] = flow
            native = NativeFunction(
                "transactional",
                namespace.identifier,
                TransactionalMniProvider::class.java.getMethod("mutate", ValueWrapper::class.java)
            ).apply {
                declarationFile = file
                returnType = MCFPPBaseType.Int
            }
            namespace.scope.functions[native.identifier] = arrayListOf(native)
            Function.currFunction = caller
            val syntax = parser("transactional()").functionCall()
            val ready = assertIs<CompilerCallPreparationResult.Ready>(
                CompilerBindingEnvironment.prepareCall(syntax, caller)
            )
            sourceCall = ready.call
            backend = CompilerCommitBackend()
            session = ExpansionSession(
                ExpansionSite(bridgeSpan(), "bridge::root"),
                ExpansionBudget(),
                CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = namespace.identifier),
                CompilerTransaction(backend)
            )
            bindings = CompilerSourceBindings.capture(ready.environment, session)
            backend.attach(bindings)
            environment = SourceProjectionEnvironment(bindings)
            int = bindings.binding.types.ref(MCFPPBaseType.Int) as TypeRef<IntT>
            commandsBefore = caller.commands.map { it.analyze() }
            factsBefore = flowData.facts.fork()
            versionBefore = flowData.versions.version(flowPlace)
        }

        /** The same detached code shape used by the approved projected-native coverage. */
        fun callCode(): Code {
            val prepared = SourceCodeProjector(environment).arguments(sourceCall)
            val function = sourceCall.target.function
            val metadata = session.arena.metadata(
                prepared.scope,
                session.origin,
                bindings.loweringTarget.executionContext
            )
            val invocation = session.arena.expr(
                metadata,
                function.returnType(),
                ExprNode.Call(function, prepared.arguments, prepared.readonlyArguments, null)
            )
            val region = session.arena.freshRegion()
            val body = session.arena.block(
                metadata,
                prepared.orderedPrelude.statements() +
                    session.arena.stmt(metadata, StmtNode.Yield(region, invocation))
            )
            val outer = session.arena.metadata(
                environment.rootScope,
                session.origin,
                bindings.loweringTarget.executionContext
            )
            return session.arena.expr(
                outer,
                function.returnType(),
                ExprNode.ExprBlock(body, function.returnType(), region)
            )
        }

        fun final(code: Code): ValidatedCode = CodeValidator().validate(code, session.validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode

        fun complete(code: Code, lowering: CompilerJournalParticipant) {
            session.stage(code)
            session.finalizeHelpers()
            session.complete(final(code), lowering)
        }

        fun compile(code: Code): CompilerBridge {
            val plan = environment.lower(code)
            val bridge = CompilerBridge(bindings)
            complete(code, bridge.prepare(plan))
            return bridge
        }

        fun storage() = ResourceData.of(
            ConstantValue.Record(
                mapOf(
                    "initial" to
                        ConstantValue.Record(mapOf("count" to ConstantValue.Integral(3)))
                )
            )
        )

        override fun close() = session.close()

        companion object {
            fun parser(text: String) = P(
                CommonTokenStream(
                    mcfppLexer(CharStreams.fromString(text, "bridge-transaction.mcfpp"))
                )
            )
        }
    }

    /** Adopt a real nested session helper and resource into the root transaction. */
    private fun adoptNested(f: Fixture, offset: Int): NestedSession {
        val child = f.session.child(ExpansionSite(bridgeSpan(offset), "bridge::nested"))
        val helper = child.context().helpers().define<IntT>(
            "nested",
            FunctionSignature.of(listOf(ParameterView.of("value", f.int, ParamMode.VALUE)), f.int),
            GeneratedBody { builder, parameters ->
                val input = assertIs<Value<IntT>>(parameters[0])
                builder.yield(builder.add(input.expr(), builder.intLiteral(1)))
            }
        )
        val resource = child.context().resources().reserve(ResourceKind.STORAGE, "nested state", f.storage())
        // ResourceRef is a host capability owned by the ACTIVE child. Its generated name must be
        // captured before adoption/closing, because later domain assertions occur after the root
        // session has committed and that capability is correctly revoked.
        val resourceName = resource.name()
        val code = child.context().quote().block(Consumer { })
        child.stage(code)
        child.acceptChild(
            CodeValidator().validate(
                code,
                child.validationEnvironment(),
                ValidationPhase.FINAL
            ) as ValidatedCode
        )
        child.close()
        return NestedSession(child, helper, resource, resourceName)
    }

    @Test
    fun failedLegacyNativeRestoresEveryCompilerDomainAndNestedSession() = isolated {
        Fixture().use { f ->
            val nested = adoptNested(f, 10)
            assertTrue(
                f.session.transaction.allHelpers().any {
                    (it.function as ImmutableFunctionRef<*>).key == (nested.helper as ImmutableFunctionRef<*>).key
                },
                "The nested helper must already belong to the root production transaction"
            )
            val functions = f.namespace.scope.functions.mapValues { it.value.toList() }
            val scope = f.caller.scope.vars.toMap()
            val scoreboards = GlobalScope.scoreboards.toMap()
            val load = Project.projectLoad.commands.map { it.analyze() }
            val names = Project.macroFunction.toMap()
            val functionCount = TempPool.functionCount
            val warnings = Project.warningCount
            val errors = Project.errorCount
            val child = f.caller.child.toList()
            val nativeParents = f.native.parent.toList()
            TransactionalMniProvider.failNext = true

            val failure = assertFailsWith<ExpansionFailure> { f.compile(f.callCode()) }
            assertTrue(
                failure.message.orEmpty().contains("did not produce a valid runtime invocation"),
                failure.message.orEmpty()
            )
            assertEquals(1, TransactionalMniProvider.calls)
            assertTrue(TransactionalMniProvider.commandWrites > 0, "The host must write commands before failing")
            assertTrue(TransactionalMniProvider.scopeWrites > 0, "The host must write a scope before failing")
            assertTrue(TransactionalMniProvider.functionWrites > 0, "The host must publish a helper before failing")
            assertTrue(TransactionalMniProvider.resourceWrites > 0, "The host must write resources before failing")
            assertTrue(TransactionalMniProvider.callGraphWrites > 0, "The host must write the call graph before failing")
            assertTrue(TransactionalMniProvider.nameWrites > 0, "The host must write names before failing")
            assertTrue(TransactionalMniProvider.flowWrites > 0, "The host must write flow facts before failing")
            assertTrue(TransactionalMniProvider.diagnosticWrites > 0, "The host must write diagnostics before failing")

            assertFalse(f.session.transaction.isOpen)
            assertSame(f.caller, Function.currFunction)
            assertEquals(f.commandsBefore, f.caller.commands.map { it.analyze() })
            assertEquals(functions, f.namespace.scope.functions.mapValues { it.value.toList() })
            assertEquals(scope, f.caller.scope.vars.toMap())
            assertEquals(scoreboards, GlobalScope.scoreboards.toMap())
            assertEquals(load, Project.projectLoad.commands.map { it.analyze() })
            assertEquals(names, Project.macroFunction.toMap())
            assertEquals(functionCount, TempPool.functionCount)
            assertEquals(warnings, Project.warningCount)
            assertEquals(errors, Project.errorCount)
            assertEquals(child, f.caller.child.toList())
            assertEquals(nativeParents, f.native.parent.toList())
            assertEquals(f.factsBefore, f.flowData.facts)
            assertEquals(f.versionBefore, f.flowData.versions.version(f.flowPlace))
            assertEquals(5, assertIs<MCInt>(StorageAccess.read(f.flow)).value)

            val nestedBinding = assertNotNull(
                f.bindings.helper((nested.helper as ImmutableFunctionRef<*>).key)
            )
            assertFalse(
                f.namespace.scope.functions.values.flatten()
                    .any { it === nestedBinding.function }
            )
            assertFalse(
                Project.projectLoad.commands.map { it.analyze() }
                    .any { it == "data modify storage ${nested.resourceName} set value {count:3}" }
            )
            assertFalse(GlobalScope.scoreboards.containsKey("txn_resource"))
            assertFalse(Project.macroFunction.containsKey("bridge/transactional"))
            assertFalse(f.caller.scope.vars.containsKey("txnScopeMarker"))
        }
    }

    @Test
    fun successfulLegacyNativePublishesEveryWrittenDomainAndNestedSession() = isolated {
        Fixture().use { f ->
            val nested = adoptNested(f, 20)
            val warnings = Project.warningCount
            val functionCount = TempPool.functionCount
            TransactionalMniProvider.failNext = false

            val bridge = f.compile(f.callCode())
            assertEquals(1, TransactionalMniProvider.calls)
            assertSame(f.caller, Function.currFunction)
            assertFalse(f.session.transaction.isOpen)
            assertNotNull(bridge.result(), "The successful host result must be readable after apply")

            assertTrue(f.caller.commands.map { it.analyze() }.contains("say transactional-command"))
            assertTrue(f.caller.scope.vars.containsKey("txnScopeMarker"))
            assertTrue(f.namespace.scope.functions.values.flatten().any { it.identifier == "txnHelper" })
            assertTrue(GlobalScope.scoreboards.containsKey("txn_resource"))
            assertTrue(
                Project.projectLoad.commands.map { it.analyze() }
                    .contains("data modify storage bridge:fixture set value {count:1}")
            )
            assertEquals("\$say transactional", Project.macroFunction["bridge/transactional"])
            assertEquals(functionCount + 1, TempPool.functionCount)
            assertEquals(warnings + 1, Project.warningCount)
            assertEquals(99, assertIs<MCInt>(StorageAccess.read(f.flow)).value)

            val nestedBinding = assertNotNull(
                f.bindings.helper((nested.helper as ImmutableFunctionRef<*>).key)
            )
            assertTrue(
                f.namespace.scope.functions.values.flatten()
                    .any { it === nestedBinding.function }
            )
            assertTrue(
                Project.projectLoad.commands.map { it.analyze() }
                    .contains("data modify storage ${nested.resourceName} set value {count:3}")
            )
            assertTrue(f.caller.child.any { it.identifier == "txnHelper" })
            assertEquals(2, TransactionalMniProvider.callGraphWrites)
        }
    }

    @Test
    fun preparedCumulativeCommitAppliesAndRollsBackWithoutRerunningLegacyHost() = isolated {
        Fixture().use { f ->
            TransactionalMniProvider.failNext = false
            val code = f.callCode()
            f.session.stage(code)
            f.session.finalizeHelpers()
            val plan = f.environment.lower(code)
            val bridge = CompilerBridge(f.bindings)
            val participant = bridge.prepare(plan)

            val prepared = f.backend.prepare(f.session.transaction.snapshot(), listOf(participant))
            // Preparation generated the complete after state but restored the before state.
            assertEquals(1, TransactionalMniProvider.calls)
            assertSame(f.caller, Function.currFunction)
            assertEquals(f.commandsBefore, f.caller.commands.map { it.analyze() })
            assertEquals(f.factsBefore, f.flowData.facts)
            assertEquals(f.versionBefore, f.flowData.versions.version(f.flowPlace))
            assertFalse(f.caller.scope.vars.containsKey("txnScopeMarker"))
            assertFalse(f.namespace.scope.functions.values.flatten().any { it.identifier == "txnHelper" })
            assertFalse(GlobalScope.scoreboards.containsKey("txn_resource"))
            assertFalse(Project.macroFunction.containsKey("bridge/transactional"))

            prepared.apply()
            assertEquals(1, TransactionalMniProvider.calls, "apply must restore captured state, never rerun the host")
            assertTrue(f.caller.commands.map { it.analyze() }.contains("say transactional-command"))
            assertTrue(GlobalScope.scoreboards.containsKey("txn_resource"))
            assertEquals(99, assertIs<MCInt>(StorageAccess.read(f.flow)).value)

            prepared.rollback()
            assertEquals(1, TransactionalMniProvider.calls)
            assertEquals(f.commandsBefore, f.caller.commands.map { it.analyze() })
            assertEquals(f.factsBefore, f.flowData.facts)
            assertFalse(GlobalScope.scoreboards.containsKey("txn_resource"))

            prepared.apply()
            assertEquals(1, TransactionalMniProvider.calls)
            assertTrue(f.caller.commands.map { it.analyze() }.contains("say transactional-command"))
            assertTrue(GlobalScope.scoreboards.containsKey("txn_resource"))
            assertTrue(f.namespace.scope.functions.values.flatten().any { it.identifier == "txnHelper" })
            assertEquals(99, assertIs<MCInt>(StorageAccess.read(f.flow)).value)
            assertSame(f.caller, Function.currFunction)
        }
    }
}
