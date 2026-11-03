package top.mcfpp.library.syntax

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppParser as Parser

/** Mutable arenas belong to capture, never to the returned DTOs. */
interface StatementCaptureBindings : ExpressionCaptureBindings {
    /** Enter a lexical Q and restore the previous Q in finally. owner is set for a new lambda D. */
    fun <T> withScope(site: ParserRuleContext, owner: DeclarationId?, action: (ScopeId) -> T): T
    /** Reserve a D shell without inserting its name into the visible bindings. */
    fun reserveLocal(site: Parser.FieldDeclarationContext): DeclarationId
    fun completeLocal(declaration: DeclarationId, facts: LocalDeclarationFacts)
    fun publishLocal(declaration: DeclarationId, name: String, site: ParserRuleContext)
    fun reserveIterationVariable(site: Parser.ForeachStatementContext, iterable: Expression): DeclarationId
    fun destination(name: String, site: Parser.TryStoreStatementContext): NameUse
    /** Resolve against the execute field directory, rather than ordinary lexical variables. */
    fun executePathPart(site: Parser.VarContext, capture: StatementBridge): Expression
    fun reserveLambda(site: Parser.InlineLambdaContext): LambdaAllocation
    fun completeLambdaParameters(allocation: LambdaAllocation, parameters: List<LambdaParameterSyntax>, explicit: Boolean)
    /** Store root and captures in B, with the same B referenced from the synthetic function D. */
    fun completeLambda(allocation: LambdaAllocation, body: LambdaBodyCapture)
    /** Map this/super's owner to its captured receiver declaration, or explicitly return null for static owners. */
    fun receiverCapture(owner: DeclarationReference): DeclarationReference?
    /** Resolve a declaration/candidate's required runtime receiver without changing its semantic reference. */
    fun declarationCapture(reference: DeclarationReference): DeclarationReference? = null
    /** Existing declaration capture owns access/flags, initializer/accessor bodies and annotation targeting. */
    fun templateMember(site: Parser.TemplateMemberDeclarationContext, capture: StatementBridge): TemplateMember
    /** Return a semantic recipe for raw template annotations; null preserves the existing A/H path. */
    fun annotationConstruction(site: Parser.AnnotationContext, capture: StatementBridge): TemplateMember.AnnotationConstruction? = null
    fun sourceLocation(site: ParserRuleContext): SourceLocation? = null
    fun recorded(node: SemanticNode, location: SourceLocation?) {}
}

class StatementBridge(
    private val bindings: StatementCaptureBindings,
    expressions: ExpressionBridge? = null
) {
    /** Default capture routes nested lambdas here without requiring a circular bindings setup. */
    val expressions: ExpressionBridge = expressions ?: ExpressionBridge(object : ExpressionCaptureBindings by bindings {
        override fun lambda(site: Parser.InlineLambdaContext, capture: ExpressionBridge): Expression.Lambda =
            this@StatementBridge.lambda(site)
    })

    private data class LambdaFrame(
        val allocation: LambdaAllocation,
        val locals: MutableSet<DeclarationId>,
        val captures: LinkedHashSet<DeclarationReference> = linkedSetOf()
    )
    private val lambdas = mutableListOf<LambdaFrame>()

    fun expression(site: ParserRuleContext): Expression = expressions.expression(site).also(::readExpression)
    fun type(site: Parser.TypeContext): TypeSyntax = expressions.type(site).also(::readType)

    fun block(site: Parser.CurlBlockContext): Statement.Block {
        requireValid(site)
        return bindings.withScope(site, null) { scope -> blockContents(site.statement(), scope, site) }
    }

    fun block(site: Parser.BlockContext): Statement {
        requireValid(site)
        return bindings.withScope(site, null) { scope -> blockContents(site, scope) }
    }

    fun statement(site: Parser.StatementContext): Statement {
        requireValid(site)
        return captureStatement(site)
    }

    fun template(site: Parser.TemplateBodyContext): TemplateBody {
        requireValid(site)
        return bindings.withScope(site, null) { scope ->
            val members = site.templateMemberDeclaration().map { member ->
                val construction = member.templateMember().annotation()?.let { bindings.annotationConstruction(it, this) }
                (construction ?: bindings.templateMember(member, this)).also {
                    if (it.scope != scope) unsupported("Template member must retain the template body scope")
                    record(it, member)
                }
            }
            record(TemplateBody(scope, members), site)
        }
    }

    /** Suitable as ExpressionCaptureBindings.lambda's implementation; statements and tail stay distinct. */
    fun lambda(site: Parser.InlineLambdaContext): Expression.Lambda {
        requireValid(site)
        val bodySite = site.lambdaBody() ?: unsupported("Legacy lambda syntax cannot be exported")
        val outerScope = bindings.scope(site)
        val explicit = site.lambdaParameterList() != null
        val allocation = bindings.reserveLambda(site)
        val sourceParameters = site.lambdaParameterList()?.lambdaParameter().orEmpty()
        if (allocation.parameters.map { it.declaration }.distinct().size != allocation.parameters.size ||
            allocation.parameters.any { it.declaration == allocation.declaration })
            unsupported("Lambda parameter declarations must have distinct identities")
        if (explicit) {
            if (allocation.parameters.map { it.name } != sourceParameters.map { it.Identifier().text })
                unsupported("Lambda allocation did not preserve its explicit parameters")
        } else if (allocation.parameters.size > 1 || allocation.parameters.any { it.name != "it" }) {
            unsupported("An implicit lambda can only allocate the inferred it parameter")
        }
        if (lambdas.any { it.allocation.declaration == allocation.declaration })
            unsupported("Each nested lambda requires its own declaration")

        val frame = LambdaFrame(allocation, allocation.parameters.mapTo(linkedSetOf()) { it.declaration })
        lambdas += frame
        val body = try {
            bindings.withScope(bodySite, allocation.declaration) { scope ->
                val parameters = allocation.parameters.mapIndexed { index, slot ->
                    LambdaParameterSyntax(slot.declaration, slot.name,
                        if (explicit) sourceParameters[index].type()?.let(::type) else null)
                }
                bindings.completeLambdaParameters(allocation, parameters, explicit)
                allocation.parameters.forEach { bindings.publishLocal(it.declaration, it.name, site) }
                val root = blockContents(bodySite.statement(), scope, bodySite)
                val tail = bodySite.lambdaTailExpression()?.expression()?.let(::expression)
                LambdaBodyCapture(root, tail, frame.captures.toList())
            }
        } finally {
            check(lambdas.removeAt(lambdas.lastIndex) === frame)
        }
        bindings.completeLambda(allocation, body)
        return record(Expression.Lambda(outerScope, allocation.declaration,
            allocation.parameters.map { it.declaration }, explicit, allocation.body, body.tail), site)
    }

    private fun blockContents(site: Parser.BlockContext, scope: ScopeId): Statement =
        site.curlBlock()?.let { blockContents(it.statement(), scope, it) }
            ?: captureStatement(site.statement())

    private fun blockContents(statements: List<Parser.StatementContext>, scope: ScopeId, site: ParserRuleContext) =
        record(Statement.Block(scope, statements.map(::captureStatement)), site)

    private fun captureStatement(site: Parser.StatementContext): Statement {
        val scope = bindings.scope(site)
        val result: Statement = when {
            site.fieldDeclaration() != null -> local(site.fieldDeclaration(), scope)
            site.statementExpression() != null -> {
                val value = site.statementExpression()
                value.varWithSelector()?.let { target ->
                    Statement.Assign(scope, expression(target), value.assignmentOperator().text, expression(value.expression()))
                } ?: Statement.Evaluate(scope, expression(value.expression()))
            }
            site.ifStatement() != null -> {
                val condition = site.ifStatement()
                if (condition.conditionExpression() == null) unsupported("Legacy if syntax cannot be exported")
                val branches = mutableListOf(ConditionalBranch(expression(condition.conditionExpression()), block(condition.block())))
                condition.elseIfStatement().forEach { branch ->
                    if (branch.conditionExpression() == null) unsupported("Legacy else-if syntax cannot be exported")
                    branches += ConditionalBranch(expression(branch.conditionExpression()), block(branch.block()))
                }
                Statement.If(scope, branches.toList(), condition.elseStatement()?.let { block(it.block()) })
            }
            site.whileStatement() != null -> {
                val loop = site.whileStatement()
                if (loop.conditionExpression() == null) unsupported("Legacy while syntax cannot be exported")
                Statement.While(scope, expression(loop.conditionExpression()), block(loop.block()))
            }
            site.doWhileStatement() != null -> {
                val loop = site.doWhileStatement()
                val body = block(loop.block())
                // The body scope has been left before resolving the condition.
                Statement.DoWhile(scope, body, expression(loop.expression()))
            }
            site.foreachStatement() != null -> {
                val loop = site.foreachStatement()
                val iterable = expression(loop.expression())
                val variable = bindings.reserveIterationVariable(loop, iterable)
                val body = bindings.withScope(loop.block(), null) { bodyScope ->
                    publish(variable, loop.Identifier().text, loop)
                    blockContents(loop.block(), bodyScope)
                }
                Statement.Foreach(scope, variable, iterable, body)
            }
            site.tryStoreStatement() != null -> {
                val store = site.tryStoreStatement()
                val destination = bindings.destination(store.Identifier().text, store)
                readName(destination)
                Statement.TryStore(scope, block(store.block()), destination)
            }
            site.executeStatement() != null -> {
                val execute = site.executeStatement()
                val contexts = execute.executeContext().map { context ->
                    val path = context.executeExpression()
                    val capturedPath = if (path.AS() != null) ExecutePath.As else ExecutePath.Members(path.`var`().map {
                        bindings.executePathPart(it, this).also(::readExpression)
                    })
                    ExecuteContext(capturedPath, expression(context.expression()))
                }
                Statement.Execute(scope, contexts, block(execute.block()))
            }
            site.orgCommand() != null -> Statement.Command(scope, expressions.commandParts(site.orgCommand()).also { parts ->
                parts.filterIsInstance<TextPart.Interpolation>().forEach { readExpression(it.expression) }
            })
            site.controlStatement() != null -> if (site.controlStatement().BREAK() != null)
                Statement.Break(scope) else Statement.Continue(scope)
            site.returnStatement() != null -> Statement.Return(scope, site.returnStatement().expression()?.let(::expression))
            site.SEMICOLON() != null -> Statement.Empty(scope)
            else -> unsupported("Statement rule has no persistent operation")
        }
        return record(result, site)
    }

    private fun local(site: Parser.FieldDeclarationContext, scope: ScopeId): Statement.Local {
        val declaration = bindings.reserveLocal(site)
        val storage = when {
            site.fieldModifier()?.CONST() != null -> LocalStorage.CONST
            site.fieldModifier()?.EXTERNAL() != null -> LocalStorage.EXTERNAL
            site.fieldModifier()?.IMPORT() != null -> LocalStorage.IMPORT_VAR
            else -> LocalStorage.VAR
        }
        val declaredType = site.type()?.let(::type)
        val initializer = site.expression()?.let(::expression)
        if (declaredType == null && initializer == null) unsupported("A local requires a type or initializer")
        val name = site.Identifier().text
        bindings.completeLocal(declaration, LocalDeclarationFacts(name, storage, declaredType, initializer))
        publish(declaration, name, site)
        return Statement.Local(scope, declaration, initializer)
    }

    private fun publish(declaration: DeclarationId, name: String, site: ParserRuleContext) {
        bindings.publishLocal(declaration, name, site)
        lambdas.lastOrNull()?.locals?.add(declaration)
    }

    private fun readName(use: NameUse) {
        when (use) {
            is NameUse.Local -> captureReference(DeclarationReference.Local(use.target))
            is NameUse.Declaration -> if (lambdas.isNotEmpty()) bindings.declarationCapture(use.target)?.let(::captureReference)
            is NameUse.Lookup -> if (lambdas.isNotEmpty()) use.candidates.forEach { bindings.declarationCapture(it)?.let(::captureReference) }
        }
    }

    /** Propagate through intervening closures, stopping at the closure that owns the declaration. */
    private fun captureReference(reference: DeclarationReference) {
        for (index in lambdas.indices.reversed()) {
            val frame = lambdas[index]
            if (reference is DeclarationReference.Local && reference.target in frame.locals) break
            frame.captures += reference
        }
    }

    private fun readExpression(value: Expression) {
        when (value) {
            is Expression.Literal, is Expression.Selector, is Expression.Coordinate, is Expression.NbtArray -> Unit
            is Expression.Name -> readName(value.use)
            is Expression.This -> if (lambdas.isNotEmpty()) bindings.receiverCapture(value.owner)?.let(::captureReference) else Unit
            is Expression.Super -> if (lambdas.isNotEmpty()) bindings.receiverCapture(value.owner)?.let(::captureReference) else Unit
            is Expression.TypeValue -> readType(value.type)
            is Expression.Group -> value.value?.let(::readExpression)
            is Expression.Unary -> readExpression(value.operand)
            is Expression.Binary -> { readExpression(value.left); readExpression(value.right) }
            is Expression.Cast -> { readExpression(value.value); readType(value.target) }
            is Expression.Member -> readExpression(value.receiver)
            is Expression.Index -> { readExpression(value.receiver); value.index?.let(::readExpression) }
            is Expression.PropertyFilter -> { readExpression(value.receiver); value.entries.forEach { readExpression(it.value) } }
            is Expression.Call -> {
                readExpression(value.callee)
                value.readonly.forEach(::readExpression)
                value.normal.forEach { readExpression(it.expression) }
                value.trailingLambda?.let(::readExpression)
            }
            // Its own frame already processed statements/tail and propagated captures to outer frames.
            is Expression.Lambda -> Unit
            is Expression.Range -> { value.lower?.let(::readExpression); value.upper?.let(::readExpression) }
            is Expression.ListValue -> value.elements.forEach(::readExpression)
            is Expression.Compound -> value.entries.forEach { readExpression(it.value) }
            is Expression.InterpolatedString -> value.parts.filterIsInstance<TextPart.Interpolation>().forEach { readExpression(it.expression) }
        }
    }

    private fun readType(value: TypeSyntax) {
        when (value) {
            is TypeSyntax.Inferred -> readExpression(value.expression)
            is TypeSyntax.Name -> readName(value.use)
            is TypeSyntax.Apply -> { readType(value.base); value.readonly.forEach(::readExpression) }
            is TypeSyntax.Container -> readType(value.argument)
            is TypeSyntax.Union -> value.alternatives.forEach(::readType)
            is TypeSyntax.Intersection -> value.components.forEach(::readType)
            is TypeSyntax.Function -> { value.parameters.forEach(::readType); readType(value.result) }
            is TypeSyntax.Vector, is TypeSyntax.Entity, is TypeSyntax.AnonymousData, is TypeSyntax.Void -> Unit
        }
    }

    private fun <T : SemanticNode> record(node: T, site: ParserRuleContext): T = node.also {
        bindings.recorded(it, bindings.sourceLocation(site))
    }

    private fun requireValid(site: ParseTree) {
        if (site is ErrorNode || (site is ParserRuleContext && site.exception != null))
            unsupported("Recovered parser syntax cannot be exported")
        for (index in 0 until site.childCount) requireValid(site.getChild(index))
    }
    private fun unsupported(message: String): Nothing = throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", message)

    companion object {
        /** Optional debug extraction only. The semantic tree never needs this text or the input stream. */
        fun sourceLocation(site: ParserRuleContext, logicalUnit: String): SourceLocation? {
            val first = site.start ?: return null
            val last = site.stop ?: return null
            val input = first.inputStream ?: return null
            if (first.startIndex < 0 || last.stopIndex < first.startIndex) return null
            fun position(exclusive: Int): SourcePosition {
                val prefix = if (exclusive == 0) "" else input.getText(Interval.of(0, exclusive - 1))
                return SourcePosition(prefix.length.toLong(), prefix.count { it == '\n' } + 1,
                    prefix.length - prefix.lastIndexOf('\n'))
            }
            // ANTLR CharStreams can count code points; String.length gives the promised UTF-16 units.
            return SourceLocation(logicalUnit, position(first.startIndex), position(last.stopIndex + 1))
        }
    }
}
