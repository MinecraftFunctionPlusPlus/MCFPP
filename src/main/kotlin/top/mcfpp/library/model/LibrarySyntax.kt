package top.mcfpp.library.model

import top.mcfpp.library.syntax.*
import java.util.IdentityHashMap

enum class BodyRole(val wireValue: Int) {
    FUNCTION(1), CONSTRUCTOR(2), DEFAULT(3), INITIALIZER(4), LAMBDA(5), TOP_LEVEL(6)
}

data class ScopeRecord(val owner: DeclarationId, val parent: ScopeId?, val bindings: List<DeclarationId>)
data class BodyRecord(
    val owner: DeclarationId,
    val environment: EnvironmentId,
    val role: BodyRole,
    val root: NodeRef,
    val captures: List<DeclarationReference> = emptyList(),
)

data class SyntaxBodySource(
    val owner: DeclarationId,
    val environment: EnvironmentId,
    val role: BodyRole,
    val root: SemanticNode,
    val captures: List<DeclarationReference> = emptyList(),
)

/** A declaration header, parameter type, parent expression, or another independently referenced N root. */
data class SyntaxRoot(val owner: DeclarationId, val path: List<Long>, val node: SemanticNode) {
    init { require(path.all { it in 0..0xffff_ffffL }) }
}

/** D/E/T/C/A binding belongs to the enclosing import session, not to the N/Q/B codec. */
interface SyntaxReferenceValidator {
    fun declaration(reference: DeclarationReference)
    fun environment(reference: EnvironmentId)
    fun type(reference: TypeRef)
    fun constant(reference: ConstantRef)
    fun annotation(reference: AnnotationId)
}

/** B/Q references retain their producer-assigned table positions; N references index [nodes]. */
class LibrarySyntax(nodes: List<SemanticNode>, scopes: List<ScopeRecord>, bodies: List<BodyRecord>) {
    val nodes: List<SemanticNode> = frozen(nodes)
    val scopes: List<ScopeRecord> = frozen(scopes.map { it.copy(bindings = frozen(it.bindings)) })
    val bodies: List<BodyRecord> = frozen(bodies.map { it.copy(captures = frozen(it.captures)) })
    private val references = IdentityHashMap<SemanticNode, NodeRef>().apply {
        nodes.forEachIndexed { index, node ->
            require(put(node, NodeRef(index + 1L)) == null) { "The same node cannot occupy two N records" }
        }
    }

    /** Set only by SyntaxCodec.validateAll after every external reference callback succeeds. */
    var externalReferencesValidated: Boolean = false
        internal set

    fun reference(node: SemanticNode): NodeRef = requireNotNull(references[node]) { "Child node is absent from N" }
    fun node(reference: NodeRef): SemanticNode {
        require(reference.value <= nodes.size.toLong()) { "N reference ${reference.value} is out of bounds" }
        return nodes[(reference.value - 1).toInt()]
    }
    fun scope(reference: ScopeId): ScopeRecord {
        require(reference.value <= scopes.size.toLong()) { "Q reference ${reference.value} is out of bounds" }
        return scopes[(reference.value - 1).toInt()]
    }
    fun body(reference: BodyId): BodyRecord {
        require(reference.value <= bodies.size.toLong()) { "B reference ${reference.value} is out of bounds" }
        return bodies[(reference.value - 1).toInt()]
    }
}
