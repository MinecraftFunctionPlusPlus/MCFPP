package top.mcfpp.test.entity

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.ShortTag
import top.mcfpp.test.util.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Production free tp declarations, real datapack export and the existing command interpreter. */
class EntityContextTeleportTest {
    private val originalPosition = ContextPosition(3.0, 64.0, -4.0)
    private val originalRotation = ContextRotation(15.0, -5.0)
    private val carrierPosition = ContextPosition(40.0, 70.0, 20.0)

    private val example = Path.of("examples/entity-context/src/main.mcfpp")
    private val exampleNamespace = "example.entitycontext"

    @Test
    fun originalExampleExportsAndTeleportsOnlyDirectPassengers() = compile(
        artifact = Path.of("build/entity-context/final-example")) { main, functions ->
        val initial = tree()
        val machine = execute(main, functions, initial, "carrier")
        assertEquals(listOf("first", "second"), machine.teleports.map { it.target })
        assertEquals(List(2) { carrierPosition }, machine.teleports.map { it.to })
        for (name in listOf("carrier", "grandchild", "unrelated")) {
            assertEquals(initial.single { it.name == name }.position, machine.entities.single { it.name == name }.position, name)
        }
        assertEquals(listOf("after"), machine.messages)
        val continuation = machine.blockTests.single()
        assertEquals("carrier", continuation.executor)
        assertEquals(originalPosition, continuation.position)
        assertEquals(originalRotation, continuation.rotation)
        assertCleanFrame(machine)
        val emitted = functions.values.flatten()
        assertTrue(emitted.any { it.contains("execute at @s run function ") })
        assertTrue(emitted.any { it.contains("execute on passengers run function ") })
        assertTrue(emitted.any { it.contains("run tp @s ~ ~ ~") })
        assertTrue(emitted.any { it.contains("store result storage ") && it.contains("store success storage ") })

        val inspect = GlobalScope.localNamespaces.getValue(exampleNamespace).scope.functions.getValue("inspect").single()
        val found = execute(inspect, functions, tree(), "carrier")
        assertEquals(listOf("air-present", "air-read", "score-zero"), found.messages)
        assertEquals("carrier", found.blockTests.single().executor)
        assertEquals(originalPosition, found.blockTests.single().position)
        assertEquals(originalRotation, found.blockTests.single().rotation)
        assertCleanFrame(found)
        val missing = execute(inspect, functions, tree(), "first")
        assertEquals(listOf("air-missing", "score-missing"), missing.messages)
        assertTrue(missing.blockTests.isEmpty(), "A missing field must skip its read callback")
        assertCleanFrame(missing)
    }

    @Test
    fun emptyRelationOrMissingExecutorSkipsTeleportAndPreservesContinuation() = compile { main, functions ->
        for (root in listOf("empty", null)) {
            val machine = execute(main, functions, tree(), root)
            assertTrue(machine.teleports.isEmpty())
            assertEquals(listOf("after"), machine.messages)
            assertEquals(root, machine.blockTests.single().executor)
            assertEquals(originalPosition, machine.blockTests.single().position)
            assertCleanFrame(machine)
        }
    }

    @Test
    fun ordinaryTargetsAndCoordinatesRetainFreshCommandResults() = compile(entry = "commandResults") { main, functions ->
        val entities = listOf(
            ContextEntity("carrier", position = carrierPosition, rotation = ContextRotation(0.0, 0.0)),
            ContextEntity("Alice", "minecraft:player", position = ContextPosition(9.0, 65.0, 0.0), rotation = ContextRotation(0.0, 0.0)),
            ContextEntity("Bob", "minecraft:player", position = ContextPosition(11.0, 65.0, 0.0), rotation = ContextRotation(0.0, 0.0))
        )
        val machine = execute(main, functions, entities, "carrier")
        assertEquals(listOf("Alice", "Bob"), machine.teleports.map { it.target })
        assertEquals(List(2) { ContextPosition(1.0, 2.0, 3.0) }, machine.teleports.map { it.to })
        assertEquals(listOf("moved-result", "moved-success", "missed-result", "missed-failure"), machine.messages)
        assertCleanFrame(machine)
    }

    private fun tree() = listOf(
        ContextEntity("carrier", position = carrierPosition, rotation = ContextRotation(90.0, 0.0),
            relations = mapOf("passengers" to listOf("first", "second")), nbt = mapOf("Air" to ShortTag(300))),
        ContextEntity("first", position = ContextPosition(41.0, 72.0, 20.0), rotation = ContextRotation(0.0, 0.0),
            relations = mapOf("passengers" to listOf("grandchild"))),
        ContextEntity("second", position = ContextPosition(39.0, 72.0, 20.0), rotation = ContextRotation(0.0, 0.0)),
        ContextEntity("grandchild", position = ContextPosition(41.0, 74.0, 20.0), rotation = ContextRotation(0.0, 0.0)),
        ContextEntity("unrelated", position = ContextPosition(100.0, 64.0, 100.0), rotation = ContextRotation(0.0, 0.0)),
        ContextEntity("empty", position = ContextPosition(0.0, 65.0, 0.0), rotation = ContextRotation(0.0, 0.0))
    )

    private fun execute(main: Function, functions: Map<String, List<String>>, entities: List<ContextEntity>, root: String?): ContextCommandExecutor =
        ContextCommandExecutor(listOf(Commands.stackIn().analyze(),
            "data modify storage mcfpp:system stack_frame[0].sentinel set value 73", "function ${main.namespaceID}"),
            functions, entities, root?.let { name -> entities.single { it.name == name } }, originalPosition, originalRotation,
            world = { _, _, _ -> ContextBlock("minecraft:stone") }, macros = Project.macroFunction.toMap(),
            initialScores = mapOf("carrier points" to 0))

    private fun assertCleanFrame(machine: ContextCommandExecutor) {
        assertEquals(73, assertIs<IntTag>(machine.readStorage("mcfpp:system", "stack_frame[0].sentinel")).value)
        assertEquals(1, machine.executedFunctionLines.count { it == "data modify storage mcfpp:system stack_frame prepend value {}" } -
            machine.executedFunctionLines.count { it == "data remove storage mcfpp:system stack_frame[0]" })
    }

    private fun compile(entry: String = "main", artifact: Path? = null, action: (Function, Map<String, List<String>>) -> Unit) {
        val config = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        val temporary = Files.createTempDirectory("mcfpp-free-teleport-")
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf() }
            CompileSettings.ignoreStdLib = false
            val program = Files.readString(example)
            MCFPPStringTest.readFromString(program, version = "26.3")
            assertEquals(0, Project.errorCount, "The shipped example must compile with the actual bundled standard library")
            val main = GlobalScope.localNamespaces.getValue(exampleNamespace).scope.functions.getValue(entry).single()
            val output = artifact ?: temporary.resolve("datapack")
            Files.createDirectories(output)
            if (artifact != null) Files.writeString(output.resolve("source.mcfpp"), program)
            DatapackCreator.createDatapack(output.toString())
            val data = output.resolve(Project.config.name).resolve("data")
            val functions = linkedMapOf<String, List<String>>()
            Files.walk(data).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
                    val relative = data.relativize(file)
                    if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                        val name = relative.subpath(2, relative.nameCount).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")
                        functions["${relative.getName(0)}:$name"] = Files.readAllLines(file)
                    }
                }
            }
            action(main, functions)
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
            temporary.toFile().deleteRecursively()
        }
    }
}
