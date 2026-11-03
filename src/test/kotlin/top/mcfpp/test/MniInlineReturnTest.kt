package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.StorageBinding
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.analysis.StoredData
import top.mcfpp.analysis.Symbol
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueFacts
import top.mcfpp.analysis.ValueKnowledge
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as P

private val INLINE_RETURN_SPAN = SourceSpan("inline-return.mcfpp", 0, 16, 1, 1, 1, 17)

private object InlineReturnUnusedMacroEntry {
    @JvmStatic
    fun unused(context: MacroContext): Block = error("The inline-return test macro is never invoked")
}

/**
 * C_RETURN: one callback exit is local to that callback. A non-void callback becomes an isolated
 * result expression; a void callback becomes a single-exit inline loop. The tests execute the real
 * expanded, validated and lowered tree: early returns must not skip caller statements, a nested
 * callback must not exit its enclosing callback, and break/continue must stay inside the callback.
 */
class MniInlineReturnTest {
    private class PlanFixture : AutoCloseable {
        val int = ImmutableTypeRef<IntT>(TypeKey(1, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(1, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(1, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(1, 5), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(1, 4), "void", kind = TypeKind.VOID)
        private val types = object : TypeService {
            override fun intType(): TypeRef<IntT> = int
            override fun boolType(): TypeRef<BoolT> = bool
            override fun floatType(): TypeRef<FloatT> = float
            override fun stringType(): TypeRef<StringT> = string
            override fun voidType(): TypeRef<VoidT> = void
            override fun lookup(name: String): TypeRef<*> = error("No type lookup in this fixture")
            override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("No function lookup in this fixture")
        }
        private val environment = ExpansionEnvironment(types, configurationKey = "inline-return")
        private val transaction = CompilerTransaction(
            CompilerTransactionBackend {
                object : PreparedCompilerCommit {
                    override fun apply() = Unit
                    override fun rollback() = Unit
                }
            }
        )
        val session = ExpansionSession(
            ExpansionSite(INLINE_RETURN_SPAN, "inline-return::plan"),
            ExpansionBudget(maxNodes = 1_000_000),
            environment,
            transaction
        )
        // All plan-owned Code must use the session's own arena; a separately constructed arena
        // has a different scope registry and can never be used for expansion/validation.
        val arena = session.arena
        private val origin = Origin(INLINE_RETURN_SPAN, expansionFrames = emptyList())
        val meta: CodeMetadata = arena.metadata(arena.rootScope, origin)
        private var callbackSequence = 0L

        fun scope(parent: ScopeId = arena.rootScope): ScopeId = arena.childScope(parent)

        fun <T> ref(type: TypeRef<T>, writable: Boolean = true): ImmutableRef<T> = arena.ref(meta, type, NodeLocation(arena.freshSymbol(), writable = writable))

        fun integer(value: Long): Expr<IntT> = arena.expr(meta, int, ExprNode.Literal(ConstantValue.Integral(value)))

        fun boolean(value: Boolean): Expr<BoolT> = arena.expr(meta, bool, ExprNode.Literal(ConstantValue.Bool(value)))

        fun stmt(node: StmtNode, scope: ScopeId = arena.rootScope): ImmutableStmt = arena.stmt(arena.metadata(scope, origin), node)

        fun block(statements: List<Stmt>, scope: ScopeId = arena.rootScope): ImmutableBlock = arena.block(arena.metadata(scope, origin), statements)

        fun callback(
            signature: FunctionSignature<*>,
            body: Block,
            returnRegion: RegionId = arena.freshRegion(),
            captures: Set<SymbolId> = emptySet(),
            captureReferences: Map<SymbolId, Ref<*>> = emptyMap()
        ): ImmutableInlineFn<Any?> {
            val id = ++callbackSequence
            return arena.inline<Any?>(
                meta,
                signature,
                CallbackId(id, "inline-return-callback-$id"),
                InlineTarget.Body(emptyList(), body, captures, returnRegion, captureReferences)
            )
        }

        fun expander(): MacroExpander {
            val method = InlineReturnUnusedMacroEntry::class.java.getMethod("unused", MacroContext::class.java)
            val reference = ImmutableFunctionRef(
                FunctionKey(701, "inlineUnused"),
                "inlineUnused",
                FunctionSignature.of(emptyList(), void),
                FunctionKind.MACRO
            )
            val prepared = PreparedMacroSignature(
                "inlineUnused",
                method,
                emptyList(),
                PreparedMacroType.Source("void", INLINE_RETURN_SPAN),
                INLINE_RETURN_SPAN
            )
            val macro = MacroFunction("inline", "unused", method, reference, true, prepared)
            return MacroExpander(session, listOf(macro), "inline-return")
        }

        fun expand(code: Code): Code = MacroTreeExpander(expander()).expand(session, code)

        fun validate(code: Code): ValidatedCode = CodeValidator().validate(code, session.validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode

        fun lower(code: Code): LoweringPlan = CodeLowerer().lower(validate(code), LoweringTarget(FunctionKey(9, "inline:target")))

        fun validateRoot(code: Code, result: ValidationRegion): ValidatedCode = CodeValidator().validate(
            code,
            ValidationEnvironment(arena, resultRegion = result),
            ValidationPhase.FINAL
        ) as ValidatedCode

        fun lowerRoot(code: Code, result: ValidationRegion): LoweringPlan = CodeLowerer().lower(validateRoot(code, result), LoweringTarget(FunctionKey(9, "inline:target")))

        override fun close() = session.close()
    }

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

    /**
     * Mirrors the existing production source-projection fixture. The callback tree is built from
     * the same captured compiler bindings as the bridge, then expanded and lowered through the real
     * production path before ScoreCommandExecutor runs the emitted commands.
     */
    private class ProductionFixture : AutoCloseable {
        val namespace = Namespace("inline-return")
        val file = MCFPPFile(namespace)
        val caller = Function("caller", namespace.identifier, null)
        val counter = MCInt("counter")
        val total = MCInt("total")
        val counterPlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val totalPlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val counterData = StoredData(
            counterPlace,
            NBTPath(StorageSource("inline-return:input")).memberIndex("counter"),
            layout = StorageLayout.Scoreboard(counter.name, counter.sbObject.name)
        )
        val totalData = StoredData(
            totalPlace,
            NBTPath(StorageSource("inline-return:input")).memberIndex("total"),
            layout = StorageLayout.Scoreboard(total.name, total.sbObject.name)
        )
        val session: ExpansionSession
        val bindings: CompilerSourceBindings
        val environment: SourceProjectionEnvironment
        val arena: CodeArena
        val int: TypeRef<*>
        val bool: TypeRef<*>
        val void: TypeRef<*>
        private val origin: Origin

        init {
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            caller.declarationFile = file
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            val sink = Function("sink", namespace.identifier, parser("{}").curlBlock()).apply {
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
            session = ExpansionSession(
                ExpansionSite(ready.call.source, "inline-return::production"),
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
            origin = Origin(ready.call.source, expansionFrames = session.origin.expansionFrames)
            int = bindings.binding.types.ref(MCFPPBaseType.Int)
            bool = bindings.binding.types.ref(MCFPPBaseType.Bool)
            void = bindings.binding.types.ref(MCFPPPrivateType.Void)
        }

        fun meta(scope: ScopeId = arena.rootScope): CodeMetadata = arena.metadata(scope, origin, bindings.loweringTarget.executionContext)

        fun root(name: String): Ref<*> = bindings.roots.getValue(bindings.binding.symbol(name)!!.symbol)

        fun integer(value: Long, scope: ScopeId = arena.rootScope): Expr<*> = arena.expr(meta(scope), int, ExprNode.Literal(ConstantValue.Integral(value)))

        fun stmt(node: StmtNode, scope: ScopeId = arena.rootScope): ImmutableStmt = arena.stmt(meta(scope), node)

        fun block(statements: List<Stmt>, scope: ScopeId = arena.rootScope): ImmutableBlock = arena.block(meta(scope), statements)

        fun captureExpander(): MacroExpander {
            val method = InlineReturnUnusedMacroEntry::class.java.getMethod("unused", MacroContext::class.java)
            val reference = ImmutableFunctionRef(
                FunctionKey(702, "productionInlineUnused"),
                "productionInlineUnused",
                FunctionSignature.of(emptyList(), void),
                FunctionKind.MACRO
            )
            val prepared = PreparedMacroSignature(
                "productionInlineUnused",
                method,
                emptyList(),
                PreparedMacroType.Source("void", INLINE_RETURN_SPAN),
                INLINE_RETURN_SPAN
            )
            return MacroExpander(
                session,
                listOf(MacroFunction("inline", "unused", method, reference, true, prepared)),
                "inline-return"
            )
        }

        fun compile(code: Code): CompilerBridge {
            val plan = environment.lower(code)
            val bridge = CompilerBridge(bindings)
            val commit = bridge.prepare(plan).prepare()
            commit.apply()
            return bridge
        }

        fun execute(initial: List<String> = emptyList()): ScoreCommandExecutor {
            val functions = (
                GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values +
                    GlobalScope.stdNamespaces.values
                )
                .flatMap { it.scope.functions.values.flatten() }.associate { function ->
                    function.namespaceID.toString() to function.commands.map { it.analyze() }
                } + Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { it.value.lines() }
            val commands = listOf(
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "scoreboard players set ${counter.name} ${counter.sbObject.name} 0",
                "scoreboard players set ${total.name} ${total.sbObject.name} 0"
            ) + initial + caller.commands.map { it.analyze() } +
                "data remove storage mcfpp:system stack_frame[0]"
            return ScoreCommandExecutor(commands, functions)
        }

        override fun close() = session.close()

        companion object {
            fun parser(text: String) = P(
                CommonTokenStream(
                    mcfppLexer(CharStreams.fromString(text, "inline-return.mcfpp"))
                )
            )
        }
    }

    @Test
    fun earlyReturnFromANonVoidCallbackKeepsFollowingCallerStatements() {
        PlanFixture().use { f ->
            val flag = f.ref(f.bool)
            f.session.registerBinding(flag)
            val callbackLocal = f.arena.local(f.meta, f.int, "callbackLocal", f.arena.freshSymbol())
            val thenScope = f.scope()
            val elseScope = f.scope()
            val returnRegion = f.arena.freshRegion()
            val alignedBody = f.block(
                listOf(
                    f.stmt(StmtNode.LocalDecl(callbackLocal, f.integer(1))),
                    f.stmt(
                        StmtNode.If(
                            flag.load(),
                            f.block(listOf(f.stmt(StmtNode.CallbackReturn(returnRegion, f.integer(3)), thenScope)), thenScope),
                            f.block(listOf(f.stmt(StmtNode.CallbackReturn(returnRegion, f.integer(9)), elseScope)), elseScope)
                        )
                    )
                )
            )
            val flagRoot = (flag as ImmutableRef<*>).location.root
            val callback = f.callback(
                FunctionSignature.of(emptyList(), f.int),
                alignedBody,
                returnRegion,
                setOf<SymbolId>(flagRoot),
                mapOf<SymbolId, Ref<*>>(flagRoot to flag)
            )
            val counter = f.ref(f.int)
            f.session.registerBinding(counter)
            val sum = f.arena.local(f.meta, f.int, "sum", f.arena.freshSymbol())
            val call = f.arena.expr(f.meta, f.int, ExprNode.InlineCall(callback, emptyList()))
            val caller = f.block(
                listOf(
                    f.stmt(StmtNode.LocalDecl(sum, call)),
                    f.stmt(
                        StmtNode.Assign(
                            counter,
                            f.arena.expr(f.meta, f.int, ExprNode.Binary(BinaryOperator.ADD, counter.load(), sum.load()))
                        )
                    ),
                    f.stmt(
                        StmtNode.Assign(
                            counter,
                            f.arena.expr(f.meta, f.int, ExprNode.Binary(BinaryOperator.ADD, counter.load(), f.integer(100)))
                        )
                    )
                )
            )
            val plan = f.lower(f.expand(caller))
            val machine = MniCodeLoweringPlanTest.Machine(plan)
            machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)] = 0L
            machine.storage[MniCodeLoweringPlanTest.Address(flagLocation(flag))] = true
            assertEquals(PlanExitKind.BLOCK to null, machine.run())
            assertEquals(103L, machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)])

            machine.storage[MniCodeLoweringPlanTest.Address(flagLocation(flag))] = false
            machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)] = 0L
            assertEquals(PlanExitKind.BLOCK to null, machine.run())
            assertEquals(109L, machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)])
            assertTrue(
                plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.DeclareLocal>()
                    .map { it.location.root }.distinct().size >= 2
            )
        }
    }

    @Test
    fun nestedCallbackReturnOnlyLeavesTheInnerCallbackAndKeepsLocalsDistinct() {
        PlanFixture().use { f ->
            val innerScope = f.scope()
            val innerRegion = f.arena.freshRegion()
            val inner = f.callback(
                FunctionSignature.of(emptyList(), f.int),
                f.block(listOf(f.stmt(StmtNode.CallbackReturn(innerRegion, f.integer(7)), innerScope)), innerScope),
                innerRegion
            )
            val outerScope = f.scope()
            val outerRegion = f.arena.freshRegion()
            val innerLocal = f.arena.local(f.meta, f.int, "innerLocal", f.arena.freshSymbol())
            val outerLocal = f.arena.local(f.meta, f.int, "outerLocal", f.arena.freshSymbol())
            val innerCall = f.arena.expr(
                f.arena.metadata(outerScope, f.meta.origin),
                f.int,
                ExprNode.InlineCall(inner, emptyList())
            )
            val outerBody = f.block(
                listOf(
                    f.stmt(StmtNode.LocalDecl(innerLocal, innerCall), outerScope),
                    f.stmt(
                        StmtNode.LocalDecl(
                            outerLocal,
                            f.arena.expr(
                                f.arena.metadata(outerScope, f.meta.origin),
                                f.int,
                                ExprNode.Binary(BinaryOperator.ADD, innerLocal.load(), f.integer(1))
                            )
                        ),
                        outerScope
                    ),
                    f.stmt(StmtNode.CallbackReturn(outerRegion, outerLocal.load()), outerScope)
                ),
                outerScope
            )
            val outer = f.callback(FunctionSignature.of(emptyList(), f.int), outerBody, outerRegion)
            val counter = f.ref(f.int)
            f.session.registerBinding(counter)
            val first = f.arena.local(f.meta, f.int, "first", f.arena.freshSymbol())
            val second = f.arena.local(f.meta, f.int, "second", f.arena.freshSymbol())
            val firstCall = f.arena.expr(f.meta, f.int, ExprNode.InlineCall(outer, emptyList()))
            val secondCall = f.arena.expr(f.meta, f.int, ExprNode.InlineCall(outer, emptyList()))
            val sum = f.arena.local(f.meta, f.int, "sum", f.arena.freshSymbol())
            val caller = f.block(
                listOf(
                    f.stmt(StmtNode.LocalDecl(first, firstCall)),
                    f.stmt(StmtNode.LocalDecl(second, secondCall)),
                    f.stmt(
                        StmtNode.LocalDecl(
                            sum,
                            f.arena.expr(
                                f.meta,
                                f.int,
                                ExprNode.Binary(BinaryOperator.ADD, first.load(), second.load())
                            )
                        )
                    ),
                    f.stmt(
                        StmtNode.Assign(
                            counter,
                            f.arena.expr(
                                f.meta,
                                f.int,
                                ExprNode.Binary(BinaryOperator.ADD, counter.load(), sum.load())
                            )
                        )
                    ),
                    f.stmt(
                        StmtNode.Assign(
                            counter,
                            f.arena.expr(
                                f.meta,
                                f.int,
                                ExprNode.Binary(BinaryOperator.ADD, counter.load(), f.integer(100))
                            )
                        )
                    )
                )
            )
            val plan = f.lower(f.expand(caller))
            val machine = MniCodeLoweringPlanTest.Machine(plan)
            machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)] = 0L
            assertEquals(PlanExitKind.BLOCK to null, machine.run())
            // Each outer insertion returns 7 + 1; the nested return must not leave the outer
            // callback early with 7, and the caller's tail statements must still run.
            assertEquals(116L, machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)])
            assertEquals(
                4,
                plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.DeclareLocal>()
                    .filter { it.hint == "innerLocal" || it.hint == "outerLocal" }
                    .map { it.location.root }.distinct().size
            )
        }
    }

    @Test
    fun voidCallbackEarlyReturnFromInsideItsOwnLoopOnlyBreaksTheInlineExit() {
        PlanFixture().use { f ->
            val counter = f.ref(f.int)
            f.session.registerBinding(counter)
            val returnRegion = f.arena.freshRegion()
            val loopRegion = f.arena.freshRegion()
            val loopScope = f.scope()
            val thenScope = f.scope(loopScope)
            val loopMeta = f.arena.metadata(loopScope, f.meta.origin)
            val loopBody = f.block(
                listOf(
                    f.stmt(
                        StmtNode.Assign(
                            counter,
                            f.arena.expr(
                                loopMeta,
                                f.int,
                                ExprNode.Binary(BinaryOperator.ADD, counter.load(), f.integer(1))
                            )
                        ),
                        loopScope
                    ),
                    f.stmt(
                        StmtNode.If(
                            f.arena.expr(
                                loopMeta,
                                f.bool,
                                ExprNode.Binary(BinaryOperator.LT, counter.load(), f.integer(3))
                            ),
                            f.block(listOf(f.stmt(StmtNode.CallbackReturn(returnRegion, null), thenScope)), thenScope),
                            null
                        ),
                        loopScope
                    ),
                    f.stmt(StmtNode.Break(loopRegion), loopScope)
                ),
                loopScope
            )
            val body = f.block(
                listOf(
                    f.stmt(
                        StmtNode.While(
                            f.arena.expr(f.meta, f.bool, ExprNode.Literal(ConstantValue.Bool(true))),
                            loopBody,
                            loopRegion
                        )
                    ),
                    f.stmt(StmtNode.CallbackReturn(returnRegion, null))
                )
            )
            val counterRoot = (counter as ImmutableRef<*>).location.root
            val callback = f.callback(
                FunctionSignature.of(emptyList(), f.void),
                body,
                returnRegion,
                setOf<SymbolId>(counterRoot),
                mapOf<SymbolId, Ref<*>>(counterRoot to counter)
            )
            val call = f.arena.expr(f.meta, f.void, ExprNode.InlineCall(callback, emptyList()))
            val caller = f.block(
                listOf(
                    f.stmt(StmtNode.Eval(call)),
                    f.stmt(
                        StmtNode.Assign(
                            counter,
                            f.arena.expr(
                                f.meta,
                                f.int,
                                ExprNode.Binary(BinaryOperator.ADD, counter.load(), f.integer(100))
                            )
                        )
                    )
                )
            )
            val plan = f.lower(f.expand(caller))
            val machine = MniCodeLoweringPlanTest.Machine(plan)
            machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)] = 0L
            assertEquals(PlanExitKind.BLOCK to null, machine.run())
            assertEquals(101L, machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)])

            machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)] = 10L
            assertEquals(PlanExitKind.BLOCK to null, machine.run())
            assertEquals(111L, machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)])
        }
    }

    @Test
    fun callerLoopUsesTheExplicitCallbackResultInsteadOfACrossingBreak() {
        PlanFixture().use { f ->
            val returnRegion = f.arena.freshRegion()
            val callbackScope = f.scope()
            val callback = f.callback(
                FunctionSignature.of(emptyList(), f.bool),
                f.block(
                    listOf(f.stmt(StmtNode.CallbackReturn(returnRegion, f.boolean(true)), callbackScope)),
                    callbackScope
                ),
                returnRegion
            )
            val counter = f.ref(f.int)
            f.session.registerBinding(counter)
            val index = f.arena.local(f.meta, f.int, "index", f.arena.freshSymbol())
            val loopRegion = f.arena.freshRegion()
            val loopScope = f.scope()
            val loopMeta = f.arena.metadata(loopScope, f.meta.origin)
            val stop = f.arena.local(loopMeta, f.bool, "stop", f.arena.freshSymbol())
            val thenScope = f.scope(loopScope)
            val call = f.arena.expr(loopMeta, f.bool, ExprNode.InlineCall(callback, emptyList()))
            val loopBody = f.block(
                listOf(
                    f.stmt(StmtNode.LocalDecl(stop, call), loopScope),
                    f.stmt(
                        StmtNode.If(
                            stop.load(),
                            f.block(listOf(f.stmt(StmtNode.Break(loopRegion), thenScope)), thenScope),
                            null
                        ),
                        loopScope
                    ),
                    f.stmt(
                        StmtNode.Assign(
                            index,
                            f.arena.expr(
                                f.arena.metadata(loopScope, f.meta.origin),
                                f.int,
                                ExprNode.Binary(BinaryOperator.ADD, index.load(), f.integer(1))
                            )
                        ),
                        loopScope
                    )
                ),
                loopScope
            )
            val caller = f.block(
                listOf(
                    f.stmt(StmtNode.LocalDecl(index, f.integer(0))),
                    f.stmt(
                        StmtNode.While(
                            f.arena.expr(
                                f.meta,
                                f.bool,
                                ExprNode.Binary(BinaryOperator.LT, index.load(), f.integer(3))
                            ),
                            loopBody,
                            loopRegion
                        )
                    ),
                    f.stmt(
                        StmtNode.Assign(
                            counter,
                            f.arena.expr(
                                f.meta,
                                f.int,
                                ExprNode.Binary(BinaryOperator.ADD, counter.load(), f.integer(1))
                            )
                        )
                    )
                )
            )
            val plan = f.lower(f.expand(caller))
            val machine = MniCodeLoweringPlanTest.Machine(plan)
            machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)] = 0L
            assertEquals(PlanExitKind.BLOCK to null, machine.run())
            assertEquals(1L, machine.storage[MniCodeLoweringPlanTest.Address(counter.location.root)])
            assertEquals(0L, machine.storage[MniCodeLoweringPlanTest.Address(index.location.root)])
        }
    }

    @Test
    fun breakContinueAndForeignReturnCannotCrossTheCallbackBoundary() {
        for (kind in listOf("break", "continue")) {
            PlanFixture().use { f ->
                val foreign = f.arena.freshRegion()
                val body = f.block(
                    listOf(
                        f.stmt(
                            if (kind == "break") StmtNode.Break(foreign)
                            else StmtNode.Continue(foreign)
                        )
                    )
                )
                val callback = f.callback(FunctionSignature.of(emptyList(), f.void), body)
                val call = f.arena.expr(f.meta, f.void, ExprNode.InlineCall(callback, emptyList()))
                val failure = assertFailsWith<CodeModelException> { f.validate(f.expand(call)) }
                assertTrue(failure.message!!.contains("escapes"), failure.message)
            }
        }
        PlanFixture().use { f ->
            val ownRegion = f.arena.freshRegion()
            val foreign = f.arena.freshRegion()
            val body = f.block(listOf(f.stmt(StmtNode.CallbackReturn(foreign, f.integer(1)))))
            val callback = f.callback(FunctionSignature.of(emptyList(), f.int), body, ownRegion)
            val call = f.arena.expr(f.meta, f.int, ExprNode.InlineCall(callback, emptyList()))
            val failure = assertFailsWith<CodeModelException> { f.validate(f.expand(call)) }
            assertTrue(failure.message!!.contains("outside a callback region"), failure.message)
        }
    }

    @Test
    fun callbackRootExitBindsItsOwnResultTypeAndStopsInsideTheCallback() {
        PlanFixture().use { f ->
            val region = f.arena.freshRegion()
            val local = f.arena.local(f.meta, f.int, "value", f.arena.freshSymbol())
            val body = f.block(
                listOf(
                    f.stmt(StmtNode.LocalDecl(local, f.integer(6))),
                    f.stmt(
                        StmtNode.CallbackReturn(
                            region,
                            f.arena.expr(
                                f.meta,
                                f.int,
                                ExprNode.Binary(BinaryOperator.ADD, local.load(), f.integer(1))
                            )
                        )
                    )
                )
            )
            val plan = f.lowerRoot(body, ValidationRegion(region, f.int, callback = true))
            assertEquals(PlanExitKind.CALLBACK to 7L, MniCodeLoweringPlanTest.Machine(plan).run())
        }
    }

    @Test
    fun emittedCommandsRunTheFollowingCallerStatementsAfterTheCallbackResult() = isolated {
        ProductionFixture().use { f ->
            val counter = f.root("counter")
            val total = f.root("total")
            val counterRoot = (counter as ImmutableRef<*>).location.root
            val bodyScope = f.arena.childScope(f.arena.rootScope)
            val bodyMeta = f.meta(bodyScope)
            val returnRegion = f.arena.freshRegion()
            val thenScope = f.arena.childScope(bodyScope)
            val counterIncrement = f.arena.expr(
                bodyMeta,
                f.int,
                ExprNode.Binary(BinaryOperator.ADD, counter.load(), f.integer(1))
            )
            val earlyReturn = f.arena.block(
                f.meta(thenScope),
                listOf(
                    f.arena.stmt(
                        f.meta(thenScope),
                        StmtNode.CallbackReturn(
                            returnRegion,
                            f.arena.expr(f.meta(thenScope), f.int, ExprNode.Literal(ConstantValue.Integral(5)))
                        )
                    )
                )
            )
            val callbackBody = f.arena.block(
                bodyMeta,
                listOf(
                    f.arena.stmt(bodyMeta, StmtNode.Assign(counter, counterIncrement)),
                    f.arena.stmt(
                        bodyMeta,
                        StmtNode.If(
                            f.arena.expr(
                                bodyMeta,
                                f.bool,
                                ExprNode.Binary(BinaryOperator.LT, counter.load(), f.integer(2))
                            ),
                            earlyReturn,
                            null
                        )
                    ),
                    f.arena.stmt(
                        bodyMeta,
                        StmtNode.CallbackReturn(
                            returnRegion,
                            f.arena.expr(bodyMeta, f.int, ExprNode.Literal(ConstantValue.Integral(9)))
                        )
                    )
                )
            )
            val callback = f.arena.inline<Any?>(
                f.meta(),
                FunctionSignature.of(emptyList(), f.int),
                CallbackId(3, "inline-return-production"),
                InlineTarget.Body(
                    emptyList(),
                    callbackBody,
                    setOf(counterRoot),
                    returnRegion,
                    mapOf<SymbolId, Ref<*>>(counterRoot to counter)
                )
            )
            val callerMeta = f.meta()
            val sum = f.arena.local(callerMeta, f.int, "sum", f.arena.freshSymbol())
            val call = f.arena.expr(callerMeta, f.int, ExprNode.InlineCall(callback, emptyList()))
            val caller = f.arena.block(
                callerMeta,
                listOf(
                    f.arena.stmt(callerMeta, StmtNode.LocalDecl(sum, call)),
                    f.arena.stmt(
                        callerMeta,
                        StmtNode.Assign(
                            total,
                            f.arena.expr(callerMeta, f.int, ExprNode.Binary(BinaryOperator.ADD, total.load(), sum.load()))
                        )
                    ),
                    f.arena.stmt(
                        callerMeta,
                        StmtNode.Assign(
                            counter,
                            f.arena.expr(callerMeta, f.int, ExprNode.Binary(BinaryOperator.ADD, counter.load(), sum.load()))
                        )
                    )
                )
            )
            val expanded = MacroTreeExpander(f.captureExpander()).expand(f.session, caller)
            f.compile(expanded)
            val machine = f.execute()
            assertEquals(5, machine.values["${f.total.name} ${f.total.sbObject.name}"])
            assertEquals(6, machine.values["${f.counter.name} ${f.counter.sbObject.name}"])
            assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        }
    }

    private fun flagLocation(flag: Ref<*>): SymbolId = (flag as ImmutableRef<*>).location.root
}
