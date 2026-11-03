@file:Suppress("UNUSED_PARAMETER")

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
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.annotation.WritesReceiver
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import top.mcfpp.util.ValueWrapper
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniProjectedNativeCallTest {
    private fun isolated(action: () -> Unit) {
        val before = CompilerStateJournal.checkpoint()
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            Function.forcedField = null
            action()
        } finally {
            before.restore()
        }
    }

    private class Prepared(
        val plan: LoweringPlan,
        val bridge: CompilerBridge,
        val commit: PreparedCompilerCommit
    )

    private class Fixture(source: String, dynamicReceiver: Boolean = false) : AutoCloseable {
        val namespace = Namespace("projected-native")
        val file = MCFPPFile(namespace)
        val caller = Function("caller", namespace.identifier, null)
        val receiver = MCInt("receiver")
        val receiverPlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val receiverData = StoredData(
            receiverPlace,
            NBTPath(StorageSource("projected:native")).memberIndex("receiver"),
            layout = StorageLayout.Scoreboard(receiver.name, receiver.sbObject.name)
        )
        val commandsBefore: List<top.mcfpp.command.Command>
        val factsBefore: FlowFacts
        val versionBefore: Long
        lateinit var legacy: NativeFunction
        lateinit var context: NativeFunction
        val ready: CompilerCallPreparationResult.Ready
        val session: ExpansionSession
        val bindings: CompilerSourceBindings
        val environment: SourceProjectionEnvironment
        val arena: CodeArena
        val sourceCall: SourceCall

        init {
            ProjectedNativeProvider.reset()
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            caller.declarationFile = file
            caller.commands.add("say native-sentinel")
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            receiver.symbol = top.mcfpp.analysis.Symbol(
                receiverPlace.root,
                "receiver",
                MCFPPBaseType.Int.typeId,
                true,
                forceRuntime = true
            )
            receiver.storageBinding = if (dynamicReceiver) {
                StorageBinding(
                    receiverData,
                    receiverPlace,
                    receiverData.path,
                    location = Location(
                        receiverPlace.unknownIndex(),
                        captured = mapOf(
                            0 to ValueRef.Constant(
                                MCFPPBaseType.Int.typeId,
                                CompilerValue.Integral(0)
                            )
                        )
                    )
                )
            } else StorageBinding(receiverData, receiverPlace, receiverData.path)
            receiverData.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
            receiverData.declarations[receiverPlace] = receiver.symbol!!
            receiverData.facts.write(
                receiverPlace,
                ValueFacts(
                    TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
                    ValueKnowledge.Unknown,
                    ValueState.INITIALIZED
                )
            )
            caller.scope.vars["receiver"] = receiver
            legacy = NativeFunction(
                "legacy",
                namespace.identifier,
                ProjectedNativeProvider::class.java.getMethod(
                    "legacy",
                    MCInt::class.java,
                    MCInt::class.java,
                    ValueWrapper::class.java
                )
            ).apply {
                declarationFile = file
                returnType = MCFPPBaseType.Int
                appendReadOnlyParam(MCFPPBaseType.Int, "constant")
                appendNormalParam(MCFPPBaseType.Int, "value")
            }
            legacy.normalParams.single().apply {
                hasDefault = true
                defaultValue = CompilerValue.Integral(7)
            }
            context = NativeFunction(
                "context",
                namespace.identifier,
                ProjectedNativeProvider::class.java.getMethod("context", NativeCallContext::class.java)
            ).apply {
                declarationFile = file
                returnType = MCFPPBaseType.Int
                appendReadOnlyParam(MCFPPBaseType.Int, "constant")
                appendNormalParam(MCFPPBaseType.Int, "value")
                caller = MCFPPBaseType.Int
            }
            for (native in listOf(legacy, context)) {
                namespace.scope.functions[native.identifier] = arrayListOf(native)
            }
            Function.currFunction = caller
            val syntax = parser(source).functionCall()
            val preparation = CompilerBindingEnvironment.prepareCall(syntax, caller)
            val rejection = (preparation as? CompilerCallPreparationResult.Rejected)?.diagnostics
                ?.joinToString("; ") { "${it.code} at ${it.source}: ${it.message}" }
                ?: "prepareCall returned ${preparation::class.simpleName}"
            ready = assertIs<CompilerCallPreparationResult.Ready>(preparation, rejection)
            sourceCall = ready.call
            session = ExpansionSession(
                ExpansionSite(sourceCall.source, "projected-native-test"),
                ExpansionBudget(),
                CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = namespace.identifier),
                CompilerTransaction(
                    CompilerTransactionBackend {
                        object : PreparedCompilerCommit {
                            override fun apply() = Unit
                            override fun rollback() = Unit
                        }
                    }
                )
            )
            bindings = CompilerSourceBindings.capture(ready.environment, session)
            environment = SourceProjectionEnvironment(bindings)
            arena = session.arena
            commandsBefore = caller.commands.toList()
            factsBefore = receiverData.facts.fork()
            versionBefore = receiverData.versions.version(receiverPlace)
        }

        fun receiverRef(): Ref<*> = bindings.roots.getValue(
            bindings.binding.symbol("receiver")!!.symbol
        )

        fun callCode(call: SourceCall = sourceCall, receiver: Ref<*>? = null): Code {
            val prepared = SourceCodeProjector(environment).arguments(call)
            val function = call.target.function
            val metadata = arena.metadata(prepared.scope, session.origin, bindings.loweringTarget.executionContext)
            val invocation = arena.expr(
                metadata,
                function.returnType(),
                ExprNode.Call(function, prepared.arguments, prepared.readonlyArguments, receiver)
            )
            val region = arena.freshRegion()
            val body = arena.block(
                metadata,
                prepared.orderedPrelude.statements() +
                    arena.stmt(metadata, StmtNode.Yield(region, invocation))
            )
            val outer = arena.metadata(
                environment.rootScope,
                session.origin,
                bindings.loweringTarget.executionContext
            )
            return arena.expr(outer, function.returnType(), ExprNode.ExprBlock(body, function.returnType(), region))
        }

        fun prepared(code: Code): Prepared {
            val plan = environment.lower(code)
            val bridge = CompilerBridge(bindings)
            val participant = bridge.prepare(plan)
            val published = caller.commands.toList()
            val commit = participant.prepare()
            assertEquals(published, caller.commands.toList(), "Preparation must not publish caller commands")
            return Prepared(plan, bridge, commit)
        }

        fun preparedAndApplied(code: Code): Prepared = prepared(code).also { it.commit.apply() }

        fun execute(initial: List<String> = emptyList(), cleanupRoot: Boolean = true): ScoreCommandExecutor {
            val all = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                .flatMap { it.scope.functions.values.flatten() }
            val functions = linkedMapOf<String, List<String>>()
            fun export(function: Function) {
                functions[function.namespaceID.toString()] = function.commands.map { it.analyze() }
                function.compiledFunctions.values.forEach(::export)
            }
            all.forEach(::export)
            Project.macroFunction.forEach { (key, value) -> functions["mcfpp:dynamic/$key"] = value.lines() }
            val cleanup = if (cleanupRoot) listOf("data remove storage mcfpp:system stack_frame[0]") else emptyList<String>()
            val commands = listOf(
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "scoreboard players set ${receiver.name} ${receiver.sbObject.name} 4"
            ) + initial +
                caller.commands.map { it.analyze() } + cleanup
            return ScoreCommandExecutor(commands, functions)
        }

        override fun close() = session.close()

        companion object {
            fun parser(text: String) = P(
                CommonTokenStream(
                    mcfppLexer(CharStreams.fromString(text, "projected-native.mcfpp"))
                )
            )
        }
    }

    private fun callNames(function: Function): Set<String> {
        val seen = linkedSetOf<String>()
        fun visit(value: Function) {
            if (!seen.add(value.identifier)) return
            value.child.forEach(::visit)
        }
        visit(function)
        return seen
    }

    @Test fun legacyAbiConsumesCompleteTypedReadonlyNormalAndResultExactlyOnce() = isolated {
        Fixture("legacy<5>()").use { f ->
            val prepared = f.preparedAndApplied(f.callCode())
            assertEquals(0, Project.errorCount)
            val call = prepared.plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.Call>().single()
            assertEquals(PlanCallKind.LEGACY_NATIVE, call.function.kind)
            assertEquals(listOf(PlanConstant.Integral(5)), call.readonlyArguments.map { it.value })
            assertEquals(1, call.arguments.size)
            assertNull(call.receiver)
            assertEquals(1, ProjectedNativeProvider.legacyCalls)
            assertEquals(listOf(5, 7), ProjectedNativeProvider.legacyArguments)
            val machine = f.execute()
            assertEquals(12, machine.read(assertIs<MCInt>(prepared.bridge.result())))
            assertTrue(machine.failedScoreOperations.isEmpty())
            assertTrue(callNames(f.caller).contains("legacy"))
            assertSame(f.caller, Function.currFunction)
            val published = f.caller.commands.toList()
            prepared.commit.rollback()
            assertEquals(f.commandsBefore, f.caller.commands)
            assertEquals(1, ProjectedNativeProvider.legacyCalls)
            assertNotEquals(published, f.caller.commands)
        }
    }

    @Test fun contextAbiReceivesCapturedReceiverWritesOriginalStorageAndReturnsResult() = isolated {
        Fixture("context<5>(7)").use { f ->
            val prepared = f.preparedAndApplied(f.callCode(receiver = f.receiverRef()))
            assertEquals(0, Project.errorCount)
            val operations = prepared.plan.blocks.flatMap { it.operations }
            val call = operations.filterIsInstance<PlanOperation.Call>().single()
            val capture = operations.filterIsInstance<PlanOperation.CaptureLocation>().single {
                PlanArgument.Reference(it.result) == call.receiver
            }
            assertEquals(call.receiver, PlanArgument.Reference(capture.result))
            assertTrue(operations.indexOf(capture) < operations.indexOf(call))
            assertEquals(1, ProjectedNativeProvider.contextCalls)
            assertEquals<List<Place?>>(listOf<Place?>(f.receiverPlace), ProjectedNativeProvider.contextReceiverPlaces)
            assertEquals(listOf(5, 7), ProjectedNativeProvider.contextArguments)
            val machine = f.execute()
            assertEquals(5, machine.values["${f.receiver.name} ${f.receiver.sbObject.name}"])
            assertEquals(17, machine.read(assertIs<MCInt>(prepared.bridge.result())))
            assertTrue(machine.failedScoreOperations.isEmpty())
            assertEquals(1, ProjectedNativeProvider.contextCalls)
        }
    }

    @Test fun storedRootWithDynamicLocationCannotBypassFrozenReceiverAdapter() = isolated {
        Fixture("context<5>(7)", dynamicReceiver = true).use { f ->
            val plan = f.environment.lower(f.callCode(receiver = f.receiverRef()))
            val participant = CompilerBridge(f.bindings).prepare(plan)
            val failure = assertFailsWith<CodeModelException> { participant.prepare() }
            assertTrue(failure.message!!.contains("dynamic"))
            assertEquals(0, ProjectedNativeProvider.contextCalls)
            assertEquals(f.commandsBefore, f.caller.commands)
            assertEquals(f.factsBefore, f.receiverData.facts)
            assertEquals(f.versionBefore, f.receiverData.versions.version(f.receiverPlace))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun nonCurrentFrameScoreboardWriteUsesFrameNbtAndKeepsPrimaryScore() = isolated {
        Fixture("legacy<5>()").use { f ->
            val framePlace = Place(top.mcfpp.analysis.SymbolId.fresh())
            val frameData = StoredData(
                framePlace,
                NBTPath.stack.intIndex(0).memberIndex("receiver"),
                layout = StorageLayout.Scoreboard(f.receiver.name, f.receiver.sbObject.name)
            )
            frameData.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
            frameData.declarations[framePlace] = top.mcfpp.analysis.Symbol(
                framePlace.root,
                "frame_receiver",
                MCFPPBaseType.Int.typeId,
                true
            )
            frameData.facts.write(
                framePlace,
                ValueFacts(
                    TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
                    ValueKnowledge.Unknown,
                    ValueState.INITIALIZED
                )
            )
            val binding = StorageBinding(
                frameData,
                framePlace,
                frameData.path,
                location = Location(framePlace, frameOffset = 1)
            )
            val target = StorageAccess.adapter(MCFPPBaseType.Int, "frame_receiver", binding)
            val written = StorageAccess.write(target, MCInt(9))
            assertFalse(written.isError)
            assertEquals(0, Project.errorCount)
            val machine = f.execute(
                initial = listOf("data modify storage mcfpp:system stack_frame prepend value {}"),
                cleanupRoot = false
            )
            assertEquals(9, machine.readNbt("mcfpp:system", "stack_frame[1].receiver").value)
            assertEquals(4, machine.values["${f.receiver.name} ${f.receiver.sbObject.name}"])
            assertTrue(machine.failedScoreOperations.isEmpty())
        }
    }

    @Test fun completeEntryRejectsMissingArgumentsWithoutDefaultsAndFailureApplyRollbackStayAtomic() = isolated {
        Fixture("legacy<5>()").use { f ->
            val missing = assertFailsWith<IllegalArgumentException> {
                f.legacy.invokeCompleteArguments(linkedMapOf("constant" to MCInt(5)), linkedMapOf(), null)
            }
            assertTrue(missing.message!!.contains("missing declared arguments"))
            assertEquals(0, ProjectedNativeProvider.legacyCalls)

            ProjectedNativeProvider.failNext = true
            val plan = f.environment.lower(f.callCode())
            val participant = CompilerBridge(f.bindings).prepare(plan)
            val failure = assertFailsWith<CodeModelException> { participant.prepare() }
            assertEquals("projected-native.mcfpp", failure.source.file)
            assertTrue(failure.message!!.contains("Complete native call"))
            assertEquals(1, ProjectedNativeProvider.legacyCalls)
            assertEquals(f.commandsBefore, f.caller.commands)
            assertEquals(f.factsBefore, f.receiverData.facts)
            assertEquals(f.versionBefore, f.receiverData.versions.version(f.receiverPlace))
            assertEquals(0, Project.errorCount)

            ProjectedNativeProvider.failNext = false
            val successful = f.preparedAndApplied(f.callCode())
            assertEquals(2, ProjectedNativeProvider.legacyCalls)
            val published = f.caller.commands.toList()
            successful.commit.rollback()
            assertEquals(f.commandsBefore, f.caller.commands)
            assertNotEquals(published, f.caller.commands)
            assertEquals(2, ProjectedNativeProvider.legacyCalls)
        }
    }
}

@NoExternalWrites
object ProjectedNativeProvider {
    var legacyCalls = 0
    var contextCalls = 0
    var failNext = false
    val legacyArguments = arrayListOf<Int>()
    val contextArguments = arrayListOf<Int>()
    val contextReceiverPlaces = arrayListOf<Place?>()

    fun reset() {
        legacyCalls = 0
        contextCalls = 0
        failNext = false
        legacyArguments.clear()
        contextArguments.clear()
        contextReceiverPlaces.clear()
    }

    @JvmStatic fun legacy(constant: MCInt, value: MCInt, result: ValueWrapper<MCInt>) {
        legacyCalls++
        legacyArguments += constant.value
        legacyArguments += value.value
        if (failNext) error("Projected native failure")
        result.set(MCInt(constant.value + value.value))
    }

    @JvmStatic @WritesReceiver
    fun context(call: NativeCallContext) {
        contextCalls++
        call.withAdapters { receiver, arguments ->
            contextReceiverPlaces += call.receiverPlace
            val constant = arguments[0] as MCInt
            val value = arguments[1] as MCInt
            contextArguments += constant.value
            contextArguments += value.value
            val physical = receiver as MCInt
            physical.assignedBy(physical.plus(MCInt(1)) as MCInt)
            call.publishResult((constant.plus(value) as MCInt).plus(physical) as MCInt)
        }
    }
}
