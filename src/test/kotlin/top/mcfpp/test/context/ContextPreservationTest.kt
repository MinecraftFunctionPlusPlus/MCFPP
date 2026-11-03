package top.mcfpp.test.context

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.context.ContextBorrowing
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Actual source/native tp commands, interpreted by the existing context executor. */
class ContextPreservationTest {
    private val rootPosition = ContextPosition(3.0, 64.0, -4.0)
    private val rootRotation = ContextRotation(15.0, -5.0)
    private val savedPosition = ContextPosition(40.0, 70.0, 20.0)
    private val innerPosition = ContextPosition(-12.0, 90.0, 8.0)
    private val innerRotation = ContextRotation(135.0, 20.0)

    private fun entities() = listOf(
        ContextEntity("owner", position = savedPosition, rotation = ContextRotation(90.0, 0.0),
            relations = mapOf("passengers" to listOf("Rider"))),
        ContextEntity("Nether", "minecraft:player", position = innerPosition, rotation = innerRotation,
            dimension = "minecraft:the_nether"),
        ContextEntity("Target", "minecraft:player", position = ContextPosition(6.0, 65.0, 0.0), rotation = ContextRotation(0.0, 0.0)),
        ContextEntity("NearOld", "minecraft:player", position = savedPosition, rotation = ContextRotation(0.0, 0.0)),
        ContextEntity("Rider", position = ContextPosition(42.0, 72.0, 20.0), rotation = ContextRotation(0.0, 0.0))
    )

    @Test fun changedExecutorKeepsTheOuterEntityThroughAnOrdinarySynchronousHelper() = compile("""
        func move(actor: entity) {
            var result = tp(actor, ~1 ~ ~)
            if (result.result == 1) {
                /say moved
            }
        }
        func main() {
            @s.withEntityCtx { outer ->
                entityByName("Nether").withEntityCtx { inner ->
                    move(outer.executor)
                    /execute if block ~ ~ ~ minecraft:stone run say inner
                }
            }
            /say after
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(listOf("owner"), machine.publicTeleports().map { it.target })
        assertEquals(rootPosition + ContextPosition(1.0, 0.0, 0.0), machine.publicTeleports().single().to)
        assertEquals(listOf("moved", "inner", "after"), machine.messages)
        assertEquals("Nether", machine.blockTests.single().executor)
        assertEquals(rootPosition, machine.blockTests.single().position)
        assertTrue(machine.executed.none { it.startsWith("execute summon ") }, "An executor borrow does not materialize space")
        machine.assertClean()
    }

    @Test fun oldPositionRetainsItsDimensionAndSnapshotAfterTheOriginalEntityMoves() = compile("""
        func main() {
            @s.withPosCtx { old ->
                entityByName("Nether").withPosCtx { inner ->
                    /tp @s 100 200 300
                    var result = tp(entityByName("Target"), old.pos)
                    if (result.result == 1) {
                        /say result
                    }
                    if (result.success) {
                        /say success
                    }
                    /execute if block ~ ~ ~ minecraft:stone run say inner
                }
            }
            /execute if block ~ ~ ~ minecraft:stone run say after
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(listOf("owner", "Target"), machine.publicTeleports().map { it.target })
        val owner = machine.entities.single { it.name == "owner" }
        assertEquals(ContextPosition(100.0, 200.0, 300.0), owner.position)
        assertEquals("minecraft:the_nether", owner.dimension)
        val target = machine.entities.single { it.name == "Target" }
        assertEquals(savedPosition, target.position)
        assertEquals("minecraft:overworld", target.dimension)
        assertEquals(listOf(innerPosition, rootPosition), machine.blockTests.map { it.position })
        assertEquals(listOf(innerRotation, rootRotation), machine.blockTests.map { it.rotation })
        assertEquals(listOf("result", "success", "inner", "after"), machine.messages)
        machine.assertClean()
    }

    @Test fun ordinaryQueriesResolveAtTheCallSiteAndOneCoreTeleportKeepsItsWholeResult() = compile("""
        func main() {
            @s.withPosCtx { old ->
                entityByName("Nether").withPosCtx { inner ->
                    tp(@p, old.pos)
                    var players = tp(@a, old.pos)
                    if (players.result == 3) {
                        /say three-players
                    }
                    if (players.success) {
                        /say players-success
                    }
                    var all = tp(@e, old.pos)
                    if (all.result == 5) {
                        /say five-entities
                    }
                    if (all.success) {
                        /say all-success
                    }
                }
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals("Nether", machine.publicTeleports().first().target, "@p must resolve before restoring the old space")
        assertEquals(listOf("three-players", "players-success", "five-entities", "all-success"), machine.messages)
        val commands = machine.executed.filter { it.startsWith("tp @e[tag=mcfpp_ctx_") }
        assertEquals(3, commands.size, "Each operation must retain one multi-target core tp")
        assertEquals(9, machine.publicTeleports().size)
        machine.assertClean()
    }

    @Test fun absentExecutorEmptyBranchesAndLocalReturnStillCleanEveryMaterialization() = compile("""
        func main() {
            @a.withPosCtx { outer ->
                entityByName("Nether").withEntityCtx { inner ->
                    var missed = tp(outer.executor, ~ ~ ~)
                    if (missed.result == 0) {
                        /say zero
                    }
                    if (!missed.success) {
                        /say missing
                    }
                    return
                }
                entityByName("Missing").withPosCtx { never ->
                    tp(@s, outer.pos)
                }
            }
            @a.withEntityCtx { sibling ->
                /say sibling
            }
            /say after
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions, root = null)
        assertTrue(machine.publicTeleports().isEmpty())
        assertEquals(List(3) { listOf("zero", "missing") }.flatten() + List(3) { "sibling" } + "after", machine.messages)
        machine.assertClean()
    }

    @Test fun nestedHelpersAndOuterRelationsUseTheirOriginalBindingsAcrossBothComponents() = compile("""
        func restore(actor: entity, location: ContextPosition) { tp(actor, location) }
        func main() {
            @s.withPosCtx { space ->
                @s.withEntityCtx { owner ->
                    entityByName("Nether").withPosCtx { inner ->
                        entityByName("Target").withEntityCtx { other ->
                            restore(owner.executor, space.pos)
                            owner.onPassengers { rider ->
                                /execute if block ~ ~ ~ minecraft:stone run say rider
                                tp(rider.executor, space.pos)
                                return
                            }
                        }
                        restore(owner.executor, space.pos)
                    }
                }
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(listOf("owner", "Rider", "owner"), machine.publicTeleports().map { it.target })
        assertEquals(List(3) { savedPosition }, machine.publicTeleports().map { it.to })
        assertEquals(List(3) { "minecraft:overworld" }, machine.publicTeleports().map { it.dimension })
        assertEquals("Rider", machine.blockTests.single().executor)
        assertEquals(innerPosition, machine.blockTests.single().position)
        assertEquals(innerRotation, machine.blockTests.single().rotation)
        assertEquals("minecraft:the_nether", machine.blockTests.single().dimension)
        machine.assertClean()
    }

    @Test fun rawOldPositionDiagnosesInsteadOfDroppingItsDimension() = compile("""
        func main() {
            @s.withPosCtx { old ->
                @a.withPosCtx { inner ->
                    /tp @s ${'$'}{old.pos}
                }
            }
        }
    """.trimIndent(), allowErrors = true) { _, _ ->
        assertTrue(Project.errorCount > 0)
        assertNull(ContextBorrowing.currentContext)
    }

    private fun ContextCommandExecutor.publicTeleports() = teleports.filterNot { it.target.startsWith("__context_anchor_") }

    private fun ContextCommandExecutor.assertClean() {
        assertEquals(73, assertIs<IntTag>(readStorage("mcfpp:system", "stack_frame[0].sentinel")).value)
        assertEquals(1, executedFunctionLines.count { it == "data modify storage mcfpp:system stack_frame prepend value {}" } -
            executedFunctionLines.count { it == "data remove storage mcfpp:system stack_frame[0]" })
        assertEquals(0, assertIs<ListTag>(readStorage("mcfpp:context", "frames")).size)
        if (executed.any { it.contains("operations prepend") })
            assertEquals(0, assertIs<ListTag>(readStorage("mcfpp:context", "operations")).size)
        assertTrue(entities.none { "mcfpp_context_anchor" in it.tags }, "All internal anchors must be removed")
        assertTrue(entities.all { entity -> entity.tags.none { it.startsWith("mcfpp_ctx_") } }, "Temporary target tags must be removed")
    }

    private fun execute(main: Function, functions: Map<String, List<String>>, root: String? = "owner"): ContextCommandExecutor {
        val initial = entities()
        return ContextCommandExecutor(listOf(Commands.stackIn().analyze(),
            "data modify storage mcfpp:system stack_frame[0].sentinel set value 73", "function ${main.namespaceID}"),
            functions, initial, root?.let { name -> initial.single { it.name == name } }, rootPosition, rootRotation,
            world = { _, _, _ -> ContextBlock("minecraft:stone") }, macros = Project.macroFunction.toMap())
    }

    private fun compile(source: String, allowErrors: Boolean = false, action: (Function, Map<String, List<String>>) -> Unit) {
        val config = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        val temporary = Files.createTempDirectory("mcfpp-context-preservation-")
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf() }
            CompileSettings.ignoreStdLib = true
            val resultLibrary = temporary.resolve("command-result")
            val resultSource = Files.readString(Path.of("src/main/mcfpp/minecraft/std/CommandResult.mcfpp"))
            MCFPPStringTest.readFromString("namespace mcfpp.minecraft.std\n$resultSource", targetPath = resultLibrary.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            Project.config.includes = arrayListOf(resultLibrary.toString())
            val declarations = Files.readString(Path.of("src/main/mcfpp/lang/teleport.mcfpp")) + "\n" +
                Files.readString(Path.of("src/main/mcfpp/lang/entity.mcfpp"))
            MCFPPStringTest.readFromString("import mcfpp.minecraft.std:*\n$declarations\n$source", version = "26.3")
            if (!allowErrors) assertEquals(0, Project.errorCount)
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            val functions = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                .flatMap { it.scope.functions.values.flatten() }
                .associate { it.namespaceID.toString() to it.commands.flatMap { command -> command.analyze().lines() } }
            action(main, functions)
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
            temporary.toFile().deleteRecursively()
        }
    }
}
