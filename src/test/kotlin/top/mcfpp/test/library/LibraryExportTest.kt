package top.mcfpp.test.library

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.MCFPPFile
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.MCFPPTypeVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.library.api.LibraryExport
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import kotlin.test.*

class LibraryExportTest {
    private fun selection(functions: List<Function>, roots: List<Function>) = OwnedLibrarySelection(
        LibraryIdentity(PackageKey("fixture:functions", "1"), "main"), LibraryTarget("26.3"), roots,
        functions.map { requireNotNull(it.declarationFile) }.fold(arrayListOf<MCFPPFile>()) { files, file ->
            files.apply { if (none { it === file }) add(file) }
        })

    @Test fun realParsedFunctionsDefaultsLocalsControlAndPrivateClosureProduceAV1File() = source("""
        namespace export.fixture
        func aHelper(value:int):int {
            /say captured
            return value+1
        }
        func foo(value:int=4):int {
            var copy:int=value
            if copy>0 {copy=aHelper(copy)}
            var count:int=0
            while count<1 {count+=1}
            return copy+count
        }
        func main(){external result=foo()}
    """.trimIndent()) { functions ->
        val helper = functions.single { it.identifier == "aHelper" }
        helper.accessModifier = Member.AccessModifier.PRIVATE
        val foo = functions.single { it.identifier == "foo" }
        val selection = selection(functions, listOf(foo))
        val directory = Files.createTempDirectory("mcfpp-v1-export-")
        val output = directory.resolve("bin.mclib")
        try {
            val result = LibraryExport.write(selection, output)
            val bytes = Files.readAllBytes(output)
            assertEquals(bytes.size.toLong(), result.bytesWritten)
            assertEquals(1L, LibraryWire.decode(bytes).formatVersion)
            val decoded = LibraryModelCodec().decode(bytes)
            val declarations = decoded.declarations.declarations.filterIsInstance<FunctionDeclaration>()
            assertEquals(setOf("foo", "aHelper"), declarations.map { it.common.name }.toSet())
            val allDeclarations = decoded.declarations.declarations
            val firstPrivate = allDeclarations.indexOfFirst { it.common.key == null }
            assertTrue(allDeclarations.drop(firstPrivate).none { it.common.key != null }, "Public keyed D records precede private records")
            val parameterOwners = allDeclarations.filterIsInstance<ValueDeclaration>()
                .filter { it.role == ValueRole.NORMAL_PARAMETER }.map { assertNotNull(it.common.owner).value }
            assertEquals(parameterOwners.sorted(), parameterOwners, "Capture follows final function D order")
            val helperRecord = declarations.single { it.common.name == "aHelper" }
            assertEquals(DeclarationAccess.PRIVATE, helperRecord.common.access)
            assertNull(helperRecord.common.key)
            assertNotNull(helperRecord.common.origin)
            assertNotNull(helperRecord.body)
            val fooRecord = declarations.single { it.common.name == "foo" }
            val parameter = decoded.declarations.declaration(fooRecord.normal.single()) as ValueDeclaration
            assertNotNull(parameter.defaultBody)
            assertNotNull(parameter.frozen)
            assertEquals(BodyRole.DEFAULT, decoded.syntax.body(assertNotNull(parameter.defaultBody)).role)
            assertTrue(decoded.syntax.nodes.any { it is Statement.If })
            assertTrue(decoded.syntax.nodes.any { it is Statement.While })
            assertTrue(decoded.syntax.nodes.filterIsInstance<Statement.Command>().flatMap { it.parts }
                .any { it is TextPart.Text && it.value.contains("say captured") })
            assertEquals(2, decoded.declarations.declarations.filterIsInstance<ValueDeclaration>().count { it.role == ValueRole.LOCAL })
            decoded.syntax.scopes.flatMap { it.bindings }.forEach { assertIs<ValueDeclaration>(decoded.declarations.declaration(it)) }
            assertTrue(decoded.localReferencesValidated)
            assertTrue(decoded.syntax.externalReferencesValidated)
            assertNull(decoded.debug)
            assertContentEquals(bytes, LibraryModelCodec().encode(LibraryExporter.capture(selection)))
            val retained = bytes.copyOf()
            val original = foo.ast
            try {
                foo.ast = null
                assertFailsWith<SyntaxCaptureException> { LibraryExport.write(selection, output) }
                assertContentEquals(retained, Files.readAllBytes(output))
            } finally { foo.ast = original }
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(directory)
        }
    }

    @Test fun overloadCandidatesAndInitializerShadowingUseTheAuthorsDeclarations() = source("""
        namespace export.fixture
        func pick(value:int):int{return value+1}
        func pick(value:float):int{return 9}
        func foo(value:int):int {
            var copy:int=value
            if value>0 {var copy:int=copy+1;return pick(copy)+export.remote:remote(copy)}
            return pick(copy)+export.remote:remote(copy)
        }
        func main(){external result=foo(3)}
    """.trimIndent(), additional = "namespace export.remote\nfunc remote(value:int):int{return value+2}") { functions ->
        val model = LibraryExporter.capture(selection(functions, listOf(functions.single { it.identifier == "foo" })))
        val decoded = LibraryModelCodec().decode(LibraryModelCodec().encode(model))
        val picks = decoded.declarations.declarations.withIndex().filter { it.value is FunctionDeclaration && it.value.common.name == "pick" }
            .map { DeclarationReference.Local(DeclarationId(it.index + 1L)) }.toSet()
        assertEquals(2, picks.size)
        decoded.syntax.nodes.filterIsInstance<Expression.Call>().forEach { call ->
            val lookup = (call.callee as Expression.Name).use as NameUse.Lookup
            if (lookup.name == "pick") assertEquals(picks, lookup.candidates.toSet())
            else {
                assertEquals("export.remote", lookup.namespace)
                assertEquals("remote", lookup.name)
                assertEquals(1, lookup.candidates.size)
            }
        }
        val keys = decoded.declarations.declarations.mapNotNull { it.common.key }
        assertEquals(keys.sortedWith { a, b -> compareBytes(TypeTableCodec.declarationKeyBytes(a), TypeTableCodec.declarationKeyBytes(b)) }, keys)
        assertTrue(decoded.declarations.environments.single { it.namespace == "export.fixture" }.imports.none { it.targetNamespace == "export.remote" })
        val locals = decoded.syntax.nodes.filterIsInstance<Statement.Local>()
        assertEquals(2, locals.size)
        val inner = locals.single { it.initializer is Expression.Binary }
        val initializer = inner.initializer as Expression.Binary
        val captured = (initializer.left as Expression.Name).use as NameUse.Local
        assertNotEquals(inner.declaration, captured.target)
        assertEquals(locals.single { it !== inner }.declaration, captured.target)
        decoded.syntax.nodes.filterIsInstance<TypeSyntax.Name>().forEach { node ->
            assertNotNull(node.resolved)
            assertTrue((decoded.tables.type(assertNotNull(node.resolved)) as LibraryType.Builtin).key in setOf("int", "float"))
        }
    }

    private fun source(code: String, additional: String? = null, action: (List<Function>) -> Unit) {
        val configuration = Project.config
        val ignoreStdlib = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        val isLib = CompileSettings.isLib
        val printAll = CompileSettings.printAll
        try {
            Project.config = ProjectConfig()
            MCFPPStringTest.readFromString(code, arrayOf("-ignoreStdLib"), version = "26.3", afterFileCreated = { caller ->
                if (additional != null) {
                    val namespace = GlobalScope.getOrCreateNamespace("export.remote")
                    val remote = MCFPPFile(namespace)
                    val tree = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(additional)))).compilationUnit()
                    MCFPPFile.currFile = remote
                    Project.currNamespace = namespace.identifier
                    try {
                        MCFPPTypeVisitor().visitCompilationUnit(tree)
                        remote.field.namespaceField = remote.namespace.scope
                        MCFPPFieldVisitor().visit(tree)
                    } finally {
                        MCFPPFile.currFile = caller
                        Project.currNamespace = caller.namespace.identifier
                    }
                }
            })
            assertEquals(0, Project.errorCount)
            val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .filter { !it.actualCallBody && it.ast != null }
            action(functions)
        } finally {
            MCFPPFile.currFile = null
            Project.config = configuration
            CompileSettings.ignoreStdLib = ignoreStdlib
            CompileSettings.isDebug = debug
            CompileSettings.isLib = isLib
            CompileSettings.printAll = printAll
        }
    }
}
