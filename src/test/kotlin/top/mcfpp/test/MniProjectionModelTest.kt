package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniProjectionModelTest {
    private val span = SourceSpan("projection.mcfpp", 3, 8, 1, 4, 1, 9)
    private val session = SessionId(81)
    private val scope = ScopeId(session, 0)
    private val metadata = CodeMetadata(session, scope, Origin(span))
    private val intKey = TypeKey(81, 1)
    private val intType = ImmutableTypeRef<IntT>(intKey, "int", kind = TypeKind.INT)

    @Test fun recursiveFieldsResolveToCanonicalHandlesWithoutExpandingTheTypeGraph() {
        val node = TypeKey(81, 2)
        val pair = TypeKey(81, 3)
        val next = FieldKey(node, 1)
        val partner = FieldKey(node, 2)
        val back = FieldKey(pair, 1)
        val graph = FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(node, "Node", "fixture:Node", TypeKind.OTHER, fields = listOf(next, partner)),
                FrozenTypeRecord(pair, "Pair", "fixture:Pair", TypeKind.OTHER, fields = listOf(back))
            ),
            listOf(
                FrozenFieldRecord(next, "next", node, true, span),
                FrozenFieldRecord(partner, "partner", pair, false, span),
                FrozenFieldRecord(back, "back", node, true, span)
            )
        )
        val canonical = graph.type(node)
        assertSame(canonical, canonical.fields()[0].type())
        assertSame(canonical, canonical.fields()[1].type().fields().single().type())
        repeat(1_000) { assertSame(graph.field(next), graph.type(node).fields()[0]) }
        assertFailsWith<UnsupportedOperationException> { (canonical.fields() as MutableList<*>).clear() }
    }

    @Test fun inheritedViewsShareTheOriginalFieldIdentityAcrossDifferentReceiverTypes() {
        val parent = TypeKey(81, 2)
        val child = TypeKey(81, 3)
        val field = FieldKey(parent, 12)
        val graph = FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(intKey, "int", "builtin:int", TypeKind.INT),
                FrozenTypeRecord(
                    parent,
                    "Parent<int>",
                    "fixture:Parent<builtin:int>",
                    TypeKind.OTHER,
                    acceptedSources = setOf(child),
                    fields = listOf(field)
                ),
                FrozenTypeRecord(child, "Child", "fixture:Child", TypeKind.OTHER, fields = listOf(field))
            ),
            listOf(FrozenFieldRecord(field, "value", intKey, true, span))
        )
        assertSame(graph.type(parent).fields().single(), graph.type(child).fields().single())
        assertSame(graph.type(intKey), graph.field(field).type())
        assertTrue(graph.type(parent).isAssignableFrom(graph.type(child)))
        assertFalse(graph.type(child).isAssignableFrom(graph.type(parent)))
    }

    @Test fun freezingRetainsAllTypeFactsWhenInputCollectionsAndLaterDomainsChange() {
        val list = TypeKey(81, 2)
        val accepted = linkedSetOf(intKey)
        val schemaFields = linkedMapOf("head" to intKey)
        val records = arrayListOf(
            FrozenTypeRecord(
                intKey,
                "int",
                "builtin:int",
                TypeKind.INT,
                constant = ConstantSchema.Scalar(ConstantShape.INT32)
            ),
            FrozenTypeRecord(
                list,
                "List<int>",
                "builtin:list<builtin:int>",
                TypeKind.OTHER,
                acceptedSources = accepted,
                index = IndexSchema(intKey, false),
                constant = ConstantSchema.Record(schemaFields)
            )
        )
        val conversions = arrayListOf(FrozenConversionRecord(intKey, list, ConversionKind.EXPLICIT))
        val old = FrozenTypeGraph.freeze(records, conversions = conversions)
        accepted.clear()
        schemaFields.clear()
        records.clear()
        conversions.clear()
        assertTrue(old.type(list).isAssignableFrom(old.type(intKey)))
        assertEquals(IndexSchema(intKey, false), old.index(list))
        val schema = assertIs<ConstantSchema.Record>(old.constantSchema(list))
        assertEquals(mapOf("head" to intKey), schema.fields)
        assertFailsWith<UnsupportedOperationException> { (schema.fields as MutableMap<*, *>).clear() }
        assertTrue(old.permits(intKey, list, ConversionKind.EXPLICIT))
        assertFalse(old.permits(intKey, list, ConversionKind.IMPLICIT))
        val updated = FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(intKey, "int", "builtin:int", TypeKind.INT),
                FrozenTypeRecord(
                    list,
                    "List<int>",
                    "builtin:list<builtin:int>",
                    TypeKind.OTHER,
                    index = IndexSchema(intKey, true)
                )
            )
        )
        assertTrue(updated.index(list)!!.writable)
        assertFalse(old.index(list)!!.writable)
        assertFalse(old.type(list).sameType(updated.type(list)))
        assertFalse(old.owns(updated.type(list)))
    }

    @Test fun graphRejectsDanglingFactsAndCannotBeImpersonatedByAnEqualPhysicalKey() {
        val missing = TypeKey(81, 99)
        val record = FrozenTypeRecord(intKey, "int", "builtin:int", TypeKind.INT)
        assertFailsWith<IllegalArgumentException> { FrozenTypeGraph.freeze(listOf(record, record)) }
        assertFailsWith<IllegalArgumentException> {
            FrozenTypeGraph.freeze(
                listOf(
                    FrozenTypeRecord(
                        intKey,
                        "list",
                        "builtin:list",
                        TypeKind.OTHER,
                        index = IndexSchema(missing, true)
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            FrozenTypeGraph.freeze(listOf(record), conversions = listOf(FrozenConversionRecord(intKey, missing, ConversionKind.EXPLICIT)))
        }
        val graph = FrozenTypeGraph.freeze(listOf(record))
        val standalone = ImmutableTypeRef<IntT>(intKey, "int", kind = TypeKind.INT)
        val forged = ImmutableTypeRef<IntT>(intKey, "int", kind = TypeKind.INT, graph = graph)
        assertFalse(graph.owns(forged))
        assertFalse(graph.type(intKey).sameType(standalone))
        assertFalse(graph.type(intKey).sameType(forged))
        assertTrue(forged.sameType(forged))
        assertFailsWith<IllegalArgumentException> { PlanType.from(forged) }
        assertFailsWith<IllegalArgumentException> {
            ImmutableTypeRef<IntT>(
                intKey,
                "int",
                fields = listOf(
                    ImmutableFieldRef(FieldKey(intKey, 1), "injected", standalone, true, span)
                ),
                kind = TypeKind.INT,
                graph = graph
            )
        }
    }

    @Test fun recursiveCallbackTypeFactsRemainFiniteAndPointIntoTheSameGraph() {
        val callback = TypeKey(81, 2)
        val parameters = arrayListOf(FrozenParameterRecord("again", callback, ParamMode.INLINE))
        val signature = FrozenInlineSignatureRecord(parameters, intKey)
        val graph = FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(intKey, "int", "builtin:int", TypeKind.INT),
                FrozenTypeRecord(callback, "Callback", "fixture:Callback", TypeKind.OTHER, inlineSignature = signature)
            )
        )
        parameters.clear()
        val descriptor = graph.type(callback).inlineSignature()!!
        assertSame(graph.type(intKey), descriptor.returnType())
        assertSame(graph.type(callback), descriptor.parameters().single().type())
        assertSame(descriptor, descriptor.parameters().single().inlineSignature())
        assertFailsWith<UnsupportedOperationException> { (descriptor.parameters() as MutableList<*>).clear() }
        val copied = ParameterView.of("callback", graph.type(callback), ParamMode.INLINE)
        assertSame(descriptor, copied.inlineSignature())
    }

    @Test fun capturedLocationsKeepRootProvenanceRelativeStepsAndSessionIdentity() {
        val root = SymbolId(session, 9)
        val components = arrayListOf<LocationStep>(LocationStep.Index(2))
        val first = NodeLocation(root, components, capturedBase = AddressId(session, 1))
        val alias = NodeLocation(root, listOf(LocationStep.Index(2)), capturedBase = AddressId(session, 1))
        val second = NodeLocation(root, listOf(LocationStep.Index(2)), capturedBase = AddressId(session, 2))
        components.clear()
        assertEquals(root, first.root)
        assertEquals(listOf(LocationStep.Index(2)), first.steps)
        assertEquals(first, alias)
        assertEquals(first.hashCode(), alias.hashCode())
        assertNotEquals(first, second)
        assertFailsWith<UnsupportedOperationException> { (first.steps as MutableList<*>).clear() }
        assertFailsWith<IllegalArgumentException> { NodeLocation(root, capturedBase = AddressId(SessionId(99), 1)) }
        assertFailsWith<IllegalArgumentException> { StmtNode.CaptureAddress(AddressId(SessionId(99), 1), first, intType) }
        assertSame(first, StmtNode.CaptureAddress(AddressId(session, 3), first, intType).location)
    }

    @Test fun readonlyArgumentsAreImmutableAndNeverBecomeOrdinaryArguments() {
        val readonly = arrayListOf(ParameterView.of("kind", intType, ParamMode.CONST))
        val function = ImmutableFunctionRef(
            FunctionKey(81, "native"),
            "native",
            FunctionSignature.of(emptyList(), intType),
            FunctionKind.NATIVE,
            readonlyParameters = readonly
        )
        val values = arrayListOf(TypedConstant(intType, ConstantValue.Integral(7), span))
        val call = ExprNode.Call(function, emptyList(), values)
        readonly.clear()
        values.clear()
        assertTrue(function.parameters().isEmpty())
        assertTrue(call.arguments.isEmpty())
        assertEquals("kind", function.readonlyParameters().single().name())
        assertEquals(ConstantValue.Integral(7), call.readonlyArguments.single().value)
        assertFailsWith<UnsupportedOperationException> { (function.readonlyParameters() as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (call.readonlyArguments as MutableList<*>).clear() }
        assertFailsWith<IllegalArgumentException> {
            ImmutableFunctionRef(
                FunctionKey(81, "macro"),
                "macro",
                FunctionSignature.of(emptyList(), intType),
                FunctionKind.MACRO,
                readonlyParameters = function.readonlyParameters()
            )
        }
    }

    @Test fun nestedPlanConstantsContainDetachedStableTypeIdentityAndNoTypeHandles() {
        val graph = FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(
                    intKey,
                    "alias",
                    "builtin:int",
                    TypeKind.INT,
                    constant = ConstantSchema.Scalar(ConstantShape.INT32)
                )
            )
        )
        val elements = arrayListOf<ConstantValue>(ConstantValue.TypeValue(graph.type(intKey)), ConstantValue.NullValue)
        val source = ConstantValue.Record(mapOf("nested" to ConstantValue.Sequence(elements)))
        val detached = assertIs<PlanConstant.Record>(PlanConstant.detach(source))
        elements.clear()
        val sequence = assertIs<PlanConstant.Sequence>(detached.fields.getValue("nested"))
        val type = assertIs<PlanConstant.TypeValue>(sequence.elements[0]).type
        assertEquals(PlanType(intKey, "alias", TypeKind.INT, "builtin:int", ConstantShape.INT32), type)
        assertSame(PlanConstant.NullValue, sequence.elements[1])
        assertFailsWith<UnsupportedOperationException> { (sequence.elements as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (detached.fields as MutableMap<*, *>).clear() }
        val inputs = arrayListOf<PlanConstant>(detached)
        val snapshot = PlanConstant.Sequence(inputs)
        inputs.clear()
        assertEquals(detached, snapshot.elements.single())
        assertEquals(PlanConstant.Integral(7), PlanConstant.detach(ConstantValue.Integral(7)))
        assertEquals(PlanConstant.FloatBits(1), PlanConstant.detach(ConstantValue.FloatBits(1)))
        assertEquals(PlanConstant.DoubleBits(2), PlanConstant.detach(ConstantValue.DoubleBits(2)))
        assertEquals(PlanConstant.Bool(true), PlanConstant.detach(ConstantValue.Bool(true)))
        assertEquals(PlanConstant.Text("x"), PlanConstant.detach(ConstantValue.Text("x")))
        assertEquals(PlanConstant.Nbt("{}"), PlanConstant.detach(ConstantValue.Nbt("{}")))
    }

    @Test fun legacyConstructorsRemainUsableAndNewPlanCollectionsAreSnapshots() {
        val location = NodeLocation(SymbolId(session, 1))
        assertNull(location.capturedBase)
        val function = ImmutableFunctionRef(FunctionKey(81, "old"), "old", FunctionSignature.of(emptyList(), intType), FunctionKind.ORDINARY)
        assertTrue(function.readonlyParameters().isEmpty())
        assertTrue(ExprNode.Call(function, emptyList()).readonlyArguments.isEmpty())
        val type = PlanType(intKey, "int", TypeKind.INT)
        val parameters = arrayListOf(PlanParameter("type", type, ParamMode.CONST))
        val target = PlanFunction(function.key, "old", PlanCallKind.ORDINARY, emptyList(), type, parameters)
        val constants = arrayListOf(PlanReadonlyArgument(type, PlanConstant.Integral(1), span))
        val site = PlanSite(span, null, emptyList())
        val call = PlanOperation.Call(null, target, emptyList(), site, constants)
        parameters.clear()
        constants.clear()
        assertEquals(1, target.readonlyParameters.size)
        assertEquals(1, call.readonlyArguments.size)
        assertFailsWith<UnsupportedOperationException> { (target.readonlyParameters as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (call.readonlyArguments as MutableList<*>).clear() }
        val base = PlanLocationId(1, type)
        val planned = PlanLocation(PlanSymbol(0, location.root), emptyList(), type, null, true, base)
        assertSame(base, planned.capturedBase)
    }

    @Test fun malformedIndexAndAddressNodesKeepTheirActualValidationErrorAndSource() {
        val arena = CodeArena(session, scope) { _, _ -> }
        val literal = ImmutableExpr(metadata, intType, ExprNode.Literal(ConstantValue.Integral(1)))
        val index = ImmutableExpr(metadata, intType, ExprNode.Index(literal, literal))
        val environment = ValidationEnvironment(arena, types = listOf(intType))
        val failure = assertFailsWith<CodeModelException> { CodeValidator().validate(index, environment, ValidationPhase.FINAL) }
        assertEquals(span, failure.source)
        assertTrue(failure.message!!.contains("Index is not supported"), failure.message)
        val capture = ImmutableStmt(metadata, StmtNode.CaptureAddress(AddressId(session, 1), NodeLocation(SymbolId(session, 1)), intType))
        val addressFailure = assertFailsWith<CodeModelException> {
            CodeValidator().validate(capture, environment, ValidationPhase.FINAL)
        }
        assertEquals(span, addressFailure.source)
        assertTrue(addressFailure.message!!.contains("no dominating declaration"), addressFailure.message)
        arena.close()
    }

    @Test fun arenaPreservesReadonlyCallsAndPrintsAndLowersTypedConstants() {
        val arena = CodeArena(session, scope) { _, _ -> }
        val graph = FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(
                    intKey,
                    "int",
                    "builtin:int",
                    TypeKind.INT,
                    constant = ConstantSchema.Scalar(ConstantShape.INT32)
                )
            )
        )
        val typedInt = graph.type(intKey)
        val parameter = ParameterView.of("constant", typedInt, ParamMode.CONST)
        val value = TypedConstant(typedInt, ConstantValue.Integral(7), span)
        fun rejects(category: String, action: () -> Unit) {
            val failure = assertFailsWith<CodeModelException> { action() }
            assertEquals(span, failure.source)
            assertTrue(failure.message!!.contains(category, ignoreCase = true), failure.message)
        }
        for ((index, kind) in listOf(FunctionKind.ORDINARY, FunctionKind.NATIVE).withIndex()) {
            val prefix = kind.name.lowercase()
            val payloadFunction = ImmutableFunctionRef(
                FunctionKey(81, "$prefix-readonly-payload"),
                "$prefix-readonly-payload",
                FunctionSignature.of(emptyList(), typedInt),
                kind
            )
            val signatureFunction = ImmutableFunctionRef(
                FunctionKey(81, "$prefix-readonly-signature"),
                "$prefix-readonly-signature",
                FunctionSignature.of(emptyList(), typedInt),
                kind,
                readonlyParameters = listOf(parameter)
            )
            val payloadCall = ExprNode.Call(payloadFunction, emptyList(), listOf(value))
            val payloadExpr = ImmutableExpr(metadata, typedInt, payloadCall)
            val signatureCall = ExprNode.Call(signatureFunction, emptyList())
            val signatureExpr = ImmutableExpr(metadata, typedInt, signatureCall)
            val environment = ValidationEnvironment(arena, functions = listOf(payloadFunction, signatureFunction))

            val arenaPayloadExpr = arena.expr(metadata, typedInt, payloadCall)
            val arenaSignatureExpr = arena.expr(metadata, typedInt, signatureCall)
            arena.stmt(metadata, StmtNode.Eval(arenaPayloadExpr))
            arena.stmt(metadata, StmtNode.Eval(arenaSignatureExpr))
            fun readonlyValue(expr: Expr<*>): ConstantValue = ((expr as ImmutableExpr<*>).node as ExprNode.Call).readonlyArguments.single().value
            fun signatureNames(expr: Expr<*>): List<String> = ((expr as ImmutableExpr<*>).node as ExprNode.Call).function.readonlyParameters().map { it.name() }
            assertEquals(value.value, readonlyValue(arenaPayloadExpr))
            assertEquals(listOf("constant"), signatureNames(arenaSignatureExpr))
            val importedPayload = arena.importTree(arenaPayloadExpr, scope)
            val importedSignature = arena.importTree(arenaSignatureExpr, scope)
            assertEquals(value.value, readonlyValue(importedPayload))
            assertEquals(listOf("constant"), signatureNames(importedSignature))
            val childSession = SessionId(82L + index)
            val childArena = arena.childArena(childSession, ScopeId(childSession, 0))
            val childPayload = childArena.importChildTree(arena, arenaPayloadExpr, childArena.rootScope)
            val childSignature = childArena.importChildTree(arena, arenaSignatureExpr, childArena.rootScope)
            assertEquals(value.value, readonlyValue(childPayload))
            assertEquals(listOf("constant"), signatureNames(childSignature))
            childArena.close()
            for (phase in ValidationPhase.entries) {
                // These two fixtures intentionally have opposite arity errors, rather than valid calls.
                rejects("readonly argument count") { CodeValidator().validate(payloadExpr, environment, phase) }
                rejects("readonly argument count") { CodeValidator().validate(signatureExpr, environment, phase) }
            }
            val complete = arena.expr(metadata, typedInt, ExprNode.Call(signatureFunction, emptyList(), listOf(value)))
            val proof = CodeValidator().validate(complete, environment, ValidationPhase.FINAL) as ValidatedCode
            val plan = CodeLowerer().lower(proof, LoweringTarget(FunctionKey(81, "target")))
            val plannedCall = plan.blocks.flatMap { it.operations }.filterIsInstance<PlanOperation.Call>().single()
            assertEquals(1, plannedCall.readonlyArguments.size)
            assertEquals(intKey, plannedCall.readonlyArguments.single().type.key)
            assertEquals(ConstantShape.INT32, plannedCall.readonlyArguments.single().type.scalarShape)
            assertEquals(PlanConstant.Integral(7), plannedCall.readonlyArguments.single().value)
            assertTrue(CodePrinter().print(complete).contains("readonly("))
            assertTrue(CodePrinter().print(signatureExpr).contains("readonly<"))
        }

        // Empty readonly metadata and arguments remain valid for existing native providers.
        val legacy = ImmutableFunctionRef(
            FunctionKey(81, "legacy"),
            "legacy",
            FunctionSignature.of(emptyList(), intType),
            FunctionKind.NATIVE
        )
        val legacyCall = ExprNode.Call(legacy, emptyList())
        val legacyExpr = arena.expr(metadata, intType, legacyCall)
        val legacyProof = CodeValidator().validate(
            legacyExpr,
            ValidationEnvironment(arena, functions = listOf(legacy)),
            ValidationPhase.FINAL
        ) as ValidatedCode
        assertTrue(CodePrinter().print(legacyExpr).contains("legacy"))
        val legacyPlan = CodeLowerer().lower(legacyProof, LoweringTarget(FunctionKey(81, "target")))
        assertEquals(PlanExitKind.EXPRESSION, (legacyPlan.blocks.single().terminator as PlanTerminator.Exit).kind)
        arena.close()
    }

    @Test fun validIndexAndCaptureTreesPrintAndLowerWithFrozenTypes() {
        val arena = CodeArena(session, scope) { _, _ -> }
        val graph = FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(intKey, "int", "int", TypeKind.INT, constant = ConstantSchema.Scalar(ConstantShape.INT32)),
                FrozenTypeRecord(TypeKey(81, 2), "List<int>", "list:int", TypeKind.OTHER, index = IndexSchema(intKey, true))
            )
        )
        val root = arena.ref(metadata, graph.type(TypeKey(81, 2)), NodeLocation(arena.freshSymbol()))
        val integer = arena.expr(metadata, graph.type(intKey), ExprNode.Literal(ConstantValue.Integral(0)))
        val index = arena.expr(metadata, graph.type(intKey), ExprNode.Index(root.load(), integer))
        val address = arena.freshAddress()
        val capture = arena.block(
            metadata,
            listOf(
                arena.stmt(
                    metadata,
                    StmtNode.CaptureAddress(
                        address,
                        NodeLocation(root.location.root, listOf(LocationStep.Index(0))),
                        graph.type(intKey)
                    )
                )
            )
        )
        val environment = ValidationEnvironment(arena, externalBindings = listOf(root))
        for (code in listOf<Code>(index, capture)) {
            val proof = CodeValidator().validate(code, environment, ValidationPhase.FINAL) as ValidatedCode
            val printed = CodePrinter().print(code)
            assertTrue(printed.contains(if (code === index) "index" else "capture"), printed)
            val plan = CodeLowerer().lower(proof, LoweringTarget(FunctionKey(81, "target")))
            val operations = plan.blocks.flatMap { it.operations }
            if (code === index) {
                val indexed = operations.filterIsInstance<PlanOperation.Index>().single()
                assertEquals(intKey, indexed.result.type.key)
                val literal = operations.filterIsInstance<PlanOperation.Literal>().single()
                assertEquals(PlanConstant.Integral(0), literal.value)
                assertEquals(literal.result, indexed.index)
            } else {
                val captured = operations.filterIsInstance<PlanOperation.CaptureLocation>().single()
                assertEquals(root.location.root, captured.location.root.binding)
                assertEquals(listOf(PlanLocationStep.Index(0)), captured.location.steps)
                assertEquals(intKey, captured.location.type.key)
                assertNull(captured.location.capturedBase)
            }
        }
        arena.close()
    }

    @Test fun consumersRejectUnregisteredCapturedBasesButKeepOrdinaryRootLocations() {
        val arena = CodeArena(session, scope) { _, _ -> }
        val root = SymbolId(session, 19)
        val captured = NodeLocation(root, capturedBase = AddressId(session, 1))
        val malformed = ImmutableExpr(metadata, intType, ExprNode.Read(captured))
        val binding = ImmutableRef(metadata, intType, NodeLocation(root))
        val environment = ValidationEnvironment(arena, externalBindings = listOf(binding))
        fun rejects(category: String, action: () -> Unit) {
            val failure = assertFailsWith<CodeModelException> { action() }
            assertEquals(span, failure.source)
            assertTrue(failure.message!!.contains(category, ignoreCase = true), failure.message)
        }

        rejects("captured address") { arena.ref(metadata, intType, captured) }
        rejects("captured address") {
            arena.expr(metadata, intType, ExprNode.Read(captured))
        }
        rejects("captured address") { arena.stmt(metadata, StmtNode.Eval(malformed)) }
        rejects("captured address") { arena.importTree(malformed, scope) }
        val childSession = SessionId(83)
        val childArena = arena.childArena(childSession, ScopeId(childSession, 0))
        rejects("captured address") {
            childArena.importChildTree(arena, malformed, childArena.rootScope)
        }
        childArena.close()
        for (phase in ValidationPhase.entries) {
            rejects("captured address") {
                CodeValidator().validate(malformed, environment, phase)
            }
        }
        assertTrue(CodePrinter().print(malformed).contains("address a0"))

        val ordinary = arena.expr(metadata, intType, ExprNode.Read(NodeLocation(root)))
        val valid = CodeValidator().validate(ordinary, environment, ValidationPhase.FINAL) as ValidatedCode
        assertTrue(CodePrinter().print(ordinary).contains("read"))
        assertEquals(
            PlanExitKind.EXPRESSION,
            CodeLowerer().lower(valid, LoweringTarget(FunctionKey(81, "target"))).blocks.single().terminator
                .let { (it as PlanTerminator.Exit).kind }
        )
        arena.close()
    }
}
