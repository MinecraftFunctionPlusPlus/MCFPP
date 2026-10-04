package top.mcfpp.type

import java.util.concurrent.atomic.AtomicLong
import java.util.Collections

/** Language identity. Display names and JVM implementation inheritance are deliberately absent. */
sealed interface TypeId {
    data class Builtin(val key: String) : TypeId
    data class Declaration(val kind: String, val namespace: String, val name: String) : TypeId
    class Applied(val constructor: TypeId, arguments: List<TypeId>) : TypeId {
        val arguments: List<TypeId> = Collections.unmodifiableList(ArrayList(arguments))
        override fun equals(other: Any?) = other is Applied && constructor == other.constructor && arguments == other.arguments
        override fun hashCode() = 31 * constructor.hashCode() + arguments.hashCode()
        override fun toString() = "Applied(constructor=$constructor, arguments=$arguments)"
    }
    class Specialized(val constructor: TypeId, arguments: List<top.mcfpp.analysis.CompilerValue>) : TypeId {
        val arguments: List<top.mcfpp.analysis.CompilerValue> = Collections.unmodifiableList(ArrayList(arguments))
        override fun equals(other: Any?) = other is Specialized && constructor == other.constructor && arguments == other.arguments
        override fun hashCode() = 31 * constructor.hashCode() + arguments.hashCode()
        override fun toString() = "Specialized(constructor=$constructor, arguments=$arguments)"
    }
    class Union(alternatives: Set<TypeId>) : TypeId {
        val alternatives: Set<TypeId> = Collections.unmodifiableSet(LinkedHashSet(alternatives.sortedBy { it.toString() }))
        override fun equals(other: Any?) = other is Union && alternatives == other.alternatives
        override fun hashCode() = alternatives.hashCode()
        override fun toString() = "Union(alternatives=$alternatives)"
    }
    class Selector(val limit: Int?, entities: List<String>?, val isName: Boolean) : TypeId {
        val entities: List<String>? = entities?.let { Collections.unmodifiableList(ArrayList(it)) }
        override fun equals(other: Any?) = other is Selector && limit == other.limit && entities == other.entities && isName == other.isName
        override fun hashCode() = 31 * (31 * (limit?.hashCode() ?: 0) + (entities?.hashCode() ?: 0)) + isName.hashCode()
        override fun toString() = "Selector(limit=$limit, entities=$entities, isName=$isName)"
    }
    data class Opaque(val declaration: Long) : TypeId

    companion object {
        private val declarations = AtomicLong()
        fun reserve(id: Long) { declarations.accumulateAndGet(id, ::maxOf) }
        fun fresh(): TypeId = Opaque(declarations.incrementAndGet())
    }
}
