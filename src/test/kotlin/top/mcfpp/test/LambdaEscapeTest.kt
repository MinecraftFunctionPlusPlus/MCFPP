package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class LambdaEscapeTest {
    @Test
    fun factoriesCopiesAndNestedReturnsOwnTheirEnvironmentsAfterFramesAreReused() {
        MCFPPStringTest.readFromString(Files.readString(Path.of("examples/lambda/escape/main.mcfpp")), version = "26.3")
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        val main = functions.getValue("main").single()
        val first = assertIs<FunctionValue>(main.scope.getVar("first"))
        val second = assertIs<FunctionValue>(main.scope.getVar("second"))
        assertNotNull(first.knownTarget())
        assertNotNull(second.knownTarget())
        assertNull(StorageAccess.snapshot(first), "A runtime factory result must not revive a constant from its prototype")
        assertNotEquals(StorageAccess.ensure(first).place, StorageAccess.ensure(second).place)
        assertTrue(StorageAccess.ensure(first).path.toCommandPart().toString().endsWith(".first"))
        assertTrue(StorageAccess.ensure(second).path.toCommandPart().toString().endsWith(".second"))
        val commands = main.commands.analyzeAll()
        val call = commands.indexOf("function ${functions.getValue("make").single().namespaceID}")
        assertTrue(call >= 0, "The source factory must actually execute")
        val copy = commands.indices.firstOrNull { index -> index > call &&
            commands[index].startsWith("data modify storage mcfpp:system stack_frame[1].") &&
            commands[index].endsWith("set from storage mcfpp:system stack_frame[0].return") }
        val release = commands.indices.firstOrNull { it > call && commands[it] == "data remove storage mcfpp:system stack_frame[0]" }
        assertTrue(copy != null && release != null && copy < release, "Copy full return payload before releasing the factory frame")
        LambdaCallTest().export("escape", linkedMapOf(
            "first" to 3, "second" to 8, "copied" to 3, "copiedFactory" to 21,
            "held" to 3, "firstAgain" to 3, "late" to 50, "nested" to 21,
            "otherNested" to 41, "innerAgain" to 21, "outerAgain" to 21,
            "reference" to 10, "noise" to 9900, "noiseAgain" to 7700, "reuses" to 2
        ))
    }
}
