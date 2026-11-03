package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class AsCopyTest {
    @Test fun listViewInitializersAndAssignmentsCopyTheirPayload() = modes {
        val main = compile("""
            func main(){
                var source = [1,2,3]
                var view = source as list<int>
                view[0] = 9
                var assigned = [0]
                assigned = source as list<int>
                assigned[1] = 8
                source[2] = 7
                external original = source[0] * 100 + source[1] * 10 + source[2]
                external copied = view[0] * 100 + view[1] * 10 + view[2]
                external reassigned = assigned[0] * 100 + assigned[1] * 10 + assigned[2]
            }
        """)
        assertNotNull(main.typedIR)
        val machine = execute(main)
        assertEquals(127, machine.read(main.scope.getVar("original") as MCInt))
        assertEquals(923, machine.read(main.scope.getVar("copied") as MCInt))
        assertEquals(183, machine.read(main.scope.getVar("reassigned") as MCInt))
        val source = main.scope.getVar("source")!!
        val copy = main.scope.getVar("view")!!
        assertNotEquals(StorageAccess.ensure(source).place, StorageAccess.ensure(copy).place)
        assertNull(copy.storageBinding!!.view)
        Function.currFunction = main
        val temporary = StorageAccess.view(source, copy.type)
        assertTrue(temporary.isTemp)
        assertSame(source.storageBinding!!.data, temporary.storageBinding!!.data)
        assertEquals(source.storageBinding!!.place, temporary.storageBinding!!.place)
    }

    @Test fun nestedErasedViewsCopyContentsAcrossDeclarationsCallsAndReturns() = modes {
        val main = compile("""
            func project(value: any): dict<list<int>> { return value as dict<list<int>>; }
            func main(){
                var source: any = {items:[1,2]}
                var view: dict<list<int>> = (source as dict<list<int>>)
                const frozen = source as dict<list<int>>
                var returned = project(source as dict<list<int>>)
                view["items"][0] = 9
                frozen["items"][1] = 8
                returned["items"][0] = 7
                external original = source["items"][0]
                external copied = view["items"][0]
                external constantCopy = frozen["items"][1]
                external returnCopy = returned["items"][0]
            }
        """)
        assertNotNull(main.typedIR)
        val machine = execute(main)
        assertEquals(1, machine.read(main.scope.getVar("original") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("copied") as MCInt))
        assertEquals(8, machine.read(main.scope.getVar("constantCopy") as MCInt))
        assertEquals(7, machine.read(main.scope.getVar("returnCopy") as MCInt))
    }

    @Test fun templateViewInitializersCopyThroughTheVisitor() = modes {
        val main = compile("""
            data Payload { var items: list<int>; }
            func main(){
                const source = {items:[1,2]} as Payload
                var view = source as Payload
                view.items[0] = 9
                const scalar = 4
                var scalarCopy = scalar as int
                scalarCopy = 6
                external scalarResult = scalar * 10 + scalarCopy
                external original = source.items[0]
                external copied = view.items[0]
            }
        """)
        assertNull(main.typedIR)
        val machine = execute(main)
        assertEquals(1, machine.read(main.scope.getVar("original") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("copied") as MCInt))
        assertEquals(46, machine.read(main.scope.getVar("scalarResult") as MCInt))
        assertNotSame(main.scope.getVar("source")!!.storageBinding!!.data,
            main.scope.getVar("view")!!.storageBinding!!.data)
    }

    private fun compile(source: String): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })

    private fun modes(action: () -> Unit) {
        val previous = CompileSettings.foldIRConstants
        try {
            for (enabled in listOf(true, false)) {
                CompileSettings.foldIRConstants = enabled
                action()
            }
        } finally { CompileSettings.foldIRConstants = previous }
    }
}
