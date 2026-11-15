package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.ContextCommandExecutor
import top.mcfpp.test.util.ContextEntity
import top.mcfpp.test.util.ContextPosition
import top.mcfpp.test.util.ContextRotation
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataTemplateLiteralTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        val previous = Project.config
        val ignored = CompileSettings.ignoreStdLib
        Project.config = ProjectConfig()
        CompileSettings.ignoreStdLib = false
        try {
            MCFPPStringTest.readFromString(source.trimIndent(), version = version)
            assertEquals(0, Project.errorCount, "Expected zero errors for valid template literal source")
            return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        } finally {
            Project.config = previous
            CompileSettings.ignoreStdLib = ignored
        }
    }

    private fun compileExpectError(source: String, version: String = "26.3") {
        val previous = Project.config
        val ignored = CompileSettings.ignoreStdLib
        Project.config = ProjectConfig()
        CompileSettings.ignoreStdLib = false
        try {
            MCFPPStringTest.readFromString(source.trimIndent(), version = version)
            assertTrue(Project.errorCount > 0, "Expected compile error for invalid template literal:\n$source")
        } finally {
            Project.config = previous
            CompileSettings.ignoreStdLib = ignored
        }
    }

    private fun execute(main: Function): ScoreCommandExecutor {
        val functions = LinkedHashMap<String, List<String>>()
        fun collect(function: Function) {
            functions[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
        }
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.forEach(::collect)
        GlobalScope.localNamespaces.values.flatMap { it.scope.template.values }.forEach { data ->
            data.constructors.forEach(::collect)
            data.scope.forEachFunction { collect(it) }
        }
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main.commands.analyzeAll(), functions).also { assertEquals(0, it.stackDepth) }
    }

    @Test
    fun objectLiteralsCarryCompleteNestedValuesAndSupportLexicalShorthand() {
        val main = compile("""
            data Base {
                var id: int
                var tag: int = 5
            }
            data Inner : Base {
                var label: string = "ok"
                var doubled: int = id * 2
            }
            data Box<T> {
                var value: T
            }
            data Outer {
                var inner: Inner
                var items: list<Inner> = []
                var opt: int?
                var boxed: Box<int> = { value: 9 }
            }
            func scoreOf(o: Outer = { inner: { id: 4, tag: 5, label: "ok", doubled: 8 }, items: [], boxed: { value: 9 } }): int {
                return o.inner.id + o.inner.tag + o.inner.doubled + o.boxed.value
            }
            func overloadPick(a: int): int { return a }
            func overloadPick(b: Base): int { return b.id + b.tag }
            func main() {
                var h: Outer = { inner: { id: 3, tag: 5, label: "ok", doubled: 6 }, items: [{ id: 1, tag: 5, label: "ok", doubled: 2 }], boxed: { value: 9 } }
                external runtimeId = 7
                var runtimeOuter: Outer = { inner: { id: runtimeId, tag: 5, label: "runtime", doubled: runtimeId * 2 }, items: [], boxed: { value: 9 } }
                var fn: (Outer) -> int = scoreOf
                external a = scoreOf({ inner: { id: 2, tag: 5, label: "ok", doubled: 4 }, items: [], boxed: { value: 1 } })
                external b = scoreOf()
                external doubledPart = h.inner.doubled
                external tagPart = h.items[0].tag
                external boxedPart = h.boxed.value
                external c = h.inner.doubled + h.items[0].tag + h.boxed.value
                external d = runtimeOuter.inner.doubled + runtimeOuter.inner.tag
                external e = overloadPick({ id: 10, tag: 5, extra: "open" })
                external f = fn({ inner: { id: 1, tag: 5, label: "ok", doubled: 2 }, items: [], boxed: { value: 9 } })
                var id = 11
                var inferred = { id, label: "lexical" }
                external shorthand = inferred.id
                var single = { id }
                external singleShorthand = single.id
            }
        """)
        val machine = execute(main)
        assertEquals(2 + 5 + 4 + 1, machine.read(main.scope.getVar("a") as MCInt))
        assertEquals(4 + 5 + 8 + 9, machine.read(main.scope.getVar("b") as MCInt))
        assertEquals(6, machine.read(main.scope.getVar("doubledPart") as MCInt), "nested field")
        assertEquals(5, machine.read(main.scope.getVar("tagPart") as MCInt), "list field")
        assertEquals(9, machine.read(main.scope.getVar("boxedPart") as MCInt), "generic field")
        assertEquals(6 + 5 + 9, machine.read(main.scope.getVar("c") as MCInt))
        assertEquals(14 + 5, machine.read(main.scope.getVar("d") as MCInt))
        assertEquals(15, machine.read(main.scope.getVar("e") as MCInt))
        assertEquals(1 + 5 + 2 + 9, machine.read(main.scope.getVar("f") as MCInt))
        assertEquals(11, machine.read(main.scope.getVar("shorthand") as MCInt))
        assertEquals(11, machine.read(main.scope.getVar("singleShorthand") as MCInt))
    }

    @Test
    fun stdlibTemplateLiteralsWorkForVariablesDisplayPropertiesAndCommandApis() {
        val previous = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val output = Files.createTempDirectory("mcfpp-template-literal-stdlib-")
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = false
            MCFPPStringTest.readFromString("""
                import mcfpp.minecraft:*
                import mcfpp.minecraft.entity:*
                import mcfpp.minecraft.item:*
                import mcfpp.minecraft.other:*
                import mcfpp.minecraft.std:*
                func applyBrightness(display: EntityRef<ItemDisplay>, b: DisplayBrightness) {
                    display.brightness = b
                }
                func emitCommands() {
                    var slot: Slot = { type: SlotType.weapon_mainhand }
                    fillOutline({ start: 0 64 0, end: 1 65 1 }, { name: "minecraft:oak_log", properties: { axis: "y" } })
                    var replaced = itemReplace(0 70 0, { type: SlotType.weapon_mainhand }, { id: "minecraft:diamond" }, 1)
                    var replacedDynamic = itemReplace(0 70 0, slot, { id: "minecraft:diamond" }, 1)
                }
                func main() {
                    const display = entityByUUID<ItemDisplay>("0-0-0-0-1")
                    var b: DisplayBrightness = { block: 7, sky: 0 }
                    applyBrightness(display, { sky: 15, block: 12 })
                    display.item = { id: "minecraft:diamond", count: 3, components: {} }
                    display.transform.scale = { x: 2.0, y: 3.0, z: 4.0 }
                    const block = entityByUUID<BlockDisplay>("0-0-0-0-2")
                    var st: BlockState = { name: "minecraft:stone", properties: {} }
                    block.blockState = st
                }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            DatapackCreator.createDatapack(output.toString())
            val functions = linkedMapOf<String, List<String>>()
            Files.walk(output).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
                    val names = output.relativize(file).map { it.toString() }
                    val data = names.indexOf("data")
                    if (data >= 0 && names.getOrNull(data + 2) == "function") {
                        functions["${names[data + 1]}:${names.drop(data + 3).joinToString("/").removeSuffix(".mcfunction")}"] =
                            Files.readAllLines(file)
                    }
                }
            }
            val machine = ContextCommandExecutor(
                listOf("function default.test:main"),
                functions,
                entities = listOf(
                    ContextEntity("display1", "minecraft:item_display", position = ContextPosition(0.0, 0.0, 0.0),
                        rotation = ContextRotation(0.0, 0.0), uuid = java.util.UUID.fromString("0-0-0-0-1")),
                    ContextEntity("display2", "minecraft:block_display", position = ContextPosition(0.0, 0.0, 0.0),
                        rotation = ContextRotation(0.0, 0.0), uuid = java.util.UUID.fromString("0-0-0-0-2"))
                ),
                macros = Project.macroFunction.toMap()
            )
            assertEquals(IntTag(15), machine.readEntity("display1", "brightness.sky"))
            assertEquals(IntTag(12), machine.readEntity("display1", "brightness.block"))
            assertEquals(StringTag("minecraft:diamond"), machine.readEntity("display1", "item.id"))
            assertEquals(StringTag("minecraft:stone"), machine.readEntity("display2", "block_state.Name"))
            val emitted = functions.values.flatten()
            assertTrue(emitted.any { it.contains("fill 0 64 0 1 65 1 minecraft:oak_log[axis=y] outline") }, emitted.joinToString("\n"))
            assertTrue(emitted.any { it.contains("item replace block 0 70 0 weapon.mainhand with minecraft:diamond 1") }, emitted.joinToString("\n"))
        } finally {
            Project.config = previous
            CompileSettings.ignoreStdLib = ignored
            output.toFile().deleteRecursively()
        }
    }

    @Test
    fun invalidTemplateLiteralStructuresAreRejected() {
        val header = """
            data Inner { var a: int }
            data Outer {
                var req: string
                var opt: int?
                var inner: Inner?
            }
            data RequiredWithDefault { var value: int = 1 }
            func takeOuter(o: Outer) {}
        """.trimIndent()
        val invalidBodies = listOf(
            "func main() { var h: Outer = {}; }",
            "func main() { var h: Outer = { req: 123 }; }",
            "func main() { var h: Outer = { req: null }; }",
            "func main() { var h: Outer = { req: \"a\", req: \"b\" }; }",
            "func main() { var h: Outer = { req: \"a\", inner: { bad: 1 } }; }",
            "func main() { var h: Outer = { req: \"a\", opt: null }; }",
            "func main() { var h: RequiredWithDefault = {}; }",
            "func main() { takeOuter({}); }",
            "func main() { takeOuter({ req: 123 }); }",
            "func main() { var xs: list<Inner> = [{ nope: 1 }]; }"
        )
        for (body in invalidBodies) {
            compileExpectError("$header\n$body")
        }
    }

    @Test
    fun duplicateKeysAreRejectedInEveryObjectContextAfterUnquoting() {
        for (body in listOf(
            "var value = { x: 1, x: 2 }",
            "var value: dict<int> = { x: 1, x: 2 }",
            "var value: nbt = { x: 1, x: 2 }",
            "var value: Point = { x: 1, x: 2 }",
            "var value = { x: 1, \"x\": 2 }",
            "var x = 1; var value = { x, x: 2 }",
            "var value = { nested: { x: 1, x: 2 } }"
        )) compileExpectError("data Point { var x: int }\nfunc main() { $body }")
    }

    @Test
    fun openObjectContractsAllowExtraFieldsAndDoNotInvokeFactories() {
        val main = compile("""
            abstract data Point { var x: int }
            data PrivateFactory {
                var x: int
                private new(x: int) { return { x } }
            }
            func take(point: Point): int { return point.x }
            func main() {
                var point: Point = { x: 4, label: "extra" }
                var privateValue: PrivateFactory = { x: 7 }
                external result = take({ x: 5, extra: true }) + point.x + privateValue.x
            }
        """)
        val machine = execute(main)
        assertEquals(16, machine.read(main.scope.getVar("result") as MCInt))
        assertTrue(machine.messages.isEmpty())
    }
}
