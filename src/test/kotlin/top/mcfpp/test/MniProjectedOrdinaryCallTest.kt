package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.*
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniProjectedOrdinaryCallTest {
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

    private class Fixture(configure: Fixture.() -> Unit = {}) : AutoCloseable {
        val namespace = Namespace("projected-ordinary")
        val file = MCFPPFile(namespace)
        val caller = Function("caller", namespace.identifier, null)
        val counter = MCInt("counter")
        val counterPlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val counterData = StoredData(
            counterPlace,
            NBTPath(StorageSource("projected:input")).memberIndex("counter"),
            layout = StorageLayout.Scoreboard(counter.name, counter.sbObject.name)
        )
        lateinit var sink: Function
        lateinit var effectA: Function
        lateinit var effectB: Function
        lateinit var identity: GenericFunction
        val session: ExpansionSession
        val bindings: CompilerSourceBindings
        val environment: SourceProjectionEnvironment
        val arena: CodeArena
        val int: TypeRef<*>
        val bool: TypeRef<*>
        val scope: ScopeId

        init {
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            caller.declarationFile = file
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            counter.symbol = Symbol(counterPlace.root, "counter", MCFPPBaseType.Int.typeId, true, forceRuntime = true)
            counter.storageBinding = StorageBinding(counterData, counterPlace, counterData.path)
            counterData.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
            counterData.declarations[counterPlace] = counter.symbol!!
            counterData.facts.write(counterPlace, ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown))
            caller.scope.vars["counter"] = counter
            sink = Function("sink", namespace.identifier, parser("{}").curlBlock()).apply {
                declarationFile = file
                returnType = MCFPPBaseType.Int
                // A source declaration whose runtime body is already emitted. This is not a
                // compiler-only actualCallBody, which the source index would hide as a
                // generated specialization instead of selecting the declared target.
                bodyCompiled = true
                normalParams.add(FunctionParam(MCFPPBaseType.Int, "a", this))
                normalParams.add(FunctionParam(MCFPPBaseType.Int, "b", this))
                buildParamVar()
                initializeReturn(this, MCFPPBaseType.Int)
                commands.add("say sink")
                commands.add("execute store result score #sum mcfpp_default run data get storage mcfpp:system stack_frame[0].a 1")
                commands.add("execute store result score #arg mcfpp_default run data get storage mcfpp:system stack_frame[0].b 1")
                commands.add("scoreboard players operation #sum mcfpp_default += #arg mcfpp_default")
                commands.add("execute store result storage mcfpp:system stack_frame[0].return int 1 run scoreboard players get #sum mcfpp_default")
            }
            register(sink)
            effectA = actual(
                "effectA",
                emptyList(),
                MCFPPBaseType.Int,
                listOf(
                    "say effect-a",
                    "scoreboard players add #effects mcfpp_default 1",
                    "data modify storage mcfpp:system stack_frame[0].return set value 3"
                )
            )
            effectB = actual(
                "effectB",
                emptyList(),
                MCFPPBaseType.Int,
                listOf(
                    "say effect-b",
                    "scoreboard players add #effects mcfpp_default 1",
                    "data modify storage mcfpp:system stack_frame[0].return set value 5"
                )
            )
            // A real defaulted declaration, so the missing-argument test proves the complete
            // entry rejects before any default completion runs.
            sink.normalParams.last().apply {
                hasDefault = true
                defaultValue = CompilerValue.Integral(3)
                defaultContext = parser("3").value()
            }
            identity = GenericFunction("identity", namespace.identifier, parser("{ return p0; }").curlBlock()).apply {
                declarationFile = file
                readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "T", this, isReadOnly = true))
                normalParams.add(FunctionParam(MCFPPBaseType.Int, "p0", this))
                returnType = MCFPPBaseType.Int
            }
            register(identity)
            Function.currFunction = caller
            configure()
            val syntax = parser("sink(identity<int>(effectA()), effectB())").functionCall()
            val preparation = CompilerBindingEnvironment.prepareCall(syntax, caller)
            val rejection = (preparation as? CompilerCallPreparationResult.Rejected)?.diagnostics
                ?.joinToString("; ") { "${it.code} at ${it.source}: ${it.message}" + (it.cause?.let { cause -> " ($cause)" } ?: "") }
                ?: "prepareCall returned ${preparation::class.simpleName}"
            val ready = assertIs<CompilerCallPreparationResult.Ready>(preparation, rejection)
            session = ExpansionSession(
                ExpansionSite(ready.call.source, "projected-ordinary-test"),
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
            scope = arena.rootScope
            int = bindings.binding.types.ref(MCFPPBaseType.Int)
            bool = bindings.binding.types.ref(MCFPPBaseType.Bool)
        }

        private fun register(function: Function) {
            namespace.scope.functions.getOrPut(function.identifier) { arrayListOf() }.add(function)
        }

        private fun initializeReturn(function: Function, type: MCFPPType) {
            val binding = StorageAccess.ensure(function.returnVar)
            binding.data.facts.write(binding.place, ValueFacts(TypeKnowledge.Exact(type.typeId), ValueKnowledge.Unknown))
        }

        private fun actual(
            name: String,
            parameters: List<Pair<String, MCFPPType>>,
            result: MCFPPType,
            body: List<String>
        ): Function = Function(name, namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = file
            returnType = result
            // Keep these visible as ordinary declarations; only their runtime body is pre-emitted.
            bodyCompiled = true
            parameters.forEach { normalParams.add(FunctionParam(it.second, it.first, this)) }
            buildParamVar()
            if (result != MCFPPPrivateType.Void) initializeReturn(this, result)
            body.forEach { commands.add(it) }
            register(this)
        }

        fun meta(scope: ScopeId = this.scope) = arena.metadata(scope, session.origin, bindings.loweringTarget.executionContext)
        fun root(name: String) = bindings.roots.getValue(bindings.binding.symbol(name)!!.symbol)
        fun integer(value: Long, scope: ScopeId = this.scope) = arena.expr(meta(scope), int, ExprNode.Literal(ConstantValue.Integral(value)))
        fun expr(type: TypeRef<*>, node: ExprNode, scope: ScopeId = this.scope) = arena.expr(meta(scope), type, node)
        fun stmt(node: StmtNode, scope: ScopeId = this.scope) = arena.stmt(meta(scope), node)
        fun block(statements: List<Stmt>, scope: ScopeId = this.scope) = arena.block(meta(scope), statements)
        fun lower(code: Code) = environment.lower(code)
        fun function(name: String): FunctionRef<*> = bindings.binding.functions.single { it.name == name }.function
        fun call(
            function: FunctionRef<*>,
            arguments: List<CallArgument> = emptyList(),
            readonly: List<TypedConstant> = emptyList()
        ): Expr<*> = expr(function.returnType(), ExprNode.Call(function, arguments, readonly))
        fun compile(code: Code): Pair<CompilerBridge, PreparedCompilerCommit> {
            val plan = lower(code)
            val bridge = CompilerBridge(bindings)
            val before = caller.commands.toList()
            val commit = bridge.prepare(plan).prepare()
            assertEquals(before, caller.commands.toList(), "Preparation must not publish commands")
            commit.apply()
            return bridge to commit
        }
        fun execute(initial: List<String> = emptyList()): ScoreCommandExecutor {
            val all = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                .flatMap { it.scope.functions.values.flatten() }
            val functions = linkedMapOf<String, List<String>>()
            fun export(function: Function) {
                functions[function.namespaceID.toString()] = function.commands.map { it.analyze() }
                function.compiledFunctions.values.forEach(::export)
            }
            all.forEach(::export)
            Project.macroFunction.forEach { (key, value) -> functions["mcfpp:dynamic/$key"] = value.lines() }
            val commands = listOf(
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "scoreboard players set ${counter.name} ${counter.sbObject.name} 0",
                "scoreboard players set #effects mcfpp_default 0",
                "scoreboard players set #sum mcfpp_default 0",
                "scoreboard players set #arg mcfpp_default 0"
            ) + initial + caller.commands.map { it.analyze() } +
                "data remove storage mcfpp:system stack_frame[0]"
            return ScoreCommandExecutor(commands, functions)
        }
        override fun close() = session.close()
        companion object {
            fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, "projected-call.mcfpp"))))
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

    @Test fun ordinaryAndGenericCompleteCallsRunActualCommandsOnceWithTypedReadonlyAndReturnValue() = isolated {
        Fixture().use { f ->
            val effectA = f.function("effectA")
            val effectB = f.function("effectB")
            val identity = f.function("identity") as ImmutableFunctionRef<*>
            val sink = f.function("sink")
            val typeArgument = TypedConstant(
                f.bindings.binding.types.ref(MCFPPConcreteType.Type),
                ConstantValue.TypeValue(f.int),
                SourceSpan.unknown()
            )
            val identityCall = f.call(identity, listOf(CallArgument.value(f.call(effectA))), listOf(typeArgument))
            val sinkCall = f.call(sink, listOf(CallArgument.value(identityCall), CallArgument.value(f.call(effectB))))
            val (bridge, _) = f.compile(sinkCall)

            // Complete arguments skip the sink's defaulted 'b': exactly two effect commands run.
            assertEquals(1, f.identity.compiledFunctions.size)
            assertFalse(f.identity.bodyCompiled)
            assertTrue(f.identity.commands.isEmpty())
            val compiled = f.identity.compiledFunctions.values.single()
            assertEquals(setOf("T"), compiled.boundReadonlyNames)
            assertSame(MCFPPBaseType.Int, StorageAccess.resolveTypeValue(compiled.scope.getVar("T")!!))
            assertSame(f.identity, f.bindings.function(identity.key))

            val machine = f.execute()
            assertEquals(8, machine.read(assertIs<MCInt>(bridge.result())))
            assertEquals(2, machine.values["#effects mcfpp_default"])
            assertEquals(listOf("effect-a", "effect-b", "sink"), machine.messages)
            assertTrue(machine.failedScoreOperations.isEmpty())
            assertTrue(callNames(f.caller).containsAll(setOf("effectA", "effectB", "identity", "sink")))
            assertSame(f.caller, Function.currFunction)
        }
    }

    @Test fun completeEntryRejectsMissingArgumentsWithoutTouchingDefaults() = isolated {
        Fixture().use { f ->
            val missing = linkedMapOf<String, Var<*>>("a" to MCInt("one"))
            val failure = assertFailsWith<IllegalArgumentException> { f.sink.invokeComplete(missing, null) }
            assertTrue(failure.message!!.contains("missing declared arguments"))
            // The declared default must not be completed for the incomplete complete-entry call.
            assertTrue(f.caller.commands.isEmpty())
        }
    }

    @Test fun lateNativeFailureAfterACompleteOrdinaryCallRollsBackCommandsGraphFlowAndDefaults() = isolated {
        Fixture { caller.commands.add("say sentinel") }.use { f ->
            val effectA = f.function("effectA")
            val sink = f.function("sink")
            val first = f.call(effectA)
            val second = f.call(sink, listOf(CallArgument.value(f.integer(1)), CallArgument.value(f.integer(2))))
            val code = f.block(listOf(f.stmt(StmtNode.Assign(f.root("counter"), first)), f.stmt(StmtNode.Eval(second))))
            val original = f.lower(code)
            var seenCalls = 0
            val blocks = original.blocks.map { block ->
                val operations = block.operations.map { operation ->
                    if (operation is PlanOperation.Call && ++seenCalls == 2) PlanOperation.Call(
                        operation.result,
                        PlanFunction(
                            operation.function.key,
                            operation.function.name,
                            PlanCallKind.LEGACY_NATIVE,
                            operation.function.parameters,
                            operation.function.resultType,
                            operation.function.readonlyParameters
                        ),
                        operation.arguments,
                        operation.site,
                        operation.readonlyArguments
                    ) else operation
                }
                if (operations == block.operations) block else PlanBasicBlock(
                    block.id,
                    block.scope,
                    operations,
                    block.terminator,
                    block.executionFrame,
                    block.contextRegions
                )
            }
            val forged = LoweringPlan(
                original.target, original.entry, original.resultType, blocks, original.symbols,
                original.scopes, original.domain, original.executionFrames, original.contextRegions
            )
            val bridge = CompilerBridge(f.bindings)
            val participant = bridge.prepare(forged)
            val commands = f.caller.commands.toList()
            val children = f.caller.child.toList()
            val functions = f.namespace.scope.functions.mapValues { it.value.toList() }
            val resources = Project.macroFunction.toMap()
            val facts = f.counterData.facts.fork()
            val version = f.counterData.versions.version(f.counterPlace)
            val diagnostics = Project.errorCount to Project.warningCount
            val failure = assertFailsWith<CodeModelException> { participant.prepare() }
            assertEquals("projected-call.mcfpp", failure.source.file)
            assertTrue(failure.message!!.contains("Legacy native call lowering"))
            assertEquals(commands, f.caller.commands.toList())
            assertEquals(children, f.caller.child.toList())
            assertEquals(functions, f.namespace.scope.functions.mapValues { it.value.toList() })
            assertEquals(resources, Project.macroFunction.toMap())
            assertEquals(facts, f.counterData.facts)
            assertEquals(version, f.counterData.versions.version(f.counterPlace))
            assertEquals(diagnostics, Project.errorCount to Project.warningCount)
            assertSame(f.caller, Function.currFunction)
        }
    }
}
