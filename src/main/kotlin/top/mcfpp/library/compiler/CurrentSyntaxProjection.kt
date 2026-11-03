package top.mcfpp.library.compiler

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.library.syntax.*

/** Declaration facts are supplied from the author's bound D/B directory, never from source snippets. */
interface StatementProjectionBindings {
    fun body(reference: BodyId): SemanticNode
    fun name(declaration: DeclarationId): String
    fun local(declaration: DeclarationId): LocalDeclarationFacts
    fun lambdaParameter(declaration: DeclarationId): LambdaParameterSyntax
    fun templateDeclaration(declaration: DeclarationId): ProjectionTemplateDeclaration =
        throw SyntaxProjectionException("LIB_BODY_UNSUPPORTED", "Template declaration metadata is unavailable: $declaration")
    fun annotation(reference: AnnotationId): ProjectionAnnotation =
        throw SyntaxProjectionException("LIB_BODY_UNSUPPORTED", "Annotation metadata is unavailable: $reference")
    fun annotationProvider(reference: DeclarationReference): String =
        throw SyntaxProjectionException("LIB_BODY_UNSUPPORTED", "Annotation provider metadata is unavailable: $reference")
    fun anonymousData(declaration: DeclarationId): ProjectionAnonymousData =
        throw SyntaxProjectionException("LIB_TYPE_UNSUPPORTED", "Anonymous declaration metadata is unavailable: $declaration")
}

enum class ProjectionAccess { PUBLIC, PROTECTED, PRIVATE }
enum class ProjectionVariance { INVARIANT, IN, OUT }

data class ProjectionParameter(
    val declaration: DeclarationId, val name: String, val type: TypeSyntax?, val defaultBody: BodyId? = null,
    val variance: ProjectionVariance = ProjectionVariance.INVARIANT
)
data class ProjectionAnnotation(val name: String, val arguments: List<Expression>)
data class ProjectionAnonymousData(val parents: List<TypeSyntax>, val body: TemplateBody)

/** JVM paths are explicit provider identifiers split into components, not executable source. */
sealed interface ProjectionAccessor {
    data object Simple : ProjectionAccessor
    data class Block(val declaration: DeclarationId, val body: BodyId) : ProjectionAccessor
    data class ExpressionBody(val declaration: DeclarationId, val body: BodyId) : ProjectionAccessor
    data class Native(val declaration: DeclarationId, val javaPath: List<String>) : ProjectionAccessor
}

sealed interface ProjectionTemplateDeclaration {
    val access: ProjectionAccess?
    data class Field(
        val name: String, val constant: Boolean, val type: TypeSyntax?, val nullable: Boolean = false,
        val initializer: BodyId? = null, val getter: ProjectionAccessor? = null, val setter: ProjectionAccessor? = null,
        override val access: ProjectionAccess? = null
    ) : ProjectionTemplateDeclaration
    data class Function(
        val name: String, val readonly: List<ProjectionParameter>, val normal: List<ProjectionParameter>,
        val result: TypeSyntax, val body: BodyId?, val isOverride: Boolean = false, val isAbstract: Boolean = false,
        override val access: ProjectionAccess? = null
    ) : ProjectionTemplateDeclaration
    data class Constructor(
        val normal: List<ProjectionParameter>, val body: BodyId, override val access: ProjectionAccess? = null
    ) : ProjectionTemplateDeclaration
    data class Operation(
        val operator: String, val readonly: List<ProjectionParameter>, val normal: List<ProjectionParameter>,
        val result: TypeSyntax, val body: BodyId? = null, val javaPath: List<String>? = null,
        override val access: ProjectionAccess? = null
    ) : ProjectionTemplateDeclaration
}

/** Session facade used by a body repository; each call creates new contexts and tokens. */
class CurrentSyntaxProjection(bindings: ProjectionBindings, val declarations: StatementProjectionBindings) {
    val statements = StatementProjection(bindings, declarations)
    val expressions: ExpressionProjection get() = statements.expressions
    val source: ProjectionSource get() = expressions.source
    fun block(body: BodyId): P.CurlBlockContext = statements.block(statements.bodyBlock(body))
    fun block(root: Statement.Block): P.CurlBlockContext = statements.block(root)
    fun statement(node: Statement): P.StatementContext = statements.statement(node)
    fun expression(node: Expression): P.ExpressionContext = expressions.expression(node)
    fun defaultValue(body: BodyId): P.ValueContext = expressions.value(statements.bodyExpression(body))
    fun defaultValue(node: Expression): P.ValueContext = expressions.value(node)
    fun type(node: TypeSyntax): P.TypeContext = expressions.type(node)
    fun template(node: TemplateBody): P.TemplateBodyContext = statements.template(node)
    fun lambda(node: Expression.Lambda): P.InlineLambdaContext = statements.lambda(node)
    fun origin(context: ParserRuleContext): ProjectionOrigin? = expressions.origin(context)
    fun declaration(context: ParserRuleContext): DeclarationId? = statements.declaration(context)
    fun sourceSlice(context: ParserRuleContext): String = source.slice(context)
}
