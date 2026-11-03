package top.mcfpp.test

import top.mcfpp.cli.ProjectCli
import top.mcfpp.io.DatapackLinker
import top.mcfpp.project.ProjectTemplates
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatapackExperienceTest {
    @Test fun linkingMergesLifecycleTagsAndHonorsAnExplicitReplacement() {
        val path = "minecraft/tags/function/load.json"
        val library = mapOf(path to "{\"values\":[\"lib:initialize\"]}".toByteArray())
        val generated = mapOf(path to "{\"values\":[\"demo:load\"]}".toByteArray())
        val combined = com.alibaba.fastjson2.JSON.parseObject(DatapackLinker.merge(library, generated).getValue(path))
        assertEquals(listOf("lib:initialize", "demo:load"), combined.getJSONArray("values").toList())
        val replacement = mapOf(path to "{\"replace\":true,\"values\":[\"demo:load\"]}".toByteArray())
        val replaced = com.alibaba.fastjson2.JSON.parseObject(DatapackLinker.merge(library, replacement).getValue(path))
        assertEquals(listOf("demo:load"), replaced.getJSONArray("values").toList())
    }

    @Test fun linksFunctionTagsResourcesAndDynamicFamiliesWithoutUnusedLibraryFiles() {
        val resources = mapOf(
            "demo/function/main.mcfunction" to "function #lib:actions\nloot give @s loot lib:reward\n",
            "lib/tags/function/actions.json" to "{\"values\":[\"lib:entry\"]}",
            "lib/function/entry.mcfunction" to "schedule function lib:next 1t\nfunction lib:dispatch/\$(name)\n",
            "lib/function/next.mcfunction" to "say next\n",
            "lib/function/dispatch/a.mcfunction" to "say a\n",
            "lib/function/dispatch/b.mcfunction" to "say b\n",
            "lib/loot_table/reward.json" to "{\"pools\":[]}",
            "lib/function/unused.mcfunction" to "say unused\n"
        ).mapValues { it.value.toByteArray() }
        val plan = DatapackLinker.resolve(resources, mapOf("demo/function/main.mcfunction" to "project function"))
        assertEquals(setOf("lib/function/unused.mcfunction"), plan.omitted)
        assertTrue(plan.retained.getValue("lib/function/dispatch/a.mcfunction").contains("dynamic"))
    }

    @Test fun helloBuildProducesOneSmallPackAndReadablePipelineDumps() {
        val previousConfig = top.mcfpp.Project.config
        val root = Files.createTempDirectory("mcfpp-experience-")
        try {
            ProjectTemplates.create(root, false)
            val output = StringWriter()
            val errors = StringWriter()
            assertEquals(0, ProjectCli.run(arrayOf("build", "--dump-ir", "-level=WARN"),
                PrintWriter(output, true), PrintWriter(errors, true), root), errors.toString())
            val pack = root.resolve("build/${root.fileName}")
            val paths = Files.walk(pack).use { files -> files.filter(Files::isRegularFile).map { pack.relativize(it).toString().replace('\\','/') }.toList() }
            assertTrue(paths.any { it.endsWith("/main.mcfunction") })
            assertFalse(paths.any { it.startsWith("data/math/") || it.startsWith("data/mcfpp.minecraft.resource/") })
            assertFalse(Files.exists(root.resolve("build/Imports")))
            val load = Files.readString(pack.resolve("data/${root.fileName}/function/load.mcfunction"))
            assertTrue(load.contains("scoreboard objectives add mcfpp_pointer_counter"), load)
            assertFalse(load.contains("math:_init") || load.contains("summon item") || load.contains("summon marker"), load)
            val ir = root.resolve("build/ir")
            assertTrue(Files.readString(ir.resolve("01-compiled.ir")).contains("stage=compiled"))
            assertTrue(Files.readString(ir.resolve("02-optimized.ir")).contains("commands {"))
            val linked = Files.readString(ir.resolve("03-linked.ir"))
            assertTrue(linked.contains("omit math/function/") && linked.contains("keep ${root.fileName}/function/main.mcfunction"), linked)
            assertTrue(output.toString().contains("MCFPP IR:"))
        } finally {
            top.mcfpp.Project.config = previousConfig
            root.toFile().deleteRecursively()
        }
    }
}
