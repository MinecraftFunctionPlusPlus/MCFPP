@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.StorageBinding
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.analysis.StoredData
import top.mcfpp.analysis.Symbol
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueFacts
import top.mcfpp.analysis.ValueKnowledge
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MacroParam
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Comment
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as P

/**
 * Real source integration for C_INLINE: a macro host consumes the deferred callback descriptor,
 * inserts it one or more times, and the actual CONST facts of each insertion are rebound then.
 * No Code tree is constructed by the tests; the original MCFPP body runs through the production
 * prepare -> expand -> validate -> lower -> command path and is executed by the score machine.
 */
class MniSourceProjectionInlineTest {

    class InlineEntries {
        companion object {
            var runTwiceHosts = 0
            var runConstHosts = 0
            var runNestedHosts = 0
            var bumpHosts = 0

            @JvmStatic
            @MNIMacro(
                identifier = "runTwice",
                params = [
                    MacroParam(name = "body", type = "(ref int,int)->int", mode = ParamMode.INLINE),
                    MacroParam(name = "destination", type = "int", mode = ParamMode.REF)
                ],
                returnType = "int"
            )
            fun runTwice(context: MacroContext, body: InlineFn<IntT>, destination: Ref<IntT>): Expr<IntT> {
                runTwiceHosts++
                return context.quote().expression(
                    context.types().intType(),
                    Consumer { builder ->
                        val first = builder.inline<IntT>(body, listOf(InlineArgument.ref(destination), InlineArgument.value(builder.intLiteral(1))))
                        val second = builder.inline<IntT>(body, listOf(InlineArgument.ref(destination), InlineArgument.value(builder.intLiteral(2))))
                        builder.yield(builder.add(builder.mul(first, builder.intLiteral(10)), second))
                    }
                )
            }

            @JvmStatic
            @MNIMacro(
                identifier = "runConst",
                params = [
                    MacroParam(name = "body", type = "(const int)->int", mode = ParamMode.INLINE),
                    MacroParam(name = "first", type = "int", mode = ParamMode.CONST),
                    MacroParam(name = "second", type = "int", mode = ParamMode.CONST),
                    MacroParam(name = "destination", type = "int", mode = ParamMode.REF)
                ],
                returnType = "int"
            )
            fun runConst(
                context: MacroContext,
                body: InlineFn<IntT>,
                first: Const<IntT>,
                second: Const<IntT>,
                destination: Ref<IntT>
            ): Expr<IntT> {
                runConstHosts++
                return context.quote().expression(
                    context.types().intType(),
                    Consumer { builder ->
                        val left = builder.inline<IntT>(body, listOf(InlineArgument.constant(first)))
                        val right = builder.inline<IntT>(body, listOf(InlineArgument.constant(second)))
                        val sum = builder.local(context.types().intType(), builder.add(left, right))
                        builder.assign(destination, sum.load())
                        builder.yield(sum.load())
                    }
                )
            }

            @JvmStatic
            @MNIMacro(
                identifier = "runNested",
                params = [
                    MacroParam(name = "prefix", type = "int"),
                    MacroParam(name = "seed", type = "int", mode = ParamMode.CONST),
                    MacroParam(name = "body", type = "(ref int,const int)->int", mode = ParamMode.INLINE),
                    MacroParam(name = "destination", type = "int", mode = ParamMode.REF)
                ],
                returnType = "int"
            )
            fun runNested(
                context: MacroContext,
                prefix: Value<IntT>,
                seed: Const<IntT>,
                body: InlineFn<IntT>,
                destination: Ref<IntT>
            ): Expr<IntT> {
                runNestedHosts++
                return context.quote().expression(
                    context.types().intType(),
                    Consumer { builder ->
                        val applied = builder.inline<IntT>(body, listOf(InlineArgument.ref(destination), InlineArgument.constant(seed)))
                        builder.assign(destination, builder.add(applied, builder.add(destination.load(), prefix.expr())))
                        builder.yield(destination.load())
                    }
                )
            }

            @JvmStatic
            @MNIMacro(
                identifier = "bump",
                params = [
                    MacroParam(name = "value", type = "int", mode = ParamMode.VALUE)
                ],
                returnType = "int"
            )
            fun bump(context: MacroContext, value: Value<IntT>): Expr<IntT> {
                bumpHosts++
                return context.quote().expression(
                    context.types().intType(),
                    Consumer { builder ->
                        builder.yield(builder.add(value.expr(), builder.intLiteral(1)))
                    }
                )
            }
        }
    }

    class NativeEntries {
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
     * Bind source callback bodies and expand explicit internal locations through production APIs.
     * A pre-host rejection must name the actual source error instead of only reporting host
     * count 0; the original provider/host assertions remain unchanged.
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
        assertEquals(0, Project.errorCount, "Source INLINE compilation diagnostics:\n$detail")
    }

    private class Fixture(
        source: String,
        parameters: List<MCFPPType> = emptyList(),
        bindState: Boolean = false
    ) {
        val namespace = Namespace("source-inline")
        val file = MCFPPFile(namespace)
        val caller: Function
        val state = MCInt("state")
        val statePlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val stateData = StoredData(
            statePlace,
            NBTPath(StorageSource("source:inline")).memberIndex("state"),
            layout = StorageLayout.Scoreboard(state.name, state.sbObject.name)
        )

        init {
            InlineEntries.runTwiceHosts = 0
            InlineEntries.runConstHosts = 0
            InlineEntries.runNestedHosts = 0
            InlineEntries.bumpHosts = 0
            NativeEntries.effects.clear()
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            namespace.injectedBy(InlineEntries::class.java)
            namespace.injectedBy(NativeEntries::class.java)
            val bodySource = "{\n$source\n}"
            val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(bodySource, "source-inline.mcfpp"))))
            val body = parser.curlBlock()
            assertEquals(0, parser.numberOfSyntaxErrors, bodySource)
            caller = Function("caller", namespace.identifier, body).apply {
                declarationFile = file
                returnType = MCFPPPrivateType.Void
            }
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            parameters.forEachIndexed { index, type ->
                caller.appendNormalParam(top.mcfpp.model.function.FunctionParam(type, "value$index", caller))
            }
            if (parameters.isNotEmpty()) caller.buildParamVar()
            state.symbol = Symbol(statePlace.root, "state", MCFPPBaseType.Int.typeId, true, forceRuntime = true)
            state.storageBinding = StorageBinding(stateData, statePlace, stateData.path)
            stateData.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
            stateData.declarations[statePlace] = state.symbol!!
            stateData.facts.write(
                statePlace,
                ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown)
            )
            if (bindState) caller.scope.vars["state"] = state
        }

        fun compile() {
            MCFPPFile.currFile = file
            Project.currNamespace = namespace.identifier
            Function.currFunction = caller
            fun find(node: org.antlr.v4.runtime.tree.ParseTree): P.FunctionCallContext? {
                if (node is P.FunctionCallContext) return node
                return (0 until node.childCount).firstNotNullOfOrNull { find(node.getChild(it)) }
            }
            val call = requireNotNull(find(requireNotNull(caller.ast)))
            val destination = if (call.namespaceID().text == "runTwice") 1 else 3
            InternalReferenceCalls.expand(call, caller, setOf(destination))
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
            // Invoke the caller as a real registered function, exactly like the source entry point:
            // a function-level return stops only this body and the outer frame pop still runs.
            val callerId = caller.namespaceID.toString()
            check(functions.containsKey(callerId)) { "Caller function is not registered: $callerId" }
            val commands = listOf(
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "function $callerId",
                "data remove storage mcfpp:system stack_frame[0]"
            )
            return ScoreCommandExecutor(
                commands,
                functions,
                initialScores = mapOf("${state.name} ${state.sbObject.name}" to 0)
            )
        }
    }

    @Test
    fun repeatedInsertionUsesFreshLocalsAndWritesOnlyTheExplicitRefParameter() = isolated {
        val fixture = Fixture(
            "runTwice({ place, x -> var local:int = x; place += local; return local; }, state);",
            bindState = true
        )
        assertCompiled(fixture)
        assertEquals(1, InlineEntries.runTwiceHosts)
        val machine = fixture.execute()
        // First insertion adds 1 to the original capture; the second adds 2. Both body locals must
        // be distinct because the host keeps both results live in one expression.
        assertEquals(3, machine.values["${fixture.state.name} ${fixture.state.sbObject.name}"])
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        assertEquals(0, Project.errorCount)
    }

    @Test
    fun actualConstFactsAreReboundAtEachInlineInsertion() = isolated {
        val fixture = Fixture("runConst({ x -> return x; }, 7, 9, state);", bindState = true)
        assertCompiled(fixture)
        assertEquals(1, InlineEntries.runConstHosts)
        val machine = fixture.execute()
        // A preview without facts must never substitute one value for both insertions.
        assertEquals(16, machine.values["${fixture.state.name} ${fixture.state.sbObject.name}"])
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        assertEquals(0, Project.errorCount)
    }

    @Test
    fun dependentConstantLocalClosesOnlyAtTheActualInsertionFacts() = isolated {
        // `12 / x` has no closed value for a guessed CONST fact (false/0). Discovery must not bind
        // or fold the body; each real insertion supplies its own nonzero x and closes `saved`.
        val fixture = Fixture(
            "runConst({ x -> const saved:int = 12 / x; return saved; }, 3, 4, state);",
            bindState = true
        )
        assertCompiled(fixture)
        assertEquals(1, InlineEntries.runConstHosts)
        val machine = fixture.execute()
        assertEquals(7, machine.values["${fixture.state.name} ${fixture.state.sbObject.name}"])
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        assertEquals(0, Project.errorCount)
    }

    @Test
    fun nestedSourceMacroCallInsideTheDeferredBodyKeepsOrderedPreludeAndOriginalCapture() = isolated {
        val fixture = Fixture(
            "runNested(effect(4), 5, { place, x -> place = bump(place); return x; }, state);",
            bindState = true
        )
        assertCompiled(fixture)
        assertEquals(1, InlineEntries.runNestedHosts)
        assertEquals(1, InlineEntries.bumpHosts)
        assertEquals(listOf(4), NativeEntries.effects)
        val machine = fixture.execute()
        // effect(4) is the ordered VALUE prelude of the source call. The callback's nested source
        // macro bumps the original state once and returns the insertion's CONST 5; the host then
        // adds the prelude prefix and the callback result to the same REF.
        assertEquals(10, machine.values["${fixture.state.name} ${fixture.state.sbObject.name}"])
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        assertEquals(0, Project.errorCount)
    }

    @Ignore("User deferred nested lambda callback REF forwarding (2026-10-22); retain body and failure evidence")
    @Test
    fun generatedMacroCallForwardsTheDeferredSourceCallbackWithItsWholePrelude() = isolated {
        // The callback body itself performs a source macro call with its own ordered effect
        // prelude and its own INLINE lambda. The inline binder turns that source call into a
        // generated Code MacroCall; the nested host must receive the forwarded deferred callback,
        // import prelude+result as one unit, and still share the original `state` capture.
        val fixture = Fixture(
            """
            runNested(effect(4), 5, { place, x ->
                var base:int = x + 1;
                var nested:int = runNested(effect(6), 7, { innerPlace, y -> innerPlace = bump(innerPlace); return y + base; }, place);
                return nested;
            }, state);
            """.trimIndent(),
            bindState = true
        )
        assertCompiled(fixture)
        assertEquals(2, InlineEntries.runNestedHosts)
        assertEquals(1, InlineEntries.bumpHosts)
        assertEquals(listOf(4, 6), NativeEntries.effects)
        val machine = fixture.execute()
        // `base` is an enclosing callback local captured only by the forwarded lambda. It must be
        // freshly remapped with the outer insertion while `state` keeps its original location.
        // Outer prefix 4 and inner prefix 6 each run once. The inner callback bumps the original
        // state to 1 and returns 7 + (5 + 1) = 13; the inner host writes 13 + (1 + 6) = 20; the
        // outer callback returns that forwarded result and the outer host writes 20 + (20 + 4) = 44.
        assertEquals(44, machine.values["${fixture.state.name} ${fixture.state.sbObject.name}"])
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        assertEquals(0, Project.errorCount)
    }
}
