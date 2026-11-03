package top.mcfpp.test.library

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*

class LibrarySpecializationTest {
    private fun parse(text: String): P.CompilationUnitContext {
        val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, "author"))))
        return parser.compilationUnit().also { assertEquals(0, parser.numberOfSyntaxErrors, text) }
    }

    /** Capture real syntax once; every consumer below receives only decoded N/Q/B and D records. */
    private class Arena : StatementCaptureBindings, StructuredSessionBindings, StatementProjectionBindings,
        StructuredFunctionSignatures {
        val repository = SessionBodyRepository()
        val names = mapOf(2L to "T", 3L to "n", 4L to "value", 5L to "item")
        val constants = linkedMapOf<ConstantRef, ConstantValue>()
        val bodies = mutableListOf<SyntaxBodySource>()
        val roots = mutableListOf<SyntaxRoot>()
        val formals = mutableMapOf<DeclarationId, FunctionParam>()
        val types = mutableMapOf<TypeRef, MCFPPType>(TypeRef(1) to MCFPPBaseType.Int, TypeRef(2) to MCFPPConcreteType.Type)
        lateinit var syntax: LibrarySyntax
        lateinit var library: SessionLibraryId
        private var q = ScopeId(1)
        override fun scope(site: ParserRuleContext) = q
        override fun <T> withScope(site: ParserRuleContext, owner: DeclarationId?, action: (ScopeId) -> T) = action(q)
        override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse =
            names.entries.singleOrNull { it.value == name }?.let { NameUse.Local(DeclarationId(it.key)) }
                ?: NameUse.Lookup(name, namespace, EnvironmentId(1), emptyList())
        override fun builtinType(key: String, site: ParserRuleContext) = if (key == "Type") TypeRef(2) else TypeRef(1)
        override fun internConstant(value: ConstantValue, site: ParserRuleContext) = ConstantRef(constants.size + 1L).also { constants[it] = value }
        override fun reserveLocal(site: P.FieldDeclarationContext): DeclarationId = error("No local fixture")
        override fun completeLocal(declaration: DeclarationId, facts: LocalDeclarationFacts) = Unit
        override fun publishLocal(declaration: DeclarationId, name: String, site: ParserRuleContext) = Unit
        override fun reserveIterationVariable(site: P.ForeachStatementContext, iterable: Expression): DeclarationId = error("No iteration")
        override fun destination(name: String, site: P.TryStoreStatementContext): NameUse = error("No store")
        override fun executePathPart(site: P.VarContext, capture: StatementBridge): Expression = error("No execute")
        override fun reserveLambda(site: P.InlineLambdaContext): LambdaAllocation = error("No captured lambda")
        override fun completeLambdaParameters(allocation: LambdaAllocation, parameters: List<LambdaParameterSyntax>, explicit: Boolean) = Unit
        override fun completeLambda(allocation: LambdaAllocation, body: LambdaBodyCapture) = Unit
        override fun receiverCapture(owner: DeclarationReference): DeclarationReference? = null
        override fun templateMember(site: P.TemplateMemberDeclarationContext, capture: StatementBridge): TemplateMember =
            TemplateMember.Declaration(q, DeclarationId(5))
        fun freeze() {
            val raw = SyntaxCodec.flatten(listOf(ScopeRecord(DeclarationId(1), null, names.keys.map(::DeclarationId))), bodies, roots)
            val strings = LibraryStrings.of(SyntaxCodec.collectStrings(raw))
            val refs = object : SyntaxReferenceValidator {
                override fun declaration(reference: DeclarationReference) = Unit
                override fun environment(reference: EnvironmentId) = Unit
                override fun type(reference: TypeRef) { require(reference.value in 1..3) }
                override fun constant(reference: ConstantRef) { require(reference in constants) }
                override fun annotation(reference: AnnotationId) = error("No annotation syntax")
            }
            syntax = SyntaxCodec.decode(SyntaxCodec.encode(raw, strings, refs), strings, refs)
            library = repository.register(this, this)
        }
        override fun name(use: NameUse, role: NameRole): ProjectedName = when (use) {
            is NameUse.Local -> ProjectedName(names.getValue(use.target.value), binding = StructuredNameBinding.Frame(use.target, names.getValue(use.target.value)))
            is NameUse.Lookup -> ProjectedName(use.name, use.namespace,
                StructuredNameBinding.Type(if (use.name == "Type") MCFPPConcreteType.Type else MCFPPBaseType.Int))
            is NameUse.Declaration -> error("No global names")
        }
        override fun declaration(reference: DeclarationReference): StructuredNameBinding = error("No global D")
        override fun local(declaration: DeclarationId, frame: Function): Var<*>? = frame.scope.getVar(names.getValue(declaration.value))
        override fun formal(declaration: DeclarationId) = formals[declaration]
        override fun type(reference: TypeRef): Any = types.getValue(reference)
        override fun boundType(reference: TypeRef, frame: Function): MCFPPType? =
            if (reference == TypeRef(3)) frame.scope.getType("T") else types[reference]
        override fun dependentType(node: TypeSyntax, frame: Function): MCFPPType? =
            if ((node as? TypeSyntax.Name)?.use == NameUse.Local(DeclarationId(2))) frame.scope.getType("T") else null
        override fun declaredType(type: MCFPPType, function: Function, readonly: Map<String, ClosedReadonlyBinding>): MCFPPType? {
            if (type !is MCFPPGenericParamType && type !is UnresolvedType) return null
            val binding = readonly["T"] ?: return function.scope.getType("T")?.takeUnless { it is MCFPPGenericParamType }
            val payload = (binding.value as? CompilerValue.Typed)?.payload ?: binding.value
            val id = (payload as? CompilerValue.TypeValue)?.id ?: return null
            return binding.descriptors[id] ?: MCFPPType.resolveTypeId(id)
        }
        override fun constant(reference: ConstantRef) = constants.getValue(reference)
        override fun builtinKey(reference: TypeRef) = when (reference.value) { 1L -> "int"; 2L -> "Type"; else -> null }
        override fun body(reference: BodyId) = syntax.node(syntax.body(reference).root)
        override fun name(declaration: DeclarationId) = names.getValue(declaration.value)
        override fun local(declaration: DeclarationId): LocalDeclarationFacts = error("No locals")
        override fun lambdaParameter(declaration: DeclarationId): LambdaParameterSyntax = error("No lambda parameter")
        fun handle(index: Long) = repository.handle(library, BodyId(index))
    }

    @Test
    fun restoredReadonlyFunctionUsesStructuredDefaultsTypesAndOneBodyForOrdinaryArguments() {
        Project.config.version = "26.3"; Project.init()
        val namespace = GlobalScope.getOrCreateNamespace("author.generic")
        val file = MCFPPFile(namespace)
        MCFPPFile.currFile = file
        val arena = Arena()
        try {
            val declaration = parse("func add<T,n:int=2>(value:T=3):T {\n/say once\nreturn value+n\n}")
                .typeDeclaration().single().declarations().functionDeclaration()
            val bridge = StatementBridge(arena)
            val params = declaration.functionDeclarationPart().functionParams()
            arena.bodies += SyntaxBodySource(DeclarationId(1), EnvironmentId(1), BodyRole.FUNCTION, bridge.block(declaration.curlBlock()))
            arena.bodies += SyntaxBodySource(DeclarationId(3), EnvironmentId(1), BodyRole.DEFAULT,
                bridge.expression(params.readOnlyParams().readOnlyParameterList().readOnlyParameter()[1].value()))
            arena.bodies += SyntaxBodySource(DeclarationId(4), EnvironmentId(1), BodyRole.DEFAULT,
                bridge.expression(params.normalParams().parameterList().parameter().single().value()))
            arena.freeze()
            fun common(name: String, owner: DeclarationId? = DeclarationId(1)) =
                DeclCommon(null, null, name, owner, EnvironmentId(1), DeclarationAccess.PUBLIC, 0)
            val records = listOf<LibraryDeclaration>(
                FunctionDeclaration(common("add", null), FunctionExecutionKind.ORDINARY, null,
                    listOf(DeclarationId(2), DeclarationId(3)), listOf(DeclarationId(4)), TypeRef(3), BodyId(1), null),
                ValueDeclaration(common("T"), ValueRole.READONLY_PARAMETER, ValueStorage.PARAMETER, TypeRef(2), null, null, null, parameterIndex = 0),
                ValueDeclaration(common("n"), ValueRole.READONLY_PARAMETER, ValueStorage.PARAMETER, TypeRef(1), null, BodyId(2), null, parameterIndex = 1),
                ValueDeclaration(common("value"), ValueRole.NORMAL_PARAMETER, ValueStorage.PARAMETER, TypeRef(3), null, BodyId(3), null, parameterIndex = 0))
            val model = LibraryDeclarations(records, listOf(EnvironmentRecord(namespace.identifier, emptyList())))
            val strings = LibraryStrings.of(FunctionCodec.collectStrings(model))
            val decoded = FunctionCodec.decodeDeclarations(FunctionCodec.encodeDeclarations(records, strings), strings)
            val restored = FunctionDeclarations.restoreHead(DeclarationId(1), decoded[0] as FunctionDeclaration,
                { decoded[(it.value - 1).toInt()] as ValueDeclaration }, object : FunctionRestoreBindings {
                    override fun type(reference: TypeRef) = arena.types.getValue(reference)
                    override fun constant(reference: ConstantRef): RestoredDefault = error("Defaults are B")
                    override fun environment(reference: EnvironmentId) = file
                    override fun owner(reference: DeclarationId): CompoundData? = null
                    override fun nativeMethod(binding: NativeBinding): java.lang.reflect.Method = error("No native")
                    override fun readonlyType(owner: DeclarationId, index: Int, parameter: FunctionParam, type: MCFPPGenericParamType) { arena.types[TypeRef(3)] = type }
                    override fun parameterRestored(id: DeclarationId, parameter: FunctionParam, record: ValueDeclaration) {
                        arena.formals[id] = parameter
                        parameter.defaultBodyHandle = record.defaultBody?.let { arena.handle(it.value) }
                    }
                })
            val generic = assertIs<GenericFunction>(restored.function)
            generic.bodyHandle = arena.handle(1)
            namespace.scope.addFunction(generic, false)
            assertNull(generic.ast)
            val default = generic.normalParams.single()
            assertNotSame(default.defaultSyntax(), default.defaultSyntax())
            assertEquals(DeclarationId(4), arena.syntax.body(BodyId(3)).owner)
            val lexical = DeclarationLexicalSnapshot.capture(generic)
            val span = top.mcfpp.mni.api.SourceSpan("author", 0, 1, 1, 1, 1, 2)
            val typeArgument = PreparedExpressionFact.fromBound(DeclarationBinding.Bound(MCFPPConcreteType.Type,
                CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(MCFPPBaseType.Int.typeId)),
                descriptors = mapOf(MCFPPBaseType.Int.typeId to MCFPPBaseType.Int)), span,
                DeclarationBinding(DeclarationPreparationContext(lexical)))
            val prepared = assertIs<PreparedCandidate.Applicable>(SpecializationPolicy.prepareDeclaredSignature(
                generic, lexical, listOf(typeArgument), span))
            assertEquals(MCFPPBaseType.Int, prepared.signature.normal.single().type)
            val caller = Function("caller", namespace.identifier, null).apply { declarationFile = file }
            val returned = caller.runInFunction {
                val unknown = MCInt("unknown").assignedBy(MCInt(4))
                listOf(generic.invoke(listOf(MCFPPTypeVar(MCFPPBaseType.Int)), emptyList(), null),
                    generic.invoke(listOf(MCFPPTypeVar(MCFPPBaseType.Int)), listOf(unknown), null),
                    generic.invoke(listOf(MCFPPTypeVar(MCFPPBaseType.Int)), listOf(MCInt(5)), null))
            }
            assertEquals(0, Project.errorCount)
            val compiled = generic.compiledFunctions.values.single()
            assertSame(generic.bodyHandle, compiled.bodyHandle)
            assertSame(default.defaultBodyHandle, compiled.normalParams.single().defaultBodyHandle)
            assertEquals(MCFPPBaseType.Int, compiled.returnType)
            val functions = (listOf(caller) + generic.compiledFunctions.values + generic.compiledFunctions.values.flatMap { it.compiledFunctions.values })
                .associate { it.namespaceID.toString() to it.commands.analyzeAll() }
            val executor = ScoreCommandExecutor(listOf(Commands.stackIn().analyze()) + caller.commands.analyzeAll() + Commands.stackOut().analyze(), functions)
            assertEquals(listOf(5, 6, 7), returned.map { executor.read(it as MCInt) })
            assertEquals(listOf("once", "once", "once"), executor.messages)
            assertEquals(0, executor.stackDepth)
            arena.repository.close()
            assertFailsWith<IllegalStateException> { default.defaultSyntax() }
        } finally { arena.repository.close(); MCFPPFile.currFile = null }
    }

    @Test
    fun templateSessionProjectsDependentFieldsAndParentsForEachActualReadonlyType() {
        Project.config.version = "26.3"; Project.init()
        val namespace = GlobalScope.getOrCreateNamespace("author.templates")
        val file = MCFPPFile(namespace)
        MCFPPFile.currFile = file
        val arena = Arena()
        try {
            val source = parse("data Box<T> { var item:T=7; }").typeDeclaration().single().declarations().templateDeclaration()
            val bridge = StatementBridge(arena)
            val root = bridge.template(source.templateBody())
            val initializer = bridge.expression(source.templateBody().templateMemberDeclaration().single().templateMember().templateFieldDeclaration().expression())
            arena.roots += SyntaxRoot(DeclarationId(1), emptyList(), root)
            arena.bodies += SyntaxBodySource(DeclarationId(1), EnvironmentId(1), BodyRole.FUNCTION, Statement.Block(root.scope, emptyList()))
            arena.bodies += SyntaxBodySource(DeclarationId(5), EnvironmentId(1), BodyRole.DEFAULT, initializer)
            // Template roots are projected directly; the repository retains the immutable DTO and provider.
            arena.freeze()
            val intParent = DataTemplate("ParentInt", namespace.identifier).apply { declarationFile = file }
            val floatParent = DataTemplate("ParentFloat", namespace.identifier).apply { declarationFile = file }
            namespace.scope.addTemplate(intParent.identifier, intParent, false)
            namespace.scope.addTemplate(floatParent.identifier, floatParent, false)
            val projections = mutableListOf<ProjectedSyntax>()
            val declaredType = TypeSyntax.Name(root.scope, NameUse.Local(DeclarationId(2)), TypeRef(3))
            val declarationProvider = object : StatementProjectionBindings by arena {
                override fun templateDeclaration(declaration: DeclarationId) = ProjectionTemplateDeclaration.Field("item", false, declaredType, initializer = BodyId(2))
                override fun body(reference: BodyId): SemanticNode = if (reference == BodyId(1)) arena.syntax.nodes.filterIsInstance<TemplateBody>().single() else arena.body(reference)
            }
            fun project(targetType: MCFPPType): ProjectedSyntax {
                val bindings = object : StructuredSessionBindings by arena {
                    override fun boundType(reference: TypeRef, frame: Function) = if (reference == TypeRef(3)) targetType else arena.types[reference]
                    override fun dependentType(node: TypeSyntax, frame: Function) = if (node == declaredType) targetType else null
                    override fun type(reference: TypeRef): Any = if (reference == TypeRef(3)) targetType else arena.types.getValue(reference)
                }
                val library = arena.repository.register(bindings, declarationProvider)
                val handle = arena.repository.handle(library, BodyId(1))
                val projection = CurrentSyntaxProjection(bindings, declarationProvider)
                return arena.repository.registerProjection(handle, projection, projection.template(declarationProvider.body(BodyId(1)) as TemplateBody))
            }
            val prototypeContext = project(MCFPPBaseType.Int).context as P.TemplateBodyContext
            val prototype = GenericDataTemplate(prototypeContext, "Box", namespace.identifier).apply {
                declarationFile = file
                readOnlyParams += DataTemplateParam("Type", "T", MCFPPConcreteType.Type)
            }
            prototype.structuredSession = object : GenericTemplateSession {
                override fun requireOpen() = arena.repository.requireOpen()
                override fun readonlyType(prototype: GenericDataTemplate, index: Int) = MCFPPConcreteType.Type
                override fun validateHeader(prototype: GenericDataTemplate) { assertEquals(1, prototype.readOnlyParams.size) }
                override fun parentTypes(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate?) = listOf(
                    if (target == null || target.scope.getType("T") == MCFPPBaseType.Int) intParent.getType() else floatParent.getType())
                override fun project(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate) = project(requireNotNull(target.scope.getType("T"))).also { projections += it }
                override fun cloneAnnotations(recorded: List<Annotation>) = TemplateAnnotationClone(emptyList(), emptyList())
                override fun bindMembers(prototype: GenericDataTemplate, target: CompiledGenericDataTemplate, projection: ProjectedSyntax) = Unit
            }
            namespace.scope.addTemplate(prototype.identifier, prototype, false)
            val first = assertNotNull(prototype.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
            val second = assertNotNull(prototype.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Float))))
            Project.completeTemplateDeclarations()
            assertEquals(0, Project.errorCount)
            assertSame(first, prototype.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
            assertEquals(MCFPPBaseType.Int, first.scope.getVar("item")?.type)
            assertEquals(MCFPPBaseType.Float, second.scope.getVar("item")?.type)
            assertTrue(first.isSubOf(intParent)); assertTrue(second.isSubOf(floatParent))
            assertNotEquals(first.nominalIdentity, second.nominalIdentity)
            assertNotSame(projections[0].context, projections[1].context)
            val initializerFact = DeclarationBinding(Function("initializer", first, null), emptyMap())
                .expression(first.preInit.getValue("item"))
            assertEquals(CompilerValue.Integral(7), (initializerFact.constant as CompilerValue.Typed).payload)
            arena.repository.close()
            assertFailsWith<IllegalStateException> { prototype.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))) }
        } finally { arena.repository.close(); MCFPPFile.currFile = null }
    }

    class Mark : Annotation("Mark", "author") {
        var shared: MutableList<Any?> = arrayListOf()
        var target: Any? = null
        var type: MCFPPType = MCFPPBaseType.Int
        override fun forFunction(function: Function) = Unit
        override fun forDataTemplate(data: DataTemplate) = Unit
        override fun forField(field: Var<*>) = Unit
    }

    @Test
    fun annotationSessionCloneRetainsSharedCellsDeclarationsTypesAndCompletedPhase() {
        val provider = PackageKey("fixture:generic-annotation", "1")
        val codec = object : PortableAnnotationCodec<Mark> {
            override val annotationClass = Mark::class.java
            override val schema = "fixture.mark"
            override val version = 1L
            override fun capture(value: Mark, writer: PortableAnnotationWriter) = writer.fields("shared" to value.shared,
                "target" to writer.declaration(requireNotNull(value.target)), "type" to writer.type(value.type))
            override fun allocateShell(reader: PortableAnnotationReader) = Mark()
            override fun restore(value: Mark, reader: PortableAnnotationReader) {
                value.shared = reader.list("shared"); value.target = reader.value("target"); value.type = reader.value("type") as MCFPPType
            }
        }
        val managed = object : ManagedNativeBindings {
            override fun loader(provider: PackageKey?) = javaClass.classLoader
            override fun provider(owner: Class<*>) = provider
        }
        val target = DataTemplate("SessionOnly", "author")
        val first = Mark().apply { this.target = target; type = target.getType() }
        val second = Mark().apply { this.target = target; type = first.type; shared = first.shared }
        first.shared.add(first.shared)
        val cloner = SessionAnnotationCloner(PortableAnnotationCodecs.builtins().register(codec, provider, managed)) {
            if (it === first) AnnotationPhase.COMPLETED else AnnotationPhase.PENDING
        }
        try {
            val clone = cloner.clone(listOf(first, second, first))
            val a = clone.annotations[0] as Mark; val b = clone.annotations[1] as Mark
            assertNotSame(first, a); assertSame(a, clone.annotations[2])
            assertSame(a.shared, b.shared); assertSame(a.shared, a.shared.single())
            assertNotSame(first.shared, a.shared)
            assertSame(target, a.target); assertSame(first.type, a.type)
            assertEquals(listOf(b), clone.pending)
            assertEquals(AnnotationPhase.COMPLETED, cloner.phase(a))
            val again = cloner.clone(clone.annotations)
            assertEquals(1, again.pending.size)
        } finally { cloner.close() }
        assertFailsWith<IllegalStateException> { cloner.clone(listOf(first)) }
    }
}
