package top.mcfpp.test.library

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.api.*
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import java.nio.file.Files
import kotlin.test.*

class LibraryCompoundImportTest {
    private val identity = LibraryIdentity(PackageKey("fixture:compound-import", "1"), "main")
    private val codec = LibraryModelCodec(listOf(CompoundCodec))

    @Test fun freshConsumerExecutesInitializersConstructorsPropertiesMethodsOperatorsEnumsAndAliases() = isolated {
        val directory = Files.createTempDirectory("mcfpp-compound-import-")
        val path = directory.resolve("bin.mclib")
        try {
            parse("""
                namespace published.compound
                typealias int as Count
                enum Mode {first=3,second=7}
                func seedValue():int {
                    /say initialized
                    return 4}
                data Parent {var base:int=1;constructor(){}}
                data Box:Parent {
                    var z:int=seedValue()
                    var a:int=this.z+1
                    var value:int=3 {get {return field+1};set {field=value}}
                    constructor(seed:int=6){this.z=seed}
                    func read():int {if this.z>100{return 100};return this.z+this.base}
                    operator +(other:int):int {return this.z+other}
                    operator +(other:float):int {return 7}
                }
                object data Box {var shared:int=9}
                func main(){}
            """.trimIndent())
            val file = sourceFile("published.compound")
            val original = file.namespace.scope.template.getValue("Box")
            LibraryExport.write(OwnedLibrarySelection(identity, LibraryTarget("26.3"), emptyList(), listOf(file),
                templates = listOf(original), enums = file.namespace.scope.enums,
                aliases = listOf(OwnedLibraryAlias("Count", file))), path)
            val originalType = original.getType().typeId
            val model = codec.decode(Files.readAllBytes(path))
            assertTrue(model.declarations.declarations.filterIsInstance<OperatorDeclaration>().size == 2)
            val session = LibraryImportSession()
            try {
                parse("""
                    namespace consumer
                    import published.compound:*
                    func main(){
                        var first=Box()
                        external initialized=first.a
                        external method=first.read()
                        external added=first+4
                        external otherOperand=first+1.0
                        first.value=8
                        external property=first.value
                        var otherBox=Box(9)
                        external secondValue=otherBox.z
                        external shared=Box.shared
                        external alias:Count=3
                        var selected:Mode=second
                        external ordinal=selected.value
                    }
                """.trimIndent()) {
                    assertFalse(GlobalScope.localNamespaces.containsKey("published.compound"))
                    session.stage(path, identity); session.bindAll()
                    assertFalse(GlobalScope.libNamespaces.containsKey("published.compound"))
                    // Importing an unused field must not allocate runtime storage just to look
                    // for callback payloads. Actual object construction below still initializes it.
                    val importedBox = session.functions().single { it.identifier == "read" }.owner as DataTemplate
                    assertNull(importedBox.scope.getVar("z")!!.storageBinding)
                    session.commit()
                }
                val restored = GlobalScope.libNamespaces.getValue("published.compound").scope.template.getValue("Box")
                assertNotSame(original, restored)
                assertIs<TypeId.Opaque>(restored.getType().typeId)
                assertNotEquals(originalType, restored.getType().typeId)
                assertSame(GlobalScope.libNamespaces.getValue("published.compound").scope.getObject("Box"), restored.companionObject)
                val parent = restored.parent.filterIsInstance<DataTemplate>().single()
                assertSame(DataTemplate.baseDataTemplate, parent.parent.single())
                assertEquals(setOf<MCFPPType?>(MCFPPBaseType.Int, MCFPPBaseType.Float), restored.scope.operators.getValue("+").keys)
                assertTrue(session.functions().filter { it.bodyHandle != null }.all { it.ast == null })
                val main = functions("consumer").single { it.identifier == "main" }
                val machine = execute(main, session)
                mapOf("initialized" to 5, "method" to 7, "added" to 10, "otherOperand" to 7,
                    "property" to 9, "secondValue" to 9, "shared" to 9, "alias" to 3, "ordinal" to 1).forEach { (name, expected) ->
                    assertEquals(expected, machine.read(main.scope.getVar(name) as MCInt), name)
                }
                assertEquals(listOf("initialized", "initialized"), machine.messages)
                assertEquals(0, machine.stackDepth)
                assertTrue(session.compiledFunctions().isNotEmpty())
                assertEquals(session.functions().size, session.functions().map { System.identityHashCode(it) }.distinct().size)
            } finally { session.close() }
            assertFalse(GlobalScope.libNamespaces.containsKey("published.compound"))
            assertFalse(GlobalScope.scoreboards.containsKey("published.compound:Mode"))
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory) }
    }

    @Test fun stagedDependencyParentsBindToTheirShellAndConflictsPublishNothing() = isolated {
        val directory = Files.createTempDirectory("mcfpp-compound-dependency-")
        val parentPath = directory.resolve("parent.mclib")
        val childPath = directory.resolve("child.mclib")
        val parentIdentity = LibraryIdentity(PackageKey("fixture:compound-parent", "1"), "main")
        try {
            parse("namespace library.base\ndata Parent {constructor(){}}\nfunc main(){}")
            val parentFile = sourceFile("library.base")
            LibraryExport.write(OwnedLibrarySelection(parentIdentity, LibraryTarget("26.3"), emptyList(), listOf(parentFile),
                templates = listOf(parentFile.namespace.scope.template.getValue("Parent"))), parentPath)
            val parentKey = codec.decode(Files.readAllBytes(parentPath)).declarations.declarations.filterIsInstance<CompoundDeclaration>().single().common.key!!
            val sourceDependency = LibraryImportSession()
            try {
                parse("""
                    namespace library.child
                    import library.base:*
                    data Child:library.base:Parent {var own:int=12;constructor(){};func read():int{return this.own}}
                    func main(){}
                """.trimIndent()) { sourceDependency.stage(parentPath); sourceDependency.bindAll(); sourceDependency.commit() }
                val file = sourceFile("library.child")
                val foreign = GlobalScope.libNamespaces.getValue("library.base").scope.template.getValue("Parent")
                LibraryExport.write(OwnedLibrarySelection(identity, LibraryTarget("26.3"), emptyList(), listOf(file),
                    dependencies = listOf(LibraryDependency(parentIdentity.library, parentIdentity.classifier)),
                    ownership = object : LibraryExportOwnership {
                        override fun typeReference(identity: TypeId) = if (identity == foreign.getType().typeId) DeclarationReference.Dependency(1, parentKey) else null
                        override fun namespacePackages(namespace: String) = if (namespace == "library.base") listOf(parentIdentity.library) else emptyList()
                    }, templates = listOf(file.namespace.scope.template.getValue("Child"))), childPath)
            } finally { sourceDependency.close() }
            val session = LibraryImportSession()
            var firstIdentity: TypeId? = null
            try {
                parse("namespace consumer\nimport library.child:*\nfunc main(){var child=Child();external answer=child.read()}") {
                    session.stage(childPath); session.stage(parentPath); session.bindAll(); session.commit()
                }
                val child = GlobalScope.libNamespaces.getValue("library.child").scope.template.getValue("Child")
                val parent = GlobalScope.libNamespaces.getValue("library.base").scope.template.getValue("Parent")
                firstIdentity = parent.getType().typeId
                assertSame(parent, child.parent.single())
                val main = functions("consumer").single { it.identifier == "main" }
                assertEquals(12, execute(main, session).read(main.scope.getVar("answer") as MCInt))
            } finally { session.close() }
            assertFalse(GlobalScope.libNamespaces.containsKey("library.base"))
            assertFalse(GlobalScope.libNamespaces.containsKey("library.child"))
            Project.init()
            LibraryImportSession().use { independent ->
                independent.stage(parentPath); independent.bindAll(); independent.commit()
                assertNotEquals(firstIdentity, GlobalScope.libNamespaces.getValue("library.base").scope.template.getValue("Parent").getType().typeId)
            }
            Project.init()
            LibraryImportSession().use { failed ->
                failed.stage(childPath); failed.stage(parentPath); failed.bindAll()
                val existing = DataTemplate("Child", "library.child")
                GlobalScope.getOrCreateNamespace("library.child").scope.addTemplate("Child", existing)
                val before = GlobalScope.libNamespaces.toMap()
                assertEquals("LIB_DECL_CONFLICT", assertFailsWith<LibraryImportException> { failed.commit() }.code)
                assertEquals(before, GlobalScope.libNamespaces)
                assertSame(existing, GlobalScope.localNamespaces.getValue("library.child").scope.template.getValue("Child"))
            }
        } finally { Files.deleteIfExists(parentPath); Files.deleteIfExists(childPath); Files.deleteIfExists(directory) }
    }

    private fun functions(namespace: String) = GlobalScope.localNamespaces.getValue(namespace).scope.functions.values.flatten().filter { !it.actualCallBody }
    private fun sourceFile(namespace: String): MCFPPFile = requireNotNull(functions(namespace).first().declarationFile)
    private fun parse(code: String, install: (() -> Unit)? = null) {
        Project.config = ProjectConfig()
        MCFPPStringTest.readFromString(code, arrayOf("-ignoreStdLib"), version = "26.3", afterFileCreated = { install?.invoke() })
        assertEquals(0, Project.errorCount)
    }
    private fun execute(main: Function, session: LibraryImportSession): ScoreCommandExecutor {
        val functions = (GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() } + session.functions())
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }
        return ScoreCommandExecutor(main.commands.analyzeAll(), functions)
    }
    private fun isolated(action: () -> Unit) {
        val config = Project.config
        val settings = listOf(CompileSettings.ignoreStdLib, CompileSettings.isDebug, CompileSettings.isLib, CompileSettings.printAll)
        try { action() } finally {
            MCFPPFile.currFile = null; Project.config = config
            CompileSettings.ignoreStdLib = settings[0]; CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
        }
    }
}
