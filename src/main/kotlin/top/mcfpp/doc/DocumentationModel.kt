package top.mcfpp.doc

import java.util.Collections

data class DocumentationParameter(val name: String, val type: String, val defaultValue: String? = null)
data class DocumentationTag(val name: String, val subject: String?, val content: String)
data class DocumentationSource(val file: String, val line: Int, val column: Int)
data class DocumentationReference(val text: String, val target: String?, val included: Boolean)

/** Detached value data: no compiler objects, parse trees, class loaders, or mutable documents. */
class DocumentationSymbol(
    val id: String,
    val packageId: String,
    val namespace: String,
    val name: String,
    val kind: String,
    val owner: String?,
    val signature: String,
    val access: String,
    val markdown: String,
    tags: List<DocumentationTag>,
    parameters: List<DocumentationParameter>,
    readonlyParameters: List<DocumentationParameter>,
    val returnType: String?,
    val receiver: String?,
    val source: DocumentationSource?,
    imports: List<String> = emptyList(),
    parents: List<String> = emptyList(),
    val inheritedFrom: String? = null,
    references: List<DocumentationReference> = emptyList(),
) {
    val tags: List<DocumentationTag> = frozen(tags)
    val parameters: List<DocumentationParameter> = frozen(parameters)
    val readonlyParameters: List<DocumentationParameter> = frozen(readonlyParameters)
    val imports: List<String> = frozen(imports)
    val parents: List<String> = frozen(parents)
    val references: List<DocumentationReference> = frozen(references)

    internal fun withReferences(references: List<DocumentationReference>) = DocumentationSymbol(id, packageId, namespace, name, kind,
        owner, signature, access, markdown, tags, parameters, readonlyParameters, returnType, receiver, source, imports, parents, inheritedFrom, references)

    internal fun inherited(owner: String, id: String) = DocumentationSymbol(id, packageId, namespace, name, kind, owner,
        signature, access, markdown, tags, parameters, readonlyParameters, returnType, receiver, source, imports, emptyList(), this.id)
}

class DocumentationModel(val title: String, symbols: List<DocumentationSymbol>, warnings: List<String> = emptyList()) {
    val schemaVersion: Int = 1
    val symbols: List<DocumentationSymbol> = frozen(symbols)
    val warnings: List<String> = frozen(warnings)
}

internal fun <T> frozen(items: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(items))
internal fun Document.exportTags(): List<DocumentationTag> = tags.map {
    DocumentationTag(it.tag, when (it) {
        is ParamTag -> it.param
        is ThrowsTag -> it.exception
        is ContextTag -> it.context
        is PropertyTag -> it.property
        else -> null
    }, it.content)
}
