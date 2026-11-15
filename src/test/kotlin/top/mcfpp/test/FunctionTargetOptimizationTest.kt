package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Command
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.optimization.OptimizationPipeline
import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionTargetOptimizationTest {
    @Test
    fun localTargetsKeepTheirCommandsWhenAnImportedDeclarationUsesTheSameId() {
        val previousConfig = Project.config
        val previousIgnore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            for (compiledImport in listOf(false, true)) {
                MCFPPStringTest.readFromString("""
                    namespace fixture.names
                    func main(){
                        /say local
                    }
                """.trimIndent(), version = "26.3")
                assertEquals(0, Project.errorCount)
                val local = GlobalScope.localNamespaces.getValue("fixture.names").scope.functions.getValue("main").single()
                val localCommands = local.commands.analyzeAll()
                val imported = Function("main", "fixture.names", null).apply {
                    bodyCompiled = compiledImport
                    if (compiledImport) commands.add(Command("say imported"))
                }
                val importedCommands = imported.commands.analyzeAll()
                GlobalScope.libNamespaces["fixture.names"] = Namespace("fixture.names").apply {
                    scope.addFunction(imported, false)
                }

                OptimizationPipeline.finish()

                assertEquals(localCommands, local.commands.analyzeAll())
                assertEquals(importedCommands, imported.commands.analyzeAll())
            }
        } finally {
            Project.closeLibraryImports()
            Project.config = previousConfig
            CompileSettings.ignoreStdLib = previousIgnore
        }
    }
}
