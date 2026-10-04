package top.mcfpp.analysis

import top.mcfpp.type.TypeId
import java.util.concurrent.atomic.AtomicLong
import java.util.Collections

@JvmInline value class SymbolId(val value: Long) {
    companion object {
        private val next = AtomicLong()
        fun fresh() = SymbolId(next.incrementAndGet())
    }
}

data class Symbol(
    val id: SymbolId,
    val name: String,
    val declaredType: TypeId,
    val mutable: Boolean,
    val requiresConstant: Boolean = false,
    val forceRuntime: Boolean = false
)

sealed interface PathSegment {
    data class Field(val name: String) : PathSegment
    data class Index(val index: Int) : PathSegment
    object UnknownIndex : PathSegment
}

/** Identity of data, independent of its representation or any typed view. */
class Place(val root: SymbolId, path: List<PathSegment> = emptyList()) {
    val path: List<PathSegment> = Collections.unmodifiableList(ArrayList(path))
    fun field(name: String) = Place(root, path + PathSegment.Field(name))
    fun index(index: Int) = Place(root, path + PathSegment.Index(index))
    fun unknownIndex() = Place(root, path + PathSegment.UnknownIndex)
    fun overlaps(other: Place): Boolean = root == other.root && path.zip(other.path).all { (a, b) ->
        a == b || a == PathSegment.UnknownIndex || b == PathSegment.UnknownIndex
    }
    override fun equals(other: Any?) = other is Place && root == other.root && path == other.path
    override fun hashCode() = 31 * root.hashCode() + path.hashCode()
    override fun toString() = "Place(root=$root, path=$path)"
}

sealed interface TypeKnowledge {
    data class Exact(val type: TypeId) : TypeKnowledge
    class Candidates(types: Set<TypeId>) : TypeKnowledge {
        val types: Set<TypeId> = Collections.unmodifiableSet(LinkedHashSet(types))
        override fun equals(other: Any?) = other is Candidates && types == other.types
        override fun hashCode() = types.hashCode()
    }
    object Unknown : TypeKnowledge

    fun join(other: TypeKnowledge): TypeKnowledge {
        if (this == Unknown || other == Unknown) return Unknown
        val a = when (this) { is Exact -> setOf(type); is Candidates -> types; Unknown -> emptySet() }
        val b = when (other) { is Exact -> setOf(other.type); is Candidates -> other.types; Unknown -> emptySet() }
        val types = a + b
        return if (types.size == 1) Exact(types.single()) else Candidates(types)
    }
}

sealed interface ValueKnowledge {
    data class Constant(val value: CompilerValue) : ValueKnowledge
    class Partial(parts: Map<PathSegment, ValueKnowledge>) : ValueKnowledge {
        val parts: Map<PathSegment, ValueKnowledge> = Collections.unmodifiableMap(LinkedHashMap(parts))
        override fun equals(other: Any?) = other is Partial && parts == other.parts
        override fun hashCode() = parts.hashCode()
    }
    object Unknown : ValueKnowledge

    fun join(other: ValueKnowledge): ValueKnowledge {
        if (this == other) return this
        if (this is Partial && other is Partial) {
            val shared = parts.keys.intersect(other.parts.keys).associateWith { parts.getValue(it).join(other.parts.getValue(it)) }
                .filterValues { it != Unknown }
            return if (shared.isEmpty()) Unknown else Partial(shared)
        }
        return Unknown
    }
}

enum class ValueState { INITIALIZED, UNINITIALIZED, ERROR }
data class ValueFacts(val type: TypeKnowledge, val value: ValueKnowledge, val state: ValueState = ValueState.INITIALIZED) {
    fun join(other: ValueFacts) = ValueFacts(type.join(other.type), value.join(other.value), when {
        state == ValueState.ERROR || other.state == ValueState.ERROR -> ValueState.ERROR
        state == ValueState.UNINITIALIZED || other.state == ValueState.UNINITIALIZED -> ValueState.UNINITIALIZED
        else -> ValueState.INITIALIZED
    })
}

sealed interface ValueRef {
    val type: TypeId
    data class Constant(override val type: TypeId, val value: CompilerValue) : ValueRef
    data class Read(override val type: TypeId, val place: Place) : ValueRef
    data class Result(override val type: TypeId, val instruction: Int) : ValueRef
    data class TypedView(override val type: TypeId, val source: ValueRef, val place: Place) : ValueRef
}

/** Reachability is separate from the facts of an initialized, uninitialized, or erroneous value. */
class FlowFacts private constructor(private val facts: MutableMap<Place, ValueFacts>, var reachable: Boolean) {
    constructor() : this(linkedMapOf(), true)
    fun fork() = FlowFacts(LinkedHashMap(facts), reachable)
    fun read(place: Place): ValueFacts? = facts[place]
    fun initialize(place: Place, value: ValueFacts) {
        check(place !in facts) { "Place is already initialized: $place" }
        facts[place] = value
    }
    fun write(place: Place, value: ValueFacts) {
        val unknownRange = PathSegment.UnknownIndex in place.path
        val previous = facts[place]
        invalidate(place)
        facts.entries.forEach { (key, fact) ->
            if (!key.overlaps(place) || key == place) return@forEach
            val type = when {
                key.path.size > place.path.size -> TypeKnowledge.Unknown
                key.path.size == place.path.size && (unknownRange || PathSegment.UnknownIndex in key.path) -> fact.type.join(value.type)
                else -> fact.type
            }
            facts[key] = fact.copy(type = type)
        }
        facts[place] = if (unknownRange) value.copy(type = previous?.type?.join(value.type) ?: TypeKnowledge.Unknown,
            value = ValueKnowledge.Unknown) else value
    }
    fun children(place: Place): Map<Place, ValueFacts> = facts.filterKeys {
        it.root == place.root && it.path.size == place.path.size + 1 && it.path.take(place.path.size) == place.path
    }
    fun copyFrom(source: FlowFacts, from: Place, to: Place, includeRoot: Boolean = true) {
        val copied = source.facts.filterKeys {
            it.root == from.root && (if (includeRoot) it.path.size >= from.path.size else it.path.size > from.path.size) && it.path.take(from.path.size) == from.path
        }.mapKeys { (key, _) -> Place(to.root, to.path + key.path.drop(from.path.size)) }
        facts.putAll(copied)
    }
    fun invalidate(place: Place) {
        facts.entries.forEach { (key, value) ->
            if (key.overlaps(place)) facts[key] = value.copy(value = ValueKnowledge.Unknown)
        }
    }
    fun barrier() {
        facts.entries.forEach { (key, value) -> facts[key] = value.copy(value = ValueKnowledge.Unknown,
            type = TypeKnowledge.Unknown) }
    }
    fun join(other: FlowFacts): FlowFacts {
        if (!reachable) return other.fork()
        if (!other.reachable) return fork()
        val joined = linkedMapOf<Place, ValueFacts>()
        for (place in facts.keys + other.facts.keys) {
            val a = facts[place]
            val b = other.facts[place]
            joined[place] = when {
                a == null -> b!!.copy(value = ValueKnowledge.Unknown, state = ValueState.UNINITIALIZED)
                b == null -> a.copy(value = ValueKnowledge.Unknown, state = ValueState.UNINITIALIZED)
                else -> a.join(b)
            }
        }
        return FlowFacts(joined, true)
    }
    override fun equals(other: Any?) = other is FlowFacts && reachable == other.reachable && facts == other.facts
    override fun hashCode() = 31 * facts.hashCode() + reachable.hashCode()
}

sealed interface StorageLayout {
    data class Scoreboard(val player: String, val objective: String) : StorageLayout
    data class Nbt(val source: String, val path: String) : StorageLayout
    data class LegacyFloat(val components: List<Scoreboard>) : StorageLayout
    object Constant : StorageLayout
    object CompilerOnly : StorageLayout
}

/** Layout caches are versioned by writes; materialization itself is not a write to the value. */
class StorageVersions {
    private var nextWrite = 0L
    private val writes = mutableMapOf<Place, Long>()
    private val materialized = mutableMapOf<Pair<Place, StorageLayout>, Long>()
    fun version(place: Place) = writes.filterKeys { it.overlaps(place) }.values.maxOrNull() ?: 0L
    fun invalidate(place: Place) { writes[place] = ++nextWrite }
    fun isMaterialized(place: Place, layout: StorageLayout) = materialized[place to layout] == version(place)
    fun materialize(place: Place, layout: StorageLayout) { materialized[place to layout] = version(place) }
}
