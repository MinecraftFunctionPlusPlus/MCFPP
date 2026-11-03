package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class LambdaReentryTest {
    @Test
    fun outerEnvironmentsArgumentsLocalsAndEarlierResultsSurviveBoundedReentry() {
        MCFPPStringTest.readFromString(Files.readString(Path.of("examples/lambda/reentry/main.mcfpp")), version = "26.3")
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        val commands = functions.values.flatten().flatMap { it.commands.analyzeAll() }
        assertTrue(commands.any { it == "function ${functions.getValue("simple").single().namespaceID}" })
        assertTrue(commands.any { it.contains("function default.test:lambda_") }, "Must enter real closure code")
        LambdaCallTest().export("reentry", linkedMapOf(
            "simple" to 512, "after" to 529, "shorter" to 304,
            "expression" to 96, "dynamic" to 162, "continued" to 1091,
            "simpleCalls" to 5, "expressionCalls" to 3,
            "dynamicBase" to 1, "dynamicRecursive" to 2,
            "parameterFirst" to 19, "parameterSecond" to 23,
            "parameterCalls" to 6, "parameterCallbackCalls" to 10
        ))
    }
}
