package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.util.IdentityHashMap

/** Checks execution capabilities without allocating nodes or changing compiler state. */
internal class ContextValidator(private val arena: CodeArena) {
    private val declarations = linkedMapOf<ExecutionContextId, ImmutableStmt>()
    private val visiting = java.util.Collections.newSetFromMap(IdentityHashMap<Code, Boolean>())

    private fun fail(code: Code, message: String): Nothing = throw CodeModelException(code.source(), message)

    private fun frame(id: ExecutionContextId?, scope: ScopeId, code: Code): ExecutionContextFrame {
        if (id == null) fail(code, "Minecraft operation requires an execution frame")
        return try {
            arena.contextFrame(id, scope, code.source())
        } catch (error: IllegalArgumentException) {
            fail(code, error.message ?: "Execution frame is unavailable")
        }
    }

    /** A visibility probe; callers must not use a miss as evidence of an unregistered ID. */
    private fun visibleFrame(id: ExecutionContextId, scope: ScopeId): ExecutionContextFrame? {
        val source = SourceSpan.unknown()
        if (!arena.hasContextFrame(id, source)) return null
        return try {
            arena.contextFrame(id, scope, source)
        } catch (_: CodeModelException) {
            null
        }
    }

    /** Legacy opaque IDs retain their old equality rule; registered frames compare executors. */
    fun sameExecutor(bound: ExecutionContextId?, current: ExecutionContextId?, scope: ScopeId): Boolean {
        if (bound == null) return true
        if (current == null) return false
        if (bound == current) return !arena.hasContextFrame(bound, SourceSpan.unknown()) || visibleFrame(bound, scope) != null
        val first = visibleFrame(bound, scope) ?: return false
        val second = visibleFrame(current, scope) ?: return false
        return first.executor == second.executor
    }

    fun metadata(code: Code, scope: ScopeId) {
        val meta = (code as OwnedCode).metadata
        meta.executionContextId?.let { id ->
            if (arena.hasContextFrame(id, code.source())) {
                frame(id, meta.scopeId, code)
                // Nested blocks are checked from their parent before entering their own scope.
                if (arena.isVisible(meta.scopeId, scope)) frame(id, scope, code)
            }
        }
    }

    /** These nodes derive their binding from their dependencies, which the main checker visits. */
    fun dependencyMetadata(code: Code, current: ExecutionContextId?, scope: ScopeId): Boolean {
        val transparent = code is Ref<*> ||
            code is ImmutableExpr<*> &&
            when (code.node) {
                is ExprNode.Literal, is ExprNode.Read, is ExprNode.ScoreRead, is ExprNode.BlockMatches,
                is ExprNode.Unary, is ExprNode.Binary, is ExprNode.Convert, is ExprNode.Field, is ExprNode.Index -> true
                else -> false
            }
        if (!transparent) return false
        val bound = (code as OwnedCode).metadata.executionContextId ?: return true
        if (visibleFrame(bound, scope) == null) return false
        return current == null || visibleFrame(current, scope) != null
    }

    private fun owned(code: Code, scope: ScopeId): ExecutionContextFrame? {
        val meta = (code as OwnedCode).metadata
        if (meta.sessionId != arena.sessionId || !arena.containsScope(meta.scopeId))
            fail(code, "Minecraft handle belongs to another session or unknown scope")
        if (!arena.isVisible(meta.scopeId, scope)) fail(code, "Minecraft handle is outside its lexical scope")
        return meta.executionContextId?.let {
            // A capability must be visible both where it was made and where it is used.
            frame(it, meta.scopeId, code)
            frame(it, scope, code)
        }
    }

    private inline fun visit(code: Code, action: () -> Unit) {
        if (!visiting.add(code)) fail(code, "Cyclic Minecraft handle dependency")
        try {
            action()
        } finally {
            visiting.remove(code)
        }
    }

    fun targets(targets: EntitySet, scope: ScopeId) {
        owned(targets, scope)
    }

    fun entity(entity: EntityHandle, scope: ScopeId, current: ExecutionContextId?): Unit = visit(entity) {
        val owner = owned(entity, scope) ?: fail(entity, "An entity requires its bound execution frame")
        val descriptor = entity as ImmutableEntityHandle
        if (owner.executor != descriptor.executor) fail(entity, "Entity executor differs from its bound execution frame")
        if (frame(current, scope, entity).executor != descriptor.executor)
            fail(entity, "Entity reference cannot cross executeAs without a stable identity")
    }

    fun position(handle: PositionHandle, scope: ScopeId, current: ExecutionContextId?): Unit = visit(handle) {
        val owner = owned(handle, scope)
        when (val node = (handle as ImmutablePositionHandle).node) {
            is PositionNode.Absolute -> Unit
            is PositionNode.Current -> {
                if (owner?.position != node.context) fail(handle, "Position lost its bound context component")
                if (frame(current, scope, handle).position != node.context)
                    fail(handle, "Position reference cannot be rebound to a different position context")
            }
            is PositionNode.Entity -> entity(node.entity, scope, current)
            is PositionNode.Offset -> position(node.base, scope, current)
            is PositionNode.LocalOffset -> {
                position(node.base, scope, current)
                rotation(node.rotation, scope, current)
            }
        }
    }

    fun rotation(handle: RotationHandle, scope: ScopeId, current: ExecutionContextId?): Unit = visit(handle) {
        val owner = owned(handle, scope)
        when (val node = (handle as ImmutableRotationHandle).node) {
            is RotationNode.Absolute -> Unit
            is RotationNode.Current -> {
                if (owner?.rotation != node.context) fail(handle, "Rotation lost its bound context component")
                if (frame(current, scope, handle).rotation != node.context)
                    fail(handle, "Rotation reference cannot be rebound to a different rotation context")
            }
            is RotationNode.Entity -> entity(node.entity, scope, current)
        }
    }

    fun block(block: BlockHandle, scope: ScopeId, current: ExecutionContextId?): Unit = visit(block) {
        owned(block, scope)
        position((block as ImmutableBlockHandle).position, scope, current)
    }

    fun score(target: MinecraftScoreTarget, code: Code, scope: ScopeId, current: ExecutionContextId?) {
        val owner = owned(code, scope)
        when (target) {
            is MinecraftScoreTarget.Fixed -> Unit
            is MinecraftScoreTarget.Entity -> {
                val executor = (target.entity as ImmutableEntityHandle).executor
                if (owner?.executor != executor) fail(code, "Score lost its bound entity executor")
                entity(target.entity, scope, current)
            }
        }
    }

    /** Return the actual child capability after checking components and its declaring scope. */
    fun transition(stmt: ImmutableStmt, current: ExecutionContextId?, scope: ScopeId): ExecutionContextFrame {
        val parent: ExecutionContextFrame
        val child: ExecutionContextFrame
        val body: Block
        val expected: ExecutionContextFrame
        when (val node = stmt.node) {
            is StmtNode.ExecuteAs -> {
                parent = node.parentContext
                child = node.context
                body = node.body
                expected = parent.withExecutor(child.id)
            }
            is StmtNode.At -> {
                parent = node.parentContext
                child = node.context
                body = node.body
                expected = parent.withPosition(child.id)
            }
            is StmtNode.Rotated -> {
                parent = node.parentContext
                child = node.context
                body = node.body
                expected = parent.withRotation(child.id)
            }
            else -> fail(stmt, "Unsupported execution context statement")
        }
        if (current != parent.id || frame(parent.id, scope, stmt) != parent)
            fail(stmt, "Context statement lost its parent frame")
        if (child.id == parent.id || child != expected) fail(stmt, "Context statement changes the wrong execution components")
        val meta = (body as OwnedCode).metadata
        if (meta.sessionId != arena.sessionId || !arena.containsScope(meta.scopeId) || meta.scopeId == scope || !arena.isVisible(scope, meta.scopeId))
            fail(stmt, "Context body requires its own child scope")
        if (meta.executionContextId != child.id || frame(child.id, meta.scopeId, stmt) != child)
            fail(stmt, "Context body lost its allocated execution frame")
        // Visibility at the body but not its immediate parent proves exact declaration ownership.
        arena.scopeParent(meta.scopeId)?.let { ancestor ->
            if (visibleFrame(child.id, ancestor) != null) fail(stmt, "Execution frame was declared outside its context body")
        }
        val previous = declarations.putIfAbsent(child.id, stmt)
        if (previous != null && previous !== stmt) fail(stmt, "Execution context is reused by another declaration")
        when (val node = stmt.node) {
            is StmtNode.ExecuteAs -> {
                targets(node.targets, scope)
                if ((node.self as OwnedCode).metadata.scopeId != meta.scopeId) fail(node.self, "Self is outside its context body")
                entity(node.self, meta.scopeId, child.id)
            }
            is StmtNode.At -> position(node.position, scope, current)
            is StmtNode.Rotated -> rotation(node.rotation, scope, current)
            else -> fail(stmt, "Unsupported execution context statement")
        }
        return child
    }
}
