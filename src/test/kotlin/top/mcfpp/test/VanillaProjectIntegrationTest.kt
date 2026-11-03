package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.api.resources.PackResources
import top.mcfpp.cli.ProjectCli
import top.mcfpp.io.PackAssembly
import top.mcfpp.project.ProjectTemplates
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class VanillaProjectIntegrationTest {
    private fun write(root: Path, name: String, value: String) {
        val file = root.resolve(name); Files.createDirectories(file.parent); Files.writeString(file, value)
    }
    @Test fun buildsBothPacksWithStableCallbacksAndKeepsPreviousOutputOnInvalidResources() {
        val previous = Project.config
        val root = Files.createTempDirectory("mcfpp-vanilla-")
        try {
            ProjectTemplates.create(root, false)
            val config = root.resolve("mcfpp.toml")
            Files.writeString(config, Files.readString(config).replace(root.fileName.toString(), "demo") +
                "\n[entrypoints]\n\"demo:on_use\" = \"demo:handleUse\"\n")
            write(root, "src/main/mcfpp/main.mcfpp", "func handleUse() {\n    print(\"callback\")\n    /function demo:raw\n}\n")
            write(root, "src/main/resources/data/demo/function/raw.mcfunction", "say raw\n")
            write(root, "src/main/resources/data/demo/advancement/use.json", """{"criteria":{"used":{"trigger":"minecraft:impossible"}},"rewards":{"function":"demo:on_use"}}""")
            write(root, "src/main/resources/data/minecraft/tags/function/load.json", """{"replace":true,"values":["demo:raw"]}""")
            write(root, "src/main/resources/assets/demo/items/wand.json", """{"model":{"type":"minecraft:model","model":"demo:item/wand"}}""")
            write(root, "src/main/resources/assets/demo/models/item/wand.json", """{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/stick"}}""")
            fun build(): Pair<Int, String> {
                val log = StringWriter(); val code = ProjectCli.run(arrayOf("build", "--offline", "-level=WARN"), PrintWriter(log, true), PrintWriter(log, true), root)
                return code to log.toString()
            }
            val first = build(); assertEquals(0, first.first, first.second)
            val data = root.resolve("build/demo/data")
            val wrapper = Files.readString(data.resolve("demo/function/on_use.mcfunction"))
            assertTrue(wrapper.contains("function demo:handle_use"), wrapper)
            assertTrue(wrapper.contains("prepend value") && wrapper.contains("remove"), wrapper)
            val body = Files.readString(data.resolve("demo/function/handle_use.mcfunction"))
            assertFalse(body.contains("prepend value"), body)
            val load = JSON.parseObject(Files.readString(data.resolve("minecraft/tags/function/load.json"))).getJSONArray("values")
            assertTrue(load.contains("demo:load") && load.contains("demo:raw"), load.toString())
            val resourceMeta = JSON.parseObject(Files.readString(root.resolve("build/demo-resources/pack.mcmeta")))
            assertEquals(64, resourceMeta.getJSONObject("pack").getIntValue("pack_format"))
            assertEquals(81, JSON.parseObject(Files.readString(root.resolve("build/demo/pack.mcmeta"))).getJSONObject("pack").getIntValue("pack_format"))
            val previousRaw = Files.readString(data.resolve("demo/function/raw.mcfunction"))
            write(root, "src/main/resources/data/demo/function/raw.mcfunction", "function demo:missing\n")
            val missing = build(); assertNotEquals(0, missing.first, missing.second)
            assertEquals(previousRaw, Files.readString(data.resolve("demo/function/raw.mcfunction")))
            write(root, "src/main/resources/data/demo/function/raw.mcfunction", "say raw\n")
            write(root, "src/main/resources/data/demo/function/handle_use.mcfunction", "say collision\n")
            val collision = build(); assertNotEquals(0, collision.first)
            assertEquals(body, Files.readString(data.resolve("demo/function/handle_use.mcfunction")))
            assertTrue(Files.exists(root.resolve("build/demo-resources/assets/demo/items/wand.json")))
            Files.writeString(config, "resourceOverrides = [\"demo/function/handle_use.mcfunction\"]\n" + Files.readString(config))
            val override = build(); assertEquals(0, override.first, override.second)
            assertEquals("say collision\n", Files.readString(data.resolve("demo/function/handle_use.mcfunction")))
            root.resolve("src/main/resources/assets").toFile().deleteRecursively()
            val noAssets = build(); assertEquals(0, noAssets.first, noAssets.second)
            assertFalse(Files.exists(root.resolve("build/demo-resources")))
        } finally { Project.config = previous; root.toFile().deleteRecursively() }
    }

    @Test fun typedReferencesKeepCommentsTextAndOptionalTagsOutOfMissingChecks() {
        val refs = PackResources.references("# function demo:ignored\nsay function demo:ignored\nexecute if predicate demo:ready run function #demo:actions\nschedule function demo:later 1t\n", PackResources.Key("function", "demo:raw"), false)
        assertEquals(listOf("predicate", "tag/function", "function"), refs.map { it.key().kind() })
        val tag = PackResources.references("""{"values":[{"id":"demo:optional","required":false},"demo:required"]}""", PackResources.Key("tag/function", "demo:load"), false)
        assertTrue(tag.first().optional()); assertFalse(tag.last().optional())
        assertFalse(PackResources.validId("demo:../outside"))
        val meta = JSON.parseObject(PackAssembly.resourceMetadata("26.3", "test")).getJSONObject("pack")
        assertEquals(listOf(97, 1), meta.getJSONArray("min_format").toList())
    }
}
