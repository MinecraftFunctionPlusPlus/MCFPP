package top.mcfpp.test.entity

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.util.ContextCommandExecutor
import top.mcfpp.test.util.ContextEntity
import top.mcfpp.test.util.ContextPosition
import top.mcfpp.test.util.ContextRotation
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.StringTag

class DisplaySchedulerTest {
    @Test fun nestedRegistrationsWaitUntilTheNextTickAndInvalidRuntimeDelaysAreSkipped() = fixture("""
        func register(delay: int) {
            loop(delay) { frame ->
                /scoreboard players add repeated fixture 1
                return false
            }
            schedule(delay) {
                /scoreboard players add delayed fixture 1
            }
        }
        func main() {
            /scoreboard players set repeated fixture 0
            /scoreboard players set delayed fixture 0
            /scoreboard players set nested fixture 0
            register(2)
            register(0)
            register(-1)
            tick { frame ->
                schedule(1) {
                    /scoreboard players add nested fixture 1
                }
                return false
            }
        }
    """.trimIndent(), "26.3") { functions ->
        val commands = listOf("scoreboard objectives add fixture dummy", "function default.test:main",
            "function default:__tick", "scoreboard players operation nested_first fixture = nested fixture",
            "function default:__tick", "function default:__tick")
        val machine = ScoreCommandExecutor(commands, functions, commandBudget = 50_000)
        assertEquals(1, machine.values["repeated fixture"])
        assertEquals(2, machine.values["delayed fixture"])
        assertEquals(0, machine.values["nested_first fixture"])
        assertEquals(1, machine.values["nested fixture"])
        assertEquals(3, machine.messages.count { it == "Invalid scheduler delay or interval" })
    }

    @Test fun dynamicUUIDsRoundTripSignedWordsAndResolveDirectTargets() = fixture("""
        func update(input: string) {
            const id = UUID(input)
            var payload: nbt = {UUID: id}
            /data modify storage fixture:uuid parsed append from storage mcfpp:system stack_frame[0].payload.UUID
            const display = entityByUUID<ItemDisplay>(id)
            display.transform.y = 2.0
        }
        func main() {
            /data modify storage fixture:uuid parsed set value []
            update("0-0-0-0-1")
            update("ffffffff-ffff-ffff-ffff-ffffffffffff")
            update("12345678-abcd-ef01-2345-6789abcdef01")
        }
    """.trimIndent(), "26.3") { functions ->
        val ids = listOf("0-0-0-0-1", "ffffffff-ffff-ffff-ffff-ffffffffffff", "12345678-abcd-ef01-2345-6789abcdef01")
        val machine = ContextCommandExecutor(listOf("function default.test:main"), functions,
            entities = ids.mapIndexed { index, id -> ContextEntity("display$index", "minecraft:item_display",
                position = ContextPosition(0.0, 0.0, 0.0), rotation = ContextRotation(0.0, 0.0), uuid = java.util.UUID.fromString(id)) },
            macros = functions.filterValues { body -> body.any { it.startsWith('$') } }.mapValues { it.value.joinToString("\n") },
            commandBudget = 100_000)
        for (index in ids.indices) {
            assertEquals(top.mcfpp.util.MCUUID(java.util.UUID.fromString(ids[index])).uuidNBT,
                machine.readStorage("fixture:uuid", "parsed[$index]"))
            assertEquals(FloatTag(2f), machine.readEntity("display$index", "transformation.translation[1]"))
        }
    }

    @Test fun legacyFloatValuesWriteVanillaFloatTags() = fixture("""
        func update(input: float) {
            entityByUUID<ItemDisplay>("0-0-0-0-1").transform.y = input
        }
        func main() { update(-2.25) }
    """.trimIndent(), "1.21.8") { functions ->
        val machine = ContextCommandExecutor(listOf("function default.test:main"), functions,
            entities = listOf(ContextEntity("display", "minecraft:item_display", position = ContextPosition(0.0, 0.0, 0.0),
                rotation = ContextRotation(0.0, 0.0), uuid = java.util.UUID.fromString("0-0-0-0-1"))),
            macros = functions.filterValues { body -> body.any { it.startsWith('$') } }.mapValues { it.value.joinToString("\n") },
            commandBudget = 100_000)
        assertEquals(FloatTag(-2.25f), machine.readEntity("display", "transformation.translation[1]"))
    }

    @Test fun delayedEntityReferencesOwnDynamicUUIDOperandsAfterTheCreatorReturns() = fixture("""
        func start(input: string) {
            const display = entityByUUID<ItemDisplay>(input)
            tick { frame ->
                display.transform.y = frame * 1.0
                return frame < 1
            }
        }
        func main() {
            start("0-0-0-0-1")
            start("0-0-0-0-2")
        }
    """.trimIndent(), "26.3") { functions ->
        val machine = ContextCommandExecutor(listOf("function default.test:main") + List(3) { "function default:__tick" }, functions,
            entities = (1..2).map { id -> ContextEntity("display$id", "minecraft:item_display", position = ContextPosition(0.0, 0.0, 0.0),
                rotation = ContextRotation(0.0, 0.0), uuid = java.util.UUID.fromString("0-0-0-0-$id")) },
            macros = functions.filterValues { body -> body.any { it.startsWith('$') } }.mapValues { it.value.joinToString("\n") },
            commandBudget = 100_000)
        for (id in 1..2) assertEquals(FloatTag(1f), machine.readEntity("display$id", "transformation.translation[1]"))
    }

    @Test fun completeBounceSummonsTheDisplayUpdatesEachFrameAndStopsAtZero() {
        val source = Files.readString(Path.of("examples/display-animation/src/main.mcfpp"))
        for (version in listOf("1.21.8", "26.3")) fixture(source, version) { functions ->
            val commands = mutableListOf("function default:load", "function default.test:main")
            repeat(51) { frame ->
                commands += "function default:__tick"
                commands += "data modify storage fixture:animation height_$frame set from entity 0-0-0-0-1 transformation.translation[1]"
            }
            commands += "function default:__tick"
            val machine = ContextCommandExecutor(commands, functions,
                macros = functions.filterValues { body -> body.any { it.startsWith('$') } }.mapValues { it.value.joinToString("\n") },
                commandBudget = 20_000 * 53)
            val displays = machine.entities.filter { it.type == "minecraft:item_display" }
            assertEquals(1, displays.size)
            for (frame in 0..50) {
                var t = frame / 50f
                val n = 7.5625f
                val d = 2.75f
                val bounced = when {
                    t < 1f / d -> n * t * t
                    t < 2f / d -> { t -= 1.5f / d; n * t * t + 0.75f }
                    t < 2.5f / d -> { t -= 2.25f / d; n * t * t + 0.9375f }
                    else -> { t -= 2.625f / d; n * t * t + 0.984375f }
                }
                val actual = (machine.readStorage("fixture:animation", "height_$frame") as FloatTag).value
                assertEquals((1f - bounced) * 4f, actual, 0.00001f, "frame $frame")
            }
            assertEquals(FloatTag(0f), machine.readEntity(displays.single().name, "transformation.translation[1]"))
            assertEquals(0, (machine.readStorage("default:scheduler", "queued") as top.mcfpp.nbt.tags.collection.ListTag).size)
        }
    }

    @Test fun runtimeTypeChecksAndDisplayPropertiesOperateOnTheRealTargets() = fixture("""
        import mcfpp.minecraft.entity:*
        import mcfpp.minecraft.item:*
        import mcfpp.minecraft.other:*
        func main() {
            const display = entityByUUID<ItemDisplay>("0-0-0-0-1")
            display.transform.y = 2.25
            var scale = DisplayVector3()
            scale.x = 1.0
            scale.y = 2.0
            scale.z = 3.0
            display.transform.scale = scale
            display.transform.scale.x = 3.0
            var rotation = DisplayQuaternion()
            rotation.z = 1.0
            rotation.w = 0.0
            display.transform.leftRotation = rotation
            display.billboard = DisplayBillboard.center
            display.itemDisplayMode = ItemDisplayMode.gui
            var brightness = DisplayBrightness()
            brightness.block = 7
            brightness.sky = 15
            display.brightness = brightness
            var shownItem = ItemStackData()
            shownItem.id = "minecraft:diamond"
            shownItem.count = 1
            shownItem.components = {}
            display.item = shownItem
            if (display.exists() && display.checkType<ItemDisplay>() && display.checkType<DisplayEntity>()) {
                /say correct-type
            }
            const wrong = entityByUUID<Zombie>("0-0-0-0-1")
            if (!wrong.checkType<Zombie>()) {
                /say checked-runtime-type
            }
            if (!entityByUUID("0-0-0-0-9").checkType<ItemDisplay>()) {
                /say missing-type
            }
            display.transform.y.tryRead { value ->
                /data modify storage fixture:display y set from storage mcfpp:system stack_frame[0].value
            }
            display.viewRange.tryRead { value ->
                /data modify storage fixture:display view set from storage mcfpp:system stack_frame[0].value
            }
            const label = entityByUUID<TextDisplay>("0-0-0-0-2")
            label.text = "hello"
            label.textOpacity = 200
            label.background = 0
            label.alignment = TextAlignment.left
            label.shadow = true
            const block = entityByUUID<BlockDisplay>("0-0-0-0-3")
            var state = BlockState()
            state.name = "minecraft:oak_log"
            state.properties = {axis: "x"}
            block.blockState = state
        }
    """.trimIndent(), "26.3") { functions ->
        fun entity(id: Int, type: String, nbt: Map<String, Tag<*>> = emptyMap()) = ContextEntity("display$id", type,
            position = ContextPosition(0.0, 0.0, 0.0), rotation = ContextRotation(0.0, 0.0),
            uuid = java.util.UUID.fromString("0-0-0-0-$id"), nbt = nbt)
        val transformation = Tag.toNBT("{translation:[1f,4f,3f],scale:[2f,2f,2f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}")
        val machine = ContextCommandExecutor(listOf("function default.test:main"), functions, entities = listOf(
            entity(1, "minecraft:item_display", mapOf("transformation" to transformation)), entity(2, "minecraft:text_display"),
            entity(3, "minecraft:block_display")), macros = Project.macroFunction.toMap())
        assertEquals(listOf("correct-type", "checked-runtime-type", "missing-type"), machine.messages)
        assertEquals(FloatTag(1f), machine.readEntity("display1", "transformation.translation[0]"))
        assertEquals(FloatTag(2.25f), machine.readEntity("display1", "transformation.translation[1]"))
        assertEquals(FloatTag(3f), machine.readEntity("display1", "transformation.scale[0]"))
        assertEquals(FloatTag(2f), machine.readEntity("display1", "transformation.scale[1]"))
        assertEquals(FloatTag(3f), machine.readEntity("display1", "transformation.scale[2]"))
        assertEquals(FloatTag(1f), machine.readEntity("display1", "transformation.left_rotation[2]"))
        assertEquals(FloatTag(0f), machine.readEntity("display1", "transformation.left_rotation[3]"))
        assertEquals(StringTag("center"), machine.readEntity("display1", "billboard"))
        assertEquals(StringTag("gui"), machine.readEntity("display1", "item_display"))
        assertEquals(IntTag(15), machine.readEntity("display1", "brightness.sky"))
        assertEquals(StringTag("minecraft:diamond"), machine.readEntity("display1", "item.id"))
        assertEquals(FloatTag(2.25f), machine.readStorage("fixture:display", "y"))
        assertEquals(FloatTag(1f), machine.readStorage("fixture:display", "view"))
        assertEquals(StringTag("hello"), machine.readEntity("display2", "text"))
        assertEquals(StringTag("left"), machine.readEntity("display2", "alignment"))
        assertEquals((-56).toByte(), machine.readEntity("display2", "text_opacity").value)
        assertEquals(StringTag("minecraft:oak_log"), machine.readEntity("display3", "block_state.Name"))
        assertEquals(StringTag("x"), machine.readEntity("display3", "block_state.Properties.axis"))
    }

    @Test fun exampleCompilesWithBothFloatBackends() {
        val source = Files.readString(Path.of("examples/display-animation/src/main.mcfpp"))
        for (version in listOf("1.21.8", "26.3")) fixture(source, version) { functions ->
            assertTrue(functions.values.flatten().any { it.contains("transformation.translation[1]") })
            assertTrue(functions.values.flatten().any { it.contains("type=minecraft:item_display") })
            top.mcfpp.io.LibBinWriter.write(Files.createTempDirectory("mcfpp-display-example-library-").toString())
        }
    }

    @Test fun independentTasksKeepTheirCapturedValuesAndRunAtTheSpecifiedTicks() = fixture("""
        func register(value: int) {
            tick { frame ->
                /scoreboard players add tick-calls fixture 1
                /scoreboard players add captures fixture ${'$'}{value}
                return frame < 2
            }
        }
        func main() {
            /scoreboard players set tick-calls fixture 0
            /scoreboard players set loop-calls fixture 0
            /scoreboard players set captures fixture 0
            /scoreboard players set delayed fixture 0
            register(2)
            register(7)
            loop(2) { frame ->
                /scoreboard players add loop-calls fixture 1
                return frame < 1
            }
            schedule(0) {
                /scoreboard players add delayed fixture 1
            }
            schedule(3) {
                /scoreboard players add delayed fixture 10
            }
        }
    """.trimIndent(), "26.3") { functions ->
        val commands = mutableListOf("scoreboard objectives add fixture dummy", "function default.test:main")
        fun observe(step: Int) {
            commands += "scoreboard players operation ticks_$step fixture = tick-calls fixture"
            commands += "scoreboard players operation loops_$step fixture = loop-calls fixture"
            commands += "scoreboard players operation delays_$step fixture = delayed fixture"
        }
        observe(0)
        repeat(4) { commands += "function default:__tick"; observe(it + 1) }
        val machine = ScoreCommandExecutor(commands, functions, commandBudget = 50_000)
        assertEquals(6, machine.values["tick-calls fixture"])
        assertEquals(2, machine.values["loop-calls fixture"])
        assertEquals(27, machine.values["captures fixture"])
        assertEquals(11, machine.values["delayed fixture"])
        assertEquals(listOf(0, 2, 4, 6, 6), (0..4).map { machine.values["ticks_$it fixture"] })
        assertEquals(listOf(0, 0, 1, 1, 2), (0..4).map { machine.values["loops_$it fixture"] })
        assertEquals(listOf(1, 1, 1, 11, 11), (0..4).map { machine.values["delays_$it fixture"] })
        assertEquals(0, machine.stackDepth)
    }

    private fun fixture(source: String, version: String, action: (Map<String, List<String>>) -> Unit) {
        val previous = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        val output = Files.createTempDirectory("mcfpp-display-scheduler-")
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = false
            MCFPPStringTest.readFromString(source, version = version)
            assertEquals(0, Project.errorCount, "Source must compile for $version")
            DatapackCreator.createDatapack(output.toString())
            val functions = linkedMapOf<String, List<String>>()
            Files.walk(output).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
                val names = output.relativize(file).map { it.toString() }
                val data = names.indexOf("data")
                if (data >= 0 && names.getOrNull(data + 2) == "function")
                    functions["${names[data + 1]}:${names.drop(data + 3).joinToString("/").removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
            } }
            action(functions)
        } finally {
            Project.config = previous
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
        }
    }
}
