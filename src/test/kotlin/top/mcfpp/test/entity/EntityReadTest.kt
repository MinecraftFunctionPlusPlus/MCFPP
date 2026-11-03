package top.mcfpp.test.entity

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.LibraryPublicationConfig
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.context.*
import top.mcfpp.core.lang.entity.EntityDataAddress
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.io.DatapackCreator
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.ShortTag
import top.mcfpp.test.util.*
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Source -> exported production commands -> the existing context interpreter. */
class EntityReadTest {
    @Test fun successfulNbtAndScoresIncludeZeroAndYieldOrdinarySnapshots() = fixture("""
        func consume(value: short) {
            if (toInt(value) == 9) {
                /say scalar-snapshot
            }
        }
        func main() {
            var target = entityByName("Alice")
            var airFound = target.dat.air.tryRead { air ->
                var copy = air
                target.dat.air = 4s
                consume(copy)
            }
            var nbtZero = target.dat.zero.tryRead { value ->
                if (value == 0) {
                    /say nbt-zero
                }
            }
            var points = target.score["points"].tryRead { value ->
                if (value == 7) {
                    /say score-seven
                }
            }
            var zero = target.score["zero"].tryRead { value ->
                if (value == 0) {
                    /say score-zero
                }
            }
            target.score["points"] = 11
            if (airFound && nbtZero && points && zero) {
                /say all-found
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(listOf("scalar-snapshot", "nbt-zero", "score-seven", "score-zero", "all-found"), machine.messages)
        assertEquals(4, (machine.readEntity("Alice", "Air").value as Number).toInt())
        assertEquals(11, machine.score("Alice", "points"))
        assertEquals(2, machine.executed.count { it.startsWith("data modify storage ") && it.contains("set from entity Alice ") })
        assertEquals(2, machine.executed.count { it.startsWith("scoreboard players get Alice ") })
        cleanFrame(machine)
    }

    @Test fun missingEntityFieldAndScoreSkipTheCallbackAndClearOneCallsite() = fixture("""
        func observe() {
            var found = entityByName("Alice").dat.air.tryRead { value ->
                /say observed
            }
            if (!found) {
                /say field-missing
            }
        }
        func main() {
            observe()
            /data remove entity Alice Air
            observe()
            var field = entityByName("Alice").dat.missing.tryRead { value ->
                /say forbidden-field
            }
            var score = entityByName("Alice").score["missing"].tryRead { value ->
                /say forbidden-score
            }
            var absentNbt = entityByName("Missing").dat.air.tryRead { value ->
                /say forbidden-entity
            }
            var absentScore = entityByName("Missing").score["points"].tryRead { value ->
                /say forbidden-entity-score
            }
            if (!field && !score && !absentNbt && !absentScore) {
                /say all-missing
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(listOf("observed", "field-missing", "all-missing"), machine.messages)
        assertEquals(2, machine.executed.count { it.startsWith("data modify storage ") && it.endsWith("set from entity Alice Air") })
        assertEquals(0, machine.executed.count { it == "scoreboard players get Missing points" })
        cleanFrame(machine)
    }

    @Test fun callbackInheritsAmbientSpaceExecutorAndLocalReturnWithScopedAddresses() = fixture("""
        func main() {
            entityByName("Alice").withPosCtx { origin ->
                var found = entityByName("Bob").dat.air.tryRead { value ->
                    origin.executor.addTag("borrowed-in-read")
                    /execute if block ~ ~ ~ minecraft:stone run say ambient
                    return
                    /say forbidden-return
                }
                if (found) {
                    /say continued
                }
                entityByName("Bob").withEntityCtx { child ->
                    child.executor.dat.air = 8s
                    child.executor.score["points"] = 12
                    origin.executor.score["points"].tryRead { value ->
                        /execute if block ~ ~ ~ minecraft:stone run say ancestor-score
                    }
                    child.executor.dat.air.tryRead { value ->
                        if (toInt(value) == 8) {
                            /say bound-nbt
                        }
                    }
                    child.executor.score["points"].tryRead { value ->
                        if (value == 12) {
                            /say bound-score
                        }
                    }
                }
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(listOf("ambient", "continued", "ancestor-score", "bound-nbt", "bound-score"), machine.messages)
        assertEquals(listOf("carrier", "Bob"), machine.blockTests.map { it.executor })
        assertEquals(List(2) { ContextPosition(10.0, 70.0, 20.0) }, machine.blockTests.map { it.position })
        assertEquals(List(2) { ContextRotation(90.0, 10.0) }, machine.blockTests.map { it.rotation })
        assertEquals(List(2) { "minecraft:the_nether" }, machine.blockTests.map { it.dimension })
        assertTrue("borrowed-in-read" in machine.entities.single { it.name == "carrier" }.tags)
        assertEquals(8, (machine.readEntity("Bob", "Air").value as Number).toInt())
        assertEquals(12, machine.score("Bob", "points"))
        cleanFrame(machine)
    }

    @Test fun bareAddressesAreValuesWhileUnknownFieldsAndNonBooleanConditionsAreRejected() = fixture("""
        func main() {
            var value = entityByName("Alice").dat.air
            var score = entityByName("Alice").score["points"]
            var unknown = @e.dat.playerOnly.tryRead { value ->
                /say forbidden-unknown
            }
            if (entityByName("Alice").dat.flag) {
                /say forbidden
            }
        }
    """.trimIndent(), errors = true) { main, _ ->
        assertEquals(2, Project.errorCount)
        assertIs<top.mcfpp.type.MCFPPNBTRefType>(assertNotNull(main.scope.getVar("value")).type)
        assertEquals(top.mcfpp.type.MCFPPScoreRefType, assertNotNull(main.scope.getVar("score")).type)
    }

    @Test fun knownPlayerFieldsInferScalarCallbacksForOrdinaryAndBoundTargets() = fixture("""
        func consumePlayer(value: int) {
            if (value == 14) {
                /say player-snapshot
            }
        }
        func main() {
            @p.dat.playerOnly.tryRead { value -> consumePlayer(value); }
            @p.withEntityCtx { context ->
                context.executor.dat.playerOnly.tryRead { value -> consumePlayer(value); }
                entityByName("carrier").withPosCtx { spatial ->
                    spatial.executor.dat.playerOnly.tryRead { value -> consumePlayer(value); }
                }
            }
        }
    """.trimIndent()) { main, functions ->
        assertEquals(List(3) { "player-snapshot" }, execute(main, functions).messages)
    }

    @Test fun closedExecutorAddressesCannotBeReadOrCapturedAndMultiWritesRemainAvailable() = fixture("""
        func main() { @a.dat.air = 3s; }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        for (name in listOf("Alice", "Bob")) assertEquals(3, (machine.readEntity(name, "Air").value as Number).toInt())
        main.runInFunction {
            ContextSession().use { session ->
                val root = session.root()
                val scope = session.openScope()
                val context = root.withEntityContext(scope)
                lateinit var address: EntityDataAddress
                ContextBorrowing.withContext(context) {
                    val executor = BoundExecutorVar("executor", context.executor, top.mcfpp.backend.EntityContextCommandOperations.encoder())
                    val data = executor.getMemberVar("dat", Member.AccessModifier.PUBLIC).first!!
                    address = data.getMemberVar("air", Member.AccessModifier.PUBLIC).first as EntityDataAddress
                    address.requireUsable()
                    val live = StorageAccess.capture(address)
                    assertFalse(live.isError)
                    assertNull(ContextBorrowing.diagnostic(live))
                }
                assertFailsWith<ContextAccessException> { address.requireUsable() }
                assertTrue(StorageAccess.capture(address).isError)
            }
        }
    }

    private fun execute(main: Function, functions: Map<String, List<String>>): ContextCommandExecutor {
        val entities = listOf(
            ContextEntity("carrier", position = ContextPosition(0.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0)),
            ContextEntity("Alice", "minecraft:player", position = ContextPosition(10.0, 70.0, 20.0), rotation = ContextRotation(90.0, 10.0),
                dimension = "minecraft:the_nether", nbt = mapOf("Air" to ShortTag(9), "Zero" to IntTag(0), "PlayerOnly" to IntTag(14))),
            ContextEntity("Bob", "minecraft:player", position = ContextPosition(15.0, 70.0, 20.0), rotation = ContextRotation(0.0, 0.0),
                nbt = mapOf("Air" to ShortTag(5), "PlayerOnly" to IntTag(14))))
        return ContextCommandExecutor(listOf(Commands.stackIn().analyze(), "function ${main.namespaceID}"), functions, entities,
            entities.first(), ContextPosition(3.0, 64.0, -4.0), ContextRotation(15.0, -5.0),
            initialScores = mapOf("Alice points" to 7, "Alice zero" to 0, "carrier points" to 6, "Missing points" to 99),
            world = { _, _, _ -> ContextBlock("minecraft:stone") }, macros = Project.macroFunction.toMap())
    }

    private fun cleanFrame(machine: ContextCommandExecutor) {
        assertEquals(1, machine.executedFunctionLines.count { it == "data modify storage mcfpp:system stack_frame prepend value {}" } -
            machine.executedFunctionLines.count { it == "data remove storage mcfpp:system stack_frame[0]" })
    }

    private fun fixture(program: String, errors: Boolean = false, action: (Function, Map<String, List<String>>) -> Unit) {
        val config = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        val temporary = Files.createTempDirectory("mcfpp-entity-read-")
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf() }
            CompileSettings.ignoreStdLib = true
            val resultLibrary = temporary.resolve("result")
            Project.config.library = LibraryPublicationConfig("fixture:entity-read-result", "1.0.0")
            val resultSource = Files.readString(Path.of("src/main/mcfpp/minecraft/std/CommandResult.mcfpp"))
            MCFPPStringTest.readFromString("namespace mcfpp.minecraft.std\n$resultSource", targetPath = resultLibrary.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val resourceLibrary = temporary.resolve("resource")
            Project.config.library = LibraryPublicationConfig("fixture:entity-read-resource", "1.0.0")
            val resourceSource = listOf("ResourceID", "Advancement", "EntityType", "LootTablePredicate")
                .joinToString("\n") { Files.readString(Path.of("src/main/mcfpp/minecraft/resource/$it.mcfpp")) }
            MCFPPStringTest.readFromString("namespace mcfpp.minecraft.resource\n$resourceSource", targetPath = resourceLibrary.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            Project.config.includes = arrayListOf(resultLibrary.toString(), resourceLibrary.toString())
            val library = temporary.resolve("entity")
            Project.config.library = LibraryPublicationConfig("fixture:entity-read-entity", "1.0.0")
            MCFPPStringTest.readFromString("""
                namespace mcfpp.minecraft.entity
                import mcfpp.minecraft.std:*
                import mcfpp.minecraft.resource:*
                @From("top.mcfpp.mni.minecraft.EntityTagData")
                data EntityData {
                    @Name("Air") var air: short
                    @Name("Zero") var zero: int
                    var missing: int
                    var flag: bool
                }
                data PlayerData { @Name("PlayerOnly") var playerOnly: int; }
            """.trimIndent(), targetPath = library.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            Project.config.includes = arrayListOf(resultLibrary.toString(), resourceLibrary.toString(), library.toString())
            val factories = Files.readString(Path.of("src/main/mcfpp/lang/entity.mcfpp"))
            val conversions = Files.readString(Path.of("src/main/mcfpp/lang/conversions.mcfpp"))
            val diagnostics = LogProcessor.beginDiagnosticCapture().use { capture ->
                MCFPPStringTest.readFromString("import mcfpp.minecraft.entity:*\nimport mcfpp.minecraft.resource:*\n$factories\n$conversions\n$program", version = "26.3")
                capture.seal().diagnostics.map { it.message }
            }
            if (errors) assertTrue(Project.errorCount > 0) else assertEquals(0, Project.errorCount)
            if (errors) {
                assertTrue(diagnostics.any { it.contains("condition must be a boolean expression") }, diagnostics.joinToString("\n"))
                assertTrue(diagnostics.any { it.contains("Unknown entity NBT field 'playerOnly'") }, diagnostics.joinToString("\n"))
                assertFalse(diagnostics.any { it.contains("no viable alternative") || it.contains("extraneous input") }, diagnostics.joinToString("\n"))
            }
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            val functions = linkedMapOf<String, List<String>>()
            if (!errors) {
                val output = temporary.resolve("export")
                DatapackCreator.createDatapack(output.toString())
                val data = output.resolve(Project.config.name).resolve("data")
                Files.walk(data).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
                    val relative = data.relativize(file)
                    if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                        val name = relative.subpath(2, relative.nameCount).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")
                        functions["${relative.getName(0)}:$name"] = Files.readAllLines(file)
                    }
                } }
            }
            action(main, functions)
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
            temporary.toFile().deleteRecursively()
        }
    }
}
