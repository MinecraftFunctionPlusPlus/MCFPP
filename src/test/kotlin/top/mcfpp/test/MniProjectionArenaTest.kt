package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniProjectionArenaTest {
    private val span = SourceSpan("projection.mcfpp", 4, 12, 2, 2, 2, 10)
    private val proofSpan = SourceSpan("declaration.mcfpp", 20, 28, 3, 1, 3, 9)
    private val origin = Origin(span)
    private val intKey = TypeKey(410, 1)
    private val listKey = TypeKey(410, 2)
    private val typeKey = TypeKey(410, 3)
    private val typeListKey = TypeKey(410, 4)
    private val proofKey = TypeKey(410, 5)
    private val nodeKey = TypeKey(410, 6)
    private val nextKey = FieldKey(nodeKey, 1)
    private val graph = FrozenTypeGraph.freeze(
        listOf(
            FrozenTypeRecord(intKey, "int", "builtin:int", TypeKind.INT, constant = ConstantSchema.Scalar(ConstantShape.INT32)),
            FrozenTypeRecord(listKey, "list<int>", "list:int", TypeKind.OTHER, index = IndexSchema(intKey, true)),
            FrozenTypeRecord(typeKey, "Type", "builtin:type", TypeKind.OTHER, constant = ConstantSchema.Scalar(ConstantShape.TYPE)),
            FrozenTypeRecord(
                typeListKey,
                "list<Type>",
                "list:type",
                TypeKind.OTHER,
                constant = ConstantSchema.Sequence(typeKey),
                index = IndexSchema(typeKey, true)
            ),
            FrozenTypeRecord(
                proofKey,
                "Proof",
                "fixture:proof",
                TypeKind.OTHER,
                constant = ConstantSchema.Record(mapOf("nested" to typeListKey))
            ),
            FrozenTypeRecord(nodeKey, "Node", "fixture:node", TypeKind.OTHER, fields = listOf(nextKey))
        ),
        listOf(FrozenFieldRecord(nextKey, "next", nodeKey, true, span))
    )
    private val int get() = graph.type(intKey)
    private val list get() = graph.type(listKey)

    private fun newArena(id: Long = 1, charge: (Int, Origin) -> Unit = { _, _ -> }): CodeArena = CodeArena(SessionId(id), ScopeId(SessionId(id), 0), charge)
    private fun meta(arena: CodeArena, scope: ScopeId = arena.rootScope) = arena.metadata(scope, origin)
    private fun number(arena: CodeArena, scope: ScopeId = arena.rootScope) = arena.expr(
        meta(arena, scope),
        int,
        ExprNode.Literal(ConstantValue.Integral(1))
    )
    private fun root(arena: CodeArena) = arena.ref(meta(arena), list, NodeLocation(arena.freshSymbol()))
    private fun node(statement: Stmt) = (statement as ImmutableStmt).node
    private fun location(reference: Ref<*>) = when (reference) {
        is ImmutableRef<*> -> reference.location
        is ImmutableLocal<*> -> reference.location
        is ImmutableMinecraftScoreRef -> throw CodeModelException(reference.source(), "Unsupported reference node '${reference.javaClass.name}'")
    }

    @Test fun arenaValidatorAndPrinterSupportMinecraftScoresAtTheirGeneratedSource() {
        val arena = newArena()
        val generated = SourceSpan("score-generated.mcfpp", 40, 47, 5, 3, 5, 10)
        val metadata = CodeMetadata(arena.sessionId, arena.rootScope, Origin(span, generated))
        @Suppress("UNCHECKED_CAST")
        val scoreInt = int as TypeRef<IntT>
        val score = ImmutableMinecraftScoreRef(metadata, scoreInt, MinecraftScoreTarget.Fixed(ScoreHolder.named("#score")), "score")
        val raw = ImmutableStmt(metadata, StmtNode.Assign(score, number(arena)))

        val imported = arena.importTree(raw, arena.rootScope)
        val assignment = assertIs<StmtNode.Assign>(imported.node)
        val importedScore = assertIs<ImmutableMinecraftScoreRef>(assignment.destination)
        assertSame(score.target, importedScore.target)
        assertEquals("score", importedScore.objective)
        assertEquals(generated, imported.source())
        for (phase in ValidationPhase.entries)
            CodeValidator().validate(raw, ValidationEnvironment(arena, types = listOf(int)), phase)
        assertTrue(CodePrinter().print(raw).contains("score holder \"#score\" \"score\""))

        val ordinary = arena.ref(metadata, int, NodeLocation(arena.freshSymbol()))
        val legacy = arena.stmt(metadata, StmtNode.Assign(ordinary, ordinary.load()))
        assertTrue(CodePrinter().print(legacy).contains("assign"))
        CodeValidator().validate(legacy, ValidationEnvironment(arena, externalBindings = listOf(ordinary), types = listOf(int)), ValidationPhase.FINAL)
    }

    @Test fun repeatedImportsRenameDynamicIndexAndBothAddressStagesWhileRetainingOriginalStorage() {
        var charged = 0
        val arena = newArena { count, _ -> charged += count }
        val metadata = meta(arena)
        val external = root(arena)
        val index = arena.local(metadata, int, "index")
        val declaration = arena.stmt(metadata, StmtNode.LocalDecl(index, number(arena)))
        val firstAddress = arena.freshAddress()
        val first = arena.stmt(
            metadata,
            StmtNode.CaptureAddress(
                firstAddress,
                NodeLocation(external.location.root, listOf(LocationStep.CapturedIndex(index.symbol))),
                int
            )
        )
        val secondAddress = arena.freshAddress()
        val second = arena.stmt(
            metadata,
            StmtNode.CaptureAddress(
                secondAddress,
                NodeLocation(external.location.root, capturedBase = firstAddress),
                int
            )
        )
        val reference = arena.ref(metadata, int, NodeLocation(external.location.root, capturedBase = secondAddress))
        val assignment = arena.stmt(metadata, StmtNode.Assign(reference, reference.load()))
        val body = arena.block(metadata, listOf(declaration, first, second, assignment))
        val before = charged
        val left = arena.importTree(body, arena.rootScope)
        val right = arena.importTree(body, arena.rootScope)
        assertEquals(16, charged - before) // Per insertion: local/read, literal, four statements, reference/read and block.
        val addresses = mutableSetOf(firstAddress, secondAddress)
        for (copy in listOf(left, right)) {
            val local = assertIs<StmtNode.LocalDecl>(node(copy.statements()[0])).local as ImmutableLocal<*>
            assertNotEquals(index.symbol, local.symbol)
            val capture = assertIs<StmtNode.CaptureAddress>(node(copy.statements()[1]))
            val recapture = assertIs<StmtNode.CaptureAddress>(node(copy.statements()[2]))
            assertTrue(addresses.add(capture.address))
            assertTrue(addresses.add(recapture.address))
            assertEquals(external.location.root, capture.location.root)
            assertEquals(listOf(LocationStep.CapturedIndex(local.symbol)), capture.location.steps)
            assertEquals(capture.address, recapture.location.capturedBase)
            assertTrue(recapture.location.steps.isEmpty())
            val assign = assertIs<StmtNode.Assign>(node(copy.statements()[3]))
            assertEquals(recapture.address, location(assign.destination).capturedBase)
            assertEquals(external.location.root, location(assign.destination).root)
            assertSame(assign.destination.load(), assign.expression)
        }
        assertNotEquals(
            assertIs<StmtNode.LocalDecl>(node(left.statements()[0])).local,
            assertIs<StmtNode.LocalDecl>(node(right.statements()[0])).local
        )
    }

    @Test fun indexDagReadonlySignatureAndDeepTypeConstantsSurviveImportWithoutExtraEvaluationNodes() {
        var charged = 0
        val arena = newArena { count, _ -> charged += count }
        val metadata = meta(arena)
        val external = root(arena)
        val index = number(arena)
        val value = arena.expr(metadata, int, ExprNode.Index(external.load(), index))
        val proof = ConstantValue.Record(
            mapOf(
                "nested" to ConstantValue.Sequence(
                    listOf(
                        ConstantValue.TypeValue(graph.type(nodeKey)),
                        ConstantValue.TypeValue(list)
                    )
                )
            )
        )
        val function = ImmutableFunctionRef(
            FunctionKey(410, "readonly"),
            "readonly",
            FunctionSignature.of(listOf(ParameterView.of("left", int, ParamMode.VALUE), ParameterView.of("right", int, ParamMode.VALUE)), int),
            FunctionKind.NATIVE,
            readonlyParameters = listOf(ParameterView.of("proof", graph.type(proofKey), ParamMode.CONST))
        )
        val typed = TypedConstant(graph.type(proofKey), proof, proofSpan)
        val call = arena.expr(
            metadata,
            int,
            ExprNode.Call(
                function,
                listOf(CallArgument.value(value), CallArgument.value(value)),
                listOf(typed)
            )
        )
        val body = arena.block(metadata, listOf(arena.stmt(metadata, StmtNode.Eval(call))))
        val before = charged
        val imported = arena.importTree(body, arena.rootScope)
        assertEquals(5, charged - before) // Index/literal/call/eval/block; shared Index and external read are each reused.
        val copiedCall = assertIs<ExprNode.Call>((assertIs<StmtNode.Eval>(node(imported.statements().single())).expression as ImmutableExpr<*>).node)
        assertSame(function, copiedCall.function)
        assertEquals(1, copiedCall.function.readonlyParameters().size)
        assertEquals(2, copiedCall.arguments.size)
        assertSame(typed, copiedCall.readonlyArguments.single())
        val copiedValue = (copiedCall.arguments[0].payload as ArgumentPayload.Value).expression
        assertSame(copiedValue, (copiedCall.arguments[1].payload as ArgumentPayload.Value).expression)
        val copiedIndex = assertIs<ExprNode.Index>((copiedValue as ImmutableExpr<*>).node)
        assertSame(external.load(), copiedIndex.receiver)
        assertTrue(copiedIndex.index !== index)
        val types = assertIs<ConstantValue.Sequence>(assertIs<ConstantValue.Record>(typed.value).fields.getValue("nested")).elements
        val node = assertIs<ConstantValue.TypeValue>(types[0]).type
        assertSame(graph.type(nodeKey), node)
        assertSame(node, node.fields().single().type())
        assertSame(list, assertIs<ConstantValue.TypeValue>(types[1]).type)
        assertEquals(proofSpan, copiedCall.readonlyArguments.single().source)
    }

    @Test fun childTreeImportsInternalAddressesAndReadonlyCallsButRequiresTheFreeStorageMapping() {
        val parent = newArena()
        val child = parent.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val external = root(child)
        val destination = root(parent)
        val address = child.freshAddress()
        val capture = child.stmt(
            meta(child),
            StmtNode.CaptureAddress(
                address,
                NodeLocation(external.location.root, listOf(LocationStep.Index(0))),
                int
            )
        )
        val selected = child.ref(meta(child), int, NodeLocation(external.location.root, capturedBase = address))
        val function = ImmutableFunctionRef(
            FunctionKey(410, "native"),
            "native",
            FunctionSignature.of(
                listOf(ParameterView.of("p", int, ParamMode.REF)),
                int
            ),
            FunctionKind.ORDINARY,
            readonlyParameters = listOf(ParameterView.of("n", int, ParamMode.CONST))
        )
        val call = child.expr(
            meta(child),
            int,
            ExprNode.Call(
                function,
                listOf(CallArgument.ref(selected)),
                listOf(TypedConstant(int, ConstantValue.Integral(7), proofSpan))
            )
        )
        val body = child.block(meta(child), listOf(capture, child.stmt(meta(child), StmtNode.Eval(call))))
        assertFailsWith<CodeModelException> { parent.importTree(body, parent.rootScope) }
        assertFailsWith<IllegalArgumentException> { parent.importChildTree(child, body, parent.rootScope) }
        val detached = child.block(meta(child), listOf(child.stmt(meta(child), StmtNode.Eval(call))))
        val missing = assertFailsWith<CodeModelException> {
            parent.importChildTree(
                child,
                detached,
                parent.rootScope,
                externalBindings = mapOf(external.location.root to destination)
            )
        }
        assertEquals(span, missing.source)
        assertTrue(missing.message!!.contains("outside the imported tree"))
        val copied = parent.importChildTree(
            child,
            body,
            parent.rootScope,
            externalBindings = mapOf(external.location.root to destination)
        )
        val copiedCapture = assertIs<StmtNode.CaptureAddress>(node(copied.statements()[0]))
        assertEquals(parent.sessionId, copiedCapture.address.session)
        assertNotEquals(address, copiedCapture.address)
        assertEquals(destination.location.root, copiedCapture.location.root)
        val copiedCall = assertIs<ExprNode.Call>((assertIs<StmtNode.Eval>(node(copied.statements()[1])).expression as ImmutableExpr<*>).node)
        assertSame(function, copiedCall.function)
        assertEquals(ConstantValue.Integral(7), copiedCall.readonlyArguments.single().value)
        assertEquals(copiedCapture.address, location((copiedCall.arguments.single().payload as ArgumentPayload.Reference).reference).capturedBase)
        child.close()
        parent.stmt(meta(parent), StmtNode.Eval((assertIs<StmtNode.Eval>(node(copied.statements()[1]))).expression))
    }

    @Test fun captureRegistrationChecksScopeIdentityAndFailedBudgetDoesNotPublishADeclaration() {
        var remaining = 0
        val arena = newArena { _, reported -> if (remaining-- <= 0) throw CodeModelException(reported.callSite, "budget") }
        val metadata = meta(arena)
        val address = arena.freshAddress()
        val storage = NodeLocation(arena.freshSymbol())
        assertFailsWith<CodeModelException> { arena.stmt(metadata, StmtNode.CaptureAddress(address, storage, int)) }
        assertEquals(
            span,
            assertFailsWith<CodeModelException> {
                arena.ref(metadata, int, NodeLocation(storage.root, capturedBase = address))
            }.source
        )
        remaining = 10
        arena.stmt(metadata, StmtNode.CaptureAddress(address, storage, int))
        assertFailsWith<CodeModelException> { arena.stmt(metadata, StmtNode.CaptureAddress(address, storage, int)) }
        assertFailsWith<CodeModelException> {
            arena.stmt(
                metadata,
                StmtNode.CaptureAddress(AddressId(arena.sessionId, 999), storage, int)
            )
        }
        val nested = arena.childScope(arena.rootScope)
        val hidden = arena.freshAddress()
        arena.stmt(meta(arena, nested), StmtNode.CaptureAddress(hidden, storage, int))
        val failure = assertFailsWith<CodeModelException> { arena.ref(metadata, int, NodeLocation(storage.root, capturedBase = hidden)) }
        assertEquals(span, failure.source)
        assertTrue(failure.message!!.contains("lexical scope"))
        val foreign = SessionId(99)
        assertFailsWith<CodeModelException> {
            arena.stmt(
                metadata,
                StmtNode.CaptureAddress(AddressId(foreign, 1), NodeLocation(SymbolId(foreign, 1)), int)
            )
        }
        arena.close()
        assertFailsWith<CodeModelException> { arena.freshAddress() }
    }

    @Test fun treeExternalAddressesAreRejectedEvenWhenHiddenInAnAncestorValueOrExplicitStorageMapping() {
        val arena = newArena()
        val external = root(arena)
        val address = arena.freshAddress()
        arena.stmt(meta(arena), StmtNode.CaptureAddress(address, external.location, int))
        val selected = arena.ref(meta(arena), int, NodeLocation(external.location.root, capturedBase = address))
        val expression = arena.expr(meta(arena), int, ExprNode.Binary(BinaryOperator.ADD, selected.load(), number(arena)))
        val nested = arena.childScope(arena.rootScope)
        val body = arena.block(meta(arena, nested), listOf(arena.stmt(meta(arena, nested), StmtNode.Eval(expression))))
        for (mapping in listOf(emptyMap(), mapOf(external.location.root to root(arena)))) {
            val failure = assertFailsWith<CodeModelException> { arena.importTree(body, arena.rootScope, externalBindings = mapping) }
            assertEquals(span, failure.source)
            assertTrue(failure.message!!.contains("outside the imported tree"))
        }
        val failure = assertFailsWith<CodeModelException> {
            arena.importTree(number(arena), arena.rootScope, externalBindings = mapOf(external.location.root to selected))
        }
        assertTrue(failure.message!!.contains("External binding"))
        assertEquals(address, selected.location.capturedBase)
    }

    @Test fun forgedCapturedReferencesAndUnregisteredRawDeclarationsCannotBeImportedOrUsed() {
        val arena = newArena()
        val metadata = meta(arena)
        val storage = NodeLocation(arena.freshSymbol())
        val address = arena.freshAddress()
        val raw = ImmutableStmt(metadata, StmtNode.CaptureAddress(address, storage, int))
        assertEquals(span, assertFailsWith<CodeModelException> { arena.block(metadata, listOf(raw)) }.source)
        assertFailsWith<CodeModelException> { arena.importTree(raw, arena.rootScope) }
        val reference = ImmutableRef(metadata, int, NodeLocation(storage.root, capturedBase = address))
        assertFailsWith<CodeModelException> { arena.stmt(metadata, StmtNode.Eval(reference.load())) }
        assertFailsWith<CodeModelException> { arena.importTree(reference, arena.rootScope) }
        val foreign = newArena(99)
        assertFailsWith<CodeModelException> { arena.importTree(number(foreign), arena.rootScope) }
    }

    @Test fun nestedConstantTypeHandlesAreCheckedBeforeChargingAndPreserveTheirOwnSource() {
        var charged = 0
        val arena = newArena { count, _ -> charged += count }
        val original = graph.type(nodeKey)
        val forged = ImmutableTypeRef<Any>(original.key, original.name(), kind = original.kind, graph = graph)
        val poisoned = ConstantValue.Record(mapOf("nested" to ConstantValue.Sequence(listOf(ConstantValue.TypeValue(forged)))))
        val function = ImmutableFunctionRef(
            FunctionKey(410, "constant"),
            "constant",
            FunctionSignature.of(emptyList(), int),
            FunctionKind.NATIVE,
            readonlyParameters = listOf(ParameterView.of("proof", graph.type(proofKey), ParamMode.CONST))
        )
        val failure = assertFailsWith<CodeModelException> {
            arena.expr(
                meta(arena),
                int,
                ExprNode.Call(function, emptyList(), listOf(TypedConstant(graph.type(proofKey), poisoned, proofSpan)))
            )
        }
        assertEquals(proofSpan, failure.source)
        assertEquals(0, charged)
        assertFailsWith<CodeModelException> { arena.expr(meta(arena), graph.type(proofKey), ExprNode.Literal(poisoned)) }
        assertEquals(0, charged)
    }

    @Test fun indexDependenciesCannotEscapeTheirScopeAndConstantWrappersKeepCanonicalRecursiveTypes() {
        var charged = 0
        val arena = newArena { count, _ -> charged += count }
        val external = root(arena)
        val nested = arena.childScope(arena.rootScope)
        val hidden = arena.local(meta(arena, nested), int, "hidden")
        val beforeFailure = charged
        val failure = assertFailsWith<CodeModelException> {
            arena.expr(meta(arena), int, ExprNode.Index(external.load(), hidden.load()))
        }
        assertEquals(span, failure.source)
        assertEquals(beforeFailure, charged)
        val foreign = newArena(99)
        assertFailsWith<IllegalArgumentException> { arena.expr(meta(arena), int, ExprNode.Index(external.load(), number(foreign))) }
        val proof = ConstantValue.Record(mapOf("nested" to ConstantValue.Sequence(listOf(ConstantValue.TypeValue(graph.type(nodeKey))))))
        val literal = arena.expr(meta(arena), graph.type(proofKey), ExprNode.Literal(proof))
        val constant = arena.constant(literal, proof)
        val beforeImport = charged
        val imported = arena.importTree(constant, arena.rootScope)
        assertEquals(1, charged - beforeImport)
        assertTrue(imported.expr() !== literal)
        assertSame(proof, imported.value())
        assertSame(graph.type(proofKey), imported.type())
        val node = assertIs<ConstantValue.TypeValue>(
            assertIs<ConstantValue.Sequence>(
                assertIs<ConstantValue.Record>(imported.value()).fields.getValue("nested")
            ).elements.single()
        ).type
        assertSame(node, node.fields().single().type())
    }

    @Test fun mappedRootSuffixIsCapturedOnceAndIsNeverPrependedToAnAddressRelativePath() {
        val parent = newArena()
        val child = parent.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val nodeType = graph.type(nodeKey)
        val field = LocationStep.Field(graph.field(nextKey))
        val destination = parent.ref(meta(parent), nodeType, NodeLocation(parent.freshSymbol(), listOf(field), writable = false))
        val external = child.ref(meta(child), nodeType, NodeLocation(child.freshSymbol()))
        val address = child.freshAddress()
        val capture = child.stmt(meta(child), StmtNode.CaptureAddress(address, external.location, nodeType))
        val selected = child.ref(meta(child), nodeType, NodeLocation(external.location.root, listOf(field), capturedBase = address))
        val tree = child.block(meta(child), listOf(capture, child.stmt(meta(child), StmtNode.Eval(selected.load()))))
        val imported = parent.importChildTree(
            child,
            tree,
            parent.rootScope,
            externalBindings = mapOf(external.location.root to destination)
        )
        val copiedCapture = assertIs<StmtNode.CaptureAddress>(node(imported.statements()[0]))
        val read = assertIs<ExprNode.Read>((assertIs<StmtNode.Eval>(node(imported.statements()[1])).expression as ImmutableExpr<*>).node)
        assertEquals(destination.location.root, copiedCapture.location.root)
        assertEquals(listOf(field), copiedCapture.location.steps)
        assertEquals(copiedCapture.address, read.location.capturedBase)
        assertEquals(listOf(field), read.location.steps)
        assertEquals(false, copiedCapture.location.writable)
        assertEquals(false, read.location.writable)
        assertSame(graph.field(nextKey), assertIs<LocationStep.Field>(read.location.steps.single()).field)
    }
}
