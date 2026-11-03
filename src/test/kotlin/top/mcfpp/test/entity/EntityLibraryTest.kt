package top.mcfpp.test.entity

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.context.*
import top.mcfpp.core.lang.entity.EntityDataAddress
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.io.*
import top.mcfpp.library.format.LibraryWire
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.ShortTag
import top.mcfpp.test.util.*
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Uses the bundled production library, without local replacements or ignoreStdLib. */
class EntityLibraryTest {
    @Test fun bundledLibraryAndFreshHelperImportExecuteEntityContextAndReadInterfaces() = isolated {
        val declarations = """
            namespace fixture.entitylibrary
            import mcfpp.lang:*
            func relocate(actor: entity, location: ContextPosition) { tp(actor, location); }
            func observeContext(context: ExecutionContext) {}
            func inspect(actor: entity) {
                actor.withEntityCtx { self ->
                    var airFound = self.executor.dat.air.tryRead { air ->
                        if (toInt(air) == 5) {
                            /say air-found
                        }
                    }
                    var scoreFound = self.executor.score["points"].tryRead { points ->
                        if (points == 7) {
                            /say score-found
                        }
                    }
                    if (airFound && scoreFound) {
                        /say both-found
                    }
                }
            }
        """.trimIndent()
        fun calls(prefix: String) = """
            func main() {
                entityByName("Alice").withPosCtx { origin ->
                    origin.onPassengers { rider ->
                        ${prefix}relocate(rider.executor, origin.pos)
                        ${prefix}inspect(rider.executor)
                    }
                }
                var moved = tp(entityByUUID("0-0-0-0-1"), 1 2 3)
                if (moved.success && moved.result == 1) {
                    /say uuid-moved
                }
                /execute if block ~ ~ ~ minecraft:stone run say after
            }
        """.trimIndent()
        MCFPPStringTest.readFromString(declarations + "\n" + calls(""), version = "26.3")
        assertEquals(0, Project.errorCount, "Source must compile against the actual bundled standard library")
        val original = GlobalScope.localNamespaces.getValue("fixture.entitylibrary").scope.functions.getValue("relocate").single()
        verifyExecution("source", original.namespace)
        val library = Path.of("build/lambda/entity-library/producer")
        // Export after synchronous helper specializations have run: live sessions must stay out.
        LibBinWriter.write(library.toString())
        Files.newInputStream(library.resolve("bin.mclib")).use {
            assertEquals(LibraryWire.CURRENT_VERSION, LibraryWire.read(it).formatVersion)
        }
        Project.config.includes = arrayListOf(library.toString())
        MCFPPStringTest.readFromString("import mcfpp.lang:*\nimport fixture.entitylibrary:*\n" + calls("fixture.entitylibrary:"), version = "26.3", afterFileCreated = {
            val restored = GlobalScope.libNamespaces.getValue("fixture.entitylibrary").scope.functions.getValue("relocate").single()
            assertNotSame(original, restored)
            assertEquals(ContextTypes.Position, restored.normalParams[1].type)
            assertNull((restored.scope.getVar(restored.normalParams[1].identifier) as? ScopedContextValue)?.contextScope)
            val observer = GlobalScope.libNamespaces.getValue("fixture.entitylibrary").scope.functions.getValue("observeContext").single()
            assertEquals(ContextTypes.ExecutionContext, observer.normalParams.single().type)
            assertNull((observer.scope.getVar("context") as? ScopedContextValue)?.contextScope)
        })
        assertEquals(0, Project.errorCount, "A fresh compiler session must resolve imported helpers and real EntityData @Name metadata")
        verifyExecution("imported", "default.test")
    }

    @Test fun legacyEntitySyntaxAndMetadataGiveMigrationDiagnostics() = isolated {
        for (syntax in listOf("entity<1>", "entity<\"minecraft:pig\">", "entity<2,\"minecraft:pig\">", "Player")) {
            val messages = LogProcessor.beginDiagnosticCapture().use { capture ->
                MCFPPStringTest.readFromString("func legacy(target: $syntax) {}", version = "26.3")
                capture.seal().diagnostics.map { it.message }
            }
            assertTrue(Project.errorCount > 0, syntax)
            assertTrue(messages.any { "Legacy entity subtype" in it && "use entity" in it }, messages.joinToString("\n"))
        }
        val identities = TypeIdentitySerializer()
        val failure = assertFailsWith<IllegalArgumentException> {
            identities.write(KryoManager.kryo, Output(128), TypeId.Selector(1, listOf("minecraft:pig"), false))
        }
        assertContains(failure.message.orEmpty(), "recompile")
        assertFailsWith<IllegalArgumentException> {
            identities.read(KryoManager.kryo, Input(byteArrayOf(4)), TypeId::class.java)
        }
    }

    @Test fun liveAddressesAndContextCapabilitiesCannotBeSerialized() = isolated {
        Project.init()
        Function("guards", "fixture.entitylibrary", null).runInFunction {
            val address = EntityDataAddress.nbt(SelectorVar(EntitySelector('s')), MCFPPNBTType.NBT)
            fun rejects(value: Any, message: String) {
                val failure = assertFails { KryoManager.kryo.writeClassAndObject(Output(1024), value) }
                assertTrue(generateSequence(failure as Throwable) { it.cause }.any { message in it.message.orEmpty() })
            }
            rejects(address, "Live entity data addresses")
            ContextSession().use { session ->
                val context = session.root()
                rejects(ExecutionContextVar("active", context), "Scoped context values")
                rejects(ContextBorrowPayload.Context(context, null), "Scoped context borrows")
            }
        }
    }

    @Test fun migratedTriggerKeepsPlayerFactsAndMultiplePlayerTargets() = isolated {
        fun compile(target: String) {
            MCFPPStringTest.readFromString("""
                import mcfpp.minecraft.other:*
                func main() {
                    var trigger = {id: "fixture_trigger"} as Trigger
                    trigger.enable<$target>()
                }
            """.trimIndent(), version = "26.3")
        }
        compile("@a")
        assertEquals(0, Project.errorCount)
        val functions = exported("trigger", "default.test")
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val entities = listOf("Alice", "Bob").map { name ->
            ContextEntity(name, "minecraft:player", position = ContextPosition(0.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0))
        } + ContextEntity("pig", position = ContextPosition(0.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0))
        val machine = ContextCommandExecutor(listOf("function ${main.namespaceID}"), functions, entities, macros = Project.macroFunction.toMap())
        assertEquals(listOf("Alice" to "fixture_trigger", "Bob" to "fixture_trigger"), machine.enabledTriggers)
        val diagnostics = LogProcessor.beginDiagnosticCapture().use { capture ->
            compile("@e")
            capture.seal().diagnostics.map { it.message }
        }
        assertTrue(diagnostics.any { "only players" in it }, diagnostics.joinToString("\n"))
    }

    private fun verifyExecution(name: String, namespace: String) {
        val functions = exported(name, namespace)
        val main = GlobalScope.localNamespaces.getValue(namespace).scope.functions.getValue("main").single()
        val entities = listOf(
            ContextEntity("carrier", position = ContextPosition(0.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0), relations = mapOf("passengers" to listOf("Bob"))),
            ContextEntity("Alice", "minecraft:player", position = ContextPosition(10.0, 70.0, 20.0), rotation = ContextRotation(90.0, 10.0), dimension = "minecraft:the_nether"),
            ContextEntity("Bob", position = ContextPosition(15.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0), nbt = mapOf("Air" to ShortTag(5))),
            ContextEntity("identified", position = ContextPosition(8.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0), uuid = java.util.UUID(0L, 1L)))
        val machine = ContextCommandExecutor(listOf(Commands.stackIn().analyze(), "function ${main.namespaceID}"), functions, entities,
            entities.first(), ContextPosition(3.0, 64.0, -4.0), ContextRotation(15.0, -5.0), initialScores = mapOf("Bob points" to 7),
            world = { _, _, _ -> ContextBlock("minecraft:stone") }, macros = Project.macroFunction.toMap())
        assertEquals(listOf("air-found", "score-found", "both-found", "uuid-moved", "after"), machine.messages)
        assertEquals(listOf("Bob", "identified"), machine.teleports.map { it.target })
        assertEquals(ContextPosition(10.0, 70.0, 20.0), machine.teleports.first().to)
        assertEquals("minecraft:the_nether", machine.teleports.first().dimension)
        assertEquals("carrier", machine.blockTests.single().executor)
        assertEquals(ContextPosition(3.0, 64.0, -4.0), machine.blockTests.single().position)
        assertEquals(1, machine.executed.count { it.startsWith("data modify ") && it.endsWith("set from entity @s Air") })
    }

    private fun exported(name: String, namespace: String): Map<String, List<String>> {
        val output = Path.of("build/lambda/entity-library/$name")
        Project.config.name = "pack"
        Project.config.copyImport = false
        DatapackCreator.createDatapack(output.toString())
        val data = output.resolve("pack/data")
        val functions = linkedMapOf<String, List<String>>()
        Files.walk(data).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
            val relative = data.relativize(file)
            if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                functions["${relative.getName(0)}:${relative.subpath(2, relative.nameCount).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
            }
        } }
        assertTrue(GlobalScope.localNamespaces.getValue(namespace).scope.functions.containsKey("main"))
        return functions
    }

    private fun isolated(action: () -> Unit) {
        val config = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf() }
            CompileSettings.ignoreStdLib = false
            action()
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
        }
    }
}
