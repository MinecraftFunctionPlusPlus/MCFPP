package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniProjectionValidationTest {
    private class Fixture {
        val span = SourceSpan("projection-validation.mcfpp", 12, 24, 2, 3, 2, 15)
        val proofSpan = SourceSpan("projection-validation.mcfpp", 18, 21, 2, 9, 2, 12)
        val arena = CodeArena(SessionId(901), ScopeId(SessionId(901), 0)) { _, _ -> }
        val root = arena.rootScope
        fun key(value: Long) = TypeKey(901, value)
        val valueField = FieldKey(key(12), 1)
        val nextField = FieldKey(key(12), 2)
        val readonlyField = FieldKey(key(12), 3)
        val listField = FieldKey(key(12), 4)
        val graph = FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(key(1), "int", "int", TypeKind.INT, constant = ConstantSchema.Scalar(ConstantShape.INT32)),
                FrozenTypeRecord(key(2), "bool", "bool", TypeKind.BOOL, constant = ConstantSchema.Scalar(ConstantShape.BOOL)),
                FrozenTypeRecord(key(3), "float", "float", TypeKind.FLOAT, constant = ConstantSchema.Scalar(ConstantShape.FLOAT32)),
                FrozenTypeRecord(key(4), "double", "double", TypeKind.FLOAT, constant = ConstantSchema.Scalar(ConstantShape.FLOAT64)),
                FrozenTypeRecord(key(5), "string", "string", TypeKind.STRING, constant = ConstantSchema.Scalar(ConstantShape.STRING)),
                FrozenTypeRecord(key(6), "void", "void", TypeKind.VOID),
                FrozenTypeRecord(key(7), "type", "type", TypeKind.OTHER, constant = ConstantSchema.Scalar(ConstantShape.TYPE)),
                FrozenTypeRecord(key(8), "null", "null", TypeKind.OTHER, constant = ConstantSchema.Scalar(ConstantShape.NULL)),
                FrozenTypeRecord(key(9), "nbt", "nbt", TypeKind.OTHER, constant = ConstantSchema.Scalar(ConstantShape.NBT)),
                FrozenTypeRecord(
                    key(10),
                    "List<int>",
                    "list:int",
                    TypeKind.OTHER,
                    index = IndexSchema(key(1), true),
                    constant = ConstantSchema.Sequence(key(1))
                ),
                FrozenTypeRecord(
                    key(11),
                    "ReadonlyList<int>",
                    "readonly-list:int",
                    TypeKind.OTHER,
                    index = IndexSchema(key(1), false)
                ),
                FrozenTypeRecord(
                    key(12),
                    "Node",
                    "node",
                    TypeKind.OTHER,
                    acceptedSources = setOf(key(13)),
                    fields = listOf(valueField, nextField, readonlyField, listField)
                ),
                FrozenTypeRecord(
                    key(13),
                    "Child",
                    "child",
                    TypeKind.OTHER,
                    fields = listOf(valueField, nextField, readonlyField, listField)
                ),
                FrozenTypeRecord(key(14), "Types", "types", TypeKind.OTHER, constant = ConstantSchema.Sequence(key(7))),
                FrozenTypeRecord(key(15), "Proof", "proof", TypeKind.OTHER, constant = ConstantSchema.Record(mapOf("types" to key(14))))
            ),
            listOf(
                FrozenFieldRecord(valueField, "value", key(1), true, span),
                FrozenFieldRecord(nextField, "next", key(12), true, span),
                FrozenFieldRecord(readonlyField, "fixed", key(1), false, span),
                FrozenFieldRecord(listField, "values", key(10), true, span)
            ),
            listOf(
                FrozenConversionRecord(key(1), key(3), ConversionKind.IMPLICIT),
                FrozenConversionRecord(key(3), key(1), ConversionKind.EXPLICIT),
                FrozenConversionRecord(key(13), key(12), ConversionKind.IMPLICIT)
            )
        )
        val int get() = graph.type(key(1))
        val bool get() = graph.type(key(2))
        val float get() = graph.type(key(3))
        val string get() = graph.type(key(5))
        val list get() = graph.type(key(10))
        val node get() = graph.type(key(12))
        fun meta(scope: ScopeId = root, context: ExecutionContextId? = null) = arena.metadata(scope, Origin(span), context)
        fun expr(type: TypeRef<*>, node: ExprNode, scope: ScopeId = root, context: ExecutionContextId? = null) = ImmutableExpr(meta(scope, context), type, node)
        fun integer(value: Long = 1, scope: ScopeId = root) = expr(int, ExprNode.Literal(ConstantValue.Integral(value)), scope)
        fun boolean(scope: ScopeId = root) = expr(bool, ExprNode.Literal(ConstantValue.Bool(true)), scope)
        fun stmt(node: StmtNode, scope: ScopeId = root, context: ExecutionContextId? = null) = ImmutableStmt(meta(scope, context), node)
        fun block(vararg nodes: StmtNode, scope: ScopeId = root, context: ExecutionContextId? = null) = ImmutableBlock(meta(scope, context), nodes.map { stmt(it, scope, context) })
        fun storage(type: TypeRef<*> = list, writable: Boolean = true, context: ExecutionContextId? = null) = arena.ref(meta(context = context), type, NodeLocation(arena.freshSymbol(), executionContextId = context, writable = writable))
        fun reference(type: TypeRef<*>, location: NodeLocation, scope: ScopeId = root, context: ExecutionContextId? = null) = ImmutableRef(meta(scope, context), type, location)
        fun read(type: TypeRef<*>, location: NodeLocation, scope: ScopeId = root, context: ExecutionContextId? = null) = expr(type, ExprNode.Read(location), scope, context)
        fun address(value: Long) = AddressId(arena.sessionId, value)
        fun capture(id: AddressId, location: NodeLocation, type: TypeRef<*> = int) = StmtNode.CaptureAddress(id, location, type)
        fun selected(base: Ref<*>, id: AddressId, steps: List<LocationStep> = emptyList(), writable: Boolean = true) = NodeLocation((base as ImmutableRef<*>).location.root, steps, writable = writable, capturedBase = id)
        fun environment(
            bindings: List<Ref<*>> = emptyList(),
            functions: List<FunctionRef<*>> = emptyList(),
            fields: Set<FieldKey>? = null
        ) = ValidationEnvironment(arena, externalBindings = bindings, functions = functions, fieldCapabilities = fields)
        fun check(code: Code, environment: ValidationEnvironment = environment()) {
            for (phase in ValidationPhase.entries) {
                val result = CodeValidator().validate(code, environment, phase)
                assertSame(code, result.code)
            }
        }
        fun rejects(code: Code, category: String, environment: ValidationEnvironment = environment(), source: SourceSpan = span) {
            for (phase in ValidationPhase.entries) {
                val failure = assertFailsWith<CodeModelException> { CodeValidator().validate(code, environment, phase) }
                assertEquals(source, failure.source)
                assertTrue(failure.message!!.contains(category, ignoreCase = true), failure.message)
            }
        }
        fun function(
            name: String = "native",
            readonly: List<ParameterView> = emptyList(),
            normal: List<ParameterView> = emptyList(),
            kind: FunctionKind = FunctionKind.NATIVE
        ) = ImmutableFunctionRef(
            FunctionKey(901, name),
            name,
            FunctionSignature.of(normal, int),
            kind,
            readonlyParameters = readonly
        )
    }

    @Test fun approvedConversionsAndStringAdditionUseFrozenFacts() {
        val f = Fixture()
        val converted = f.expr(f.float, ExprNode.Convert(f.integer(), f.float, ConversionKind.IMPLICIT))
        f.check(converted)
        val text = f.expr(f.string, ExprNode.Literal(ConstantValue.Text("left")))
        f.check(f.expr(f.string, ExprNode.Binary(BinaryOperator.ADD, text, text)))
        f.rejects(f.expr(f.string, ExprNode.Binary(BinaryOperator.SUBTRACT, text, text)), "Operator")
        f.rejects(f.expr(f.int, ExprNode.Convert(converted, f.int, ConversionKind.IMPLICIT)), "Unsupported actual type conversion")
        f.check(f.expr(f.int, ExprNode.Convert(converted, f.int, ConversionKind.EXPLICIT)))
        val child = f.storage(f.graph.type(f.key(13)))
        f.check(f.expr(f.node, ExprNode.Convert(child.load(), f.node, ConversionKind.IMPLICIT)), f.environment(listOf(child)))
        f.rejects(f.expr(f.node, ExprNode.Convert(f.integer(), f.node, ConversionKind.EXPLICIT)), "Unsupported actual type conversion")
    }

    @Test fun recursiveCanonicalFieldsAndCallerCapabilitiesAreBothRequired() {
        val f = Fixture()
        val root = f.storage(f.graph.type(f.key(13)))
        val next = f.expr(f.node, ExprNode.Field(root.load(), f.graph.field(f.nextField)))
        val value = f.expr(f.int, ExprNode.Field(next, f.graph.field(f.valueField)))
        f.check(value, f.environment(listOf(root), fields = setOf(f.nextField, f.valueField)))
        f.rejects(value, "not authorized", f.environment(listOf(root), fields = setOf(f.valueField)))
        val forged = ImmutableFieldRef(f.valueField, "value", f.int, true, f.span)
        f.rejects(f.expr(f.int, ExprNode.Field(root.load(), forged)), "canonical frozen", f.environment(listOf(root)))
        val unrelated = ImmutableFieldRef(FieldKey(f.key(10), 99), "value", f.int, true, f.span)
        f.rejects(f.expr(f.int, ExprNode.Field(root.load(), unrelated)), "does not belong", f.environment(listOf(root)))
    }

    @Test fun indexResultsAndIndicesRequireTheActualSchemaAndCanonicalDomain() {
        val f = Fixture()
        val root = f.storage()
        f.check(f.expr(f.int, ExprNode.Index(root.load(), f.integer())), f.environment(listOf(root)))
        f.rejects(f.expr(f.int, ExprNode.Index(root.load(), f.boolean())), "Index requires actual int", f.environment(listOf(root)))
        f.rejects(f.expr(f.bool, ExprNode.Index(root.load(), f.integer())), "Actual type mismatch", f.environment(listOf(root)))
        f.rejects(f.expr(f.int, ExprNode.Index(f.integer(), f.integer())), "not supported")
        val other = Fixture()
        f.rejects(f.expr(f.int, ExprNode.Index(root.load(), other.integer())), "another frozen domain", f.environment(listOf(root)))
    }

    @Test fun deepConstantsRespectEverySchemaIncludingTypeNullAndFloatingWidth() {
        val f = Fixture()
        val proof = ConstantValue.Record(mapOf("types" to ConstantValue.Sequence(listOf(ConstantValue.TypeValue(f.node)))))
        f.check(f.expr(f.graph.type(f.key(15)), ExprNode.Literal(proof)))
        assertSame(f.node, f.node.fields().first { it.name() == "next" }.type())
        f.check(f.expr(f.graph.type(f.key(8)), ExprNode.Literal(ConstantValue.NullValue)))
        f.check(f.expr(f.graph.type(f.key(9)), ExprNode.Literal(ConstantValue.Nbt("{value:1}"))))
        f.check(f.expr(f.float, ExprNode.Literal(ConstantValue.FloatBits(0))))
        f.check(f.expr(f.graph.type(f.key(4)), ExprNode.Literal(ConstantValue.DoubleBits(0))))
        f.check(f.expr(f.list, ExprNode.Literal(ConstantValue.Sequence(listOf(ConstantValue.Integral(3))))))
        f.rejects(f.expr(f.int, ExprNode.Literal(ConstantValue.Integral(Int.MAX_VALUE.toLong() + 1))), "Literal payload")
        f.rejects(f.expr(f.int, ExprNode.Literal(ConstantValue.NullValue)), "Literal payload")
        f.rejects(f.expr(f.float, ExprNode.Literal(ConstantValue.DoubleBits(0))), "Literal payload")
        f.rejects(f.expr(f.list, ExprNode.Literal(ConstantValue.Sequence(listOf(ConstantValue.Bool(true))))), "Literal payload")
        f.rejects(f.expr(f.graph.type(f.key(15)), ExprNode.Literal(ConstantValue.Record(emptyMap()))), "record fields")
        f.rejects(f.expr(f.node, ExprNode.Literal(ConstantValue.Record(emptyMap()))), "no supported actual type schema")
        f.rejects(f.expr(f.graph.type(f.key(9)), ExprNode.Literal(ConstantValue.Nbt("{"))), "NBT payload is malformed")
    }

    @Test fun sameKeyForgeriesAndForeignTypesInsideConstantGraphsAreRejected() {
        val f = Fixture()
        val forged = ImmutableTypeRef<Any>(f.node.key, f.node.name(), kind = f.node.kind, graph = f.graph)
        val type = f.graph.type(f.key(7))
        f.rejects(f.expr(type, ExprNode.Literal(ConstantValue.TypeValue(forged))), "canonical frozen")
        val other = Fixture()
        val nested = ConstantValue.Record(mapOf("types" to ConstantValue.Sequence(listOf(ConstantValue.TypeValue(other.node)))))
        f.rejects(f.expr(f.graph.type(f.key(15)), ExprNode.Literal(nested)), "another frozen domain")
        f.rejects(ImmutableExpr(f.meta(), forged, ExprNode.Read(NodeLocation(f.arena.freshSymbol()))), "canonical frozen")
    }

    @Test fun readonlyCallsKeepCompleteTypedValuesForOrdinaryAndNativeTargets() {
        val f = Fixture()
        for (kind in listOf(FunctionKind.ORDINARY, FunctionKind.NATIVE)) {
            val function = f.function(
                kind.name,
                readonly = listOf(
                    ParameterView.of("n", f.int, ParamMode.VALUE),
                    ParameterView.of("t", f.graph.type(f.key(7)), ParamMode.CONST)
                ),
                normal = listOf(ParameterView.of("value", f.int, ParamMode.VALUE)),
                kind = kind
            )
            val call = f.expr(
                f.int,
                ExprNode.Call(
                    function,
                    listOf(CallArgument.value(f.integer())),
                    listOf(
                        TypedConstant(f.int, ConstantValue.Integral(7), f.proofSpan),
                        TypedConstant(f.graph.type(f.key(7)), ConstantValue.TypeValue(f.node), f.proofSpan)
                    )
                )
            )
            f.check(call, f.environment(functions = listOf(function)))
        }
    }

    @Test fun readonlyArityAndPayloadFailuresKeepTheOffendingArgumentSpan() {
        val f = Fixture()
        val function = f.function(readonly = listOf(ParameterView.of("n", f.int, ParamMode.CONST)))
        val env = f.environment(functions = listOf(function))
        f.rejects(f.expr(f.int, ExprNode.Call(function, emptyList())), "Readonly argument count", env)
        val wrong = TypedConstant(f.int, ConstantValue.Bool(true), f.proofSpan)
        f.rejects(f.expr(f.int, ExprNode.Call(function, emptyList(), listOf(wrong))), "Literal payload", env, f.proofSpan)
        val extra = TypedConstant(f.int, ConstantValue.Integral(1), f.proofSpan)
        f.rejects(f.expr(f.int, ExprNode.Call(function, emptyList(), listOf(extra, extra))), "Readonly argument count", env, f.proofSpan)
        val badType = TypedConstant(f.bool, ConstantValue.Bool(true), f.proofSpan)
        f.rejects(f.expr(f.int, ExprNode.Call(function, emptyList(), listOf(badType))), "Actual type mismatch", env, f.proofSpan)
    }

    @Test fun sameKeyFunctionsCannotChangeReadonlyMetadataOrUseRuntimeModes() {
        val f = Fixture()
        val canonical = f.function(readonly = listOf(ParameterView.of("n", f.int, ParamMode.CONST)))
        val forged = f.function(readonly = listOf(ParameterView.of("n", f.bool, ParamMode.CONST)))
        f.rejects(
            f.expr(f.int, ExprNode.Call(forged, emptyList(), listOf(TypedConstant(f.bool, ConstantValue.Bool(true), f.proofSpan)))),
            "Actual type mismatch",
            f.environment(functions = listOf(canonical))
        )
        val mode = f.function(readonly = listOf(ParameterView.of("n", f.int, ParamMode.VALUE)))
        f.rejects(f.expr(f.int, ExprNode.Call(mode, emptyList())), "mode differs", f.environment(functions = listOf(canonical)))
        for (parameter in listOf(
            ParameterView.of("n", f.int, ParamMode.REF),
            ParameterView.of("n", f.int, ParamMode.INLINE, FunctionSignature.of(emptyList(), f.int))
        )) {
            val function = f.function(parameter.mode().name, readonly = listOf(parameter))
            f.rejects(
                f.expr(f.int, ExprNode.Call(function, emptyList(), listOf(TypedConstant(f.int, ConstantValue.Integral(1), f.proofSpan)))),
                "closed value mode",
                f.environment(functions = listOf(function)),
                f.proofSpan
            )
        }
        for (kind in listOf(FunctionKind.HELPER, FunctionKind.MACRO)) {
            val failure = assertFailsWith<IllegalArgumentException> {
                f.function(kind.name, readonly = canonical.readonlyParameters(), kind = kind)
            }
            assertEquals("Macro and helper functions have no readonly parameters", failure.message)
        }
    }

    @Test fun captureStagesValidateDynamicIndicesAndPermitRelativeCanonicalFields() {
        val f = Fixture()
        val root = f.storage(f.node)
        val index = f.arena.local(f.meta(), f.int, "index")
        val first = f.address(1)
        val second = f.address(2)
        val finalAddress = f.address(3)
        val listLocation = f.selected(root, first, listOf(LocationStep.Field(f.graph.field(f.listField))))
        val element = f.selected(root, second, listOf(LocationStep.CapturedIndex(index.symbol)))
        val destination = f.reference(f.int, f.selected(root, finalAddress))
        val body = f.block(
            f.capture(first, root.location, f.node),
            f.capture(second, listLocation, f.list),
            StmtNode.LocalDecl(index, f.integer()),
            f.capture(finalAddress, element),
            StmtNode.Assign(destination, destination.load())
        )
        f.check(body, f.environment(listOf(root), fields = setOf(f.listField)))
        assertSame(destination.load(), (body.statements().last() as ImmutableStmt).node.let { (it as StmtNode.Assign).expression })
    }

    @Test fun capturedAddressUsesRequireEarlierDeclarationsAndMatchingSourceType() {
        val f = Fixture()
        val root = f.storage()
        val id = f.address(1)
        val selected = f.read(f.int, f.selected(root, id))
        val declaration = f.capture(id, NodeLocation(root.location.root, listOf(LocationStep.Index(0))))
        f.rejects(f.block(StmtNode.Eval(selected), declaration), "no dominating declaration", f.environment(listOf(root)))
        f.rejects(f.block(f.capture(id, f.selected(root, id))), "no dominating declaration", f.environment(listOf(root)))
        f.rejects(f.block(declaration, declaration), "Duplicate captured", f.environment(listOf(root)))
        f.rejects(f.block(f.capture(id, root.location, f.bool)), "Actual type mismatch", f.environment(listOf(root)))
        val foreignSession = SessionId(999)
        val foreign = f.capture(AddressId(foreignSession, 1), NodeLocation(SymbolId(foreignSession, 1)))
        f.rejects(f.block(foreign), "another session", f.environment(listOf(root)))
    }

    @Test fun branchLoopAndNestedBlockAddressesDoNotEscapeTheirControlPaths() {
        val f = Fixture()
        val root = f.storage()
        val id = f.address(1)
        val declaration = f.capture(id, NodeLocation(root.location.root, listOf(LocationStep.Index(0))))
        val selected = f.read(f.int, f.selected(root, id))
        // Even equal lexical scopes do not turn branch declarations into dominating capabilities.
        val branch = f.block(declaration)
        f.rejects(
            f.block(StmtNode.If(f.boolean(), branch, null), StmtNode.Eval(selected)),
            "no dominating declaration",
            f.environment(listOf(root))
        )
        f.rejects(
            f.block(StmtNode.If(f.boolean(), branch, f.block(StmtNode.Eval(selected)))),
            "no dominating declaration",
            f.environment(listOf(root))
        )
        f.rejects(
            f.block(StmtNode.ScopeBlock(branch), StmtNode.Eval(selected)),
            "no dominating declaration",
            f.environment(listOf(root))
        )
        val region = f.arena.freshRegion()
        val loop = f.block(declaration, StmtNode.Eval(selected), StmtNode.Break(region))
        f.check(f.block(StmtNode.While(f.boolean(), loop, region)), f.environment(listOf(root)))
        f.rejects(
            f.block(StmtNode.While(f.boolean(), loop, region), StmtNode.Eval(selected)),
            "no dominating declaration",
            f.environment(listOf(root))
        )
    }

    @Test fun capturedAddressesCannotChangeRootContextOrWritableCapability() {
        val f = Fixture()
        val root = f.storage(writable = false)
        val other = f.storage()
        val id = f.address(1)
        val source = NodeLocation(root.location.root, listOf(LocationStep.Index(0)), writable = false)
        val declaration = f.capture(id, source)
        f.check(f.block(declaration, StmtNode.Eval(f.read(f.int, f.selected(root, id, writable = false)))), f.environment(listOf(root)))
        f.rejects(
            f.block(declaration, StmtNode.Eval(f.read(f.int, f.selected(root, id)))),
            "widens a read-only",
            f.environment(listOf(root))
        )
        f.rejects(
            f.block(declaration, StmtNode.Eval(f.read(f.int, f.selected(other, id, writable = false)))),
            "provenance",
            f.environment(listOf(root, other))
        )
        val first = f.arena.freshExecutionContext()
        val second = f.arena.freshExecutionContext()
        val storage = f.storage(f.int, context = first)
        val capture = f.capture(id, storage.location)
        val changed = NodeLocation(storage.location.root, executionContextId = second, capturedBase = id)
        f.rejects(
            f.block(capture, StmtNode.Eval(f.read(f.int, changed, context = first)), context = first),
            "different execution context",
            f.environment(listOf(storage))
        )
        val forged = f.reference(f.int, f.selected(root, id, writable = false))
        f.rejects(forged.load(), "cannot be registered", f.environment(listOf(forged)))
    }

    @Test fun locationIndexSchemasAndCapturedComponentsCannotBeBypassedByExternalBindings() {
        val f = Fixture()
        val root = f.storage()
        val missing = f.arena.freshSymbol()
        val path = NodeLocation(root.location.root, listOf(LocationStep.CapturedIndex(missing)))
        f.rejects(f.read(f.int, path), "component has no dominating binding", f.environment(listOf(root)))
        val wrong = f.storage(f.bool)
        val badPath = NodeLocation(root.location.root, listOf(LocationStep.CapturedIndex(wrong.location.root)))
        f.rejects(f.read(f.int, badPath), "component has the wrong actual type", f.environment(listOf(root, wrong)))
        val context = f.arena.freshExecutionContext()
        val contextualIndex = f.storage(f.int, context = context)
        val movedIndex = NodeLocation(root.location.root, listOf(LocationStep.CapturedIndex(contextualIndex.location.root)))
        f.rejects(f.read(f.int, movedIndex), "component belongs to another execution context", f.environment(listOf(root, contextualIndex)))
        val forged = f.reference(f.int, NodeLocation(f.arena.freshSymbol(), listOf(LocationStep.Index(0))))
        f.rejects(forged.load(), "no dominating declaration", f.environment(listOf(forged)))
        val scalar = f.storage(f.int)
        val invalid = f.reference(f.int, NodeLocation(scalar.location.root, listOf(LocationStep.Index(0))))
        f.rejects(invalid.load(), "not supported", f.environment(listOf(scalar, invalid)))
        val valid = f.reference(f.int, NodeLocation(root.location.root, listOf(LocationStep.Index(0))))
        f.check(valid.load(), f.environment(listOf(root, valid)))
        f.rejects(f.read(f.int, NodeLocation(root.location.root, listOf(LocationStep.Key("zero")))), "Keyed location", f.environment(listOf(root)))
    }

    @Test fun readonlySequenceAndFieldsCannotBeCapturedAsWritableOrPassedByRef() {
        val f = Fixture()
        val root = f.storage(f.graph.type(f.key(11)))
        val id = f.address(1)
        val readonly = NodeLocation(root.location.root, listOf(LocationStep.Index(0)), writable = false)
        f.check(f.block(f.capture(id, readonly), StmtNode.Eval(f.read(f.int, f.selected(root, id, writable = false)))), f.environment(listOf(root)))
        f.rejects(
            f.block(f.capture(id, NodeLocation(root.location.root, listOf(LocationStep.Index(0))))),
            "widens a read-only",
            f.environment(listOf(root))
        )
        val objectRoot = f.storage(f.node)
        val path = NodeLocation(objectRoot.location.root, listOf(LocationStep.Field(f.graph.field(f.readonlyField))), writable = false)
        val function = f.function("write", normal = listOf(ParameterView.of("r", f.int, ParamMode.REF)), kind = FunctionKind.HELPER)
        val ref = f.reference(f.int, f.selected(objectRoot, id, writable = false))
        val call = f.expr(f.int, ExprNode.Call(function, listOf(CallArgument.ref(ref))))
        f.rejects(f.block(f.capture(id, path), StmtNode.Eval(call)), "read-only", f.environment(listOf(objectRoot), listOf(function)))
    }

    @Test fun capturedIndexLocalsMustDominateAndInlineBodiesDoNotInheritUncapturedAddresses() {
        val f = Fixture()
        val root = f.storage()
        val local = f.arena.local(f.meta(), f.int, "later")
        val id = f.address(1)
        val location = NodeLocation(root.location.root, listOf(LocationStep.CapturedIndex(local.symbol)))
        f.rejects(
            f.block(f.capture(id, location), StmtNode.LocalDecl(local, f.integer())),
            "component has no dominating binding",
            f.environment(listOf(root))
        )
        val bodyScope = f.arena.childScope(f.root)
        val region = f.arena.freshRegion()
        val body = f.block(StmtNode.CallbackReturn(region, f.read(f.int, f.selected(root, id), bodyScope)), scope = bodyScope)
        val callback = ImmutableInlineFn<Any>(
            f.meta(),
            FunctionSignature.of(emptyList(), f.int),
            CallbackId(901, "cb"),
            InlineTarget.Body(emptyList(), body, emptySet(), region)
        )
        val call = f.expr(f.int, ExprNode.InlineCall(callback, emptyList()))
        val tree = f.block(f.capture(id, NodeLocation(root.location.root, listOf(LocationStep.Index(0)))), StmtNode.Eval(call))
        // PRE_EXPANSION checks the body before recursive expansion; FINAL rejects the surviving InlineCall.
        val failure = assertFailsWith<CodeModelException> {
            CodeValidator().validate(tree, f.environment(listOf(root)), ValidationPhase.PRE_EXPANSION)
        }
        assertEquals(f.span, failure.source)
        assertTrue(failure.message!!.contains("no dominating declaration"), failure.message)
    }

    @Test fun aSharedInlineBodyValidatesEachCaptureInAnIndependentInvocationState() {
        val f = Fixture()
        val root = f.storage()
        val id = f.address(1)
        val bodyScope = f.arena.childScope(f.root)
        val region = f.arena.freshRegion()
        val body = f.block(
            f.capture(id, NodeLocation(root.location.root, listOf(LocationStep.Index(0)))),
            StmtNode.CallbackReturn(region, f.read(f.int, f.selected(root, id), bodyScope)),
            scope = bodyScope
        )
        val callback = ImmutableInlineFn<Any>(
            f.meta(),
            FunctionSignature.of(emptyList(), f.int),
            CallbackId(901, "shared"),
            InlineTarget.Body(emptyList(), body, setOf(root.location.root), region)
        )
        val first = f.expr(f.int, ExprNode.InlineCall(callback, emptyList()))
        val second = f.expr(f.int, ExprNode.InlineCall(callback, emptyList()))
        val tree = f.block(StmtNode.Eval(first), StmtNode.Eval(second))
        val environment = f.environment(listOf(root))
        assertSame(tree, CodeValidator().validate(tree, environment, ValidationPhase.PRE_EXPANSION).code)

        // Body-local address declarations from either invocation must not leak into the caller.
        val escaped = f.block(StmtNode.Eval(first), StmtNode.Eval(second), StmtNode.Eval(f.read(f.int, f.selected(root, id))))
        val failure = assertFailsWith<CodeModelException> {
            CodeValidator().validate(escaped, environment, ValidationPhase.PRE_EXPANSION)
        }
        assertEquals(f.span, failure.source)
        assertTrue(failure.message!!.contains("no dominating declaration"), failure.message)
    }

    @Test fun distinctCaptureStatementsCannotReuseAnAddressEvenOnSeparateBranches() {
        val f = Fixture()
        val root = f.storage()
        val id = f.address(1)
        val source = NodeLocation(root.location.root, listOf(LocationStep.Index(0)))
        val first = f.block(f.capture(id, source))
        val second = f.block(f.capture(id, source))
        f.rejects(f.block(StmtNode.If(f.boolean(), first, second)), "Duplicate captured address", f.environment(listOf(root)))
    }

    @Test fun repeatingTheSameCaptureStatementOnOnePathRemainsADuplicateDeclaration() {
        val f = Fixture()
        val root = f.storage()
        val declaration = f.stmt(f.capture(f.address(1), NodeLocation(root.location.root, listOf(LocationStep.Index(0)))))
        val tree = ImmutableBlock(f.meta(), listOf(declaration, declaration))
        f.rejects(tree, "Duplicate captured address", f.environment(listOf(root)))
    }
}
