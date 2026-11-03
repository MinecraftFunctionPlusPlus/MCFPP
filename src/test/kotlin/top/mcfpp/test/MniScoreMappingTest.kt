package top.mcfpp.test

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.command.Comment
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.BoolT
import top.mcfpp.mni.api.ConstantValue
import top.mcfpp.mni.api.DeclarationContext
import top.mcfpp.mni.api.DeclarationEdit
import top.mcfpp.mni.api.DeclarationEdits
import top.mcfpp.mni.api.FieldRef
import top.mcfpp.mni.api.FunctionSignature
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.ScoreHolder
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.api.TemplateProcessor
import top.mcfpp.mni.api.TemplateView
import top.mcfpp.mni.api.TypeRef
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.mni.expand.TemplateProcessorRunner
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.FieldAnnotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.util.Collections
import java.util.IdentityHashMap
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.apache.logging.log4j.core.Logger as CoreLogger
import org.apache.logging.log4j.core.config.Property as Log4jProperty

/**
 * Real source -> annotation processor -> generated `load`/`save` -> caller commands.
 *
 * The test mirrors the public example without importing it: the same public declaration/code API
 * builds the members, the production processor barrier publishes them, and the source body calls
 * them through ordinary member lookup. All command assertions run [ScoreCommandExecutor].
 */
private const val NAMESPACE = "score.mapping"
private const val HOLDER = "#scores"
private const val HEALTH = "health.hp"
private const val ALIVE = "alive-state"
private const val GENERATED_MEMBERS = "mni:GeneratedMembers"

class MniScoreMappingTest {
    /** Legacy field annotation with a real positional string argument. */
    class ScoreFieldAnnotation(private val objective: String) : FieldAnnotation("Score", NAMESPACE) {
        internal fun portableObjective() = objective
        override fun forField(field: Var<*>) {
            // The processor owns the mapping; the legacy action is deliberately observation-free.
        }
    }

    private class FieldMapping(val field: FieldRef<*>, val integer: Boolean, val objective: String)

    private class Observed {
        companion object {
            var holder: String? = null
            var objectives: List<String> = emptyList()
            fun reset() {
                holder = null
                objectives = emptyList()
            }
        }
    }

    /** Real compiler diagnostics, so a zero-error assertion failure shows the actual cause. */
    private class Recorder :
        AbstractAppender(
            "score-mapping",
            null,
            PatternLayout.createDefaultLayout(),
            false,
            Log4jProperty.EMPTY_ARRAY
        ) {
        val events = arrayListOf<LogEvent>()
        override fun append(event: LogEvent) {
            events += event.toImmutable()
        }
        fun diagnostics(): String = events
            .filter { it.level == Level.ERROR || it.level == Level.WARN }
            .joinToString("\n") { "${it.level}: ${it.message.formattedMessage}" }
    }

    private var activeRecorder: Recorder? = null
    private fun diagnostics(): String = activeRecorder?.diagnostics().orEmpty()

    /** Mirror of the Kotlin provider. The test never constructs declarations by hand. */
    class ScoreMappingProcessor : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            val configuration = template.annotations().firstOrNull { it.name().endsWith(":ScoreMapping") }
                ?: fail(context, template.source(), "ScoreMapping requires a @ScoreMapping annotation")
            val holder = (configuration.arguments()["0"] as? ConstantValue.Text)?.value
                ?: fail(
                    context,
                    configuration.source(),
                    "ScoreMapping argument 0 must be the fixed score holder string"
                )
            try {
                ScoreHolder.named(holder)
            } catch (failure: IllegalArgumentException) {
                fail(context, configuration.source(), "Invalid score holder: ${failure.message}")
            }
            val intType = context.types().intType()
            val boolType = context.types().boolType()
            val mappings = arrayListOf<FieldMapping>()
            val targets = hashSetOf<String>()
            for (field in template.fieldDeclarations()) {
                val annotations = field.annotations().filter { it.name().endsWith(":Score") }
                if (annotations.isEmpty()) continue
                if (annotations.size != 1)
                    fail(context, field.source(), "Field '${field.field().name()}' has more than one @Score mapping")
                val annotation = annotations.single()
                val objective = (annotation.arguments()["0"] as? ConstantValue.Text)?.value
                    ?: fail(
                        context,
                        annotation.source(),
                        "Field '${field.field().name()}' requires a string objective"
                    )
                if (!objective.matches(Regex("[A-Za-z0-9_.+-]{1,16}")))
                    fail(context, annotation.source(), "Invalid score objective '$objective'")
                if (!field.field().writable())
                    fail(
                        context,
                        field.source(),
                        "Field '${field.field().name()}' is read-only; " +
                            "score load requires a writable field"
                    )
                val type = field.field().type()
                val integer = type.sameType(intType)
                val bool = type.sameType(boolType)
                if (!integer && !bool)
                    fail(
                        context,
                        field.source(),
                        "Field '${field.field().name()}' has unsupported type " +
                            "'${type.name()}'; score mapping supports only int and bool"
                    )
                if (!targets.add("$holder $objective"))
                    fail(context, annotation.source(), "Duplicate score target mapping '$holder $objective'")
                mappings += FieldMapping(field.field(), integer, objective)
            }
            if (mappings.isEmpty())
                fail(context, template.source(), "ScoreMapping found no @Score fields to map")
            Observed.holder = holder
            Observed.objectives = mappings.map { it.objective }

            @Suppress("UNCHECKED_CAST")
            val owner = template.type() as TypeRef<Any>
            val noArgsVoid = FunctionSignature.of(emptyList(), context.types().voidType())
            val declarations = context.declarations()
            val load = declarations.method(owner, "load", noArgsVoid) { body ->
                val builder = body.builder()
                val minecraft = builder.minecraft()
                for (mapping in mappings) {
                    val score = minecraft.score(ScoreHolder.named(holder), mapping.objective)
                    if (mapping.integer) {
                        @Suppress("UNCHECKED_CAST")
                        val field = mapping.field as FieldRef<IntT>
                        builder.assign(builder.fieldRef(body.self(), field), score.load())
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val field = mapping.field as FieldRef<BoolT>
                        builder.assign(
                            builder.fieldRef(body.self(), field),
                            builder.ne(score.load(), builder.intLiteral(0))
                        )
                    }
                }
            }
            val save = declarations.method(owner, "save", noArgsVoid) { body ->
                val builder = body.builder()
                val minecraft = builder.minecraft()
                for (mapping in mappings) {
                    val score = minecraft.score(ScoreHolder.named(holder), mapping.objective)
                    if (mapping.integer) {
                        @Suppress("UNCHECKED_CAST")
                        val field = mapping.field as FieldRef<IntT>
                        builder.assign(score, builder.fieldRef(body.self(), field).load())
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val field = mapping.field as FieldRef<BoolT>
                        val value = builder.fieldRef(body.self(), field)
                        builder.ifElse(
                            value.load(),
                            Consumer { branch -> branch.assign(score, branch.intLiteral(1)) },
                            Consumer { branch -> branch.assign(score, branch.intLiteral(0)) }
                        )
                    }
                }
            }
            val marker = declarations.metadata(owner, GENERATED_MEMBERS, emptyMap())
            return declarations.edits(listOf<DeclarationEdit>(load, save, marker))
        }

        private fun fail(context: DeclarationContext, site: SourceSpan, message: String): Nothing {
            context.diagnostics().error(site, message)
            throw IllegalStateException("Declaration diagnostics did not abort score mapping")
        }
    }

    /** Creates a valid member, then fails, to prove the publication transaction is atomic. */
    class BrokenProcessor : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            @Suppress("UNCHECKED_CAST")
            val owner = template.type() as TypeRef<Any>
            context.declarations().method(
                owner,
                "ghost",
                FunctionSignature.of(emptyList(), context.types().voidType())
            ) { }
            context.diagnostics().error(template.source(), "intentional score-mapping processor failure")
            throw IllegalStateException("Declaration diagnostics did not abort")
        }
    }

    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val config = Project.config
        val callerFile = MCFPPFile.currFile
        val callerFunction = Function.currFunction
        val callerTemplate = DataTemplate.currTemplate
        val ignoreStdLib = CompileSettings.ignoreStdLib
        try {
            action()
        } finally {
            CompileSettings.ignoreStdLib = ignoreStdLib
            namespaces.restore()
            project.restore()
            Project.config = config
            MCFPPFile.currFile = callerFile
            Function.currFunction = callerFunction
            DataTemplate.currTemplate = callerTemplate
        }
    }

    private fun compile(
        sources: Map<String, String>,
        configure: (Namespace) -> Unit,
        verify: (Namespace) -> Unit
    ) = isolated {
        val directory = Files.createTempDirectory("mcfpp-score-mapping-")
        val context = LogManager.getContext(false) as LoggerContext
        val configuration = context.configuration
        val logger = LogManager.getLogger("mcfpp") as CoreLogger
        val oldLevel = logger.level
        val oldAdditive = logger.isAdditive
        val oldLogger = LogProcessor.logger
        val oldLogLevel = LogProcessor.level
        val recorder = Recorder().apply { start() }
        logger.level = Level.ALL
        logger.isAdditive = false
        logger.addAppender(recorder)
        LogProcessor.logger = logger
        LogProcessor.level = LogLevel.DEBUG
        activeRecorder = recorder
        try {
            sources.forEach { (name, text) -> Files.writeString(directory.resolve(name), text) }
            CompileSettings.ignoreStdLib = true
            Project.config = ProjectConfig(version = "26.3").apply {
                root = directory
                sourcePath = directory
                rootNamespace = NAMESPACE
                targetPath = null
                noDatapack = true
            }
            Observed.reset()
            Project.init()
            Project.readProject()
            Project.indexType()
            Project.resolveField()
            val namespace = GlobalScope.localNamespaces.getValue(NAMESPACE)
            configure(namespace)
            Project.runAnnotation()
            Project.compile()
            try {
                verify(namespace)
            } catch (failure: Throwable) {
                System.err.println("Captured compiler diagnostics:\n" + diagnostics())
                throw failure
            }
        } finally {
            if (context.configuration !== configuration) context.start(configuration)
            logger.removeAppender(recorder)
            recorder.stop()
            logger.level = oldLevel
            logger.isAdditive = oldAdditive
            LogProcessor.logger = oldLogger
            LogProcessor.level = oldLogLevel
            activeRecorder = null
            directory.toFile().deleteRecursively()
        }
    }

    private fun register(namespace: Namespace) {
        assertTrue(namespace.registerTemplateProcessor("$NAMESPACE:ScoreMapping", ScoreMappingProcessor::class.java))
        assertTrue(namespace.scope.addAnnotation("Score", ScoreFieldAnnotation::class.java))
    }

    private fun template(namespace: Namespace): DataTemplate = namespace.scope.template.values.single { it.identifier == "Player" }

    private fun functionMap(): Map<String, List<String>> {
        val functions = linkedMapOf<String, List<String>>()
        val visited = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        fun collect(function: Function) {
            if (!visited.add(function)) return
            functions[function.namespaceID.toString()] = function.commands
                .filterNot { it is Comment }.map { it.analyze() }
            function.compiledFunctions.values.forEach(::collect)
        }
        (
            GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values +
                GlobalScope.stdNamespaces.values
            ).forEach { namespace ->
            namespace.scope.functions.values.flatten().forEach(::collect)
            namespace.scope.template.values.forEach { template ->
                template.constructors.forEach(::collect)
                template.scope.forEachFunction(::collect)
            }
        }
        Project.macroFunction.forEach { (key, value) -> functions["mcfpp:dynamic/$key"] = value.lines() }
        return functions
    }

    private fun execute(main: Function, setup: List<String>, initialScores: Map<String, Int>): ScoreCommandExecutor {
        val commands = setup + listOf(
            "data modify storage mcfpp:system stack_frame prepend value {}",
            "function ${main.namespaceID}",
            "data remove storage mcfpp:system stack_frame[0]"
        )
        return ScoreCommandExecutor(commands, functionMap(), initialScores = initialScores)
    }

    private fun main(namespace: Namespace): Function = namespace.scope.functions.getValue("main").single()

    internal fun portableScoreFacts(roundTrip: (ScoreFieldAnnotation) -> ScoreFieldAnnotation) = compile(
        linkedMapOf("portable.mcfpp" to """
            data Player {@Score("health.hp") var health:int=0;}
            func main(){}
        """.trimIndent()),
        configure = { namespace -> assertTrue(namespace.scope.addAnnotation("Score", ScoreFieldAnnotation::class.java)) }
    ) { namespace ->
        val player = template(namespace)
        val field = requireNotNull(player.scope.getVar("health"))
        val original = field.annotations.single() as ScoreFieldAnnotation
        val restored = roundTrip(original)
        assertEquals(HEALTH, restored.portableObjective())
        assertEquals(original.compilerArguments(), restored.compilerArguments())
        field.annotations.clear(); field.annotations.add(restored)
        val caller = Function("portable_catalog", namespace.identifier, null).apply {
            owner = player; declarationFile = player.declarationFile
        }
        val environment = top.mcfpp.mni.expand.CompilerSourceIndex(caller, declarationTarget = player)
            .prepareDeclarationEnvironment(assertIs<top.mcfpp.antlr.mcfppParser.CompilationUnitContext>(Project.trees.getValue(requireNotNull(player.declarationFile))))
        val fact = requireNotNull(environment.declarationView).fieldDeclarations().single { it.field().name() == "health" }
            .annotations().single()
        assertEquals("$NAMESPACE:Score", fact.name())
        assertEquals(HEALTH, (fact.arguments().getValue("0") as ConstantValue.Text).value)
        environment.requireCurrent()
        assertEquals(0, Project.errorCount, diagnostics())
    }

    @Test
    fun sourceAnnotationArgumentsDriveCallableIntAndBoolRoundTrip() = compile(
        linkedMapOf(
            "player.mcfpp" to """
                @ScoreMapping("#scores")
                data Player {
                    @Score("health.hp")
                    var health: int = 0
                    @Score("alive-state")
                    var alive: bool = false
                }

                func main() {
                    var player = Player()
                    player.health = 0
                    player.alive = false
                    player.load()
                    external loadedHealth: int = player.health
                    external loadedAlive: int = 0
                    if (player.alive) {
                        loadedAlive = 1
                    }
                    player.health = 9
                    player.alive = true
                    player.save()
                }

                func saveFalse() {
                    var player = Player()
                    player.health = 9
                    player.alive = false
                    player.save()
                }
            """.trimIndent()
        ),
        configure = ::register
    ) { namespace ->
        assertEquals(0, Project.errorCount, diagnostics())
        assertEquals("#scores", Observed.holder, "The processor must read its original annotation argument")
        assertEquals(
            setOf(HEALTH, ALIVE),
            Observed.objectives.toSet(),
            "Field annotations must project their real positional string arguments"
        )
        assertEquals(0, TemplateProcessorRunner.run(), "The declaration barrier must not execute twice")
        val player = template(namespace)
        assertTrue(player.scope.getFunctionCandidates("load").isNotEmpty())
        assertTrue(player.scope.getFunctionCandidates("save").isNotEmpty())
        assertEquals(
            listOf(GENERATED_MEMBERS),
            player.declarationMetadata().map { it.name() },
            "The external marker must be published exactly once and must not recurse"
        )

        // A library/inherited copy must keep the closed annotation argument after serialization.
        val health = requireNotNull(player.scope.getVar("health"))
        val copied = Property.copyAnnotations(health.annotations)
        assertEquals(1, copied.size)
        assertEquals(
            HEALTH,
            (copied.single().compilerArguments().single() as CompilerValue.Typed)
                .payload.let { (it as CompilerValue.Text).value }
        )

        val main = main(namespace)
        val loadedHealth = assertIs<MCInt>(main.scope.getVar("loadedHealth"))
        val loadedAlive = assertIs<MCInt>(main.scope.getVar("loadedAlive"))
        val machine = execute(
            main,
            setup = listOf(
                "scoreboard players set $HOLDER $HEALTH 12",
                "scoreboard players set $HOLDER $ALIVE 3"
            ),
            initialScores = mapOf("$HOLDER $HEALTH" to 12, "$HOLDER $ALIVE" to 3)
        )
        assertEquals(12, machine.read(loadedHealth), "load must copy the exact int score")
        assertEquals(1, machine.read(loadedAlive), "any nonzero bool score must read true")
        assertEquals(9, machine.values["$HOLDER $HEALTH"], "save must write the exact int")
        assertEquals(1, machine.values["$HOLDER $ALIVE"], "true must save as strictly 1")
        assertEquals(0, machine.stackDepth)
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())

        val saveFalse = namespace.scope.functions.getValue("saveFalse").single()
        val falseMachine = ScoreCommandExecutor(
            listOf(
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "function ${saveFalse.namespaceID}",
                "data remove storage mcfpp:system stack_frame[0]"
            ),
            functionMap()
        )
        assertEquals(9, falseMachine.values["$HOLDER $HEALTH"], "save must write the exact int")
        assertEquals(0, falseMachine.values["$HOLDER $ALIVE"], "false must save as exactly 0")
        assertEquals(0, falseMachine.stackDepth)
        assertTrue(falseMachine.failedScoreOperations.isEmpty(), falseMachine.failedScoreOperations.toString())

        // One executor, two real calls. The first call leaves nonzero scores and temps behind; both
        // actual holder keys are then removed before the second call reuses the same generated
        // function and temp names. Missing keys must read as zero instead of stale first-call data.
        val stale = ScoreCommandExecutor(
            listOf(
                "scoreboard players set $HOLDER $HEALTH 12",
                "scoreboard players set $HOLDER $ALIVE 3",
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "function ${main.namespaceID}",
                "scoreboard players reset $HOLDER $HEALTH",
                "scoreboard players reset $HOLDER $ALIVE",
                "function ${main.namespaceID}",
                "data remove storage mcfpp:system stack_frame[0]"
            ),
            functionMap(),
            initialScores = mapOf("$HOLDER $HEALTH" to 12, "$HOLDER $ALIVE" to 3)
        )
        assertEquals(
            0,
            stale.read(loadedHealth),
            "a genuinely absent int score must load as 0, not a stale nonzero temp"
        )
        assertEquals(
            0,
            stale.read(loadedAlive),
            "a genuinely absent bool score must load as false, not a stale true temp"
        )
        assertEquals(9, stale.values["$HOLDER $HEALTH"], "the second save still writes the exact int")
        assertEquals(1, stale.values["$HOLDER $ALIVE"], "the second save still writes true as 1")
        assertEquals(0, stale.stackDepth)
        assertTrue(stale.failedScoreOperations.isEmpty(), stale.failedScoreOperations.toString())

        // Additional structural evidence only: the generated read zeroes its temp before the
        // conditional copy. The runtime assertions above prove the absent-key behavior. The
        // generated load is a small wrapper, so inspect every function call reachable from it.
        val loadFunction = player.scope.getFunctionCandidates("load").single()
        val publishedFunctions = functionMap()
        val visitedFunctions = mutableSetOf<String>()
        val reachableCommands = mutableListOf<String>()
        fun collectReachableFunction(namespaceID: String) {
            if (!visitedFunctions.add(namespaceID)) return
            val body = publishedFunctions[namespaceID] ?: return
            reachableCommands += body
            body.forEach { command ->
                Regex("(?:^|\\s)function (\\S+)\\s*$").find(command)?.let {
                    collectReachableFunction(it.groupValues[1])
                }
            }
        }
        collectReachableFunction(loadFunction.namespaceID.toString())
        val zeroed = reachableCommands.count { Regex("scoreboard players set \\S+ \\S+ 0").matches(it) }
        assertTrue(
            zeroed >= 2,
            "Every generated score read reachable from load must zero its temporary " +
                "before the conditional get:\n" + reachableCommands.joinToString("\n")
        )
        assertEquals(0, Project.errorCount, diagnostics())
    }

    @Test
    fun missingScoreConditionsAreFalseAndResetRemovesTheActualKey() {
        val missing = "missing"
        val machine = ScoreCommandExecutor(
            listOf(
                "scoreboard players set $HOLDER $HEALTH 12",
                "execute if score $HOLDER $missing = $HOLDER $missing run say compare-if",
                "execute unless score $HOLDER $missing = $HOLDER $missing run say compare-unless",
                "execute if score $HOLDER $HEALTH = $HOLDER $missing run say missing-right-if",
                "execute if score $HOLDER $missing matches 2 run say matches-if",
                "execute unless score $HOLDER $missing matches 2 run say matches-unless",
                "execute store success score $HOLDER chained if score $HOLDER $HEALTH = $HOLDER $HEALTH " +
                    "if score $HOLDER $missing = $HOLDER $missing",
                "execute store success score $HOLDER inverted unless score $HOLDER $missing = $HOLDER $missing",
                "scoreboard players reset $HOLDER $HEALTH",
                "execute if score $HOLDER $HEALTH = $HOLDER $HEALTH run say stale-after-reset"
            ),
            initialScores = mapOf("$HOLDER $HEALTH" to 12)
        )
        assertEquals(listOf("compare-unless", "matches-unless"), machine.messages)
        assertEquals(
            0,
            machine.values["$HOLDER chained"],
            "a missing operand in a chained condition must behave as false"
        )
        assertEquals(
            1,
            machine.values["$HOLDER inverted"],
            "unless must invert the false result for a genuinely missing score"
        )
        assertFalse(machine.values.containsKey("$HOLDER $HEALTH"), "reset must remove the actual key")
        assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())

        assertFailsWith<NoSuchElementException> {
            ScoreCommandExecutor(listOf("scoreboard players operation $HOLDER result = $HOLDER $missing"))
        }
        assertFailsWith<IllegalStateException> {
            ScoreCommandExecutor(listOf("unknown score command"))
        }
    }

    @Test
    fun unsupportedFieldTypeIsDiagnosedBeforeAnyCommandOrMemberIsPublished() = compile(
        linkedMapOf(
            "invalid_type.mcfpp" to """
                @ScoreMapping("#scores")
                data Player {
                    @Score("ratio")
                    var ratio: float = 0
                }
                func main() {}
            """.trimIndent()
        ),
        configure = ::register
    ) { namespace ->
        assertTrue(Project.errorCount > 0)
        val player = template(namespace)
        assertTrue(player.scope.getFunctionCandidates("load").isEmpty())
        assertTrue(player.scope.getFunctionCandidates("save").isEmpty())
        assertTrue(player.declarationMetadata().isEmpty())
    }

    @Test
    fun duplicateTargetIsDiagnosedBeforeAnyGeneratedMember() = compile(
        linkedMapOf(
            "duplicate.mcfpp" to """
                @ScoreMapping("#scores")
                data Player {
                    @Score("health.hp")
                    var health: int = 0
                    @Score("health.hp")
                    var shield: int = 0
                }
                func main() {}
            """.trimIndent()
        ),
        configure = ::register
    ) { namespace ->
        assertTrue(Project.errorCount > 0)
        val player = template(namespace)
        assertTrue(player.scope.getFunctionCandidates("load").isEmpty())
        assertTrue(player.scope.getFunctionCandidates("save").isEmpty())
        assertTrue(player.declarationMetadata().isEmpty())
    }

    @Test
    fun failedLaterProcessorKeepsTheSuccessfulScoreMembersAndDiscardsItsGhost() = compile(
        linkedMapOf(
            "atomic.mcfpp" to """
                @ScoreMapping("#scores")
                @Broken
                data Player {
                    @Score("health.hp")
                    var health: int = 0
                }
                func main() {
                    var player = Player()
                    player.load()
                    player.save()
                }
            """.trimIndent()
        ),
        configure = { namespace ->
            register(namespace)
            assertTrue(namespace.registerTemplateProcessor("$NAMESPACE:Broken", BrokenProcessor::class.java))
        }
    ) { namespace ->
        assertTrue(Project.errorCount > 0, "The later processor must report its failure")
        val player = template(namespace)
        assertTrue(
            player.scope.getFunctionCandidates("load").isNotEmpty(),
            "The earlier atomic processor remains published\n" + diagnostics()
        )
        assertTrue(player.scope.getFunctionCandidates("save").isNotEmpty(), diagnostics())
        assertTrue(
            player.scope.getFunctionCandidates("ghost").isEmpty(),
            "A failed processor publishes no partial member"
        )
        assertEquals(listOf(GENERATED_MEMBERS), player.declarationMetadata().map { it.name() })
        assertFalse(player.scope.functions.values.flatten().any { it.identifier == "ghost" })
    }
}
