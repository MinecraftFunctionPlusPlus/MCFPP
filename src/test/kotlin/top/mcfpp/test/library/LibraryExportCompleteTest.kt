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
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.TypeId
import java.nio.file.Files
import kotlin.test.*

class LibraryExportCompleteTest {
    @Test fun sourceInferenceReceiversAndBuiltinNativeTargetsReachThePublishedFile() {
        val configuration = Project.config
        val settings = listOf(CompileSettings.ignoreStdLib, CompileSettings.isDebug, CompileSettings.isLib, CompileSettings.printAll)
        val directory = Files.createTempDirectory("mcfpp-complete-export-")
        try {
            Project.config = ProjectConfig()
            MCFPPStringTest.readFromString("""
                namespace export.complete
                data Box<N:int=2> {
                    var inferred=N+1
                    var count:int=0
                    func captured(seed:int):int {
                        var callback:(int)->int={value:int -> this.count+count+seed+value}
                        return callback(seed)
                    }
                }
                func listSize(values:list<int>,value:int):int {
                    values.add(value)
                    return values.size()
                }
                func main(){}
            """.trimIndent(), arrayOf("-ignoreStdLib"), version = "26.3")
            assertEquals(0, Project.errorCount)
            val scope = GlobalScope.localNamespaces.getValue("export.complete").scope
            val root = scope.functions.getValue("listSize").single { !it.actualCallBody }
            val file = assertNotNull(root.declarationFile)
            val selection = OwnedLibrarySelection(LibraryIdentity(PackageKey("fixture:complete-export", "1"), "main"),
                LibraryTarget("26.3"), listOf(root), listOf(file), templates = listOf(scope.template.getValue("Box")))
            val output = directory.resolve("bin.mclib")
            val result = LibraryExport.write(selection, output)
            val bytes = Files.readAllBytes(output)
            assertEquals(bytes.size.toLong(), result.bytesWritten)
            val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
            val model = codec.decode(bytes)
            val declarations = model.declarations.declarations
            fun id(record: LibraryDeclaration) = DeclarationId(declarations.indexOf(record) + 1L)
            val box = declarations.filterIsInstance<CompoundDeclaration>().single { it.common.name == "Box" }
            val inferred = declarations.filterIsInstance<ValueDeclaration>().single { it.common.owner == id(box) && it.common.name == "inferred" }
            assertNull(inferred.declaredType)
            val inferredType = assertIs<LibraryType.Dependent>(model.tables.type(assertNotNull(inferred.type)))
            val operation = assertIs<TypeSyntax.Inferred>(model.syntax.node(inferredType.syntax))
            val initializer = model.syntax.node(model.syntax.body(assertNotNull(inferred.defaultBody)).root)
            assertSame(initializer, operation.expression, "Type inference retains the single authored initializer tree")
            assertEquals(NameUse.Local(box.readonly.single()), assertIs<Expression.Name>(assertIs<Expression.Binary>(operation.expression).left).use)

            val lambda = model.syntax.nodes.filterIsInstance<Expression.Lambda>().single()
            val captures = model.syntax.body(lambda.body).captures.map { (it as DeclarationReference.Local).target }
            val receiver = declarations.filterIsInstance<ValueDeclaration>().single { it.common.name == "this" }
            assertEquals(ValueRole.LOCAL, receiver.role)
            assertTrue(id(receiver) in captures)
            assertTrue(captures.any { model.declarations.declaration(it).common.name == "seed" })
            assertTrue(model.syntax.scopes.any { it.owner == receiver.common.owner && id(receiver) in it.bindings })

            val nativeTargets = declarations.filterIsInstance<FunctionDeclaration>().filter { it.execution == FunctionExecutionKind.NATIVE }
            assertEquals(setOf("add", "size"), nativeTargets.map { assertNotNull(it.native).method }.toSet())
            nativeTargets.forEach { target ->
                val binding = assertNotNull(target.native)
                assertNull(target.common.key)
                assertNull(target.body)
                assertEquals(target.receiver, binding.caller)
                assertEquals("top.mcfpp.mni.NBTListData", binding.ownerClass)
                assertTrue(target.readonly.isEmpty())
            }
            val memberTargets = model.syntax.nodes.filterIsInstance<Expression.Member>().filter { it.name in setOf("add", "size") }
            assertEquals(2, memberTargets.size)
            memberTargets.forEach { member -> assertTrue(assertNotNull(member.candidates).any { reference ->
                reference is DeclarationReference.Local && nativeTargets.any { id(it) == reference.target }
            }) }
            assertTrue(model.manifest.dependencies.isEmpty())
            assertEquals("fixture:complete-export", model.manifest.library.coordinate)
            assertTrue(model.localReferencesValidated)
            assertTrue(model.syntax.externalReferencesValidated)
            repeat(3) { TypeId.fresh() }
            assertContentEquals(bytes, codec.encode(LibraryExporter.capture(selection)))
        } finally {
            Files.deleteIfExists(directory.resolve("bin.mclib")); Files.deleteIfExists(directory)
            MCFPPFile.currFile = null
            Project.config = configuration
            CompileSettings.ignoreStdLib = settings[0]; CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
        }
    }
}
