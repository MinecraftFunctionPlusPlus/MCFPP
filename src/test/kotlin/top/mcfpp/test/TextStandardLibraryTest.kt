package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.Project
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinWriter
import top.mcfpp.lib.*
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class TextStandardLibraryTest {
    @Test fun componentSerializersProduceValidEscapedObjects() {
        val styled = PlainChatComponent("quote: \" \\ 中").apply {
            styles += ColorStyle(0x00000a)
            styles += UnderlineStyle(true)
            styles += InsertionStyle("\"\\")
            styles += HoverEventShowTextStyle(PlainChatComponent("hint\""))
        }
        val parsed = JSON.parseObject(styled.toCommandPart().toString())
        assertEquals("#00000a", parsed.getString("color"))
        assertTrue(parsed.getBooleanValue("underlined"))
        assertEquals("\"\\", parsed.getString("insertion"))
        for (component in listOf(styled, TranslatableChatComponent("key\"", "fallback\\", listOf(styled)),
            SelectorChatComponent("@a[name=\"A\"]", PlainChatComponent(", ")), KeybindChatComponent("key.jump"))) {
            assertTrue(JSON.isValid(component.toCommandPart().toString()), component.toCommandPart().toString())
        }
        val parts = ListChatComponent().apply { append(styled); append(PlainChatComponent("tail")) }
        assertEquals(2, parts.components.size, "Appending plain text must preserve the preceding style boundary")
        assertTrue(JSON.isValid(ListChatComponent().apply { components += listOf(styled, styled) }.toCommandPart().toString()))
    }

    @Test fun factoriesAndEventsCompileForModernAndLegacyTargets() {
        for (version in listOf("26.3", "1.21.8", "1.21.4")) {
            MCFPPStringTest.readFromString("""
                import mcfpp.lang:*
                func main() {
                    var source = Text.of("button");
                    var styled = source.color("gold").bold().italic(false).underlined().strikethrough(false).obfuscated(false)
                        .insertion("quoted \" \\").hover(Text.of("hint")).suggestCommand("/say \"ok\"");
                    var raw = Text.fromJson("{\"text\":\"Hello\",\"bold\":true}").toJson();
                    var bind = Text.keybind("key.jump");
                    var names = Text.selector("@a", Text.of(", "));
                    var score = Text.score("Steve", "points");
                    var stored = Text.nbtStorage("fixture:ui", "message", false);
                    var translated = Text.translatable("fixture:greeting", [Text.of("player")], "Hi %s");
                    print(styled);
                    print(Text.of("page").changePage(2));
                }
            """.trimIndent(), version = version)
            assertEquals(0, Project.errorCount, version)
            val commands = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .flatMap { it.commands.analyzeAll() }.joinToString("\n")
            assertTrue(if (version == "1.21.4") "clickEvent" in commands && "contents" in commands else "click_event" in commands && "command" in commands, commands)
        }
    }

    @Test fun invalidJsonColorsAndDynamicParsingHaveSourceDiagnostics() {
        for (body in listOf("Text.fromJson(\"{oops\");", "Text.of(\"x\").color(\"#12\");", "Text.of(\"x\").changePage(0);",
            "Text.fromJson(\"{\\\"text\\\":\\\"x\\\",\\\"bold\\\":1}\");")) {
            MCFPPStringTest.readFromString("import mcfpp.lang:*\nfunc main() { $body }", version = "26.3")
            assertTrue(Project.errorCount > 0, body)
        }
        MCFPPStringTest.readFromString("import mcfpp.lang:*\nfunc parse(value: string): text { return Text.fromJson(value); }\nfunc main(){ parse(\"x\"); }", version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun richTextSurvivesFreshLibraryImportAndExportsForExecution() {
        val previous = Project.config.includes
        try {
            MCFPPStringTest.readFromString("""
                namespace fixture.richtext
                import mcfpp.lang:*
                func message(name: string, points: int, enabled: bool): text {
                    var pointsLabel = Text.of(points).bold(enabled);
                    var active = pointsLabel.bold;
                    return Text.join([Text.of(name).color("gold"), pointsLabel, Text.of(active)], Text.of(" / "))
                        .hover(Text.of(name)).suggestCommand(name);
                }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val folder = Files.createTempDirectory(Files.createDirectories(Path.of("build/text-stdlib")), "library-")
            LibBinWriter.write(folder.toString())
            Project.config.includes = arrayListOf(folder.toString())
            val name = "A\"\\中"
            MCFPPStringTest.readFromString("""
                import fixture.richtext:*
                import mcfpp.lang:*
                func main() {
                    var result = message(${JSON.toJSONString(name)}, 7, true);
                    var source = Text.of("left").color("red");
                    var joined = source + Text.of("right");
                    var changed = source.color("blue").bold(false);
                    var mutable = Text.of("mutable");
                    mutable.bold = true;
                    mutable.color = "aqua";
                    var flag = mutable.bold;
                    var raw = Text.fromJson("[{\"text\":\"a\",\"color\":\"red\"},{\"text\":\"b\"}]");
                    var translated = Text.translatable("fixture.greeting", [Text.of("world")], "Hello, %s!");
                    var controls = Text.keybind("key.jump");
                    var audience = Text.selector("@a", Text.of(", "));
                    var liveScore = Text.score("Steve", "text_points");
                    var stored = Text.nbtStorage("text:fixture", "label", false);
                    /data modify storage text:results result set from storage mcfpp:system stack_frame[0].result
                    /data modify storage text:results source set from storage mcfpp:system stack_frame[0].source
                    /data modify storage text:results joined set from storage mcfpp:system stack_frame[0].joined
                    /data modify storage text:results changed set from storage mcfpp:system stack_frame[0].changed
                    /data modify storage text:results raw set from storage mcfpp:system stack_frame[0].raw
                    /data modify storage text:results mutable set from storage mcfpp:system stack_frame[0].mutable
                    /data modify storage text:results flag set from storage mcfpp:system stack_frame[0].flag
                    /data modify storage text:results translated set from storage mcfpp:system stack_frame[0].translated
                    /data modify storage text:results controls set from storage mcfpp:system stack_frame[0].controls
                    /data modify storage text:results audience set from storage mcfpp:system stack_frame[0].audience
                    /data modify storage text:results liveScore set from storage mcfpp:system stack_frame[0].liveScore
                    /data modify storage text:results stored set from storage mcfpp:system stack_frame[0].stored
                    /scoreboard objectives add text_points dummy
                    /scoreboard players set Steve text_points 11
                    /data modify storage text:fixture label set value "stored"
                    print(result);
                    print(joined);
                    print(translated);
                    print(audience);
                    print(liveScore);
                    print(stored);
                }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val root = Files.createDirectories(Path.of("build/text-stdlib/runtime"))
            Project.config.name = "pack"; Project.config.copyImport = true
            DatapackCreator.createDatapack(root.toAbsolutePath().toString())
            val assertions = listOf(
                mapOf("output" to mapOf("command" to "tellraw", "target" to "Steve", "text" to "$name / 7 / true", "count" to 1)),
                mapOf("output" to mapOf("command" to "tellraw", "target" to "Steve", "text" to "leftright", "count" to 1)),
                // DSB ignores translation fallback. The Mojang codec renders this as "Hello, world!".
                mapOf("storage" to mapOf("id" to "text:results", "path" to "translated[0].translate", "equals" to "fixture.greeting")),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "translated[0].fallback", "equals" to "Hello, %s!")),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "translated[0].with[0][0].text", "equals" to "world")),
                mapOf("output" to mapOf("command" to "tellraw", "target" to "Steve", "text" to "Steve", "count" to 1)),
                mapOf("output" to mapOf("command" to "tellraw", "target" to "Steve", "text" to "11", "count" to 1)),
                mapOf("output" to mapOf("command" to "tellraw", "target" to "Steve", "text" to "stored", "count" to 1)),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "source[0].color", "equals" to "red")),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "changed[0].color", "equals" to "blue")),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "changed[0].bold", "equals" to 0)),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "mutable[0].color", "equals" to "aqua")),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "flag", "equals" to 1)),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "result[0].hover_event.value[0].text", "equals" to name)),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "result[0].click_event.command", "equals" to name)),
                mapOf("storage" to mapOf("id" to "text:results", "path" to "raw[0].extra[0].text", "equals" to "b")),
                mapOf("storage" to mapOf("id" to "mcfpp:system", "path" to "stack_frame", "equals" to emptyList<Any>()))
            )
            Files.writeString(root.resolve("text.dps.json"), JSON.toJSONString(mapOf("version" to "26.3", "packs" to listOf("./pack"),
                "unsupported" to "error", "failOnMissingResources" to true,
                "steps" to listOf(mapOf("load" to true), mapOf("player" to mapOf("name" to "Steve")), mapOf("commands" to listOf(
                    "data modify storage mcfpp:system stack_frame set value []", "function default.test:main"))), "assertions" to assertions), JSONWriter.Feature.PrettyFormat))
        } finally { Project.config.includes = previous }
    }
}
