package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.CodeArena
import top.mcfpp.mni.expand.DefaultQuote
import java.util.function.Consumer
import java.util.function.Function
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniBuilderTest {
    private class Fixture {
        val int = ImmutableTypeRef<IntT>(TypeKey(1, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(1, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(1, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(1, 4), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(1, 5), "void", kind = TypeKind.VOID)
        val types = object : TypeService {
            override fun intType(): TypeRef<IntT> = int
            override fun boolType(): TypeRef<BoolT> = bool
            override fun floatType(): TypeRef<FloatT> = float
            override fun stringType(): TypeRef<StringT> = string
            override fun voidType(): TypeRef<VoidT> = void
            override fun lookup(name: String): TypeRef<*> = error("Not needed by builder tests")
            override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("Not needed by builder tests")
        }
        val arena = CodeArena(SessionId(1), ScopeId(SessionId(1), 0)) { _, _ -> }
        val origin = Origin(SourceSpan.unknown())
        val meta = arena.metadata(arena.rootScope, origin)
        val quote = DefaultQuote(arena, types, origin)
        fun storage(): ImmutableRef<IntT> = arena.ref(meta, int, NodeLocation(arena.freshSymbol()))
        fun <R> function(
            name: String,
            result: TypeRef<R>,
            kind: FunctionKind = FunctionKind.ORDINARY,
            parameters: List<ParameterView> = emptyList()
        ): ImmutableFunctionRef<R> = ImmutableFunctionRef(FunctionKey(1, name), name, FunctionSignature.of(parameters, result), kind)
        fun callback(result: TypeRef<*>, parameters: List<ParameterView> = emptyList()): ImmutableInlineFn<Any> = arena.inline(meta, FunctionSignature.of(parameters, result), CallbackId(1, "callback"), InlineTarget.Declared(FunctionKey(1, "callback")))
    }

    /** A small independent runtime oracle catches misplaced loop condition statements. */
    private class Machine(val storage: MutableMap<SymbolId, Any> = linkedMapOf()) {
        private class Yielded(val region: RegionId, val value: Any) : RuntimeException()
        private var iterations = 0
        fun expression(code: Expr<*>): Any = when (val node = (code as ImmutableExpr<*>).node) {
            is ExprNode.Literal -> when (val value = node.value) {
                is ConstantValue.Integral -> value.value
                is ConstantValue.Bool -> value.value
                is ConstantValue.Text -> value.value
                else -> error("Unsupported test literal")
            }
            is ExprNode.Read -> storage.getValue(node.location.root)
            is ExprNode.Binary -> {
                val left = expression(node.left)
                val right = expression(node.right)
                when (node.operator) {
                    BinaryOperator.ADD -> (left as Long) + (right as Long)
                    BinaryOperator.SUBTRACT -> (left as Long) - (right as Long)
                    BinaryOperator.LT -> (left as Long) < (right as Long)
                    BinaryOperator.EQ -> left == right
                    else -> error("Unsupported test operator")
                }
            }
            is ExprNode.ExprBlock -> try {
                block(node.block)
                error("Expression region exited without a result")
            } catch (yield: Yielded) {
                if (yield.region != node.region) throw yield
                yield.value
            }
            else -> error("Unsupported test expression")
        }
        fun block(code: Block) {
            code.statements().forEach { statement ->
                when (val node = (statement as ImmutableStmt).node) {
                    is StmtNode.LocalDecl -> storage[(node.local as ImmutableLocal<*>).symbol] = expression(node.initialValue)
                    is StmtNode.Assign -> {
                        val location = when (val destination = node.destination) {
                            is ImmutableRef<*> -> destination.location
                            is ImmutableLocal<*> -> destination.location
                            is ImmutableMinecraftScoreRef -> error("Unsupported test reference")
                        }
                        storage[location.root] = expression(node.expression)
                    }
                    is StmtNode.Eval -> expression(node.expression)
                    is StmtNode.ScopeBlock -> block(node.block)
                    is StmtNode.If -> if (expression(node.condition) as Boolean) block(node.thenBlock) else node.elseBlock?.let(::block)
                    is StmtNode.While -> while (expression(node.condition) as Boolean) {
                        check(++iterations <= 20) { "Loop condition did not observe updated storage" }
                        block(node.body)
                    }
                    is StmtNode.Yield -> throw Yielded(node.region, expression(node.value))
                    else -> error("Unsupported test statement")
                }
            }
        }
    }

    @Test
    fun hostConditionRunsOnceAndItsGeneratedLocalsRunBeforeEveryIteration() {
        val fixture = Fixture()
        val output = fixture.storage()
        var hostConditions = 0
        var hostBodies = 0
        val block = fixture.quote.block(
            Consumer { builder ->
                builder.whileLoop(
                    Function { condition ->
                        hostConditions++
                        val current = condition.local(fixture.int, output.load())
                        condition.lt(current.load(), condition.intLiteral(3))
                    },
                    Consumer { body ->
                        hostBodies++
                        body.assign(output, body.add(output.load(), body.intLiteral(1)))
                    }
                )
            }
        )
        assertEquals(1, hostConditions)
        assertEquals(1, hostBodies)
        val machine = Machine(linkedMapOf(output.location.root to 0L))
        machine.block(block)
        assertEquals(3L, machine.storage[output.location.root])
        assertTrue(
            ((block.statements().single() as ImmutableStmt).node as StmtNode.While).condition.let {
                (it as ImmutableExpr<*>).node is ExprNode.ExprBlock
            }
        )
    }

    @Test
    fun kotlinOperatorsAndPropertiesGenerateRuntimeLoopAndPreserveScopeLifetime() {
        val fixture = Fixture()
        lateinit var retained: CodeScope
        lateinit var counter: Local<IntT>
        val expression = fixture.quote.kotlin().expression(fixture.int) {
            retained = this
            counter = local("i", literal(0))
            val total = local("total", literal(0))
            whileLoop({ counter.value lt literal(3) }) {
                total += counter.value
                counter += 1
            }
            ifThen(total.value eq literal(3)) {
                total.value = total.value + 4
            }
            yield(total.value)
        }
        assertEquals(7L, Machine().expression(expression))
        assertFailsWith<CodeModelException> { with(retained) { counter += 1 } }
    }

    @Test
    fun kotlinDslAndJavaConsumersExecuteTheSameConstruction() {
        fun build(dsl: Boolean): Pair<Fixture, Pair<Ref<IntT>, Block>> {
            val fixture = Fixture()
            val output = fixture.storage()
            val block = if (dsl) fixture.quote.kotlin().block {
                val value = local(fixture.int, intLiteral(2))
                whileLoop({ lt(value.load(), intLiteral(3)) }, { assign(value, add(value.load(), intLiteral(1))) })
                ifElse(eq(value.load(), intLiteral(3)), {
                    assign(output, add(value.load(), intLiteral(4)))
                }, { assign(output, intLiteral(0)) })
            } else fixture.quote.block(
                Consumer { b ->
                    val value = b.local(fixture.int, b.intLiteral(2))
                    b.whileLoop(
                        Function { condition -> condition.lt(value.load(), condition.intLiteral(3)) },
                        Consumer { body -> body.assign(value, body.add(value.load(), body.intLiteral(1))) }
                    )
                    b.ifElse(
                        b.eq(value.load(), b.intLiteral(3)),
                        Consumer { yes ->
                            yes.assign(output, yes.add(value.load(), yes.intLiteral(4)))
                        },
                        Consumer { no -> no.assign(output, no.intLiteral(0)) }
                    )
                }
            )
            return fixture to (output to block)
        }
        listOf(false, true).forEach { dsl ->
            val (_, pair) = build(dsl)
            val (output, block) = pair
            val machine = Machine()
            machine.block(block)
            val root = (output as ImmutableRef<*>).location.root
            assertEquals(7L, machine.storage[root])
            assertEquals(3, block.statements().size)
        }
    }

    @Test
    fun closedParentAndSiblingBuilderCapabilitiesCannotEscape() {
        val fixture = Fixture()
        lateinit var retained: CodeBuilder
        lateinit var hidden: Local<IntT>
        fixture.quote.block(
            Consumer { outer ->
                retained = outer
                outer.ifThen(
                    outer.boolLiteral(true),
                    Consumer { child ->
                        assertFailsWith<CodeModelException> { outer.intLiteral(1) }
                        hidden = child.local(fixture.int, child.intLiteral(1))
                    }
                )
                assertFailsWith<CodeModelException> { outer.evaluate(hidden.load()) }
                outer.ifThen(
                    outer.boolLiteral(true),
                    Consumer { sibling ->
                        assertFailsWith<CodeModelException> { sibling.evaluate(hidden.load()) }
                    }
                )
            }
        )
        assertFailsWith<CodeModelException> { retained.intLiteral(1) }
        assertFailsWith<CodeModelException> { retained.minecraft() }
    }

    @Test
    fun repeatedAppendFreshensInternalStorageAndPreservesExternalAliases() {
        val fixture = Fixture()
        val output = fixture.storage()
        lateinit var original: Local<IntT>
        val fragment = fixture.quote.block(
            Consumer { builder ->
                original = builder.local("delta", fixture.int, builder.intLiteral(1))
                builder.assign(output, builder.add(output.load(), original.load()))
            }
        )
        val block = fixture.quote.block(
            Consumer { builder ->
                builder.append(fragment)
                builder.append(fragment)
            }
        )
        val imported = block.statements().map { ((it as ImmutableStmt).node as StmtNode.ScopeBlock).block }
        val symbols = imported.map { ((it.statements()[0] as ImmutableStmt).node as StmtNode.LocalDecl).local as ImmutableLocal<*> }
        assertNotEquals((original as ImmutableLocal<*>).symbol, symbols[0].symbol)
        assertNotEquals(symbols[0].symbol, symbols[1].symbol)
        imported.forEach { assertSame(output, ((it.statements()[1] as ImmutableStmt).node as StmtNode.Assign).destination) }
        val machine = Machine(linkedMapOf(output.location.root to 0L))
        machine.block(block)
        assertEquals(2L, machine.storage[output.location.root])
    }

    @Test
    fun expressionResultsFollowBranchesAndNestedQuotesCaptureVisibleLocals() {
        val fixture = Fixture()
        val result = fixture.quote.expression(
            fixture.int,
            Consumer { builder ->
                val captured = builder.local(fixture.int, builder.intLiteral(7))
                val nested = fixture.quote.expression(fixture.int, Consumer { inner -> inner.yield(captured.load()) })
                builder.ifElse(builder.boolLiteral(true), Consumer { yes -> yes.yield(nested) }, Consumer { no -> no.yield(no.intLiteral(0)) })
            }
        )
        assertEquals(7L, Machine().expression(result))
        assertFailsWith<CodeModelException> {
            fixture.quote.expression(
                fixture.int,
                Consumer { b ->
                    b.ifThen(b.boolLiteral(false), Consumer { branch -> branch.yield(branch.intLiteral(1)) })
                }
            )
        }
        assertFailsWith<CodeModelException> {
            fixture.quote.expression(
                fixture.int,
                Consumer { b ->
                    val value = b.intLiteral(1)
                    b.yield(value)
                    b.yield(value)
                }
            )
        }
        assertFailsWith<CodeModelException> {
            fixture.quote.expression(
                fixture.int,
                Consumer { b ->
                    b.whileLoop(
                        Function { condition -> condition.boolLiteral(false) },
                        Consumer { body -> body.yield(body.intLiteral(1)) }
                    )
                }
            )
        }
        assertFailsWith<CodeModelException> { fixture.quote.block(Consumer { b -> b.yield(b.intLiteral(1)) }) }
        assertFailsWith<CodeModelException> { fixture.quote.expression(fixture.void, Consumer { }) }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun actualTypeDescriptorsRejectGenericLiesAndForeignFieldOwners() {
        val fixture = Fixture()
        fixture.quote.block(
            Consumer { b ->
                val integer = b.intLiteral(1)
                val boolean = b.boolLiteral(true)
                val lyingInt = boolean as Expr<IntT>
                assertFailsWith<CodeModelException> { b.add(lyingInt, lyingInt) }
                assertFailsWith<CodeModelException> { b.lt(lyingInt, lyingInt) }
                assertFailsWith<CodeModelException> { b.ifThen(integer as Expr<BoolT>, Consumer { }) }
                assertFailsWith<CodeModelException> { b.local(fixture.bool as TypeRef<IntT>, integer) }
                assertFailsWith<CodeModelException> { b.assign(fixture.storage(), lyingInt) }
                assertFailsWith<CodeModelException> { b.callVoid(fixture.function("notVoid", fixture.int) as FunctionRef<VoidT>, emptyList()) }
                val stale = ImmutableTypeRef<IntT>(TypeKey(1, 1, 1), "int", kind = TypeKind.INT)
                val staleExpression = fixture.arena.expr((integer as OwnedCode).metadata, stale, ExprNode.Literal(ConstantValue.Integral(1)))
                assertFailsWith<CodeModelException> { b.add(integer, staleExpression) }
            }
        )
    }

    @Test
    fun callsRespectModesVoidTargetsAndExplicitEvaluation() {
        val fixture = Fixture()
        val storage = fixture.storage()
        val ordinary = fixture.function("ordinary", fixture.int)
        val void = fixture.function("void", fixture.void)
        val helper = fixture.function(
            "helper",
            fixture.int,
            FunctionKind.HELPER,
            listOf(ParameterView.of("destination", fixture.int, ParamMode.REF))
        )
        val macro = fixture.function(
            "macro",
            fixture.int,
            FunctionKind.MACRO,
            listOf(ParameterView.of("limit", fixture.int, ParamMode.CONST))
        )
        val block = fixture.quote.block(
            Consumer { b ->
                b.call(ordinary, emptyList())
                b.evaluate(b.call(helper, listOf(CallArgument.ref(storage))))
                b.callVoid(void, emptyList())
                assertFailsWith<CodeModelException> { b.call(helper, listOf(CallArgument.value(b.intLiteral(1)))) }
                assertFailsWith<CodeModelException> { b.call(ordinary, listOf(CallArgument.ref(storage))) }
                assertFailsWith<CodeModelException> { b.call(void, emptyList()) }
                val unsupported = fixture.function("ordinaryRef", fixture.int, FunctionKind.ORDINARY, helper.parameters())
                assertFailsWith<CodeModelException> { b.call(unsupported, listOf(CallArgument.ref(storage))) }
                val readOnly = fixture.arena.ref(fixture.meta, fixture.int, NodeLocation(fixture.arena.freshSymbol(), writable = false))
                assertFailsWith<CodeModelException> { b.call(helper, listOf(CallArgument.ref(readOnly))) }
                val literal = b.intLiteral(3)
                val constant = fixture.arena.constant(literal, ConstantValue.Integral(3))
                assertTrue((b.call(macro, listOf(CallArgument.constant(constant))) as ImmutableExpr<*>).node is ExprNode.MacroCall)
                assertFailsWith<CodeModelException> { b.call(macro, listOf(CallArgument.value(literal))) }
            }
        )
        assertEquals(2, block.statements().size)
        val call = (((block.statements()[0] as ImmutableStmt).node as StmtNode.Eval).expression as ImmutableExpr<*>).node as ExprNode.Call
        assertSame(storage, (call.arguments.single().payload as ArgumentPayload.Reference).reference)
    }

    @Test
    fun inlineCallsUseTheirActualSignatureAndFieldsRetainLocationCapabilities() {
        val fixture = Fixture()
        val callback = fixture.callback(fixture.int, listOf(ParameterView.of("input", fixture.int, ParamMode.VALUE)))
        val void = fixture.callback(fixture.void)
        val owner = TypeKey(1, 10)
        val field = ImmutableFieldRef(FieldKey(owner, 1), "value", fixture.int, true, SourceSpan.unknown())
        val constant = ImmutableFieldRef(FieldKey(owner, 2), "constant", fixture.int, false, SourceSpan.unknown())
        val receiverType = ImmutableTypeRef<Any>(owner, "Record", fields = listOf(field, constant))
        val receiver = fixture.arena.ref(fixture.meta, receiverType, NodeLocation(fixture.arena.freshSymbol()))
        val foreign = ImmutableFieldRef(FieldKey(TypeKey(1, 11), 1), "value", fixture.int, true, SourceSpan.unknown())
        val block = fixture.quote.block(
            Consumer { b ->
                val value = b.inline<IntT>(callback, listOf(InlineArgument.value(b.intLiteral(1))))
                b.evaluate(value)
                b.inlineVoid(void, emptyList())
                val falseMarker = b.inline<BoolT>(callback, listOf(InlineArgument.value(b.intLiteral(1))))
                assertFailsWith<CodeModelException> { b.ifThen(falseMarker, Consumer { }) }
                assertFailsWith<CodeModelException> { b.inline<IntT>(callback, listOf(InlineArgument.value(b.boolLiteral(true)))) }
                assertFailsWith<CodeModelException> { b.inlineVoid(callback, listOf(InlineArgument.value(b.intLiteral(1)))) }
                assertFailsWith<CodeModelException> { b.inline<IntT>(void, emptyList()) }
                assertFailsWith<CodeModelException> { b.field(receiver.load(), foreign) }
                val selected = b.fieldRef(receiver, field) as ImmutableRef<*>
                assertEquals(receiver.location.root, selected.location.root)
                assertEquals(listOf(LocationStep.Field(field)), selected.location.steps)
                assertTrue((b.field(receiver.load(), field) as ImmutableExpr<*>).node is ExprNode.Field)
                val readOnly = b.fieldRef(receiver, constant)
                assertFailsWith<CodeModelException> { b.assign(readOnly, b.intLiteral(1)) }
            }
        )
        assertEquals(2, block.statements().size)
    }
}
