package top.mcfpp.test.context

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.context.*
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.TypeRelations
import kotlin.test.*

class ContextContractTest {
    private fun compile(source: String, action: () -> Unit) {
        val previous = CompileSettings.ignoreStdLib
        try {
            CompileSettings.ignoreStdLib = true
            MCFPPStringTest.readFromString(source, version = "26.3")
            action()
        } finally { CompileSettings.ignoreStdLib = previous }
    }

    @Test fun nominalViewsExposeOnlyTheirDeclaredComponents() {
        assertTrue(TypeRelations.isSubtype(ContextTypes.EntityExecutionContext, ContextTypes.PosContext))
        assertTrue(TypeRelations.isSubtype(ContextTypes.EntityExecutionContext, ContextTypes.EntityContext))
        assertFalse(TypeRelations.isSubtype(ContextTypes.PosContext, ContextTypes.EntityContext))
        assertNull(ContextTypes.memberType(ContextTypes.PosContext, "executor"))
        assertEquals(ContextTypes.Position, ContextTypes.memberType(ContextTypes.PosContext, "pos"))
        assertEquals(ContextTypes.Dimension, ContextTypes.memberType(ContextTypes.PosContext, "dimension"))
        assertNull(ContextTypes.memberType(ContextTypes.EntityContext, "pos"))
    }

    @Test fun contextRequirementIsResolvedInsidePositionEntry() = compile("""
        context(ctx: PosContext)
        func test() {
            /say ${'$'}{ctx.pos}
        }
        func main() {
            withPosCtx(~ ~1 ~) { space -> test() }
        }
    """.trimIndent()) {
        assertEquals(0, Project.errorCount)
        assertTrue(GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .flatMap { it.commands }.any { "execute positioned ~ ~1 ~" in it.analyze() })
    }

    @Test fun unusedContextRequirementStillRejectsMissingDependency() = compile("""
        context(ctx: PosContext)
        func test() {
            /say test
        }
        func main() { test() }
    """.trimIndent()) { assertTrue(Project.errorCount > 0) }

    @Test fun explicitContextArgumentsSelectAnAncestorBinding() = compile("""
        context(ctx: PosContext)
        func test() {
            /say test
        }
        func main() {
            withPosCtx(~ ~1 ~) { outer ->
                withPosCtx(~ ~2 ~) { inner -> test(ctx = outer) }
            }
        }
    """.trimIndent()) { assertEquals(0, Project.errorCount) }

    @Test fun oldExecuteStatementIsNoLongerSourceSyntax() {
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("func main() { execute(pos = ~ ~ ~) {} }"))))
        parser.removeErrorListeners()
        parser.compilationUnit()
        assertTrue(parser.numberOfSyntaxErrors > 0)
    }

    @Test fun explicitlyProvidedLexicalContextsResolveAndSameLevelAmbiguityFails() {
        val declarations = "context(ctx: PosContext) func required() {}\n"
        compile(declarations + """
            func main() {
                withPosCtx(~ ~1 ~) { outer ->
                    withPosCtx(~ ~2 ~) { inner ->
                        context(outer) { required() }
                        required(ctx = inner)
                    }
                }
            }
        """.trimIndent()) { assertEquals(0, Project.errorCount) }
        compile(declarations + """
            func main() {
                withPosCtx(~ ~1 ~) { outer ->
                    withPosCtx(~ ~2 ~) { inner -> context(outer, inner) { required() } }
                }
            }
        """.trimIndent()) { assertTrue(Project.errorCount > 0) }
    }

    @Test fun contextFunctionValuesRetainRequirementsAtInvocation() {
        val declarations = "context(ctx: PosContext) func required() {}\n"
        compile(declarations + """
            func main() {
                var callback: context(PosContext) () -> void = required
                withPosCtx(~ ~ ~) { space -> callback() }
            }
        """.trimIndent()) { assertEquals(0, Project.errorCount) }
        compile(declarations + """
            func main() {
                var callback: context(PosContext) () -> void = required
                callback()
            }
        """.trimIndent()) { assertTrue(Project.errorCount > 0) }
    }

    @Test fun contextLambdaResolvesItsOwnRequirementsAndFreeVariables() = compile("""
        context(ctx: PosContext) func required() {}
        func main() {
            var amount = 1
            var callback: context(PosContext) () -> void = {
                /say ${'$'}{amount}
                required()
            }
            amount = 2
            withPosCtx(~ ~ ~) { space -> callback() }
        }
    """.trimIndent()) {
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
        val main = functions.single { it.identifier == "main" }
        val commands = functions.associate { it.namespaceID.toString() to it.commands.map { command -> command.analyze() } }
        val machine = top.mcfpp.test.util.ContextCommandExecutor(main.commands.map { it.analyze() }, commands,
            macros = Project.macroFunction.toMap())
        assertEquals(listOf("1"), machine.messages, "The lambda must retain the value captured before amount becomes 2")
    }

    @Test fun narrowPositionParameterCannotReadExecutor() = compile("""
        context(ctx: PosContext)
        func invalid() { var stolen = ctx.executor }
        func main() {
            @s.withEntityCtx { actor -> invalid() }
        }
    """.trimIndent()) { assertTrue(Project.errorCount > 0) }

    @Test fun ordinaryHelperCannotAcquireItsCallersUndeclaredDependency() = compile("""
        context(ctx: PosContext)
        func required() {}
        func helper() { required() }
        func main() {
            withPosCtx(~ ~ ~) { outer -> helper() }
        }
    """.trimIndent()) { assertTrue(Project.errorCount > 0) }

    @Test fun nativeContextsUseTheirOwnArgumentsWithoutChangingTheJvmSignature() = compile("""
        context(space: PosContext)
        func nativeProbe() = top.mcfpp.test.context.ContextContractNative.probe
        func main() { withPosCtx(~ ~1 ~) { frame -> nativeProbe() } }
    """.trimIndent()) {
        assertEquals(0, Project.errorCount)
        assertTrue(ContextContractNative.observed)
        ContextContractNative.observed = false
    }

    @Test fun narrowingACallbackViewKeepsTheEntryGuaranteeForItsChildFrames() = compile("""
        func main() {
            @s.withEntityCtx { actor: Context ->
                withPosCtx(~ ~1 ~) { child -> var executor = child.executor }
            }
        }
    """.trimIndent()) { assertEquals(0, Project.errorCount) }

    @Test fun everyContextModifierHasATypedSourceEntry() {
        val operands = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/mcfpp/lang/context.mcfpp"))
        compile("""
            $operands
            data Dimension { var id: string; constructor(id: string) { this.id = id } }
            data EntityType { var id: string; constructor(id: string) { this.id = id } }
            func main() {
                withCurrentCtx { root ->
                    withRotationCtx(rotation(90f, 0f)) { rotated -> }
                    withFacingCtx(~1 ~ ~) { facing -> }
                    withAnchorCtx(Anchor.eyes) { eyes -> }
                    withAlignedCtx(Axes.xz) { aligned -> }
                    withHeightmapCtx(Heightmap.ocean_floor) { height -> }
                    withDimensionCtx(Dimension("minecraft:the_nether")) { nether -> }
                    withSummonedCtx(EntityType("minecraft:pig")) { spawned ->
                        spawned.executor.withPositionedCtx { located -> }
                        spawned.executor.withRotationCtx { rotated -> }
                        spawned.executor.withFacingCtx(Anchor.feet) { facing -> }
                    }
                }
            }
        """.trimIndent()) {
            assertEquals(0, Project.errorCount)
            val commands = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .flatMap { it.commands }.joinToString("\n") { it.analyze() } + "\n" + Project.macroFunction.values.joinToString("\n")
            for (fragment in listOf("rotated 90.0 0.0", "facing ~1 ~ ~", "anchored eyes", "align xz",
                "positioned over ocean_floor", "execute in", "execute summon",
                "positioned as @s", "rotated as @s", "facing entity @s feet")) assertContains(commands, fragment)
        }
    }
}

object ContextContractNative {
    var observed = false
    @JvmStatic @top.mcfpp.annotations.MNIFunction(normalParams = [], returnType = "void")
    @top.mcfpp.mni.annotation.NoExternalWrites
    fun probe(context: top.mcfpp.mni.NativeCallContext) {
        assertTrue(context.arguments.isEmpty())
        assertEquals(setOf("space"), context.contextArguments.keys)
        context.withContextArguments { arguments ->
            val value = assertIs<ExecutionContextVar>(arguments.getValue("space"))
            assertEquals(ContextTypes.PosContext, value.type)
            assertNotNull(value.requireContext())
        }
        observed = true
    }
}
