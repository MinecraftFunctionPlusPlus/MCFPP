package top.mcfpp.test.entity

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.context.ContextBorrowing
import top.mcfpp.core.lang.context.ScopedContextValue
import top.mcfpp.io.info.NamespaceInfo
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.ContextBlock
import top.mcfpp.test.util.ContextCommandExecutor
import top.mcfpp.test.util.ContextEntity
import top.mcfpp.test.util.ContextPosition
import top.mcfpp.test.util.ContextRotation
import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.*

/** Source compilation + the existing strict interpreter; no Minecraft game launch. */
class EntityContextEntryTest {
    private val originalPosition = ContextPosition(3.0, 65.0, -4.0)
    private val originalRotation = ContextRotation(25.0, -5.0)
    private val ambient = ContextEntity("ambient", position = ContextPosition(0.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0))
    private val players = listOf(
        ContextEntity("first", "minecraft:player", position = ContextPosition(10.0, 70.0, 20.0), rotation = ContextRotation(90.0, 10.0)),
        ContextEntity("second", "minecraft:player", position = ContextPosition(-6.0, 80.0, 5.0), rotation = ContextRotation(180.0, 20.0))
    )

    private fun compile(source: String, version: String = "26.3", check: (Function) -> Unit) {
        val config = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf() }
            MCFPPStringTest.readFromString(source, args = arrayOf("-ignoreStdLib"), version = version)
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            check(main)
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
        }
    }

    private fun execute(main: Function, root: ContextEntity? = ambient): ContextCommandExecutor {
        val functions = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
            .flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.flatMap { command -> command.analyze().lines() } }
            .toMutableMap()
        Project.macroFunction.forEach { (name, body) -> functions["mcfpp:dynamic/$name"] = body.lines() }
        val commands = listOf(Commands.stackIn().analyze(),
            "data modify storage mcfpp:system stack_frame[0].sentinel set value 73",
            "function ${main.namespaceID}")
        return ContextCommandExecutor(commands, functions, listOf(ambient) + players, root,
            originalPosition, originalRotation, world = { _, _, _ -> ContextBlock("minecraft:stone") },
            macros = Project.macroFunction.toMap())
    }

    private fun ContextCommandExecutor.assertCleanFrame() {
        assertEquals(73, assertIs<IntTag>(readStorage("mcfpp:system", "stack_frame[0].sentinel")).value)
        val pushed = executedFunctionLines.count { it == "data modify storage mcfpp:system stack_frame prepend value {}" }
        val popped = executedFunctionLines.count { it == "data remove storage mcfpp:system stack_frame[0]" }
        assertEquals(1, pushed - popped, "Only the driver frame may remain")
    }

    @Test fun withPosCtxUsesAtAndInheritsTheOriginalExecutor() = compile("""
        func main() {
            @a.withPosCtx { ctx ->
                /execute if block ~ ~ ~ minecraft:stone run say at
            }
            /execute if block ~ ~ ~ minecraft:stone run say outer
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        assertTrue(main.commands.any { it.analyze().startsWith("execute at @a run function ") })
        val machine = execute(main)
        assertEquals(listOf("at", "at", "outer"), machine.messages)
        assertEquals(listOf("ambient", "ambient", "ambient"), machine.blockTests.map { it.executor })
        assertEquals(players.map { it.position } + originalPosition, machine.blockTests.map { it.position })
        assertEquals(players.map { it.rotation } + originalRotation, machine.blockTests.map { it.rotation })
        machine.assertCleanFrame()
    }

    @Test fun withEntityCtxUsesAsAndInheritsAllSpatialComponents() = compile("""
        func main() {
            @a.withEntityCtx { ctx ->
                /execute if block ~ ~ ~ minecraft:stone run say as
            }
            /execute if block ~ ~ ~ minecraft:stone run say outer
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        assertTrue(main.commands.any { it.analyze().startsWith("execute as @a run function ") })
        val machine = execute(main)
        assertEquals(listOf("first", "second", "ambient"), machine.blockTests.map { it.executor })
        assertEquals(List(3) { originalPosition }, machine.blockTests.map { it.position })
        assertEquals(List(3) { originalRotation }, machine.blockTests.map { it.rotation })
        machine.assertCleanFrame()
    }

    @Test fun atKeepsAnEmptyExecutorAndAnEmptySelectorDoesNotInvokeItsBody() = compile("""
        func main() {
            @a.withPosCtx { ctx ->
                /execute if block ~ ~ ~ minecraft:stone run say position-only
            }
            @s.withEntityCtx { ctx ->
                /say must-not-run
            }
            /say after
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        val machine = execute(main, root = null)
        assertEquals(listOf("position-only", "position-only", "after"), machine.messages)
        assertEquals(listOf(null, null), machine.blockTests.map { it.executor })
        machine.assertCleanFrame()
    }

    @Test fun nestedAsCanBorrowOuterPositionWithoutChangingItsBinding() = compile("""
        func main() {
            @a.withPosCtx { space ->
                var alias = space
                @a.withEntityCtx { actor ->
                    /execute if block ${'$'}{alias.pos} minecraft:stone run say nested
                }
            }
            /say after
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        val machine = execute(main)
        assertEquals(listOf("first", "second", "first", "second"), machine.blockTests.map { it.executor })
        assertEquals(listOf(players[0].position, players[0].position, players[1].position, players[1].position),
            machine.blockTests.map { it.position })
        assertEquals(List(4) { "nested" } + "after", machine.messages)
        machine.assertCleanFrame()
    }

    @Test fun receiverEffectsAndOrdinaryCapturesHappenOnceBeforeBranching() = compile("""
        func selected(): entity {
            /say receiver
            return @a
        }
        func main() {
            external captured = 7
            selected().withEntityCtx { ctx ->
                /say ${'$'}{captured}
            }
            captured = 8
            /say ${'$'}{captured}
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        val machine = execute(main)
        assertEquals(listOf("receiver", "7", "7", "8"), machine.messages)
        machine.assertCleanFrame()
    }

    @Test fun localReturnCleansEachBranchAndSiblingCallbacksStillExecute() = compile("""
        func sideEffect(ctx: PosContext): int {
            /say tail
            return 1
        }
        func main() {
            @a.withEntityCtx { ctx ->
                /say before-return
                return
                /say unreachable
            }
            /say between
            @a.withPosCtx({ ctx -> sideEffect(ctx) })
            /say after
        }
    """.trimIndent()) { main ->
        assertEquals(0, Project.errorCount)
        val machine = execute(main)
        assertEquals(listOf("before-return", "before-return", "between", "tail", "tail", "after"), machine.messages)
        machine.assertCleanFrame()
        val generated = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.filter { it.actualCallBody }
        assertTrue(generated.isNotEmpty())
        assertTrue(generated.all { it.ast == null && it.scope.allVars.none { value -> value is ScopedContextValue } })
        val exported = NamespaceInfo.from(GlobalScope.localNamespaces.getValue("default.test"))
        assertTrue(exported.functions.all { info -> info.get().identifier !in generated.map { it.identifier } })
        assertNull(ContextBorrowing.currentContext)
    }

    @Test fun persistentClosuresAndOldTargetReturnDiagnose() {
        compile("""
                func main() {
                    @s.withPosCtx { ctx ->
                        var escaped: () -> void = { var stolen = ctx; }
                    }
                }
            """.trimIndent()) {
            assertTrue(Project.errorCount > 0)
            assertNull(ContextBorrowing.currentContext)
        }
        compile("""
            func main() {
                @s.withEntityCtx { ctx -> return; }
            }
        """.trimIndent(), version = "1.13") {
            assertTrue(Project.errorCount > 0)
        }
    }
}
