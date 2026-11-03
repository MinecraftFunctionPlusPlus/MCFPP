package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.LibraryPublicationConfig
import top.mcfpp.command.Commands
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinWriter
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.ShortTag
import top.mcfpp.test.util.*
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

/** Actual standard-library declarations -> source compiler -> exported Minecraft commands. */
class ReferenceSourceTest {
    @Test fun explicitFactoriesReferenceCopiesAndAddressAssignmentUseTheSameBackend() = fixture("""
        func main() {
            /data modify storage test:data first set value 0
            /data modify storage test:data second set value 99
            var objective = "hp"
            const fixed: ScoreRef = scoreRef("#fixed", objective)
            objective = "other"
            fixed.set(7)
            var current: NBTRef<int> = nbtStorageRef<int>("test:data", "first")
            var copied = current
            var projected = current as NBTRef<int>
            current = nbtStorageRef<int>("test:data", "second")
            projected.set(10)
            copied.set(11)
            fixed.tryRead { value ->
                if (value == 7) {
                    /say fixed-score
                }
            }
            copied.tryRead { value ->
                if (value == 11) {
                    /say copied-storage
                }
            }
            var target = entityByName("Alice")
            var air: NBTRef<short> = target.dat.air
            target.dat.air = 4s
            air.tryRead { value ->
                nbtStorageRef<short>("test:data", "observedAir").set(value)
                /say address-assignment
            }
            nbtEntityRef<short>(target, "Air").set(5s)
            nbtBlockRef<int>("minecraft:overworld", 1, 64, 2, "count").set(6)
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(listOf("fixed-score", "copied-storage", "address-assignment"), machine.messages)
        assertEquals(7, machine.score("#fixed", "hp"))
        assertEquals(11, (machine.readStorage("test:data", "first").value as Number).toInt())
        assertEquals(99, (machine.readStorage("test:data", "second").value as Number).toInt())
        assertEquals(4, (machine.readStorage("test:data", "observedAir").value as Number).toInt())
        assertEquals(5, (machine.readEntity("Alice", "Air").value as Number).toInt())
        assertEquals(6, (machine.readBlock("minecraft:overworld", 1, 64, 2, "count").value as Number).toInt())
    }

    @Test fun entityProjectionsCaptureArgumentsAndScopedCallbacksResolveTheirOriginalExecutor() = fixture("""
        func nextIndex(): int {
            /say index-once
            return 0;
        }
        func nextObjective(): string {
            /say objective-once
            return "points";
        }
        func readBound(value: ScoreRef) {
            value.tryRead { points ->
                if (points == 13) {
                    /say bound-alice
                }
            }
        }
        func main() {
            var target = entityByName("Alice")
            var score: ScoreRef = target.score[nextObjective()]
            score.set(13)
            var index = nextIndex()
            var first: NBTRef<short> = target.dat.passengers[index].air
            index = 1
            first.set(8s)
            target.dat.fields["a.b\"c\\d"].set(9)
            target.withEntityCtx { origin ->
                const saved: ScoreRef = origin.executor.score["points"]
                entityByName("Bob").withEntityCtx { nested ->
                    readBound(saved)
                }
            }
        }
    """.trimIndent()) { main, functions ->
        val machine = execute(main, functions)
        assertEquals(listOf("objective-once", "index-once", "bound-alice"), machine.messages)
        assertEquals(8, (machine.readEntity("Alice", "Passengers[0].Air").value as Number).toInt())
        assertEquals(2, (machine.readEntity("Alice", "Passengers[1].Air").value as Number).toInt())
        assertEquals(9, (machine.readEntity("Alice", "Fields.\"a.b\\\"c\\\\d\"").value as Number).toInt())
        assertEquals(13, machine.score("Alice", "points"))
        assertEquals(5, machine.score("Bob", "points"))
    }

    @Test fun sourceRejectsInvalidExplicitAddressOperandsAndSetTypes() = fixture("""
        func main() {
            var invalid = scoreRef("@a", "hp")
            var valid = nbtStorageRef<int>("test:data", "first")
            valid.set("wrong")
            var relative = nbtBlockRef<int>("minecraft:overworld", "~1", 64, 2, "count")
        }
    """.trimIndent(), expectErrors = true) { _, _ -> }

    private fun fixture(program: String, expectErrors: Boolean = false,
                        action: (Function, Map<String, List<String>>) -> Unit) {
        val previousConfig = Project.config
        val previousIgnore = CompileSettings.ignoreStdLib
        val previousDebug = CompileSettings.isDebug
        val temporary = Files.createTempDirectory("mcfpp-reference-source-")
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf() }
            CompileSettings.ignoreStdLib = true
            fun exportLibrary(name: String, source: String): Path {
                val output = temporary.resolve(name)
                Project.config.library = LibraryPublicationConfig("fixture:reference-source-$name", "1.0.0")
                MCFPPStringTest.readFromString(source, version = "26.3")
                assertEquals(0, Project.errorCount)
                LibBinWriter.write(output.toString())
                assertTrue(Files.isRegularFile(output.resolve("bin.mclib")), "Library export must produce bin.mclib")
                Files.writeString(output.resolve("module.json"), "{}")
                return output
            }
            val result = exportLibrary("result", "namespace mcfpp.minecraft.std\n" +
                Files.readString(Path.of("src/main/mcfpp/minecraft/std/CommandResult.mcfpp")))
            Project.config.includes = arrayListOf(result.toString())
            val resources = listOf("ResourceID", "Advancement", "EntityType", "LootTablePredicate")
                .joinToString("\n") { Files.readString(Path.of("src/main/mcfpp/minecraft/resource/$it.mcfpp")) }
            val resource = exportLibrary("resource", "namespace mcfpp.minecraft.resource\nimport mcfpp.minecraft.std:*\n$resources")
            Project.config.includes = arrayListOf(result.toString(), resource.toString())
            val schema = exportLibrary("entity", """
                namespace mcfpp.minecraft.entity
                import mcfpp.minecraft.std:*
                import mcfpp.minecraft.resource:*
                data EntityData {
                    @Name("Air") var air: short
                    @Name("Passengers") var passengers: list<EntityData>
                    @Name("Fields") var fields: dict<int>
                }
            """.trimIndent())
            Project.config.includes = arrayListOf(result.toString(), resource.toString(), schema.toString())
            val factorySource = Files.readString(Path.of("src/main/mcfpp/lang/reference.mcfpp"))
            val entitySource = Files.readString(Path.of("src/main/mcfpp/lang/entity.mcfpp"))
            val diagnostics = LogProcessor.beginDiagnosticCapture().use { capture ->
                MCFPPStringTest.readFromString("import mcfpp.minecraft.entity:*\nimport mcfpp.minecraft.resource:*\n$factorySource\n$entitySource\n$program", version = "26.3")
                capture.seal().diagnostics.map { it.message }
            }
            if (expectErrors) {
                assertTrue(Project.errorCount > 0)
                assertTrue(diagnostics.any { "fixed scoreboard holder" in it }, diagnostics.joinToString("\n"))
                assertFalse(diagnostics.any { "no viable alternative" in it || "extraneous input" in it }, diagnostics.joinToString("\n"))
            } else assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            val functions = linkedMapOf<String, List<String>>()
            if (!expectErrors) {
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
            Project.config = previousConfig
            CompileSettings.ignoreStdLib = previousIgnore
            CompileSettings.isDebug = previousDebug
            temporary.toFile().deleteRecursively()
        }
    }

    private fun execute(main: Function, functions: Map<String, List<String>>): ContextCommandExecutor {
        val position = ContextPosition(0.0, 64.0, 0.0)
        val rotation = ContextRotation(0.0, 0.0)
        val alice = ContextEntity("Alice", "minecraft:player", position = position, rotation = rotation, nbt = mapOf(
            "Air" to ShortTag(9),
            "Passengers" to ListTag().apply { add(CompoundTag("Air" to ShortTag(1))); add(CompoundTag("Air" to ShortTag(2))) },
            "Fields" to CompoundTag("a.b\"c\\d" to IntTag(3))))
        val bob = ContextEntity("Bob", "minecraft:player", position = position, rotation = rotation)
        return ContextCommandExecutor(listOf(Commands.stackIn().analyze(), "function ${main.namespaceID}"), functions,
            listOf(alice, bob), bob, initialScores = mapOf("Alice points" to 7, "Bob points" to 5),
            world = { _, _, _ -> ContextBlock("minecraft:chest", nbt = mapOf("count" to IntTag(0))) },
            macros = Project.macroFunction.toMap())
    }
}
