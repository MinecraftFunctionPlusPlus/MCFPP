package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.util.IdentityHashMap

/** Transient adapter only: no frontend handle or callback is retained by the resulting plan. */
internal class ContextLowerer(private val arena: CodeArena, private val scope: (ScopeId) -> PlanScope) {
    private val identities = linkedMapOf<ExecutionContextId, PlanContextId>()
    private val frames = linkedMapOf<ExecutionContextId, PlanExecutionFrame>()
    private val visiting = java.util.Collections.newSetFromMap(IdentityHashMap<Code, Boolean>())
    fun frames(): List<PlanExecutionFrame> = frames.values.toList()
    private fun fail(code: Code, message: String): Nothing = throw CodeModelException(code.source(), message)
    private fun site(code: Code) = (code as OwnedCode).metadata.origin.let { PlanSite(it.callSite, it.generatedFrom, it.expansionFrames) }
    private fun component(id: ExecutionContextId) = identities.getOrPut(id) { PlanContextId(identities.size) }

    /** Opaque legacy IDs keep their existing lookup identity, without inventing frame components. */
    fun frame(id: ExecutionContextId?, useScope: ScopeId, code: Code): PlanExecutionFrame? {
        if (id == null || !arena.hasContextFrame(id, code.source())) return null
        val actual = arena.contextFrame(id, useScope, code.source())
        return frames.getOrPut(id) {
            var declaration = useScope
            while (true) {
                val parent = arena.scopeParent(declaration) ?: break
                try {
                    arena.contextFrame(id, parent, code.source())
                } catch (_: CodeModelException) {
                    break
                }
                declaration = parent
            }
            PlanExecutionFrame(
                component(actual.id),
                actual.id,
                component(actual.executor),
                component(actual.position),
                component(actual.rotation),
                scope(declaration),
                component(actual.dimension)
            )
        }
    }

    fun frame(descriptor: ExecutionContextFrame, code: Code): PlanExecutionFrame {
        val owned = (code as OwnedCode).metadata
        val actual = arena.contextFrame(descriptor.id, owned.scopeId, code.source())
        if (actual != descriptor) fail(code, "Execution frame differs from its allocated components")
        return frame(descriptor.id, owned.scopeId, code) ?: fail(code, "Execution frame has no allocation")
    }

    private inline fun <T> visit(code: Code, action: () -> T): T {
        if (!visiting.add(code)) fail(code, "Cyclic Minecraft handle in lowering")
        try {
            return action()
        } finally {
            visiting.remove(code)
        }
    }

    fun entity(handle: EntityHandle): PlanEntity {
        val descriptor = handle as? ImmutableEntityHandle ?: fail(handle, "Unsupported entity handle")
        val meta = descriptor.metadata
        val frame = frame(meta.executionContextId, meta.scopeId, handle) ?: fail(handle, "Entity has no allocated frame")
        if (component(descriptor.executor) != frame.executor) fail(handle, "Entity lost its executor component")
        return PlanEntity(frame.executor, scope(meta.scopeId), site(handle))
    }

    fun query(handle: EntitySet): PlanEntityQuery {
        val query = (handle as? ImmutableEntitySet)?.query ?: fail(handle, "Unsupported entity collection")
        return PlanEntityQuery(query.selection, query.entityType, query.tags, query.limit, query.sort)
    }

    fun position(handle: PositionHandle): PlanPosition = visit(handle) {
        val descriptor = handle as? ImmutablePositionHandle ?: fail(handle, "Unsupported position handle")
        val node = when (val node = descriptor.node) {
            is PositionNode.Absolute -> PlanPositionNode.Absolute(node.x, node.y, node.z)
            is PositionNode.Current -> PlanPositionNode.Current(component(node.context))
            is PositionNode.Entity -> PlanPositionNode.Entity(entity(node.entity))
            is PositionNode.Offset -> PlanPositionNode.Offset(position(node.base), node.x, node.y, node.z)
            is PositionNode.LocalOffset -> PlanPositionNode.LocalOffset(position(node.base), rotation(node.rotation), node.left, node.up, node.forward)
        }
        PlanPosition(node, scope(descriptor.metadata.scopeId), site(handle))
    }

    fun rotation(handle: RotationHandle): PlanRotation = visit(handle) {
        val descriptor = handle as? ImmutableRotationHandle ?: fail(handle, "Unsupported rotation handle")
        val node = when (val node = descriptor.node) {
            is RotationNode.Absolute -> PlanRotationNode.Absolute(node.yaw, node.pitch)
            is RotationNode.Current -> PlanRotationNode.Current(component(node.context))
            is RotationNode.Entity -> PlanRotationNode.Entity(entity(node.entity))
        }
        PlanRotation(node, scope(descriptor.metadata.scopeId), site(handle))
    }

    fun block(handle: BlockHandle): PlanBlockPosition {
        val descriptor = handle as? ImmutableBlockHandle ?: fail(handle, "Unsupported block handle")
        return PlanBlockPosition(position(descriptor.position), scope(descriptor.metadata.scopeId), site(handle))
    }

    fun predicate(predicate: BlockPredicate) = PlanBlockPredicate(predicate.resource, predicate.isTag, predicate.states)

    fun score(target: MinecraftScoreTarget, objective: String, type: PlanType, code: Code): PlanScoreAddress {
        val meta = (code as OwnedCode).metadata
        val destination = when (target) {
            is MinecraftScoreTarget.Fixed -> PlanScoreTarget.Fixed(target.holder.name)
            is MinecraftScoreTarget.Entity -> PlanScoreTarget.Entity(entity(target.entity))
        }
        return PlanScoreAddress(destination, objective, type, frame(meta.executionContextId, meta.scopeId, code), scope(meta.scopeId))
    }
}
