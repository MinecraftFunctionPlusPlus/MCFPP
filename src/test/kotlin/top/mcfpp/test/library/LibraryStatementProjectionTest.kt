package top.mcfpp.test.library

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.TerminalNode
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.library.compiler.*
import top.mcfpp.library.syntax.*
import kotlin.test.*

class LibraryStatementProjectionTest {
    private class Fixture : ProjectionBindings, StatementProjectionBindings, StatementCaptureBindings {
        val scope = ScopeId(1)
        val bodyScope = ScopeId(2)
        val integer = TypeSyntax.Name(scope, NameUse.Declaration(DeclarationReference.Local(DeclarationId(90))), TypeRef(1))
        val constants = linkedMapOf(ConstantRef(1) to ConstantValue.Integral(1),
            ConstantRef(2) to ConstantValue.Typed(TypeRef(1), ConstantRef(1)))
        val literal = Expression.Literal(scope, ConstantRef(2))
        val names = mutableMapOf(1L to "x", 2L to "flag", 3L to "incoming", 10L to "local", 11L to "element",
            21L to "item", 22L to "captured", 90L to "int", 91L to "Parent", 92L to "positioned", 93L to "x")
        val localFacts = mutableMapOf<DeclarationId, LocalDeclarationFacts>()
        val bodyRoots = mutableMapOf<BodyId, SemanticNode>()
        val parameterFacts = mutableMapOf<DeclarationId, LambdaParameterSyntax>()
        val members = mutableMapOf<DeclarationId, ProjectionTemplateDeclaration>()
        val completedLocals = mutableMapOf<DeclarationId, LocalDeclarationFacts>()
        val completedLambdas = mutableMapOf<BodyId, LambdaBodyCapture>()
        lateinit var current: CurrentSyntaxProjection
        val capture = StatementBridge(this)
        fun name(id: Long, at: ScopeId = scope) = Expression.Name(at, NameUse.Local(DeclarationId(id)))
        fun fieldName(id: Long) = Expression.Name(scope, NameUse.Declaration(DeclarationReference.Local(DeclarationId(id))))
        fun block(vararg values: Statement) = Statement.Block(scope, values.toList())
        override fun name(use: NameUse, role: NameRole): ProjectedName {
            val id = when (use) {
                is NameUse.Local -> use.target
                is NameUse.Declaration -> (use.target as DeclarationReference.Local).target
                is NameUse.Lookup -> return ProjectedName(use.name, use.namespace, use.candidates)
            }
            return ProjectedName(names.getValue(id.value), binding = id)
        }
        override fun constant(reference: ConstantRef): ConstantValue = constants.getValue(reference)
        override fun type(reference: TypeRef): Any = reference
        override fun builtinKey(reference: TypeRef) = "int"
        override fun name(declaration: DeclarationId) = names.getValue(declaration.value)
        override fun body(reference: BodyId) = bodyRoots.getValue(reference)
        override fun local(declaration: DeclarationId) = localFacts.getValue(declaration)
        override fun lambdaParameter(declaration: DeclarationId) = parameterFacts.getValue(declaration)
        override fun templateDeclaration(declaration: DeclarationId) = members.getValue(declaration)
        override fun annotation(reference: AnnotationId) = ProjectionAnnotation("Marker", listOf(literal))
        override fun anonymousData(declaration: DeclarationId) = ProjectionAnonymousData(
            listOf(TypeSyntax.Apply(scope, TypeSyntax.Name(scope,
                NameUse.Declaration(DeclarationReference.Local(DeclarationId(91)))), listOf(literal))), templateRoot())
        override fun scope(site: ParserRuleContext) = current.origin(site)?.node?.scope ?: scope
        override fun <T> withScope(site: ParserRuleContext, owner: DeclarationId?, action: (ScopeId) -> T) = action(scope(site))
        override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse =
            current.origin(site)?.nameUse ?: error("Projected name fell back to caller lookup: $name")
        override fun builtinType(key: String, site: ParserRuleContext) = TypeRef(1)
        override fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef =
            constants.entries.firstOrNull { it.value == value }?.key ?: error("Unexpected projected constant $value")
        override fun resolvedType(site: ParserRuleContext) = current.origin(site)?.typeReference
        override fun owner(site: ParserRuleContext) = DeclarationReference.Local(DeclarationId(500))
        override fun reserveLocal(site: P.FieldDeclarationContext) = assertNotNull(current.declaration(site))
        override fun completeLocal(declaration: DeclarationId, facts: LocalDeclarationFacts) { completedLocals[declaration] = facts }
        override fun publishLocal(declaration: DeclarationId, name: String, site: ParserRuleContext) = Unit
        override fun reserveIterationVariable(site: P.ForeachStatementContext, iterable: Expression) = assertNotNull(current.declaration(site))
        override fun destination(name: String, site: P.TryStoreStatementContext) =
            (current.origin(site)?.node as Statement.TryStore).destination
        override fun executePathPart(site: P.VarContext, capture: StatementBridge) = capture.expression(site)
        override fun reserveLambda(site: P.InlineLambdaContext): LambdaAllocation {
            val node = current.origin(site)?.node as Expression.Lambda
            return LambdaAllocation(node.declaration, node.body, node.parameters.map { LambdaParameterSlot(it, name(it)) })
        }
        override fun completeLambdaParameters(allocation: LambdaAllocation, parameters: List<LambdaParameterSyntax>, explicit: Boolean) {
            parameters.forEach { assertEquals(parameterFacts.getValue(it.declaration), it) }
        }
        override fun completeLambda(allocation: LambdaAllocation, body: LambdaBodyCapture) { completedLambdas[allocation.body] = body }
        override fun receiverCapture(owner: DeclarationReference): DeclarationReference? = null
        override fun templateMember(site: P.TemplateMemberDeclarationContext, capture: StatementBridge) =
            current.origin(site)?.node as TemplateMember
        fun start(): CurrentSyntaxProjection = CurrentSyntaxProjection(this, this).also { current = it }

        fun templateRoot(): TemplateBody = TemplateBody(scope, listOf(
            TemplateMember.AnnotationApplication(scope, AnnotationId(1), listOf(DeclarationId(100))),
            *members.keys.map { TemplateMember.Declaration(scope, it) }.toTypedArray()))

        fun prepareTemplate() {
            bodyRoots[BodyId(3)] = literal
            bodyRoots[BodyId(4)] = block(Statement.Return(scope, literal))
            bodyRoots[BodyId(5)] = Expression.ListValue(scope, listOf(literal, name(1)))
            bodyRoots[BodyId(6)] = name(1)
            bodyRoots[BodyId(7)] = block()
            members[DeclarationId(100)] = ProjectionTemplateDeclaration.Field("simple", false, integer, nullable = true,
                initializer = BodyId(3), getter = ProjectionAccessor.Simple, setter = ProjectionAccessor.Simple,
                access = ProjectionAccess.PRIVATE)
            members[DeclarationId(101)] = ProjectionTemplateDeclaration.Field("structured", false, integer,
                getter = ProjectionAccessor.Block(DeclarationId(201), BodyId(4)),
                setter = ProjectionAccessor.ExpressionBody(DeclarationId(202), BodyId(6)))
            members[DeclarationId(102)] = ProjectionTemplateDeclaration.Field("native", false, integer,
                getter = ProjectionAccessor.Native(DeclarationId(203), listOf("provider", "get")),
                setter = ProjectionAccessor.Native(DeclarationId(204), listOf("provider", "set")))
            members[DeclarationId(110)] = ProjectionTemplateDeclaration.Function("calculate",
                listOf(ProjectionParameter(DeclarationId(112), "T", null, BodyId(3), ProjectionVariance.OUT)),
                listOf(ProjectionParameter(DeclarationId(113), "values", TypeSyntax.Container(scope, ContainerKind.LIST, integer), BodyId(5))),
                integer, BodyId(4), isOverride = true, access = ProjectionAccess.PROTECTED)
            members[DeclarationId(111)] = ProjectionTemplateDeclaration.Function("abstractValue", emptyList(), emptyList(),
                integer, null, isAbstract = true, access = ProjectionAccess.PUBLIC)
            members[DeclarationId(120)] = ProjectionTemplateDeclaration.Constructor(emptyList(), BodyId(7))
            members[DeclarationId(130)] = ProjectionTemplateDeclaration.Operation("+", emptyList(), emptyList(), integer, body = BodyId(4))
            members[DeclarationId(131)] = ProjectionTemplateDeclaration.Operation("named", emptyList(), emptyList(), integer,
                javaPath = listOf("provider", "operator"))
        }
    }

    private fun all(site: ParserRuleContext): List<ParserRuleContext> = listOf(site) + site.children.orEmpty()
        .filterIsInstance<ParserRuleContext>().flatMap(::all)

    private fun checkTree(site: ParserRuleContext, current: CurrentSyntaxProjection) {
        site.children.orEmpty().forEach { child ->
            assertSame(site, child.parent)
            if (child is ParserRuleContext) checkTree(child, current)
            if (child is TerminalNode) {
                val token = child.symbol
                assertSame(current.source, token.inputStream)
                assertEquals(token.text, token.inputStream.getText(Interval(token.startIndex, token.stopIndex)))
            }
        }
    }

    @Test
    fun statementsRoundtripThroughExistingCaptureWithDeclarationBindingsAndControlShapes() {
        val f = Fixture()
        val local = Statement.Local(f.scope, DeclarationId(10), f.name(1))
        f.localFacts[local.declaration] = LocalDeclarationFacts("local", LocalStorage.CONST, f.integer, local.initializer)
        val root = f.block(local, Statement.Assign(f.scope, f.name(10), "+=", f.literal),
            Statement.If(f.scope, listOf(ConditionalBranch(f.name(2), Statement.Return(f.scope, f.name(10))),
                ConditionalBranch(f.name(2), Statement.Continue(f.scope))), Statement.Break(f.scope)),
            Statement.While(f.scope, f.name(2), f.block(Statement.Continue(f.scope))),
            Statement.DoWhile(f.scope, f.block(Statement.Break(f.scope)), f.name(2)),
            Statement.Foreach(f.scope, DeclarationId(11), Expression.ListValue(f.scope, listOf(f.name(1))),
                Statement.Return(f.scope, f.name(11))), Statement.Empty(f.scope), Statement.Return(f.scope, null))
        f.bodyRoots[BodyId(1)] = root
        val current = f.start()
        val projected = current.block(BodyId(1))
        assertEquals(root, f.capture.block(projected))
        assertEquals(f.localFacts, f.completedLocals)
        assertEquals("+=", all(projected).filterIsInstance<P.AssignmentOperatorContext>().single().text)
        assertNotNull(all(projected).filterIsInstance<P.IfStatementContext>().single().conditionExpression())
        assertEquals(DeclarationId(11), current.declaration(all(projected).filterIsInstance<P.ForeachStatementContext>().single()))
        checkTree(projected, current)
    }

    @Test
    fun storeExecuteAndCommandsKeepDedicatedNamesPathsAndText() {
        val f = Fixture()
        val root = f.block(Statement.TryStore(f.scope, f.block(), NameUse.Local(DeclarationId(1))),
            Statement.Execute(f.scope, listOf(ExecuteContext(ExecutePath.As, Expression.Selector(f.scope, SelectorKind.ALL_ENTITIES)),
                ExecuteContext(ExecutePath.Members(listOf(f.fieldName(92), f.fieldName(93))), f.name(1))),
                f.block(Statement.Return(f.scope, null))),
            Statement.Command(f.scope, listOf(TextPart.Text("say 😀 value="), TextPart.Interpolation(f.name(1)), TextPart.Text("!"))))
        val current = f.start()
        val projected = current.block(root)
        assertEquals(root, f.capture.block(projected))
        assertEquals(listOf("as", "positioned.x"), all(projected).filterIsInstance<P.ExecuteExpressionContext>().map { it.text })
        assertTrue(current.sourceSlice(projected).contains("/say 😀 value=\${x}!\n"))
        val location = assertNotNull(StatementBridge.sourceLocation(projected, "library"))
        assertEquals(current.sourceSlice(projected).length.toLong(), location.end.offset - location.start.offset)
        checkTree(projected, current)
    }

    @Test
    fun lambdaStatementsAndTailStaySeparateAndCaptureTheOriginalDeclarations() {
        val f = Fixture()
        val local = Statement.Local(f.bodyScope, DeclarationId(22), f.name(1, f.bodyScope))
        f.localFacts[local.declaration] = LocalDeclarationFacts("captured", LocalStorage.VAR, f.integer, local.initializer)
        f.parameterFacts[DeclarationId(21)] = LambdaParameterSyntax(DeclarationId(21), "item", f.integer)
        val root = Statement.Block(f.bodyScope, listOf(local, Statement.Evaluate(f.bodyScope, f.name(21, f.bodyScope))))
        f.bodyRoots[BodyId(2)] = root
        val lambda = Expression.Lambda(f.scope, DeclarationId(20), listOf(DeclarationId(21)), true, BodyId(2), f.name(22, f.bodyScope))
        val current = f.start()
        val projected = current.lambda(lambda)
        assertEquals(2, projected.lambdaBody().statement().size)
        assertNotNull(projected.lambdaBody().lambdaTailExpression())
        assertEquals(lambda, f.capture.lambda(projected))
        val completed = f.completedLambdas.getValue(BodyId(2))
        assertEquals(root, completed.root)
        assertEquals(lambda.tail, completed.tail)
        assertEquals(listOf(DeclarationReference.Local(DeclarationId(1))), completed.captures)
        assertEquals(DeclarationId(20), current.declaration(projected))
        assertEquals(DeclarationId(21), current.declaration(projected.lambdaParameterList().lambdaParameter(0)))
        checkTree(projected, current)
        f.bodyRoots[BodyId(8)] = Statement.Block(f.bodyScope, emptyList())
        val implicit = lambda.copy(parameters = emptyList(), explicitParameters = false, body = BodyId(8), tail = null)
        val empty = current.lambda(implicit)
        assertNull(empty.lambdaParameterList())
        assertNull(empty.lambdaBody().lambdaTailExpression())
        assertEquals("", current.sourceSlice(empty.lambdaBody()))
        assertEquals(empty.lambdaBody().start.startIndex, empty.lambdaBody().stop.stopIndex + 1)
        assertSame(current.source, empty.lambdaBody().start.inputStream)
        assertSame(current.source, empty.lambdaBody().stop.inputStream)
        assertEquals(implicit, f.capture.lambda(empty))
    }

    @Test
    fun templatesProjectDeclarationDefaultsModifiersAccessorFormsAndAnnotationIds() {
        val f = Fixture().apply { prepareTemplate() }
        val current = f.start()
        val root = f.templateRoot()
        val projected = current.template(root)
        assertEquals(root, f.capture.template(projected))
        val annotation = all(projected).filterIsInstance<P.AnnotationContext>().single()
        assertEquals("Marker", annotation.id.text)
        assertEquals(listOf(DeclarationId(100)), (current.origin(annotation)?.node as TemplateMember.AnnotationApplication).targets)
        val functions = all(projected).filterIsInstance<P.TemplateFunctionDeclarationContext>()
        assertNotNull(functions[0].OVERRIDE())
        assertNotNull(functions[1].ABSTRACT())
        assertNull(functions[1].curlBlock())
        val parameters = functions[0].functionDeclarationPart().functionParams()
        assertNotNull(parameters.readOnlyParams().readOnlyParameterList().readOnlyParameter(0).OUT())
        assertNotNull(parameters.normalParams().parameterList().parameter(0).value().nbtValue().nbtList())
        assertEquals(DeclarationId(113), current.declaration(parameters.normalParams().parameterList().parameter(0)))
        val fields = all(projected).filterIsInstance<P.TemplateFieldDeclarationContext>()
        assertNotNull(fields[0].templateType().singleTemplateFieldType().QUEST())
        assertNotNull(fields[1].accessor().getter().curlBlock())
        assertNotNull(fields[1].accessor().setter().expression())
        assertEquals(DeclarationId(201), current.declaration(fields[1].accessor().getter()))
        assertNotNull(fields[2].accessor().getter().javaRefer())
        assertNotNull(fields[2].accessor().setter().javaRefer())
        assertEquals(1, all(projected).filterIsInstance<P.OperationOverrideDeclarationContext>().size)
        assertEquals(1, all(projected).filterIsInstance<P.NativeOperationOverrideDeclarationContext>().size)
        assertNotSame(projected, current.template(root))
        checkTree(projected, current)
        val anonymous = current.type(TypeSyntax.AnonymousData(f.scope, DeclarationId(200)))
        val data = anonymous.typeBody().anonymousTemplateType()
        assertEquals(DeclarationId(200), current.declaration(data))
        assertEquals("Parent", data.extendName(0).className().text)
        assertNotNull(data.extendName(0).readOnlyArgs())
        checkTree(anonymous, current)
    }

    @Test
    fun defaultBodiesCheckValueGrammarAndLocalStorageUsesCurrentModifierRules() {
        val f = Fixture().apply { prepareTemplate() }
        val current = f.start()
        val value = current.defaultValue(BodyId(5))
        assertEquals(f.bodyRoots[BodyId(5)], f.capture.expression(value))
        assertFailsWith<SyntaxProjectionException> { current.defaultValue(BodyId(4)) }
        f.bodyRoots[BodyId(9)] = Expression.Binary(f.scope, "+", f.literal, f.literal)
        assertFailsWith<SyntaxProjectionException> { current.defaultValue(BodyId(9)) }
        LocalStorage.entries.forEachIndexed { index, storage ->
            val declaration = DeclarationId(300L + index)
            f.localFacts[declaration] = LocalDeclarationFacts("variable$index", storage, f.integer, null)
            val projected = current.statement(Statement.Local(f.scope, declaration, null))
            assertEquals(storage, when {
                projected.fieldDeclaration().VAR() != null -> LocalStorage.VAR
                projected.fieldDeclaration().fieldModifier().CONST() != null -> LocalStorage.CONST
                projected.fieldDeclaration().fieldModifier().EXTERNAL() != null -> LocalStorage.EXTERNAL
                else -> LocalStorage.IMPORT_VAR
            })
        }
    }
}
