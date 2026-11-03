package top.mcfpp.test.context

import top.mcfpp.core.lang.context.*
import top.mcfpp.lib.EntityFacts
import top.mcfpp.model.Member
import top.mcfpp.mni.api.ExecutionContextFrame
import top.mcfpp.mni.api.ExecutionContextId
import top.mcfpp.mni.api.SessionId
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.TypeRelations
import kotlin.test.*

class ExecutionContextModelTest {
    @Test fun atAndAsKeepIndependentComponentsAndAmbientAbsence() {
        ContextSession().use { session ->
            val ambient = session.root(ExecutorPresence.NONE)
            val posScope = session.openScope()
            val position = ambient.withPosContext(posScope)
            assertEquals(ExecutorPresence.NONE, position.executor.presence)
            assertEquals(0, BoundExecutorVar("self", position.executor).entityFacts.upperBound)
            assertEquals(ambient.components.executor, position.components.executor)
            assertEquals(position.source, position.components.position)
            assertEquals(position.source, position.components.rotation)
            assertEquals(position.source, position.components.dimension)
            assertNotEquals(ambient.source, position.source)

            val entity = position.withEntityContext(session.openScope(posScope), EntityFacts.PLAYER)
            assertEquals(ExecutorPresence.PRESENT, entity.executor.presence)
            assertEquals(entity.source, entity.components.executor)
            assertEquals(position.components.position, entity.components.position)
            assertEquals(position.components.rotation, entity.components.rotation)
            assertEquals(position.components.dimension, entity.components.dimension)
            assertTrue(BoundExecutorVar("self", entity.executor).entityFacts.playersOnly)
        }
    }

    @Test fun nestedCallbacksFormAnInstanceTreeWithStableNominalTypes() {
        ContextSession().use { session ->
            val root = session.root()
            val first = root.withPosContext(session.openScope())
            val second = first.withPosContext(session.openScope(first.scope))
            val relation = second.onRelation(ContextRelation.PASSENGERS, session.openScope(second.scope))
            assertSame(root, first.parent)
            assertSame(first, second.parent)
            assertSame(second, relation.parent)
            assertEquals(ContextTypes.PosContext, ContextTypes.of(first))
            assertEquals(ContextTypes.PosContext, ContextTypes.of(second))
            assertEquals(ContextTypes.RelationContext, ContextTypes.of(relation))
            assertTrue(TypeRelations.isSubtype(ContextTypes.RelationContext, ContextTypes.EntityContext))
            assertTrue(TypeRelations.isSubtype(ContextTypes.RelationContext, ContextTypes.ExecutionContext))
            assertFalse(TypeRelations.isSubtype(ContextTypes.PosContext, ContextTypes.EntityContext))
            assertEquals(ExecutorPresence.PRESENT, relation.executor.presence)
            assertEquals(second.components.position, relation.components.position)
        }
    }

    @Test fun ancestorBorrowsStayBoundWhileSiblingEscapedAndForeignUsesFail() {
        ContextSession().use { session ->
            val root = session.root()
            val scope = session.openScope()
            val context = root.withPosContext(scope)
            val nested = session.openScope(scope)
            context.executor.requireUsableIn(nested)
            context.pos.requireUsableIn(nested)
            assertFailsWith<ContextAccessException> { context.requireUsableIn(session.rootScope) }
            assertFailsWith<ContextAccessException> { context.pos.requireUsableIn(session.openScope()) }
            ContextSession().use { other ->
                assertFailsWith<ContextAccessException> { context.executor.requireUsableIn(other.rootScope) }
                assertFailsWith<ContextAccessException> { session.openScope(other.rootScope) }
            }
            assertFailsWith<ContextAccessException> { scope.close() }
            nested.close()
            scope.close()
            assertFailsWith<ContextAccessException> { context.pos.requireUsableIn(scope) }
            assertFailsWith<ContextAccessException> { context.withEntityContext(session.openScope()) }
        }
    }

    @Test fun inheritedPositionIsDirectWhileChangedExecutorOrSpaceNeedsPreservation() {
        ContextSession().use { session ->
            val root = session.root()
            val pos = root.withPosContext(session.openScope())
            val passengers = pos.onRelation(ContextRelation.PASSENGERS, session.openScope(pos.scope))
            val encoder = DirectContextCommandEncoder { passengers }
            assertEquals("~ ~ ~", encoder.position(pos.pos).toString())
            assertEquals("@s", encoder.executor(passengers.executor).toString())
            assertFailsWith<ContextAccessException> { encoder.executor(pos.executor) }
            assertSame(pos.components.position, pos.pos.provenance)
            assertSame(pos.components.dimension, pos.pos.dimension)

            val moved = passengers.withPosContext(session.openScope(passengers.scope))
            assertFailsWith<ContextAccessException> { pos.pos.requireDirectUseIn(moved) }
            passengers.executor.requireDirectUseIn(moved)
            // Equal coordinates would not make these identities interchangeable.
            assertNotEquals(pos.pos.provenance, moved.pos.provenance)
            assertNotEquals(pos.pos.dimension, moved.pos.dimension)
        }
    }

    @Test fun sourceAdaptersPreserveBindingAndScopeThroughMembersAliasesAndUpcasts() {
        ContextSession().use { session ->
            val pos = session.root().withPosContext(session.openScope())
            val nested = pos.onRelation(ContextRelation.PASSENGERS, session.openScope(pos.scope))
            val source = ExecutionContextVar("position", pos, encoder = DirectContextCommandEncoder { nested })
            val alias = source.borrow(nested.scope, "alias")
            assertNotSame(source, alias)
            assertSame(pos, alias.context)
            val upcast = assertIs<ExecutionContextVar>(alias.implicitCast(ContextTypes.ExecutionContext))
            assertSame(pos, upcast.context)
            assertEquals(ContextTypes.ExecutionContext, upcast.type)
            val position = assertIs<BoundPositionVar>(upcast.getMemberVar("pos", Member.AccessModifier.PUBLIC).first)
            assertSame(pos.pos, position.binding)
            assertEquals("~ ~ ~", position.toCommandPart().toString())
            val executor = assertIs<BoundExecutorVar>(source.getMemberVar("executor", Member.AccessModifier.PUBLIC).first)
            assertEquals(MCFPPEntityType.NormalSelector, executor.type)
            assertSame(pos.executor, executor.clone().binding)
            assertFailsWith<ContextAccessException> { executor.toEntityTargetCommand() }
            for (value in listOf(source, position, executor)) {
                assertFalse(value.canImplicitCast(MCFPPBaseType.Any))
                assertFalse(value.canExplicitCast(MCFPPBaseType.Object))
                assertFailsWith<ContextAccessException> { value.storeToStack() }
            }
            assertFalse(ContextTypes.ExecutionContext.hasRuntimeRepresentation)
            assertFalse(ContextTypes.Position.hasRuntimeRepresentation)
        }
    }

    @Test fun declarationsRequireBindingBeforeProvidingContextMembersOrCommands() {
        ContextSession().use { session ->
            val context = session.root().withPosContext(session.openScope())
            val declaration = assertIs<ExecutionContextVar>(ContextTypes.PosContext.buildUnConcrete("ctx"))
            assertFailsWith<ContextAccessException> { declaration.requireContext() }
            val bound = declaration.bind(context)
            assertSame(context, bound.requireContext())
            assertFailsWith<ContextAccessException> { BoundExecutorVar("self", context.executor).toCommandPart() }
            assertFailsWith<ContextAccessException> { BoundPositionVar("position", context.pos).toCommandPart() }
            val positionDeclaration = assertIs<BoundPositionVar>(ContextTypes.Position.buildUnConcrete("pos"))
            assertSame(context.pos, positionDeclaration.bind(context.pos).requireBinding())
        }
    }

    @Test fun mniFrameTransitionsPreserveIndependentDimensionAndRejectForeignDimension() {
        val session = SessionId(901)
        val root = ExecutionContextFrame.root(ExecutionContextId(session, 1))
        val positioned = root.withPosition(ExecutionContextId(session, 2))
        assertEquals(root.dimension, positioned.dimension)
        val changedDimension = positioned.withDimension(ExecutionContextId(session, 3))
        assertEquals(positioned.position, changedDimension.position)
        assertEquals(positioned.rotation, changedDimension.rotation)
        val executor = changedDimension.withExecutor(ExecutionContextId(session, 4))
        val rotation = executor.withRotation(ExecutionContextId(session, 5))
        assertEquals(changedDimension.dimension, rotation.dimension)
        val atEntity = rotation.atEntity(ExecutionContextId(session, 6))
        assertEquals(rotation.executor, atEntity.executor)
        assertEquals(atEntity.id, atEntity.position)
        assertEquals(atEntity.id, atEntity.rotation)
        assertEquals(atEntity.id, atEntity.dimension)
        assertFailsWith<IllegalArgumentException> {
            root.copy(dimension = ExecutionContextId(SessionId(902), 1))
        }
    }
}
