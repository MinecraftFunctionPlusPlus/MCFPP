package top.mcfpp.mni.expand

import top.mcfpp.analysis.PreparedCompilerSignature
import top.mcfpp.backend.ReferenceAccessHelpers
import top.mcfpp.backend.ReferenceHelperRecipe
import top.mcfpp.io.info.MacroLoadingSnapshot
import top.mcfpp.io.info.NamespaceMacroSnapshot
import top.mcfpp.library.compiler.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.TypeRef
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.immutableList
import top.mcfpp.mni.api.immutableMap
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.type.MCFPPType
import java.lang.reflect.Method

/** Exact managed classes admitted for the lifetime of an explicitly activated library directory. */
internal object ManagedAnnotationAdmission {
    fun contains(type: Class<out Annotation>): Boolean = PreparedNativeDirectory.current()?.admits(type) == true
}

/**
 * Link after allocating and binding the complete declaration/type directory. Provider codecs are
 * registered explicitly by their managed plugin before preparation; no namespace scan discovers them.
 */
data class NativeLibraryUnit(
    val declarations: LibraryDeclarations,
    val types: CompilerTypeTableRestore,
    val managed: ManagedNativeBindings,
)

class PreparedNativeDirectory private constructor(
    private val units: List<NativeLibraryUnit>,
    private val codecs: PortableAnnotationCodecs
) : AutoCloseable {
    private var closed = false
    private var activated = false
    private val methods = java.util.IdentityHashMap<NativeLibraryUnit, MutableMap<NativeBinding, Method>>()
    private val macros = units.flatMap { unit -> unit.declarations.declarations.filterIsInstance<MacroDeclaration>().map { unit to it } }
    private val providers = units.flatMap { unit -> unit.declarations.declarations.filterIsInstance<AnnotationProviderDeclaration>().map { unit to it.binding } }
    private val providerClasses: List<Class<out Annotation>>
    private val helpers: List<ReferenceHelperRecipe> by lazy {
        requireOpen()
        units.flatMap { unit -> unit.declarations.declarations.filterIsInstance<ReferenceHelperDeclaration>()
            .map { NativeDeclarations.restoreReference(it, unit.types) } }.also(ReferenceAccessHelpers::validateRecipes)
    }
    private data class Metadata(val types: List<MCFPPType>, val callbacks: List<SourceCallbackSignature>)
    private val metadata by lazy { describeTypes() }
    internal val sourceTypes: List<MCFPPType> get() { requireOpen(); return metadata.types }
    internal val callbacks: List<SourceCallbackSignature> get() { requireOpen(); return metadata.callbacks }

    init {
        val registered = codecs.bindings().toSet()
        require(providers.all { it.second in registered }) { "Every D13 provider requires an explicitly registered portable codec" }
        providerClasses = providers.map { (unit, provider) -> NativeDeclarations.restoreAnnotationProvider(provider, unit.managed) }.distinct()
        units.forEach { unit -> unit.declarations.declarations.filterIsInstance<FunctionDeclaration>().mapNotNull { it.native }.forEach { link(unit, it) } }
        macros.forEach { (unit, macro) -> link(unit, macro.binding.native) }
    }
    private fun describeTypes(): Metadata {
        requireOpen()
        val callbackShapes = arrayListOf<SourceCallbackSignature>()
        fun parameterMode(mode: MacroParameterMode): ParamMode = when (mode) {
            MacroParameterMode.VALUE -> ParamMode.VALUE; MacroParameterMode.REF -> ParamMode.REF
            MacroParameterMode.CONST -> ParamMode.CONST; MacroParameterMode.INLINE -> ParamMode.INLINE
        }
        fun describe(unit: NativeLibraryUnit, type: MacroType): SourceCallbackType = when (type) {
            is MacroType.Source -> {
                SourceCallbackType.Declared(unit.types.type(type.type).typeId)
            }
            is MacroType.Callback -> SourceCallbackType.Callback(SourceCallbackSignature(type.parameters.map {
                SourceCallbackParameter(describe(unit, it.type), parameterMode(it.mode))
            }, describe(unit, type.result)).also { callbackShapes += it })
        }
        macros.forEach { (unit, macro) -> macro.binding.parameters.forEach { describe(unit, it.type) }; describe(unit, macro.binding.result) }
        val callbackTypes = units.flatMap { unit ->
            val local = linkedSetOf<TypeRef>()
            fun collect(type: MacroType) { when (type) {
                is MacroType.Source -> local.add(type.type)
                is MacroType.Callback -> { type.parameters.forEach { collect(it.type) }; collect(type.result) }
            } }
            unit.declarations.declarations.filterIsInstance<MacroDeclaration>().forEach { macro -> macro.binding.parameters.forEach { collect(it.type) }; collect(macro.binding.result) }
            unit.declarations.declarations.filterIsInstance<FunctionDeclaration>().filter { it.native != null }.forEach {
                it.receiver?.let(local::add); it.result?.let(local::add); it.native?.caller?.let(local::add)
            }
            local.map(unit.types::type)
        }
        return Metadata(immutableList((callbackTypes + helpers.flatMap { it.operandTypes.values }).distinctBy { it.typeId }), immutableList(callbackShapes))
    }

    private fun link(unit: NativeLibraryUnit, binding: NativeBinding) { methods.getOrPut(unit) { linkedMapOf() }.getOrPut(binding) { NativeDeclarations.resolveMethod(binding, unit.managed) } }
    internal fun requireOpen() { check(!closed) { "Prepared native directory is closed" } }
    internal fun admits(type: Class<out Annotation>): Boolean = providerClasses.any { it === type }

    /** Standard MNI preparation picks up this directory until close; independent sessions cannot overlap. */
    fun activate(): PreparedNativeDirectory {
        requireOpen()
        check(currentDirectory.get() == null && !activated) { "A native directory is already active on this thread" }
        activated = true
        currentDirectory.set(this)
        return this
    }

    fun nativeMethod(binding: NativeBinding): Method {
        requireOpen()
        return methods.values.mapNotNull { it[binding] }.distinct().singleOrNull()
            ?: error("Native method is absent or ambiguous without its owning unit")
    }
    fun nativeMethod(unit: NativeLibraryUnit, binding: NativeBinding): Method {
        requireOpen()
        return methods[unit]?.get(binding) ?: error("Native method is outside its prepared unit")
    }

    fun restoreAnnotations(records: List<AnnotationRecord>, cells: List<StateCell>, bindings: AnnotationRestoreBindings): RestoredAnnotations {
        requireOpen()
        return codecs.restore(records, cells, object : AnnotationRestoreBindings by bindings {
            override fun provider(reference: top.mcfpp.library.syntax.DeclarationReference): AnnotationProviderBinding {
                val binding = bindings.provider(reference)
                val matches = providers.filter { it.second == binding }
                require(matches.isNotEmpty()) { "Annotation provider is outside the prepared D13 directory" }
                require(matches.map { NativeDeclarations.restoreAnnotationProvider(binding, it.first.managed) }.distinct().size == 1)
                return binding
            }
        })
    }

    fun installReferenceHelpers() { requireOpen(); ReferenceAccessHelpers.restoreRecipes(helpers) }
    fun installReferenceHelpersOwned(): AutoCloseable { requireOpen(); return ReferenceAccessHelpers.installOwned(helpers) }

    internal fun hasMacro(namespace: String, identifier: String): Boolean {
        requireOpen()
        return macros.any { (unit, macro) -> unit.declarations.environment(macro.common.environment).namespace == namespace && macro.binding.sourceName == identifier }
    }

    internal fun bindMacros(catalog: SourceTypeCatalog): Map<String, List<MacroFunction>> {
        requireOpen()
        return macros.groupBy { (unit, macro) -> unit.declarations.environment(macro.common.environment).namespace }.mapValues { (namespace, records) ->
            records.map { (unit, macro) -> NativeDeclarations.restoreMacro(namespace, macro, unit.types, catalog, unit.managed) }
        }
    }

    /** Frozen B candidates retain the owning arena; D/T numbers are never merged across units. */
    internal fun bindMacros(unit: NativeLibraryUnit, declarations: List<top.mcfpp.library.syntax.DeclarationId>, catalog: SourceTypeCatalog): List<MacroFunction> {
        requireOpen()
        require(units.any { it === unit }) { "Macro unit is outside this prepared directory" }
        return declarations.map { id ->
            val record = unit.declarations.declaration(id) as? MacroDeclaration
                ?: error("Frozen macro candidate is not D11: $id")
            val namespace = unit.declarations.environment(record.common.environment).namespace
            NativeDeclarations.restoreMacro(namespace, record, unit.types, catalog, unit.managed)
        }
    }

    internal fun validateOrdinary(signatures: Collection<PreparedCompilerSignature>, catalog: SourceTypeCatalog, bound: Collection<MacroFunction>) {
        requireOpen()
        bound.forEach { macro ->
            require(signatures.none { signature ->
                val function = signature.function
                function.owner == null && function.namespace == macro.namespace && function.identifier == macro.identifier && signature.readonly.isEmpty() &&
                    signature.normal.size == macro.parameters.size && signature.normal.zip(macro.parameters).all { (normal, parameter) ->
                        catalog.ref(normal.type).sameType(parameter.type())
                    }
            }) { "Imported macro conflicts with an ordinary declaration: ${macro.qualifiedName}" }
        }
    }

    override fun close() {
        if (closed) return
        if (activated) {
            check(currentDirectory.get() === this) { "Close the native directory on its activation thread" }
            currentDirectory.remove(); activated = false
        }
        closed = true
    }

    companion object {
        private val currentDirectory = ThreadLocal<PreparedNativeDirectory>()
        internal fun current(): PreparedNativeDirectory? = currentDirectory.get()?.also { it.requireOpen() }
        fun prepare(declarations: LibraryDeclarations, types: CompilerTypeTableRestore,
                    managed: ManagedNativeBindings, codecs: PortableAnnotationCodecs): PreparedNativeDirectory =
            prepareAll(listOf(NativeLibraryUnit(declarations, types, managed)), codecs)
        fun prepareAll(units: List<NativeLibraryUnit>, codecs: PortableAnnotationCodecs): PreparedNativeDirectory {
            require(units.isNotEmpty()) { "A native directory requires at least one unit" }
            return PreparedNativeDirectory(units.toList(), codecs)
        }
    }
}

/** All macros in this view share the exact source catalog and canonical references of one round. */
internal class PreparedMacroCatalog(snapshot: MacroLoadingSnapshot, private val directory: PreparedNativeDirectory? = null) {
    val sourceSnapshot = snapshot
    val types = snapshot.types
    val diagnostics = snapshot.diagnostics
    val namespaces: Map<String, NamespaceMacroSnapshot>
    init {
        val imported = directory?.bindMacros(types).orEmpty()
        namespaces = immutableMap((snapshot.namespaces.keys + imported.keys).associateWith { namespace ->
            val functions = snapshot.namespaces[namespace]?.functions.orEmpty() + imported[namespace].orEmpty()
            functions.forEachIndexed { index, function ->
                require(functions.take(index).none { other -> other.identifier == function.identifier &&
                    other.parameters.size == function.parameters.size && other.parameters.zip(function.parameters).all { (a, b) -> a.type().sameType(b.type()) }
                }) { "Duplicate prepared macro signature: ${function.qualifiedName}" }
            }
            NamespaceMacroSnapshot(namespace, types, functions)
        })
    }
    fun requireCurrent() { directory?.requireOpen() }
}
