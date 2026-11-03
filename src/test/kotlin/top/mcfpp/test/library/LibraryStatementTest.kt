package top.mcfpp.test.library

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.library.syntax.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryStatementTest {
    private data class Frame(val id: ScopeId, val owner: DeclarationId, val names: MutableMap<String, DeclarationId>)
    private class Capture : StatementCaptureBindings {
        private var nextDeclaration = 100L
        private var nextScope = 1L
        private var nextBody = 1L
        private val frames = mutableListOf(Frame(ScopeId(1), DeclarationId(50), linkedMapOf(
            "x" to DeclarationId(1), "flag" to DeclarationId(2), "other" to DeclarationId(3), "value" to DeclarationId(4)
        )))
        val facts = linkedMapOf<DeclarationId, LocalDeclarationFacts>()
        val constants = linkedMapOf<ConstantRef, ConstantValue>()
        val bodies = linkedMapOf<BodyId, LambdaBodyCapture>()
        val lambdaParameters = linkedMapOf<DeclarationId, List<LambdaParameterSyntax>>()
        val origins = mutableListOf<Pair<SemanticNode, SourceLocation>>()
        val scopes = mutableListOf<Triple<ScopeId, ScopeId, DeclarationId>>()
        val templateIds = linkedMapOf<Parser.TemplateMemberDeclarationContext, DeclarationId>()
        val templateBodies = linkedMapOf<DeclarationId, Statement.Block>()
        val templateInitializers = linkedMapOf<DeclarationId, Expression>()
        val templateHeaders = linkedMapOf<DeclarationId, Triple<String?, Boolean, Boolean>>()
        val bridge = StatementBridge(this)
        private fun allocate() = DeclarationId(++nextDeclaration)
        override fun scope(site: ParserRuleContext) = frames.last().id
        override fun <T> withScope(site: ParserRuleContext, owner: DeclarationId?, action: (ScopeId) -> T): T {
            val parent = frames.last()
            val frame = Frame(ScopeId(++nextScope), owner ?: parent.owner, linkedMapOf())
            scopes += Triple(frame.id, parent.id, frame.owner)
            frames += frame
            return try { action(frame.id) } finally { assertTrue(frames.removeAt(frames.lastIndex) === frame) }
        }
        private fun lookup(name: String): DeclarationId? = frames.asReversed().firstNotNullOfOrNull { it.names[name] }
        override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse {
            if (role == NameRole.TYPE || namespace != null || name == "consume")
                return NameUse.Declaration(DeclarationReference.Local(DeclarationId(90)))
            return NameUse.Local(lookup(name) ?: throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", "Unknown local $name"))
        }
        override fun builtinType(key: String, site: ParserRuleContext) = TypeRef(1)
        override fun internConstant(value: ConstantValue, site: ParserRuleContext) =
            constants.entries.firstOrNull { it.value == value }?.key
                ?: ConstantRef(constants.size + 1L).also { constants[it] = value }
        override fun reserveLocal(site: Parser.FieldDeclarationContext) = allocate()
        override fun completeLocal(declaration: DeclarationId, facts: LocalDeclarationFacts) { this.facts[declaration] = facts }
        override fun publishLocal(declaration: DeclarationId, name: String, site: ParserRuleContext) {
            check(frames.last().names.putIfAbsent(name, declaration) == null) { "Duplicate local $name" }
        }
        override fun reserveIterationVariable(site: Parser.ForeachStatementContext, iterable: Expression) = allocate()
        override fun destination(name: String, site: Parser.TryStoreStatementContext) =
            NameUse.Local(assertNotNull(lookup(name)))
        override fun executePathPart(site: Parser.VarContext, capture: StatementBridge): Expression {
            val name = assertNotNull(site.varWithSuffix()).Identifier().text
            return Expression.Name(scope(site), NameUse.Declaration(DeclarationReference.Local(
                DeclarationId(if (name == "positioned") 800 else 801)
            )))
        }
        override fun reserveLambda(site: Parser.InlineLambdaContext): LambdaAllocation {
            val declaration = allocate()
            val names = site.lambdaParameterList()?.lambdaParameter()?.map { it.Identifier().text } ?: listOf("it")
            return LambdaAllocation(declaration, BodyId(nextBody++), names.map { LambdaParameterSlot(allocate(), it) })
        }
        override fun completeLambdaParameters(allocation: LambdaAllocation, parameters: List<LambdaParameterSyntax>, explicit: Boolean) {
            lambdaParameters[allocation.declaration] = parameters
        }
        override fun completeLambda(allocation: LambdaAllocation, body: LambdaBodyCapture) { bodies[allocation.body] = body }
        override fun owner(site: ParserRuleContext) = DeclarationReference.Local(DeclarationId(900))
        override fun receiverCapture(owner: DeclarationReference) = DeclarationReference.Local(DeclarationId(5))
        override fun sourceLocation(site: ParserRuleContext) = StatementBridge.sourceLocation(site, "fixture.mcfpp")
        override fun recorded(node: SemanticNode, location: SourceLocation?) {
            if (location != null) origins += node to location
        }
        override fun templateMember(site: Parser.TemplateMemberDeclarationContext, capture: StatementBridge): TemplateMember {
            val all = (site.parent as Parser.TemplateBodyContext).templateMemberDeclaration()
            if (templateIds.isEmpty()) all.filter { it.templateMember().annotation() == null }.forEach { templateIds[it] = allocate() }
            val member = site.templateMember()
            if (member.annotation() != null) {
                val next = all.drop(all.indexOf(site) + 1).first { it in templateIds }
                return TemplateMember.AnnotationApplication(scope(site), AnnotationId(1), listOf(templateIds.getValue(next)))
            }
            val declaration = templateIds.getValue(site)
            val function = member.templateFunctionDeclaration()
            templateHeaders[declaration] = Triple(site.accessModifier()?.text, function?.OVERRIDE() != null, function?.ABSTRACT() != null)
            member.templateFieldDeclaration()?.let { field ->
                field.expression()?.let { templateInitializers[declaration] = capture.expression(it) }
                frames.last().names[field.Identifier().text] = declaration
            }
            val body = function?.curlBlock() ?: member.templateConstructorDeclaration()?.curlBlock()
                ?: member.operationOverrideDeclaration()?.curlBlock()
            if (body != null) templateBodies[declaration] = capture.block(body)
            return TemplateMember.Declaration(scope(site), declaration)
        }
        fun block(source: String) = bridge.block(parse(source) { curlBlock() })
    }

    companion object {
        private fun <T : ParserRuleContext> parse(source: String, rule: Parser.() -> T): T {
            val errors = mutableListOf<String>()
            val listener = object : BaseErrorListener() {
                override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                         charPositionInLine: Int, msg: String?, e: RecognitionException?) {
                    errors += "$line:$charPositionInLine $msg"
                }
            }
            val lexer = mcfppLexer(CharStreams.fromString(source)).apply { removeErrorListeners(); addErrorListener(listener) }
            val tokens = CommonTokenStream(lexer)
            val parser = Parser(tokens).apply { removeErrorListeners(); addErrorListener(listener) }
            val result = parser.rule()
            assertTrue(errors.isEmpty(), "$source: $errors")
            assertEquals(Token.EOF, tokens.LA(1), "Unconsumed source: $source")
            return result
        }
        private fun local(expression: Expression): DeclarationId = assertIs<NameUse.Local>(assertIs<Expression.Name>(expression).use).target
    }

    @Test
    fun initializerBindsBeforeLocalPublicationAndBlockShadowingDoesNotEscape() {
        val capture = Capture()
        val root = capture.block("{ var x = x; if flag { var x = x; return x; }; return x; }")
        val outer = assertIs<Statement.Local>(root.statements[0])
        assertEquals(DeclarationId(1), local(assertNotNull(outer.initializer)))
        val branch = assertIs<Statement.Block>(assertIs<Statement.If>(root.statements[1]).branches.single().body)
        val inner = assertIs<Statement.Local>(branch.statements[0])
        assertEquals(outer.declaration, local(assertNotNull(inner.initializer)))
        assertEquals(inner.declaration, local(assertNotNull(assertIs<Statement.Return>(branch.statements[1]).value)))
        assertEquals(outer.declaration, local(assertNotNull(assertIs<Statement.Return>(root.statements[2]).value)))
        assertTrue(root.scope != branch.scope)
        assertEquals(0x0301, root.tag)
    }

    @Test
    fun localStorageDeclaredTypesAssignmentsAndEmptyStatementsRemainStructured() {
        val capture = Capture()
        val root = capture.block("{ const fixed: int = 1; external ext: int; import var incoming: int; var plain: int; plain += fixed; }")
        val locals = root.statements.take(4).map { assertIs<Statement.Local>(it) }
        assertEquals(listOf(LocalStorage.CONST, LocalStorage.EXTERNAL, LocalStorage.IMPORT_VAR, LocalStorage.VAR),
            locals.map { capture.facts.getValue(it.declaration).storage })
        locals.forEach { assertIs<TypeSyntax.Name>(capture.facts.getValue(it.declaration).declaredType) }
        val assignment = assertIs<Statement.Assign>(root.statements[4])
        assertEquals("+=", assignment.operator)
        assertEquals(locals[3].declaration, local(assignment.target))
        assertEquals(locals[0].declaration, local(assignment.value))
        assertIs<Statement.Empty>(capture.bridge.statement(parse(";") { statement() }))
    }

    @Test
    fun branchesLoopsAndSingleStatementBlocksPreserveScopeAndControlOperations() {
        val capture = Capture()
        val root = capture.block("{ if flag return x else if other continue else break; while flag { continue; }; do { var x = 2; } while x; for (x : [x]) return x; }")
        val branch = assertIs<Statement.If>(root.statements[0])
        assertEquals(2, branch.branches.size)
        assertIs<Statement.Return>(branch.branches[0].body)
        assertIs<Statement.Continue>(branch.branches[1].body)
        assertIs<Statement.Break>(branch.elseBody)
        assertIs<Statement.Continue>(assertIs<Statement.Block>(assertIs<Statement.While>(root.statements[1]).body).statements.single())
        val loop = assertIs<Statement.DoWhile>(root.statements[2])
        assertEquals(DeclarationId(1), local(loop.condition))
        val foreach = assertIs<Statement.Foreach>(root.statements[3])
        assertEquals(DeclarationId(1), local(assertIs<Expression.ListValue>(foreach.iterable).elements.single()))
        assertEquals(foreach.variable, local(assertNotNull(assertIs<Statement.Return>(foreach.body).value)))
    }

    @Test
    fun storeAndExecuteUseTheirDedicatedBindingRoutesAndCommandsKeepInterpolation() {
        val capture = Capture()
        val root = capture.block("""
            {
                try { var x = 2; } store(x);
                execute(as = @e, positioned.x = x) { return; };
                /say value=${'$'}{x}
                return;
            }
        """.trimIndent())
        val store = assertIs<Statement.TryStore>(root.statements[0])
        assertEquals(NameUse.Local(DeclarationId(1)), store.destination)
        val execute = assertIs<Statement.Execute>(root.statements[1])
        assertEquals(ExecutePath.As, execute.contexts[0].path)
        assertIs<Expression.Selector>(execute.contexts[0].value)
        val parts = assertIs<ExecutePath.Members>(execute.contexts[1].path).parts
        assertEquals(2, parts.size)
        assertIs<NameUse.Declaration>(assertIs<Expression.Name>(parts[1]).use)
        assertEquals(DeclarationId(1), local(execute.contexts[1].value))
        val command = assertIs<Statement.Command>(root.statements[2])
        assertEquals(TextPart.Text("say value="), command.parts[0])
        assertEquals(DeclarationId(1), local(assertIs<TextPart.Interpolation>(command.parts[1]).expression))
        assertNull(assertIs<Statement.Return>(root.statements[3]).value)
    }

    @Test
    fun nestedLambdasPreserveStatementTailSeparationAndPropagateOuterCaptures() {
        val capture = Capture()
        val root = capture.block("{ var captured = 1; var callback = { p: int -> var local = p; { q -> captured + local + q } }; }")
        val outerDeclaration = assertIs<Statement.Local>(root.statements[0]).declaration
        val outer = assertIs<Expression.Lambda>(assertNotNull(assertIs<Statement.Local>(root.statements[1]).initializer))
        val outerBody = capture.bodies.getValue(outer.body)
        val localStatement = assertIs<Statement.Local>(outerBody.root.statements.single())
        assertEquals(outer.parameters.single(), local(assertNotNull(localStatement.initializer)))
        val inner = assertIs<Expression.Lambda>(outerBody.tail)
        val innerBody = capture.bodies.getValue(inner.body)
        assertTrue(innerBody.root.statements.isEmpty())
        assertIs<Expression.Binary>(innerBody.tail)
        assertEquals(listOf(DeclarationReference.Local(outerDeclaration), DeclarationReference.Local(localStatement.declaration)), innerBody.captures)
        assertEquals(listOf(DeclarationReference.Local(outerDeclaration)), outerBody.captures)
        assertEquals(outerBody.tail, outer.tail)
        assertIs<TypeSyntax.Name>(capture.lambdaParameters.getValue(outer.declaration).single().declaredType)
        assertTrue(capture.scopes.any { it.third == outer.declaration })
        assertTrue(capture.scopes.any { it.third == inner.declaration })
    }

    @Test
    fun implicitItReceiverCapturesAndSemicolonTerminatedLambdaExpressionsStayDistinct() {
        val capture = Capture()
        val implicit = capture.bridge.lambda(parse("{ it }") { inlineLambda() })
        assertEquals(implicit.parameters.single(), local(assertNotNull(implicit.tail)))
        assertTrue(capture.bodies.getValue(implicit.body).captures.isEmpty())
        val receiver = capture.bridge.lambda(parse("{ this }") { inlineLambda() })
        assertEquals(listOf(DeclarationReference.Local(DeclarationId(5))), capture.bodies.getValue(receiver.body).captures)
        val terminated = capture.bridge.lambda(parse("{ p -> p; }") { inlineLambda() })
        val body = capture.bodies.getValue(terminated.body)
        assertNull(terminated.tail)
        assertNull(body.tail)
        assertIs<Statement.Evaluate>(body.root.statements.single())
    }

    @Test
    fun templateMemberStreamDelegatesDeclarationDetailsAndRetainsAnnotationTargets() {
        val capture = Capture()
        val source = parse("""
            {
                public var value: int = 1;
                @Marker(2)
                protected override func read(): int { return value; };
                constructor() { value = 3; };
                operator +(other: int): int { return value; };
            }
        """.trimIndent()) { templateBody() }
        val body = capture.bridge.template(source)
        assertEquals(5, body.members.size)
        val field = assertIs<TemplateMember.Declaration>(body.members[0]).declaration
        val annotation = assertIs<TemplateMember.AnnotationApplication>(body.members[1])
        val function = assertIs<TemplateMember.Declaration>(body.members[2]).declaration
        assertEquals(listOf(function), annotation.targets)
        assertEquals(Triple("protected", true, false), capture.templateHeaders.getValue(function))
        assertEquals(setOf(field), capture.templateInitializers.keys)
        assertEquals(3, capture.templateBodies.size)
        assertEquals(0x0401, body.tag)
    }

    @Test
    fun diagnosticOriginsCountUtf16AndRecoveredSyntaxIsRejected() {
        val capture = Capture()
        val source = "{\nvar icon = \"😀\";\nreturn icon;\n}"
        val block = capture.block(source)
        val returned = assertIs<Statement.Return>(block.statements[1])
        val origin = capture.origins.first { it.first === returned }.second
        assertEquals(source.indexOf("return").toLong(), origin.start.offset)
        assertEquals(SourcePosition(source.indexOf("return").toLong(), 3, 1), origin.start)
        assertEquals("fixture.mcfpp", origin.logicalUnit)
        val parser = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString("{ return +; }")))).apply { removeErrorListeners() }
        val invalid = parser.curlBlock()
        assertTrue(parser.numberOfSyntaxErrors > 0)
        assertEquals("LIB_BODY_UNSUPPORTED", assertFailsWith<SyntaxCaptureException> { capture.bridge.block(invalid) }.code)
    }
}
