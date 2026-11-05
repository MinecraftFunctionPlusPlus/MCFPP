package top.mcfpp.test

import top.mcfpp.analysis.*
import top.mcfpp.library.format.TypeTableCodec
import top.mcfpp.library.format.LibraryBinaryReader
import top.mcfpp.library.format.LibraryBinaryWriter
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.scope.SimpleLibScope
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.optimization.CommandCostModel
import top.mcfpp.optimization.TypedMinecraftCost
import top.mcfpp.optimization.FunctionPass
import top.mcfpp.optimization.PassContext
import top.mcfpp.optimization.PassIds
import top.mcfpp.optimization.PassManager
import top.mcfpp.optimization.OptimizationConfig
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.TypeId
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.*
import kotlin.test.Test

/** Count graph visits/identities rather than depending on machine-specific timing limits. */
class PerformanceAlgorithmsTest {
    @Test fun delayedParentUpdateReachesExistingNestedViewsWithoutRefreshingUnrelatedOwners() {
        var unrelatedReads = 0
        val unrelated = object : DataTemplate("unrelated", "audit") {
            override fun getType(): MCFPPDataTemplateType { unrelatedReads++; return super.getType() }
        }
        val a = DataTemplate("parentA", "audit")
        val b = DataTemplate("parentB", "audit")
        val c = DataTemplate("parentC", "audit")
        top.mcfpp.library.api.LibraryImportSession().use { session ->
            val remember = session.javaClass.getDeclaredMethod("rememberType", top.mcfpp.type.MCFPPType::class.java).apply { isAccessible = true }
            val refresh = session.javaClass.getDeclaredMethod("refreshNominalViews", DataTemplate::class.java).apply { isAccessible = true }
            val ta = a.getType()
            remember.invoke(session, ta)
            remember.invoke(session, unrelated.getType())
            a.parent.add(b)
            refresh.invoke(session, a)
            val nestedB = ta.parentType.filterIsInstance<MCFPPDataTemplateType>().single()
            assertTrue(nestedB.parentType.none { it.typeId == c.nominalIdentity })
            val before = unrelatedReads
            b.parent.add(c)
            refresh.invoke(session, b)
            assertTrue(nestedB.parentType.any { it.typeId == c.nominalIdentity })
            assertEquals(before, unrelatedReads)
        }
    }

    @Test fun runtimeRepresentationVisitsSharedFieldsOnceAndRechecksAfterMutation() {
        var visits = 0
        val leaf = object : top.mcfpp.type.MCFPPType() {
            override val hasRuntimeRepresentation: Boolean get() { visits++; return true }
        }
        var type: top.mcfpp.type.MCFPPType = leaf
        repeat(16) { level ->
            val owner = DataTemplate("runtime$level", "audit")
            for (name in listOf("left", "right")) owner.scope.putVar(name,
                top.mcfpp.core.lang.UnknownVar(name).apply { this.type = type }, true)
            type = owner.getType()
        }
        assertTrue(type.hasRuntimeRepresentation)
        assertEquals(1, visits)
        val root = type as MCFPPDataTemplateType
        root.template.scope.putVar("bad", top.mcfpp.core.lang.UnknownVar("bad").apply {
            this.type = object : top.mcfpp.type.MCFPPType() {
                override val hasRuntimeRepresentation = false
            }
        }, true)
        assertFalse(root.hasRuntimeRepresentation)
    }

    @Test fun runtimeRepresentationFindsFalseExitFromCycle() {
        val a = DataTemplate("cycleA", "audit")
        val b = DataTemplate("cycleB", "audit")
        val ta = a.getType(); val tb = b.getType()
        a.scope.putVar("b", top.mcfpp.core.lang.UnknownVar("b").apply { type = tb }, true)
        b.scope.putVar("a", top.mcfpp.core.lang.UnknownVar("a").apply { type = ta }, true)
        assertTrue(ta.hasRuntimeRepresentation)
        b.scope.putVar("bad", top.mcfpp.core.lang.UnknownVar("bad").apply {
            type = object : top.mcfpp.type.MCFPPType() { override val hasRuntimeRepresentation = false }
        }, true)
        assertFalse(ta.hasRuntimeRepresentation)
    }

    @Test fun runtimeRepresentationDoesNotConflateIndependentCopiesOfOneNominalDeclaration() {
        val first = DataTemplate("copied", "audit")
        val second = DataTemplate("copied", "audit")
        second.scope.putVar("bad", top.mcfpp.core.lang.UnknownVar("bad").apply {
            type = object : top.mcfpp.type.MCFPPType() { override val hasRuntimeRepresentation = false }
        }, true)
        val owner = DataTemplate("copyOwner", "audit")
        owner.scope.putVar("first", top.mcfpp.core.lang.UnknownVar("first").apply { type = first.getType() }, true)
        owner.scope.putVar("second", top.mcfpp.core.lang.UnknownVar("second").apply { type = second.getType() }, true)
        assertEquals(first.nominalIdentity, second.nominalIdentity)
        assertFalse(owner.getType().hasRuntimeRepresentation)
    }

    @Test fun intersectionIdentityEncodesSharedGraphAndIgnoresPhysicalSharing() {
        val constructor = TypeId.Builtin("pair")
        var shared: TypeId = TypeId.Builtin("int")
        repeat(32) { shared = TypeId.Applied(constructor, listOf(shared, shared)) }
        val identity = top.mcfpp.model.compound.UnionDataTemplate.identityFor(listOf(shared)) as TypeId.Declaration
        // The old implementation expands 2^32 paths before it can return any identifier.
        assertTrue(identity.name.length < 32 * 256)
        val left = TypeId.Applied(constructor, listOf(TypeId.Builtin("int")))
        val independent = TypeId.Applied(TypeId.Builtin("pair"), listOf(TypeId.Builtin("int")))
        fun id(a: TypeId, b: TypeId) = top.mcfpp.model.compound.UnionDataTemplate.identityFor(listOf(TypeId.Applied(constructor, listOf(a, b))))
        assertEquals(id(left, left), id(left, independent))
        assertNotEquals(id(left, left), id(left, TypeId.Builtin("Aa")))
        assertNotEquals(id(left, TypeId.Builtin("Aa")), id(left, TypeId.Builtin("BB")))
    }

    private class CountedList<T>(private val values: List<T>) : AbstractList<T>() {
        var visits = 0
        override val size get() = values.size
        override fun get(index: Int): T { visits++; return values[index] }
    }

    @Test fun costVisitsSharedBranchTailOnce() {
        val leaf = CountedList<Instruction>(listOf(Instruction.RawCommand("say leaf")))
        val blocks = arrayListOf<BasicBlock>()
        val condition = ValueRef.Read(TypeId.Builtin("bool"), Place(SymbolId(1)))
        repeat(10) { level ->
            val id = level * 3
            blocks += BasicBlock(id, emptyList(), Terminator.Branch(condition, id + 1, id + 2))
            blocks += BasicBlock(id + 1, emptyList(), Terminator.Jump(id + 3))
            blocks += BasicBlock(id + 2, emptyList(), Terminator.Jump(id + 3))
        }
        blocks += BasicBlock(30, leaf, Terminator.Return(null))
        val cost = TypedMinecraftCost(emptyMap()).cost(TypedIR(0, blocks), CommandCostModel())
        assertEquals(31.0, cost)
        assertEquals(2, leaf.visits) // One macro classification and one instruction-cost pass.
    }

    @Test fun costDoesNotEnumerateRecursiveCallPermutations() {
        val bodies = (1L..7L).associate { id ->
            SymbolId(id) to CountedList(listOf(BasicBlock(0, (1L..7L).filter { it != id }.map {
                Instruction.Call(null, SymbolId(it), emptyList(), Effect.Pure)
            }, Terminator.Return(null))))
        }
        val graph = bodies.mapValues { TypedIR(0, it.value) }
        val estimator = TypedMinecraftCost(graph)
        bodies.values.forEach { it.visits = 0 }
        assertTrue(estimator.cost(graph.values.first(), CommandCostModel()).isFinite())
        assertEquals(2, bodies.values.sumOf { it.visits })
    }

    @Test fun sharedTemplateAncestorsKeepOneDescriptorPerTraversal() {
        var left = DataTemplate("left0", "audit")
        var right = DataTemplate("right0", "audit")
        repeat(10) { level ->
            val nextLeft = DataTemplate("left${level + 1}", "audit")
            val nextRight = DataTemplate("right${level + 1}", "audit")
            nextLeft.parent.addAll(listOf(left, right))
            nextRight.parent.addAll(listOf(left, right))
            left = nextLeft; right = nextRight
        }
        val root = left.getType()
        val seen = Collections.newSetFromMap(IdentityHashMap<MCFPPDataTemplateType, Boolean>())
        fun visit(type: MCFPPDataTemplateType) {
            if (seen.add(type)) type.parentType.filterIsInstance<MCFPPDataTemplateType>().forEach(::visit)
        }
        visit(root)
        assertEquals(21, seen.size)
        assertNotSame(root, left.getType()) // No descriptor survives into an unrelated traversal.
    }

    @Test fun structuralCanonicalizationKeepsDeepSharedConstantsCompact() {
        val constants = arrayListOf<ConstantValue>(ConstantValue.Integral(1))
        repeat(32) { index ->
            val child = ConstantRef(index + 1L)
            constants += ConstantValue.Sequence(listOf(child, child))
        }
        val canonical = TypeTableCodec.canonicalize(LibraryTables(emptyList(), constants), object : CanonicalIdentityBindings {})
        assertEquals(33, canonical.tables.constants.size)
        TypeTableCodec.validate(canonical.tables)
        canonical.tables.constants.filterIsInstance<ConstantValue.Sequence>().forEach {
            assertEquals(it.elements.first(), it.elements.last())
        }
    }

    @Test fun factSnapshotsAndPublishedViewsSurviveLaterWrites() {
        val place = Place(SymbolId(1))
        fun value(number: Long) = ValueFacts(TypeKnowledge.Exact(TypeId.Builtin("int")), ValueKnowledge.Constant(CompilerValue.Integral(number)))
        val current = FlowFacts().apply { write(place, value(1)); setLength(place, 2) }
        val old = current.fork()
        val view = current.entries()
        current.write(place, value(3))
        current.setLength(place, 4)
        assertEquals(value(1), old.read(place))
        assertEquals(value(1), view[place])
        assertEquals(2, old.length(place))
        assertEquals(value(3), current.read(place))
        current.replaceWith(old)
        current.replaceWith(current)
        assertEquals(old, current)
    }

    @Test fun missingFunctionLookupTerminatesOnSharedAndCyclicScopes() {
        val root = SimpleLibScope()
        val left = SimpleLibScope()
        val right = SimpleLibScope()
        root.parent.addAll(listOf(left, right))
        left.parent.add(right)
        right.parent.add(root)
        assertIs<UnknownFunction>(root.getFunction("missing", emptyList(), emptyList()))
        assertTrue(root.getFunctionCandidates("missing").isEmpty())
        assertEquals(top.mcfpp.model.function.ParameterMatcher.TypeSelection.Missing,
            root.getFunctionByTypes("missing", emptyList()))
        // A completed traversal must not suppress a later query of the same scope.
        assertIs<UnknownFunction>(root.getFunction("another", emptyList(), emptyList()))
    }

    @Test fun nestedAndSiblingRecordsRetainIndependentUtf8Payloads() {
        val writer = LibraryBinaryWriter()
        writer.record {
            string("第一段")
            record { string("嵌套 🚀") }
            string("末尾")
        }
        writer.record { string("第二段") }
        val reader = LibraryBinaryReader(writer.toByteArray())
        reader.record {
            assertEquals("第一段", string())
            record { assertEquals("嵌套 🚀", string()) }
            assertEquals("末尾", string())
        }
        reader.record { assertEquals("第二段", string()) }
        reader.requireEnd()
    }

    @Test fun structuralEqualityComparesSharedDagsWithoutUnfoldingThem() {
        var left: TypeId = TypeId.Builtin("int")
        var right: TypeId = TypeId.Builtin("int")
        var leftValue: CompilerValue = CompilerValue.Integral(1)
        var rightValue: CompilerValue = CompilerValue.Integral(1)
        repeat(32) {
            left = TypeId.Applied(TypeId.Builtin("pair"), listOf(left, left))
            right = TypeId.Applied(TypeId.Builtin("pair"), listOf(right, right))
            leftValue = CompilerValue.Sequence(listOf(leftValue, leftValue))
            rightValue = CompilerValue.Sequence(listOf(rightValue, rightValue))
        }
        assertTrue(left == right)
        assertEquals(left.hashCode(), right.hashCode())
        assertTrue(leftValue == rightValue)
        assertEquals(leftValue.hashCode(), rightValue.hashCode())
        assertEquals(left.toString(), right.toString())
        assertEquals(leftValue.toString(), rightValue.toString())
        val shared = CompilerValue.Sequence(listOf(leftValue, leftValue))
        val duplicated = CompilerValue.Sequence(listOf(leftValue, rightValue))
        assertEquals(shared.toString(), duplicated.toString())
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(TypeId.Applied(TypeId.Builtin("Aa"), emptyList()),
            TypeId.Applied(TypeId.Builtin("BB"), emptyList()))
    }

    @Test fun unionHashEqualityAndOrderingKeepSharedUnionGraphsCompact() {
        fun graph(): TypeId {
            var value: TypeId = TypeId.Builtin("int")
            repeat(16) { level ->
                val left = TypeId.Union(setOf(value, TypeId.Builtin("left$level")))
                val right = TypeId.Union(setOf(value, TypeId.Builtin("right$level")))
                value = TypeId.Union(setOf(left, right))
            }
            return value
        }
        val left = graph()
        val right = graph()
        assertTrue(left == right)
        assertEquals(left.hashCode(), right.hashCode())
        assertEquals(left.toString(), right.toString())
    }

    @Test fun unchangedPassRetainsItsAnalysisOwner() {
        val ir = TypedIR(0, listOf(BasicBlock(0, emptyList(), Terminator.Return(null))))
        val context = PassContext(OptimizationConfig())
        val facts = context.flow(ir)
        val manager = PassManager(listOf(object : FunctionPass {
            override val id = PassIds.CONSTANTS
            override fun run(ir: TypedIR, context: PassContext) = ir.copy()
        }))
        val result = manager.run(ir, context)
        assertSame(ir, result)
        assertSame(facts, context.flow(result))
    }
}
