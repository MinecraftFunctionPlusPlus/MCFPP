package top.mcfpp.test.entity

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.context.ContextRelation
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.*
import kotlin.test.*

/** Actual source/emitted commands interpreted by the existing context executor. */
class EntityContextRelationTest {
    private val rootPosition = ContextPosition(3.0, 64.0, -4.0)
    private val rootRotation = ContextRotation(15.0, -5.0)
    private val carrierPosition = ContextPosition(40.0, 70.0, 20.0)
    private val carrierRotation = ContextRotation(90.0, 0.0)

    private fun entity(name: String, relations: Map<String, List<String>> = emptyMap(),
                       position: ContextPosition = ContextPosition(0.0, 65.0, 0.0),
                       dimension: String = "minecraft:overworld", player: Boolean = false) =
        ContextEntity(name, if (player) "minecraft:player" else "minecraft:pig", position = position,
            rotation = ContextRotation(7.0, 2.0), dimension = dimension, relations = relations)

    private fun tree(): List<ContextEntity> = listOf(
        ContextEntity("carrier", position = carrierPosition, rotation = carrierRotation,
            relations = mapOf("passengers" to listOf("first", "second"))),
        entity("first", mapOf("passengers" to listOf("grandchild")), ContextPosition(41.0, 72.0, 20.0)),
        entity("second", position = ContextPosition(39.0, 72.0, 20.0)),
        entity("grandchild", position = ContextPosition(41.0, 74.0, 20.0)),
        entity("unrelated", position = ContextPosition(100.0, 64.0, 100.0)),
        entity("empty")
    )

    private fun compile(source: String, version: String = "26.3", action: (Function) -> Unit) {
        val config = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf() }
            MCFPPStringTest.readFromString(source, args = arrayOf("-ignoreStdLib"), version = version)
            action(GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single())
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
        }
    }

    private fun execute(main: Function, entities: List<ContextEntity>, root: String? = "carrier"): ContextCommandExecutor {
        val functions = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
            .flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.flatMap { command -> command.analyze().lines() } }
        val context = root?.let { name -> entities.single { it.name == name } }
        return ContextCommandExecutor(listOf(Commands.stackIn().analyze(),
            "data modify storage mcfpp:system stack_frame[0].sentinel set value 73", "function ${main.namespaceID}"),
            functions, entities, context, rootPosition, rootRotation, world = { _, _, _ -> ContextBlock("minecraft:stone") },
            macros = Project.macroFunction.toMap())
    }

    private fun ContextCommandExecutor.assertCleanFrame() {
        assertEquals(73, assertIs<IntTag>(readStorage("mcfpp:system", "stack_frame[0].sentinel")).value)
        assertEquals(1, executedFunctionLines.count { it == "data modify storage mcfpp:system stack_frame prepend value {}" } -
            executedFunctionLines.count { it == "data remove storage mcfpp:system stack_frame[0]" })
    }

    @Test fun directPassengersTeleportThroughBoundOperandsAndEachLocalReturnCleansItsFrame() = compile("""
        func main() {
            @s.withPosCtx { source ->
                source.onPassengers { passenger ->
                    /tp ${'$'}{passenger.executor} ${'$'}{source.pos}
                    /execute if block ~ ~ ~ minecraft:stone run say passenger
                    return
                    /say unreachable
                }
            }
            /say after
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        val initial = tree()
        val machine = execute(main, initial)
        assertEquals(listOf("first", "second"), machine.teleports.map { it.target })
        assertEquals(List(2) { carrierPosition }, machine.teleports.map { it.to })
        assertEquals(listOf("first", "second"), machine.blockTests.map { it.executor })
        assertEquals(List(2) { carrierRotation }, machine.blockTests.map { it.rotation })
        assertEquals(listOf("passenger", "passenger", "after"), machine.messages)
        assertEquals(initial.single { it.name == "unrelated" }.position, machine.entities.single { it.name == "unrelated" }.position)
        assertTrue(machine.executedFunctionLines.any { it.startsWith("execute on passengers run function ") })
        machine.assertCleanFrame()
    }

    @Test fun missingExecutorAndEmptyRelationsInvokeNoCallback() = compile("""
        func main() {
            @s.withPosCtx { source ->
                source.onPassengers { passenger ->
                    /say unexpected
                }
                source.onOrigin { origin ->
                    /say unexpected-origin
                }
            }
            /say after
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        for (root in listOf("empty", null)) {
            val machine = execute(main, tree(), root)
            assertEquals(listOf("after"), machine.messages)
            assertTrue(machine.teleports.isEmpty())
            machine.assertCleanFrame()
        }
    }

    @Test fun outerReceiverRelationsInheritNewerCallSiteSpaceAndDimension() = compile("""
        func main() {
            @s.withPosCtx { outer ->
                @a.withPosCtx { inner ->
                    outer.onPassengers { rider ->
                        /execute if block ~ ~ ~ minecraft:stone run say nested
                        /execute if dimension minecraft:the_nether run say nether
                        /tp ${'$'}{rider.executor} ${'$'}{inner.pos}
                    }
                }
            }
            /execute if block ~ ~ ~ minecraft:stone run say after
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        val anchor = entity("anchor", mapOf("passengers" to listOf("decoy")), ContextPosition(-8.0, 90.0, 12.0),
            "minecraft:the_nether", player = true).apply { rotation = ContextRotation(135.0, 20.0) }
        val machine = execute(main, tree() + anchor + entity("decoy"))
        assertEquals(listOf("first", "second", "carrier"), machine.blockTests.map { it.executor })
        assertEquals(listOf(anchor.position, anchor.position, rootPosition), machine.blockTests.map { it.position })
        assertEquals(listOf(anchor.rotation, anchor.rotation, rootRotation), machine.blockTests.map { it.rotation })
        assertEquals(listOf("minecraft:the_nether", "minecraft:the_nether", "minecraft:overworld"), machine.blockTests.map { it.dimension })
        assertEquals(listOf("first", "second"), machine.teleports.map { it.target }, "The at target's decoy passenger is not the origin")
        assertEquals(List(2) { "minecraft:the_nether" }, machine.teleports.map { it.dimension })
        assertEquals(listOf("nested", "nether", "nested", "nether", "after"), machine.messages)
        machine.assertCleanFrame()
    }

    @Test fun singleRelationFamilyUsesItsSourceExecutorAndRetainsSpace() {
        val relations = ContextRelation.values().filter { it != ContextRelation.PASSENGERS }
        val callbacks = relations.joinToString("\n") { relation ->
            val name = "on" + relation.commandName.replaceFirstChar { it.uppercaseChar() }
            """
                source.$name { related ->
                    /execute if block ~ ~ ~ minecraft:stone run say ${relation.commandName}
                }
            """.trimIndent()
        }
        compile("""
            func main() {
                @s.withEntityCtx { source ->
                    $callbacks
                }
                /say after
            }
        """.trimIndent()) { main ->
            assertEquals(0, Project.errorCount)
            val origin = entity("start", relations.associate { it.commandName to listOf(it.commandName) })
            val entities = listOf(origin) + relations.map { entity(it.commandName) }
            val machine = execute(main, entities, "start")
            assertEquals(relations.map { it.commandName }, machine.blockTests.map { it.executor })
            assertEquals(List(relations.size) { rootPosition }, machine.blockTests.map { it.position })
            assertEquals(List(relations.size) { rootRotation }, machine.blockTests.map { it.rotation })
            assertEquals(relations.map { it.commandName } + "after", machine.messages)
            machine.assertCleanFrame()
            val inverse = execute(main, tree(), "first")
            assertEquals(listOf("carrier"), inverse.blockTests.map { it.executor })
            assertEquals(listOf("vehicle", "after"), inverse.messages)
        }
    }

    @Test fun passengerTraversalIsOnlyRecursiveWhenRequestedAndIncomingBranchesAreNotDeduplicated() {
        compile("""
            func main() {
                @s.withEntityCtx { source ->
                    source.onPassengers { child ->
                        /execute if block ~ ~ ~ minecraft:stone run say child
                        child.onPassengers { grandchild ->
                            /execute if block ~ ~ ~ minecraft:stone run say grandchild
                        }
                    }
                }
            }
        """.trimIndent()) { main ->
            assertEquals(0, Project.errorCount)
            val machine = execute(main, tree())
            assertEquals(listOf("first", "grandchild", "second"), machine.blockTests.map { it.executor })
            assertEquals(listOf("child", "grandchild", "child"), machine.messages)
            machine.assertCleanFrame()
        }
        compile("""
            func main() {
                @a.withEntityCtx { source ->
                    source.onOwner { owner ->
                        /execute if block ~ ~ ~ minecraft:stone run say shared
                    }
                }
            }
        """.trimIndent()) { main ->
            assertEquals(0, Project.errorCount)
            val origins = listOf("first", "second").map { entity(it, mapOf("owner" to listOf("shared")), player = true) }
            val machine = execute(main, origins + entity("shared"), root = null)
            assertEquals(listOf("shared", "shared"), machine.blockTests.map { it.executor })
            assertEquals(listOf("shared", "shared"), machine.messages)
            machine.assertCleanFrame()
        }
    }

    @Test fun teleportChangesEntitySpaceWithoutRebindingTheContextSnapshot() = compile("""
        func main() {
            @s.withPosCtx { source ->
                source.onPassengers { rider ->
                    /tp ${'$'}{rider.executor} ^ ^ ^1 ~90 ~5
                    /execute if block ${'$'}{source.pos} minecraft:stone run say snapshot
                }
            }
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        val initial = tree()
        val initialRiderPosition = initial.single { it.name == "first" }.position
        val machine = execute(main, initial)
        val destination = ContextPosition(carrierPosition.x - 1.0, carrierPosition.y, carrierPosition.z)
        assertEquals(List(2) { destination }, machine.teleports.map { it.to })
        assertEquals(List(2) { ContextRotation(180.0, 5.0) }, machine.teleports.map { it.rotation })
        assertEquals(List(2) { carrierPosition }, machine.blockTests.map { it.position })
        assertEquals(List(2) { carrierRotation }, machine.blockTests.map { it.rotation })
        assertEquals(carrierPosition, machine.entities.single { it.name == "carrier" }.position)
        assertEquals(initialRiderPosition, initial.single { it.name == "first" }.position, "The interpreter owns mutable copies")
        machine.assertCleanFrame()
    }

    @Test fun relationVersionCapabilityIsExplicitAndOldTargetsDiagnose() {
        val expected = ContextRelation.values().map { it.commandName }.toSet()
        assertEquals(expected, TargetCapabilities.forVersion("1.19.4")!!.executeRelations)
        assertTrue(TargetCapabilities.forVersion("1.19.3")!!.executeRelations.isEmpty())
        val source = """
            func main() {
                @s.withEntityCtx { actor ->
                    actor.onOrigin { origin ->
                        /say origin
                    }
                }
                /say after
            }
        """.trimIndent()
        compile(source, "1.19.4") { main ->
            assertEquals(0, Project.errorCount)
            val origin = entity("start", mapOf("origin" to listOf("shooter")))
            assertEquals(listOf("origin", "after"), execute(main, listOf(origin, entity("shooter")), "start").messages)
        }
        compile(source, "1.19.3") {
            assertTrue(Project.errorCount > 0)
        }
    }
}
