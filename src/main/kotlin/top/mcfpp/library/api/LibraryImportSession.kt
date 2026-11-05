package top.mcfpp.library.api

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.StructuredNameBinding
import top.mcfpp.analysis.StructuredSessionBindings
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.StructuredBindings
import top.mcfpp.mni.expand.PreparedNativeDirectory
import top.mcfpp.mni.expand.NativeLibraryUnit
import top.mcfpp.mni.expand.FrozenMacroCandidates
import top.mcfpp.mni.expand.SourceTypeCatalog
import top.mcfpp.mni.expand.MacroFunction
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.FunctionTargetResolver
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum as CompilerEnum
import top.mcfpp.model.Member
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.type.*
import top.mcfpp.util.NBTUtil.toJava
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap

/** Progress of a [LibraryImportSession]. */
enum class LibraryImportState {
    /** Accepts library payloads through stage. */
    OPEN,
    /** All staged declarations and dependencies are resolved. */
    BOUND,
    /** Imported declarations are published in compiler namespaces. */
    COMMITTED,
    /** Import failed and released its body repository. */
    FAILED,
    /** Session resources and imported registrations are released. */
    CLOSED
}
/** An import failure with a machine-readable [code] and a human-readable explanation. */
class LibraryImportException(val code: String, message: String) : IllegalStateException("$code: $message")
/**
 * A function tag contribution resolved to compiler functions.
 * @property tag Target Minecraft function tag.
 * @property members Functions contributed in their declared order.
 * @property replace Whether this contribution replaces earlier tag contents.
 */
data class BoundLibraryTag(val tag: TagKey, val members: List<Function>, val replace: Boolean)

/**
 * Imports version 1 libraries into the current compiler.
 * Stage every library and dependency, call [bindAll], then call [commit] to publish the declarations.
 * Keep the session open while imported functions are in use. [close] releases their registrations and body data.
 * @param compatibility Accepted library format and semantic versions.
 */
class LibraryImportSession(private val compatibility: LibraryCompatibility = LibraryCompatibility()) : AutoCloseable {
    /** Current import phase. Methods advance this state as they complete. */
    var state: LibraryImportState = LibraryImportState.OPEN
        private set
    private val units = linkedMapOf<LibraryIdentity, ImportedUnit>()
    private val namespaces = linkedMapOf<String, Namespace>()
    private val repository = SessionBodyRepository()
    private val published = linkedMapOf<String, Namespace>()
    private val publicationRegistries = linkedMapOf<String, MutableMap<String, Namespace>>()
    private val publicationBefore = linkedMapOf<String, NamespaceContents>()
    private val registeredAnonymous = java.util.Collections.newSetFromMap(IdentityHashMap<DataTemplate, Boolean>())
    private val registeredTypes = linkedMapOf<TypeId, MCFPPType>()
    private val nominalViews = IdentityHashMap<DataTemplate, MutableSet<MCFPPDataTemplateType>>()
    private var typeDirectoryVersion = 0L

    private fun rememberType(type: MCFPPType): MCFPPType {
        if (registeredTypes.putIfAbsent(type.typeId, type) == null) typeDirectoryVersion++
        rememberNominalViews(type)
        return type
    }

    /** Enroll nested parent descriptors too: changing B then updates A's existing B view in place.
     * The index is session-owned and never resolves a generic shell while inspecting it. */
    private fun rememberNominalViews(root: MCFPPType) {
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<MCFPPType, Boolean>())
        val pending = ArrayDeque<MCFPPType>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val type = pending.removeLast()
            if (!seen.add(type)) continue
            if (type is MCFPPDataTemplateType) {
                val views = nominalViews.getOrPut(type.rawTemplateDeclaration()) {
                    java.util.Collections.newSetFromMap(IdentityHashMap<MCFPPDataTemplateType, Boolean>())
                }
                if (!views.add(type) && type !== root) continue
            }
            pending.addAll(type.parentType)
        }
    }

    /** Generic descriptors replace their unresolved shell after initial import enrollment. */
    internal fun trackResolvedTypeView(type: MCFPPDataTemplateType) {
        requireUsable()
        rememberNominalViews(type)
    }

    /** A resolved parent list changes only this declaration's descriptors. Their parents are
     * shared within this update and enrolled for later delayed-parent notifications. */
    private fun refreshNominalViews(owner: DataTemplate) {
        typeDirectoryVersion++
        val views = nominalViews[owner]?.filter { it.rawTemplateDeclaration() === owner }.orEmpty().toMutableList()
        // A generic descriptor can replace its unresolved raw shell after initial enrollment.
        registeredTypes.values.filterIsInstance<MCFPPGenericDataTemplateType>().forEach { type ->
            if (type.rawTemplateDeclaration() === owner && views.none { it === type }) views.add(type)
        }
        if (views.isEmpty()) return
        val parents = owner.getType().parentType
        views.forEach { it.parentType = ArrayList(parents) }
        parents.forEach(::rememberNominalViews)
    }

    /** Exact session directory lookup; querying never materializes a new generic binding. */
    internal fun resolveTypeIdentity(identity: TypeId): MCFPPType? {
        repository.requireOpen(); requireUsable()
        return registeredTypes[identity]?.takeUnless { it is UnresolvedType || it is MCFPPGenericParamType }
    }
    private val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
    private val identities = LibraryIdentities<Any>()
    private val previousScoreboards = linkedMapOf<String, top.mcfpp.lib.SbObject?>()
    private var managed: ManagedNativeBindings = object : ManagedNativeBindings {
        override fun loader(provider: PackageKey?): ClassLoader {
            require(provider == null) { "External providers require a managed loader registration" }
            return top.mcfpp.Project.classLoader
        }
        override fun provider(owner: Class<*>): PackageKey? = null
    }
    private var codecs = PortableAnnotationCodecs.builtins()
    private var nativeDirectory: PreparedNativeDirectory? = null
    private var preparedBuiltinMode = false

    /** Only the bundled, digest-checked build plan may defer completed declarations. */
    internal fun enablePreparedBuiltin() {
        check(state == LibraryImportState.OPEN && units.isEmpty())
        preparedBuiltinMode = true
    }
    private var helperLease: AutoCloseable? = null
    private val functionTargets = FunctionTargetResolver { identifier ->
        repository.requireOpen()
        ownedFunctions().singleOrNull { it.namespaceID.toString() == identifier }
    }
    private val sourceProviders = linkedMapOf<String, PackageKey?>()

    /**
     * The prepared kernel retains this import session. Its object graph is restored by the
     * compiler journal; these private indexes must also release every request-owned object.
     * Keep this list explicit so new mutable caches have an obvious lifecycle owner.
     */
    internal fun checkpointForReuse(): () -> Unit {
        check(state == LibraryImportState.COMMITTED) { "Only a committed library can back a compiler kernel" }
        // Materialize stable provider metadata before the deep baseline captures its types.
        nativeDirectory?.sourceTypes
        val savedVersion = typeDirectoryVersion
        val snapshot = ReusableState()
        snapshot.map(registeredTypes); snapshot.map(nominalViews)
        nominalViews.values.forEach(snapshot::set)
        snapshot.add(identities.checkpointForReuse())
        snapshot.add(repository.checkpointForReuse())
        units.values.forEach { snapshot.add(it.checkpointForReuse()) }
        return {
            check(state == LibraryImportState.COMMITTED) { "The prepared library session is no longer usable" }
            snapshot.restore()
            typeDirectoryVersion = savedVersion
        }
    }

    /** Small, allocation-free counters used to verify that request-owned indexes reset. */
    internal fun retainedStateCounts(): List<Int> = listOf(
        registeredTypes.size, nominalViews.size, nominalViews.values.sumOf { it.size },
        units.values.sumOf { it.retainedRequestEntries() }
    )

    /** Configures provider loading and annotation decoding before the first library is staged. */
    fun configureNative(managed: ManagedNativeBindings, codecs: PortableAnnotationCodecs, sourceProviders: Map<String, PackageKey?> = emptyMap()) {
        check(state == LibraryImportState.OPEN && units.isEmpty()) { "Configure providers before staging" }
        if (this.codecs !== codecs) this.codecs.close()
        this.managed = managed
        this.codecs = codecs
        this.sourceProviders.putAll(sourceProviders)
    }
    private val managedView = object : ManagedNativeBindings {
        override fun loader(provider: PackageKey?): ClassLoader { requireUsable(); return managed.loader(provider) }
        override fun provider(owner: Class<*>): PackageKey? { requireUsable(); return managed.provider(owner) }
    }
    /** Returns the provider loader associated with this usable session. */
    fun nativeBindings(): ManagedNativeBindings { requireUsable(); return managedView }
    /** Returns the annotation codecs owned by this usable session. */
    fun annotationCodecs(): PortableAnnotationCodecs { requireUsable(); return codecs }
    private fun requireUsable() { check(state != LibraryImportState.FAILED && state != LibraryImportState.CLOSED) { "Library session is not usable" } }
    private fun requireBound() { check(state == LibraryImportState.BOUND || state == LibraryImportState.COMMITTED) { "Library session is not bound" } }

    private fun inventory(): List<ImportedUnit> = units.values.sortedWith { a, b ->
        compareUtf8(a.identity.library.coordinate, b.identity.library.coordinate).takeIf { it != 0 }
            ?: compareUtf8(a.identity.library.version, b.identity.library.version).takeIf { it != 0 }
            ?: compareUtf8(a.identity.classifier, b.identity.classifier)
    }
    /** Returns bound dependencies in reproducible package order for a later library export. */
    fun exportDependencies(): List<LibraryDependency> { requireBound(); return inventory().map { LibraryDependency(it.identity.library, it.identity.classifier) } }
    /** Returns ownership information that lets an exporter retain references to imported declarations. */
    fun exportOwnership(): LibraryExportOwnership {
        requireBound()
        fun reference(value: Any): DeclarationReference.Dependency? {
            requireBound()
            inventory().forEachIndexed { index, unit -> unit.ownedReference(value)?.let { return DeclarationReference.Dependency(index + 1L, it) } }
            return null
        }
        return object : LibraryExportOwnership {
            override fun functionReference(function: Function) = reference(function)
            override fun typeReference(identity: TypeId): DeclarationReference.Dependency? {
                requireBound()
                inventory().forEachIndexed { index, unit -> unit.typeReference(identity)?.let { return DeclarationReference.Dependency(index + 1L, it) } }
                return null
            }
            override fun declarationReference(value: Any) = reference(value)
            override fun isDependencyDeclaration(value: Any): Boolean {
                requireBound()
                return units.values.any { it.ownsDeclaration(value) }
            }
            override fun sourceSignature(function: Function): FunctionSourceSignature? {
                requireBound()
                return inventory().firstNotNullOfOrNull { it.sourceSignature(function) }
            }
            override fun macroReferences(namespace: String?, name: String, source: MCFPPFile): List<DeclarationReference.Dependency> {
                requireBound()
                val author = units.values.firstOrNull { unit -> unit.ownsFile(source) }
                val references = author?.macroReferences(namespace, name, source) ?: run {
                    val visible = if (namespace != null) setOf(namespace) else setOf(source.namespace.identifier) +
                        source.field.importedNamespaceField.values.map { it.identifier } +
                        source.unsolvedImports.filterValues { it == "*" || it == name }.keys + GlobalScope.stdNamespaces.keys
                    units.values.flatMap { unit -> unit.macroDeclarations().filter { (_, record) ->
                        unit.model.declarations.environment(record.common.environment).namespace in visible && record.binding.sourceName == name
                    }.map { (id, _) -> unit to id } }
                }
                val inventory = inventory()
                return references.map { (unit, id) -> DeclarationReference.Dependency(inventory.indexOf(unit) + 1L,
                    requireNotNull(unit.model.declarations.declaration(id).common.key)) }
            }
            override fun namespacePackages(namespace: String): List<PackageKey> {
                requireBound()
                return inventory().filter { unit -> unit.model.declarations.environments.any { it.namespace == namespace } }.map { it.identity.library }.distinct()
            }
        }
    }
    private fun providerPackages(ownerClass: String): List<PackageKey?> = buildList {
        units.values.forEach { unit -> unit.model.declarations.declarations.forEach { record -> when (record) {
            is FunctionDeclaration -> record.native?.takeIf { it.ownerClass == ownerClass }?.let { add(it.providerPackage) }
            is MacroDeclaration -> record.binding.native.takeIf { it.ownerClass == ownerClass }?.let { add(it.providerPackage) }
            is AnnotationProviderDeclaration -> record.binding.takeIf { it.ownerClass == ownerClass }?.let { add(it.providerPackage) }
        } } }
    }.distinct()
    private fun isCoreProvider(ownerClass: String): Boolean {
        if (!ownerClass.startsWith("top.mcfpp.mni.") && !ownerClass.startsWith("top.mcfpp.core.")) return false
        val loader = NativeDeclarations::class.java.classLoader
        return try { Class.forName(ownerClass, false, loader).classLoader === loader } catch (_: ClassNotFoundException) { false }
    }
    /** Tests whether the bound session can identify the JVM provider class named by [ownerClass]. */
    fun hasSourceProvider(ownerClass: String): Boolean {
        requireBound()
        return providerPackages(ownerClass).isNotEmpty() || sourceProviders.containsKey(ownerClass) || isCoreProvider(ownerClass)
    }
    /** Resolves the package providing [ownerClass]. Null identifies a compiler-provided class. */
    fun sourceProvider(ownerClass: String): PackageKey? {
        requireBound()
        val bindings = providerPackages(ownerClass)
        val provider = when {
            bindings.size == 1 -> bindings.single()
            bindings.isEmpty() && sourceProviders.containsKey(ownerClass) -> sourceProviders[ownerClass]
            bindings.isEmpty() && isCoreProvider(ownerClass) -> null
            bindings.isEmpty() -> problem("LIB_NATIVE_PROVIDER_UNREGISTERED", "Native owner is not registered: $ownerClass")
            else -> problem("LIB_NATIVE_PROVIDER_IDENTITY", "Native owner has ambiguous providers: $ownerClass")
        }
        val owner = Class.forName(ownerClass, false, managed.loader(provider))
        require(managed.provider(owner) == provider)
        return provider
    }
    /** Returns paths whose saved function commands yield to a retained, recompiled source body. */
    fun suppressedResourcePaths(): Set<String> {
        requireBound()
        return units.values.flatMap { unit -> unit.model.runtime.filterIsInstance<RuntimeRecord.FunctionBinding>().filter {
            it.policy == RuntimePolicy.REANALYZABLE_FALLBACK && unit.retainsBody(it.declaration)
        }.map { it.resourcePath } }.toSet()
    }
    /** Returns copies of bound static resource bytes, after removing paths replaced by recompiled bodies. */
    fun resources(): Map<String, ByteArray> {
        requireBound()
        val suppressed = suppressedResourcePaths()
        val result = linkedMapOf<String, ByteArray>()
        inventory().forEach { unit -> unit.resourceBytes.forEach { (path, bytes) -> if (path !in suppressed) {
            val previous = result[path]
            require(previous == null || previous.contentEquals(bytes)) { "Conflicting library resource $path" }
            result[path] = bytes.copyOf()
        } } }
        return result
    }
    /** Returns runtime records from all bound libraries in reproducible package order. */
    fun runtime(): List<RuntimeRecord> { requireBound(); return inventory().flatMap { it.model.runtime } }
    /** Returns function tag contributions with each member resolved to its compiler function. */
    fun tagContributions(): List<BoundLibraryTag> {
        requireBound()
        return inventory().flatMap { unit -> unit.model.runtime.filterIsInstance<RuntimeRecord.TagContribution>().map { record ->
            BoundLibraryTag(record.tag, record.members.map { reference -> val (owner, id) = resolve(unit, reference); owner.callable(id) }, record.replace)
        } }
    }
    /** Compiles initializer and tag functions after commit, then registers initialization in dependency order. Returns the prepared functions. */
    fun prepareInitializers(): List<Function> = guarded {
        check(state == LibraryImportState.COMMITTED) { "Initializers require a committed directory" }
        val ordered = arrayListOf<ImportedUnit>()
        val visiting = hashSetOf<LibraryIdentity>()
        val visited = hashSetOf<LibraryIdentity>()
        fun visit(unit: ImportedUnit) {
            if (unit.identity in visited) return
            check(visiting.add(unit.identity)) { "Cyclic initialization dependencies" }
            unit.model.manifest.dependencies.map { units.getValue(LibraryIdentity(it.library, it.classifier)) }
                .sortedWith { a, b -> compareUtf8(a.identity.library.coordinate + "/" + a.identity.classifier, b.identity.library.coordinate + "/" + b.identity.classifier) }.forEach(::visit)
            visiting.remove(unit.identity); visited.add(unit.identity); ordered.add(unit)
        }
        inventory().forEach(::visit)
        val initializers = ordered.flatMap { unit -> unit.model.runtime.filterIsInstance<RuntimeRecord.Initialization>().sortedBy { it.order }.map {
            val (owner, id) = resolve(unit, it.declaration); owner.callable(id)
        } }
        val contributions = tagContributions()
        val targets = (initializers + contributions.flatMap { it.members }).distinctBy { it.declarationId }
        // Compile every target before changing published tag membership or order.
        targets.forEach { if (!it.bodyCompiled && it.hasBody) it.compileBody() }
        initializers.forEach { function ->
            if (FunctionTag.LOAD.functions.none { it === function }) function.addTag("minecraft", "load")
        }
        contributions.forEach { record -> record.members.forEach { function ->
            val tag = GlobalScope.functionTags["${record.tag.namespace}:${record.tag.name}"]
            if (tag?.functions?.none { it === function } != false) function.addTag(record.tag.namespace, record.tag.name)
        } }
        val owned = java.util.Collections.newSetFromMap(IdentityHashMap<Function, Boolean>()).apply { addAll(ownedFunctions()) }
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        val orderedMembers = (initializers + FunctionTag.LOAD.functions.filter { it in owned }).filter(seen::add)
        val replacement = orderedMembers.iterator()
        val load = FunctionTag.LOAD.functions.mapNotNull { function ->
            if (function !in owned) function else if (replacement.hasNext()) replacement.next() else null
        }
        check(!replacement.hasNext()) { "Initializer is outside the session's LOAD membership" }
        FunctionTag.LOAD.functions.clear(); FunctionTag.LOAD.functions.addAll(load)
        targets
    }
    /** Returns whether the imported annotation's declaration effect is pending or completed. */
    fun annotationPhase(value: Annotation): AnnotationPhase {
        requireBound()
        return units.values.firstNotNullOfOrNull { it.phase(value) } ?: error("Annotation is outside the import session")
    }

    private fun compareUtf8(a: String, b: String): Int {
        val left = a.toByteArray(Charsets.UTF_8); val right = b.toByteArray(Charsets.UTF_8)
        for (index in 0 until minOf(left.size, right.size)) {
            val difference = (left[index].toInt() and 255) - (right[index].toInt() and 255)
            if (difference != 0) return difference
        }
        return left.size - right.size
    }

    /** Reads and stages a library file. [expectedIdentity], when supplied, must match its manifest. */
    fun stage(path: Path, expectedIdentity: LibraryIdentity? = null): LibraryModel = guarded { stage(Files.readAllBytes(path), expectedIdentity) }

    /** Decodes and stages [bytes], checks package identity, and copies resources after validating their lengths and SHA-256 digests. */
    fun stage(bytes: ByteArray, expectedIdentity: LibraryIdentity? = null, resources: Map<String, ByteArray> = emptyMap()): LibraryModel = guarded {
        check(state == LibraryImportState.OPEN) { "Libraries must all be staged before binding" }
        stageDecoded(codec.decode(bytes, compatibility), expectedIdentity, resources)
    }

    /** Archive-owned builtin snapshots share only immutable protocol nodes, never session state. */
    internal fun stage(source: top.mcfpp.io.LibBinReader.Payload): LibraryModel = guarded {
        check(state == LibraryImportState.OPEN) { "Libraries must all be staged before binding" }
        val model = source.builtinSnapshot?.decode(source.binary, codec, compatibility) ?: codec.decode(source.binary, compatibility)
        if (model.importPlan == null) source.builtinPlan?.let {
            model.importPlan = BuiltinImportPlan.decode(it, source.binary, model)
        }
        stageDecoded(model, source.identity, source.resources)
    }

    private fun stageDecoded(model: LibraryModel, expectedIdentity: LibraryIdentity?, resources: Map<String, ByteArray>): LibraryModel {
        check(state == LibraryImportState.OPEN) { "Libraries must all be staged before binding" }
        val identity = LibraryIdentity(model.manifest.library, model.manifest.classifier)
        if (expectedIdentity != null && identity != expectedIdentity) problem("LIB_DEPENDENCY", "Payload identity differs from the resolved package: $identity")
        if (identity in units) problem("LIB_DECL_CONFLICT", "Library has already been staged: $identity")
        if (units.keys.any { it.library.coordinate == identity.library.coordinate && it.library.version != identity.library.version })
            problem("LIB_DEPENDENCY", "Multiple versions of one package are not supported")
        validateResourceBytes(model, resources)
        val keys = model.importPlan?.publicKeys?.values?.toList()
            ?: model.declarations.declarations.mapNotNull { it.common.key }.map { TypeTableCodec.declarationKeyBytes(it).toList() }
        if (keys.distinct().size != keys.size) problem("LIB_DECL_CONFLICT", "Duplicate public declaration key")
        units[identity] = ImportedUnit(identity, model, resources.mapValues { it.value.copyOf() })
        return model
    }

    private fun validateResourceBytes(model: LibraryModel, resources: Map<String, ByteArray>) {
        require(resources.keys == model.resources.map { it.path }.toSet()) { "Resource payload inventory differs from its manifest" }
        model.resources.forEach { record ->
            val bytes = resources.getValue(record.path)
            require(bytes.size.toULong() == record.byteLength && java.security.MessageDigest.getInstance("SHA-256").digest(bytes).toList() == record.sha256) {
                "Resource length or digest differs: ${record.path}"
            }
        }
    }

    /** Resolves all staged libraries together, checks their dependencies and declarations, and advances to [LibraryImportState.BOUND]. */
    fun bindAll() = guarded {
        val ambient = top.mcfpp.mni.expand.CompilerStateJournal.checkpoint()
        try {
        check(state == LibraryImportState.OPEN) { "Import session is not open" }
        units.values.forEach { unit -> unit.model.manifest.dependencies.forEach { dependency ->
            if (LibraryIdentity(dependency.library, dependency.classifier) !in units)
                problem("LIB_DEPENDENCY", "Dependency has not been staged: ${dependency.library}")
        } }
        units.values.forEach { unit -> unit.model.declarations.declarations.filterIsInstance<NamespaceDeclaration>().forEach {
            namespaces.getOrPut(it.common.name) { Namespace(it.common.name) }
        } }
        units.values.forEach(ImportedUnit::allocateEnvironments)
        units.values.forEach(ImportedUnit::registerIdentities)
        units.values.forEach(ImportedUnit::prepareBindings)
        units.values.forEach(ImportedUnit::allocateCompounds)
        units.values.forEach(ImportedUnit::prepareGenericHeaders)
        if (units.isNotEmpty()) nativeDirectory = PreparedNativeDirectory.prepareAll(units.values.map { it.nativeUnit }, codecs).activate()
        units.values.forEach(ImportedUnit::allocateHeads)
        units.values.forEach(ImportedUnit::finishBodies)
        units.values.forEach(ImportedUnit::finishCompounds)
        units.values.forEach(ImportedUnit::restoreAnnotations)
        units.values.forEach(ImportedUnit::bindEnvironments)
        units.values.forEach(ImportedUnit::validateReferences)
        val resourceDirectory = linkedMapOf<String, ByteArray>()
        units.values.forEach { unit -> unit.resourceBytes.forEach { (path, bytes) ->
            val previous = resourceDirectory.putIfAbsent(path, bytes)
            require(previous == null || previous.contentEquals(bytes)) { "Conflicting library resource $path" }
        } }
        preflight()
        state = LibraryImportState.BOUND
        } finally { ambient.restore() }
    }

    /** Publishes bound declarations and provider registrations. A failed publication restores the earlier compiler state. */
    fun commit() = guarded {
        top.mcfpp.Project.invalidateTemplateInheritanceHeaders()
        check(state == LibraryImportState.BOUND) { "bindAll must succeed before commit" }
        val changes = preflight()
        val extensions = units.values.flatMap { it.extensions() }
        extensions.forEachIndexed { index, function ->
            val existing = requireNotNull(function.owner).scope.getFunctionCandidates(function.identifier)
            if (existing.any { ParameterMatcher.sameSignature(it, function) } || extensions.take(index).any {
                it.owner === function.owner && it.identifier == function.identifier && ParameterMatcher.sameSignature(it, function)
            }) problem("LIB_DECL_CONFLICT", "Extension conflicts with a receiver member: ${function.identifier}")
        }
        val registries = changes.keys.associateWith(::publicationRegistry)
        val oldNamespaces = changes.keys.associateWith { registries.getValue(it)[it] }
        val oldMaps = changes.keys.mapNotNull { name -> oldNamespaces[name]?.let { name to NamespaceContents(it) } }.toMap()
        val compilerJournal = top.mcfpp.mni.expand.CompilerStateJournal.checkpoint(ArrayList<Any>().apply {
            addAll(ownedFunctions())
            units.values.forEach { unit -> addAll(unit.ownedTemplates()) }
        })
        try {
            changes.forEach { (name, contents) ->
                val registry = registries.getValue(name)
                val namespace = registry[name] ?: namespaces.getValue(name)
                contents.install(namespace)
                registry[name] = namespace
                published[name] = namespace
            }
            units.values.forEach { unit -> unit.enums().forEach { enumeration ->
                previousScoreboards[enumeration.namespaceID] = GlobalScope.scoreboards[enumeration.namespaceID]
                GlobalScope.scoreboards[enumeration.namespaceID] = enumeration.sbObject
            } }
            helperLease = nativeDirectory?.installReferenceHelpersOwned()
            extensions.forEach { requireNotNull(it.owner).scope.addFunction(it, false) }
            units.values.flatMap { it.anonymousParents() }.forEach { template ->
                if (top.mcfpp.Project.journalState().anonymous.none { it === template }) {
                    top.mcfpp.Project.registerAnonymousTemplate(template)
                    registeredAnonymous.add(template)
                }
            }
            units.values.forEach(ImportedUnit::applyPendingAnnotations)
            state = LibraryImportState.COMMITTED
            publicationBefore.putAll(oldMaps)
            publicationRegistries.putAll(registries)
        } catch (failure: Exception) {
            helperLease?.close(); helperLease = null
            compilerJournal.restore()
            changes.keys.forEach { name ->
                val previous = oldNamespaces[name]
                val registry = registries.getValue(name)
                if (previous == null) registry.remove(name)
                else { oldMaps.getValue(name).install(previous); registry[name] = previous }
            }
            restoreScoreboards()
            published.clear()
            registeredAnonymous.clear()
            throw failure
        }
    }

    /** Returns bound functions, including private functions needed to generate command output. */
    fun functions(): List<Function> {
        check(state == LibraryImportState.BOUND || state == LibraryImportState.COMMITTED) { "Library functions are not bound" }
        return ownedFunctions()
    }
    /** Returns bound functions whose bodies are compiled. */
    fun compiledFunctions(): List<Function> = functions().filter { it.bodyCompiled }

    /** Bundled signatures already carry their validated declaration/type references. Do not
     * resolve every nominal parameter's member layout at each source completion barrier.
     * Dependent signatures still resolve in their invocation's readonly/receiver context. */
    internal fun preparedBuiltinSignatures(): Set<Function> =
        java.util.Collections.newSetFromMap(IdentityHashMap<Function, Boolean>()).apply {
            units.values.forEach { unit -> if (unit.usesPreparedBuiltin()) addAll(unit.compounds.boundFunctionHeads()) }
        }

    private fun ownedFunctions(): List<Function> {
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        val result = arrayListOf<Function>()
        fun include(function: Function) {
            if (!seen.add(function)) return
            result.add(function)
            function.compiledFunctions.values.forEach(::include)
        }
        (units.values.flatMap { it.heads.values.map { head -> head.function } + it.compounds.functions() + it.constantFunctions() } +
            namespaces.values.flatMap { it.scope.functions.values.flatten() } +
            (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values).flatMap { it.scope.functions.values.flatten() }
                .filter { it.bodyHandle?.repository === repository }).forEach(::include)
        return result
    }

    private class NamespaceContents(namespace: Namespace) {
        val functions = HashMap(namespace.scope.functions.mapValues { ArrayList(it.value) })
        val templates = HashMap(namespace.scope.template)
        val objects = ArrayList(namespace.scope.objects)
        val enums = ArrayList(namespace.scope.enums)
        val aliases = HashMap(namespace.scope.journalTypeAliases)
        val variables = HashMap(namespace.scope.vars)
        val annotations = HashMap(namespace.scope.annotations)
        fun install(namespace: Namespace) {
            namespace.scope.functions = functions
            namespace.scope.template = templates
            namespace.scope.objects = objects
            namespace.scope.enums = enums
            namespace.scope.journalTypeAliases = aliases
            namespace.scope.vars.clear(); namespace.scope.vars.putAll(variables)
            namespace.scope.annotations = annotations
        }
    }

    private fun publicationRegistry(name: String): MutableMap<String, Namespace> =
        if (name in GlobalScope.stdNamespaces || name in top.mcfpp.Project.config.stdNamespace) GlobalScope.stdNamespaces else GlobalScope.libNamespaces

    private fun preflight(): Map<String, NamespaceContents> {
        return namespaces.mapValues { (name, incomingNamespace) ->
            val scopes = listOfNotNull(GlobalScope.localNamespaces[name], GlobalScope.libNamespaces[name], GlobalScope.stdNamespaces[name])
            val incoming = incomingNamespace.scope.functions.flatMap { (key, functions) -> functions.filter { !it.actualCallBody }.map { key to it } }
            val previousByName = hashMapOf<String, MutableList<Function>>()
            incoming.forEach { (key, function) ->
                if (scopes.any { it.scope.getFunctionCandidates(key).any { previous -> ParameterMatcher.sameSignature(previous, function) } } ||
                    previousByName[key].orEmpty().any { ParameterMatcher.sameSignature(it, function) })
                    problem("LIB_DECL_CONFLICT", "Function conflicts in namespace $name: $key")
                previousByName.getOrPut(key) { arrayListOf() }.add(function)
            }
            val contents = NamespaceContents(publicationRegistry(name)[name] ?: Namespace(name))
            incoming.forEach { (key, function) -> contents.functions.getOrPut(key) { arrayListOf() }.add(function) }
            incomingNamespace.scope.template.forEach { (key, template) ->
                if (scopes.any { it.scope.hasTemplate(key) || it.scope.hasEnum(key) || it.scope.hasObject(key) || key in it.scope.journalTypeAliases })
                    problem("LIB_DECL_CONFLICT", "Template conflicts in namespace $name: $key")
                contents.templates[key] = template
            }
            incomingNamespace.scope.objects.forEach { value ->
                if (scopes.any { it.scope.hasObject(value.identifier) || it.scope.containType(value.identifier) })
                    problem("LIB_DECL_CONFLICT", "Object conflicts in namespace $name: ${value.identifier}")
                contents.objects.add(value)
            }
            incomingNamespace.scope.enums.forEach { value ->
                if (scopes.any { it.scope.containType(value.identifier) || it.scope.hasObject(value.identifier) })
                    problem("LIB_DECL_CONFLICT", "Enum conflicts in namespace $name: ${value.identifier}")
                contents.enums.add(value)
            }
            incomingNamespace.scope.journalTypeAliases.forEach { (key, value) ->
                if (scopes.any { it.scope.containType(key) || it.scope.hasObject(key) })
                    problem("LIB_DECL_CONFLICT", "Alias conflicts in namespace $name: $key")
                contents.aliases[key] = value
            }
            incomingNamespace.scope.vars.forEach { (key, value) ->
                if (scopes.any { it.scope.vars.containsKey(key) }) problem("LIB_DECL_CONFLICT", "Global variable conflicts in namespace $name: $key")
                contents.variables[key] = value
            }
            incomingNamespace.scope.annotations.forEach { (key, value) ->
                if (scopes.any { it.scope.annotations[key]?.let { previous -> previous !== value } == true })
                    problem("LIB_DECL_CONFLICT", "Annotation provider alias conflicts in namespace $name: $key")
                contents.annotations[key] = value
            }
            contents
        }
    }

    private fun resolve(source: ImportedUnit, reference: DeclarationReference): Pair<ImportedUnit, DeclarationId> = when (reference) {
        is DeclarationReference.Local -> source to reference.target
        is DeclarationReference.Dependency -> {
            val dependency = source.model.manifest.dependencies.getOrNull((reference.dependencyIndex - 1).toInt())
                ?: problem("LIB_DEPENDENCY", "Dependency reference is out of bounds")
            val unit = units[LibraryIdentity(dependency.library, dependency.classifier)]
                ?: problem("LIB_DEPENDENCY", "Dependency is unavailable")
            val key = TypeTableCodec.declarationKeyBytes(reference.key).toList()
            val id = unit.publicKeys[key] ?: problem("LIB_SYMBOL_UNRESOLVED", "Dependency declaration is unavailable: ${reference.key.namespace}:${reference.key.name}")
            unit to id
        }
    }

    private inner class ImportedUnit(val identity: LibraryIdentity, val model: LibraryModel, val resourceBytes: Map<String, ByteArray>) : StructuredSessionBindings, StatementProjectionBindings, StructuredFunctionSignatures {
        // Protocol tables are immutable. Build these directories once per import, rather than
        // scanning every declaration for each source environment and every runtime for each head.
        // Keep declaration IDs and table order: overload selection and private names rely on both.
        private val declarationsByNamespace = model.importPlan?.namespaces?.mapValues { (_, ids) ->
            ids.map { IndexedValue(it - 1, model.declarations.declarations[it - 1]) }
        } ?: model.declarations.declarations.withIndex().groupBy {
            model.declarations.environment(it.value.common.environment).namespace
        }
        private val namespaceNames = model.declarations.declarations.filterIsInstance<NamespaceDeclaration>()
            .mapTo(hashSetOf()) { it.common.name }
        private val runtimeByDeclaration = model.runtime.filterIsInstance<RuntimeRecord.FunctionBinding>()
            .groupBy { it.declaration }
        private val propertiesByBacking = linkedMapOf<DeclarationId, PropertyDeclaration>().apply {
            model.declarations.declarations.filterIsInstance<PropertyDeclaration>().forEach { property ->
                (property.backing as? DeclarationReference.Local)?.target?.let { putIfAbsent(it, property) }
            }
        }
        val heads = linkedMapOf<DeclarationId, RestoredFunctionHead>()
        private val parameterViews = linkedMapOf<DeclarationId, MCFPPGenericParamType>()
        private val nativeParameterViews = linkedMapOf<Pair<DeclarationId, Long>, MCFPPGenericParamType>()
        private val dependentTypes = linkedMapOf<TypeId, LibraryType.Dependent>()
        private val openSyntaxTypes = IdentityHashMap<TypeSyntax, MCFPPType>()
        private val lambdaNodes = model.syntax.nodes.filterIsInstance<Expression.Lambda>().associateBy { it.declaration }
        private val frozenBodies = model.declarations.declarations.mapIndexedNotNull { index, record ->
            if (record is ValueDeclaration && record.defaultBody == null && record.frozen != null)
                DeclarationId(index + 1L) to BodyId(model.syntax.bodies.size + index + 1L) else null
        }.toMap()
        private val constantFunctions = arrayListOf<Function>()
        private lateinit var restoredAnnotations: RestoredAnnotations
        private val lazyPlan = model.importPlan?.takeIf { preparedBuiltinMode && model.manifest.dependencies.isEmpty() }
        private val deferredCompounds = lazyPlan?.compounds?.values?.filter { it.deferred }?.map { it.declaration }?.toSet().orEmpty()
        fun usesPreparedBuiltin(): Boolean = lazyPlan != null
        private var annotationsReady = false
        private val annotationPhases = IdentityHashMap<Annotation, AnnotationPhase>()
        private lateinit var annotationCloner: SessionAnnotationCloner
        val publicKeys = model.declarations.declarations.mapIndexedNotNull { index, declaration ->
            declaration.common.key?.takeIf { declaration.common.access != DeclarationAccess.PRIVATE }?.let {
                (model.importPlan?.publicKeys?.get(index + 1) ?: TypeTableCodec.declarationKeyBytes(it).toList()) to DeclarationId(index + 1L)
            }
        }.toMap()
        private val files = linkedMapOf<EnvironmentId, MCFPPFile>()
        private val environmentScopes = linkedMapOf<EnvironmentId, NamespaceScope>()
        private val frames = IdentityHashMap<Function, MutableMap<DeclarationId, Var<*>>>()
        private val parameters = linkedMapOf<DeclarationId, FunctionParam>()
        private val globals = linkedMapOf<DeclarationId, Var<*>>()
        private val structuralIntersections = linkedMapOf<List<TypeId>, UnionDataTemplate>()
        private val unresolvedTypeClosure = hashMapOf<TypeRef, Boolean>()
        private val unresolvedConstantClosure = hashMapOf<ConstantRef, Boolean>()
        private fun unresolved(reference: TypeRef): Boolean = unresolvedTypeClosure.getOrPut(reference) { when (val type = model.tables.type(reference)) {
            is LibraryType.UnresolvedNominal -> true
            is LibraryType.Applied -> unresolved(type.constructor) || type.arguments.any(::unresolved)
            is LibraryType.Specialized -> unresolved(type.constructor) || type.arguments.any(::unresolved)
            is LibraryType.Union -> type.alternatives.any(::unresolved)
            is LibraryType.Intersection -> type.components.any(::unresolved)
            is LibraryType.Function -> type.parameters.any(::unresolved) || unresolved(type.result)
            is LibraryType.NbtRef -> unresolved(type.value)
            else -> false
        } }
        private fun unresolved(reference: ConstantRef): Boolean = unresolvedConstantClosure.getOrPut(reference) { when (val value = model.tables.constant(reference)) {
            is ConstantValue.TypeValue -> unresolved(value.value)
            is ConstantValue.Typed -> unresolved(value.type) || unresolved(value.value)
            is ConstantValue.Sequence -> value.elements.any(::unresolved)
            is ConstantValue.Record -> value.fields.any { unresolved(it.value) }
            is ConstantValue.FunctionValue -> unresolved(value.type) || value.captures.any { unresolved(it.value) }
            else -> false
        } }
        private var descriptorVersion = -1L
        private var descriptorCache: Map<TypeId, MCFPPType>? = null
        private fun descriptors(): Map<TypeId, MCFPPType> {
            descriptorCache?.takeIf { descriptorVersion == typeDirectoryVersion }?.let { return it }
            val result = model.tables.types.indices.asSequence().map { TypeRef(it + 1L) }
                .filterNot(::unresolved).associate { reference -> types.type(reference).let { it.typeId to it } }
            // This directory is session-owned. Keep the concrete map supported by the raw
            // declaration journal; RestoredDefault retains and checkpoints this same identity.
            descriptorCache = result
            descriptorVersion = typeDirectoryVersion
            return descriptorCache!!
        }
        private val types: CompilerTypeTableRestore = CompilerTypeTableRestore(model.tables, object : CompilerTypeBindings {
            override fun restoreDeclaration(reference: DeclarationReference): MCFPPType {
                val (unit, id) = resolve(this@ImportedUnit, reference)
                return rememberType(unit.compounds.type(id))
            }
            override fun intersection(components: List<MCFPPType>, declaration: DeclarationId?): MCFPPType =
                declaration?.let(compounds::type) ?: structuralIntersections.getOrPut(components.map { it.typeId }) {
                    UnionDataTemplate.deferred(components.map { it.typeId }, repository::requireOpen) {
                        components.map { it as? MCFPPDataTemplateType ?: error("Intersection component is not a data template") }
                    }
                }.getType()
            override fun restoreParameter(value: LibraryType.Parameter): MCFPPType {
                val (unit, owner) = resolve(this@ImportedUnit, value.owner)
                if (value.role == ParameterRole.NATIVE_TYPE) return unit.nativeParameter(owner, value.index)
                val record = unit.model.declarations.declaration(owner)
                val parameters = when (record) { is FunctionDeclaration -> if (value.role == ParameterRole.LAMBDA_TYPE) record.normal else record.readonly; is CompoundDeclaration -> record.readonly; else -> error("Parameter owner is not generic") }
                return unit.parameterViews.getValue(parameters[value.index.toInt()])
            }
            override fun restoreDependent(value: LibraryType.Dependent): MCFPPType {
                val reference = TypeRef(model.tables.types.indexOf(value) + 1L)
                return UnresolvedType("<retained-library-type>", identities.localType(identity, reference)).also { dependentTypes[it.typeId] = value }
            }
            override fun specialize(constructor: MCFPPType, arguments: List<CompilerValue>): MCFPPType = rememberType(specializeType(constructor, arguments))
            override fun apply(constructor: MCFPPType, arguments: List<MCFPPType>): MCFPPType =
                (constructor as MCFPPNotCompiledGenericType).compile(*arguments.toTypedArray())
            override fun functionConstant(value: ConstantValue.FunctionValue, restore: (ConstantRef) -> CompilerValue): CompilerValue = restoreFunctionConstant(value, restore)
        })
        val compounds: CompoundImporter = createCompounds()
        // Construction only creates the D directory. Callbacks run in bindAll after this assignment.
        private fun createCompounds(): CompoundImporter = CompoundImporter(model, object : ImportCompoundBindings {
            override fun declaration(reference: DeclarationId) = model.declarations.declaration(reference)
            override fun shell(reference: DeclarationId) = compounds.shell(reference)
            override fun reference(reference: DeclarationReference): Any {
                val (unit, id) = resolve(this@ImportedUnit, reference)
                return unit.boundValue(id)
            }
            override fun type(reference: TypeRef) = types.type(reference)
            override fun constant(reference: ConstantRef) = restoredConstant(reference)
            override fun environment(reference: EnvironmentId) = files.getValue(reference)
            override fun annotation(reference: AnnotationId): Annotation = restoredAnnotations.annotation(reference)
            override fun template(owner: DeclarationId): top.mcfpp.antlr.mcfppParser.TemplateBodyContext =
                projectTemplate(owner, null).context as top.mcfpp.antlr.mcfppParser.TemplateBodyContext
            override fun bindGeneric(shell: CompoundShell) = bindGenericPrototype(shell)
            override fun prepareParents(shell: CompoundShell) {
                val record = shell.declaration as? CompoundDeclaration ?: return
                if (shell.id.value.toInt() in deferredCompounds) {
                    // The build plan admits only closed nominal parents here. Publish the
                    // stable inheritance header without asking a parent to build its members.
                    val template = shell.value as DataTemplate
                    template.parent.clear(); template.scope.parent.clear()
                    record.parents.forEach { parent ->
                        val type = types.type(requireNotNull(parent.type)) as MCFPPDataTemplateType
                        template.extends(type.rawTemplateDeclaration())
                    }
                    if (record.parents.isEmpty()) template.extends(DataTemplate.baseDataTemplate)
                    template.scope.parent.add(files.getValue(record.common.environment).field)
                    return
                }
                if (record.parents.isEmpty()) return
                // Raw generic expression parents are resolved with each materialization's readonly facts.
                if (record.readonly.isNotEmpty() && record.parents.any { it.type == null }) return
                val template = shell.value as DataTemplate
                val identities = record.parents.mapNotNull { it.type?.let(types::identity) }
                template.restoreStructuredParents(identities, repository::requireOpen, resolve = {
                    record.parents.map { parent ->
                        val type = parent.type?.let(types::type)
                            ?: resolveTypeNode(model.syntax.node(requireNotNull(parent.expression)) as TypeSyntax, null, emptyMap())
                        type as? MCFPPDataTemplateType ?: error("Retained parent is not a data template")
                    }
                }, onResolved = { refreshNominalViews(template) })
            }
            override fun expression(body: BodyId): top.mcfpp.antlr.mcfppParser.ExpressionContext {
                val projection = CurrentSyntaxProjection(this@ImportedUnit, this@ImportedUnit)
                val context = projection.expression(this@ImportedUnit.body(body) as Expression)
                repository.registerProjection(repository.handle(library, body), projection, context)
                return context
            }
            override fun defaultValue(body: BodyId): top.mcfpp.antlr.mcfppParser.ValueContext = projectDefault(body)
            override fun allocated(shell: CompoundShell) { shell.value?.let { bindIdentity(shell.id, it) } }
            override fun bound(shell: CompoundShell) {
                shell.value?.let { applyMetadata(model.declarations.declaration(shell.id), it) }
                shell.value?.let { bindIdentity(shell.id, it) }
                if (shell.declaration is CompoundDeclaration && shell.value is DataTemplate) {
                    val target = shell.value as DataTemplate
                    shell.declaration.inherited.forEach { effect -> effect.property?.let { propertyId ->
                        val property = model.declarations.declaration(propertyId) as PropertyDeclaration
                        fun reference(accessor: LibraryAccessor): DeclarationReference? = when (accessor) {
                            is LibraryAccessor.Function -> accessor.target; is LibraryAccessor.Native -> accessor.target; else -> null
                        }
                        val choices = listOf(reference(property.getter) to effect.getterOwner, reference(property.setter) to effect.setterOwner)
                            .mapNotNull { (reference, owner) -> reference?.let { it to owner } }
                        if (choices.isNotEmpty()) target.restoreInheritedAccessorBinding(model.declarations.declaration(effect.field).common.name) { source ->
                            repository.requireOpen()
                            val matches = choices.filter { (reference, _) ->
                                val (unit, id) = resolve(this@ImportedUnit, reference)
                                unit.callable(id) === source
                            }
                            val (reference, owner) = matches.singleOrNull() ?: error("Inherited accessor has no exact declaration binding")
                            if (owner == null) source else {
                                val type = types.type(owner) as MCFPPDataTemplateType
                                type.tryResolve()
                                val receiver = type.template
                                val (unit, id) = resolve(this@ImportedUnit, reference)
                                val actual = (receiver as? CompiledGenericDataTemplate)?.let { unit.materialized[it]?.members?.get(id) as? Function }
                                    ?: unit.callable(id)
                                require(actual.owner?.inheritanceIdentity == receiver.inheritanceIdentity) { "Inherited accessor is not bound to its typed receiver" }
                                actual
                            }
                        }
                    } }
                }
                (shell.value as? DataTemplateConstructor)?.let { constructor ->
                    constructor.ast = null
                    (shell.declaration as ConstructorDeclaration).body?.let { constructor.bodyHandle = repository.handle(library, it) }
                    val record = shell.declaration as ConstructorDeclaration
                    record.normal.forEach { parameterId ->
                        val parameterShell = compounds.shell(parameterId)
                        val parameter = parameterShell.value as FunctionParam
                        parameters[parameterId] = parameter
                        parameter.defaultBodyHandle = defaultHandle(parameterId)
                        parameterShell.isBound = true
                        bindIdentity(parameterId, parameter)
                    }
                }
                if (annotationsReady) shell.value?.takeUnless { (it as? DataTemplate)?.hasDeferredLibraryMembers == true }
                    ?.let { attachAnnotations(shell.id, it) }
            }
        })
        private lateinit var library: SessionLibraryId
        lateinit var nativeUnit: NativeLibraryUnit
            private set
        private val localSites = model.syntax.nodes.filterIsInstance<Statement.Local>().associateBy { it.declaration }
        private val privatePrefix = java.security.MessageDigest.getInstance("SHA-256").digest(LibraryBinaryWriter().apply {
            TypeTableCodec.writePackage(this, identity.library); string(identity.classifier)
        }.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        private val materialized = IdentityHashMap<CompiledGenericDataTemplate, MaterializedBindings>()
        private val annotationIds = IdentityHashMap<Annotation, AnnotationId>()
        private val pendingAnnotations = arrayListOf<() -> Unit>()
        private val functionConstantCache = IdentityHashMap<ConstantValue.FunctionValue, CompilerValue>()

        fun checkpointForReuse(): () -> Unit {
            val snapshot = ReusableState()
            snapshot.map(heads); snapshot.map(parameterViews); snapshot.map(nativeParameterViews)
            snapshot.map(dependentTypes); snapshot.map(openSyntaxTypes)
            snapshot.list(constantFunctions); snapshot.map(annotationPhases)
            snapshot.map(files); snapshot.map(environmentScopes)
            snapshot.map(frames); frames.values.forEach(snapshot::map)
            snapshot.map(parameters); snapshot.map(globals); snapshot.map(structuralIntersections)
            snapshot.map(unresolvedTypeClosure); snapshot.map(unresolvedConstantClosure)
            snapshot.map(materialized); snapshot.map(annotationIds)
            snapshot.list(pendingAnnotations); snapshot.map(functionConstantCache)
            snapshot.add(types.checkpointForReuse())
            snapshot.add(compounds.checkpointForReuse())
            if (::annotationCloner.isInitialized) snapshot.add(annotationCloner.checkpointForReuse())
            if (::restoredAnnotations.isInitialized) snapshot.add(restoredAnnotations.checkpointForReuse())
            val savedDescriptorVersion = descriptorVersion
            val savedDescriptorCache = descriptorCache
            return {
                snapshot.restore()
                descriptorVersion = savedDescriptorVersion
                descriptorCache = savedDescriptorCache
            }
        }

        fun retainedRequestEntries(): Int = frames.size + materialized.size + constantFunctions.size +
            functionConstantCache.size + openSyntaxTypes.size + structuralIntersections.size + heads.size +
            (if (::restoredAnnotations.isInitialized) restoredAnnotations.loadedCount() else 0)

        fun retainsBody(reference: DeclarationReference): Boolean {
            val (unit, id) = resolve(this, reference)
            val declaration = unit.model.declarations.declaration(id)
            return declaration is FunctionDeclaration && declaration.body != null
        }
        fun constantFunctions(): List<Function> = constantFunctions.toList()
        fun ownedTemplates(): List<DataTemplate> = compounds.boundValues().values.filterIsInstance<DataTemplate>() + materialized.keys + structuralIntersections.values
        fun anonymousParents(): List<DataTemplate> {
            val values = compounds.boundValues()
            return model.declarations.declarations.mapIndexedNotNull { index, record ->
                if (record is CompoundDeclaration && (record.category == CompoundCategory.INTERSECTION || record.common.key == null))
                    values[DeclarationId(index + 1L)] as? DataTemplate else null
            } + structuralIntersections.values
        }
        private fun boundValue(id: DeclarationId): Any {
            val record = model.declarations.declaration(id)
            if (record !is CompoundDeclaration && record !is EnumDeclaration && record !is NamespaceDeclaration)
                compounds.owner(id)?.scope?.ensureMembers()
            return compounds.value(id)
        }
        fun callable(id: DeclarationId): Function = heads[id]?.function ?: boundValue(id) as Function
        fun extensions(): List<Function> = heads.values.map { it.function }.filterIsInstance<ExtensionFunction>()
        fun ownsFile(file: MCFPPFile) = files.values.any { it === file }
        fun macroDeclarations(): List<Pair<DeclarationId, MacroDeclaration>> = model.declarations.declarations.mapIndexedNotNull { index, record ->
            if (record is MacroDeclaration && record.common.access == DeclarationAccess.PUBLIC && record.common.key != null) DeclarationId(index + 1L) to record else null
        }
        fun macroReferences(namespace: String?, name: String, file: MCFPPFile): List<Pair<ImportedUnit, DeclarationId>> {
            val environment = model.declarations.environment(files.entries.single { it.value === file }.key)
            val allowed = (model.manifest.dependencies.map { LibraryIdentity(it.library, it.classifier) } + identity).toSet()
            return units.values.filter { it.identity in allowed }.flatMap { unit -> unit.macroDeclarations().filter { (_, record) ->
                val ownerNamespace = unit.model.declarations.environment(record.common.environment).namespace
                val own = unit === this && ownerNamespace == environment.namespace && record.binding.sourceName == name && (namespace == null || namespace == ownerNamespace)
                val imported = environment.imports.any { item -> item.targetNamespace == ownerNamespace &&
                    (item.resolvedPackages.isEmpty() || unit.identity.library in item.resolvedPackages) &&
                    (if (item.member != null) record.binding.sourceName == item.member && name == (item.alias ?: item.member) && (namespace == null || namespace == item.targetNamespace)
                     else record.binding.sourceName == name && (namespace == null || namespace == (item.alias ?: item.targetNamespace))) }
                own || imported
            }.map { (id, _) -> unit to id } }
        }
        fun phase(value: Annotation): AnnotationPhase? {
            val phase = annotationPhases[value] ?: return null
            if (phase == AnnotationPhase.PENDING && compounds.boundValues().values.filterIsInstance<DataTemplate>().any { template ->
                template !is GenericDataTemplate && template.hasCompletedDeclaration && template.annotations.any { it === value } &&
                    template.pendingDeclarationAnnotations.none { it === value }
            }) return AnnotationPhase.COMPLETED
            return phase
        }
        private fun completeAnnotation(annotation: Annotation) {
            val visited = java.util.Collections.newSetFromMap(IdentityHashMap<Annotation, Boolean>())
            fun visit(value: Annotation) {
                if (visited.add(value)) { annotationPhases[value] = AnnotationPhase.COMPLETED; value.parents.forEach(::visit) }
            }
            visit(annotation)
        }

        fun restoreAnnotations() {
            val bindings = object : AnnotationRestoreBindings {
                override fun provider(reference: DeclarationReference): AnnotationProviderBinding {
                    val (unit, id) = resolve(this@ImportedUnit, reference)
                    return (unit.model.declarations.declaration(id) as AnnotationProviderDeclaration).binding
                }
                override fun constant(reference: ConstantRef) = types.constant(reference)
                override fun type(reference: TypeRef) = types.type(reference)
                override fun declaration(reference: DeclarationReference): Any {
                    val (unit, id) = resolve(this@ImportedUnit, reference)
                    return unit.boundValue(id)
                }
            }
            restoredAnnotations = lazyPlan?.let { plan ->
                requireNotNull(nativeDirectory).restoreAnnotationsLazily(model.annotations, model.stateCells, bindings, plan.cellHints)
            } ?: (nativeDirectory?.restoreAnnotations(model.annotations, model.stateCells, bindings)
                ?: codecs.restore(model.annotations, model.stateCells, bindings))
            restoredAnnotations.onLoaded = { id, value ->
                annotationIds[value] = id; annotationPhases[value] = model.annotations[(id.value - 1).toInt()].phase
            }
            if (lazyPlan == null) model.annotations.forEachIndexed { index, record ->
                val value = restoredAnnotations.annotation(AnnotationId(index + 1L))
                annotationIds[value] = AnnotationId(index + 1L); annotationPhases[value] = record.phase
            }
            annotationCloner = SessionAnnotationCloner(codecs) { value -> phase(value) ?: restoredAnnotations.phase(value) }
            annotationsReady = true
            compounds.boundValues().forEach { (id, value) ->
                if ((value as? DataTemplate)?.hasDeferredLibraryMembers != true) attachAnnotations(id, value)
            }
            materialized.values.toList().forEach { it.attachAnnotations() }
        }

        private fun attachAnnotations(id: DeclarationId, value: Any) {
                val record = model.declarations.declaration(id)
                val annotations = record.common.annotations.map(restoredAnnotations::annotation)
                when (value) {
                    is GenericDataTemplate -> {
                        value.annotations.clear()
                        value.pendingDeclarationAnnotations.clear()
                        value.pendingDeclarationAnnotations.addAll(annotations.filter { annotationPhases[it] == AnnotationPhase.PENDING })
                        value.recordDeclarationAnnotations(annotations)
                    }
                    is DataTemplate -> {
                        value.annotations.clear(); value.annotations.addAll(annotations)
                        value.pendingDeclarationAnnotations.addAll(annotations.filter { annotationPhases[it] == AnnotationPhase.PENDING })
                    }
                    is Function -> { value.annotations.clear(); value.annotations.addAll(annotations) }
                    is Var<*> -> { value.annotations.clear(); value.annotations.addAll(annotations) }
                    is FunctionParam -> value.function.scope.getVar(value.identifier)?.let { it.annotations.clear(); it.annotations.addAll(annotations) }
                }
                if (value !is DataTemplate) annotations.filter { annotationPhases[it] == AnnotationPhase.PENDING }.forEach { annotation ->
                    pendingAnnotations += {
                        when (value) {
                            is Function -> annotation.on(value)
                            is Var<*> -> annotation.on(value)
                            is FunctionParam -> value.function.scope.getVar(value.identifier)?.let(annotation::on)
                        }
                        completeAnnotation(annotation)
                    }
                }
                // Restoring a closed value already seeds its callable-target evidence. Ordinary
                // field declarations have no payload to bind: ensure() would eagerly allocate
                // storage for thousands of unused standard-library fields during import.
                if (value is Var<*> && value.storageBinding?.data?.callableTargets?.isNotEmpty() == true)
                    StorageAccess.bindFunctionTargets(value, functionTargets)
        }

        fun applyPendingAnnotations() {
            while (pendingAnnotations.isNotEmpty()) {
                val batch = pendingAnnotations.toList(); pendingAnnotations.clear()
                batch.forEach { it() }
            }
            compounds.boundValues().values.filterIsInstance<DataTemplate>().forEach { template ->
                if (!template.hasDeferredLibraryMembers) top.mcfpp.Project.registerTemplateContract(template)
            }
        }

        override fun functionTargets(reference: ConstantRef): FunctionTargetResolver = functionTargets
        private fun sameDeclarationValue(actual: Any, value: Any): Boolean = actual === value ||
            value is top.mcfpp.io.info.MacroFunctionInfo && actual is top.mcfpp.io.info.MacroFunctionInfo && actual == value ||
            value is top.mcfpp.backend.ReferenceHelperRecipe && actual is top.mcfpp.backend.ReferenceHelperRecipe && actual == value
        fun ownsDeclaration(value: Any): Boolean = compounds.boundValues().values.any { sameDeclarationValue(it, value) } ||
            materialized.values.any { context -> context.members.values.any { it === value } } ||
            heads.values.any { head -> head.function.compiledFunctions.values.any { it === value } }
        fun ownedReference(value: Any): DeclarationKey? {
            fun key(id: DeclarationId) = model.declarations.declaration(id).common.let { if (it.access == DeclarationAccess.PRIVATE) null else it.key }
            compounds.boundValues().forEach { (id, actual) ->
                if (sameDeclarationValue(actual, value)) return key(id)
            }
            materialized.values.forEach { context -> context.members.forEach { (id, actual) -> if (actual === value) return key(id) } }
            heads.forEach { (id, head) -> if (head.function.compiledFunctions.values.any { it === value }) return key(id) }
            return null
        }
        fun sourceSignature(function: Function): FunctionSourceSignature? {
            val key = ownedReference(function) ?: return null
            val id = publicKeys[TypeTableCodec.declarationKeyBytes(key).toList()] ?: return null
            if (model.declarations.declaration(id) !is FunctionDeclaration) return null
            val scope = model.syntax.scopes.indexOfFirst { it.owner == id }.let { ScopeId(if (it >= 0) it + 1L else 1L) }
            val root = TemplateBody(scope, listOf(TemplateMember.Declaration(scope, id)))
            val bodyId = BodyId(model.syntax.bodies.size + model.declarations.declarations.size + 1L)
            val declarations = object : StatementProjectionBindings by this {
                override fun body(reference: BodyId): SemanticNode = if (reference == bodyId) root else this@ImportedUnit.body(reference)
                override fun templateDeclaration(declaration: DeclarationId): ProjectionTemplateDeclaration {
                    val header = this@ImportedUnit.templateDeclaration(declaration) as ProjectionTemplateDeclaration.Function
                    return header.copy(body = null, isAbstract = true)
                }
            }
            val projection = CurrentSyntaxProjection(this, declarations)
            val context = projection.template(root)
            val registered = repository.register(this, declarations)
            repository.registerProjection(repository.handle(registered, bodyId), projection, context)
            val part = context.templateMemberDeclaration().single().templateMember().templateFunctionDeclaration().functionDeclarationPart()
            val record = model.declarations.declaration(id) as FunctionDeclaration
            val receiver = record.receiver?.let { reference ->
                projection.type(typeSyntax(reference, record.common.environment, scope)).also {
                    repository.registerProjection(repository.handle(registered, bodyId), projection, it)
                }
            }
            return FunctionSourceSignature(part.functionParams(), part.functionReturnType(), receiver)
        }
        fun typeReference(identity: TypeId): DeclarationKey? = compounds.boundValues().entries.firstOrNull {
            (it.value as? CompoundData)?.getType()?.typeId == identity
        }?.key?.let { model.declarations.declaration(it).common.key }

        private fun displayName(use: NameUse): ProjectedName = when (use) {
            is NameUse.Local -> ProjectedName(name(use.target))
            is NameUse.Lookup -> ProjectedName(use.name, use.namespace)
            is NameUse.Declaration -> {
                val (unit, id) = resolve(this, use.target)
                val record = unit.model.declarations.declaration(id)
                ProjectedName(record.common.name, record.common.key?.namespace.takeIf { record is CompoundDeclaration || record is EnumDeclaration || record is TypeAliasDeclaration })
            }
        }
        private fun templateRoot(owner: DeclarationId): TemplateBody = model.syntax.nodes.filterIsInstance<TemplateBody>()
            .single { model.syntax.scope(it.scope).owner == owner }

        private fun projectTemplate(owner: DeclarationId, target: CompiledGenericDataTemplate?): ProjectedSyntax {
            val root = templateRoot(owner)
            val context = target?.let { materialized.getOrPut(it) { MaterializedBindings(owner, it) } }
            val bindings = context ?: object : StructuredSessionBindings by this, StructuredFunctionSignatures by this {
                // A declaration header can mention its own nominal shell before allocation finishes.
                override fun name(use: NameUse, role: NameRole) = displayName(use)
                override fun type(reference: TypeRef): Any = reference
            }
            val rootId = BodyId(model.syntax.bodies.size + model.declarations.declarations.size + 1L)
            val declarations = object : StatementProjectionBindings by (context ?: this) {
                override fun body(reference: BodyId): SemanticNode = if (reference == rootId) root else this@ImportedUnit.body(reference)
            }
            val projectedLibrary = repository.register(bindings, declarations)
            val projection = CurrentSyntaxProjection(bindings, declarations)
            return repository.registerProjection(repository.handle(projectedLibrary, rootId), projection, projection.template(root))
        }

        private fun bindGenericPrototype(shell: CompoundShell): GenericDataTemplate {
            val record = model.declarations.declaration(shell.id) as CompoundDeclaration
            val prototype = shell.value as GenericDataTemplate
            prototype.declarationFile = files.getValue(record.common.environment)
            prototype.scope.parent.clear(); prototype.scope.parent.add(prototype.declarationFile!!.field)
            prototype.isAbstract = record.common.flags and DeclarationFlags.ABSTRACT != 0L
            prototype.isFinal = record.common.flags and DeclarationFlags.FINAL != 0L
            record.readonly.forEach { id ->
                val value = model.declarations.declaration(id) as ValueDeclaration
                val parameter = DataTemplateParam(value.common.name, value.common.name, value.type?.let(types::type), when (value.variance) {
                    Variance.IN -> DeclarationVariance.IN; Variance.OUT -> DeclarationVariance.OUT; Variance.INVARIANT -> DeclarationVariance.INVARIANT
                })
                prototype.readOnlyParams.add(parameter)
                compounds.install(id, parameter)
            }
            prototype.structuredSession = object : GenericTemplateSession {
                override fun requireOpen() = repository.requireOpen()
                override fun readonlyType(prototype: GenericDataTemplate, index: Int): MCFPPType =
                    types.type(requireNotNull((model.declarations.declaration(record.readonly[index]) as ValueDeclaration).type))
                override fun readonlyType(prototype: GenericDataTemplate, index: Int, converted: List<Var<*>>): MCFPPType {
                    val facts = record.readonly.take(converted.size).zip(converted).associate { (id, value) -> id to closed(value) }
                    return resolveType(requireNotNull((model.declarations.declaration(record.readonly[index]) as ValueDeclaration).type), null, facts)
                }
                override fun completeReadonlyArguments(prototype: GenericDataTemplate, supplied: List<Var<*>>): List<Var<*>> {
                    require(supplied.size <= record.readonly.size) { "Too many readonly template arguments" }
                    val result = arrayListOf<Var<*>>()
                    val facts = linkedMapOf<DeclarationId, ClosedReadonlyBinding>()
                    record.readonly.forEachIndexed { index, id ->
                        val parameter = model.declarations.declaration(id) as ValueDeclaration
                        val expected = resolveType(requireNotNull(parameter.type), null, facts)
                        val value = supplied.getOrNull(index) ?: parameter.defaultBody?.let {
                            val default = evaluate(body(it) as Expression, null, facts)
                            StorageAccess.literal(default.type, requireNotNull(default.constant), types = default.descriptors)
                        } ?: parameter.frozen?.let { reference ->
                            val default = boundConstant(reference, null, facts)
                            StorageAccess.literal(default.type, requireNotNull(default.constant), types = default.descriptors)
                        } ?: error("Readonly template argument '${parameter.common.name}' is required")
                        val converted = value.implicitCast(expected)
                        require(!converted.isError)
                        facts[id] = closed(converted); result.add(converted)
                    }
                    return result
                }
                override fun validateHeader(prototype: GenericDataTemplate) { validateVariance(shell.id) }
                override fun validateInferredTypes(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate?, types: Map<String, MCFPPType>) {
                    validateVariance(shell.id, types)
                }
                override fun parentTypes(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate?): List<MCFPPDataTemplateType> = record.parents.map { parent ->
                    if (target == null && parent.type == null) {
                        val expression = model.syntax.node(requireNotNull(parent.expression)) as TypeSyntax
                        val base = (expression as? TypeSyntax.Apply)?.base ?: expression
                        resolveTypeNode(base, null, emptyMap()) as MCFPPDataTemplateType
                    } else {
                        val context = target?.let { materialized.getOrPut(it) { MaterializedBindings(shell.id, it) } }
                        parent.type?.let { resolveType(it, null, context?.facts().orEmpty()) }
                            ?: resolveTypeNode(model.syntax.node(requireNotNull(parent.expression)) as TypeSyntax, null, context?.facts().orEmpty())
                    } as MCFPPDataTemplateType
                }
                override fun project(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate) = projectTemplate(shell.id, target)
                override fun cloneAnnotations(recorded: List<Annotation>): TemplateAnnotationClone = error("A materialized annotation arena requires its target")
                override fun cloneAnnotations(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate, recorded: List<Annotation>) =
                    materialized.getValue(target).copyAnnotations(recorded)
                override fun bindMembers(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate, projection: ProjectedSyntax) {
                    materialized.getValue(target).bindMembers(projection)
                }
                override val materializesOperators: Boolean get() = true
                override fun bindOperators(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate, projection: ProjectedSyntax) {
                    materialized.getValue(target).bindOperators(projection)
                }
            }
            return prototype
        }
        fun prepareGenericHeaders() {
            model.declarations.declarations.forEachIndexed { index, record -> if (record is CompoundDeclaration && record.readonly.isNotEmpty())
                compounds.bind(DeclarationId(index + 1L)) }
        }

        /** Variance follows bound D/T/N identities; no type spelling is parsed or globally resolved. */
        private fun validateVariance(owner: DeclarationId, inferred: Map<String, MCFPPType>? = null) {
            val record = model.declarations.declaration(owner) as CompoundDeclaration
            val formals = record.readonly.associateWith { model.declarations.declaration(it) as ValueDeclaration }
            if (formals.values.all { it.variance == Variance.INVARIANT }) return
            fun occurrence(id: DeclarationId, position: Int) {
                val formal = formals[id] ?: return
                require(formal.variance == Variance.INVARIANT || formal.variance == Variance.OUT && position == 1 || formal.variance == Variance.IN && position == -1) {
                    "${formal.variance} parameter '${formal.common.name}' occurs in an incompatible member type position"
                }
            }
            lateinit var syntax: (TypeSyntax, Int) -> Unit
            lateinit var expression: (Expression, Int) -> Unit
            lateinit var table: (TypeRef, Int, MutableSet<TypeRef>) -> Unit
            fun reference(reference: DeclarationReference, position: Int) {
                val (unit, id) = resolve(this, reference)
                if (unit === this) occurrence(id, position)
            }
            expression = { node, position -> when (node) {
                is Expression.Name -> when (val use = node.use) { is NameUse.Local -> occurrence(use.target, position); is NameUse.Declaration -> reference(use.target, position); else -> Unit }
                is Expression.TypeValue -> syntax(node.type, position)
                is Expression.Group -> node.value?.let { expression(it, position) }
                is Expression.Unary -> expression(node.operand, position)
                is Expression.Binary -> { expression(node.left, position); expression(node.right, position) }
                is Expression.Cast -> { expression(node.value, position); syntax(node.target, position) }
                is Expression.Call -> { expression(node.callee, position); node.readonly.forEach { expression(it, position) }; node.normal.forEach { expression(it.expression, position) } }
                is Expression.Member -> expression(node.receiver, position)
                is Expression.Index -> { expression(node.receiver, position); node.index?.let { expression(it, position) } }
                is Expression.ListValue -> node.elements.forEach { expression(it, position) }
                is Expression.Compound -> node.entries.forEach { expression(it.value, position) }
                else -> Unit
            }; Unit }
            fun argumentVariance(base: TypeSyntax): List<Variance> {
                val use = (base as? TypeSyntax.Name)?.use
                val ref = when (use) { is NameUse.Declaration -> use.target; is NameUse.Local -> DeclarationReference.Local(use.target); else -> null } ?: return emptyList()
                val (unit, id) = resolve(this, ref)
                return (unit.model.declarations.declaration(id) as? CompoundDeclaration)?.readonly.orEmpty().map {
                    (unit.model.declarations.declaration(it) as ValueDeclaration).variance
                }
            }
            syntax = { node, position -> when (node) {
                is TypeSyntax.Name -> when (val use = node.use) { is NameUse.Local -> occurrence(use.target, position); is NameUse.Declaration -> reference(use.target, position); else -> node.resolved?.let { table(it, position, hashSetOf()) } }
                is TypeSyntax.Apply -> {
                    val variances = argumentVariance(node.base)
                    node.readonly.forEachIndexed { index, arg -> expression(arg, when (variances.getOrNull(index)) { Variance.OUT -> position; Variance.IN -> -position; else -> 0 }) }
                }
                is TypeSyntax.Container -> syntax(node.argument, 0)
                is TypeSyntax.Union -> node.alternatives.forEach { syntax(it, position) }
                is TypeSyntax.Intersection -> node.components.forEach { syntax(it, position) }
                is TypeSyntax.Function -> { node.parameters.forEach { syntax(it, -position) }; syntax(node.result, position) }
                else -> Unit
            }; Unit }
            table = { ref, position, visiting -> if (visiting.add(ref)) { when (val type = model.tables.type(ref)) {
                is LibraryType.Parameter -> if (type.role == ParameterRole.READONLY) {
                    val (unit, declaration) = resolve(this, type.owner)
                    if (unit === this && declaration == owner) occurrence(record.readonly[type.index.toInt()], position)
                }
                is LibraryType.Dependent -> syntax(model.syntax.node(type.syntax) as TypeSyntax, position)
                is LibraryType.Applied -> type.arguments.forEach { table(it, 0, visiting) }
                is LibraryType.NbtRef -> table(type.value, 0, visiting)
                is LibraryType.Union -> type.alternatives.forEach { table(it, position, visiting) }
                is LibraryType.Intersection -> type.components.forEach { table(it, position, visiting) }
                is LibraryType.Function -> { type.parameters.forEach { table(it, -position, visiting) }; table(type.result, position, visiting) }
                else -> Unit
            }; visiting.remove(ref) }; Unit }
            fun fieldPosition(field: ValueDeclaration): Int {
                val property = record.members.map(model.declarations::declaration).filterIsInstance<PropertyDeclaration>().firstOrNull { (it.backing as? DeclarationReference.Local)?.target?.let(model.declarations::declaration) === field }
                return if (field.storage == ValueStorage.CONST || property != null && property.setter == LibraryAccessor.Absent) 1
                    else if (property != null && property.getter == LibraryAccessor.Absent) -1 else 0
            }
            fun actual(type: MCFPPType, position: Int) {
                parameterViews.forEach { (id, view) -> if (type.typeId == view.typeId) occurrence(id, position) }
                if (type is MCFPPFunctionType) {
                    type.parameters.forEach { actual(it, -position) }; actual(type.result, position)
                } else if (type is MCFPPDataTemplateType && type.template is CompiledGenericDataTemplate) {
                    val target = type.template as CompiledGenericDataTemplate
                    target.args.zip(target.originTemplate.readOnlyParams).forEach { (argument, parameter) ->
                        val value = StorageAccess.snapshot(argument)
                        val payload = if (value is CompilerValue.Typed) value.payload else value
                        (payload as? CompilerValue.TypeValue)?.id?.let { id ->
                            (StorageAccess.boundTypes(argument)[id] ?: MCFPPType.resolveTypeId(id))?.let { descriptor -> actual(descriptor,
                                when (parameter.variance) { DeclarationVariance.OUT -> position; DeclarationVariance.IN -> -position; else -> 0 }) }
                        }
                    }
                } else (type as? MCFPPTypeWithGeneric)?.generic?.forEach { actual(it, 0) }
            }
            if (inferred != null) record.members.map(model.declarations::declaration).filterIsInstance<ValueDeclaration>().filter { it.declaredType == null }.forEach { field ->
                inferred[field.common.name]?.let { actual(it, fieldPosition(field)) }
            } else {
                formals.values.filter { it.variance != Variance.INVARIANT }.forEach { require(it.type?.let(types::type) == MCFPPConcreteType.Type) { "Variance requires a type parameter" } }
                record.parents.forEach { parent -> parent.expression?.let { syntax(model.syntax.node(it) as TypeSyntax, 1) } ?: parent.type?.let { table(it, 1, hashSetOf()) } }
                record.members.map(model.declarations::declaration).forEach { member -> when (member) {
                    is ValueDeclaration -> member.declaredType?.let { syntax(model.syntax.node(it) as TypeSyntax, fieldPosition(member)) }
                    is FunctionDeclaration -> { (member.readonly + member.normal).forEach { id -> (model.declarations.declaration(id) as ValueDeclaration).type?.let { table(it, -1, hashSetOf()) } }; member.result?.let { table(it, 1, hashSetOf()) } }
                    else -> Unit
                } }
            }
        }

        private fun closed(value: Var<*>): ClosedReadonlyBinding = ClosedReadonlyBinding(value.type,
            requireNotNull(StorageAccess.snapshot(value)) { "Readonly value has no complete snapshot" }, StorageAccess.boundTypes(value))

        private fun specializeType(constructor: MCFPPType, arguments: List<CompilerValue>): MCFPPType {
            val prototype = (constructor as MCFPPDataTemplateType).template as GenericDataTemplate
            val identity = TypeId.Specialized(prototype.nominalIdentity, arguments)
            return MCFPPGenericDataTemplateType(prototype, arrayListOf(), identity,
                sessionResolver = {
                    repository.requireOpen()
                    val directory = descriptors()
                    val vars = arrayListOf<Var<*>>()
                    arguments.forEachIndexed { index, value ->
                        val type = prototype.structuredSession!!.readonlyType(prototype, index, vars)
                        vars.add(StorageAccess.literal(type, value, types = directory))
                    }
                    prototype.compile(vars)
                }, sessionGuard = repository::requireOpen)
        }

        private fun resolveType(reference: TypeRef, frame: Function?, facts: Map<DeclarationId, ClosedReadonlyBinding>): MCFPPType { return when (val type = model.tables.type(reference)) {
            is LibraryType.Parameter -> {
                val (unit, owner) = resolve(this, type.owner)
                if (type.role == ParameterRole.NATIVE_TYPE) return unit.nativeParameter(owner, type.index)
                val ownerRecord = unit.model.declarations.declaration(owner)
                val ids = when (ownerRecord) { is FunctionDeclaration -> if (type.role == ParameterRole.LAMBDA_TYPE) ownerRecord.normal else ownerRecord.readonly; is CompoundDeclaration -> ownerRecord.readonly; else -> error("Invalid parameter owner") }
                val id = ids[type.index.toInt()]
                val lambdaType = if (type.role == ParameterRole.LAMBDA_TYPE && unit === this && frame != null && frame.bodyHandle?.repository === repository &&
                    model.syntax.bodies.getOrNull((frame.bodyHandle!!.body.value - 1).toInt())?.owner == owner)
                    frame.normalParams[type.index.toInt()].type else null
                val value = if (unit === this) facts[id] else null
                val payload = value?.value.let { if (it is CompilerValue.Typed) it.payload else it }
                val actualId = (payload as? CompilerValue.TypeValue)?.id
                if (lambdaType != null) lambdaType
                else if (actualId != null) requireNotNull(value).descriptors[actualId] ?: MCFPPType.resolveTypeId(actualId) ?: error("Readonly type is unbound")
                else if (unit === this) frame?.scope?.getType(unit.name(id))?.takeUnless { it is MCFPPGenericParamType } ?: unit.parameterViews.getValue(id)
                else unit.parameterViews.getValue(id)
            }
            is LibraryType.Dependent -> resolveTypeNode(model.syntax.node(type.syntax) as TypeSyntax, frame, facts)
            is LibraryType.Applied -> when (val constructor = (model.tables.type(type.constructor) as? LibraryType.Builtin)?.key?.removePrefix("constructor:")) {
                "list" -> MCFPPListType(resolveType(type.arguments.single(), frame, facts))
                "ImmutableList" -> MCFPPImmutableListType(resolveType(type.arguments.single(), frame, facts))
                "dict" -> MCFPPDictType(resolveType(type.arguments.single(), frame, facts))
                "map" -> MCFPPMapType(resolveType(type.arguments.single(), frame, facts))
                "NBTRef" -> MCFPPNBTRefType(resolveType(type.arguments.single(), frame, facts))
                else -> (resolveType(type.constructor, frame, facts) as MCFPPNotCompiledGenericType).compile(*type.arguments.map { resolveType(it, frame, facts) }.toTypedArray())
            }
            is LibraryType.Union -> MCFPPUnionType(*type.alternatives.map { resolveType(it, frame, facts) }.toTypedArray())
            is LibraryType.Function -> MCFPPFunctionType(type.parameters.map { resolveType(it, frame, facts) }, resolveType(type.result, frame, facts), when (type.execution) {
                FunctionExecution.NORMAL -> MCFPPFunctionType.ExecutionCategory.NORMAL
                FunctionExecution.PAUSED -> MCFPPFunctionType.ExecutionCategory.PAUSED
                FunctionExecution.GENERATOR -> MCFPPFunctionType.ExecutionCategory.GENERATOR
            })
            is LibraryType.NbtRef -> MCFPPNBTRefType(resolveType(type.value, frame, facts))
            else -> types.type(reference)
        } }

        private fun resolveTypeNode(node: TypeSyntax, frame: Function?, facts: Map<DeclarationId, ClosedReadonlyBinding>): MCFPPType = when (node) {
            is TypeSyntax.Name -> {
                val id = (node.use as? NameUse.Local)?.target
                if (id != null) {
                    val fact = facts[id] ?: frame?.scope?.getVar(name(id))?.let { value -> StorageAccess.snapshot(value)?.let {
                        ClosedReadonlyBinding(value.type, it, StorageAccess.boundTypes(value))
                    } }
                    val payload = fact?.value.let { if (it is CompilerValue.Typed) it.payload else it }
                    val typeId = (payload as? CompilerValue.TypeValue)?.id
                    if (typeId != null) requireNotNull(fact).descriptors[typeId] ?: MCFPPType.resolveTypeId(typeId) ?: error("Type value has no descriptor")
                    else node.resolved?.let { resolveType(it, frame, facts) } ?: parameterViews.getValue(id)
                } else node.resolved?.takeUnless { model.tables.type(it) is LibraryType.Dependent }?.let { resolveType(it, frame, facts) }
                    ?: (node.use as? NameUse.Declaration)?.target?.let { target -> val (unit, declaration) = resolve(this, target); unit.compounds.type(declaration) }
                    ?: (node.use as? NameUse.Lookup)?.let { LibraryBuiltinTypes.restore(if (it.name == "type") "Type" else it.name) }
                    ?: error("Type name lacks an author binding")
            }
            is TypeSyntax.Apply -> {
                val constructor = resolveTypeNode(node.base, frame, facts)
                val arguments = node.readonly.map { expression -> evaluate(expression, frame, facts) }
                if (arguments.any { argument ->
                    val payload = (argument.constant as? CompilerValue.Typed)?.payload ?: argument.constant
                    val descriptor = (payload as? CompilerValue.TypeValue)?.id?.let { argument.descriptors[it] ?: MCFPPType.resolveTypeId(it) }
                    argument.constant == null || descriptor is MCFPPGenericParamType || descriptor is UnresolvedType
                }) openType(node)
                else if (constructor is MCFPPNotCompiledGenericType) constructor.compile(*arguments.map { argument ->
                    val payload = (argument.constant as? CompilerValue.Typed)?.payload ?: argument.constant
                    val id = (payload as CompilerValue.TypeValue).id
                    argument.descriptors[id] ?: MCFPPType.resolveTypeId(id) ?: error("Type argument is unbound")
                }.toTypedArray()) else {
                    val prototype = (constructor as MCFPPDataTemplateType).template as GenericDataTemplate
                    requireNotNull(prototype.compile(arguments.map { argument -> StorageAccess.literal(argument.type,
                        requireNotNull(argument.constant), types = argument.descriptors) })).getType()
                }
            }
            is TypeSyntax.Container -> when (node.kind) {
                ContainerKind.LIST -> MCFPPListType(resolveTypeNode(node.argument, frame, facts))
                ContainerKind.IMMUTABLE_LIST -> MCFPPImmutableListType(resolveTypeNode(node.argument, frame, facts))
                ContainerKind.MAP -> MCFPPMapType(resolveTypeNode(node.argument, frame, facts))
                ContainerKind.DICT -> MCFPPDictType(resolveTypeNode(node.argument, frame, facts))
            }
            is TypeSyntax.Union -> MCFPPUnionType(*node.alternatives.map { resolveTypeNode(it, frame, facts) }.toTypedArray())
            is TypeSyntax.Intersection -> node.components.map { resolveTypeNode(it, frame, facts) }.let { components ->
                if (components.any { it is MCFPPGenericParamType || it is UnresolvedType }) openType(node)
                else UnionDataTemplate(components.map { (it as MCFPPDataTemplateType).template }).getType()
            }
            is TypeSyntax.Vector -> MCFPPVectorType(node.dimensions.toInt())
            is TypeSyntax.Entity -> MCFPPEntityType(node.limit?.let { reference ->
                val value = types.constant(reference).let { if (it is CompilerValue.Typed) it.payload else it }
                (value as CompilerValue.Integral).value.toInt()
            }, node.names)
            is TypeSyntax.Function -> MCFPPFunctionType(node.parameters.map { resolveTypeNode(it, frame, facts) }, resolveTypeNode(node.result, frame, facts), when (node.execution) {
                FunctionExecution.NORMAL -> MCFPPFunctionType.ExecutionCategory.NORMAL
                FunctionExecution.PAUSED -> MCFPPFunctionType.ExecutionCategory.PAUSED
                FunctionExecution.GENERATOR -> MCFPPFunctionType.ExecutionCategory.GENERATOR
            })
            is TypeSyntax.AnonymousData -> compounds.type(node.declaration)
            is TypeSyntax.Void -> MCFPPPrivateType.Void
            is TypeSyntax.Inferred -> evaluate(node.expression, frame, facts).type
        }
        private fun openType(node: TypeSyntax): MCFPPType = openSyntaxTypes.getOrPut(node) {
            val reference = model.syntax.reference(node)
            val owner = model.syntax.scope(node.scope).owner
            val deferred = LibraryType.Dependent(reference, model.declarations.declaration(owner).common.environment, owner)
            UnresolvedType("<retained-library-type>", identities.localType(identity, TypeRef(model.tables.types.size + reference.value))).also {
                dependentTypes[it.typeId] = deferred
            }
        }

        private fun evaluate(expression: Expression, frame: Function?, facts: Map<DeclarationId, ClosedReadonlyBinding>): DeclarationBinding.Bound {
            val environment = model.declarations.declaration(model.syntax.scope(expression.scope).owner).common.environment
            val file = files.getValue(environment)
            val evaluator = frame ?: Function("__library_type_evaluation", file.namespace.identifier, null).apply { declarationFile = file }
            val provider = factBindings(facts)
            val transientBody = BodyId(model.syntax.bodies.size + model.declarations.declarations.size + 1L)
            val declarations = object : StatementProjectionBindings by this { override fun body(reference: BodyId): SemanticNode = if (reference == transientBody) expression else this@ImportedUnit.body(reference) }
            val registeredProjection = CurrentSyntaxProjection(provider, declarations)
            val registeredContext = registeredProjection.expression(expression)
            val id = repository.register(provider, declarations)
            repository.registerProjection(repository.handle(id, transientBody), registeredProjection, registeredContext)
            return DeclarationBinding(evaluator, emptyMap()).expression(registeredContext)
        }

        private fun factBindings(facts: Map<DeclarationId, ClosedReadonlyBinding>): StructuredSessionBindings = object : StructuredSessionBindings by this {
            override fun name(use: NameUse, role: NameRole): ProjectedName = if (use is NameUse.Local && use.target in facts)
                ProjectedName(this@ImportedUnit.name(use.target), binding = StructuredNameBinding.Frame(use.target, this@ImportedUnit.name(use.target))) else this@ImportedUnit.name(use, role)
            override fun local(declaration: DeclarationId, frame: Function): Var<*>? = facts[declaration]?.let {
                StorageAccess.literal(it.type, it.value, this@ImportedUnit.name(declaration), it.descriptors)
            } ?: this@ImportedUnit.local(declaration, frame)
            override fun boundType(reference: TypeRef, frame: Function) = resolveType(reference, frame, facts)
            override fun dependentType(syntax: TypeSyntax, frame: Function) = resolveTypeNode(syntax, frame, facts)
            override fun closedConstant(reference: ConstantRef) = boundConstant(reference, null, facts)
            override fun closedConstant(reference: ConstantRef, frame: Function) = boundConstant(reference, frame, facts)
        }

        private fun boundConstant(reference: ConstantRef, frame: Function?, facts: Map<DeclarationId, ClosedReadonlyBinding>): DeclarationBinding.Bound {
            val descriptors = linkedMapOf<TypeId, MCFPPType>()
            val active = hashSetOf<ConstantRef>()
            fun restore(reference: ConstantRef): Pair<MCFPPType, CompilerValue> {
                check(active.add(reference)) { "Cyclic constant" }
                val result = when (val value = model.tables.constant(reference)) {
                    ConstantValue.Null -> MCFPPPrivateType.Null to CompilerValue.NullValue
                    is ConstantValue.Integral -> (if (value.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) MCFPPBaseType.Int else MCFPPNBTType.Long) to CompilerValue.Integral(value.value)
                    is ConstantValue.FloatBits -> MCFPPBaseType.Float to CompilerValue.FloatBits(value.bits)
                    is ConstantValue.DoubleBits -> MCFPPNBTType.Double to CompilerValue.DoubleBits(value.bits)
                    is ConstantValue.Bool -> MCFPPBaseType.Bool to CompilerValue.Bool(value.value)
                    is ConstantValue.Text -> MCFPPBaseType.String to CompilerValue.Text(value.value)
                    is ConstantValue.TypeValue -> {
                        val type = resolveType(value.value, frame, facts); descriptors[type.typeId] = type
                        MCFPPConcreteType.Type to CompilerValue.TypeValue(type.typeId)
                    }
                    is ConstantValue.Nbt -> MCFPPNBTType.NBT to CompilerValue.Nbt(value.snbt)
                    is ConstantValue.Sequence -> MCFPPListType(MCFPPBaseType.Any) to CompilerValue.Sequence(value.elements.map { restore(it).second })
                    is ConstantValue.Record -> MCFPPDictType(MCFPPBaseType.Any) to CompilerValue.Record(value.fields.associate { it.name to restore(it.value).second })
                    is ConstantValue.Typed -> resolveType(value.type, frame, facts).let { type ->
                        type to CompilerValue.Typed(type.typeId, restore(value.value).second.let { if (it is CompilerValue.Typed) it.payload else it })
                    }
                    is ConstantValue.FunctionValue -> resolveType(value.type, frame, facts) to restoreFunctionConstant(value, { restore(it).second }, frame, facts)
                }
                active.remove(reference); descriptors[result.first.typeId] = result.first
                return result
            }
            val (type, value) = restore(reference)
            return DeclarationBinding.Bound(type, if (value is CompilerValue.Typed) value else CompilerValue.Typed(type.typeId, value), descriptors = descriptors)
        }
        override fun closedConstant(reference: ConstantRef) = boundConstant(reference, null, emptyMap())
        override fun closedConstant(reference: ConstantRef, frame: Function) = boundConstant(reference, frame, emptyMap())

        private fun restoreFunctionConstant(value: ConstantValue.FunctionValue, restore: (ConstantRef) -> CompilerValue,
                                            frame: Function? = null, readonly: Map<DeclarationId, ClosedReadonlyBinding> = emptyMap()): CompilerValue {
            if (frame == null && readonly.isEmpty()) functionConstantCache[value]?.let { return it }
            val (codeUnit, codeId) = resolve(this, value.target)
            val record = codeUnit.model.declarations.declaration(codeId) as FunctionDeclaration
            val signature = resolveType(value.type, frame, readonly) as MCFPPFunctionType
            val captured = value.captures.associate { capture -> capture.declaration to restore(capture.value) }
            if (captured.isEmpty() && codeId !in codeUnit.lambdaNodes) {
                val target = codeUnit.heads.getValue(codeId).function
                return CompilerValue.Record(mapOf("target" to CompilerValue.Text(target.namespaceID.toString()), "environment" to CompilerValue.Record(emptyMap())))
            }
            require(codeUnit === this) { "A foreign implementation must be restored by its owning library" }
            val function = Function("__library_${privatePrefix}_${codeId.value}_constant_${constantFunctions.size}",
                files.getValue(record.common.environment).namespace.identifier, null).apply {
                declarationFile = files.getValue(record.common.environment)
                scope.parent.add(declarationFile!!.field)
                returnType = signature.result
            }
            require(record.normal.size == signature.parameters.size)
            val formals = record.normal.zip(signature.parameters).associate { (id, type) ->
                val parameter = FunctionParam(type, name(id), function)
                function.normalParams.add(parameter); function.scope.putVar(parameter.identifier, parameter.buildVar())
                id to parameter
            }
            val capturedTypes = model.tables.types.indices.map { TypeRef(it + 1L) }.mapNotNull { reference ->
                runCatching { resolveType(reference, frame, readonly) }.getOrNull()
            }.associateBy { it.typeId }
            val captures = captured.mapValues { (reference, snapshot) ->
                val (unit, id) = resolve(this, reference)
                val typeId = (snapshot as? CompilerValue.Typed)?.type
                val type = typeId?.let { capturedTypes[it] ?: MCFPPType.resolveTypeId(it) }
                    ?: (unit.model.declarations.declaration(id) as? ValueDeclaration)?.type?.let { unit.types.type(it) }
                    ?: error("Closed capture lacks a declared type")
                ClosedReadonlyBinding(type, snapshot, capturedTypes)
            }
            captures.forEach { (reference, fact) ->
                val (_, id) = resolve(this, reference)
                if (name(id) == "this") function.scope.putVar("this", StorageAccess.literal(fact.type, fact.value, "this", fact.descriptors))
            }
            val facts = readonly + captures.mapNotNull { (reference, fact) -> (reference as? DeclarationReference.Local)?.target?.let { it to fact } }.toMap()
            val lambda = lambdaNodes[codeId]
            val bodyId = requireNotNull(record.body)
            val original = body(bodyId) as Statement.Block
            val root = original.copy(statements = original.statements + listOfNotNull(lambda?.tail?.let {
                if (signature.result == MCFPPPrivateType.Void) Statement.Evaluate(original.scope, it) else Statement.Return(original.scope, it)
            }))
            val provider = object : StructuredSessionBindings by this, StatementProjectionBindings by this, StructuredFunctionSignatures {
                override fun body(reference: BodyId): SemanticNode = if (reference == bodyId) root else this@ImportedUnit.body(reference)
                override fun local(declaration: DeclarationId, frame: Function): Var<*>? = formals[declaration]?.let { frame.scope.getVar(it.identifier) }
                    ?: captures[DeclarationReference.Local(declaration)]?.let { StorageAccess.literal(it.type, it.value, this@ImportedUnit.name(declaration), it.descriptors) }
                    ?: this@ImportedUnit.local(declaration, frame)
                override fun formal(declaration: DeclarationId) = formals[declaration] ?: this@ImportedUnit.formal(declaration)
                override fun declaration(reference: DeclarationReference): StructuredNameBinding = captures[reference]?.let { fact ->
                    StructuredNameBinding.Value { StorageAccess.literal(fact.type, fact.value, types = fact.descriptors) }
                } ?: this@ImportedUnit.declaration(reference)
                override fun boundType(reference: TypeRef, frame: Function) = resolveType(reference, frame, facts)
                override fun dependentType(syntax: TypeSyntax, frame: Function) = resolveTypeNode(syntax, frame, facts)
                override fun dependentType(type: MCFPPType, frame: Function): MCFPPType? = dependentTypes[type.typeId]?.let { resolveTypeNode(model.syntax.node(it.syntax) as TypeSyntax, frame, facts) }
                override fun declaredType(type: MCFPPType, function: Function, readonly: Map<String, ClosedReadonlyBinding>): MCFPPType? =
                    dependentTypes[type.typeId]?.let { resolveTypeNode(model.syntax.node(it.syntax) as TypeSyntax, function, facts) }
            }
            val library = repository.register(provider, provider)
            function.bodyHandle = repository.handle(library, bodyId)
            constantFunctions.add(function)
            return CompilerValue.Record(mapOf("target" to CompilerValue.Text(function.namespaceID.toString()), "environment" to CompilerValue.Record(emptyMap()))).also {
                if (frame == null && readonly.isEmpty()) functionConstantCache[value] = it
            }
        }

        override fun boundType(reference: TypeRef, frame: Function): MCFPPType? {
            val type = model.tables.type(reference)
            if (type is LibraryType.Parameter && type.role == ParameterRole.LAMBDA_TYPE) {
                val (unit, owner) = resolve(this, type.owner)
                if (unit !== this || frame.bodyHandle?.repository !== repository ||
                    model.syntax.bodies.getOrNull((frame.bodyHandle!!.body.value - 1).toInt())?.owner != owner) return null
            }
            return resolveType(reference, frame, emptyMap())
        }
        override fun dependentType(syntax: TypeSyntax, frame: Function): MCFPPType = resolveTypeNode(syntax, frame, emptyMap())
        override fun dependentType(type: MCFPPType, frame: Function): MCFPPType? = dependentTypes[type.typeId]?.let { resolveTypeNode(model.syntax.node(it.syntax) as TypeSyntax, frame, emptyMap()) }
        override fun declaredType(type: MCFPPType, function: Function, readonly: Map<String, ClosedReadonlyBinding>): MCFPPType? {
            val facts = parameters.filterValues { it.function === function }.mapNotNull { (id, parameter) -> readonly[parameter.identifier]?.let { id to it } }.toMap()
            dependentTypes[type.typeId]?.let { return resolveTypeNode(model.syntax.node(it.syntax) as TypeSyntax, function, facts) }
            val parameter = parameterViews.entries.firstOrNull { it.value.typeId == type.typeId }
            if (parameter != null) {
                val value = facts[parameter.key] ?: readonly[name(parameter.key)] ?: return function.scope.getType(name(parameter.key))?.takeUnless { it is MCFPPGenericParamType }
                val payload = if (value.value is CompilerValue.Typed) value.value.payload else value.value
                val actual = (payload as? CompilerValue.TypeValue)?.id ?: return null
                return value.descriptors[actual] ?: MCFPPType.resolveTypeId(actual)
            }
            return null
        }

        private inner class MaterializedBindings(val owner: DeclarationId, val target: CompiledGenericDataTemplate) :
            StructuredSessionBindings by this@ImportedUnit, StatementProjectionBindings by this@ImportedUnit, StructuredFunctionSignatures, StructuredTemplateMemberState {
            val members = linkedMapOf<DeclarationId, Any>()
            private val formals = linkedMapOf<DeclarationId, FunctionParam>()
            private var memberProjection: ProjectedSyntax? = null
            private var recipesScheduled = false
            private var pendingMemberBindings = 0
            init { target.structuredMemberState = this }
            private val record get() = model.declarations.declaration(owner) as CompoundDeclaration
            private var copiedAnnotations: Map<AnnotationId, Annotation>? = null
            private val copies: Map<AnnotationId, Annotation> get() = copiedAnnotations ?: run {
                val originals = model.annotations.indices.map { restoredAnnotations.annotation(AnnotationId(it + 1L)) }
                val copied = annotationCloner.clone(originals)
                copied.annotations.mapIndexed { index, value ->
                    annotationPhases[value] = annotationCloner.phase(value)
                    AnnotationId(index + 1L) to value
                }.toMap().also { copiedAnnotations = it }
            }
            override fun preparationFacts(): RawPreparationFact = RawPreparationFact.row(
                RawPreparationFact.scalar(pendingMemberBindings), RawPreparationFact.scalar(recipesScheduled), RawPreparationFact.identity(memberProjection),
                RawPreparationFact.row(*members.map { (id, value) -> RawPreparationFact.row(RawPreparationFact.scalar(id.value), RawPreparationFact.identity(value)) }.toTypedArray())
            )
            override fun checkpoint(): () -> Unit {
                val savedMembers = LinkedHashMap(members); val savedFormals = LinkedHashMap(formals)
                val savedProjection = memberProjection; val savedRecipes = recipesScheduled; val savedPending = pendingMemberBindings
                val savedCopies = copiedAnnotations
                val annotationStates = savedCopies?.values?.map { it.checkpointDeclarationState() }.orEmpty()
                val phases = IdentityHashMap<Annotation, AnnotationPhase?>().apply { savedCopies?.values?.forEach { put(it, annotationPhases[it]) } }
                return {
                    members.clear(); members.putAll(savedMembers); formals.clear(); formals.putAll(savedFormals)
                    memberProjection = savedProjection; recipesScheduled = savedRecipes; pendingMemberBindings = savedPending
                    copiedAnnotations?.values?.forEach { annotationPhases.remove(it) }
                    copiedAnnotations = savedCopies
                    phases.forEach { (annotation, phase) -> if (phase != null) annotationPhases[annotation] = phase }
                    annotationStates.forEach { it.restore() }
                }
            }
            private fun afterField(name: String, bind: (Var<*>) -> Unit) {
                pendingMemberBindings++
                target.whenDeclaredFieldReady(name) { field ->
                    repository.requireOpen()
                    bind(field)
                    pendingMemberBindings--
                    scheduleRecipes()
                }
            }
            fun facts(): Map<DeclarationId, ClosedReadonlyBinding> = record.readonly.associateWith { id ->
                closed(requireNotNull(target.scope.getVar(this@ImportedUnit.name(id))))
            }
            fun copyAnnotations(recorded: List<Annotation>): TemplateAnnotationClone {
                val values = recorded.map { copies.getValue(annotationIds.getValue(it)) }
                return TemplateAnnotationClone(values, values.filter { annotationCloner.phase(it) == AnnotationPhase.PENDING })
            }
            override fun name(use: NameUse, role: NameRole): ProjectedName {
                val targetReference = when (use) { is NameUse.Local -> DeclarationReference.Local(use.target); is NameUse.Declaration -> use.target; else -> null }
                if (targetReference != null) {
                    val (unit, id) = resolve(this@ImportedUnit, targetReference)
                    if (unit === this@ImportedUnit && (id in members || id in record.readonly || id == owner))
                        return ProjectedName(this@ImportedUnit.name(id), binding = declaration(targetReference))
                }
                if (use is NameUse.Lookup && use.candidates.isNotEmpty()) {
                    val values = use.candidates.map(::declaration)
                    if (values.all { it is StructuredNameBinding.Functions }) return ProjectedName(use.name, use.namespace,
                        StructuredNameBinding.Functions(values.flatMap { (it as StructuredNameBinding.Functions).candidates }))
                }
                return this@ImportedUnit.name(use, role)
            }
            override fun declaration(reference: DeclarationReference): StructuredNameBinding {
                if (reference is DeclarationReference.Local) {
                    val id = reference.target
                    if (id == owner) return StructuredNameBinding.Type(target.getType())
                    val value = members[id]
                    if (value is Function) return StructuredNameBinding.Functions(listOf(value))
                    if (value is Var<*>) return StructuredNameBinding.Value { frame ->
                        if (frame.accessorField == value.identifier) frame.scope.getVar("field")
                        else (frame.scope.getVar("this") as? DataTemplateObject)?.getMemberVar(value.identifier, Member.AccessModifier.PRIVATE)?.first
                            ?: value
                    }
                }
                return this@ImportedUnit.declaration(reference)
            }
            override fun local(declaration: DeclarationId, frame: Function): Var<*>? =
                if (declaration in record.readonly) target.scope.getVar(this@ImportedUnit.name(declaration))
                else formals[declaration]?.let { frame.scope.getVar(it.identifier) } ?: this@ImportedUnit.local(declaration, frame)
            override fun formal(declaration: DeclarationId): FunctionParam? = activeFormal(declaration) ?: formals[declaration] ?: this@ImportedUnit.formal(declaration)
            override fun type(reference: TypeRef): Any = try { resolveType(reference, null, facts()) } catch (_: DeclarationBinding.Failure) { types.type(reference) }
            override fun boundType(reference: TypeRef, frame: Function) = resolveType(reference, frame, facts())
            override fun dependentType(syntax: TypeSyntax, frame: Function) = resolveTypeNode(syntax, frame, facts())
            override fun dependentType(type: MCFPPType, frame: Function): MCFPPType? = dependentTypes[type.typeId]?.let { resolveTypeNode(model.syntax.node(it.syntax) as TypeSyntax, frame, facts()) }
            override fun declaredType(type: MCFPPType, function: Function, readonly: Map<String, ClosedReadonlyBinding>): MCFPPType? {
                val all = facts() + formals.filterValues { it.function === function }.mapNotNull { (id, formal) -> readonly[formal.identifier]?.let { id to it } }.toMap()
                dependentTypes[type.typeId]?.let { return resolveTypeNode(model.syntax.node(it.syntax) as TypeSyntax, function, all) }
                val id = parameterViews.entries.firstOrNull { it.value.typeId == type.typeId }?.key ?: return null
                val value = all[id] ?: return function.scope.getType(this@ImportedUnit.name(id))?.takeUnless { it is MCFPPGenericParamType }
                val payload = if (value.value is CompilerValue.Typed) value.value.payload else value.value
                val actual = (payload as? CompilerValue.TypeValue)?.id ?: return null
                return value.descriptors[actual] ?: MCFPPType.resolveTypeId(actual)
            }

            private fun attach(id: DeclarationId, value: Any) {
                val declaration = model.declarations.declaration(id)
                applyMetadata(declaration, value)
                if (value is Function) value.structuredSignatureBindings = guardedSignatures(this@MaterializedBindings)
                when (value) {
                    is Function -> { rememberType(value.returnType); value.normalParams.forEach { rememberType(it.type) } }
                    is Var<*> -> rememberType(value.type)
                }
                if (!annotationsReady) return
                val annotations = declaration.common.annotations.map { copies.getValue(it) }
                when (value) {
                    is Function -> { value.annotations.clear(); value.annotations.addAll(annotations) }
                    is Var<*> -> { value.annotations.clear(); value.annotations.addAll(annotations) }
                }
                annotations.filter { annotationCloner.phase(it) == AnnotationPhase.PENDING }.forEach { annotation ->
                    val apply = {
                        when (value) { is Function -> annotation.on(value); is Var<*> -> annotation.on(value) }
                        completeAnnotation(annotation)
                    }
                    if (state == LibraryImportState.COMMITTED) apply() else pendingAnnotations.add(apply)
                }
            }
            fun attachAnnotations() {
                members.toMap().forEach { (id, value) -> attach(id, value) }
                scheduleRecipes()
            }
            private fun scheduleRecipes() {
                if (!annotationsReady || memberProjection == null || recipesScheduled || pendingMemberBindings != 0) return
                recipesScheduled = true
                val apply = {
                    val root = model.syntax.nodes.filterIsInstance<TemplateBody>().single { model.syntax.scope(it.scope).owner == owner }
                    root.members.filterIsInstance<TemplateMember.AnnotationConstruction>().forEach { recipe ->
                        val (providerUnit, providerId) = resolve(this@ImportedUnit, recipe.provider)
                        val binding = (providerUnit.model.declarations.declaration(providerId) as AnnotationProviderDeclaration).binding
                        val provider = NativeDeclarations.restoreAnnotationProvider(binding, managed)
                        val arguments = recipe.arguments.map { expression -> requireNotNull(evaluate(expression, null, facts()).constant) { "Annotation recipe arguments must be closed" } }
                        val values = ArrayList(arguments.map { requireNotNull(annotationValue(it)) { "Unsupported annotation constructor argument" } })
                        val annotation = requireNotNull(Annotation.build(provider, values)) { "Cannot construct annotation recipe ${binding.ownerClass}" }
                        annotation.recordCompilerArguments(arguments)
                        recipe.targets.forEach { id ->
                            when (val value = members[id] ?: if (id == owner) target else error("Annotation recipe target is not materialized: $id")) {
                                is Function -> { value.annotations.add(annotation); annotation.on(value) }
                                is Var<*> -> { value.annotations.add(annotation); annotation.on(value) }
                                is DataTemplate -> { value.annotations.add(annotation); annotation.on(value) }
                                is FunctionParam -> requireNotNull(value.function.scope.getVar(value.identifier)).let { it.annotations.add(annotation); annotation.on(it) }
                                else -> error("Unsupported annotation recipe target: $id")
                            }
                        }
                        completeAnnotation(annotation)
                    }
                }
                if (state == LibraryImportState.COMMITTED) apply() else pendingAnnotations.add(apply)
            }
            private fun annotationValue(value: CompilerValue): Any? = when (value) {
                is CompilerValue.Typed -> when (val payload = value.payload) {
                    is CompilerValue.Integral -> when (value.type) {
                        MCFPPNBTType.Byte.typeId -> payload.value.toByte(); MCFPPNBTType.Short.typeId -> payload.value.toShort()
                        MCFPPBaseType.Int.typeId -> payload.value.toInt(); else -> payload.value
                    }
                    else -> annotationValue(payload)
                }
                is CompilerValue.Text -> value.value; is CompilerValue.Bool -> value.value
                is CompilerValue.Integral -> value.value; is CompilerValue.FloatBits -> Float.fromBits(value.bits)
                is CompilerValue.DoubleBits -> Double.fromBits(value.bits)
                is CompilerValue.Nbt -> top.mcfpp.nbt.tags.Tag.toNBT(value.snbt).toJava()
                is CompilerValue.Sequence -> value.elements.map { annotationValue(it) }
                is CompilerValue.Record -> value.fields.mapValues { annotationValue(it.value) }
                else -> null
            }

            private fun bindFunction(id: DeclarationId, function: Function, projection: ProjectedSyntax) {
                val declaration = model.declarations.declaration(id)
                members[id] = function
                val ids = when (declaration) { is FunctionDeclaration -> declaration.readonly + declaration.normal; is ConstructorDeclaration -> declaration.normal; else -> error("Not a callable") }
                val actual = (function as? top.mcfpp.model.Generic<*>)?.readOnlyParams.orEmpty() + (function as? NativeFunction)?.readOnlyParams.orEmpty() + function.normalParams
                require(ids.size == actual.size) { "Materialized signature differs from D$id" }
                ids.zip(actual).forEach { (parameterId, parameter) ->
                    val value = model.declarations.declaration(parameterId) as ValueDeclaration
                    parameter.type = resolveType(requireNotNull(value.type), function, facts())
                    parameter.defaultBodyHandle = value.defaultBody?.let { repository.handle(projection.handle.library, it) }
                    formals[parameterId] = parameter; members[parameterId] = parameter
                    if (value.role == ValueRole.READONLY_PARAMETER && parameter.type == MCFPPConcreteType.Type)
                        function.scope.putType(parameter.identifier, parameterViews.getValue(parameterId), true)
                    attach(parameterId, parameter)
                }
                function.ast = null
                val body = when (declaration) { is FunctionDeclaration -> declaration.body; is ConstructorDeclaration -> declaration.body; else -> null }
                function.bodyHandle = body?.let { repository.handle(projection.handle.library, it) }
                if (declaration is FunctionDeclaration) function.returnType = declaration.result?.let { resolveType(it, function, facts()) } ?: MCFPPPrivateType.Void
                attach(id, function)
            }

            private fun restoreFunction(id: DeclarationId, projection: ProjectedSyntax): Function {
                val declaration = model.declarations.declaration(id) as FunctionDeclaration
                val head = FunctionDeclarations.restoreHead(id, declaration.copy(common = declaration.common.copy(annotations = emptyList())),
                    { model.declarations.declaration(it) as ValueDeclaration }, object : FunctionRestoreBindings {
                        override fun type(reference: TypeRef) = resolveType(reference, null, facts())
                        override fun constant(reference: ConstantRef) = restoredConstant(reference)
                        override fun environment(reference: EnvironmentId) = files.getValue(reference)
                        override fun owner(reference: DeclarationId): CompoundData? = target
                        override fun nativeMethod(binding: NativeBinding) = this@ImportedUnit.nativeMethod(binding)
                    })
                bindFunction(id, head.function, projection)
                return head.function
            }

            fun bindMembers(projection: ProjectedSyntax) {
                rememberType(target.getType())
                memberProjection = projection
                val root = projection.context as top.mcfpp.antlr.mcfppParser.TemplateBodyContext
                root.templateMemberDeclaration().forEach { site ->
                    val id = projection.declaration(site) ?: return@forEach
                    val declaration = model.declarations.declaration(id)
                    when (declaration) {
                        is ValueDeclaration -> {
                            val field = target.scope.getVar(declaration.common.name)
                            if (field != null) { members[id] = field; attach(id, field) }
                            else {
                                require(declaration.common.name in target.deferredFields) { "A materialized field has neither a real slot nor a deferred declaration" }
                                afterField(declaration.common.name) { actual -> members[id] = actual; attach(id, actual) }
                            }
                        }
                        is FunctionDeclaration -> {
                            val body = site.templateMember().templateFunctionDeclaration()?.curlBlock()
                            val placeholders = target.scope.functions.values.flatten().filter { it.ast === body }
                            if (declaration.native != null) {
                                placeholders.forEach { placeholder -> target.scope.functions.values.forEach { it.removeAll { candidate -> candidate === placeholder } } }
                                target.scope.addFunction(restoreFunction(id, projection), true)
                            } else bindFunction(id, placeholders.single(), projection)
                        }
                        is ConstructorDeclaration -> {
                            val body = site.templateMember().templateConstructorDeclaration()?.curlBlock()
                            bindFunction(id, target.constructors.single { it.ast === body }, projection)
                        }
                        else -> Unit
                    }
                }
                record.members.forEach { id -> when (val declaration = model.declarations.declaration(id)) {
                    is PropertyDeclaration -> {
                        fun bindProperty() {
                        val property = requireNotNull(target.scope.getProperty(declaration.common.name))
                        members[id] = property
                        fun bindAccessor(accessor: LibraryAccessor, actual: Function?): Function? {
                            val reference = when (accessor) { is LibraryAccessor.Function -> accessor.target; is LibraryAccessor.Native -> accessor.target; else -> null }
                            if (reference is DeclarationReference.Local) {
                                val function = if (accessor is LibraryAccessor.Native) restoreFunction(reference.target, projection) else actual ?: restoreFunction(reference.target, projection)
                                bindFunction(reference.target, function, projection)
                                function.accessorField = declaration.common.name
                                return function
                            }
                            return actual
                        }
                        val getter = bindAccessor(declaration.getter, (property.accessor as? top.mcfpp.model.property.FunctionAccessor)?.function ?: (property.accessor as? top.mcfpp.model.property.NativeAccessor)?.function)
                        val setter = bindAccessor(declaration.setter, (property.mutator as? top.mcfpp.model.property.FunctionMutator)?.function ?: (property.mutator as? top.mcfpp.model.property.NativeMutator)?.function)
                        if (declaration.getter is LibraryAccessor.Native || declaration.setter is LibraryAccessor.Native) {
                            val replacement = property.copy(
                                accessor = if (declaration.getter is LibraryAccessor.Native) top.mcfpp.model.property.NativeAccessor(getter as NativeFunction) else property.accessor,
                                mutator = if (declaration.setter is LibraryAccessor.Native) top.mcfpp.model.property.NativeMutator(setter as NativeFunction) else property.mutator)
                            replacement.declaredParentTemplate = target; replacement.accessModifier = property.accessModifier
                            replacement.isFinal = property.isFinal; replacement.isStatic = property.isStatic; replacement.isInherited = property.isInherited
                            target.scope.putProperty(declaration.common.name, replacement, true); members[id] = replacement
                        }
                        }
                        if (target.scope.getProperty(declaration.common.name) != null) bindProperty()
                        else {
                            require(declaration.common.name in target.deferredFields) { "A materialized property has no deferred declaration" }
                            afterField(declaration.common.name) { bindProperty() }
                        }
                    }
                    is FunctionDeclaration -> if (id !in members) {
                        val function = restoreFunction(id, projection)
                        target.scope.addFunction(function, true)
                    }
                    else -> Unit
                } }
                if (record.members.none { model.declarations.declaration(it) is OperatorDeclaration }) scheduleRecipes()
            }
            fun bindOperators(projection: ProjectedSyntax) {
                record.members.forEach { id -> (model.declarations.declaration(id) as? OperatorDeclaration)?.let { declaration ->
                    val reference = declaration.implementation as? DeclarationReference.Local
                        ?: error("A materialized operator requires an owned implementation")
                    val function = members[reference.target] as? Function ?: restoreFunction(reference.target, projection)
                    target.scope.addOperator(declaration.operator, declaration.operand?.let { resolveType(it, null, facts()) }, function, true)
                    members[id] = function
                } }
                scheduleRecipes()
            }
        }

        fun registerIdentities() {
            val children = model.declarations.declarations.withIndex().groupBy { it.value.common.owner }
            val positions = children.values.flatMap { siblings -> siblings.mapIndexed { index, record ->
                DeclarationId(record.index + 1L) to index
            } }.toMap()
            val paths = hashMapOf<DeclarationId, List<String>>()
            fun path(id: DeclarationId): List<String> {
                paths[id]?.let { return it }
                val record = model.declarations.declaration(id)
                val position = positions.getValue(id)
                return (record.common.owner?.let(::path).orEmpty() + "${record.tag}:${record.common.name}:$position").also { paths[id] = it }
            }
            identities.register(identity, model.manifest.dependencies.map { LibraryIdentity(it.library, it.classifier) },
                model.declarations.declarations.mapIndexed { index, declaration ->
                    val id = DeclarationId(index + 1L)
                    LibraryDeclarationIdentity(id, declaration.common.key, declaration.common.origin,
                        if (declaration.common.key == null && declaration.common.origin == null) path(id) else null)
                })
        }
        private fun bindIdentity(id: DeclarationId, value: Any) {
            identities.resolve(identity, DeclarationReference.Local(id)).bind(value)
            when (value) {
                is CompoundData -> rememberType(value.getType())
                is MCFPPType -> rememberType(value)
            }
        }
        private fun nativeMethod(binding: NativeBinding): Method = nativeDirectory?.nativeMethod(nativeUnit, binding)
            ?: NativeDeclarations.resolveMethod(binding, managed)
        private fun nativeParameter(owner: DeclarationId, index: Long): MCFPPGenericParamType = nativeParameterViews.getOrPut(owner to index) {
            val record = model.declarations.declaration(owner) as? FunctionDeclaration ?: error("Native parameter owner is not D2")
            val method = nativeMethod(requireNotNull(record.native) { "Native parameter owner has no binding" })
            val names = requireNotNull(method.getAnnotation(top.mcfpp.annotations.MNIFunction::class.java)).genericType
            require(index >= 0 && index < names.size.toLong()) { "Native generic parameter index is out of bounds" }
            MCFPPGenericParamType(names[index.toInt()], arrayListOf(),
                identities.parameter(identity, DeclarationReference.Local(owner), ParameterRole.NATIVE_TYPE, index))
                .also { view -> heads[owner]?.function?.scope?.putType(names[index.toInt()], view, true) }
        }
        fun prepareBindings() {
            library = repository.register(this, this)
            model.declarations.declarations.forEachIndexed { index, record -> if (record is ValueDeclaration && record.role in setOf(ValueRole.READONLY_PARAMETER, ValueRole.LAMBDA_PARAMETER)) {
                val id = DeclarationId(index + 1L)
                parameterViews[id] = MCFPPGenericParamType(record.common.name, arrayListOf(),
                    identities.parameter(identity, DeclarationReference.Local(requireNotNull(record.common.owner)),
                        if (record.role == ValueRole.LAMBDA_PARAMETER) ParameterRole.LAMBDA_TYPE else ParameterRole.READONLY, requireNotNull(record.parameterIndex)))
            } }
            nativeUnit = NativeLibraryUnit(model.declarations, types, managed).also { it.importPlan = model.importPlan }
        }
        fun allocateCompounds() {
            model.declarations.declarations.forEachIndexed { index, record -> if (record is NamespaceDeclaration)
                compounds.install(DeclarationId(index + 1L), namespaces.getValue(record.common.name)) }
            compounds.allocate()
            deferredCompounds.forEach { number ->
                val id = DeclarationId(number.toLong())
                val template = compounds.shell(id).value as DataTemplate
                val record = model.declarations.declaration(id) as CompoundDeclaration
                template.declarationFile = files.getValue(record.common.environment)
                template.declarationEnvironment = null
                template.isAbstract = record.common.flags and DeclarationFlags.ABSTRACT != 0L
                template.isFinal = record.common.flags and DeclarationFlags.FINAL != 0L
                val layout = requireNotNull(lazyPlan).compounds.getValue(number)
                template.deferLibraryMembers(DeferredLibraryMembers(repository::requireOpen, load = {
                    layout.functions.forEach { allocateHead(DeclarationId(it.toLong())) }
                    layout.functions.forEach { function ->
                        val functionId = DeclarationId(function.toLong())
                        finishBody(functionId, heads.getValue(functionId))
                    }
                    compounds.bind(id)
                }, onLoaded = {
                    if (annotationsReady) attachAnnotations(id, template)
                    if (state == LibraryImportState.COMMITTED) top.mcfpp.Project.registerTemplateContract(template)
                }))
                compounds.prepareHeaders(id)
            }
            deferredCompounds.forEach { refreshNominalViews(compounds.shell(DeclarationId(it.toLong())).value as DataTemplate) }
        }
        private fun defaultHandle(id: DeclarationId): BodyHandle? {
            val record = model.declarations.declaration(id) as ValueDeclaration
            return (record.defaultBody ?: frozenBodies[id])?.let { repository.handle(library, it) }
        }
        private fun restoredConstant(reference: ConstantRef) = RestoredDefault(types.constant(reference), descriptors())
        private fun projectDefault(body: BodyId): top.mcfpp.antlr.mcfppParser.ValueContext {
            val projection = CurrentSyntaxProjection(this, this)
            val context = projection.defaultValue(body)
            repository.registerProjection(repository.handle(library, body), projection, context)
            return context
        }

        fun allocateEnvironments() {
            model.declarations.environments.forEachIndexed { index, environment ->
                val namespace = namespaces[environment.namespace] ?: problem("LIB_SYMBOL_UNRESOLVED", "Declaration namespace is not staged: ${environment.namespace}")
                val id = EnvironmentId(index + 1L)
                val scope = NamespaceScope("@library/$privatePrefix/environment/${id.value}")
                environmentScopes[id] = scope
                files[id] = MCFPPFile(namespace).also { it.field.namespaceField = scope }
            }
        }

        private fun fillScope(scope: NamespaceScope, namespace: String, only: String? = null, alias: String? = null, includePrivate: Boolean = false) {
            declarationsByNamespace[namespace].orEmpty().forEach { (index, record) ->
                if (!includePrivate && (record.common.key == null || record.common.access != DeclarationAccess.PUBLIC)) return@forEach
                if (only != null && record.common.name != only) return@forEach
                val id = DeclarationId(index + 1L)
                val name = alias ?: record.common.name
                when (record) {
                    is FunctionDeclaration -> if ((record.receiver == null || record.execution == FunctionExecutionKind.NATIVE) && record.common.owner?.let { model.declarations.declaration(it) is NamespaceDeclaration } != false && id in heads) {
                        val function = heads.getValue(id).function
                        scope.functions.getOrPut(if (record.common.access == DeclarationAccess.PRIVATE) function.identifier else name) { arrayListOf() }.add(function)
                    }
                    is CompoundDeclaration -> {
                        val value = compounds.value(id) as DataTemplate
                        if (value is ObjectCompoundData) scope.objects.add(value) else scope.template[name] = value
                    }
                    is EnumDeclaration -> scope.enums.add(compounds.value(id) as CompilerEnum)
                    is TypeAliasDeclaration -> scope.putResolvedAlias(name, compounds.value(id) as MCFPPType, true)
                    is ValueDeclaration -> if (record.role == ValueRole.GLOBAL) scope.vars[name] = globals.getValue(id)
                    is AnnotationProviderDeclaration -> scope.annotations[name] = NativeDeclarations.restoreAnnotationProvider(record.binding, managed)
                    else -> Unit
                }
            }
        }

        fun bindEnvironments() {
            model.declarations.environments.forEachIndexed { index, environment ->
                val id = EnvironmentId(index + 1L)
                val file = files.getValue(id)
                val own = environmentScopes.getValue(id)
                fillScope(own, environment.namespace, includePrivate = true)
                file.field.importedNamespaceField[environment.namespace] = own
                val allowed = (model.manifest.dependencies.map { LibraryIdentity(it.library, it.classifier) } + identity).toSet()
                environment.imports.forEachIndexed { ordinal, item ->
                    val sources = units.values.filter { unit -> unit.identity in allowed &&
                        (item.resolvedPackages.isEmpty() || unit.identity.library in item.resolvedPackages) &&
                        item.targetNamespace in unit.namespaceNames }
                    if (sources.isEmpty()) problem("LIB_DEPENDENCY", "Author import has no staged package namespace: ${item.targetNamespace}")
                    val scope = NamespaceScope("@library/$privatePrefix/environment/${id.value}/import/$ordinal")
                    sources.forEach { it.fillScope(scope, item.targetNamespace, item.member, if (item.member != null) item.alias else null) }
                    if (item.member == null) file.field.importedNamespaceField[item.alias ?: item.targetNamespace] = scope
                    else file.field.importField[item.targetNamespace] = scope
                    file.unsolvedImports[item.targetNamespace] = item.member ?: "*"
                }
            }
        }

        fun allocateHeads() {
            val ids = model.importPlan?.signatures?.keys ?: model.declarations.declarations.indices
                .filter { model.declarations.declarations[it] is FunctionDeclaration }.map { it + 1 }
            ids.filter { lazyPlan?.signatures?.get(it)?.owner !in deferredCompounds }
                .forEach { allocateHead(DeclarationId(it.toLong())) }
        }

        private fun allocateHead(id: DeclarationId) {
                if (id in heads || id in lambdaNodes) return
                val record = model.declarations.declaration(id) as FunctionDeclaration
                val directOwner = record.common.owner?.let { model.declarations.declaration(it) }
                val hidden = record.common.access != DeclarationAccess.PUBLIC && directOwner !is CompoundDeclaration
                val allocation = record.copy(common = record.common.copy(annotations = emptyList(),
                    name = if (hidden) "__library_${privatePrefix}_${id.value}_${record.common.name}" else record.common.name), tags = emptyList())
                var head = FunctionDeclarations.restoreHead(id, allocation, { (model.declarations.declaration(it) as ValueDeclaration).copy(frozen = null) },
                    object : FunctionRestoreBindings {
                        override fun type(reference: TypeRef) = types.type(reference)
                        override fun constant(reference: ConstantRef) = restoredConstant(reference)
                        override fun environment(reference: EnvironmentId) = files.getValue(reference)
                        override fun owner(reference: DeclarationId): CompoundData? {
                            return compounds.owner(reference)
                        }
                        override fun nativeMethod(binding: NativeBinding): Method = this@ImportedUnit.nativeMethod(binding)
                        override fun readonlyType(owner: DeclarationId, index: Int, parameter: FunctionParam, type: MCFPPGenericParamType) {
                            val value = parameterViews.getValue(record.readonly[index])
                            parameter.function.scope.putType(parameter.identifier, value, true)
                        }
                    })
                val linked = runtimeByDeclaration[DeclarationReference.Local(id)]?.singleOrNull()
                if (linked?.policy == RuntimePolicy.LINK_ONLY && record.body == null && record.execution == FunctionExecutionKind.ORDINARY &&
                    record.normal.isEmpty() && record.readonly.isEmpty() && (record.result == null || types.type(record.result) == MCFPPPrivateType.Void)) {
                    val path = linked.resourcePath.substringAfter("/data/")
                    val namespace = path.substringBefore('/')
                    val identifier = path.substringAfter("/function/").removeSuffix(".mcfunction")
                    val parent = identifier.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
                    val raw = MCFunction(namespace, parent, identifier.substringAfterLast('/')).apply { declarationFile = files.getValue(record.common.environment) }
                    applyMetadata(record, raw)
                    head = RestoredFunctionHead(raw, null, emptyMap())
                }
                heads[id] = head
                nativeParameterViews.filterKeys { it.first == id }.values.forEach { view -> head.function.scope.putType(view.identifier, view, true) }
                compounds.install(id, head.function)
                parameters.putAll(head.parameters)
                head.parameters.forEach { (parameterId, parameter) -> compounds.install(parameterId, parameter) }
                if ((record.receiver == null || record.execution == FunctionExecutionKind.NATIVE) && record.common.access == DeclarationAccess.PUBLIC && record.common.key != null && (directOwner == null || directOwner is NamespaceDeclaration)) {
                    val namespace = namespaces.getValue(files.getValue(record.common.environment).namespace.identifier)
                    val candidates = namespace.scope.functions.getOrPut(record.common.name) { arrayListOf() }
                    if (candidates.any { ParameterMatcher.sameSignature(it, head.function) }) problem("LIB_DECL_CONFLICT", "Duplicate staged function ${record.common.name}")
                    candidates.add(head.function)
                }
        }

        fun finishBodies() {
            heads.forEach { (id, head) -> finishBody(id, head) }
            model.declarations.declarations.forEachIndexed { index, record ->
                val value: Any? = when (record) {
                    is AnnotationProviderDeclaration -> NativeDeclarations.restoreAnnotationProvider(record.binding, managed)
                    is MacroDeclaration -> requireNotNull(top.mcfpp.io.info.MacroFunctionInfo.from(requireNotNull(nativeDirectory).nativeMethod(nativeUnit, record.binding.native)))
                        .declaredAs(record.binding.sourceName, record.binding.parameters.map { it.name })
                    is ReferenceHelperDeclaration -> NativeDeclarations.restoreReference(record, types)
                    else -> null
                }
                if (value != null) compounds.install(DeclarationId(index + 1L), value)
            }
        }

        private fun finishBody(id: DeclarationId, head: RestoredFunctionHead) {
                val runtime = runtimeByDeclaration[DeclarationReference.Local(id)]?.singleOrNull()
                if (runtime?.policy == RuntimePolicy.LINK_ONLY) {
                    val content = resourceBytes.getValue(runtime.resourcePath)
                    val text = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(content)).toString()
                    text.lineSequence().filter { it.isNotBlank() }.forEach { head.function.commands.add(top.mcfpp.command.Command(it)) }
                    head.function.bodyCompiled = true
                } else head.body?.let { head.function.bodyHandle = repository.handle(library, it) }
                val record = model.declarations.declaration(id) as FunctionDeclaration
                if (head.body == null && runtime == null && record.execution != FunctionExecutionKind.NATIVE && !head.function.isAbstract)
                    problem("LIB_BODY_UNSUPPORTED", "Function D$id has neither a retained body nor a linked runtime resource")
                if (record.tags.isNotEmpty()) pendingAnnotations += { record.tags.forEach { head.function.addTag(it.namespace, it.name) } }
                head.parameters.forEach { (parameterId, parameter) ->
                    val value = model.declarations.declaration(parameterId) as ValueDeclaration
                    parameter.hasDefault = value.defaultBody != null || value.frozen != null
                    value.frozen?.let { constant -> restoredConstant(constant).let { restored -> parameter.defaultValue = restored.value; parameter.defaultTypes = restored.types } }
                    parameter.defaultBodyHandle = defaultHandle(parameterId)
                }
        }

        fun finishCompounds() {
            val errors = top.mcfpp.Project.errorCount
            compounds.bindAll(deferredCompounds)
            model.declarations.declarations.forEachIndexed { index, record -> if (record is ValueDeclaration && record.role == ValueRole.GLOBAL) {
                val id = DeclarationId(index + 1L)
                val type = types.type(requireNotNull(record.type))
                val value = record.frozen?.let { constant -> restoredConstant(constant).let { restored ->
                    requireNotNull(StorageAccess.restore(type, restored.value, record.common.name, restored.types))
                } } ?: type.buildUnConcrete(record.common.name)
                applyMetadata(record, value)
                value.isStatic = true
                value.nbtPath = top.mcfpp.lib.NBTPath(top.mcfpp.lib.StorageSource("mcfpp:system"))
                    .memberIndex("library_$privatePrefix").memberIndex("global_${id.value}")
                if (record.frozen != null) StorageAccess.bindStaticField(value) else StorageAccess.publishNbt(value)
                globals[id] = value
                compounds.install(id, value)
                files.getValue(record.common.environment).field.vars[record.common.name] = value
            } }
            if (top.mcfpp.Project.errorCount != errors) problem("LIB_DECL_CONFLICT", "Compound binding emitted declaration/layout errors")
            model.declarations.declarations.forEachIndexed { index, record ->
                if (record.common.access != DeclarationAccess.PUBLIC || record.common.key == null) return@forEachIndexed
                val id = DeclarationId(index + 1L)
                val namespace = namespaces.getValue(files.getValue(record.common.environment).namespace.identifier)
                when (record) {
                    is CompoundDeclaration -> {
                        val value = compounds.value(id) as DataTemplate
                        val added = if (value is ObjectCompoundData) namespace.scope.addObject(value.identifier, value, false)
                            else namespace.scope.addTemplate(value.identifier, value, false)
                        if (!added) problem("LIB_DECL_CONFLICT", "Duplicate staged compound ${value.namespaceID}")
                    }
                    is EnumDeclaration -> if (!namespace.scope.addEnum(record.common.name, compounds.value(id) as CompilerEnum, false))
                        problem("LIB_DECL_CONFLICT", "Duplicate staged enum ${record.common.name}")
                    is TypeAliasDeclaration -> if (!namespace.scope.putResolvedAlias(record.common.name, compounds.value(id) as MCFPPType, false))
                        problem("LIB_DECL_CONFLICT", "Duplicate staged alias ${record.common.name}")
                    is ValueDeclaration -> if (record.role == ValueRole.GLOBAL && namespace.scope.vars.putIfAbsent(record.common.name, globals.getValue(id)) != null)
                        problem("LIB_DECL_CONFLICT", "Duplicate staged global ${record.common.name}")
                    is AnnotationProviderDeclaration -> {
                        val provider = NativeDeclarations.restoreAnnotationProvider(record.binding, managed)
                        val previous = namespace.scope.annotations.putIfAbsent(record.common.name, provider)
                        if (previous != null && previous !== provider) problem("LIB_DECL_CONFLICT", "Duplicate staged provider ${record.common.name}")
                    }
                }
            }
        }
        fun enums(): List<CompilerEnum> = model.declarations.declarations.mapIndexedNotNull { index, record ->
            if (record is EnumDeclaration) compounds.value(DeclarationId(index + 1L)) as CompilerEnum else null
        }

        fun validateReferences() {
            // The bundled plan is generated from an already validated, closed protocol model
            // and checked against its exact digest. Resolving every syntax reference here
            // would construct the very members and annotation arenas it defers. External
            // libraries retain the full semantic validation below.
            if (lazyPlan != null) return
            SyntaxCodec.validateAll(model.syntax, object : SyntaxReferenceValidator {
                override fun declaration(reference: DeclarationReference) {
                    val (unit, id) = resolve(this@ImportedUnit, reference)
                    val record = unit.model.declarations.declaration(id)
                    if (id in unit.lambdaNodes || unit.compounds.genericOwner(id) != null) unit.model.declarations.declaration(id)
                    else if (record !is ValueDeclaration || record.role == ValueRole.MEMBER) unit.compounds.value(id)
                    else require(unit === this@ImportedUnit) { "Frame declarations cannot cross library boundaries" }
                }
                override fun environment(reference: EnvironmentId) { files.getValue(reference) }
                override fun type(reference: TypeRef) { types.type(reference) }
                override fun constant(reference: ConstantRef) { types.constant(reference) }
                override fun annotation(reference: AnnotationId) { restoredAnnotations.annotation(reference) }
            })
            model.tables.types.indices.map { TypeRef(it + 1L) }.filterNot(::unresolved).forEach { rememberType(types.type(it)) }
            model.tables.constants.indices.map { ConstantRef(it + 1L) }.filterNot(::unresolved).forEach(types::constant)
        }

        override fun declaration(reference: DeclarationReference): StructuredNameBinding {
            val (unit, id) = resolve(this, reference)
            val record = unit.model.declarations.declaration(id)
            return when (record) {
                is MacroDeclaration -> macroBinding(listOf(unit to id))
                is FunctionDeclaration -> StructuredNameBinding.Functions(listOf(unit.callable(id)))
                is ValueDeclaration -> {
                    if (record.role == ValueRole.GLOBAL) StructuredNameBinding.Value { unit.globals.getValue(id) }
                    else if (record.role == ValueRole.MEMBER) StructuredNameBinding.Value { frame ->
                        val template = unit.compounds.owner(requireNotNull(record.common.owner))
                        val receiver = frame.scope.getVar("this") as? DataTemplateObject
                        if (frame.accessorField == record.common.name) frame.scope.getVar("field")
                        else if (receiver != null) receiver.getMemberVar(record.common.name, Member.AccessModifier.PRIVATE).first
                        else template?.scope?.getVar(record.common.name)
                    } else {
                        require(unit === this) { "Private/value declarations cannot be captured across packages" }
                        StructuredNameBinding.Frame(id, record.common.name)
                    }
                }
                is CompoundDeclaration, is EnumDeclaration, is TypeAliasDeclaration -> StructuredNameBinding.Type(unit.compounds.type(id))
                is ConstructorDeclaration, is OperatorDeclaration -> StructuredNameBinding.Functions(listOf(unit.boundValue(id) as Function))
                is PropertyDeclaration -> record.backing?.let { unit.declaration(it) }
                    ?: problem("LIB_SYMBOL_UNRESOLVED", "Property has no bound value backing: $reference")
                is EnumMemberDeclaration -> {
                    val owner = unit.compounds.value(requireNotNull(record.common.owner)) as CompilerEnum
                    val member = unit.compounds.value(id) as EnumMember
                    StructuredNameBinding.Value { top.mcfpp.core.lang.obj.EnumVar(owner, member) }
                }
                is NamespaceDeclaration -> problem("LIB_SYMBOL_UNRESOLVED", "A namespace is not an expression value: $reference")
                else -> problem("LIB_SYMBOL_UNRESOLVED", "Declaration is not bound: $reference")
            }
        }
        private fun macroBinding(candidates: List<Pair<ImportedUnit, DeclarationId>>): StructuredNameBinding.Macros =
            StructuredNameBinding.Macros(object : FrozenMacroCandidates {
                override val directory: PreparedNativeDirectory get() = requireNotNull(nativeDirectory).also { repository.requireOpen() }
                override fun bind(catalog: SourceTypeCatalog): List<MacroFunction> = candidates.flatMap { (unit, id) ->
                    directory.bindMacros(unit.nativeUnit, listOf(id), catalog)
                }
            })
        override fun name(use: NameUse, role: NameRole): ProjectedName = when (use) {
            is NameUse.Local -> ProjectedName(name(use.target), binding = declaration(DeclarationReference.Local(use.target)))
            is NameUse.Declaration -> {
                val (unit, id) = resolve(this, use.target)
                ProjectedName(unit.heads[id]?.function?.identifier ?: unit.name(id), binding = declaration(use.target))
            }
            is NameUse.Lookup -> if (role == NameRole.TYPE && use.candidates.isEmpty()) {
                val type = LibraryBuiltinTypes.restore(if (use.name == "type") "Type" else use.name)
                ProjectedName(use.name, use.namespace, StructuredNameBinding.Type(type))
            } else run {
                val references = use.candidates.map { resolve(this, it) }
                if (references.isNotEmpty() && references.all { (unit, id) -> unit.model.declarations.declaration(id) is MacroDeclaration })
                    return@run ProjectedName(use.name, use.namespace, macroBinding(references))
                val bindings = use.candidates.map(::declaration)
                if (bindings.size == 1 && bindings.single() !is StructuredNameBinding.Functions)
                    ProjectedName(use.name, use.namespace, bindings.single())
                else {
                    val candidates = bindings.flatMap { (it as? StructuredNameBinding.Functions)?.candidates
                        ?: problem("LIB_SYMBOL_UNRESOLVED", "Frozen lookup candidate is not a function") }
                    if (candidates.isEmpty()) problem("LIB_SYMBOL_UNRESOLVED", "Frozen lookup has no candidates: ${use.name}")
                    val name = candidates.map { it.identifier }.distinct().singleOrNull() ?: use.name
                    ProjectedName(name, use.namespace, StructuredNameBinding.Functions(candidates))
                }
            }
        }
        override fun local(declaration: DeclarationId, frame: Function): Var<*>? = frames[frame]?.get(declaration)
            ?: parameters[declaration]?.let { parameter -> frame.scope.getVar(parameter.identifier) }
        private fun activeFormal(declaration: DeclarationId): FunctionParam? {
            val frame = StructuredBindings.frame(Function.currFunction)
            val handle = frame.bodyHandle ?: return null
            if (handle.repository !== repository) return null
            val owner = model.syntax.bodies.getOrNull((handle.body.value - 1).toInt())?.owner ?: return null
            val record = model.declarations.declaration(owner)
            val normal = when (record) { is FunctionDeclaration -> record.normal; is ConstructorDeclaration -> record.normal; else -> return null }
            normal.indexOf(declaration).takeIf { it >= 0 }?.let { return frame.normalParams.getOrNull(it) }
            val readonly = (record as? FunctionDeclaration)?.readonly.orEmpty()
            val actual = (frame as? top.mcfpp.model.Generic<*>)?.readOnlyParams ?: (frame as? NativeFunction)?.readOnlyParams.orEmpty()
            return readonly.indexOf(declaration).takeIf { it >= 0 }?.let(actual::getOrNull)
        }
        override fun formal(declaration: DeclarationId): FunctionParam? = activeFormal(declaration) ?: parameters[declaration]
        override fun memberFunctions(candidates: List<DeclarationReference>, receiver: Var<*>, frame: Function): List<Function> {
            val staticType = (receiver as? top.mcfpp.core.lang.obj.StaticMemberView)?.declaration as? MCFPPType
            (staticType?.objectData as? DataTemplate)?.let(top.mcfpp.Project::prepareObjectInitializer)
            return frozenMembers(candidates, staticType ?: StorageAccess.actualType(receiver), frame, staticType != null)
        }
        override fun memberFunctions(candidates: List<DeclarationReference>, receiverType: MCFPPType, frame: Function): List<Function> =
            frozenMembers(candidates, receiverType, frame, false)
        private fun frozenMembers(candidates: List<DeclarationReference>, receiverType: MCFPPType, frame: Function, static: Boolean): List<Function> {
            repository.requireOpen()
            val receiverOwner = if (static) receiverType.objectData else receiverType.instanceData
            return candidates.map { reference ->
                val (unit, id) = resolve(this, reference)
                val record = unit.model.declarations.declaration(id) as? FunctionDeclaration ?: error("Frozen member candidate is not D2")
                val seen = java.util.Collections.newSetFromMap(IdentityHashMap<CompoundData, Boolean>())
                val pending = java.util.ArrayDeque<CompoundData>().apply { add(receiverOwner) }
                var materializedFunction: Function? = null
                while (pending.isNotEmpty() && materializedFunction == null) {
                    val current = pending.removeFirst()
                    if (!seen.add(current)) continue
                    materializedFunction = (current as? CompiledGenericDataTemplate)?.let { target -> unit.materialized[target]?.members?.get(id) as? Function }
                    current.parent.forEach(pending::addLast)
                }
                val bound = materializedFunction ?: unit.callable(id)
                if (bound is NativeFunction) {
                    val names = bound.javaMethod?.getAnnotation(top.mcfpp.annotations.MNIFunction::class.java)?.genericType.orEmpty()
                    val arguments = (receiverType as? MCFPPTypeWithGeneric)?.generic.orEmpty()
                    if (names.isNotEmpty() && names.size == arguments.size) bound.replaceGenericParams(names.zip(arguments).toMap()) else bound
                } else if (record.receiver != null || bound.accessModifier == Member.AccessModifier.PRIVATE || bound.owner == null) bound
                else receiverOwner.scope.getFunctionCandidates(bound.identifier, frame).singleOrNull { actual ->
                    actual.owner != null && ParameterMatcher.sameSignature(actual, bound) &&
                        (actual.owner === bound.owner || (actual.owner as? DataTemplate)?.isSubOf(bound.owner as? DataTemplate ?: return@singleOrNull false) == true)
                } ?: bound
            }.distinctBy { it.declarationId }
        }
        override fun publishLocal(declaration: DeclarationId, frame: Function, value: Var<*>) {
            val record = model.declarations.declaration(declaration) as? ValueDeclaration
                ?: problem("LIB_SYMBOL_UNRESOLVED", "Local D is not a value")
            require(record.role == ValueRole.LOCAL || record.role == ValueRole.GLOBAL)
            val stored = if (record.role == ValueRole.GLOBAL) globals.getValue(declaration).also { global ->
                val binding = StorageAccess.ensure(global)
                val symbol = global.symbol
                val prior = binding.data.declarations[binding.place]
                val constant = global.isConst
                try {
                    global.isConst = false; global.symbol = symbol?.copy(mutable = true)
                    prior?.let { binding.data.declarations[binding.place] = it.copy(mutable = true) }
                    StorageAccess.write(global, value)
                } finally {
                    global.isConst = constant; global.symbol = symbol
                    if (prior != null) binding.data.declarations[binding.place] = prior
                }
                frame.scope.putVar(record.common.name, global, true)
            } else value
            frames.getOrPut(frame) { linkedMapOf() }[declaration] = stored
        }
        override fun constant(reference: ConstantRef) = model.tables.constant(reference)
        override fun annotationProvider(reference: DeclarationReference): String {
            repository.requireOpen()
            val (unit, id) = resolve(this, reference)
            val binding = (unit.model.declarations.declaration(id) as? AnnotationProviderDeclaration)?.binding ?: error("Annotation recipe provider is not D13")
            val provider = NativeDeclarations.restoreAnnotationProvider(binding, managed)
            val alias = "__library_provider_${unit.privatePrefix}_${id.value}"
            environmentScopes.values.forEach { scope ->
                val previous = scope.annotations.putIfAbsent(alias, provider)
                require(previous == null || previous === provider)
            }
            return alias
        }
        override fun type(reference: TypeRef): Any = types.type(reference)
        override fun builtinKey(reference: TypeRef): String? = (model.tables.type(reference) as? LibraryType.Builtin)?.key
        override fun body(reference: BodyId): SemanticNode = frozenBodies.entries.firstOrNull { it.value == reference }?.let { (id, _) ->
            Expression.Literal(ScopeId(1), requireNotNull((model.declarations.declaration(id) as ValueDeclaration).frozen))
        } ?: model.syntax.node(model.syntax.body(reference).root)
        override fun name(declaration: DeclarationId) = model.declarations.declaration(declaration).common.name
        override fun local(declaration: DeclarationId): LocalDeclarationFacts {
            val record = model.declarations.declaration(declaration) as ValueDeclaration
            val statement = localSites[declaration]
            return LocalDeclarationFacts(record.common.name, when (record.storage) {
                ValueStorage.VAR -> LocalStorage.VAR; ValueStorage.CONST -> LocalStorage.CONST
                ValueStorage.EXTERNAL -> LocalStorage.EXTERNAL; ValueStorage.IMPORT_VAR -> LocalStorage.IMPORT_VAR
                else -> problem("LIB_BODY_UNSUPPORTED", "Parameter cannot be projected as a local statement")
            }, record.declaredType?.let { model.syntax.node(it) as TypeSyntax }, statement?.initializer)
        }
        override fun lambdaParameter(declaration: DeclarationId): LambdaParameterSyntax {
            val record = model.declarations.declaration(declaration) as ValueDeclaration
            require(record.role == ValueRole.LAMBDA_PARAMETER)
            return LambdaParameterSyntax(declaration, record.common.name, record.declaredType?.let { model.syntax.node(it) as TypeSyntax })
        }
        private fun typeSyntax(reference: TypeRef, environment: EnvironmentId, scope: ScopeId = ScopeId(1)): TypeSyntax = when (val type = model.tables.type(reference)) {
            is LibraryType.UnresolvedNominal -> problem("LIB_SYMBOL_UNRESOLVED", "An explicit unresolved parent has no executable type syntax")
            is LibraryType.Builtin -> if (type.key == "void") TypeSyntax.Void(scope) else TypeSyntax.Name(scope,
                NameUse.Lookup(if (type.key == "Type") "type" else type.key.removePrefix("constructor:"), null, environment, emptyList()), reference)
            is LibraryType.Declaration -> TypeSyntax.Name(scope, NameUse.Declaration(type.target), reference)
            is LibraryType.Parameter -> {
                val (unit, owner) = resolve(this, type.owner)
                if (type.role == ParameterRole.NATIVE_TYPE) TypeSyntax.Name(scope,
                    NameUse.Lookup(unit.nativeParameter(owner, type.index).typeName, null, environment, emptyList()), reference)
                else {
                val record = unit.model.declarations.declaration(owner)
                val ids = when (record) { is FunctionDeclaration -> if (type.role == ParameterRole.LAMBDA_TYPE) record.normal else record.readonly; is CompoundDeclaration -> record.readonly; else -> error("Invalid parameter owner") }
                require(unit === this) { "A foreign open type parameter requires its owner's signature context" }
                TypeSyntax.Name(scope, NameUse.Local(ids[type.index.toInt()]), reference)
                }
            }
            is LibraryType.Dependent -> model.syntax.node(type.syntax) as TypeSyntax
            is LibraryType.Specialized -> TypeSyntax.Apply(scope, typeSyntax(type.constructor, environment, scope), type.arguments.map { Expression.Literal(scope, it) }, reference)
            is LibraryType.Union -> TypeSyntax.Union(scope, type.alternatives.map { typeSyntax(it, environment, scope) })
            is LibraryType.Intersection -> TypeSyntax.Intersection(scope, type.components.map { typeSyntax(it, environment, scope) })
            is LibraryType.Function -> TypeSyntax.Function(scope, type.parameters.map { typeSyntax(it, environment, scope) }, typeSyntax(type.result, environment, scope), type.execution)
            is LibraryType.Applied -> {
                val constructor = (model.tables.type(type.constructor) as? LibraryType.Builtin)?.key?.removePrefix("constructor:")
                val kind = when (constructor) { "list" -> ContainerKind.LIST; "ImmutableList" -> ContainerKind.IMMUTABLE_LIST; "map" -> ContainerKind.MAP; "dict" -> ContainerKind.DICT; else -> null }
                if (kind != null) TypeSyntax.Container(scope, kind, typeSyntax(type.arguments.single(), environment, scope))
                else TypeSyntax.Apply(scope, typeSyntax(type.constructor, environment, scope), type.arguments.map { Expression.TypeValue(scope, typeSyntax(it, environment, scope)) }, reference)
            }
            is LibraryType.NbtRef -> TypeSyntax.Apply(scope, TypeSyntax.Name(scope, NameUse.Lookup("NBTRef", null, environment, emptyList())),
                listOf(Expression.TypeValue(scope, typeSyntax(type.value, environment, scope))), reference)
        }
        override fun templateDeclaration(declaration: DeclarationId): ProjectionTemplateDeclaration {
            val record = model.declarations.declaration(declaration)
            val access = when (record.common.access) {
                DeclarationAccess.PUBLIC -> ProjectionAccess.PUBLIC
                DeclarationAccess.PROTECTED -> ProjectionAccess.PROTECTED
                DeclarationAccess.PRIVATE -> ProjectionAccess.PRIVATE
            }
            fun boundType(reference: TypeRef): TypeSyntax {
                val scope = ScopeId(1)
                return when (val type = model.tables.type(reference)) {
                    is LibraryType.UnresolvedNominal -> problem("LIB_SYMBOL_UNRESOLVED", "An explicit unresolved parent has no executable type syntax")
                    is LibraryType.Builtin -> if (type.key == "void") TypeSyntax.Void(scope) else TypeSyntax.Name(scope,
                        NameUse.Lookup(name = if (type.key == "Type") "type" else type.key.removePrefix("constructor:"), namespace = null,
                            environment = record.common.environment, candidates = emptyList()), reference)
                    is LibraryType.Declaration -> TypeSyntax.Name(scope, NameUse.Declaration(type.target), reference)
                    is LibraryType.Parameter -> {
                        val (unit, owner) = resolve(this, type.owner)
                        if (type.role == ParameterRole.NATIVE_TYPE) TypeSyntax.Name(scope,
                            NameUse.Lookup(unit.nativeParameter(owner, type.index).typeName, null, record.common.environment, emptyList()), reference)
                        else {
                        val declaration = unit.model.declarations.declaration(owner)
                        val parameters = when (declaration) { is FunctionDeclaration -> if (type.role == ParameterRole.LAMBDA_TYPE) declaration.normal else declaration.readonly; is CompoundDeclaration -> declaration.readonly; else -> error("Parameter owner is not generic") }
                        require(unit === this) { "An open foreign parameter needs its owner's type context" }
                        TypeSyntax.Name(scope, NameUse.Local(parameters[type.index.toInt()]), reference)
                        }
                    }
                    is LibraryType.Dependent -> model.syntax.node(type.syntax) as TypeSyntax
                    is LibraryType.Specialized -> TypeSyntax.Apply(scope, boundType(type.constructor), type.arguments.map { Expression.Literal(scope, it) }, reference)
                    is LibraryType.Union -> TypeSyntax.Union(scope, type.alternatives.map(::boundType))
                    is LibraryType.Intersection -> TypeSyntax.Intersection(scope, type.components.map(::boundType))
                    is LibraryType.Function -> TypeSyntax.Function(scope, type.parameters.map(::boundType), boundType(type.result), type.execution)
                    is LibraryType.Applied -> {
                        val constructor = model.tables.type(type.constructor) as? LibraryType.Builtin
                        val kind = when (constructor?.key?.removePrefix("constructor:")) {
                            "list" -> ContainerKind.LIST; "ImmutableList" -> ContainerKind.IMMUTABLE_LIST
                            "map" -> ContainerKind.MAP; "dict" -> ContainerKind.DICT; else -> null
                        }
                        if (kind != null) TypeSyntax.Container(scope, kind, boundType(type.arguments.single()))
                        else TypeSyntax.Apply(scope, boundType(type.constructor), type.arguments.map { Expression.TypeValue(scope, boundType(it)) }, reference)
                    }
                    is LibraryType.NbtRef -> TypeSyntax.Apply(scope,
                        TypeSyntax.Name(scope, NameUse.Lookup(name = "NBTRef", namespace = null,
                            environment = record.common.environment, candidates = emptyList())),
                        listOf(Expression.TypeValue(scope, boundType(type.value))), reference)
                }
            }
            fun declaredType(reference: NodeRef?, bound: TypeRef?): TypeSyntax = reference?.let { model.syntax.node(it) as TypeSyntax }
                ?: bound?.let(::boundType) ?: TypeSyntax.Void(ScopeId(1))
            fun parameter(id: DeclarationId): ProjectionParameter {
                val value = model.declarations.declaration(id) as ValueDeclaration
                return ProjectionParameter(id, value.common.name, declaredType(value.declaredType, value.type), value.defaultBody ?: frozenBodies[id], when (value.variance) {
                    Variance.IN -> ProjectionVariance.IN; Variance.OUT -> ProjectionVariance.OUT; Variance.INVARIANT -> ProjectionVariance.INVARIANT
                })
            }
            fun accessor(value: LibraryAccessor): ProjectionAccessor? = when (value) {
                LibraryAccessor.Absent -> null; LibraryAccessor.Simple -> ProjectionAccessor.Simple
                is LibraryAccessor.Function -> {
                    val (unit, id) = resolve(this, value.target)
                    require(unit === this) { "Cross-library accessor projection requires the complete importer" }
                    ProjectionAccessor.Block(id, requireNotNull((model.declarations.declaration(id) as FunctionDeclaration).body))
                }
                is LibraryAccessor.Native -> {
                    val (unit, id) = resolve(this, value.target)
                    require(unit === this)
                    val binding = requireNotNull((model.declarations.declaration(id) as FunctionDeclaration).native)
                    ProjectionAccessor.Native(id, binding.ownerClass.split('.') + binding.method)
                }
            }
            return when (record) {
                is ValueDeclaration -> {
                    val property = propertiesByBacking[declaration]
                    ProjectionTemplateDeclaration.Field(record.common.name, record.storage == ValueStorage.CONST,
                        record.declaredType?.let { declaredType(it, record.type) }, record.common.flags and DeclarationFlags.NULLABLE != 0L,
                        record.defaultBody ?: frozenBodies[declaration], property?.getter?.let(::accessor), property?.setter?.let(::accessor), access)
                }
                is FunctionDeclaration -> ProjectionTemplateDeclaration.Function(record.common.name, record.readonly.map(::parameter), record.normal.map(::parameter),
                    declaredType(null, record.result), record.body, record.common.flags and DeclarationFlags.OVERRIDE != 0L,
                    record.common.flags and DeclarationFlags.ABSTRACT != 0L, access)
                is ConstructorDeclaration -> ProjectionTemplateDeclaration.Constructor(record.normal.map(::parameter), requireNotNull(record.body), access)
                is OperatorDeclaration -> {
                    val (unit, id) = resolve(this, record.implementation)
                    require(unit === this)
                    val implementation = model.declarations.declaration(id) as FunctionDeclaration
                    ProjectionTemplateDeclaration.Operation(record.operator, implementation.readonly.map(::parameter), implementation.normal.map(::parameter),
                        declaredType(null, implementation.result), implementation.body,
                        implementation.native?.let { it.ownerClass.split('.') + it.method }, access)
                }
                else -> problem("LIB_BODY_UNSUPPORTED", "D$declaration is not a source template member")
            }
        }
        private fun guardedSignatures(provider: StructuredFunctionSignatures): StructuredFunctionSignatures = object : StructuredFunctionSignatures {
            override fun declaredType(type: MCFPPType, function: Function, readonly: Map<String, ClosedReadonlyBinding>): MCFPPType? {
                repository.requireOpen()
                return provider.declaredType(type, function, readonly)
            }
        }
        private fun applyMetadata(record: LibraryDeclaration, value: Any) {
            val common = record.common
            val access = when (common.access) { DeclarationAccess.PUBLIC -> Member.AccessModifier.PUBLIC; DeclarationAccess.PROTECTED -> Member.AccessModifier.PROTECTED; DeclarationAccess.PRIVATE -> Member.AccessModifier.PRIVATE }
            when (value) {
                is Function -> { value.accessModifier = access; value.isFinal = common.flags and DeclarationFlags.FINAL != 0L
                    value.structuredSignatureBindings = guardedSignatures(this@ImportedUnit)
                    value.isAbstract = common.flags and DeclarationFlags.ABSTRACT != 0L; value.isOverride = common.flags and DeclarationFlags.OVERRIDE != 0L
                    if (value is NativeFunction && record is FunctionDeclaration && record.execution == FunctionExecutionKind.NATIVE) {
                        value.librarySessionGuard = repository::requireOpen
                        value.returnsConstWhenArgsConst = common.flags and DeclarationFlags.CONST != 0L
                    } }
                is Var<*> -> { value.accessModifier = access; value.isFinal = common.flags and DeclarationFlags.FINAL != 0L
                    value.isStatic = common.flags and DeclarationFlags.STATIC != 0L; value.isConst = common.flags and DeclarationFlags.CONST != 0L
                    value.nullable = common.flags and DeclarationFlags.NULLABLE != 0L
                    if (common.flags and DeclarationFlags.FORCE_RUNTIME != 0L) value.bindDeclaration(forceRuntime = true)
                    if (value is top.mcfpp.core.lang.MCNumber<*>) value.isDataOnly = common.flags and DeclarationFlags.DATA_ONLY != 0L
                    if (value is top.mcfpp.core.lang.OnScoreboard) value.isDataOnly = common.flags and DeclarationFlags.DATA_ONLY != 0L }
            }
        }
        fun clear() {
            if (::annotationCloner.isInitialized) annotationCloner.close()
            compounds.boundValues().values.filterIsInstance<GenericDataTemplate>().forEach { it.clearStructuredMaterializations() }
            materialized.clear(); constantFunctions.clear(); functionConstantCache.clear(); pendingAnnotations.clear()
            annotationPhases.clear(); annotationIds.clear(); parameterViews.clear(); nativeParameterViews.clear(); dependentTypes.clear(); openSyntaxTypes.clear()
            unresolvedTypeClosure.clear(); unresolvedConstantClosure.clear()
            structuralIntersections.clear()
            frames.clear(); heads.clear(); parameters.clear(); globals.clear(); files.clear(); environmentScopes.clear()
        }
    }

    private fun problem(code: String, message: String): Nothing = throw LibraryImportException(code, message)
    private inline fun <T> guarded(action: () -> T): T = try { action() } catch (failure: Exception) {
        if (state != LibraryImportState.COMMITTED && state != LibraryImportState.CLOSED) {
            state = LibraryImportState.FAILED
            repository.close()
            functionTargets.close()
            nativeDirectory?.close(); nativeDirectory = null
        }
        throw failure
    }

    /** Releases imported registrations, body data, provider services, and annotation codecs. Repeated calls keep the session closed. */
    override fun close() {
        if (state == LibraryImportState.CLOSED) return
        val imported = ownedFunctions()
        val owned = java.util.Collections.newSetFromMap(IdentityHashMap<Function, Boolean>()).apply { addAll(imported) }
        val templates = java.util.Collections.newSetFromMap(IdentityHashMap<DataTemplate, Boolean>()).apply { units.values.forEach { addAll(it.ownedTemplates()) } }
        top.mcfpp.Project.journalState().let { project ->
            project.anonymous.removeAll { it in registeredAnonymous }
            project.pending.removeAll { it in templates }
            val identities = templates.map { it.inheritanceIdentity }.toSet()
            project.boundParents.removeAll(identities); project.invalidHeaders.removeAll(identities)
        }
        registeredAnonymous.clear()
        units.values.flatMap { it.extensions() }.forEach { function -> function.owner?.scope?.functions?.entries?.removeIf { entry ->
            entry.value.removeAll { it === function }; entry.value.isEmpty()
        } }
        helperLease?.close(); helperLease = null
        GlobalScope.functionTags.values.forEach { tag -> tag.functions.removeAll { it in owned } }
        GlobalScope.localNamespaces.values.forEach { namespace -> namespace.scope.functions.entries.removeIf { entry ->
            entry.value.removeAll { it in owned }; entry.value.isEmpty()
        } }
        published.forEach { (name, namespace) ->
            val registry = publicationRegistries.getValue(name)
            if (registry[name] === namespace) {
                namespace.scope.functions.entries.removeIf { entry -> entry.value.removeAll(owned); entry.value.isEmpty() }
                val incoming = namespaces.getValue(name)
                namespace.scope.template.entries.removeIf { entry -> incoming.scope.template[entry.key] === entry.value }
                namespace.scope.objects.removeAll { value -> incoming.scope.objects.any { it === value } }
                namespace.scope.enums.removeAll { value -> incoming.scope.enums.any { it === value } }
                namespace.scope.journalTypeAliases.entries.removeIf { entry -> incoming.scope.journalTypeAliases[entry.key] === entry.value }
                namespace.scope.vars.entries.removeIf { entry -> incoming.scope.vars[entry.key] === entry.value }
                namespace.scope.annotations.entries.removeIf { entry -> incoming.scope.annotations[entry.key] === entry.value &&
                    publicationBefore[name]?.annotations?.get(entry.key) !== entry.value }
                if (namespace === incoming && namespace.scope.functions.isEmpty() && namespace.scope.template.isEmpty() &&
                    namespace.scope.objects.isEmpty() && namespace.scope.enums.isEmpty() && namespace.scope.journalTypeAliases.isEmpty() && namespace.scope.vars.isEmpty() && namespace.scope.annotations.isEmpty()) registry.remove(name)
            }
        }
        restoreScoreboards()
        repository.close()
        functionTargets.close()
        nativeDirectory?.close(); nativeDirectory = null
        units.values.forEach(ImportedUnit::clear)
        units.clear(); namespaces.clear(); published.clear()
        publicationBefore.clear()
        publicationRegistries.clear()
        identities.clear()
        registeredTypes.clear(); nominalViews.clear()
        codecs.close(); sourceProviders.clear()
        state = LibraryImportState.CLOSED
    }
    private fun restoreScoreboards() {
        previousScoreboards.forEach { (name, previous) ->
            if (units.values.any { unit -> unit.enums().any { it.namespaceID == name && GlobalScope.scoreboards[name] === it.sbObject } }) {
                if (previous == null) GlobalScope.scoreboards.remove(name) else GlobalScope.scoreboards[name] = previous
            }
        }
        previousScoreboards.clear()
    }
}
