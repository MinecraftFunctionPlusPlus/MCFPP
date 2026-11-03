package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MniProjectionPlanTest {
    private class Fixture(session: Long = 1, private val universe: Long = 841, shared: FrozenTypeGraph? = null) {
        val arena = CodeArena(SessionId(session), ScopeId(SessionId(session), 0)) { _, _ -> }
        val site = SourceSpan("projection-call.mcfpp", 10, 20, 2, 1, 2, 11)
        val generated = SourceSpan("projection-default.mcfpp", 30, 40, 3, 1, 3, 11)
        val origin = Origin(site, generated, listOf(ExpansionOriginFrame("fixture:macro", site)))
        fun key(id: Long) = TypeKey(universe, id)
        val valueField = FieldKey(key(9), 1)
        val nextField = FieldKey(key(9), 2)
        val graph = shared ?: FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(key(1), "int", "builtin:int", TypeKind.INT, constant = ConstantSchema.Scalar(ConstantShape.INT32)),
                FrozenTypeRecord(key(2), "bool", "builtin:bool", TypeKind.BOOL, constant = ConstantSchema.Scalar(ConstantShape.BOOL)),
                FrozenTypeRecord(key(3), "float", "builtin:float", TypeKind.FLOAT, constant = ConstantSchema.Scalar(ConstantShape.FLOAT32)),
                FrozenTypeRecord(key(4), "void", "builtin:void", TypeKind.VOID),
                FrozenTypeRecord(key(5), "string", "builtin:string", TypeKind.STRING, constant = ConstantSchema.Scalar(ConstantShape.STRING)),
                FrozenTypeRecord(key(6), "type", "builtin:type", TypeKind.OTHER, constant = ConstantSchema.Scalar(ConstantShape.TYPE)),
                FrozenTypeRecord(
                    key(7),
                    "List<int>",
                    "list:builtin:int",
                    TypeKind.OTHER,
                    index = IndexSchema(key(1), true),
                    constant = ConstantSchema.Sequence(key(1))
                ),
                FrozenTypeRecord(key(8), "List<List<int>>", "list:list:builtin:int", TypeKind.OTHER, index = IndexSchema(key(7), true)),
                FrozenTypeRecord(key(9), "Node<int>", "nominal:fixture:Node<builtin:int>", TypeKind.OTHER, fields = listOf(valueField, nextField)),
                FrozenTypeRecord(key(10), "Types", "list:builtin:type", TypeKind.OTHER, constant = ConstantSchema.Sequence(key(6))),
                FrozenTypeRecord(key(11), "Proof", "record:proof", TypeKind.OTHER, constant = ConstantSchema.Record(mapOf("types" to key(10)))),
                FrozenTypeRecord(key(12), "double", "nbt:double", TypeKind.FLOAT, constant = ConstantSchema.Scalar(ConstantShape.FLOAT64)),
                FrozenTypeRecord(key(13), "null", "builtin:null", TypeKind.OTHER, constant = ConstantSchema.Scalar(ConstantShape.NULL)),
                FrozenTypeRecord(key(14), "nbt", "builtin:nbt", TypeKind.OTHER, constant = ConstantSchema.Scalar(ConstantShape.NBT))
            ),
            listOf(
                FrozenFieldRecord(valueField, "value", key(1), true, generated),
                FrozenFieldRecord(nextField, "next", key(9), true, generated)
            ),
            listOf(FrozenConversionRecord(key(1), key(3), ConversionKind.IMPLICIT))
        )
        val int get() = graph.type(key(1))
        val bool get() = graph.type(key(2))
        val list get() = graph.type(key(7))
        val nested get() = graph.type(key(8))
        val node get() = graph.type(key(9))
        val target = LoweringTarget(FunctionKey(901, "fixture:target"))
        fun meta(scope: ScopeId = arena.rootScope) = arena.metadata(scope, origin)
        fun expr(type: TypeRef<*>, node: ExprNode, scope: ScopeId = arena.rootScope): Expr<*> = arena.expr(meta(scope), type, node)
        fun integer(value: Long = 1) = expr(int, ExprNode.Literal(ConstantValue.Integral(value)))
        fun stmt(node: StmtNode, scope: ScopeId = arena.rootScope): Stmt = arena.stmt(meta(scope), node)
        fun block(statements: List<Stmt>, scope: ScopeId = arena.rootScope) = arena.block(meta(scope), statements)
        fun storage(type: TypeRef<*> = int) = arena.ref(meta(), type, NodeLocation(arena.freshSymbol()))
        fun location(ref: Ref<*>) = (ref as ImmutableRef<*>).location
        fun alias(root: Ref<*>, id: AddressId, type: TypeRef<*> = int, steps: List<LocationStep> = emptyList(), scope: ScopeId = arena.rootScope) = arena.ref(meta(scope), type, NodeLocation(location(root).root, steps, capturedBase = id))
        fun function(
            name: String,
            parameters: List<ParameterView> = emptyList(),
            readonly: List<ParameterView> = emptyList(),
            kind: FunctionKind = FunctionKind.ORDINARY,
            result: TypeRef<*> = int
        ) = ImmutableFunctionRef(FunctionKey(901, name), name, FunctionSignature.of(parameters, result), kind, readonlyParameters = readonly)
        fun call(function: FunctionRef<*>, arguments: List<CallArgument> = emptyList(), readonly: List<TypedConstant> = emptyList()) = expr(function.returnType(), ExprNode.Call(function, arguments, readonly))
        fun proof(code: Code, bindings: List<Ref<*>> = emptyList(), functions: List<FunctionRef<*>> = emptyList(), result: ValidationRegion? = null) = CodeValidator().validate(
            code,
            ValidationEnvironment(
                arena,
                bindings,
                functions,
                resultRegion = result,
                fieldCapabilities = setOf(valueField, nextField)
            ),
            ValidationPhase.FINAL
        ) as ValidatedCode
        fun lower(
            code: Code,
            bindings: List<Ref<*>> = emptyList(),
            functions: List<FunctionRef<*>> = emptyList(),
            result: ValidationRegion? = null,
            target: LoweringTarget = this.target
        ) = CodeLowerer().lower(proof(code, bindings, functions, result), target)
    }

    @Test fun nestedIndexExpressionsEvaluateReceiverAndEachIndexExactlyOnceInOrder() {
        val f = Fixture()
        val receiver = f.function("receiver", result = f.nested)
        val first = f.function("firstIndex")
        val second = f.function("secondIndex")
        val inner = f.expr(f.list, ExprNode.Index(f.call(receiver), f.call(first)))
        val expression = f.expr(f.int, ExprNode.Index(inner, f.call(second)))
        val plan = f.lower(expression, functions = listOf(receiver, first, second))
        f.arena.close()
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        machine.invoke = { function, _ ->
            when (function.name) {
                "receiver" -> listOf(listOf(3L, 7L))
                "firstIndex" -> 0L
                "secondIndex" -> 1L
                else -> error("Unexpected call")
            }
        }
        assertEquals(7L, machine.run().second)
        assertEquals(listOf("receiver", "firstIndex", "secondIndex"), machine.calls)
        assertEquals(2, operations(plan).filterIsInstance<PlanOperation.Index>().size)
        assertDetached(plan)
    }

    @Test fun ordinaryAndNativeReadonlyArgumentsKeepTypedNestedValuesWithoutRuntimeReevaluation() {
        for (kind in listOf(FunctionKind.ORDINARY, FunctionKind.NATIVE)) {
            val f = Fixture()
            val proofType = f.graph.type(f.key(11))
            val readonly = listOf(ParameterView.of("n", f.int, ParamMode.VALUE), ParameterView.of("proof", proofType, ParamMode.CONST))
            val target = f.function("target", listOf(ParameterView.of("value", f.int, ParamMode.VALUE)), readonly, kind)
            val value = f.function("value")
            val payload = ConstantValue.Record(mapOf("types" to ConstantValue.Sequence(listOf(ConstantValue.TypeValue(f.node)))))
            val plan = f.lower(
                f.call(
                    target,
                    listOf(CallArgument.value(f.call(value))),
                    listOf(
                        TypedConstant(f.int, ConstantValue.Integral(7), f.generated),
                        TypedConstant(proofType, payload, f.generated)
                    )
                ),
                functions = listOf(value, target)
            )
            f.arena.close()
            val machine = MniCodeLoweringPlanTest.Machine(plan)
            machine.invokeWithReadonly = { function, constants, arguments ->
                if (function.name == "value") 29L else {
                    assertEquals(7L, constants[0])
                    val nested = ((constants[1] as Map<*, *>)["types"] as List<*>).single() as PlanType
                    assertEquals("nominal:fixture:Node<builtin:int>", nested.stableSourceId)
                    (arguments.single() as Long) + (constants[0] as Long)
                }
            }
            assertEquals(36L, machine.run().second)
            assertEquals(listOf("value", "target"), machine.calls)
            val call = operations(plan).filterIsInstance<PlanOperation.Call>().last()
            assertEquals(listOf(ParamMode.VALUE, ParamMode.CONST), call.function.readonlyParameters.map { it.mode })
            assertEquals(listOf(f.generated, f.generated), call.readonlyArguments.map { it.source })
            assertEquals(if (kind == FunctionKind.NATIVE) PlanCallKind.LEGACY_NATIVE else PlanCallKind.ORDINARY, call.function.kind)
            assertDetached(plan)
        }
    }

    @Test fun anUnusedValueStillExecutesBeforeRefCaptureAndLaterEffectsCannotRetargetItsAliases() {
        val f = Fixture()
        val index = f.storage()
        val root = f.storage(f.list)
        val advanceA = f.function("A")
        val advanceB = f.function("B")
        val helper = f.function(
            "helper",
            listOf(
                ParameterView.of("a", f.int, ParamMode.REF),
                ParameterView.of("b", f.int, ParamMode.REF)
            ),
            kind = FunctionKind.HELPER,
            result = f.graph.type(f.key(4))
        )
        val local = f.arena.local(f.meta(), f.int, "unused")
        val id = f.arena.freshAddress()
        val capture = f.stmt(
            StmtNode.CaptureAddress(
                id,
                NodeLocation(
                    f.location(root).root,
                    listOf(LocationStep.CapturedIndex(f.location(index).root))
                ),
                f.int
            )
        )
        val alias = f.alias(root, id)
        val code = f.block(
            listOf(
                f.stmt(StmtNode.LocalDecl(local, f.call(advanceA))),
                capture,
                f.stmt(StmtNode.Eval(f.call(advanceB))),
                f.stmt(
                    StmtNode.Eval(
                        f.call(
                            helper,
                            listOf(CallArgument.ref(alias), CallArgument.ref(alias))
                        )
                    )
                )
            )
        )
        val plan = f.lower(code, listOf(index, root), listOf(advanceA, advanceB, helper))
        f.arena.close()
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        val indexAddress = address(f.location(index).root)
        val first = address(f.location(root).root, listOf(1))
        val second = address(f.location(root).root, listOf(2))
        machine.storage[indexAddress] = 0L
        machine.storage[first] = 4L
        machine.storage[second] = 8L
        machine.invoke = { function, arguments ->
            when (function.name) {
                "A" -> {
                    machine.storage[indexAddress] = 1L
                    3L
                }
                "B" -> {
                    machine.storage[indexAddress] = 2L
                    5L
                }
                else -> {
                    assertEquals(first, arguments[0])
                    assertEquals(arguments[0], arguments[1])
                    machine.storage[arguments[0] as MniCodeLoweringPlanTest.Address] = 28L
                    machine.storage[arguments[1] as MniCodeLoweringPlanTest.Address] = (machine.storage[first] as Long) + 1
                    null
                }
            }
        }
        machine.run()
        assertEquals(listOf("A", "B", "helper"), machine.calls)
        assertEquals(29L, machine.storage[first])
        assertEquals(8L, machine.storage[second])
        val captures = operations(plan).filterIsInstance<PlanOperation.CaptureLocation>()
        assertEquals(1, captures.size)
        val references = operations(plan).filterIsInstance<PlanOperation.Call>().last().arguments.filterIsInstance<PlanArgument.Reference>()
        assertEquals(listOf(captures.single().result, captures.single().result), references.map { it.location })
    }

    @Test fun relativeCapturedPathsExtendTheSavedAddressRatherThanRestartingAtTheProvenanceRoot() {
        val f = Fixture()
        val root = f.storage(f.nested)
        val index = f.storage()
        val base = f.arena.freshAddress()
        val selected = f.arena.freshAddress()
        val prefix = f.stmt(StmtNode.CaptureAddress(base, NodeLocation(f.location(root).root, listOf(LocationStep.Index(0))), f.list))
        val suffix = f.stmt(
            StmtNode.CaptureAddress(
                selected,
                NodeLocation(
                    f.location(root).root,
                    listOf(LocationStep.CapturedIndex(f.location(index).root)),
                    capturedBase = base
                ),
                f.int
            )
        )
        val value = f.alias(root, selected)
        val region = f.arena.freshRegion()
        val code = f.block(
            listOf(
                prefix,
                f.stmt(StmtNode.Assign(index, f.integer(1))),
                suffix,
                f.stmt(StmtNode.Assign(index, f.integer(2))),
                f.stmt(StmtNode.Yield(region, value.load()))
            )
        )
        val plan = f.lower(code, listOf(root, index), result = ValidationRegion(region, f.int))
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        machine.storage[address(f.location(root).root, listOf(0, 1))] = 7L
        machine.storage[address(f.location(root).root, listOf(1))] = 99L
        machine.storage[address(f.location(root).root, listOf(0, 2))] = 88L
        assertEquals(PlanExitKind.RESULT to 7L, machine.run())
        val captures = operations(plan).filterIsInstance<PlanOperation.CaptureLocation>().filter { it.location.root.binding == f.location(root).root }
        assertEquals(2, captures.size)
        assertEquals(captures[0].result, captures[1].location.capturedBase)
        assertEquals(captures[1].result, operations(plan).filterIsInstance<PlanOperation.Read>().single().location)
    }

    @Test fun captureDeclarationsExecuteAgainOnEveryLoopIteration() {
        val f = Fixture()
        val root = f.storage(f.list)
        val counter = f.storage()
        val scope = f.arena.childScope(f.arena.rootScope)
        val id = f.arena.freshAddress()
        val capture = f.stmt(
            StmtNode.CaptureAddress(
                id,
                NodeLocation(
                    f.location(root).root,
                    listOf(LocationStep.CapturedIndex(f.location(counter).root))
                ),
                f.int
            ),
            scope
        )
        val alias = f.alias(root, id, scope = scope)
        val body = f.block(
            listOf(
                capture,
                f.stmt(StmtNode.Assign(alias, f.expr(f.int, ExprNode.Binary(BinaryOperator.ADD, alias.load(), f.integer()), scope)), scope),
                f.stmt(StmtNode.Assign(counter, f.expr(f.int, ExprNode.Binary(BinaryOperator.ADD, counter.load(), f.integer()))), scope)
            ),
            scope
        )
        val condition = f.expr(f.bool, ExprNode.Binary(BinaryOperator.LT, counter.load(), f.integer(2)))
        val code = f.block(listOf(f.stmt(StmtNode.While(condition, body, f.arena.freshRegion()))))
        val plan = f.lower(code, listOf(root, counter))
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        machine.storage[address(f.location(counter).root)] = 0L
        machine.storage[address(f.location(root).root, listOf(0))] = 4L
        machine.storage[address(f.location(root).root, listOf(1))] = 8L
        machine.run()
        assertEquals(5L, machine.storage[address(f.location(root).root, listOf(0))])
        assertEquals(9L, machine.storage[address(f.location(root).root, listOf(1))])
        assertEquals(listOf(listOf(0), listOf(1)), machine.captured.filter { it.symbol == f.location(root).root }.map { it.path })
        assertEquals(1, operations(plan).filterIsInstance<PlanOperation.CaptureLocation>().count { it.location.root.binding == f.location(root).root })
    }

    @Test fun everyLiteralIncludingNestedTypesIsRecursivelyDetachedBeforePublication() {
        val f = Fixture()
        val nested = ConstantValue.Record(mapOf("types" to ConstantValue.Sequence(listOf(ConstantValue.TypeValue(f.node)))))
        val cases = listOf(
            11L to nested,
            7L to ConstantValue.Sequence(listOf(ConstantValue.Integral(7))),
            3L to ConstantValue.FloatBits(1.25f.toRawBits()),
            12L to ConstantValue.DoubleBits(1.25.toRawBits()),
            13L to ConstantValue.NullValue,
            14L to ConstantValue.Nbt("{value:1}"),
            5L to ConstantValue.Text("hello")
        )
        val plans = cases.map { (key, payload) -> f.lower(f.expr(f.graph.type(f.key(key)), ExprNode.Literal(payload))) }
        f.arena.close()
        plans.forEach { plan ->
            assertIs<PlanConstant>(operations(plan).filterIsInstance<PlanOperation.Literal>().single().value)
            MniCodeLoweringPlanTest.Machine(plan).run()
            assertDetached(plan)
        }
        assertEquals(ConstantShape.FLOAT32, plans[2].resultType!!.scalarShape)
        assertEquals(ConstantShape.FLOAT64, plans[3].resultType!!.scalarShape)
        val payload = assertIs<PlanConstant.Record>(operations(plans[0]).filterIsInstance<PlanOperation.Literal>().single().value)
        val type = assertIs<PlanConstant.TypeValue>(assertIs<PlanConstant.Sequence>(payload.fields.getValue("types")).elements.single()).type
        assertEquals("nominal:fixture:Node<builtin:int>", type.stableSourceId)
        assertFailsWith<UnsupportedOperationException> { (payload.fields as MutableMap<*, *>).clear() }
    }

    @Test fun independentSessionsShareADeclarationDomainWithoutSharingAnyArenaState() {
        val first = Fixture(session = 201)
        val second = Fixture(session = 202, shared = first.graph)
        fun plan(f: Fixture) = f.lower(f.expr(f.graph.type(f.key(6)), ExprNode.Literal(ConstantValue.TypeValue(f.node))))
        val a = plan(first)
        val b = plan(second)
        first.arena.close()
        second.arena.close()
        assertEquals(a.domain, b.domain)
        assertEquals(setOf(841L), a.domain.typeDomains)
        assertFalse(a.domain.complete)
        assertEquals(MniCodeLoweringPlanTest.Machine(a).run(), MniCodeLoweringPlanTest.Machine(b).run())
        assertDetached(a)
        assertDetached(b)
    }

    @Test fun rebuiltDomainsKeepStableSourceIdentityButRejectMixedOldHandles() {
        val old = Fixture(universe = 851)
        val fresh = Fixture(session = 2, universe = 852)
        val oldPlan = old.lower(old.integer())
        val newPlan = fresh.lower(fresh.integer())
        assertNotEquals(oldPlan.domain, newPlan.domain)
        assertEquals(oldPlan.resultType!!.stableSourceId, newPlan.resultType!!.stableSourceId)
        val mixed = fresh.expr(fresh.graph.type(fresh.key(6)), ExprNode.Literal(ConstantValue.TypeValue(old.node)))
        val failure = assertFailsWith<CodeModelException> { fresh.lower(mixed) }
        assertEquals(fresh.site, failure.source)
        old.arena.close()
        fresh.arena.close()
        assertEquals(1L, MniCodeLoweringPlanTest.Machine(oldPlan).run().second)
        assertEquals(1L, MniCodeLoweringPlanTest.Machine(newPlan).run().second)
        assertDetached(oldPlan)
        assertDetached(newPlan)
    }

    @Test fun explicitDomainVersionsAreImmutableAndAnUnrelatedDomainCannotConsumeAProof() {
        val f = Fixture()
        val domains = linkedSetOf(841L)
        val stamp = PlanDomainStamp(domains, "layout-7", "targets-11")
        domains.clear()
        val plan = f.lower(f.integer(), target = f.target.copy(domain = stamp))
        assertTrue(plan.domain.complete)
        assertEquals(setOf(841L), plan.domain.typeDomains)
        assertEquals("layout-7", plan.domain.layoutVersion)
        assertEquals("targets-11", plan.domain.targetVersion)
        assertFailsWith<UnsupportedOperationException> { (plan.domain.typeDomains as MutableSet<Long>).clear() }
        val failure = assertFailsWith<CodeModelException> {
            f.lower(f.integer(), target = f.target.copy(domain = PlanDomainStamp(setOf(999L), "layout-8", "targets-12")))
        }
        assertEquals(f.site, failure.source)
        assertTrue(failure.message!!.contains("declaration domain"))
        assertNull(f.lower(f.integer()).domain.layoutVersion)
        assertNull(f.lower(f.integer()).domain.targetVersion)
        assertDetached(plan)
    }

    @Test fun allNewOperationsPreserveCallsiteDefinitionAndExpansionFrames() {
        val f = Fixture()
        val root = f.storage(f.list)
        val id = f.arena.freshAddress()
        val capture = f.stmt(StmtNode.CaptureAddress(id, f.location(root), f.list))
        val alias = f.alias(root, id, type = f.list)
        val read = f.expr(f.int, ExprNode.Index(alias.load(), f.integer(0)))
        val plan = f.lower(f.block(listOf(capture, f.stmt(StmtNode.Eval(read)))), listOf(root))
        operations(plan).forEach { operation ->
            assertEquals(f.site, operation.site.callSite)
            assertEquals(f.generated, operation.site.generatedFrom)
            assertEquals(listOf("fixture:macro"), operation.site.frames.map { it.macroName })
        }
        assertTrue(operations(plan).any { it is PlanOperation.Index })
        assertDetached(plan)
    }

    @Test fun canonicalFieldsRetainTheGenericDeclaringOwnerAsDetachedSourceIdentity() {
        val f = Fixture()
        val root = f.storage(f.node)
        val code = f.expr(f.int, ExprNode.Field(root.load(), f.graph.field(f.valueField)))
        val plan = f.lower(code, listOf(root))
        f.arena.close()
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        machine.storage[address(f.location(root).root)] = mapOf(f.valueField to 23L)
        assertEquals(23L, machine.run().second)
        val field = operations(plan).filterIsInstance<PlanOperation.Field>().single().field
        assertEquals("nominal:fixture:Node<builtin:int>", field.ownerType!!.stableSourceId)
        assertEquals("builtin:int", field.type.stableSourceId)
        assertDetached(plan)
    }

    private fun operations(plan: LoweringPlan) = plan.blocks.flatMap { it.operations }
    private fun address(symbol: SymbolId, path: List<Any> = emptyList()) = MniCodeLoweringPlanTest.Address(symbol, path)

    /** Inspect every reachable data field after arena closure, rather than checking only Literal's top-level type. */
    private fun assertDetached(plan: LoweringPlan) {
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        fun visit(value: Any?) {
            if (value == null || !seen.add(value)) return
            assertFalse(
                value is TypeRef<*> ||
                    value is Code ||
                    value is FunctionRef<*> ||
                    value is CodeArena ||
                    value is SourceFunction ||
                    value is BoundExpr ||
                    value is top.mcfpp.type.MCFPPType ||
                    value is top.mcfpp.model.function.Function ||
                    value is java.lang.reflect.Method ||
                    value is org.antlr.v4.runtime.tree.ParseTree ||
                    value is kotlin.Function<*> ||
                    value is Runnable ||
                    value is java.util.function.Function<*, *> ||
                    value is java.util.function.Consumer<*>,
                value.javaClass.name
            )
            when (value) {
                is Map<*, *> -> value.forEach { (key, item) ->
                    visit(key)
                    visit(item)
                }
                is Iterable<*> -> value.forEach(::visit)
                is Array<*> -> value.forEach(::visit)
                is String, is Number, is Boolean, is Enum<*> -> Unit
                else -> {
                    check(value.javaClass.name.startsWith("top.mcfpp.mni.")) { "Unexpected plan payload ${value.javaClass.name}" }
                    value.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach {
                        it.isAccessible = true
                        visit(it.get(value))
                    }
                }
            }
        }
        visit(plan)
    }
}
