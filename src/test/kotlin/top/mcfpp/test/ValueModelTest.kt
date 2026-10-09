package top.mcfpp.test

import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ValueModelTest {
    @Test fun ordinaryValuesKeepDeclarationIdentityAfterAssignment() {
        val declared = StorageAccess.literal(MCFPPBaseType.Int, CompilerValue.Integral(4), "value")
        declared.bindDeclaration()
        val assigned = declared.assignedBy(MCInt(5))
        assertEquals(declared.symbol, assigned.symbol)
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(5)), StorageAccess.snapshot(assigned))
        val errors = top.mcfpp.Project.errorCount
        val runtime = MCInt("runtime")
        StorageAccess.bindIncomingParameter(runtime)
        assigned.assignedBy(runtime)
        assertEquals(errors, top.mcfpp.Project.errorCount)
    }
    private val int = MCFPPBaseType.Int.typeId
    private val bool = MCFPPBaseType.Bool.typeId
    private fun constant(value: Long) = ValueFacts(TypeKnowledge.Exact(int), ValueKnowledge.Constant(CompilerValue.Integral(value)))
    private fun layoutType(): MCFPPDataTemplateType {
        top.mcfpp.test.util.MCFPPStringTest.readFromString("""
            namespace fixture.value_model_layout;
            data Node { value as int; next as Node?; constructor(value as int){this.value=value;} }
            func main(){}
        """.trimIndent(), version = "26.3")
        assertEquals(0, top.mcfpp.Project.errorCount)
        return assertNotNull(top.mcfpp.model.scope.GlobalScope.getCanonicalTemplate(
            "fixture.value_model_layout", "Node")).getType()
    }
    private fun layoutDescriptors(node: MCFPPDataTemplateType): Map<TypeId, MCFPPType> =
        (listOf<MCFPPType>(node) + node.instanceFields.map { it.type }).associateBy { it.typeId }
    private fun layoutProof(node: MCFPPDataTemplateType) = ValueFacts(
        TypeKnowledge.Exact(node.typeId), ValueKnowledge.Unknown, readableLayout = node.typeId)

    @Test fun snapshotsNeverRetainMutableContainerOrVarPayloads() {
        val value = MCInt(4).apply { bindDeclaration() }
        val source = mutableListOf(value)
        val snapshot = StorageAccess.snapshot(StorageAccess.listLiteral(MCFPPListType(MCFPPBaseType.Int), source))!!
        assertEquals(CompilerValue.Typed(MCFPPListType(MCFPPBaseType.Int).typeId,
            CompilerValue.Sequence(listOf(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(4))))), snapshot)
        val hash = snapshot.hashCode()
        StorageAccess.write(value, MCInt(9))
        source.clear()
        assertEquals(hash, snapshot.hashCode())
        assertNotEquals(snapshot, StorageAccess.snapshot(StorageAccess.listLiteral(MCFPPListType(MCFPPBaseType.Int), listOf(value))))
        val unknown = MCInt("unknown")
        StorageAccess.bindIncomingParameter(unknown)
        val partial = StorageAccess.listLiteral(MCFPPListType(MCFPPBaseType.Int), listOf(value, unknown))
        assertEquals(ValueState.INITIALIZED, StorageAccess.ensure(unknown).data.facts.read(StorageAccess.ensure(unknown).place)!!.state)
        assertNull(StorageAccess.snapshot(partial))
        assertNotEquals(CompilerValue.NullValue, StorageAccess.snapshot(partial))
    }

    @Test fun identityAndConstantConstructorsDefensivelyFreezeCollections() {
        val arguments = mutableListOf(int)
        val applied = TypeId.Applied(TypeId.Builtin("list"), arguments)
        val ids = hashSetOf<TypeId>(applied)
        arguments.clear()
        assertTrue(applied in ids)
        assertEquals(listOf(int), applied.arguments)
        val fields = mutableMapOf("value" to CompilerValue.Integral(3))
        val record = CompilerValue.Record(fields)
        val payloads = mutableListOf<CompilerValue>(record)
        val sequence = CompilerValue.Sequence(payloads)
        val values = hashSetOf<CompilerValue>(sequence)
        fields.clear()
        payloads.clear()
        assertTrue(sequence in values)
        assertEquals(CompilerValue.Integral(3), (sequence.elements.single() as CompilerValue.Record).fields["value"])
        val segments = mutableListOf<PathSegment>(PathSegment.Field("value"))
        val place = Place(SymbolId.fresh(), segments)
        val places = hashSetOf(place)
        segments.clear()
        assertTrue(place in places)
        assertEquals(listOf(PathSegment.Field("value")), place.path)
        val indices = mutableMapOf(0 to 3)
        val location = Location(Place(place.root).unknownIndex(), indices)
        val locations = hashSetOf(location)
        indices.clear()
        assertTrue(location in locations)
        assertEquals(mapOf(0 to 3), location.indices)
    }

    @Test fun cloningAnInitializedValuePreservesFactsWithoutInitializingAnUnknownDeclaration() {
        val scope = top.mcfpp.model.scope.FunctionScope(null)
        val initialized = MCInt(7)
        scope.putVar("initialized", initialized)
        val copy = initialized.clone()
        assertEquals(initialized.symbol, copy.symbol)
        assertEquals(StorageAccess.snapshot(initialized), StorageAccess.snapshot(copy))
        assertEquals(CompilerValue.Typed(int, CompilerValue.Integral(7)), StorageAccess.snapshot(copy))
        assertEquals(ValueState.INITIALIZED, StorageAccess.ensure(copy).data.facts.read(StorageAccess.ensure(copy).place)!!.state)
        val uninitialized = MCInt("uninitialized")
        scope.putVar("uninitialized", uninitialized)
        assertNull(StorageAccess.snapshot(uninitialized.clone()))

        val node = layoutType()
        val types = layoutDescriptors(node)
        val root = Place(SymbolId.fresh())
        val missing = root.field("next").field("value")
        val constructor = FlowFacts().apply {
            initialize(root, ValueFacts(TypeKnowledge.Exact(node.typeId), ValueKnowledge.Unknown))
        }
        assertNull(constructor.read(root)!!.readableLayout)
        assertNull(constructor.readAccess(missing, types = types))
        val marked = FlowFacts().apply { initialize(root, layoutProof(node)) }
        val readable = assertNotNull(marked.readAccess(missing, types = types))
        assertEquals(ValueState.INITIALIZED, readable.state)
        assertEquals(TypeKnowledge.Exact(int), readable.type)
        assertEquals(ValueKnowledge.Unknown, readable.value)
        assertNull(readable.readableLayout)
        assertEquals(node.typeId, marked.readAccess(root.field("next"), types = types)!!.readableLayout)
        var deep = root
        repeat(8) { deep = deep.field("next") }
        assertEquals(ValueState.INITIALIZED, marked.readAccess(deep.field("value"), types = types)!!.state)
        assertNull(marked.read(missing))
        assertEquals(setOf(root), marked.entries().keys)
        assertTrue(marked.knownLengths().isEmpty())
        val copied = Place(SymbolId.fresh())
        val copiedFacts = FlowFacts().apply { copyFrom(marked, root, copied) }
        assertEquals(node.typeId, copiedFacts.read(copied)!!.readableLayout)
        assertEquals(ValueState.INITIALIZED,
            copiedFacts.readAccess(copied.field("next").field("value"), types = types)!!.state)
        for (state in listOf(ValueState.UNINITIALIZED, ValueState.MAYBE_INITIALIZED, ValueState.ERROR)) {
            val blocked = marked.fork().apply {
                initialize(root.field("next"), ValueFacts(TypeKnowledge.Exact(node.typeId), ValueKnowledge.Unknown, state))
            }
            assertEquals(state, blocked.readAccess(root.field("next"), types = types)!!.state)
            assertNotEquals(ValueState.INITIALIZED, blocked.readAccess(missing, types = types)?.state)
            val blockedLeaf = marked.fork().apply {
                initialize(missing, ValueFacts(TypeKnowledge.Exact(int), ValueKnowledge.Unknown, state))
            }
            assertEquals(state, blockedLeaf.readAccess(missing, types = types)!!.state)
        }
        val slots = Place(SymbolId.fresh())
        val oneBranch = FlowFacts().apply {
            setLength(slots, 2)
            initialize(slots.index(0), layoutProof(node))
        }
        val absent = FlowFacts().apply { setLength(slots, 2) }
        val joined = oneBranch.join(absent)
        assertNull(joined.read(slots.index(0))!!.readableLayout)
        assertNotEquals(ValueState.INITIALIZED,
            joined.readAccess(slots.unknownIndex().field("next").field("value"), types = types)?.state)
        assertNull(oneBranch.read(slots.unknownIndex()))
        assertNotEquals(ValueState.INITIALIZED,
            oneBranch.readAccess(slots.unknownIndex().field("next").field("value"), types = types)?.state)
        val unknownWrite = FlowFacts().apply { write(slots.unknownIndex(), layoutProof(node)) }
        assertNull(unknownWrite.read(slots.unknownIndex())!!.readableLayout)
        assertNotEquals(ValueState.INITIALIZED,
            unknownWrite.readAccess(slots.unknownIndex().field("next").field("value"), types = types)?.state)
    }

    @Test fun joinsKeepSharedTypesAndValuesAcrossReachablePaths() {
        val place = Place(SymbolId.fresh())
        val a = FlowFacts().apply { write(place, constant(7)) }
        val b = a.fork()
        assertEquals(constant(7), a.join(b).read(place))
        b.write(place, constant(8))
        assertEquals(ValueFacts(TypeKnowledge.Exact(int), ValueKnowledge.Unknown), a.join(b).read(place))
        b.write(place, ValueFacts(TypeKnowledge.Exact(bool), ValueKnowledge.Constant(CompilerValue.Bool(true))))
        assertEquals(TypeKnowledge.Candidates(setOf(int, bool)), a.join(b).read(place)!!.type)
        b.reachable = false
        assertEquals(a, a.join(b))

        val node = layoutType()
        val proof = layoutProof(node)
        val marked = FlowFacts().apply { initialize(place, proof) }
        assertEquals(node.typeId, marked.join(marked.fork()).read(place)!!.readableLayout)
        val unmarked = FlowFacts().apply { initialize(place, proof.copy(readableLayout = null)) }
        assertNull(marked.join(unmarked).read(place)!!.readableLayout)
        val incompatible = FlowFacts().apply {
            initialize(place, ValueFacts(TypeKnowledge.Exact(int), ValueKnowledge.Unknown))
        }
        assertNull(marked.join(incompatible).read(place)!!.readableLayout)
        for (state in listOf(ValueState.UNINITIALIZED, ValueState.MAYBE_INITIALIZED, ValueState.ERROR)) {
            val branch = FlowFacts().apply { initialize(place, proof.copy(state = state, readableLayout = null)) }
            assertNull(marked.join(branch).read(place)!!.readableLayout, state.toString())
        }
        val partial = marked.join(FlowFacts())
        assertEquals(ValueState.MAYBE_INITIALIZED, partial.read(place)!!.state)
        assertNull(partial.read(place)!!.readableLayout)
        val withConstant = FlowFacts().apply {
            initialize(place, proof.copy(value = ValueKnowledge.Constant(CompilerValue.Record(emptyMap()))))
        }
        val transferred = withConstant.withoutValues()
        assertEquals(ValueKnowledge.Unknown, transferred.read(place)!!.value)
        assertEquals(node.typeId, transferred.read(place)!!.readableLayout)
        assertEquals(node.typeId, withConstant.withoutValues(initializedOnly = true).read(place)!!.readableLayout)
        val invalidProof = FlowFacts().apply {
            initialize(place, proof.copy(type = TypeKnowledge.Exact(int)))
        }
        assertNull(invalidProof.withoutValues().read(place)!!.readableLayout)
        assertNull(marked.join(invalidProof).read(place)!!.readableLayout)
        for (state in listOf(ValueState.UNINITIALIZED, ValueState.MAYBE_INITIALIZED, ValueState.ERROR)) {
            val invalidState = FlowFacts().apply { initialize(place, proof.copy(state = state)) }
            assertNull(invalidState.withoutValues().read(place)!!.readableLayout)
            assertNull(invalidState.join(invalidState.fork()).read(place)!!.readableLayout)
        }
    }

    @Test fun viewsSharePlaceIdentityAndInvalidateOnlyOverlappingFields() {
        val root = Place(SymbolId.fresh())
        val left = root.field("left")
        val right = root.field("right")
        val facts = FlowFacts().apply { write(left, constant(1)); write(right, constant(2)) }
        val view = ValueRef.TypedView(MCFPPBaseType.Object.typeId, ValueRef.Read(int, left), left)
        facts.invalidate(view.place)
        assertEquals(ValueKnowledge.Unknown, facts.read(left)!!.value)
        assertEquals(constant(2), facts.read(right))
        assertTrue(root.index(1).overlaps(root.unknownIndex()))
        assertFalse(root.index(1).overlaps(root.index(2)))
    }

    @Test fun collectionLengthsFollowCopiesAndReachableJoinsWithoutSharingMutableState() {
        val source = Place(SymbolId.fresh())
        val nested = source.index(0)
        val facts = FlowFacts().apply {
            write(source, ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown))
            setLength(source, 2)
            setLength(nested, 3)
        }
        val branch = facts.fork()
        assertEquals(facts, branch)
        branch.setLength(nested, 4)
        assertNotEquals(facts, branch)
        assertEquals(3, facts.length(nested))
        assertEquals(2, facts.join(branch).length(source))
        assertNull(facts.join(branch).length(nested))
        branch.reachable = false
        assertEquals(3, facts.join(branch).length(nested))
        val copied = Place(SymbolId.fresh())
        val copy = FlowFacts().apply { copyFrom(facts, source, copied, includeRoot = false) }
        assertEquals(2, copy.length(copied))
        assertEquals(3, copy.length(copied.index(0)))
        copy.setLength(copied.index(0), 7)
        assertEquals(3, facts.length(nested))
    }

    @Test fun elementWritesPreserveParentLengthsWhileReplacementAndUnknownEffectsWithdrawShapes() {
        val root = Place(SymbolId.fresh())
        val left = root.index(0)
        val right = root.index(1)
        val facts = FlowFacts().apply {
            setLength(root, 2)
            setLength(left, 3)
            setLength(right, 4)
        }
        facts.write(left.index(0), constant(5))
        assertEquals(2, facts.length(root))
        assertEquals(3, facts.length(left))
        facts.write(left, constant(6))
        assertNull(facts.length(left))
        assertEquals(4, facts.length(right))
        facts.invalidate(root.unknownIndex())
        assertEquals(2, facts.length(root))
        assertNull(facts.length(right))
        facts.setLength(left, 1)
        facts.write(root, constant(7))
        assertTrue(facts.knownLengths().isEmpty())
        facts.setLength(root, 1)
        facts.barrier()
        assertTrue(facts.knownLengths().isEmpty())
    }

    @Test fun staticWritesRebuildCompleteAncestorsWithoutMutatingEarlierSnapshots() {
        val root = Place(SymbolId.fresh())
        val list = root.field("list")
        val original = CompilerValue.Typed(MCFPPBaseType.Object.typeId, CompilerValue.Record(mapOf(
            "list" to CompilerValue.Sequence(listOf(CompilerValue.Integral(1), CompilerValue.Integral(2))),
            "sibling" to CompilerValue.Integral(9)
        )))
        val hash = original.hashCode()
        val facts = FlowFacts().apply {
            initialize(root, ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Object.typeId), ValueKnowledge.Constant(original)))
            initialize(list, ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Constant(
                CompilerValue.Sequence(listOf(CompilerValue.Integral(1), CompilerValue.Integral(2))))))
            initialize(list.index(0), constant(1))
            initialize(list.index(1), constant(2))
            initialize(root.field("sibling"), constant(9))
        }
        facts.writeConstant(list.index(0), constant(5))
        assertEquals(hash, original.hashCode())
        assertEquals(ValueKnowledge.Constant(CompilerValue.Sequence(listOf(CompilerValue.Integral(5), CompilerValue.Integral(2)))),
            facts.read(list)!!.value)
        assertEquals(constant(2), facts.read(list.index(1)))
        assertEquals(constant(9), facts.read(root.field("sibling")))
        assertNotEquals(ValueKnowledge.Constant(original), facts.read(root)!!.value)
        facts.writeConstant(list.unknownIndex(), constant(7))
        assertEquals(ValueKnowledge.Unknown, facts.read(root)!!.value)
        assertEquals(ValueKnowledge.Unknown, facts.read(list.index(1))!!.value)

        val node = layoutType()
        val types = layoutDescriptors(node)
        val parent = Place(SymbolId.fresh())
        val child = parent.field("next")
        val scalar = child.field("value")
        val siblingDeep = child.field("next").field("value")
        val marked = FlowFacts().apply {
            initialize(parent, layoutProof(node))
            initialize(child, layoutProof(node))
            initialize(scalar, ValueFacts(TypeKnowledge.Exact(int), ValueKnowledge.Unknown))
        }
        marked.writeConstant(scalar, constant(99))
        assertEquals(node.typeId, marked.read(parent)!!.readableLayout)
        assertEquals(node.typeId, marked.read(child)!!.readableLayout)
        assertEquals(constant(99), marked.read(scalar))
        assertEquals(ValueState.INITIALIZED, marked.readAccess(siblingDeep, types = types)!!.state)
        assertEquals(TypeKnowledge.Exact(int), marked.readAccess(siblingDeep, types = types)!!.type)
        assertNull(marked.read(siblingDeep))
        assertTrue(marked.knownLengths().isEmpty())
        marked.write(scalar, ValueFacts(TypeKnowledge.Exact(int), ValueKnowledge.Unknown))
        assertEquals(node.typeId, marked.read(parent)!!.readableLayout)
        assertEquals(node.typeId, marked.read(child)!!.readableLayout)
        assertEquals(ValueState.INITIALIZED, marked.readAccess(siblingDeep, types = types)!!.state)
        marked.invalidate(scalar)
        assertNull(marked.read(parent)!!.readableLayout)
        assertNull(marked.read(child)!!.readableLayout)
        assertNotEquals(ValueState.INITIALIZED, marked.readAccess(siblingDeep, types = types)?.state)
        marked.writeConstant(scalar, constant(7))
        assertNull(marked.read(parent)!!.readableLayout)
        assertNull(marked.read(child)!!.readableLayout)
        assertNotEquals(ValueState.INITIALIZED, marked.readAccess(siblingDeep, types = types)?.state)
    }

    @Test fun materializationPreservesFactsAndCacheVersionsTrackWrites() {
        val place = Place(SymbolId.fresh())
        val layout = StorageLayout.Scoreboard("value", "mcfpp_default")
        val storage = StorageVersions()
        val facts = FlowFacts().apply { write(place, constant(4)) }
        storage.materialize(place, layout)
        assertTrue(storage.isMaterialized(place, layout))
        assertEquals(constant(4), facts.read(place))
        storage.invalidate(place.field("nested"))
        assertFalse(storage.isMaterialized(place, layout))
        storage.materialize(place, layout)
        assertTrue(storage.isMaterialized(place, layout))
    }

    @Test fun irBranchesJoinEqualConstantsAndLoopsReachAConservativeFixedPoint() {
        val place = Place(SymbolId.fresh())
        val condition = Place(SymbolId.fresh())
        fun write(value: Long) = Instruction.Write(place, ValueRef.Constant(int, CompilerValue.Integral(value)))
        val ir = TypedIR(0, listOf(
            BasicBlock(0, emptyList(), Terminator.Branch(ValueRef.Read(bool, condition), 1, 2)),
            BasicBlock(1, listOf(write(5)), Terminator.Jump(3)),
            BasicBlock(2, listOf(write(5)), Terminator.Jump(3)),
            BasicBlock(3, emptyList(), Terminator.Return(null))
        ))
        assertEquals(constant(5), FlowAnalysis.analyze(ir).entries.getValue(3).read(place))
        val loop = TypedIR(0, listOf(
            BasicBlock(0, listOf(write(1)), Terminator.Jump(1)),
            BasicBlock(1, emptyList(), Terminator.Branch(ValueRef.Read(bool, condition), 2, 3)),
            BasicBlock(2, listOf(write(2)), Terminator.Jump(1)),
            BasicBlock(3, emptyList(), Terminator.Return(null))
        ))
        assertEquals(ValueKnowledge.Unknown, FlowAnalysis.analyze(loop).entries.getValue(1).read(place)!!.value)
    }

    @Test fun rawCommandsAndUnknownCallsAreBarriersButPureCallsAreNot() {
        val place = Place(SymbolId.fresh())
        val initial = FlowFacts().apply { write(place, constant(1)) }
        fun result(effect: Effect) = FlowAnalysis.analyze(TypedIR(0, listOf(BasicBlock(0,
            listOf(Instruction.Call(null, SymbolId.fresh(), emptyList(), effect)), Terminator.Return(null)))), initial).exits.getValue(0)
        assertEquals(constant(1), result(Effect.Pure).read(place))
        assertEquals(ValueKnowledge.Unknown, result(Effect.Unknown).read(place)!!.value)
        val raw = FlowAnalysis.analyze(TypedIR(0, listOf(BasicBlock(0,
            listOf(Instruction.RawCommand("data remove storage example:data values")), Terminator.Return(null)))), initial)
        assertEquals(ValueKnowledge.Unknown, raw.exits.getValue(0).read(place)!!.value)

        val node = layoutType()
        val types = layoutDescriptors(node)
        val marked = FlowFacts().apply { initialize(place, layoutProof(node)) }
        val missing = place.field("next").field("next").field("value")
        fun layoutResult(effect: Effect) = FlowAnalysis.analyze(TypedIR(0, listOf(BasicBlock(0,
            listOf(Instruction.Call(null, SymbolId.fresh(), emptyList(), effect)), Terminator.Return(null)))), marked)
            .exits.getValue(0)
        assertEquals(node.typeId, layoutResult(Effect.Pure).read(place)!!.readableLayout)
        assertEquals(ValueState.INITIALIZED, layoutResult(Effect.Pure).readAccess(missing, types = types)!!.state)
        for (effect in listOf(Effect.Writes(setOf(place.field("next"))), Effect.Unknown)) {
            val withdrawn = layoutResult(effect)
            assertNull(withdrawn.read(place)!!.readableLayout)
            assertNotEquals(ValueState.INITIALIZED, withdrawn.readAccess(missing, types = types)?.state)
            withdrawn.writeConstant(place.field("value"), constant(42))
            assertNull(withdrawn.read(place)!!.readableLayout)
            assertNotEquals(ValueState.INITIALIZED, withdrawn.readAccess(missing, types = types)?.state)
        }
        val rawLayout = FlowAnalysis.analyze(TypedIR(0, listOf(BasicBlock(0,
            listOf(Instruction.RawCommand("data remove storage example:data node")), Terminator.Return(null)))), marked)
            .exits.getValue(0)
        assertNull(rawLayout.read(place)!!.readableLayout)
        assertNotEquals(ValueState.INITIALIZED, rawLayout.readAccess(missing, types = types)?.state)
        val barrier = marked.fork().apply { barrier() }
        assertNull(barrier.read(place)!!.readableLayout)
        assertNotEquals(ValueState.INITIALIZED, barrier.readAccess(missing, types = types)?.state)
        val replacement = marked.fork().apply { write(place, constant(5)) }
        assertNull(replacement.read(place)!!.readableLayout)
        assertNotEquals(ValueState.INITIALIZED, replacement.readAccess(missing, types = types)?.state)
    }
    @Test fun specializationKeysDistinguishNullUnknownErrorsAndTargets() {
        val function = top.mcfpp.model.function.Function("generic", context = null)
        val value = MCInt(4).apply { bindDeclaration() }
        val key = SpecializationKeys.forArguments(function, listOf(value))
        val cache = hashMapOf(key to "compiled")
        StorageAccess.write(value, MCInt(9))
        assertEquals("compiled", cache[key])
        assertNull(cache[SpecializationKeys.forArguments(function, listOf(value))])
        val version = top.mcfpp.Project.config.version
        try {
            top.mcfpp.Project.config.version = "26.3"
            val native = SpecializationKeys.forArguments(function, listOf(value))
            top.mcfpp.Project.config.version = "26.2"
            assertNotEquals(native, SpecializationKeys.forArguments(function, listOf(value)))
        } finally { top.mcfpp.Project.config.version = version }
        assertNotEquals(SpecializationKeys.argument(StorageAccess.literal(MCFPPPrivateType.Null, CompilerValue.NullValue)), SpecializationArgument.Unknown)
        assertEquals(SpecializationArgument.Error, SpecializationKeys.argument(MCInt("error").apply { isError = true }))
    }

    @Test fun materializationKeepsDeclarationIdentityAndCompleteFacts() {
        top.mcfpp.test.util.MCFPPStringTest.readFromString("func arithmetic(){ var value = 4; value = 5; }", version = "26.3")
        assertEquals(0, top.mcfpp.Project.errorCount)
        val function = top.mcfpp.model.scope.GlobalScope.localNamespaces["default.test"]!!.scope.functions["arithmetic"]!!.first()
        val value = function.scope.getVar("value") as MCInt
        val symbol = value.symbol
        val before = StorageAccess.snapshot(value)
        assertEquals(CompilerValue.Typed(int, CompilerValue.Integral(5)), before)
        function.runInFunction { StorageAccess.materialize(value) }
        assertEquals(symbol, function.scope.getVar("value")!!.symbol)
        assertEquals(int, symbol!!.declaredType)
        assertEquals(before, StorageAccess.snapshot(function.scope.getVar("value")!!))
        assertEquals(StorageAccess.ensure(value).place, StorageAccess.ensure(function.scope.getVar("value")!!).place)
    }

}
