package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import top.mcfpp.cli.ProjectCli
import top.mcfpp.project.ProjectTemplates
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import kotlin.test.*
import kotlin.test.Test

class IdeProjectModelTest {
    @Test fun exportsStandaloneJsonAndReusesLockedOfflineState() {
        val root = Files.createTempDirectory("mcfpp-ide-model-")
        try {
            ProjectTemplates.create(root, false)
            fun export(vararg flags: String): Pair<Int, String> {
                val output = StringWriter()
                val errors = StringWriter()
                val exit = ProjectCli.run(arrayOf("ide-model", "--json", *flags), PrintWriter(output, true), PrintWriter(errors, true), root)
                assertEquals(0, exit, errors.toString())
                return exit to output.toString()
            }
            val model = JSON.parseObject(export().second)
            assertEquals(1, model.getIntValue("schema"))
            assertEquals(root.toString(), model.getString("root"))
            assertEquals(root.resolve("src/main/mcfpp").toString(), model.getString("sourcePath"))
            assertFalse(model.getBooleanValue("mniEnabled"))
            assertTrue(Files.exists(root.resolve(".mcfpp/ide-model.json")))
            val lock = Files.readAllBytes(root.resolve("mcfpp.lock"))
            JSON.parseObject(export("--locked", "--offline").second)
            assertContentEquals(lock, Files.readAllBytes(root.resolve("mcfpp.lock")))
        } finally { root.toFile().deleteRecursively() }
    }
}
