package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.mnifixtures.TransactionalMniProvider
import top.mcfpp.test.util.ContextBlock
import top.mcfpp.test.util.ContextCommandExecutor
import top.mcfpp.test.util.ContextEntity
import top.mcfpp.test.util.ContextPosition
import top.mcfpp.test.util.ContextRotation
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
import java.util.IdentityHashMap
import java.util.function.Consumer
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/**
 * Production command observations for C_CONTEXT and context-bound REF helpers.
 *
 * These tests compile through the real [CompilerBridge]. The first cases keep the emitted command
 * shape observable; the context/REF/ray cases execute the committed functions with the strict
 * [ContextCommandExecutor] world, so executor/position/rotation, local offsets, block predicates,
 * helper REF identity and nonlocal exits are exercised as generated commands rather than model
 * values. In-game execution is still not claimed.
 */
class MniExecutionContextTest {
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

    private data class Compiled(
        val commands: List<String>,
        val plan: LoweringPlan,
        val caller: Function,
        val functions: Map<String, List<String>>
    )

    private inner class Fixture : AutoCloseable {
        val namespace = Namespace("context-commands")
        val file = MCFPPFile(namespace)
        val caller = Function("caller", namespace.identifier, null)
        val session: ExpansionSession
        val bindings: CompilerSourceBindings
        val environment: SourceProjectionEnvironment
        @Suppress("UNCHECKED_CAST")
        val int: TypeRef<IntT>
        @Suppress("UNCHECKED_CAST")
        val bool: TypeRef<BoolT>
        @Suppress("UNCHECKED_CAST")
        val void: TypeRef<VoidT>

        init {
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            caller.declarationFile = file
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            val marker = NativeFunction(
                "marker",
                namespace.identifier,
                TransactionalMniProvider::class.java.getMethod("mutate", ValueWrapper::class.java)
            ).apply {
                declarationFile = file
                returnType = MCFPPBaseType.Int
            }
            namespace.scope.functions[marker.identifier] = arrayListOf(marker)
            Function.currFunction = caller
            val ready = assertIs<CompilerCallPreparationResult.Ready>(
                CompilerBindingEnvironment.prepareCall(parser("marker()").functionCall(), caller)
            )
            val backend = CompilerCommitBackend()
            session = ExpansionSession(
                ExpansionSite(span(), "context::root"),
                ExpansionBudget(),
                CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = namespace.identifier),
                CompilerTransaction(backend)
            )
            bindings = CompilerSourceBindings.capture(ready.environment, session)
            backend.attach(bindings)
            environment = SourceProjectionEnvironment(bindings)
            int = bindings.binding.types.ref(MCFPPBaseType.Int) as TypeRef<IntT>
            bool = bindings.binding.types.ref(MCFPPBaseType.Bool) as TypeRef<BoolT>
            void = bindings.binding.types.ref(MCFPPPrivateType.Void) as TypeRef<VoidT>
        }

        fun helper(hint: String): FunctionRef<VoidT> = session.context().helpers().define<VoidT>(
            hint,
            FunctionSignature.of(
                listOf(
                    ParameterView.of("target", int, ParamMode.REF),
                    ParameterView.of("delta", int, ParamMode.VALUE)
                ),
                void
            ),
            GeneratedBody { builder, parameters ->
                val target = assertIs<Ref<IntT>>(parameters[0])
                val delta = assertIs<Value<IntT>>(parameters[1])
                builder.assign(target, builder.add(target.load(), delta.expr()))
            }
        )

        /** A helper whose own body enters executeAs before writing its REF formal. */
        fun nestedAsHelper(hint: String): FunctionRef<VoidT> = session.context().helpers().define<VoidT>(
            hint,
            FunctionSignature.of(listOf(ParameterView.of("target", int, ParamMode.REF)), void),
            GeneratedBody { builder, parameters ->
                val target = assertIs<Ref<IntT>>(parameters[0])
                val mc = builder.minecraft()
                mc.executeAs(mc.entities(EntityQuery.allEntities())) { actor, _ ->
                    actor.assign(target, actor.intLiteral(1))
                }
            }
        )

        fun compile(code: Code): Compiled {
            session.stage(code)
            session.finalizeHelpers()
            val plan = environment.lower(code)
            val bridge = CompilerBridge(bindings)
            val validated = CodeValidator().validate(code, session.validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode
            session.complete(validated, bridge.prepare(plan))
            return Compiled(commands(caller), plan, caller, functionMap())
        }

        fun functionMap(): Map<String, List<String>> {
            val functions = (
                GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values +
                    GlobalScope.stdNamespaces.values
                )
                .flatMap { it.scope.functions.values.flatten() }.associate { function ->
                    function.namespaceID.toString() to function.commands.map { it.analyze() }
                }
            return functions + Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }
                .mapValues { it.value.lines() }
        }

        fun execute(
            compiled: Compiled,
            entities: List<ContextEntity> = emptyList(),
            rootPosition: ContextPosition = ContextPosition(10.0, 64.0, 10.0),
            rootRotation: ContextRotation = ContextRotation(0.0, 0.0),
            world: (Int, Int, Int) -> ContextBlock? = { _, _, _ -> null },
            initialScores: Map<String, Int> = emptyMap()
        ): ContextCommandExecutor {
            val commands = listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
                compiled.caller.commands.map { it.analyze() } +
                "data remove storage mcfpp:system stack_frame[0]"
            return ContextCommandExecutor(
                commands, compiled.functions, entities, null,
                rootPosition, rootRotation, world, initialScores,
                Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }
            )
        }

        fun commands(root: Function): List<String> {
            val seen = java.util.Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
            val result = arrayListOf<String>()
            fun visit(function: Function) {
                if (!seen.add(function)) return
                function.commands.forEach { result += it.analyze() }
                function.child.forEach(::visit)
            }
            visit(root)
            return result
        }

        override fun close() = session.close()
    }

    private fun span() = SourceSpan("context-command.mcfpp", 0, 1, 1, 1, 1, 2)

    private fun parser(text: String) = P(
        CommonTokenStream(
            mcfppLexer(CharStreams.fromString(text, "context-command.mcfpp"))
        )
    )

    private fun player(name: String, x: Double, y: Double, z: Double, yaw: Double, pitch: Double) = ContextEntity(
        name,
        "minecraft:player",
        emptySet(),
        ContextPosition(x, y, z),
        ContextRotation(yaw, pitch)
    )

    private fun stoneWorld(x: Int, y: Int, z: Int): ContextBlock? = if (x == 1 && y == 64 && z == 1) ContextBlock("minecraft:stone") else null

    private fun allStone(x: Int, y: Int, z: Int): ContextBlock? = ContextBlock("minecraft:stone")

    @Test
    fun productionBridgeEmitsExecutorPositionAndRotationAsSeparateContexts() = isolated {
        Fixture().use { f ->
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self ->
                        actor.at(actor.position(self)) { placed ->
                            placed.rotated(placed.rotation(self)) { turned ->
                                turned.evaluate(
                                    turned.blockMatches(
                                        turned.blockAt(turned.currentPosition()),
                                        BlockPredicate.block("minecraft:stone")
                                    )
                                )
                            }
                        }
                    }
                }
            )
            val compiled = f.compile(code)
            val commands = compiled.commands
            val asCommand = commands.single { it.startsWith("execute as @a ") && "run function" in it }
            assertFalse("positioned" in asCommand || "rotated" in asCommand, asCommand)
            val atCommand = commands.single { it.startsWith("execute positioned as @s run function") }
            assertFalse("rotated" in atCommand, atCommand)
            val rotatedCommand = commands.single { it.startsWith("execute rotated as @s run function") }
            assertFalse("positioned" in rotatedCommand, rotatedCommand)
            assertTrue(
                commands.any { it.startsWith("execute if block ~ ~ ~ minecraft:stone run data modify") },
                commands.joinToString("\n")
            )
            assertTrue(
                commands.any {
                    it.startsWith("execute unless score ") && " if score " in it && "run return run function" in it
                },
                "A nonlocal edge must dispatch through the recorded context stack"
            )

            val regions = compiled.plan.contextRegions
            assertEquals(3, regions.size)
            assertIs<PlanContextChange.ExecuteAs>(regions[0].change)
            assertIs<PlanContextChange.At>(regions[1].change)
            assertIs<PlanContextChange.Rotated>(regions[2].change)
            assertEquals(regions[0].frame.executor, regions[1].frame.executor)
            assertEquals(regions[0].frame.rotation, regions[1].frame.rotation)
            assertNotEquals(regions[0].frame.position, regions[1].frame.position)
            assertEquals(regions[1].frame.executor, regions[2].frame.executor)
            assertEquals(regions[1].frame.position, regions[2].frame.position)
            assertNotEquals(regions[1].frame.rotation, regions[2].frame.rotation)
        }
    }

    @Test
    fun nestedExecuteAsAndNonlocalBreakStayInsideTheirOwnContextStack() = isolated {
        Fixture().use { f ->
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    val count = mc.score(ScoreHolder.named("#count"), "count")
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { outer, _ ->
                        outer.executeAs(outer.entities(EntityQuery.allEntities())) { inner, _ ->
                            inner.assign(count, inner.add(count.load(), inner.intLiteral(1)))
                        }
                    }
                }
            )
            val compiled = f.compile(code)
            val commands = compiled.commands
            assertEquals(2, commands.count { it.startsWith("execute as ") && "run function" in it })
            assertEquals(2, compiled.plan.contextRegions.size)
            assertEquals(compiled.plan.contextRegions[0].frame, compiled.plan.contextRegions[1].parent)
            // The inner iteration is emitted inside a function of the outer body, never flattened
            // into the outer executor's block.
            val outerBody = (compiled.plan.contextRegions[0].change as PlanContextChange.ExecuteAs).self.executor
            val innerBody = (compiled.plan.contextRegions[1].change as PlanContextChange.ExecuteAs).self.executor
            assertNotEquals(outerBody, innerBody)
        }
    }

    @Test
    fun fixedAndSameExecutorRefHelpersSpecializeWithoutCopyOrWriteback() = isolated {
        Fixture().use { f ->
            val helper = f.helper("increment")
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    val fixed = mc.score(ScoreHolder.named("#fixed"), "hits")
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self ->
                        val own = actor.score(self, "hits")
                        actor.callVoid(helper, listOf(CallArgument.ref(fixed), CallArgument.value(actor.intLiteral(1))))
                        actor.callVoid(helper, listOf(CallArgument.ref(own), CallArgument.value(actor.intLiteral(1))))
                    }
                }
            )
            val compiled = f.compile(code)
            // The fixed alias and the same-executor @s alias are both actual scoreboard writes.
            assertTrue(
                compiled.commands.any { it.startsWith("scoreboard players operation #fixed hits = ") },
                compiled.commands.joinToString("\n")
            )
            assertTrue(
                compiled.commands.any { it.startsWith("scoreboard players operation @s hits = ") },
                compiled.commands.joinToString("\n")
            )
            // The helper is specialized rather than copying a reference payload through the stack.
            assertTrue(
                compiled.commands.any { it.startsWith("function ") && it.contains("_helper_ref") },
                compiled.commands.joinToString("\n")
            )
            assertFalse(
                compiled.commands.any { it.startsWith("data modify ") && it.contains(".target") },
                "A REF payload must not be copied into the helper frame"
            )
        }
    }

    @Test
    fun aCrossExecuteAsRefIsRejectedBeforeAnyCommit() = isolated {
        Fixture().use { f ->
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { outer, self ->
                        val hp = outer.score(self, "hp")
                        outer.executeAs(outer.entities(EntityQuery.allEntities())) { inner, _ ->
                            inner.assign(hp, inner.intLiteral(1))
                        }
                    }
                }
            )
            val failure = assertFailsWith<ExpansionFailure> { f.compile(code) }
            assertTrue(failure.message.orEmpty().contains("cannot cross executeAs"), failure.message.orEmpty())
        }
    }

    @Test
    fun aCrossExecuteAsHelperRefIsRejectedBeforeSpecialization() = isolated {
        Fixture().use { f ->
            val helper = f.helper("cross")
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { outer, self ->
                        val hp = outer.score(self, "hp")
                        outer.executeAs(outer.entities(EntityQuery.allEntities())) { inner, _ ->
                            inner.callVoid(helper, listOf(CallArgument.ref(hp), CallArgument.value(inner.intLiteral(1))))
                        }
                    }
                }
            )
            val failure = assertFailsWith<ExpansionFailure> { f.compile(code) }
            assertTrue(failure.message.orEmpty().contains("cannot cross executeAs"), failure.message.orEmpty())
        }
    }

    private fun blockSample(builder: MinecraftBuilder) = builder.evaluate(
        builder.blockMatches(
            builder.blockAt(builder.currentPosition()),
            BlockPredicate.block("minecraft:stone")
        )
    )

    @Test
    fun actualCommandsChangeOneContextComponentAndRestoreEveryParent() = isolated {
        Fixture().use { f ->
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self ->
                        blockSample(actor)
                        actor.at(actor.position(self)) { placed ->
                            blockSample(placed)
                            placed.rotated(placed.rotation(self)) { turned -> blockSample(turned) }
                            blockSample(placed)
                        }
                        blockSample(actor)
                    }
                }
            )
            val compiled = f.compile(code)
            val alice = player("alice", 1.0, 64.0, 2.0, 0.0, 0.0)
            val bob = player("bob", 5.0, 70.0, 6.0, 30.0, 40.0)
            val executor = f.execute(
                compiled,
                listOf(alice, bob),
                rootPosition = ContextPosition(10.0, 64.0, 10.0),
                rootRotation = ContextRotation(90.0, 15.0),
                world = ::allStone
            )
            val tests = executor.blockTests
            assertEquals(10, tests.size, tests.joinToString("\n"))
            for ((offset, entity) in listOf(alice, bob).withIndex()) {
                val samples = tests.subList(offset * 5, offset * 5 + 5)
                assertTrue(samples.all { it.matched })
                assertEquals(
                    listOf(entity.name, entity.name, entity.name, entity.name, entity.name),
                    samples.map { it.executor }
                )
                assertEquals(ContextPosition(10.0, 64.0, 10.0), samples[0].position)
                assertEquals(ContextRotation(90.0, 15.0), samples[0].rotation)
                assertEquals(entity.position, samples[1].position)
                assertEquals(ContextRotation(90.0, 15.0), samples[1].rotation)
                assertEquals(entity.position, samples[2].position)
                assertEquals(entity.rotation, samples[2].rotation)
                assertEquals(entity.position, samples[3].position)
                assertEquals(ContextRotation(90.0, 15.0), samples[3].rotation)
                assertEquals(ContextPosition(10.0, 64.0, 10.0), samples[4].position)
                assertEquals(ContextRotation(90.0, 15.0), samples[4].rotation)
            }
        }
    }

    @Test
    fun actualLocalOffsetsUseTheAmbientRotationAndNeverReplaceIt() = isolated {
        Fixture().use { f ->
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    mc.rotated(mc.rotation(90.0, 0.0)) { ambient ->
                        ambient.executeAs(ambient.entities(EntityQuery.allPlayers())) { actor, self ->
                            actor.at(actor.position(self)) { placed ->
                                val fromCurrent = placed.localOffset(
                                    placed.currentPosition(),
                                    placed.currentRotation(),
                                    1.0,
                                    0.0,
                                    0.0
                                )
                                placed.evaluate(
                                    placed.blockMatches(
                                        placed.blockAt(fromCurrent),
                                        BlockPredicate.block("minecraft:stone")
                                    )
                                )
                                val fromAbsolute = placed.localOffset(
                                    placed.currentPosition(),
                                    placed.rotation(0.0, 0.0),
                                    1.0,
                                    0.0,
                                    0.0
                                )
                                placed.evaluate(
                                    placed.blockMatches(
                                        placed.blockAt(fromAbsolute),
                                        BlockPredicate.block("minecraft:stone")
                                    )
                                )
                            }
                        }
                        blockSample(ambient)
                    }
                }
            )
            val compiled = f.compile(code)
            val alice = player("alice", 0.0, 64.0, 0.0, 0.0, 0.0)
            val bob = player("bob", 5.0, 64.0, 5.0, 10.0, 20.0)
            val executor = f.execute(
                compiled,
                listOf(alice, bob),
                rootPosition = ContextPosition(10.0, 64.0, 10.0),
                rootRotation = ContextRotation(0.0, 0.0),
                world = ::allStone
            )
            val tests = executor.blockTests
            assertEquals(5, tests.size, tests.joinToString("\n"))
            assertEquals(ContextPosition(0.0, 64.0, 1.0), tests[0].position, "current yaw 90 is left +Z")
            assertEquals(ContextRotation(90.0, 0.0), tests[0].rotation)
            assertEquals(ContextPosition(1.0, 64.0, 0.0), tests[1].position, "explicit yaw 0 is left +X")
            assertEquals(
                ContextRotation(90.0, 0.0),
                tests[1].rotation,
                "an absolute local offset must not replace the ambient rotation"
            )
            assertEquals(ContextPosition(5.0, 64.0, 6.0), tests[2].position)
            assertEquals(ContextRotation(90.0, 0.0), tests[2].rotation)
            assertEquals(ContextPosition(6.0, 64.0, 5.0), tests[3].position)
            assertEquals(ContextRotation(90.0, 0.0), tests[3].rotation)
            assertEquals(ContextPosition(10.0, 64.0, 10.0), tests[4].position)
            assertEquals(ContextRotation(90.0, 0.0), tests[4].rotation)
            assertEquals(null, tests[4].executor)
        }
    }

    @Test
    fun actualBlockPredicateControlsTheFollowingBranch() = isolated {
        Fixture().use { f ->
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    val hits = mc.score(ScoreHolder.named("#hits"), "hits")
                    val hit = mc.blockMatches(
                        mc.blockAt(mc.position(1.0, 64.0, 1.0)),
                        BlockPredicate.block("minecraft:stone")
                    )
                    mc.ifThen(hit) { branch ->
                        branch.assign(hits, branch.add(hits.load(), branch.intLiteral(1)))
                    }
                    val miss = mc.blockMatches(
                        mc.blockAt(mc.position(2.0, 64.0, 2.0)),
                        BlockPredicate.block("minecraft:stone")
                    )
                    mc.ifThen(miss) { branch ->
                        branch.assign(hits, branch.add(hits.load(), branch.intLiteral(100)))
                    }
                }
            )
            val compiled = f.compile(code)
            val executor = f.execute(
                compiled,
                world = ::stoneWorld,
                initialScores = mapOf("#hits hits" to 0)
            )
            assertEquals(1, executor.score("#hits", "hits"))
            assertEquals(listOf(true, false), executor.blockTests.map { it.matched })
        }
    }

    @Test
    fun actualSameExecutorRefHelpersWriteTheOriginalScoresInsideANestedContext() = isolated {
        Fixture().use { f ->
            val helper = f.helper("increment")
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    val fixed = mc.score(ScoreHolder.named("#fixed"), "hits")
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self ->
                        actor.callVoid(
                            helper,
                            listOf(
                                CallArgument.ref(fixed),
                                CallArgument.value(actor.intLiteral(1))
                            )
                        )
                        actor.at(actor.position(self)) { placed ->
                            val own = placed.score(self, "hits")
                            placed.callVoid(
                                helper,
                                listOf(
                                    CallArgument.ref(own),
                                    CallArgument.value(placed.intLiteral(1))
                                )
                            )
                        }
                    }
                }
            )
            val compiled = f.compile(code)
            val alice = player("alice", 1.0, 64.0, 1.0, 0.0, 0.0)
            val bob = player("bob", 2.0, 64.0, 2.0, 0.0, 0.0)
            val executor = f.execute(
                compiled,
                listOf(alice, bob),
                initialScores = mapOf("alice hits" to 10, "bob hits" to 20, "#fixed hits" to 0)
            )
            assertEquals(2, executor.score("#fixed", "hits"))
            assertEquals(11, executor.score("alice", "hits"))
            assertEquals(21, executor.score("bob", "hits"))
        }
    }

    @Test
    fun aHelperBodyThatEntersExecuteAsCannotReuseTheCallersCapturedRef() = isolated {
        Fixture().use { f ->
            val helper = f.nestedAsHelper("collision")
            val code = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { outer, self ->
                        val hp = outer.score(self, "hp")
                        outer.callVoid(helper, listOf(CallArgument.ref(hp)))
                    }
                }
            )
            val failure = assertFailsWith<ExpansionFailure> { f.compile(code) }
            assertTrue(failure.message.orEmpty().contains("cannot cross executeAs"), failure.message.orEmpty())
        }
    }

    // ---- InlineExpander context cloning (real arena, no backend) ----

    private class InlineTypes : TypeService {
        val int = ImmutableTypeRef<IntT>(TypeKey(930, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(930, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(930, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(930, 4), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(930, 5), "void", kind = TypeKind.VOID)
        override fun intType(): TypeRef<IntT> = int
        override fun boolType(): TypeRef<BoolT> = bool
        override fun floatType(): TypeRef<FloatT> = float
        override fun stringType(): TypeRef<StringT> = string
        override fun voidType(): TypeRef<VoidT> = void
        override fun lookup(name: String): TypeRef<*> = error("No lookup")
        override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("No lookup")
    }

    private object InlineDummy {
        @JvmStatic
        fun unused(context: MacroContext): Block = error("The dummy macro is never expanded")
    }

    private fun inlineExpander(session: ExpansionSession): MacroExpander {
        val method = InlineDummy::class.java.getMethod("unused", MacroContext::class.java)
        val reference = ImmutableFunctionRef(
            FunctionKey(931, "inlineDummy"),
            "inlineDummy",
            FunctionSignature.of(emptyList(), session.environment.types.voidType()),
            FunctionKind.MACRO
        )
        val prepared = PreparedMacroSignature(
            "inlineDummy",
            method,
            emptyList(),
            PreparedMacroType.Source("void", span()),
            span()
        )
        val macro = MacroFunction("context-inline", "unused", method, reference, true, prepared)
        return MacroExpander(session, listOf(macro), "context-inline")
    }

    @Test
    fun inlineCallbackClonesNestedContextHandlesAndAllocatesFreshFrames() {
        val types = InlineTypes()
        val transaction = CompilerTransaction(
            CompilerTransactionBackend {
                object : PreparedCompilerCommit {
                    override fun apply() { }
                    override fun rollback() { }
                }
            }
        )
        val session = ExpansionSession(
            ExpansionSite(span(), "context-inline"),
            ExpansionBudget(),
            ExpansionEnvironment(types, emptyList(), configurationKey = "context-inline"),
            transaction
        )
        session.use {
            val template = session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self ->
                        actor.at(actor.position(self)) { placed ->
                            placed.rotated(placed.rotation(self)) { turned ->
                                turned.evaluate(
                                    turned.blockMatches(
                                        turned.blockAt(turned.currentPosition()),
                                        BlockPredicate.block("minecraft:stone")
                                    )
                                )
                            }
                        }
                    }
                }
            )
            val root = session.rootExecutionContext()
            val metadata = session.arena.metadata(session.arena.rootScope, Origin(span(), span()), root.id)
            val signature = FunctionSignature.of(emptyList(), types.void)
            val callback = session.arena.inline<Any?>(
                metadata,
                signature,
                CallbackId(932, "context-inline"),
                InlineTarget.Body(emptyList(), template, emptySet(), session.arena.freshRegion())
            )
            val call = session.arena.expr(metadata, types.void, ExprNode.InlineCall(callback, emptyList()))
            val tree = session.arena.block(metadata, listOf(session.arena.stmt(metadata, StmtNode.Eval(call))))
            val expanded = inlineExpander(session).expandInlineTree(session, tree)
            val proof = CodeValidator().validate(expanded, session.validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode
            val plan = CodeLowerer().lower(
                proof,
                LoweringTarget(FunctionKey(933, "context-inline-target"), (expanded as OwnedCode).metadata.executionContextId)
            )
            val regions = plan.contextRegions
            assertEquals(3, regions.size)
            assertIs<PlanContextChange.ExecuteAs>(regions[0].change)
            assertIs<PlanContextChange.At>(regions[1].change)
            assertIs<PlanContextChange.Rotated>(regions[2].change)
            assertEquals(3, regions.map { it.scope.index }.distinct().size)
            assertEquals(regions[0].frame, regions[1].parent)
            assertEquals(regions[1].frame, regions[2].parent)
            assertTrue(plan.blocks.any { it.contextRegions.isNotEmpty() })
            assertTrue(plan.blocks.flatMap { it.operations }.any { it is PlanOperation.BlockMatches })
        }
    }

    @Test
    fun inlineCallbackContextStatementsSurviveTheProductionBridgeAndExecute() = isolated {
        Fixture().use { f ->
            val template = f.session.context().quote().block(
                Consumer { builder ->
                    val mc = builder.minecraft()
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self ->
                        blockSample(actor)
                        actor.at(actor.position(self)) { placed ->
                            blockSample(placed)
                            placed.rotated(placed.rotation(self)) { turned -> blockSample(turned) }
                        }
                    }
                }
            )
            val root = f.session.rootExecutionContext()
            val metadata = f.session.arena.metadata(f.session.arena.rootScope, Origin(span(), span()), root.id)
            val callback = f.session.arena.inline<Any?>(
                metadata,
                FunctionSignature.of(emptyList(), f.void),
                CallbackId(934, "context-inline-production"),
                InlineTarget.Body(emptyList(), template, emptySet(), f.session.arena.freshRegion())
            )
            val call = f.session.arena.expr(metadata, f.void, ExprNode.InlineCall(callback, emptyList()))
            val after = f.session.arena.expr(
                metadata,
                f.bool,
                ExprNode.BlockMatches(
                    f.session.arena.blockHandle(
                        metadata,
                        f.session.arena.position(metadata, PositionNode.Current(root.id))
                    ),
                    BlockPredicate.block("minecraft:stone")
                )
            )
            val tree = f.session.arena.block(
                metadata,
                listOf(
                    f.session.arena.stmt(metadata, StmtNode.Eval(call)),
                    f.session.arena.stmt(metadata, StmtNode.Eval(after))
                )
            )
            val expanded = inlineExpander(f.session).expandInlineTree(f.session, tree)
            val compiled = f.compile(expanded)
            val alice = player("alice", 1.0, 64.0, 1.0, 0.0, 0.0)
            val bob = player("bob", 5.0, 64.0, 5.0, 30.0, 40.0)
            val executor = f.execute(
                compiled,
                listOf(alice, bob),
                rootPosition = ContextPosition(10.0, 64.0, 10.0),
                rootRotation = ContextRotation(90.0, 15.0),
                world = ::allStone
            )
            assertEquals(7, executor.blockTests.size, executor.blockTests.joinToString("\n"))
            for ((offset, entity) in listOf(alice, bob).withIndex()) {
                val samples = executor.blockTests.subList(offset * 3, offset * 3 + 3)
                assertEquals(ContextPosition(10.0, 64.0, 10.0), samples[0].position)
                assertEquals(ContextRotation(90.0, 15.0), samples[0].rotation)
                assertEquals(entity.position, samples[1].position)
                assertEquals(ContextRotation(90.0, 15.0), samples[1].rotation)
                assertEquals(entity.position, samples[2].position)
                assertEquals(entity.rotation, samples[2].rotation)
            }
            val restored = executor.blockTests.last()
            assertEquals(ContextPosition(10.0, 64.0, 10.0), restored.position)
            assertEquals(ContextRotation(90.0, 15.0), restored.rotation)
            assertEquals(null, restored.executor)
        }
    }

    @Test
    fun actualBreakInsideExecuteAsSkipsLaterEntitiesAndResumesTheOuterLoop() = isolated {
        Fixture().use { f ->
            val arena = f.session.arena
            val root = f.session.rootExecutionContext()
            val rootScope = arena.rootScope
            val origin = Origin(span(), span())
            val rootMeta = arena.metadata(rootScope, origin, root.id)
            val asScope = arena.childScope(rootScope)
            val asFrame = arena.executorContext(root, asScope)
            val asMeta = arena.metadata(asScope, origin, asFrame.id)
            val count = arena.scoreRef(rootMeta, f.int, MinecraftScoreTarget.Fixed(ScoreHolder.named("#break")), "count")
            val after = arena.scoreRef(rootMeta, f.int, MinecraftScoreTarget.Fixed(ScoreHolder.named("#after")), "value")
            val loopRegion = arena.freshRegion()
            val one = arena.expr(asMeta, f.int, ExprNode.Literal(ConstantValue.Integral(1)))
            val increment = arena.expr(
                asMeta,
                f.int,
                ExprNode.Binary(BinaryOperator.ADD, count.load(), one)
            )
            val body = arena.block(
                asMeta,
                listOf(
                    arena.stmt(asMeta, StmtNode.Assign(count, increment)),
                    arena.stmt(asMeta, StmtNode.Break(loopRegion))
                )
            )
            val executeAs = arena.stmt(
                rootMeta,
                StmtNode.ExecuteAs(
                    arena.entitySet(rootMeta, EntityQuery.allPlayers()),
                    arena.entityHandle(asMeta, asFrame),
                    body,
                    root,
                    asFrame
                )
            )
            val loopBody = arena.block(rootMeta, listOf(executeAs))
            val three = arena.expr(rootMeta, f.int, ExprNode.Literal(ConstantValue.Integral(3)))
            val condition = arena.expr(
                rootMeta,
                f.bool,
                ExprNode.Binary(BinaryOperator.LT, count.load(), three)
            )
            val loop = arena.stmt(rootMeta, StmtNode.While(condition, loopBody, loopRegion))
            val markAfter = arena.stmt(
                rootMeta,
                StmtNode.Assign(
                    after,
                    arena.expr(rootMeta, f.int, ExprNode.Literal(ConstantValue.Integral(7)))
                )
            )
            val sample = arena.stmt(
                rootMeta,
                StmtNode.Eval(
                    arena.expr(
                        rootMeta,
                        f.bool,
                        ExprNode.BlockMatches(
                            arena.blockHandle(
                                rootMeta,
                                arena.position(rootMeta, PositionNode.Current(root.id))
                            ),
                            BlockPredicate.block("minecraft:stone")
                        )
                    )
                )
            )
            val tree = arena.block(rootMeta, listOf(loop, markAfter, sample))
            val compiled = f.compile(tree)
            val alice = player("alice", 1.0, 64.0, 1.0, 0.0, 0.0)
            val bob = player("bob", 2.0, 64.0, 2.0, 0.0, 0.0)
            val executor = f.execute(
                compiled,
                listOf(alice, bob),
                rootPosition = ContextPosition(10.0, 64.0, 10.0),
                rootRotation = ContextRotation(90.0, 0.0),
                world = ::allStone,
                initialScores = mapOf("#break count" to 0, "#after value" to 0)
            )
            assertEquals(
                1,
                executor.score("#break", "count"),
                "break must abandon the remaining executeAs entities"
            )
            assertEquals(
                7,
                executor.score("#after", "value"),
                "the command after the loop must still run"
            )
            val restored = executor.blockTests.single()
            assertEquals(ContextPosition(10.0, 64.0, 10.0), restored.position)
            assertEquals(ContextRotation(90.0, 0.0), restored.rotation)
            assertEquals(null, restored.executor)
        }
    }
}
