package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardLibraryExpansionTest {
    private fun compile(source: String, version: String = "26.3") {
        val diagnostics = top.mcfpp.test.util.compilerDiagnostics {
            MCFPPStringTest.readFromString(source, version = version)
        }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
    }
    @Test fun unsupportedDynamicStringOperationsHaveDiagnostics() {
        MCFPPStringTest.readFromString("""
            func check(value: string): bool { return value.contains("x"); }
            func main() { var found = check("x"); }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun gameRuleSetRejectsWrongValueType() {
        MCFPPStringTest.readFromString("""
            import mcfpp.minecraft:*
            func main() { GameRules.keepInventory.set(1); }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun callbackReferencesRetainContextDependencies() {
        compile("""
            import mcfpp.lang:*
            context(space: PosContext)
            func positive(value: int): bool { return value > 0; }
            func main() {
                var values = [1, 2];
                withPosCtx(0 64 0) { ctx ->
                    var selected = values.filter(positive);
                }
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
    }

    @Test fun defaultTargetExposesTypedGameRules() {
        MCFPPStringTest.readFromString("""
            import mcfpp.minecraft:*
            func main() {
                GameRules.keepInventory.set(true);
                var kept = GameRules.keepInventory.get();
                GameRules.randomTickSpeed.set(3);
            }
        """.trimIndent(), version = "1.21.8")
        assertEquals(0, Project.errorCount)
    }
    @Test fun itemAndBlockCommandSignaturesGenerateTypedCommands() {
        MCFPPStringTest.readFromString("""
            import mcfpp.minecraft:*
            import mcfpp.minecraft.std:*
            import mcfpp.minecraft.other:*
            import mcfpp.minecraft.resource:*
            func main() {
                var item = Item(); item.id = "minecraft:stone";
                var modifier = ItemModifier(); modifier.id = "fixture:modify";
                var slot = Slot.weapon_mainhand;
                var replaced = itemReplace(@a, slot, item, 2);
                var defaultCount = itemReplace(@a, slot, item);
                var copied = itemReplaceFrom(@a, slot, @p, slot);
                var changed = itemModify(@a, slot, modifier);
                var area = Area(0 64 0, 2 66 2);
                var block = BlockState(); block.name = "minecraft:stone"; block.properties = {};
                var outlined = fillOutline(area, block);
                var timing = titleTimes(@a, 5, 30, 5);
                var damaged = damageResult(@e.limit(1), 1.0);
                var cloned = cloneResult(area, 10 64 0);
                var enchantment = Enchant(); enchantment.id = "minecraft:unbreaking";
                var enchanted = enchantResult(@a, enchantment);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val commands = (GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.flatMap { it.commands.analyzeAll() } + Project.macroFunction.values).joinToString("\n")
        assertTrue("item replace entity" in commands)
        assertTrue("item modify entity" in commands)
        assertTrue("outline" in commands)
    }
    @Test fun commandAndEntityExamplesCompile() {
        for (name in listOf("rewards", "area-skill", "score-game")) {
            compile(java.nio.file.Files.readString(java.nio.file.Path.of("examples/stdlib/$name/src/main.mcfpp")), version = "26.3")
            assertEquals(0, Project.errorCount, name)
        }
    }

    @Test fun mapProjectionsAndLazyDefaultsCompile() {
        MCFPPStringTest.readFromString("""
            import mcfpp.lang:*
            func inspect(values: map<int>, key: string): int {
                var existing = values.getOrElse(key) { 8 };
                var inserted = values.getOrPut(key) { 9 };
                var keys = values.keys();
                var numbers = values.values();
                var entries = values.entries();
                return existing + inserted;
            }
            func main() {
                var values = {entries: [{key: "a", value: 3}]} as map<int>;
                var result = inspect(values, "new.key");
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
    }
    @Test fun expandedCollectionsAndScopesCompileAgainstBundledLibrary() {
        compile("""
            func fallback(): int { return 99; }
            func positive(x: int): bool { return x > 0; }
            func test(values: list<int>): int {
                var mapped = values.mapIndexed { index, value -> index + value };
                var flattened = values.flatMap { value -> [value, value] };
                var filtered = values.filterNot { it < 2 };
                var visits = values.onEach { var copy = it; };
                values.forEachIndexed { index, value -> var copy = index + value; };
                values.forEach { var copy = it; };
                var hasPositive = values.any(positive);
                var all = values.all { it > 0 };
                var none = values.none { it < 0 };
                var count = values.count { it > 1 };
                var first = values.indexOfFirst { it == 2 };
                var last = values.indexOfLast { it == 2 };
                var defaulted = values.getOrElse(20, fallback);
                var selected = values.firstOrElse({ it > 1 }, fallback);
                var finalValue = values.lastOrElse(fallback);
                var found = values.tryFirst({ it == 2 }, { value -> var copy = value; });
                var sum = values.sum();
                var summed = values.sumOf { it * 2 };
                var taken = values.takeWhile { it < 3 };
                var dropped = values.dropWhile { it < 2 };
                var sliced = values.take(2).drop(1).slice(0, 1).reversed().distinct().toList();
                var readonly = values.toImmutableList();
                var roSize = readonly.size();
                var transformed = sum.let { value -> value + 1 };
                var retained = transformed.also { value -> var copy = value; };
                retained.let { value -> var copy = value; };
                var computed = run { 5 };
                repeat(2) { index -> var copy = index; };
                return sum + summed + computed + values.count();
            }
            func main() { var result = test([1, 2, 2, 4]); }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
    }

    @Test fun stringsCompileWithDynamicSlicesAndEscapedConcatenation() {
        MCFPPStringTest.readFromString("""
            import mcfpp.lang:*
            func dynamic(value: string, start: int, end: int): string {
                var length = value.length();
                var empty = value.isEmpty();
                var present = value.isNotEmpty();
                var part = value.substring(start, end);
                return part.contact("!");
            }
            func main() {
                var message = " hello ";
                var trimmed = message.trim();
                var replaced = message.replace("hello", "world");
                var parts = message.split(" ");
                var lower = "AbC".lowercase();
                var upper = "aBc".uppercase();
                var found = message.contains("hello");
                var starts = message.startsWith(" ");
                var ends = message.endsWith(" ");
                var index = message.indexOf("h");
                var number = "12".toIntOrElse { 0 };
                var textResult = dynamic(message, -1, 100);
                var bounded = number.coerceIn(0, 10).coerceAtLeast(3).coerceAtMost(8);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val commands = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.flatMap { it.commands.analyzeAll() }
        assertTrue(commands.any { "data get" in it })
    }
}
