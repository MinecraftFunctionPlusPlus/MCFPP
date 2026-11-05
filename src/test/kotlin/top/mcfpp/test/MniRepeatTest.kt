package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Comment
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.Expr
import top.mcfpp.mni.api.InlineArgument
import top.mcfpp.mni.api.InlineFn
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.VoidT
import top.mcfpp.mni.api.kotlin
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as P

/**
 * Authoring fixture for the public repeat example in `examples/mni/repeat`.
 *
 * The Kotlin entry below intentionally mirrors `KotlinRepeatMacro.kt`. Tests enter through the real
 * source compiler (`MCFPPImVisitor.compileFunctionBody`), so the source INLINE callback, captures,
 * early return and the repeat while-loop all run through prepare -> expand -> validate -> lower.
 * Every assertion executes the generated commands with [ScoreCommandExecutor], not a hand-built
 * Code tree.
 *
 * Two different source callbacks use the same provider algorithm: an unconditional accumulator and
 * a guarded callback whose `return` skips only its own insertion. The guarded call also proves the
 * caller tail still runs. A nested repeat call proves distinct expansions keep their loop counters
 * and callback locals independent. The captured `total`/`tail` are real `dynamic` caller locals, so
 * their storage locations and initialization commands come from the source compiler rather than a
 * synthetic fixture adapter.
 */
class MniRepeatTest {

    class RepeatEntries {
        companion object {
            var hostCalls = 0

            @JvmStatic
            @MNIMacro(identifier = "repeat", normalParams = [
                "int",
                "int",
                "(int,int)->int"
            ], returnType = "int")
            fun repeatFold(context: MacroContext, times: Value<IntT>, initial: Value<IntT>, body: InlineFn<IntT>): Expr<IntT> {
                hostCalls++
                return context.quote().kotlin().expression(context.types().intType()) {
                    val iteration = local("i", context.types().intType(), intLiteral(0))
                    val limit = local("limit", context.types().intType(), times.expr())
                    val accumulator = local("accumulator", context.types().intType(), initial.expr())
                    whileLoop({ lt(iteration.load(), limit.load()) }, {
                        assign(accumulator, inline(body, listOf(InlineArgument.value(accumulator.load()), InlineArgument.value(iteration.load()))))
                        assign(iteration, add(iteration.load(), intLiteral(1)))
                    })
                    yield(accumulator.load())
                }
            }

            @JvmStatic
            @MNIMacro(
                identifier = "repeat",
                normalParams = [
                    "int",
                    "(int)->void"
                ],
                returnType = "void"
            )
            fun repeat(context: MacroContext, times: Value<IntT>, body: InlineFn<VoidT>): Block {
                hostCalls++
                return context.quote().kotlin().block {
                    // The loop counter is an independent generated local. The VALUE count is
                    // already a caller-side prelude value; bind it once for every condition test.
                    val iteration = local("i", context.types().intType(), intLiteral(0))
                    val limit = local("limit", context.types().intType(), times.expr())
                    whileLoop(
                        { lt(iteration.load(), limit.load()) },
                        {
                            inlineVoid(body, listOf(InlineArgument.value(iteration.load())))
                            // Belongs to the provider, not the callback: an early callback
                            // return must not skip this increment or leave the repeat loop.
                            assign(iteration, add(iteration.load(), intLiteral(1)))
                        }
                    )
                }
            }
        }
    }

    /** Real native call with a host-side effect, used to observe VALUE count evaluation. */
    class EffectEntries {
        companion object {
            val effects = arrayListOf<Int>()

            @JvmStatic
            @MNIFunction(identifier = "effect", normalParams = ["int"], returnType = "int")
            fun effect(order: MCInt, result: ValueWrapper<MCInt>) {
                effects += order.value
                result.set(MCInt(order.value))
            }
        }
    }

    private class Fixture(owner: String, source: String) {
        val namespace = Namespace(owner)
        private val file = MCFPPFile(namespace)
        val caller: Function

        /**
         * The compiler-created caller locals captured by the source callbacks. The test body
         * declares them as `dynamic` so their real storage locations and initialization commands
         * come from the normal source pipeline instead of a synthetic scoreboard/NBT adapter.
         */
        val total: MCInt
            get() = requireNotNull(caller.scope.getVar("total") as? MCInt) {
                "The caller source must declare `external total: int = 0`"
            }

        val tail: MCInt
            get() = requireNotNull(caller.scope.getVar("tail") as? MCInt) {
                "The caller source must declare `external tail: int = 0`"
            }

        init {
            RepeatEntries.hostCalls = 0
            EffectEntries.effects.clear()
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            namespace.declareMniFixtures(RepeatEntries::class.java)
            namespace.declareMniFixtures(EffectEntries::class.java)

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

        fun execute(): ScoreCommandExecutor {
            val functions = (
                GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values +
                    GlobalScope.stdNamespaces.values
                ).flatMap { it.scope.functions.values.flatten() }
                .associate { function ->
                    function.namespaceID.toString() to
                        function.commands.filterNot { it is Comment }.map { it.analyze() }
                } +
                Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { it.value.lines() }
            // Invoke the caller as a real function. A `return` inside the caller body must stop only
            // that function body; the caller tail statements and the outer frame pop still run.
            val callerId = caller.namespaceID.toString()
            check(functions.containsKey(callerId)) { "Caller function is not registered: $callerId" }
            val commands = listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
                "function $callerId" +
                "data remove storage mcfpp:system stack_frame[0]"
            return ScoreCommandExecutor(commands, functions)
        }

        /** Caller plus all lowering functions committed into this fixture's namespace. */
        fun producedCommands(): List<String> {
            val generated = generatedFunctions()
            return (caller.commands + generated.flatMap { it.commands })
                .filterNot { it is Comment }
                .map { it.analyze() }
        }

        fun generatedFunctions(): List<Function> = namespace.scope.functions.values.flatten()
            .filter { it !== caller && it.identifier.startsWith("mni/") }
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
     * The failure message names the actual source error instead of only the error counter, so a
     * nested-callback defect exposed by another production writer is reported verbatim.
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
        assertEquals(0, Project.errorCount, "Repeat compilation diagnostics:\n$detail")
    }

    /**
     * Same provider algorithm, two different source callbacks. The first accumulates every
     * iteration; the second returns early at i == 2 and must still let the loop and caller tail
     * continue. Both callbacks write the original captured `total`, not a copied parameter area.
     */
    @Test
    fun sameAlgorithmServesTwoCallbacksAndEarlyReturnKeepsTheCallerTail() = isolated {
        val fixture = Fixture(
            "repeat.two",
            """
            external total: int = repeat(5, 0, { accumulator, i ->
                var local:int = i
                accumulator + local
            })
            external tail: int = 0
            total = repeat(5, total, { accumulator, i ->
                var local:int = i
                if (i == 2) {
                    return accumulator
                }
                accumulator + local
            })
            tail = total + 1
            """.trimIndent()
        )
        assertCompiled(fixture)
        assertEquals(2, RepeatEntries.hostCalls, "Each source call expands exactly one provider host")

        val machine = fixture.execute()
        val total = fixture.total
        val tail = fixture.tail
        // First callback: 0+1+2+3+4 == 10. Second callback: 0+1+(skip 2)+3+4 == 8.
        assertEquals(18, machine.read(total), "Both explicit returned accumulators must contribute")
        assertEquals(
            19,
            machine.read(tail),
            "The callback return must not exit the caller; `tail = total + 1` must run"
        )
        assertEquals(0, machine.stackDepth)
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        assertNoNumberedDispatch(fixture)
    }

    /** VALUE count evaluates once in the caller prelude; the callback sees 0, 1, 2. */
    @Test
    fun valueCountIsEvaluatedOnceAndCallbackReceivesTheCurrentIteration() = isolated {
        val fixture = Fixture(
            "repeat.once",
            """
            external total: int = repeat(effect(3), 0, { accumulator, i -> accumulator + i })
            external tail: int = 0
            tail = total + 1
            """.trimIndent()
        )
        assertCompiled(fixture)
        assertEquals(1, RepeatEntries.hostCalls)
        assertEquals(
            listOf(3),
            EffectEntries.effects,
            "The VALUE count expression must execute exactly once in source order"
        )

        val machine = fixture.execute()
        assertEquals(
            3,
            machine.read(fixture.total),
            "The callback VALUE argument must be the current iteration, so 0+1+2 == 3"
        )
        assertEquals(4, machine.read(fixture.tail))
        assertEquals(0, machine.stackDepth)
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        assertNoNumberedDispatch(fixture)
    }

    /**
     * Nested repeat call: the outer callback inserts an inner repeat. The outer counter, inner
     * counter and both callback locals must be separate expansions; the inner callback also has to
     * retain the outer iteration and the caller's original `total` capture.
     */
    @Test
    fun nestedExpansionsKeepCountersAndCallbackLocalsIndependent() = isolated {
        val fixture = Fixture(
            "repeat.nested",
            """
            external total: int = repeat(2, 0, { accumulator, i ->
                var inner:int = repeat(2, 0, { subtotal, j ->
                    var local:int = i * 10 + j
                    subtotal + local
                })
                accumulator + inner
            })
            external tail: int = 0
            tail = total + 1
            """.trimIndent()
        )
        assertCompiled(fixture)
        assertEquals(2, RepeatEntries.hostCalls, "Outer and inner repeat each run their provider host once")

        val machine = fixture.execute()
        assertEquals(
            22,
            machine.read(fixture.total),
            "0+1+10+11 == 22 when the outer counter, inner counter and both locals stay distinct"
        )
        assertEquals(
            23,
            machine.read(fixture.tail),
            "The caller tail after the nested repeats is total + 1"
        )
        assertEquals(0, machine.stackDepth)
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        assertNoNumberedDispatch(fixture)
    }

    private fun assertNoNumberedDispatch(fixture: Fixture) {
        val generated = fixture.generatedFunctions()
        assertTrue(generated.isNotEmpty(), "The expansion must lower real generated control-flow blocks")
        assertTrue(
            generated.all { it.normalParams.isEmpty() },
            "No callback or parameter area may be passed to generated blocks: " +
                generated.map { it.namespaceID }
        )
        val rendered = fixture.producedCommands().joinToString("\n")
        // A generated helper may legitimately carry "callback" in its name. What matters is that
        // repeat stays a static source expansion: no runtime repeat function and no command-macro
        // dispatch entry may be produced for it.
        assertFalse(
            Regex("function \\S*:repeat(?:\\s|\\z)").containsMatchIn(rendered),
            "repeat must not become a runtime macro function:\n$rendered"
        )
        assertTrue(
            Project.macroFunction.keys.none { it.contains("repeat") },
            "repeat must not register a command-macro dispatch: ${Project.macroFunction.keys}"
        )
    }
}
