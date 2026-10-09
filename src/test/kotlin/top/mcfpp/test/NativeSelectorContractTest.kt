package top.mcfpp.test

import com.mojang.brigadier.StringReader
import kotlin.test.Test
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.CompileSettings
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.mni.annotation.WritesReceiver
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EntitySelector
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.SelectorData
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.exception.CommandException
import top.mcfpp.lib.NBTPath
import top.mcfpp.nbt.tags.CompoundTag
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class NativeSelectorContractTest {
    @Test
    fun completeSelectorSnapshotsFreezeOperandsAndPreserveDuplicateChecks() = withLibrary {
        MCFPPStringTest.readFromString("func main() {}", version = "26.3")
        val operand = top.mcfpp.core.lang.MCInt("operand").apply {
            bindDeclaration()
            StorageAccess.write(this, top.mcfpp.core.lang.MCInt(4))
        }
        val original = SelectorVar(EntitySelector('e').x(operand).tag("saved", false).limit(1))
        val frozen = assertNotNull(top.mcfpp.analysis.StorageAccess.snapshot(original))
        val type = original.type
        StorageAccess.write(operand, top.mcfpp.core.lang.MCInt(99))
        val restored = StorageAccess.restore(type, frozen, "restored") as SelectorVar
        assertEquals("@e[x=4,tag=saved,limit=1]", restored.toCommandPart().toString())
        val copy = restored.clone()
        assertNotSame(restored.value, copy.value)
        val errors = Project.errorCount
        copy.value.x(8)
        assertEquals(errors + 1, Project.errorCount)
        assertEquals(3, copy.value.predicates.size)
        assertTrue(copy.value.selectingSingleEntity())
        assertEquals(frozen, top.mcfpp.analysis.StorageAccess.snapshot(restored))
        val named = SelectorVar(EntitySelector('e').name("quote \" slash \\", false))
        val namedSnapshot = assertNotNull(top.mcfpp.analysis.StorageAccess.snapshot(named))
        val namedCopy = StorageAccess.restore(named.type, namedSnapshot, "namedCopy") as SelectorVar
        assertEquals("""@e[name="quote \" slash \\"]""", namedCopy.toCommandPart().toString())
        val nameReader = StringReader(namedCopy.toCommandPart().toString().substringAfter("name=").dropLast(1))
        assertEquals("quote \" slash \\", nameReader.readString())
        assertFalse(nameReader.canRead())
        val wordReader = StringReader("savedword")
        assertEquals("savedword", wordReader.readUnquotedString())
        assertFalse(wordReader.canRead())
    }

    @Test
    fun allSelectorFiltersCaptureNormalOperandsAcrossLibraryRoundTrip() = withLibrary { output ->
        val resources = output.resolve("resources")
        val entities = output.resolve("entities")
        val library = output.resolve("selectors")
        Project.config.includes = arrayListOf()
        MCFPPStringTest.readFromString("""
            namespace mcfpp.minecraft.resource;
            data ResourceID { id as string; }
            data EntityType: ResourceID {}
            data Predicate: ResourceID {}
            data Advancement: ResourceID {}
        """.trimIndent(), targetPath = resources.toString(), version = "26.3")
        assertEquals(0, Project.errorCount)
        Project.config.includes = arrayListOf(resources.toString())
        MCFPPStringTest.readFromString("""
            namespace mcfpp.minecraft.entity;
            @From<"top.mcfpp.mni.SelectorData">
            data EntityData {}
        """.trimIndent(), targetPath = entities.toString(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val source = """
            namespace fixture.selectors;
            import mcfpp.minecraft.resource:*;
            data Box {
                func observe(number as int, label as string, word as string, payload as nbt, bounds as range, mode as string, order as string)->int {
                    var kind = EntityType(); kind.id = label;
                    var predicate = Predicate(); predicate.id = label;
                    var advancement = Advancement(); advancement.id = label;
                    var target = @e;
                    target.x(number); target.y(number); target.z(number);
                    target.dx(number); target.dy(number); target.dz(number);
                    target.distance(bounds); target.xRotation(bounds); target.yRotation(bounds); target.level(bounds);
                    target.tag(word); target.tagNot(word);
                    target.teamNot(word); target.team(word);
                    target.nameNot(label); target.name(label);
                    target.typeNot(kind); target.type(kind);
                    target.predicate(predicate); target.predicateNot(predicate);
                    target.filterNbt(payload);
                    target.gamemodeNot(mode); target.gamemode(mode);
                    target.advancements(advancement); target.advancementsNot(advancement);
                    target.limit(number); target.sort(order);
                    var copied = target;
                    number = 99; label = "changed"; word = "changed"; kind.id = "changed"; mode = "creative"; order = "arbitrary";
                    target.tag("later");
                    /say ${'$'}{target}
                    /say ${'$'}{copied}
                    return 7;
                }
                func recurse(depth as int, label as string)->int {
                    var selected = @e;
                    selected.tag(label);
                    if (depth > 0) { this.recurse(depth - 1, "inner"); }
                    label = "changed";
                    /say ${'$'}{selected}
                    return 7;
                }
            }
            func main() {
                var box = Box();
                dynamic var result = box.observe(4, "fixture:original", "savedword", {value:7} as nbt, 1..3, "survival", "nearest");
                dynamic var openResult = box.observe(4, "fixture:original", "savedword", {value:7} as nbt, 1.., "survival", "nearest");
            }
        """
        Project.config.includes = arrayListOf(resources.toString(), entities.toString())
        MCFPPStringTest.readFromString(source.trimIndent(), targetPath = library.toString(), version = "26.3")
        assertEquals(0, Project.errorCount)
        checkExport(output.resolve("source"))
        Project.config.includes = arrayListOf(resources.toString(), entities.toString(), library.toString())
        MCFPPStringTest.readFromString("""
            import fixture.selectors:*;
            func main() {
                var box = Box();
                dynamic var result = box.observe(4, "fixture:original", "savedword", {value:7} as nbt, 1..3, "survival", "nearest");
                dynamic var openResult = box.observe(4, "fixture:original", "savedword", {value:7} as nbt, 1.., "survival", "nearest");
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        checkExport(output.resolve("fresh"))
        MCFPPStringTest.readFromString("""
            import fixture.selectors:*;
            func main() {
                var box = Box();
                dynamic var result = box.recurse(1, "outer");
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        checkRecursiveCapture(output.resolve("recursive"))
        val methods = SelectorData::class.java.declaredMethods.filter { it.isAnnotationPresent(MNIFunction::class.java) }
        assertEquals(27, methods.size)
        assertTrue(methods.any { it.name == "filterNbt" })
        assertFalse(methods.any { it.name == "nbt" })
        methods.forEach {
            assertContentEquals(arrayOf(NativeCallContext::class.java), it.parameterTypes)
            assertTrue(it.isAnnotationPresent(WritesReceiver::class.java), it.name)
            assertEquals("entity", it.getAnnotation(MNIFunction::class.java).caller)
        }
        MCFPPStringTest.readFromString("""
            import mcfpp.minecraft.entity:*;
            func reject() {
                var original = @e;
                original.x(1);
                var copied = original;
                copied.x(2);
                var dto = EntityData();
                dto.x(1);
                original.tag("bad:tag");
                original.team("bad team");
            }
            func main() {}
        """.trimIndent(), version = "26.3")
        assertEquals(4, Project.errorCount)
        val rejected = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("reject").single()
        assertTrue(rejected.bodyCompiled)
        assertTrue(rejected.commands.none { it.toString().contains("say ") })
    }

    private fun checkExport(output: Path) {
        val namespace = GlobalScope.getUnsolvedImportNamespace("fixture.selectors")!!
        val box = namespace.scope.getTemplate("Box")!!
        val observe = box.scope.functions.getValue("observe").single()
        assertTrue(observe.compiledFunctions.isEmpty())
        val target = StorageAccess.read(assertNotNull(observe.scope.getVar("target"))) as SelectorVar
        val copied = StorageAccess.read(assertNotNull(observe.scope.getVar("copied"))) as SelectorVar
        assertNull(top.mcfpp.analysis.StorageAccess.snapshot(target))
        assertNull(top.mcfpp.analysis.StorageAccess.snapshot(copied))
        assertNotSame(target.value, copied.value)
        assertNotEquals(target.storageBinding?.place, copied.storageBinding?.place)
        assertEquals(28, target.value.predicates.size)
        assertEquals(27, copied.value.predicates.size)
        val functions = exportedFunctions(output)
        val commands = functions.getValue(observe.namespaceID.toString())
        val selectorFunctions = functions.filter { (id, body) ->
            body.any { it.startsWith("$" + "say @e[") } && commands.any { it.contains("function $id with storage ") }
        }
        val selectorBodies = selectorFunctions.values.flatten().filter { it.startsWith("$" + "say @e[") }
        assertEquals(2, selectorBodies.size)
        selectorFunctions.forEach { (id, body) ->
            val calls = commands.withIndex().filter { it.value.contains("function $id with storage ") }
            assertEquals(1, calls.size, id)
            val call = calls.single()
            val argumentPath = call.value.substringAfter(" with storage mcfpp:system ")
            val slots = Regex("\\$\\((arg_\\d+)\\)").findAll(body.single { it.startsWith("$" + "say @e[") })
                .map { it.groupValues[1] }.toSet()
            assertTrue(slots.isNotEmpty())
            slots.forEach { slot ->
                val prefix = "data modify storage mcfpp:system $argumentPath.$slot set from storage mcfpp:system "
                val preparations = commands.withIndex().filter { it.value.startsWith(prefix) }
                assertEquals(1, preparations.size, "$id/$slot")
                val preparation = preparations.single()
                assertTrue(preparation.index < call.index)
                val captured = preparation.value.removePrefix(prefix)
                val labelPath = assertNotNull(observe.scope.getVar("label")).nbtPath.pathToCommandPart().toString()
                assertNotEquals(labelPath, captured, "A filter must not read the subsequently overwritten parameter")
                assertTrue(captured.startsWith("stack_frame[0]."), captured)
            }
        }
        selectorBodies.forEach { body ->
            listOf("x=", "y=", "z=", "dx=", "dy=", "dz=", "distance=", "x_rotation=", "y_rotation=",
                "level=", "tag=", "tag=!", "team=", "team=!", "name=", "name=!", "type=", "type=!",
                "predicate=", "predicate=!", "nbt=", "gamemode=", "gamemode=!", "advancements={",
                "limit=", "sort=").forEach { assertTrue(body.contains(it), "$it in $body") }
            assertFalse(body.contains("changed"))
        }
        assertTrue(commands.any { it.startsWith("execute if data storage mcfpp:system stack_frame[0].") && it.contains(".left run data modify") })
        assertTrue(commands.any { it.startsWith("execute if data storage mcfpp:system stack_frame[0].") && it.contains(".right run data modify") })
        assertTrue(commands.any { it.contains("set value \"\"") })
    }

    private fun checkRecursiveCapture(output: Path) {
        val entry = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val box = (assertNotNull(entry.scope.getVar("box")) as DataTemplateObject).templateType
        val canonical = assertNotNull(GlobalScope.libNamespaces.getValue("fixture.selectors").scope.getTemplate("Box"))
        assertSame(canonical, box)
        val recursive = box.scope.functions.getValue("recurse").single()
        val functions = exportedFunctions(output)
        val commands = functions.getValue(recursive.namespaceID.toString())
        // The legacy if lowering puts the continuation in each actual branch helper.
        val helpers = commands.mapNotNull { Regex("(?:^| run )function (\\S+)").find(it)?.groupValues?.get(1) }
            .filter { it != recursive.namespaceID.toString() }
        assertEquals(2, helpers.size, commands.joinToString("\n"))
        val macros = helpers.map { helper ->
            val branch = functions.getValue(helper)
            val call = branch.single { it.contains("function mcfpp:dynamic/") }
            val id = call.substringAfter("function ").substringBefore(" with ")
            val body = functions.getValue(id).single()
            assertTrue(Regex("""\${'$'}say\s+@e\[tag=\${'$'}\(arg_0\)]\s*""").matches(body), "$id: $body")
            Triple(id, call.substringAfter(" with storage mcfpp:system "), branch.any { it == "function ${recursive.namespaceID}" })
        }
        val outer = macros.single { it.third }
        val inner = macros.single { !it.third }
        // Run compiler-generated preparation and call frames; the two world-facing say calls are omitted.
        val preparationFunctions = functions.mapValues { (id, body) -> if (macros.any { it.first == id }) emptyList() else body }
        val machine = ScoreCommandExecutor(preparationFunctions.getValue(entry.namespaceID.toString()), preparationFunctions)
        assertEquals("inner", (machine.readNbt("mcfpp:system", "${inner.second}.arg_0") as StringTag).value)
        assertEquals("outer", (machine.readNbt("mcfpp:system", "${outer.second}.arg_0") as StringTag).value)
        assertEquals(0, machine.stackDepth)
        checkCallerFrameCapture(output)
    }

    private fun checkCallerFrameCapture(output: Path) {
        val sink = Function("selector_frame_observer", "fixture.selectors", null)
        sink.runInFunction {
            Function.addCommand(Commands.stackIn())
            val input = MCString("frame_input")
            StorageAccess.bindIncomingParameter(input)
            Function.addCommand(Commands.dataSetValue(input.nbtPath, StringTag("outer_word")))
            val selected = SelectorVar(EntitySelector('e'))
            val tag = selected.getMemberFunction("tag", emptyList(), listOf(input), Member.AccessModifier.PUBLIC).first as NativeFunction
            val outer = tag.invoke(emptyList(), listOf(input), selected) as SelectorVar
            val cached = StorageAccess.read(outer) as SelectorVar
            Function.addCommand(Commands.stackIn())
            Function.addCommand(Commands.dataSetValue(input.nbtPath, StringTag("inner_word")))
            val caller = StorageAccess.callerValue(cached) as SelectorVar
            val nested = tag.invoke(emptyList(), listOf(MCString(StringTag("extra"))), caller) as SelectorVar
            assertSame(cached.storageBinding!!.data, nested.storageBinding!!.data)
            assertTrue(nested.value.predicates.first().v.nbtPath.pathToCommandPart().toString().startsWith("stack_frame[1]."))
            Function.addCommands(Command.buildAll("say", nested).buildMacroFunction())
            Function.addCommand(Commands.stackOut())
            Function.addCommand(Commands.stackOut())
        }
        val functions = exportedFunctions(output)
        val commands = sink.commands.map { it.toString() }
        val call = commands.single { it.contains("function mcfpp:dynamic/") }
        val id = call.substringAfter("function ").substringBefore(" with ")
        val path = call.substringAfter(" with storage mcfpp:system ")
        val prepared = ScoreCommandExecutor(commands, functions + (id to emptyList()))
        assertEquals("outer_word", (prepared.readNbt("mcfpp:system", "$path.arg_0") as StringTag).value)
        assertEquals(0, prepared.stackDepth)
    }

    private fun exportedFunctions(output: Path): Map<String, List<String>> {
        DatapackCreator.createDatapack(output.toString())
        val data = output.resolve(Project.config.name).resolve("data")
        val functions = linkedMapOf<String, List<String>>()
        Files.walk(data).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
                val relative = data.relativize(file)
                if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                    val name = relative.subpath(2, relative.nameCount).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")
                    functions["${relative.getName(0)}:$name"] = Files.readAllLines(file)
                }
            }
        }
        return functions
    }

    @Test
    fun dynamicSelectorStringsGateActualConsumersAndBothMacroLoweringEntrypoints() = withLibrary { output ->
        val resources=output.resolve("guard-resources")
        val entities=output.resolve("guard-entities")
        val library=output.resolve("guard-library")
        Project.config.includes=arrayListOf()
        MCFPPStringTest.readFromString("""
            namespace mcfpp.minecraft.resource;
            data ResourceID {id as string;}
            data EntityType:ResourceID {}
            data Predicate:ResourceID {}
            data Advancement:ResourceID {}
        """.trimIndent(),targetPath=resources.toString(),version="26.3")
        assertEquals(0,Project.errorCount)
        Project.config.includes=arrayListOf(resources.toString())
        MCFPPStringTest.readFromString("""
            namespace mcfpp.minecraft.entity;
            @From<"top.mcfpp.mni.SelectorData">
            data EntityData {}
        """.trimIndent(),targetPath=entities.toString(),version="26.3")
        assertEquals(0,Project.errorCount)
        val source="""
            namespace fixture.guards;
            data Box {
                func name(input as string)->int {var selected=@e;selected.name(input);input="changed";
                    /say ${'$'}{selected}
                    return 7;
                }
                func tag(input as string)->int {var selected=@e;selected.tag(input);input="changed";
                    /say ${'$'}{selected}
                    return 7;
                }
                func team(input as string)->int {var selected=@e;selected.team(input);input="changed";
                    /say ${'$'}{selected}
                    return 7;
                }
            }
            func known(){var selected=@e;selected.name("quote \" slash \\");
                /say ${'$'}{selected}
            }
            func main(){var box=Box();dynamic var result=box.name("initial");dynamic var tagResult=box.tag("initial");dynamic var teamResult=box.team("initial");known();}
        """.trimIndent()
        for(fresh in listOf(false,true)) {
            Project.config.includes=if(fresh) arrayListOf(resources.toString(),entities.toString(),library.toString()) else arrayListOf(resources.toString(),entities.toString())
            MCFPPStringTest.readFromString(if(fresh) "import fixture.guards:*;\nfunc main(){var box=Box();dynamic var result=box.name(\"initial\");dynamic var tagResult=box.tag(\"initial\");dynamic var teamResult=box.team(\"initial\");known();}" else source,
                targetPath=if(fresh)null else library.toString(),version="26.3")
            assertEquals(0,Project.errorCount)
            val main=GlobalScope.localNamespaces.values.flatMap {it.scope.functions.values.flatten()}.single {it.identifier=="main"}
            val box=main.scope.getVar("box") as DataTemplateObject
            val methods=listOf("name","tag","team").associateWith { name -> box.templateType.scope.functions.getValue(name).single() }
            val inputBindings=methods.mapValues { (_,method) -> assertNotNull(method.scope.getVar("input")?.storageBinding) }
            val functions=exportedFunctions(output.resolve("guard-$fresh"))
            val known=(if(fresh) GlobalScope.libNamespaces else GlobalScope.localNamespaces).getValue("fixture.guards").scope.functions.getValue("known").single()
            val knownMachine=ScoreCommandExecutor(listOf(Commands.stackIn().analyze())+functions.getValue(known.namespaceID.toString()),functions)
            val knownReader=StringReader(knownMachine.messages.single().substringAfter("name=").dropLast(1))
            assertEquals("quote \" slash \\",knownReader.readString());assertFalse(knownReader.canRead())
            for((name,method) in methods) {
                val values=if(name=="name") listOf("","space: colon","quote\"","slash\\","line\n","line\r")
                    else listOf("","word_+-.9","bad:word","bad word","quote\"","slash\\","line\n","line\r")
                for(value in values) {
                    val valid=if(name=="name") value.none {it in "\"\\\r\n"} else value.all {it.isLetterOrDigit()&&it.code<128 || it in "_.+-"}
                    val binding=inputBindings.getValue(name)
                    val prepare=Commands.dataSetValue(binding.path,StringTag(value)).analyze()
                    val machine=ScoreCommandExecutor(listOf(Commands.stackIn().analyze(),prepare)+functions.getValue(method.namespaceID.toString()),functions)
                    if(valid) {
                        val selected=machine.messages.single()
                        assertEquals(if(name=="name") "@e[name=\"$value\"]" else "@e[$name=$value]",selected)
                        if(name=="name") {
                            val reader=StringReader(selected.substringAfter("name=").dropLast(1))
                            assertEquals(value,reader.readString());assertFalse(reader.canRead())
                        } else {
                            val reader=StringReader(selected.substringAfter("$name=").dropLast(1))
                            assertEquals(value,reader.readUnquotedString());assertFalse(reader.canRead())
                        }
                    } else assertEquals(listOf("Invalid runtime selector ${if(name=="name")"name" else "tag/team word"}"),machine.messages)
                }
            }
            // The manual macro entrypoint validates the exact argument compound supplied by its caller.
            val sink=Function("manual_guard","fixture.guards",context=null)
            Function.currFunction=sink
            val input=MCString("incoming")
            val binding=StorageAccess.bindIncomingParameter(input)
            val selector=SelectorVar(EntitySelector('e').name(input,false))
            val selected=StorageAccess.read(selector) as SelectorVar
            val operand=selected.value.predicates.single().v
            val command=Command("say").build(selected.value.toCommandPart())
            assertFailsWith<CommandException> {command.analyze()}
            val args=NBTPath.stack.intIndex(0).memberIndex("manualArguments")
            Function.addCommand(Commands.dataSetValue(args,CompoundTag()))
            Function.addCommand(Commands.dataSetFrom(args.memberIndex(operand.identifier),operand.nbtPath))
            Function.addCommand(command.buildMacroFunction(args))
            val lowered=sink.commands.flatMap {it.analyze().lines()}
            val manualFunctions=exportedFunctions(output.resolve("manual-$fresh"))
            for(value in listOf("", "legal: name", "bad\"", "bad\\", "bad\n", "bad\r")) {
                val machine=ScoreCommandExecutor(listOf(Commands.stackIn().analyze(),Commands.dataSetValue(binding.path,StringTag(value)).analyze())+lowered,manualFunctions)
                assertEquals(if(value.none {it in "\"\\\r\n"}) listOf("@e[name=\"$value\"]") else listOf("Invalid runtime selector name"),machine.messages)
            }
        }
        for(value in listOf("line\n","line\r")) {
            val sink=Function("line_break_rejection","fixture.guards",context=null)
            Function.currFunction=sink
            val target=SelectorVar(EntitySelector('e'))
            val context=NativeCallContext(sink,target,listOf(MCString(StringTag(value))),target.type)
            val errors=Project.errorCount
            SelectorData.name(context)
            assertEquals(errors+1,Project.errorCount)
            assertNull(context.result)
            assertTrue(target.value.predicates.isEmpty())
            assertTrue(sink.commands.none {it.toString().contains("say ")})
        }
        Project.config.includes=arrayListOf(resources.toString(),entities.toString(),library.toString())
        MCFPPStringTest.readFromString("""
            func main(){var target=@e;target.name("line\n");target.name("line\r");}
        """.trimIndent(),version="26.3")
        // These escape spellings are rejected by source syntax, independently of the native guard above.
        assertTrue(Project.errorCount>0)
        assertTrue(Project.macroFunction.values.none {it.startsWith("${'$'}say")})
    }

    private fun withLibrary(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-selector-contract-")
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            action(output)
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignore
            output.toFile().deleteRecursively()
        }
    }
}
