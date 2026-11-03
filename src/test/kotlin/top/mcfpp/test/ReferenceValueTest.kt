package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.ref.*
import top.mcfpp.lib.EntityTarget
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class ReferenceValueTest {
    @Test fun referencesHaveDistinctInvariantTypesAndRejectUnstorablePointees() {
        val parent = DataTemplate("Parent", "refs")
        val child = DataTemplate("Child", "refs").apply { extends(parent) }
        val parentRef = MCFPPNBTRefType(parent.getType())
        val childRef = MCFPPNBTRefType(child.getType())
        assertTrue(child.getType().isSubOf(parent.getType()))
        assertFalse(childRef.isSubOf(parentRef))
        assertNull(TypeRelations.resolveImplicitConversion(childRef, parentRef))
        val nbtInt = MCFPPNBTRefType(MCFPPBaseType.Int)
        assertNotEquals<MCFPPType>(MCFPPScoreRefType, nbtInt)
        assertNull(TypeRelations.resolveImplicitConversion(MCFPPBaseType.Int, MCFPPScoreRefType))
        assertNull(TypeRelations.resolveImplicitConversion(MCFPPBaseType.Int, nbtInt))
        assertNull(TypeRelations.resolveImplicitConversion(MCFPPScoreRefType, nbtInt))
        assertEquals(nbtInt, MCFPPNBTRefType(MCFPPBaseType.Int))
        assertFailsWith<IllegalArgumentException> { MCFPPNBTRefType(MCFPPPrivateType.Void) }
        assertFailsWith<IllegalArgumentException> { MCFPPNBTRefType(MCFPPEntityType.NormalSelector) }
        val function = MCFPPFunctionType(emptyList(), MCFPPBaseType.Int)
        assertEquals(function, MCFPPNBTRefType(function).valueType)
    }

    @Test fun unknownMergesAndWritesDiscardStaticAddressClaims() {
        val type = TypeKnowledge.Exact(MCFPPScoreRefType.typeId)
        val holder = ReferenceDescriptor(ReferenceKind.SCORE_HOLDER, CompilerValue.Record(emptyMap()))
        val entity = ReferenceDescriptor(ReferenceKind.SCORE_ENTITY, CompilerValue.Record(emptyMap()))
        val holderMetadata = ReferenceMetadata(setOf(holder))
        val known = ValueFacts(type, ValueKnowledge.Unknown, reference = holderMetadata)
        val unknown = ValueFacts(type, ValueKnowledge.Unknown)
        assertTrue(known.join(unknown).reference!!.descriptors.isEmpty())
        assertTrue(holderMetadata.join(ReferenceMetadata()).descriptors.isEmpty())
        val root = Place(SymbolId.fresh())
        val facts = FlowFacts()
        facts.initialize(root, ValueFacts(TypeKnowledge.Exact(MCFPPListType(MCFPPScoreRefType).typeId), ValueKnowledge.Unknown))
        facts.initialize(root.index(0), known)
        facts.initialize(root.index(1), known.copy(reference = ReferenceMetadata(setOf(entity))))
        facts.setLength(root, 2)
        facts.write(root.unknownIndex(), known)
        assertEquals(setOf(holder, entity), facts.entries().getValue(root.unknownIndex()).reference!!.descriptors)
        facts.invalidate(root)
        assertTrue(facts.read(root.index(0))!!.reference!!.descriptors.isEmpty())
    }

    @Test fun sourceTypeArgumentsAcceptNamedAndNestedStorageTypes() {
        MCFPPStringTest.readFromString("""
            data Payload { var value: int }
            func consume(score: ScoreRef, nested: NBTRef<list<int>>, named: NBTRef<Payload>) {}
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val params = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("consume").single().normalParams
        assertEquals(MCFPPScoreRefType, params[0].type)
        assertEquals(MCFPPNBTRefType(MCFPPListType(MCFPPBaseType.Int)), params[1].type)
        assertIs<MCFPPNBTRefType>(params[2].type)
    }

    @Test fun runtimeDescriptorsOwnCapturedFieldsAcrossCopiesRebindingAndContainers() = compileCommands { function ->
        val objective = runtimeText("objective", "hp")
        val reference = ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_HOLDER,
            linkedMapOf("holder" to text("Alice"), "objective" to objective), name = "original")
        assertNull(StorageAccess.snapshot(reference))
        assertTrue(StorageAccess.hasRuntimeRepresentation(reference))
        val copy = MCFPPScoreRefType.buildUnConcrete("copy")
        StorageAccess.write(copy, reference)
        val list = StorageAccess.listLiteral(MCFPPListType(MCFPPScoreRefType), listOf(copy), "references")
        val record = StorageAccess.dictionaryLiteral(MCFPPDictType(MCFPPScoreRefType), mapOf("saved" to copy), "record")
        val other = ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_HOLDER,
            linkedMapOf("holder" to text("Bob"), "objective" to text("mana")), name = "other")
        StorageAccess.write(reference, other)
        StorageAccess.write(objective, text("changed"))
        StorageAccess.materialize(list)
        StorageAccess.materialize(record)
        val extracted = StorageAccess.element(list, StorageAccess.literal(MCFPPBaseType.Int, CompilerValue.Integral(0)), MCFPPScoreRefType)
        assertEquals(ReferenceKind.SCORE_HOLDER, ReferenceValues.descriptor(extracted)?.kind)
        val captured = StorageAccess.capturePayload(MCFPPScoreRefType, copy, "argument")!!
        StorageAccess.materialize(captured)
        val machine = execute(function)
        assertEquals("hp", machine.field(copy, "objective"))
        assertEquals("Alice", machine.field(copy, "holder"))
        assertEquals("mana", machine.field(reference, "objective"))
        assertEquals("Bob", machine.field(reference, "holder"))
        assertEquals("hp", machine.field(captured, "objective"))
        assertEquals("hp", (machine.readNbt("mcfpp:system", path(list) + "[0].objective") as StringTag).value)
        assertEquals("hp", (machine.readNbt("mcfpp:system", path(record) + ".saved.objective") as StringTag).value)
        assertEquals(0, Project.errorCount)
    }

    @Test fun staticAddressKnowledgeAndEntityFactsSurviveOrdinaryStorage() = compileCommands { _ ->
        val target = SelectorVar(EntityTarget.PlayerName(text("Alice")))
        val reference = ReferenceValues.create(MCFPPNBTRefType(MCFPPNBTType.Short), ReferenceKind.NBT_ENTITY,
            mapOf("path" to text("Air")), target, "air")
        val copy = MCFPPNBTRefType(MCFPPNBTType.Short).buildUnConcrete("copy")
        StorageAccess.write(copy, reference)
        assertEquals(ReferenceValues.descriptor(reference), ReferenceValues.descriptor(copy))
        assertTrue(ReferenceValues.metadata(copy)!!.targetFacts.playersOnly)
        assertTrue(ReferenceValues.metadata(copy)!!.targetFacts.selectingSingleEntity)
        val restoredTarget = assertIs<EntityTarget.PlayerName>(ReferenceValues.entityTarget(copy))
        assertEquals("Alice", restoredTarget.name.value.value)
        val list = StorageAccess.listLiteral(MCFPPListType(copy.type), listOf(copy), "staticReferences")
        val extracted = StorageAccess.element(list, StorageAccess.literal(MCFPPBaseType.Int, CompilerValue.Integral(0)), copy.type)
        assertTrue(ReferenceValues.metadata(extracted)!!.targetFacts.playersOnly)
        assertEquals(ReferenceValues.descriptor(copy), ReferenceValues.descriptor(extracted))
        assertEquals(0, Project.errorCount)
    }

    private fun compileCommands(body: (Function) -> Unit) {
        MCFPPStringTest.readFromString("func main() {}", version = "26.3")
        assertEquals(0, Project.errorCount)
        val function = Function("referenceValues", "test", null)
        function.runInFunction {
            Function.addCommand("data modify storage mcfpp:system stack_frame prepend value {}")
            body(function)
        }
    }
    private fun text(value: String): MCString = StorageAccess.literal(MCFPPBaseType.String, CompilerValue.Text(value)) as MCString
    private fun runtimeText(name: String, value: String): MCString = MCString(name).apply {
        nbtPath = NBTPath.stack.intIndex(0).memberIndex(name)
        Function.addCommand(Commands.dataSetValue(nbtPath, StringTag(value)))
        StorageAccess.publishNbt(this)
    }
    private fun execute(function: Function) = ScoreCommandExecutor(function.commands.analyzeAll().filterNot { it.startsWith("#") })
    private fun path(value: Var<*>) = StorageAccess.ensure(value).path.toCommandPart().toString().removePrefix("storage mcfpp:system ")
    private fun ScoreCommandExecutor.field(value: Var<*>, name: String) = (readNbt("mcfpp:system", path(value) + ".$name") as StringTag).value
}
