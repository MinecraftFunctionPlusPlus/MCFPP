package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NullOperatorsTest {
    private fun result(source: String, expected: Int) {
        val diagnostics = compilerDiagnostics { MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3") }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(expected, executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun elvisDistinguishesMissingAndZeroAndSkipsFallbackCalls() = result("""
        data Box { var value: int?; var calls: int = 0
            func fallback(): int { this.calls += 1; return 7; }
        }
        func read(box: Box): int { var value = box.value ?: box.fallback(); return value + box.calls * 10; }
        func main(){
            var full: Box = {value: 0, calls: 0}
            var empty: Box = {calls: 0}
            external result = read(full) + read(empty)
        }
    """, 17)

    @Test fun safeFieldsAssignmentsAndDeletionShortCircuit() = result("""
        data Inner { var value: int? }
        data Box { var inner: Inner?; var calls: int = 0
            func next(): int { this.calls += 1; return 8; }
        }
        func edit(box: Box): int {
            box.inner?.value = box.next()
            var value = box.inner?.value ?: 2
            box.inner?.value = null
            return value + (box.inner?.value ?: 3) + box.calls * 100
        }
        func main(){
            var full: Box = {inner: {value: 0}, calls: 0}
            var empty: Box = {calls: 0}
            external result = edit(full) + edit(empty)
        }
    """, 116)

    @Test fun uncheckedReadAndElvisReturnWorkInsideFunctions() = result("""
        data Box { var value: int? }
        func read(box: Box): int { var value = box.value ?: return 5; return value + 1; }
        func assumed(box: Box): int { return box.value!!; }
        func main(){
            var full: Box = {value: 4}
            var empty: Box = {}
            external result = read(full) + read(empty) + assumed(full)
        }
    """, 14)

    @Test fun nullableAccessorsAndNullArgumentsAreRejected() {
        for (source in listOf(
            "data Box { var value: int? { get = 1 } }; func main(){}",
            "data Box { var value: int? { set { field = value; } } }; func main(){}",
            "func take(value: any){}; func main(){ take(null); }",
            "func take(value: object){}; func main(){ take(null); }",
            "func take(value: int){}; func main(){ take(null); }",
            "func main(){ print(null); }"
        )) {
            val diagnostics = compilerDiagnostics { MCFPPStringTest.readFromString(source, version = "26.3") }
            assertTrue(Project.errorCount > 0, source)
            assertTrue(diagnostics.any { "cannot be optional" in it || "null is only allowed" in it }, diagnostics.joinToString("\n"))
        }
    }

    @Test fun safeLetAndMemberCallsSkipArguments() = result("""
        data Inner { var value: int; func add(n: int): int { return this.value + n; } }
        data Box { var inner: Inner?; var calls: int = 0
            func next(): int { this.calls += 1; return 2; }
        }
        func read(box: Box): int {
            var a = box.inner?.add(box.next()) ?: 3
            var b = box.inner?.let { it.value + 1 } ?: 4
            return a + b + box.calls * 100
        }
        func main(){
            var full: Box = {inner: {value: 5}, calls: 0}
            var empty: Box = {calls: 0}
            external result = read(full) + read(empty)
        }
    """, 120)

    @Test fun elvisReturnExitsLoopAndNestedArguments() = result("""
        data Box { var value: int? }
        func sum(a: int, b: int): int { return a + b; }
        func read(box: Box): int {
            external again = true
            while(again){ return sum(1, box.value ?: return 8); }
            return 99
        }
        func main(){
            var full: Box = {value: 4}
            var empty: Box = {}
            external result = read(full) + read(empty)
        }
    """, 13)

    @Test fun elvisReturnPropagatesThroughLogicalHelpersAndLambdas() = result("""
        data Box { var value: bool? }
        func read(box: Box, flag: bool): int {
            if(flag && (box.value ?: return 7)){ return 1; }
            return 2
        }
        func main(){
            var full: Box = {value: true}
            var empty: Box = {}
            var callback: (Box) -> int = { box -> var v = box.value ?: return 8; if(v){ return 3; }; 4 }
            external result = read(full, true) + read(empty, true) + read(empty, false) + callback(full) + callback(empty)
        }
    """, 21)

    @Test fun optionalResultsCannotEscapeOrCreatePersistentProofs() {
        for (body in listOf(
            "var value = box.inner?.value;",
            "take(box.inner?.value);",
            "var value = box.value!!; var second = box.value;",
            "var value = box.value ?: null;",
            "box.required = null;",
            "box.inner = null; box.inner?.value = null;"
        )) {
            val diagnostics = compilerDiagnostics { MCFPPStringTest.readFromString("""
                data Inner { var value: int }
                data Box { var inner: Inner?; var value: int?; var required: int }
                func take(value: any){}
                func inspect(box: Box){ $body }
                func main(){ var box: Box = {required: 1}; inspect(box); }
            """.trimIndent(), version = "26.3") }
            assertTrue(Project.errorCount > 0, "$body\n${diagnostics.joinToString("\n")}")
        }
    }

    @Test fun elvisUsesContextualFallbackTypesAndPrecedence() = result("""
        data Inner { var value: int }
        data Box { var inner: Inner?; var items: list<int>?; var value: int? }
        func read(box: Box): int {
            var inner: Inner = box.inner ?: {value: 2}
            var items: list<int> = box.items ?: []
            var number = box.value ?: 2 + 3 * 4
            if(box.value ?: 1 > 0){ number += inner.value; }
            return number + items.size()
        }
        func main(){
            const known: Box = {value: 0}
            const number = known.value ?: 99
            var empty: Box = {}
            external result = read(empty) + number
        }
    """, 16)
}
