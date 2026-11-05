package top.mcfpp.analysis

import top.mcfpp.type.TypeId
import java.util.IdentityHashMap

/** A deterministic DAG display, also suitable for ordering keys. Structurally equal nodes get
 * the same reference even when their physical sharing differs. No child string is expanded into
 * each parent: both work and output follow the encoded graph rather than its unfolded tree.
 */
internal object StructuralDisplay {
    private data class Shape(val kind: String, val attributes: List<String>, val children: List<Int>)

    fun render(root: Any): String {
        val visited = IdentityHashMap<Any, Int>()
        val interned = linkedMapOf<Shape, Int>()
        lateinit var node: (Any) -> Int
        fun shape(kind: String, attributes: List<String> = emptyList(), children: List<Any> = emptyList()): Shape =
            Shape(kind, attributes, children.map(node))
        node = { value ->
            visited[value] ?: run {
                val description = when (value) {
                    is TypeId.Builtin -> shape("Builtin", listOf(value.key))
                    is TypeId.Declaration -> shape("Declaration", listOf(value.kind, value.namespace, value.name))
                    is TypeId.Applied -> shape("Applied", children = listOf(value.constructor) + value.arguments)
                    is TypeId.Specialized -> shape("Specialized", children = listOf(value.constructor) + value.arguments)
                    is TypeId.Union -> shape("Union", children = value.alternatives.toList())
                    is TypeId.Selector -> shape("Selector", listOf(value.limit.toString(), value.isName.toString(),
                        if (value.entities == null) "absent" else "present") + value.entities.orEmpty())
                    is TypeId.Opaque -> shape("Opaque", listOf(value.declaration.toString()))
                    CompilerValue.NullValue -> shape("Null")
                    is CompilerValue.Integral -> shape("Integral", listOf(value.value.toString()))
                    is CompilerValue.FloatBits -> shape("FloatBits", listOf(value.bits.toString()))
                    is CompilerValue.DoubleBits -> shape("DoubleBits", listOf(value.bits.toString()))
                    is CompilerValue.Bool -> shape("Bool", listOf(value.value.toString()))
                    is CompilerValue.Text -> shape("Text", listOf(value.value))
                    is CompilerValue.TypeValue -> shape("TypeValue", children = listOf(value.id))
                    is CompilerValue.Nbt -> shape("Nbt", listOf(value.snbt))
                    is CompilerValue.Sequence -> shape("Sequence", children = value.elements)
                    is CompilerValue.Record -> shape("Record", value.fields.keys.toList(), value.fields.values.toList())
                    is CompilerValue.Typed -> shape("Typed", children = listOf(value.type, value.payload))
                    else -> error("Not an immutable compiler type/value: ${value.javaClass.name}")
                }
                interned.getOrPut(description) { interned.size }.also { visited[value] = it }
            }
        }
        val rootId = node(root)
        return buildString {
            append('#').append(rootId).append(" {")
            interned.forEach { (description, id) ->
                append(' ').append('#').append(id).append('=').append(description.kind).append('(')
                description.attributes.forEachIndexed { index, attribute ->
                    if (index > 0) append(',')
                    append('"')
                    attribute.forEach { char -> when (char) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(char)
                    } }
                    append('"')
                }
                append(';')
                description.children.forEachIndexed { index, child ->
                    if (index > 0) append(',')
                    append('#').append(child)
                }
                append(");")
            }
            append(" }")
        }
    }
}
