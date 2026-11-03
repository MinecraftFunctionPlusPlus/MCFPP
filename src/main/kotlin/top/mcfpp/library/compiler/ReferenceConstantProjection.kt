package top.mcfpp.library.compiler

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.backend.ReferenceHelperRecipe
import top.mcfpp.core.lang.ref.*
import top.mcfpp.library.format.LibraryBinaryWriter
import top.mcfpp.library.format.TypeTableCodec
import top.mcfpp.library.model.compareBytes
import top.mcfpp.library.syntax.KeyType
import top.mcfpp.type.TypeId
import java.security.MessageDigest
import java.util.IdentityHashMap

/** Publication names use author-bound type keys, never compiler allocation identities. */
internal class ReferenceConstantProjection(
    recipes: List<ReferenceHelperRecipe>,
    private val keyType: (TypeId) -> KeyType,
    private val intersectionComponents: (TypeId) -> List<TypeId>?,
) {
    private val byWrite = recipes.associateBy { it.writeId }.also {
        require(it.size == recipes.size) { "Owned reference helper write IDs must be unique" }
    }
    private val names = IdentityHashMap<ReferenceHelperRecipe, String>()
    private val active = java.util.Collections.newSetFromMap(IdentityHashMap<ReferenceHelperRecipe, Boolean>())
    private val projected = IdentityHashMap<CompilerValue, CompilerValue>()

    fun publishedName(recipe: ReferenceHelperRecipe): String {
        names[recipe]?.let { return it }
        require(active.add(recipe)) { "Reference helper type/value identities form a recursive publication key" }
        try {
            val keys = linkedMapOf<List<Byte>, ByteArray>()
            val visited = hashSetOf<TypeId>()
            lateinit var visit: (TypeId) -> Unit
            fun constant(value: CompilerValue) {
                when (value) {
                    is CompilerValue.Typed -> { visit(value.type); constant(value.payload) }
                    is CompilerValue.TypeValue -> visit(value.id)
                    is CompilerValue.Sequence -> value.elements.forEach(::constant)
                    is CompilerValue.Record -> value.fields.values.forEach(::constant)
                    else -> Unit
                }
            }
            visit = { id -> if (visited.add(id)) {
                val components = intersectionComponents(id)
                when {
                    LibraryBuiltinTypes.protocolKey(id) != null -> Unit
                    components != null -> components.forEach(visit)
                    id is TypeId.Applied -> when ((id.constructor as? TypeId.Builtin)?.key) {
                        "vector" -> Unit
                        "NBTRef" -> id.arguments.forEach(visit)
                        TypeId.FUNCTION_CONSTRUCTOR -> {
                            (id.arguments[1] as TypeId.Applied).arguments.forEach(visit)
                            visit(id.arguments[2])
                        }
                        else -> { visit(id.constructor); id.arguments.forEach(visit) }
                    }
                    id is TypeId.Specialized -> { visit(id.constructor); id.arguments.forEach(::constant) }
                    id is TypeId.Union -> id.alternatives.forEach(visit)
                }
                val bytes = TypeTableCodec.keyBytes(keyType(id))
                keys.putIfAbsent(bytes.toList(), bytes)
            } }
            recipe.operandTypes.values.forEach { visit(it.typeId) }
            (recipe.shape as? ReferenceTargetShape.Selector)?.filters?.forEach { visit(it.type) }
            val bytes = LibraryBinaryWriter().apply {
                string("mcfpp.reference/1"); string(recipe.kind.key); bool(recipe.singleRead)
                optional(recipe.shape) { shape -> when (shape) {
                    ReferenceTargetShape.PlayerName -> u8(1)
                    ReferenceTargetShape.UUIDText -> u8(2)
                    ReferenceTargetShape.BoundExecutor -> error("Invocation-owned executor helpers cannot be published")
                    is ReferenceTargetShape.Selector -> {
                        u8(3); string(shape.kind.name)
                        list(shape.filters) { string(it.name); bool(it.reverse); bytes(TypeTableCodec.keyBytes(keyType(it.type))) }
                    }
                } }
                list(keys.values.sortedWith { left, right -> compareBytes(left, right) }) { bytes(it) }
                list(recipe.pathShape) { when (it) {
                    is ReferencePathShape.Field -> { u8(1); string(it.name) }
                    ReferencePathShape.Index -> u8(2)
                } }
            }.toByteArray()
            return ("reference_" + MessageDigest.getInstance("SHA-256").digest(bytes).take(12)
                .joinToString("") { "%02x".format(it.toInt() and 255) }).also { names[recipe] = it }
        } finally { active.remove(recipe) }
    }

    /** Only a real reference type marker authorizes interpreting a record's helper fields. */
    fun project(value: CompilerValue): CompilerValue {
        projected[value]?.let { return it }
        val result = when (value) {
            is CompilerValue.Typed -> {
                val referenceType = value.type == TypeId.Builtin("ScoreRef") ||
                    (value.type as? TypeId.Applied)?.constructor == TypeId.Builtin("NBTRef")
                val descriptor = if (referenceType) ReferenceValues.metadataFromSnapshot(value)?.staticDescriptor else null
                val recipe = descriptor?.fields?.fields?.get("write")?.let(::text)?.let(byWrite::get)
                if (recipe == null) value.copy(payload = project(value.payload)) else {
                    val actual = requireNotNull(descriptor)
                    require(actual.kind == recipe.kind && actual.targetShape == recipe.shape && actual.pathShape == recipe.pathShape) {
                        "Owned reference descriptor does not match its helper recipe"
                    }
                    require((value.type == TypeId.Builtin("ScoreRef")) == (recipe.kind == ReferenceKind.SCORE_HOLDER || recipe.kind == ReferenceKind.SCORE_ENTITY))
                    val read = actual.fields.fields["read"]?.let(::text)
                    require(read == recipe.readId) { "Owned reference descriptor has a mismatched read helper" }
                    val required = when (recipe.kind) {
                        ReferenceKind.SCORE_HOLDER -> setOf("holder", "objective")
                        ReferenceKind.SCORE_ENTITY -> setOf("objective", "target")
                        ReferenceKind.NBT_STORAGE -> setOf("storage", "path")
                        ReferenceKind.NBT_ENTITY -> setOf("path", "target")
                        ReferenceKind.NBT_BLOCK -> setOf("dimension", "x", "y", "z", "path")
                    }
                    require(actual.fields.fields.keys.containsAll(required)) { "Owned reference descriptor is missing address fields" }
                    val name = publishedName(recipe)
                    value.copy(payload = replacePayload(value.payload, "${recipe.namespace}:${name}_write",
                        if (recipe.singleRead) "${recipe.namespace}:${name}_read" else null))
                }
            }
            is CompilerValue.Record -> CompilerValue.Record(value.fields.mapValues { project(it.value) })
            is CompilerValue.Sequence -> CompilerValue.Sequence(value.elements.map(::project))
            else -> value
        }
        projected[value] = result
        projected[result] = result
        return result
    }

    private fun replacePayload(value: CompilerValue, write: String, read: String?): CompilerValue = when (value) {
        is CompilerValue.Typed -> value.copy(payload = replacePayload(value.payload, write, read))
        is CompilerValue.Record -> CompilerValue.Record(value.fields.mapValues { (name, part) -> when (name) {
            "write" -> replaceText(part, write)
            "read" -> replaceText(part, requireNotNull(read))
            else -> project(part)
        } })
        else -> error("A typed reference descriptor must contain a record")
    }
    private fun replaceText(value: CompilerValue, text: String): CompilerValue = when (value) {
        is CompilerValue.Typed -> value.copy(payload = replaceText(value.payload, text))
        is CompilerValue.Text -> CompilerValue.Text(text)
        else -> error("A reference helper ID must be a string")
    }
    private fun text(value: CompilerValue): String? = when (value) {
        is CompilerValue.Typed -> text(value.payload)
        is CompilerValue.Text -> value.value
        else -> null
    }
}
