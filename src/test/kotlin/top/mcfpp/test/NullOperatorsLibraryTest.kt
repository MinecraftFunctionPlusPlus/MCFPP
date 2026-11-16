package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.LibBinWriter
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NullOperatorsLibraryTest {
    @Test fun exportedOptionalOperationsExecuteInAFreshSession() {
        val includes = Project.config.includes
        try {
            Project.config.includes = arrayListOf()
            val diagnostics = compilerDiagnostics { MCFPPStringTest.readFromString("""
                data Inner { var value: int? }
                data Box { var inner: Inner? }
                func optional(full: bool): int {
                    var box: Box = {}
                    if(full){ box.inner = {value: 4}; }
                    var a = box.inner?.value ?: return 7
                    var b = box.inner?.let { it.value ?: 9 } ?: 10
                    var c = box.inner!!.value!!
                    box.inner?.value = null
                    return a + b + c + (box.inner?.value ?: 3)
                }
                func main(){}
            """.trimIndent(), version = "26.3") }
            assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
            LibBinWriter.write("build/null-operators/library")
            Project.config.includes = arrayListOf("build/null-operators/library")
            val imported = compilerDiagnostics { MCFPPStringTest.readFromString("""
                func main(){ external result = default.test:optional(true) + default.test:optional(false); }
            """.trimIndent(), version = "26.3", afterFileCreated = {
                assertNotNull(GlobalScope.libNamespaces.getValue("default.test").scope.functions.getValue("optional").single().bodyHandle)
            }) }
            assertEquals(0, Project.errorCount, imported.joinToString("\n"))
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            assertEquals(22, executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
        } finally { Project.config.includes = includes }
    }
}
