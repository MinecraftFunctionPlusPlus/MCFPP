package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.type.TypeId

class UnionDataTemplate(templates: List<DataTemplate>) :
    DataTemplate(identifierFor(templates), "mcfpp.intersection") {

    init {
        templates.forEach { extends(it) }
    }

    companion object {
        /** This namespace and non-source identifier prefix are reserved for synthesized intersections. */
        internal fun isIntersectionIdentity(namespace: String, identifier: String): Boolean =
            namespace == "mcfpp.intersection" && identifier.startsWith("0intersection_")

        /** The registry is project-owned, so initialization and savepoints also own this cache. */
        internal fun resolve(templates: List<DataTemplate>): DataTemplate {
            val identity = TypeId.Declaration("template", "mcfpp.intersection", identifierFor(templates))
            Project.findRegisteredAnonymousTemplate(identity)?.let { return it }
            return UnionDataTemplate(templates).also(Project::registerAnonymousTemplate)
        }

        private fun identifierFor(templates: List<DataTemplate>): String {
            val key = node("intersection", templates.map { encode(it.inheritanceIdentity) })
            // Length-framed identities preserve arbitrary readonly strings and nested bindings.
            // UTF-16 code units are encoded directly, without a lossy display-name/hash shortcut.
            // A digit cannot begin a source identifier, keeping synthesized declarations distinct.
            return buildString {
                append("0intersection_")
                key.forEach { append(it.code.toString(16).padStart(4, '0')) }
            }
        }

        private fun node(tag: String, values: List<String>): String = buildString {
            append(tag.length); append(':'); append(tag)
            append(values.size); append(':')
            values.forEach { append(it.length); append(':'); append(it) }
        }

        private fun encode(identity: TypeId): String = when (identity) {
            is TypeId.Builtin -> node("builtin", listOf(identity.key))
            is TypeId.Declaration -> node("declaration", listOf(identity.kind, identity.namespace, identity.name))
            is TypeId.Applied -> node("applied", listOf(encode(identity.constructor)) + identity.arguments.map(::encode))
            is TypeId.Specialized -> node("specialized", listOf(encode(identity.constructor)) + identity.arguments.map(::encode))
            is TypeId.Union -> node("union", identity.alternatives.map(::encode).sorted())
            is TypeId.Selector -> node("selector", listOf(
                identity.limit?.let { node("limit", listOf(it.toString())) } ?: node("unlimited", emptyList()),
                identity.entities?.let { node("entities", it) } ?: node("all-entities", emptyList()),
                identity.isName.toString()
            ))
            is TypeId.Opaque -> node("opaque", listOf(identity.declaration.toString()))
        }

        private fun encode(value: CompilerValue): String = when (value) {
            CompilerValue.NullValue -> node("null", emptyList())
            is CompilerValue.Integral -> node("integral", listOf(value.value.toString()))
            is CompilerValue.FloatBits -> node("float", listOf(value.bits.toString()))
            is CompilerValue.DoubleBits -> node("double", listOf(value.bits.toString()))
            is CompilerValue.Bool -> node("bool", listOf(value.value.toString()))
            is CompilerValue.Text -> node("text", listOf(value.value))
            is CompilerValue.TypeValue -> node("type", listOf(encode(value.id)))
            is CompilerValue.Nbt -> node("nbt", listOf(value.snbt))
            is CompilerValue.Sequence -> node("sequence", value.elements.map(::encode))
            is CompilerValue.Record -> node("record", value.fields.toSortedMap().map { (name, field) ->
                node("field", listOf(name, encode(field)))
            })
            is CompilerValue.Typed -> node("typed", listOf(encode(value.type), encode(value.payload)))
        }
    }
}
