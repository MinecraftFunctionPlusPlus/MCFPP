package top.mcfpp.library.model

import top.mcfpp.library.syntax.*

data class LibraryTarget(val minecraftVersion: String, val edition: Int = 1, val requiredCapabilities: List<String> = emptyList())
data class LibraryDependency(val library: PackageKey, val classifier: String)
data class LibraryManifest(
    val library: PackageKey,
    val classifier: String,
    val target: LibraryTarget,
    val dependencies: List<LibraryDependency> = emptyList(),
    val entryEnvironments: List<EnvironmentId> = emptyList(),
    val rootDeclarations: List<DeclarationId> = emptyList(),
    val semantics: String = "mcfpp.source/1",
    val runtimeAbi: String = "mcfpp.runtime/1",
)

data class NamespaceDeclaration(override val common: DeclCommon, val members: List<DeclarationId>) : LibraryDeclaration {
    override val tag: Int get() = 1
}

@JvmInline value class StateCellRef(val value: Long) { init { require(value in 1..0xffff_ffffL) } }
enum class AnnotationPhase(val wireValue: Int) { PENDING(1), COMPLETED(2) }
data class AnnotationRecord(
    val provider: DeclarationReference, val arguments: List<ConstantRef>, val parents: List<AnnotationId>,
    val phase: AnnotationPhase, val state: PortableState? = null,
)
data class PortableState(val schema: String, val version: Long, val fields: List<StateField>)
data class StateField(val name: String, val value: PortableValue)
sealed interface PortableValue {
    data class Constant(val value: ConstantRef) : PortableValue
    data class Declaration(val value: DeclarationReference) : PortableValue
    data class Type(val value: TypeRef) : PortableValue
    data class Annotation(val value: AnnotationId) : PortableValue
    data class Cell(val value: StateCellRef) : PortableValue
}
data class StateMapEntry(val key: ConstantRef, val value: PortableValue)
sealed interface StateCell {
    data class Sequence(val values: List<PortableValue>) : StateCell
    data class Map(val entries: List<StateMapEntry>) : StateCell
    data class Set(val values: List<ConstantRef>) : StateCell
}

enum class RuntimePolicy(val wireValue: Int) { LINK_ONLY(1), REANALYZABLE_FALLBACK(2) }
sealed interface RuntimeRecord {
    data class FunctionBinding(
        val declaration: DeclarationReference, val resourcePath: String, val target: LibraryTarget,
        val generationOptions: ConstantRef, val policy: RuntimePolicy,
    ) : RuntimeRecord
    data class TagContribution(val tag: TagKey, val members: List<DeclarationReference>, val replace: Boolean) : RuntimeRecord
    data class Scoreboard(val name: String, val criterion: String, val display: ConstantRef?) : RuntimeRecord
    data class Initialization(val declaration: DeclarationReference, val order: Long) : RuntimeRecord
}
enum class ResourceOrigin(val wireValue: Int) { STATIC(1), PRECOMPILED(2), RUNTIME_SUPPORT(3) }
data class LibraryResource(
    val path: String, val byteLength: ULong, val sha256: List<Byte>, val owner: DeclarationReference?,
    val module: String, val activation: String?, val origin: ResourceOrigin,
)
data class NodeLocation(val node: NodeRef, val fileIndex: Long, val start: Long, val end: Long, val line: Long, val column: Long)
data class DeclarationDocument(val declaration: DeclarationId, val text: String)
data class LibraryDebug(
    val files: List<String>, val locations: List<NodeLocation>, val documents: List<DeclarationDocument>, val producer: String? = null,
)

/** Pure protocol document. Successful decoding does not bind dependency language symbols or load providers. */
class LibraryModel(
    val manifest: LibraryManifest,
    val declarations: LibraryDeclarations,
    val tables: LibraryTables,
    val syntax: LibrarySyntax,
    annotations: List<AnnotationRecord> = emptyList(),
    stateCells: List<StateCell> = emptyList(),
    runtime: List<RuntimeRecord> = emptyList(),
    resources: List<LibraryResource> = emptyList(),
    val debug: LibraryDebug? = null,
) {
    val annotations = frozen(annotations.map { it.copy(arguments = frozen(it.arguments), parents = frozen(it.parents),
        state = it.state?.copy(fields = frozen(it.state.fields))) })
    val stateCells = frozen(stateCells.map { when (it) {
        is StateCell.Sequence -> it.copy(values = frozen(it.values))
        is StateCell.Map -> it.copy(entries = frozen(it.entries))
        is StateCell.Set -> it.copy(values = frozen(it.values))
    } })
    val runtime = frozen(runtime)
    val resources = frozen(resources.map { it.copy(sha256 = frozen(it.sha256)) })
    var localReferencesValidated: Boolean = false
        internal set
    /** This codec never resolves dependency declarations or makes a runnable compiler session. */
    val requiresDependencyBinding: Boolean get() = manifest.dependencies.isNotEmpty()
}

/** Target checks are caller-selected; omitted target fields mean target binding is deferred. */
data class LibraryCompatibility(
    val semantics: Set<String> = setOf("mcfpp.source/1"),
    val runtimeAbis: Set<String> = setOf("mcfpp.runtime/1"),
    val minecraftVersion: String? = null,
    val edition: Int = 1,
    val availableCapabilities: Set<String>? = null,
)
