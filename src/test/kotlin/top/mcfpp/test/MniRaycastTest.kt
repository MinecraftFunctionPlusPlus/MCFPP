package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Comment
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.BlockPredicate
import top.mcfpp.mni.api.BoolT
import top.mcfpp.mni.api.Const
import top.mcfpp.mni.api.Expr
import top.mcfpp.mni.api.InlineArgument
import top.mcfpp.mni.api.InlineFn
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.kotlin
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.ContextBlock
import top.mcfpp.test.util.ContextCommandExecutor
import top.mcfpp.test.util.ContextPosition
import top.mcfpp.test.util.ContextRotation
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.util.function.Consumer
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as P

/**
 * Source-level acceptance fixture for the fixed-step block ray example in
 * `examples/mni/raycast`.
 *
 * The Kotlin provider below intentionally mirrors `KotlinRaycastMacro.kt`. Every case enters the
 * real source compiler (`MCFPPImVisitor.compileFunctionBody`), so the source INLINE callback,
 * captures, early returns, contextual `at`, block predicates and the provider body all run
 * through prepare -> expand -> validate -> lower -> committed production commands. The generated
 * registered functions are then executed by the delivered strict
 * [ContextCommandExecutor], not by a hand-built Code/Plan and not by a manufactured hit list.
 *
 * The world has stone at `x = 0` and `x = -1` on `y = 64, z = 0`, the ray starts at
 * `(0.5, 64, 0.5)` with yaw 90, and the documented step is 0.5. The five sampled points are
 * `x = 0.5, 0.0, -0.5, -1.0, -1.5`. The first two are both block `x = 0` and the next two are
 * both block `x = -1`, so the continue strategy must count four hits over two distinct blocks.
 * The ray runs with no executor entity, which also proves the ambient rotation is used without
 * assuming `@s`.
 *
 * Two different source callbacks use the exact same provider algorithm: one returns true to
 * continue and one returns false to stop. A third callback returns true early from one insertion
 * to show that a callback return is local and that the callback bool controls later sampling.
 */
class MniRaycastTest {

    /**
     * Same provider algorithm as `KotlinRaycastMacro.kt`: CONST count, fixed documented step,
     * ambient position/rotation, one runtime gate per unrolled sample, block test inside the
     * sampled `at` point, and callback bool written back to the provider-owned `running` local.
     */
    class RaycastEntries {
        companion object {
            var hostCalls = 0

            @JvmStatic
            @MNIMacro(identifier = "raycast", normalParams = [
                "int",
                "int",
                "(int,int)->int",
                "(int)->bool"
            ], returnType = "int")
            fun raycastFold(context: MacroContext, samples: Const<IntT>, initial: Value<IntT>,
                            step: InlineFn<IntT>, keepGoing: InlineFn<BoolT>): Expr<IntT> {
                hostCalls++
                val count = samples.intValue()
                require(count >= 0)
                return context.quote().kotlin().expression(context.types().intType()) {
                    val running = local("running", context.types().boolType(), boolLiteral(true))
                    val accumulator = local("accumulator", context.types().intType(), initial.expr())
                    for (index in 0 until count) ifThen(running.load()) {
                        val mc = minecraft()
                        val point = mc.localOffset(mc.currentPosition(), mc.currentRotation(), 0.0, 0.0, index * SAMPLE_STEP)
                        mc.at(point, Consumer { sampled ->
                            val sample = sampled.minecraft()
                            sample.ifThen(sample.blockMatches(sample.blockAt(sample.currentPosition()), BlockPredicate.block("minecraft:stone")), Consumer { hit ->
                                hit.assign(accumulator, hit.inline(step, listOf(InlineArgument.value(accumulator.load()), InlineArgument.value(hit.intLiteral(index)))))
                                hit.assign(running, hit.inline(keepGoing, listOf(InlineArgument.value(hit.intLiteral(index)))))
                            })
                        })
                    }
                    yield(accumulator.load())
                }
            }

            @JvmStatic
            @MNIMacro(
                identifier = "raycast",
                normalParams = [
                    "int",
                    "(int)->bool"
                ],
                returnType = "void"
            )
            fun raycast(context: MacroContext, samples: Const<IntT>, body: InlineFn<BoolT>): Block {
                hostCalls++
                val count = samples.intValue()
                require(count >= 0) { "Raycast sample count must not be negative" }
                return context.quote().kotlin().block {
                    val running = local("running", context.types().boolType(), boolLiteral(true))
                    for (index in 0 until count) {
                        ifThen(running.load()) {
                            val active = this
                            val mc = active.minecraft()
                            // CONST specialization keeps the public offset API literal.
                            val point = mc.localOffset(
                                mc.currentPosition(),
                                mc.currentRotation(),
                                0.0,
                                0.0,
                                index * SAMPLE_STEP
                            )
                            mc.at(
                                point,
                                Consumer { sampled ->
                                    val sample = sampled.minecraft()
                                    val hit = sample.blockMatches(
                                        sample.blockAt(sample.currentPosition()),
                                        BlockPredicate.block("minecraft:stone")
                                    )
                                    sample.ifThen(
                                        hit,
                                        Consumer { hitScope ->
                                            hitScope.assign(
                                                running,
                                                hitScope.inline(
                                                    body,
                                                    listOf(
                                                        InlineArgument.value(hitScope.intLiteral(index))
                                                    )
                                                )
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            private const val SAMPLE_STEP = 0.5
        }
    }

    private class Fixture(owner: String, source: String, private val captured: List<String>) {
        val namespace = Namespace(owner)
        private val file = MCFPPFile(namespace)
        val caller: Function

        init {
            RaycastEntries.hostCalls = 0
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            namespace.declareMniFixtures(RaycastEntries::class.java)

            val bodySource = "{\n$source\n}"
            val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(bodySource, "$owner.mcfpp"))))
            val body = parser.curlBlock()
            assertEquals(0, parser.numberOfSyntaxErrors, bodySource)
            caller = Function("caller", namespace.identifier, body).apply {
                declarationFile = file
                returnType = MCFPPPrivateType.Void
            }
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
        }

        fun compile() {
            MCFPPFile.currFile = file
            Project.currNamespace = namespace.identifier
            Function.currFunction = caller
            MCFPPImVisitor().compileFunctionBody(caller.ast)
        }

        /**
         * Execute the actual source-compiled caller through its registered function boundary (so a
         * caller `return` exits only that function) with the strict generated-command context
         * executor. The completion copies are real NBT commands inserted before the caller frame is
         * popped, so the observed values are the values the generated function wrote to the
         * caller's real stack frame.
         */
        fun execute(): ContextCommandExecutor {
            val functions = functionMap()
            // Invoke the caller as a real function. A `return` inside the caller body must stop only
            // that function body; the completion copies and the outer frame pop still run.
            val callerId = caller.namespaceID.toString()
            check(functions.containsKey(callerId)) { "Caller function is not registered: $callerId" }
            val commands = listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
                "function $callerId" +
                captured.map { name ->
                    "data modify storage verification:ray $name set from storage " +
                        "mcfpp:system stack_frame[0].$name"
                } +
                "data remove storage mcfpp:system stack_frame[0]"
            return ContextCommandExecutor(
                commands = commands,
                functions = functions,
                entities = emptyList(),
                rootContext = null,
                rootPosition = ContextPosition(0.5, 64.0, 0.5),
                rootRotation = ContextRotation(90.0, 0.0),
                world = { x, y, z ->
                    if (y == 64 && z == 0 && (x == 0 || x == -1)) {
                        ContextBlock("minecraft:stone")
                    } else {
                        null
                    }
                },
                macros = Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }
            )
        }

        fun read(machine: ContextCommandExecutor, name: String): Int = (machine.readStorage("verification:ray", name) as IntTag).value

        fun producedCommands(): List<String> {
            val generated = generatedFunctions()
            return (caller.commands + generated.flatMap { it.commands })
                .filterNot { it is Comment }
                .map { it.analyze() }
        }

        fun generatedFunctions(): List<Function> = namespace.scope.functions.values.flatten()
            .filter { it !== caller && it.identifier.startsWith("mni/") }

        private fun functionMap(): Map<String, List<String>> = (
            GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values +
                GlobalScope.stdNamespaces.values
            )
            .flatMap { it.scope.functions.values.flatten() }
            .associate { function ->
                function.namespaceID.toString() to
                    function.commands.filterNot { it is Comment }.map { it.analyze() }
            } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }
                .mapValues { it.value.lines() }
    }

    private fun isolated(action: () -> Unit) {
        val before = CompilerStateJournal.checkpoint()
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            Project.projectLoad = Function("load", Project.config.rootNamespace, null)
            MCFPPFile.currFile = null
            Function.forcedField = null
            action()
        } finally {
            before.restore()
        }
    }

    /**
     * Compile through the real source pipeline while retaining the compiler's own diagnostics.
     * A defect exposed by another production writer is reported verbatim instead of only as an
     * error counter.
     */
    private fun assertCompiled(fixture: Fixture) {
        val publication = LogProcessor.beginDiagnosticCapture().use { capture ->
            fixture.compile()
            capture.seal()
        }
        val detail = publication.diagnostics
            .filter { it.level == LogLevel.ERROR }
            .joinToString("\n") { diagnostic ->
                val origin = diagnostic.source?.let { source ->
                    listOfNotNull(source.file, source.line?.toString(), source.column?.toString())
                        .joinToString(":")
                }?.takeIf { it.isNotEmpty() }?.let { " at $it" } ?: ""
                diagnostic.message + origin
            }
            .ifEmpty { "No error diagnostic was captured" }
        assertEquals(0, Project.errorCount, "Raycast compilation diagnostics:\n$detail")
    }

    private fun expectedSamples() = listOf(
        ContextPosition(0.5, 64.0, 0.5),
        ContextPosition(0.0, 64.0, 0.5),
        ContextPosition(-0.5, 64.0, 0.5),
        ContextPosition(-1.0, 64.0, 0.5),
        ContextPosition(-1.5, 64.0, 0.5)
    )

    private fun assertSample(actual: ContextPosition, expected: ContextPosition) {
        assertEquals(expected.x, actual.x, 1e-9, "sample x")
        assertEquals(expected.y, actual.y, 1e-9, "sample y")
        assertEquals(expected.z, actual.z, 1e-9, "sample z")
    }

    /**
     * Continue strategy: all five points are tested, the two consecutive points in each stone
     * block are separate hits, the callback receives the real sample index, and the caller tail
     * runs. `return true` keeps the outer sampling going.
     */
    @Test
    fun continueStrategyCountsRepeatedPointsInTheSameBlock() = isolated {
        val fixture = Fixture(
            "ray.continue",
            """
            // For these five samples, encode count*10+last in one explicit int accumulator.
            external packed: int = raycast(5, 0, { accumulator, i ->
                var sample:int = i
                (accumulator / 10 + 1) * 10 + sample
            }, { true })
            external hits: int = packed / 10
            external last: int = -1
            if (packed > 0) { last = packed % 10; }
            external tail = hits + 1
            """.trimIndent(),
            listOf("hits", "last", "tail")
        )
        assertCompiled(fixture)
        assertEquals(1, RaycastEntries.hostCalls, "The CONST count expands once on the host")

        val machine = fixture.execute()
        val tests = machine.blockTests
        assertEquals(5, tests.size, tests.joinToString("\n"))
        val expected = expectedSamples()
        expected.zip(tests).forEach { (position, test) ->
            assertSample(test.position, position)
            assertEquals(
                ContextRotation(90.0, 0.0),
                test.rotation,
                "Ambient rotation must stay 90/0 at every sample"
            )
            assertEquals(null, test.executor, "The ray must not need an executor entity")
        }
        assertEquals(
            listOf(true, true, true, true, false),
            tests.map { it.matched },
            "Stone blocks x=0 and x=-1 are each covered by two consecutive points"
        )

        val matched = tests.filter { it.matched }
        assertEquals(4, matched.size, "Continue strategy sees every sampled hit")
        val distinct = matched.map { test ->
            Triple(
                floor(test.position.x).toInt(),
                floor(test.position.y).toInt(),
                floor(test.position.z).toInt()
            )
        }.distinct()
        assertEquals(2, distinct.size, "Four sample hits cover only two distinct blocks")
        assertTrue(
            matched.groupingBy { test ->
                Triple(
                    floor(test.position.x).toInt(),
                    floor(test.position.y).toInt(),
                    floor(test.position.z).toInt()
                )
            }.eachCount().values.any { it > 1 },
            "A block must be hit by more than one sample"
        )

        assertEquals(4, fixture.read(machine, "hits"))
        assertEquals(
            3,
            fixture.read(machine, "last"),
            "The callback VALUE argument is the current sample index"
        )
        assertEquals(
            5,
            fixture.read(machine, "tail"),
            "The caller tail after the ray must still run"
        )
        assertTrue(
            machine.executed.none { "rotated" in it },
            "A current-rotation local offset must not re-emit or replace the ambient rotation"
        )
        assertNoRuntimeDispatch(fixture)
    }

    /**
     * Stop strategy: the same provider algorithm with a callback that returns false on the first
     * hit. The provider skips all remaining samples (so only the first real block test runs), but
     * the caller tail still executes.
     */
    @Test
    fun stopStrategySkipsRemainingSamplesButNotTheCallerTail() = isolated {
        val fixture = Fixture(
            "ray.stop",
            """
            external packed: int = raycast(5, 0, { accumulator, i ->
                (accumulator / 10 + 1) * 10 + i
            }, { return false; })
            external hits: int = packed / 10
            external last: int = -1
            if (packed > 0) { last = packed % 10; }
            external tail = hits + 1
            """.trimIndent(),
            listOf("hits", "last", "tail")
        )
        assertCompiled(fixture)
        assertEquals(1, RaycastEntries.hostCalls)

        val machine = fixture.execute()
        val tests = machine.blockTests
        assertEquals(
            1,
            tests.size,
            "After the first false result the provider must skip later samples entirely"
        )
        assertSample(tests.single().position, expectedSamples().first())
        assertTrue(tests.single().matched)
        assertEquals(ContextRotation(90.0, 0.0), tests.single().rotation)
        assertEquals(null, tests.single().executor)

        assertEquals(1, fixture.read(machine, "hits"))
        assertEquals(0, fixture.read(machine, "last"))
        assertEquals(
            2,
            fixture.read(machine, "tail"),
            "Stopping the ray must not exit the caller function"
        )
        assertNoRuntimeDispatch(fixture)
    }

    /**
     * Callback early return: `return true` at sample 1 leaves only that insertion, then the same
     * callback continues for samples 2-4. The skipped increment proves the early return is local,
     * and the five real block tests prove the outer sampling continued.
     */
    @Test
    fun callbackEarlyReturnIsLocalAndTheBoolResultStillControlsSampling() = isolated {
        val fixture = Fixture(
            "ray.early",
            """
            external packed: int = raycast(5, 0, { accumulator, i ->
                var sample:int = i
                if (sample == 1) {
                    return accumulator
                }
                (accumulator / 10 + 1) * 10 + sample
            }, { true })
            external hits: int = packed / 10
            external last: int = -1
            if (packed > 0) { last = packed % 10; }
            external tail = hits + 1
            """.trimIndent(),
            listOf("hits", "last", "tail")
        )
        assertCompiled(fixture)
        assertEquals(1, RaycastEntries.hostCalls)

        val machine = fixture.execute()
        assertEquals(
            5,
            machine.blockTests.size,
            "An early callback return must not stop the provider's outer sampling"
        )
        assertEquals(listOf(true, true, true, true, false), machine.blockTests.map { it.matched })
        assertEquals(
            3,
            fixture.read(machine, "hits"),
            "Sample 1 returns before its increment; samples 0, 2 and 3 still increment"
        )
        assertEquals(3, fixture.read(machine, "last"))
        assertEquals(
            4,
            fixture.read(machine, "tail"),
            "The caller tail runs after the early callback return"
        )
        assertNoRuntimeDispatch(fixture)
    }

    private fun assertNoRuntimeDispatch(fixture: Fixture) {
        val generated = fixture.generatedFunctions()
        assertTrue(generated.isNotEmpty(), "The expansion must lower real contextual helper blocks")
        assertTrue(
            generated.all { it.normalParams.isEmpty() },
            "No callback id, parameter area or dispatch argument may reach generated helpers: " +
                generated.map { it.namespaceID }
        )
        val rendered = fixture.producedCommands().joinToString("\n")
        assertFalse(
            Regex("function \\S*:raycast(?:\\s|\\z)").containsMatchIn(rendered),
            "raycast must remain a static source expansion, not a runtime macro function:\n$rendered"
        )
        assertTrue(
            Project.macroFunction.keys.none { it.contains("raycast") },
            "raycast must not register a command-macro dispatch: ${Project.macroFunction.keys}"
        )
    }
}
