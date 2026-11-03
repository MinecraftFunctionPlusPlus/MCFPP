package top.mcfpp.library.compiler

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.Token
import top.mcfpp.antlr.mcfppLexer as L
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.library.syntax.*
import java.util.IdentityHashMap

/** Structural statement/declaration adapter. It never lexes or parses the display buffer. */
class StatementProjection(
    bindings: ProjectionBindings,
    val declarations: StatementProjectionBindings
) : ProjectionStructures {
    val expressions = ExpressionProjection(bindings, this)
    private val declarationOrigins = IdentityHashMap<ParserRuleContext, DeclarationId>()
    fun declaration(context: ParserRuleContext): DeclarationId? = declarationOrigins[context]
    fun origin(context: ParserRuleContext): ProjectionOrigin? = expressions.origin(context)

    private fun <T : ParserRuleContext> context(context: T, semantic: SemanticNode, build: T.() -> Unit): T =
        expressions.node(context, semantic, build)
    private fun <T : ParserRuleContext> child(parent: ParserRuleContext, child: T): T = expressions.child(parent, child)
    private fun token(parent: ParserRuleContext, type: Int, text: String): Token = expressions.token(parent, type, text)
    private fun space() = expressions.space()
    private fun <T : ParserRuleContext> declared(context: T, declaration: DeclarationId): T = context.also {
        declarationOrigins[it] = declaration
    }
    private fun separator(parent: ParserRuleContext, semantic: SemanticNode) = child(parent,
        context(P.SeparatorContext(null, -1), semantic) { token(this, L.NL, "\n") })

    fun bodyBlock(body: BodyId): Statement.Block = declarations.body(body) as? Statement.Block
        ?: unsupported("Body $body is not a statement block")
    fun bodyExpression(body: BodyId): Expression = declarations.body(body) as? Expression
        ?: unsupported("Body $body is not an expression")

    fun block(root: Statement.Block): P.CurlBlockContext = context(P.CurlBlockContext(null, -1), root) {
        token(this, L.LCURL, "{"); token(this, L.NL, "\n")
        root.statements.forEach { child(this, statement(it)); separator(this, root) }
        token(this, L.RCURL, "}")
    }

    fun blockBody(root: Statement): P.BlockContext = context(P.BlockContext(null, -1), root) {
        if (root is Statement.Block) child(this, block(root)) else child(this, statement(root))
    }

    fun statement(value: Statement): P.StatementContext = context(P.StatementContext(null, -1), value) {
        when (value) {
            is Statement.Block -> unsupported("The current grammar only permits a block in a body position")
            is Statement.Local -> {
                declared(this, value.declaration)
                child(this, local(value))
            }
            is Statement.Evaluate -> child(this, context(P.StatementExpressionContext(null, -1), value) {
                child(this, expressions.expression(value.value))
            })
            is Statement.Assign -> child(this, context(P.StatementExpressionContext(null, -1), value) {
                child(this, variableSelection(value.target))
                child(this, context(P.AssignmentOperatorContext(null, -1), value) {
                    token(this, assignmentOperator(value.operator), value.operator)
                })
                child(this, expressions.expression(value.value))
            })
            is Statement.If -> child(this, context(P.IfStatementContext(null, -1), value) {
                val first = value.branches.first()
                token(this, L.IF, "if"); space(); child(this, expressions.condition(first.condition)); space()
                child(this, blockBody(first.body))
                value.branches.drop(1).forEach { branch ->
                    space(); child(this, context(P.ElseIfStatementContext(null, -1), value) {
                        token(this, L.ELSE, "else"); space(); token(this, L.IF, "if"); space()
                        child(this, expressions.condition(branch.condition)); space(); child(this, blockBody(branch.body))
                    })
                }
                value.elseBody?.let { body ->
                    space(); child(this, context(P.ElseStatementContext(null, -1), value) {
                        token(this, L.ELSE, "else"); space(); child(this, blockBody(body))
                    })
                }
            })
            is Statement.While -> child(this, context(P.WhileStatementContext(null, -1), value) {
                token(this, L.WHILE, "while"); space(); child(this, expressions.condition(value.condition)); space()
                child(this, blockBody(value.body))
            })
            is Statement.DoWhile -> child(this, context(P.DoWhileStatementContext(null, -1), value) {
                token(this, L.DO, "do"); space(); child(this, blockBody(value.body)); space()
                token(this, L.WHILE, "while"); space(); child(this, expressions.expression(value.condition))
            })
            is Statement.Foreach -> child(this, declared(context(P.ForeachStatementContext(null, -1), value) {
                token(this, L.FOR, "for"); space(); token(this, L.LPAREN, "(")
                token(this, L.Identifier, declarations.name(value.variable)); token(this, L.COLON, ":")
                child(this, expressions.expression(value.iterable)); token(this, L.RPAREN, ")"); space()
                child(this, blockBody(value.body))
            }, value.variable))
            is Statement.TryStore -> child(this, context(P.TryStoreStatementContext(null, -1), value) {
                token(this, L.TRY, "try"); space(); child(this, blockBody(value.body)); space()
                token(this, L.STORE, "store"); token(this, L.LPAREN, "(")
                val name = expressions.bindings.name(value.destination, NameRole.VALUE)
                if (name.namespace != null) unsupported("A store destination must be a local display name")
                token(this, L.Identifier, name.name); token(this, L.RPAREN, ")")
            })
            is Statement.Execute -> child(this, context(P.ExecuteStatementContext(null, -1), value) {
                if (value.contexts.isEmpty()) unsupported("An execute statement requires a context")
                token(this, L.EXECUTE, "execute"); token(this, L.LPAREN, "(")
                value.contexts.forEachIndexed { index, execute ->
                    if (index != 0) token(this, L.COMMA, ",")
                    child(this, context(P.ExecuteContextContext(null, -1), value) {
                        child(this, context(P.ExecuteExpressionContext(null, -1), value) {
                            when (val path = execute.path) {
                                ExecutePath.As -> token(this, L.AS, "as")
                                is ExecutePath.Members -> path.parts.forEachIndexed { partIndex, part ->
                                    if (partIndex != 0) token(this, L.DOT, ".")
                                    child(this, variable(part))
                                }
                            }
                        })
                        token(this, L.ASSIGNMENT, "="); child(this, expressions.expression(execute.value))
                    })
                }
                token(this, L.RPAREN, ")"); space(); child(this, blockBody(value.body))
            })
            is Statement.Command -> child(this, command(value))
            is Statement.Break, is Statement.Continue -> child(this, context(P.ControlStatementContext(null, -1), value) {
                token(this, if (value is Statement.Break) L.BREAK else L.CONTINUE,
                    if (value is Statement.Break) "break" else "continue")
            })
            is Statement.Return -> child(this, context(P.ReturnStatementContext(null, -1), value) {
                token(this, L.RETURN, "return")
                value.value?.let { space(); child(this, expressions.expression(it)) }
            })
            is Statement.Empty -> token(this, L.SEMICOLON, ";")
        }
    }

    private fun local(value: Statement.Local): P.FieldDeclarationContext = declared(
        context(P.FieldDeclarationContext(null, -1), value) {
            val facts = declarations.local(value.declaration)
            if (facts.declaredType == null && value.initializer == null) unsupported("A local needs a declared type or initializer")
            if (facts.storage == LocalStorage.VAR) token(this, L.VAR, "var")
            else child(this, context(P.FieldModifierContext(null, -1), value) {
                when (facts.storage) {
                    LocalStorage.CONST -> token(this, L.CONST, "const")
                    LocalStorage.EXTERNAL -> token(this, L.EXTERNAL, "external")
                    LocalStorage.IMPORT_VAR -> { token(this, L.IMPORT, "import"); space(); token(this, L.VAR, "var") }
                    LocalStorage.VAR -> error("Handled outside fieldModifier")
                }
            })
            space(); token(this, L.Identifier, facts.name)
            facts.declaredType?.let { token(this, L.COLON, ":"); child(this, expressions.type(it)) }
            // The statement owns this initializer. The provider's optional snapshot is never executed again.
            value.initializer?.let { token(this, L.ASSIGNMENT, "="); child(this, expressions.expression(it)) }
        }, value.declaration)

    /** Strip only the fixed precedence wrappers created by ExpressionProjection, with shape checks. */
    private fun variableSelection(value: Expression): P.VarWithSelectorContext {
        val common = expressions.expression(value).commonBinaryOperatorExpression() ?: unsupported("Expected a variable expression")
        if (common.op.isNotEmpty()) unsupported("A variable cannot contain a binary operator")
        val or = common.conditionalOrExpression().single()
        if (or.op.isNotEmpty()) unsupported("A variable cannot contain a binary operator")
        val and = or.conditionalAndExpression().single()
        if (and.op.isNotEmpty()) unsupported("A variable cannot contain a binary operator")
        val equality = and.equalityExpression().single()
        if (equality.op.isNotEmpty()) unsupported("A variable cannot contain a binary operator")
        val relational = equality.relationalExpression().single()
        if (relational.op.isNotEmpty()) unsupported("A variable cannot contain a binary operator")
        val additive = relational.additiveExpression().single()
        if (additive.op.isNotEmpty()) unsupported("A variable cannot contain a binary operator")
        val multiply = additive.multiplicativeExpression().single()
        if (multiply.op.isNotEmpty()) unsupported("A variable cannot contain a binary operator")
        val cast = multiply.castExpression().single()
        if (cast.type() != null) unsupported("A variable cannot be a cast expression")
        val unary = cast.unaryExpression()
        return unary.rightVarExpression()?.varWithSelector() ?: unsupported("A variable cannot be a unary expression")
    }

    private fun variable(value: Expression): P.VarContext {
        val selected = variableSelection(value)
        if (selected.selector().isNotEmpty()) unsupported("Execute member paths store each var component separately")
        val property = selected.jvmAccessExpression().propertyOperator()
        if (property.propertyOperatorExpression().isNotEmpty()) unsupported("An execute path component cannot be a property filter")
        return property.primary().`var`() ?: unsupported("An execute path component must be a current grammar var")
    }

    private fun command(value: Statement.Command): P.OrgCommandContext = context(P.OrgCommandContext(null, -1), value) {
        token(this, L.SLASH, "/")
        value.parts.forEach { part -> child(this, context(P.OrgCommandContentContext(null, -1), value) {
            when (part) {
                is TextPart.Text -> token(this, L.OrgCommandText, part.value)
                is TextPart.Interpolation -> child(this, context(P.OrgCommandExpressionContext(null, -1), part.expression) {
                    token(this, L.OrgCommandExprStart, "\${"); child(this, expressions.expression(part.expression)); token(this, L.RCURL, "}")
                })
            }
        }) }
    }

    fun lambda(value: Expression.Lambda): P.InlineLambdaContext = lambda(value, expressions)

    override fun lambda(node: Expression.Lambda, projection: ExpressionProjection): P.InlineLambdaContext {
        require(projection === expressions) { "A structured body must share its projection source and bindings" }
        val root = bodyBlock(node.body)
        return declared(context(P.InlineLambdaContext(null, -1), node) {
            token(this, L.LCURL, "{")
            if (node.explicitParameters) {
                child(this, context(P.LambdaParameterListContext(null, -1), node) {
                    node.parameters.forEachIndexed { index, reference ->
                        if (index != 0) token(this, L.COMMA, ",")
                        val parameter = declarations.lambdaParameter(reference)
                        if (parameter.declaration != reference) unsupported("Lambda parameter metadata has a different D identity")
                        child(this, declared(context(P.LambdaParameterContext(null, -1), node) {
                            token(this, L.Identifier, parameter.name)
                            parameter.declaredType?.let { token(this, L.COLON, ":"); child(this, expressions.type(it)) }
                        }, reference))
                    }
                })
                space(); token(this, L.ARROW, "->")
            }
            token(this, L.NL, "\n")
            val body = child(this, context(P.LambdaBodyContext(null, -1), root) {
                root.statements.forEach { child(this, statement(it)); separator(this, root) }
                node.tail?.let { tail -> child(this, context(P.LambdaTailExpressionContext(null, -1), tail) {
                    child(this, expressions.expression(tail))
                }) }
            })
            if (node.tail != null) token(this, L.NL, "\n")
            val close = token(this, L.RCURL, "}")
            if (body.start == null) {
                // Empty rules still need an ordered [start, stop + 1) range for callback spans.
                val boundary = CommonToken(close).apply { text = ""; stopIndex = startIndex - 1 }
                body.start = boundary
                body.stop = boundary
            }
        }, node.declaration)
    }

    fun template(value: TemplateBody): P.TemplateBodyContext = context(P.TemplateBodyContext(null, -1), value) {
        token(this, L.LCURL, "{"); token(this, L.NL, "\n")
        value.members.forEach { member -> child(this, templateMember(member)); separator(this, value) }
        token(this, L.RCURL, "}")
    }

    private fun templateMember(value: TemplateMember): P.TemplateMemberDeclarationContext =
        context(P.TemplateMemberDeclarationContext(null, -1), value) {
            val descriptor = (value as? TemplateMember.Declaration)?.let { declarations.templateDeclaration(it.declaration) }
            if (value is TemplateMember.Declaration) declared(this, value.declaration)
            descriptor?.access?.let { access ->
                child(this, context(P.AccessModifierContext(null, -1), value) {
                    when (access) {
                        ProjectionAccess.PUBLIC -> token(this, L.PUBLIC, "public")
                        ProjectionAccess.PROTECTED -> token(this, L.PROTECTED, "protected")
                        ProjectionAccess.PRIVATE -> token(this, L.PRIVATE, "private")
                    }
                }); space()
            }
            child(this, context(P.TemplateMemberContext(null, -1), value) {
                if (value is TemplateMember.AnnotationApplication) child(this, annotation(value, declarations.annotation(value.annotation)))
                else if (value is TemplateMember.AnnotationConstruction) child(this, annotation(value,
                    ProjectionAnnotation(declarations.annotationProvider(value.provider), value.arguments)))
                else when (descriptor) {
                    is ProjectionTemplateDeclaration.Field -> child(this, templateField(value as TemplateMember.Declaration, descriptor))
                    is ProjectionTemplateDeclaration.Function -> child(this, templateFunction(value as TemplateMember.Declaration, descriptor))
                    is ProjectionTemplateDeclaration.Constructor -> child(this, templateConstructor(value as TemplateMember.Declaration, descriptor))
                    is ProjectionTemplateDeclaration.Operation -> child(this, templateOperation(value as TemplateMember.Declaration, descriptor))
                    null -> unsupported("Template member has no declaration metadata")
                }
            })
        }

    private fun annotation(value: TemplateMember, facts: ProjectionAnnotation): P.AnnotationContext =
        context(P.AnnotationContext(null, -1), value) {
            token(this, L.AT, "@")
            id = token(this, L.Identifier, facts.name)
            if (facts.arguments.isNotEmpty()) child(this, context(P.AnnotationArgsContext(null, -1), value) {
                token(this, L.LANGLE, "<")
                facts.arguments.forEachIndexed { index, argument ->
                    if (index != 0) token(this, L.COMMA, ",")
                    child(this, expressions.value(argument))
                }
                token(this, L.RANGLE, ">")
            })
        }

    private fun templateField(value: TemplateMember.Declaration, facts: ProjectionTemplateDeclaration.Field): P.TemplateFieldDeclarationContext =
        declared(context(P.TemplateFieldDeclarationContext(null, -1), value) {
            token(this, if (facts.constant) L.CONST else L.VAR, if (facts.constant) "const" else "var")
            space(); token(this, L.Identifier, facts.name)
            facts.type?.let { type ->
                token(this, L.COLON, ":")
                child(this, context(P.TemplateTypeContext(null, -1), value) {
                    child(this, context(P.SingleTemplateFieldTypeContext(null, -1), value) {
                        child(this, expressions.type(type)); if (facts.nullable) token(this, L.QUEST, "?")
                    })
                })
            }
            facts.initializer?.let { token(this, L.ASSIGNMENT, "="); child(this, expressions.expression(bodyExpression(it))) }
            if (facts.getter != null || facts.setter != null) child(this, context(P.AccessorContext(null, -1), value) {
                token(this, L.LCURL, "{")
                facts.getter?.let { getter -> child(this, this@StatementProjection.getter(value, getter)) }
                if (facts.getter != null && facts.setter != null) separator(this, value)
                facts.setter?.let { setter -> child(this, this@StatementProjection.setter(value, setter)) }
                token(this, L.RCURL, "}")
            })
        }, value.declaration)

    private fun getter(value: TemplateMember.Declaration, facts: ProjectionAccessor): P.GetterContext =
        context(P.GetterContext(null, -1), value) { token(this, L.GET, "get"); accessorBody(this, value, facts) }
    private fun setter(value: TemplateMember.Declaration, facts: ProjectionAccessor): P.SetterContext =
        context(P.SetterContext(null, -1), value) { token(this, L.SET, "set"); accessorBody(this, value, facts) }

    private fun accessorBody(parent: ParserRuleContext, value: TemplateMember.Declaration, facts: ProjectionAccessor) {
        when (facts) {
            ProjectionAccessor.Simple -> Unit
            is ProjectionAccessor.Block -> {
                declared(parent, facts.declaration); space(); child(parent, block(bodyBlock(facts.body)))
            }
            is ProjectionAccessor.ExpressionBody -> {
                declared(parent, facts.declaration); token(parent, L.ASSIGNMENT, "=")
                child(parent, expressions.expression(bodyExpression(facts.body)))
            }
            is ProjectionAccessor.Native -> {
                declared(parent, facts.declaration); token(parent, L.ASSIGNMENT, "="); child(parent, javaReference(value, facts.javaPath))
            }
        }
    }

    private fun templateFunction(value: TemplateMember.Declaration, facts: ProjectionTemplateDeclaration.Function): P.TemplateFunctionDeclarationContext =
        declared(context(P.TemplateFunctionDeclarationContext(null, -1), value) {
            if (facts.isOverride) { token(this, L.OVERRIDE, "override"); space() }
            if (facts.isAbstract) { token(this, L.ABSTRACT, "abstract"); space() }
            child(this, context(P.FunctionDeclarationPartContext(null, -1), value) {
                token(this, L.FUNCTION, "func"); space(); token(this, L.Identifier, facts.name)
                child(this, parameters(value, facts.readonly, facts.normal))
                token(this, L.COLON, ":"); child(this, expressions.returnType(facts.result))
            })
            facts.body?.let { space(); child(this, block(bodyBlock(it))) }
        }, value.declaration)

    private fun templateConstructor(value: TemplateMember.Declaration, facts: ProjectionTemplateDeclaration.Constructor): P.TemplateConstructorDeclarationContext =
        declared(context(P.TemplateConstructorDeclarationContext(null, -1), value) {
            token(this, L.CONSTRUCTOR, "constructor"); child(this, normalParameters(value, facts.normal)); space()
            child(this, block(bodyBlock(facts.body)))
        }, value.declaration)

    private fun templateOperation(value: TemplateMember.Declaration, facts: ProjectionTemplateDeclaration.Operation): ParserRuleContext {
        if ((facts.body == null) == (facts.javaPath == null)) unsupported("An operator needs exactly one structured body or native binding")
        val operation: ParserRuleContext = if (facts.javaPath == null) P.OperationOverrideDeclarationContext(null, -1)
            else P.NativeOperationOverrideDeclarationContext(null, -1)
        return declared(context(operation, value) {
            token(this, L.OPERATOR, "operator"); space()
            child(this, context(P.SupportOperatorContext(null, -1), value) { token(this, binaryOperator(facts.operator), facts.operator) })
            child(this, parameters(value, facts.readonly, facts.normal)); token(this, L.COLON, ":")
            child(this, expressions.returnType(facts.result))
            if (facts.javaPath != null) { token(this, L.ASSIGNMENT, "="); child(this, javaReference(value, facts.javaPath)) }
            else { space(); child(this, block(bodyBlock(facts.body!!))) }
        }, value.declaration)
    }

    private fun parameters(value: SemanticNode, readonly: List<ProjectionParameter>, normal: List<ProjectionParameter>): P.FunctionParamsContext =
        context(P.FunctionParamsContext(null, -1), value) {
            if (readonly.isNotEmpty()) child(this, context(P.ReadOnlyParamsContext(null, -1), value) {
                token(this, L.LANGLE, "<")
                child(this, context(P.ReadOnlyParameterListContext(null, -1), value) {
                    readonly.forEachIndexed { index, parameter ->
                        if (index != 0) token(this, L.COMMA, ",")
                        child(this, declared(context(P.ReadOnlyParameterContext(null, -1), value) {
                            when (parameter.variance) {
                                ProjectionVariance.IN -> { token(this, L.IN, "in"); space() }
                                ProjectionVariance.OUT -> { token(this, L.OUT, "out"); space() }
                                ProjectionVariance.INVARIANT -> Unit
                            }
                            token(this, L.Identifier, parameter.name)
                            parameter.type?.let { token(this, L.COLON, ":"); child(this, expressions.type(it)) }
                            parameter.defaultBody?.let { token(this, L.ASSIGNMENT, "="); child(this, expressions.value(bodyExpression(it))) }
                        }, parameter.declaration))
                    }
                })
                token(this, L.RANGLE, ">")
            })
            child(this, normalParameters(value, normal))
        }

    private fun normalParameters(value: SemanticNode, normal: List<ProjectionParameter>): P.NormalParamsContext =
        context(P.NormalParamsContext(null, -1), value) {
            token(this, L.LPAREN, "(")
            if (normal.isNotEmpty()) child(this, context(P.ParameterListContext(null, -1), value) {
                normal.forEachIndexed { index, parameter ->
                    if (index != 0) token(this, L.COMMA, ",")
                    if (parameter.variance != ProjectionVariance.INVARIANT) unsupported("Normal parameters cannot carry variance")
                    child(this, declared(context(P.ParameterContext(null, -1), value) {
                        token(this, L.Identifier, parameter.name); token(this, L.COLON, ":")
                        child(this, expressions.type(parameter.type ?: unsupported("A normal parameter requires its declared type")))
                        parameter.defaultBody?.let { token(this, L.ASSIGNMENT, "="); child(this, expressions.value(bodyExpression(it))) }
                    }, parameter.declaration))
                }
            })
            token(this, L.RPAREN, ")")
        }

    private fun javaReference(value: SemanticNode, path: List<String>): P.JavaReferContext = context(P.JavaReferContext(null, -1), value) {
        if (path.isEmpty() || path.any { it.isEmpty() }) unsupported("A native provider path needs nonempty components")
        path.forEachIndexed { index, part -> if (index != 0) token(this, L.DOT, "."); token(this, L.Identifier, part) }
    }

    override fun anonymousType(node: TypeSyntax.AnonymousData, projection: ExpressionProjection): P.AnonymousTemplateTypeContext {
        require(projection === expressions) { "An anonymous type must share its projection source and bindings" }
        val facts = declarations.anonymousData(node.declaration)
        return declared(context(P.AnonymousTemplateTypeContext(null, -1), node) {
            token(this, L.DATA, "data")
            facts.parents.forEachIndexed { index, parent ->
                token(this, if (index == 0) L.COLON else L.COMMA, if (index == 0) ":" else ",")
                child(this, context(P.ExtendNameContext(null, -1), parent) {
                    val body = expressions.type(parent).typeBody()
                    child(this, body.className() ?: unsupported("An anonymous parent requires a named type"))
                    body.readOnlyArgs()?.let { child(this, it) }
                })
            }
            space(); child(this, template(facts.body))
        }, node.declaration)
    }

    private fun assignmentOperator(operator: String): Int = when (operator) {
        "=" -> L.ASSIGNMENT; "+=" -> L.ADD_ASSIGNMENT; "-=" -> L.SUB_ASSIGNMENT
        "*=" -> L.MULT_ASSIGNMENT; "/=" -> L.DIV_ASSIGNMENT; "%=" -> L.MOD_ASSIGNMENT
        else -> unsupported("Unsupported assignment operator $operator")
    }
    private fun binaryOperator(operator: String): Int = when (operator) {
        "+" -> L.ADD; "-" -> L.SUB; "*" -> L.MULT; "/" -> L.SLASH; "%" -> L.MOD
        ">" -> L.RANGLE; "<" -> L.LANGLE; ">=" -> L.GE; "<=" -> L.LE; "==" -> L.EQEQ; "!=" -> L.EXCL_EQ
        "~=" -> L.WVEQ; "||" -> L.DISJ; "&&" -> L.CONJ; "|" -> L.PIPE; else -> L.Identifier
    }
    private fun unsupported(message: String): Nothing = throw SyntaxProjectionException("LIB_BODY_UNSUPPORTED", message)
}
