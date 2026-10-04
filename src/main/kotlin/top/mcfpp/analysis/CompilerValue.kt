package top.mcfpp.analysis

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.type.MCFPPType
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
        override fun equals(other: Any?) = other is Sequence && elements == other.elements
        override fun hashCode() = elements.hashCode()
        override fun toString() = "Sequence(elements=$elements)"
    }
    class Record(fields: Map<String, CompilerValue>) : CompilerValue {
        val fields: Map<String, CompilerValue> = Collections.unmodifiableMap(fields.toSortedMap())
        override fun equals(other: Any?) = other is Record && fields == other.fields
        override fun hashCode() = fields.hashCode()
        override fun toString() = "Record(fields=$fields)"
    }
    data class Typed(val type: TypeId, val payload: CompilerValue) : CompilerValue
}

/** Temporary boundary for the existing backends. A partially known value has no full snapshot. */
object ValueSnapshot {
    fun of(value: Any?): CompilerValue? = when (value) {
        null -> CompilerValue.NullValue
        is CompilerValue -> value
        is MCFPPType -> CompilerValue.TypeValue(value.typeId)
        is Var<*> -> when {
            value.symbol != null && !value.hasAssigned -> null
            value.storageBinding != null -> StorageAccess.snapshot(value)
            value is top.mcfpp.core.lang.JsonTextConcrete -> value.toCommandPart().let { command ->
                if (command.isMacro) null else CompilerValue.Typed(value.type.typeId, CompilerValue.Nbt(top.mcfpp.backend.NbtEncoding.snbt(Tag.toNBT(command.toString()))))
            }
            value is top.mcfpp.core.lang.MCAny && value.compilerPayload != null -> value.compilerPayload?.let(::of)?.let { CompilerValue.Typed(value.type.typeId, it) }
            value is MCFPPValue<*> -> of(value.value)?.let { CompilerValue.Typed(value.type.typeId, it) }
            else -> null
        }
        is MCFPPValue<*> -> of(value.value)
        is Boolean -> CompilerValue.Bool(value)
        is Byte -> CompilerValue.Integral(value.toLong())
        is Short -> CompilerValue.Integral(value.toLong())
        is Int -> CompilerValue.Integral(value.toLong())
        is Long -> CompilerValue.Integral(value)
        is Float -> CompilerValue.FloatBits(value.toRawBits())
        is Double -> CompilerValue.DoubleBits(value.toRawBits())
        is String -> CompilerValue.Text(value)
        is Tag<*> -> CompilerValue.Nbt(top.mcfpp.backend.NbtEncoding.snbt(value))
        is Map<*, *> -> {
            if (value.keys.any { it !is String }) null else {
                val fields = sortedMapOf<String, CompilerValue>()
                var complete = true
                for ((k, v) in value) {
                    val snapshot = of(v)
                    if (snapshot == null) { complete = false; break }
                    fields[k as String] = snapshot
                }
                if (complete) CompilerValue.Record(Collections.unmodifiableMap(fields)) else null
            }
        }
        is Iterable<*> -> sequence(value.toList())
        is Array<*> -> sequence(value.toList())
        is Pair<*, *> -> sequence(listOf(value.first, value.second))
        else -> null // A Java object is not implicitly immutable or serializable.
    }

    private fun sequence(values: List<*>): CompilerValue? {
        val elements = values.map { of(it) ?: return null }
        return CompilerValue.Sequence(Collections.unmodifiableList(elements))
    }
}
