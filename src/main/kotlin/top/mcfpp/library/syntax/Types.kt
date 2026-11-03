package top.mcfpp.library.syntax

/** File-local references, never compiler session identities. Zero is not a reference. */
private fun requireReference(value: Long) = require(value in 1..0xffff_ffffL) { "Reference must be a positive u32" }

@JvmInline value class ScopeId(val value: Long) { init { requireReference(value) } }
@JvmInline value class DeclarationId(val value: Long) { init { requireReference(value) } }
@JvmInline value class BodyId(val value: Long) { init { requireReference(value) } }
@JvmInline value class EnvironmentId(val value: Long) { init { requireReference(value) } }
@JvmInline value class TypeRef(val value: Long) { init { requireReference(value) } }
@JvmInline value class ConstantRef(val value: Long) { init { requireReference(value) } }

data class PackageKey(val coordinate: String, val version: String)
data class AnonymousOrigin(val logicalUnit: String, val declarationOrdinal: Long, val childPath: List<Long>) {
    init {
        require(logicalUnit.isNotEmpty())
        require(declarationOrdinal in 0..0xffff_ffffL && childPath.all { it in 0..0xffff_ffffL })
    }
}

/** ABI keys are structural and cannot refer back to an allocated T/D table index. */
data class DeclarationKey(
    val namespace: String,
    val ownerPath: List<DeclarationPathPart>,
    val kind: Int,
    val name: String,
    val signature: DeclarationSignature? = null
) {
    init { require(kind in 1..13 && name.isNotEmpty()) }
}
data class DeclarationPathPart(val kind: Int, val name: String, val signature: DeclarationSignature? = null) {
    init { require(kind in 1..13 && name.isNotEmpty()) }
}
data class DeclarationSignature(val readonly: List<KeyType>, val normal: List<KeyType>, val receiver: KeyType? = null)

sealed interface KeyType {
    data class Builtin(val key: String) : KeyType
    data class Nominal(val library: PackageKey, val classifier: String, val namespace: String, val path: List<String>) : KeyType
    data class Parameter(val ownerDepth: Long, val role: ParameterRole, val index: Long) : KeyType {
        init { require(ownerDepth in 0..0xffff_ffffL && index in 0..0xffff_ffffL) }
    }
    data class Applied(val base: KeyType, val arguments: List<KeyType>) : KeyType
    /** Canonical inline C encodings, with all references expanded by the key encoder. */
    data class Specialized(val base: KeyType, val arguments: List<List<Byte>>) : KeyType
    data class Union(val alternatives: List<KeyType>) : KeyType { init { require(alternatives.isNotEmpty()) } }
    data class Function(val execution: FunctionExecution, val parameters: List<KeyType>, val result: KeyType) : KeyType
    /** Canonical inline type/argument tree; never source text or parser bytes. */
    data class Dependent(val canonicalTree: List<Byte>) : KeyType
    data class Anonymous(val library: PackageKey, val classifier: String, val origin: AnonymousOrigin) : KeyType
}

sealed interface DeclarationReference {
    data class Local(val target: DeclarationId) : DeclarationReference
    data class Dependency(val dependencyIndex: Long, val key: DeclarationKey) : DeclarationReference {
        init { requireReference(dependencyIndex) }
    }
}

sealed interface NameUse {
    data class Local(val target: DeclarationId) : NameUse
    data class Declaration(val target: DeclarationReference) : NameUse
    /** Only lookup that still requires overload/readonly argument binding belongs here. */
    data class Lookup(
        val name: String,
        val namespace: String?,
        val environment: EnvironmentId,
        val candidates: List<DeclarationReference>
    ) : NameUse { init { require(name.isNotEmpty()) } }
}

enum class ParameterRole(val wireValue: Int) { READONLY(1), LAMBDA_TYPE(2), NATIVE_TYPE(3) }
enum class FunctionExecution(val wireValue: Int) { NORMAL(1), PAUSED(2), GENERATOR(3) }

/** Immutable constant facts; mutable compiler values and their storage never enter this model. */
sealed interface ConstantValue {
    data object Null : ConstantValue
    data class Integral(val value: Long) : ConstantValue
    data class FloatBits(val bits: Int) : ConstantValue
    data class DoubleBits(val bits: Long) : ConstantValue
    data class Bool(val value: Boolean) : ConstantValue
    data class Text(val value: String) : ConstantValue
    data class TypeValue(val value: TypeRef) : ConstantValue
    data class Nbt(val snbt: String) : ConstantValue
    data class Sequence(val elements: List<ConstantRef>) : ConstantValue
    data class Record(val fields: List<ConstantField>) : ConstantValue
    data class Typed(val type: TypeRef, val value: ConstantRef) : ConstantValue
    data class FunctionValue(
        val target: DeclarationReference,
        val captures: List<ConstantCapture>,
        val type: TypeRef
    ) : ConstantValue
}
data class ConstantField(val name: String, val value: ConstantRef)
data class ConstantCapture(val declaration: DeclarationReference, val value: ConstantRef)

interface SemanticNode {
    val scope: ScopeId
    /** Explicit protocol identity; enum ordinal and parser rule numbers are never used. */
    val tag: Int
}

sealed interface TypeSyntax : SemanticNode {
    /** Infer from the preserved expression after its author bindings are available, without evaluating it. */
    data class Inferred(override val scope: ScopeId, val expression: Expression) : TypeSyntax {
        override val tag: Int get() = 0x020b
    }
    data class Name(override val scope: ScopeId, val use: NameUse, val resolved: TypeRef? = null) : TypeSyntax {
        override val tag: Int get() = 0x0201
    }
    data class Apply(
        override val scope: ScopeId, val base: TypeSyntax, val readonly: List<Expression>, val resolved: TypeRef? = null
    ) : TypeSyntax { override val tag: Int get() = 0x0202 }
    data class Container(override val scope: ScopeId, val kind: ContainerKind, val argument: TypeSyntax) : TypeSyntax {
        override val tag: Int get() = 0x0203
    }
    data class Vector(override val scope: ScopeId, val dimensions: Long) : TypeSyntax {
        init { require(dimensions in 1..0xffff_ffffL) }
        override val tag: Int get() = 0x0204
    }
    data class Entity(override val scope: ScopeId, val limit: ConstantRef?, val names: List<String>) : TypeSyntax {
        override val tag: Int get() = 0x0205
    }
    data class Union(override val scope: ScopeId, val alternatives: List<TypeSyntax>) : TypeSyntax {
        init { require(alternatives.isNotEmpty()) }
        override val tag: Int get() = 0x0206
    }
    data class Intersection(override val scope: ScopeId, val components: List<TypeSyntax>) : TypeSyntax {
        init { require(components.isNotEmpty()) }
        override val tag: Int get() = 0x0207
    }
    data class AnonymousData(override val scope: ScopeId, val declaration: DeclarationId) : TypeSyntax {
        override val tag: Int get() = 0x0208
    }
    data class Function(
        override val scope: ScopeId, val parameters: List<TypeSyntax>, val result: TypeSyntax,
        val execution: FunctionExecution = FunctionExecution.NORMAL
    ) : TypeSyntax { override val tag: Int get() = 0x0209 }
    data class Void(override val scope: ScopeId) : TypeSyntax { override val tag: Int get() = 0x020a }
}

enum class ContainerKind(val wireValue: Int) { LIST(1), IMMUTABLE_LIST(2), MAP(3), DICT(4) }
