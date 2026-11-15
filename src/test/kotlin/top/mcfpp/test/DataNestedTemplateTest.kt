package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.LibBinWriter
import top.mcfpp.io.info.LibraryMetadata
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataNestedTemplateTest {
    private fun compile(source: String) {
        val diagnostics = compilerDiagnostics { MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3") }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
    }

    @Test fun qualifiedTypesConstructAndSurviveFreshLibraryImport() {
        val previous = Project.config
        val output = Files.createTempDirectory("mcfpp-nested-data-")
        try {
            Project.config = ProjectConfig()
            compile("""
                namespace fixture.nested
                data Outer {
                    data Inner {
                        const schema: int = 5
                        var value: int = 3
                        var label: string?
                        func mutate(){ this.value = 9; }
                    }
                    var child: Inner
                }
                data Derived: Outer.Inner {}
                func make(): Outer.Inner { return Outer.Inner(); }
                func read(value: Outer.Inner): int { return value.schema * 10 + value.value; }
                func hasLabel(value: Outer.Inner): bool { return value.label != null; }
                func main(){ external result = read(make()); }
            """)
            val original = GlobalScope.getTemplate("fixture.nested", "Outer.Inner")!!
            assertNotNull(original.scope.getVar("schema")!!.declarationConstant)
            LibBinWriter.write(output.toString())
            LibraryMetadata.reset()
            Project.config.includes = arrayListOf(output.toString())
            compile("""
                import fixture.nested:*
                func main(){
                    var value: Outer.Inner = Outer.Inner()
                    external result = read(value as Outer.Inner)
                    if(hasLabel(value)){ result += 1; }
                }
            """)
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            assertEquals(53, executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
            val rejected = compilerDiagnostics {
                MCFPPStringTest.readFromString("""
                    import fixture.nested:Outer
                    func main(){ const value: Outer.Inner = {schema: 5, value: 3}; value.mutate(); }
                """.trimIndent(), version = "26.3")
            }
            assertTrue(rejected.any { "constant" in it.lowercase() && "mutate" in it }, rejected.joinToString("\n"))
        } finally {
            Project.config = previous
            output.toFile().deleteRecursively()
        }
    }

    @Test fun sourceAnonymousDeclarationsGiveAMigrationDiagnostic() {
        val diagnostics = compilerDiagnostics {
            MCFPPStringTest.readFromString("func main(){ var value: data { var id: int } = {id: 1}; }", version = "26.3")
        }
        assertTrue(diagnostics.any { "named data declaration" in it }, diagnostics.joinToString("\n"))
    }

    @Test fun outerReadonlyBindingsParticipateInChildIdentity() {
        val previous = Project.config
        val output = Files.createTempDirectory("mcfpp-nested-generic-")
        try {
        Project.config = ProjectConfig()
        compile("""
            namespace fixture.generic
            data Outer<N: int> { data Inner { const schema: int = N; var value: int = 1 } }
            func main(){ var a: Outer.Inner<2> = {schema: 2, value: 1}; var b: Outer.Inner<3> = {schema: 3, value: 1}; }
        """)
        val main = GlobalScope.localNamespaces.getValue("fixture.generic").scope.functions.getValue("main").single()
        assertNotEquals(main.scope.getVar("a")!!.type.typeId, main.scope.getVar("b")!!.type.typeId)
        LibBinWriter.write(output.toString())
        LibraryMetadata.reset()
        Project.config.includes = arrayListOf(output.toString())
        compile("""
            import fixture.generic:Outer
            func main(){ var a: Outer.Inner<2> = Outer.Inner<2>(); external result = a.schema; }
        """)
        val imported = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(2, executePublishedFunctions(imported).read(imported.scope.getVar("result") as MCInt))
        } finally { Project.config = previous; output.toFile().deleteRecursively() }
    }

    @Test fun multilevelForwardReferencesAndObjectOwnershipUseQualifiedTypes() {
        compile("""
            data Outer {
                var child: Inner
                data Inner { var sibling: Other?; data Leaf { const code = 7 } }
                data Other { var owner: Inner? }
            }
            object data Catalog { data Entry { const code = 8 } }
            func accept(value: list<Outer.Inner.Leaf>): int { return value[0].code; }
            func main(){
                var leaf: Outer.Inner.Leaf = Outer.Inner.Leaf()
                var entry: Catalog.Entry = Catalog.Entry()
                external result = accept([leaf]) * 10 + entry.code
            }
        """)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(78, executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }
}
