@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.apache.logging.log4j.Logger
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.antlr.MCFPPExprVisitor
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
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
import java.lang.reflect.Proxy
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as P

/**
 * Source-level integration of the namespace macro pipeline.
 *
 * The tests register real `@MNIMacro`/`@MNIFunction` providers in a real namespace and compile
 * original MCFPP source through [MCFPPImVisitor.compileFunctionBody], which is also the path that
 * first runs the exploratory [top.mcfpp.analysis.PrimitiveCompiler]. No test constructs a Code
 * tree by hand.
 */
class MniSourceMacroCallTest {

    class Entries {
        companion object {
            var hostCalls = 0

            @JvmStatic
            @MNIMacro(
                identifier = "ignorePair",
                normalParams = [
                    "int",
                    "int"
                ],
                returnType = "int"
            )
            fun ignorePair(context: MacroContext, first: Value<IntT>, second: Value<IntT>): Expr<IntT> {
                hostCalls++
                return context.quote().expression(
                    context.types().intType(),
                    Consumer { builder ->
                        builder.yield(builder.intLiteral(0))
                    }
                )
            }

            @JvmStatic
            @MNIMacro(
                identifier = "bumpPair",
                normalParams = [
                    "int",
                    "int"
                ],
                returnType = "int"
            )
            fun bumpPair(context: MacroContext, first: Ref<IntT>, second: Ref<IntT>): Expr<IntT> {
                hostCalls++
                return context.quote().expression(
                    context.types().intType(),
                    Consumer { builder ->
                        // Both REF formals must name the caller's single original address. If either
                        // one were copied, the second write would not observe the first write.
                        builder.assign(first, builder.add(first.load(), builder.intLiteral(1)))
                        builder.assign(second, builder.add(second.load(), builder.intLiteral(10)))
                        builder.yield(second.load())
                    }
                )
            }

            @JvmStatic
            @MNIMacro(
                identifier = "constantOnly",
                normalParams = [
                    "int"
                ],
                returnType = "int"
            )
            fun constantOnly(context: MacroContext, value: Const<IntT>): Expr<IntT> {
                hostCalls++
                return context.quote().expression(
                    context.types().intType(),
                    Consumer { builder ->
                        builder.yield(builder.intLiteral(value.intValue()))
                    }
                )
            }

            @JvmStatic
            @MNIMacro(identifier = "explodeSource", returnType = "int")
            fun explodeSource(context: MacroContext): Expr<IntT> {
                hostCalls++
                context.helpers().define<VoidT>(
                    "staged source helper",
                    FunctionSignature.of(emptyList(), context.types().voidType()),
                    GeneratedBody { _, _ -> }
                )
                context.resources().reserve(
                    ResourceKind.OBJECTIVE,
                    "staged source objective",
                    ResourceData.of(ConstantValue.Record(mapOf("criteria" to ConstantValue.Text("dummy"))))
                )
                context.resources().reserve(
                    ResourceKind.STORAGE,
                    "staged source storage",
                    ResourceData.of(ConstantValue.Record(mapOf("initial" to ConstantValue.Integral(3))))
                )
                context.diagnostics().warning(context.callSite(), "staged source warning")
                error("source macro host exploded")
            }
        }
    }

    class NativeEntries {
        companion object {
            val effects = arrayListOf<Int>()
            var warnOnEffect = false

            @JvmStatic
            @MNIFunction(identifier = "effect", normalParams = ["int"], returnType = "int")
            fun effect(order: MCInt, result: ValueWrapper<MCInt>) {
                effects += order.value
                if (warnOnEffect) LogProcessor.warn("source macro effect warning")
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

    private class Fixture(source: String, parameters: List<MCFPPType> = emptyList(), bindState: Boolean = false,
                          private val internalPositions: Set<Int> = emptySet()) {
        val namespace = Namespace("source-macro")
        val file = MCFPPFile(namespace)
        val caller: Function
        val state = MCInt("state")
        val statePlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val stateData = StoredData(
            statePlace,
            NBTPath(StorageSource("source:input")).memberIndex("state"),
            layout = StorageLayout.Scoreboard(state.name, state.sbObject.name)
        )

        init {
            Entries.hostCalls = 0
            NativeEntries.effects.clear()
            NativeEntries.warnOnEffect = false
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            namespace.declareMniFixtures(Entries::class.java)
            namespace.declareMniFixtures(NativeEntries::class.java)
            val bodySource = "{\n$source\n}"
            val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(bodySource, "source-macro.mcfpp"))))
            val body = parser.curlBlock()
            assertEquals(0, parser.numberOfSyntaxErrors, bodySource)
            caller = Function("caller", namespace.identifier, body).apply {
                declarationFile = file
                returnType = MCFPPPrivateType.Void
            }
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            parameters.forEachIndexed { index, type ->
                caller.appendNormalParam(FunctionParam(type, "value$index", caller))
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
            if (internalPositions.isEmpty()) MCFPPImVisitor().compileFunctionBody(caller.ast)
            else {
                fun find(node: org.antlr.v4.runtime.tree.ParseTree): P.FunctionCallContext? {
                    if (node is P.FunctionCallContext) return node
                    return (0 until node.childCount).firstNotNullOfOrNull { find(node.getChild(it)) }
                }
                InternalReferenceCalls.expand(requireNotNull(find(requireNotNull(caller.ast))), caller, internalPositions)
            }
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
            val commands = listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
                caller.commands.filterNot { it is Comment }.map { it.analyze() } +
                "data remove storage mcfpp:system stack_frame[0]"
            return ScoreCommandExecutor(
                commands,
                functions,
                initialScores = mapOf("${state.name} ${state.sbObject.name}" to 0)
            )
        }
    }

    private fun failingWarningLogger(): Logger = Proxy.newProxyInstance(
        Logger::class.java.classLoader,
        arrayOf(Logger::class.java)
    ) { _, method, args ->
        if (method.name == "warn" && args?.firstOrNull()?.toString()?.contains("source macro effect warning") == true)
            throw IllegalStateException("source macro publication failed")
        when (method.returnType) {
            java.lang.Boolean.TYPE -> true
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0.0f
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    } as Logger

    @Test
    fun valueArgumentsAreEvaluatedOnceInSourceOrderEvenWhenTheHostIgnoresThem() = isolated {
        val fixture = Fixture("ignorePair(effect(1), effect(2));")
        fixture.compile()
        assertEquals(1, Entries.hostCalls, "The macro host must run exactly once")
        assertEquals(
            listOf(1, 2),
            NativeEntries.effects,
            "Both VALUE arguments must be evaluated exactly once, left to right"
        )
        assertEquals(0, Project.errorCount)
    }

    @Test
    fun publicationFailureAfterOuterApplyRollsBackSourceStateAndReturnsError() = isolated {
        val fixture = Fixture("ignorePair(effect(1), effect(2));")
        NativeEntries.warnOnEffect = true
        val commands = fixture.caller.commands.filterNot { it is Comment }.map { it.analyze() }
        val functions = fixture.namespace.scope.functions.mapValues { it.value.toList() }
        val resources = GlobalScope.scoreboards.toMap()
        val warnings = Project.warningCount
        val errors = Project.errorCount
        val previousLogger = LogProcessor.logger
        LogProcessor.logger = failingWarningLogger()
        try {
            fixture.compile()
        } finally {
            LogProcessor.logger = previousLogger
            NativeEntries.warnOnEffect = false
        }
        assertEquals(
            warnings,
            Project.warningCount,
            "A failure while publishing the after snapshot must restore its warning counter"
        )
        assertEquals(commands, fixture.caller.commands.filterNot { it is Comment }.map { it.analyze() })
        assertEquals(functions, fixture.namespace.scope.functions.mapValues { it.value.toList() })
        assertEquals(resources, GlobalScope.scoreboards.toMap())
        assertTrue(
            Project.errorCount > errors,
            "The original publication failure must still be reported as an error value diagnostic"
        )
    }

    @Test
    fun refArgumentsKeepTheOriginalAddressAndShareItWhenBothNameTheSameLocation() = isolated {
        val fixture = Fixture("bumpPair(state, state);", bindState = true, internalPositions = setOf(0, 1))
        fixture.compile()
        assertEquals(1, Entries.hostCalls)
        val machine = fixture.execute()
        assertEquals(
            11,
            machine.values["${fixture.state.name} ${fixture.state.sbObject.name}"],
            "First +1 and then +10 must both target the original caller address"
        )
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        assertEquals(0, Project.errorCount)
    }

    @Test
    fun constParameterRejectsARuntimeValueBeforeAnyHostInvocationOrPublication() = isolated {
        val fixture = Fixture("constantOnly(value0);", parameters = listOf(MCFPPBaseType.Int))
        val commands = fixture.caller.commands.filterNot { it is Comment }.map { it.analyze() }
        val resources = GlobalScope.scoreboards.toMap()
        val load = Project.projectLoad.commands.map { it.analyze() }
        val warnings = Project.warningCount
        val capture = LogProcessor.beginDiagnosticCapture()
        try {
            fixture.compile()
            val diagnostics = capture.seal().diagnostics
            assertTrue(
                diagnostics.any { it.message.contains("CONST requires a proven constant argument") },
                diagnostics.joinToString("\n") { it.message }
            )
        } catch (failure: Throwable) {
            capture.discard()
            throw failure
        }
        assertEquals(0, Entries.hostCalls, "A rejected CONST call must not invoke the macro host")
        assertEquals(commands, fixture.caller.commands.filterNot { it is Comment }.map { it.analyze() })
        assertEquals(resources, GlobalScope.scoreboards.toMap())
        assertEquals(load, Project.projectLoad.commands.map { it.analyze() })
        assertEquals(warnings, Project.warningCount)
    }

    @Test
    fun failedMacroPublishesNoCommandsResourcesDiagnosticsHelpersOrNames() = isolated {
        val fixture = Fixture("explodeSource();")
        val functions = fixture.namespace.scope.functions.mapValues { it.value.toList() }
        val scoreboards = GlobalScope.scoreboards.toMap()
        val load = Project.projectLoad.commands.map { it.analyze() }
        val warnings = Project.warningCount
        val names = Project.macroFunction.toMap()
        val commands = fixture.caller.commands.filterNot { it is Comment }.map { it.analyze() }
        val capture = LogProcessor.beginDiagnosticCapture()
        try {
            fixture.compile()
            val diagnostics = capture.seal().diagnostics
            assertTrue(
                diagnostics.any { it.message.contains("source macro host exploded") },
                diagnostics.joinToString("\n") { it.message }
            )
        } catch (failure: Throwable) {
            capture.discard()
            throw failure
        }
        assertEquals(1, Entries.hostCalls, "The failing host must run exactly once")
        assertFalse(fixture.namespace.scope.functions.values.flatten().any { it.identifier.contains("source helper") })
        assertEquals(functions, fixture.namespace.scope.functions.mapValues { it.value.toList() })
        assertEquals(scoreboards, GlobalScope.scoreboards.toMap())
        assertEquals(load, Project.projectLoad.commands.map { it.analyze() })
        assertEquals(warnings, Project.warningCount)
        assertEquals(names, Project.macroFunction.toMap())
        assertEquals(commands, fixture.caller.commands.filterNot { it is Comment }.map { it.analyze() })
    }

    @Test
    fun ordinaryCallsStillUseTheLegacyPathAndDoNotRunAMacroHost() = isolated {
        val fixture = Fixture("effect(7);")
        fixture.compile()
        assertEquals(0, Entries.hostCalls)
        assertEquals(listOf(7), NativeEntries.effects)
        assertEquals(0, Project.errorCount)
    }

    @Test
    fun ordinaryVisitorCallKeepsTheEagerPathWhenNoMacroMatches() = isolated {
        val fixture = Fixture("effect(7);")
        MCFPPFile.currFile = fixture.file
        Project.currNamespace = fixture.namespace.identifier
        Function.currFunction = fixture.caller
        val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString("effect(7)", "source-macro.mcfpp"))))
        val call = parser.functionCall()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val result = MCFPPExprVisitor().visit(call)
        assertFalse(result!!.isError)
        assertEquals(0, Entries.hostCalls)
        assertEquals(listOf(7), NativeEntries.effects)
        assertEquals(0, Project.errorCount)
    }
}
