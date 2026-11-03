package top.mcfpp.test.entity

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EntityFacts
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntityTarget
import top.mcfpp.mni.annotation.MCFPPEntity
import top.mcfpp.model.Member
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import kotlin.test.*

class EntityIdentityTest {
    @Test
    fun targetPropertiesDoNotCreateDifferentPublicTypes() = inFunction {
        val identity = TypeId.Builtin("entity")
        for (type in listOf(MCFPPEntityType.NormalSelector, MCFPPEntityType.SpecifiedEntity,
            MCFPPEntityType(8, listOf("minecraft:zombie")), MCFPPEntityType(1, null, true),
            MCFPPEntityType.Player)) {
            assertEquals(identity, type.typeId)
            assertEquals("entity", type.typeName)
        }
        assertSame(MCFPPEntityType.NormalSelector, MCFPPType.resolveTypeId(identity))
        assertEquals(SelectorVar(EntitySelector('a')).type, SelectorVar(EntitySelector('s')).type)
        assertEquals(EntityFacts.PLAYER, EntityTarget.PlayerName(MCString(StringTag("Alex"))).facts)
        assertEquals(EntityFacts.SINGLE,
            EntityTarget.UUIDText(MCString(StringTag("00000000-0000-0000-0000-000000000001"))).facts)
    }

    @Test
    fun explicitLimitsReplaceSelectorDefaultsAndUnknownLimitsProveNothing() = inFunction {
        for (kind in listOf('p', 'r', 'n', 's')) {
            assertTrue(EntitySelector(kind).selectingSingleEntity(), "@$kind default")
            assertEquals(1, EntitySelector(kind).getLimit())
        }
        for (kind in listOf('p', 'r', 'n', 'a', 'e')) {
            assertTrue(EntitySelector(kind).limit(1).selectingSingleEntity(), "@$kind limit=1")
            assertFalse(EntitySelector(kind).limit(3).selectingSingleEntity(), "@$kind limit=3")
            assertEquals(3, EntitySelector(kind).limit(3).getLimit())
        }
        val runtimeLimit = MCInt("runtimeLimit")
        StorageAccess.bindIncomingParameter(runtimeLimit)
        for (kind in listOf('p', 'r', 'n', 's', 'a', 'e')) {
            val selector = EntitySelector(kind).limit(runtimeLimit)
            assertNull(selector.facts().upperBound, "@$kind dynamic limit")
            assertFalse(selector.selectingSingleEntity())
        }
        assertFalse(EntitySelector('a').selectingSingleEntity())
        assertTrue(EntitySelector('p').onlyIncludingPlayers())
        assertFalse(EntitySelector('s').onlyIncludingPlayers())
    }

    @Test
    fun unknownEntityParameterDoesNotAcquireASelectorOrSingleTargetFact() = inFunction {
        val incoming = MCFPPEntityType(1, listOf("minecraft:player"), true)
            .buildUnConcrete("incoming") as SelectorVar
        StorageAccess.bindIncomingParameter(incoming)
        assertNull(incoming.entityTargetOrNull())
        assertEquals(EntityFacts.UNKNOWN, incoming.entityFacts)
        assertFalse(incoming.isPlayer())
        assertFailsWith<IllegalStateException> { incoming.value }
    }

    @Test
    fun restoredSelectorMutationPersistsAndAssignmentPreservesTheCopy() = inFunction {
        val original = SelectorVar(EntitySelector('e'))
        original.value.limit(1)
        assertTrue(original.entityFacts.selectingSingleEntity)
        assertEquals("@e[limit=1]", original.toCommandPart().toString())
        val frozen = assertNotNull(StorageAccess.snapshot(original))
        val restored = StorageAccess.restore(original.type, frozen, "restored") as SelectorVar
        restored.value.tag("saved", false)
        assertEquals("@e[limit=1,tag=saved]", restored.toCommandPart().toString())
        assertEquals("@e[limit=1]", original.toCommandPart().toString())
        val copied = StorageAccess.write(SelectorVar("copied"), restored) as SelectorVar
        restored.value.tag("later", false)
        assertEquals("@e[limit=1,tag=saved]", copied.toCommandPart().toString())
    }

    @Test
    fun nonpositiveLimitsAreDiagnosedAndDoNotProveSingleTarget() = inFunction {
        for (limit in listOf(0, -1)) {
            val before = Project.errorCount
            val selector = EntitySelector('p').limit(limit)
            assertEquals(before + 1, Project.errorCount)
            assertNull(selector.facts().upperBound)
            assertFalse(selector.selectingSingleEntity())
        }
    }

    @Test
    fun unknownKindExposesCommonMembersWithoutMergingEntityTemplates() = inFunction {
        val namespace = Namespace("mcfpp.minecraft.entity")
        val common = DataTemplate("EntityData", namespace.identifier).apply {
            val field = MCInt("commonField")
            addMember(field)
            addMember(Property.buildSimpleProperty(field))
        }
        val zombie = DataTemplate("ZombieData", namespace.identifier).apply {
            annotations.add(MCFPPEntity())
            val field = MCInt("zombieField")
            addMember(field)
            addMember(Property.buildSimpleProperty(field))
        }
        namespace.scope.addTemplate(common.identifier, common)
        namespace.scope.addTemplate(zombie.identifier, zombie)
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        val unknown = SelectorVar(EntitySelector('e'), "unknown")
        assertNull(unknown.entityFacts.knownKinds)
        assertFalse(unknown.entityFacts.selectingSingleEntity)
        val data = assertIs<top.mcfpp.core.lang.entity.EntityDataAddress>(unknown.getMemberVar("dat", Member.AccessModifier.PUBLIC).first)
        assertNotNull(data.getMemberVar("commonField", Member.AccessModifier.PUBLIC).first)
        assertNull(data.getMemberVar("zombieField", Member.AccessModifier.PUBLIC).first)
        assertNull(SelectorVar.declarationData(MCFPPEntityType(1, listOf("minecraft:zombie")))
            .scope.getVar("zombieField"))
    }

    private fun inFunction(action: () -> Unit) {
        Project.init()
        Function("entity_identity", "fixture.entity", null).runInFunction(action)
    }
}
