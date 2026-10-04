package top.mcfpp.io

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.type.TypeId
import java.util.Collections

/** Explicit metadata encoding avoids reflecting immutable payloads and JVM collection internals. */
class TypeIdentitySerializer : Serializer<TypeId>() {
    override fun write(kryo: Kryo, output: Output, value: TypeId) = writeId(output, value)
    override fun read(kryo: Kryo, input: Input, type: Class<out TypeId>): TypeId = readId(input)

    private fun writeId(output: Output, value: TypeId) {
        when (value) {
            is TypeId.Builtin -> { output.writeByte(0); output.writeString(value.key) }
            is TypeId.Declaration -> {
                output.writeByte(1); output.writeString(value.kind); output.writeString(value.namespace); output.writeString(value.name)
            }
            is TypeId.Applied -> {
                output.writeByte(2); writeId(output, value.constructor)
                output.writeInt(value.arguments.size); value.arguments.forEach { writeId(output, it) }
            }
            is TypeId.Union -> {
                output.writeByte(3); output.writeInt(value.alternatives.size)
                value.alternatives.sortedBy { it.toString() }.forEach { writeId(output, it) }
            }
            is TypeId.Selector -> {
                output.writeByte(4); output.writeBoolean(value.limit != null)
                value.limit?.let(output::writeInt)
                output.writeBoolean(value.entities != null)
                value.entities?.let { entities -> output.writeInt(entities.size); entities.forEach(output::writeString) }
                output.writeBoolean(value.isName)
            }
            is TypeId.Opaque -> { output.writeByte(5); output.writeLong(value.declaration) }
            is TypeId.Specialized -> {
                output.writeByte(6); writeId(output, value.constructor)
                output.writeInt(value.arguments.size); value.arguments.forEach { writeValue(output, it) }
            }
        }
    }

    private fun readId(input: Input): TypeId = when (val kind = input.readByte().toInt()) {
        0 -> TypeId.Builtin(input.readString())
        1 -> TypeId.Declaration(input.readString(), input.readString(), input.readString())
        2 -> TypeId.Applied(readId(input), List(input.readInt()) { readId(input) })
        3 -> TypeId.Union(List(input.readInt()) { readId(input) }.toSet())
        4 -> {
            val limit = if (input.readBoolean()) input.readInt() else null
            val entities = if (input.readBoolean()) List(input.readInt()) { input.readString() } else null
            TypeId.Selector(limit, entities, input.readBoolean())
        }
        5 -> input.readLong().let { TypeId.reserve(it); TypeId.Opaque(it) }
        6 -> TypeId.Specialized(readId(input), List(input.readInt()) { readValue(input) })
        else -> error("Unknown type identity encoding: $kind")
    }

    private fun writeValue(output: Output, value: CompilerValue) {
        when (value) {
            CompilerValue.NullValue -> output.writeByte(0)
            is CompilerValue.Integral -> { output.writeByte(1); output.writeLong(value.value) }
            is CompilerValue.FloatBits -> { output.writeByte(2); output.writeInt(value.bits) }
            is CompilerValue.DoubleBits -> { output.writeByte(3); output.writeLong(value.bits) }
            is CompilerValue.Bool -> { output.writeByte(4); output.writeBoolean(value.value) }
            is CompilerValue.Text -> { output.writeByte(5); output.writeString(value.value) }
            is CompilerValue.TypeValue -> { output.writeByte(6); writeId(output, value.id) }
            is CompilerValue.Nbt -> { output.writeByte(7); output.writeString(value.snbt) }
            is CompilerValue.Sequence -> {
                output.writeByte(8); output.writeInt(value.elements.size); value.elements.forEach { writeValue(output, it) }
            }
            is CompilerValue.Record -> {
                output.writeByte(9); output.writeInt(value.fields.size)
                value.fields.toSortedMap().forEach { (key, payload) -> output.writeString(key); writeValue(output, payload) }
            }
            is CompilerValue.Typed -> { output.writeByte(10); writeId(output, value.type); writeValue(output, value.payload) }
        }
    }

    private fun readValue(input: Input): CompilerValue = when (val kind = input.readByte().toInt()) {
        0 -> CompilerValue.NullValue
        1 -> CompilerValue.Integral(input.readLong())
        2 -> CompilerValue.FloatBits(input.readInt())
        3 -> CompilerValue.DoubleBits(input.readLong())
        4 -> CompilerValue.Bool(input.readBoolean())
        5 -> CompilerValue.Text(input.readString())
        6 -> CompilerValue.TypeValue(readId(input))
        7 -> CompilerValue.Nbt(input.readString())
        8 -> CompilerValue.Sequence(Collections.unmodifiableList(List(input.readInt()) { readValue(input) }))
        9 -> {
            val fields = sortedMapOf<String, CompilerValue>()
            repeat(input.readInt()) { fields[input.readString()] = readValue(input) }
            CompilerValue.Record(Collections.unmodifiableMap(fields))
        }
        10 -> CompilerValue.Typed(readId(input), readValue(input))
        else -> error("Unknown constant payload encoding: $kind")
    }
}
