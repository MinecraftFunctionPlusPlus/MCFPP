package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.UnionDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import kotlin.test.*
import kotlin.test.Test

/** Direct model tests: Project.init only, with no readProject or bundled library dependency. */
class MniAnonymousParentIdentityLookupTest {
    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val currentFunction = Function.currFunction
        val forced = Function.forcedField
        val currentTemplate = DataTemplate.currTemplate
        val currentFile = MCFPPFile.currFile
        try {
            Project.init()
            Project.compileStage = Project.CompileStage.READ_LIB
            MCFPPFile.currFile = null
            DataTemplate.currTemplate = null
            action()
        } finally {
            namespaces.restore()
            project.restore()
            Function.currFunction = currentFunction
            Function.forcedField = forced
            DataTemplate.currTemplate = currentTemplate
            MCFPPFile.currFile = currentFile
        }
    }

    private fun template(name: String, namespace: String = "lookup.parents") = DataTemplate(name, namespace).apply { declarationFile = null }

    private fun namespace(identifier: String) = Namespace(identifier).also {
        GlobalScope.localNamespaces[identifier] = it
    }

    private fun lookup(template: DataTemplate) = assertIs<MCFPPDataTemplateType>(MCFPPType.resolveTypeId(template.inheritanceIdentity)).template

    private fun field(owner: DataTemplate, name: String) = MCInt(name).apply {
        declaredParentTemplate = owner
        assertTrue(owner.addMember(this))
    }

    @Test fun anonymousIntersectionIsARealCanonicalParentWithInheritedMembers() = isolated {
        val a = template("A")
        val b = template("B")
        field(a, "x")
        field(b, "y")
        Project.registerAnonymousTemplate(a)
        Project.registerAnonymousTemplate(b)
        val intersection = UnionDataTemplate.resolve(listOf(a, b))
        val child = template("Child")
        child.deferParentTypes(listOf(intersection.inheritanceIdentity))
        Project.registerAnonymousTemplate(child)
        val indexedScopes = (
            GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values +
                GlobalScope.stdNamespaces.values
            ).map { it.scope }
        for (candidate in listOf(a, b, intersection, child)) {
            fun aliases(candidateType: MCFPPType): Boolean = when (candidateType) {
                is MCFPPDataTemplateType -> candidateType.template === candidate
                else -> false
            }
            assertFalse(
                indexedScopes.any { scope ->
                    scope.template.values.any { it === candidate } ||
                        scope.objects.any { it === candidate } ||
                        scope.cachedAliasTargets().any(::aliases)
                },
                "${candidate.inheritanceIdentity} must be resolved only through the anonymous registry"
            )
        }
        assertSame(intersection, lookup(intersection))
        assertTrue(child.parent.isEmpty(), "READ_LIB must leave typed parents pending")

        Project.compileStage = Project.CompileStage.COMPILE
        Project.completeTemplateDeclarations()
        assertSame(intersection, child.parent.single())
        assertSame(intersection, lookup(intersection))
        assertSame(intersection, UnionDataTemplate.resolve(listOf(a, b)))
        assertSame(a, child.scope.getVar("x")!!.declaredParentTemplate)
        assertSame(b, child.scope.getVar("y")!!.declaredParentTemplate)
        assertEquals(listOf(a.inheritanceIdentity, b.inheritanceIdentity), intersection.parent.map { it.inheritanceIdentity })
        assertTrue(child.pendingParentTypeIds.isEmpty())
        assertTrue(child.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
    }

    @Test fun fullIdentityDistinguishesNamespaceKindAndMissingDeclarations() = isolated {
        val first = template("Shared", "lookup.first")
        val second = template("Shared", "lookup.second")
        Project.registerAnonymousTemplate(first)
        Project.registerAnonymousTemplate(second)
        assertSame(first, lookup(first))
        assertSame(second, lookup(second))
        assertNull(MCFPPType.resolveTypeId(TypeId.Declaration("template", "lookup.absent", "Shared")))
        assertNull(MCFPPType.resolveTypeId(TypeId.Declaration("object", "lookup.first", "Shared")))
        assertEquals(0, Project.errorCount)
    }

    @Test fun synthesizedNamesAreOpaqueAndAreNeverDecodedOrGuessed() = isolated {
        val opaque = template("0intersection_not_an_encoded_parent_list", "mcfpp.intersection")
        assertNull(MCFPPType.resolveTypeId(opaque.inheritanceIdentity))
        Project.registerAnonymousTemplate(opaque)
        assertSame(opaque, lookup(opaque))
        assertNull(MCFPPType.resolveTypeId(TypeId.Declaration("template", opaque.namespace, opaque.identifier + "0")))
        assertEquals(0, Project.errorCount)
    }

    @Test fun registeredObjectsAndAbstractDataUseTheCompleteDeclarationKind() = isolated {
        val ordinary = template("Shared")
        val contract = template("Contract").apply { isAbstract = true }
        val objectTemplate = ObjectDataTemplate("Shared", ordinary.namespace).apply { declarationFile = null }
        listOf(ordinary, contract, objectTemplate).forEach(Project::registerAnonymousTemplate)
        assertSame(ordinary, lookup(ordinary))
        assertSame(contract, lookup(contract))
        assertSame(objectTemplate, lookup(objectTemplate))
        assertEquals(
            setOf("template", "object"),
            listOf(ordinary, contract, objectTemplate).map { (it.inheritanceIdentity as TypeId.Declaration).kind }.toSet()
        )
        assertEquals(0, Project.errorCount)
    }

    @Test fun indexedAliasedAndRegisteredReferencesMustShareOneCanonicalModel() = isolated {
        val owner = template("Shared")
        val scope = namespace(owner.namespace).scope
        scope.template[owner.identifier] = owner
        assertTrue(scope.putResolvedAlias("Alias", owner.getType()))
        Project.registerAnonymousTemplate(owner)
        repeat(3) { assertSame(owner, lookup(owner)) }
        assertEquals(0, Project.errorCount)
    }

    @Test fun conflictingIndexedAndAnonymousModelsAreRejected() = isolated {
        val registered = template("Shared")
        val indexed = template("Shared")
        namespace(indexed.namespace).scope.template[indexed.identifier] = indexed
        Project.registerAnonymousTemplate(registered)
        assertNull(MCFPPType.resolveTypeId(registered.inheritanceIdentity))
        assertEquals(1, Project.errorCount)
        assertSame(registered, Project.findRegisteredAnonymousTemplate(registered.inheritanceIdentity))
    }

    @Test fun conflictingAliasAndAnonymousModelsAreRejected() = isolated {
        val registered = template("Shared")
        val aliased = template("Shared")
        assertTrue(namespace(aliased.namespace).scope.putResolvedAlias("Alias", aliased.getType()))
        Project.registerAnonymousTemplate(registered)
        assertNull(MCFPPType.resolveTypeId(registered.inheritanceIdentity))
        assertEquals(1, Project.errorCount)
    }

    @Test fun conflictingNamedAndAliasedModelsInOneScopeAreRejected() = isolated {
        val named = template("Shared")
        val aliased = template("Shared")
        val scope = namespace(named.namespace).scope
        scope.template[named.identifier] = named
        assertTrue(scope.putResolvedAlias("Alias", aliased.getType()))
        assertNull(MCFPPType.resolveTypeId(named.inheritanceIdentity))
        assertEquals(1, Project.errorCount)
    }

    @Test fun conflictingAliasesInOneScopeAreRejected() = isolated {
        val first = template("Shared")
        val second = template("Shared")
        val scope = namespace(first.namespace).scope
        assertTrue(scope.putResolvedAlias("FirstAlias", first.getType()))
        assertTrue(scope.putResolvedAlias("SecondAlias", second.getType()))
        assertNull(MCFPPType.resolveTypeId(first.inheritanceIdentity))
        assertEquals(1, Project.errorCount)
    }

    @Test fun matchingIdentityAcrossScopesPreservesLocalLibraryAndStandardPriorities() = isolated {
        val local = template("Shared")
        val library = template("Shared")
        val standard = template("Shared")
        namespace(local.namespace).scope.template[local.identifier] = local
        GlobalScope.libNamespaces[library.namespace] = Namespace(library.namespace).apply {
            scope.template[library.identifier] = library
        }
        GlobalScope.stdNamespaces[standard.namespace] = Namespace(standard.namespace).apply {
            scope.template[standard.identifier] = standard
        }
        assertSame(local, lookup(local))
        assertEquals(0, Project.errorCount)

        GlobalScope.localNamespaces.remove(local.namespace)
        assertSame(library, lookup(library))
        assertEquals(0, Project.errorCount)

        GlobalScope.libNamespaces.remove(library.namespace)
        assertSame(standard, lookup(standard))
        assertEquals(0, Project.errorCount)
    }

    @Test fun indexedSlotWithADifferentIdentityDoesNotSatisfyTheRequestedParent() = isolated {
        val wrong = template("Shared", "lookup.other")
        namespace("lookup.expected").scope.template["Shared"] = wrong
        assertNull(MCFPPType.resolveTypeId(TypeId.Declaration("template", "lookup.expected", "Shared")))
        assertEquals(0, Project.errorCount)
    }

    @Test fun readLibLookupDoesNotCompileGenericBodiesOrApplyPendingAnnotations() = isolated {
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("{}"))))
        val prototype = GenericDataTemplate(parser.templateBody(), "Box", "lookup.generic").apply {
            declarationFile = null
        }
        val probe = LookupProbe()
        prototype.annotations.add(probe)
        prototype.pendingDeclarationAnnotations.add(probe)
        namespace(prototype.namespace).scope.template[prototype.identifier] = prototype
        Project.registerAnonymousTemplate(prototype)
        repeat(3) { assertSame(prototype, lookup(prototype)) }
        val specialized = TypeId.Specialized(prototype.inheritanceIdentity, listOf(CompilerValue.Integral(7)))
        assertNull(MCFPPType.resolveTypeId(specialized), "Existing specialization resolution must remain deferred in READ_LIB")
        assertEquals(0, prototype.index)
        assertTrue(prototype.compiledTemplates.isEmpty())
        assertFalse(prototype.hasCompletedDeclaration)
        assertEquals(1, prototype.pendingDeclarationAnnotations.size)
        assertSame(probe, prototype.pendingDeclarationAnnotations.single())
        assertEquals(0, probe.applications)
        assertEquals(0, Project.errorCount)
    }

    class LookupProbe : Annotation("LookupProbe", "lookup.generic") {
        var applications = 0
        override fun forDataTemplate(data: DataTemplate) {
            applications++
        }
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
    }
}
