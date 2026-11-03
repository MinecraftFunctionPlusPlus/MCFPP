package top.mcfpp.test.library

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.core.lang.Var
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.SyntaxCodec
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPType
import java.util.IdentityHashMap
import kotlin.test.*

class LibraryBodyTest {
    /** The fixture starts as semantic DTOs and crosses N/Q/B bytes; it has no source/parser input. */
    private class Fixture : StructuredSessionBindings, StatementProjectionBindings {
        private val constants = linkedMapOf<ConstantRef, ConstantValue>()
        val functions = mutableMapOf<DeclarationId, Function>()
        private val frames = IdentityHashMap<Function, MutableMap<DeclarationId, Var<*>>>()
        private val names = mapOf(1L to "add", 2L to "main", 10L to "left", 11L to "right",
            20L to "left", 21L to "right", 22L to "known", 23L to "unknown", 24L to "mixed", 90L to "int", 94L to "Alias")
        val locals = linkedMapOf<DeclarationId, LocalDeclarationFacts>()
        val syntax: LibrarySyntax
        val repository = SessionBodyRepository()
        val library: SessionLibraryId

        init {
            val calleeScope = ScopeId(1)
            val callerScope = ScopeId(2)
            fun local(scope: ScopeId, id: Long) = Expression.Name(scope, NameUse.Local(DeclarationId(id)))
            fun integer(value: Long): Expression.Literal {
                val raw = ConstantRef(constants.size + 1L).also { constants[it] = ConstantValue.Integral(value) }
                val typed = ConstantRef(constants.size + 1L).also { constants[it] = ConstantValue.Typed(TypeRef(1), raw) }
                return Expression.Literal(callerScope, typed)
            }
            fun call(left: Expression, right: Expression) = Expression.Call(callerScope,
                Expression.Name(callerScope, NameUse.Declaration(DeclarationReference.Local(DeclarationId(1)))),
                emptyList(), listOf(Argument(left), Argument(right)))
            fun declaration(id: Long, initializer: Expression): Statement.Local {
                val reference = DeclarationId(id)
                locals[reference] = LocalDeclarationFacts(names.getValue(id), LocalStorage.EXTERNAL,
                    TypeSyntax.Name(callerScope, NameUse.Declaration(DeclarationReference.Local(DeclarationId(90))), TypeRef(1)), initializer)
                return Statement.Local(callerScope, reference, initializer)
            }
            val add = Statement.Block(calleeScope, listOf(
                Statement.Command(calleeScope, listOf(TextPart.Text("say called"))),
                Statement.Return(calleeScope, Expression.Binary(calleeScope, "+", local(calleeScope, 10), local(calleeScope, 11)))
            ))
            val main = Statement.Block(callerScope, listOf(
                declaration(20, integer(4)), declaration(21, integer(5)),
                declaration(22, call(integer(2), integer(3))),
                declaration(23, call(local(callerScope, 20), local(callerScope, 21))),
                declaration(24, call(local(callerScope, 20), integer(3)))
            ))
            val original = SyntaxCodec.flatten(listOf(
                ScopeRecord(DeclarationId(1), null, listOf(DeclarationId(10), DeclarationId(11))),
                ScopeRecord(DeclarationId(2), null, locals.keys.toList())), listOf(
                SyntaxBodySource(DeclarationId(1), EnvironmentId(1), BodyRole.FUNCTION, add),
                SyntaxBodySource(DeclarationId(2), EnvironmentId(1), BodyRole.FUNCTION, main)))
            val references = object : SyntaxReferenceValidator {
                override fun declaration(reference: DeclarationReference) {
                    require(reference is DeclarationReference.Local && reference.target.value in names)
                }
                override fun environment(reference: EnvironmentId) { require(reference == EnvironmentId(1)) }
                override fun type(reference: TypeRef) { require(reference == TypeRef(1)) }
                override fun constant(reference: ConstantRef) { require(reference in constants) }
                override fun annotation(reference: AnnotationId) = error("No annotation in this fixture")
            }
            val strings = LibraryStrings.of(SyntaxCodec.collectStrings(original))
            syntax = SyntaxCodec.decode(SyntaxCodec.encode(original, strings, references), strings, references)
            library = repository.register(this, this)
        }

        override fun name(use: NameUse, role: NameRole): ProjectedName {
            val reference = when (use) {
                is NameUse.Local -> use.target
                is NameUse.Declaration -> (use.target as DeclarationReference.Local).target
                is NameUse.Lookup -> return ProjectedName(use.name, use.namespace,
                    StructuredNameBinding.Functions(use.candidates.flatMap { (declaration(it) as StructuredNameBinding.Functions).candidates }))
            }
            val binding = if (use is NameUse.Local) StructuredNameBinding.Frame(reference, names.getValue(reference.value))
                else declaration(DeclarationReference.Local(reference))
            return ProjectedName(names.getValue(reference.value), binding = binding)
        }
        override fun declaration(reference: DeclarationReference): StructuredNameBinding {
            val id = (reference as DeclarationReference.Local).target
            if (id == DeclarationId(90)) return StructuredNameBinding.Type(MCFPPBaseType.Int)
            if (id == DeclarationId(94)) return StructuredNameBinding.Type(MCFPPListType(MCFPPBaseType.Int))
            return StructuredNameBinding.Functions(listOf(functions.getValue(id)))
        }
        override fun local(declaration: DeclarationId, frame: Function): Var<*>? = frames[frame]?.get(declaration)
            ?: when (declaration.value) {
                10L -> frame.normalParams.getOrNull(0)?.let { frame.scope.getVar(it.identifier) }
                11L -> frame.normalParams.getOrNull(1)?.let { frame.scope.getVar(it.identifier) }
                else -> null
            }
        override fun publishLocal(declaration: DeclarationId, frame: Function, value: Var<*>) {
            frames.getOrPut(frame) { mutableMapOf() }[declaration] = value
        }
        fun addConstant(value: ConstantValue): ConstantRef = ConstantRef(constants.size + 1L).also { constants[it] = value }
        override fun constant(reference: ConstantRef) = constants.getValue(reference)
        override fun type(reference: TypeRef): Any = when (reference.value) {
            2L -> MCFPPBaseType.Float
            3L -> MCFPPNBTType.Double
            4L -> MCFPPListType(MCFPPBaseType.Int)
            else -> MCFPPBaseType.Int
        }
        override fun builtinKey(reference: TypeRef) = when (reference.value) { 2L -> "float"; 3L -> "double"; else -> "int" }
        override fun body(reference: BodyId) = syntax.node(syntax.body(reference).root)
        override fun name(declaration: DeclarationId) = names.getValue(declaration.value)
        override fun local(declaration: DeclarationId) = locals.getValue(declaration)
        override fun lambdaParameter(declaration: DeclarationId): LambdaParameterSyntax = error("No lambda parameters")
        fun handle(id: Long) = repository.handle(library, BodyId(id))
    }

    @Test
    fun restoredOrdinaryBodiesRunKnownUnknownAndMixedArgumentsWithOneSideEffectPerCall() {
        Project.config.version = "26.3"
        Project.config.rootNamespace = "library"
        Project.init()
        val namespace = GlobalScope.getOrCreateNamespace("library.test")
        val file = MCFPPFile(namespace)
        MCFPPFile.currFile = file
        Project.currNamespace = namespace.identifier
        val fixture = Fixture()
        try {
            val add = Function("add", namespace.identifier, null).apply {
                declarationFile = file
                returnType = MCFPPBaseType.Int
                appendNormalParam(MCFPPBaseType.Int, "left")
                appendNormalParam(MCFPPBaseType.Int, "right")
                buildParamVar()
                bodyHandle = fixture.handle(1)
            }
            val main = Function("main", namespace.identifier, null).apply {
                declarationFile = file
                bodyHandle = fixture.handle(2)
            }
            namespace.scope.addFunction(add, false)
            namespace.scope.addFunction(main, false)
            fixture.functions[DeclarationId(1)] = add
            val consumer = GlobalScope.getOrCreateNamespace("consumer")
            consumer.scope.addFunction(Function("add", consumer.identifier, null).apply {
                returnType = MCFPPBaseType.Bool
                appendNormalParam(MCFPPBaseType.Int, "left")
                appendNormalParam(MCFPPBaseType.Int, "right")
                buildParamVar()
            }, false)
            main.declarationFile = MCFPPFile(consumer)
            assertNull(add.ast)
            assertNull(main.ast)
            main.compileBody()
            top.mcfpp.optimization.OptimizationPipeline.finish()
            assertEquals(0, Project.errorCount)
            assertTrue(main.bodyCompiled)
            assertTrue(add.bodyCompiled)
            assertNotNull(main.typedIR)
            assertNotNull(add.typedIR)
            assertTrue(add.compiledFunctions.isEmpty(), "Ordinary constant arguments do not create specializations")
            val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
                Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }
            val entry = listOf(Commands.stackIn().analyze()) + main.commands.analyzeAll() + Commands.stackOut().analyze()
            val machine = ScoreCommandExecutor(entry, functions)
            assertEquals(5, machine.read(main.scope.getVar("known") as MCInt))
            assertEquals(9, machine.read(main.scope.getVar("unknown") as MCInt))
            assertEquals(7, machine.read(main.scope.getVar("mixed") as MCInt))
            assertEquals(listOf("called", "called", "called"), machine.messages)
            assertEquals(0, machine.stackDepth)
            assertTrue(machine.branchGuards.isEmpty())
        } finally {
            fixture.repository.close()
            MCFPPFile.currFile = null
        }
    }

    @Test
    fun copiedHandlesKeepTheirLibraryAndEveryProjectionIsFreshUntilSessionClose() {
        val fixture = Fixture()
        val function = Function("detached", "library.test", null).apply { bodyHandle = fixture.handle(1) }
        val copied = Function(function)
        assertSame(function.bodyHandle, copied.bodyHandle)
        assertEquals(fixture.library, copied.bodyHandle?.library)
        val first = assertNotNull(function.bodyContext())
        val second = assertNotNull(copied.bodyContext())
        assertNotSame(first, second)
        assertNotSame(first.start, second.start)
        assertEquals(first.text, second.text)
        assertEquals(fixture.body(BodyId(1)), StructuredBodyContexts.origin(first)?.node)
        assertSame(fixture, StructuredBodyContexts.bindings(first))
        assertEquals(function.bodyHandle, StructuredBodyContexts.body(second))
        fixture.repository.close()
        fixture.repository.close()
        assertNull(StructuredBodyContexts.origin(first))
        assertNull(StructuredBodyContexts.projected(second))
        assertFailsWith<IllegalStateException> { copied.bodyContext() }
        assertFailsWith<IllegalStateException> { fixture.handle(1) }
        val next = Fixture()
        try {
            val nextBody = next.handle(1).project()
            assertNotEquals(fixture.library, nextBody.handle.library)
            assertNull(StructuredBodyContexts.origin(first))
            assertNotNull(StructuredBodyContexts.origin(nextBody.context))
            assertFailsWith<IllegalArgumentException> { next.repository.body(assertNotNull(function.bodyHandle)) }
        } finally { next.repository.close() }
    }

    @Test
    fun authorCandidatesSurviveConsumerFunctionAndFunctionValueShadowingInVisitorAndBinder() {
        Project.config.version = "26.3"
        Project.init()
        val author = GlobalScope.getOrCreateNamespace("author")
        val consumer = GlobalScope.getOrCreateNamespace("consumer")
        val fixture = Fixture()
        try {
            val authorFile = MCFPPFile(author)
            MCFPPFile.currFile = authorFile
            val add = Function("add", author.identifier, null).apply {
                declarationFile = authorFile
                returnType = MCFPPBaseType.Int
                appendNormalParam(MCFPPBaseType.Int, "left")
                appendNormalParam(MCFPPBaseType.Int, "right")
                buildParamVar()
                bodyHandle = fixture.handle(1)
            }
            fixture.functions[DeclarationId(1)] = add
            author.scope.addFunction(add, false)
            add.compileBody()
            val consumerFile = MCFPPFile(consumer)
            MCFPPFile.currFile = consumerFile
            Project.currNamespace = consumer.identifier
            val shadow = Function("add", consumer.identifier, null).apply {
                returnType = MCFPPBaseType.Int
                appendNormalParam(MCFPPBaseType.Int, "left")
                appendNormalParam(MCFPPBaseType.Int, "right")
                buildParamVar()
                bodyCompiled = true
            }
            consumer.scope.addFunction(shadow, false)
            val caller = Function("caller", consumer.identifier, null).apply { declarationFile = consumerFile }
            val signature = MCFPPFunctionType(listOf(MCFPPBaseType.Int, MCFPPBaseType.Int), MCFPPBaseType.Int)
            caller.scope.putVar("add", FunctionValue("add", signature, shadow), true)
            val original = ((fixture.body(BodyId(2)) as Statement.Block).statements[2] as Statement.Local).initializer as Expression.Call
            val lookup = original.copy(callee = Expression.Name(original.scope, NameUse.Lookup("add", null,
                EnvironmentId(1), listOf(DeclarationReference.Local(DeclarationId(1))))))
            val projection = CurrentSyntaxProjection(fixture, fixture)
            val context = projection.expression(lookup)
            fixture.repository.registerProjection(fixture.handle(2), projection, context)
            assertEquals(MCFPPBaseType.Int, DeclarationBinding(caller, emptyMap()).expression(context).type)
            val returned = caller.runInFunction { MCFPPExprVisitor().visitExpression(context) } as MCInt
            top.mcfpp.optimization.OptimizationPipeline.finish()
            assertEquals(0, Project.errorCount)
            val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .associate { it.namespaceID.toString() to it.commands.analyzeAll() }
            val machine = ScoreCommandExecutor(listOf(Commands.stackIn().analyze()) + caller.commands.analyzeAll() +
                Commands.stackOut().analyze(), functions)
            assertEquals(5, machine.read(returned))
            assertEquals(listOf("called"), machine.messages)
            assertEquals(0, machine.stackDepth)
        } finally { fixture.repository.close(); MCFPPFile.currFile = null }
    }

    @Test
    fun semanticConstantsAndClosedTypesProduceActualValuesWithoutReadingDisplayLiterals() {
        Project.config.version = "26.3"
        Project.init()
        val fixture = Fixture()
        val caller = Function("facts", "consumer", null)
        try {
            fun literal(value: ConstantValue, type: TypeRef): top.mcfpp.antlr.mcfppParser.ExpressionContext {
                val raw = fixture.addConstant(value)
                val typed = fixture.addConstant(ConstantValue.Typed(type, raw))
                val projection = CurrentSyntaxProjection(fixture, fixture)
                val context = projection.expression(Expression.Literal(ScopeId(1), typed))
                fixture.repository.registerProjection(fixture.handle(1), projection, context)
                return context
            }
            val negativeZero = literal(ConstantValue.FloatBits((-0.0f).toRawBits()), TypeRef(2))
            val nanBits = 0x7ff8000000001234L
            val nan = literal(ConstantValue.DoubleBits(nanBits), TypeRef(3))
            assertEquals("null", nan.text)
            fun payload(value: CompilerValue?): CompilerValue? = if (value is CompilerValue.Typed) payload(value.payload) else value
            caller.runInFunction {
                assertEquals(CompilerValue.FloatBits((-0.0f).toRawBits()),
                    payload(StorageAccess.snapshot(MCFPPExprVisitor().visitExpression(negativeZero))))
                assertEquals(CompilerValue.DoubleBits(nanBits),
                    payload(StorageAccess.snapshot(MCFPPExprVisitor().visitExpression(nan))))
            }
            assertEquals(CompilerValue.DoubleBits(nanBits), payload(DeclarationBinding(caller, emptyMap()).expression(nan).constant))
            val projection = CurrentSyntaxProjection(fixture, fixture)
            val alias = projection.type(TypeSyntax.Name(ScopeId(1),
                NameUse.Declaration(DeclarationReference.Local(DeclarationId(94))), TypeRef(4)))
            fixture.repository.registerProjection(fixture.handle(1), projection, alias)
            assertEquals("Alias", alias.text)
            caller.scope.putType("Alias", MCFPPBaseType.Bool, true)
            val expected = MCFPPListType(MCFPPBaseType.Int)
            assertEquals(expected, MCFPPType.parseFromContext(alias, caller.scope, caller))
            assertEquals(expected, DeclarationBinding(caller, emptyMap()).type(alias))
            assertEquals(0, Project.errorCount)
        } finally { fixture.repository.close() }
    }
}
