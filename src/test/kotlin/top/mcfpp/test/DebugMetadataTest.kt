package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import top.mcfpp.cli.ProjectCli
import top.mcfpp.debug.DebugValuePaths
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import kotlin.test.*
import kotlin.test.Test

class DebugMetadataTest {
    @Test fun debugBuildPublishesExecutableSourceLocationsAndRuntimeVariables() {
        val root = Files.createTempDirectory("mcfpp-debug-metadata-")
        try {
            Files.writeString(root.resolve("mcfpp.toml"), """
                schema = 1
                name = "debug"
                namespace = "debug_test"
                version = "26.3"
                sourcePath = "src"
                targetPath = "build"
                args = ["-ignoreStdLib"]
            """.trimIndent())
            Files.createDirectories(root.resolve("src"))
            val source = root.resolve("src/main.mcfpp")
            Files.writeString(source, "func main(){\nvar value = 1\nvalue += 2\nvar decimal:float=1.5\ndecimal+=0.5\n/say done\n}\n")
            val output = StringWriter()
            val writer = PrintWriter(output, true)
            val result = ProjectCli.run(arrayOf("build", "--debug-info"), writer, writer, root)
            assertEquals(0, result, output.toString())
            val metadata = JSON.parseObject(Files.readString(root.resolve("build/debug/mcfpp.debug.json")))
            assertEquals(1, metadata.getIntValue("schema"))
            assertEquals("26.3", metadata.getString("version"))
            assertTrue(metadata.getJSONObject("sources").containsKey(source.toString()))
            val main = metadata.getJSONObject("functions").getJSONObject("debug_test:main")
            val locations = main.getJSONArray("locations").map { it as JSONObject }
            assertTrue(locations.any { it.getJSONObject("point").getIntValue("line") == 3 && it.getBooleanValue("entry") })
            val variable = locations.first { it.getJSONObject("point").getIntValue("line") == 3 }
                .getJSONObject("point").getJSONArray("variables").map { it as JSONObject }.single { it.getString("name") == "value" }
            assertEquals("score", variable.getString("kind"))
            val decimal = locations.first { it.getJSONObject("point").getIntValue("line") == 5 }
                .getJSONObject("point").getJSONArray("variables").map { it as JSONObject }.single { it.getString("name") == "decimal" }
            assertEquals("storage mcfpp:system", decimal.getString("source"))
            val commands = Files.readAllLines(root.resolve("build/debug/debug/data/debug_test/function/main.mcfunction"))
            assertTrue(commands.any { it.contains("scoreboard players set") && it.endsWith(" 1") }, commands.joinToString("\n"))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun watchPathsReadFieldsAndIndicesAndRejectCallsOrAssignments() {
        val values = mapOf("item" to mapOf("values" to listOf(2, 3)))
        assertEquals(3, DebugValuePaths.read(values, "item.values[1]"))
        assertEquals(3, DebugValuePaths.read(values, "item.values[-1]"))
        assertFails { DebugValuePaths.read(values, "item.clear()") }
        assertFails { DebugValuePaths.read(values, "item = 5") }
    }
}
