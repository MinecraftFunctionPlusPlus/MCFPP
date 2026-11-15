package top.mcfpp.test.context

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.io.FunctionInventory
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.optimization.OptimizationConfig
import top.mcfpp.optimization.PassIds
import top.mcfpp.test.util.*
import kotlin.test.*

class ContextOptimizationTest {
    private data class Output(val main: List<String>, val functions: Map<String, List<String>>,
                              val messages: List<String>, val macros: Map<String, String>)

    private fun compile(source: String, optimization: OptimizationConfig = OptimizationConfig()): Output {
        val config = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf(); this.optimization = optimization }
            CompileSettings.ignoreStdLib = true
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertEquals(0, Project.errorCount)
            val functions = FunctionInventory.all()
                .associate { it.namespaceID.toString() to it.commands.map { command -> command.analyze() } }
            val main = functions.getValue("default.test:main")
            val entities = listOf("A", "B").map { ContextEntity(it, position = ContextPosition(1.0, 2.0, 3.0), rotation = ContextRotation(0.0, 0.0)) }
            val machine = ContextCommandExecutor(listOf(Commands.stackIn().analyze(),
                "data modify storage mcfpp:system stack_frame[0].sentinel set value 73", "function default.test:main"), functions, entities, entities.first(),
                ContextPosition(0.0, 0.0, 0.0), ContextRotation(0.0, 0.0), macros = Project.macroFunction.toMap())
            assertEquals(73, assertIs<IntTag>(machine.readStorage("mcfpp:system", "stack_frame[0].sentinel")).value)
            assertEquals(1, machine.executedFunctionLines.count { it == "data modify storage mcfpp:system stack_frame prepend value {}" } -
                machine.executedFunctionLines.count { it == "data remove storage mcfpp:system stack_frame[0]" })
            return Output(main, functions, machine.messages, Project.macroFunction.toMap())
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
        }
    }

    @Test fun aSingleCommandBecomesOneOrderedChainAndUnusedHelpersDisappear() {
        val result = compile("""
            func main() {
                @e.withEntityCtx { actor ->
                    withPosCtx(~1 ~2 ~3) { space ->
                        @s.withRotationCtx { rotation ->
                            /say branch
                        }
                    }
                }
                /say after
            }
        """.trimIndent())
        assertEquals(listOf("execute as @e[tag=!mcfpp_context_anchor] positioned ~1 ~2 ~3 rotated as @s run say branch", "say after"), result.main)
        assertEquals(listOf("branch", "branch", "after"), result.messages)
        assertTrue(result.functions.keys.none { "context_entry_" in it || "context_body_" in it })
    }

    @Test fun multipleCommandsStayInOneBranchAndSelectorsAreNotReevaluated() {
        val source = """
            func main() {
                @e.withEntityCtx { actor ->
                    withPosCtx(~1 ~ ~) { space ->
                        /say first
                        /summon minecraft:pig ~ ~ ~
                        /say second
                    }
                }
                /say after
            }
        """.trimIndent()
        val optimized = compile(source)
        val reference = compile(source, OptimizationConfig(level = 0))
        assertEquals(listOf("first", "second", "first", "second", "after"), optimized.messages)
        assertEquals(reference.messages, optimized.messages)
        val call = optimized.main.single { it.startsWith("execute ") }
        assertTrue(call.startsWith("execute as @e[tag=!mcfpp_context_anchor] positioned ~1 ~ ~ run function "))
        assertEquals(1, optimized.functions.keys.count { "context_body_" in it })
        assertTrue(optimized.functions.values.flatten().none { "mcfpp:context frames" in it })
        assertTrue(reference.functions.values.flatten().any { "mcfpp:context frames prepend" in it })
    }

    @Test fun localReturnStopsOnlyItsCallbackAndThePassCanBeDisabled() {
        val source = """
            func main() {
                @e.withEntityCtx { actor ->
                    /say branch
                    return
                    /say unreachable
                }
                /say after
            }
        """.trimIndent()
        val optimized = compile(source)
        val reference = compile(source, OptimizationConfig(disabledPasses = setOf(PassIds.CONTEXTS)))
        assertEquals(listOf("branch", "branch", "after"), optimized.messages)
        assertEquals(reference.messages, optimized.messages)
        assertTrue(optimized.main.any { "run function " in it })
        assertTrue(optimized.functions.values.flatten().none { "mcfpp:context frames" in it })
        assertTrue(reference.functions.values.flatten().any { "mcfpp:context frames prepend" in it })
    }

    @Test fun capturedRuntimeValuesKeepTheirEnvironmentWithoutUnusedContextFrames() {
        val result = compile("""
            func main() {
                external value: int = 7
                @e.withEntityCtx { actor ->
                    /say ${'$'}{value}
                }
                /say after
            }
        """.trimIndent())
        assertEquals(listOf("7", "7", "after"), result.messages)
        assertTrue(result.functions.values.flatten().any { "mcfpp:system stack_frame prepend" in it })
        assertTrue(result.functions.values.flatten().none { "mcfpp:context frames" in it })
    }

    @Test fun dynamicContextMacrosFuseTheirCallbackWithoutMovingArgumentPreparation() {
        val source = """
            data Dimension { var id: string; constructor(id: string) { this.id = id } }
            func main() {
                external name: string = "minecraft:the_nether"
                var dimension = Dimension(name)
                withDimensionCtx(dimension) { world ->
                    withPosCtx(~1 ~ ~) { space ->
                        /say entered
                    }
                }
                /say after
            }
        """.trimIndent()
        val optimized = compile(source)
        val reference = compile(source, OptimizationConfig(level = 0))
        assertEquals(listOf("entered", "after"), optimized.messages)
        assertEquals(reference.messages, optimized.messages)
        assertTrue(optimized.macros.values.any { it == "${'$'}execute in ${'$'}(arg_0) positioned ~1 ~ ~ run say entered" },
            optimized.macros.toString())
        assertTrue(optimized.functions.keys.none { "context_entry_" in it || "context_body_" in it })
        assertTrue(optimized.main.any { "macro_temp" in it || "macro_" in it }, "Dynamic operands must still be prepared at the call site")
    }

    @Test fun ordinaryLiteralMacroSyntaxDoesNotCaptureTheDynamicEntryArguments() {
        val source = """
            data Dimension { var id: string; constructor(id: string) { this.id = id } }
            func main() {
                external name: string = "minecraft:the_nether"
                var dimension = Dimension(name)
                withDimensionCtx(dimension) { world ->
                    /say ${'$'}(arg_0)
                }
                /say after
            }
        """.trimIndent()
        val optimized = compile(source)
        val reference = compile(source, OptimizationConfig(level = 0))
        assertEquals(listOf("${'$'}(arg_0)", "after"), optimized.messages)
        assertEquals(reference.messages, optimized.messages)
        assertTrue(optimized.functions.values.flatten().any { it == "say ${'$'}(arg_0)" })
        assertTrue(optimized.functions.keys.none { "context_entry_" in it })
    }

    @Test fun aDynamicCallbackWithLocalReturnRemainsACallbackInsteadOfAnEntrance() {
        val source = """
            data Dimension { var id: string; constructor(id: string) { this.id = id } }
            func main() {
                external name: string = "minecraft:the_nether"
                var dimension = Dimension(name)
                withDimensionCtx(dimension) { world ->
                    /say before
                    return
                    /say unreachable
                }
                /say after
            }
        """.trimIndent()
        val optimized = compile(source)
        val reference = compile(source, OptimizationConfig(level = 0))
        assertEquals(listOf("before", "after"), optimized.messages)
        assertEquals(reference.messages, optimized.messages)
        val callback = optimized.functions.filterKeys { "context_body_" in it }.values.single()
        assertEquals(listOf("say before", "return 1"), callback)
        assertTrue(optimized.functions.keys.none { "context_entry_" in it })
    }
}
