package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.LibraryMetadata
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.UnionDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.library.AnnotationParentFixturesTest
import top.mcfpp.library.api.LibraryExport
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

/** Uses compiler builtins and actual bin.mclib files, never the bundled standard library. */
class DataTemplateParentIdentityMetadataTest {
    private val namespace = "fixture.parentmetadata"
    private lateinit var exported: LibraryModel
    private var imported: LibraryImportSession? = null

    private fun isolated(action: (Path) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val config = Project.config
        val settings = listOf(CompileSettings.ignoreStdLib, CompileSettings.isDebug, CompileSettings.isLib,
            CompileSettings.printAll, CompileSettings.foldIRConstants)
        val maxWhile = CompileSettings.maxWhileInline
        val caller = Function.currFunction
        val forced = Function.forcedField
        val file = MCFPPFile.currFile
        val template = DataTemplate.currTemplate
        val output = Files.createTempDirectory("mcfpp-parent-identity-")
        try {
            Project.config = ProjectConfig(version = "26.3")
            CompileSettings.ignoreStdLib = true
            Project.init()
            Project.compileStage = Project.CompileStage.COMPILE
            MCFPPFile.currFile = null
            action(output)
        } finally {
            imported?.close(); imported = null
            base.restore(); namespaces.restore(); project.restore()
            Project.config = config
            CompileSettings.ignoreStdLib = settings[0]; CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]; CompileSettings.maxWhileInline = maxWhile
            Function.currFunction = caller; Function.forcedField = forced
            MCFPPFile.currFile = file; DataTemplate.currTemplate = template
            output.toFile().deleteRecursively()
        }
    }

    private fun ordinary(name: String) = DataTemplate(name, namespace).apply { declarationFile = null }
    private fun publish(template: DataTemplate) {
        GlobalScope.localNamespaces.getOrPut(template.namespace) { Namespace(template.namespace) }
            .scope.template[template.identifier] = template
    }
    private fun local(name: String) = GlobalScope.localNamespaces.getValue(namespace).scope.getTemplate(name)!!
    private fun loaded(name: String) = GlobalScope.libNamespaces.getValue(namespace).scope.getTemplate(name)!!
    private fun field(owner: DataTemplate, name: String) = MCInt(name).apply {
        declaredParentTemplate = owner
        assertTrue(owner.addMember(this))
    }
    private fun source(text: String) {
        Project.config.includes = arrayListOf()
        MCFPPStringTest.readFromString("namespace $namespace\n${text.trimIndent()}\nfunc main(){}", version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue(namespace).scope.functions.getValue("main").single()
        val file = assertNotNull(main.declarationFile)
        var tree: org.antlr.v4.runtime.tree.ParseTree = assertNotNull(main.ast)
        while (tree.parent != null) tree = tree.parent
        Project.trees[file] = tree
    }
    private fun write(output: Path) {
        AnnotationParentFixturesTest.registry().use { codecs ->
            val selection = LibraryExporter.snapshotCurrentProject(
                LibraryIdentity(PackageKey("fixture:parent-metadata", "1"), "mcfpp"), LibraryTarget("26.3"))
            exported = LibraryExport.write(selection.copy(services = ExportSourceServices.create(selection,
                AnnotationParentFixturesTest.managed, codecs, { AnnotationParentFixturesTest.providerPackage },
                { true })), output.resolve("bin.mclib")).model
        }
        assertTrue(Files.size(output.resolve("bin.mclib")) > 8)
    }
    private fun read(output: Path) {
        LibraryMetadata.reset()
        Project.init()
        Project.compileStage = Project.CompileStage.READ_LIB
        val session = LibraryImportSession()
        imported = session
        session.configureNative(AnnotationParentFixturesTest.managed, AnnotationParentFixturesTest.registry())
        session.stage(output.resolve("bin.mclib"))
        session.bindAll(); session.commit()
        Project.libraryImportSession = session
        assertEquals(0, Project.errorCount)
    }
    private fun compound(name: String) = exported.declarations.declarations.filterIsInstance<CompoundDeclaration>()
        .single { it.common.name == name }
    private fun complete() {
        Project.compileStage = Project.CompileStage.COMPILE
        Project.resolveImportedTemplateParents()
        Project.completeTemplateDeclarations()
        assertEquals(0, Project.errorCount)
    }

    @Test fun closedParentIdentitiesKeepEveryTypedPayloadAndOrderInARealBinary() = isolated { output ->
        val origin = TypeId.Declaration("template", "parent.origin", "Box")
        val arguments = listOf<CompilerValue>(
            CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)),
            CompilerValue.Typed(MCFPPBaseType.Bool.typeId, CompilerValue.Bool(true)),
            CompilerValue.TypeValue(MCFPPBaseType.Int.typeId),
            CompilerValue.TypeValue(MCFPPBaseType.Bool.typeId),
            CompilerValue.FloatBits((-0.0f).toRawBits()),
            CompilerValue.DoubleBits(Double.NaN.toRawBits()),
            CompilerValue.Text("Box_type-0:<int>,\\\""),
            CompilerValue.Sequence(listOf(CompilerValue.Integral(3), CompilerValue.Bool(false))),
            CompilerValue.Record(mapOf("value" to CompilerValue.TypeValue(MCFPPBaseType.Int.typeId)))
        )
        val ids = arguments.reversed().map { TypeId.Specialized(origin, listOf(it)) }
        val child = ordinary("Pending")
        publish(child)
        child.deferParentTypes(ids)
        write(output)
        val parentRecords = compound("Pending").parents
        assertEquals(ids.size, parentRecords.size)
        parentRecords.forEach { parent ->
            assertNull(parent.expression)
            val specialized = assertIs<LibraryType.Specialized>(exported.tables.type(assertNotNull(parent.type)))
            assertEquals(LibraryType.UnresolvedNominal("template", "parent.origin", "Box"), exported.tables.type(specialized.constructor))
        }
        assertEquals(listOf("Pending"), exported.declarations.declarations.filterIsInstance<CompoundDeclaration>().map { it.common.name },
            "An unresolved nominal parent identity must not fabricate a declaration")
        read(output)
        val restored = loaded("Pending")
        assertNotSame(child, restored)
        assertEquals(ids, restored.pendingParentTypeIds)
        assertEquals(arguments.reversed(), restored.pendingParentTypeIds.map { assertIs<TypeId.Specialized>(it).arguments.single() })
        assertTrue(restored.parent.isEmpty())
        assertFalse(restored.hasCompletedDeclaration)
        assertTrue(restored.pendingParentTypeIds.all { assertIs<TypeId.Specialized>(it).constructor == origin })
    }

    @Test fun unindexedOrdinaryParentDefinitionsRestoreActualMembersAndCanonicalOrder() = isolated { output ->
        val a = ordinary("A"); val b = ordinary("B")
        field(a, "x"); field(b, "y")
        val child = ordinary("Child").apply { extends(b); extends(a) }
        publish(child)
        Project.registerTemplateContract(child)
        Project.completeTemplateDeclarations()
        assertEquals(0, Project.errorCount)
        write(output)
        val record = compound("Child")
        assertEquals(2, record.parents.size)
        assertTrue(record.parents.all { it.type != null && it.expression == null })
        assertTrue(record.members.map(exported.declarations::declaration).none { it is ValueDeclaration && it.role == ValueRole.MEMBER },
            "Inherited declarations must not become local metadata")
        read(output)
        val restored = loaded("Child")
        assertTrue(restored.parent.isEmpty())
        assertFalse(restored.hasCompletedDeclaration)
        assertEquals(2, restored.pendingParentTypeIds.size)
        assertEquals(setOf("A", "B", "Child"), exported.declarations.declarations.filterIsInstance<CompoundDeclaration>().map { it.common.name }.toSet())
        complete()
        val (restoredB, restoredA) = restored.parent.map { assertIs<DataTemplate>(it) }
        assertEquals(listOf("B", "A"), listOf(restoredB.identifier, restoredA.identifier))
        assertNotSame(a, restoredA); assertNotSame(b, restoredB)
        assertSame(restoredA, restoredA.scope.getVar("x")!!.declaredParentTemplate)
        assertEquals(listOf(restoredB, restoredA), restored.parent.toList())
        assertSame(restoredB, restored.parent.first())
        assertSame(restoredA, restored.parent.last())
        assertSame(restoredA, restored.scope.getVar("x")!!.declaredParentTemplate)
        assertSame(restoredB, restored.scope.getVar("y")!!.declaredParentTemplate)
        assertTrue(restored.hasCompletedDeclaration)
    }

    @Test fun nestedUnindexedIntersectionParentKeepsItsWholeDeclarationClosure() = isolated { output ->
        val a = ordinary("A"); val b = ordinary("B")
        field(a, "x"); field(b, "y")
        val intersection = UnionDataTemplate.resolve(listOf(a, b))
        val middle = ordinary("Middle").apply { extends(intersection) }
        val child = ordinary("Child").apply { extends(middle) }
        publish(child)
        Project.registerTemplateContract(child)
        Project.completeTemplateDeclarations()
        assertEquals(0, Project.errorCount)
        write(output)
        read(output)
        val restored = loaded("Child")
        assertTrue(restored.parent.isEmpty())
        assertEquals(1, restored.pendingParentTypeIds.size)
        assertTrue(exported.declarations.declarations.filterIsInstance<CompoundDeclaration>().map { it.common.name }.containsAll(listOf("Child", "Middle", "A", "B")))
        assertFalse(restored.hasCompletedDeclaration)
        complete()
        val restoredMiddle = assertIs<DataTemplate>(restored.parent.single())
        val restoredIntersection = assertIs<DataTemplate>(restoredMiddle.parent.single())
        val (restoredA, restoredB) = restoredIntersection.parent.map { assertIs<DataTemplate>(it) }
        assertEquals(listOf("A", "B"), listOf(restoredA.identifier, restoredB.identifier))
        assertSame(restoredMiddle, restored.parent.single())
        assertSame(restoredIntersection, restoredMiddle.parent.single())
        assertSame(restoredIntersection, UnionDataTemplate.resolve(listOf(restoredA, restoredB)))
        assertNotSame(intersection, restoredIntersection)
        assertSame(restoredA, restored.scope.getVar("x")!!.declaredParentTemplate)
        assertSame(restoredB, restored.scope.getVar("y")!!.declaredParentTemplate)
        assertTrue(restored.hasCompletedDeclaration)
    }

    @Test fun embeddedGenericIntersectionsUseTheLoadedOriginAndDistinctCanonicalBindings() = isolated { output ->
        source("""
            data Box<T>{var x:T;}
            data Extra{var y:int;}
            data Holder{var ints:(Box<int>&Extra);var bools:(Box<bool>&Extra);}
        """)
        val oldHolder = local("Holder")
        val oldTypes = listOf("ints", "bools").map { assertIs<MCFPPDataTemplateType>(oldHolder.scope.getVar(it)!!.type) }
        write(output)
        read(output)
        val box = assertIs<GenericDataTemplate>(loaded("Box"))
        assertTrue(box.compiledTemplates.isEmpty(), "READ_LIB must not compile a specialized parent")
        val holder = loaded("Holder")
        val types = listOf("ints", "bools").map { assertIs<MCFPPDataTemplateType>(holder.scope.getVar(it)!!.type).also { type -> type.tryResolve() } }
        types.forEach { type ->
            assertEquals(2, type.template.pendingParentTypeIds.size)
            assertTrue(type.template.parent.isEmpty())
            assertFalse(type.template.hasCompletedDeclaration)
        }
        complete()
        for ((index, type) in types.withIndex()) {
            val argument = if (index == 0) MCFPPBaseType.Int else MCFPPBaseType.Bool
            val canonical = assertNotNull(box.compile(listOf(MCFPPTypeVar(argument))))
            assertSame(box, canonical.originTemplate)
            assertIs<TypeId.Specialized>(canonical.inheritanceIdentity)
            assertEquals(listOf(canonical.inheritanceIdentity, loaded("Extra").inheritanceIdentity), type.template.parent.map { it.inheritanceIdentity })
            assertSame(canonical, type.template.parent.first())
            assertSame(canonical, type.template.scope.getVar("x")!!.declaredParentTemplate)
            assertEquals(argument.typeId, type.template.scope.getVar("x")!!.type.typeId)
            assertSame(type.template, UnionDataTemplate.resolve(listOf(canonical, loaded("Extra"))))
            assertNotSame(oldTypes[index].template, type.template)
        }
        assertNotEquals(types[0].typeId, types[1].typeId)
        assertEquals(2, box.compiledTemplates.size)
        assertEquals(0, Project.errorCount)
    }

    @Test fun readonlyValueParentsKeepTheirBindingOrderAndApplyAnnotationsOnlyOnce() = portableReadonlyParents { it }

    internal fun portableReadonlyParents(roundTrip: (ParentProbe) -> ParentProbe) = isolated { output ->
        source("data Box<N:int>{}")
        val prototype = assertIs<GenericDataTemplate>(local("Box"))
        prototype.annotations.add(roundTrip(ParentProbe()))
        val first = assertNotNull(prototype.compile(listOf(MCInt(3))))
        val second = assertNotNull(prototype.compile(listOf(MCInt(7))))
        val child = ordinary("Child").apply { extends(second); extends(first) }
        publish(child)
        Project.registerTemplateContract(child)
        write(output)
        val parents = compound("Child").parents.map { assertIs<LibraryType.Specialized>(exported.tables.type(assertNotNull(it.type))) }
        assertEquals(listOf(7L, 3L), parents.map { assertIs<ConstantValue.Typed>(exported.tables.constant(it.arguments.single())).value }
            .map { assertIs<ConstantValue.Integral>(exported.tables.constant(it)).value })
        assertEquals(1, exported.declarations.declarations.filterIsInstance<CompoundDeclaration>().count { it.common.name == "Box" },
            "Specialized parents must reference their prototype declaration")
        read(output)
        val restoredPrototype = assertIs<GenericDataTemplate>(loaded("Box"))
        assertTrue(restoredPrototype.compiledTemplates.isEmpty())
        val restored = loaded("Child")
        assertEquals(2, restored.pendingParentTypeIds.size)
        assertTrue(restored.parent.isEmpty())
        assertFalse(restored.hasCompletedDeclaration)
        complete()
        val loadedArguments = restored.parent.map { assertIs<TypeId.Specialized>(it.inheritanceIdentity).arguments.single() }
        assertEquals(listOf(second.inheritanceIdentity, first.inheritanceIdentity).map { assertIs<TypeId.Specialized>(it).arguments },
            loadedArguments.map { listOf(it) })
        restored.parent.forEachIndexed { index, parent ->
            val canonical = assertIs<CompiledGenericDataTemplate>(parent)
            assertSame(restoredPrototype, canonical.originTemplate)
            assertSame(canonical, restoredPrototype.compile(listOf(MCInt(if (index == 0) 7 else 3))))
            assertEquals(1, assertIs<ParentProbe>(canonical.annotations.single()).applications)
        }
        repeat(2) { Project.completeTemplateDeclarations() }
        assertEquals(2, restoredPrototype.index)
        restored.parent.forEach { assertEquals(1, assertIs<ParentProbe>(it.annotations.single()).applications) }
        assertEquals(0, Project.errorCount)
    }

    @Test fun sourceParentExpressionsKeepTheirOriginalDeclarationEnvironment() = isolated { output ->
        source("data Root{var x:int;}\ndata Child:Root{}")
        val child = local("Child")
        write(output)
        val record = compound("Child")
        val parent = record.parents.single()
        val parentType = assertIs<LibraryType.Declaration>(exported.tables.type(assertNotNull(parent.type)))
        val parentD = assertIs<DeclarationReference.Local>(parentType.target).target
        assertEquals("Root", exported.declarations.declaration(parentD).common.name)
        assertEquals(namespace, exported.declarations.environment(record.common.environment).namespace)
        assertNull(parent.expression, "A closed author binding is retained directly as a T/D edge")
        read(output)
        val restored = loaded("Child")
        assertEquals(1, restored.pendingParentTypeIds.size)
        assertTrue(restored.parent.isEmpty())
        assertEquals(namespace, assertNotNull(restored.declarationFile).namespace.identifier)
        assertFalse(restored.hasCompletedDeclaration)
        complete()
        assertSame(loaded("Root"), restored.parent.single())
        assertSame(loaded("Root"), restored.scope.getVar("x")!!.declaredParentTemplate)
    }

    @Test fun genericPrototypeAlsoRestoresItsUnindexedOrdinaryParentDefinition() = isolated { output ->
        source("data Box<T>{}")
        val prototype = assertIs<GenericDataTemplate>(local("Box"))
        val unindexed = ordinary("Unindexed")
        field(unindexed, "inherited")
        prototype.extends(unindexed)
        write(output)
        assertTrue(compound("Box").parents.any { it.type != null })
        assertEquals(1, exported.declarations.declarations.filterIsInstance<CompoundDeclaration>().count { it.common.name == "Unindexed" })
        read(output)
        val restored = assertIs<GenericDataTemplate>(loaded("Box"))
        assertEquals(1, restored.pendingParentTypeIds.size)
        assertTrue(restored.compiledTemplates.isEmpty())
        assertFalse(restored.hasCompletedDeclaration)
        complete()
        val canonicalParent = assertIs<DataTemplate>(restored.parent.last())
        assertEquals("Unindexed", canonicalParent.identifier)
        assertSame(canonicalParent, restored.parent.last())
        val specialized = assertNotNull(restored.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
        assertSame(canonicalParent, specialized.scope.getVar("inherited")!!.declaredParentTemplate)
        assertNotSame(unindexed, canonicalParent)
        assertEquals(0, Project.errorCount)
    }

    @Test fun ordinaryMetadataCannotSilentlyDowngradeACompiledGenericDeclaration() = isolated { _ ->
        source("data Box<T>{var x:T;}")
        val prototype = assertIs<GenericDataTemplate>(local("Box"))
        val specialized = assertNotNull(prototype.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
        val failure = assertFailsWith<IllegalArgumentException> { DataTemplateInfo.from(specialized) }
        assertTrue(failure.message!!.contains("specialized type identity"))
        assertEquals(0, Project.errorCount)
    }

    @Test fun previousAndFutureVersionsAreRejectedBeforeReadingAnyDeclarationPayload() = isolated { _ ->
        for (version in listOf(LibraryWire.CURRENT_VERSION - 1, LibraryWire.CURRENT_VERSION + 1)) {
            Project.init()
            val bytes = LibraryBinaryWriter().apply {
                rawBytes(byteArrayOf(0x4d, 0x43, 0x46, 0x50, 0x4c, 0x49, 0x42, 0))
                u32(version)
            }.toByteArray()
            // No declaration payload follows the header: attempting to read one would throw.
            LibraryImportSession().use { session ->
                val failure = assertFailsWith<LibraryFormatException> { session.stage(bytes) }
                assertEquals(if (version > LibraryWire.CURRENT_VERSION) LibraryFormatCode.NEWER else LibraryFormatCode.UNSUPPORTED, failure.code)
            }
            assertEquals(0, Project.errorCount, "The explicit import API reports rejection through its exception")
            assertTrue(GlobalScope.libNamespaces.isEmpty())
        }
    }

    class ParentProbe : Annotation("ParentProbe", "fixture.parentmetadata") {
        var applications = 0
        override fun forDataTemplate(data: DataTemplate) { applications++ }
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
    }
}
