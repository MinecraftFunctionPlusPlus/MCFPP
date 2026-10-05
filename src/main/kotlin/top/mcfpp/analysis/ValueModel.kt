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

/** A logical access range plus the immutable result IDs that capture its runtime indices. */
class Location(val place: Place, indices: Map<Int, Int> = emptyMap()) {
    val indices: Map<Int, Int> = Collections.unmodifiableMap(LinkedHashMap(indices))
    init { require(indices.keys.all { place.path.getOrNull(it) == PathSegment.UnknownIndex }) }
    fun child(segment: PathSegment, capturedIndex: Int? = null) = Location(Place(place.root, place.path + segment),
        if (capturedIndex == null) indices else indices + (place.path.size to capturedIndex))
    override fun equals(other: Any?) = other is Location && place == other.place && indices == other.indices
    override fun hashCode() = 31 * place.hashCode() + indices.hashCode()
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
    data class TypedView(override val type: TypeId, val source: ValueRef, val place: Place, val location: Location = Location(place)) : ValueRef
}

/** Reachability is separate from the facts of an initialized, uninitialized, or erroneous value. */
class FlowFacts private constructor(private val facts: MutableMap<Place, ValueFacts>, var reachable: Boolean,
                                    private val lengths: MutableMap<Place, Int>) {
    constructor() : this(linkedMapOf(), true, linkedMapOf())
    fun fork() = FlowFacts(LinkedHashMap(facts), reachable, LinkedHashMap(lengths))
    private fun alternatives(place: Place): List<Place>? {
        var locations = listOf(Place(place.root))
        for (segment in place.path) locations = if (segment == PathSegment.UnknownIndex) {
            locations.flatMap { parent ->
                val size = lengths[parent] ?: return null
                (0 until size).map(parent::index)
            }
        } else locations.map { Place(it.root, it.path + segment) }
        return locations
    }
    fun read(place: Place): ValueFacts? {
        if (PathSegment.UnknownIndex !in place.path) return facts[place] ?: facts.entries.filter { (range, _) ->
            range.root == place.root && range.path.size == place.path.size &&
                range.path.zip(place.path).all { (left, right) -> left == right || left == PathSegment.UnknownIndex }
        }.map { it.value }.reduceOrNull(ValueFacts::join)?.let {
            it.copy(value = ValueKnowledge.Unknown, type = if (it.state == ValueState.INITIALIZED) it.type else TypeKnowledge.Unknown)
        }
        val candidates = alternatives(place) ?: return facts[place]
        if (candidates.isEmpty()) return null
        val values = candidates.map { facts[it] ?: return null }
        val joined = values.reduce(ValueFacts::join)
        return joined.copy(value = ValueKnowledge.Unknown,
            type = if (joined.state == ValueState.INITIALIZED) joined.type else TypeKnowledge.Unknown)
    }
    fun length(place: Place): Int? {
        if (PathSegment.UnknownIndex !in place.path) return lengths[place]
        val candidates = alternatives(place) ?: return lengths[place]
        return candidates.map { lengths[it] ?: return null }.distinct().singleOrNull()
    }
    fun setLength(place: Place, length: Int) { require(length >= 0); lengths[place] = length }
    fun knownLengths(): Map<Place, Int> = lengths.toMap()
    fun knownTypes(): Map<Place, TypeKnowledge> = facts.mapValues { (_, fact) ->
        if (fact.state == ValueState.INITIALIZED) fact.type else TypeKnowledge.Unknown
    }
    fun refineTypes(source: FlowFacts) {
        source.facts.forEach { (place, fact) -> facts[place]?.let { facts[place] = it.copy(type = fact.type) } }
    }
    /** Refine evidence after an effect or after seeding a constructed shape, without another logical write. */
    fun refine(place: Place, value: ValueFacts) { facts[place] = value }
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

    /** A static write updates complete ancestor snapshots while invalidating overlapping read caches. */
    fun writeConstant(place: Place, value: ValueFacts) {
        val constant = (value.value as? ValueKnowledge.Constant)?.value
        val ancestors = if (constant == null || PathSegment.UnknownIndex in place.path) emptyMap() else facts.filterKeys {
            it.root == place.root && it.path.size < place.path.size && place.path.take(it.path.size) == it.path
        }.mapNotNull { (key, fact) ->
            val old = (fact.value as? ValueKnowledge.Constant)?.value ?: return@mapNotNull null
            old.replacing(place.path.drop(key.path.size), constant)?.let { key to fact.copy(value = ValueKnowledge.Constant(it)) }
        }.toMap()
        write(place, value)
        facts.putAll(ancestors)
    }
    fun children(place: Place): Map<Place, ValueFacts> = facts.filterKeys {
        it.root == place.root && it.path.size == place.path.size + 1 && it.path.take(place.path.size) == place.path
    }
    /** A collection edit replaces its indexed shape; old descendants must not survive at obsolete indices. */
    fun forgetDescendants(place: Place) {
        facts.keys.removeAll { it.overlaps(place) && it.path.size > place.path.size }
        lengths.keys.removeAll { it.overlaps(place) && it.path.size > place.path.size }
    }
    fun copyFrom(source: FlowFacts, from: Place, to: Place, includeRoot: Boolean = true) {
        // A write through one unknown index does not replace every member of the range.
        if (PathSegment.UnknownIndex in to.path) return
        if (PathSegment.UnknownIndex in from.path) {
            val alternatives = source.alternatives(from)
            if (alternatives != null) {
                val copies = alternatives.map { part -> FlowFacts().apply { copyFrom(source, part, to, includeRoot) } }
                val joined = copies.reduceOrNull { a, b -> a.join(b) } ?: return
                facts.putAll(joined.facts.mapValues { it.value.copy(value = ValueKnowledge.Unknown) })
                lengths.putAll(joined.lengths)
                return
            }
        }
        val copied = source.facts.filterKeys {
            it.root == from.root && (if (includeRoot) it.path.size >= from.path.size else it.path.size > from.path.size) && it.path.take(from.path.size) == from.path
        }.mapKeys { (key, _) -> Place(to.root, to.path + key.path.drop(from.path.size)) }
        facts.putAll(copied)
        val shapes = source.lengths.filterKeys { it.root == from.root && it.path.size >= from.path.size && it.path.take(from.path.size) == from.path }
            .mapKeys { (key, _) -> Place(to.root, to.path + key.path.drop(from.path.size)) }
        lengths.putAll(shapes)
    }
    fun invalidate(place: Place) {
        lengths.keys.removeAll { it.overlaps(place) && it.path.size >= place.path.size }
        facts.entries.forEach { (key, value) ->
            if (key.overlaps(place)) facts[key] = value.copy(value = ValueKnowledge.Unknown)
        }
    }
    fun barrier() {
        lengths.clear()
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
        // Different lengths discard exact shape, but every existing element may still share one type.
        for (place in lengths.keys + other.lengths.keys) {
            if (lengths[place] == other.lengths[place]) continue
            val range = place.unknownIndex()
            val left = read(range)
            val right = other.read(range)
            val common = when {
                lengths[place] == 0 -> right
                other.lengths[place] == 0 -> left
                left != null && right != null -> left.join(right)
                else -> null
            }
            if (common != null) joined[range] = common.copy(value = ValueKnowledge.Unknown)
        }
        return FlowFacts(joined, true, lengths.filter { (place, size) -> other.lengths[place] == size }.toMutableMap())
    }
    override fun equals(other: Any?) = other is FlowFacts && reachable == other.reachable && facts == other.facts && lengths == other.lengths
    override fun hashCode() = 31 * (31 * facts.hashCode() + lengths.hashCode()) + reachable.hashCode()
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
