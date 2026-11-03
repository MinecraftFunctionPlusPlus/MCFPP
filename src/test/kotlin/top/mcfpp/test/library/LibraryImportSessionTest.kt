package top.mcfpp.test.library

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.LibBinReader
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.api.*
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class LibraryImportSessionTest {
    private val packageA = LibraryIdentity(PackageKey("fixture:import-a", "1"), "main")
    private val packageB = LibraryIdentity(PackageKey("fixture:import-b", "1"), "main")

    @Test fun exportedFileRunsInFreshConsumerWithDefaultsControlAndPrivateCalls() = isolated {
        val directory = Files.createTempDirectory("mcfpp-import-")
        val path = directory.resolve("bin.mclib")
        try {
            parse("""
                namespace published.math
                func aHelper(left:int,right:int):int {
                    /say called
                    if left+right>100 {return 100}
                    return left+right
                }
                func add(left:int=2,right:int=3):int {
                    var result:int=aHelper(left,right)
                    if result>100 {return 100}
                    return result
                }
                func main(){external ignored=add()}
            """.trimIndent())
            val author = sourceFunctions("published.math")
            author.single { it.identifier == "aHelper" }.accessModifier = Member.AccessModifier.PRIVATE
            LibraryExport.write(selection(packageA, author, listOf(author.single { it.identifier == "add" })), path)
            val session = LibraryImportSession(LibraryCompatibility(minecraftVersion = "26.3"))
            try {
                parse("""
                    namespace consumer
                    import published.math:*
                    func main(){
                        external left:int=4
                        external right:int=5
                        external known=add(2,3)
                        external unknown=add(left,right)
                        external mixed=add(left,3)
                        external defaults=add()
                        external controlled=add(200,1)
                    }
                """.trimIndent()) {
                    assertFalse(GlobalScope.localNamespaces.containsKey("published.math"), "Author globals were cleared before staging")
                    LibBinReader.stagePublished(path, session, packageA)
                    session.bindAll()
                    assertFalse(GlobalScope.libNamespaces.containsKey("published.math"))
                    session.commit()
                }
                val public = GlobalScope.libNamespaces.getValue("published.math")
                assertTrue(public.scope.getFunctionCandidates("aHelper").isEmpty())
                assertEquals(1, public.scope.getFunctionCandidates("add").size)
                val heads = session.functions().filter { it.bodyHandle != null }
                assertEquals(2, heads.size)
                assertTrue(heads.all { it.ast == null })
                assertTrue(session.functions().none { imported -> author.any { it === imported } })
                val main = sourceFunctions("consumer").single { it.identifier == "main" }
                val machine = execute(main, session)
                assertEquals(5, machine.read(main.scope.getVar("known") as MCInt))
                assertEquals(9, machine.read(main.scope.getVar("unknown") as MCInt))
                assertEquals(7, machine.read(main.scope.getVar("mixed") as MCInt))
                assertEquals(5, machine.read(main.scope.getVar("defaults") as MCInt))
                assertEquals(100, machine.read(main.scope.getVar("controlled") as MCInt))
                assertEquals(List(5) { "called" }, machine.messages)
                assertEquals(0, machine.stackDepth)
                assertTrue(session.compiledFunctions().any { it.identifier.startsWith("__library_") })
            } finally { session.close() }
            assertFalse(GlobalScope.libNamespaces.containsKey("published.math"))
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory) }
    }

    @Test fun allLibrariesAreStagedBeforeDependencyBodiesBindAndRun() = isolated {
        val directory = Files.createTempDirectory("mcfpp-import-dependencies-")
        val first = directory.resolve("a.mclib")
        val second = directory.resolve("b.mclib")
        try {
            exportDependency(second)
            val dependency = LibraryModelCodec().decode(Files.readAllBytes(second))
            val key = dependency.declarations.declarations.filterIsInstance<FunctionDeclaration>().single().common.key!!
            val authorDependencies = LibraryImportSession()
            try {
                parse("""
                    namespace lib.a
                    import lib.b:*
                    func combined(value:int):int{return twice(value)+1}
                    func main(){external ignored=combined(2)}
                """.trimIndent()) {
                    authorDependencies.stage(second, packageB); authorDependencies.bindAll(); authorDependencies.commit()
                }
                val source = sourceFunctions("lib.a")
                LibraryExport.write(selection(packageA, source, listOf(source.single { it.identifier == "combined" })).copy(
                    dependencies = listOf(LibraryDependency(packageB.library, packageB.classifier)),
                    ownership = object : LibraryExportOwnership {
                        override fun functionReference(function: Function) = if (function.namespace == "lib.b") DeclarationReference.Dependency(1, key) else null
                        override fun namespacePackages(namespace: String) = if (namespace == "lib.b") listOf(packageB.library) else emptyList()
                    }), first)
            } finally { authorDependencies.close() }
            val session = LibraryImportSession()
            try {
                parse("namespace consumer\nfunc main(){external answer=lib.a:combined(3)}") {
                    session.stage(first, packageA)
                    session.stage(second, packageB)
                    session.bindAll(); session.commit()
                }
                val main = sourceFunctions("consumer").single { it.identifier == "main" }
                val machine = execute(main, session)
                assertEquals(7, machine.read(main.scope.getVar("answer") as MCInt))
                assertEquals(listOf("dependency"), machine.messages)
                assertEquals(0, machine.stackDepth)
            } finally { session.close() }
        } finally { Files.deleteIfExists(first); Files.deleteIfExists(second); Files.deleteIfExists(directory) }
    }

    @Test fun missingDependenciesAndCommitConflictsNeverPublishAPartialLibrary() = isolated {
        val directory = Files.createTempDirectory("mcfpp-import-failure-")
        val path = directory.resolve("b.mclib")
        try {
            exportDependency(path)
            val original = LibraryModelCodec().decode(Files.readAllBytes(path))
            val requiring = LibraryModel(original.manifest.copy(dependencies = listOf(LibraryDependency(packageA.library, "main"))),
                original.declarations, original.tables, original.syntax)
            Project.init()
            val before = GlobalScope.libNamespaces.toMap()
            LibraryImportSession().use { session ->
                session.stage(LibraryModelCodec().encode(requiring))
                assertEquals("LIB_DEPENDENCY", assertFailsWith<LibraryImportException> { session.bindAll() }.code)
                assertEquals(before, GlobalScope.libNamespaces)
                assertEquals(LibraryImportState.FAILED, session.state)
                assertFailsWith<IllegalStateException> { session.functions() }
            }
            LibraryImportSession().use { session ->
                session.stage(path); session.bindAll()
                val conflict = Function("twice", "lib.b", null).apply {
                    returnType = MCFPPBaseType.Int
                    appendNormalParam(MCFPPBaseType.Int, "value")
                }
                GlobalScope.getOrCreateNamespace("lib.b").scope.addFunction(conflict, false)
                assertEquals("LIB_DECL_CONFLICT", assertFailsWith<LibraryImportException> { session.commit() }.code)
                assertEquals(before, GlobalScope.libNamespaces)
                assertEquals(LibraryImportState.FAILED, session.state)
            }
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory) }
    }

    private fun exportDependency(path: Path) {
        parse("""
            namespace lib.b
            func twice(value:int):int {
                /say dependency
                return value+value
            }
            func main(){external ignored=twice(2)}
        """.trimIndent())
        val source = sourceFunctions("lib.b")
        LibraryExport.write(selection(packageB, source, listOf(source.single { it.identifier == "twice" })), path)
    }
    private fun selection(identity: LibraryIdentity, source: List<Function>, roots: List<Function>) =
        OwnedLibrarySelection(identity, LibraryTarget("26.3"), roots, source.map { requireNotNull(it.declarationFile) }.distinct())
    private fun sourceFunctions(namespace: String): List<Function> = GlobalScope.localNamespaces.getValue(namespace)
        .scope.functions.values.flatten().filter { !it.actualCallBody }
    private fun parse(code: String, install: (() -> Unit)? = null) {
        Project.config = ProjectConfig()
        MCFPPStringTest.readFromString(code, arrayOf("-ignoreStdLib"), version = "26.3", afterFileCreated = { install?.invoke() })
        assertEquals(0, Project.errorCount)
    }
    private fun execute(main: Function, session: LibraryImportSession): ScoreCommandExecutor {
        val functions = (GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() } +
            GlobalScope.libNamespaces.values.flatMap { it.scope.functions.values.flatten() } + session.functions())
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }
        return ScoreCommandExecutor(listOf(Commands.stackIn().analyze(), "function ${main.namespaceID}",
            Commands.stackOut().analyze()), functions)
    }
    private fun isolated(action: () -> Unit) {
        val configuration = Project.config
        val settings = listOf(CompileSettings.ignoreStdLib, CompileSettings.isDebug, CompileSettings.isLib, CompileSettings.printAll)
        try { action() } finally {
            MCFPPFile.currFile = null
            Project.config = configuration
            CompileSettings.ignoreStdLib = settings[0]; CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
        }
    }
}
