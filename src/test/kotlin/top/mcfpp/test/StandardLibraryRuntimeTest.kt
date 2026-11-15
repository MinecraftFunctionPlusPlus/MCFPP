package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.Project
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinWriter
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exports production datapacks and DSB assertions for the new observable behavior. */
class StandardLibraryRuntimeTest {
    @Test fun publishedExamplesExportForExecution() {
        val player = mapOf("player" to mapOf("name" to "Steve"))
        for (example in listOf("rewards", "area-skill", "score-game")) {
            var source = Files.readString(Path.of("examples/stdlib/$example/src/main.mcfpp"))
            if (example == "score-game") source += """

                func inspectRules() {
                    var kept = GameRules.keepInventory.get();
                    GameRules.keepInventory.set(false);
                    var disabled = GameRules.keepInventory.get();
                    GameRules.keepInventory.set(true);
                    GameRules.randomTickSpeed.set(5);
                    var speed = GameRules.randomTickSpeed.get();
                    /data modify storage stdlib:results kept set from storage mcfpp:system stack_frame[0].kept
                    /data modify storage stdlib:results disabled set from storage mcfpp:system stack_frame[0].disabled
                    /data modify storage stdlib:results speed set from storage mcfpp:system stack_frame[0].speed
                }
            """.trimIndent()
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertEquals(0, Project.errorCount, example)
            val extra = when (example) {
                "rewards" -> listOf("reward: ready", "31", "10").map { value -> mapOf("output" to mapOf("command" to "tellraw", "target" to "Steve", "text" to value, "count" to 1)) }
                "area-skill" -> listOf(mapOf("entity" to mapOf("type" to "minecraft:zombie", "tag" to "stdlib_skill_hit", "count" to 2,
                    "nbt" to mapOf("path" to "Health", "equals" to 18))))
                else -> listOf(mapOf("score" to mapOf("target" to "Steve", "objective" to "stdlib_points", "equals" to 10)),
                    mapOf("output" to mapOf("command" to "title actionbar", "target" to "Steve", "text" to "获得 5 点积分", "count" to 2)))
            }
            val before = when (example) {
                "area-skill" -> listOf("summon minecraft:zombie 1 0 1 {Tags:[\"stdlib_skill_target\"],Health:20.0f}",
                    "summon minecraft:zombie 3 0 1 {Tags:[\"stdlib_skill_target\"],Health:20.0f}")
                "score-game" -> listOf("function default.test:setup")
                else -> emptyList()
            }
            val after = when (example) {
                "area-skill" -> listOf("function default.test:main")
                "score-game" -> listOf("function default.test:main", "function default.test:inspect_rules")
                else -> emptyList()
            }
            export("example-$example", if (example == "score-game") mapOf("kept" to 1, "disabled" to 0, "speed" to 5) else emptyMap(),
                before, after, listOf(player), extra)
        }
    }

    @Test fun commandsAndEntityContextsExportForExecution() {
        MCFPPStringTest.readFromString("""
            import mcfpp.lang:*
            import mcfpp.minecraft:*
            import mcfpp.minecraft.std:*
            import mcfpp.minecraft.resource:*
            import mcfpp.minecraft.other:*
            func main() {
                /scoreboard players set inherited_space mcfpp_default 0
                /scoreboard players set entity_space mcfpp_default 0
                /scoreboard players set skipped mcfpp_default 0
                var targets = @e.tag("stdlib_subject");
                var count = targets.count();
                targets.forEach { ctx ->
                    /execute if block ~ ~ ~ minecraft:gold_block run scoreboard players add inherited_space mcfpp_default 1
                }
                targets.forEachAt { ctx ->
                    /execute if block ~ ~-1 ~ minecraft:stone run scoreboard players add entity_space mcfpp_default 1
                    ctx.executor.addTag("stdlib_visited");
                    @p.forEach { inner -> ctx.executor.addTag("stdlib_ancestor"); }
                }
                @e.tag("stdlib_missing").forEachAt { ctx ->
                    /scoreboard players add skipped mcfpp_default 1
                }
                /execute if block ~ ~ ~ minecraft:gold_block run scoreboard players add inherited_space mcfpp_default 1
                var tagged = targets.hasTag("stdlib_visited");
                var item = Item(); item.id = "minecraft:stone";
                var slot = Slot.weapon_mainhand;
                var replaced = itemReplace(targets, slot, item, 2);
                var chestSlot = Slot(0, SlotType.container);
                var blockReplaced = itemReplace(0 70 0, chestSlot, item, 4);
                var fromBlock = itemReplaceFrom(targets, slot, 0 70 0, chestSlot);
                var toBlock = itemReplaceFrom(1 70 0, chestSlot, @p, slot);
                var betweenBlocks = itemReplaceFrom(2 70 0, chestSlot, 1 70 0, chestSlot);
                var copied = itemReplaceFrom(targets, slot, @p, slot);
                var modifier = ItemModifier(); modifier.id = "fixture:set_count";
                var modified = itemModify(targets, slot, modifier);
                var blockModified = itemModify(2 70 0, chestSlot, modifier);
                var block = BlockState(); block.name = "minecraft:stone"; block.properties = {};
                var outlined = fillOutline(Area(0 80 0, 2 82 2), block);
                var killed = @e.tag("stdlib_subject").limit(1).kill();
                var remaining = targets.count();
                /data modify storage stdlib:results count set from storage mcfpp:system stack_frame[0].count
                /data modify storage stdlib:results tagged set from storage mcfpp:system stack_frame[0].tagged
                /data modify storage stdlib:results replaced set from storage mcfpp:system stack_frame[0].replaced
                /data modify storage stdlib:results copied set from storage mcfpp:system stack_frame[0].copied
                /data modify storage stdlib:results modified set from storage mcfpp:system stack_frame[0].modified
                /data modify storage stdlib:results blockReplaced set from storage mcfpp:system stack_frame[0].blockReplaced
                /data modify storage stdlib:results fromBlock set from storage mcfpp:system stack_frame[0].fromBlock
                /data modify storage stdlib:results toBlock set from storage mcfpp:system stack_frame[0].toBlock
                /data modify storage stdlib:results betweenBlocks set from storage mcfpp:system stack_frame[0].betweenBlocks
                /data modify storage stdlib:results blockModified set from storage mcfpp:system stack_frame[0].blockModified
                /data modify storage stdlib:results chest set from block 2 70 0 Items
                /data modify storage stdlib:results outlined set from storage mcfpp:system stack_frame[0].outlined
                /data modify storage stdlib:results killed set from storage mcfpp:system stack_frame[0].killed
                /data modify storage stdlib:results remaining set from storage mcfpp:system stack_frame[0].remaining
                /execute store result storage stdlib:results inherited int 1 run scoreboard players get inherited_space mcfpp_default
                /execute store result storage stdlib:results positioned int 1 run scoreboard players get entity_space mcfpp_default
                /execute store result storage stdlib:results skipped int 1 run scoreboard players get skipped mcfpp_default
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        export("commands", mapOf("count" to 2, "tagged" to 1, "remaining" to 1, "inherited" to 3, "positioned" to 2, "skipped" to 0,
            "chest[0].count" to 3,
            "blockReplaced.success" to 1, "fromBlock.success" to 1, "toBlock.success" to 1, "betweenBlocks.success" to 1, "blockModified.success" to 1,
            "replaced.success" to 1, "copied.success" to 1, "modified.success" to 1, "outlined.success" to 1, "outlined.result" to 26, "killed.success" to 1),
            listOf("setblock 0 0 0 minecraft:gold_block", "setblock 1 63 1 minecraft:stone", "setblock 3 63 1 minecraft:stone",
                "setblock 0 70 0 minecraft:chest", "setblock 1 70 0 minecraft:chest", "setblock 2 70 0 minecraft:chest",
                "summon minecraft:armor_stand 1 64 1 {Tags:[\"stdlib_subject\"]}", "summon minecraft:armor_stand 3 64 1 {Tags:[\"stdlib_subject\"]}"),
            setupSteps = listOf(mapOf("player" to mapOf("name" to "Steve", "inventory" to listOf(mapOf("id" to "minecraft:diamond", "count" to 1))))),
            extra = listOf(mapOf("entity" to mapOf("tag" to "stdlib_ancestor", "count" to 1,
                "nbt" to mapOf("path" to "HandItems[0].count", "equals" to 3)))))
        val resource = Path.of("build/stdlib-expansion/commands/pack/data/fixture/item_modifier/set_count.json")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, """[{"function":"minecraft:set_count","count":3}]""")
    }
    @Test fun collectionsStringsAndMapsExportForExecution() {
        val input = "A\"\\中😀"
        val source = """
            import mcfpp.lang:*
            func match(value: int): bool {
                /scoreboard players add stdlib_calls mcfpp_default 1
                return value == 2;
            }
            func fallback(): int {
                /scoreboard players add stdlib_defaults mcfpp_default 1
                return 99;
            }
            func observe(values: list<int>, message: string, start: int, end: int, items: map<int>, key: string) {
                var mapped = values.mapIndexed { index, value -> index + value };
                var offset = 3;
                var captured = values.map { value -> value + offset };
                var flattened = values.flatMap { value -> [value, value] };
                var selected = values.filterNot { it < 2 };
                var total = values.sumOf { it * 2 };
                var unique = values.distinct().reversed();
                var cut = values.take(3).drop(1).slice(0, 1);
                var prefix = values.takeWhile { it < 4 };
                var suffix = values.dropWhile { it < 2 };
                var ro = values.toImmutableList();
                var roTotal = ro.sum();
                var copied = ro.toList();
                var predicate: (int) -> bool = match;
                var hasMatch = values.any(predicate);
                var tried = values.tryFirst(match, { value ->
                    /scoreboard players add stdlib_actions mcfpp_default 1
                });
                var present = values.getOrElse(0, fallback);
                var missing = values.getOrElse(30, fallback);
                var first = values.firstOrElse({ it > 2 }, fallback);
                var last = values.lastOrElse(fallback);
                var empty: list<int> = [];
                var emptyAll = empty.all { it > 0 };
                var emptyNone = empty.none { it > 0 };
                var emptyAny = empty.any { it > 0 };
                var lastIndex = values.indexOfLast { it == 2 };
                var counted = values.count { it == 2 };
                var retained = values.onEach { var copy = it; };
                var changed = total.let { it + 1 }.also { value -> var copy = value; };
                var computed = run { 7 };
                repeat(3) { index ->
                    /scoreboard players add stdlib_repeat mcfpp_default 1
                };
                var length = message.length();
                var bounded = length.coerceIn(0, 4).coerceAtLeast(8).coerceAtMost(7);
                var parsed = "12".toIntOrElse(fallback);
                var invalid = "oops".toIntOrElse(fallback);
                var part = message.substring(start, end);
                var combined = part.contact("!");
                var astral = message.substring(4, 6);
                var backwards = message.substring(5, 2);
                var equal = part == message;
                var existing = items.getOrPut("a", fallback);
                var inserted = items.getOrPut("new.key", fallback);
                var mapKeys = items.keys();
                var mapValues = items.values();
                var mapEntries = items.entries();
                var escapedValue = items.getOrPut(key) { 17 };
                var escapedRead = items.getOrElse(key) { 0 };
                var keysAfter = items.keys();
                /data modify storage stdlib:results mapped set from storage mcfpp:system stack_frame[0].mapped
                /data modify storage stdlib:results captured set from storage mcfpp:system stack_frame[0].captured
                /data modify storage stdlib:results bounded set from storage mcfpp:system stack_frame[0].bounded
                /data modify storage stdlib:results parsed set from storage mcfpp:system stack_frame[0].parsed
                /data modify storage stdlib:results invalid set from storage mcfpp:system stack_frame[0].invalid
                /data modify storage stdlib:results flattened set from storage mcfpp:system stack_frame[0].flattened
                /data modify storage stdlib:results selected set from storage mcfpp:system stack_frame[0].selected
                /data modify storage stdlib:results total set from storage mcfpp:system stack_frame[0].total
                /data modify storage stdlib:results unique set from storage mcfpp:system stack_frame[0].unique
                /data modify storage stdlib:results cut set from storage mcfpp:system stack_frame[0].cut
                /data modify storage stdlib:results prefix set from storage mcfpp:system stack_frame[0].prefix
                /data modify storage stdlib:results suffix set from storage mcfpp:system stack_frame[0].suffix
                /data modify storage stdlib:results roTotal set from storage mcfpp:system stack_frame[0].roTotal
                /data modify storage stdlib:results copied set from storage mcfpp:system stack_frame[0].copied
                /data modify storage stdlib:results present set from storage mcfpp:system stack_frame[0].present
                /data modify storage stdlib:results missing set from storage mcfpp:system stack_frame[0].missing
                /data modify storage stdlib:results first set from storage mcfpp:system stack_frame[0].first
                /data modify storage stdlib:results last set from storage mcfpp:system stack_frame[0].last
                /data modify storage stdlib:results emptyAll set from storage mcfpp:system stack_frame[0].emptyAll
                /data modify storage stdlib:results emptyNone set from storage mcfpp:system stack_frame[0].emptyNone
                /data modify storage stdlib:results emptyAny set from storage mcfpp:system stack_frame[0].emptyAny
                /data modify storage stdlib:results lastIndex set from storage mcfpp:system stack_frame[0].lastIndex
                /data modify storage stdlib:results counted set from storage mcfpp:system stack_frame[0].counted
                /data modify storage stdlib:results changed set from storage mcfpp:system stack_frame[0].changed
                /data modify storage stdlib:results computed set from storage mcfpp:system stack_frame[0].computed
                /data modify storage stdlib:results length set from storage mcfpp:system stack_frame[0].length
                /data modify storage stdlib:results combined set from storage mcfpp:system stack_frame[0].combined
                /data modify storage stdlib:results astral set from storage mcfpp:system stack_frame[0].astral
                /data modify storage stdlib:results backwards set from storage mcfpp:system stack_frame[0].backwards
                /data modify storage stdlib:results equal set from storage mcfpp:system stack_frame[0].equal
                /data modify storage stdlib:results existing set from storage mcfpp:system stack_frame[0].existing
                /data modify storage stdlib:results inserted set from storage mcfpp:system stack_frame[0].inserted
                /data modify storage stdlib:results mapKeys set from storage mcfpp:system stack_frame[0].mapKeys
                /data modify storage stdlib:results mapValues set from storage mcfpp:system stack_frame[0].mapValues
                /data modify storage stdlib:results mapEntries set from storage mcfpp:system stack_frame[0].mapEntries
                /data modify storage stdlib:results keysAfter set from storage mcfpp:system stack_frame[0].keysAfter
                /data modify storage stdlib:results escapedRead set from storage mcfpp:system stack_frame[0].escapedRead
                /data modify storage stdlib:results original set from storage mcfpp:system stack_frame[0].values
            }
            func numbers(values: list<float>, value: float) {
                var bounded = value.coerceIn(1.0, 3.0);
                var total = values.sum();
                /data modify storage stdlib:results floatBounded set from storage mcfpp:system stack_frame[0].bounded
                /data modify storage stdlib:results floatTotal set from storage mcfpp:system stack_frame[0].total
            }
            func main() {
                /scoreboard players set stdlib_calls mcfpp_default 0
                /scoreboard players set stdlib_defaults mcfpp_default 0
                /scoreboard players set stdlib_repeat mcfpp_default 0
                /scoreboard players set stdlib_actions mcfpp_default 0
                var items = {entries: [{key: "a", value: 3}]} as map<int>;
                observe([1, 2, 2, 4], ${JSON.toJSONString(input)}, -9, 999, items, ${JSON.toJSONString("odd.\"\\中")});
                numbers([1.5, 2.5], 4.5);
                /execute store result storage stdlib:results calls int 1 run scoreboard players get stdlib_calls mcfpp_default
                /execute store result storage stdlib:results defaults int 1 run scoreboard players get stdlib_defaults mcfpp_default
                /execute store result storage stdlib:results repeats int 1 run scoreboard players get stdlib_repeat mcfpp_default
                /execute store result storage stdlib:results actions int 1 run scoreboard players get stdlib_actions mcfpp_default
            }
        """.trimIndent()
        MCFPPStringTest.readFromString(source, version = "26.3")
        assertEquals(0, Project.errorCount)
        export("values", mapOf(
            "mapped" to listOf(1, 3, 4, 7), "flattened" to listOf(1, 1, 2, 2, 2, 2, 4, 4), "selected" to listOf(2, 2, 4),
            "captured" to listOf(4, 5, 5, 7), "bounded" to 7, "parsed" to 12, "invalid" to 99, "floatBounded" to 3, "floatTotal" to 4,
            "total" to 18, "unique" to listOf(4, 2, 1), "cut" to listOf(2), "prefix" to listOf(1, 2, 2), "suffix" to listOf(2, 2, 4),
            "roTotal" to 9, "copied" to listOf(1, 2, 2, 4), "present" to 1, "missing" to 99, "first" to 4, "last" to 4,
            "emptyAll" to 1, "emptyNone" to 1, "emptyAny" to 0, "lastIndex" to 2, "counted" to 2, "changed" to 19, "computed" to 7,
            "length" to input.length, "combined" to "$input!", "astral" to "😀", "backwards" to "", "equal" to 1,
            "existing" to 3, "inserted" to 99, "mapKeys" to listOf("a", "new.key"), "mapValues" to listOf(3, 99),
            "mapEntries" to listOf(mapOf("key" to "a", "value" to 3), mapOf("key" to "new.key", "value" to 99)),
            "escapedRead" to 17, "keysAfter" to listOf("a", "new.key", "odd.\"\\中"), "original" to listOf(1, 2, 2, 4), "calls" to 4, "defaults" to 3, "repeats" to 3, "actions" to 1
        ))
    }

    @Test fun higherOrderHelpersSurviveFreshLibraryImport() {
        val previous = Project.config.includes
        try {
            MCFPPStringTest.readFromString("""
                namespace fixture.stdlib
                func transform(values: list<int>): int {
                    return values.filterNot { it < 2 }.mapIndexed { index, value -> value + index }.sum();
                }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val directory = Files.createTempDirectory(Files.createDirectories(Path.of("build/stdlib-expansion")), "library-")
            LibBinWriter.write(directory.toString())
            Project.config.includes = arrayListOf(directory.toString())
            MCFPPStringTest.readFromString("""
                import fixture.stdlib:*
                func main() {
                    var total = transform([1, 2, 3]);
                    /data modify storage stdlib:results total set from storage mcfpp:system stack_frame[0].total
                }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            export("imported", mapOf("total" to 6))
        } finally { Project.config.includes = previous }
    }

    private fun export(name: String, expected: Map<String, Any>, before: List<String> = emptyList(), after: List<String> = emptyList(),
                       setupSteps: List<Map<String, Any>> = emptyList(), extra: List<Map<String, Any>> = emptyList()) {
        val root = Files.createDirectories(Path.of("build/stdlib-expansion/$name"))
        Project.config.name = "pack"; Project.config.copyImport = true
        DatapackCreator.createDatapack(root.toAbsolutePath().toString())
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val commands = listOf("data modify storage mcfpp:system stack_frame set value [{}]") + before +
            listOf("function ${main.namespaceID}") + after + listOf("data remove storage mcfpp:system stack_frame[0]")
        val assertions = expected.map { (key, value) -> mapOf("storage" to mapOf("id" to "stdlib:results", "path" to key, "equals" to value)) } +
            mapOf("storage" to mapOf("id" to "mcfpp:system", "path" to "stack_frame", "equals" to emptyList<Any>())) + extra
        Files.writeString(root.resolve("$name.dps.json"), JSON.toJSONString(mapOf("version" to "26.3", "unsupported" to "error",
            "failOnMissingResources" to true, "packs" to listOf("./pack"), "steps" to (listOf(mapOf("load" to true)) + setupSteps + mapOf("commands" to commands)), "assertions" to assertions), JSONWriter.Feature.PrettyFormat))
    }
}
