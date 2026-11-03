package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.property.SimpleAccessor
import top.mcfpp.model.property.SimpleMutator
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.TypeId
import kotlin.test.*
import kotlin.test.Test

/** Direct restored-layout tests use compiler builtins and do not read bundled libraries. */
class MniPendingParentLayoutTest {
    private fun isolated(action: (Namespace) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val config = Project.config
        val ignoreStdLib = CompileSettings.ignoreStdLib
        val caller = Function.currFunction
        val forced = Function.forcedField
        val file = MCFPPFile.currFile
        val template = DataTemplate.currTemplate
        try {
            Project.config = ProjectConfig(version = "26.3")
            CompileSettings.ignoreStdLib = true
            Project.init()
            Project.compileStage = Project.CompileStage.READ_LIB
            MCFPPFile.currFile = null
            val namespace = Namespace("fixture.pendinglayout")
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            action(namespace)
        } finally {
            base.restore()
            namespaces.restore()
            project.restore()
            Project.config = config
            CompileSettings.ignoreStdLib = ignoreStdLib
            Function.currFunction = caller
            Function.forcedField = forced
            MCFPPFile.currFile = file
            DataTemplate.currTemplate = template
        }
    }

    private fun declaration(namespace: Namespace, name: String) = DataTemplate(name, namespace.identifier).apply {
        declarationFile = null
        namespace.scope.template[name] = this
    }

    private fun root(namespace: Namespace) = declaration(namespace, "Root").apply {
        assertTrue(addMember(MCInt("item").also { it.declaredParentTemplate = this }))
        assertTrue(
            addMember(
                Property("item", SimpleAccessor(), SimpleMutator()).also {
                    it.declaredParentTemplate = this
                    it.isStatic = false
                }
            )
        )
    }

    private fun restoreEffect(target: DataTemplate, origin: TypeId) {
        val field = MCInt("item").apply {
            isDataOnly = true
            accessModifier = AccessModifier.PROTECTED
        }
        val property = Property("item", SimpleAccessor(), SimpleMutator()).apply {
            accessModifier = AccessModifier.PROTECTED
            isStatic = false
        }
        target.restoreInheritedFields(listOf(DataTemplate.CompletedInheritedField(origin, field, property)))
    }

    private fun assertUnlaid(target: DataTemplate) {
        assertNull(target.scope.getVar("item"))
        assertNull(target.scope.getProperty("item"))
        assertTrue(target.scope.fieldOrigins.isEmpty())
        assertFalse(target.hasCompletedDeclaration)
        assertFalse(target.hasDeclarationCompletionFailure)
        assertEquals(0, Project.errorCount)
    }

    private fun complete(target: DataTemplate) {
        Project.compileStage = Project.CompileStage.COMPILE
        Project.registerTemplateContract(target)
        Project.completeTemplateDeclarations()
    }

    private fun assertSlot(target: DataTemplate, origin: DataTemplate): MCInt {
        val field = assertIs<MCInt>(target.scope.getVar("item"))
        val property = assertNotNull(target.scope.getProperty("item"))
        assertEquals(MCFPPBaseType.Int.typeId, field.type.typeId)
        assertSame(origin, field.declaredParentTemplate)
        assertSame(origin, target.scope.fieldOrigins.getValue("item").single().owner)
        assertTrue(field.isDataOnly)
        assertEquals(AccessModifier.PROTECTED, field.accessModifier)
        assertSame(origin, property.declaredParentTemplate)
        assertTrue(property.isInherited)
        assertEquals(AccessModifier.PROTECTED, property.accessModifier)
        assertTrue(target.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
        return field
    }

    @Test fun repeatedDirectAndNamespaceLayoutWaitForCanonicalParentsWithoutConsumingEffects() = isolated { namespace ->
        val root = root(namespace)
        val child = declaration(namespace, "Child")
        child.deferParentTypes(listOf(root.inheritanceIdentity))
        restoreEffect(child, root.inheritanceIdentity)

        repeat(3) {
            assertSame(child, child.flatExtends())
            namespace.resolve()
            assertUnlaid(child)
            assertTrue(child.parent.isEmpty())
            assertEquals(listOf(root.inheritanceIdentity), child.pendingParentTypeIds)
        }

        complete(child)
        assertSame(root, child.parent.single())
        assertTrue(child.pendingParentTypeIds.isEmpty())
        val field = assertSlot(child, root)
        val property = child.scope.getProperty("item")!!
        // An already-applied saved effect must not overwrite a later declaration change.
        field.isDataOnly = false
        property.accessModifier = AccessModifier.PUBLIC
        repeat(3) {
            child.flatExtends()
            namespace.resolve()
            Project.completeTemplateDeclarations()
            assertSame(field, child.scope.getVar("item"))
            assertSame(property, child.scope.getProperty("item"))
            assertFalse(field.isDataOnly)
            assertEquals(AccessModifier.PUBLIC, property.accessModifier)
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun aKnownDirectParentWithPendingAncestorsDoesNotCacheAnEmptyChildLayout() = isolated { namespace ->
        val root = root(namespace)
        val middle = declaration(namespace, "Middle")
        middle.deferParentTypes(listOf(root.inheritanceIdentity))
        restoreEffect(middle, root.inheritanceIdentity)
        val child = declaration(namespace, "Child").apply { extends(middle) }
        restoreEffect(child, root.inheritanceIdentity)

        repeat(3) {
            child.flatExtends()
            namespace.resolve()
            assertUnlaid(middle)
            assertUnlaid(child)
            assertSame(middle, child.parent.single())
        }

        complete(child)
        assertSame(root, middle.parent.single())
        assertSame(middle, child.parent.single())
        assertSlot(middle, root)
        val field = assertSlot(child, root)
        repeat(2) {
            child.flatExtends()
            assertSame(field, child.scope.getVar("item"))
        }
    }

    @Test fun deepAndProjectCheckpointsRestorePendingLayoutAndAllowARealCompletionRetry() = isolated { namespace ->
        val root = root(namespace)
        val child = declaration(namespace, "Child")
        child.deferParentTypes(listOf(root.inheritanceIdentity))
        restoreEffect(child, root.inheritanceIdentity)
        val before = DeclarationStateJournal.checkpoint(listOf(child, root))
        val projectBefore = ProjectStateJournal.checkpoint()
        child.flatExtends()
        assertUnlaid(child)
        complete(child)
        val firstField = assertSlot(child, root)
        val firstProperty = child.scope.getProperty("item")!!
        val after = before.captureCurrent()
        val projectAfter = projectBefore.captureCurrent()

        repeat(2) {
            before.restore()
            projectBefore.restore()
            assertEquals(Project.CompileStage.READ_LIB, Project.compileStage)
            repeat(2) {
                namespace.resolve()
                child.flatExtends()
                assertUnlaid(child)
            }
            assertEquals(listOf(root.inheritanceIdentity), child.pendingParentTypeIds)
            after.restore()
            projectAfter.restore()
            assertSame(firstField, assertSlot(child, root))
            assertSame(firstProperty, child.scope.getProperty("item"))
            assertSame(root, child.parent.single())
        }

        before.restore()
        projectBefore.restore()
        complete(child)
        assertNotSame(firstField, assertSlot(child, root))
        assertNotSame(firstProperty, child.scope.getProperty("item"))
        assertSame(root, child.parent.single())
    }

    @Test fun aRealRestoredOriginMismatchIsStillRejectedAfterThePendingBoundary() = isolated { namespace ->
        val root = root(namespace)
        val child = declaration(namespace, "Child")
        child.deferParentTypes(listOf(root.inheritanceIdentity))
        restoreEffect(child, TypeId.Declaration("template", namespace.identifier, "DifferentOrigin"))
        repeat(2) {
            child.flatExtends()
            namespace.resolve()
            assertUnlaid(child)
        }

        complete(child)
        assertSame(root, child.parent.single())
        assertSame(root, child.scope.fieldOrigins.getValue("item").single().owner)
        assertFalse(assertIs<MCInt>(child.scope.getVar("item")).isDataOnly)
        assertTrue(Project.errorCount > 0)
        assertFalse(child.hasCompletedDeclaration)
        assertTrue(child.hasDeclarationCompletionFailure)
    }
}
