package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniReceiverPlanTest {
    private class Fixture(id: Long = 1) {
        val arena = CodeArena(SessionId(id), ScopeId(SessionId(id), 0)) { _, _ -> }
        val site = SourceSpan("receiver.mcfpp", 10, 40, 2, 1, 2, 31)
        val origin = Origin(site, expansionFrames = listOf(ExpansionOriginFrame("test:receiver", site)))
        val int = ImmutableTypeRef<IntT>(TypeKey(1, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(1, 2), "bool", kind = TypeKind.BOOL)
        val target = LoweringTarget(FunctionKey(9, "test:receiverTarget"))

        fun meta(context: ExecutionContextId? = null) = arena.metadata(arena.rootScope, origin, context)
        fun <T> ref(
            type: TypeRef<T>,
            location: NodeLocation = NodeLocation(arena.freshSymbol()),
            metadata: CodeMetadata = meta()
        ): ImmutableRef<T> = arena.ref(metadata, type, location)
        fun <T> function(
            name: String,
            result: TypeRef<T>,
            parameters: List<ParameterView> = emptyList(),
            kind: FunctionKind = FunctionKind.ORDINARY,
            readonly: List<ParameterView> = emptyList()
        ) = ImmutableFunctionRef(
            FunctionKey(4, name),
            name,
            FunctionSignature.of(parameters, result),
            kind,
            readonlyParameters = readonly
        )
        fun <T> call(
            metadata: CodeMetadata,
            function: FunctionRef<T>,
            arguments: List<CallArgument> = emptyList(),
            readonly: List<TypedConstant> = emptyList(),
            receiver: Ref<*>? = null
        ) = ImmutableExpr(metadata, function.returnType(), ExprNode.Call(function, arguments, readonly, receiver))
        fun proof(code: Code, bindings: List<Ref<*>> = emptyList(), functions: List<FunctionRef<*>> = emptyList()): ValidatedCode = CodeValidator().validate(code, ValidationEnvironment(arena, bindings, functions), ValidationPhase.FINAL) as ValidatedCode
        fun lower(
            code: Code,
            context: ExecutionContextId? = null,
            bindings: List<Ref<*>> = emptyList(),
            functions: List<FunctionRef<*>> = emptyList()
        ) = CodeLowerer().lower(proof(code, bindings, functions), LoweringTarget(target.function, context))
    }

    private data class Address(
        val symbol: SymbolId,
        val path: List<Any> = emptyList(),
        val context: ExecutionContextId? = null
    )

    /** Executes only the detached plan protocol; it never consults a global selector or Code node. */
    private class Machine(val plan: LoweringPlan) {
        val storage = linkedMapOf<Address, Any?>()
        val calls = arrayListOf<String>()
        val readonlyFacts = arrayListOf<List<Any?>>()
        val receivers = arrayListOf<Address>()
        val captured = arrayListOf<Address>()
        var invoke: (PlanFunction, List<Any?>) -> Any? = { _, _ -> null }
        private val values = hashMapOf<PlanTemp, Any?>()
        private val locations = hashMapOf<PlanLocationId, Address>()

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
                    is PlanLocationStep.CapturedIndex -> (storage[Address(it.symbol.binding)] as Number).toInt()
                    is PlanLocationStep.CapturedKey -> storage[Address(it.symbol.binding)] as String
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

        fun run(maxBlocks: Int = 100): Pair<PlanExitKind, Any?> {
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
                        is PlanOperation.Move -> values[operation.result] = value(operation.value)
                        is PlanOperation.Convert -> values[operation.result] = when (operation.result.type.kind) {
                            TypeKind.INT -> (value(operation.operand) as Number).toLong()
                            TypeKind.FLOAT -> (value(operation.operand) as Number).toDouble()
                            else -> value(operation.operand)
                        }
                        is PlanOperation.Call -> {
                            calls += operation.function.name
                            readonlyFacts += operation.readonlyArguments.map { constant(it.value) }
                            operation.receiver?.let { receivers += locations.getValue(it.location) }
                            val arguments = operation.arguments.map { argument ->
                                when (argument) {
                                    is PlanArgument.Value -> value(argument.value)
                                    is PlanArgument.Reference -> locations.getValue(argument.location)
                                }
                            }
                            operation.result?.let { values[it] = invoke(operation.function, arguments) }
                        }
                        else -> error("Unsupported plan operation in receiver test backend: $operation")
                    }
                }
                val exit = block.terminator
                if (exit !is PlanTerminator.Exit) error("Unsupported plan terminator in receiver test backend: $exit")
                return exit.kind to exit.value?.let(::value)
            }
            error("Execution block limit exceeded")
        }
    }

    @Test
    fun memberReceiverIsCapturedBeforeEffectfulArgumentsAtItsOriginalFieldIndexAndContext() {
        val f = Fixture()
        val context = f.arena.rootContext(f.arena.rootScope).id
        val meta = f.meta(context)
        val index = f.ref(f.int)
        val receiverRoot = f.arena.freshSymbol()
        val field = ImmutableFieldRef(FieldKey(TypeKey(1, 20), 1), "hp", f.int, true, f.site)
        val receiver = f.ref(
            f.int,
            NodeLocation(
                receiverRoot,
                listOf(LocationStep.Field(field), LocationStep.Index(0), LocationStep.CapturedIndex(index.location.root)),
                executionContextId = context,
                writable = true
            ),
            meta
        )
        val effectA = f.function("effectA", f.int, kind = FunctionKind.NATIVE)
        val effectB = f.function("effectB", f.int, kind = FunctionKind.NATIVE)
        val member = f.function(
            "member",
            f.int,
            listOf(
                ParameterView.of("a", f.int, ParamMode.VALUE),
                ParameterView.of("b", f.int, ParamMode.VALUE)
            )
        )
        val expression = f.call(
            meta,
            member,
            listOf(CallArgument.value(f.call(meta, effectA)), CallArgument.value(f.call(meta, effectB))),
            receiver = receiver
        )
        val node = assertIs<ExprNode.Call>((expression as ImmutableExpr<*>).node)
        assertSame(receiver, node.receiver)

        val plan = f.lower(expression, context, listOf(index, receiver), listOf(effectA, effectB, member))
        f.arena.close()

        val operations = plan.blocks.single().operations
        val captureIndex = operations.indexOfFirst {
            it is PlanOperation.CaptureLocation && it.location.root.binding == receiverRoot
        }
        assertTrue(captureIndex >= 0, "The receiver capture must be present")
        val capture = operations[captureIndex] as PlanOperation.CaptureLocation
        assertEquals(f.int.key, capture.result.type.key)
        assertEquals(context, capture.location.executionContext)
        assertEquals(true, capture.location.writable)
        assertEquals(field.key, (capture.location.steps[0] as PlanLocationStep.Field).field.key)
        assertEquals(0, (capture.location.steps[1] as PlanLocationStep.Index).value)
        val frozenIndex = capture.location.steps[2] as PlanLocationStep.CapturedIndex
        assertEquals(index.location.root, frozenIndex.symbol.binding)

        val effectAIndex = operations.indexOfFirst { it is PlanOperation.Call && it.function.name == "effectA" }
        val effectBIndex = operations.indexOfFirst { it is PlanOperation.Call && it.function.name == "effectB" }
        val memberCall = operations.filterIsInstance<PlanOperation.Call>().single { it.function.name == "member" }
        val memberIndex = operations.indexOf(memberCall)
        assertTrue(
            captureIndex < effectAIndex && effectAIndex < effectBIndex && effectBIndex < memberIndex,
            "Receiver capture must precede effectful arguments and the call"
        )
        assertEquals(PlanArgument.Reference(capture.result), memberCall.receiver)
        assertEquals(member.key, memberCall.function.key)
        assertEquals(PlanCallKind.ORDINARY, memberCall.function.kind)
        assertEquals(f.int.key, memberCall.result!!.type.key)
        assertEquals(f.int.key, plan.resultType!!.key)

        val machine = Machine(plan)
        machine.storage[Address(index.location.root)] = 0L
        machine.invoke = { function, arguments ->
            when (function.name) {
                "effectA" -> {
                    machine.storage[Address(index.location.root)] = 1L
                    10L
                }
                "effectB" -> 20L
                else -> (arguments[0] as Long) + (arguments[1] as Long)
            }
        }
        assertEquals(PlanExitKind.EXPRESSION to 30L, machine.run())
        assertEquals(listOf("effectA", "effectB", "member"), machine.calls)
        // effectA changed the dynamic index to 1, but the receiver kept index 0 and its context.
        assertEquals(listOf(Address(receiverRoot, listOf(field.key, 0, 0), context)), machine.receivers)
        assertEquals(listOf(Address(receiverRoot, listOf(field.key, 0, 0), context)), machine.captured)
    }

    @Test
    fun genericCallKeepsTypedReadonlyTargetIdentityReceiverAndReturnValue() {
        val f = Fixture()
        val receiver = f.ref(f.int)
        val effect = f.function("effect", f.int, kind = FunctionKind.NATIVE)
        val identity = f.function(
            "identity",
            f.int,
            listOf(ParameterView.of("p0", f.int, ParamMode.VALUE)),
            FunctionKind.ORDINARY,
            readonly = listOf(ParameterView.of("T", f.int, ParamMode.CONST))
        )
        val readonly = TypedConstant(f.int, ConstantValue.Integral(11), f.site)
        val expression = f.call(
            f.meta(),
            identity,
            listOf(CallArgument.value(f.call(f.meta(), effect))),
            listOf(readonly),
            receiver
        )
        val node = assertIs<ExprNode.Call>((expression as ImmutableExpr<*>).node)
        assertSame(receiver, node.receiver)

        val plan = f.lower(expression, bindings = listOf(receiver), functions = listOf(effect, identity))
        val capture = plan.blocks.single().operations.filterIsInstance<PlanOperation.CaptureLocation>().single()
        val call = plan.blocks.single().operations.filterIsInstance<PlanOperation.Call>().single { it.function.name == "identity" }
        assertEquals(PlanArgument.Reference(capture.result), call.receiver)
        assertEquals(receiver.location.root, capture.location.root.binding)
        assertEquals(identity.key, call.function.key)
        assertEquals(PlanCallKind.ORDINARY, call.function.kind)
        assertEquals(f.int.key, call.readonlyArguments.single().type.key)
        assertEquals(11L, (call.readonlyArguments.single().value as PlanConstant.Integral).value)
        assertEquals(f.int.key, call.result!!.type.key)
        assertEquals(f.int.key, plan.resultType!!.key)

        val machine = Machine(plan)
        machine.invoke = { function, arguments ->
            when (function.name) {
                "effect" -> 5L
                "identity" -> {
                    (arguments.single() as Long) + 11L
                }
                else -> error("Unexpected call '${function.name}'")
            }
        }
        assertEquals(PlanExitKind.EXPRESSION to 16L, machine.run())
        assertEquals(listOf("effect", "identity"), machine.calls)
        assertEquals(listOf(Address(receiver.location.root)), machine.receivers)
        assertEquals(listOf(emptyList<Any?>(), listOf<Any?>(11L)), machine.readonlyFacts)
    }

    @Test
    fun receiverlessExistingConstructionStaysCompatible() {
        val f = Fixture()
        val input = f.function("input", f.int)
        val sink = f.function("sink", f.int, listOf(ParameterView.of("value", f.int, ParamMode.VALUE)))
        // The three-argument call node is the existing receiverless constructor.
        val expression = ImmutableExpr(
            f.meta(),
            f.int,
            ExprNode.Call(sink, listOf(CallArgument.value(f.call(f.meta(), input))))
        )
        val node = assertIs<ExprNode.Call>((expression as ImmutableExpr<*>).node)
        assertNull(node.receiver)

        val plan = f.lower(expression, functions = listOf(input, sink))
        val call = plan.blocks.single().operations.filterIsInstance<PlanOperation.Call>().single { it.function.name == "sink" }
        assertNull(call.receiver)
        assertEquals(sink.key, call.function.key)
        assertEquals(f.int.key, call.result!!.type.key)

        val machine = Machine(plan)
        machine.invoke = { function, arguments ->
            when (function.name) {
                "input" -> 4L
                "sink" -> arguments.single()
                else -> error("Unexpected call '${function.name}'")
            }
        }
        assertEquals(PlanExitKind.EXPRESSION to 4L, machine.run())
        assertEquals(listOf("input", "sink"), machine.calls)
        assertTrue(machine.receivers.isEmpty())
    }

    @Test
    fun importedCallRemapsInternalReceiverAndPrintsItBeforeArguments() {
        val f = Fixture()
        val bodyScope = f.arena.childScope(f.arena.rootScope)
        val bodyMeta = f.arena.metadata(bodyScope, f.origin)
        val receiver = f.arena.local(bodyMeta, f.int, "receiver")
        val effect = f.function("effect", f.int, kind = FunctionKind.NATIVE)
        val member = f.function("member", f.int, listOf(ParameterView.of("value", f.int, ParamMode.VALUE)))
        val region = f.arena.freshRegion()
        val initial = f.arena.expr(bodyMeta, f.int, ExprNode.Literal(ConstantValue.Integral(3)))
        val effectCall = f.call(bodyMeta, effect)
        val call = f.call(bodyMeta, member, listOf(CallArgument.value(effectCall)), receiver = receiver)
        val body = f.arena.block(
            bodyMeta,
            listOf(
                f.arena.stmt(bodyMeta, StmtNode.LocalDecl(receiver, initial)),
                f.arena.stmt(bodyMeta, StmtNode.Yield(region, call))
            )
        )
        val expression = f.arena.expr(f.meta(), f.int, ExprNode.ExprBlock(body, f.int, region))

        val imported = f.arena.importTree(expression, f.arena.rootScope)
        val importedBlock = assertIs<ExprNode.ExprBlock>((imported as ImmutableExpr<*>).node)
        val importedLocal = assertIs<StmtNode.LocalDecl>(
            (importedBlock.block.statements()[0] as ImmutableStmt).node
        ).local as ImmutableLocal<*>
        val importedYield = assertIs<StmtNode.Yield>((importedBlock.block.statements()[1] as ImmutableStmt).node)
        val importedCall = assertIs<ExprNode.Call>((importedYield.value as ImmutableExpr<*>).node)
        assertNotEquals(receiver.symbol, importedLocal.symbol)
        assertSame(importedLocal, importedCall.receiver)

        val printed = CodePrinter().print(imported)
        val receiverless = CodePrinter().print(f.call(bodyMeta, member, listOf(CallArgument.value(effectCall))))
        assertTrue(printed.contains("receiver "))
        assertTrue(!receiverless.contains("receiver "))
        assertTrue(printed.contains("call \"member\""))

        val plan = f.lower(imported, functions = listOf(effect, member))
        val executed = plan.blocks.single { block ->
            block.operations.any { it is PlanOperation.Call && it.function.name == "member" }
        }
        val captureIndex = executed.operations.indexOfFirst {
            it is PlanOperation.CaptureLocation && it.location.root.binding == importedLocal.symbol
        }
        val effectIndex = executed.operations.indexOfFirst {
            it is PlanOperation.Call && it.function.name == "effect"
        }
        val memberIndex = executed.operations.indexOfFirst {
            it is PlanOperation.Call && it.function.name == "member"
        }
        assertTrue(
            captureIndex >= 0 && captureIndex < effectIndex && effectIndex < memberIndex,
            "The imported receiver must be captured before its arguments and the call"
        )
    }

    @Test
    fun childImportedReceiverKeepsItsPhysicalAliasAndCaptureOrder() {
        val f = Fixture()
        val childSession = SessionId(9)
        val child = f.arena.childArena(childSession, ScopeId(childSession, 0))
        val childMeta = child.metadata(child.rootScope, f.origin)
        val parentReceiver = f.ref(f.int)
        val childReceiver = child.ref(childMeta, f.int, NodeLocation(child.freshSymbol()))
        val effect = f.function("effect", f.int, kind = FunctionKind.NATIVE)
        val member = f.function("member", f.int, listOf(ParameterView.of("value", f.int, ParamMode.VALUE)))
        val effectCall = f.call(childMeta, effect)
        val expression = f.call(childMeta, member, listOf(CallArgument.value(effectCall)), receiver = childReceiver)
        val imported = f.arena.importChildTree(
            child,
            expression,
            f.arena.rootScope,
            externalBindings = mapOf(childReceiver.location.root to parentReceiver)
        )
        val importedCall = assertIs<ExprNode.Call>((imported as ImmutableExpr<*>).node)
        assertSame(parentReceiver, importedCall.receiver)
        child.close()

        val plan = f.lower(imported, bindings = listOf(parentReceiver), functions = listOf(effect, member))
        val operations = plan.blocks.single().operations
        val captureIndex = operations.indexOfFirst {
            it is PlanOperation.CaptureLocation && it.location.root.binding == parentReceiver.location.root
        }
        val effectIndex = operations.indexOfFirst {
            it is PlanOperation.Call && it.function.name == "effect"
        }
        val memberIndex = operations.indexOfFirst {
            it is PlanOperation.Call && it.function.name == "member"
        }
        assertTrue(captureIndex in 0 until effectIndex && effectIndex < memberIndex)
        val capture = operations[captureIndex] as PlanOperation.CaptureLocation
        val memberCall = operations.filterIsInstance<PlanOperation.Call>().single { it.function.name == "member" }
        assertEquals(PlanArgument.Reference(capture.result), memberCall.receiver)

        val machine = Machine(plan)
        machine.invoke = { function, arguments ->
            when (function.name) {
                "effect" -> 10L
                "member" -> (arguments.single() as Long) + 5L
                else -> error("Unexpected call '${function.name}'")
            }
        }
        assertEquals(PlanExitKind.EXPRESSION to 15L, machine.run())
        assertEquals(listOf("effect", "member"), machine.calls)
        assertEquals(listOf(Address(parentReceiver.location.root)), machine.receivers)
        assertEquals(listOf(Address(parentReceiver.location.root)), machine.captured)
    }

    @Test
    fun aCallCannotDropAReceiverWithAHiddenCapturedAddress() {
        val f = Fixture()
        val meta = f.meta()
        val base = f.ref(f.int)
        val address = f.arena.freshAddress()
        val capture = f.arena.stmt(
            meta,
            StmtNode.CaptureAddress(
                address,
                NodeLocation(base.location.root, listOf(LocationStep.Index(0))),
                f.int
            )
        )
        val receiver = f.ref(f.int, NodeLocation(base.location.root, capturedBase = address))
        val member = f.function("member", f.int)
        val call = f.call(meta, member, receiver = receiver)

        val missing = assertFailsWith<CodeModelException> {
            f.arena.importTree(call, f.arena.rootScope)
        }
        assertTrue(missing.message!!.contains("Captured address declaration is outside the imported tree"))

        val body = f.arena.block(meta, listOf(capture, f.arena.stmt(meta, StmtNode.Eval(call))))
        val imported = f.arena.importTree(body, f.arena.rootScope)
        val copiedCapture = assertIs<StmtNode.CaptureAddress>((imported.statements()[0] as ImmutableStmt).node)
        val copiedCall = assertIs<ExprNode.Call>(
            (
                assertIs<StmtNode.Eval>(
                    (imported.statements()[1] as ImmutableStmt).node
                ).expression as ImmutableExpr<*>
                ).node
        )
        val copiedReceiver = assertIs<ImmutableRef<*>>(copiedCall.receiver)
        assertNotEquals(address, copiedCapture.address)
        assertEquals(copiedCapture.address, copiedReceiver.location.capturedBase)
        assertEquals(base.location.root, copiedReceiver.location.root)
    }

    @Test
    fun validatorRejectsForeignAndOutOfScopeReceivers() {
        val f = Fixture()
        val member = f.function("member", f.int)
        val foreign = Fixture(9)
        val foreignReceiver = foreign.ref(foreign.int)
        val foreignError = assertFailsWith<CodeModelException> {
            f.proof(f.call(f.meta(), member, receiver = foreignReceiver), functions = listOf(member))
        }
        assertTrue(foreignError.message!!.contains("another session"))

        val childScope = f.arena.childScope(f.arena.rootScope)
        val hidden = f.ref(f.int, NodeLocation(f.arena.freshSymbol()), f.arena.metadata(childScope, f.origin))
        val scopeError = assertFailsWith<CodeModelException> {
            f.proof(f.call(f.meta(), member, receiver = hidden), functions = listOf(member))
        }
        assertTrue(scopeError.message!!.contains("outside its dominating lexical scope"))
    }

    @Test
    fun validatorRejectsReceiverTypeAndContextThatDoNotMatchItsLocation() {
        val f = Fixture()
        val member = f.function("member", f.int)
        val storage = f.ref(f.int)
        val forged = f.ref(f.bool, storage.location)
        val typeError = assertFailsWith<CodeModelException> {
            f.proof(f.call(f.meta(), member, receiver = forged), bindings = listOf(storage), functions = listOf(member))
        }
        assertTrue(typeError.message!!.contains("Actual type mismatch"))

        val first = f.arena.rootContext(f.arena.rootScope).id
        val second = f.arena.rootContext(f.arena.rootScope).id
        val contextual = f.ref(
            f.int,
            NodeLocation(f.arena.freshSymbol(), executionContextId = first),
            f.meta(second)
        )
        val contextError = assertFailsWith<CodeModelException> {
            f.proof(f.call(f.meta(second), member, receiver = contextual), functions = listOf(member))
        }
        assertTrue(contextError.message!!.contains("Reference lost its bound execution context"))
    }
}
