package top.mcfpp.test

import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ValueModelTest {
    @Test fun requiredCompileTimeValuesAreADeclarationConstraintAfterAdapterReplacement() {
        val declared = MCFPPDeclaredConcreteType(MCFPPBaseType.Int).build("required", 4)
        declared.bindDeclaration()
        val assigned = declared.assignedBy(MCIntConcrete(5))
        assertEquals(declared.symbol, assigned.symbol)
        assertTrue(assigned.symbol!!.requiresConstant)
        assertTrue(ValueSnapshot.of(assigned) != null)
        val errors = top.mcfpp.Project.errorCount
        assigned.assignedBy(MCInt("runtime"))
        assertEquals(errors + 1, top.mcfpp.Project.errorCount)
        assigned.replacedBy(MCInt("runtime"))
        assertEquals(errors + 2, top.mcfpp.Project.errorCount)
    }
    private val int = MCFPPBaseType.Int.typeId
    private val bool = MCFPPBaseType.Bool.typeId
    private fun constant(value: Long) = ValueFacts(TypeKnowledge.Exact(int), ValueKnowledge.Constant(CompilerValue.Integral(value)))

    @Test fun snapshotsNeverRetainMutableContainerOrVarPayloads() {
        val value = MCIntConcrete(4)
        val source = mutableListOf(value)
        val snapshot = ValueSnapshot.of(source)
        val hash = snapshot.hashCode()
        value.value = 9
        source.clear()
        assertEquals(hash, snapshot.hashCode())
        assertNotEquals(snapshot, ValueSnapshot.of(listOf(value)))
        assertNull(ValueSnapshot.of(listOf(MCInt("unknown"))))
        assertNotEquals(ValueSnapshot.of(null), ValueSnapshot.of(MCInt("unknown")))
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
    }

    @Test fun cloningAnInitializedValuePreservesFactsWithoutInitializingAnUnknownDeclaration() {
        val scope = top.mcfpp.model.scope.FunctionScope(null)
        val initialized = MCIntConcrete(7).apply { hasAssigned = true; isDynamic = true }
        scope.putVar("initialized", initialized)
        val copy = initialized.clone()
        assertEquals(initialized.symbol, copy.symbol)
        assertEquals(ValueSnapshot.of(initialized), ValueSnapshot.of(copy))
        assertTrue(copy.isDynamic)
        val uninitialized = MCIntConcrete(0)
        scope.putVar("uninitialized", uninitialized)
        assertNull(ValueSnapshot.of(uninitialized.clone()))
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
    }
    @Test fun specializationKeysDistinguishNullUnknownErrorsAndTargets() {
        val function = top.mcfpp.model.function.Function("generic", context = null)
        val value = MCIntConcrete(4)
        val key = SpecializationKeys.forArguments(function, listOf(value))
        val cache = hashMapOf(key to "compiled")
        value.value = 9
        assertEquals("compiled", cache[key])
        assertNull(cache[SpecializationKeys.forArguments(function, listOf(value))])
        val version = top.mcfpp.Project.config.version
        try {
            top.mcfpp.Project.config.version = "26.3"
            val native = SpecializationKeys.forArguments(function, listOf(value))
            top.mcfpp.Project.config.version = "26.2"
            assertNotEquals(native, SpecializationKeys.forArguments(function, listOf(value)))
        } finally { top.mcfpp.Project.config.version = version }
        assertNotEquals(SpecializationKeys.argument(top.mcfpp.core.lang.MCAnyConcrete(null)), SpecializationArgument.Unknown)
        assertEquals(SpecializationArgument.Error, SpecializationKeys.argument(MCInt("error").apply { isError = true }))
    }

    @Test fun trackingLossKeepsDeclarationIdentity() {
        top.mcfpp.test.util.MCFPPStringTest.readFromString("func arithmetic(){ var value = 4; value = 5; }", version = "26.3")
        assertEquals(0, top.mcfpp.Project.errorCount)
        val function = top.mcfpp.model.scope.GlobalScope.localNamespaces["default.test"]!!.scope.functions["arithmetic"]!!.first()
        val value = function.scope.getVar("value") as MCIntConcrete
        val symbol = value.symbol
        function.runInFunction { value.toDynamic(true) }
        assertEquals(symbol, function.scope.getVar("value")!!.symbol)
        assertEquals(int, symbol!!.declaredType)
        assertTrue(function.scope.getVar("value")!!.valueRef() is ValueRef.Read)
    }

}
