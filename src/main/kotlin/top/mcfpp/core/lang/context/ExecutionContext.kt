package top.mcfpp.core.lang.context

import java.util.concurrent.atomic.AtomicLong
import top.mcfpp.lib.EntityFacts

/** An identity belongs to one compilation session, never to a selector expression. */
data class ContextSourceId internal constructor(val session: Long, val value: Long)

/** An ambient command source may have no entity. Entering an entity branch proves presence. */
enum class ExecutorPresence { NONE, OPTIONAL, PRESENT }

enum class ContextRelation(val commandName: String) {
    PASSENGERS("passengers"), VEHICLE("vehicle"), ORIGIN("origin"), OWNER("owner"),
    CONTROLLER("controller"), LEASHER("leasher"), TARGET("target"), ATTACKER("attacker")
}

/** Component identity changes only when an operation establishes that component. */
data class ContextComponents internal constructor(
    val executor: ContextSourceId,
    val position: ContextSourceId,
    val rotation: ContextSourceId,
    val dimension: ContextSourceId
) {
    init {
        require(listOf(position, rotation, dimension).all { it.session == executor.session }) {
            "Execution context components belong to another session"
        }
    }
}

class ContextAccessException(message: String) : IllegalStateException(message)

/** A lexical lifetime. Closing it invalidates every context and handle declared in it. */
class ContextScope internal constructor(
    val session: ContextSession,
    val id: Long,
    val parent: ContextScope?
) : AutoCloseable {
    private var open = true
    internal val children = ArrayList<ContextScope>()
    val isActive: Boolean get() = open && (parent?.isActive ?: true)

    fun contains(other: ContextScope): Boolean {
        if (session !== other.session) return false
        var current: ContextScope? = other
        while (current != null) {
            if (current === this) return true
            current = current.parent
        }
        return false
    }

    fun requireActive() {
        if (!isActive) throw ContextAccessException("Execution context scope $id is no longer active")
    }

    override fun close() {
        requireActive()
        if (children.any { it.isActive }) throw ContextAccessException("Close nested execution context scopes first")
        open = false
    }
}

/** Owns source identities and lexical lifetimes for one synchronous compilation session. */
class ContextSession : AutoCloseable {
    val id: Long = sessions.incrementAndGet()
    private var nextScope = 0L
    private var nextSource = 0L
    val rootScope = ContextScope(this, nextScope++, null)

    fun openScope(parent: ContextScope = rootScope): ContextScope {
        requireOwned(parent)
        return ContextScope(this, nextScope++, parent).also { parent.children.add(it) }
    }

    fun root(
        executorPresence: ExecutorPresence = ExecutorPresence.OPTIONAL,
        executorFacts: EntityFacts = EntityFacts.UNKNOWN
    ): ExecutionContext {
        rootScope.requireActive()
        val source = freshSource()
        return ExecutionContext(this, source, rootScope, null,
            ContextComponents(source, source, source, source), executorPresence,
            EntityFacts(if (executorPresence == ExecutorPresence.NONE) 0 else 1,
                executorFacts.playersOnly, executorFacts.knownKinds))
    }

    internal fun freshSource(): ContextSourceId = ContextSourceId(id, nextSource++)

    internal fun requireOwned(scope: ContextScope) {
        if (scope.session !== this) throw ContextAccessException("Execution context scope belongs to another session")
        scope.requireActive()
    }

    override fun close() {
        fun closeScope(scope: ContextScope) {
            scope.children.filter { it.isActive }.forEach(::closeScope)
            if (scope.isActive) scope.close()
        }
        closeScope(rootScope)
    }

    companion object {
        private val sessions = AtomicLong()
    }
}

/**
 * An immutable source binding. Parent records instance nesting; it does not determine
 * the concrete language type of the next callback.
 */
open class ExecutionContext internal constructor(
    val session: ContextSession,
    val source: ContextSourceId,
    val scope: ContextScope,
    val parent: ExecutionContext?,
    val components: ContextComponents,
    val executorPresence: ExecutorPresence,
    val executorFacts: EntityFacts
) {
    init {
        session.requireOwned(scope)
        require(source.session == session.id && components.executor.session == session.id) {
            "Execution context source belongs to another session"
        }
        if (parent != null) {
            parent.requireUsableIn(scope)
            require(parent.source != source) { "A child execution context needs a fresh source identity" }
        }
    }

    val executor: BoundExecutor = BoundExecutor(this)
    val pos: BoundPosition = BoundPosition(this)

    fun requireUsableIn(useScope: ContextScope) {
        session.requireOwned(useScope)
        scope.requireActive()
        if (!scope.contains(useScope)) throw ContextAccessException("Execution context cannot escape its lexical scope")
    }

    private fun requireChildScope(childScope: ContextScope) {
        requireUsableIn(childScope)
        if (childScope === scope) throw ContextAccessException("A context callback requires a nested lexical scope")
    }

    /** execute at target changes space, retaining the ambient executor, including absence. */
    fun withPosContext(childScope: ContextScope): PosContext {
        requireChildScope(childScope)
        val child = session.freshSource()
        return PosContext(this, child, childScope,
            ContextComponents(components.executor, child, child, child))
    }

    /** A successful execute as branch supplies exactly one present executor. */
    fun withEntityContext(childScope: ContextScope, targetFacts: EntityFacts = EntityFacts.UNKNOWN): EntityContext {
        requireChildScope(childScope)
        val child = session.freshSource()
        return EntityContext(this, child, childScope, components.copy(executor = child),
            EntityFacts(1, targetFacts.playersOnly, targetFacts.knownKinds))
    }

    /** The callback is a successful relationship branch; no match means no invocation. */
    fun onRelation(relation: ContextRelation, childScope: ContextScope, ambient: ExecutionContext = this): RelationContext {
        executor.requireUsableIn(ambient.scope)
        ambient.requireChildScope(childScope)
        val child = session.freshSource()
        return RelationContext(ambient, child, childScope, ambient.components.copy(executor = child), relation, executor)
    }
}

class PosContext internal constructor(
    parent: ExecutionContext, source: ContextSourceId, scope: ContextScope, components: ContextComponents
) : ExecutionContext(parent.session, source, scope, parent, components, parent.executorPresence, parent.executorFacts)

open class EntityContext internal constructor(
    parent: ExecutionContext, source: ContextSourceId, scope: ContextScope, components: ContextComponents,
    executorFacts: EntityFacts = EntityFacts.SINGLE
) : ExecutionContext(parent.session, source, scope, parent, components, ExecutorPresence.PRESENT, executorFacts)

class RelationContext internal constructor(
    parent: ExecutionContext, source: ContextSourceId, scope: ContextScope, components: ContextComponents,
    val relation: ContextRelation,
    val origin: BoundExecutor = parent.executor
) : EntityContext(parent, source, scope, components)

/** Borrowing retains the original executor identity even when the current @s changes. */
class BoundExecutor internal constructor(val owner: ExecutionContext) {
    val session: ContextSession get() = owner.session
    val scope: ContextScope get() = owner.scope
    val source: ContextSourceId get() = owner.source
    val provenance: ContextSourceId get() = owner.components.executor
    val presence: ExecutorPresence get() = owner.executorPresence
    val facts: EntityFacts get() = owner.executorFacts

    fun requireUsableIn(useScope: ContextScope) = owner.requireUsableIn(useScope)

    /** A different component requires identity preservation by lowering, never reevaluation. */
    fun requireDirectUseIn(current: ExecutionContext) {
        requireUsableIn(current.scope)
        if (provenance != current.components.executor)
            throw ContextAccessException("Bound executor requires preservation before the executor changes")
    }
}

/** Position and dimension form one binding; relative coordinates alone cannot identify it. */
class BoundPosition internal constructor(val owner: ExecutionContext) {
    val session: ContextSession get() = owner.session
    val scope: ContextScope get() = owner.scope
    val source: ContextSourceId get() = owner.source
    val provenance: ContextSourceId get() = owner.components.position
    val dimension: ContextSourceId get() = owner.components.dimension

    fun requireUsableIn(useScope: ContextScope) = owner.requireUsableIn(useScope)

    fun requireDirectUseIn(current: ExecutionContext) {
        requireUsableIn(current.scope)
        if (provenance != current.components.position || dimension != current.components.dimension)
            throw ContextAccessException("Bound position requires preservation before position or dimension changes")
    }
}
