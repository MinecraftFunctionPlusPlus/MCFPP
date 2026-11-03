package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.DataOnly
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/** Exercises the real legacy materialization and Project barrier, after actual library loading. */
class MniHeaderMaterializationTest {
    companion object {
        private var hostCalls = 0
        @JvmStatic fun host(context: NativeCallContext) {
            hostCalls++
        }
    }
    private val source = SourceSpan("materialization.mcfpp", 0, 3, 1, 1, 1, 4)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, source.file))))
    private fun isolated(action: (Namespace, MCFPPFile) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val current = Function.currFunction
        val file = MCFPPFile.currFile
        val template = DataTemplate.currTemplate
        val forced = Function.forcedField
        val settings = listOf(
            CompileSettings.ignoreStdLib,
            CompileSettings.isDebug,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        val maxWhile = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig(version = "26.3")
            CompileSettings.ignoreStdLib = false
            MCFPPStringTest.readFromString("func main(){}", version = "26.3")
            assertEquals(0, Project.errorCount)
            val namespace = GlobalScope.getOrCreateNamespace("header.materialization")
            val original = MCFPPFile(namespace)
            namespace.scope.journalFileFields.add(original.field)
            MCFPPFile.currFile = original
            Project.currNamespace = namespace.identifier
            action(namespace, original)
        } finally {
            base.restore()
            namespaces.restore()
            project.restore()
            Function.currFunction = current
            Function.forcedField = forced
            MCFPPFile.currFile = file
            DataTemplate.currTemplate = template
            CompileSettings.ignoreStdLib = settings[0]
            CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = maxWhile
        }
    }
    private fun prototype(
        namespace: Namespace,
        file: MCFPPFile,
        name: String = "Box",
        body: String = "{var value:T; func unused():int{return 7;}}",
        objectType: Boolean = false
    ): GenericDataTemplate {
        val parse = parser(body)
        val syntax = parse.templateBody()
        assertEquals(0, parse.numberOfSyntaxErrors, body)
        val result = if (objectType) GenericObjectDataTemplate(syntax, name, namespace.identifier)
        else GenericDataTemplate(syntax, name, namespace.identifier)
        result.declarationFile = file
        result.readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
        if (objectType) namespace.scope.objects.add(result) else namespace.scope.template[name] = result
        return result
    }
    private fun argument(type: MCFPPType = MCFPPBaseType.Int) = MCFPPTypeVar(type, "T")
    private fun key(prototype: GenericDataTemplate, type: MCFPPType = MCFPPBaseType.Int) = SpecializationKeys.forArguments(prototype.declarationId, listOf(argument(type)))
    private fun fact(namespace: Namespace, file: MCFPPFile, spelling: String = "int"): PreparedExpressionFact = PreparedExpressionFact.readonly(DeclarationLexicalSnapshot.capture(file, namespace.identifier), parser(spelling).expression())
    private fun stage(
        prototype: GenericDataTemplate,
        namespace: Namespace,
        file: MCFPPFile,
        options: GenerationOptions? = null
    ): PreparedTemplateReservation {
        val session = SignaturePreparationSession()
        val header = if (options == null) session.stageHeader(prototype, listOf(fact(namespace, file)), source)
        else session.stageHeader(prototype, listOf(fact(namespace, file)), source, options)
        session.stagedHeaderBatch().publishHeaders()
        return header
    }

    @Test fun publishedPreparedShellIsActuallyMaterializedAndPublishedOnlyAfterTheRealBarrier() = isolated { namespace, file ->
        val prototype = prototype(namespace, file)
        val header = stage(prototype, namespace, file)
        val name = header.model.identifier
        assertTrue(header.model.scope.allVars.isEmpty())
        Project.collectingTemplateSignatures {
            val instance = assertNotNull(prototype.compile(listOf(argument())))
            assertSame(header.model, instance)
            assertSame(instance, prototype.materializedTemplate(header.key))
            assertNull(prototype.compiledTemplates[header.key])
            assertEquals(HeaderPhase.COMPLETING, header.phase)
            assertNotNull(instance.scope.getVar("value"))
            assertNotNull(instance.scope.functions["unused"])
            assertTrue(Project.journalState().pending.any { it === instance })
        }
        assertTrue(header.model.hasCompletedDeclaration)
        assertEquals(HeaderPhase.COMPLETE, header.phase)
        assertSame(header.model, prototype.compiledTemplates[header.key])
        assertEquals(name, header.model.identifier)
        assertEquals(1, prototype.index)
        assertEquals(0, Project.errorCount)
    }

    @Test fun legacyCallHasACanonicalPendingModelAndRunsItsAnnotationExactlyOnceAtCompletion() = isolated { namespace, file ->
        val prototype = prototype(namespace, file)
        prototype.annotations.add(Probe())
        lateinit var instance: CompiledGenericDataTemplate
        Project.collectingTemplateSignatures {
            instance = assertNotNull(prototype.compile(listOf(argument())))
            assertSame(instance, prototype.materializedTemplate(key(prototype)))
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertEquals(0, assertIs<Probe>(instance.annotations.single()).applications)
            assertSame(instance, prototype.compile(listOf(argument())))
            assertEquals(1, prototype.index)
        }
        val annotation = assertIs<Probe>(instance.annotations.single())
        assertEquals(1, annotation.applications)
        assertSame(instance, prototype.compiledTemplates[key(prototype)])
        repeat(2) { assertSame(instance, prototype.compile(listOf(argument()))) }
        assertEquals(1, annotation.applications)
        assertEquals(1, prototype.index)
        assertEquals(0, Project.errorCount)
    }

    @Test fun originalLocalFieldsPropertiesFunctionsAndConstructorsHaveExactSourceAssociations() = isolated { namespace, file ->
        val prototype = prototype(
            namespace,
            file,
            body = """
            {var value:T; var inferred=3;
             func same(value:int):int{return value;}
             func same(value:float):float{return value;}
             constructor(value:T){} }
            """.trimIndent()
        )
        val session = SignaturePreparationSession()
        val header = session.stageHeader(prototype, listOf(fact(namespace, file)), source)
        val local = assertIs<HeaderReadResult.LocalHeaders>(
            session.readHeaders(
                session.stagedHeaderBatch(),
                DeclarationLexicalSnapshot.capture(file, namespace.identifier, headers = session)
            )
        )
        val recorded = local.headers.single { it.owner === header }
        local.staged.publishHeaders()
        val instance = assertNotNull(prototype.compile(listOf(argument())))
        assertSame(header.model, instance)
        val origins = assertNotNull(prototype.materializedOrigins(header.key))
        val declarations = prototype.ctx.templateMemberDeclaration().map { it.templateMember() }
        for (field in declarations.mapNotNull { it.templateFieldDeclaration() }) {
            assertSame(instance.scope.getVar(field.Identifier().text), origins.fields[field])
            assertSame(instance.scope.getProperty(field.Identifier().text), origins.properties[field])
            assertSame(instance, origins.fields.getValue(field).declaredParentTemplate)
        }
        for (head in recorded.members) {
            assertSame(instance, origins.functions.getValue(head.declaration).owner)
        }
        for (declaration in prototype.ctx.templateMemberDeclaration().filter { it.templateMember().templateFunctionDeclaration() != null }) {
            val function = declaration.templateMember().templateFunctionDeclaration()
            assertSame(function.curlBlock(), origins.functions.getValue(declaration).ast)
        }
        val constructor = prototype.ctx.templateMemberDeclaration().single { it.templateMember().templateConstructorDeclaration() != null }
        assertSame(instance.constructors.single(), origins.functions[constructor])
        assertEquals(2, origins.fields.size)
        assertEquals(3, origins.functions.size)
        assertEquals(0, Project.errorCount)
    }

    @Test fun distinctReadonlyBindingsAndGenerationOptionsNeverBorrowAnotherShellOrName() = isolated { namespace, file ->
        val prototype = prototype(namespace, file)
        val other = GenerationOptions(Project.config.version, CompileSettings.maxWhileInline + 1, CompileSettings.foldIRConstants)
        val reserved = stage(prototype, namespace, file, other)
        val integer = assertNotNull(prototype.compile(listOf(argument())))
        val boolean = assertNotNull(prototype.compile(listOf(argument(MCFPPBaseType.Bool))))
        assertNotSame(reserved.model, integer)
        assertNotSame(integer, boolean)
        assertSame(MCFPPBaseType.Int, integer.scope.getVar("value")!!.type)
        assertSame(MCFPPBaseType.Bool, boolean.scope.getVar("value")!!.type)
        assertNull(prototype.compiledTemplates[reserved.key])
        assertEquals(HeaderPhase.RESERVED, reserved.phase)
        assertEquals(3, prototype.index)
        assertEquals(3, setOf(reserved.model.identifier, integer.identifier, boolean.identifier).size)
        assertEquals(0, Project.errorCount)
    }

    @Test fun localSourceAssociationsExcludeInheritedAdaptersAndPreserveTheirDeclaringOrigin() = isolated { namespace, file ->
        val parent = prototype(namespace, file, "Base", "{var inherited:T;}")
        val child = prototype(namespace, file, "Child", "{var own:T;}").apply { parentID.add("Base<T>") }
        val instance = assertNotNull(child.compile(listOf(argument())))
        val base = assertNotNull(parent.materializedTemplate(key(parent)))
        val origins = assertNotNull(child.materializedOrigins(key(child)))
        assertEquals(1, origins.fields.size)
        assertEquals("own", origins.fields.keys.single().Identifier().text)
        assertSame(base, instance.scope.getVar("inherited")!!.declaredParentTemplate)
        assertNotSame(instance.scope.getVar("own"), instance.scope.getVar("inherited"))
        assertEquals(0, Project.errorCount)
    }

    @Test fun explicitNewRequestRevokesTheOldCacheUntilProofIsRenewedWithoutReplayingMembersOrEffects() = isolated { namespace, file ->
        val prototype = prototype(namespace, file)
        prototype.annotations.add(Probe())
        val instance = assertNotNull(prototype.compile(listOf(argument())))
        val field = instance.scope.getVar("value")
        val function = instance.scope.functions.getValue("unused").single()
        Project.collectingTemplateSignatures {
            Project.registerTemplateContract(instance)
            assertFalse(instance.hasCompletedDeclaration)
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertSame(instance, prototype.compile(listOf(argument())))
            assertSame(field, instance.scope.getVar("value"))
            assertSame(function, instance.scope.functions.getValue("unused").single())
        }
        assertTrue(instance.hasCompletedDeclaration)
        assertSame(instance, prototype.compiledTemplates[key(prototype)])
        assertEquals(1, assertIs<Probe>(instance.annotations.single()).applications)
        assertEquals(1, prototype.index)
    }

    @Test fun realFieldFailureKeepsTheFailedOwnerOutOfTheCompleteCacheAfterCounterResetAndRequeue() = isolated { namespace, file ->
        val prototype = prototype(namespace, file, body = "{var value:int;var value:int;}")
        val header = stage(prototype, namespace, file)
        val instance = assertNotNull(prototype.compile(listOf(argument())))
        assertSame(header.model, instance)
        assertTrue(Project.errorCount > 0)
        assertTrue(instance.hasDeclarationCompletionFailure)
        assertFalse(instance.hasCompletedDeclaration)
        assertTrue(prototype.compiledTemplates.isEmpty())
        Project.errorCount = 0
        Project.registerTemplateContract(instance)
        assertSame(instance, prototype.compile(listOf(argument())))
        assertFalse(instance.hasCompletedDeclaration)
        assertTrue(prototype.compiledTemplates.isEmpty())
        assertEquals(0, Project.errorCount)
        assertFalse(DataTemplate.baseDataTemplate.hasDeclarationCompletionFailure)
    }

    @Test fun recursiveFieldTypesReuseTheInFlightPreparedModelWithoutPublishingItAsComplete() = isolated { namespace, file ->
        val prototype = prototype(namespace, file, body = "{var next:Box<T>?;}")
        val header = stage(prototype, namespace, file)
        Project.collectingTemplateSignatures {
            assertSame(header.model, prototype.compile(listOf(argument())))
            assertTrue(prototype.compiledTemplates.isEmpty())
            val next = assertNotNull(header.model.scope.getVar("next"))
            assertSame(header.model, assertIs<MCFPPDataTemplateType>(next.type).template)
            assertEquals(1, prototype.materializedInstances().size)
        }
        assertTrue(header.model.hasCompletedDeclaration)
        assertSame(header.model, prototype.compiledTemplates[header.key])
        assertEquals(1, prototype.index)
        assertEquals(0, Project.errorCount)
    }

    @Test fun aThrownCompletionEffectCannotEnterTheCompleteCacheOrBeErasedByClearingCounters() = isolated { namespace, file ->
        val prototype = prototype(namespace, file)
        prototype.annotations.add(ThrowingProbe())
        assertFailsWith<IllegalStateException> { prototype.compile(listOf(argument())) }
        val instance = assertNotNull(prototype.materializedTemplate(key(prototype)))
        assertTrue(instance.hasDeclarationCompletionFailure)
        assertTrue(prototype.compiledTemplates.isEmpty())
        assertFalse(instance.hasCompletedDeclaration)
        val pending = instance.pendingDeclarationAnnotations.single()
        val field = instance.scope.getVar("value")
        val function = instance.scope.functions.getValue("unused").single()
        repeat(2) {
            Project.errorCount = 0
            Project.registerTemplateContract(instance)
            assertFalse(instance.hasCompletedDeclaration)
            assertTrue(instance.hasDeclarationCompletionFailure)
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertSame(instance, prototype.materializedTemplate(key(prototype)))
            assertSame(pending, instance.pendingDeclarationAnnotations.single())
            assertSame(field, instance.scope.getVar("value"))
            assertSame(function, instance.scope.functions.getValue("unused").single())
            assertEquals(1, assertIs<ThrowingProbe>(instance.annotations.single()).applications)
            assertEquals(0, Project.errorCount)
        }
        assertFalse(DataTemplate.baseDataTemplate.hasDeclarationCompletionFailure)
    }

    @Test fun unsupportedOperatorHeadsRejectAtTheirOriginalSourceAndNeverProduceACompleteCacheEntry() = isolated { namespace, file ->
        val prototype = prototype(namespace, file, body = "{operator +(other:T):T{return other;}}")
        val capture = LogProcessor.beginDiagnosticCapture()
        try {
            val instance = assertNotNull(prototype.compile(listOf(argument())))
            val diagnostics = capture.seal().diagnostics
            val diagnostic = diagnostics.single { it.message.contains("operator header has no materialization adapter") }
            assertEquals(prototype.ctx.templateMemberDeclaration().single().start.line, diagnostic.source?.line)
            assertTrue(instance.hasDeclarationCompletionFailure)
            assertFalse(instance.hasCompletedDeclaration)
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertNull(prototype.materializedOrigins(key(prototype)))
        } finally {
            capture.close()
        }
    }

    @Test fun functionConstructorAndObjectInitializerBodiesRemainUnexecutedAtDeclarationCompletion() = isolated { namespace, file ->
        val native = NativeFunction(
            "nativeValue",
            namespace.identifier,
            MniHeaderMaterializationTest::class.java.getMethod("host", NativeCallContext::class.java)
        ).apply {
            declarationFile = file
            returnType = MCFPPBaseType.Int
        }
        file.field.addFunction(native, force = true)
        val prototype = prototype(
            namespace,
            file,
            body = """
            {var value:int=nativeValue();
             func unused():int{return nativeValue();}
             constructor(){nativeValue();}}
            """.trimIndent(),
            objectType = true
        )
        val header = stage(prototype, namespace, file)
        hostCalls = 0
        val instance = assertNotNull(prototype.compile(listOf(argument())))
        assertSame(header.model, instance)
        assertEquals(0, hostCalls)
        assertTrue(instance.preInit.containsKey("value"))
        (instance.constructors + instance.scope.functions.values.flatten()).forEach {
            assertFalse(it.bodyCompiled)
            assertTrue(it.commands.isEmpty())
        }
        assertFalse(Project.journalState().preparedObjects.any { it === instance })
        assertEquals(0, Project.errorCount)
    }

    @Test fun privateAndDeepCheckpointsRestorePendingCompleteAndLostCanonicalModelsAndOriginAdapters() = isolated { namespace, file ->
        val prototype = prototype(namespace, file)
        prototype.annotations.add(DataOnly())
        val before = DeclarationStateJournal.checkpoint(listOf(prototype))
        val header = stage(prototype, namespace, file)
        lateinit var pending: DeclarationStateJournal
        Project.collectingTemplateSignatures {
            assertSame(header.model, prototype.compile(listOf(argument())))
            pending = before.captureCurrent()
            assertTrue(prototype.compiledTemplates.isEmpty())
        }
        val complete = pending.captureCurrent()
        val origins = assertNotNull(prototype.materializedOrigins(header.key))
        val field = assertIs<MCInt>(origins.fields.values.single())
        assertTrue(field.isDataOnly)
        before.restore()
        val lost = complete.captureCurrent()
        field.type = MCFPPBaseType.Bool
        val changed = lost.captureCurrent()
        repeat(2) {
            pending.restore()
            assertSame(header.model, prototype.materializedTemplate(header.key))
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertEquals(HeaderPhase.COMPLETING, header.phase)
            complete.restore()
            assertSame(header.model, prototype.compiledTemplates[header.key])
            assertSame(origins, prototype.materializedOrigins(header.key))
            assertSame(field, origins.fields.values.single())
            assertSame(MCFPPBaseType.Int, field.type)
            assertTrue(field.isDataOnly)
            lost.restore()
            assertNull(prototype.materializedTemplate(header.key))
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertEquals(0, prototype.index)
            changed.restore()
            assertNull(prototype.materializedTemplate(header.key))
            assertSame(MCFPPBaseType.Bool, field.type)
        }
    }

    private class Probe : Annotation("Probe", "header.materialization") {
        var applications = 0
        override fun forDataTemplate(template: DataTemplate) {
            applications++
        }
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
    }
    private class ThrowingProbe : Annotation("ThrowingProbe", "header.materialization") {
        var applications = 0
        override fun forDataTemplate(template: DataTemplate) {
            applications++
            error("completion effect failed")
        }
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
    }
}
