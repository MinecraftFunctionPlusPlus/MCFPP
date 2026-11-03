package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import java.util.function.Consumer
import java.util.function.Function
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MniCodeLoweringPlanTest {
    private class Fixture(id: Long = 1) {
        val arena = CodeArena(SessionId(id), ScopeId(SessionId(id), 0)) { _, _ -> }
        val site = SourceSpan("lower.mcfpp", 10, 20, 2, 1, 2, 11)
        val origin = Origin(site, expansionFrames = listOf(ExpansionOriginFrame("test:macro", site)))
        val meta = arena.metadata(arena.rootScope, origin)
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
            override fun lookup(name: String): TypeRef<*> = error("No type lookup during lowering")
            override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("No target lookup during lowering")
        }
        val quote = DefaultQuote(arena, types, origin)
        val target = LoweringTarget(FunctionKey(9, "test:target"))
        fun integer(value: Long) = ImmutableExpr(meta, int, ExprNode.Literal(ConstantValue.Integral(value)))
        fun boolean(value: Boolean) = ImmutableExpr(meta, bool, ExprNode.Literal(ConstantValue.Bool(value)))
        fun <T> ref(type: TypeRef<T>, location: NodeLocation = NodeLocation(arena.freshSymbol())) = arena.ref(meta, type, location)
        fun <T> function(name: String, result: TypeRef<T>, parameters: List<ParameterView> = emptyList(), kind: FunctionKind = FunctionKind.ORDINARY) = ImmutableFunctionRef(FunctionKey(4, name), name, FunctionSignature.of(parameters, result), kind)
        fun <T> call(function: FunctionRef<T>, arguments: List<CallArgument> = emptyList()) = ImmutableExpr(meta, function.returnType(), ExprNode.Call(function, arguments))
        fun stmt(node: StmtNode, scope: ScopeId = arena.rootScope) = ImmutableStmt(arena.metadata(scope, origin), node)
        fun block(vararg nodes: StmtNode, scope: ScopeId = arena.rootScope) = ImmutableBlock(arena.metadata(scope, origin), nodes.map { stmt(it, scope) })
        fun proof(code: Code, bindings: List<Ref<*>> = emptyList(), functions: List<FunctionRef<*>> = emptyList(), result: ValidationRegion? = null): ValidatedCode = CodeValidator().validate(code, ValidationEnvironment(arena, bindings, functions, resultRegion = result), ValidationPhase.FINAL) as ValidatedCode
        fun lower(code: Code, bindings: List<Ref<*>> = emptyList(), functions: List<FunctionRef<*>> = emptyList(), result: ValidationRegion? = null) = CodeLowerer().lower(proof(code, bindings, functions, result), target)
    }

    internal data class Address(val symbol: SymbolId, val path: List<Any> = emptyList(), val context: ExecutionContextId? = null)

    /** Executes only the detached backend protocol; it never walks Code nodes or calls a visitor. */
    internal class Machine(val plan: LoweringPlan) {
        val storage = linkedMapOf<Address, Any?>()
        val calls = arrayListOf<String>()
        val readonlyCalls = arrayListOf<List<Any?>>()
        val captured = arrayListOf<Address>()
        var invoke: (PlanFunction, List<Any?>) -> Any? = { _, _ -> null }
        var invokeWithReadonly: ((PlanFunction, List<Any?>, List<Any?>) -> Any?)? = null
        private val values = hashMapOf<PlanTemp, Any?>()
        private val locations = hashMapOf<PlanLocationId, Address>()
        private val localAddresses = hashMapOf<SymbolId, Address>()
        private fun value(temp: PlanTemp): Any? {
            check(temp in values) { "Read of an uninitialized result register $temp" }
            return values[temp]
        }
        private fun address(location: PlanLocation): Address {
            val base = location.capturedBase?.let { locations.getValue(it) }
            val suffix = location.steps.map {
                when (it) {
                    is PlanLocationStep.Field -> it.field.key
                    is PlanLocationStep.Index -> it.value
                    is PlanLocationStep.Key -> it.value
                    is PlanLocationStep.CapturedIndex -> (storage[localAddresses[it.symbol.binding] ?: Address(it.symbol.binding)] as Number).toInt()
                    is PlanLocationStep.CapturedKey -> storage[localAddresses[it.symbol.binding] ?: Address(it.symbol.binding)] as String
                }
            }
            return Address(
                base?.symbol ?: location.root.binding,
                (base?.path ?: emptyList()) + suffix,
                if (base != null) base.context else location.executionContext
            )
        }
        private fun constant(value: PlanConstant): Any? = when (value) {
            is PlanConstant.Integral -> value.value
            is PlanConstant.Bool -> value.value
            is PlanConstant.Text -> value.value
            is PlanConstant.FloatBits -> Float.fromBits(value.bits).toDouble()
            is PlanConstant.DoubleBits -> Double.fromBits(value.bits)
            is PlanConstant.Nbt -> value.snbt
            is PlanConstant.TypeValue -> value.type
            PlanConstant.NullValue -> null
            is PlanConstant.Sequence -> value.elements.map(::constant)
            is PlanConstant.Record -> value.fields.mapValues { constant(it.value) }
        }
        fun run(maxBlocks: Int = 1000): Pair<PlanExitKind, Any?> {
            val blocks = plan.blocks.associateBy { it.id }
            var next = plan.entry
            repeat(maxBlocks) {
                val block = blocks.getValue(next)
                block.operations.forEach { operation ->
                    when (operation) {
                        is PlanOperation.Literal -> values[operation.result] = constant(operation.value)
                        is PlanOperation.CaptureLocation -> {
                            val address = address(operation.location)
                            locations[operation.result] = address
                            captured += address
                        }
                        is PlanOperation.Read -> values[operation.result] = storage[locations.getValue(operation.location)]
                        is PlanOperation.Write -> storage[locations.getValue(operation.location)] = value(operation.value)
                        is PlanOperation.DeclareLocal -> {
                            val address = address(operation.location)
                            localAddresses[operation.location.root.binding] = address
                            storage[address] = value(operation.initialValue)
                        }
                        is PlanOperation.Move -> values[operation.result] = value(operation.value)
                        is PlanOperation.Unary -> values[operation.result] = when (operation.operator) {
                            UnaryOperator.NEGATE -> -(value(operation.operand) as Long)
                            UnaryOperator.NOT -> !(value(operation.operand) as Boolean)
                        }
                        is PlanOperation.Binary -> {
                            val left = value(operation.left)
                            val right = value(operation.right)
                            values[operation.result] = when (operation.operator) {
                                BinaryOperator.ADD -> (left as Long) + (right as Long)
                                BinaryOperator.SUBTRACT -> (left as Long) - (right as Long)
                                BinaryOperator.MULTIPLY -> (left as Long) * (right as Long)
                                BinaryOperator.DIVIDE -> (left as Long) / (right as Long)
                                BinaryOperator.MODULO -> (left as Long) % (right as Long)
                                BinaryOperator.EQ -> left == right
                                BinaryOperator.NE -> left != right
                                BinaryOperator.LT -> (left as Long) < (right as Long)
                                BinaryOperator.LE -> (left as Long) <= (right as Long)
                                BinaryOperator.GT -> (left as Long) > (right as Long)
                                BinaryOperator.GE -> (left as Long) >= (right as Long)
                                BinaryOperator.AND, BinaryOperator.OR -> error("Logical operators must be control flow")
                            }
                        }
                        is PlanOperation.Convert -> values[operation.result] = when (operation.result.type.kind) {
                            TypeKind.INT -> (value(operation.operand) as Number).toLong()
                            TypeKind.FLOAT -> (value(operation.operand) as Number).toDouble()
                            else -> value(operation.operand)
                        }
                        is PlanOperation.Field -> values[operation.result] = (value(operation.receiver) as Map<*, *>)[operation.field.key]
                        is PlanOperation.Index -> values[operation.result] = (value(operation.receiver) as List<*>)[(value(operation.index) as Number).toInt()]
                        is PlanOperation.Call -> {
                            calls += operation.function.name
                            val readonly = operation.readonlyArguments.map { constant(it.value) }
                            readonlyCalls += readonly
                            val arguments = operation.arguments.map { argument ->
                                when (argument) {
                                    is PlanArgument.Value -> value(argument.value)
                                    is PlanArgument.Reference -> locations.getValue(argument.location)
                                }
                            }
                            val handler = invokeWithReadonly
                            val result = if (handler == null) invoke(operation.function, arguments) else handler(operation.function, readonly, arguments)
                            operation.result?.let { values[it] = result }
                        }
                        else -> error("Unsupported plan operation in test backend: $operation")
                    }
                }
                when (val exit = block.terminator) {
                    is PlanTerminator.Jump -> next = exit.target
                    is PlanTerminator.Branch -> next = if (value(exit.condition) as Boolean) exit.ifTrue else exit.ifFalse
                    is PlanTerminator.Exit -> return exit.kind to exit.value?.let(::value)
                    is PlanTerminator.Unreachable -> error(exit.reason)
                    else -> error("Unsupported plan terminator in test backend: $exit")
                }
            }
            error("Execution block limit exceeded")
        }
    }

    @Test
    fun ordinaryAndLegacyCallsEvaluateLeftToRightOnceAndPlansSurviveArenaClosure() {
        val f = Fixture()
        val first = f.function("first", f.int)
        val second = f.function("native", f.int, kind = FunctionKind.NATIVE)
        val sink = f.function("sink", f.int, listOf(ParameterView.of("a", f.int, ParamMode.VALUE), ParameterView.of("b", f.int, ParamMode.VALUE)))
        val plan = f.lower(f.call(sink, listOf(CallArgument.value(f.call(first)), CallArgument.value(f.call(second)))), functions = listOf(first, second, sink))
        f.arena.close()
        val machine = Machine(plan)
        machine.invoke = { target, arguments ->
            when (target.name) {
                "first" -> 3L
                "native" -> 5L
                else -> (arguments[0] as Long) * 10 + (arguments[1] as Long)
            }
        }
        assertEquals(PlanExitKind.EXPRESSION to 35L, machine.run())
        assertEquals(listOf("first", "native", "sink"), machine.calls)
        assertEquals(listOf(PlanCallKind.ORDINARY, PlanCallKind.LEGACY_NATIVE, PlanCallKind.ORDINARY), plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.Call>().map { it.function.kind })
        assertEquals(f.int.key, plan.resultType!!.key)
        assertEquals(f.site, plan.blocks.first().operations.first().site.callSite)
        assertEquals("test:macro", plan.blocks.first().operations.first().site.frames.single().macroName)
    }

    @Test
    fun assignmentFreezesTheDestinationBeforeAnEffectfulRightHandSide() {
        val f = Fixture()
        val index = f.ref(f.int)
        val root = f.arena.freshSymbol()
        val element = f.ref(f.int, NodeLocation(root, listOf(LocationStep.CapturedIndex(index.location.root))))
        val change = f.function("changeIndex", f.int, kind = FunctionKind.NATIVE)
        val plan = f.lower(f.block(StmtNode.Assign(element, f.call(change))), listOf(index, element), listOf(change))
        val machine = Machine(plan)
        machine.storage[Address(index.location.root)] = 0L
        machine.storage[Address(root, listOf(0))] = 4L
        machine.storage[Address(root, listOf(1))] = 5L
        machine.invoke = { _, _ ->
            machine.storage[Address(index.location.root)] = 1L
            7L
        }
        assertEquals(PlanExitKind.BLOCK to null, machine.run())
        assertEquals(7L, machine.storage[Address(root, listOf(0))])
        assertEquals(5L, machine.storage[Address(root, listOf(1))])
    }

    @Test
    fun helperReferencesFreezeAtTheirArgumentPositionsAndPreserveAliases() {
        val f = Fixture()
        val index = f.ref(f.int)
        val root = f.arena.freshSymbol()
        val element = f.ref(f.int, NodeLocation(root, listOf(LocationStep.CapturedIndex(index.location.root))))
        val effect = f.function("advance", f.int, kind = FunctionKind.NATIVE)
        val helper = f.function(
            "mutate",
            f.void,
            listOf(
                ParameterView.of("a", f.int, ParamMode.VALUE),
                ParameterView.of("b", f.int, ParamMode.REF),
                ParameterView.of("c", f.int, ParamMode.VALUE),
                ParameterView.of("d", f.int, ParamMode.REF),
                ParameterView.of("e", f.int, ParamMode.REF)
            ),
            FunctionKind.HELPER
        )
        val call = f.call(helper, listOf(CallArgument.value(f.call(effect)), CallArgument.ref(element), CallArgument.value(f.call(effect)), CallArgument.ref(element), CallArgument.ref(element)))
        val plan = f.lower(f.block(StmtNode.Eval(call)), listOf(index, element), listOf(effect, helper))
        val machine = Machine(plan)
        var advances = 0L
        machine.storage[Address(index.location.root)] = 0L
        machine.invoke = { function, args ->
            if (function.name == "advance") {
                advances++
                machine.storage[Address(index.location.root)] = advances
                advances
            } else {
                assertEquals(Address(root, listOf(1)), args[1])
                assertEquals(Address(root, listOf(2)), args[3])
                assertEquals(args[3], args[4])
                machine.storage[args[3] as Address] = 8L
                machine.storage[args[4] as Address] = (machine.storage[args[4] as Address] as Long) + 1
                null
            }
        }
        machine.run()
        assertEquals(2L, advances)
        assertEquals(9L, machine.storage[Address(root, listOf(2))])
        assertEquals(listOf("advance", "advance", "mutate"), machine.calls)
        assertNull(plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.Call>().last().result)
    }

    @Test
    fun shortCircuitBranchesExecuteTheRightOperandOnlyWhenRequired() {
        for ((operator, left, expectedCalls) in listOf(
            Triple(BinaryOperator.AND, false, 0),
            Triple(BinaryOperator.AND, true, 1),
            Triple(BinaryOperator.OR, true, 0),
            Triple(BinaryOperator.OR, false, 1)
        )) {
            val f = Fixture()
            val effect = f.function("rhs", f.bool)
            val expr = ImmutableExpr(f.meta, f.bool, ExprNode.Binary(operator, f.boolean(left), f.call(effect)))
            val plan = f.lower(expr, functions = listOf(effect))
            val machine = Machine(plan)
            machine.invoke = { _, _ -> true }
            assertEquals(if (operator == BinaryOperator.AND) left else true, machine.run().second)
            assertEquals(expectedCalls, machine.calls.size)
            assertTrue(plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.Binary>().isEmpty())
        }
    }

    @Test
    fun loopConditionsAreBuiltOnceAndExecutedAgainOnEveryBackEdge() {
        val f = Fixture()
        val output = f.ref(f.int)
        val probe = f.function("probe", f.bool, listOf(ParameterView.of("value", f.int, ParamMode.VALUE)))
        var constructions = 0
        val code = f.quote.block(
            Consumer { b ->
                val counter = b.local("counter", f.int, b.intLiteral(0))
                b.whileLoop(
                    Function { condition ->
                        constructions++
                        condition.call(probe, listOf(CallArgument.value(counter.load())))
                    },
                    Consumer { body -> body.assign(counter, body.add(counter.load(), body.intLiteral(1))) }
                )
                b.assign(output, counter.load())
            }
        )
        val plan = f.lower(code, listOf(output), listOf(probe))
        val machine = Machine(plan)
        machine.invoke = { _, args -> (args.single() as Long) < 3 }
        machine.run()
        assertEquals(1, constructions)
        assertEquals(4, machine.calls.size)
        assertEquals(3L, machine.storage[Address(output.location.root)])
    }

    @Test
    fun breakAndContinueUseTheirLoopTargetsAndDoNotExecuteTheRemainder() {
        val f = Fixture()
        val i = f.ref(f.int)
        val sum = f.ref(f.int)
        val loop = f.arena.freshRegion()
        val bodyScope = f.arena.childScope(f.arena.rootScope)
        val continueScope = f.arena.childScope(bodyScope)
        val breakScope = f.arena.childScope(bodyScope)
        fun comparison(operator: BinaryOperator, number: Long) = ImmutableExpr(f.meta, f.bool, ExprNode.Binary(operator, i.load(), f.integer(number)))
        val body = f.block(
            StmtNode.Assign(i, ImmutableExpr(f.meta, f.int, ExprNode.Binary(BinaryOperator.ADD, i.load(), f.integer(1)))),
            StmtNode.If(comparison(BinaryOperator.EQ, 2), f.block(StmtNode.Continue(loop), scope = continueScope), null),
            StmtNode.If(comparison(BinaryOperator.EQ, 4), f.block(StmtNode.Break(loop), scope = breakScope), null),
            StmtNode.Assign(sum, ImmutableExpr(f.meta, f.int, ExprNode.Binary(BinaryOperator.ADD, sum.load(), i.load()))),
            scope = bodyScope
        )
        val plan = f.lower(f.block(StmtNode.While(comparison(BinaryOperator.LT, 5), body, loop)), listOf(i, sum))
        val machine = Machine(plan)
        machine.storage[Address(i.location.root)] = 0L
        machine.storage[Address(sum.location.root)] = 0L
        machine.run()
        assertEquals(4L, machine.storage[Address(i.location.root)])
        assertEquals(4L, machine.storage[Address(sum.location.root)])
    }

    @Test
    fun expressionRegionsJoinOnlyTheirYieldValuesAndResumeTheCallerInOrder() {
        for (choice in listOf(false, true)) {
            val f = Fixture()
            val flag = f.ref(f.bool)
            val first = f.function("chosen", f.int)
            val second = f.function("after", f.int)
            val inner = f.quote.expression(
                f.int,
                Consumer { b ->
                    b.ifElse(flag.load(), Consumer { yes -> yes.yield(yes.call(first, emptyList())) }, Consumer { no -> no.yield(no.intLiteral(4)) })
                }
            )
            val expr = ImmutableExpr(f.meta, f.int, ExprNode.Binary(BinaryOperator.ADD, inner, f.call(second)))
            val plan = f.lower(expr, listOf(flag), listOf(first, second))
            val machine = Machine(plan)
            machine.storage[Address(flag.location.root)] = choice
            machine.invoke = { function, _ -> if (function.name == "chosen") 8L else 1L }
            assertEquals(if (choice) 9L else 5L, machine.run().second)
            assertEquals(if (choice) listOf("chosen", "after") else listOf("after"), machine.calls)
            assertTrue(plan.scopes.size >= 3)
        }
    }

    @Test
    fun callbackReturnInsideALoopHasAnExplicitRootExitAndSkipsLaterEffects() {
        val f = Fixture()
        val callback = f.arena.freshRegion()
        val loop = f.arena.freshRegion()
        val bodyScope = f.arena.childScope(f.arena.rootScope)
        val never = f.function("never", f.void)
        val body = f.block(StmtNode.CallbackReturn(callback, f.integer(7)), scope = bodyScope)
        val code = f.block(StmtNode.While(f.boolean(true), body, loop), StmtNode.Eval(f.call(never)))
        // A constant infinite/returning loop makes a following statement invalid. Use a dynamic condition
        // so final validation can retain a normal path without changing the runtime early-return case.
        val flag = f.ref(f.bool)
        val valid = f.block(StmtNode.While(flag.load(), body, loop), StmtNode.Eval(f.call(never)), StmtNode.CallbackReturn(callback, f.integer(9)))
        assertFailsWith<CodeModelException> { f.proof(code, functions = listOf(never), result = ValidationRegion(callback, f.int, true)) }
        val plan = f.lower(valid, listOf(flag), listOf(never), ValidationRegion(callback, f.int, true))
        val machine = Machine(plan)
        machine.storage[Address(flag.location.root)] = true
        assertEquals(PlanExitKind.CALLBACK to 7L, machine.run())
        assertTrue(machine.calls.isEmpty())
    }

    @Test
    fun rootResultYieldsAndValueConversionsRetainTheirActualTypes() {
        val f = Fixture()
        val region = f.arena.freshRegion()
        val negative = ImmutableExpr(f.meta, f.int, ExprNode.Unary(UnaryOperator.NEGATE, f.integer(6)))
        val converted = ImmutableExpr(f.meta, f.float, ExprNode.Convert(negative, f.float, ConversionKind.EXPLICIT))
        val plan = f.lower(f.block(StmtNode.Yield(region, converted)), result = ValidationRegion(region, f.float))
        assertEquals(PlanExitKind.RESULT to -6.0, Machine(plan).run())
        assertEquals(f.float.key, plan.resultType!!.key)
        assertEquals(ConversionKind.EXPLICIT, plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.Convert>().single().kind)
    }

    @Test
    fun fieldsAndBoundExecutionContextsRemainDetachedAndIdentifiable() {
        val f = Fixture()
        val ownerKey = TypeKey(1, 20)
        val field = ImmutableFieldRef(FieldKey(ownerKey, 1), "hp", f.int, true, f.site)
        val owner = ImmutableTypeRef<Any>(ownerKey, "PlayerData", fields = listOf(field))
        val context = f.arena.freshExecutionContext()
        val metadata = f.arena.metadata(f.arena.rootScope, f.origin, context)
        val ref = f.arena.ref(metadata, owner, NodeLocation(f.arena.freshSymbol(), executionContextId = context))
        val expr = ImmutableExpr(metadata, f.int, ExprNode.Field(ref.load(), field))
        val plan = CodeLowerer().lower(f.proof(expr, listOf(ref)), LoweringTarget(f.target.function, context))
        f.arena.close()
        val machine = Machine(plan)
        machine.storage[Address(ref.location.root, context = context)] = mapOf(field.key to 23L)
        assertEquals(23L, machine.run().second)
        assertEquals(context, plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.CaptureLocation>().single().location.executionContext)
        assertEquals(field.key, plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.Field>().single().field.key)
    }

    @Test
    fun staleProofsAndWrongTargetContextsFailBeforeAnyPlanCanBePublished() {
        val f = Fixture()
        val proof = f.proof(f.integer(1))
        val failure = assertFailsWith<CodeModelException> {
            CodeLowerer().lower(proof, LoweringTarget(f.target.function, f.arena.freshExecutionContext()))
        }
        assertEquals(f.site, failure.source)
        assertTrue(failure.message!!.contains("execution context"))
        f.arena.close()
        assertFailsWith<CodeModelException> { CodeLowerer().lower(proof, f.target) }
    }

    @Test
    fun unexpandedCallsCannotObtainTheFinalProofRequiredByTheLowerer() {
        val f = Fixture()
        val macro = f.function("unexpanded", f.int, kind = FunctionKind.MACRO)
        val code = ImmutableExpr(f.meta, f.int, ExprNode.MacroCall(macro, emptyList()))
        val env = ValidationEnvironment(f.arena, functions = listOf(macro))
        assertIs<PrevalidatedCode>(CodeValidator().validate(code, env, ValidationPhase.PRE_EXPANSION))
        val failure = assertFailsWith<CodeModelException> { CodeValidator().validate(code, env, ValidationPhase.FINAL) }
        assertEquals(f.site, failure.source)
        assertTrue(failure.message!!.contains("MacroCall remains"))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun publishedPlanCollectionsCannotBeChangedByAConsumer() {
        val f = Fixture()
        val plan = f.lower(f.integer(2))
        assertFailsWith<UnsupportedOperationException> { (plan.blocks as MutableList<PlanBasicBlock>).clear() }
        assertFailsWith<UnsupportedOperationException> { (plan.blocks.single().operations as MutableList<PlanOperation>).clear() }
        assertEquals(2L, Machine(plan).run().second)
    }

    @Test
    fun repeatedAppendsKeepIndependentLocalStorageAndTheOriginalFreeBinding() {
        val f = Fixture()
        val output = f.ref(f.int)
        val reusable = f.quote.block(
            Consumer { b ->
                val local = b.local("scratch", f.int, b.intLiteral(1))
                b.assign(local, b.add(local.load(), b.intLiteral(1)))
                b.assign(output, b.add(output.load(), local.load()))
            }
        )
        val code = f.quote.block(
            Consumer {
                it.append(reusable)
                it.append(reusable)
            }
        )
        val plan = f.lower(code, listOf(output))
        val declarations = plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.DeclareLocal>()
        assertEquals(2, declarations.size)
        assertEquals(2, declarations.map { it.location.root }.distinct().size)
        val machine = Machine(plan)
        machine.storage[Address(output.location.root)] = 0L
        machine.run()
        assertEquals(4L, machine.storage[Address(output.location.root)])
        assertTrue(plan.scopes.all { scope -> scope.parent == null || plan.scopes.any { it.binding == scope.parent } })
    }

    @Test
    fun nonreturningExpressionsHaveNoFabricatedRuntimeResult() {
        val f = Fixture()
        val code = f.quote.expression(
            f.int,
            Consumer { b ->
                b.whileLoop(Function { it.boolLiteral(true) }, Consumer { })
            }
        )
        val plan = f.lower(code)
        val failure = assertFailsWith<IllegalStateException> { Machine(plan).run(maxBlocks = 30) }
        assertEquals("Execution block limit exceeded", failure.message)
    }
}
