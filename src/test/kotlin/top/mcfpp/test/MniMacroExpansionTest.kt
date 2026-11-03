@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MacroParam
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import java.util.Collections
import java.util.IdentityHashMap
import java.util.function.Consumer
import kotlin.test.*
import kotlin.test.Test

/** Host entries for recursive expansion; only the generated code is inspected. */
class MniMacroExpansionEntries {
    companion object {
        var innerIntRef: FunctionRef<*>? = null
        var bumpRef: FunctionRef<*>? = null
        var recurRef: FunctionRef<*>? = null
        var relayRef: FunctionRef<*>? = null
        var innerExecutions = 0
        var wrapExecutions = 0
        var showExecutions = 0
        var recurExecutions = 0

        @JvmStatic
        @MNIMacro(identifier = "innerInt", params = [MacroParam(name = "value", type = "int")], returnType = "int")
        fun innerInt(context: MacroContext, value: Value<IntT>): Expr<IntT> {
            innerExecutions++
            return context.quote().expression(
                context.types().intType(),
                Consumer { builder ->
                    builder.yield(builder.add(value.expr(), builder.intLiteral(1)))
                }
            )
        }

        @JvmStatic
        @MNIMacro(identifier = "identityInt", params = [MacroParam(name = "value", type = "int")], returnType = "int")
        fun identityInt(context: MacroContext, value: Value<IntT>): Expr<IntT> = context.quote().expression(
            context.types().intType(),
            Consumer { builder ->
                builder.yield(value.expr())
            }
        )

        @JvmStatic
        @MNIMacro(identifier = "wrapInner", returnType = "int")
        fun wrapInner(context: MacroContext): Expr<IntT> {
            wrapExecutions++
            val target = requireNotNull(innerIntRef)
            @Suppress("UNCHECKED_CAST")
            val function = target as FunctionRef<IntT>
            return context.quote().expression(
                context.types().intType(),
                Consumer { builder ->
                    builder.yield(builder.call(function, listOf(CallArgument.value(builder.intLiteral(7)))))
                }
            )
        }

        @JvmStatic
        @MNIMacro(identifier = "bump", params = [MacroParam(name = "value", type = "int", mode = ParamMode.REF)], returnType = "int")
        fun bump(context: MacroContext, value: Ref<IntT>): Expr<IntT> = context.quote().expression(
            context.types().intType(),
            Consumer { builder ->
                builder.assign(value, builder.add(value.load(), builder.intLiteral(1)))
                builder.yield(value.load())
            }
        )

        @JvmStatic
        @MNIMacro(identifier = "show", params = [MacroParam(name = "value", type = "int", mode = ParamMode.REF)], returnType = "int")
        fun show(context: MacroContext, value: Ref<IntT>): Expr<IntT> {
            showExecutions++
            val target = requireNotNull(bumpRef)
            @Suppress("UNCHECKED_CAST")
            val function = target as FunctionRef<IntT>
            return context.quote().expression(
                context.types().intType(),
                Consumer { builder ->
                    builder.yield(builder.call(function, listOf(CallArgument.ref(value))))
                }
            )
        }

        @JvmStatic
        @MNIMacro(identifier = "relay", params = [MacroParam(name = "value", type = "int", mode = ParamMode.REF)], returnType = "int")
        fun relay(context: MacroContext, value: Ref<IntT>): Expr<IntT> {
            val target = requireNotNull(relayRef)
            @Suppress("UNCHECKED_CAST")
            val function = target as FunctionRef<IntT>
            return context.quote().expression(
                context.types().intType(),
                Consumer { builder ->
                    builder.yield(builder.call(function, listOf(CallArgument.ref(value))))
                }
            )
        }

        @JvmStatic
        @MNIMacro(identifier = "explode", returnType = "int")
        fun explode(context: MacroContext): Expr<IntT> {
            context.resources().reserve(
                ResourceKind.OBJECTIVE,
                "pending",
                ResourceData.of(
                    ConstantValue.Record(mapOf("criteria" to ConstantValue.Text("dummy")))
                )
            )
            error("host exploded")
        }

        @JvmStatic
        @MNIMacro(identifier = "wrongType", returnType = "int")
        fun wrongType(context: MacroContext): Expr<BoolT> = context.quote().expression(
            context.types().boolType(),
            Consumer { builder ->
                builder.yield(builder.boolLiteral(true))
            }
        )

        @JvmStatic
        @MNIMacro(identifier = "recur", returnType = "int")
        fun recur(context: MacroContext): Expr<IntT> {
            recurExecutions++
            val target = requireNotNull(recurRef)
            @Suppress("UNCHECKED_CAST")
            val function = target as FunctionRef<IntT>
            return context.quote().expression(
                context.types().intType(),
                Consumer { builder ->
                    builder.yield(builder.call(function, emptyList()))
                }
            )
        }

        @JvmStatic
        @MNIMacro(identifier = "withHelper", returnType = "void")
        fun withHelper(context: MacroContext): Block {
            val target = requireNotNull(innerIntRef)
            @Suppress("UNCHECKED_CAST")
            val function = target as FunctionRef<IntT>
            val helper = context.helpers().define(
                "call a macro",
                FunctionSignature.of(emptyList(), context.types().voidType()),
                GeneratedBody { builder, _ ->
                    builder.evaluate(builder.call(function, listOf(CallArgument.value(builder.intLiteral(3)))))
                }
            )
            return context.quote().block(Consumer { builder -> builder.callVoid(helper, emptyList()) })
        }
    }
}

class MniMacroExpansionTest {
    private class Types : TypeService {
        val int = ImmutableTypeRef<IntT>(TypeKey(91, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(91, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(91, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(91, 4), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(91, 5), "void", kind = TypeKind.VOID)

        override fun intType(): TypeRef<IntT> = int
        override fun boolType(): TypeRef<BoolT> = bool
        override fun floatType(): TypeRef<FloatT> = float
        override fun stringType(): TypeRef<StringT> = string
        override fun voidType(): TypeRef<VoidT> = void
        override fun lookup(name: String): TypeRef<*> = when (name) {
            "int" -> int
            "bool" -> bool
            "float" -> float
            "string" -> string
            "void" -> void
            else -> error("Unknown test type: $name")
        }
        override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("Test macros are never resolved through TypeService")
    }

    private class Backend {
        var preparations = 0
        var publications = 0
        val transaction = CompilerTransactionBackend { changes ->
            preparations++
            object : PreparedCompilerCommit {
                override fun apply() {
                    publications++
                    changes.helpers.forEach { }
                }
                override fun rollback() { }
            }
        }
    }

    private class Fixture {
        val types = Types()
        val diagnostics = arrayListOf<MacroRegistrationDiagnostic>()
        val macros: List<MacroFunction>
        val environment: ExpansionEnvironment
        val backend = Backend()

        init {
            val registry = MacroRegistry(
                "test",
                901,
                object : MacroTypeResolver {
                    override fun resolve(typeName: String): TypeRef<*>? = when (typeName) {
                        "int" -> types.int
                        "bool" -> types.bool
                        "float" -> types.float
                        "string" -> types.string
                        "void" -> types.void
                        else -> null
                    }
                    override fun inlineType(signature: InlineSignature<*>): TypeRef<*>? = null
                },
                { diagnostics += it }
            )
            registry.register(MniMacroExpansionEntries::class.java)
            check(diagnostics.isEmpty()) { diagnostics.joinToString("\n") { it.message } }
            macros = registry.all()
            MniMacroExpansionEntries.innerIntRef = registry.find("innerInt").single().reference
            MniMacroExpansionEntries.bumpRef = registry.find("bump").single().reference
            MniMacroExpansionEntries.recurRef = registry.find("recur").single().reference
            MniMacroExpansionEntries.relayRef = registry.find("show").single().reference
            environment = ExpansionEnvironment(types, macros.map { it.reference }, configurationKey = "expansion-test")
            MniMacroExpansionEntries.innerExecutions = 0
            MniMacroExpansionEntries.wrapExecutions = 0
            MniMacroExpansionEntries.showExecutions = 0
            MniMacroExpansionEntries.recurExecutions = 0
        }

        fun macro(identifier: String): MacroFunction = macros.single { it.identifier == identifier }

        fun session(budget: ExpansionBudget = ExpansionBudget()): ExpansionSession = ExpansionSession(ExpansionSite(SPAN, "test::root"), budget, environment, CompilerTransaction(backend.transaction))

        fun expander(session: ExpansionSession): MacroExpander = MacroExpander(session, macros, "expansion-test")
    }

    companion object {
        val SPAN = SourceSpan("mni-expansion.mcfpp", 0, 12, 1, 1, 1, 13)

        private fun valueBinding(
            fixture: Fixture,
            session: ExpansionSession,
            macro: MacroFunction,
            expression: Expr<*>
        ): BoundMacroCall {
            val arena = session.arena
            val metadata = arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
            val local = arena.local(metadata, macro.parameters.single().type(), "argument")
            val prelude = arena.block(metadata, listOf(arena.stmt(metadata, StmtNode.LocalDecl(local, expression))))
            return BoundMacroCall(macro, SPAN, prelude, listOf(arena.value(local.load())), emptyList(), arena.rootScope)
        }

        private fun emptyBinding(fixture: Fixture, session: ExpansionSession, macro: MacroFunction): BoundMacroCall {
            val arena = session.arena
            val metadata = arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
            return BoundMacroCall(macro, SPAN, arena.block(metadata, emptyList()), emptyList(), emptyList(), arena.rootScope)
        }

        private fun referenceBinding(
            fixture: Fixture,
            session: ExpansionSession,
            macro: MacroFunction,
            destination: Ref<*>
        ): BoundMacroCall {
            val arena = session.arena
            val metadata = arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
            val location = when (destination) {
                is ImmutableRef<*> -> destination.location
                is ImmutableLocal<*> -> destination.location
                else -> error("Unsupported test reference")
            }
            val address = arena.freshAddress()
            val prelude = arena.block(
                metadata,
                listOf(
                    arena.stmt(
                        metadata,
                        StmtNode.CaptureAddress(address, location, destination.type())
                    )
                )
            )
            val captured = arena.ref(
                metadata,
                destination.type(),
                NodeLocation(
                    location.root,
                    executionContextId = location.executionContextId,
                    writable = location.writable,
                    capturedBase = address
                )
            )
            return BoundMacroCall(macro, SPAN, prelude, listOf(captured), emptyList(), arena.rootScope)
        }

        private fun scoreBinding(
            session: ExpansionSession,
            macro: MacroFunction,
            score: Ref<IntT>
        ): BoundMacroCall {
            val arena = session.arena
            val metadata = arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
            return BoundMacroCall(
                macro,
                SPAN,
                arena.block(metadata, emptyList()),
                listOf(score),
                emptyList(),
                arena.rootScope
            )
        }

        private fun findCall(code: Code): ExprNode.Call {
            var found: ExprNode.Call? = null
            fun visit(value: Code) {
                if (found != null) return
                when (value) {
                    is ImmutableExpr<*> -> when (val node = value.node) {
                        is ExprNode.Call -> found = node
                        is ExprNode.ExprBlock -> visit(node.block)
                        is ExprNode.Unary -> visit(node.operand)
                        is ExprNode.Binary -> {
                            visit(node.left)
                            visit(node.right)
                        }
                        is ExprNode.Convert -> visit(node.value)
                        else -> Unit
                    }
                    is ImmutableStmt -> when (val node = value.node) {
                        is StmtNode.LocalDecl -> visit(node.initialValue)
                        is StmtNode.Eval -> visit(node.expression)
                        is StmtNode.Assign -> visit(node.expression)
                        is StmtNode.Yield -> visit(node.value)
                        else -> Unit
                    }
                    is ImmutableBlock -> value.statements().forEach { visit(it) }
                    else -> Unit
                }
            }
            visit(code)
            return assertNotNull(found)
        }

        private fun findAssignment(code: Code): StmtNode.Assign {
            var found: StmtNode.Assign? = null
            fun visit(value: Code) {
                if (found != null) return
                when (value) {
                    is ImmutableExpr<*> -> when (val node = value.node) {
                        is ExprNode.ExprBlock -> visit(node.block)
                        else -> Unit
                    }
                    is ImmutableStmt -> when (val node = value.node) {
                        is StmtNode.LocalDecl -> visit(node.initialValue)
                        is StmtNode.Eval -> visit(node.expression)
                        is StmtNode.Assign -> found = node
                        is StmtNode.Yield -> visit(node.value)
                        else -> Unit
                    }
                    is ImmutableBlock -> value.statements().forEach { visit(it) }
                    else -> Unit
                }
            }
            visit(code)
            return assertNotNull(found)
        }

        /** Count actual capture declarations, independent of how the printer lays out expr blocks. */
        private fun captureCount(code: Code): Int {
            val seen = Collections.newSetFromMap(IdentityHashMap<Code, Boolean>())
            var count = 0
            fun visit(value: Code) {
                if (!seen.add(value)) return
                when (value) {
                    is ImmutableExpr<*> -> when (val node = value.node) {
                        is ExprNode.Unary -> visit(node.operand)
                        is ExprNode.Binary -> {
                            visit(node.left)
                            visit(node.right)
                        }
                        is ExprNode.Convert -> visit(node.value)
                        is ExprNode.Field -> visit(node.receiver)
                        is ExprNode.Index -> {
                            visit(node.receiver)
                            visit(node.index)
                        }
                        is ExprNode.Call -> node.arguments.forEach { visit(it.payload.code) }
                        is ExprNode.MacroCall -> node.arguments.forEach { visit(it.payload.code) }
                        is ExprNode.ExprBlock -> visit(node.block)
                        is ExprNode.InlineCall -> {
                            visit(node.callback)
                            node.arguments.forEach { visit(it.payload.code) }
                        }
                        else -> Unit
                    }
                    is ImmutableStmt -> when (val node = value.node) {
                        is StmtNode.CaptureAddress -> count++
                        is StmtNode.LocalDecl -> visit(node.initialValue)
                        is StmtNode.Assign -> visit(node.expression)
                        is StmtNode.Eval -> visit(node.expression)
                        is StmtNode.If -> {
                            visit(node.condition)
                            visit(node.thenBlock)
                            node.elseBlock?.let { visit(it) }
                        }
                        is StmtNode.While -> {
                            visit(node.condition)
                            visit(node.body)
                        }
                        is StmtNode.ScopeBlock -> visit(node.block)
                        is StmtNode.CallbackReturn -> node.value?.let { visit(it) }
                        is StmtNode.Yield -> visit(node.value)
                        is StmtNode.ExecuteAs -> visit(node.body)
                        is StmtNode.At -> visit(node.body)
                        is StmtNode.Rotated -> visit(node.body)
                        else -> Unit
                    }
                    is ImmutableBlock -> value.statements().forEach { visit(it) }
                    is ImmutableValue<*> -> visit(value.expr())
                    is ImmutableConst<*> -> visit(value.expr())
                    is ImmutableInlineFn<*> -> (value.target as? InlineTarget.Body)?.let { visit(it.block) }
                    else -> Unit
                }
            }
            visit(code)
            return count
        }
    }

    @Test
    fun macrosInsideArgumentPreludesJoinTheBudgetedChain() {
        val fixture = Fixture()
        fixture.session().use { session ->
            val arena = session.arena
            val metadata = arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
            val literal = arena.expr(metadata, fixture.types.int, ExprNode.Literal(ConstantValue.Integral(5)))
            val inner = arena.expr(
                metadata,
                fixture.types.int,
                ExprNode.MacroCall(fixture.macro("innerInt").reference, listOf(CallArgument.value(literal)))
            )
            val result = fixture.expander(session).expandRoot(
                valueBinding(fixture, session, fixture.macro("identityInt"), inner)
            )
            val validated = result.validate()
            assertEquals(fixture.types.int, (validated.code as Expr<*>).type())
            assertEquals(1, MniMacroExpansionEntries.innerExecutions)
            assertEquals(0, fixture.backend.preparations)
            assertTrue(session.transaction.isOpen)
            assertEquals(1, session.budget.depth)
        }
    }

    @Test
    fun macrosInsideHostReturnedTreesJoinTheSameChain() {
        val fixture = Fixture()
        fixture.session().use { session ->
            val result = fixture.expander(session).expandRoot(
                emptyBinding(fixture, session, fixture.macro("wrapInner"))
            )
            result.validate()
            assertEquals(1, MniMacroExpansionEntries.wrapExecutions)
            assertEquals(1, MniMacroExpansionEntries.innerExecutions)
            assertEquals(0, fixture.backend.preparations)
        }
    }

    @Test
    fun expandedMemberCallArgumentKeepsItsExactReceiver() {
        val fixture = Fixture()
        fixture.session().use { session ->
            val arena = session.arena
            val metadata = arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
            val receiver = arena.ref(metadata, fixture.types.int, NodeLocation(arena.freshSymbol(), writable = true))
            session.registerBinding(receiver)
            val member = ImmutableFunctionRef(
                FunctionKey(902, "member"),
                "member",
                FunctionSignature.of(
                    listOf(ParameterView.of("value", fixture.types.int, ParamMode.VALUE)),
                    fixture.types.int
                ),
                FunctionKind.ORDINARY
            )
            session.registerFunction(member)
            val literal = arena.expr(metadata, fixture.types.int, ExprNode.Literal(ConstantValue.Integral(5)))
            val nested = arena.expr(
                metadata,
                fixture.types.int,
                ExprNode.MacroCall(fixture.macro("innerInt").reference, listOf(CallArgument.value(literal)))
            )
            val call = arena.expr(
                metadata,
                fixture.types.int,
                ExprNode.Call(member, listOf(CallArgument.value(nested)), emptyList(), receiver)
            )
            val result = fixture.expander(session).expandRoot(
                valueBinding(fixture, session, fixture.macro("identityInt"), call)
            )
            val validated = result.validate()
            assertSame(receiver, findCall(validated.code).receiver)
            assertEquals(1, MniMacroExpansionEntries.innerExecutions)
            assertEquals(0, fixture.backend.preparations)
        }
    }

    @Test
    fun namedHolderScoreRefAliasesThroughNestedExpansionWithoutRecapture() {
        val fixture = Fixture()
        fixture.session().use { session ->
            val arena = session.arena
            val metadata = arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
            val holder = ScoreHolder.named("#macro_score")
            val score = arena.scoreRef(metadata, fixture.types.int, MinecraftScoreTarget.Fixed(holder), "points")
            val result = fixture.expander(session).expandRoot(
                scoreBinding(session, fixture.macro("relay"), score)
            )
            val validated = result.validate()
            val assignment = findAssignment(validated.code)
            assertSame(score, assignment.destination)
            val add = assertIs<ExprNode.Binary>((assignment.expression as ImmutableExpr<*>).node)
            val read = assertIs<ExprNode.ScoreRead>((add.left as ImmutableExpr<*>).node)
            assertSame(score.target, read.target)
            assertEquals("points", read.objective)
            val printed = CodePrinter().print(validated.code)
            assertTrue(printed.contains(holder.name))
            assertTrue(printed.contains("points"))
            assertEquals(0, captureCount(validated.code))
            assertEquals(1, MniMacroExpansionEntries.showExecutions)
            assertEquals(0, fixture.backend.preparations)
        }
    }

    @Test
    fun capturedRefAliasesSurviveNestedChildSessionsWithoutRecapture() {
        val fixture = Fixture()
        fixture.session().use { session ->
            val arena = session.arena
            val metadata = arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
            val destination = arena.ref(metadata, fixture.types.int, NodeLocation(arena.freshSymbol(), writable = true))
            session.registerBinding(destination)
            val result = fixture.expander(session).expandRoot(
                referenceBinding(fixture, session, fixture.macro("show"), destination)
            )
            result.validate()
            assertEquals(1, captureCount(result.code))
            assertEquals(1, MniMacroExpansionEntries.showExecutions)
            assertEquals(0, fixture.backend.preparations)
        }
    }

    @Test
    fun helperBodiesAreExpandedBeforeFinalValidation() {
        val fixture = Fixture()
        fixture.session().use { session ->
            val result = fixture.expander(session).expandRoot(
                emptyBinding(fixture, session, fixture.macro("withHelper"))
            )
            val helper = session.stagedHelpers().single()
            assertNotNull(helper.finalCode)
            session.finalizeHelpers()
            result.validate()
        }
    }

    @Test
    fun hostTypeAndBudgetFailuresDiscardEverything() {
        val fixture = Fixture()
        fixture.session().use { session ->
            val failure = assertFailsWith<ExpansionFailure> {
                fixture.expander(session).expandRoot(emptyBinding(fixture, session, fixture.macro("explode")))
            }
            assertTrue(failure.message!!.contains("host exploded"))
            assertFalse(session.transaction.isOpen)
            assertEquals(0, session.budget.depth)
            assertFailsWith<ExpansionFailure> { session.context() }
            assertEquals(0, fixture.backend.preparations)
            assertEquals(0, fixture.backend.publications)
        }

        val wrong = Fixture()
        wrong.session().use { session ->
            val failure = assertFailsWith<ExpansionFailure> {
                wrong.expander(session).expandRoot(emptyBinding(wrong, session, wrong.macro("wrongType")))
            }
            assertTrue(failure.message!!.contains("declared int but returned bool"))
            assertFalse(session.transaction.isOpen)
        }

        val limited = Fixture()
        limited.session(ExpansionBudget(maxNodes = 4)).use { session ->
            val arena = session.arena
            val metadata = arena.metadata(arena.rootScope, Origin(SPAN, SPAN))
            val literal = arena.expr(metadata, limited.types.int, ExprNode.Literal(ConstantValue.Integral(1)))
            val failure = assertFailsWith<ExpansionFailure> {
                limited.expander(session).expandRoot(
                    valueBinding(limited, session, limited.macro("identityInt"), literal)
                )
            }
            assertTrue(failure.message!!.contains("generated nodes"))
            assertFalse(session.transaction.isOpen)
            assertEquals(0, session.budget.depth)
        }

        val recursive = Fixture()
        recursive.session(ExpansionBudget(maxDepth = 3, maxNodes = 4_096)).use { session ->
            val failure = assertFailsWith<ExpansionFailure> {
                recursive.expander(session).expandRoot(
                    emptyBinding(recursive, session, recursive.macro("recur"))
                )
            }
            assertTrue(failure.message!!.contains("exceeds depth 3"))
            assertFalse(session.transaction.isOpen)
            assertEquals(0, session.budget.depth)
            assertTrue(session.budget.usedNodes > 0)
        }
    }

    @Test
    fun defaultBudgetMatchesTheDocumentedLimits() {
        val budget = ExpansionBudget()
        assertEquals(64, budget.maxDepth)
        assertEquals(100_000, budget.maxNodes)
    }
}
