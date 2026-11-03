package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class LambdaCaptureTest {
    @Test
    fun creationSnapshotsNestedCapturesAndCallContextsExportForExecution() {
        MCFPPStringTest.readFromString(Files.readString(Path.of("examples/lambda/capture/main.mcfpp")), version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val probe = assertIs<FunctionValue>(main.scope.getVar("probe"))
        assertNotNull(probe.knownTarget())
        val environment = StorageAccess.ensure(probe).field("environment")
        val fields = environment.data.facts.entries().map { it.key }.filter {
            it.root == environment.place.root && it.path.size == environment.place.path.size + 1 &&
                it.path.take(environment.place.path.size) == environment.place.path
        }.map { (it.path.last() as top.mcfpp.analysis.PathSegment.Field).name }
        assertTrue(fields.isNotEmpty(), "Probe must own an actual explicit capture")
        assertTrue(fields.all { it.startsWith("capture_") },
            "The environment owns explicit lexical captures, not implicit Minecraft context")
        val expected = linkedMapOf("first" to 3, "again" to 3, "latest" to 7, "changedGlobal" to 8,
            "nested" to 101, "relayed" to 3, "shadowResult" to 13, "loopTotal" to 12, "contextCapture" to 11,
            "position" to 1, "executor" to 1, "dimension" to 1)
        LambdaCallTest().export("capture", expected)
        val root = Path.of("build/lambda/sync-capture")
        val first = JSON.parseObject(Files.readString(root.resolve("capture.dps.json")))
        first["steps"] = listOf(mapOf("commands" to listOf(
            "summon minecraft:armor_stand 1 64 1 {Tags:[\"context_a\"]}",
            "execute as @e[tag=context_a,limit=1] at @s run function lambda:run")))
        Files.writeString(root.resolve("capture.dps.json"), JSON.toJSONString(first, JSONWriter.Feature.PrettyFormat))
        val second = JSON.parseObject(JSON.toJSONString(first))
        second["steps"] = listOf(mapOf("commands" to listOf(
            "execute in minecraft:the_nether run summon minecraft:armor_stand 4 64 1 {Tags:[\"context_b\"]}",
            "execute in minecraft:the_nether as @e[tag=context_b,limit=1] at @s run function lambda:run")))
        second.getJSONArray("assertions").forEach { assertion ->
            val storage = (assertion as com.alibaba.fastjson2.JSONObject).getJSONObject("storage")
            if (storage.getString("path") in setOf("executor", "dimension")) storage["equals"] = 2
        }
        Files.writeString(root.resolve("capture-nether.dps.json"), JSON.toJSONString(second, JSONWriter.Feature.PrettyFormat))
    }

    @Test
    fun writesToCapturedLocalsAndTheirContentsAreRejected() {
        for (body in listOf(
            "var local=1; var bad:()->void={local=2;};",
            "var local=1; var bad:()->void={local+=2;};",
            "var values:list<int> = [1]; var bad:()->void={values[0]=2;};"
        )) {
            MCFPPStringTest.readFromString("func main(){ $body }", version = "26.3")
            assertTrue(Project.errorCount > 0, "Expected capture-write diagnostic for $body")
            val commands = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .flatMap { it.commands.analyzeAll() }.joinToString("\n")
            assertTrue(commands.contains("Cannot modify captured value") && commands.contains("read-only"),
                "Expected a semantic capture-write diagnostic, not a syntax/type failure: $body\n$commands")
        }
        MCFPPStringTest.readFromString("func main(){ var bad:()->void={var local=1; var local=2;}; }", version = "26.3")
        assertTrue(Project.errorCount > 0, "Actual duplicate locals must still be rejected")
        assertTrue(GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .flatMap { it.commands.analyzeAll() }.any { it.contains("Duplicate defined variable: local") })
    }
}
