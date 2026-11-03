package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.TypeId
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class MniAnonymousTemplateRegistryTest {
    private inline fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        try {
            Project.init()
            action()
        } finally {
            namespaces.restore()
            project.restore()
        }
    }

    private fun anonymous(name: String, namespace: String) = DataTemplate(name, namespace).apply { declarationFile = null }

    @Test
    fun lookupRequiresTheCompleteTypeIdentityAndReturnsTheRegisteredObject() = isolated {
        val first = anonymous("Shared", "registry.first")
        val otherNamespace = anonymous("Shared", "registry.second")
        assertNull(Project.findRegisteredAnonymousTemplate(first.inheritanceIdentity))
        Project.registerAnonymousTemplate(first)
        Project.registerAnonymousTemplate(otherNamespace)

        assertSame(first, Project.findRegisteredAnonymousTemplate(first.inheritanceIdentity))
        assertSame(otherNamespace, Project.findRegisteredAnonymousTemplate(otherNamespace.inheritanceIdentity))
        assertNull(
            Project.findRegisteredAnonymousTemplate(
                TypeId.Declaration("template", "registry.missing", "Shared")
            )
        )
    }

    @Test
    fun lookupReflectsProjectInitializationAndJournalRestoration() = isolated {
        val before = ProjectStateJournal.checkpoint()
        val template = anonymous("Journaled", "registry.journal")
        Project.registerAnonymousTemplate(template)
        val after = before.captureCurrent()
        val identity = template.inheritanceIdentity

        assertSame(template, Project.findRegisteredAnonymousTemplate(identity))
        before.restore()
        assertNull(Project.findRegisteredAnonymousTemplate(identity))
        after.restore()
        assertSame(template, Project.findRegisteredAnonymousTemplate(identity))

        Project.init()
        assertNull(Project.findRegisteredAnonymousTemplate(identity))
    }
}
