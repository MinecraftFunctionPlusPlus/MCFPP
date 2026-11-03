package top.mcfpp.test.library

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.library.compiler.LibraryDeclarationIdentity
import top.mcfpp.library.compiler.LibraryIdentities
import top.mcfpp.library.compiler.LibraryIdentity
import top.mcfpp.library.syntax.DeclarationId
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.model.Member
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import kotlin.test.*

class LibraryNominalIdentityTest {
    private fun shells(count: Int = 1): Pair<List<top.mcfpp.library.compiler.DeclarationShell<CompoundData>>,
        List<top.mcfpp.library.compiler.DeclarationShell<CompoundData>>> {
        val identities = LibraryIdentities<CompoundData>()
        val declarations = (1..count).map { LibraryDeclarationIdentity(DeclarationId(it.toLong()), privatePath = listOf("private", "$it")) }
        return identities.register(LibraryIdentity(PackageKey("fixture:left", "1"), "main"), emptyList(), declarations) to
            identities.register(LibraryIdentity(PackageKey("fixture:right", "1"), "main"), emptyList(), declarations)
    }

    @Test
    fun sameNamedPrivateModelsHaveDistinctActualTypesAndSameShellCopiesKeepIdentity() {
        Project.init()
        val (left, right) = shells(3)
        val first = listOf<CompoundData>(DataTemplate("Hidden", "shared"), ObjectDataTemplate("Singleton", "shared"), Enum("State", "shared"))
        val second = listOf<CompoundData>(DataTemplate("Hidden", "shared"), ObjectDataTemplate("Singleton", "shared"), Enum("State", "shared"))
        first.indices.forEach { index ->
            left[index].bind(first[index])
            right[index].bind(second[index])
            assertEquals(left[index].sessionIdentity, first[index].getType().typeId)
            assertEquals(right[index].sessionIdentity, second[index].getType().typeId)
            assertNotEquals(first[index].getType(), second[index].getType())
            assertNotEquals(first[index], second[index])
            assertEquals(first[index].identifier, second[index].identifier)
            assertEquals(first[index].namespace, second[index].namespace)
            assertEquals(first[index].javaClass, second[index].javaClass)
            assertEquals(Member.AccessModifier.PUBLIC, first[index].getAccess(second[index]))
        }
        val copied = DataTemplate("Hidden", "shared").apply { copySessionIdentityFrom(first[0]) }
        assertEquals(first[0].getType(), copied.getType())
        assertEquals(first[0], copied)
        assertEquals(first[0].hashCode(), copied.hashCode())
        assertEquals(Member.AccessModifier.PRIVATE, first[0].getAccess(copied))
        assertFailsWith<IllegalStateException> { right[0].bind(first[0]) }
        assertFailsWith<IllegalStateException> { first[0].bindSessionIdentity(right[0].sessionIdentity) }
        assertEquals(left[0].sessionIdentity, first[0].getType().typeId)
        assertEquals(DataTemplate::class.java, copied.javaClass)
        assertEquals(TypeId.Declaration("template", "source", "Named"), DataTemplate("Named", "source").getType().typeId)
        assertEquals(TypeId.Declaration("object", "source", "Named"), ObjectDataTemplate("Named", "source").getType().typeId)
        assertEquals(TypeId.Declaration("enum", "source", "Named"), Enum("Named", "source").getType().typeId)
    }

    @Test
    fun inheritancePermissionsAndPreparationJournalUseTheBoundDeclaration() {
        Project.init()
        val (left, right) = shells(2)
        val parent = DataTemplate("Hidden", "shared").also(left[0]::bind)
        val unrelated = DataTemplate("Hidden", "shared").also(right[0]::bind)
        val child = DataTemplate("Child", "shared").also(left[1]::bind)
        child.extends(parent)
        assertTrue(child.isSubOf(parent))
        assertFalse(child.isSubOf(unrelated))
        assertFalse(parent.isSubOf(unrelated))
        assertEquals(Member.AccessModifier.PROTECTED, child.getAccess(parent))
        assertEquals(Member.AccessModifier.PUBLIC, child.getAccess(unrelated))
        assertTrue(TypeRelations.isSubtype(child.getType(), parent.getType()))
        assertFalse(TypeRelations.isSubtype(child.getType(), unrelated.getType()))
        val temporary = DataTemplate("Temporary", "source")
        val checkpoint = temporary.checkpointDeclarationState()
        val before = temporary.preparationFacts()
        temporary.bindSessionIdentity(TypeId.fresh() as TypeId.Opaque)
        assertNotEquals(before, temporary.preparationFacts())
        checkpoint.restore()
        assertNull(temporary.sessionIdentity)
        assertEquals(before, temporary.preparationFacts())
        assertEquals(TypeId.Declaration("template", "source", "Temporary"), temporary.getType().typeId)
        val published = DataTemplate("Published", "source")
        GlobalScope.getOrCreateNamespace("source").scope.addTemplate("Published", published)
        assertFailsWith<IllegalStateException> { published.bindSessionIdentity(TypeId.fresh() as TypeId.Opaque) }
        assertFailsWith<IllegalStateException> { DataTemplate.baseDataTemplate.bindSessionIdentity(TypeId.fresh() as TypeId.Opaque) }
    }

    @Test
    fun readonlySpecializationsKeepActualOriginAndArgumentsWithoutGeneratedNameIdentity() {
        Project.init()
        val (left, right) = shells(2)
        val first = GenericDataTemplate(P.TemplateBodyContext(null, -1), "Box", "shared").also(left[0]::bind)
        val second = GenericDataTemplate(P.TemplateBodyContext(null, -1), "Box", "shared").also(right[0]::bind)
        val arguments = listOf(CompilerValue.Integral(7))
        val one = CompiledGenericDataTemplate("Box-0", "shared", first, emptyList(), arguments)
        val same = CompiledGenericDataTemplate("Box-copy", "shared", first, emptyList(), arguments)
        val other = CompiledGenericDataTemplate("Box-0", "shared", second, emptyList(), arguments)
        val changed = CompiledGenericDataTemplate("Box-0", "shared", first, emptyList(), listOf(CompilerValue.Integral(8)))
        val expected = TypeId.Specialized(left[0].sessionIdentity, arguments)
        assertEquals(expected, one.nominalIdentity)
        assertEquals(expected, one.getType().typeId)
        assertEquals(one.getType(), same.getType())
        assertEquals(one, same)
        assertEquals(one.hashCode(), same.hashCode())
        assertNotEquals(one.getType(), other.getType())
        assertNotEquals(one.getType(), changed.getType())
        assertFalse(one.isSubOf(other))
        assertFailsWith<IllegalStateException> { one.bindSessionIdentity(left[0].sessionIdentity) }
        val objectOrigin = GenericObjectDataTemplate(P.TemplateBodyContext(null, -1), "Cache", "shared").also(left[1]::bind)
        val objectInstance = CompiledGenericObjectDataTemplate("Cache-0", "shared", objectOrigin, emptyList(), arguments)
        assertEquals(TypeId.Specialized(left[1].sessionIdentity, arguments), objectInstance.getType().typeId)
        val source = GenericDataTemplate(P.TemplateBodyContext(null, -1), "Box", "source")
        val sourceInstance = CompiledGenericDataTemplate("source-instance", "source", source, emptyList(), arguments)
        assertEquals(TypeId.Declaration("template", "source", "Box"), (sourceInstance.getType().typeId as TypeId.Specialized).constructor)
    }
}
