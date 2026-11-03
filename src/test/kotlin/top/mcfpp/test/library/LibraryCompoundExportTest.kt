package top.mcfpp.test.library

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.api.LibraryExport
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.TypeId
import java.nio.file.Files
import kotlin.test.*

class LibraryCompoundExportTest {
    private val identity = LibraryIdentity(PackageKey("fixture:compound-export", "1"), "main")
    private val codec = LibraryModelCodec(listOf(CompoundCodec))

    @Test fun sourceCompoundsPreserveMembersInitializersAccessorsOperatorsParentsEnumsAndAliases() = source("""
        namespace compound.fixture
        typealias int as Count
        enum Mode {first=3,second=7}
        data Payload {var token:int=17;constructor(){}}
        data LocalOnly {var marker:int=6}
        func makePayload():Payload {var local:Payload=Payload();return local}
        func seedValue():int {var unused:LocalOnly;var local=makePayload();return local.token}
        data Parent {
            var base:int=1
            constructor(){}
            func baseValue():int{return this.base}
        }
        data Child:Parent {
            var value:int=seedValue() {
                get {return field+1}
                set {field=value}
            }
            constructor(seed:int){this.value=seed}
            func read():int{return this.value+this.base}
            operator +(other:int):int{return this.value+other}
            operator +(other:float):int{return 7}
        }
        object data Settings {var limit:int=9}
        func main(){}
    """.trimIndent()) { file ->
        val scope = file.namespace.scope
        val originalOperators = scope.template.getValue("Child").scope.operators
        val selection = OwnedLibrarySelection(identity, LibraryTarget("26.3"), emptyList(), listOf(file),
            templates = listOf(scope.template.getValue("Child"), scope.objects.filterIsInstance<DataTemplate>().single { it.identifier == "Settings" }),
            enums = listOf(scope.enums.single { it.identifier == "Mode" }), aliases = listOf(OwnedLibraryAlias("Count", file)))
        withFile(selection) { model ->
            val declarations = model.declarations.declarations
            val parent = declarations.filterIsInstance<CompoundDeclaration>().single { it.common.name == "Parent" }
            val child = declarations.filterIsInstance<CompoundDeclaration>().single { it.common.name == "Child" }
            assertTrue(parent.parents.isEmpty(), "The language's implicit DataObject parent is supplied by the complete importer")
            assertEquals(1, child.parents.size)
            val parentType = model.tables.type(assertNotNull(child.parents.single().type)) as LibraryType.Declaration
            assertEquals(parent, model.declarations.declaration((parentType.target as DeclarationReference.Local).target))
            val field = declarations.filterIsInstance<ValueDeclaration>().single { it.role == ValueRole.MEMBER && it.common.name == "value" }
            assertEquals(BodyRole.INITIALIZER, model.syntax.body(assertNotNull(field.defaultBody)).role)
            val initializer = model.syntax.node(model.syntax.body(assertNotNull(field.defaultBody)).root) as Expression.Call
            assertEquals("seedValue", ((initializer.callee as Expression.Name).use as NameUse.Lookup).name)
            assertTrue(declarations.filterIsInstance<CompoundDeclaration>().any { it.common.name == "Payload" })
            assertTrue(declarations.filterIsInstance<CompoundDeclaration>().any { it.common.name == "LocalOnly" })
            assertTrue(declarations.filterIsInstance<FunctionDeclaration>().any { it.common.name == "makePayload" })
            val token = declarations.filterIsInstance<ValueDeclaration>().single { it.role == ValueRole.MEMBER && it.common.name == "token" }
            val tokenRoot = model.syntax.node(model.syntax.body(assertNotNull(token.defaultBody)).root) as Expression.Literal
            val tokenConstant = model.tables.constant(tokenRoot.constant) as ConstantValue.Typed
            assertEquals(ConstantValue.Integral(17), model.tables.constant(tokenConstant.value))
            assertNotNull(field.declaredType)
            val property = declarations.filterIsInstance<PropertyDeclaration>().single { it.common.name == "value" }
            assertIs<LibraryAccessor.Function>(property.getter)
            assertIs<LibraryAccessor.Function>(property.setter)
            val operators = declarations.filterIsInstance<OperatorDeclaration>()
            assertEquals(2, operators.size)
            assertTrue(operators.all { it.operator == "+" })
            assertEquals(setOf("int", "float"), operators.map { (model.tables.type(assertNotNull(it.operand)) as LibraryType.Builtin).key }.toSet())
            assertTrue(declarations.filterIsInstance<ConstructorDeclaration>().any { it.normal.isNotEmpty() && it.body != null })
            assertTrue(declarations.filterIsInstance<FunctionDeclaration>().all { it.body != null })
            val enumeration = declarations.filterIsInstance<EnumDeclaration>().single()
            assertEquals(listOf("first", "second"), enumeration.members.map { model.declarations.declaration(it).common.name })
            assertTrue(enumeration.members.all { model.tables.constant((model.declarations.declaration(it) as EnumMemberDeclaration).data) is ConstantValue.Nbt })
            val alias = declarations.filterIsInstance<TypeAliasDeclaration>().single()
            assertEquals(LibraryType.Builtin("int"), model.tables.type(alias.target))
            assertNotNull(alias.declaredTarget)
            assertTrue(model.syntax.nodes.any { it is TemplateBody })
            assertTrue(model.localReferencesValidated)
            assertTrue(model.syntax.externalReferencesValidated)
            assertTrue(declarations.none { it.common.name == "main" })
        }
        val original = codec.encode(LibraryExporter.capture(selection))
        assertContentEquals(original, codec.encode(LibraryExporter.capture(selection.copy(templates = selection.templates.reversed(), aliases = selection.aliases.reversed()))))
        assertSame(originalOperators, scope.template.getValue("Child").scope.operators)
    }

    @Test fun actualSameNameCompanionKeepsItsLogicalOwnerKeyAndExplicitRelationship() = source("""
        namespace compound.fixture
        data Box {var value:int=1;constructor(){}}
        object data Box {var shared:int=9}
        func main(){}
    """.trimIndent()) { file ->
        val data = file.namespace.scope.template.getValue("Box")
        val actualCompanion = assertNotNull(data.companionObject)
        assertEquals("Box", actualCompanion.identifier)
        withFile(OwnedLibrarySelection(identity, LibraryTarget("26.3"), emptyList(), listOf(file), templates = listOf(data))) { model ->
            val templates = model.declarations.declarations.filterIsInstance<CompoundDeclaration>()
            val ordinary = templates.single { it.category == CompoundCategory.DATA }
            val companion = templates.single { it.category == CompoundCategory.OBJECT }
            assertEquals(companion, model.declarations.declaration(assertNotNull(ordinary.companion)))
            val ordinaryKey = assertNotNull(ordinary.common.key)
            val companionKey = assertNotNull(companion.common.key)
            assertTrue(ordinaryKey.ownerPath.isEmpty())
            assertEquals(listOf(DeclarationPathPart(3, "Box")), companionKey.ownerPath)
            assertEquals("Box", companionKey.name)
            val keys = model.declarations.declarations.mapNotNull { it.common.key }.map { TypeTableCodec.declarationKeyBytes(it).toList() }
            assertEquals(keys.size, keys.distinct().size)
            val public = model.declarations.declarations.takeWhile { it.common.key != null }
            assertEquals(model.declarations.declarations.count { it.common.key != null }, public.size)
        }
    }

    @Test fun foreignParentIsOnlyAResolvedDependencyReference() {
        val dependency = PackageKey("fixture:foreign-parent", "1")
        var parent: DataTemplate? = null
        source("""
            namespace compound.fixture
            import dependency:*
            data Child:dependency:Parent {var own:int=2;constructor(){}}
            func main(){}
        """.trimIndent(), install = {
            val namespace = Namespace("dependency")
            parent = DataTemplate("Parent", "dependency").apply { declarationFile = null; extends(DataTemplate.baseDataTemplate) }
            namespace.scope.addTemplate("Parent", parent!!)
            GlobalScope.libNamespaces["dependency"] = namespace
        }) { file ->
            val sourceParent = assertNotNull(parent)
            withFile(OwnedLibrarySelection(identity, LibraryTarget("26.3"), emptyList(), listOf(file),
                dependencies = listOf(LibraryDependency(dependency, "main")),
                ownership = object : LibraryExportOwnership {
                    override fun typeReference(identity: TypeId) = if (identity == sourceParent.getType().typeId)
                        DeclarationReference.Dependency(1, DeclarationKey("dependency", emptyList(), 3, "Parent")) else null
                    override fun namespacePackages(namespace: String) = if (namespace == "dependency") listOf(dependency) else emptyList()
                }, templates = listOf(file.namespace.scope.template.getValue("Child")))) { model ->
                assertTrue(model.declarations.declarations.none { it.common.name == "Parent" || it.common.name == "dependency" })
                val child = model.declarations.declarations.filterIsInstance<CompoundDeclaration>().single()
                val reference = (model.tables.type(assertNotNull(child.parents.single().type)) as LibraryType.Declaration).target
                assertEquals(DeclarationReference.Dependency(1, DeclarationKey("dependency", emptyList(), 3, "Parent")), reference)
                assertEquals(listOf(LibraryDependency(dependency, "main")), model.manifest.dependencies)
            }
        }
    }

    private fun withFile(selection: OwnedLibrarySelection, action: (LibraryModel) -> Unit) {
        val directory = Files.createTempDirectory("mcfpp-compound-export-")
        val path = directory.resolve("bin.mclib")
        try {
            LibraryExport.write(selection, path)
            val bytes = Files.readAllBytes(path)
            val model = codec.decode(bytes)
            action(model)
            assertContentEquals(bytes, codec.encode(LibraryExporter.capture(selection)))
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory) }
    }
    private fun source(code: String, install: (() -> Unit)? = null, action: (MCFPPFile) -> Unit) {
        val configuration = Project.config
        val settings = listOf(CompileSettings.ignoreStdLib, CompileSettings.isDebug, CompileSettings.isLib, CompileSettings.printAll)
        try {
            Project.config = ProjectConfig()
            MCFPPStringTest.readFromString(code, arrayOf("-ignoreStdLib"), version = "26.3", afterFileCreated = { install?.invoke() })
            assertEquals(0, Project.errorCount)
            val main = GlobalScope.localNamespaces.getValue("compound.fixture").scope.functions.getValue("main").single()
            action(assertNotNull(main.declarationFile))
        } finally {
            MCFPPFile.currFile = null
            Project.config = configuration
            CompileSettings.ignoreStdLib = settings[0]; CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
        }
    }
}
