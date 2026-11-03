package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.annotation.DataOnly
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.*
import top.mcfpp.util.TempPool
import kotlin.test.*
import kotlin.test.Test

class MniGenericHeaderStateTest {
    private val source = SourceSpan("header-state.mcfpp", 3, 12, 1, 4, 1, 13)
    private val options = GenerationOptions("26.3", 0)
    private fun parser(text: String) = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(text))))
    private fun prototype(name: String = "Box") = GenericDataTemplate(parser("{}").templateBody(), name, "headers.test").apply {
        declarationFile = null
        readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
    }
    private fun fact(text: String): PreparedExpressionFact = PreparedExpressionFact.readonly(
        DeclarationLexicalSnapshot.capture(null, "headers.test"),
        parser(text).expression()
    )
    private fun initialize() {
        Project.init()
    }

    @Test fun sameTypedKeyReusesTheShellBeforeAnyPublicationOrReader() {
        initialize()
        val declaration = prototype()
        val session = SignaturePreparationSession()
        val first = session.stageHeader(declaration, listOf(fact("int")), source, options)
        val second = session.stageHeader(declaration, listOf(fact("int")), source, options)
        assertSame(first, second)
        assertSame(first.model, second.model)
        assertEquals(HeaderPhase.RESERVED, first.phase)
        assertFalse(first.isPublished)
        assertEquals(0, declaration.index)
        assertTrue(declaration.compiledTemplates.isEmpty())
        assertNull(declaration.preparedHeader(first.key))
        assertSame(first.model, first.type.checkpointPrivateState().elements.single())
        assertEquals(first.identity, first.type.typeId)
        assertTrue(first.model.scope.allVars.isEmpty())
        assertTrue(first.model.constructors.isEmpty())
        session.discard()
    }

    @Test fun differentReadonlyBindingsAndGenerationOptionsDoNotShareShells() {
        initialize()
        val declaration = prototype()
        val session = SignaturePreparationSession()
        val int = session.stageHeader(declaration, listOf(fact("int")), source, options)
        val bool = session.stageHeader(declaration, listOf(fact("bool")), source, options)
        val target = session.stageHeader(declaration, listOf(fact("int")), source, options.copy(targetVersion = "26.4"))
        assertNotSame(int.model, bool.model)
        assertNotEquals(int.identity, bool.identity)
        assertNotSame(int.model, target.model)
        assertNotEquals(int.key, target.key)
        assertEquals(int.identity, target.identity, "Generation options separate artifacts without changing language type identity")
        assertEquals(0, declaration.index)
        assertTrue(declaration.compiledTemplates.isEmpty())
        session.discard()
    }

    @Test fun objectAndAbstractDataShellsUseTheActualCompilerShapesAndFrozenTypedIdentity() {
        initialize()
        val obj = GenericObjectDataTemplate(parser("{}").templateBody(), "Singleton", "headers.test").apply {
            declarationFile = null
            readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
        }
        val contract = prototype("Contract").apply { isAbstract = true }
        val session = SignaturePreparationSession()
        val objectHeader = session.stageHeader(obj, listOf(fact("int")), source, options)
        val abstractHeader = session.stageHeader(contract, listOf(fact("int")), source, options)
        assertIs<CompiledGenericObjectDataTemplate>(objectHeader.model)
        assertIs<MCFPPGenericObjectDataTemplateType>(objectHeader.type)
        assertSame(objectHeader.model, objectHeader.model.companionObject)
        assertEquals("object", (objectHeader.identity.constructor as TypeId.Declaration).kind)
        assertTrue(abstractHeader.model.isAbstract)
        assertEquals("template", (abstractHeader.identity.constructor as TypeId.Declaration).kind)
        session.discard()
    }

    @Test fun unimplementedReaderCannotTurnAReservationIntoReady() {
        initialize()
        val session = SignaturePreparationSession()
        val header = session.stageHeader(prototype(), listOf(fact("int")), source, options)
        val result = assertIs<SignaturePreparationResult.NeedsCompletion>(
            session.prepare(
                SignaturePreparationRequest(emptyList(), emptyList(), source)
            )
        )
        assertSame(header, result.staged.headers.single())
        assertTrue(result.requests.isEmpty(), "The header itself is the completion request; no fake Function is created")
        assertEquals(HeaderPhase.RESERVED, header.phase)
        assertFailsWith<UnsupportedOperationException> { (result.staged.headers as MutableList<*>).clear() }
        session.discard()
        assertTrue(result.staged.discarded)
    }

    @Test fun controlledBatchPublicationReservesOneNameAndNeverPublishesAnIncompleteCompiledEntry() {
        initialize()
        val declaration = prototype()
        val session = SignaturePreparationSession()
        val header = session.stageHeader(declaration, listOf(fact("int")), source, options)
        val batch = session.stagedHeaderBatch()
        batch.publishHeaders()
        val name = header.model.identifier
        repeat(2) { batch.publishHeaders() }
        assertEquals(1, declaration.index)
        assertEquals("Box_type-0", name)
        assertEquals(name, header.model.identifier)
        assertTrue(header.isPublished)
        assertSame(header, declaration.preparedHeader(header.key))
        assertTrue(declaration.compiledTemplates.isEmpty())
        val next = SignaturePreparationSession()
        val second = next.stageHeader(declaration, listOf(fact("int")), source, options)
        assertSame(header, second)
        session.discard()
        next.discard()
    }

    @Test fun legacyCacheIsIncompleteUntilItsActualBarrierAndReusesTheProvenInstanceWithoutReplayingEffects() = isolatedCompiler { namespace, file ->
        val declaration = actualPrototype(namespace, file, CompletionProbe())
        val argument = fact("int")
        val key = SpecializationKey(
            declaration.declarationId,
            listOf(SpecializationArgument.Constant(argument.value)),
            currentOptions()
        )
        val session = SignaturePreparationSession()
        lateinit var header: PreparedTemplateReservation
        lateinit var instance: CompiledGenericDataTemplate
        Project.collectingTemplateSignatures {
            instance = assertNotNull(declaration.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int, "T"))))
            assertSame(instance, declaration.materializedTemplate(key))
            assertTrue(declaration.compiledTemplates.isEmpty())
            assertTrue(Project.journalState().pending.any { it === instance })
            header = session.stageHeader(declaration, listOf(argument), source, currentOptions())
            assertSame(instance, header.model)
            assertTrue(header.isPublished)
            assertEquals(HeaderPhase.COMPLETING, header.phase)
            assertFalse(instance.hasCompletedDeclaration)
            assertEquals(0, assertIs<CompletionProbe>(instance.annotations.single()).applications)
            assertIs<SignaturePreparationResult.NeedsCompletion>(
                session.prepare(
                    SignaturePreparationRequest(emptyList(), emptyList(), source)
                )
            )
        }
        val annotation = assertIs<CompletionProbe>(instance.annotations.single())
        assertTrue(instance.hasCompletedDeclaration)
        assertEquals(HeaderPhase.COMPLETE, header.phase)
        assertSame(instance, declaration.compiledTemplates[key])
        assertEquals(1, annotation.applications)
        val name = instance.identifier
        val fresh = SignaturePreparationSession()
        val proven = fresh.stageHeader(declaration, listOf(argument), source, currentOptions())
        assertSame(instance, proven.model)
        assertEquals(HeaderPhase.COMPLETE, proven.phase)
        assertIs<SignaturePreparationResult.Ready>(fresh.prepare(SignaturePreparationRequest(emptyList(), emptyList(), source)))
        val publish = SignaturePreparationSession()
        val published = publish.stageHeader(declaration, listOf(argument), source, currentOptions())
        publish.stagedHeaderBatch().publishHeaders()
        assertEquals(1, declaration.index)
        assertEquals(name, instance.identifier)
        assertSame(instance, declaration.compiledTemplates[key])
        assertSame(published, declaration.preparedHeader(key))
        Project.collectingTemplateSignatures {
            Project.registerTemplateContract(instance)
            assertFalse(instance.hasCompletedDeclaration)
            assertSame(instance, declaration.materializedTemplate(key))
            assertTrue(declaration.compiledTemplates.isEmpty())
            assertEquals(HeaderPhase.COMPLETING, header.phase)
            assertEquals(HeaderPhase.COMPLETING, proven.phase)
            assertEquals(HeaderPhase.COMPLETING, published.phase)
            val retry = SignaturePreparationSession()
            assertSame(published, retry.stageHeader(declaration, listOf(argument), source, currentOptions()))
            assertIs<SignaturePreparationResult.NeedsCompletion>(
                retry.prepare(
                    SignaturePreparationRequest(emptyList(), emptyList(), source)
                )
            )
            retry.discard()
        }
        assertEquals(HeaderPhase.COMPLETE, published.phase)
        assertSame(instance, declaration.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int, "T"))))
        assertEquals(1, annotation.applications)
        assertTrue(instance.scope.functions.getValue("unused").single().commands.isEmpty())
        assertEquals(0, Project.errorCount)
        session.discard()
        fresh.discard()
        publish.discard()
    }

    @Test fun preparedHeadersComposeWithDeepStateAcrossCompletePendingAndMultipleLostShellGenerations() = isolatedCompiler { namespace, file ->
        val declaration = actualPrototype(namespace, file, DataOnly())
        val before = DeclarationStateJournal.checkpoint(listOf(declaration))
        val argument = fact("int")
        val session = SignaturePreparationSession()
        lateinit var header: PreparedTemplateReservation
        lateinit var pending: DeclarationStateJournal
        lateinit var field: MCInt
        lateinit var property: top.mcfpp.model.property.Property
        Project.collectingTemplateSignatures {
            val instance = assertNotNull(declaration.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int, "T"))))
            header = session.stageHeader(declaration, listOf(argument), source, currentOptions())
            session.stagedHeaderBatch().publishHeaders()
            assertSame(instance, header.model)
            field = assertIs<MCInt>(instance.scope.getVar("value"))
            property = assertNotNull(instance.scope.getProperty("value"))
            pending = before.captureCurrent()
            assertSame(instance, declaration.materializedTemplate(header.key))
            assertTrue(declaration.compiledTemplates.isEmpty())
            assertFalse(field.isDataOnly)
            assertEquals(HeaderPhase.COMPLETING, header.phase)
        }
        val complete = pending.captureCurrent()
        val originalParent = property.parent
        val originalOwner = property.declaredParentTemplate
        assertTrue(field.isDataOnly)
        assertEquals(HeaderPhase.COMPLETE, header.phase)
        pending.restore()
        assertFalse(field.isDataOnly)
        assertEquals(HeaderPhase.COMPLETING, header.phase)
        before.restore()
        val lost = complete.captureCurrent()
        assertNull(declaration.preparedHeader(header.key))
        assertTrue(declaration.compiledTemplates.isEmpty())
        assertEquals(0, declaration.index)
        val firstTarget = DataTemplate("FirstLost", namespace.identifier).apply { declarationFile = null }
        val firstParent = MCInt(7, "firstParent")
        field.type = MCFPPBaseType.Float
        property.parent = firstParent
        property.declaredParentTemplate = firstTarget
        header.type.template = firstTarget
        val changed = lost.captureCurrent()
        val secondTarget = DataTemplate("SecondLost", namespace.identifier).apply { declarationFile = null }
        val secondParent = MCInt(11, "secondParent")
        field.type = MCFPPBaseType.Bool
        property.parent = secondParent
        property.declaredParentTemplate = secondTarget
        header.type.template = secondTarget
        val later = changed.captureCurrent()
        repeat(2) {
            pending.restore()
            assertSame(header, declaration.preparedHeader(header.key))
            assertEquals(1, declaration.index)
            assertSame(header.model, declaration.materializedTemplate(header.key))
            assertTrue(declaration.compiledTemplates.isEmpty())
            assertEquals(HeaderPhase.COMPLETING, header.phase)
            assertFalse(field.isDataOnly)
            assertSame(field, header.model.scope.getVar("value"))
            assertSame(property, header.model.scope.getProperty("value"))
            assertSame(MCFPPBaseType.Int, field.type)
            assertSame(header.model, header.type.template)
            assertSame(originalParent, property.parent)
            assertSame(originalOwner, property.declaredParentTemplate)
            complete.restore()
            assertSame(header, declaration.preparedHeader(header.key))
            assertSame(header.model, declaration.compiledTemplates[header.key])
            assertEquals(HeaderPhase.COMPLETE, header.phase)
            assertTrue(field.isDataOnly)
            assertEquals(1, declaration.index)
            lost.restore()
            assertNull(declaration.preparedHeader(header.key))
            assertTrue(declaration.compiledTemplates.isEmpty())
            assertEquals(0, declaration.index)
            assertEquals(HeaderPhase.COMPLETING, header.phase)
            assertSame(MCFPPBaseType.Int, field.type)
            assertSame(header.model, header.type.template)
            changed.restore()
            assertNull(declaration.preparedHeader(header.key))
            assertEquals(0, declaration.index)
            assertSame(MCFPPBaseType.Float, field.type)
            assertSame(firstTarget, header.type.template)
            assertSame(firstParent, property.parent)
            assertSame(firstTarget, property.declaredParentTemplate)
            later.restore()
            assertNull(declaration.preparedHeader(header.key))
            assertTrue(declaration.compiledTemplates.isEmpty())
            assertEquals(0, declaration.index)
            assertEquals(HeaderPhase.COMPLETING, header.phase)
            assertSame(MCFPPBaseType.Bool, field.type)
            assertSame(secondTarget, header.type.template)
            assertSame(secondParent, property.parent)
            assertSame(secondTarget, property.declaredParentTemplate)
        }
        assertTrue(later.elements.any { it === header.model })
        assertTrue(later.elements.any { it === header.type })
        assertTrue(later.elements.any { it === firstTarget })
        assertTrue(later.elements.any { it === secondTarget })
        assertEquals(0, Project.errorCount)
        session.discard()
    }

    @Test fun staleBatchCannotReplaceAnotherCanonicalShellOrConsumeAnotherName() {
        initialize()
        val declaration = prototype()
        val first = SignaturePreparationSession()
        val second = SignaturePreparationSession()
        val a = first.stageHeader(declaration, listOf(fact("int")), source, options)
        val b = second.stageHeader(declaration, listOf(fact("int")), source, options)
        assertNotSame(a.model, b.model)
        first.stagedHeaderBatch().publishHeaders()
        assertFailsWith<IllegalStateException> { second.stagedHeaderBatch().publishHeaders() }
        assertSame(a, declaration.preparedHeader(a.key))
        assertEquals(1, declaration.index)
        assertFalse(b.isPublished)
        assertTrue(declaration.compiledTemplates.isEmpty())
        first.discard()
        second.discard()
    }

    @Test fun wholeBatchPreflightRejectsChangedDeclarationBeforePublishingAnyShell() {
        initialize()
        val first = prototype("First")
        val second = prototype("Second")
        val session = SignaturePreparationSession()
        val a = session.stageHeader(first, listOf(fact("int")), source, options)
        session.stageHeader(second, listOf(fact("int")), source, options)
        val batch = session.stagedHeaderBatch()
        second.identifier = "Changed"
        val failure = assertFailsWith<PreparationFactFailure> { batch.publishHeaders() }
        assertEquals(source, failure.source)
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, failure.code)
        assertEquals(0, first.index)
        assertEquals(0, second.index)
        assertFalse(a.isPublished)
        assertNull(first.preparedHeader(a.key))
        session.discard()
    }

    @Test fun privateCheckpointRetainsRemovedShellsAndTheirPhaseAcrossGenerations() {
        initialize()
        val declaration = prototype()
        val session = SignaturePreparationSession()
        val before = declaration.checkpointDeclarationState()
        val header = session.stageHeader(declaration, listOf(fact("int")), source, options)
        session.stagedHeaderBatch().publishHeaders()
        val installed = declaration.checkpointDeclarationState()
        header.beginMaterialization()
        val completing = installed.captureCurrent()
        before.restore()
        assertNull(declaration.preparedHeader(header.key))
        assertEquals(0, declaration.index)
        val detached = completing.captureCurrent()
        repeat(2) {
            installed.restore()
            assertSame(header, declaration.preparedHeader(header.key))
            assertEquals(HeaderPhase.RESERVED, header.phase)
            completing.restore()
            assertEquals(HeaderPhase.COMPLETING, header.phase)
            assertEquals(1, declaration.index)
            detached.restore()
            assertNull(declaration.preparedHeader(header.key))
            assertEquals(0, declaration.index)
        }
        assertTrue(detached.elements.any { it === header.model })
        assertTrue(detached.elements.any { it === header.type })
        assertTrue(detached.elements.any { it === header.readonlyFacts.single().value })
        assertTrue(declaration.compiledTemplates.isEmpty())
        session.discard()
    }

    @Test fun discardedSessionRevokesItsBatchAndLeavesGlobalStateAndNamesUntouched() {
        initialize()
        val declaration = prototype()
        val session = SignaturePreparationSession()
        val header = session.stageHeader(declaration, listOf(fact("int")), source, options)
        val batch = session.stagedHeaderBatch()
        session.discard()
        assertFailsWith<IllegalStateException> { batch.publishHeaders() }
        assertFailsWith<IllegalStateException> { session.stageHeader(declaration, listOf(fact("int")), source, options) }
        assertEquals(0, declaration.index)
        assertFalse(header.isPublished)
        assertNull(declaration.preparedHeader(header.key))
        assertTrue(declaration.compiledTemplates.isEmpty())
    }

    @Test fun specializationBudgetRejectsGrowthWithTheOriginalSourceAndNoPartialPublication() {
        initialize()
        val declaration = prototype()
        val session = SignaturePreparationSession(PreparationLimits(maxTemplateHeaders = 1))
        val first = session.stageHeader(declaration, listOf(fact("int")), source, options)
        val failure = assertFailsWith<PreparationFactFailure> {
            session.stageHeader(declaration, listOf(fact("bool")), source, options)
        }
        assertEquals(source, failure.source)
        assertContains(failure.message.orEmpty(), "budget")
        assertTrue(session.stagedHeaderBatch().headers.isEmpty())
        assertNull(declaration.preparedHeader(first.key))
        assertEquals(0, declaration.index)
        session.discard()
    }

    private class UnknownPrototype : GenericDataTemplate(mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("{}")))).templateBody(), "Unknown", "headers.test") {
        var calls = 0
        override fun initialize() {
            calls++
            error("Unknown initializer")
        }
        override fun getType(): MCFPPDataTemplateType {
            calls++
            error("Unknown type getter")
        }
    }

    @Test fun unknownTemplateImplementationIsRejectedBeforeAnyVirtualInitializerOrGetter() {
        initialize()
        val unknown = UnknownPrototype()
        val session = SignaturePreparationSession()
        val failure = assertFailsWith<PreparationFactFailure> { session.stageHeader(unknown, emptyList(), source, options) }
        assertEquals(source, failure.source)
        assertEquals(0, unknown.calls)
        assertEquals(0, unknown.index)
        assertTrue(unknown.compiledTemplates.isEmpty())
        session.discard()
    }

    @Test fun actualPureStagingDoesNotExecuteBodiesOrChangeCurrentFunctionStorageOrCompilerRegistries() {
        initialize()
        val previous = Function.currFunction
        val function = Function("sentinel", "headers.test", null)
        Function.currFunction = function
        try {
            function.commands.add(Command("say before"))
            val sentinel = MCInt(29, "sentinel")
            function.scope.vars["sentinel"] = sentinel
            val declaration = prototype()
            val argument = fact("int")
            val commands = function.commands.toList()
            val currentScope = function.scope
            val flow = StorageAccess.flowSnapshot(listOf(sentinel))
            val namespaces = GlobalScope.localNamespaces.toMap()
            val objectives = GlobalScope.scoreboards.toMap()
            val resources = Project.macroFunction.toMap()
            val constants = Project.constants.toMap()
            val errors = Project.errorCount
            val warnings = Project.warningCount
            val effect = function.runtimeEffect
            EffectAnalysis.withActualEffects(function) {
                val counters = listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount)
                val session = SignaturePreparationSession()
                session.stageHeader(declaration, listOf(argument), source, options)
                assertIs<SignaturePreparationResult.NeedsCompletion>(session.prepare(SignaturePreparationRequest(emptyList(), emptyList(), source)))
                session.discard()
                assertEquals(counters, listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount))
            }
            assertSame(function, Function.currFunction)
            assertSame(currentScope, function.scope)
            assertEquals(commands, function.commands.toList())
            assertSame(sentinel, function.scope.vars["sentinel"])
            assertEquals(flow, StorageAccess.flowSnapshot(listOf(sentinel)))
            assertSame(effect, function.runtimeEffect)
            assertEquals(namespaces, GlobalScope.localNamespaces)
            assertEquals(objectives, GlobalScope.scoreboards)
            assertEquals(resources, Project.macroFunction)
            assertEquals(constants, Project.constants)
            assertEquals(errors, Project.errorCount)
            assertEquals(warnings, Project.warningCount)
            assertTrue(function.child.isEmpty())
            assertTrue(function.compiledFunctions.isEmpty())
            assertEquals(0, declaration.index)
            assertTrue(declaration.compiledTemplates.isEmpty())
        } finally {
            Function.currFunction = previous
        }
    }

    private fun currentOptions() = GenerationOptions(
        Project.config.version,
        CompileSettings.maxWhileInline,
        CompileSettings.foldIRConstants
    )

    private fun actualPrototype(namespace: Namespace, file: MCFPPFile, annotation: Annotation) = GenericDataTemplate(
        parser("{var value:int=3; func unused():int{return 7;}}").templateBody(),
        "ActualBox",
        namespace.identifier
    ).apply {
        declarationFile = file
        readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
        annotations.add(annotation)
        namespace.scope.genericTemplate[identifier to listOf(MCFPPConcreteType.Type)] = this
    }

    private fun isolatedCompiler(action: (Namespace, MCFPPFile) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val current = Function.currFunction
        val forced = Function.forcedField
        val currentFile = MCFPPFile.currFile
        val currentTemplate = DataTemplate.currTemplate
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
            MCFPPStringTest.readFromString("func main(){}", version = "26.3")
            assertEquals(0, Project.errorCount)
            val namespace = GlobalScope.getOrCreateNamespace("headers.test")
            val file = MCFPPFile(namespace)
            MCFPPFile.currFile = file
            Project.currNamespace = namespace.identifier
            action(namespace, file)
        } finally {
            base.restore()
            namespaces.restore()
            project.restore()
            Function.currFunction = current
            Function.forcedField = forced
            MCFPPFile.currFile = currentFile
            DataTemplate.currTemplate = currentTemplate
            CompileSettings.ignoreStdLib = settings[0]
            CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = maxWhileInline
        }
    }

    class CompletionProbe : Annotation("HeaderCompletionProbe", "headers.test") {
        var applications = 0
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
        override fun forDataTemplate(data: DataTemplate) {
            applications++
        }
    }
}
