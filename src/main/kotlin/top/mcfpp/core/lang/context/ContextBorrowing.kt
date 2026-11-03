package top.mcfpp.core.lang.context

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageBinding
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.PathSegment
import top.mcfpp.analysis.ValueFacts
import top.mcfpp.analysis.ValueKnowledge
import top.mcfpp.analysis.ValueState
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeRelations

/** An immutable compiler capability. It is deliberately outside serializable CompilerValue. */
sealed class ContextBorrowPayload {
    abstract val owner: ExecutionContext
    abstract val encoder: ContextCommandEncoder?
    abstract val kind: String
    abstract fun adapter(name: String, type: MCFPPType): ScopedContextVar<*>
    fun member(name: String): ContextBorrowPayload? = if (this is Context) when (name) {
        "executor" -> Executor(owner.executor, encoder)
        "pos" -> Position(owner.pos, encoder)
        else -> null
    } else null

    /** Cache/domain evidence only; restoring this record never reconstructs a capability. */
    val identity: CompilerValue get() = CompilerValue.Record(mapOf(
        "borrowKind" to CompilerValue.Text(kind),
        "session" to CompilerValue.Integral(owner.session.id),
        "scope" to CompilerValue.Integral(owner.scope.id),
        "active" to CompilerValue.Bool(owner.scope.isActive),
        "source" to CompilerValue.Integral(owner.source.value),
        "executor" to CompilerValue.Integral(owner.components.executor.value),
        "position" to CompilerValue.Integral(owner.components.position.value),
        "rotation" to CompilerValue.Integral(owner.components.rotation.value),
        "dimension" to CompilerValue.Integral(owner.components.dimension.value)
    ))

    data class Context(override val owner: ExecutionContext, override val encoder: ContextCommandEncoder?) : ContextBorrowPayload() {
        override val kind: String get() = "context"
        override fun adapter(name: String, type: MCFPPType): ScopedContextVar<*> {
            if (type !is MCFPPExecutionContextType || !TypeRelations.isSubtype(ContextTypes.of(owner), type))
                throw ContextAccessException("Execution context cannot be erased or stored as '$type'")
            return ExecutionContextVar(name, owner, type, encoder)
        }
    }
    data class Executor(val binding: BoundExecutor, override val encoder: ContextCommandEncoder?) : ContextBorrowPayload() {
        override val owner: ExecutionContext get() = binding.owner
        override val kind: String get() = "executor"
        override fun adapter(name: String, type: MCFPPType): ScopedContextVar<*> {
            if (type !is MCFPPEntityType) throw ContextAccessException("Bound executor cannot be erased or stored as '$type'")
            return BoundExecutorVar(name, binding, encoder)
        }
    }
    data class Position(val binding: BoundPosition, override val encoder: ContextCommandEncoder?) : ContextBorrowPayload() {
        override val owner: ExecutionContext get() = binding.owner
        override val kind: String get() = "position"
        override fun adapter(name: String, type: MCFPPType): ScopedContextVar<*> {
            if (type != ContextTypes.Position) throw ContextAccessException("Bound position cannot be erased or stored as '$type'")
            return BoundPositionVar(name, binding, encoder)
        }
    }

    companion object {
        fun direct(value: Var<*>): ContextBorrowPayload? = when (value) {
            is ExecutionContextVar -> value.context?.let { Context(it, value.encoder) }
            is BoundExecutorVar -> Executor(value.binding, value.encoder)
            is BoundPositionVar -> value.binding?.let { Position(it, value.encoder) }
            else -> null
        }
    }
}

/** Controlled source callback lifetime, shared by ordinary synchronous callees while compiling. */
object ContextBorrowing {
    private val active = ThreadLocal<ExecutionContext?>()
    val currentContext: ExecutionContext? get() = active.get()
    val currentScope: ContextScope? get() = currentContext?.scope

    /** Restores the caller on every exit and closes this callback's entire lexical lifetime. */
    fun <T> withContext(context: ExecutionContext, body: () -> T): T {
        val previous = active.get()
        context.scope.requireActive()
        if (previous != null) {
            previous.requireUsableIn(context.scope)
            if (previous.scope === context.scope)
                throw ContextAccessException("A controlled callback requires its own lexical scope")
        }
        active.set(context)
        try {
            return body()
        } finally {
            try {
                fun close(scope: ContextScope) {
                    scope.children.filter { it.isActive }.forEach(::close)
                    if (scope.isActive) scope.close()
                }
                close(context.scope)
            } finally {
                if (previous == null) active.remove() else active.set(previous)
            }
        }
    }

    fun payload(place: Place, fact: (Place) -> ValueFacts?): ContextBorrowPayload? {
        fact(place)?.let { value ->
            return if (value.state == ValueState.INITIALIZED) (value.value as? ValueKnowledge.Borrow)?.payload else null
        }
        val member = place.path.lastOrNull() as? PathSegment.Field ?: return null
        return payload(Place(place.root, place.path.dropLast(1)), fact)?.member(member.name)
    }

    fun payload(binding: StorageBinding): ContextBorrowPayload? = payload(binding.place, binding.data.facts::read)

    fun payload(value: Var<*>): ContextBorrowPayload? = value.storageBinding?.let(::payload)
        ?: if (value.storageBinding == null) ContextBorrowPayload.direct(value) else null

    /** Lifetime evidence is independent of the value adapter and survives erasure/wrapping. */
    fun provenance(value: Var<*>): Set<ContextBorrowPayload> = buildSet {
        payload(value)?.let(::add)
        value.storageBinding?.let { binding ->
            addAll(binding.data.facts.readAccess(binding.place, binding.data.declarations, binding.data.types)
                ?.reference?.borrows.orEmpty())
            binding.data.facts.entries().forEach { (place, facts) ->
                if (place.root == binding.place.root &&
                    place.path.take(binding.place.path.size) == binding.place.path &&
                    facts.state == ValueState.INITIALIZED) {
                    (facts.value as? ValueKnowledge.Borrow)?.payload?.let(::add)
                    addAll(facts.reference?.borrows.orEmpty())
                }
            }
        }
    }

    fun requireUsable(payload: ContextBorrowPayload) {
        val scope = currentScope ?: throw ContextAccessException("Scoped context value requires an active synchronous context callback")
        payload.owner.requireUsableIn(scope)
    }

    fun diagnostic(value: Var<*>): String? {
        return try { provenance(value).forEach(::requireUsable); null }
        catch (failure: ContextAccessException) { failure.message }
    }

    fun escapeDiagnostic(value: Var<*>, destination: String): String? =
        if (provenance(value).isNotEmpty() || value is ScopedContextValue)
            "Scoped context value '${value.identifier}' cannot escape into $destination" else null
}
