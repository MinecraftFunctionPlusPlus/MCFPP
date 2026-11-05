package top.mcfpp.analysis

import top.mcfpp.type.TypeId
import java.util.Collections

/** Immutable compiler payloads. Neither mutable Vars nor NBT tags escape into cache keys. */
sealed interface CompilerValue {
    object NullValue : CompilerValue
    data class Integral(val value: Long) : CompilerValue
    data class FloatBits(val bits: Int) : CompilerValue
    data class DoubleBits(val bits: Long) : CompilerValue
    data class Bool(val value: Boolean) : CompilerValue
    data class Text(val value: String) : CompilerValue
    data class TypeValue(val id: TypeId) : CompilerValue
    data class Nbt(val snbt: String) : CompilerValue
    class Sequence(elements: List<CompilerValue>) : CompilerValue {
        val elements: List<CompilerValue> = Collections.unmodifiableList(ArrayList(elements))
        private val structuralHash = this.elements.hashCode()
        override fun equals(other: Any?) = other is Sequence && structuralHash == other.structuralHash &&
            StructuralEquality.compare(this, other) { elements == other.elements }
        override fun hashCode() = structuralHash
        override fun toString() = StructuralDisplay.render(this)
    }
    class Record(fields: Map<String, CompilerValue>) : CompilerValue {
        val fields: Map<String, CompilerValue> = Collections.unmodifiableMap(fields.toSortedMap())
        private val structuralHash = this.fields.hashCode()
        override fun equals(other: Any?) = other is Record && structuralHash == other.structuralHash &&
            StructuralEquality.compare(this, other) { fields == other.fields }
        override fun hashCode() = structuralHash
        override fun toString() = StructuralDisplay.render(this)
    }
    data class Typed(val type: TypeId, val payload: CompilerValue) : CompilerValue

    /** Persistent replacement of a known field/index; no mutable host value is retained. */
    fun replacing(path: List<PathSegment>, value: CompilerValue): CompilerValue? {
        if (path.isEmpty()) return value
        if (this is Typed) return payload.replacing(path, value)?.let { Typed(type, it) }
        val tail = path.drop(1)
        return when (val head = path.first()) {
            is PathSegment.Field -> if (this is Record) {
                val replacement = if (tail.isEmpty()) value else fields[head.name]?.replacing(tail, value) ?: return null
                Record(fields + (head.name to replacement))
            } else null
            is PathSegment.Index -> if (this is Sequence && head.index in elements.indices) {
                val replacement = elements[head.index].replacing(tail, value) ?: return null
                Sequence(elements.mapIndexed { index, element -> if (index == head.index) replacement else element })
            } else null
            PathSegment.UnknownIndex -> null
        }
    }
}
