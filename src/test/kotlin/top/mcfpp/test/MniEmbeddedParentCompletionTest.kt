package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.SpecializationArgument
import top.mcfpp.analysis.SpecializationKeys
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import kotlin.test.*
import kotlin.test.Test

class MniEmbeddedParentCompletionTest {
    private fun isolated(source: String, action: (Namespace) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val current = Function.currFunction
        val forced = Function.forcedField
        val file = MCFPPFile.currFile
        val template = DataTemplate.currTemplate
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
            MCFPPStringTest.readFromString("namespace fixture.pending\n$source\nfunc main(){}", version = "26.3")
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
            action(GlobalScope.localNamespaces.getValue("fixture.pending"))
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

    private fun target(namespace: Namespace, name: String = "Embedded") = DataTemplate(name, namespace.identifier).apply {
        declarationFile = null
        namespace.scope.template[name] = this
    }

    private fun prototype(namespace: Namespace) = assertIs<GenericDataTemplate>(assertNotNull(namespace.scope.getTemplate("Box")))

    private fun identity(prototype: GenericDataTemplate, argument: Var<*>): TypeId.Specialized = TypeId.Specialized(
        prototype.inheritanceIdentity,
        listOf(assertIs<SpecializationArgument.Constant>(SpecializationKeys.argument(argument)).value)
    )

    @Test fun readLibDefersSpecializationsUntilTheLaterIncludeAndRealCompletionBarrier() = isolated("data Box<T>{var item:T;}") { namespace ->
        val prototype = prototype(namespace)
        val id = identity(prototype, MCFPPTypeVar(MCFPPBaseType.Int, "T"))
        assertSame(prototype, namespace.scope.template.remove("Box"))
        val child = target(namespace)
        child.deferParentTypes(listOf(id))
        Project.compileStage = Project.CompileStage.READ_LIB
        assertNull(MCFPPType.resolveSpecialization(id))
        Project.registerAnonymousTemplate(child)
        Project.resolveTemplateInheritance(child)
        Project.completeTemplateDeclarations()
        assertTrue(prototype.compiledTemplates.isEmpty())
        assertEquals(0, prototype.index)
        assertTrue(child.parent.isEmpty())
        assertEquals(listOf(id), child.pendingParentTypeIds)
        assertFalse(child.inheritanceIdentity in Project.journalState().boundParents)
        assertFalse(child.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)

        namespace.scope.template["Box"] = prototype
        Project.compileStage = Project.CompileStage.COMPILE
        Project.completeTemplateDeclarations()
        val canonical = prototype.compiledTemplates.values.single()
        assertSame(canonical, child.parent.single())
        assertSame(canonical, child.scope.getVar("item")!!.declaredParentTemplate)
        assertEquals(id, canonical.inheritanceIdentity)
        assertTrue(child.pendingParentTypeIds.isEmpty())
        assertTrue(child.inheritanceIdentity in Project.journalState().boundParents)
        assertTrue(child.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
    }

    @Test fun orderedParentsKeepDifferentReadonlyBindingsOfOnePrototypeAndApplyEffectsOnce() = portableOrderedParents { it }

    internal fun portableOrderedParents(roundTrip: (CountApplications) -> CountApplications) = isolated("data Box<N:int>{}") { namespace ->
        val prototype = prototype(namespace)
        prototype.annotations.add(roundTrip(CountApplications()))
        val first = identity(prototype, MCInt(3, "N"))
        val second = identity(prototype, MCInt(7, "N"))
        val child = target(namespace)
        val inputs = arrayListOf<TypeId>(second, first)
        child.deferParentTypes(inputs)
        inputs.clear()
        Project.registerTemplateContract(child)
        assertEquals(listOf(second, first), child.parent.map { it.inheritanceIdentity })
        assertNotSame(child.parent[0], child.parent[1])
        assertEquals(2, prototype.compiledTemplates.size)
        for (parent in child.parent) {
            val canonical = assertIs<CompiledGenericDataTemplate>(parent)
            assertSame(canonical, MCFPPType.resolveSpecialization(canonical.inheritanceIdentity as TypeId.Specialized))
            assertEquals(1, assertIs<CountApplications>(canonical.annotations.single()).applications)
            assertTrue(canonical.hasCompletedDeclaration)
        }
        Project.registerTemplateContract(child)
        assertEquals(listOf(second, first), child.parent.map { it.inheritanceIdentity })
        assertEquals(2, prototype.index)
        child.parent.forEach { assertEquals(1, assertIs<CountApplications>(it.annotations.single()).applications) }
        assertTrue(child.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }

    @Test fun importedPendingParentsEnterTheExistingCompletionPass() = isolated("data Parent{var inherited:int;}") { namespace ->
        val parent = namespace.scope.template.getValue("Parent")
        val library = Namespace("embedded.library")
        GlobalScope.libNamespaces[library.identifier] = library
        val child = target(library)
        child.deferParentTypes(listOf(parent.inheritanceIdentity))
        Project.compileStage = Project.CompileStage.INDEX_TYPE
        Project.resolveImportedTemplateParents()
        assertSame(parent, child.parent.single())
        assertFalse(child.hasCompletedDeclaration)
        Project.completeTemplateDeclarations()
        assertSame(parent, child.scope.getVar("inherited")!!.declaredParentTemplate)
        assertNotNull(child.scope.getProperty("inherited"))
        assertTrue(child.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
    }

    @Test fun missingParentDoesNotPublishTheEarlierValidParentOrACompletionProof() = isolated("data Healthy{}") { namespace ->
        val healthy = namespace.scope.template.getValue("Healthy")
        val child = target(namespace)
        val ids = listOf(healthy.inheritanceIdentity, TypeId.Declaration("template", "missing.provider", "Absent"))
        child.deferParentTypes(ids)
        Project.registerTemplateContract(child)
        assertTrue(Project.errorCount > 0)
        assertTrue(child.parent.isEmpty())
        assertTrue(child.scope.parent.isEmpty())
        assertEquals(ids, child.pendingParentTypeIds)
        assertFalse(child.inheritanceIdentity in Project.journalState().boundParents)
        assertFalse(child.hasCompletedDeclaration)
        Project.errorCount = 0
        Project.registerTemplateContract(child)
        assertFalse(child.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount, "Requeueing must not replay the failed missing-parent resolution")
        assertTrue(child.hasDeclarationCompletionFailure)
        assertTrue(child.parent.isEmpty())
        assertFalse(child.inheritanceIdentity in Project.journalState().boundParents)
    }

    @Test fun finalParentDoesNotPublishTheEarlierValidParentAndRollbackPermitsARealRetry() = isolated("data Healthy{}\nfinal data Closed{}") { namespace ->
        val healthy = namespace.scope.template.getValue("Healthy")
        val closed = namespace.scope.template.getValue("Closed")
        val child = target(namespace)
        val ids = listOf(healthy.inheritanceIdentity, closed.inheritanceIdentity)
        child.deferParentTypes(ids)
        val before = DeclarationStateJournal.checkpoint(listOf(child, healthy, closed))
        val projectBefore = ProjectStateJournal.checkpoint()
        Project.registerTemplateContract(child)
        assertTrue(Project.errorCount > 0)
        assertTrue(child.parent.isEmpty())
        assertTrue(child.scope.parent.isEmpty())
        assertEquals(ids, child.pendingParentTypeIds)
        assertFalse(child.inheritanceIdentity in Project.journalState().boundParents)
        Project.errorCount = 0
        Project.registerTemplateContract(child)
        assertFalse(child.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount, "Requeueing must not replay the failed final-parent resolution")
        assertTrue(child.hasDeclarationCompletionFailure)
        before.restore()
        projectBefore.restore()
        closed.isFinal = false
        Project.registerTemplateContract(child)
        assertEquals(2, child.parent.size)
        assertSame(healthy, child.parent[0])
        assertSame(closed, child.parent[1])
        assertTrue(child.pendingParentTypeIds.isEmpty())
        assertTrue(child.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
    }

    @Test fun failedResolutionNeedsCheckpointRollbackBeforeAFixedProviderCanBindAndProofCanReturn() = isolated("data Healthy{}") { namespace ->
        val healthy = namespace.scope.template.getValue("Healthy")
        val child = target(namespace)
        val missing = TypeId.Declaration("template", "later.provider", "Arrived")
        val ids = listOf(healthy.inheritanceIdentity, missing)
        child.deferParentTypes(ids)
        val before = DeclarationStateJournal.checkpoint(listOf(child, healthy))
        val projectBefore = ProjectStateJournal.checkpoint()
        Project.registerTemplateContract(child)
        assertTrue(Project.errorCount > 0)
        val failed = before.captureCurrent()
        val failedProject = projectBefore.captureCurrent()

        val provider = Namespace("later.provider")
        GlobalScope.localNamespaces[provider.identifier] = provider
        val arrived = target(provider, "Arrived")
        Project.errorCount = 0
        repeat(2) {
            Project.registerTemplateContract(child)
            assertEquals(0, Project.errorCount)
            assertTrue(child.hasDeclarationCompletionFailure)
            assertFalse(child.hasCompletedDeclaration)
            assertEquals(ids, child.pendingParentTypeIds)
            assertTrue(child.parent.isEmpty())
            assertFalse(child.inheritanceIdentity in Project.journalState().boundParents)
        }

        before.restore()
        projectBefore.restore()
        assertFalse(child.hasDeclarationCompletionFailure)
        Project.registerTemplateContract(child)
        assertEquals(0, Project.errorCount)
        assertEquals(2, child.parent.size)
        assertSame(healthy, child.parent[0])
        assertSame(arrived, child.parent[1])
        assertTrue(child.pendingParentTypeIds.isEmpty())
        assertTrue(child.hasCompletedDeclaration)
        assertTrue(child.inheritanceIdentity in Project.journalState().boundParents)
        val completed = failed.captureCurrent()
        val completedProject = failedProject.captureCurrent()
        repeat(2) {
            failed.restore()
            failedProject.restore()
            Project.errorCount = 0
            Project.registerTemplateContract(child)
            assertEquals(0, Project.errorCount)
            assertTrue(child.hasDeclarationCompletionFailure)
            assertFalse(child.hasCompletedDeclaration)
            assertEquals(ids, child.pendingParentTypeIds)
            assertTrue(child.parent.isEmpty())
            assertFalse(child.inheritanceIdentity in Project.journalState().boundParents)
            completed.restore()
            completedProject.restore()
            assertFalse(child.hasDeclarationCompletionFailure)
            assertTrue(child.hasCompletedDeclaration)
            assertSame(arrived, child.parent[1])
            assertTrue(child.pendingParentTypeIds.isEmpty())
            assertTrue(child.inheritanceIdentity in Project.journalState().boundParents)
        }
    }

    @Test fun canonicalCycleCheckRollsBackEveryTentativeEdgeAndPreservesPendingOrder() = isolated("data Healthy{}") { namespace ->
        val healthy = namespace.scope.template.getValue("Healthy")
        val child = target(namespace)
        val ids = listOf(healthy.inheritanceIdentity, child.inheritanceIdentity)
        child.deferParentTypes(ids)
        Project.registerTemplateContract(child)
        assertTrue(Project.errorCount > 0)
        assertTrue(child.parent.isEmpty())
        assertTrue(child.scope.parent.isEmpty())
        assertEquals(ids, child.pendingParentTypeIds)
        assertFalse(child.inheritanceIdentity in Project.journalState().boundParents)
        assertFalse(child.hasCompletedDeclaration)
    }

    @Test fun sourceParentsUseTheSameAtomicBindingAndRejectFinalWithoutHalfAncestry() = isolated("data Healthy{}\nfinal data Closed{}") { namespace ->
        val child = target(namespace)
        child.parentID.addAll(listOf("${namespace.identifier}:Healthy", "${namespace.identifier}:Closed"))
        Project.registerTemplateContract(child)
        assertTrue(Project.errorCount > 0)
        assertTrue(child.parent.isEmpty())
        assertTrue(child.scope.parent.isEmpty())
        assertFalse(child.inheritanceIdentity in Project.journalState().boundParents)
        assertFalse(child.hasCompletedDeclaration)
        Project.errorCount = 0
        repeat(2) {
            Project.registerTemplateContract(child)
            assertEquals(0, Project.errorCount)
            assertFalse(child.hasCompletedDeclaration)
            assertTrue(child.parent.isEmpty())
            assertFalse(child.inheritanceIdentity in Project.journalState().boundParents)
        }
    }

    @Test fun privateCheckpointRestoresTheOriginalPendingContainerAndAllObservedGenerations() = isolated("data Box<T>{}") { namespace ->
        val prototype = prototype(namespace)
        val first = identity(prototype, MCFPPTypeVar(MCFPPBaseType.Int, "T"))
        val second = identity(prototype, MCFPPTypeVar(MCFPPBaseType.Bool, "T"))
        val child = target(namespace)
        child.deferParentTypes(listOf(first, second))
        val slot = DataTemplate::class.java.getDeclaredField("pendingParentTypes").apply { isAccessible = true }
        val container = slot.get(child)
        val before = child.checkpointDeclarationState()
        child.deferParentTypes(listOf(second))
        val after = before.captureCurrent()
        child.deferParentTypes(emptyList())
        val cleared = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(container, slot.get(child))
            assertEquals(listOf(first, second), child.pendingParentTypeIds)
            after.restore()
            assertSame(container, slot.get(child))
            assertEquals(listOf(second), child.pendingParentTypeIds)
            cleared.restore()
            assertSame(container, slot.get(child))
            assertTrue(child.pendingParentTypeIds.isEmpty())
        }
        assertTrue(cleared.elements.any { it === first })
        assertTrue(cleared.elements.any { it === second })
        assertFailsWith<UnsupportedOperationException> { (child.pendingParentTypeIds as MutableList<TypeId>).add(first) }
    }

    @Test fun deepCheckpointRecapturesPendingIdentitiesOnLostParentRootsAcrossGenerations() = isolated("data Box<T>{}") { namespace ->
        val prototype = prototype(namespace)
        val ints = identity(prototype, MCFPPTypeVar(MCFPPBaseType.Int, "T"))
        val bools = identity(prototype, MCFPPTypeVar(MCFPPBaseType.Bool, "T"))
        val floats = identity(prototype, MCFPPTypeVar(MCFPPBaseType.Float, "T"))
        val owner = target(namespace, "Owner")
        val lost = target(namespace, "Lost")
        lost.deferParentTypes(listOf(ints))
        owner.extends(lost)
        val before = DeclarationStateJournal.checkpoint(listOf(owner))
        owner.parent.clear()
        owner.scope.parent.clear()
        namespace.scope.template.remove(lost.identifier)
        lost.deferParentTypes(listOf(bools))
        val after = before.captureCurrent()
        lost.deferParentTypes(listOf(floats))
        val third = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(lost, owner.parent.single())
            assertEquals(listOf(ints), lost.pendingParentTypeIds)
            after.restore()
            assertTrue(owner.parent.isEmpty())
            assertEquals(listOf(bools), lost.pendingParentTypeIds)
            third.restore()
            assertTrue(owner.parent.isEmpty())
            assertEquals(listOf(floats), lost.pendingParentTypeIds)
        }
        assertTrue(third.elements.any { it === lost })
        assertTrue(third.elements.any { it === ints })
        assertTrue(third.elements.any { it === bools })
        assertTrue(third.elements.any { it === floats })
    }

    @Test fun realCompletionAndDeepRestoreRetainCanonicalParentFieldsEffectsCacheAndPendingState() = isolated("@DataOnly\ndata Box<T>{var item:T;}") { namespace ->
        val prototype = prototype(namespace)
        val id = identity(prototype, MCFPPTypeVar(MCFPPBaseType.Int, "T"))
        val child = target(namespace)
        child.deferParentTypes(listOf(id))
        val before = DeclarationStateJournal.checkpoint(listOf(child, prototype))
        val projectBefore = ProjectStateJournal.checkpoint()
        Project.registerTemplateContract(child)
        val canonical = assertIs<CompiledGenericDataTemplate>(child.parent.single())
        val field = assertIs<MCInt>(child.scope.getVar("item"))
        assertTrue(field.isDataOnly)
        assertTrue(child.hasCompletedDeclaration)
        val after = before.captureCurrent()
        val projectAfter = projectBefore.captureCurrent()
        prototype.compiledTemplates.clear()
        child.parent.clear()
        child.scope.parent.clear()
        child.deferParentTypes(listOf(id))
        val lost = after.captureCurrent()
        repeat(2) {
            before.restore()
            projectBefore.restore()
            assertEquals(listOf(id), child.pendingParentTypeIds)
            assertTrue(child.parent.isEmpty())
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertFalse(child.hasCompletedDeclaration)
            after.restore()
            projectAfter.restore()
            assertSame(canonical, child.parent.single())
            assertSame(canonical, prototype.compiledTemplates.values.single())
            assertTrue(child.pendingParentTypeIds.isEmpty())
            assertTrue(child.hasCompletedDeclaration)
            assertSame(field, child.scope.getVar("item"))
            assertTrue(field.isDataOnly)
            assertSame(canonical, field.declaredParentTemplate)
            lost.restore()
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertTrue(child.parent.isEmpty())
            assertEquals(listOf(id), child.pendingParentTypeIds)
            assertFalse(child.hasCompletedDeclaration)
        }
        assertTrue(lost.elements.any { it === canonical })
    }

    class CountApplications : Annotation("PendingParentCounter", "fixture.pending") {
        var applications = 0
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
        override fun forDataTemplate(data: DataTemplate) {
            applications++
        }
    }
}
