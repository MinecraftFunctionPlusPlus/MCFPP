package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.*
import top.mcfpp.util.TempPool
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniHeaderTypeAdmissionTest {
    private val source = SourceSpan("admission.mcfpp", 0, 10, 1, 1, 1, 11)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, source.file))))
    private fun syntax(text: String): P.TypeContext = parser(text).let { parser ->
        parser.type().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun expression(text: String): P.ExpressionContext = parser(text).let { parser ->
        parser.expression().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun fixture(name: String = "admission.original"): MCFPPFile {
        Project.init()
        val namespace = Namespace(name)
        GlobalScope.localNamespaces[name] = namespace
        return MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }
    private fun prototype(
        file: MCFPPFile,
        name: String = "Box",
        type: MCFPPType = MCFPPConcreteType.Type,
        spelling: String = "Type",
        body: String = "{}"
    ) = GenericDataTemplate(parser(body).templateBody(), name, file.namespace.identifier).apply {
        declarationFile = file
        readOnlyParams.add(DataTemplateParam(spelling, "T", type))
        file.namespace.scope.genericTemplate[name to listOf(type)] = this
    }
    private fun function(file: MCFPPFile, input: String? = null, output: String? = null): Function = Function("target", file.namespace.identifier, null).apply {
        declarationFile = file
        input?.let { normalParams.add(FunctionParam(UnresolvedType(it), "input", this)) }
        output?.let { returnType = UnresolvedType(it) }
    }
    private fun lexical(file: MCFPPFile, session: SignaturePreparationSession) = DeclarationLexicalSnapshot.capture(file, file.namespace.identifier, headers = session)
    private fun binder(file: MCFPPFile, session: SignaturePreparationSession) = DeclarationBinding(DeclarationPreparationContext(lexical(file, session)))
    private fun payload(fact: PreparedExpressionFact) = assertIs<CompilerValue.Typed>(fact.value).payload
    private fun needs(session: SignaturePreparationSession, function: Function) = assertIs<SignaturePreparationResult.NeedsCompletion>(
        session.prepare(
            SignaturePreparationRequest(listOf(function), emptyList(), source)
        )
    )

    @Test fun actualNormalSignatureQueuesTheRealPrototypeAndTypedReadonlyWithoutPublishing() {
        val file = fixture()
        val box = prototype(file)
        val session = SignaturePreparationSession()
        val target = function(file, input = "Box<int>")
        val result = needs(session, target)
        val header = result.staged.headers.single()
        assertEquals(source, header.source)
        assertEquals(source, header.readonlyFacts.single().source)
        assertSame(box, header.prototype)
        assertSame(box, header.model.originTemplate)
        assertEquals(CompilerValue.TypeValue(MCFPPBaseType.Int.typeId), payload(header.readonlyFacts.single()))
        assertEquals(HeaderPhase.RESERVED, header.phase)
        assertSame(target, result.requests.single().function)
        assertFalse(header.isPublished)
        assertEquals(0, box.index)
        assertTrue(box.compiledTemplates.isEmpty())
        assertTrue(header.model.scope.allVars.isEmpty())
        assertTrue(target.commands.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun actualReturnSignatureConsumesClosedReadonlyFormalIdentity() {
        val file = fixture()
        prototype(file)
        val target = GenericFunction("target", file.namespace.identifier, parser("{}").curlBlock()).apply { declarationFile = file }
        val formal = FunctionParam(MCFPPConcreteType.Type, "Element", target, isReadOnly = true)
        target.readOnlyParams.add(formal)
        target.returnType = UnresolvedType("Box<Element>")
        val argument = PreparedExpressionFact.readonly(DeclarationLexicalSnapshot.capture(file, file.namespace.identifier), expression("int"))
        val session = SignaturePreparationSession()
        val result = assertIs<SignaturePreparationResult.NeedsCompletion>(
            session.prepare(
                SignaturePreparationRequest(listOf(target), listOf(argument), source)
            )
        )
        assertEquals(CompilerValue.TypeValue(MCFPPBaseType.Int.typeId), payload(result.staged.headers.single().readonlyFacts.single()))
        assertSame(formal, result.requests.single().lexical.formals.single().identity)
        assertTrue(result.requests.single().readonlyComplete)
        assertEquals(argument.value, result.requests.single().completedReadonly.single().value)
        assertTrue(target.compiledFunctions.isEmpty())
    }

    @Test fun readonlyDefaultValuesUseTheSameQueue() {
        val file = fixture()
        val box = prototype(file)
        val target = GenericFunction("target", file.namespace.identifier, parser("{}").curlBlock()).apply { declarationFile = file }
        target.readOnlyParams.add(
            FunctionParam(
                MCFPPListType(MCFPPConcreteType.Type),
                "types",
                target,
                hasDefault = true,
                isReadOnly = true
            ).apply {
                val input = parser("[Box<int>]")
                defaultContext = input.value()
                assertEquals(0, input.numberOfSyntaxErrors)
                assertEquals(Token.EOF, input.currentToken.type)
            }
        )
        val result = needs(SignaturePreparationSession(), target)
        assertSame(box, result.staged.headers.single().prototype)
        assertTrue(target.commands.isEmpty())
    }

    @Test fun fieldTypeBindingUsesSharedTypeSyntaxAndLeavesAnExplicitCompletionBatch() {
        val file = fixture()
        val box = prototype(file)
        val session = SignaturePreparationSession()
        val node = syntax("list<Box<int>>")
        val error = assertFailsWith<PreparationFactFailure> { binder(file, session).type(node) }
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, error.code)
        assertEquals(source.file, error.source.file)
        val header = session.stagedHeaderBatch().headers.single()
        assertSame(box, header.prototype)
        assertEquals(HeaderPhase.RESERVED, header.phase)
        assertEquals(0, box.index)
        assertNull(box.preparedHeader(header.key))
    }

    @Test fun frozenPrototypeRelationshipsSurviveCallerAndNamespaceReplacement() {
        val file = fixture()
        val original = prototype(file)
        val session = SignaturePreparationSession()
        val frozen = lexical(file, session)
        val replacement = prototype(file)
        val callerNamespace = Namespace("admission.caller")
        GlobalScope.localNamespaces[callerNamespace.identifier] = callerNamespace
        val caller = MCFPPFile(callerNamespace)
        val previous = MCFPPFile.currFile
        try {
            MCFPPFile.currFile = caller
            assertFailsWith<PreparationFactFailure> { DeclarationBinding(DeclarationPreparationContext(frozen)).type(syntax("Box<int>")) }
            assertSame(original, session.stagedHeaderBatch().headers.single().prototype)
            assertTrue(replacement.compiledTemplates.isEmpty())
            assertEquals(0, replacement.index)
        } finally {
            MCFPPFile.currFile = previous
        }
    }

    @Test fun importedPrototypeLookupRemainsFrozenAfterTheImportChanges() {
        val file = fixture()
        val importedNamespace = Namespace("admission.imported").also { GlobalScope.localNamespaces[it.identifier] = it }
        val imported = MCFPPFile(importedNamespace)
        val original = prototype(imported)
        file.unsolvedImports[importedNamespace.identifier] = "Box"
        val session = SignaturePreparationSession()
        val frozen = lexical(file, session)
        file.unsolvedImports.clear()
        prototype(file)
        assertFailsWith<PreparationFactFailure> { DeclarationBinding(DeclarationPreparationContext(frozen)).type(syntax("Box<int>")) }
        assertSame(original, session.stagedHeaderBatch().headers.single().prototype)
    }

    @Test fun prototypeReadonlyFormalTypesUseTheirOwnFrozenImportsAndAliases() {
        val file = fixture()
        val owner = Namespace("admission.prototype").also { GlobalScope.localNamespaces[it.identifier] = it }
        val declaration = MCFPPFile(owner)
        declaration.field.putResolvedAlias("Number", MCFPPBaseType.Float, true)
        val original = prototype(declaration, type = UnresolvedType("Number"), spelling = "Number")
        file.unsolvedImports[owner.identifier] = "Box"
        val session = SignaturePreparationSession()
        val frozen = lexical(file, session)
        declaration.field.putResolvedAlias("Number", MCFPPBaseType.Bool, true)
        file.field.putResolvedAlias("Number", MCFPPBaseType.String, true)
        assertFailsWith<PreparationFactFailure> { DeclarationBinding(DeclarationPreparationContext(frozen)).type(syntax("Box<7>")) }
        val header = session.stagedHeaderBatch().headers.single()
        assertSame(original, header.prototype)
        assertEquals(CompilerValue.FloatBits(7f.toRawBits()), payload(header.readonlyFacts.single()))
    }

    @Test fun formalShadowingCannotFallBackToTheVisibleTemplate() {
        val file = fixture()
        prototype(file)
        val target = GenericFunction("target", file.namespace.identifier, parser("{}").curlBlock()).apply { declarationFile = file }
        target.readOnlyParams.add(FunctionParam(MCFPPBaseType.Int, "Box", target, isReadOnly = true))
        target.normalParams.add(FunctionParam(UnresolvedType("Box<int>"), "value", target))
        val argument = PreparedExpressionFact.readonly(DeclarationLexicalSnapshot.capture(file, file.namespace.identifier), expression("7"))
        val result = assertIs<SignaturePreparationResult.Rejected>(
            SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(target), listOf(argument), source)
            )
        )
        assertEquals(PreparationFactCode.FORMAL_DEPENDENCY, result.diagnostics.single().code)
    }

    @Test fun typeBindingsAndFileAliasesTakePriorityOverFreeTemplateHeads() {
        val file = fixture()
        val box = prototype(file)
        val session = SignaturePreparationSession()
        file.field.putResolvedAlias("Box", MCFPPBaseType.Bool, true)
        val frozen = lexical(file, session)
        assertSame(MCFPPBaseType.Bool, DeclarationBinding(DeclarationPreparationContext(frozen)).type(syntax("Box")))
        assertSame(
            MCFPPBaseType.Int,
            DeclarationBinding(
                DeclarationPreparationContext(
                    frozen,
                    typeBindings = mapOf("Box" to MCFPPBaseType.Int)
                )
            ).type(syntax("Box"))
        )
        assertTrue(session.stagedHeaderBatch().headers.isEmpty())
        assertEquals(0, box.index)
    }

    @Test fun readonlyValuesAreConvertedToThePrototypeFormalBeforeTheKeyIsBuilt() {
        val file = fixture()
        val box = prototype(file, type = MCFPPBaseType.Float, spelling = "float")
        val session = SignaturePreparationSession()
        val binding = binder(file, session)
        assertFailsWith<PreparationFactFailure> { binding.type(syntax("Box<7>")) }
        val header = session.stagedHeaderBatch().headers.single()
        assertSame(box, header.prototype)
        val argument = header.readonlyFacts.single()
        assertEquals(MCFPPBaseType.Float.typeId, argument.type.typeId)
        assertEquals(CompilerValue.FloatBits(7f.toRawBits()), payload(argument))
        assertEquals(listOf(SpecializationArgument.Constant(argument.value)), header.key.arguments)
    }

    @Test fun repeatedFieldApplicationsUseOneCanonicalShellInTheSameDomain() {
        val file = fixture()
        prototype(file)
        val session = SignaturePreparationSession()
        val binding = binder(file, session)
        repeat(2) { assertFailsWith<PreparationFactFailure> { binding.type(syntax("Box<int>")) } }
        assertEquals(1, session.stagedHeaderBatch().headers.size)
    }

    @Test fun closedCompositeReadonlyArgumentsKeepTheirTypedPayload() {
        val file = fixture()
        prototype(file, type = MCFPPListType(MCFPPBaseType.Int), spelling = "list<int>")
        val session = SignaturePreparationSession()
        assertFailsWith<PreparationFactFailure> { binder(file, session).type(syntax("Box<[1,2]>")) }
        val argument = session.stagedHeaderBatch().headers.single().readonlyFacts.single()
        assertEquals(MCFPPListType(MCFPPBaseType.Int).typeId, argument.type.typeId)
        assertEquals(
            listOf(
                CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(1)),
                CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(2))
            ),
            assertIs<CompilerValue.Sequence>(payload(argument)).elements
        )
    }

    @Test fun mutatedReadonlyTypeGraphsRejectOldSnapshotsBeforeStagingAndAllowFreshSnapshots() {
        val file = fixture()
        val elementType = MCFPPListType(MCFPPBaseType.Int)
        val box = prototype(file, type = elementType, spelling = "list<int>")
        val oldSession = SignaturePreparationSession()
        val oldBinding = DeclarationBinding(DeclarationPreparationContext(lexical(file, oldSession)))
        elementType.generic[0] = MCFPPBaseType.Bool
        val node = syntax("Box<[true]>")
        val error = assertFailsWith<PreparationFactFailure> { oldBinding.type(node) }
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, error.code)
        assertEquals(preparationSpan(node.typeBody()), error.source)
        assertTrue(oldSession.stagedHeaderBatch().headers.isEmpty())
        assertTrue(box.compiledTemplates.isEmpty())
        assertEquals(0, box.index)

        val freshSession = SignaturePreparationSession()
        val pending = assertFailsWith<PreparationFactFailure> { binder(file, freshSession).type(node) }
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, pending.code)
        val header = freshSession.stagedHeaderBatch().headers.single()
        assertSame(box, header.prototype)
        assertEquals(MCFPPListType(MCFPPBaseType.Bool).typeId, header.readonlyFacts.single().type.typeId)
        assertEquals(
            listOf(CompilerValue.Typed(MCFPPBaseType.Bool.typeId, CompilerValue.Bool(true))),
            assertIs<CompilerValue.Sequence>(payload(header.readonlyFacts.single())).elements
        )
        assertEquals(listOf(SpecializationArgument.Constant(header.readonlyFacts.single().value)), header.key.arguments)
        assertTrue(box.compiledTemplates.isEmpty())
        assertEquals(0, box.index)
    }

    @Test fun unknownTypesAndInvalidReadonlyApplicationsKeepTheirOriginalSpans() {
        val file = fixture()
        prototype(file)
        for (text in listOf("Missing<int>", "Box<missing>", "Box<true>")) {
            val session = SignaturePreparationSession()
            val error = assertFailsWith<PreparationFactFailure> { binder(file, session).type(syntax(text)) }
            assertEquals(source.file, error.source.file)
            assertTrue(error.source.endOffset > error.source.startOffset)
            assertTrue(session.stagedHeaderBatch().headers.isEmpty())
        }
    }

    @Test fun pendingOrdinaryTemplatesCannotPretendToHaveACompleteEmptyLayout() {
        val file = fixture()
        val ordinary = DataTemplate("Plain", file.namespace.identifier)
        file.namespace.scope.template["Plain"] = ordinary
        val result = needs(SignaturePreparationSession(), function(file, output = "Plain"))
        assertTrue(result.staged.headers.isEmpty())
        assertFalse(ordinary.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
    }

    @Test fun unknownPrototypeShapesAreRejectedBeforeTheirGettersOrInitializers() {
        val file = fixture()
        var calls = 0
        val unknown = object : GenericDataTemplate(parser("{}").templateBody(), "Unknown", file.namespace.identifier) {
            override fun initialize() {
                calls++
                error("initialize must not run")
            }
            override fun getType(): MCFPPDataTemplateType {
                calls++
                error("getType must not run")
            }
        }
        file.namespace.scope.genericTemplate["Unknown" to emptyList()] = unknown
        val session = SignaturePreparationSession()
        val failure = assertFailsWith<PreparationFactFailure> { binder(file, session).type(syntax("Unknown<int>")) }
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, failure.code)
        assertEquals(source.file, failure.source.file)
        assertEquals(0, calls)
        assertTrue(session.stagedHeaderBatch().headers.isEmpty())
    }

    @Test fun stagingDoesNotInitializeVisitBodiesApplyAnnotationsOrConsumeCompilerNames() {
        val file = fixture()
        val box = prototype(
            file,
            body = "{var value:int=missing(); func hidden():int{return missing();}}"
        )
        val annotation = NoExecutionProbe()
        box.annotations.add(annotation)
        val current = Function("sentinel", file.namespace.identifier, null).apply { commands.add(Command("say before")) }
        val target = function(file, input = "Box<int>")
        val old = Function.currFunction
        try {
            Function.currFunction = current
            val counts = listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount)
            val commands = current.commands.toList()
            val errors = Project.errorCount
            val session = SignaturePreparationSession()
            needs(session, target)
            assertSame(current, Function.currFunction)
            assertEquals(commands, current.commands.toList())
            assertEquals(counts, listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount))
            assertEquals(errors, Project.errorCount)
            assertEquals(0, box.index)
            assertTrue(box.compiledTemplates.isEmpty())
            assertTrue(box.scope.allVars.isEmpty())
            assertTrue(session.stagedHeaderBatch().headers.single().model.constructors.isEmpty())
            assertEquals(0, annotation.calls)
        } finally {
            Function.currFunction = old
        }
    }

    class NoExecutionProbe : Annotation("NoExecutionProbe", "admission.original") {
        var calls = 0
        override fun forFunction(function: Function) {
            calls++
        }
        override fun forField(field: Var<*>) {
            calls++
        }
        override fun forDataTemplate(data: DataTemplate) {
            calls++
        }
    }

    @Test fun actuallyCompletedLegacyApplicationsAndTheirWrappersAreReadyWithoutRecompiling() = withCompiler("data Box<T>{}\nfunc main(){}") { file ->
        val box = assertIs<GenericDataTemplate>(assertNotNull(file.namespace.scope.getTemplate("Box")))
        assertSame(MCFPPConcreteType.Type, box.readOnlyParams.single().type)
        assertTrue(box.compiledTemplates.isEmpty())
        assertEquals(0, box.index)
        val instance = assertNotNull(box.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int, "T"))))
        assertEquals(0, Project.errorCount)
        assertTrue(instance.hasCompletedDeclaration)
        val session = SignaturePreparationSession()
        val target = function(file, input = "Box<int>", output = "list<Box<int>>")
        val defaultSyntax = parser("{}").value()
        target.normalParams.single().apply {
            hasDefault = true
            defaultContext = defaultSyntax
        }
        val ready = assertIs<SignaturePreparationResult.Ready>(session.prepare(SignaturePreparationRequest(listOf(target), emptyList(), source)))
        assertSame(
            instance,
            assertIs<MCFPPDataTemplateType>(ready.declarations.signatures.single().normal.single().type)
                .checkpointPrivateState().elements.single()
        )
        val header = session.stagedHeaderBatch().headers.single()
        assertSame(defaultSyntax, ready.declarations.signatures.single().normal.single().defaultSyntax)
        assertEquals(HeaderPhase.COMPLETE, header.phase)
        assertEquals(1, box.index)
        val wrapperSession = SignaturePreparationSession()
        val wrapperTarget = function(file).apply { normalParams.add(FunctionParam(instance.getType(), "value", this)) }
        assertIs<SignaturePreparationResult.Ready>(wrapperSession.prepare(SignaturePreparationRequest(listOf(wrapperTarget), emptyList(), source)))
        assertSame(instance, wrapperSession.stagedHeaderBatch().headers.single().model)
        assertEquals(1, box.index)
        assertTrue(target.commands.isEmpty())
        assertEquals(0, Project.errorCount)
        val consumer = Namespace("admission.alias").also { GlobalScope.localNamespaces[it.identifier] = it }
        val aliasFile = MCFPPFile(consumer)
        aliasFile.field.putResolvedAlias("Alias", instance.getType(), true)
        val aliasSession = SignaturePreparationSession()
        assertIs<SignaturePreparationResult.Ready>(
            aliasSession.prepare(
                SignaturePreparationRequest(
                    listOf(function(aliasFile, input = "Alias")),
                    emptyList(),
                    source
                )
            )
        )
        assertSame(instance, aliasSession.stagedHeaderBatch().headers.single().model)
        val hiddenName = assertIs<SignaturePreparationResult.Rejected>(
            SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(function(aliasFile, input = "Box<int>")), emptyList(), source)
            )
        )
        assertEquals(PreparationFactCode.UNKNOWN_NAME, hiddenName.diagnostics.single().code)
    }

    private fun withCompiler(program: String, action: (MCFPPFile) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val current = Function.currFunction
        val currentFile = MCFPPFile.currFile
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        val settings = listOf(
            CompileSettings.ignoreStdLib,
            CompileSettings.isDebug,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        val maxWhileInline = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig(version = "26.3")
            CompileSettings.ignoreStdLib = false
            MCFPPStringTest.readFromString(program, version = "26.3")
            assertEquals(0, Project.errorCount)
            assertTrue(Project.journalState().ready)
            val file = assertNotNull(MCFPPFile.currFile)
            action(file)
        } finally {
            base.restore()
            namespaces.restore()
            project.restore()
            Function.currFunction = current
            MCFPPFile.currFile = currentFile
            Function.forcedField = forced
            DataTemplate.currTemplate = template
            CompileSettings.ignoreStdLib = settings[0]
            CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = maxWhileInline
        }
    }
}
