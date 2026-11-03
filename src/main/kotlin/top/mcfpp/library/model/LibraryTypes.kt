package top.mcfpp.library.model

import top.mcfpp.library.syntax.*
import java.util.Collections

@JvmInline value class NodeRef(val value: Long) {
    init { require(value in 1..0xffff_ffffL) { "Node reference must be a positive u32" } }
}

/** The explicit T table. References are file-local; none is a compiler TypeId. */
sealed interface LibraryType {
    data class Builtin(val key: String) : LibraryType
    data class Declaration(val target: DeclarationReference) : LibraryType
    data class Parameter(val owner: DeclarationReference, val role: ParameterRole, val index: Long) : LibraryType {
        init { require(index in 0..0xffff_ffffL) }
    }
    data class Applied(val constructor: TypeRef, val arguments: List<TypeRef>) : LibraryType
    data class Specialized(val constructor: TypeRef, val arguments: List<ConstantRef>) : LibraryType
    data class Union(val alternatives: List<TypeRef>) : LibraryType {
        init { require(alternatives.isNotEmpty()) }
    }
    data class Intersection(val components: List<TypeRef>, val declaration: DeclarationId? = null) : LibraryType {
        init { require(components.isNotEmpty()) }
    }
    data class Function(val execution: FunctionExecution, val parameters: List<TypeRef>, val result: TypeRef) : LibraryType
    data class NbtRef(val value: TypeRef) : LibraryType
    data class Dependent(val syntax: NodeRef, val environment: EnvironmentId, val owner: DeclarationId) : LibraryType
    /** Exact pending TypeId.Declaration data; it supplies neither a model nor executable name binding. */
    data class UnresolvedNominal(val kind: String, val namespace: String, val name: String) : LibraryType {
        init { require(kind.isNotEmpty() && name.isNotEmpty()) { "Unresolved nominal kind/name must be non-empty" } }
    }
}

internal fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

/** Owns list payloads, including the already shared syntax.ConstantValue boundary types. */
class LibraryTables(types: List<LibraryType>, constants: List<ConstantValue>) {
    val types: List<LibraryType> = frozen(types.map { type -> when (type) {
        is LibraryType.Applied -> type.copy(arguments = frozen(type.arguments))
        is LibraryType.Specialized -> type.copy(arguments = frozen(type.arguments))
        is LibraryType.Union -> type.copy(alternatives = frozen(type.alternatives))
        is LibraryType.Intersection -> type.copy(components = frozen(type.components))
        is LibraryType.Function -> type.copy(parameters = frozen(type.parameters))
        else -> type
    } })
    val constants: List<ConstantValue> = frozen(constants.map { constant -> when (constant) {
        is ConstantValue.Sequence -> constant.copy(elements = frozen(constant.elements))
        is ConstantValue.Record -> constant.copy(fields = frozen(constant.fields))
        is ConstantValue.FunctionValue -> constant.copy(captures = frozen(constant.captures))
        else -> constant
    } })

    fun type(reference: TypeRef): LibraryType {
        require(reference.value <= types.size.toLong()) { "Type reference ${reference.value} is outside the T table" }
        return types[(reference.value - 1).toInt()]
    }

    fun constant(reference: ConstantRef): ConstantValue {
        require(reference.value <= constants.size.toLong()) { "Constant reference ${reference.value} is outside the C table" }
        return constants[(reference.value - 1).toInt()]
    }
}

/** A complete shared S table can be supplied by later declaration/body/resource codecs. */
class LibraryStrings private constructor(values: List<String>) {
    val values: List<String> = frozen(values)
    private val references = values.mapIndexed { index, value -> value to (index + 1L) }.toMap()

    fun reference(value: String): Long = requireNotNull(references[value]) { "String is absent from the shared S table: $value" }
    fun value(reference: Long): String {
        require(reference in 1..values.size.toLong()) { "String reference $reference is outside the S table" }
        return values[(reference - 1).toInt()]
    }

    companion object {
        fun of(values: Collection<String>): LibraryStrings = LibraryStrings(values.distinct().sortedWith(::compareUtf8))
        fun ordered(values: List<String>): LibraryStrings {
            require(values.zipWithNext().all { (a, b) -> compareUtf8(a, b) < 0 }) { "S table must be sorted and unique" }
            return LibraryStrings(values)
        }
    }
}

fun compareBytes(left: ByteArray, right: ByteArray): Int {
    for (index in 0 until minOf(left.size, right.size)) {
        val difference = (left[index].toInt() and 255) - (right[index].toInt() and 255)
        if (difference != 0) return difference
    }
    return left.size.compareTo(right.size)
}

fun compareUtf8(left: String, right: String): Int = compareBytes(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))

/** Identity bytes must be structural keys, never the D/N/E table numbers being assigned. */
interface CanonicalIdentityBindings {
    fun localDeclaration(reference: DeclarationId): ByteArray = error("Register a stable declaration key/origin for $reference")
    fun dependency(index: Long): ByteArray = error("Register the resolved package identity for dependency $index")
    fun node(reference: NodeRef): ByteArray = error("Register the semantic child-path key for $reference")
    fun environment(reference: EnvironmentId): ByteArray = error("Register the lexical environment key for $reference")
}

data class CanonicalLibraryTables(
    val tables: LibraryTables,
    val typeReferences: Map<TypeRef, TypeRef>,
    val constantReferences: Map<ConstantRef, ConstantRef>,
)
