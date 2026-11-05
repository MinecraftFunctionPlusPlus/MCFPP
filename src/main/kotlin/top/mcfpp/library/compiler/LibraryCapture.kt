package top.mcfpp.library.compiler

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.api.SourceCompoundIndex
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Generic
import top.mcfpp.model.Member
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum as CompilerEnum
import top.mcfpp.core.lang.Var
import top.mcfpp.model.property.Property
import top.mcfpp.type.*
import java.util.IdentityHashMap
import top.mcfpp.model.annotation.Annotation
import java.lang.reflect.Method
import java.security.MessageDigest

/** Source arenas retain declaration-time syntax; author bindings never depend on a consumer's imports. */
internal class LibraryCapture(private val selection: OwnedLibrarySelection) : CompoundCaptureBindings {
    private val compounds = SourceCompoundIndex(selection)
    private val objects = IdentityHashMap<Any, DeclarationId>()
    private val builtinValueUses = IdentityHashMap<NameUse, TypeRef>()
    private val objectById = HashMap<DeclarationId, Any>()
    private val functions = IdentityHashMap<Function, DeclarationId>()
    private val foreignFunctions = LinkedHashMap<DeclarationReference, Function>()
    private val environments = ArrayList<EnvironmentRecord>()
    private val environmentIds = IdentityHashMap<MCFPPFile, EnvironmentId>()
    private val metadataEnvironmentIds = linkedMapOf<String, EnvironmentId>()
    private val namespaces = LinkedHashMap<String, DeclarationId>()
    private val declarations = ArrayList<LibraryDeclaration?>()
    private val scopes = ArrayList<ScopeRecord>()
    private data class Frame(val id: ScopeId, val owner: DeclarationId, val names: LinkedHashMap<String, DeclarationId> = linkedMapOf())
    private val frames = ArrayList<Frame>()
    private val bodySources = ArrayList<SyntaxBodySource?>()
    private val roots = ArrayList<SyntaxRoot>()
    private val storedNodes = LinkedHashMap<NodeRef, TypeSyntax>()
    private val storedNodeReferences = IdentityHashMap<TypeSyntax, NodeRef>()
    private val rootCounts = HashMap<DeclarationId, Long>()
    private val types = ArrayList<LibraryType>()
    private val constants = ArrayList<ConstantValue>()
    private val typeIds = HashMap<TypeId, TypeRef>()
    private val pendingTypeIds = HashMap<TypeId, TypeRef>()
    private val actualTypes = HashMap<TypeRef, MCFPPType>()
    private val valueTypes = HashMap<DeclarationId, MCFPPType>()
    private val sourceLocals = HashMap<DeclarationId, Parser.FieldDeclarationContext>()
    private val parameterIds = HashMap<Triple<DeclarationId, Boolean, Int>, DeclarationId>()
    private val capturedFunctions = IdentityHashMap<Function, Any>()
    private val localOrdinals = HashMap<DeclarationId, Long>()
    private val privateOrdinals = HashMap<DeclarationId, Long>()
    private data class ReadonlySlot(val owner: DeclarationId, val index: Int)
    private data class NativeSlot(val owner: DeclarationId, val index: Int)
    private val readonlySlots = HashMap<DeclarationId, ReadonlySlot>()
    private val parameterTypes = HashMap<TypeId, ReadonlySlot>()
    private var signatureParameters: Map<TypeId, KeyType.Parameter> = emptyMap()
    private val nativeTypes = HashMap<TypeId, NativeSlot>()
    private val builtinTargets = linkedMapOf<String, NativeFunction>()
    private val builtinNames = IdentityHashMap<Function, String>()
    private val builtinIdentityKeys = IdentityHashMap<Function, String>()
    private val builtinTargetFunctions = java.util.Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
    private val receiverDeclarations = linkedMapOf<DeclarationId, DeclarationId>()
    private class CapturedSourceType(val syntax: TypeSyntax, val owner: DeclarationId, val environment: EnvironmentId) : MCFPPType()
    private val capturedSourceTypes = HashMap<TypeId, CapturedSourceType>()
    private val inferredSourceTypes = IdentityHashMap<MCFPPType, CapturedSourceType>()
    private data class LambdaHead(val owner: DeclarationId, val environment: EnvironmentId)
    private val lambdaHeads = HashMap<DeclarationId, LambdaHead>()
    private val inferring = java.util.Collections.newSetFromMap(IdentityHashMap<MCFPPType, Boolean>())
    private var allFunctions = emptyList<Function>()
    private val annotationDeclarationRoots = arrayListOf<Any>()
    private val globalSites = IdentityHashMap<Parser.FieldDeclarationContext, DeclarationId>()
    private val globalVariables = IdentityHashMap<Var<*>, DeclarationId>()
    private val receivers = IdentityHashMap<Function, MCFPPType>()
    private var ownedCodecs: PortableAnnotationCodecs? = null
    private val annotationCodecs by lazy { selection.services?.annotations ?: PortableAnnotationCodecs.builtins().also { ownedCodecs = it } }
    private val services by lazy { selection.services ?: ExportSourceServices.create(selection.copy(functions = allFunctions), codecs = annotationCodecs) }
    private val providerIds = linkedMapOf<AnnotationProviderBinding, DeclarationId>()
    private val runtimeRecords = arrayListOf<RuntimeRecord>()
    private lateinit var referenceProjection: ReferenceConstantProjection
    private val metadata by lazy { ExportMetadataCapture(object : ExportMetadataBindings {
        override val types = object : CompilerTypeCapture {
            override fun type(value: MCFPPType) = this@LibraryCapture.type(value, currentOwner, null)
            override fun identity(id: TypeId) = typeIdentity(id)
            override fun constant(value: CompilerValue) = this@LibraryCapture.constant(value, emptyMap())
            override fun snapshot() = LibraryTables(this@LibraryCapture.types, constants)
        }
        override val identities get() = canonicalIdentities()
        override val managed get() = services.native
        override val annotations get() = annotationCodecs
        override fun sourceProvider(ownerClass: String) = services.sourceProvider(ownerClass)
        override fun sourceType(namespace: String, expression: String) = services.sourceType(namespace, expression)
        override fun provider(binding: AnnotationProviderBinding) = providerReference(binding)
        override fun declaration(value: Any) = reference(value)
        override fun phase(value: Annotation) = compounds.phase(value)
    }) }
    private var currentCompound: DataTemplate? = null
    private lateinit var current: Function
    private var currentOwner = DeclarationId(1)
    private var currentEnvironment = EnvironmentId(1)
    private val namespaceNames get() = namespaces.keys
    internal fun releaseSourceAdapters() { compounds.close(); ownedCodecs?.close() }

    fun capture(): LibraryModel {
        compounds.collect()
        require(selection.functions.isNotEmpty() || compounds.entries.isNotEmpty() || selection.macros.isNotEmpty() || selection.helpers.isNotEmpty() ||
            selection.providers.isNotEmpty() || selection.resources.isNotEmpty() || selection.ownedSources.isNotEmpty()) { "Select source declarations or owned resources" }
        var owned = discover()
        allFunctions = owned
        require(selection.helpers.none { selection.ownership.isDependencyDeclaration(it.recipe) }) { "A dependency helper must remain owned by its original library" }
        referenceProjection = ReferenceConstantProjection(selection.helpers.map { it.recipe }, ::keyType) { id ->
            (compounds.types[id] as? UnionDataTemplate)?.parent?.filterIsInstance<DataTemplate>()?.map { it.inheritanceIdentity }
        }
        discoverNativeTypes()
        do {
            val declarationsBefore = compounds.entries.size
            val rootsBefore = annotationDeclarationRoots.size
            val functionsBefore = owned
            discoverAnnotationReferences(owned)
            owned = discover()
            val changed = declarationsBefore != compounds.entries.size || rootsBefore != annotationDeclarationRoots.size ||
                functionsBefore.size != owned.size || owned.any { function -> functionsBefore.none { it === function } }
        } while (changed)
        allFunctions = owned
        val files = identityDistinct(owned.mapNotNull(::sourceFile) + compounds.entries.mapNotNull { it.source } + selection.macros.mapNotNull { it.source } + selection.helpers.mapNotNull { it.source } +
            selection.providers.mapNotNull { it.source }).ifEmpty { selection.ownedSources }
        val records = files.map { it to environmentRecord(it, compounds.unit(it)) }
        val metadataNamespaces = (owned.filter { sourceFile(it) == null }.map(::functionNamespace) +
            compounds.entries.filter { it.source == null || it.value is UnionDataTemplate }.map { it.namespace } + selection.macros.filter { it.source == null }.map { it.namespace } +
            selection.helpers.filter { it.source == null }.map { it.recipe.namespace } + selection.providers.filter { it.source == null }.map { it.namespace }).distinct()
        val metadataRecords = metadataNamespaces.associateWith { EnvironmentRecord(it, emptyList()) }
        val orderedEnvironments = (records.map { it.second } + metadataRecords.values).distinct().sortedWith { a, b -> compareBytes(environmentKey(a), environmentKey(b)) }
        environments.addAll(orderedEnvironments)
        records.forEach { (file, record) -> environmentIds[file] = EnvironmentId(orderedEnvironments.indexOf(record) + 1L) }
        metadataRecords.forEach { (namespace, record) -> metadataEnvironmentIds[namespace] = EnvironmentId(orderedEnvironments.indexOf(record) + 1L) }
        data class PublicHead(val key: DeclarationKey, val value: Any? = null)
        val names = (owned.map { functionNamespace(it) } + compounds.entries.map { it.namespace } + selection.macros.map { it.namespace } + selection.helpers.map { it.recipe.namespace } +
            selection.providers.map { it.namespace }).distinct().ifEmpty { files.map { it.namespace.identifier }.distinct() }
        val publicHeads = names.map {
            PublicHead(DeclarationKey(it, emptyList(), 1, it))
        } + owned.filter { compounds.entry(it) == null && it.accessModifier != Member.AccessModifier.PRIVATE }.map { PublicHead(functionKey(it), it) } +
            compounds.entries.filter { it.public }.map { PublicHead(entryKey(it), it.value) }
        require(publicHeads.map { TypeTableCodec.declarationKeyBytes(it.key).toList() }.distinct().size == publicHeads.size) { "Conflicting public declaration keys" }
        publicHeads.sortedWith { a, b -> compareBytes(TypeTableCodec.declarationKeyBytes(a.key), TypeTableCodec.declarationKeyBytes(b.key)) }
            .forEach { head ->
                val id = reserve()
                if (head.value == null) namespaces[head.key.namespace] = id else register(head.value, id)
            }
        owned.filter { compounds.entry(it) == null && it.accessModifier == Member.AccessModifier.PRIVATE }.sortedWith { a, b ->
            val owner = namespaces.getValue(a.namespace).value.compareTo(namespaces.getValue(b.namespace).value)
            if (owner != 0) owner else compareBytes(TypeTableCodec.declarationKeyBytes(functionKey(a)), TypeTableCodec.declarationKeyBytes(functionKey(b)))
        }.forEach { register(it, reserve()) }
        val pending = compounds.entries.filter { !it.public }.toMutableList()
        while (pending.isNotEmpty()) {
            val eligible = pending.filter { it.owner == null || objects.containsKey(it.owner) }.sortedWith { a, b -> compareBytes(
                TypeTableCodec.declarationKeyBytes(entryKey(a)), TypeTableCodec.declarationKeyBytes(entryKey(b))) }
            require(eligible.isNotEmpty()) { "Private declaration owner cycle" }
            eligible.forEach { register(it.value, reserve()); pending.remove(it) }
        }
        selection.initializers.forEach { function ->
            val source = file(function)
            compounds.unit(source).topStatement()?.statement().orEmpty().mapNotNull { it.fieldDeclaration() }.forEach { declaration ->
                val value = source.field.vars[declaration.Identifier().text] ?: function.scope.vars[declaration.Identifier().text]
                    ?: unsupported("A top-level source variable has no prepared author model")
                val id = reserve(); register(value, id); globalSites[declaration] = id; globalVariables[value] = id
                valueTypes[id] = value.type
            }
        }
        owned.forEach { function ->
            val owner = functions.getValue(function)
            readonly(function).forEachIndexed { index, parameter -> reserveParameter(owner, parameter, true, index) }
            function.normalParams.forEachIndexed { index, parameter -> reserveParameter(owner, parameter, false, index) }
        }
        compounds.templates.filterIsInstance<GenericDataTemplate>().forEach { template ->
            val owner = objects.getValue(template)
            template.readOnlyParams.forEachIndexed { index, parameter ->
                val id = id(parameter, owner); val slot = ReadonlySlot(owner, index); readonlySlots[id] = slot
                template.scope.getType(parameter.identifier)?.let { parameterTypes[it.typeId] = slot }
                valueTypes[id] = parameter.type ?: MCFPPConcreteType.Type
            }
        }
        owned.filterIsInstance<NativeFunction>().forEach { registerNativeTypes(it, functions.getValue(it)) }
        captureProviders()
        captureMacros()
        val sourceAnnotations = compounds.annotations() // Also marks source-deferred roots before the A phase callback runs.
        metadata.captureAnnotations(identityDistinct(objects.entries.sortedBy { it.value.value }.flatMap { entry -> when (val value = entry.key) {
            is DataTemplate -> value.annotations + value.pendingDeclarationAnnotations + value.pendingFieldAnnotations.entries
                .sortedWith { left, right -> compareUtf8(left.key, right.key) }.flatMap { it.value }
            is Function -> value.annotations
            is Var<*> -> value.annotations
            is CompilerEnum -> value.annotations
            else -> emptyList()
        } } + sourceAnnotations))
        val orderedFunctions = owned.sortedBy { functions.getValue(it).value }
        orderedFunctions.filter { compounds.entry(it) == null }.forEach { function ->
            val result = FunctionDeclarations.capture(function, functions.getValue(function), this)
            put(result.id, result.declaration)
            result.parameters.forEach { (id, value) -> put(id, value) }
        }
        compounds.templates.sortedBy { objects.getValue(it).value }.forEach { template ->
            val captured = CompoundDeclarations.capture(template, objects.getValue(template), this)
            captured.declarations.forEach { (id, record) -> put(id, record) }
        }
        compounds.enumerations.sortedBy { objects.getValue(it).value }.forEach { value -> CompoundDeclarations.captureEnum(value, objects.getValue(value), this).forEach { (id, record) -> put(id, record) } }
        compounds.aliases.sortedBy { objects.getValue(it.second).value }.forEach { (alias, value) ->
            inAlias(value) { put(objects.getValue(value), CompoundDeclarations.captureAlias(value, alias.name, objects.getValue(value), environmentIds.getValue(alias.source), this)) }
        }
        captureMetadata()
        captureRuntime()
        namespaces.forEach { (namespace, id) ->
            val source = files.firstOrNull { it.namespace.identifier == namespace }
                ?: selection.macros.firstOrNull { it.namespace == namespace }?.source
                ?: selection.providers.firstOrNull { it.namespace == namespace }?.source
                ?: selection.helpers.firstOrNull { it.recipe.namespace == namespace }?.source
            val environment = environment(source, namespace)
            val members = orderedFunctions.filter { compounds.entry(it) == null && functionNamespace(it) == namespace }.map { functions.getValue(it) } +
                compounds.entries.filter { it.owner == null && it.namespace == namespace && it.kind in setOf(3, 8, 10) }.map { objects.getValue(it.value) }
            put(id, NamespaceDeclaration(DeclCommon(DeclarationKey(namespace, emptyList(), 1, namespace), null,
                namespace, null, environment, DeclarationAccess.PUBLIC, 0),
                (members + declarations.mapIndexedNotNull { index, record -> DeclarationId(index + 1L).takeIf { record?.common?.owner == id } }).distinct()))
        }
        val tableSnapshot = LibraryTables(types, constants)
        val canonical = TypeTableCodec.canonicalize(tableSnapshot, canonicalIdentities())
        val d = declarationOrder()
        val qOrder = scopes.indices.sortedWith(compareBy<Int> { d.getValue(scopes[it].owner).value }.thenBy { it })
        val q = qOrder.mapIndexed { index, old -> ScopeId(old + 1L) to ScopeId(index + 1L) }.toMap()
        val bOrder = bodySources.indices.sortedWith(compareBy<Int> { d.getValue(requireNotNull(bodySources[it]).owner).value }.thenBy { requireNotNull(bodySources[it]).role.wireValue })
        val b = bOrder.mapIndexed { index, old -> BodyId(old + 1L) to BodyId(index + 1L) }.toMap()
        val remap = ExportNodeRemapper(canonical, d, q, b, typeValues = builtinValueUses)
        val syntax = SyntaxCodec.flatten(qOrder.map { index -> scopes[index].let {
            it.copy(owner = remap.declaration(it.owner), parent = it.parent?.let(remap::scope), bindings = it.bindings.map(remap::declaration))
        } }, bOrder.map { index -> requireNotNull(bodySources[index]) { "Incomplete lambda B shell" }.let {
            it.copy(owner = remap.declaration(it.owner), captures = it.captures.map(remap::reference), root = remap.node(it.root))
        } }, roots.map { it.copy(owner = remap.declaration(it.owner), node = remap.node(it.node)) })
        val nodeRefs = storedNodes.mapValues { syntax.reference(remap.node(it.value)) }
        val finalDeclarations = declarations.indices.sortedBy { d.getValue(DeclarationId(it + 1L)).value }
            .map { rewriteDeclaration(requireNotNull(declarations[it]) { "Incomplete D shell" }, remap, nodeRefs) }
        val finalTables = remap.tables(canonical.tables, nodeRefs)
        val captured = metadata.capturedAnnotations
        val model = LibraryModel(LibraryManifest(selection.identity.library, selection.identity.classifier, selection.target,
            selection.dependencies, environments.indices.map { EnvironmentId(it + 1L) }, namespaces.values.map(remap::declaration).sortedBy { it.value }),
            LibraryDeclarations(finalDeclarations, environments), finalTables, syntax,
            captured.annotations.map(remap::annotation), captured.stateCells.map(remap::stateCell), runtimeRecords.map(remap::runtime),
            selection.resources.map { resource -> LibraryResource(resource.path, resource.bytes.size.toULong(),
                MessageDigest.getInstance("SHA-256").digest(resource.bytes).toList(), resource.owner?.let { remap.reference(reference(it)) }, resource.module,
                resource.activation, resource.origin) })
        val result = ExportDependencyClosure.trim(model)
        LibraryModelCodec(listOf(CompoundCodec, NativeCodec)).validate(result)
        return result
    }
    private fun canonicalIdentities(): CanonicalIdentityBindings = object : CanonicalIdentityBindings {
            override fun localDeclaration(reference: DeclarationId): ByteArray = stableDeclaration(reference)
            override fun node(reference: NodeRef): ByteArray {
                val root = roots.single { it.node === storedNodes.getValue(reference) }
                return LibraryBinaryWriter().apply { bytes(stableDeclaration(root.owner)); list(root.path) { u32(it) } }.toByteArray()
            }
            override fun environment(reference: EnvironmentId): ByteArray = environmentKey(environments[(reference.value - 1).toInt()])
            override fun dependency(index: Long): ByteArray {
                require(index in 1..selection.dependencies.size.toLong())
                val dependency = selection.dependencies[(index - 1).toInt()]
                return LibraryBinaryWriter().apply {
                    TypeTableCodec.writePackage(this, dependency.library); string(dependency.classifier)
                }.toByteArray()
            }
    }

    private fun reserve(): DeclarationId = DeclarationId(declarations.size + 1L).also { declarations.add(null) }
    private fun register(value: Any, id: DeclarationId) {
        objects[value] = id; objectById[id] = value
        if (value is Function) functions[value] = id
    }
    private fun put(id: DeclarationId, value: LibraryDeclaration) { declarations[(id.value - 1).toInt()] = value }
    private fun unsupported(message: String): Nothing = throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", message)
    private fun sourceFile(function: Function): MCFPPFile? {
        compounds.entry(function)?.let { return it.source }
        return function.declarationFile ?: selection.ownedSources.firstOrNull { it.topFunction === function }
    }
    private fun file(function: Function): MCFPPFile = sourceFile(function)
        ?: unsupported("An executable source operation requires the actual declaration file for ${function.identifier}")
    private fun environment(source: MCFPPFile?, namespace: String): EnvironmentId = source?.let(environmentIds::getValue)
        ?: metadataEnvironmentIds.getValue(namespace)
    private fun functionNamespace(function: Function): String = if (selection.initializers.any { it === function }) file(function).namespace.identifier else function.namespace
    private fun owned(function: Function): Boolean = sourceFile(function)?.let { source -> selection.ownedSources.any { it === source } }
        ?: (!selection.ownership.isDependencyDeclaration(function) && (compounds.entry(function) != null || selection.functions.any { it === function }))

    private fun ordinary(function: Function) {
        val entry = compounds.entry(function)
        if (!owned(function) || (function.ast == null && function !is NativeFunction && compounds.accessorExpression(function) == null && entry?.kind != 4 && !function.isAbstract && selection.initializers.none { it === function }) || function.actualCallBody || (function.owner != null && entry == null && function !is ExtensionFunction))
            unsupported("${function.namespace}:${function.identifier} requires composite/native/annotation/runtime export support or is not an owned source body")
        require(source(function) != null || entry != null || function is NativeFunction || function.isAbstract || selection.initializers.any { it === function }) { "Source signature is missing for ${function.identifier}" }
        functionKey(function)
    }

    private fun discover(): List<Function> {
        val seen = IdentityHashMap<Function, Boolean>()
        val initialized = IdentityHashMap<DataTemplate, Boolean>()
        val scheduled = IdentityHashMap<Function, Boolean>()
        val pending = ArrayDeque<Function>()
        fun enqueue(input: Function) { val function = compounds.entry(input)?.value as? Function ?: input; if (scheduled.put(function, true) == null) {
            val signature = source(function)
            readonly(function).forEachIndexed { index, parameter -> compounds.include(parameter.type, signature?.parameters?.readOnlyParams()?.readOnlyParameterList()?.readOnlyParameter()?.getOrNull(index)?.type()) }
            function.normalParams.forEachIndexed { index, parameter -> compounds.include(parameter.type, signature?.parameters?.normalParams()?.parameterList()?.parameter()?.getOrNull(index)?.type()) }
            compounds.include(function.returnType, signature?.result?.type())
            receiver(function)?.let(compounds::include)
            ordinary(function); pending.add(function)
        } }
        (selection.functions + annotationDeclarationRoots.filterIsInstance<Function>() + compounds.entries.mapNotNull { it.value as? Function }).forEach(::enqueue)
        fun walk(tree: ParseTree, function: Function) {
                if (tree is Parser.FieldDeclarationContext && selection.initializers.any { it === function }) {
                    val value = file(function).field.vars[tree.Identifier().text] ?: function.scope.vars[tree.Identifier().text]
                    if (value != null) compounds.include(value.type, tree.type())
                }
                if (tree is Parser.AnonymousTemplateTypeContext) compounds.anonymous(tree, file(function),
                    if (function.identifier == "__initializer_capture__") function.owner else function)
                if (tree is Parser.TypeContext) {
                    val type = compounds.sourceType(tree) ?: if (dependsOnReadonly(tree, function) || tree.typeBody().anonymousTemplateType() != null) null else file(function).withDeclarationContext {
                        MCFPPType.parseFromContext(tree, function.scope, function)
                    }
                    type?.let(compounds::include)
                }
                if (tree is Parser.FunctionCallContext) {
                    val id = tree.namespaceID()
                    val available = candidates(function, id.Identifier().text, id.namespacePath()?.text)
                    if (available.isEmpty()) declaredType(function, id.Identifier().text, id.namespacePath()?.text)?.let(compounds::include)
                    available.forEach { candidate ->
                        if (owned(candidate)) enqueue(candidate)
                        else if (candidate !is NativeFunction || !coreNative(candidate)) foreign(candidate)
                    }
                }
                for (index in 0 until tree.childCount) walk(tree.getChild(index), function)
        }
        while (pending.isNotEmpty() || compounds.templates.any { !initialized.containsKey(it) }) {
            compounds.templates.filter { initialized.put(it, true) == null }.toList().forEach { template ->
                val context = Function("__initializer_capture__", template, null).apply { declarationFile = template.declarationFile }
                template.preInit.values.forEach { walk(it, context) }
                val body = when (val source = declarationContext(template)) {
                    is Parser.TemplateDeclarationContext -> source.templateBody()
                    is Parser.ObjectTemplateDeclarationContext -> source.templateBody()
                    is Parser.AnonymousTemplateTypeContext -> source.templateBody()
                    else -> null
                }
                body?.templateMemberDeclaration()?.mapNotNull { it.templateMember().templateFieldDeclaration()?.templateType() }?.forEach { walk(it, context) }
            }
            compounds.entries.mapNotNull { it.value as? Function }.forEach(::enqueue)
            if (pending.isEmpty()) continue
            val function = pending.removeFirst()
            if (seen.put(function, true) != null) continue
            ordinary(function)
            function.ast?.let { walk(it, function) }
            if (selection.initializers.any { it === function }) compounds.unit(file(function)).topStatement()?.let { walk(it, function) }
            compounds.accessorExpression(function)?.let { walk(it.expression, function) }
            (readonly(function) + function.normalParams).mapNotNull { it.defaultContext }.forEach { walk(it, function) }
            source(function)?.let { walk(it.parameters, function); it.result?.let { result -> walk(result, function) } }
        }
        return seen.keys.toList().sortedWith { a, b -> compareBytes(TypeTableCodec.declarationKeyBytes(functionKey(a)), TypeTableCodec.declarationKeyBytes(functionKey(b))) }
    }
    private fun discoverAnnotationReferences(functions: List<Function>) {
        val roots = identityDistinct(functions.flatMap { it.annotations } + compounds.annotations())
        annotationCodecs.capture(roots, object : AnnotationCaptureBindings {
            override fun provider(binding: AnnotationProviderBinding) = DeclarationReference.Local(DeclarationId(1))
            override fun constant(value: CompilerValue): ConstantRef {
                fun scan(value: CompilerValue) { when (value) {
                    is CompilerValue.TypeValue -> MCFPPType.resolveTypeId(value.id)?.let(compounds::include)
                    is CompilerValue.Typed -> { MCFPPType.resolveTypeId(value.type)?.let(compounds::include); scan(value.payload) }
                    is CompilerValue.Sequence -> value.elements.forEach(::scan)
                    is CompilerValue.Record -> value.fields.values.forEach(::scan)
                    else -> Unit
                } }
                scan(value); return ConstantRef(1)
            }
            override fun type(value: MCFPPType): TypeRef { compounds.include(value); return TypeRef(1) }
            override fun declaration(value: Any): DeclarationReference {
                when (value) {
                    is Function -> if (owned(value) && annotationDeclarationRoots.none { it === value }) annotationDeclarationRoots.add(value)
                    is FunctionParam -> if (owned(value.function) && annotationDeclarationRoots.none { it === value.function }) annotationDeclarationRoots.add(value.function)
                    is DataTemplate -> if (!selection.ownership.isDependencyDeclaration(value) &&
                        (value.declarationFile == null || value.declarationFile?.let { source -> selection.ownedSources.any { it === source } } == true)) compounds.include(value.getType())
                    is Var<*> -> value.declaredParentTemplate?.let { if (!selection.ownership.isDependencyDeclaration(it) &&
                        (it.declarationFile == null || it.declarationFile?.let { source -> selection.ownedSources.any { it === source } } == true)) compounds.include(it.getType()) }
                }
                return DeclarationReference.Local(DeclarationId(1))
            }
            override fun phase(value: Annotation) = compounds.phase(value)
        })
    }
    private fun discoverNativeTypes() {
        selection.macros.forEach { macro ->
            val descriptor = macro.descriptor
            val binding = NativeBinding(services.sourceProvider(descriptor.providerClass), descriptor.providerClass, descriptor.methodName,
                descriptor.parameterClasses, descriptor.returnClass)
            val method = NativeDeclarations.resolveMethod(binding, services.native)
            val prepared = descriptor.prepare(method)
            fun type(value: top.mcfpp.mni.expand.PreparedMacroType) { when (value) {
                is top.mcfpp.mni.expand.PreparedMacroType.Source -> compounds.include(services.sourceType(macro.namespace, value.expression))
                is top.mcfpp.mni.expand.PreparedMacroType.Callback -> { value.signature.parameters.forEach { type(it.type) }; type(value.signature.returnType) }
            } }
            prepared.parameters.forEach { type(it.type) }; type(prepared.returnType)
        }
        selection.helpers.forEach { helper -> helper.recipe.operandTypes.values.forEach(compounds::include) }
    }

    private fun providerReference(binding: AnnotationProviderBinding): DeclarationReference {
        val type = NativeDeclarations.restoreAnnotationProvider(binding, services.native)
        objects[type]?.let { return DeclarationReference.Local(it) }
        selection.ownership.declarationReference(type)?.let { return it }
        val id = providerIds.getOrPut(binding) {
            reserve().also { id ->
                val environment = EnvironmentId(1)
                val namespace = environments[(environment.value - 1).toInt()].namespace
                put(id, metadata.captureProvider(DeclCommon(null, null, binding.ownerClass, namespaces.getValue(namespace), environment,
                    DeclarationAccess.PRIVATE, 0), binding))
            }
        }
        return DeclarationReference.Local(id)
    }
    private fun captureProviders() {
        selection.providers.sortedWith { a, b -> compareUtf8(a.namespace + ":" + a.name, b.namespace + ":" + b.name) }.forEach { provider ->
            val binding = annotationCodecs.captureBinding(provider.provider)
            val id = reserve()
            val common = DeclCommon(DeclarationKey(provider.namespace, emptyList(), 13, provider.name), null, provider.name,
                namespaces.getValue(provider.namespace), environment(provider.source, provider.namespace), DeclarationAccess.PUBLIC, 0)
            put(id, metadata.captureProvider(common, binding))
            register(provider, id)
            if (!objects.containsKey(provider.provider)) register(provider.provider, id)
        }
    }
    private fun captureMacros() {
        selection.macros.sortedWith { a, b -> compareUtf8(a.namespace + ":" + a.descriptor.identifier + ":" + a.descriptor.methodDescriptor,
            b.namespace + ":" + b.descriptor.identifier + ":" + b.descriptor.methodDescriptor) }.forEach { macro ->
            val id = reserve()
            val name = macro.descriptor.identifier.ifEmpty { macro.descriptor.methodName }
            val common = DeclCommon(null, null, name, namespaces.getValue(macro.namespace), environment(macro.source, macro.namespace), DeclarationAccess.PUBLIC, 0)
            val record = metadata.captureMacro(macro.namespace, common, macro.descriptor)
            fun key(value: MacroType): KeyType = when (value) {
                is MacroType.Source -> keyType(actualTypes.getValue(value.type).typeId)
                is MacroType.Callback -> KeyType.Function(FunctionExecution.NORMAL, value.parameters.map { key(it.type) }, key(value.result))
            }
            put(id, record.copy(common = common.copy(key = DeclarationKey(macro.namespace, emptyList(), 11, name,
                DeclarationSignature(emptyList(), record.binding.parameters.map { key(it.type) })))))
            register(macro.descriptor, id)
        }
    }
    private fun captureMetadata() {
        selection.helpers.sortedWith { left, right -> compareUtf8(left.recipe.namespace + ":" + left.recipe.identifier,
            right.recipe.namespace + ":" + right.recipe.identifier) }.forEach { helper ->
            val id = reserve()
            val common = DeclCommon(null, null, "reference", namespaces.getValue(helper.recipe.namespace), environment(helper.source, helper.recipe.namespace), DeclarationAccess.PRIVATE, 0)
            val record = metadata.captureReference(common, helper.recipe)
            val name = referenceProjection.publishedName(helper.recipe)
            put(id, record.copy(common = common.copy(name = name), recipe = record.recipe.copy(name = name)))
            register(helper.recipe, id)
        }
    }
    private fun captureRuntime() {
        val byTag = linkedMapOf<TagKey, MutableList<DeclarationReference>>()
        identityDistinct(allFunctions.flatMap { it.tags } + GlobalScope.functionTags.values.filter { tag -> tag.functions.any { member -> allFunctions.any { it === member } } }).forEach { tag ->
            val members = identityDistinct(tag.functions.filter { member -> allFunctions.any { it === member } } + allFunctions.filter { function -> function.tags.any { it === tag } })
            byTag.getOrPut(TagKey(tag.namespace, tag.identifier)) { arrayListOf() }.addAll(members.map(::reference))
        }
        byTag.entries.sortedWith { a, b -> compareUtf8(a.key.namespace + ":" + a.key.name, b.key.namespace + ":" + b.key.name) }.forEach {
            runtimeRecords.add(RuntimeRecord.TagContribution(it.key, it.value.distinct(), false))
        }
        selection.scoreboards.sortedWith { a, b -> compareUtf8(a.name, b.name) }.forEach { scoreboard ->
            runtimeRecords.add(RuntimeRecord.Scoreboard(scoreboard.name, scoreboard.criterion, scoreboard.display?.let { intern(ConstantValue.Text(it.toCommandPart().toString())) }))
        }
        selection.initializers.forEachIndexed { index, function -> runtimeRecords.add(RuntimeRecord.Initialization(reference(function), index.toLong())) }
    }

    private fun candidates(function: Function, name: String, namespace: String?): List<Function> =
        identityDistinct(((if (namespace == null) function.owner?.scope?.getFunctionCandidates(name).orEmpty() else emptyList()) +
            GlobalScope.getFunctionCandidates(namespace, name, file(function).field)).filter {
            !it.actualCallBody
        }).sortedWith { a, b -> compareBytes(TypeTableCodec.declarationKeyBytes(functionKey(a)), TypeTableCodec.declarationKeyBytes(functionKey(b))) }

    private fun foreign(function: Function): DeclarationReference.Dependency {
        val reference = selection.ownership.functionReference(function) ?: selection.ownership.declarationReference(function)
            ?: unsupported("External function ${function.namespace}:${function.identifier} needs an explicit package ownership binding")
        require(reference.dependencyIndex <= selection.dependencies.size.toLong()) { "External function dependency is absent from selection" }
        foreignFunctions[reference] = function
        return reference
    }

    private fun reference(function: Function): DeclarationReference {
        functions[function]?.let { return DeclarationReference.Local(it) }
        functions[compounds.sourceFunction(function)]?.let { return DeclarationReference.Local(it) }
        if (function.ast != null) functions.keys.filter { it.ast === function.ast && it.identifier == function.identifier }.singleOrNull()?.let {
            return DeclarationReference.Local(functions.getValue(it))
        }
        selection.ownership.functionReference(function)?.let { foreignFunctions[it] = function; return it }
        if (function is NativeFunction && coreNative(function)) return builtinTarget(function, function.caller)
        return if (!owned(function)) foreign(function) else unsupported("Owned function was not included in the source closure: ${function.identifier}")
    }

    private fun readonly(function: Function): List<FunctionParam> = when (function) {
        is NativeFunction -> function.readOnlyParams
        is Generic<*> -> function.readOnlyParams
        else -> emptyList()
    }
    private fun nativeTypeNames(function: NativeFunction): List<String> =
        function.javaMethod.getAnnotation(top.mcfpp.annotations.MNIFunction::class.java)?.genericType?.toList().orEmpty()
    private fun registerNativeTypes(function: NativeFunction, owner: DeclarationId) {
        val names = nativeTypeNames(function)
        fun visit(type: MCFPPType) {
            if (type is MCFPPGenericParamType && type.typeId !in parameterTypes) {
                val index = names.indexOf(type.identifier)
                require(index >= 0) { "Native generic ${type.identifier} is absent from its exact method declaration" }
                val slot = NativeSlot(owner, index)
                require(nativeTypes.putIfAbsent(type.typeId, slot)?.let { it == slot } != false) { "Native type identity is shared between callable owners" }
            }
            (type as? MCFPPTypeWithGeneric)?.generic?.forEach(::visit)
        }
        (readonly(function).map { it.type } + function.normalParams.map { it.type } + function.returnType + function.caller).forEach(::visit)
    }
    private fun functionKey(function: Function): DeclarationKey {
        if (!owned(function)) selection.ownership.functionReference(function)?.let { return it.key }
        if (function.accessorField != null && function.accessModifier == Member.AccessModifier.PRIVATE) return DeclarationKey(functionNamespace(function),
            compounds.entry(function)?.let { compounds.path(it).map { owner -> DeclarationPathPart(owner.kind, owner.name) } }.orEmpty(),
            2, declarationName(function))
        val source = source(function)
        val readonlySyntax = source?.parameters?.readOnlyParams()?.readOnlyParameterList()?.readOnlyParameter().orEmpty()
        val normalSyntax = source?.parameters?.normalParams()?.parameterList()?.parameter().orEmpty()
        return DeclarationKey(functionNamespace(function),
        compounds.entry(function)?.let { compounds.path(it).map { owner -> DeclarationPathPart(owner.kind, owner.name) } }.orEmpty(),
        compounds.entry(function)?.kind ?: 2, declarationName(function),
        DeclarationSignature(readonly(function).mapIndexed { index, parameter -> signatureType(readonlySyntax.getOrNull(index)?.type(), parameter.type, function) },
            function.normalParams.mapIndexed { index, parameter -> signatureType(normalSyntax.getOrNull(index)?.type(), parameter.type, function) },
            receiver(function)?.let { value -> signatureType(source?.receiver, value, function) }))
    }
    private fun entryKey(entry: SourceCompoundIndex.Entry): DeclarationKey = if (entry.value is Function) functionKey(entry.value) else
        DeclarationKey(entry.namespace, compounds.path(entry).map { DeclarationPathPart(it.kind, compoundName(it.value, it.name)) }, entry.kind, compoundName(entry.value, entry.name),
            (entry.value as? CompoundDeclarations.OperatorSlot)?.let { slot ->
                compounds.implementation(slot)?.let { functionKey(it).signature }
                    ?: DeclarationSignature(emptyList(), listOfNotNull(slot.operand).map { keyType(it.typeId) })
            })

    private fun readonlyKey(name: String, function: Function): KeyType.Parameter? {
        readonly(function).indexOfFirst { it.identifier == name }.takeIf { it >= 0 }?.let {
            return KeyType.Parameter(0, ParameterRole.READONLY, it.toLong())
        }
        val owners = (listOfNotNull(function.owner) + compounds.lexicalOwners(function).asReversed()).distinct()
        owners.forEachIndexed { depth, owner ->
            val parameters = when (owner) { is GenericDataTemplate -> owner.readOnlyParams.map { it.identifier }; is Function -> readonly(owner).map { it.identifier }; else -> emptyList() }
            parameters.indexOf(name).takeIf { it >= 0 }?.let { return KeyType.Parameter(depth + 1L, ParameterRole.READONLY, it.toLong()) }
        }
        return null
    }
    private fun dependsOnReadonly(tree: ParseTree, function: Function): Boolean =
        if (tree.childCount == 0) readonlyKey(tree.text, function) != null
        else (0 until tree.childCount).any { dependsOnReadonly(tree.getChild(it), function) }

    /** Derive ABI keys before D/T allocation, using source operations and owner/index parameters. */
    private fun signatureType(site: Parser.TypeContext?, actual: MCFPPType, function: Function): KeyType {
        if (actual is MCFPPGenericParamType) readonlyKey(actual.identifier, function)?.let { return it }
        if (site == null || !dependsOnReadonly(site, function)) {
            val parameters = linkedMapOf<TypeId, KeyType.Parameter>()
            fun collect(type: MCFPPType) {
                if (type is MCFPPGenericParamType) readonlyKey(type.identifier, function)?.let { parameters[type.typeId] = it }
                (type as? MCFPPTypeWithGeneric)?.generic?.forEach(::collect)
            }
            collect(actual)
            if (function is NativeFunction) {
                val names = nativeTypeNames(function)
                fun native(type: MCFPPType) {
                    if (type is MCFPPGenericParamType && type.typeId !in parameters) names.indexOf(type.identifier).takeIf { it >= 0 }?.let {
                        parameters[type.typeId] = KeyType.Parameter(0, ParameterRole.NATIVE_TYPE, it.toLong())
                    }
                    (type as? MCFPPTypeWithGeneric)?.generic?.forEach(::native)
                }
                native(actual)
            }
            val previous = signatureParameters
            return try { signatureParameters = previous + parameters; keyType(actual.typeId) } finally { signatureParameters = previous }
        }
        val names = linkedMapOf<String, DeclarationId>()
        val keys = linkedMapOf<DeclarationId, KeyType>()
        val values = arrayListOf<ConstantValue>()
        val builtinKeys = linkedMapOf<TypeRef, KeyType>()
        val capture = ExpressionBridge(object : ExpressionCaptureBindings {
            override fun scope(site: ParserRuleContext) = ScopeId(1)
            override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse {
                val token = "${namespace.orEmpty()}:$name"
                val id = names.getOrPut(token) {
                    DeclarationId(names.size + 1L).also { id ->
                        keys[id] = if (namespace == null) readonlyKey(name, function) ?: namedKey(name, namespace, role, function)
                            else namedKey(name, namespace, role, function)
                    }
                }
                return NameUse.Local(id)
            }
            override fun builtinType(key: String, site: ParserRuleContext): TypeRef = TypeRef(builtinKeys.size + 1L).also {
                builtinKeys[it] = keyType(builtin(key).typeId)
            }
            override fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef =
                ConstantRef(values.size + 1L).also { values.add(value) }
        })
        val tree = capture.type(site)
        fun key(type: TypeSyntax): KeyType = when (type) {
            is TypeSyntax.Name -> type.resolved?.let(builtinKeys::getValue) ?: keys.getValue((type.use as NameUse.Local).target)
            is TypeSyntax.Container -> KeyType.Applied(KeyType.Builtin(when (type.kind) {
                ContainerKind.LIST -> "list"; ContainerKind.IMMUTABLE_LIST -> "ImmutableList"; ContainerKind.MAP -> "map"; ContainerKind.DICT -> "dict"
            }), listOf(key(type.argument)))
            is TypeSyntax.Vector -> KeyType.Builtin("vec${type.dimensions}")
            is TypeSyntax.Union -> KeyType.Union(type.alternatives.map(::key))
            is TypeSyntax.Function -> KeyType.Function(type.execution, type.parameters.map(::key), key(type.result))
            is TypeSyntax.Void -> KeyType.Builtin("void")
            else -> KeyType.Dependent(inlineTree(type, { keys.getValue((it as NameUse.Local).target) }, { values[(it.value - 1).toInt()] },
                { builtinKeys.getValue(it) }).toList())
        }
        return key(tree)
    }
    private fun namedKey(name: String, namespace: String?, role: NameRole, function: Function): KeyType {
        if (role == NameRole.TYPE) {
            val value = declaredType(function, name, namespace)
            if (value != null) return keyType(value.typeId)
            return keyType(builtin(name).typeId)
        }
        val type = declaredType(function, name, namespace)
        if (type != null) return keyType(type.typeId)
        val callables = candidates(function, name, namespace)
        if (callables.isNotEmpty()) return KeyType.Dependent(LibraryBinaryWriter().apply {
            u16(0x0102)
            list(callables) { callable ->
                val dependency = selection.ownership.functionReference(callable)
                val library = dependency?.let { selection.dependencies[(it.dependencyIndex - 1).toInt()] }
                TypeTableCodec.writePackage(this, library?.library ?: selection.identity.library)
                string(library?.classifier ?: selection.identity.classifier)
                TypeTableCodec.writeDeclarationKey(this, dependency?.key ?: functionKey(callable))
            }
        }.toByteArray().toList())
        unsupported("A dependent signature may reference only its readonly parameters and author-bound types: $name")
    }

    /** Inline the same neutral N/C fields as v1, expanding references and omitting session scope IDs. */
    private fun inlineTree(tree: SemanticNode, name: (NameUse) -> KeyType, constant: (ConstantRef) -> ConstantValue,
                           type: (TypeRef) -> KeyType): ByteArray {
        fun LibraryBinaryWriter.c(value: ConstantValue) {
            when (value) {
                ConstantValue.Null -> tagged(1) {}
                is ConstantValue.Integral -> tagged(2) { i64(value.value) }
                is ConstantValue.FloatBits -> tagged(3) { u32(value.bits.toLong() and 0xffff_ffffL) }
                is ConstantValue.DoubleBits -> tagged(4) { u64(value.bits.toULong()) }
                is ConstantValue.Bool -> tagged(5) { bool(value.value) }
                is ConstantValue.Text -> tagged(6) { string(value.value) }
                is ConstantValue.TypeValue -> tagged(7) { TypeTableCodec.writeKeyType(this, type(value.value)) }
                is ConstantValue.Nbt -> tagged(8) { string(value.snbt) }
                is ConstantValue.Sequence -> tagged(9) { list(value.elements) { c(constant(it)) } }
                is ConstantValue.Record -> tagged(10) { list(value.fields) { string(it.name); c(constant(it.value)) } }
                is ConstantValue.Typed -> tagged(11) { TypeTableCodec.writeKeyType(this, type(value.type)); c(constant(value.value)) }
                is ConstantValue.FunctionValue -> unsupported("Function constants cannot form a dependent signature")
            }
        }
        fun LibraryBinaryWriter.n(value: SemanticNode) {
            tagged(value.tag) {
                fun n(child: SemanticNode) = this.n(child)
                fun ns(children: List<SemanticNode>) = list(children) { n(it) }
                fun hint(value: TypeRef?) = optional(value) { TypeTableCodec.writeKeyType(this, type(it)) }
                when (value) {
                    is TypeSyntax.Name -> { TypeTableCodec.writeKeyType(this, name(value.use)); hint(value.resolved) }
                    is TypeSyntax.Apply -> { n(value.base); ns(value.readonly); hint(value.resolved) }
                    is TypeSyntax.Container -> { u8(value.kind.wireValue); n(value.argument) }
                    is TypeSyntax.Vector -> u32(value.dimensions)
                    is TypeSyntax.Entity -> { optional(value.limit) { c(constant(it)) }; list(value.names) { string(it) } }
                    is TypeSyntax.Union -> ns(value.alternatives)
                    is TypeSyntax.Intersection -> ns(value.components)
                    is TypeSyntax.Function -> { ns(value.parameters); n(value.result); u8(value.execution.wireValue) }
                    is TypeSyntax.Void -> Unit
                    is TypeSyntax.Inferred -> n(value.expression)
                    is Expression.Name -> TypeTableCodec.writeKeyType(this, name(value.use))
                    is Expression.TypeValue -> n(value.type)
                    is Expression.Literal -> c(constant(value.constant))
                    is Expression.Group -> optional(value.value) { n(it) }
                    is Expression.Unary -> { string(value.operator); n(value.operand) }
                    is Expression.Binary -> { string(value.operator); n(value.left); n(value.right) }
                    is Expression.Cast -> { n(value.value); n(value.target) }
                    is Expression.Member -> { n(value.receiver); string(value.name); u8(value.keyword.wireValue) }
                    is Expression.Index -> { n(value.receiver); optional(value.index) { n(it) } }
                    is Expression.Call -> { n(value.callee); ns(value.readonly); list(value.normal) { n(it.expression) }; optional(value.trailingLambda) { n(it) } }
                    is Expression.PropertyFilter -> { n(value.receiver); list(value.entries) { string(it.name); n(it.value) } }
                    is Expression.Range -> { optional(value.lower) { n(it) }; optional(value.upper) { n(it) } }
                    is Expression.ListValue -> ns(value.elements)
                    is Expression.Compound -> list(value.entries) { string(it.key); n(it.value) }
                    is Expression.NbtArray -> { u8(value.element.wireValue); list(value.values) { c(constant(it)) } }
                    is Expression.Coordinate -> list(value.dimensions) { u8(it.mode.wireValue); optional(it.value) { c(constant(it)) } }
                    is Expression.Selector -> u8(value.kind.wireValue)
                    is Expression.InterpolatedString -> list(value.parts) { part -> when (part) {
                        is TextPart.Text -> { u8(1); string(part.value) }
                        is TextPart.Interpolation -> { u8(2); n(part.expression) }
                    } }
                    else -> unsupported("Dependent signature operation ${value.tag} is not supported")
                }
            }
        }
        return LibraryBinaryWriter().apply { n(tree) }.toByteArray()
    }

    private fun syntaxDependsOnReadonly(node: SemanticNode): Boolean =
        (node is TypeSyntax.Name && node.use is NameUse.Local && node.use.target in readonlySlots) ||
        (node is Expression.Name && node.use is NameUse.Local && node.use.target in readonlySlots) ||
        semanticChildren(node).any(::syntaxDependsOnReadonly)
    private fun semanticChildren(node: SemanticNode): List<SemanticNode> = when (node) {
        is TypeSyntax.Apply -> listOf(node.base) + node.readonly
        is TypeSyntax.Container -> listOf(node.argument)
        is TypeSyntax.Union -> node.alternatives
        is TypeSyntax.Intersection -> node.components
        is TypeSyntax.Function -> node.parameters + node.result
        is TypeSyntax.Inferred -> listOf(node.expression)
        is Expression.TypeValue -> listOf(node.type)
        is Expression.Group -> listOfNotNull(node.value)
        is Expression.Unary -> listOf(node.operand)
        is Expression.Binary -> listOf(node.left, node.right)
        is Expression.Cast -> listOf(node.value, node.target)
        is Expression.Member -> listOf(node.receiver)
        is Expression.Index -> listOfNotNull(node.receiver, node.index)
        is Expression.Call -> listOf(node.callee) + node.readonly + node.normal.map { it.expression } + listOfNotNull(node.trailingLambda)
        is Expression.ListValue -> node.elements
        is Expression.Compound -> node.entries.map { it.value }
        else -> emptyList()
    }

    private fun intersectionKey(value: UnionDataTemplate): KeyType = KeyType.Dependent(LibraryBinaryWriter().apply {
        string("mcfpp.intersection/1")
        list(value.parent.filterIsInstance<DataTemplate>()) { bytes(TypeTableCodec.keyBytes(keyType(it.inheritanceIdentity))) }
    }.toByteArray().toList())
    private fun keyType(identity: TypeId): KeyType = signatureParameters[identity]
        ?: LibraryBuiltinTypes.protocolKey(identity)?.let { KeyType.Builtin(it) }
        ?: (compounds.types[identity] as? UnionDataTemplate)?.let(::intersectionKey) ?: when (identity) {
        is TypeId.Builtin -> KeyType.Builtin(LibraryBuiltinTypes.protocolKey(identity) ?: unsupported("Unsupported builtin signature ${identity.key}"))
        is TypeId.Applied -> when ((identity.constructor as? TypeId.Builtin)?.key) {
            "vector" -> KeyType.Builtin("vec" + (identity.arguments.single() as TypeId.Builtin).key)
            TypeId.FUNCTION_CONSTRUCTOR -> {
                val execution = when ((identity.arguments[0] as TypeId.Builtin).key) {
                    "func.execution:normal" -> FunctionExecution.NORMAL
                    "func.execution:paused" -> FunctionExecution.PAUSED
                    "func.execution:generator" -> FunctionExecution.GENERATOR
                    else -> unsupported("Unsupported callback execution signature")
                }
                KeyType.Function(execution, (identity.arguments[1] as TypeId.Applied).arguments.map(::keyType), keyType(identity.arguments[2]))
            }
            else -> KeyType.Applied(keyType(identity.constructor), identity.arguments.map(::keyType))
        }
        is TypeId.Union -> KeyType.Union(identity.alternatives.map(::keyType))
        is TypeId.Specialized -> KeyType.Specialized(keyType(identity.constructor), identity.arguments.map { compilerConstantKey(it).toList() })
        is TypeId.Declaration, is TypeId.Opaque -> {
            val local = compounds.types[identity]?.let(compounds::entry)
            if (local?.origin != null) KeyType.Anonymous(selection.identity.library, selection.identity.classifier, local.origin)
            else if (local != null) KeyType.Nominal(selection.identity.library, selection.identity.classifier, local.namespace,
                compounds.path(local).map { compoundName(it.value, it.name) } + compoundName(local.value, local.name))
            else {
                val reference = selection.ownership.typeReference(identity) ?: unsupported("Nominal signature needs an explicit dependency type binding")
                val dependency = selection.dependencies.getOrNull((reference.dependencyIndex - 1).toInt()) ?: unsupported("Missing type dependency")
                KeyType.Nominal(dependency.library, dependency.classifier, reference.key.namespace,
                    reference.key.ownerPath.map { it.name } + reference.key.name)
            }
        }
        else -> unsupported("Readonly/dependent/opaque signatures require the complete exporter")
    }
    private fun compilerConstantKey(value: CompilerValue): ByteArray = LibraryBinaryWriter().apply {
        fun LibraryBinaryWriter.c(value: CompilerValue) {
            when (value) {
                CompilerValue.NullValue -> tagged(1) {}
                is CompilerValue.Integral -> tagged(2) { i64(value.value) }
                is CompilerValue.FloatBits -> tagged(3) { u32(value.bits.toLong() and 0xffff_ffffL) }
                is CompilerValue.DoubleBits -> tagged(4) { u64(value.bits.toULong()) }
                is CompilerValue.Bool -> tagged(5) { bool(value.value) }
                is CompilerValue.Text -> tagged(6) { string(value.value) }
                is CompilerValue.TypeValue -> tagged(7) { TypeTableCodec.writeKeyType(this, keyType(value.id)) }
                is CompilerValue.Nbt -> tagged(8) { string(top.mcfpp.backend.NbtEncoding.snbt(top.mcfpp.backend.NbtEncoding.parse(value.snbt))) }
                is CompilerValue.Sequence -> tagged(9) { list(value.elements) { c(it) } }
                is CompilerValue.Record -> tagged(10) { list(value.fields.entries.sortedWith { a, b -> compareUtf8(a.key, b.key) }) { string(it.key); c(it.value) } }
                is CompilerValue.Typed -> tagged(11) { TypeTableCodec.writeKeyType(this, keyType(value.type)); c(value.payload) }
            }
        }
        c(if (::referenceProjection.isInitialized) referenceProjection.project(value) else value)
    }.toByteArray()

    private fun unit(function: Function): Parser.CompilationUnitContext = generateSequence(function.ast as ParserRuleContext?) {
        it.parent as? ParserRuleContext
    }.filterIsInstance<Parser.CompilationUnitContext>().firstOrNull() ?: unsupported("Already-parsed compilation unit is missing")

    private fun environmentRecord(file: MCFPPFile, unit: Parser.CompilationUnitContext): EnvironmentRecord {
        fun packages(namespace: String): List<PackageKey> = if (selection.ownedSources.any { it.namespace.identifier == namespace })
            listOf(selection.identity.library) else selection.ownership.namespacePackages(namespace).also {
                if (it.isEmpty() && namespace !in GlobalScope.stdNamespaces) unsupported("Import namespace $namespace needs explicit package ownership")
            }
        val explicit = FunctionDeclarations.environment(file.namespace.identifier, unit.importDeclaration()) {
            packages(it.importType().namespacePath().text)
        }
        val implicit = file.field.importedNamespaceField.keys.sorted().filter { namespace -> explicit.imports.none { it.targetNamespace == namespace } }
            .map { ImportRecord(it, null, null, null, packages(it)) }
        return explicit.copy(imports = explicit.imports + implicit)
    }

    private fun environmentKey(record: EnvironmentRecord) = LibraryBinaryWriter().apply {
        string(record.namespace); list(record.imports) {
            string(it.targetNamespace); optional(it.member) { string(it) }; optional(it.alias) { string(it) }; optional(it.from) { string(it) }
            list(it.resolvedPackages) { TypeTableCodec.writePackage(this, it) }
        }
    }.toByteArray()

    override fun environment(function: Function): EnvironmentId = environment(sourceFile(function), functionNamespace(function))
    override fun receiver(function: Function): MCFPPType? = when (function) {
        is NativeFunction -> function.caller.takeUnless { it == MCFPPPrivateType.Void }
        is ExtensionFunction -> receivers.getOrPut(function) {
            val context = source(function)?.receiver ?: unsupported("Extension receiver requires its structured source type")
            top.mcfpp.analysis.StructuredBindings.type(context, function) ?: file(function).withDeclarationContext {
                MCFPPType.parseFromContext(context, function.scope, function) ?: unsupported("Extension receiver has no author binding")
            }
        }
        else -> null
    }
    override fun key(function: Function): DeclarationKey? = if (function.accessModifier == Member.AccessModifier.PRIVATE || compounds.entry(function)?.public == false) null else functionKey(function)
    override fun declarationName(function: Function): String = builtinNames[function] ?: if (selection.initializers.any { it === function })
        "top_" + MessageDigest.getInstance("SHA-256").digest(compounds.logicalUnit(file(function)).toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
        else compounds.entry(function)?.name ?: function.identifier
    override fun origin(function: Function): AnonymousOrigin? = if (function.accessModifier == Member.AccessModifier.PRIVATE)
        if (function in builtinTargetFunctions) null else AnonymousOrigin("namespace/${function.namespace}", functions.getValue(function).value - 1, emptyList()) else null
    override fun declarationOwner(function: Function): DeclarationId = compounds.entry(function)?.owner?.let { objects.getValue(it) }
        ?: namespaces.getValue(functionNamespace(function))
    override fun source(function: Function): FunctionSourceSignature? = compounds.signature(function) ?: FunctionDeclarations.sourceSignature(function)
        ?: selection.ownership.sourceSignature(function)
        ?: (function as? NativeFunction)?.let(compounds::nativeSignature)
        ?: (function.ast?.parent as? Parser.OperationOverrideDeclarationContext)?.let { FunctionSourceSignature(it.functionParams(), it.functionReturnType()) }
        ?: (function.ast?.parent as? Parser.TemplateConstructorDeclarationContext)?.let { constructor ->
            constructor.normalParams()?.let { normal -> FunctionSourceSignature(object : Parser.FunctionParamsContext(null, -1) {
                override fun normalParams() = normal
            }, null) }
        }
    override fun preparedBody(function: Function): Statement.Block? {
        if (selection.initializers.any { it === function }) {
            val top = compounds.unit(file(function)).topStatement() ?: return null
            return withScope(top, null) { scope -> Statement.Block(scope, top.statement().map(StatementBridge(this)::statement)) }
        }
        return compounds.accessorExpression(function)?.let { accessor ->
        withScope(accessor.expression, null) { scope ->
            val expression = (accessor.expression as? Parser.JavaReferContext)?.let { reference ->
                val parts = reference.Identifier().map { it.text }
                val root = when (parts.first()) {
                    "this" -> Expression.This(scope, owner(reference))
                    "super" -> Expression.Super(scope, owner(reference))
                    else -> Expression.Name(scope, name(parts.first(), null, NameRole.VALUE, reference))
                }
                parts.drop(1).fold<String, Expression>(root) { receiver, member ->
                    Expression.Member(scope, receiver, member, candidates = memberCandidates(receiver, member, reference))
                }
            } ?: StatementBridge(this).expression(accessor.expression)
            Statement.Block(scope, listOf(if (accessor.getter) Statement.Return(scope, expression)
                else Statement.Assign(scope, Expression.Name(scope, name("field", null, NameRole.VALUE, accessor.expression)), "=", expression)))
        }
        }
    }
    override fun reserveParameter(owner: DeclarationId, parameter: FunctionParam, readonly: Boolean, index: Int): DeclarationId =
        parameterIds.getOrPut(Triple(owner, readonly, index)) { reserve().also {
            register(parameter, it)
            valueTypes[it] = parameter.type
            if (parameter.type is UnresolvedType) source(parameter.function)?.parameters?.let { syntax ->
                val declared = if (readonly) syntax.readOnlyParams()?.readOnlyParameterList()?.readOnlyParameter()?.getOrNull(index)?.type()
                    else syntax.normalParams().parameterList()?.parameter()?.getOrNull(index)?.type()
                declared?.let { compounds.rememberDeclaredType(parameter.type, it) }
            }
            if (readonly) {
                val slot = ReadonlySlot(owner, index); readonlySlots[it] = slot
                if (parameter.type == MCFPPConcreteType.Type) parameter.function.scope.getType(parameter.identifier)?.let { view -> parameterTypes[view.typeId] = slot }
            }
        } }
    override fun publishParameter(id: DeclarationId, parameter: FunctionParam, readonly: Boolean, index: Int) = publish(id, parameter.identifier)
    override fun <T> inFunction(function: Function, owner: DeclarationId, environment: EnvironmentId, action: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        capturedFunctions[function]?.let { return it as T }
        val previous = if (::current.isInitialized) current else null
        val previousOwner = currentOwner; val previousEnvironment = currentEnvironment; val previousCompound = currentCompound
        current = function; currentOwner = owner; currentEnvironment = environment
        currentCompound = function.owner as? DataTemplate
        if (function is NativeFunction) registerNativeTypes(function, owner)
        return try { withScope(function.ast ?: ParserRuleContext(), owner) { action() }.also { result ->
            if (result != null) capturedFunctions[function] = result as Any
        } } finally {
            if (previous != null) current = previous
            currentOwner = previousOwner; currentEnvironment = previousEnvironment; currentCompound = previousCompound
        }
    }
    override fun scope(site: ParserRuleContext): ScopeId = frames.last().id
    override fun <T> withScope(site: ParserRuleContext, owner: DeclarationId?, action: (ScopeId) -> T): T {
        val id = ScopeId(scopes.size + 1L); val declaration = owner ?: currentOwner
        scopes.add(ScopeRecord(declaration, frames.lastOrNull()?.id, emptyList()))
        frames.add(Frame(id, declaration))
        return try { action(id) } finally { frames.removeAt(frames.lastIndex) }
    }
    private fun publish(id: DeclarationId, name: String) {
        val frame = frames.last()
        require(frame.names.putIfAbsent(name, id) == null) { "Duplicate local name $name" }
        val index = (frame.id.value - 1).toInt()
        scopes[index] = scopes[index].copy(bindings = scopes[index].bindings + id)
    }
    override fun publishLocal(declaration: DeclarationId, name: String, site: ParserRuleContext) = publish(declaration, name)
    override fun reserveLocal(site: Parser.FieldDeclarationContext): DeclarationId = (globalSites[site] ?: reserve()).also {
        sourceLocals[it] = site
        ordinal(it, frames.last().owner)
    }
    override fun completeLocal(declaration: DeclarationId, facts: LocalDeclarationFacts) {
        val actual = facts.declaredType?.let(::actualType) ?: facts.initializer?.let(::inferredExpressionType)
            ?: unsupported("Cannot infer local ${facts.name}; supply an explicit supported type")
        valueTypes[declaration] = actual
        val node = facts.declaredType?.let { storeNode(declaration, it) }
        val owner = frames.last().owner
        val global = globalSites.values.any { it == declaration }
        put(declaration, ValueDeclaration(DeclCommon(null, null, facts.name, owner, currentEnvironment,
            DeclarationAccess.PRIVATE, if (facts.storage == LocalStorage.CONST) DeclarationFlags.CONST else 0),
            if (global) ValueRole.GLOBAL else ValueRole.LOCAL, when (facts.storage) {
                LocalStorage.VAR -> ValueStorage.VAR; LocalStorage.CONST -> ValueStorage.CONST
                LocalStorage.EXTERNAL -> ValueStorage.EXTERNAL; LocalStorage.IMPORT_VAR -> ValueStorage.IMPORT_VAR
            }, type(actual, owner, node), node, null, null))
    }
    override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse {
        if (namespace == null) frames.asReversed().firstNotNullOfOrNull { it.names[name] }?.let { id ->
            if (role == NameRole.VALUE || id in readonlySlots) return NameUse.Local(id)
        }
        if (role == NameRole.VALUE && namespace == null) file(current).field.vars[name]?.let { value ->
            globalVariables[value]?.let { return NameUse.Declaration(DeclarationReference.Local(it)) }
        }
        if (role == NameRole.TYPE) {
            // The name denotes the application's base declaration. A surrounding Child<T>
            // context can carry an unresolved type for the whole application, not for Child.
            val actual = declaredType(current, name, namespace) ?: resolveSourceType(site)
                ?: unsupported("Type name $name has no author binding")
            val alias = compounds.entries.firstOrNull { it.kind == 10 && it.name == name && (namespace ?: file(current).namespace.identifier) == it.namespace }
            val local = alias ?: compounds.types[actual.typeId]?.let(compounds::entry)
            if (local != null) return NameUse.Declaration(DeclarationReference.Local(objects.getValue(local.value)))
            LibraryBuiltinTypes.protocolKey(actual.typeId)?.let { key ->
                return NameUse.Lookup(key, null, currentEnvironment, emptyList())
            }
            if (actual.typeId is TypeId.Applied)
                return NameUse.Lookup(name, namespace, currentEnvironment, emptyList())
            val reference = selection.ownership.typeReference(actual.typeId)
                ?: unsupported("Type name $name needs an explicit dependency declaration binding")
            return NameUse.Declaration(reference)
        }
        if (namespace == null) frames.asReversed().firstNotNullOfOrNull { it.names[name] }?.let { return NameUse.Local(it) }
        val template = current.owner as? DataTemplate ?: currentCompound
        if (namespace == null && template != null) {
            val fieldName = if (name == "field") current.accessorField ?: name else name
            template.scope.getVar(fieldName)?.let { field ->
                val declared = field.declaredParentTemplate?.scope?.getVar(field.identifier) ?: field
                if (compounds.entry(declared) != null || selection.ownership.declarationReference(declared) != null)
                    return NameUse.Declaration(reference(declared))
            }
        }
        val candidates = candidates(current, name, namespace)
        if (candidates.isEmpty()) {
            val source = file(current)
            val localMacros = selection.macros.filter { macro ->
                val identifier = macro.descriptor.identifier.ifEmpty { macro.descriptor.methodName }
                if (namespace != null) macro.namespace == namespace && identifier == name else {
                    macro.namespace == source.namespace.identifier && identifier == name || compounds.unit(source).importDeclaration().any { declaration ->
                        val target = declaration.importType()
                        val identifiers = declaration.Identifier()
                        val alias = if (declaration.AS() != null) identifiers.first().text else null
                        declaration.FROM() == null && target.namespacePath().text == macro.namespace &&
                            (target.Identifier()?.text?.let { it == identifier && (alias ?: identifier) == name } ?: (identifier == name))
                    }
                }
            }.map { DeclarationReference.Local(objects.getValue(it.descriptor)) }
            val macros = localMacros + selection.ownership.macroReferences(namespace, name, source)
            if (macros.isNotEmpty()) return NameUse.Lookup(name, namespace, currentEnvironment, macros)
            val type = declaredType(current, name, namespace) ?: unsupported("Unbound author-environment value $name")
            compounds.entries.firstOrNull { it.kind == 10 && it.name == name &&
                (namespace ?: source.namespace.identifier) == it.namespace }?.let { alias ->
                return NameUse.Declaration(DeclarationReference.Local(objects.getValue(alias.value)))
            }
            if (LibraryBuiltinTypes.protocolKey(type.typeId) != null) {
                val use = NameUse.Lookup(name, namespace, currentEnvironment, emptyList())
                builtinValueUses[use] = this.type(type, currentOwner, null)
                return use
            }
            val local = compounds.types[type.typeId]
            return NameUse.Declaration(if (local != null) reference(local) else selection.ownership.typeReference(type.typeId)
                ?: unsupported("Type value $name needs a package binding"))
        }
        return NameUse.Lookup(name, namespace, currentEnvironment, candidates.map(::reference))
    }
    override fun destination(name: String, site: Parser.TryStoreStatementContext) = this.name(name, null, NameRole.VALUE, site)
    private fun knownType(expression: Expression): MCFPPType? = when (expression) {
        is Expression.Name -> when (val use = expression.use) {
            is NameUse.Local -> valueTypes[use.target]
            is NameUse.Declaration -> (use.target as? DeclarationReference.Local)?.target?.let { valueTypes[it] ?: when (val value = objectById[it]) {
                is Var<*> -> value.type; is CompoundData -> value.getType(); is MCFPPTypeAliasType -> value.cachedTarget; is MCFPPType -> value; else -> null
            } }
            is NameUse.Lookup -> builtinValueUses[use]?.let(actualTypes::get)
        }
        is Expression.This -> lexicalReceiver()
        is Expression.Super -> lexicalReceiver()?.parentType?.firstOrNull()
        is Expression.TypeValue -> actualType(expression.type)
        is Expression.Group -> expression.value?.let(::knownType)
        is Expression.Cast -> actualType(expression.target)
        is Expression.Literal -> (constants[(expression.constant.value - 1).toInt()] as? ConstantValue.Typed)?.type?.let(actualTypes::get)
        is Expression.Unary -> knownType(expression.operand)
        is Expression.Binary -> knownType(expression.left)
        is Expression.Index -> (knownType(expression.receiver) as? MCFPPTypeWithGeneric)?.generic?.singleOrNull()
        is Expression.ListValue -> expression.elements.firstOrNull()?.let(::knownType)?.let(::MCFPPListType)
        is Expression.Member -> knownType(expression.receiver)?.instanceData?.scope?.getVar(expression.name)?.type
        is Expression.Call -> {
            val refs = when (val callee = expression.callee) {
                is Expression.Name -> when (val use = callee.use) { is NameUse.Declaration -> listOf(use.target); is NameUse.Lookup -> use.candidates; else -> emptyList() }
                is Expression.Member -> callee.candidates.orEmpty()
                else -> emptyList()
            }
            refs.mapNotNull { (it as? DeclarationReference.Local)?.target?.let(objectById::get) as? Function }
                .map { it.returnType }.distinctBy { it.typeId }.singleOrNull()
        }
        else -> null
    }?.let { actual ->
        val inferred = if (actual is CapturedSourceType) actual else inferredSourceTypes[actual]
        when {
            inferred?.syntax is TypeSyntax.Inferred -> knownType((inferred.syntax as TypeSyntax.Inferred).expression)
            compounds.inferredType(actual) != null -> null
            else -> actual
        }
    }

    override fun memberCandidates(receiver: Expression, name: String, site: ParserRuleContext): List<DeclarationReference>? {
        val actual = knownType(receiver) ?: return null
        if (actual is UnresolvedType || actual is CapturedSourceType || actual is MCFPPGenericParamType || actual == MCFPPBaseType.Any) return null
        val static = receiver is Expression.TypeValue || (receiver as? Expression.Name)?.use.let { use ->
            builtinValueUses.containsKey(use) || ((use as? NameUse.Declaration)?.target as? DeclarationReference.Local)?.target?.let { objectById[it] is CompoundData || objectById[it] is MCFPPType } == true
        }
        val scope = (if (static) actual.objectData else actual.instanceData).scope
        return identityDistinct(scope.getFunctionCandidates(name, current).filter { !it.actualCallBody }).map { function ->
            if (functions.containsKey(function) || function.ast != null && functions.keys.any { it.ast === function.ast && it.identifier == function.identifier }) reference(function)
            else selection.ownership.functionReference(function) ?: selection.ownership.declarationReference(function) ?: when {
                function is NativeFunction && function.javaMethod.declaringClass.classLoader === MCFPPType::class.java.classLoader &&
                    (function.javaMethod.declaringClass.name.startsWith("top.mcfpp.mni.") || function.javaMethod.declaringClass.name.startsWith("top.mcfpp.core.")) -> builtinTarget(function, actual)
                else -> foreign(function)
            }
        }.distinct()
    }
    private fun builtinTarget(original: NativeFunction, receiver: MCFPPType): DeclarationReference {
        val source = if (::current.isInitialized) file(current) else selection.ownedSources.firstOrNull() ?: allFunctions.firstOrNull()?.let(::file)
            ?: unsupported("A native linkage needs an owned author environment")
        val substitutions = nativeTypeNames(original).associateWith { MCFPPGenericParamType(it, arrayListOf(MCFPPBaseType.Any)) as MCFPPType }.toMutableMap()
        if (receiver is MCFPPTypeWithGeneric && receiver.generic.size == 1 && "E" in substitutions) substitutions["E"] = receiver.generic.single()
        fun replace(type: MCFPPType): MCFPPType = (type as? MCFPPTypeWithGeneric)?.replaceGenericParam(substitutions) ?: type
        val wrapper = NativeFunction(original.identifier, source.namespace.identifier, original.javaMethod).apply {
            declarationFile = source; accessModifier = Member.AccessModifier.PRIVATE
            caller = receiver; returnType = replace(original.returnType); returnsConstWhenArgsConst = original.returnsConstWhenArgsConst
        }
        fun parameter(source: FunctionParam, readonly: Boolean) = FunctionParam(replace(source.type), source.identifier, wrapper, source.hasDefault, readonly).also {
            it.defaultValue = source.defaultValue; it.defaultTypes = source.defaultTypes; it.defaultContext = source.defaultContext; it.defaultBodyHandle = source.defaultBodyHandle
        }
        original.readOnlyParams.forEach { wrapper.readOnlyParams.add(parameter(it, true)) }
        original.normalParams.forEach { wrapper.normalParams.add(parameter(it, false)) }
        val key = LibraryBinaryWriter().apply {
            string(original.javaMethod.declaringClass.name); string(original.javaMethod.name)
            list(original.javaMethod.parameterTypes.toList()) { string(it.name) }; string(original.javaMethod.returnType.name)
            TypeTableCodec.writeDeclarationKey(this, DeclarationKey(wrapper.namespace, emptyList(), 2, original.identifier,
                DeclarationSignature(wrapper.readOnlyParams.map { signatureType(null, it.type, wrapper) }, wrapper.normalParams.map { signatureType(null, it.type, wrapper) }, signatureType(null, receiver, wrapper))))
        }.toByteArray()
        val stable = MessageDigest.getInstance("SHA-256").digest(key).joinToString("") { "%02x".format(it.toInt() and 255) }
        builtinTargets[stable]?.let { return reference(it) }
        builtinTargets[stable] = wrapper; builtinTargetFunctions.add(wrapper); builtinNames[wrapper] = original.identifier; builtinIdentityKeys[wrapper] = stable
        val id = reserve(); register(wrapper, id)
        wrapper.readOnlyParams.forEachIndexed { index, p -> reserveParameter(id, p, true, index) }
        wrapper.normalParams.forEachIndexed { index, p -> reserveParameter(id, p, false, index) }
        registerNativeTypes(wrapper, id)
        FunctionDeclarations.capture(wrapper, id, this).also { captured -> captured.parameters.forEach { (parameter, value) -> put(parameter, value) }; put(id, captured.declaration) }
        return DeclarationReference.Local(id)
    }
    private fun coreNative(function: NativeFunction): Boolean = function.javaMethod.declaringClass.let {
        it.classLoader === MCFPPType::class.java.classLoader && (it.name.startsWith("top.mcfpp.mni.") || it.name.startsWith("top.mcfpp.core."))
    }
    private fun builtin(spelling: String): MCFPPType = LibraryBuiltinTypes.restore(if (spelling == "type") "Type" else spelling)
    override fun builtinType(key: String, site: ParserRuleContext): TypeRef = type(builtin(key), currentOwner, null)
    private fun resolveSourceType(site: ParserRuleContext): MCFPPType? {
        val context = site as? Parser.TypeContext ?: site.parent as? Parser.TypeContext
            ?: return null
        compounds.sourceType(context)?.let { return it }
        if (dependsOnReadonly(context, current)) return null
        return file(current).withDeclarationContext {
            MCFPPType.parseFromContext(context, current.scope, current)
                ?: unsupported("Cannot bind the author's declared type")
        }
    }
    override fun resolvedType(site: ParserRuleContext): TypeRef? {
        val source = resolveSourceType(site) ?: return null
        val actual = if (source is MCFPPTypeAliasType) requireNotNull(source.cachedTarget) else source
        // This callback supplies an optional identity hint while the same N type tree is being
        // built. Dependent types remain in that tree; capturing their declared syntax here would
        // recursively enter this callback for the identical author context.
        if (actual is UnresolvedType) return null
        return typeIdentity(actual.typeId).also { actualTypes[it] = actual }
    }
    override fun type(value: MCFPPType, owner: DeclarationId, declaredSyntax: NodeRef?): TypeRef {
        val inferred = inferredType(value)
        val actual = inferred ?: if (value is MCFPPTypeAliasType) requireNotNull(value.cachedTarget) else value
        if (inferred != null) valueTypes.entries.filter { it.value === value }.forEach { it.setValue(actual) }
        if (actual is CapturedSourceType) return typeIds.getOrPut(actual.typeId) { sourceTypeReference(actual.syntax, actual.owner, actual.environment) }.also { actualTypes[it] = actual }
        val declared = declaredSyntax ?: compounds.declaredType(actual)?.let { storeNode(owner, StatementBridge(this).type(it)) }
        val syntax = declared?.let(storedNodes::getValue)
        val slot = (syntax as? TypeSyntax.Name)?.use?.let { (it as? NameUse.Local)?.target }?.let(readonlySlots::get)
            ?: parameterTypes[actual.typeId]
        val reference = if (slot != null) internType(LibraryType.Parameter(DeclarationReference.Local(slot.owner), ParameterRole.READONLY, slot.index.toLong()))
            else if (nativeTypes.containsKey(actual.typeId)) nativeTypes.getValue(actual.typeId).let { internType(LibraryType.Parameter(DeclarationReference.Local(it.owner), ParameterRole.NATIVE_TYPE, it.index.toLong())) }
            else if (actual is UnresolvedType || (syntax != null && syntaxDependsOnReadonly(syntax)))
                dependentType(owner, declared ?: unsupported("A dependent type requires its source type tree"))
            else typeIdentity(actual.typeId)
        actualTypes[reference] = actual
        return reference
    }
    private fun typeIdentity(identity: TypeId): TypeRef {
        capturedSourceTypes[identity]?.let { return sourceTypeReference(it.syntax, it.owner, it.environment) }
        typeIds[identity]?.let { return it }
        val record = LibraryBuiltinTypes.protocolKey(identity)?.let { LibraryType.Builtin(it) } ?: when (identity) {
            in parameterTypes -> parameterTypes.getValue(identity).let { LibraryType.Parameter(DeclarationReference.Local(it.owner), ParameterRole.READONLY, it.index.toLong()) }
            in nativeTypes -> nativeTypes.getValue(identity).let { LibraryType.Parameter(DeclarationReference.Local(it.owner), ParameterRole.NATIVE_TYPE, it.index.toLong()) }
            is TypeId.Builtin -> LibraryType.Builtin(LibraryBuiltinTypes.protocolKey(identity) ?: unsupported("Unsupported builtin type"))
            is TypeId.Applied -> when ((identity.constructor as? TypeId.Builtin)?.key) {
                "vector" -> LibraryType.Builtin("vec" + (identity.arguments.single() as TypeId.Builtin).key)
                "NBTRef" -> LibraryType.NbtRef(typeIdentity(identity.arguments.single()))
                TypeId.FUNCTION_CONSTRUCTOR -> {
                    val key = keyType(identity) as KeyType.Function
                    LibraryType.Function(key.execution, (identity.arguments[1] as TypeId.Applied).arguments.map(::typeIdentity), typeIdentity(identity.arguments[2]))
                }
                else -> LibraryType.Applied(typeIdentity(identity.constructor), identity.arguments.map(::typeIdentity))
            }
            is TypeId.Union -> LibraryType.Union(identity.alternatives.map(::typeIdentity))
            is TypeId.Specialized -> LibraryType.Specialized(typeIdentity(identity.constructor), identity.arguments.map { constant(it, emptyMap()) })
            is TypeId.Declaration, is TypeId.Opaque -> (compounds.types[identity] as? UnionDataTemplate)?.let { intersection ->
                LibraryType.Intersection(intersection.parent.filterIsInstance<DataTemplate>().map { typeIdentity(it.inheritanceIdentity) },
                    (reference(intersection) as DeclarationReference.Local).target)
            } ?: LibraryType.Declaration(compounds.types[identity]?.let(::reference)
                ?: selection.ownership.typeReference(identity) ?: unsupported("Nominal type ownership binding is missing"))
            else -> unsupported("Dependent/opaque/specialized types require the complete exporter")
        }
        return TypeRef(types.size + 1L).also { types.add(record); typeIds[identity] = it }
    }
    private fun internType(value: LibraryType): TypeRef {
        val existing = types.indexOf(value)
        return if (existing >= 0) TypeRef(existing + 1L) else TypeRef(types.size + 1L).also { types.add(value) }
    }
    /** Only explicit pending parent metadata can carry an unresolved nominal identity. */
    private fun pendingParentIdentity(identity: TypeId): TypeRef = pendingTypeIds.getOrPut(identity) {
        if (LibraryBuiltinTypes.protocolKey(identity) != null || compounds.types.containsKey(identity) ||
            selection.ownership.typeReference(identity) != null || identity in parameterTypes || identity in nativeTypes) typeIdentity(identity)
        else when (identity) {
            is TypeId.Declaration -> internType(LibraryType.UnresolvedNominal(identity.kind, identity.namespace, identity.name))
            is TypeId.Specialized -> internType(LibraryType.Specialized(pendingParentIdentity(identity.constructor), identity.arguments.map(::pendingParentConstant)))
            is TypeId.Applied -> if ((identity.constructor as? TypeId.Builtin)?.key in setOf("vector", TypeId.FUNCTION_CONSTRUCTOR)) typeIdentity(identity)
                else internType(LibraryType.Applied(pendingParentIdentity(identity.constructor), identity.arguments.map(::pendingParentIdentity)))
            is TypeId.Union -> internType(LibraryType.Union(identity.alternatives.map(::pendingParentIdentity)))
            else -> typeIdentity(identity)
        }
    }
    private fun pendingParentConstant(original: CompilerValue): ConstantRef {
        val value = referenceProjection.project(original)
        return intern(when (value) {
        CompilerValue.NullValue -> ConstantValue.Null
        is CompilerValue.Integral -> ConstantValue.Integral(value.value)
        is CompilerValue.FloatBits -> ConstantValue.FloatBits(value.bits)
        is CompilerValue.DoubleBits -> ConstantValue.DoubleBits(value.bits)
        is CompilerValue.Bool -> ConstantValue.Bool(value.value)
        is CompilerValue.Text -> ConstantValue.Text(value.value)
        is CompilerValue.TypeValue -> ConstantValue.TypeValue(pendingParentIdentity(value.id))
        is CompilerValue.Nbt -> ConstantValue.Nbt(top.mcfpp.backend.NbtEncoding.snbt(top.mcfpp.backend.NbtEncoding.parse(value.snbt)))
        is CompilerValue.Sequence -> ConstantValue.Sequence(value.elements.map(::pendingParentConstant))
        is CompilerValue.Record -> ConstantValue.Record(value.fields.entries.sortedWith { left, right -> compareUtf8(left.key, right.key) }
            .map { ConstantField(it.key, pendingParentConstant(it.value)) })
        is CompilerValue.Typed -> ConstantValue.Typed(pendingParentIdentity(value.type), pendingParentConstant(value.payload))
        })
    }
    private fun inferredType(value: MCFPPType): MCFPPType? {
        val source = compounds.inferredType(value) ?: return null
        require(inferring.add(value)) { "Cyclic inferred source field type" }
        return try {
            val owner = objects.entries.firstOrNull { (it.key as? Var<*>)?.type === value }?.value
            val prepared = bodySources.filterNotNull().firstOrNull { it.owner == owner && it.role == BodyRole.INITIALIZER }?.root as? Expression
            inferredSourceTypes.getOrPut(value) { inferredExpressionType(requireNotNull(prepared) { "An inferred source field must retain its captured initializer before capturing its type" }) }
        } finally { inferring.remove(value) }
    }
    override fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef = intern(value)
    private fun intern(value: ConstantValue): ConstantRef {
        val index = constants.indexOf(value)
        if (index >= 0) return ConstantRef(index + 1L)
        return ConstantRef(constants.size + 1L).also { constants.add(value) }
    }
    override fun constant(original: CompilerValue, types: Map<TypeId, MCFPPType>): ConstantRef {
        val value = referenceProjection.project(original)
        return intern(when (value) {
        CompilerValue.NullValue -> ConstantValue.Null
        is CompilerValue.Integral -> ConstantValue.Integral(value.value)
        is CompilerValue.FloatBits -> ConstantValue.FloatBits(value.bits)
        is CompilerValue.DoubleBits -> ConstantValue.DoubleBits(value.bits)
        is CompilerValue.Bool -> ConstantValue.Bool(value.value)
        is CompilerValue.Text -> ConstantValue.Text(value.value)
        is CompilerValue.TypeValue -> ConstantValue.TypeValue(typeIdentity(value.id))
        is CompilerValue.Nbt -> ConstantValue.Nbt(top.mcfpp.backend.NbtEncoding.snbt(top.mcfpp.backend.NbtEncoding.parse(value.snbt)))
        is CompilerValue.Sequence -> ConstantValue.Sequence(value.elements.map { constant(it, types) })
        is CompilerValue.Record -> ConstantValue.Record(value.fields.entries.sortedWith { a, b -> compareUtf8(a.key, b.key) }.map { ConstantField(it.key, constant(it.value, types)) })
        is CompilerValue.Typed -> ConstantValue.Typed(typeIdentity(value.type), constant(value.payload, types))
        })
    }
    override fun storeNode(owner: DeclarationId, node: TypeSyntax): NodeRef = storedNodeReferences.getOrPut(node) { NodeRef(storedNodes.size + 1L).also { reference ->
        storedNodes[reference] = node
        val index = rootCounts.getOrDefault(owner, 0); rootCounts[owner] = index + 1
        roots.add(SyntaxRoot(owner, listOf(index), node))
    } }
    override fun storeBody(body: SyntaxBodySource): BodyId = BodyId(bodySources.size + 1L).also { bodySources.add(body) }
    private fun actualType(type: TypeSyntax): MCFPPType = when (type) {
        is TypeSyntax.Name -> (type.use as? NameUse.Local)?.target?.let { id ->
            readonlySlots[id]?.let { slot ->
                MCFPPGenericParamType(declarations.getOrNull((id.value - 1).toInt())?.common?.name ?: "parameter", arrayListOf()).also {
                    parameterTypes[it.typeId] = slot
                }
            }
        } ?: type.resolved?.let { actualTypes[it] ?: LibraryBuiltinTypes.restore((types[(it.value - 1).toInt()] as LibraryType.Builtin).key) }
            ?: unsupported("Unbound declared type")
        is TypeSyntax.Container -> when (type.kind) {
            ContainerKind.LIST -> MCFPPListType(actualType(type.argument)); ContainerKind.IMMUTABLE_LIST -> MCFPPImmutableListType(actualType(type.argument))
            ContainerKind.MAP -> MCFPPMapType(actualType(type.argument)); ContainerKind.DICT -> MCFPPDictType(actualType(type.argument))
        }
        is TypeSyntax.Apply -> type.resolved?.let { actualTypes[it] } ?: capturedType(type)
        is TypeSyntax.AnonymousData -> (objectById[type.declaration] as? DataTemplate)?.getType() ?: unsupported("Anonymous type has no owned declaration")
        is TypeSyntax.Vector -> MCFPPVectorType(type.dimensions.toInt())
        is TypeSyntax.Union -> MCFPPUnionType(*type.alternatives.map(::actualType).toTypedArray())
        is TypeSyntax.Function -> MCFPPFunctionType(type.parameters.map(::actualType), actualType(type.result))
        is TypeSyntax.Void -> MCFPPPrivateType.Void
        is TypeSyntax.Inferred -> capturedType(type)
        else -> unsupported("This declared type requires the complete exporter")
    }
    private fun capturedType(node: TypeSyntax): CapturedSourceType = CapturedSourceType(node,
        scopes[(node.scope.value - 1).toInt()].owner, currentEnvironment).also { capturedSourceTypes[it.typeId] = it }
    private fun inferredExpressionType(expression: Expression): CapturedSourceType = capturedType(TypeSyntax.Inferred(expression.scope, expression))
    private fun sourceTypeReference(node: TypeSyntax, owner: DeclarationId, environment: EnvironmentId): TypeRef {
        fun dependent() = internType(LibraryType.Dependent(storeNode(owner, node), environment, owner))
        fun type(value: TypeSyntax) = sourceTypeReference(value, owner, environment)
        return when (node) {
            is TypeSyntax.Name -> {
                val parameter = (node.use as? NameUse.Local)?.target?.let(readonlySlots::get)
                if (parameter != null) internType(LibraryType.Parameter(DeclarationReference.Local(parameter.owner), ParameterRole.READONLY, parameter.index.toLong()))
                else node.resolved ?: (node.use as? NameUse.Declaration)?.target?.let { internType(LibraryType.Declaration(it)) } ?: dependent()
            }
            is TypeSyntax.Container -> internType(LibraryType.Applied(typeIdentity(TypeId.Builtin(when (node.kind) {
                ContainerKind.LIST -> "list"; ContainerKind.IMMUTABLE_LIST -> "ImmutableList"; ContainerKind.MAP -> "map"; ContainerKind.DICT -> "dict"
            })), listOf(type(node.argument))))
            is TypeSyntax.Apply -> {
                if (syntaxDependsOnReadonly(node)) dependent() else {
                    val values = node.readonly.map { argument -> when (argument) {
                        is Expression.Literal -> argument.constant
                        is Expression.TypeValue -> intern(ConstantValue.TypeValue(type(argument.type)))
                        is Expression.Name -> (argument.use as? NameUse.Declaration)?.target?.let { intern(ConstantValue.TypeValue(internType(LibraryType.Declaration(it)))) }
                        else -> null
                    } }
                    if (values.any { it == null }) dependent() else internType(LibraryType.Specialized(type(node.base), values.filterNotNull()))
                }
            }
            is TypeSyntax.Vector -> internType(LibraryType.Builtin("vec${node.dimensions}"))
            is TypeSyntax.Entity -> typeIdentity(MCFPPEntityType.NormalSelector.typeId)
            is TypeSyntax.Union -> internType(LibraryType.Union(node.alternatives.map(::type)))
            is TypeSyntax.Intersection -> internType(LibraryType.Intersection(node.components.map(::type), null))
            is TypeSyntax.AnonymousData -> internType(LibraryType.Declaration(DeclarationReference.Local(node.declaration)))
            is TypeSyntax.Function -> internType(LibraryType.Function(node.execution, node.parameters.map(::type), type(node.result)))
            is TypeSyntax.Void -> typeIdentity(MCFPPPrivateType.Void.typeId)
            is TypeSyntax.Inferred -> dependent()
        }
    }
    override fun reserveIterationVariable(site: Parser.ForeachStatementContext, iterable: Expression): DeclarationId {
        return reserve().also { id ->
            val element = inferredExpressionType(Expression.Index(iterable.scope, iterable, Expression.Literal(iterable.scope,
                intern(ConstantValue.Typed(typeIdentity(MCFPPBaseType.Int.typeId), intern(ConstantValue.Integral(0)))))))
            valueTypes[id] = element
            val owner = frames.last().owner
            ordinal(id, owner)
            put(id, ValueDeclaration(DeclCommon(null, null, site.Identifier().text, owner, currentEnvironment,
                DeclarationAccess.PRIVATE, 0), ValueRole.LOCAL, ValueStorage.VAR, this.type(element, owner, null), null, null, null))
        }
    }
    override fun executePathPart(site: Parser.VarContext, capture: StatementBridge): Expression =
        ExpressionBridge(object : ExpressionCaptureBindings by this {
            override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse =
                NameUse.Lookup(name, namespace, currentEnvironment, emptyList())
        }).expression(site)
    override fun reserveLambda(site: Parser.InlineLambdaContext): LambdaAllocation {
        val owner = frames.last().owner
        val declaration = reserve().also { ordinal(it, owner) }
        lambdaHeads[declaration] = LambdaHead(owner, currentEnvironment)
        val body = BodyId(bodySources.size + 1L).also { bodySources.add(null) }
        fun usesIt(tree: ParseTree): Boolean = when {
            tree is Parser.InlineLambdaContext -> false
            tree is Parser.VarWithSuffixContext && tree.Identifier().text == "it" -> true
            else -> (0 until tree.childCount).any { usesIt(tree.getChild(it)) }
        }
        val names = site.lambdaParameterList()?.lambdaParameter()?.map { it.Identifier().text } ?: if (usesIt(site.lambdaBody())) listOf("it") else emptyList()
        return LambdaAllocation(declaration, body, names.map { LambdaParameterSlot(reserve(), it) })
    }
    override fun completeLambdaParameters(allocation: LambdaAllocation, parameters: List<LambdaParameterSyntax>, explicit: Boolean) {
        parameters.forEachIndexed { index, parameter ->
            val declared = parameter.declaredType?.let { storeNode(parameter.declaration, it) }
            val actual = parameter.declaredType?.let(::actualType)
            actual?.let { valueTypes[parameter.declaration] = it }
            put(parameter.declaration, ValueDeclaration(DeclCommon(null, null, parameter.name, allocation.declaration,
                currentEnvironment, DeclarationAccess.PRIVATE, 0), ValueRole.LAMBDA_PARAMETER, ValueStorage.PARAMETER,
                actual?.let { type(it, allocation.declaration, declared) }, declared, null, null, parameterIndex = index.toLong()))
        }
    }
    override fun completeLambda(allocation: LambdaAllocation, body: LambdaBodyCapture) {
        val head = lambdaHeads.getValue(allocation.declaration)
        bodySources[(allocation.body.value - 1).toInt()] = SyntaxBodySource(allocation.declaration, head.environment, BodyRole.LAMBDA, body.root, body.captures)
        put(allocation.declaration, FunctionDeclaration(DeclCommon(null, null, "lambda", head.owner, head.environment,
            DeclarationAccess.PRIVATE, 0), FunctionExecutionKind.ORDINARY, null, emptyList(), allocation.parameters.map { it.declaration }, null, allocation.body, null))
    }
    private fun lexicalReceiver(): MCFPPType? {
        val owner = current.owner ?: currentCompound
        if (owner is ObjectDataTemplate || owner is GenericObjectDataTemplate || current.isStatic && current !is ExtensionFunction) return null
        return receiver(current) ?: owner?.getType()
    }
    override fun receiverCapture(owner: DeclarationReference): DeclarationReference? {
        val actual = lexicalReceiver() ?: return null
        val frame = frames.takeWhile { it.owner !in lambdaHeads }.lastOrNull() ?: return null
        val id = receiverDeclarations.getOrPut(frame.owner) {
            reserve().also { declaration ->
                ordinal(declaration, frame.owner)
                valueTypes[declaration] = actual
                put(declaration, ValueDeclaration(DeclCommon(null, null, "this", frame.owner, currentEnvironment,
                    DeclarationAccess.PRIVATE, 0), ValueRole.LOCAL, ValueStorage.PARAMETER, type(actual, frame.owner, null), null, null, null))
                frame.names["this"] = declaration
                val index = (frame.id.value - 1).toInt()
                scopes[index] = scopes[index].copy(bindings = scopes[index].bindings + declaration)
            }
        }
        return DeclarationReference.Local(id)
    }
    override fun declarationCapture(reference: DeclarationReference): DeclarationReference? {
        val target = (reference as? DeclarationReference.Local)?.target?.let(objectById::get) ?: foreignFunctions[reference]
            ?: (current.owner as? DataTemplate ?: currentCompound)?.scope?.allVars?.firstOrNull { selection.ownership.declarationReference(it) == reference }
            ?: return null
        val member = target as? Member ?: return null
        if (member.isStatic) return null
        val owner = when (target) { is Function -> target.owner; is Var<*> -> target.parentTemplate(); else -> null }
        val receiver = lexicalReceiver() ?: return null
        if (owner == null || !receiver.instanceData.isSubOf(owner)) return null
        return receiverCapture(reference)
    }
    override fun anonymousType(site: Parser.AnonymousTemplateTypeContext, capture: ExpressionBridge): DeclarationId =
        id(compounds.anonymous(site, file(current), current), frames.last().owner)
    private fun ordinal(id: DeclarationId, owner: DeclarationId) {
        privateOrdinals[id] = localOrdinals.getOrDefault(owner, 0L)
        localOrdinals[owner] = privateOrdinals.getValue(id) + 1
    }
    override fun templateMember(site: Parser.TemplateMemberDeclarationContext, capture: StatementBridge): TemplateMember =
        site.templateMember().annotation()?.let { TemplateMember.AnnotationApplication(scope(site), metadata.annotation(compounds.annotation(it)),
            compounds.annotationTargets(site).map { target -> objects.getValue(target.value) }) }
            ?: TemplateMember.Declaration(scope(site), objects.getValue(compounds.member(site).value))
    override fun annotationConstruction(site: Parser.AnnotationContext, capture: StatementBridge): TemplateMember.AnnotationConstruction? {
        val provider = compounds.annotationConstruction(site) ?: return null
        val member = site.parent as? Parser.TemplateMemberContext
        val declaration = member?.parent as? Parser.TemplateMemberDeclarationContext ?: unsupported("Deferred annotation has no source member")
        return TemplateMember.AnnotationConstruction(scope(site), providerReference(annotationCodecs.captureBinding(provider)),
            site.annotationArgs()?.value().orEmpty().map(capture::expression), compounds.annotationTargets(declaration).map { objects.getValue(it.value) })
    }
    override fun annotation(owner: DeclarationId, annotation: Annotation): AnnotationId = metadata.annotation(annotation)
    override fun nativeProvider(method: Method): PackageKey? = services.native.provider(method.declaringClass).also { metadata.nativeBinding(method) }

    private fun declaredType(function: Function, name: String, namespace: String?): MCFPPType? {
        val field = file(function).field
        val template = if (namespace == null) field.getAccessibleTemplate(name) else field.getAccessibleTemplate(namespace, name)
        if (template != null) return template.getType()
        val obj = if (namespace == null) field.getAccessibleObject(name) else field.getAccessibleObject(namespace, name)
        if (obj != null) return obj.getType()
        val enumeration = if (namespace == null) field.getAccessibleEnum(name) else field.getAccessibleEnum(namespace, name)
        return enumeration?.getType() ?: if (namespace == null) field.getType(name) else null
    }
    override fun id(value: Any, owner: DeclarationId?): DeclarationId {
        val canonical = compounds.entry(value)?.value ?: value
        return objects[canonical] ?: reserve().also { register(canonical, it) }
    }
    override fun reference(value: Any): DeclarationReference {
        if (value is Function) return reference(value)
        val canonical = compounds.entry(value)?.value ?: value
        return objects[canonical]?.let { DeclarationReference.Local(it) }
            ?: selection.ownership.declarationReference(value) ?: when (value) {
                is CompoundData -> selection.ownership.typeReference(value.getType().typeId)
                else -> null
            } ?: unsupported("Declaration has no owned/dependency identity: ${value.javaClass.simpleName}")
    }
    override fun compoundKey(value: Any): DeclarationKey? = compounds.entry(value)?.takeIf { it.public }?.let(::entryKey)
    override fun compoundOrigin(value: Any): AnonymousOrigin? = compounds.entry(value)?.origin
    override fun compoundOwner(value: Any): DeclarationId? = compounds.entry(value)?.let { entry ->
        entry.owner?.let { objects.getValue(it) } ?: if (entry.source == null || value is UnionDataTemplate) namespaces.getValue(entry.namespace) else null
    }
    override fun compoundName(value: Any, name: String): String = if (value is UnionDataTemplate)
        "intersection_" + MessageDigest.getInstance("SHA-256").digest(TypeTableCodec.keyBytes(intersectionKey(value)))
            .take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
        else compounds.entry(value)?.name ?: name
    override fun compoundEnvironment(value: Any): EnvironmentId = requireNotNull(compounds.entry(value)).let {
        environment(if (value is UnionDataTemplate) null else it.source, it.namespace)
    }
    override fun compoundAnnotations(value: Any): List<Annotation> {
        val direct = when (value) {
            is DataTemplate -> value.annotations
            is Var<*> -> value.annotations
            is CompilerEnum -> value.annotations
            else -> emptyList()
        }
        val entry = compounds.entry(value) ?: return direct
        if (entry.source != null) return direct
        val pending = when (value) {
            is DataTemplate -> value.pendingDeclarationAnnotations
            is Var<*> -> (entry.owner as? DataTemplate)?.pendingFieldAnnotations?.get(value.identifier).orEmpty()
            else -> emptyList()
        }
        return identityDistinct(direct + pending)
    }
    override fun identity(value: TypeId): TypeRef = typeIdentity(value)
    override fun dependentType(owner: DeclarationId, syntax: NodeRef): TypeRef {
        val node = storedNodes.getValue(syntax)
        if (!syntaxDependsOnReadonly(node)) return type(actualType(node), owner, null)
        return internType(LibraryType.Dependent(syntax, currentEnvironment, owner))
    }
    override fun declarationContext(value: DataTemplate): ParserRuleContext? = compounds.entry(value)?.context
    override fun inheritedFields(value: DataTemplate): List<SourceInheritedField> = compounds.inheritedFields(value)
    override fun sourceFunction(value: Function): Function = compounds.sourceFunction(value)
    override fun inheritedSlot(owner: CompoundData, field: Var<*>): DeclarationReference {
        val declaration = compounds.inheritedSlot(owner, field)
        val canonical = compounds.entry(declaration)?.value ?: declaration
        return objects[canonical]?.let { DeclarationReference.Local(it) }
            ?: selection.ownership.declarationReference(declaration)
            ?: unsupported("Inherited origin slot ${owner.namespaceID}.${field.identifier} has no owned/dependency declaration identity (${declaration.javaClass.simpleName})")
    }
    override fun preparedParents(value: DataTemplate): List<ParentRecord>? {
        val parents = value.parent.filterIsInstance<DataTemplate>().filter { it !== DataTemplate.baseDataTemplate }
        val context = declarationContext(value)
        if (context == null) {
            if (value.pendingParentTypeIds.isNotEmpty()) return value.pendingParentTypeIds.map { ParentRecord(pendingParentIdentity(it), null) }
            if (value.parentID.isNotEmpty()) unsupported("Metadata parent expressions require structured type identities or an actual author syntax tree")
        }
        val sourceParents = when (context) {
            is Parser.TemplateDeclarationContext -> context.compoundDeclaration()?.extendName().orEmpty()
            is Parser.ObjectTemplateDeclarationContext -> context.compoundDeclaration().extendName()
            is Parser.AnonymousTemplateTypeContext -> context.extendName()
            else -> emptyList()
        }
        if (sourceParents.isNotEmpty() && (value is GenericDataTemplate || sourceParents.any { it.readOnlyArgs() != null })) {
            val capture = StatementBridge(this)
            return sourceParents.map { parent ->
                val name = parent.className()
                val base = TypeSyntax.Name(scope(parent), this.name(name.classWithoutNamespace().Identifier().text,
                    name.namespacePath()?.text, NameRole.TYPE, name))
                val tree = parent.readOnlyArgs()?.let { TypeSyntax.Apply(scope(parent), base,
                    it.expressionList()?.expression().orEmpty().map(capture::expression)) } ?: base
                ParentRecord(null, storeNode(objects.getValue(value), tree))
            }
        }
        if (sourceParents.isNotEmpty()) return sourceParents.map { parent ->
            val name = parent.className()
            val actual = declaredType(current, name.classWithoutNamespace().Identifier().text, name.namespacePath()?.text)
                ?: unsupported("A parent has no author declaration binding")
            ParentRecord(type(actual, objects.getValue(value), null), null)
        }
        return parents.map { ParentRecord(type(it.getType(), objects.getValue(value), null), null) }
    }
    override fun <T> inCompound(value: DataTemplate, owner: DeclarationId, action: () -> T): T {
        fun parentScope(index: Int, parents: List<Any>): T {
            if (index == parents.size) return inSourceOwner(requireNotNull(compounds.entry(value)), owner, value) {
            (value as? GenericDataTemplate)?.readOnlyParams?.forEachIndexed { index, parameter ->
                val id = id(parameter, owner); val slot = ReadonlySlot(owner, index); readonlySlots[id] = slot
                value.scope.getType(parameter.identifier)?.let { parameterTypes[it.typeId] = slot }
                valueTypes[id] = parameter.type ?: MCFPPConcreteType.Type
            }
            action()
            }
            val parent = parents[index]
            val parentId = objects.getValue(parent)
            return withScope(ParserRuleContext(), parentId) {
                when (parent) {
                    is GenericDataTemplate -> parent.readOnlyParams.forEachIndexed { ordinal, parameter ->
                        val id = id(parameter, parentId); readonlySlots[id] = ReadonlySlot(parentId, ordinal)
                        valueTypes[id] = parameter.type ?: MCFPPConcreteType.Type
                        publish(id, parameter.identifier)
                    }
                    is Function -> readonly(parent).forEachIndexed { ordinal, parameter ->
                        val id = reserveParameter(parentId, parameter, true, ordinal); publish(id, parameter.identifier)
                    }
                }
                parentScope(index + 1, parents)
            }
        }
        return parentScope(0, compounds.lexicalOwners(value))
    }
    private fun <T> inAlias(value: MCFPPTypeAliasType, action: () -> T): T =
        inSourceOwner(requireNotNull(compounds.entry(value)), objects.getValue(value), null, action)
    private fun <T> inSourceOwner(entry: SourceCompoundIndex.Entry, owner: DeclarationId, template: DataTemplate?, action: () -> T): T {
        val previous = if (::current.isInitialized) current else null
        val oldOwner = currentOwner; val oldEnvironment = currentEnvironment; val oldCompound = currentCompound
        current = (if (template != null) Function("__capture__", template, null) else Function("__capture__", entry.namespace, null)).apply {
            declarationFile = entry.source; entry.source?.let { scope.parent.add(it.field) }
        }
        currentOwner = owner; currentEnvironment = if (template != null) compoundEnvironment(template) else environment(entry.source, entry.namespace); currentCompound = template
        return try { withScope(entry.context ?: ParserRuleContext(), owner) { action() } } finally {
            if (previous != null) current = previous
            currentOwner = oldOwner; currentEnvironment = oldEnvironment; currentCompound = oldCompound
        }
    }
    override fun storeTemplate(owner: DeclarationId, node: TemplateBody) {
        val index = rootCounts.getOrDefault(owner, 0); rootCounts[owner] = index + 1
        roots.add(SyntaxRoot(owner, listOf(index), node))
    }
    override fun owner(site: ParserRuleContext): DeclarationReference = if (current is ExtensionFunction) reference(current) else
        reference(current.owner ?: currentCompound ?: unsupported("This/super has no source owner"))

    private fun stableDeclaration(id: DeclarationId): ByteArray {
        namespaces.entries.firstOrNull { it.value == id }?.let { return TypeTableCodec.declarationKeyBytes(DeclarationKey(it.key, emptyList(), 1, it.key)) }
        val record = requireNotNull(declarations[(id.value - 1).toInt()])
        record.common.key?.let { return TypeTableCodec.declarationKeyBytes(it) }
        record.common.origin?.let { return LibraryBinaryWriter().apply { TypeTableCodec.writeAnonymousOrigin(this, it) }.toByteArray() }
        return LibraryBinaryWriter().apply {
            optional(record.common.owner) { bytes(stableDeclaration(it)) }
            u16(record.tag)
            (record as? ValueDeclaration)?.let { u8(when (it.role) {
                ValueRole.READONLY_PARAMETER, ValueRole.NORMAL_PARAMETER, ValueRole.LAMBDA_PARAMETER -> 0
                ValueRole.LOCAL -> 1
                else -> 2
            }); u8(it.role.wireValue) }
            optional((record as? ValueDeclaration)?.parameterIndex) { u32(it) }
            privateOrdinals[id]?.let { u32(it) }
            string(record.common.name)
            (objectById[id] as? Function)?.let(builtinIdentityKeys::get)?.let { string(it) }
            objectById[id]?.let(compounds::entry)?.let { bytes(TypeTableCodec.declarationKeyBytes(entryKey(it))) }
        }.toByteArray()
    }
    private fun declarationOrder(): Map<DeclarationId, DeclarationId> {
        val all = declarations.indices.map { DeclarationId(it + 1L) }
        val order = all.filter { requireNotNull(declarations[(it.value - 1).toInt()]).common.key != null }
            .sortedWith { a, b -> compareBytes(stableDeclaration(a), stableDeclaration(b)) }.toMutableList()
        val unkeyed = all.filter { it !in order }.toMutableSet()
        var cursor = 0
        while (cursor < order.size) {
            val owner = order[cursor++]
            val children = unkeyed.filter { requireNotNull(declarations[(it.value - 1).toInt()]).common.owner == owner }
                .sortedWith { a, b -> compareBytes(stableDeclaration(a), stableDeclaration(b)) }
            order.addAll(children); unkeyed.removeAll(children.toSet())
        }
        require(unkeyed.isEmpty()) { "Private declaration has no stable reachable owner" }
        return order.mapIndexed { index, old -> old to DeclarationId(index + 1L) }.toMap()
    }
    private fun rewriteDeclaration(value: LibraryDeclaration, remap: ExportNodeRemapper, nodes: Map<NodeRef, NodeRef>): LibraryDeclaration =
        remap.declarationRecord(value, nodes)

    private fun <T : Any> identityDistinct(values: List<T>): List<T> {
        val seen = IdentityHashMap<T, Boolean>()
        return values.filter { seen.put(it, true) == null }
    }
}
