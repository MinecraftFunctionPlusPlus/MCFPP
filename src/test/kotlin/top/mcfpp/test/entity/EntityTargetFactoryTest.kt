package top.mcfpp.test.entity

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.exception.CommandException
import top.mcfpp.lib.EntityTarget
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.EntityTargetData
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPEntityType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class EntityTargetFactoryTest {
    @Test
    fun productionDeclarationsBindStaticNamesCanonicalAndShortUUIDs() = withFactories("""
        func main() {
            var name = "Alex"
            var named = entityByName(name)
            name = "Changed"
            /say ${'$'}{named}
            var shortUUID = entityByUUID("0-0-0-0-1")
            /say ${'$'}{shortUUID}
            var fullUUID = entityByUUID("ffffffff-ffff-ffff-ffff-ffffffffffff")
            /say ${'$'}{fullUUID}
        }
    """.trimIndent()) {
        assertEquals(0, Project.errorCount)
        assertIs<NativeFunction>(scopeFunctions().getValue("entityByName").single())
        assertIs<NativeFunction>(scopeFunctions().getValue("entityByUUID").single())
        val main = scopeFunctions().getValue("main").single()
        assertEquals(listOf("Alex", "0-0-0-0-1", "ffffffff-ffff-ffff-ffff-ffffffffffff"), execute(main).messages)
        assertTrue((main.scope.getVar("named") as SelectorVar).entityFacts.playersOnly)
        assertFalse((main.scope.getVar("shortUUID") as SelectorVar).entityFacts.playersOnly)
    }

    @Test
    fun staticFactoryValidationRejectsEmptyAmbiguousAndMalformedInputs() = withFactories("func main() {}") {
        val function = scopeFunctions().getValue("main").single()
        val commandCount = function.commands.size
        val invalidNames = listOf("", "@s", "0-0-0-0-1", "bad name", "a".repeat(17), "a-b")
        val invalidUUIDs = listOf("", "0-0-0-1", "0--0-0-1", "000000000-0-0-0-1", "+0-0-0-0-1", "0-0-0-0-z")
        for ((nameFactory, values) in listOf(true to invalidNames, false to invalidUUIDs)) for (value in values) {
            val errors = Project.errorCount
            val context = NativeCallContext(function, null, listOf(MCString(StringTag(value))), MCFPPEntityType.NormalSelector)
            if (nameFactory) EntityTargetData.entityByName(context) else EntityTargetData.entityByUUID(context)
            assertEquals(errors + 1, Project.errorCount, value)
            assertNull(context.result, value)
        }
        assertEquals(commandCount, function.commands.size)
    }

    @Test
    fun runtimeFactoriesFreezeParametersAndGuardActualSourceConsumers() = withFactories("""
        data Box {
            func byName(input: string):int {
                var selected = entityByName(input)
                input = "Changed"
                /say ${'$'}{selected}
                return 7
            }
            func byUUID(input: string):int {
                var selected = entityByUUID(input)
                input = "Changed"
                /say ${'$'}{selected}
                return 7
            }
        }
        func main() {
            var box = Box()
            external named = box.byName("Alex")
            external identified = box.byUUID("0-0-0-0-1")
        }
    """.trimIndent()) {
        assertEquals(0, Project.errorCount)
        val main = scopeFunctions().getValue("main").single()
        val box = main.scope.getVar("box") as DataTemplateObject
        val methods = box.templateType.scope.functions
        for (isName in listOf(true, false)) {
            val method = methods.getValue(if (isName) "byName" else "byUUID").single()
            val binding = assertNotNull(method.scope.getVar("input")?.storageBinding)
            val valid = if (isName) listOf("Alex", "A_9", "a".repeat(16))
                else listOf("0-0-0-0-1", "ffffffff-ffff-ffff-ffff-ffffffffffff")
            val invalid = if (isName) listOf("", "@s", "a-b", "bad name", "a".repeat(17), "x\$(arg)")
                else listOf("", "0-0-0-1", "0--0-0-1", "000000000-0-0-0-1", "+0-0-0-0-1", "0-0-0-0-z", "0-0-0-0-1 run say injected")
            for (value in valid + invalid) {
                val machine = execute(method, listOf(Commands.dataSetValue(binding.path, StringTag(value)).analyze()))
                if (value in valid) assertEquals(listOf(value), machine.messages, value)
                else {
                    assertEquals(1, machine.messages.size, value)
                    assertTrue(machine.messages.single().startsWith("Invalid runtime ${if (isName) "entityByName" else "entityByUUID"} target;"), value)
                }
            }
        }
        assertEquals(0, Project.errorCount)
    }

    @Test
    fun dedicatedGuardsSurviveCommandCopiesAndBothMacroEntrypoints() = withFactories("func main() {}") {
        for (isName in listOf(true, false)) for (manual in listOf(false, true)) {
            val namespace = GlobalScope.localNamespaces.getValue("default.test")
            val factory = assertIs<NativeFunction>(scopeFunctions().getValue(if (isName) "entityByName" else "entityByUUID").single())
            val function = Function("copied_guard_${isName}_$manual", namespace.identifier, null)
            namespace.scope.addFunction(function, false)
            lateinit var input: MCString
            function.runInFunction {
                input = MCString("input")
                StorageAccess.bindIncomingParameter(input)
                val selected = assertIs<SelectorVar>(factory.invoke(emptyList(), listOf(input), null))
                Function.addCommand(Commands.dataSetValue(input.nbtPath, StringTag("Changed")))
                val part = selected.toCommandPart().clone()
                val command = Command("say").build(part).clone()
                assertFailsWith<CommandException> { command.analyze() }
                if (manual) {
                    val operand = when (val target = selected.entityTargetOrNull()) {
                        is EntityTarget.PlayerName -> target.name
                        is EntityTarget.UUIDText -> target.uuid
                        else -> error("Expected a direct text target")
                    }
                    val arguments = NBTPath.stack.intIndex(0).memberIndex("manualArguments")
                    Function.addCommand(Commands.dataSetValue(arguments, CompoundTag()))
                    Function.addCommand(Commands.dataSetFrom(arguments.memberIndex(operand.identifier), operand.nbtPath))
                    Function.addCommand(command.buildMacroFunction(arguments))
                } else Function.addCommands(command.buildMacroFunction())
            }
            val valid = if (isName) "A_9" else "0-0-0-0-1"
            val invalid = if (isName) "a-b" else "Alex"
            for (value in listOf(valid, invalid, "")) {
                val machine = execute(function, listOf(Commands.dataSetValue(input.nbtPath, StringTag(value)).analyze()))
                if (value == valid) assertEquals(listOf(value), machine.messages)
                else {
                    assertEquals(1, machine.messages.size)
                    assertTrue(machine.messages.single().startsWith("Invalid runtime ${if (isName) "entityByName" else "entityByUUID"} target;"))
                }
            }
        }
        assertEquals(0, Project.errorCount)
    }

    private fun scopeFunctions() = GlobalScope.localNamespaces.getValue("default.test").scope.functions

    private fun execute(function: Function, initialize: List<String> = emptyList()): ScoreCommandExecutor {
        val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.flatMap { command -> command.analyze().lines() } }
            .toMutableMap()
        Project.macroFunction.forEach { (name, body) -> functions["mcfpp:dynamic/$name"] = body.lines() }
        return ScoreCommandExecutor(listOf(Commands.stackIn().analyze()) + initialize + function.commands.flatMap { it.analyze().lines() }, functions)
    }

    private fun withFactories(program: String, action: () -> Unit) {
        val config = Project.config
        val ignored = CompileSettings.ignoreStdLib
        val debug = CompileSettings.isDebug
        try {
            Project.config = ProjectConfig().apply { includes = arrayListOf() }
            CompileSettings.ignoreStdLib = true
            val productionDeclarations = Files.readString(Path.of("src/main/mcfpp/lang/entity.mcfpp"))
            MCFPPStringTest.readFromString(productionDeclarations + "\n" + program, version = "26.3")
            action()
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignored
            CompileSettings.isDebug = debug
        }
    }
}
