package top.mcfpp.test.context

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.context.ContextBorrowing
import top.mcfpp.io.FunctionInventory
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

    @Test fun namedContextsRunAsBAtALiveAndSavedPositionsThenRestoreBSpace() = compile("""
        context(frame: EntityExecutionContext)
        func probe() {
            /execute if block ~ ~ ~ minecraft:stone run say probe
        }
        func main() {
            @s.withEntityCtx { owner ->
                owner.executor.withPosCtx { space ->
                    entityByName("Nether").withEntityCtx { other ->
                        other.executor.withPosCtx { inner ->
                            owner.executor.withPosCtx { live -> probe() }
                            withPosCtx(space.pos.offset(1, 0, 0)) { saved -> probe(frame = saved) }
                            /execute if block ~ ~ ~ minecraft:stone run say inner
                        }
                    }
                }
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(List(3) { "Nether" }, machine.blockTests.map { it.executor })
        assertEquals(listOf(savedPosition, savedPosition + ContextPosition(1.0, 0.0, 0.0), innerPosition),
            machine.blockTests.map { it.position })
        assertEquals(listOf("minecraft:overworld", "minecraft:overworld", "minecraft:the_nether"),
            machine.blockTests.map { it.dimension })
        assertEquals(listOf(ContextRotation(90.0, 0.0), innerRotation, innerRotation), machine.blockTests.map { it.rotation })
        val preserveA = machine.executed.indexOfFirst { "set from entity @s UUID" in it }
        val enterB = machine.executed.indexOfFirst { it.startsWith("execute as Nether ") }
        assertTrue(preserveA >= 0 && enterB > preserveA, "A must be saved before execute as B")
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
                entityByName("Nether").withPosCtx { inner ->
                    var missed = tp(@s, outer.pos)
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

    @Test fun unusedNativeResultsCanCrossFrameIndependentHelpersWhileReadResultsRemain() = compile("""
        func observe() {
            /say between
        }
        func main() {
            @s.withPosCtx { space ->
                tp(@s, space.pos)
                observe()
                var result = tp(@s, space.pos)
                observe()
                if (result.success) {
                    /say moved
                }
                tp(@s, space.pos)
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(List(3) { "owner" }, machine.publicTeleports().map { it.target })
        assertEquals(List(3) { savedPosition }, machine.publicTeleports().map { it.to })
        assertEquals(listOf("between", "between", "moved"), machine.messages)
        assertEquals(1, functions.values.flatten().count { "store result storage" in it && "store success storage" in it })
        machine.assertClean()
    }

    @Test fun dynamicDimensionEntryPreservesAncestorSpaceBeforeTheMacroSwitch() = compile("""
        data Dimension { var id: string; constructor(id: string) { this.id = id } }
        func main() {
            external name: string = "minecraft:the_nether"
            var dimension = Dimension(name)
            @s.withPosCtx { original ->
                withDimensionCtx(dimension) { other ->
                    tp(entityByName("Target"), original.pos)
                    /execute if block ~ ~ ~ minecraft:stone run say inner
                }
            }
            /execute if block ~ ~ ~ minecraft:stone run say after
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals("Target", machine.publicTeleports().single().target)
        assertEquals(savedPosition, machine.publicTeleports().single().to)
        assertEquals("minecraft:overworld", machine.publicTeleports().single().dimension)
        assertEquals(listOf("inner", "after"), machine.messages)
        assertEquals(listOf("minecraft:the_nether", "minecraft:overworld"), machine.blockTests.map { it.dimension })
        assertEquals(listOf(ContextPosition(5.0, 70.0, 2.5), rootPosition), machine.blockTests.map { it.position })
        val save = machine.executed.indexOfFirst { it.startsWith("execute summon minecraft:marker ") }
        val switch = machine.executed.indexOfFirst { it.startsWith("execute in minecraft:the_nether ") }
        assertTrue(save >= 0 && switch > save, "The original position must be saved before the dynamic dimension switch")
        machine.assertClean()
    }

    @Test fun localPositionKeepsItsOriginalExecutorEyesRotationAndWorldOffset() = compile("""
        func main() {
            @s.withEntityCtx { actor ->
                withRotationCtx(rotation(90f, 0f)) { frame ->
                    withAnchorCtx(Anchor.eyes) { eyes ->
                        entityByName("Nether").withEntityCtx { other ->
                            other.executor.withPosCtx { elsewhere ->
                                tp(entityByName("Target"), eyes.local(0, 0, 2).offset(1, 0, 0))
                            }
                        }
                    }
                }
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        val moved = machine.publicTeleports().single()
        assertEquals("Target", moved.target)
        assertEquals(ContextPosition(2.0, 65.62, -4.0), moved.to)
        assertEquals("minecraft:overworld", moved.dimension)
        machine.assertClean()
    }

    @Test fun recursiveCallsKeepDistinctContextBindingsInTheSameCommandFrame() = compile("""
        context(location: PosContext)
        func recurse(depth: int, other: PosContext) {
            if (depth > 0) {
                recurse(depth - 1, other, location = other)
                return
            }
            tp(entityByName("Target"), location.pos)
        }
        func main() {
            @s.withPosCtx { original ->
                entityByName("Nether").withPosCtx { current ->
                    external depth: int = 1
                    recurse(depth, current, location = original)
                }
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(innerPosition, machine.publicTeleports().single().to)
        assertEquals("minecraft:the_nether", machine.publicTeleports().single().dimension)
        machine.assertClean()
    }

    @Test fun modifierEntriesChangeTheirOwnComponentsAndRestoreTheirCaller() = compile("""
        data Dimension { var id: string; constructor(id: string) { this.id = id } }
        data EntityType { var id: string; constructor(id: string) { this.id = id } }
        func main() {
            withPosCtx(3.75 64 -4.25) { base ->
                withAlignedCtx(Axes.xz) { aligned ->
                    /execute if block ~ ~ ~ minecraft:stone run say aligned
                }
                withDimensionCtx(Dimension("minecraft:the_nether")) { nether ->
                    /execute if block ~ ~ ~ minecraft:stone run say dimension
                }
                entityByName("Nether").withPositionedCtx { located ->
                    /execute if block ~ ~ ~ minecraft:stone run say positioned
                }
                withFacingCtx(~-1 ~ ~) { facing ->
                    /execute if block ~ ~ ~ minecraft:stone run say facing
                }
                withAnchorCtx(Anchor.eyes) { eyes ->
                    /execute if block ~ ~ ~ minecraft:stone run say eyes
                }
                withHeightmapCtx(Heightmap.ocean_floor) { surface ->
                    /execute if block ~ ~ ~ minecraft:stone run say height
                }
                withSummonedCtx(EntityType("minecraft:pig")) { pig ->
                    /execute if block ~ ~ ~ minecraft:stone run say summoned
                }
                /execute if block ~ ~ ~ minecraft:stone run say restored
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions, heightmap = { dimension, map, x, z ->
            assertEquals("minecraft:overworld", dimension); assertEquals("ocean_floor", map)
            assertEquals(3, x); assertEquals(-5, z); 72.0
        })
        val observed = machine.blockTests
        assertEquals(ContextPosition(3.0, 64.0, -5.0), observed[0].position)
        assertEquals(ContextPosition(3.75 / 8, 64.0, -4.25 / 8), observed[1].position)
        assertEquals("minecraft:the_nether", observed[1].dimension)
        assertEquals(innerPosition, observed[2].position)
        assertEquals(rootRotation, observed[2].rotation)
        assertEquals("minecraft:overworld", observed[2].dimension)
        assertEquals(90.0, observed[3].rotation.yaw, 0.0)
        assertEquals(0.0, observed[3].rotation.pitch, 0.0)
        assertEquals("eyes", observed[4].anchor)
        assertEquals(72.0, observed[5].position.y)
        assertNotEquals("owner", observed[6].executor)
        assertEquals("minecraft:pig", machine.entities.single { it.name == observed[6].executor }.type)
        assertEquals("owner", observed[7].executor)
        assertEquals(ContextPosition(3.75, 64.0, -4.25), observed[7].position)
        assertEquals(rootRotation, observed[7].rotation)
        machine.assertClean()
    }

    private fun ContextCommandExecutor.assertClean() {
        assertEquals(73, assertIs<IntTag>(readStorage("mcfpp:system", "stack_frame[0].sentinel")).value)
        assertEquals(1, executedFunctionLines.count { it == "data modify storage mcfpp:system stack_frame prepend value {}" } -
            executedFunctionLines.count { it == "data remove storage mcfpp:system stack_frame[0]" })
        if (executed.any { "mcfpp:context frames prepend" in it })
            assertEquals(0, assertIs<ListTag>(readStorage("mcfpp:context", "frames")).size)
        if (executed.any { it.contains("operations prepend") })
            assertEquals(0, assertIs<ListTag>(readStorage("mcfpp:context", "operations")).size)
        assertTrue(entities.none { "mcfpp_context_anchor" in it.tags }, "All internal anchors must be removed")
        assertTrue(entities.all { entity -> entity.tags.none { it.startsWith("mcfpp_ctx_") } }, "Temporary target tags must be removed")
    }

    private fun execute(main: Function, functions: Map<String, List<String>>, root: String? = "owner",
                        heightmap: ((String, String, Int, Int) -> Double)? = null): ContextCommandExecutor {
        val initial = entities()
        return ContextCommandExecutor(listOf(Commands.stackIn().analyze(),
            "data modify storage mcfpp:system stack_frame[0].sentinel set value 73", "function ${main.namespaceID}"),
            functions, initial, root?.let { name -> initial.single { it.name == name } }, rootPosition, rootRotation,
            world = { _, _, _ -> ContextBlock("minecraft:stone") }, macros = Project.macroFunction.toMap(), heightmap = heightmap)
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
            val declarations = (Files.readString(Path.of("src/main/mcfpp/lang/teleport.mcfpp")) + "\n" +
                Files.readString(Path.of("src/main/mcfpp/lang/entity.mcfpp")) + "\n" +
                Files.readString(Path.of("src/main/mcfpp/lang/context.mcfpp"))).lineSequence()
                .filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")
            MCFPPStringTest.readFromString("import mcfpp.minecraft.std:*\n$declarations\n$source", version = "26.3")
            if (!allowErrors) assertEquals(0, Project.errorCount)
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            val functions = FunctionInventory.all()
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
