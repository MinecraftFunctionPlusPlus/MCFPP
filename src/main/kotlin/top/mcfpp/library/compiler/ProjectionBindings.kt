package top.mcfpp.library.compiler

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.library.syntax.*

/** Display spelling and the already resolved author-session binding. Never resolve it against caller imports. */
data class ProjectedName(val name: String, val namespace: String? = null, val binding: Any? = null)

interface ProjectionBindings {
    fun name(use: NameUse, role: NameRole): ProjectedName
    fun constant(reference: ConstantRef): ConstantValue
    fun type(reference: TypeRef): Any? = null
    fun builtinKey(reference: TypeRef): String? = null
    fun diagnosticOrigin(node: SemanticNode): Any? = null
}

/** Session-only facts; none of these objects are part of the release encoding. */
data class ProjectionOrigin(
    val node: SemanticNode,
    val nameUse: NameUse? = null,
    val boundName: ProjectedName? = null,
    val constantReference: ConstantRef? = null,
    val constant: ConstantValue? = null,
    val typeReference: TypeRef? = null,
    val boundType: Any? = null,
    val diagnosticOrigin: Any? = null
)

interface ProjectionStructures {
    fun lambda(node: Expression.Lambda, projection: ExpressionProjection): Parser.InlineLambdaContext =
        throw SyntaxProjectionException("LIB_BODY_UNSUPPORTED", "Lambda projection requires its declaration/body provider")
    fun anonymousType(node: TypeSyntax.AnonymousData, projection: ExpressionProjection): Parser.AnonymousTemplateTypeContext =
        throw SyntaxProjectionException("LIB_TYPE_UNSUPPORTED", "Anonymous type projection requires its declaration provider")
}

class SyntaxProjectionException(val code: String, message: String) : IllegalArgumentException(message)
