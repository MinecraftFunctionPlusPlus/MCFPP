package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class LambdaDynamicTest {
    @Test
    fun runtimeTargetsUseTheirOwnEnvironmentsReceiversAndSingleArgumentEvaluation() {
        MCFPPStringTest.readFromString(Files.readString(Path.of("examples/lambda/dynamic/main.mcfpp")), version = "26.3")
        assertEquals(0, Project.errorCount)
        val namespace = GlobalScope.localNamespaces.getValue("default.test").scope
        val run = namespace.functions.getValue("runChoice").single()
        val selected = assertIs<FunctionValue>(run.scope.getVar("selected"))
        assertEquals(2, selected.knownTargets().size, "Both runtime source branches must remain callable")
        assertNull(selected.knownTarget())
        LambdaCallTest().export("dynamic", linkedMapOf("first" to 13, "second" to 120,
            "receiverFirst" to 13, "receiverSecond" to 80, "orderFirst" to 12, "orderSecond" to 102,
            "callsFirst" to 2, "callsSecond" to 2, "parameter" to 42, "repeated" to 42))
        // Include actual compiled member/constructor bodies and their reachable helpers.
        val pending = java.util.ArrayDeque<Function>()
        GlobalScope.localNamespaces.values.forEach { local ->
            pending.addAll(local.scope.functions.values.flatten())
            local.scope.template.values.forEach { template ->
                pending.addAll(template.scope.functions.values.flatten())
                pending.addAll(template.constructors)
            }
        }
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        val pack = Path.of("build/lambda/sync-dynamic/pack")
        while (pending.isNotEmpty()) {
            val function = pending.removeFirst()
            if (!seen.add(function)) continue
            pending.addAll(function.compiledFunctions.values)
            pending.addAll(function.child)
            val id = function.namespaceID.toString()
            val file = pack.resolve("data/${id.substringBefore(':')}/function/${id.substringAfter(':')}.mcfunction")
            Files.createDirectories(file.parent)
            Files.writeString(file, function.commands.analyzeAll().joinToString("\n") + "\n")
        }
    }

    @Test
    fun untrustedPersistentCallablePayloadsDoNotBecomeStringAddresses() {
        MCFPPStringTest.readFromString("func main(){ import var persisted: () -> int; persisted(); }", version = "26.3")
        assertTrue(Project.errorCount > 0)
        val diagnostics = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .flatMap { it.commands.analyzeAll() }.joinToString("\n")
        assertTrue(diagnostics.contains("finite compiler-known callable targets"), diagnostics)
        assertTrue(diagnostics.contains("arbitrary string addresses") && diagnostics.contains("persistent callable data"), diagnostics)
    }
}
