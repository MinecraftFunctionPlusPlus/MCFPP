package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataConstantTest {
    private fun modes(action: () -> Unit) {
        val previous = CompileSettings.foldIRConstants
        try {
            for (enabled in listOf(true, false)) {
                CompileSettings.foldIRConstants = enabled
                action()
            }
        } finally { CompileSettings.foldIRConstants = previous }
    }

    private fun compile(source: String) = top.mcfpp.test.util.compilerDiagnostics {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
    }

    @Test fun completeValuesAndIndependentCopiesWorkWithEitherFoldingMode() = modes {
        compile("""
            func main(){
                const scalar = 2 + 3
                const source = [scalar, 6]
                var copy = source as list<int>
                copy[0] = 9
                external result = source[0] * 10 + copy[0]
            }
        """)
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() }
        val machine = ScoreCommandExecutor(main.commands.analyzeAll(), functions)
        assertEquals(59, machine.read(main.scope.getVar("result") as MCInt))
        assertNotNull(StorageAccess.snapshot(main.scope.getVar("scalar")!!))
    }

    @Test fun runtimeAndMissingInitializersAreRejected() = modes {
        for (body in listOf("const value: int;", "external input = 3; const value = input;",
            "external input = 3; const value = [1, input];")) {
            val diagnostics = compile("func main(){ $body }")
            assertTrue(diagnostics.any { "complete compile-time initializer" in it }, diagnostics.joinToString("\n"))
        }
        compile("data Box { const value: int = input; new(input: int){return {value: input};} }\nfunc main(){}")
        assertTrue(Project.errorCount > 0)
        compile("data Box { var value: int }\nfunc main(){ external input = 3; const box: Box = {value: input}; }")
        assertTrue(Project.errorCount > 0)
        compile("data Box { var value: int }\nfunc main(){ external input = 3; var copy = input; const box: Box = {value: copy}; }")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun nestedWritesAndViewsCannotModifyConstantStorage() = modes {
        for (write in listOf("source[0] = 7", "source.add(7)")) {
            val diagnostics = compile("func main(){ const source = [1, 2]; $write; }")
            assertTrue(diagnostics.any { "const" in it.lowercase() }, diagnostics.joinToString("\n"))
        }
        for (write in listOf("source[\"entry\"][0] = 7", "source.clear()")) {
            val diagnostics = compile("func main(){ const source = {entry:[1]}; $write; }")
            assertTrue(diagnostics.any { "const" in it.lowercase() }, diagnostics.joinToString("\n"))
        }
        compile("""
            data Box { var items: list<int> }
            func main(){ const box: Box = {items: [1,2]}; (box as Box).items[0] = 7; }
        """)
        assertTrue(Project.errorCount > 0)
        compile("data Box { var items: list<int> }\nfunc main(){ const box: Box = {items:[1]}; ((box as any) as Box).items[0] = 7; }")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun explicitFixedFieldsMatchTheTemplateAndRemainProtectedInCopies() = modes {
        compile("""
            data Box { const schema: int = 2 + 3; var value: int }
            func main(){ var box: Box = {schema: 5, value: 1}; box.value = 4; external result = box.schema * 10 + box.value; }
        """)
        assertEquals(0, Project.errorCount)
        val template = GlobalScope.getTemplate("default.test", "Box")!!
        assertNotNull(template.scope.getVar("schema")!!.declarationConstant)
        compile("""
            data Box { const schema: int = 5; var value: int }
            func main(){ var box: Box = {schema: 5, value: 1}; box.schema = 8; }
        """)
        assertTrue(Project.errorCount > 0)
        compile("""
            data Box { const schema: int = 5; var value: int }
            func main(){ var box: Box = {schema: 6, value: 1}; }
        """)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun parentAndErasedViewsReadTheActualFixedFieldValue() {
        compile("""
            data Parent { const schema = 1 }
            data Child { const schema = 8 }
            func read(value: Parent): int { return value.schema; }
            func main(){
                var child = Child()
                external result = read(child as Parent) * 10 + ((child as any) as Parent).schema
                var copied = child as Parent
                external copiedResult = read(copied)
            }
        """)
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val machine = top.mcfpp.test.util.executePublishedFunctions(main)
        assertEquals(88, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(8, machine.read(main.scope.getVar("copiedResult") as MCInt))
    }

    @Test fun recursiveCallsKeepUntrustedDataViewsAsRuntimeReads() {
        val diagnostics = compile("""
            data Parent { const schema = 1 }
            data Child { const schema = 8 }
            func read(value: Parent, count: int): int {
                if(count > 0){ return read(value, count - 1); }
                return value.schema;
            }
            func main(){ var child = Child(); external result = read(child as Parent, 1); }
        """)
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(8, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun recursiveUntrustedLayoutsDoNotFreezeTheFirstArgumentsListLength() {
        val diagnostics = compile("""
            data Parent { const schema = 1; var items: list<int> }
            data Child { const schema = 8; var items: list<int> }
            func last(value: Parent, sibling: list<int>, count: int): int {
                if(count > 0){
                    var next: Child = {schema: 8, items: [7]}
                    return last(next as Parent, [9], count - 1);
                }
                return value.items[-1] * 10 + sibling[-1];
            }
            func main(){
                var child: Child = {schema: 8, items: [2, 3]}
                external result = last(child as Parent, [4, 5], 1);
            }
        """)
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(79, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun aFixedFieldCanInitializeAConstantWhenItsSiblingIsRuntime() = modes {
        val diagnostics = compile("""
            data Box { const schema = 5; var value: int }
            func main(){ external input = 3; var box: Box = {schema: 5, value: input}; const schema = box.schema; external result = schema; }
        """)
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
    }
}
