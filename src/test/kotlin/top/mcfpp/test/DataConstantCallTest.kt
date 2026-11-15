package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.MCInt
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.WritesReceiver
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataConstantCallTest {
    @Test fun indirectInstanceWritesPreserveTheFixedSibling() {
        val diagnostics = compilerDiagnostics {
            MCFPPStringTest.readFromString("""
                data Box {
                    const schema = 5
                    var value: int
                    func increment(){ this.value += 1; }
                    func twice(){ this.increment(); this.increment(); }
                }
                func main(){ var box: Box = {value: 1}; box.twice(); external result = box.schema * 10 + box.value; }
            """.trimIndent(), version = "26.3")
        }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(53, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun nativeOrdinaryArgumentsAreIndependentValues() {
        MCFPPStringTest.readFromString("func main(){ const value = 1; }", version = "26.3")
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        Function.currFunction = main
        val value = main.scope.getVar("value")!!
        val before = StorageAccess.snapshot(value)
        val function = NativeFunction("changeArgument", "fixture", DataConstantProvider::class.java.getMethod("changeArgument", NativeCallContext::class.java))
        function.appendNormalParam(MCFPPBaseType.Int, "value")
        val diagnostics = compilerDiagnostics { function.invoke(emptyList(), listOf(value), null) }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        assertEquals(before, StorageAccess.snapshot(value))
    }

    @Test fun mutableObjectSiblingAndOrdinaryParameterCopiesRemainWritable() {
        val diagnostics = compilerDiagnostics {
            MCFPPStringTest.readFromString("""
                object data State {
                    const schema = [1, 2]
                    var counter: int = 0
                    func increment(){ State.counter += 1; }
                }
                data Box {
                    const schema = 5
                    var value: int
                    func touchGlobal(){ State.counter += 1; }
                }
                func change(copy: Box){ copy.value = 8; }
                func main(){
                    State.counter = 0
                    State.increment()
                    State.increment()
                    const box: Box = {value: 1}
                    box.touchGlobal()
                    change(box)
                    external result = State.counter * 10 + box.value
                }
            """.trimIndent(), version = "26.3")
        }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(31, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun nativeProviderIsNotEnteredForProtectedReceiver() {
        MCFPPStringTest.readFromString("func main(){ const value = 1; }", version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        Function.currFunction = main
        val value = main.scope.getVar("value")!!
        val before = StorageAccess.snapshot(value)
        val commands = main.commands.analyzeAll()
        DataConstantProvider.calls = 0
        val function = NativeFunction("mutate", "fixture", DataConstantProvider::class.java.getMethod("mutate", NativeCallContext::class.java))
        function.caller = MCFPPBaseType.Int
        function.invoke(emptyList(), emptyList(), value)
        assertTrue(Project.errorCount > 0)
        assertEquals(0, DataConstantProvider.calls)
        assertEquals(before, StorageAccess.snapshot(value))
        assertEquals(commands, main.commands.analyzeAll())
    }

    @Test fun queriesAndOutputAreAllowedButDirectAndIndirectWritesAreRejected() {
        val declaration = """
            data Box {
                var value: int { get { var result = field; return result; }; set { field = value; } }
                func read(): int { return this.value }
                func localRead(): int { var copy = this.value; copy += 1; return copy; }
                func recursiveRead(n: int): int { if(n > 0){ return this.recursiveRead(n - 1); }; return this.value; }
                var sneaky: int { get { this.value = 9; return this.value; } }
                func print() {
                    /say read
                }
                func mutate() { this.value = 9 }
                func indirect() { this.mutate() }
                func recursive(n: int) { if(n > 0){ this.recursive(n - 1); }; this.value = 9; }
            }
        """.trimIndent()
        for (call in listOf("box.read()", "box.localRead()", "box.recursiveRead(2)", "box.print()", "box.mutate()", "box.indirect()", "box.recursive(2)", "box.sneaky")) {
            val diagnostics = compilerDiagnostics {
                MCFPPStringTest.readFromString("$declaration\nfunc main(){ const box: Box = {value: 1}; $call; }", version = "26.3")
            }
            if (call in listOf("box.read()", "box.localRead()", "box.recursiveRead(2)", "box.print()")) assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
            else assertTrue(diagnostics.any { "constant" in it.lowercase() }, diagnostics.joinToString("\n"))
        }
    }
}

object DataConstantProvider {
    var calls = 0
    @JvmStatic @WritesReceiver @MNIFunction(caller = "int")
    fun mutate(context: NativeCallContext) {
        calls++
        Function.addCommand("say provider-entered")
    }
    @JvmStatic @NoExternalWrites @MNIFunction(normalParams = ["int"])
    fun changeArgument(context: NativeCallContext) {
        context.withArguments { StorageAccess.write(it.single(), MCInt(8)) }
    }
}
