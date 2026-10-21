package top.mcfpp.test

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIOperator
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.NativeAccessor
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class NativeTemplateCommandContractTest {
    @Test fun invalidNativeSignaturesAndUnpublishedOrFailedResultsAreErrors() = withLibrary { _ ->
        CompileSettings.ignoreStdLib = true
        MCFPPStringTest.readFromString("func main(){}")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        main.runInFunction {
            for ((name, parameters) in listOf(
                "legacyHostCall" to emptyArray<Class<*>>(),
                "missingResult" to arrayOf<Class<*>>(NativeCallContext::class.java),
                "failedResult" to arrayOf<Class<*>>(NativeCallContext::class.java)
            )) {
                val before = main.commands.size
                val errors = Project.errorCount
                val method = NativeTemplateCommandContractTest::class.java.getMethod(name, *parameters)
                val function = NativeFunction(name, javaMethod = method).apply { returnType = MCFPPBaseType.Int }
                val result = function.invoke(emptyList(), null)
                assertTrue(result.isError, name)
                assertNull(top.mcfpp.analysis.StorageAccess.snapshot(result), name)
                assertEquals(errors + 1, Project.errorCount, name)
                assertEquals(before, main.commands.size, name)
            }
        }

        val logger = assertIs<Logger>(LogProcessor.logger)
        val captured = arrayListOf<String>()
        val appender = object : AbstractAppender("native-registration-contract", null, null, false, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) { captured.add(event.message.formattedMessage) }
        }
        appender.start()
        try {
            logger.addAppender(appender)
            MCFPPStringTest.readFromString("func main(){}")
            assertEquals(0, Project.errorCount)
            val registrationMain = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            val commands = registrationMain.commands.toList()
            val errors = Project.errorCount
            val template = DataTemplate("NativeRegistration", "default.test")
            captured.clear()
            template.injectedBy(NativeRegistrationContractHelper::class.java)
            val diagnostics = captured.joinToString("\n")
            assertEquals(errors + 3, Project.errorCount, diagnostics)
            assertEquals(2, Regex("must use NativeCallContext").findAll(diagnostics).count(), diagnostics)
            assertFalse(template.scope.functions.containsKey("wrongSignature"))
            assertTrue(diagnostics.contains("must use Var wrappers"), diagnostics)
            for (name in listOf("legacyUnary", "legacyBinary")) {
                val diagnostic = "Method $name in class top.mcfpp.test.NativeRegistrationContractHelper must use NativeCallContext"
                assertEquals(1, Regex(Regex.escape(diagnostic)).findAll(diagnostics).count(), diagnostics)
                assertFalse(template.scope.functions.containsKey(name), name)
            }
            assertNull(template.getOperator("!", null))
            assertNull(template.getOperator("+", MCFPPBaseType.Int))
            val registered = assertIs<NativeFunction>(template.scope.functions.getValue("valid").single())
            assertEquals(NativeRegistrationContractHelper::class.java.getMethod("valid", NativeCallContext::class.java), registered.javaMethod)
            assertSame(template, registered.owner)
            assertEquals(top.mcfpp.type.MCFPPPrivateType.Void, registered.caller)
            assertEquals(top.mcfpp.type.MCFPPPrivateType.Void, registered.returnType)
            assertTrue(registered.readOnlyParams.isEmpty())
            assertTrue(registered.normalParams.isEmpty())
            val legacy = assertIs<NativeFunction>(template.scope.functions.getValue("legacyFree").single())
            registrationMain.runInFunction {
                registered.invoke(emptyList(), null)
                legacy.invoke(emptyList(), null)
            }
            assertEquals(errors + 3, Project.errorCount)
            assertEquals(commands, registrationMain.commands)

            captured.clear()
            MCFPPStringTest.readFromString("""
                func acceptedLegacy() = top.mcfpp.test.NativeRegistrationContractHelper.legacyFree
                func acceptedLegacyResult(): int = top.mcfpp.test.NativeRegistrationContractHelper.legacyResult
                func accepted() = top.mcfpp.test.NativeRegistrationContractHelper.valid
                func main(){external result=acceptedLegacyResult();}
            """.trimIndent())
            val declarationDiagnostics = captured.joinToString("\n")
            assertFalse(assertNotNull(MCFPPFile.currFile).syntaxError, declarationDiagnostics)
            assertEquals(0, Project.errorCount, declarationDiagnostics)
            val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
            val acceptedLegacy = assertIs<NativeFunction>(functions.getValue("acceptedLegacy").single())
            assertTrue(acceptedLegacy.javaMethod.parameterTypes.isEmpty())
            val accepted = assertIs<NativeFunction>(functions.getValue("accepted").single())
            assertTrue(java.lang.reflect.Modifier.isStatic(accepted.javaMethod.modifiers))
            assertContentEquals(arrayOf<Class<*>>(NativeCallContext::class.java), accepted.javaMethod.parameterTypes)
            assertEquals(Function.Companion.OwnerType.NONE, accepted.ownerType)
            assertEquals(top.mcfpp.type.MCFPPPrivateType.Void, accepted.returnType)
            assertTrue(accepted.readOnlyParams.isEmpty())
            assertTrue(accepted.normalParams.isEmpty())
            val acceptedMain = functions.getValue("main").single()
            assertEquals(7, ScoreCommandExecutor(acceptedMain.commands.analyzeAll()).read(acceptedMain.scope.getVar("result") as MCInt))
            val acceptedCommands = acceptedMain.commands.toList()
            acceptedMain.runInFunction {
                accepted.invoke(emptyList(), null)
                acceptedLegacy.invoke(emptyList(), null)
            }
            assertEquals(0, Project.errorCount)
            assertEquals(acceptedCommands, acceptedMain.commands)

            captured.clear()
            MCFPPStringTest.readFromString("""
                func rejected(value:string) = top.mcfpp.test.NativeRegistrationContractHelper.wrongSignature
                func main(){}
            """.trimIndent())
            val rejectedDiagnostics = captured.joinToString("\n")
            assertEquals(1, Project.errorCount, rejectedDiagnostics)
            assertTrue(rejectedDiagnostics.contains("must use Var wrappers"), rejectedDiagnostics)
            assertFalse(GlobalScope.localNamespaces.getValue("default.test").scope.functions.containsKey("rejected"))
        } finally {
            logger.removeAppender(appender)
            appender.stop()
        }
    }

    @Test fun unencodableAreaAndOldTargetSlotsDoNotPublishCommandResults() = withLibrary { _ ->
        fun reject(source: String, version: String = "26.3", operation: String) {
            Project.config.includes = arrayListOf()
            MCFPPStringTest.readFromString(source.trimIndent(), version = version)
            assertTrue(Project.errorCount > 0)
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            val result = main.scope.getVar("result")
            assertTrue(result == null || result.isError, "A rejected native call must not bind a valid result")
            val commands = main.commands.map(Command::toString)
            assertFalse(commands.any { "run $operation " in it })
            assertFalse(commands.any { "store success" in it && "run $operation" in it })
        }
        reject("""
            import mcfpp.minecraft:*;import mcfpp.minecraft.other:*;import mcfpp.minecraft.std:*
            func main(){var area: Area;var result=clone(area,0 0 0,CloneMaskMode.replace,CloneOperation.normal);}
        """, operation = "clone")
        reject("""
            import mcfpp.minecraft:*;import mcfpp.minecraft.other:*;import mcfpp.minecraft.std:*
            func main(){var area=Area(^ ^ ^,1 2 3);var result=clone(area,0 0 0,CloneMaskMode.replace,CloneOperation.normal);}
        """, operation = "clone")
        reject("""
            import mcfpp.minecraft.other:*;import mcfpp.minecraft.resource:*;import mcfpp.minecraft.std:*
            func main(){external index=3;var slot=Slot(index,SlotType.container)
                var loot=LootTable();loot.id="fixture:loot";var result=lootReplace(0 0 0,slot,loot);}
        """, version = "1.19.4", operation = "loot")
    }

    @Test fun predicateFactoryCopiesNormalIdAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = "func main(){var box=Box();external result=box.observe(\"fixture:original\");}"
        write("""
            namespace fixture.contracts
            import mcfpp.minecraft.resource:*
            data Box {
                func observe(id: string):int {
                    var original=Predicate.of(id)
                    var copied=original
                    id="fixture:changed"
                    /data modify storage fixture:observed original set from storage mcfpp:system stack_frame[0].original.id
                    /data modify storage fixture:observed copied set from storage mcfpp:system stack_frame[0].copied.id
                    return 7
                }
            }
            $mainSource
        """, output)
        fun check(main: Function, executionOutput: Path) {
            val observe = observe(main)
            assertTrue(observe.compiledFunctions.isEmpty())
            val original = assertIs<DataTemplateObject>(observe.scope.getVar("original"))
            val copied = assertIs<DataTemplateObject>(observe.scope.getVar("copied"))
            assertNotEquals(assertNotNull(original.storageBinding).place, assertNotNull(copied.storageBinding).place)
            val functions = exported(executionOutput)
            val machine = ScoreCommandExecutor(functions.getValue(main.namespaceID.toString()), functions)
            assertEquals("fixture:original", assertIs<StringTag>(machine.readNbt("fixture:observed", "original")).value)
            assertEquals("fixture:original", assertIs<StringTag>(machine.readNbt("fixture:observed", "copied")).value)
            assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(0, machine.stackDepth)
        }
        check(sourceMain(), output.resolve("source"))
        check(consume(mainSource, output), output.resolve("fresh"))
    }

    @Test fun nominalAreaAndSlotsUseActualFieldsAcrossLibraryRoundTrip() = withLibrary { output ->
        fun mainSource(flag: Boolean) = "func main(){var box=Box();external result=box.observe(3,SlotType.container,\"fixture:loot\",$flag);}"
        val librarySource = """
            namespace fixture.contracts
            import mcfpp.minecraft:*
            import mcfpp.minecraft.other:*
            import mcfpp.minecraft.resource:*
            import mcfpp.minecraft.std:*
            data Box {
                func reject(flag: bool) {
                    var area=Area(1 2 3,4 5 6)
                    while(flag){area.end=10 11 12;break;}
                    clone(area,7 8 9,CloneMaskMode.replace,CloneOperation.normal)
                }
                func observe(index: int,kind: SlotType,id: string,flag: bool):int {
                    var area=Area(1 2 3,4 5 6)
                    var areaCopy=area
                    area.end=10 11 12
                    clone(areaCopy,7 8 9,CloneMaskMode.replace,CloneOperation.normal)
                    clone(area,7 8 9,CloneMaskMode.replace,CloneOperation.normal)
                    var conditional=Area(0 0 0,0 0 0)
                    if(flag){
                        conditional.start=1 2 3;conditional.end=4 5 6
                        /say constructed-area
                    }
                    else{conditional.start=1 2 3;conditional.end=4 5 6;}
                    clone(conditional,7 8 9,CloneMaskMode.replace,CloneOperation.normal)
                    var indexed=Slot(index,kind)
                    var copied=indexed
                    indexed.index=index+1
                    var unindexed=Slot(SlotType.weapon_mainhand)
                    var loot=LootTable();loot.id=id
                    var first=lootReplace(0 0 0,copied,loot)
                    var second=lootReplace(0 0 0,unindexed,loot)
                    var third=lootReplace(0 0 0,indexed,loot)
                    return 7
                }
            }
        """.trimIndent()
        write("$librarySource\n${mainSource(true)}", output)
        fun check(main: Function, executionOutput: Path, flag: Boolean) {
            val observed = observe(main)
            assertTrue(observed.compiledFunctions.isEmpty())
            val functions = exported(executionOutput)
            val replacements = functions.values.flatten().filter { "run loot replace block 0 0 0 " in it }
            assertTrue(replacements.isNotEmpty(), "The published files must contain the native commands")
            assertFalse(replacements.any { "weapon_mainhand" in it || "EnumMember" in it || "JavaVar" in it })
            val cloneTrace = "native-clone: "
            val lootTrace = "native-loot: "
            val functionTrace = "native-function: "
            val lootCapture = Regex("""execute store result storage (\S+) (\S+) int 1 store success storage (\S+) (\S+) byte 1 run (loot replace block 0 0 0 .+)""")
            // Keep exported files intact. Observe world effects in an execution copy, after the
            // executor selects the real branch and substitutes the original macro arguments.
            val executable = functions.mapValues { (name, lines) ->
                listOf("say $functionTrace$name") + lines.flatMap { line ->
                    val prefix = if (line.startsWith("$")) "$" else ""
                    val command = line.removePrefix("$")
                    when {
                        command.startsWith("clone ") -> listOf("${prefix}say $cloneTrace$command")
                        "run loot replace block 0 0 0 " in command -> {
                            val capture = assertNotNull(lootCapture.matchEntire(command), command)
                            val parts = capture.groupValues
                            listOf(
                                "${prefix}say $lootTrace${parts[5]}",
                                // The external world result is a test input; compiler facts stay unknown.
                                "${prefix}data modify storage ${parts[1]} ${parts[2]} set value 1",
                                "${prefix}data modify storage ${parts[3]} ${parts[4]} set value 1b"
                            )
                        }
                        else -> listOf(line)
                    }
                }
            }
            val machine = ScoreCommandExecutor(executable.getValue(main.namespaceID.toString()), executable)
            val clones = machine.messages.filter { it.startsWith(cloneTrace) }.map { it.removePrefix(cloneTrace) }
            assertEquals(listOf(
                "clone 1 2 3 4 5 6 7 8 9 replace normal",
                "clone 1 2 3 10 11 12 7 8 9 replace normal",
                "clone 1 2 3 4 5 6 7 8 9 replace normal"
            ), clones)
            val loot = machine.messages.filter { it.startsWith(lootTrace) }.map { it.removePrefix(lootTrace) }
            assertEquals(listOf(
                "loot replace block 0 0 0 container.3 loot fixture:loot",
                "loot replace block 0 0 0 weapon.mainhand loot fixture:loot",
                "loot replace block 0 0 0 container.4 loot fixture:loot"
            ), loot)
            val executed = machine.messages.filter { it.startsWith(functionTrace) }.map { it.removePrefix(functionTrace) }.toSet()
            val selectedCommands = executed.flatMap { functions.getValue(it) }
            assertTrue(selectedCommands.any { "set from storage mcfpp:system stack_frame[0].index" in it })
            assertTrue(selectedCommands.any { "stack_frame[0].loot.id set from storage mcfpp:system stack_frame[0].id" in it })
            assertTrue(selectedCommands.any { "set from storage mcfpp:system stack_frame[0].loot" in it })
            val selectedModels = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .plus(observed).filter { it.namespaceID.toString() in executed }
            for (name in listOf("first", "second", "third")) {
                val locals = selectedModels.mapNotNull { it.scope.vars[name] }
                assertTrue(locals.isNotEmpty(), "Missing $name in selected continuation scopes")
                locals.forEach { assertNull(top.mcfpp.analysis.StorageAccess.snapshot(it), name) }
            }
            assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
            val messages = machine.messages.filterNot {
                it.startsWith(cloneTrace) || it.startsWith(lootTrace) || it.startsWith(functionTrace)
            }
            assertEquals(if (flag) listOf("constructed-area") else emptyList(), messages)
            assertEquals(0, machine.stackDepth)
        }
        check(sourceMain(), output.resolve("source-true"), true)
        Project.config.includes = arrayListOf()
        MCFPPStringTest.readFromString("$librarySource\n${mainSource(false)}", version = "26.3")
        assertEquals(0, Project.errorCount)
        check(sourceMain(), output.resolve("source-false"), false)
        for (flag in listOf(true, false)) {
            check(consume("import mcfpp.minecraft.other:*;\n${mainSource(flag)}", output), output.resolve("fresh-$flag"), flag)
        }
        fun reject(source: String, namespace: String) {
            val logger = assertIs<Logger>(LogProcessor.logger)
            val captured = arrayListOf<String>()
            val appender = object : AbstractAppender("area-flow-contract", null, null, false, Property.EMPTY_ARRAY) {
                override fun append(event: LogEvent) { captured.add(event.message.formattedMessage) }
            }
            appender.start()
            try {
                logger.addAppender(appender)
                MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
                val diagnostics = captured.joinToString("\n")
                assertEquals(1, Project.errorCount, diagnostics)
                assertTrue(diagnostics.contains("Cannot capture an incomplete compiler-only payload 'area'"), diagnostics)
                val main = GlobalScope.localNamespaces.getValue(namespace).scope.functions.getValue("main").single()
                val box = assertIs<DataTemplateObject>(main.scope.getVar("box"))
                val rejected = box.templateType.scope.functions.getValue("reject").single()
                assertTrue(rejected.compiledFunctions.isEmpty(), "The flag must remain an ordinary runtime parameter")
                val area = assertIs<DataTemplateObject>(rejected.scope.getVar("area"))
                assertFalse(area.isError, "The loop must retain a valid Area binding")
                assertNotNull(area.storageBinding)
                fun point(name: String): top.mcfpp.core.lang.Var<*> {
                    val field = assertNotNull(DataTemplate.getField(area, name))
                    return if (field is top.mcfpp.core.lang.PropertyVar) field.get() else field
                }
                assertNotNull(top.mcfpp.analysis.StorageAccess.snapshot(point("start")))
                assertNull(top.mcfpp.analysis.StorageAccess.snapshot(point("end")),
                    "The zero-iteration and break exits have different end coordinates")
                assertFalse(rejected.commands.any { it.toString().startsWith("clone ") || "run clone " in it.toString() })
            } finally {
                logger.removeAppender(appender)
                appender.stop()
            }
        }
        val rejectedMain = "func main(){var box=Box();box.reject(true);}"
        Project.config.includes = arrayListOf()
        reject("$librarySource\n$rejectedMain", "fixture.contracts")
        Project.config.includes = arrayListOf(output.toString())
        reject("import fixture.contracts:*;\n$rejectedMain", "default.test")
    }

    @Test fun nativeBossbarPropertiesPublishUnknownResultsAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = "func main(){var box=Box();external result=box.observe(\"fixture:bar\",11,true);}"
        write("""
            namespace fixture.contracts
            import mcfpp.minecraft.resource:*
            data Box {
                func observe(id: string,number: int,flag: bool):int {
                    var bar=BossBar(id)
                    bar.max=number;bar.value=number;bar.visible=flag
                    external maximum=bar.max
                    external current=bar.value
                    external visible=bar.visible
                    number+=1;flag=false
                    return number
                }
            }
            $mainSource
        """, output)
        fun check(main: Function, executionOutput: Path) {
            val observe = observe(main)
            assertTrue(observe.compiledFunctions.isEmpty())
            for (name in listOf("number", "flag")) {
                val parameter = assertNotNull(observe.scope.getVar(name))
                assertNull(parameter.parent, "A native setter must not attach $name to its receiver")
                assertEquals(name, parameter.identifier)
                assertEquals("stack_frame[0].$name", assertNotNull(parameter.storageBinding).path.pathToCommandPart().toString())
            }
            val results = listOf("maximum", "current", "visible").map { assertNotNull(observe.scope.getVar(it)) }
            assertIs<MCInt>(results[0]);assertIs<MCInt>(results[1]);assertIs<ScoreBool>(results[2])
            results.forEach {
                val binding = assertNotNull(it.storageBinding)
                assertEquals(top.mcfpp.analysis.ValueState.INITIALIZED, binding.data.facts.read(binding.place)?.state)
                assertNull(top.mcfpp.analysis.StorageAccess.snapshot(it))
            }
            val bar = assertIs<DataTemplateObject>(observe.scope.getVar("bar"))
            val published = listOf("max", "value", "visible").map { name ->
                assertIs<NativeAccessor>(bar.templateType.scope.getProperty(name)!!.accessor).function.returnVar
            }
            published.forEach {
                val binding = assertNotNull(it.storageBinding)
                assertEquals(top.mcfpp.analysis.ValueState.INITIALIZED, binding.data.facts.read(binding.place)?.state)
                assertNull(top.mcfpp.analysis.StorageAccess.snapshot(it))
            }
            assertEquals(results.map { it.type }, published.map { it.type })
            assertEquals(3, published.map { assertNotNull(it.storageBinding).place }.toSet().size)
            val functions = exported(executionOutput)
            val commands = functions.values.flatten().map { it.removePrefix("$") }
            for (attribute in listOf("max", "value", "visible")) {
                assertEquals(1, commands.count { Regex("execute store result score \\S+ \\S+ run bossbar get \\S+ $attribute").matches(it) })
            }
            for (attribute in listOf("max", "value")) {
                assertEquals(1, commands.count { Regex("execute store result bossbar \\S+ $attribute run scoreboard players get \\S+ \\S+").matches(it) })
            }
            assertEquals(2, commands.count { "run bossbar set " in it && " visible " in it })
            assertTrue(commands.any { "matches 1 run bossbar set " in it && it.endsWith(" visible true") })
            assertTrue(commands.any { "unless score " in it && it.endsWith(" visible false") })
            val body = functions.getValue(observe.namespaceID.toString())
            assertTrue(body.any { "set from storage mcfpp:system stack_frame[0].bar.id" in it })
        }
        check(sourceMain(), output.resolve("source"))
        check(consume(mainSource, output), output.resolve("fresh"))
    }

    private fun sourceMain() = GlobalScope.localNamespaces.getValue("fixture.contracts").scope.functions.getValue("main").single()
    private fun observe(main: Function) = (main.scope.getVar("box") as DataTemplateObject).templateType.scope.functions.getValue("observe").single()

    private fun write(source: String, output: Path) {
        Project.config.includes = arrayListOf()
        MCFPPStringTest.readFromString(source.trimIndent(), targetPath = output.toString(), version = "26.3")
        assertEquals(0, Project.errorCount)
    }

    private fun consume(source: String, output: Path): Function {
        Project.config.includes = arrayListOf(output.toString())
        MCFPPStringTest.readFromString("import fixture.contracts:*;\n$source", version = "26.3")
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun exported(output: Path): Map<String, List<String>> {
        val directory = output.resolve("consumer")
        DatapackCreator.createDatapack(directory.toString())
        val data = directory.resolve(Project.config.name).resolve("data")
        return linkedMapOf<String, List<String>>().also { functions ->
            Files.walk(data).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".mcfunction") }.forEach { file ->
                    val relative = data.relativize(file)
                    if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                        val name = relative.subpath(2, relative.nameCount).joinToString("/").removeSuffix(".mcfunction")
                        functions["${relative.getName(0)}:$name"] = Files.readAllLines(file).map(String::trim)
                    }
                }
            }
        }
    }

    private fun withLibrary(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-native-template-contract-")
        val config = Project.config
        val settings = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib,
            CompileSettings.printAll, CompileSettings.foldIRConstants)
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = false
            action(output)
        } finally {
            Project.config = config
            CompileSettings.isDebug = settings[0];CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2];CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            output.toFile().deleteRecursively()
        }
    }

    companion object {
        @JvmStatic @NoExternalWrites fun legacyHostCall() {
            error("An invalid host signature must be rejected before invocation")
        }

        @JvmStatic @NoExternalWrites fun missingResult(@Suppress("UNUSED_PARAMETER") context: NativeCallContext) {}

        @JvmStatic @NoExternalWrites fun failedResult(context: NativeCallContext) {
            context.publishResult(top.mcfpp.core.lang.MCInt(7))
            LogProcessor.error("Native operation rejected its input")
        }
    }
}

object NativeRegistrationContractHelper {
    @JvmStatic @MNIFunction fun legacyFree() {}
    @JvmStatic @NoExternalWrites @MNIFunction(returnType = "int")
    fun legacyResult(result: ValueWrapper<MCInt>) { result.set(MCInt(7)) }

    @JvmStatic @MNIFunction(normalParams = ["string"])
    fun wrongSignature(@Suppress("UNUSED_PARAMETER") value: String) {
        error("An invalid host signature must be rejected before invocation")
    }

    @JvmStatic @MNIOperator(operator = "!", returnType = "int")
    fun legacyUnary(@Suppress("UNUSED_PARAMETER") caller: Any?, @Suppress("UNUSED_PARAMETER") result: Any?) {
        error("An invalid host signature must be rejected before invocation")
    }

    @JvmStatic @MNIOperator(operator = "+", paramType = "int", returnType = "int")
    fun legacyBinary(@Suppress("UNUSED_PARAMETER") caller: Any?, @Suppress("UNUSED_PARAMETER") value: Any?,
                     @Suppress("UNUSED_PARAMETER") result: Any?) {
        error("An invalid host signature must be rejected before invocation")
    }

    @JvmStatic @MNIFunction fun valid(@Suppress("UNUSED_PARAMETER") context: NativeCallContext) {}
}
