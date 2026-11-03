package top.mcfpp.model.function

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.library.compiler.*
import top.mcfpp.library.syntax.*
import java.util.IdentityHashMap
import java.util.UUID

data class ClosedReadonlyBinding(
    val type: top.mcfpp.type.MCFPPType,
    val value: top.mcfpp.analysis.CompilerValue,
    val descriptors: Map<top.mcfpp.type.TypeId, top.mcfpp.type.MCFPPType> = emptyMap()
)

/** Pure declared-type substitution may consume immutable readonly facts before runtime adapters exist. */
interface StructuredFunctionSignatures {
    fun declaredType(type: top.mcfpp.type.MCFPPType, function: Function,
                     readonly: Map<String, ClosedReadonlyBinding>): top.mcfpp.type.MCFPPType?
}

/** Session identities. These handles are never serialized into a published library. */
data class SessionLibraryId internal constructor(val value: UUID)
data class BodyHandle internal constructor(
    val library: SessionLibraryId,
    val body: BodyId,
    internal val repository: SessionBodyRepository
) {
    fun project(): ProjectedBody = repository.project(this)
    fun requireOpen() = repository.requireOpen()
}

data class BoundBody(
    val handle: BodyHandle,
    val root: SemanticNode,
    val bindings: ProjectionBindings,
    val declarations: StatementProjectionBindings
)

open class ProjectedSyntax internal constructor(val bound: BoundBody, val syntax: CurrentSyntaxProjection,
                                               open val context: ParserRuleContext) {
    val handle: BodyHandle get() = bound.handle
    fun origin(context: ParserRuleContext): ProjectionOrigin? { handle.requireOpen(); return syntax.origin(context) }
    fun declaration(context: ParserRuleContext): DeclarationId? { handle.requireOpen(); return syntax.declaration(context) }
}

class ProjectedBody internal constructor(bound: BoundBody, syntax: CurrentSyntaxProjection,
                                        override val context: P.CurlBlockContext) : ProjectedSyntax(bound, syntax, context)

/** Owns author bindings and every projection index until its compilation session closes. */
class SessionBodyRepository : AutoCloseable {
    private data class Library(val bindings: ProjectionBindings, val declarations: StatementProjectionBindings)
    private val libraries = linkedMapOf<SessionLibraryId, Library>()
    private var open = true
    fun requireOpen() { check(open) { "The structured body compilation session is closed" } }

    fun register(bindings: ProjectionBindings, declarations: StatementProjectionBindings): SessionLibraryId {
        requireOpen()
        return SessionLibraryId(UUID.randomUUID()).also { libraries[it] = Library(bindings, declarations) }
    }

    fun handle(library: SessionLibraryId, body: BodyId): BodyHandle {
        requireOpen()
        val entry = requireNotNull(libraries[library]) { "Library does not belong to this body session" }
        entry.declarations.body(body)
        return BodyHandle(library, body, this)
    }

    fun body(handle: BodyHandle): BoundBody {
        requireOpen()
        require(handle.repository === this) { "Body handle belongs to another compilation session" }
        val library = requireNotNull(libraries[handle.library]) { "Body library is unavailable" }
        return BoundBody(handle, library.declarations.body(handle.body), library.bindings, library.declarations)
    }

    /** A call/analysis gets new mutable context and token objects, backed by the immutable body DTO. */
    fun project(handle: BodyHandle): ProjectedBody {
        val body = body(handle)
        val root = body.root as? Statement.Block
            ?: throw SyntaxProjectionException("LIB_BODY_UNSUPPORTED", "A function body requires a statement block")
        val syntax = CurrentSyntaxProjection(body.bindings, body.declarations)
        return ProjectedBody(body, syntax, syntax.block(root)).also(StructuredBodyContexts::register)
    }

    /** Defaults, types, templates and callbacks can register their own structured root in this same session. */
    fun registerProjection(handle: BodyHandle, syntax: CurrentSyntaxProjection, context: ParserRuleContext): ProjectedSyntax {
        val body = body(handle)
        require(syntax.declarations === body.declarations && syntax.expressions.bindings === body.bindings) {
            "A projection must use the author bindings owned by its body repository"
        }
        return ProjectedSyntax(body, syntax, context).also(StructuredBodyContexts::register)
    }

    override fun close() {
        if (!open) return
        open = false
        StructuredBodyContexts.release(this)
        libraries.clear()
    }
}

/** Current-consumer entry point. Closing the owning repository removes every indexed context. */
object StructuredBodyContexts {
    private val contexts = IdentityHashMap<ParserRuleContext, ProjectedSyntax>()
    internal fun register(body: ProjectedSyntax) {
        fun visit(context: ParserRuleContext) {
            contexts[context] = body
            context.children.orEmpty().filterIsInstance<ParserRuleContext>().forEach(::visit)
        }
        visit(body.context)
    }
    internal fun release(repository: SessionBodyRepository) {
        contexts.entries.removeIf { it.value.handle.repository === repository }
    }
    fun projected(context: ParserRuleContext): ProjectedSyntax? = contexts[context]?.also { it.handle.requireOpen() }
    fun origin(context: ParserRuleContext): ProjectionOrigin? = projected(context)?.origin(context)
    fun declaration(context: ParserRuleContext): DeclarationId? = projected(context)?.declaration(context)
    fun bindings(context: ParserRuleContext): ProjectionBindings? = projected(context)?.bound?.bindings
    fun body(context: ParserRuleContext): BodyHandle? = projected(context)?.handle
    fun projection(context: ParserRuleContext): CurrentSyntaxProjection? = projected(context)?.syntax
}
