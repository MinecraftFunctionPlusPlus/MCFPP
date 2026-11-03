package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.SpecializationKeys
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.annotation.DataOnly
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.util.LogProcessor
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniTemplateCompletionProofTest {
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text))))
    private fun model(name: String = "Target") = DataTemplate(name, "proof.templates").apply { declarationFile = null }
    private fun probe(target: DataTemplate, mode: String = "normal") = Probe().apply {
        this.mode = mode
        target.pendingDeclarationAnnotations.add(this)
    }
    private fun body(owner: DataTemplate, name: String = "unused") = Function(
        name,
        owner,
        parser("{\n/say body-must-not-run\n}").curlBlock()
    ).also { owner.scope.addFunction(it, false) }

    private fun proofState(root: DataTemplate): String {
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
        val states = arrayListOf<String>()
        fun visit(template: DataTemplate) {
            if (!visited.add(template)) return
            fun raw(name: String): Any? = DataTemplate::class.java.getDeclaredField(name).apply {
                isAccessible = true
            }.get(template)
            states.add(
                "${template.namespaceID}: generation=${raw("declarationCompletionGeneration")}, " +
                    "phase=${raw("declarationCompletionPhase")}, poisoned=${raw("declarationCompletionPoisoned")}, " +
                    "deferred=${template.deferredFields.keys}, pendingAnnotations=${template.pendingDeclarationAnnotations.size}, " +
                    "pendingFields=${template.pendingFieldAnnotations.keys}, layout=${raw("fieldLayoutDiagnostics")}"
            )
            template.parent.filterIsInstance<DataTemplate>().forEach(::visit)
        }
        visit(root)
        return "errors=${Project.errorCount}, pending=${Project.journalState().pending.map { it.namespaceID }}; " +
            states.joinToString("; ")
    }

    private fun isolated(action: (Namespace, MCFPPFile) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val current = Function.currFunction
        val forcedField = Function.forcedField
        val file = MCFPPFile.currFile
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
            MCFPPStringTest.readFromString("func main(){}", version = "26.3")
            assertEquals(0, Project.errorCount)
            val namespace = GlobalScope.getOrCreateNamespace("proof.templates")
            val declarationFile = MCFPPFile(namespace)
            MCFPPFile.currFile = declarationFile
            Project.currNamespace = namespace.identifier
            action(namespace, declarationFile)
        } finally {
            base.restore()
            namespaces.restore()
            project.restore()
            Function.currFunction = current
            MCFPPFile.currFile = file
            DataTemplate.currTemplate = template
            Function.forcedField = forcedField
            CompileSettings.ignoreStdLib = settings[0]
            CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = maxWhileInline
        }
    }

    private fun generic(namespace: Namespace, file: MCFPPFile, annotation: Annotation = Probe()): GenericDataTemplate = GenericDataTemplate(
        parser("{var value:int=3; func unused():int{return 7;}}").templateBody(),
        "Box",
        namespace.identifier
    ).apply {
        declarationFile = file
        readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
        annotations.add(annotation)
        namespace.scope.genericTemplate[identifier to listOf(MCFPPConcreteType.Type)] = this
    }

    @Test fun actualLegacyCacheEntryIsPendingUntilTheOutermostBarrierAndThenReusedOnce() = isolated { namespace, file ->
        val prototype = generic(namespace, file)
        val argument = MCFPPTypeVar(MCFPPBaseType.Int, "T")
        val key = SpecializationKeys.forArguments(prototype.declarationId, listOf(argument))
        lateinit var instance: CompiledGenericDataTemplate
        Project.collectingTemplateSignatures {
            instance = assertNotNull(prototype.compile(listOf(argument)))
            assertSame(instance, prototype.materializedTemplate(key))
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertTrue(Project.journalState().pending.any { it === instance })
            assertFalse(instance.hasCompletedDeclaration)
            assertEquals(0, assertIs<Probe>(instance.annotations.single()).applications)
        }
        val annotation = assertIs<Probe>(instance.annotations.single())
        assertTrue(instance.hasCompletedDeclaration, proofState(instance))
        assertEquals(1, annotation.applications)
        assertSame(instance, prototype.compiledTemplates[key])
        assertTrue(annotation.sawField)
        assertFalse(annotation.sawCompletedProof)
        assertSame(instance, prototype.compile(listOf(argument)))
        assertEquals(1, annotation.applications)
        assertTrue(instance.scope.functions.getValue("unused").single().commands.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun newRequestsInvalidateOldProofThroughoutNestedCollection() = isolated { _, _ ->
        val target = model()
        val annotation = probe(target)
        Project.registerTemplateContract(target)
        assertTrue(target.hasCompletedDeclaration)
        Project.collectingTemplateSignatures {
            Project.registerTemplateContract(target)
            assertFalse(target.hasCompletedDeclaration)
            Project.collectingTemplateSignatures {
                Project.registerTemplateContract(target)
                assertFalse(target.hasCompletedDeclaration)
            }
            assertFalse(target.hasCompletedDeclaration)
        }
        assertTrue(target.hasCompletedDeclaration)
        assertEquals(1, annotation.applications)
    }

    @Test fun directDeclarationPassRevokesProofBeforeApplyingNewFactsAndDoesNotCompileBodies() = isolated { namespace, _ ->
        val target = model()
        namespace.scope.addTemplate(target.identifier, target, false)
        val function = body(target)
        Project.registerTemplateContract(target)
        assertTrue(target.hasCompletedDeclaration)
        val annotation = probe(target)
        Project.completeTemplateDeclarations()
        assertTrue(target.hasCompletedDeclaration, proofState(target))
        assertFalse(annotation.sawCompletedProof)
        assertEquals(1, annotation.applications)
        assertTrue(function.commands.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun anonymousRegistrationUsesTheSamePendingAndCompletionBoundary() = isolated { _, _ ->
        val target = model("Anonymous")
        val annotation = probe(target)
        Project.collectingTemplateSignatures {
            Project.registerAnonymousTemplate(target)
            assertFalse(target.hasCompletedDeclaration)
            assertTrue(Project.journalState().pending.any { it === target })
        }
        assertTrue(target.hasCompletedDeclaration)
        assertEquals(1, annotation.applications)
    }

    @Test fun alreadyDiagnosedInvalidContractIsRecheckedAndCorrectionDoesNotReplayAnnotations() = isolated { _, _ ->
        val target = model()
        val annotation = probe(target)
        val abstractMethod = Function("missing", target, null).apply { isAbstract = true }
        target.scope.addFunction(abstractMethod, false)
        Project.registerTemplateContract(target)
        assertFalse(target.hasCompletedDeclaration)
        assertTrue(Project.errorCount > 0)
        val errors = Project.errorCount
        Project.registerTemplateContract(target)
        assertEquals(errors, Project.errorCount, "Repeated diagnostics are deduplicated")
        assertFalse(target.hasCompletedDeclaration, "A zero diagnostic delta cannot prove an invalid contract")
        target.isAbstract = true
        Project.errorCount = 0
        Project.registerTemplateContract(target)
        assertTrue(target.hasCompletedDeclaration)
        assertEquals(1, annotation.applications)
        assertEquals(0, Project.errorCount)
    }

    @Test fun conflictingFieldLayoutCannotAcquireProofAfterDiagnosticDeduplication() = isolated { _, _ ->
        val parent = model("Parent")
        val child = model("Child")
        val inherited = MCInt("value")
        val local = MCFloat("value")
        parent.scope.putVar("value", inherited)
        parent.scope.putProperty("value", Property.buildSimpleProperty(inherited))
        child.scope.putVar("value", local)
        child.scope.putProperty("value", Property.buildSimpleProperty(local))
        child.extends(parent)
        Project.registerTemplateContract(child)
        assertFalse(child.hasCompletedDeclaration)
        assertTrue(Project.errorCount > 0)
        Project.errorCount = 0
        Project.registerTemplateContract(child)
        assertEquals(0, Project.errorCount, "The layout diagnostic was already recorded")
        assertFalse(child.hasCompletedDeclaration)
    }

    @Test fun diagnosedRawParentCycleCannotAcquireProofAfterClearingTheErrorCounter() = isolated { namespace, _ ->
        val first = model("A")
        val second = model("B")
        first.parentID.add("B")
        second.parentID.add("A")
        namespace.scope.addTemplate(first.identifier, first, false)
        namespace.scope.addTemplate(second.identifier, second, false)
        Project.completeTemplateDeclarations()
        assertTrue(Project.errorCount > 0)
        assertFalse(Project.hasValidTemplateInheritance(first))
        assertFalse(Project.hasValidTemplateInheritance(second))
        assertFalse(first.hasCompletedDeclaration)
        assertFalse(second.hasCompletedDeclaration)
        assertTrue(first.parent.isEmpty())
        assertTrue(second.parent.isEmpty())

        Project.errorCount = 0
        Project.registerTemplateContract(first)
        Project.registerTemplateContract(second)
        assertEquals(0, Project.errorCount, "The raw header cycle diagnostic remains deduplicated")
        assertFalse(first.hasCompletedDeclaration, "An unmaterialized parent graph cannot prove a cyclic declaration")
        assertFalse(second.hasCompletedDeclaration)
    }

    private enum class ParentResolutionEntry { DECLARATION_PASS, ANONYMOUS, IMPORTED, DIRECT }

    private fun assertParentFailureSurvivesRetry(
        entry: ParentResolutionEntry,
        finalParent: Boolean,
        abstractChild: Boolean = false
    ) = isolated { namespace, file ->
        val parent = if (finalParent) model("FinalParent").apply {
            isFinal = true
            declarationFile = file
            namespace.scope.addTemplate(identifier, this, false)
        } else null
        val child = model("Child").apply {
            declarationFile = file
            isAbstract = abstractChild
            parentID.add(parent?.identifier ?: "DefinitelyMissingParent")
        }
        if (entry != ParentResolutionEntry.ANONYMOUS) {
            namespace.scope.addTemplate(child.identifier, child, false)
        }
        when (entry) {
            ParentResolutionEntry.DECLARATION_PASS -> Project.completeTemplateDeclarations()
            ParentResolutionEntry.ANONYMOUS -> Project.registerAnonymousTemplate(child)
            ParentResolutionEntry.IMPORTED -> {
                GlobalScope.localNamespaces.remove(namespace.identifier)
                GlobalScope.libNamespaces[namespace.identifier] = namespace
                GlobalScope.importedLibNamespaces[namespace.identifier] = namespace
                Project.resolveImportedTemplateParents()
            }
            ParentResolutionEntry.DIRECT -> Project.resolveTemplateInheritance(child)
        }
        assertTrue(Project.errorCount > 0)
        assertTrue(child.hasDeclarationCompletionFailure, proofState(child))
        assertFalse(child.hasCompletedDeclaration)
        assertTrue(child.parent.isEmpty(), "An invalid raw parent never became an actual parent edge")

        Project.errorCount = 0
        repeat(2) { Project.registerTemplateContract(child) }
        assertFalse(child.hasCompletedDeclaration, proofState(child))
        assertTrue(child.hasDeclarationCompletionFailure)
        assertEquals(0, Project.errorCount, "The previously bound entry must retain failure without replaying diagnostics")
        parent?.let {
            assertFalse(it.hasDeclarationCompletionFailure)
            Project.registerTemplateContract(it)
            assertTrue(it.hasCompletedDeclaration, proofState(it))
        }
    }

    @Test fun missingParentInTheDeclarationPassCannotCompleteAfterCounterReset() = assertParentFailureSurvivesRetry(ParentResolutionEntry.DECLARATION_PASS, false)

    @Test fun finalParentInTheDeclarationPassCannotCompleteAfterCounterReset() = assertParentFailureSurvivesRetry(ParentResolutionEntry.DECLARATION_PASS, true)

    @Test fun anonymousMissingParentCannotCompleteAfterCounterReset() = assertParentFailureSurvivesRetry(ParentResolutionEntry.ANONYMOUS, false)

    @Test fun anonymousFinalParentCannotCompleteAfterCounterReset() = assertParentFailureSurvivesRetry(ParentResolutionEntry.ANONYMOUS, true)

    @Test fun importedMissingParentCannotCompleteAfterCounterReset() = assertParentFailureSurvivesRetry(ParentResolutionEntry.IMPORTED, false)

    @Test fun importedFinalParentCannotCompleteAfterCounterReset() = assertParentFailureSurvivesRetry(ParentResolutionEntry.IMPORTED, true)

    @Test fun directlyResolvedMissingParentCannotCompleteAfterCounterReset() = assertParentFailureSurvivesRetry(ParentResolutionEntry.DIRECT, false)

    @Test fun directlyResolvedFinalParentCannotCompleteAfterCounterReset() = assertParentFailureSurvivesRetry(ParentResolutionEntry.DIRECT, true)

    @Test fun directlyResolvedMissingAbstractDataParentCannotCompleteAfterCounterReset() = assertParentFailureSurvivesRetry(ParentResolutionEntry.DIRECT, false, abstractChild = true)

    @Test fun mutatingStageErrorCannotBeErasedByClearingCountersAndRequeueing() = isolated { _, _ ->
        val target = model()
        val annotation = probe(target, "error")
        Project.registerTemplateContract(target)
        assertFalse(target.hasCompletedDeclaration)
        assertEquals(1, annotation.applications)
        assertTrue(target.pendingDeclarationAnnotations.isEmpty())
        assertTrue(Project.errorCount > 0)
        Project.errorCount = 0
        Project.registerTemplateContract(target)
        assertFalse(target.hasCompletedDeclaration, "Consumed, partially applied annotation effects require rollback")
        assertEquals(1, annotation.applications)
    }

    @Test fun failedChildDoesNotPoisonItsHealthySharedParentOrLaterGenericBinding() = isolated { namespace, file ->
        val parent = DataTemplate.baseDataTemplate
        val broken = model("Broken").apply { extends(parent) }
        val failure = probe(broken, "error")
        Project.registerTemplateContract(broken)
        assertTrue(Project.errorCount > 0)
        assertFalse(broken.hasCompletedDeclaration)
        assertFalse(parent.hasCompletedDeclaration, "A failed barrier grants no participating declaration a proof")
        assertEquals(1, failure.applications)

        Project.errorCount = 0
        val prototype = generic(namespace, file)
        val healthy = assertNotNull(prototype.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int, "T"))))
        assertTrue(healthy.hasCompletedDeclaration, proofState(healthy))
        assertTrue(parent.hasCompletedDeclaration, proofState(parent))
        assertEquals(1, assertIs<Probe>(healthy.annotations.single()).applications)
        Project.registerTemplateContract(broken)
        assertFalse(broken.hasCompletedDeclaration, "The failing annotation's own partial state still requires rollback")
        assertEquals(1, failure.applications)
        assertEquals(0, Project.errorCount)
    }

    @Test fun thrownAnnotationRevokesProofAndPrivateRollbackAllowsARealSuccessfulRetry() = isolated { _, _ ->
        val target = model()
        val annotation = probe(target, "throw")
        val before = target.checkpointDeclarationState()
        assertFailsWith<IllegalStateException> { Project.registerTemplateContract(target) }
        assertFalse(target.hasCompletedDeclaration)
        assertEquals(1, annotation.applications)
        before.restore()
        annotation.mode = "normal"
        // The private hook restores owned relations/proof, never arbitrary host annotation fields.
        assertEquals(1, annotation.applications)
        Project.registerTemplateContract(target)
        assertTrue(target.hasCompletedDeclaration)
        assertEquals(2, annotation.applications)
        assertEquals(0, Project.errorCount)
    }

    @Test fun invalidDefaultsDoNotProveADeclarationAndRollbackPreservesSuccessfulEffects() = isolated { _, _ ->
        val target = model()
        val annotation = probe(target)
        val function = body(target)
        val parameter = FunctionParam(MCFPPBaseType.Int, "value", function, hasDefault = true).apply {
            defaultContext = parser("3").value()
        }
        function.normalParams.add(parameter)
        Project.registerTemplateContract(target)
        assertTrue(target.hasCompletedDeclaration)
        val before = DeclarationStateJournal.checkpoint(listOf(target))
        parameter.defaultContext = parser("[missing]").value()
        Project.registerTemplateContract(target)
        assertFalse(target.hasCompletedDeclaration)
        assertTrue(Project.errorCount > 0)
        before.restore()
        Project.errorCount = 0
        Project.registerTemplateContract(target)
        assertTrue(target.hasCompletedDeclaration)
        assertEquals(1, annotation.applications)
        assertTrue(function.commands.isEmpty())
    }

    @Test fun reentrantRequestCannotBorrowTheProofOfAnEarlierGeneration() = isolated { _, _ ->
        val target = model()
        val annotation = probe(target, "requeue")
        Project.registerTemplateContract(target)
        assertFalse(target.hasCompletedDeclaration)
        assertEquals(1, annotation.applications)
        Project.registerTemplateContract(target)
        assertTrue(target.hasCompletedDeclaration)
        assertEquals(1, annotation.applications)
        assertEquals(0, Project.errorCount)
    }

    @Test fun deepCheckpointRetainsProofForLostActualGenericInstancesAcrossGenerations() = isolated { namespace, file ->
        val prototype = generic(namespace, file, DataOnly())
        lateinit var instance: CompiledGenericDataTemplate
        lateinit var before: DeclarationStateJournal
        Project.collectingTemplateSignatures {
            instance = assertNotNull(prototype.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int, "T"))))
            before = DeclarationStateJournal.checkpoint(listOf(prototype))
            assertFalse(instance.hasCompletedDeclaration)
        }
        val after = before.captureCurrent()
        val annotation = assertIs<DataOnly>(instance.annotations.single())
        val field = assertIs<MCInt>(instance.scope.getVar("value"))
        val property = assertNotNull(instance.scope.getProperty("value"))
        assertTrue(field.isDataOnly)
        prototype.compiledTemplates.clear()
        lateinit var lost: DeclarationStateJournal
        Project.collectingTemplateSignatures {
            Project.registerTemplateContract(instance)
            lost = after.captureCurrent()
            assertFalse(instance.hasCompletedDeclaration)
        }
        repeat(2) {
            before.restore()
            assertSame(instance, prototype.materializedInstances().single())
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertFalse(instance.hasCompletedDeclaration)
            assertSame(field, instance.scope.getVar("value"))
            assertSame(property, instance.scope.getProperty("value"))
            assertFalse(field.isDataOnly)
            after.restore()
            assertSame(instance, prototype.compiledTemplates.values.single())
            assertTrue(instance.hasCompletedDeclaration, proofState(instance))
            assertSame(field, instance.scope.getVar("value"))
            assertSame(property, instance.scope.getProperty("value"))
            assertTrue(field.isDataOnly)
            assertSame(annotation, instance.annotations.single())
            lost.restore()
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertSame(instance, prototype.materializedInstances().single())
            assertFalse(instance.hasCompletedDeclaration)
            assertSame(field, instance.scope.getVar("value"))
            assertSame(property, instance.scope.getProperty("value"))
            assertTrue(field.isDataOnly)
            assertEquals(1, prototype.index)
        }
        assertTrue(lost.elements.any { it === instance })
    }

    private class UnknownTemplate : DataTemplate("Unknown", "proof.templates") {
        var calls = 0
        override fun initialize() {
            calls++
            error("Unexpected initialize")
        }
        override fun getType(): MCFPPDataTemplateType {
            calls++
            error("Unexpected getType")
        }
    }

    @Test fun proofGetterReadsOnlyItsPrivateEvidenceAndNeverUnknownVirtualHooks() = isolated { _, _ ->
        val target = UnknownTemplate()
        repeat(2) { assertFalse(target.hasCompletedDeclaration) }
        assertEquals(0, target.calls)
    }

    class Probe : Annotation("CompletionProbe", "proof.templates") {
        var applications = 0
        var mode = "normal"
        var sawCompletedProof = false
        var sawField = false
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
        override fun forDataTemplate(data: DataTemplate) {
            applications++
            sawCompletedProof = data.hasCompletedDeclaration
            sawField = data.scope.getVar("value") != null
            when (mode) {
                "error" -> LogProcessor.error("Completion probe failed")
                "throw" -> error("Completion probe threw")
                "requeue" -> Project.registerTemplateContract(data)
            }
        }
    }
}
