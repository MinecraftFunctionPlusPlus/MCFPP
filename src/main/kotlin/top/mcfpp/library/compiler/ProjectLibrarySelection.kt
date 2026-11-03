package top.mcfpp.library.compiler

import top.mcfpp.Project
import top.mcfpp.backend.ReferenceAccessHelpers
import top.mcfpp.core.lang.MCNumber
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import java.util.IdentityHashMap
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.mni.expand.CompilerSourceIndex
import top.mcfpp.mni.expand.PreparedMacroType
import top.mcfpp.mni.api.TypeRef as MniTypeRef

/** Reads the project's source directory, never cumulative imported modules or generated instance caches. */
internal object ProjectLibrarySelection {
    fun snapshot(identity: LibraryIdentity, target: LibraryTarget, resources: List<OwnedLibraryResource>): OwnedLibrarySelection {
        val namespaces = GlobalScope.localNamespaces.values.sortedWith { a, b -> compareUtf8(a.identifier, b.identifier) }
        // NoStackFunction carries its generating function as a parent and has no declaration
        // body. IR/control-flow lowering republishes these helpers from the retained source B.
        val declaredFunctions = namespaces.flatMap { it.scope.functions.values.flatten() }
            .filter { !it.actualCallBody && !it.isDerivedImplementation && it !is NoStackFunction }
        val templates = namespaces.flatMap { it.scope.template.values + it.scope.objects.filterIsInstance<DataTemplate>() }
            .filter { it !is CompiledGenericDataTemplate }
        val sources = distinct(Project.journalState().sourceFiles + Project.trees.keys + declaredFunctions.mapNotNull { it.declarationFile } + templates.mapNotNull { it.declarationFile })
        fun owned(file: MCFPPFile?) = file != null && sources.any { it === file }
        val session = Project.libraryImportSession
        val dependencies = session?.exportDependencies().orEmpty()
        val ownership = session?.exportOwnership() ?: object : LibraryExportOwnership {}
        fun extensions(tree: ParseTree): Sequence<top.mcfpp.antlr.mcfppParser.ExtensionFunctionDeclarationContext> = sequence {
            if (tree is top.mcfpp.antlr.mcfppParser.ExtensionFunctionDeclarationContext) yield(tree)
            for (index in 0 until tree.childCount) yieldAll(extensions(tree.getChild(index)))
        }
        val extensionFunctions = sources.flatMap { source ->
            val unit = Project.trees[source] ?: return@flatMap emptyList()
            extensions(unit).map { declaration -> source.withDeclarationContext {
                val type = MCFPPType.parseFromContext(declaration.type(), source.field) ?: error("Extension receiver has no prepared author type")
                type.instanceData.scope.functions[declaration.Identifier().text].orEmpty().single { it.ast === declaration.curlBlock() && !it.actualCallBody }
            } }.toList()
        }
        val functions = (declaredFunctions + extensionFunctions).filter {
            (owned(it.declarationFile) || it.declarationFile == null && (it is NativeFunction || it.isAbstract)) && !ownership.isDependencyDeclaration(it)
        }
        val ownedTemplates = templates.filter { (it.declarationFile == null || owned(it.declarationFile)) && !ownership.isDependencyDeclaration(it) }
        val macros = namespaces.flatMap { namespace ->
            val source = sources.firstOrNull { it.namespace.identifier == namespace.identifier }
            namespace.macroDeclarations().filter { !ownership.isDependencyDeclaration(it) }.map { OwnedLibraryMacro(namespace.identifier, it, source) }
        }
        val helpers = namespaces.flatMap { namespace ->
            val source = sources.firstOrNull { it.namespace.identifier == namespace.identifier }
            ReferenceAccessHelpers.recipes(namespace.identifier).filter { !ownership.isDependencyDeclaration(it) }
                .map { OwnedLibraryHelper(it, source) }
        }
        val providers = namespaces.flatMap { namespace ->
            val source = sources.firstOrNull { it.namespace.identifier == namespace.identifier }
            namespace.scope.annotations.entries.filter { !ownership.isDependencyDeclaration(it.value) }
                .sortedWith { a, b -> compareUtf8(a.key, b.key) }.map { OwnedLibraryAnnotationProvider(namespace.identifier, it.key, it.value, source) }
        }
        val enums = namespaces.flatMap { it.scope.enums }.filter { value -> !ownership.isDependencyDeclaration(value) }
        val aliases = sources.flatMap { source ->
            val unit = Project.trees[source] as? top.mcfpp.antlr.mcfppParser.CompilationUnitContext
            unit?.typealiasDeclaration().orEmpty().map { OwnedLibraryAlias(it.Identifier().text, source) }
        }
        val builtinScores = setOf("mcfpp_default", "mcfpp_boolean", "mcfpp_init", "mcfpp_temp", "mcfpp_pointer_counter",
            "mcs_float_sign", "mcs_float_int0", "mcs_float_int1", "mcs_float_exp", "float_sign", "float_int0", "float_int1", "float_exp", "int")
        val scoreboards = (enums.map { it.sbObject } + ownedTemplates.flatMap { template ->
            template.scope.allVars.filter { it.declaredParentTemplate === template }.filterIsInstance<MCNumber<*>>().map { it.sbObject }
        } + functions.flatMap { it.scope.allVars.filterIsInstance<MCNumber<*>>().map { value -> value.sbObject } })
            .filter { it.name !in builtinScores && !ownership.isDependencyDeclaration(it) }.distinctBy { it.name }
        val initializers = sources.filter { (Project.trees[it] as? top.mcfpp.antlr.mcfppParser.CompilationUnitContext)?.topStatement()?.statement()?.isNotEmpty() == true }
            .map { it.topFunction }.filter { !it.actualCallBody }
        val base = OwnedLibrarySelection(identity, target, distinct(functions + initializers), sources, dependencies, ownership,
            distinct(ownedTemplates), distinct(enums), aliases, macros, helpers, scoreboards, initializers, resources, providers)
        return if (session == null) base else base.copy(services = ExportSourceServices.create(base, session.nativeBindings(), session.annotationCodecs(),
            { name -> session.sourceProvider(name) }, { name -> session.hasSourceProvider(name) }))
    }
    private fun <T : Any> distinct(values: Collection<T>): List<T> {
        val seen = IdentityHashMap<T, Boolean>()
        return values.filter { seen.put(it, true) == null }
    }
}

/** Explicit author classes supplement an imported session's provider directory. No display text becomes a type identity. */
internal object ExportSourceServices {
    fun create(selection: OwnedLibrarySelection, imported: ManagedNativeBindings? = null,
               codecs: PortableAnnotationCodecs? = null, importedProvider: ((String) -> PackageKey?)? = null,
               hasImportedProvider: ((String) -> Boolean)? = null): LibraryExportServices {
        val annotations = codecs ?: PortableAnnotationCodecs.builtins()
        val loader = Project.classLoader
        val classes = linkedMapOf<String, Class<*>>()
        (selection.functions + GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .filter { function -> selection.ownedSources.any { it === function.declarationFile } }).filterIsInstance<NativeFunction>()
            .forEach { classes[it.javaMethod.declaringClass.name] = it.javaMethod.declaringClass }
        selection.macros.forEach { macro ->
            val owner = Class.forName(macro.descriptor.providerClass, false, loader)
            classes[owner.name] = owner
        }
        annotations.bindings().forEach { binding ->
            if (binding.providerPackage == null) classes[binding.ownerClass] = Class.forName(binding.ownerClass, false, loader)
        }
        val packages = classes.keys.associateWith { name -> if (hasImportedProvider?.invoke(name) == true) requireNotNull(importedProvider)(name) else null }
        val managed = object : ManagedNativeBindings {
            override fun loader(provider: PackageKey?): ClassLoader = if (provider == null) loader else requireNotNull(imported) { "Unregistered native package $provider" }.loader(provider)
            override fun provider(owner: Class<*>): PackageKey? {
                if (classes[owner.name] === owner) return packages[owner.name]
                if (owner.classLoader === MCFPPType::class.java.classLoader &&
                    (owner.name.startsWith("top.mcfpp.mni.") || owner.name.startsWith("top.mcfpp.core."))) return null
                return requireNotNull(imported) { "Native owner has no explicit author/session registration: ${owner.name}" }.provider(owner)
            }
        }
        fun provider(name: String): PackageKey? {
            if (name in classes) return packages[name]
            return requireNotNull(importedProvider) { "Native class has no explicit author/session registration: $name" }(name)
        }
        val types = linkedMapOf<String, MCFPPType>()
        fun collect(type: MCFPPType) {
            if (type is UnresolvedType || type is MCFPPGenericParamType) return
            if (types.putIfAbsent(type.typeName, type) != null) return
            (type as? MCFPPTypeWithGeneric)?.generic?.forEach(::collect)
            (type as? MCFPPUnionType)?.types?.forEach(::collect)
        }
        listOf("object", "any", "int", "float", "bool", "string", "text", "range", "pos2", "pos3", "nbt", "byte", "short", "long", "double",
            "ByteArray", "IntArray", "LongArray", "Type", "java", "void", "null", "entity", "ScoreRef", "DataObject").forEach { key ->
            LibraryBuiltinTypes.restore(key).also { collect(it); types[key] = it }
        }
        types["type"] = MCFPPConcreteType.Type
        selection.functions.forEach { function -> function.normalParams.forEach { collect(it.type) }; collect(function.returnType) }
        selection.templates.forEach { template -> collect(template.getType()); template.scope.allVars.forEach { collect(it.type) } }
        val metadataTypes = lazy {
            val resolved = linkedMapOf<Pair<String, String>, MCFPPType>()
            selection.macros.mapNotNull { it.source }.distinct().forEach { source ->
                val unit = Project.trees[source] as? top.mcfpp.antlr.mcfppParser.CompilationUnitContext
                    ?: error("Native metadata requires the author's existing parsed unit")
                val caller = Function("__export_metadata__", source.namespace.identifier, null).apply {
                    declarationFile = source; scope.parent.add(source.field)
                }
                // Only the existing author anchor supplies a source span. Raw generic bodies are
                // not part of this file-level metadata request and keep their own lexical binders.
                fun anchor(tree: ParseTree): org.antlr.v4.runtime.ParserRuleContext? {
                    if (tree is org.antlr.v4.runtime.ParserRuleContext && tree.childCount > 0 &&
                        (0 until tree.childCount).none { tree.getChild(it) is org.antlr.v4.runtime.ParserRuleContext }) return tree
                    return (0 until tree.childCount).firstNotNullOfOrNull { anchor(tree.getChild(it)) }
                }
                val prepared = source.withDeclarationContext { CompilerSourceIndex(caller).prepareDeclarationEnvironment(requireNotNull(anchor(unit))) }
                prepared.requireCurrent()
                prepared.macros.namespaces.forEach { (namespace, snapshot) -> snapshot.functions.forEach { function ->
                    fun sourceType(syntax: PreparedMacroType, reference: MniTypeRef<*>) {
                        when (syntax) {
                            is PreparedMacroType.Source -> {
                                val type = snapshot.types.source(reference)
                                val previous = resolved.put(namespace to syntax.expression, type)
                                require(previous == null || previous.typeId == type.typeId) { "Native author type spelling has conflicting prepared bindings" }
                            }
                            is PreparedMacroType.Callback -> {
                                val signature = requireNotNull(reference.inlineSignature())
                                syntax.signature.parameters.zip(signature.parameters()).forEach { (parameter, actual) -> sourceType(parameter.type, actual.type()) }
                                sourceType(syntax.signature.returnType, signature.returnType())
                            }
                        }
                    }
                    function.prepared.parameters.zip(function.parameters).forEach { (parameter, actual) -> sourceType(parameter.type, actual.type()) }
                    sourceType(function.prepared.returnType, function.returnType)
                } }
            }
            resolved
        }
        return LibraryExportServices(managed, annotations, ::provider) { namespace, spelling ->
            val sources = selection.ownedSources.filter { it.namespace.identifier == namespace }
            val found = (sources.mapNotNull { it.field.getType(spelling) } + selection.templates.filter {
                it.namespace == namespace && (it.identifier == spelling || it.namespaceID == spelling)
            }.map { it.getType() }).distinctBy { it.typeId }
            if (found.size == 1) found.single() else types[spelling] ?: metadataTypes.value[namespace to spelling]
                ?: error("LIB_TYPE_UNSUPPORTED: No prepared author type for $namespace:$spelling")
        }
    }
}
