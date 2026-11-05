package top.mcfpp.type

import top.mcfpp.analysis.CompilerValue
import java.util.IdentityHashMap

/** Stable, lossless identity encoding for synthesized intersections (schema 1).
 * Each structural node is emitted once; references preserve sharing without making physical
 * object sharing part of language identity. Length framing preserves arbitrary UTF-16 strings.
 * This format is deliberately independent of diagnostic toString implementations.
 */
internal object CanonicalIdentityEncoding {
    private data class Node(val kind: String, val attributes: List<String>, val children: List<Int>)

    fun intersection(components: List<TypeId>): String {
        val visited = IdentityHashMap<Any, Int>()
        val nodes = linkedMapOf<Node, Int>()
        lateinit var visit: (Any) -> Int
        fun node(kind: String, attributes: List<String> = emptyList(), children: List<Any> = emptyList()) =
            Node(kind, attributes, children.map(visit))
        visit = { value ->
            visited[value] ?: run {
                val shape = when (value) {
                    is TypeId.Builtin -> node("builtin", listOf(value.key))
                    is TypeId.Declaration -> node("declaration", listOf(value.kind, value.namespace, value.name))
                    is TypeId.Applied -> node("applied", children = listOf(value.constructor) + value.arguments)
                    is TypeId.Specialized -> node("specialized", children = listOf(value.constructor) + value.arguments)
                    // Union owns its canonical order; Record owns its sorted field order.
                    is TypeId.Union -> node("union", children = value.alternatives.toList())
                    is TypeId.Selector -> node("selector", listOf(value.limit?.toString() ?: "unlimited",
                        value.isName.toString(), if (value.entities == null) "all" else "entities") + value.entities.orEmpty())
                    is TypeId.Opaque -> node("opaque", listOf(value.declaration.toString()))
                    CompilerValue.NullValue -> node("null")
                    is CompilerValue.Integral -> node("integral", listOf(value.value.toString()))
                    is CompilerValue.FloatBits -> node("float", listOf(value.bits.toString()))
                    is CompilerValue.DoubleBits -> node("double", listOf(value.bits.toString()))
                    is CompilerValue.Bool -> node("bool", listOf(value.value.toString()))
                    is CompilerValue.Text -> node("text", listOf(value.value))
                    is CompilerValue.TypeValue -> node("type", children = listOf(value.id))
                    is CompilerValue.Nbt -> node("nbt", listOf(value.snbt))
                    is CompilerValue.Sequence -> node("sequence", children = value.elements)
                    is CompilerValue.Record -> node("record", value.fields.keys.toList(), value.fields.values.toList())
                    is CompilerValue.Typed -> node("typed", children = listOf(value.type, value.payload))
                    else -> error("Unsupported immutable identity node: ${value.javaClass.name}")
                }
                nodes.getOrPut(shape) { nodes.size }.also { visited[value] = it }
            }
        }
        val roots = components.map(visit)
        val encoded = buildString {
            fun text(value: String) { append(value.length).append(':').append(value) }
            append("intersection-dag/1;")
            append(roots.size).append(':'); roots.forEach { append(it).append(';') }
            append(nodes.size).append(':')
            nodes.forEach { (shape, _) ->
                text(shape.kind)
                append(shape.attributes.size).append(':'); shape.attributes.forEach(::text)
                append(shape.children.size).append(':'); shape.children.forEach { append(it).append(';') }
            }
        }
        val hex = "0123456789abcdef"
        return buildString {
            append("0intersection_")
            encoded.forEach { char ->
                for (shift in 12 downTo 0 step 4) append(hex[(char.code ushr shift) and 15])
            }
        }
    }
}
