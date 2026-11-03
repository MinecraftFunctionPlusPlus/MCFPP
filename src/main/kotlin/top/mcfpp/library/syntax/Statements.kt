package top.mcfpp.library.syntax

/** Statement operations; nested bodies remain structured nodes. */
sealed interface Statement : SemanticNode {
    data class Block(override val scope: ScopeId, val statements: List<Statement>) : Statement {
        override val tag: Int get() = 0x0301
    }
    data class Local(override val scope: ScopeId, val declaration: DeclarationId, val initializer: Expression?) : Statement {
        override val tag: Int get() = 0x0302
    }
    data class Evaluate(override val scope: ScopeId, val value: Expression) : Statement {
        override val tag: Int get() = 0x0303
    }
    data class Assign(
        override val scope: ScopeId, val target: Expression, val operator: String, val value: Expression
    ) : Statement {
        init { require(operator in setOf("=", "+=", "-=", "*=", "/=", "%=")) }
        override val tag: Int get() = 0x0304
    }
    data class If(override val scope: ScopeId, val branches: List<ConditionalBranch>, val elseBody: Statement?) : Statement {
        init { require(branches.isNotEmpty()) }
        override val tag: Int get() = 0x0305
    }
    data class While(override val scope: ScopeId, val condition: Expression, val body: Statement) : Statement {
        override val tag: Int get() = 0x0306
    }
    data class DoWhile(override val scope: ScopeId, val body: Statement, val condition: Expression) : Statement {
        override val tag: Int get() = 0x0307
    }
    data class Foreach(
        override val scope: ScopeId, val variable: DeclarationId, val iterable: Expression, val body: Statement
    ) : Statement { override val tag: Int get() = 0x0308 }
    data class TryStore(override val scope: ScopeId, val body: Statement, val destination: NameUse) : Statement {
        override val tag: Int get() = 0x0309
    }
    data class Execute(override val scope: ScopeId, val contexts: List<ExecuteContext>, val body: Statement) : Statement {
        override val tag: Int get() = 0x030a
    }
    data class Command(override val scope: ScopeId, val parts: List<TextPart>) : Statement {
        override val tag: Int get() = 0x030b
    }
    data class Break(override val scope: ScopeId) : Statement { override val tag: Int get() = 0x030c }
    data class Continue(override val scope: ScopeId) : Statement { override val tag: Int get() = 0x030d }
    data class Return(override val scope: ScopeId, val value: Expression?) : Statement {
        override val tag: Int get() = 0x030e
    }
    data class Empty(override val scope: ScopeId) : Statement { override val tag: Int get() = 0x030f }
}

data class ConditionalBranch(val condition: Expression, val body: Statement)
data class ExecuteContext(val path: ExecutePath, val value: Expression)
sealed interface ExecutePath {
    data object As : ExecutePath
    data class Members(val parts: List<Expression>) : ExecutePath { init { require(parts.isNotEmpty()) } }
}

@JvmInline value class AnnotationId(val value: Long) {
    init { require(value in 1..0xffff_ffffL) { "Annotation reference must be a positive u32" } }
}

data class TemplateBody(override val scope: ScopeId, val members: List<TemplateMember>) : SemanticNode {
    override val tag: Int get() = 0x0401
}
sealed interface TemplateMember : SemanticNode {
    data class Declaration(override val scope: ScopeId, val declaration: DeclarationId) : TemplateMember {
        override val tag: Int get() = 0x0402
    }
    data class AnnotationApplication(
        override val scope: ScopeId, val annotation: AnnotationId, val targets: List<DeclarationId>
    ) : TemplateMember { override val tag: Int get() = 0x0403 }
    /** Source construction deferred until the template's readonly arguments have been bound. */
    data class AnnotationConstruction(
        override val scope: ScopeId, val provider: DeclarationReference,
        val arguments: List<Expression>, val targets: List<DeclarationId>
    ) : TemplateMember {
        init {
            require(targets.isNotEmpty()) { "An annotation construction requires a target declaration" }
            require(targets.distinct().size == targets.size) { "Annotation construction targets must be unique" }
        }
        override val tag: Int get() = 0x0404
    }
}

enum class LocalStorage(val wireValue: Int) { VAR(1), CONST(2), EXTERNAL(3), IMPORT_VAR(4) }

/** Declaration arena input; the initializer is owned by Statement.Local, not another executable B. */
data class LocalDeclarationFacts(
    val name: String, val storage: LocalStorage, val declaredType: TypeSyntax?, val initializer: Expression?
)
data class LambdaParameterSlot(val declaration: DeclarationId, val name: String)
data class LambdaParameterSyntax(val declaration: DeclarationId, val name: String, val declaredType: TypeSyntax?)
data class LambdaAllocation(val declaration: DeclarationId, val body: BodyId, val parameters: List<LambdaParameterSlot>)
data class LambdaBodyCapture(
    val root: Statement.Block, val tail: Expression?, val captures: List<DeclarationReference>
)
