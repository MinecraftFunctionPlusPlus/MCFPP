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
            assertEquals(errors + 4, Project.errorCount, diagnostics)
            assertEquals(4, Regex("must use NativeCallContext").findAll(diagnostics).count(), diagnostics)
            for (name in listOf("legacyFree", "wrongSignature", "legacyUnary", "legacyBinary")) {
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
            registrationMain.runInFunction { registered.invoke(emptyList(), null) }
            assertEquals(errors + 4, Project.errorCount)
            assertEquals(commands, registrationMain.commands)

            captured.clear()
            MCFPPStringTest.readFromString("""
                func rejected() = top.mcfpp.test.NativeRegistrationContractHelper.legacyFree;
                func accepted() = top.mcfpp.test.NativeRegistrationContractHelper.valid;
                func main(){}
            """.trimIndent())
            val declarationDiagnostics = captured.joinToString("\n")
            assertFalse(assertNotNull(MCFPPFile.currFile).syntaxError, declarationDiagnostics)
            assertEquals(1, Project.errorCount, declarationDiagnostics)
            val diagnostic = "Method legacyFree in class top.mcfpp.test.NativeRegistrationContractHelper must use NativeCallContext"
            assertEquals(1, Regex(Regex.escape(diagnostic)).findAll(declarationDiagnostics).count(), declarationDiagnostics)
            assertEquals(1, Regex("must use NativeCallContext").findAll(declarationDiagnostics).count(), declarationDiagnostics)
            val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
            assertFalse(functions.containsKey("rejected"))
            val accepted = assertIs<NativeFunction>(functions.getValue("accepted").single())
            assertTrue(java.lang.reflect.Modifier.isStatic(accepted.javaMethod.modifiers))
            assertContentEquals(arrayOf<Class<*>>(NativeCallContext::class.java), accepted.javaMethod.parameterTypes)
            assertEquals(Function.Companion.OwnerType.NONE, accepted.ownerType)
            assertEquals(top.mcfpp.type.MCFPPPrivateType.Void, accepted.returnType)
            assertTrue(accepted.readOnlyParams.isEmpty())
            assertTrue(accepted.normalParams.isEmpty())
            val acceptedMain = functions.getValue("main").single()
            val acceptedCommands = acceptedMain.commands.toList()
            acceptedMain.runInFunction { accepted.invoke(emptyList(), null) }
            assertEquals(1, Project.errorCount)
            assertEquals(acceptedCommands, acceptedMain.commands)
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
            import mcfpp.minecraft:*;import mcfpp.minecraft.other:*;import mcfpp.minecraft.std:*;
            func main(){var area as Area;var result=clone(area,0 0 0,CloneMaskMode.replace,CloneOperation.normal);}
        """, operation = "clone")
        reject("""
            import mcfpp.minecraft:*;import mcfpp.minecraft.other:*;import mcfpp.minecraft.std:*;
            func main(){var area=Area(^ ^ ^,1 2 3);var result=clone(area,0 0 0,CloneMaskMode.replace,CloneOperation.normal);}
        """, operation = "clone")
        reject("""
            import mcfpp.minecraft.other:*;import mcfpp.minecraft.resource:*;import mcfpp.minecraft.std:*;
            func main(){dynamic var index=3;var slot=Slot(index,SlotType.container);
                var loot=LootTable();loot.id="fixture:loot";var result=lootReplace(0 0 0,slot,loot);}
        """, version = "1.19.4", operation = "loot")
    }

    @Test fun predicateFactoryCopiesNormalIdAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = "func main(){var box=Box();dynamic var result=box.observe(\"fixture:original\");}"
        write("""
            namespace fixture.contracts;
            import mcfpp.minecraft.resource:*;
            data Box {
                func observe(id as string)->int {
                    var original=Predicate.of(id);
                    var copied=original;
                    id="fixture:changed";
                    /data modify storage fixture:observed original set from storage mcfpp:system stack_frame[0].original.id
                    /data modify storage fixture:observed copied set from storage mcfpp:system stack_frame[0].copied.id
                    return 7;
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
        val mainSource = "func main(){var box=Box();dynamic var result=box.observe(3,SlotType.container,\"fixture:loot\",true);}"
        write("""
            namespace fixture.contracts;
            import mcfpp.minecraft:*;
            import mcfpp.minecraft.other:*;
            import mcfpp.minecraft.resource:*;
            import mcfpp.minecraft.std:*;
            data ConditionalArea:Area {
                constructor(flag as bool) {
                    this.start=1 2 3;
                    this.end=4 5 6;
                    if(flag){
                        /say constructed-area
                    }
                }
            }
            data DifferentArea:Area {
                constructor(flag as bool) {
                    this.start=1 2 3;
                    if(flag){this.end=4 5 6;}
                    else{this.end=10 11 12;}
                }
            }
            data Box {
                func reject(flag as bool) {
                    var area=DifferentArea(flag);
                    clone(area,7 8 9,CloneMaskMode.replace,CloneOperation.normal);
                }
                func observe(index as int,kind as SlotType,id as string,flag as bool)->int {
                    var area=Area(1 2 3,4 5 6);
                    var areaCopy=area;
                    area.end=10 11 12;
                    clone(areaCopy,7 8 9,CloneMaskMode.replace,CloneOperation.normal);
                    clone(area,7 8 9,CloneMaskMode.replace,CloneOperation.normal);
                    var conditional=ConditionalArea(flag);
                    clone(conditional,7 8 9,CloneMaskMode.replace,CloneOperation.normal);
                    var indexed=Slot(index,kind);
                    var copied=indexed;
                    indexed.index=index+1;
                    var unindexed=Slot(SlotType.weapon_mainhand);
                    var loot=LootTable();loot.id=id;
                    var first=lootReplace(0 0 0,copied,loot);
                    var second=lootReplace(0 0 0,unindexed,loot);
                    var third=lootReplace(0 0 0,indexed,loot);
                    return 7;
                }
            }
            $mainSource
        """, output)
        fun check(main: Function, executionOutput: Path) {
            assertTrue(observe(main).compiledFunctions.isEmpty())
            val functions = exported(executionOutput)
            val commands = functions.values.flatten()
            assertEquals(2, commands.count { it == "clone 1 2 3 4 5 6 7 8 9 replace normal" })
            assertEquals(1, commands.count { it == "clone 1 2 3 10 11 12 7 8 9 replace normal" })
            val replacements = commands.filter { "run loot replace block 0 0 0 " in it }
            assertEquals(3, replacements.size)
            assertFalse(replacements.any { "weapon_mainhand" in it || "EnumMember" in it || "JavaVar" in it })
            val body = functions.getValue(observe(main).namespaceID.toString())
            assertTrue(body.any { "set from storage mcfpp:system stack_frame[0].index" in it })
            assertTrue(body.any { "stack_frame[0].loot.id set from storage mcfpp:system stack_frame[0].id" in it })
            assertTrue(body.any { "set from storage mcfpp:system stack_frame[0].loot" in it })
            for (name in listOf("first", "second", "third")) {
                assertNull(top.mcfpp.analysis.StorageAccess.snapshot(assertNotNull(observe(main).scope.getVar(name))))
            }
            val worldFunctions = functions.filterValues { lines -> lines.any { "run loot replace block 0 0 0 " in it } }.keys
            val preparations = functions.mapValues { (name, lines) ->
                if (name in worldFunctions) emptyList() else lines.filterNot { it.startsWith("clone ") }
            }
            val machine = ScoreCommandExecutor(preparations.getValue(main.namespaceID.toString()), preparations)
            val calls = body.filter { line -> worldFunctions.any { line.startsWith("function $it with storage ") } }
            assertEquals(3, calls.size)
            val slots = calls.map { call ->
                val name = call.substringAfter("function ").substringBefore(" with ")
                val world = functions.getValue(name).single()
                val slot = Regex("""run loot replace block 0 0 0 (\S+) loot""").find(world)!!.groupValues[1]
                val path = call.substringAfter(" with storage mcfpp:system ")
                val parameter = Regex("""\$\((arg_\d+)\)""").matchEntire(slot)?.groupValues?.get(1)
                val lootParameter = Regex("""loot \$\((arg_\d+)\)""").find(world)!!.groupValues[1]
                assertEquals("fixture:loot", assertIs<StringTag>(machine.readNbt("mcfpp:system", "$path.$lootParameter")).value)
                if (parameter == null) slot else assertIs<StringTag>(machine.readNbt("mcfpp:system", "$path.$parameter")).value
            }
            assertEquals(listOf("container.3", "weapon.mainhand", "container.4"), slots)
            assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(listOf("constructed-area"), machine.messages)
            assertEquals(0, machine.stackDepth)
        }
        check(sourceMain(), output.resolve("source"))
        check(consume("import mcfpp.minecraft.other:*;\n$mainSource", output), output.resolve("fresh"))
        Project.config.includes = arrayListOf(output.toString())
        MCFPPStringTest.readFromString("""
            import fixture.contracts:*;
            func main(){var box=Box();box.reject(true);}
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0, "Different coordinates on unknown constructor paths require a capability diagnostic")
    }

    @Test fun nativeBossbarPropertiesPublishUnknownResultsAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = "func main(){var box=Box();dynamic var result=box.observe(\"fixture:bar\",11,true);}"
        write("""
            namespace fixture.contracts;
            import mcfpp.minecraft.resource:*;
            data Box {
                func observe(id as string,number as int,flag as bool)->int {
                    var bar=BossBar(id);
                    bar.max=number;bar.value=number;bar.visible=flag;
                    dynamic var maximum=bar.max;
                    dynamic var current=bar.value;
                    dynamic var visible=bar.visible;
                    number+=1;flag=false;
                    return number;
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
    @JvmStatic @MNIFunction fun legacyFree() {
        error("An invalid host signature must be rejected before invocation")
    }

    @JvmStatic @MNIFunction(normalParams = ["string value"])
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
