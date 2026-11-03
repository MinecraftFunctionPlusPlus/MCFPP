package top.mcfpp.test.entity

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.LibraryPublicationConfig
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.Void
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.io.DatapackCreator
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntityTarget
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.SelectorData
import top.mcfpp.mni.minecraft.EntityAttributeData
import top.mcfpp.mni.minecraft.EntityRideData
import top.mcfpp.mni.minecraft.EntityTeleportData
import top.mcfpp.mni.minecraft.PlayerXpData
import top.mcfpp.mni.minecraft.StdCommands
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.*
import top.mcfpp.type.MCFPPEntityType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class EntityCommandTargetTest {
    @Test
    fun directTargetsAndBoundExecutorsUseCommonCommandMembers() = withFixture("""
        func mark(target: entity) { target.addTag("helper"); }
        func main() {
            var named = entityByName("Alice")
            var identified = entityByUUID("0-0-0-0-1")
            mark(named)
            named.addTag("named")
            named.ride(identified)
            identified.listTag()
            identified.stopRide()
            named.getAttributeBase("fixture:test", 1.0)
            named.queryXpPoints()
            @e.withEntityCtx { ctx ->
                ctx.executor.addTag("bound")
                ctx.executor.ride(named)
                ctx.executor.getAttributeBase("fixture:test", 1.0)
            }
            @a.withEntityCtx { player -> player.executor.queryXpPoints(); }
        }
    """.trimIndent()) { _, output ->
        val commands = exportedFunctions(output).values.flatten()
        assertTrue(commands.any { it.contains("run tag Alice add named") })
        assertTrue(commands.any { it.contains("run ride Alice mount 0-0-0-0-1") })
        assertTrue(commands.any { it.contains("run tag 0-0-0-0-1 list") })
        assertTrue(commands.any { it.contains("run ride 0-0-0-0-1 dismount") })
        assertTrue(commands.any { it.contains("run tag @s add bound") })
        assertTrue(commands.any { it.contains("run attribute @s fixture:test base get 1.0") })
        assertTrue(commands.any { it.contains("run xp query @s points") })
        val common = MCFPPEntityType.NormalSelector.instanceData
        assertNotNull(common.scope.getVar("commonField"))
        assertNull(common.scope.getVar("zombieOnly"))
        val methods = StdCommands::class.java.declaredMethods.mapNotNull {
            it.getAnnotation(top.mcfpp.annotations.MNIFunction::class.java)
        }
        assertTrue(methods.flatMap { it.normalParams.toList() }.none { it.contains("entity<") || it.startsWith("Player") })
    }

    @Test
    fun singlePlayerAndSelectorConstructionRequirementsUseValueFacts() = withFixture("func main() {}") { main, _ ->
        val resultType = GlobalScope.getUnsolvedImportNamespace("mcfpp.minecraft.std")!!.scope.getTemplate("CommandResult")!!.getType()
        main.runInFunction {
            fun rejects(receiver: Var<*>, args: List<Var<*>> = emptyList(), operation: (NativeCallContext) -> Unit) {
                val before = Project.errorCount
                val context = NativeCallContext(main, receiver, args, resultType)
                operation(context)
                assertEquals(before + 1, Project.errorCount)
                assertNull(context.result)
            }
            rejects(SelectorVar(EntitySelector('p').limit(2)), operation = PlayerXpData::queryXpPoints)
            val runtimeLimit = MCInt("runtimeLimit")
            StorageAccess.bindIncomingParameter(runtimeLimit)
            rejects(SelectorVar(EntitySelector('p').limit(runtimeLimit)), operation = PlayerXpData::queryXpPoints)
            rejects(SelectorVar(EntitySelector('e')), listOf(MCInt(1)), PlayerXpData::addXpPoints)
            val uuid = SelectorVar(EntityTarget.UUIDText(text("0-0-0-0-1")))
            rejects(uuid, listOf(MCInt(1)), PlayerXpData::addXpPoints)
            rejects(SelectorVar(EntitySelector('a')), listOf(text("fixture:test"), MCFloat(1f)), EntityAttributeData::getAttributeBase)
            rejects(SelectorVar(EntitySelector('p').limit(2)), listOf(uuid), EntityRideData::ride)
            rejects(SelectorVar(EntitySelector('a')), listOf(SelectorVar(EntitySelector('a'))), EntityTeleportData::teleportToEntity)
            val unknown = MCFPPEntityType(1, listOf("minecraft:player")).buildUnConcrete("unknown")
            StorageAccess.bindIncomingParameter(unknown)
            rejects(unknown, operation = PlayerXpData::queryXpPoints)
            val named = SelectorVar(EntityTarget.PlayerName(text("Alice")))
            val before = Project.errorCount
            val filter = NativeCallContext(main, named, listOf(MCInt(1)), MCFPPEntityType.NormalSelector)
            SelectorData.limit(filter)
            assertEquals(before + 1, Project.errorCount)
            assertNull(filter.result)
            val property = assertNotNull(MCFPPEntityType.data.scope.getProperty("x"))
            val setterErrors = Project.errorCount
            property.setter(named, Void, MCInt(1))
            assertEquals(setterErrors + 1, Project.errorCount)
        }
    }

    @Test
    fun currentAttributeAndXpResultsIncludeZeroMissingAndGuardedFailure() = withFixture("""
        func observe(name: string) {
            var query = entityByName(name).queryXpPoints()
            if (query.success && query.result == 0) {
                /say xp-zero-success
            }
            if (!query.success && query.result == 0) {
                /say xp-failure
            }
        }
        func main() {
            var found = entityByName("Alice").getAttributeBase("fixture:test", 1.0)
            var missing = entityByName("Alice").getAttributeBase("fixture:missing", 1.0)
            var absent = entityByName("Missing").getAttributeBase("fixture:test", 1.0)
            if (found.success && found.result == 7) {
                /say attribute-success
            }
            if (!missing.success && missing.result == 0) {
                /say attribute-missing
            }
            if (!absent.success && absent.result == 0) {
                /say entity-missing
            }
            observe("Alice")
            observe("")
            observe("Missing")
        }
    """.trimIndent()) { main, output ->
        val machine = execute(main, exportedFunctions(output), entities())
        val diagnostics = machine.messages.filter { it.startsWith("Invalid runtime entityByName target;") }
        assertEquals(1, diagnostics.size)
        assertEquals(listOf("attribute-success", "attribute-missing", "entity-missing", "xp-zero-success", "xp-failure", "xp-failure"),
            machine.messages - diagnostics.toSet())
    }

    @Test
    fun multiVictimDamageRetainsOriginalSecondarySelfTargets() = withFixture("func main() {}") { _, output ->
        val namespace = GlobalScope.localNamespaces.getValue("default.test")
        val function = Function("damage_targets", namespace.identifier, null)
        namespace.scope.addFunction(function, false)
        function.runInFunction {
            StdCommands.damageFrom(NativeCallContext(function, null, listOf(
                SelectorVar(EntitySelector('a')), MCFloat(1f), SelectorVar(EntitySelector('s')),
                SelectorVar(EntitySelector('s')), text("fixture:test")
            )))
        }
        assertEquals(0, Project.errorCount)
        val machine = execute(function, exportedFunctions(output), entities())
        assertEquals(listOf("Alice", "Bob"), machine.damages.map { it.target })
        assertEquals(List(2) { "carrier" }, machine.damages.map { it.attacker })
        assertEquals(List(2) { "carrier" }, machine.damages.map { it.origin })
        assertEquals(List(2) { 1.0 }, machine.damages.map { it.amount })
    }

    private fun entities() = listOf(
        ContextEntity("carrier", position = ContextPosition(0.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0)),
        ContextEntity("Alice", "minecraft:player", position = ContextPosition(1.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0),
            attributes = mapOf("fixture:test" to 7.0), xpPoints = 0),
        ContextEntity("Bob", "minecraft:player", position = ContextPosition(2.0, 64.0, 0.0), rotation = ContextRotation(0.0, 0.0))
    )

    private fun execute(function: Function, functions: Map<String, List<String>>, entities: List<ContextEntity>): ContextCommandExecutor =
        ContextCommandExecutor(listOf(Commands.stackIn().analyze(), "function ${function.namespaceID}"), functions, entities,
            entities.single { it.name == "carrier" }, ContextPosition(0.0, 64.0, 0.0), ContextRotation(0.0, 0.0), macros = Project.macroFunction.toMap())

    private fun exportedFunctions(output: Path): Map<String, List<String>> {
        DatapackCreator.createDatapack(output.toString())
        val data = output.resolve(Project.config.name).resolve("data")
        val functions = linkedMapOf<String, List<String>>()
        Files.walk(data).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
                val relative = data.relativize(file)
                if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                    val id = relative.subpath(2, relative.nameCount).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")
                    functions["${relative.getName(0)}:$id"] = Files.readAllLines(file)
                }
            }
        }
        return functions
    }

    private fun withFixture(program: String, action: (Function, Path) -> Unit) {
        val config = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        val temporary = Files.createTempDirectory("mcfpp-entity-consumers-")
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf() }
            CompileSettings.ignoreStdLib = true
            val resultLibrary = temporary.resolve("result")
            Project.config.library = LibraryPublicationConfig("fixture:entity-command-result", "1.0.0")
            val resultSource = Files.readString(Path.of("src/main/mcfpp/minecraft/std/CommandResult.mcfpp"))
            MCFPPStringTest.readFromString("namespace mcfpp.minecraft.std\n$resultSource", targetPath = resultLibrary.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val otherLibrary = temporary.resolve("other")
            Project.config.library = LibraryPublicationConfig("fixture:entity-command-other", "1.0.0")
            val otherSource = Files.readString(Path.of("src/main/mcfpp/minecraft/other/Attribute.mcfpp"))
            MCFPPStringTest.readFromString("namespace mcfpp.minecraft.other\n$otherSource", targetPath = otherLibrary.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val resourceLibrary = temporary.resolve("resource")
            Project.config.library = LibraryPublicationConfig("fixture:entity-command-resource", "1.0.0")
            val resourceSource = listOf("ResourceID", "Advancement", "EntityType", "LootTablePredicate")
                .joinToString("\n") { Files.readString(Path.of("src/main/mcfpp/minecraft/resource/$it.mcfpp")) }
            MCFPPStringTest.readFromString("namespace mcfpp.minecraft.resource\n$resourceSource", targetPath = resourceLibrary.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            Project.config.includes = arrayListOf(resultLibrary.toString(), otherLibrary.toString(), resourceLibrary.toString())
            val commonLibrary = temporary.resolve("entity")
            Project.config.library = LibraryPublicationConfig("fixture:entity-command-entity", "1.0.0")
            MCFPPStringTest.readFromString("""
                namespace mcfpp.minecraft.entity
                import mcfpp.minecraft.std:*
                import mcfpp.minecraft.other:*
                import mcfpp.minecraft.resource:*
                @From("top.mcfpp.mni.minecraft.EntityTagData")
                @From("top.mcfpp.mni.minecraft.EntityRideData")
                @From("top.mcfpp.mni.minecraft.EntityAttributeData")
                @From("top.mcfpp.mni.minecraft.PlayerXpData")
                data EntityData { var commonField: int; }
                @MCFPPEntity
                data ZombieData { var zombieOnly: int; }
            """.trimIndent(), targetPath = commonLibrary.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            Project.config.includes = arrayListOf(resultLibrary.toString(), otherLibrary.toString(), resourceLibrary.toString(), commonLibrary.toString())
            val factories = Files.readString(Path.of("src/main/mcfpp/lang/entity.mcfpp"))
            MCFPPStringTest.readFromString("import mcfpp.minecraft.std:*\nimport mcfpp.minecraft.other:*\nimport mcfpp.minecraft.resource:*\nimport mcfpp.minecraft.entity:*\n$factories\n$program", version = "26.3")
            assertEquals(0, Project.errorCount)
            action(GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single(), temporary.resolve("export"))
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
            temporary.toFile().deleteRecursively()
        }
    }

    private fun text(value: String) = MCString(StringTag(value))
}
