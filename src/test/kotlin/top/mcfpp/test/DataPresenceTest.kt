package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import top.mcfpp.core.lang.MCInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataPresenceTest {
    @Test fun methodPresenceQueriesBypassMutatingGettersOnConstants() {
        val diagnostics = compilerDiagnostics {
            MCFPPStringTest.readFromString("""
                data Box {
                    var value: int { get { this.value = 9; return field; }; set { field = value; } }
                    func exists(): bool { return this.value != null; }
                }
                func main(){
                    const box: Box = {value: 0}
                    external result = 0
                    if(box.exists()){ result = 1; }
                }
            """.trimIndent(), version = "26.3")
        }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(1, executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }
    @Test fun computedPropertiesDoNotAcquireBackingPositions() {
        val diagnostics = compilerDiagnostics {
            MCFPPStringTest.readFromString("""
                data Box { var computed: int { get = 4 } }
                func inspect(box: Box){ if(box.computed != null){} }
                func main(){ var box: Box = {}; inspect(box); }
            """.trimIndent(), version = "26.3")
        }
        assertTrue(diagnostics.any { "no backing position" in it }, diagnostics.joinToString("\n"))
    }
    @Test fun guardsProveReadsAndDeletionRevokesTheProof() {
        val diagnostics = compilerDiagnostics {
            MCFPPStringTest.readFromString("""
                data Box { var number: int? }
                func read(box: Box): int {
                    if(box.number != null){ return box.number; }
                    return 0
                }
                func consume(box: Box): int {
                    var result = 0
                    while(box.number != null){ result += box.number; box.number = null; }
                    return result
                }
                func main(){ var box: Box = {number: 4}; external result = read(box) + consume(box); }
            """.trimIndent(), version = "26.3")
        }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(8, executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
        val rejected = compilerDiagnostics {
            MCFPPStringTest.readFromString("""
                data Box { var number: int? }
                func read(box: Box): int { box.number = null; return box.number; }
                func main(){ var box: Box = {number: 4}; read(box); }
            """.trimIndent(), version = "26.3")
        }
        assertTrue(rejected.any { "may be absent" in it }, rejected.joinToString("\n"))
    }

    @Test fun missingAndEmptyValuesHaveDistinctPresenceAndDeletionUpdatesBranches() {
        val diagnostics = compilerDiagnostics {
            MCFPPStringTest.readFromString("""
                data Box { var number: int?; var label: string?; var items: list<int>? }
                func inspect(box: Box): int {
                    var result = 0
                    if(box.number != null){ result += 1; }
                    if(box.label != null){ result += 2; }
                    if(box.items != null){ result += 4; }
                    box.number = null
                    box.number = null
                    if(box.number == null){ result += 8; }
                    box.number = 0
                    if(box.number != null){ result += 16; }
                    return result
                }
                func main(){
                    var full: Box = {number: 0, label: "", items: []}
                    var missing: Box = {}
                    external result = inspect(full) * 100 + inspect(missing)
                }
            """.trimIndent(), version = "26.3")
        }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(3124, executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun existenceAndDeletionUseBackingPositions() {
        MCFPPStringTest.readFromString("""
            data Box {
                var value: int?
            }
            func inspect(box: Box) {
                if (box.value != null) {
                    /say present
                }
                box.value = null
                box.value = null
            }
            func main(){ var box: Box = {}; inspect(box); }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
        val commands = functions.flatMap { it.commands.analyzeAll() }
        assertTrue(commands.any { "if data" in it }, commands.joinToString("\n"))
        assertTrue(commands.any { "data remove" in it }, commands.joinToString("\n"))
        assertTrue(commands.none { "say getter" in it || "say setter" in it }, commands.joinToString("\n"))
    }

    @Test fun deletionCannotReachConstantContents() {
        for (target in listOf("box", "(box as Box)", "((box as any) as Box)")) {
            val diagnostics = compilerDiagnostics {
                MCFPPStringTest.readFromString("""
                    data Box { var value: int? }
                    func main(){ const box: Box = {value: 0}; $target.value = null; }
                """.trimIndent(), version = "26.3")
            }
            assertTrue(diagnostics.any { "const" in it.lowercase() }, diagnostics.joinToString("\n"))
        }
    }

    @Test fun branchMergesAndMutatingCallsInvalidatePresenceProofs() {
        for (mutation in listOf("if(flag){ box.value = null; };", "box.clear();")) {
            val diagnostics = compilerDiagnostics {
                MCFPPStringTest.readFromString("""
                    data Box { var value: int?; func clear(){ this.value = null; } }
                    func read(box: Box, flag: bool): int {
                        if(box.value != null){ $mutation return box.value; }
                        return 0
                    }
                    func main(){ var box: Box = {value: 1}; read(box, true); }
                """.trimIndent(), version = "26.3")
            }
            assertTrue(diagnostics.any { "may be absent" in it }, diagnostics.joinToString("\n"))
        }
    }
}
