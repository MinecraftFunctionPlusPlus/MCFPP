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
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Comment
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.Ref
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.kotlin
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.ValueWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as P

/**
 * Compiler-internal location regression for the original damage builder algorithm.
 *
 * Explicit Kotlin position indices provide Ref<IntT> capabilities. Public source addresses use
 * ScoreRef/NBTRef VALUE parameters; these tests retain the original alias/copy/frame assertions.
 */
class MniDamageTest {

    class DamageEntries {
        companion object {
            var hostCalls = 0

            @JvmStatic
            @MNIMacro(
                identifier = "damage",
                normalParams = [
                    "int",
                    "int"
                ],
                returnType = "void"
            )
            fun damage(context: MacroContext, health: Ref<IntT>, amount: Value<IntT>): Block {
                hostCalls++
                // One REF write per branch; no local, helper or parameter-area copy.
                return context.quote().kotlin().block {
                    ifElse(
                        lt(health.load(), amount.expr()),
                        { assign(health, intLiteral(0)) },
                        { assign(health, sub(health.load(), amount.expr())) }
                    )
                }
            }
        }
    }

    /** Real native call with a host-side effect, used to observe VALUE argument order. */
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

    private class Fixture(
        namespaceName: String,
        source: String,
        bound: Map<String, MCInt> = emptyMap()
    ) {
        val namespace = Namespace(namespaceName)
        private val file = MCFPPFile(namespace)
        val caller: Function

        init {
            GlobalScope.localNamespaces[namespaceName] = namespace
            namespace.scope.journalFileFields.add(file.field)
            namespace.declareMniFixtures(DamageEntries::class.java)
            namespace.declareMniFixtures(EffectEntries::class.java)

            val bodySource = "{\n$source\n}"
            val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(bodySource, "$namespaceName.mcfpp"))))
            val body = parser.curlBlock()
            assertEquals(0, parser.numberOfSyntaxErrors, source)
            MCFPPFile.currFile = file
            caller = Function("caller", namespaceName, body).apply {
                declarationFile = file
                returnType = MCFPPPrivateType.Void
            }
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            bound.forEach { (name, value) -> caller.scope.vars[name] = value }
        }

        fun compile() {
            MCFPPFile.currFile = file
            Project.currNamespace = namespace.identifier
            Function.currFunction = caller
            val statements = requireNotNull(caller.ast).statement()
            val calls = statements.map { statement ->
                fun find(node: org.antlr.v4.runtime.tree.ParseTree): P.FunctionCallContext? {
                    if (node is P.FunctionCallContext && node.namespaceID().text == "damage") return node
                    return (0 until node.childCount).firstNotNullOfOrNull { find(node.getChild(it)) }
                }
                find(statement)
            }
            if (calls.isNotEmpty() && calls.all { it != null })
                calls.forEach { InternalReferenceCalls.expand(it!!, caller, setOf(0)) }
            else MCFPPImVisitor().compileFunctionBody(caller.ast)
            top.mcfpp.optimization.OptimizationPipeline.finish()
        }

        fun initialScore(value: MCInt, amount: Int): Pair<String, Int> = "${value.name} ${value.sbObject.name}" to amount

        fun execute(initialScores: Map<String, Int> = emptyMap(), setup: List<String> = emptyList()): ScoreCommandExecutor {
            val functions = (
                GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values +
                    GlobalScope.stdNamespaces.values
                ).flatMap { it.scope.functions.values.flatten() }
                .associate { function ->
                    function.namespaceID.toString() to
                        function.commands.filterNot { it is Comment }.map { it.analyze() }
                } +
                Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { it.value.lines() }
            // The root frame belongs to the test caller, never to the macro. One push and one pop
            // therefore prove the expansion added no additional stack frame. Invoke the caller as a
            // real function so a `return run function` inside it exits only the caller body and
            // control returns here for the pop, just like the generated entry-point function.
            val callerId = caller.namespaceID.toString()
            check(functions.containsKey(callerId)) { "Caller function is not registered: $callerId" }
            val commands = setup + listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
                "function $callerId" +
                "data remove storage mcfpp:system stack_frame[0]"
            return ScoreCommandExecutor(commands, functions, initialScores = initialScores)
        }

        /** Caller plus all lowering functions committed into this fixture's namespace. */
        fun producedCommands(): List<String> {
            val generated = namespace.scope.functions.values.flatten().filter { it !== caller }
            return (caller.commands + generated.flatMap { it.commands })
                .filterNot { it is Comment }
                .map { it.analyze() }
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

    @Test
    fun sameMacroWritesTheOriginalScoreAndClampsAtZero() = isolated {
        DamageEntries.hostCalls = 0

        val normalHealth = scoreBacked("damage.normal", "health")
        val normal = Fixture("damage.normal", "damage(health, 30);", mapOf("health" to normalHealth))
        normal.compile()
        assertEquals(0, Project.errorCount)
        assertEquals(1, DamageEntries.hostCalls, "The macro host must run exactly once")
        val normalMachine = normal.execute(mapOf(normal.initialScore(normalHealth, 100)))
        assertEquals(70, normalMachine.read(normalHealth))
        assertEquals(0, normalMachine.stackDepth)

        val lethalHealth = scoreBacked("damage.lethal", "health")
        val lethal = Fixture("damage.lethal", "damage(health, 30);", mapOf("health" to lethalHealth))
        lethal.compile()
        assertEquals(0, Project.errorCount)
        assertEquals(2, DamageEntries.hostCalls)
        val lethalMachine = lethal.execute(mapOf(lethal.initialScore(lethalHealth, 20)))
        assertEquals(0, lethalMachine.read(lethalHealth), "20 - 30 must clamp to zero")
        assertEquals(0, lethalMachine.stackDepth)
    }

    @Test
    fun aliasedAmountReadsTheOriginalLocationBeforeTheWrite() = isolated {
        val health = scoreBacked("damage.alias", "health")
        val fixture = Fixture(
            "damage.alias",
            "damage(health, 40); damage(health, health);",
            mapOf("health" to health)
        )
        fixture.compile()
        assertEquals(0, Project.errorCount)
        val machine = fixture.execute(mapOf(fixture.initialScore(health, 100)))
        // First call: 100 - 40 == 60. Second call reads 60 before subtracting it from itself.
        assertEquals(0, machine.read(health))
        assertEquals(0, machine.stackDepth)
    }

    @Test
    fun sameMacroAcceptsScoreAndNbtVariableLocations() = isolated {
        val scoreHealth = scoreBacked("damage.score", "health")
        val scoreFixture = Fixture("damage.score", "damage(health, 30);", mapOf("health" to scoreHealth))
        scoreFixture.compile()
        assertEquals(0, Project.errorCount)
        val scoreMachine = scoreFixture.execute(mapOf(scoreFixture.initialScore(scoreHealth, 100)))
        assertEquals(70, scoreMachine.read(scoreHealth))

        val variableHealth = nbtBacked("damage.variable", "health")
        val variableFixture = Fixture(
            "damage.variable",
            "damage(health, 30);",
            mapOf("health" to variableHealth)
        )
        variableFixture.compile()
        assertEquals(0, Project.errorCount)
        val variableMachine = variableFixture.execute(
            setup = listOf("data modify storage damage.variable:input health set value 100")
        )
        assertEquals(70, variableMachine.read(variableHealth))
        assertEquals(0, variableMachine.stackDepth)
    }

    @Test
    fun valueArgumentExpressionRunsOnceInSourceOrder() = isolated {
        EffectEntries.effects.clear()
        val health = scoreBacked("damage.once", "health")
        val fixture = Fixture(
            "damage.once",
            "damage(health, effect(5) + effect(7));",
            mapOf("health" to health)
        )
        fixture.compile()
        assertEquals(0, Project.errorCount)
        assertEquals(
            listOf(5, 7),
            EffectEntries.effects,
            "Both native operand calls must execute exactly once, left to right"
        )
        val machine = fixture.execute(mapOf(fixture.initialScore(health, 100)))
        assertEquals(88, machine.read(health))
    }

    @Test
    fun clampMatchesHandwrittenCodeWithoutParameterCopyOrMacroFrame() = isolated {
        val health = scoreBacked("damage.shape", "health")
        val macro = Fixture("damage.shape", "damage(health, 30);", mapOf("health" to health))
        macro.compile()
        assertEquals(0, Project.errorCount)

        val commands = macro.producedCommands()
        val rendered = commands.joinToString("\n")
        val scoreAddress = "${health.name} ${health.sbObject.name}"
        assertTrue(
            commands.any { it.startsWith("scoreboard players operation $scoreAddress = ") },
            "The REF write must target the caller score directly:\n$rendered"
        )
        assertFalse(
            commands.any { command ->
                command.startsWith("data modify") &&
                    Regex("(^|[^A-Za-z0-9_])${Regex.escape(health.name)}([^A-Za-z0-9_]|\\z)").containsMatchIn(command)
            },
            "No library parameter-area copy may mention health:\n$rendered"
        )
        assertFalse(
            commands.contains("data modify storage mcfpp:system stack_frame prepend value {}"),
            "The expansion must not push another stack frame:\n$rendered"
        )
        assertFalse(
            commands.any { Regex("function \\S*:damage(?:\\s|\\z)").containsMatchIn(it) },
            "A namespace macro must not become a runtime function call:\n$rendered"
        )
        assertTrue(
            Project.macroFunction.keys.none { it.contains("damage") },
            "The expansion must not create a command macro for damage"
        )
        val generated = macro.namespace.scope.functions.values.flatten()
            .filter { it !== macro.caller && it.identifier.startsWith("mni/") }
        assertTrue(generated.isNotEmpty(), "The macro result must be lowered into generated blocks")
        assertTrue(
            generated.all { it.normalParams.isEmpty() },
            "No helper may receive a copied REF/parameter area: ${generated.map { it.namespaceID }}"
        )

        val macroMachine = macro.execute(mapOf(macro.initialScore(health, 100)))
        assertEquals(70, macroMachine.read(health))
        assertEquals(0, macroMachine.stackDepth)

        for ((initial, expected) in listOf(100 to 70, 30 to 0, 0 to 0, 7 to 0)) {
            // The ordinary compiler materializes a source variable from its own initialization
            // commands. A pre-bound score only fits the macro path; the baseline therefore uses a
            // real `dynamic` declaration, exactly like the runnable main.mcfpp example.
            val hand = Fixture(
                "damage.handwritten$initial",
                "external health = $initial; " +
                    "if (health < 30) { health = 0; } else { health = health - 30; }"
            )
            hand.compile()
            assertEquals(0, Project.errorCount)
            val handHealth = assertIs<MCInt>(hand.caller.scope.getVar("health"))
            val handMachine = hand.execute()
            assertEquals(
                expected,
                handMachine.read(handHealth),
                "Handwritten baseline at initial=$initial"
            )
            assertEquals(0, handMachine.stackDepth)
            val macroValue = macro.execute(mapOf(macro.initialScore(health, initial))).read(health)
            assertEquals(
                handMachine.read(handHealth),
                macroValue,
                "Macro damage must match the handwritten clamp at initial=$initial"
            )
        }
    }
}

/** A writable `int` whose storage layout is a real scoreboard address. */
private fun scoreBacked(owner: String, name: String): MCInt {
    val value = MCInt(name)
    val place = Place(top.mcfpp.analysis.SymbolId.fresh())
    val path = NBTPath(StorageSource("$owner:input")).memberIndex(name)
    return boundInt(value, place, path, StorageLayout.Scoreboard(value.name, value.sbObject.name))
}

/** A writable `int` whose storage layout is an NBT variable location. */
private fun nbtBacked(owner: String, name: String): MCInt {
    val value = MCInt(name)
    val place = Place(top.mcfpp.analysis.SymbolId.fresh())
    val path = NBTPath(StorageSource("$owner:input")).memberIndex(name)
    return boundInt(value, place, path, StoredData(place, path).layout)
}

private fun boundInt(value: MCInt, place: Place, path: NBTPath, layout: StorageLayout): MCInt {
    val data = StoredData(place, path, layout = layout)
    value.symbol = Symbol(place.root, value.name, MCFPPBaseType.Int.typeId, true, forceRuntime = true)
    value.storageBinding = StorageBinding(data, place, data.path)
    data.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
    data.declarations[place] = value.symbol!!
    data.facts.write(
        place,
        ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown)
    )
    return value
}
