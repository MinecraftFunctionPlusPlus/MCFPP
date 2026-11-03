package top.mcfpp.library.compiler

import top.mcfpp.library.format.SyntaxCodec
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*

/** Selection indices address the import inventory; publication contains only author-bound references. */
internal object ExportDependencyClosure {
    fun trim(model: LibraryModel): LibraryModel {
        val inventory = model.manifest.dependencies
        val used = linkedSetOf<Long>()
        fun reference(value: DeclarationReference) {
            if (value is DeclarationReference.Dependency) {
                require(value.dependencyIndex in 1..inventory.size.toLong()) { "Unregistered export dependency" }
                used.add(value.dependencyIndex)
            }
        }
        fun provider(value: PackageKey?) {
            if (value != null) inventory.forEachIndexed { index, entry -> if (entry.library == value) used.add(index + 1L) }
        }
        fun native(value: NativeBinding?) = value?.providerPackage?.let(::provider)
        fun accessor(value: LibraryAccessor) { when (value) {
            is LibraryAccessor.Function -> reference(value.target)
            is LibraryAccessor.Native -> reference(value.target)
            else -> Unit
        } }
        model.declarations.declarations.forEach { declaration -> when (declaration) {
            is FunctionDeclaration -> native(declaration.native)
            is PropertyDeclaration -> { declaration.backing?.let(::reference); accessor(declaration.getter); accessor(declaration.setter) }
            is OperatorDeclaration -> reference(declaration.implementation)
            is CompoundDeclaration -> declaration.inherited.forEach { reference(it.slot) }
            is MacroDeclaration -> native(declaration.binding.native)
            is AnnotationProviderDeclaration -> provider(declaration.binding.providerPackage)
            else -> Unit
        } }
        model.tables.types.forEach { type -> when (type) {
            is LibraryType.Declaration -> reference(type.target)
            is LibraryType.Parameter -> reference(type.owner)
            else -> Unit
        } }
        model.tables.constants.filterIsInstance<ConstantValue.FunctionValue>().forEach { value ->
            reference(value.target); value.captures.forEach { reference(it.declaration) }
        }
        SyntaxCodec.validateAll(model.syntax, object : SyntaxReferenceValidator {
            override fun declaration(value: DeclarationReference) = reference(value)
            override fun environment(reference: EnvironmentId) {}
            override fun type(reference: TypeRef) {}
            override fun constant(reference: ConstantRef) {}
            override fun annotation(reference: AnnotationId) {}
        })
        fun portable(value: PortableValue) { if (value is PortableValue.Declaration) reference(value.value) }
        model.annotations.forEach { record -> reference(record.provider); record.state?.fields?.forEach { portable(it.value) } }
        model.stateCells.forEach { value -> when (value) {
            is StateCell.Sequence -> value.values.forEach(::portable)
            is StateCell.Map -> value.entries.forEach { portable(it.value) }
            is StateCell.Set -> Unit
        } }
        model.runtime.forEach { value -> when (value) {
            is RuntimeRecord.FunctionBinding -> reference(value.declaration)
            is RuntimeRecord.TagContribution -> value.members.forEach(::reference)
            is RuntimeRecord.Initialization -> reference(value.declaration)
            is RuntimeRecord.Scoreboard -> Unit
        } }
        model.resources.mapNotNull { it.owner }.forEach(::reference)
        model.declarations.environments.forEach { environment -> environment.imports.forEach { import ->
            import.resolvedPackages.filter { it != model.manifest.library }.forEach { packageKey ->
                val matching = inventory.indices.filter { inventory[it].library == packageKey }
                require(matching.isNotEmpty()) { "Author import package is outside the dependency inventory: $packageKey" }
                matching.forEach { used.add(it + 1L) }
            }
        } }
        val ordered = used.sortedWith { a, b ->
            val first = inventory[(a - 1).toInt()]; val second = inventory[(b - 1).toInt()]
            compareUtf8(first.library.coordinate, second.library.coordinate).takeIf { it != 0 }
                ?: compareUtf8(first.library.version, second.library.version).takeIf { it != 0 }
                ?: compareUtf8(first.classifier, second.classifier)
        }
        val mapping = ordered.mapIndexed { index, original -> original to index + 1L }.toMap()
        val canonical = CanonicalLibraryTables(model.tables,
            model.tables.types.indices.associate { TypeRef(it + 1L).let { it to it } },
            model.tables.constants.indices.associate { ConstantRef(it + 1L).let { it to it } })
        val remap = ExportNodeRemapper(canonical, dependencies = mapping)
        val nodes = model.syntax.nodes.indices.associate { NodeRef(it + 1L).let { it to it } }
        val syntax = LibrarySyntax(model.syntax.nodes.map(remap::node), model.syntax.scopes,
            model.syntax.bodies.map { it.copy(captures = it.captures.map(remap::reference)) })
        return LibraryModel(model.manifest.copy(dependencies = ordered.map { inventory[(it - 1).toInt()] }),
            LibraryDeclarations(model.declarations.declarations.map { remap.declarationRecord(it, nodes) }, model.declarations.environments),
            remap.tables(model.tables, nodes), syntax, model.annotations.map(remap::annotation), model.stateCells.map(remap::stateCell),
            model.runtime.map(remap::runtime), model.resources.map { it.copy(owner = it.owner?.let(remap::reference)) }, model.debug)
    }
}
