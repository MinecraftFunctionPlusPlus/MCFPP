package top.mcfpp.analysis.lambda

import top.mcfpp.analysis.StructuredBindings
import top.mcfpp.analysis.StructuredSessionBindings
import top.mcfpp.core.lang.Var
import top.mcfpp.library.compiler.StatementProjectionBindings
import top.mcfpp.library.syntax.*
import top.mcfpp.model.function.BodyHandle
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.StructuredBodyContexts
import top.mcfpp.model.function.StructuredFunctionSignatures
import top.mcfpp.model.function.ClosedReadonlyBinding
import top.mcfpp.type.MCFPPType
import top.mcfpp.antlr.mcfppParser as P

/** Session-only lambda lowering. The published B excludes its distinct tail. */
internal object StructuredLambdaBodies {
    fun empty(creator: Function): BodyHandle? {
        val owner = StructuredBindings.frame(creator).bodyHandle ?: return null
        val original = owner.repository.body(owner)
        val root = Statement.Block(original.root.scope, emptyList())
        val declarations = object : StatementProjectionBindings by original.declarations {
            override fun body(reference: BodyId): SemanticNode = if (reference == owner.body) root else original.declarations.body(reference)
        }
        val library = owner.repository.register(original.bindings, declarations)
        return owner.repository.handle(library, owner.body)
    }

    fun handle(syntax: P.InlineLambdaContext, creator: Function, returnTail: Boolean): BodyHandle? {
        val owner = StructuredBodyContexts.body(syntax) ?: return null
        val lambda = StructuredBodyContexts.origin(syntax)?.node as? Expression.Lambda
            ?: error("LIB_BODY_UNSUPPORTED: Projected lambda lost its semantic node")
        val original = owner.repository.body(owner)
        val root = original.declarations.body(lambda.body) as? Statement.Block
            ?: error("LIB_BODY_UNSUPPORTED: Lambda body is not a block")
        val tail = lambda.tail?.let { if (returnTail) Statement.Return(root.scope, it) else Statement.Evaluate(root.scope, it) }
        val lowered = root.copy(statements = root.statements + listOfNotNull(tail))
        val declarations = object : StatementProjectionBindings by original.declarations {
            override fun body(reference: BodyId): SemanticNode = if (reference == lambda.body) lowered else original.declarations.body(reference)
        }
        val session = original.bindings as? StructuredSessionBindings
            ?: error("LIB_BODY_UNSUPPORTED: Lambda has no bound session provider")
        val own = linkedSetOf<DeclarationId>().apply { addAll(lambda.parameters) }
        fun locals(statement: Statement) {
            when (statement) {
                is Statement.Local -> own.add(statement.declaration)
                is Statement.Foreach -> { own.add(statement.variable); locals(statement.body) }
                is Statement.Block -> statement.statements.forEach(::locals)
                is Statement.If -> { statement.branches.forEach { locals(it.body) }; statement.elseBody?.let(::locals) }
                is Statement.While -> locals(statement.body)
                is Statement.DoWhile -> locals(statement.body)
                is Statement.Execute -> locals(statement.body)
                is Statement.TryStore -> locals(statement.body)
                else -> Unit
            }
        }
        locals(root)
        val lexical = StructuredBindings.frame(creator)
        val bindings = object : StructuredSessionBindings by session, StructuredFunctionSignatures {
            override fun boundType(reference: TypeRef, frame: Function): MCFPPType? =
                session.boundType(reference, lexical) ?: session.boundType(reference, frame)
            override fun dependentType(syntax: TypeSyntax, frame: Function): MCFPPType? =
                session.dependentType(syntax, lexical) ?: session.dependentType(syntax, frame)
            override fun dependentType(type: MCFPPType, frame: Function): MCFPPType? =
                session.dependentType(type, lexical) ?: session.dependentType(type, frame)
            override fun closedConstant(reference: ConstantRef, frame: Function) = session.closedConstant(reference, lexical)
            override fun declaredType(type: MCFPPType, function: Function, readonly: Map<String, ClosedReadonlyBinding>): MCFPPType? =
                (original.bindings as? StructuredFunctionSignatures)?.declaredType(type, lexical, readonly)
            override fun local(declaration: DeclarationId, frame: Function): Var<*>? {
                if (declaration in own) return session.local(declaration, frame)
                    ?: if (declaration in lambda.parameters) frame.scope.getVar(original.declarations.lambdaParameter(declaration).name) else null
                val source = session.local(declaration, lexical) ?: return null
                return (frame.scope as? LambdaCaptureScope)?.boundCapture(source) ?: source
            }
        }
        val library = owner.repository.register(bindings, declarations)
        return owner.repository.handle(library, lambda.body)
    }
}
