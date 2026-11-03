package top.mcfpp.library.model

import top.mcfpp.library.syntax.*

enum class DeclarationAccess(val wireValue: Int) { PUBLIC(1), PROTECTED(2), PRIVATE(3) }
object DeclarationFlags {
    const val FINAL = 1L
    const val STATIC = 2L
    const val ABSTRACT = 4L
    const val OVERRIDE = 8L
    const val CONST = 16L
    const val NULLABLE = 32L
    const val FORCE_RUNTIME = 64L
    const val DATA_ONLY = 128L
    const val ALL = 255L
}

data class DeclCommon(
    val key: DeclarationKey?, val origin: AnonymousOrigin?, val name: String, val owner: DeclarationId?,
    val environment: EnvironmentId, val access: DeclarationAccess, val flags: Long,
    val annotations: List<AnnotationId> = emptyList()
) {
    init { require(name.isNotEmpty() && flags >= 0 && flags and DeclarationFlags.ALL.inv() == 0L) }
}

/** Additional D kinds implement this same common/payload contract; table order assigns their IDs. */
interface LibraryDeclaration {
    val common: DeclCommon
    val tag: Int
}

enum class FunctionExecutionKind(val wireValue: Int) { ORDINARY(1), INLINE(2), COMPILE_TIME(3), NATIVE(4) }
@JvmInline value class RuntimeRef(val value: Long) {
    init { require(value in 1..0xffff_ffffL) }
}
data class TagKey(val namespace: String, val name: String)
enum class NativeInvocation(val wireValue: Int) { STATIC(1) }
data class NativeBinding(
    val providerPackage: PackageKey?, val ownerClass: String, val method: String,
    val jvmParameters: List<String>, val jvmResult: String,
    val invocation: NativeInvocation = NativeInvocation.STATIC, val caller: TypeRef? = null
) {
    init { require(ownerClass.isNotEmpty() && method.isNotEmpty() && jvmResult.isNotEmpty() && jvmParameters.none(String::isEmpty)) }
}

/** Generic heads use readonly declarations. A receiver denotes an extension/native receiver. */
data class FunctionDeclaration(
    override val common: DeclCommon, val execution: FunctionExecutionKind,
    val receiver: TypeRef?, val readonly: List<DeclarationId>, val normal: List<DeclarationId>,
    val result: TypeRef?, val body: BodyId?, val native: NativeBinding?,
    val tags: List<TagKey> = emptyList(), val linkedRuntime: List<RuntimeRef> = emptyList()
) : LibraryDeclaration { override val tag: Int get() = 2 }

enum class ValueRole(val wireValue: Int) {
    MEMBER(1), GLOBAL(2), LOCAL(3), READONLY_PARAMETER(4), NORMAL_PARAMETER(5), LAMBDA_PARAMETER(6)
}
enum class ValueStorage(val wireValue: Int) { VAR(1), CONST(2), EXTERNAL(3), IMPORT_VAR(4), PARAMETER(5) }
enum class Variance(val wireValue: Int) { INVARIANT(1), IN(2), OUT(3) }

/** Shared D5 shape for fields, locals and all parameter roles. */
data class ValueDeclaration(
    override val common: DeclCommon, val role: ValueRole, val storage: ValueStorage,
    val type: TypeRef?, val declaredType: NodeRef?, val defaultBody: BodyId?, val frozen: ConstantRef?,
    val variance: Variance = Variance.INVARIANT, val parameterIndex: Long? = null
) : LibraryDeclaration {
    init { require(parameterIndex == null || parameterIndex in 0..0xffff_ffffL) }
    override val tag: Int get() = 5
}

data class ImportRecord(
    val targetNamespace: String, val member: String?, val alias: String?, val from: String?,
    val resolvedPackages: List<PackageKey>
)
data class EnvironmentRecord(val namespace: String, val imports: List<ImportRecord>, val owner: DeclarationId? = null)

class LibraryDeclarations(declarations: List<LibraryDeclaration>, environments: List<EnvironmentRecord>) {
    val declarations: List<LibraryDeclaration> = frozen(declarations.map { declaration ->
        val common = declaration.common.copy(annotations = frozen(declaration.common.annotations))
        when (declaration) {
            is FunctionDeclaration -> declaration.copy(common = common, readonly = frozen(declaration.readonly),
                normal = frozen(declaration.normal), tags = frozen(declaration.tags), linkedRuntime = frozen(declaration.linkedRuntime),
                native = declaration.native?.copy(jvmParameters = frozen(declaration.native.jvmParameters)))
            is ValueDeclaration -> declaration.copy(common = common)
            else -> declaration
        }
    })
    val environments: List<EnvironmentRecord> = frozen(environments.map { environment ->
        environment.copy(imports = frozen(environment.imports.map { it.copy(resolvedPackages = frozen(it.resolvedPackages)) }))
    })
    fun declaration(reference: DeclarationId): LibraryDeclaration {
        require(reference.value <= declarations.size.toLong()) { "Declaration is outside the D table: $reference" }
        return declarations[(reference.value - 1).toInt()]
    }
    fun environment(reference: EnvironmentId): EnvironmentRecord {
        require(reference.value <= environments.size.toLong()) { "Environment is outside the E table: $reference" }
        return environments[(reference.value - 1).toInt()]
    }
}
