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
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.StringHelper.toSnakeCase
import java.util.function.Consumer
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/**
 * Production commit coverage: one cumulative journal for helpers, resources, names, warnings and
 * the root plan, with real [CompilerBridge], real generated MCFPP functions and the real compiler
 * state journal. Runtime assertions execute the emitted commands, not a fake mutation recorder.
 */
private fun productionSpan(offset: Int = 0) = SourceSpan("production.mcfpp", offset, offset + 1, 1, offset + 1, 1, offset + 2)

class MniProductionCommitTest {
    private fun isolated(action: () -> Unit) {
        val before = CompilerStateJournal.checkpoint()
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            Project.projectLoad = Function("load", Project.config.rootNamespace, null)
            MCFPPFile.currFile = null
            Function.forcedField = null
            action()
        } finally {
            before.restore()
        }
    }

    private class Fixture : AutoCloseable {
        val namespace = Namespace("production")
        val file = MCFPPFile(namespace)
        val caller = Function("caller", "production", null)
        val counter = MCInt("counter")
        val counterPlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val counterData = StoredData(
            counterPlace,
            NBTPath(StorageSource("production:input")).memberIndex("counter"),
            layout = StorageLayout.Scoreboard(counter.name, counter.sbObject.name)
        )
        val total = MCInt("total")
        val totalPlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val totalData = StoredData(
            totalPlace,
            NBTPath(StorageSource("production:input")).memberIndex("total"),
            layout = StorageLayout.Scoreboard(total.name, total.sbObject.name)
        )
        lateinit var session: ExpansionSession
        lateinit var backend: CompilerCommitBackend
        lateinit var bindings: CompilerSourceBindings
        lateinit var environment: SourceProjectionEnvironment
        @Suppress("UNCHECKED_CAST")
        val int: TypeRef<IntT>
        @Suppress("UNCHECKED_CAST")
        val void: TypeRef<VoidT>

        init {
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            caller.declarationFile = file
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            val sink = Function("sink", "production", parser("{}").curlBlock()).apply {
                declarationFile = file
                returnType = MCFPPPrivateType.Void
                normalParams.add(FunctionParam(MCFPPBaseType.Int, "value", this))
            }
            namespace.scope.functions["sink"] = arrayListOf(sink)
            counter.symbol = Symbol(counterPlace.root, "counter", MCFPPBaseType.Int.typeId, true, forceRuntime = true)
            counter.storageBinding = StorageBinding(counterData, counterPlace, counterData.path)
            counterData.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
            counterData.declarations[counterPlace] = counter.symbol!!
            counterData.facts.write(
                counterPlace,
                ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown)
            )
            total.symbol = Symbol(totalPlace.root, "total", MCFPPBaseType.Int.typeId, true, forceRuntime = true)
            total.storageBinding = StorageBinding(totalData, totalPlace, totalData.path)
            totalData.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
            totalData.declarations[totalPlace] = total.symbol!!
            totalData.facts.write(
                totalPlace,
                ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown)
            )
            caller.scope.vars["counter"] = counter
            caller.scope.vars["total"] = total
            Function.currFunction = caller
            val syntax = parser("sink(counter)").functionCall()
            val ready = assertIs<CompilerCallPreparationResult.Ready>(
                CompilerBindingEnvironment.prepareCall(syntax, caller)
            )
            backend = CompilerCommitBackend()
            session = ExpansionSession(
                ExpansionSite(productionSpan(), "production::root"),
                ExpansionBudget(),
                CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = "production"),
                CompilerTransaction(backend)
            )
            bindings = CompilerSourceBindings.capture(ready.environment, session)
            backend.attach(bindings)
            environment = SourceProjectionEnvironment(bindings)
            int = bindings.binding.types.ref(MCFPPBaseType.Int) as TypeRef<IntT>
            void = bindings.binding.types.ref(MCFPPPrivateType.Void) as TypeRef<VoidT>
        }

        fun root(name: String) = bindings.roots.getValue(bindings.binding.symbol(name)!!.symbol)

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

        fun objective() = ResourceData.of(ConstantValue.Record(mapOf("criteria" to ConstantValue.Text("dummy"))))
        fun storage() = ResourceData.of(
            ConstantValue.Record(
                mapOf(
                    "initial" to
                        ConstantValue.Record(mapOf("count" to ConstantValue.Integral(3)))
                )
            )
        )

        fun execute(initial: Int): ScoreCommandExecutor {
            val functions = (
                GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values +
                    GlobalScope.stdNamespaces.values
                ).flatMap { it.scope.functions.values.flatten() }
                .associate { function -> function.namespaceID.toString() to function.commands.map { it.analyze() } } +
                Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { it.value.lines() }
            val commands = listOf(
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "scoreboard players set ${counter.name} ${counter.sbObject.name} $initial",
                "scoreboard players set ${total.name} ${total.sbObject.name} 0"
            ) +
                caller.commands.map { it.analyze() } + "data remove storage mcfpp:system stack_frame[0]"
            return ScoreCommandExecutor(commands, functions)
        }

        override fun close() = session.close()

        companion object {
            fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, "production.mcfpp"))))
        }
    }

    @Test
    fun helperResultAndGlobalCaptureCommitWithResourceNameAndWarningOnce() = isolated {
        Fixture().use { f ->
            val global = assertIs<Ref<IntT>>(f.root("counter"))
            f.session.registerBinding(global, global = true)
            var hostInvocations = 0
            val helper = f.session.context().helpers().define<IntT>(
                "increment",
                FunctionSignature.of(listOf(ParameterView.of("value", f.int, ParamMode.VALUE)), f.int),
                GeneratedBody { builder, parameters ->
                    hostInvocations++
                    val input = assertIs<Value<IntT>>(parameters[0])
                    builder.assign(global, builder.add(global.load(), input.expr()))
                    builder.yield(global.load())
                }
            )
            val objective = f.session.context().resources()
                .reserve(ResourceKind.OBJECTIVE, "fixture counter", f.objective())
            val state = f.session.context().resources().reserve(ResourceKind.STORAGE, "fixture state", f.storage())
            // ResourceRef is a host capability and is revoked when the session closes. Capture
            // the stable generated names while the handle is still valid, then assert on strings.
            val objectiveName = objective.name()
            val stateName = state.name()
            f.session.context().diagnostics().warning(productionSpan(4), "generated production warning")
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    builder.assign(global, builder.call(helper, listOf(CallArgument.value(builder.intLiteral(5)))))
                }
            )
            val warnings = Project.warningCount
            f.compile(code)
            assertEquals(1, hostInvocations, "The host body must be built exactly once")
            assertEquals(warnings + 1, Project.warningCount)
            assertTrue(
                GlobalScope.scoreboards.values.any {
                    it.criterion == "dummy" && it.name == objectiveName.toSnakeCase()
                }
            )
            assertTrue(
                Project.projectLoad.commands.map { it.analyze() }.any {
                    it == "data modify storage $stateName set value {count:3}"
                }
            )
            val canonical = assertNotNull(
                f.bindings.helper((helper as ImmutableFunctionRef<*>).key)?.function,
                "The final helper must a canonical compiler Function before its arena closes"
            )
            assertTrue(
                f.namespace.scope.functions.values.flatten().any { it === canonical },
                "The canonical helper Function must be published in its namespace"
            )
            assertTrue(
                f.namespace.scope.functions.values.flatten().any { function ->
                    function.commands.any { it.analyze() == "function ${canonical.namespaceID}" }
                },
                "The emitted helper call must reference the canonical Function namespaceID"
            )
            assertNotNull(
                f.bindings.helper((helper as ImmutableFunctionRef<*>).key)?.plan,
                "The final helper body must be registered as a detached plan before its arena closes"
            )
            val machine = f.execute(10)
            assertEquals(15, machine.values["${f.counter.name} ${f.counter.sbObject.name}"])
            assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        }
    }

    @Test
    fun twoRefFormalsShareOneOriginalAddressInSourceOrder() = isolated {
        Fixture().use { f ->
            val address = assertIs<Ref<IntT>>(f.root("total"))
            val result = assertIs<Ref<IntT>>(f.root("counter"))
            val helper = f.session.context().helpers().define<IntT>(
                "aliased",
                FunctionSignature.of(
                    listOf(
                        ParameterView.of("first", f.int, ParamMode.REF),
                        ParameterView.of("second", f.int, ParamMode.REF)
                    ),
                    f.int
                ),
                GeneratedBody { builder, parameters ->
                    val first = assertIs<Ref<IntT>>(parameters[0])
                    val second = assertIs<Ref<IntT>>(parameters[1])
                    // Both formals name the same original address: the second write must see the
                    // first one immediately, with no copy-in/copy-back temporary in between.
                    builder.assign(first, builder.add(first.load(), builder.intLiteral(1)))
                    builder.assign(second, builder.add(second.load(), builder.intLiteral(10)))
                    builder.yield(second.load())
                }
            )
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    builder.assign(
                        result,
                        builder.call(
                            helper,
                            listOf(
                                CallArgument.ref(address),
                                CallArgument.ref(address)
                            )
                        )
                    )
                }
            )
            f.compile(code)
            val machine = f.execute(0)
            assertEquals(
                11,
                machine.values["${f.total.name} ${f.total.sbObject.name}"],
                "Aliased REF formals must observe the same original address"
            )
            assertEquals(11, machine.values["${f.counter.name} ${f.counter.sbObject.name}"])
            assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        }
    }

    @Test
    fun refFormalAndGlobalCaptureShareOneOriginalAddress() = isolated {
        Fixture().use { f ->
            val address = assertIs<Ref<IntT>>(f.root("counter"))
            val result = assertIs<Ref<IntT>>(f.root("total"))
            f.session.registerBinding(address, global = true)
            val helper = f.session.context().helpers().define<IntT>(
                "globalAlias",
                FunctionSignature.of(listOf(ParameterView.of("destination", f.int, ParamMode.REF)), f.int),
                GeneratedBody { builder, parameters ->
                    val destination = assertIs<Ref<IntT>>(parameters[0])
                    // The legal global capture and the REF formal denote the same storage. The
                    // REF body must not use a private copy, otherwise the second write is stale.
                    builder.assign(address, builder.add(address.load(), builder.intLiteral(1)))
                    builder.assign(destination, builder.add(destination.load(), builder.intLiteral(10)))
                    builder.yield(destination.load())
                }
            )
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    builder.assign(result, builder.call(helper, listOf(CallArgument.ref(address))))
                }
            )
            f.compile(code)
            val machine = f.execute(0)
            assertEquals(11, machine.values["${f.counter.name} ${f.counter.sbObject.name}"])
            assertEquals(11, machine.values["${f.total.name} ${f.total.sbObject.name}"])
            assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        }
    }

    @Test
    fun childHelperAdoptedFromNestedSessionIsLoweredBeforeTheRootArenaCloses() = isolated {
        Fixture().use { f ->
            val child = f.session.child(ExpansionSite(productionSpan(10), "production::child"))
            val childHelper = child.context().helpers().define<IntT>(
                "nested",
                FunctionSignature.of(listOf(ParameterView.of("value", f.int, ParamMode.VALUE)), f.int),
                GeneratedBody { builder, parameters ->
                    val input = assertIs<Value<IntT>>(parameters[0])
                    builder.yield(builder.add(input.expr(), builder.intLiteral(1)))
                }
            )
            val childCode = child.context().quote().block(Consumer { })
            child.stage(childCode)
            child.acceptChild(
                CodeValidator().validate(
                    childCode,
                    child.validationEnvironment(),
                    ValidationPhase.FINAL
                ) as ValidatedCode
            )
            child.close()
            val global = assertIs<Ref<IntT>>(f.root("counter"))
            f.session.registerBinding(global, global = true)
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    builder.assign(global, builder.call(childHelper, listOf(CallArgument.value(builder.intLiteral(2)))))
                }
            )
            f.compile(code)
            val canonical = assertNotNull(
                f.bindings.helper((childHelper as ImmutableFunctionRef<*>).key)?.function,
                "An adopted child helper must be a canonical compiler Function"
            )
            assertTrue(
                f.namespace.scope.functions.values.flatten().any { it === canonical },
                "An adopted child helper must be published from the root transaction"
            )
            assertTrue(
                f.namespace.scope.functions.values.flatten().any { function ->
                    function.commands.any { it.analyze() == "function ${canonical.namespaceID}" }
                },
                "The emitted child-helper call must reference the canonical Function namespaceID"
            )
            val machine = f.execute(0)
            assertEquals(3, machine.values["${f.counter.name} ${f.counter.sbObject.name}"])
            assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        }
    }

    @Test
    fun cancellationRevokesStagedHelpersResourcesAndWarningsWithoutPublication() = isolated {
        Fixture().use { f ->
            val functions = f.namespace.scope.functions.mapValues { it.value.toList() }
            val scoreboards = GlobalScope.scoreboards.toMap()
            val load = Project.projectLoad.commands.map { it.analyze() }
            val warnings = Project.warningCount
            f.session.context().helpers().define<VoidT>(
                "cancelled",
                FunctionSignature.of(emptyList(), f.void),
                GeneratedBody { _, _ -> }
            )
            f.session.context().resources().reserve(ResourceKind.OBJECTIVE, "cancelled", f.objective())
            f.session.context().diagnostics().warning(productionSpan(6), "discarded")
            assertFailsWith<ExpansionFailure> {
                f.session.context().diagnostics().error(productionSpan(7), "cancel production expansion")
            }
            assertFalse(f.session.transaction.isOpen)
            assertEquals(functions, f.namespace.scope.functions.mapValues { it.value.toList() })
            assertEquals(scoreboards, GlobalScope.scoreboards.toMap())
            assertEquals(load, Project.projectLoad.commands.map { it.analyze() })
            assertEquals(warnings, Project.warningCount)
        }
    }

    @Test
    fun failureAfterHelperAndResourceStagingRestoresEveryProductionDomain() = isolated {
        Fixture().use { f ->
            val functions = f.namespace.scope.functions.mapValues { it.value.toList() }
            val scoreboards = GlobalScope.scoreboards.toMap()
            val load = Project.projectLoad.commands.map { it.analyze() }
            val warnings = Project.warningCount
            f.session.context().helpers().define<VoidT>(
                "rollback",
                FunctionSignature.of(emptyList(), f.void),
                GeneratedBody { _, _ -> }
            )
            f.session.context().resources().reserve(ResourceKind.STORAGE, "rollback", f.storage())
            f.session.context().diagnostics().warning(productionSpan(8), "rollback warning")
            val failing = object : CompilerJournalParticipant {
                override val domains = setOf(CompilerDomain.COMMANDS)
                override fun prepare(): PreparedCompilerCommit = error("Independent preparation must not run")
                override fun generation(): (() -> Unit)? = {
                    throw IllegalStateException("root generation failed after staged helper and resource")
                }
            }
            val code = f.session.context().quote().block(Consumer { })
            val failure = assertFailsWith<ExpansionFailure> { f.complete(code, failing) }
            assertTrue(failure.message!!.contains("root generation failed"))
            assertFalse(f.session.transaction.isOpen)
            assertEquals(functions, f.namespace.scope.functions.mapValues { it.value.toList() })
            assertEquals(scoreboards, GlobalScope.scoreboards.toMap())
            assertEquals(load, Project.projectLoad.commands.map { it.analyze() })
            assertEquals(warnings, Project.warningCount)
        }
    }

    @Test
    fun preparedCumulativeSnapshotAppliesOnceAndRollsBackEveryDomain() = isolated {
        Fixture().use { f ->
            val global = assertIs<Ref<IntT>>(f.root("counter"))
            f.session.registerBinding(global, global = true)
            var hostInvocations = 0
            f.session.context().helpers().define<IntT>(
                "snapshot",
                FunctionSignature.of(listOf(ParameterView.of("value", f.int, ParamMode.VALUE)), f.int),
                GeneratedBody { builder, parameters ->
                    hostInvocations++
                    val input = assertIs<Value<IntT>>(parameters[0])
                    builder.assign(global, builder.add(global.load(), input.expr()))
                    builder.yield(global.load())
                }
            )
            val objective = f.session.context().resources().reserve(ResourceKind.OBJECTIVE, "snapshot", f.objective())
            val objectiveName = objective.name()
            f.session.context().resources().reserve(ResourceKind.STORAGE, "snapshot state", f.storage())
            f.session.context().diagnostics().warning(productionSpan(9), "snapshot warning")
            f.session.finalizeHelpers()
            val functions = f.namespace.scope.functions.mapValues { it.value.toList() }
            val scoreboards = GlobalScope.scoreboards.toMap()
            val load = Project.projectLoad.commands.map { it.analyze() }
            val warnings = Project.warningCount
            val prepared = f.backend.prepare(f.session.transaction.snapshot())
            // prepare generated the whole cumulative after snapshot but restored the before state.
            assertEquals(functions, f.namespace.scope.functions.mapValues { it.value.toList() })
            assertEquals(scoreboards, GlobalScope.scoreboards.toMap())
            assertEquals(load, Project.projectLoad.commands.map { it.analyze() })
            assertEquals(warnings, Project.warningCount)
            assertEquals(1, hostInvocations)
            prepared.apply()
            assertEquals(warnings + 1, Project.warningCount)
            assertTrue(
                GlobalScope.scoreboards.values.any {
                    it.criterion == "dummy" && it.name == objectiveName.toSnakeCase()
                }
            )
            assertEquals(1, hostInvocations, "apply must restore captured state, never rerun the host")
            prepared.rollback()
            assertEquals(functions, f.namespace.scope.functions.mapValues { it.value.toList() })
            assertEquals(scoreboards, GlobalScope.scoreboards.toMap())
            assertEquals(load, Project.projectLoad.commands.map { it.analyze() })
            assertEquals(warnings, Project.warningCount)
            assertEquals(1, hostInvocations)
        }
    }
}
