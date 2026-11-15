package top.mcfpp.doc

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.library.api.LibraryCompilerSession
import top.mcfpp.library.model.LibraryModel
import java.nio.file.Path
import java.security.MessageDigest

data class DocumentationOptions(val includePrivate: Boolean = false, val includeDependencies: Boolean = false)
enum class DocumentationFormat { html, json, both }
data class DocumentationExportResult(val directory: Path, val files: List<Path>)

object DocumentationExport {
    /** Runs the shared project frontend, closes its compiler session, and returns a detached snapshot. */
    fun generate(request: Map<String, Any>, options: DocumentationOptions = DocumentationOptions()): DocumentationModel {
        LibraryCompilerSession.openSession(request).use { session ->
            val result = LibraryCompilerSession.documentSession(session, options)
            check(result["success"] == true) {
                (result["diagnostics"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.filter { it["severity"] == "error" }
                    ?.joinToString("\n") { it["message"].toString() } ?: "Documentation declaration preparation failed"
            }
            return result["documentation"] as DocumentationModel
        }
    }

    /** Captures the current prepared project before source function bodies are compiled. */
    fun captureCurrentProject(options: DocumentationOptions = DocumentationOptions()): DocumentationModel {
        DocumentationSignatures.validate()
        check(Project.errorCount == 0) { "Invalid documentation declarations" }
        val publication = Project.config.library
        val packageId = publication?.let { "${it.coordinate}@${it.version}/${it.classifier}" } ?: "local:${Project.config.name}"
        val own = Project.journalState().sourceFiles.flatMap { file ->
            val unit = file.tree() as P.CompilationUnitContext
            val imports = unit.importDeclaration().map { it.importType().text + (if (it.AS() != null) " as ${it.Identifier(0).text}" else "") }
            file.documentation.declarations.map { item ->
                val macro = item.kind == "native" && file.namespace.macroDeclarations().any { descriptor ->
                    descriptor.identifier == item.name && descriptor.parameters.map { it.name } == item.parameters.map { it.name }
                }
                val kind = if (macro) "macro" else item.kind
                val path = file.toPath().toAbsolutePath().normalize()
                val root = Project.config.root.toAbsolutePath().normalize()
                val relative = if (path.startsWith(root)) root.relativize(path).toString().replace('\\', '/') else file.name
                DocumentationSymbol(symbolId(packageId, file.namespace.identifier, item.owner, item.name, kind, item.parameters, item.readonly, item.receiver),
                    packageId, file.namespace.identifier, item.name, kind, item.owner, item.signature, item.access,
                    item.document.markdown, item.document.exportTags(), item.parameters, item.readonly, item.result, item.receiver,
                    DocumentationSource(relative, item.syntax.start.line, item.syntax.start.charPositionInLine + 1), imports, item.parents)
            }
        }
        val libraries = Project.libraryImportSession?.documentationModels().orEmpty()
        return assemble(Project.config.name, own, libraries, options)
    }

    /** Reads library API documentation directly, including aliases and macro declarations. */
    fun fromLibraries(title: String, libraries: List<LibraryModel>, options: DocumentationOptions = DocumentationOptions(includeDependencies = true)): DocumentationModel =
        assemble(title, emptyList(), libraries, options)

    private fun assemble(title: String, own: List<DocumentationSymbol>, libraries: List<LibraryModel>, options: DocumentationOptions): DocumentationModel {
        val dependencySymbols = libraries.flatMap { LibraryDocumentation(it, libraries).symbols() }
        // Several source files can contribute to the same namespace documentation.
        val all = (own + dependencySymbols).groupBy { it.id }.map { (_, items) ->
            val first = items.first()
            if (first.kind != "namespace" || items.size == 1) first else DocumentationSymbol(first.id, first.packageId, first.namespace,
                first.name, first.kind, first.owner, first.signature, first.access, items.map { it.markdown }.filter { it.isNotBlank() }.distinct().joinToString("\n\n"),
                items.flatMap { it.tags }.distinct(), first.parameters, first.readonlyParameters, first.returnType, first.receiver, first.source, first.imports)
        }.toMutableList()
        val original = all.toList()
        val resolver = DocumentationLinks(original)
        val expanded = hashSetOf<String>()
        fun ownerType(member: DocumentationSymbol): DocumentationSymbol? {
            val origin = member.inheritedFrom?.let { id -> all.firstOrNull { it.id == id } } ?: member
            return all.firstOrNull { it.packageId == origin.packageId && it.namespace == origin.namespace && it.name == origin.owner && it.owner == null }
        }
        fun derives(candidate: DocumentationSymbol, base: DocumentationSymbol, visited: Set<String> = emptySet()): Boolean {
            if (candidate.id in visited) return false
            return candidate.parents.mapNotNull { resolver.resolve(candidate, it) }.any { it.id == base.id || derives(it, base, visited + candidate.id) }
        }
        fun inherit(type: DocumentationSymbol, active: Set<String>) {
            if (type.id in expanded || type.id in active) return
            for (parent in type.parents) {
                val base = resolver.resolve(type, parent)?.takeIf { it.kind in setOf("data", "object") } ?: continue
                inherit(base, active + type.id)
                for (member in all.filter { it.namespace == base.namespace && it.packageId == base.packageId && it.owner == base.name }.toList()) {
                    if (member.kind != "field" || member.access == "private") continue
                    val existing = all.firstOrNull { it.namespace == type.namespace && it.packageId == type.packageId && it.owner == type.name &&
                        it.name == member.name && it.parameters.map { p -> p.type } == member.parameters.map { p -> p.type } &&
                        it.readonlyParameters.map { p -> p.type } == member.readonlyParameters.map { p -> p.type } &&
                        (it.kind == "field") == (member.kind == "field") }
                    val replaceInherited = existing?.inheritedFrom != null && ownerType(member)?.let { candidate ->
                        ownerType(existing)?.let { base -> derives(candidate, base) }
                    } == true
                    if (existing == null || replaceInherited) {
                        if (existing != null) all.remove(existing)
                        all += DocumentationSymbol(symbolId(type.packageId, type.namespace, type.name, member.name, member.kind, member.parameters, member.readonlyParameters, member.receiver),
                        type.packageId, type.namespace, member.name, member.kind, type.name, member.signature, member.access, member.markdown, member.tags,
                        member.parameters, member.readonlyParameters, member.returnType, member.receiver, member.source, member.imports, inheritedFrom = member.inheritedFrom ?: member.id)
                    }
                }
            }
            expanded += type.id
        }
        original.filter { it.kind in setOf("data", "object") }.forEach { inherit(it, emptySet()) }
        val ownPackages = own.map { it.packageId }.toSet()
        fun visible(symbol: DocumentationSymbol): Boolean {
            if (!options.includeDependencies && symbol.packageId !in ownPackages) return false
            if (!options.includePrivate && symbol.access == "private") return false
            return symbol.owner == null || all.any { it.packageId == symbol.packageId && it.namespace == symbol.namespace && it.name == symbol.owner && it.owner == null &&
                (options.includePrivate || it.access != "private") }
        }
        val selected = all.filter(::visible).sortedWith(compareBy({ it.packageId }, { it.namespace }, { it.owner ?: "" }, { it.name }, { it.signature }))
        val included = selected.map { it.id }.toSet()
        val links = DocumentationLinks(all)
        val warnings = arrayListOf<String>()
        val symbols = selected.map { symbol ->
            val references = DocumentationMarkdown.references(symbol).map { reference ->
                val target = links.resolve(symbol, reference)
                if (target == null) warnings += "${symbol.source?.let { "${it.file}:${it.line}" } ?: "${symbol.packageId} ${symbol.namespace}:${symbol.name}"}: DOC_LINK: Unresolved or ambiguous reference [$reference]"
                DocumentationReference(reference, target?.id, target?.id in included)
            }
            symbol.withReferences(references)
        }
        return DocumentationModel(title, symbols, warnings.distinct())
    }

    fun write(model: DocumentationModel, directory: Path, format: DocumentationFormat = DocumentationFormat.both): DocumentationExportResult =
        DocumentationWriter.write(model, directory, format)

    internal fun symbolId(packageId: String, namespace: String, owner: String?, name: String, kind: String,
                          parameters: List<DocumentationParameter>, readonly: List<DocumentationParameter>, receiver: String?): String {
        val identity = listOf(packageId, namespace, owner.orEmpty(), name, kind, receiver.orEmpty(),
            readonly.joinToString(",") { it.type.replace(Regex("\\s+"), "") }, parameters.joinToString(",") { it.type.replace(Regex("\\s+"), "") }).joinToString("\u0000")
        return "s-" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
