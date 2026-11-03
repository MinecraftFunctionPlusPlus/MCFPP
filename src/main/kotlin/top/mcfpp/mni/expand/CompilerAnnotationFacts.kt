package top.mcfpp.mni.expand

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.mni.api.ConstantValue
import top.mcfpp.type.TypeId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.ArrayList
import java.util.Base64
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Closed, serializable projection of original annotation arguments.
 *
 * The compiler binds every annotation argument through the same pure declaration reader that
 * prepares macro arguments, then keeps the immutable [CompilerValue] facts on the annotation.
 * A copied annotation (inherited field, library restore, `Property.copyAnnotations`) cannot keep
 * live compiler objects, so the same facts are also written as a serializable payload. The
 * processor-facing `AnnotationView` exposes them under positional keys `"0"`, `"1"`, ... because
 * the source grammar has no named annotation arguments.
 */
internal object CompilerAnnotationFacts {
    /** Serializable mirror. No host object, parser node, Var or mutable tag can enter this graph. */
    private sealed interface ValueFact : Serializable {
        data class Null(val marker: Boolean = true) : ValueFact
        data class Integral(val value: Long) : ValueFact
        data class FloatBits(val bits: Int) : ValueFact
        data class DoubleBits(val bits: Long) : ValueFact
        data class Bool(val value: Boolean) : ValueFact
        data class Text(val value: String) : ValueFact
        data class Nbt(val snbt: String) : ValueFact
        data class TypeValue(val id: TypeFact) : ValueFact
        class Sequence(elements: List<ValueFact>) : ValueFact {
            val elements: ArrayList<ValueFact> = ArrayList(elements)
        }
        class Record(fields: Map<String, ValueFact>) : ValueFact {
            val fields: LinkedHashMap<String, ValueFact> = LinkedHashMap(fields.toSortedMap())
        }
        class Typed(val type: TypeFact, val payload: ValueFact) : ValueFact
    }

    private sealed interface TypeFact : Serializable {
        data class Builtin(val key: String) : TypeFact
        data class Declaration(val kind: String, val namespace: String, val name: String) : TypeFact
        class Applied(val constructor: TypeFact, arguments: List<TypeFact>) : TypeFact {
            val arguments: ArrayList<TypeFact> = ArrayList(arguments)
        }
        class Specialized(val constructor: TypeFact, arguments: List<ValueFact>) : TypeFact {
            val arguments: ArrayList<ValueFact> = ArrayList(arguments)
        }
        class Union(alternatives: List<TypeFact>) : TypeFact {
            val alternatives: ArrayList<TypeFact> = ArrayList(alternatives)
        }
        class Selector(val limit: Int?, entities: List<String>?, val isName: Boolean) : TypeFact {
            val entityNames: ArrayList<String>? = entities?.let { ArrayList(it) }
        }
        data class Opaque(val declaration: Long) : TypeFact
    }

    fun encode(arguments: List<CompilerValue>): String {
        if (arguments.isEmpty()) return ""
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { stream -> stream.writeObject(ArrayList(arguments.map(::fact))) }
            output.toByteArray()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(payload: String): List<CompilerValue> {
        if (payload.isEmpty()) return emptyList()
        val bytes = Base64.getDecoder().decode(payload)
        val stored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as ArrayList<*> }
        return stored.map { value(it as ValueFact) }
    }

    /** Processor-facing positional argument map; the canonical catalog re-anchors type values. */
    fun constants(arguments: List<CompilerValue>, catalog: SourceTypeCatalog): Map<String, ConstantValue> {
        if (arguments.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, ConstantValue>()
        arguments.forEachIndexed { index, argument -> result[index.toString()] = constant(argument, catalog) }
        return Collections.unmodifiableMap(result)
    }

    private fun constant(argument: CompilerValue, catalog: SourceTypeCatalog): ConstantValue = when (argument) {
        is CompilerValue.Typed -> constant(argument.payload, catalog)
        CompilerValue.NullValue -> ConstantValue.NullValue
        is CompilerValue.Integral -> ConstantValue.Integral(argument.value)
        is CompilerValue.FloatBits -> ConstantValue.FloatBits(argument.bits)
        is CompilerValue.DoubleBits -> ConstantValue.DoubleBits(argument.bits)
        is CompilerValue.Bool -> ConstantValue.Bool(argument.value)
        is CompilerValue.Text -> ConstantValue.Text(argument.value)
        is CompilerValue.Nbt -> ConstantValue.Nbt(argument.snbt)
        is CompilerValue.TypeValue -> ConstantValue.TypeValue(catalog.ref(argument.id))
        is CompilerValue.Sequence -> ConstantValue.Sequence(argument.elements.map { constant(it, catalog) })
        is CompilerValue.Record -> ConstantValue.Record(
            argument.fields.mapValues { (_, child) ->
                constant(child, catalog)
            }
        )
    }

    private fun fact(argument: CompilerValue): ValueFact = when (argument) {
        CompilerValue.NullValue -> ValueFact.Null()
        is CompilerValue.Integral -> ValueFact.Integral(argument.value)
        is CompilerValue.FloatBits -> ValueFact.FloatBits(argument.bits)
        is CompilerValue.DoubleBits -> ValueFact.DoubleBits(argument.bits)
        is CompilerValue.Bool -> ValueFact.Bool(argument.value)
        is CompilerValue.Text -> ValueFact.Text(argument.value)
        is CompilerValue.Nbt -> ValueFact.Nbt(argument.snbt)
        is CompilerValue.TypeValue -> ValueFact.TypeValue(type(argument.id))
        is CompilerValue.Sequence -> ValueFact.Sequence(argument.elements.map(::fact))
        is CompilerValue.Record -> ValueFact.Record(argument.fields.mapValues { (_, child) -> fact(child) })
        is CompilerValue.Typed -> ValueFact.Typed(type(argument.type), fact(argument.payload))
    }

    private fun value(fact: ValueFact): CompilerValue = when (fact) {
        is ValueFact.Null -> CompilerValue.NullValue
        is ValueFact.Integral -> CompilerValue.Integral(fact.value)
        is ValueFact.FloatBits -> CompilerValue.FloatBits(fact.bits)
        is ValueFact.DoubleBits -> CompilerValue.DoubleBits(fact.bits)
        is ValueFact.Bool -> CompilerValue.Bool(fact.value)
        is ValueFact.Text -> CompilerValue.Text(fact.value)
        is ValueFact.Nbt -> CompilerValue.Nbt(fact.snbt)
        is ValueFact.TypeValue -> CompilerValue.TypeValue(typeId(fact.id))
        is ValueFact.Sequence -> CompilerValue.Sequence(fact.elements.map(::value))
        is ValueFact.Record -> CompilerValue.Record(fact.fields.mapValues { (_, child) -> value(child) })
        is ValueFact.Typed -> CompilerValue.Typed(typeId(fact.type), value(fact.payload))
    }

    private fun type(id: TypeId): TypeFact = when (id) {
        is TypeId.Builtin -> TypeFact.Builtin(id.key)
        is TypeId.Declaration -> TypeFact.Declaration(id.kind, id.namespace, id.name)
        is TypeId.Applied -> TypeFact.Applied(type(id.constructor), id.arguments.map(::type))
        is TypeId.Specialized -> TypeFact.Specialized(type(id.constructor), id.arguments.map(::fact))
        is TypeId.Union -> TypeFact.Union(id.alternatives.map(::type))
        is TypeId.Selector -> TypeFact.Selector(id.limit, id.entities, id.isName)
        is TypeId.Opaque -> TypeFact.Opaque(id.declaration)
    }

    private fun typeId(fact: TypeFact): TypeId = when (fact) {
        is TypeFact.Builtin -> TypeId.Builtin(fact.key)
        is TypeFact.Declaration -> TypeId.Declaration(fact.kind, fact.namespace, fact.name)
        is TypeFact.Applied -> TypeId.Applied(typeId(fact.constructor), fact.arguments.map(::typeId))
        is TypeFact.Specialized -> TypeId.Specialized(typeId(fact.constructor), fact.arguments.map(::value))
        is TypeFact.Union -> TypeId.Union(fact.alternatives.map(::typeId).toSet())
        is TypeFact.Selector -> TypeId.Selector(fact.limit, fact.entityNames, fact.isName)
        is TypeFact.Opaque -> TypeId.Opaque(fact.declaration)
    }
}
