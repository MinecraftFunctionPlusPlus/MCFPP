package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.LibBinFormat
import top.mcfpp.io.KryoManager
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.info.NativeFunctionInfo
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.UnsolvedTemplate
import top.mcfpp.model.compound.UnsolvedObjectTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class TemplateInitializationTest {
    @Test fun cyclicTemplateReferencesSurviveActualKryoRoundTrip() {
        fun roundTrip(value: Any): Any {
            val bytes = com.esotericsoftware.kryo.io.Output(4096, -1).use {
                KryoManager.kryo.writeClassAndObject(it, value)
                it.toBytes()
            }
            return com.esotericsoftware.kryo.io.Input(bytes).use { KryoManager.kryo.readClassAndObject(it) }
        }
        for (template in listOf(DataTemplate("Receiver", "fixture.cycles"), ObjectDataTemplate("ObjectReceiver", "fixture.cycles"))) {
            val type = template.getType()
            template.scope.addFunction(NativeFunction("receiver", "fixture.cycles").apply { caller = type }, true)
            val restored = roundTrip(type) as MCFPPDataTemplateType
            val info = when (val declaration = restored.template) {
                is UnsolvedObjectTemplate -> declaration.info
                is UnsolvedTemplate -> declaration.info
                else -> error("Expected unresolved declaration")
            }
            assertSame(restored, info.field.functions.filterIsInstance<NativeFunctionInfo>().single().caller)
        }
        val declaration = DataTemplate("FieldOwner", "fixture.cycles")
        declaration.scope.addFunction(NativeFunction("receiver", "fixture.cycles").apply { caller = declaration.getType() }, true)
        val restored = roundTrip(declaration) as UnsolvedTemplate
        val caller = restored.info.field.functions.filterIsInstance<NativeFunctionInfo>().single().caller as MCFPPDataTemplateType
        assertSame(restored.info, (caller.template as UnsolvedTemplate).info)
    }

    private fun withLibrary(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-template-library-")
        val config = Project.config
        val settings = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib,
            CompileSettings.printAll, CompileSettings.foldIRConstants)
        val maxInline = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = false
            action(output)
        } finally {
            Project.config = config
            CompileSettings.isDebug = settings[0]
            CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = maxInline
            output.toFile().deleteRecursively()
        }
    }

    private fun write(source: String, output: Path, includes: List<Path> = emptyList()) {
        Project.config.includes = ArrayList(includes.map(Path::toString))
        MCFPPStringTest.readFromString(source.trimIndent(), targetPath = output.toString(), version = "26.3")
        assertEquals(0, Project.errorCount)
        assertTrue(Files.exists(output.resolve("bin.mclib")))
        com.esotericsoftware.kryo.io.Input(Files.newInputStream(output.resolve("bin.mclib"))).use {
            assertEquals(LibBinFormat.MAGIC, it.readInt())
            assertEquals(LibBinFormat.VERSION, it.readInt())
        }
    }

    private fun consume(source: String, output: Path, includes: List<Path> = listOf(output)): Function {
        Project.config.includes = ArrayList(includes.map(Path::toString))
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(main: Function, output: Path): ScoreCommandExecutor {
        val consumer = output.resolve("consumer")
        DatapackCreator.createDatapack(consumer.toString())
        val data = consumer.resolve(Project.config.name).resolve("data")
        val functions = LinkedHashMap<String, List<String>>()
        Files.walk(data).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".mcfunction") }.forEach { file ->
                val relative = data.relativize(file)
                if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                    val identifier = relative.subpath(2, relative.nameCount).joinToString("/") { it.toString() }
                        .removeSuffix(".mcfunction")
                    functions["${relative.getName(0)}:$identifier"] = Files.readAllLines(file)
                }
            }
        }
        return ScoreCommandExecutor(functions.getValue(main.namespaceID.toString()), functions).also { assertEquals(0, it.stackDepth) }
    }

    @Test fun implicitAndExplicitInitializersRoundTripInDeclarationOrderAndRunForEveryInstance() {
        for (constructor in listOf("", "constructor(){}")) withLibrary { output ->
            write("""
                namespace fixture.defaults;
                data Box {
                    z as int = 4;
                    a as int = this.z + 1;
                    $constructor
                }
                func main(){}
            """, output)
            val original = GlobalScope.getTemplate("fixture.defaults", "Box")!!
            val main = consume("""
                import fixture.defaults:*;
                func main(){
                    var first = Box();
                    first.z = 9;
                    var second = Box();
                    dynamic var z = second.z;
                    dynamic var a = second.a;
                    dynamic var firstZ = first.z;
                }
            """, output)
            val restored = GlobalScope.getTemplate("fixture.defaults", "Box")!!
            assertNotSame(original, restored)
            assertEquals(listOf("z", "a"), restored.preInit.keys.toList())
            if (constructor.isEmpty()) assertNull(restored.constructors.single().ast)
            val machine = execute(main, output)
            assertEquals(4, machine.read(main.scope.getVar("z") as MCInt))
            assertEquals(5, machine.read(main.scope.getVar("a") as MCInt))
            assertEquals(9, machine.read(main.scope.getVar("firstZ") as MCInt))
        }
    }

    @Test fun restoredInitializersAndFreeFunctionsKeepTheirDeclarationNamespace() = withLibrary { output ->
        write("""
            namespace fixture.defaults;
            func seed() -> int { return 4; }
            func produce() -> int { return seed(); }
            data Box { const value as int = produce(); }
            func main(){}
        """, output)
        val original = GlobalScope.getTemplate("fixture.defaults", "Box")!!
        val main = consume("""
            import fixture.defaults:*;
            func seed() -> int { return 9; }
            func main(){
                var box = Box();
                dynamic var result = box.value * 10 + produce();
            }
        """, output)
        val restored = GlobalScope.getTemplate("fixture.defaults", "Box")!!
        assertNotSame(original, restored)
        assertNotNull(GlobalScope.libNamespaces.getValue("fixture.defaults").scope.functions.getValue("produce").single().ast)
        assertEquals(44, execute(main, output).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun restoredGenericSpecializationsKeepTheDeclarationFileForRuntimeArguments() = withLibrary { output ->
        write("""
            namespace fixture.defaults;
            func seed() -> int { return 4; }
            func add<T as type>(value as int) -> int { return seed() + value; }
            func main(){}
        """, output)
        val original = GlobalScope.localNamespaces.getValue("fixture.defaults").scope.functions.getValue("add").single()
        val main = consume("""
            import fixture.defaults:*;
            func seed() -> int { return 9; }
            func main(){
                dynamic var first = add<int>(1);
                dynamic var second = add<int>(2);
            }
        """, output)
        val restored = GlobalScope.libNamespaces.getValue("fixture.defaults").scope.functions.getValue("add").single()
        assertNotSame(original, restored)
        assertNotNull(restored.ast)
        assertEquals(1, restored.compiledFunctions.size)
        val machine = execute(main, output)
        val wrapper = restored.compiledFunctions.values.single()
        val functionDirectory = output.resolve("consumer").resolve(Project.config.name).resolve("data")
            .resolve(wrapper.namespaceID.namespace).resolve("function")
        val wrapperFile = functionDirectory.resolve("${wrapper.namespaceID.identifier}.mcfunction")
        assertTrue(Files.exists(wrapperFile), wrapperFile.toString())
        assertTrue(Files.readAllLines(wrapperFile).any { it.isNotBlank() && !it.trimStart().startsWith("#") })
        assertFalse(Files.exists(functionDirectory.resolve("${restored.namespaceID.identifier}.mcfunction")),
            "An uncompiled generic prototype must not be exported")
        assertEquals(5, machine.read(main.scope.getVar("first") as MCInt))
        assertEquals(6, machine.read(main.scope.getVar("second") as MCInt))
    }

    @Test fun restoredDeclarationImportsResolveLibrariesLoadedAfterTheirUsers() = withLibrary { output ->
        val helper = output.resolve("helper")
        val library = output.resolve("library")
        write("""
            namespace fixture.helper;
            typealias int as Seed;
            func seed() -> int { return 4; }
            func main(){}
        """, helper)
        write("""
            namespace fixture.defaults;
            import fixture.helper:*;
            func produce() -> int { var value as Seed = seed(); return value; }
            data Box { value as int = produce(); }
            func main(){}
        """, library, includes = listOf(helper))
        val original = GlobalScope.getTemplate("fixture.defaults", "Box")!!
        val main = consume("""
            import fixture.defaults:*;
            typealias bool as Seed;
            func seed() -> int { return 9; }
            func main(){ var box = Box(); dynamic var result = box.value; }
        """, library, includes = listOf(library, helper))
        val restored = GlobalScope.getTemplate("fixture.defaults", "Box")!!
        assertNotSame(original, restored)
        assertNotNull(GlobalScope.libNamespaces.getValue("fixture.defaults").scope.functions.getValue("produce").single().ast)
        assertEquals(4, execute(main, output).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun restoredTemplateMethodsKeepOwnersAndIndependentReceivers() = withLibrary { output ->
        write("""
            namespace fixture.members;
            data Box {
                value as int;
                constructor(value as int){ this.value = value; }
                func setValue(value as int){ this.value = value; }
                func add<T as type>(amount as int) -> int { return this.value + amount; }
            }
            data Child : Box {
                constructor(value as int){ this.value = value; }
            }
            func main(){}
        """, output)
        val original = GlobalScope.getTemplate("fixture.members", "Box")!!
        val main = consume("""
            import fixture.members:*;
            func main(){
                var first = Box(1);
                var second = Box(2);
                var child = Child(3);
                first.setValue(9);
                child.setValue(7);
                dynamic var result = first.value * 100 + second.value * 10 + child.value;
                dynamic var added = first.add<int>(3);
            }
        """, output)
        val box = GlobalScope.getTemplate("fixture.members", "Box")!!
        val child = GlobalScope.getTemplate("fixture.members", "Child")!!
        assertNotSame(original, box)
        val setter = box.scope.functions.getValue("setValue").single()
        assertSame(box, setter.owner)
        assertSame(box, child.scope.getFunction("setValue", emptyList(), listOf(top.mcfpp.core.lang.MCIntConcrete(7))).owner)
        val generic = box.scope.functions.getValue("add").single()
        assertSame(box, generic.owner)
        val machine = execute(main, output)
        assertEquals(927, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(12, machine.read(main.scope.getVar("added") as MCInt))
        val functions = output.resolve("consumer").resolve(Project.config.name).resolve("data")
            .resolve(setter.namespaceID.namespace).resolve("function")
        assertTrue(Files.exists(functions.resolve("${setter.namespaceID.identifier}.mcfunction")))
        val wrapper = generic.compiledFunctions.values.single()
        assertTrue(Files.exists(functions.resolve("${wrapper.namespaceID.identifier}.mcfunction")))
        assertTrue(setter.namespaceID.identifier.startsWith("box/"))
        assertTrue(wrapper.namespaceID.identifier.startsWith("box/"))
    }

    @Test fun restoredObjectMethodsKeepStaticOwnersAndTemplateScope() = withLibrary { output ->
        write("""
            namespace fixture.members;
            object data Defaults {
                value as int = 4;
                func setValue(value as int){ Defaults.value = value; }
                func read() -> int { return value; }
            }
            func main(){}
        """, output)
        val original = GlobalScope.localNamespaces.getValue("fixture.members").scope.objects
            .filterIsInstance<ObjectDataTemplate>().single { it.identifier == "Defaults" }
        val main = consume("""
            import fixture.members:*;
            func main(){ Defaults.setValue(7); dynamic var result = Defaults.read(); }
        """, output)
        val defaults = GlobalScope.libNamespaces.getValue("fixture.members").scope.objects
            .filterIsInstance<ObjectDataTemplate>().single { it.identifier == "Defaults" }
        assertNotSame(original, defaults)
        val setter = defaults.scope.functions.getValue("setValue").single()
        val reader = defaults.scope.functions.getValue("read").single()
        for (method in listOf(setter, reader)) {
            assertSame(defaults, method.owner)
            assertTrue(method.isStatic)
            assertTrue(method.namespaceID.identifier.startsWith("defaults/static/"))
        }
        val initializer = Function("initialize", main.namespace, null)
        initializer.runInFunction {
            Function.addCommand(Commands.stackIn())
            defaults.constructors.single().invoke(emptyList(), null)
            Function.addCommand(Commands.stackOut())
        }
        main.commands.addAll(0, initializer.commands)
        assertEquals(0, Project.errorCount)
        val machine = execute(main, output)
        assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
        val functions = output.resolve("consumer").resolve(Project.config.name).resolve("data")
            .resolve(setter.namespaceID.namespace).resolve("function")
        for (method in listOf(setter, reader))
            assertTrue(Files.exists(functions.resolve("${method.namespaceID.identifier}.mcfunction")))
    }

    @Test fun restoredObjectInitializersExecuteWhenTheirConstructorIsExplicitlyInvoked() = withLibrary { output ->
        write("""
            namespace fixture.defaults;
            object data Defaults {
                z as int = 4;
                a as int = Defaults.z + 1;
            }
            func main(){}
        """, output)
        val original = GlobalScope.localNamespaces.getValue("fixture.defaults").scope.objects
            .filterIsInstance<ObjectDataTemplate>().single { it.identifier == "Defaults" }
        val main = consume("""
            import fixture.defaults:*;
            func main(){}
        """, output)
        val restored = GlobalScope.libNamespaces.getValue("fixture.defaults").scope.objects
            .filterIsInstance<ObjectDataTemplate>().single { it.identifier == "Defaults" }
        assertNotSame(original, restored)
        assertEquals(listOf("z", "a"), restored.preInit.keys.toList())
        main.commands.clear()
        main.runInFunction {
            Function.addCommand(Commands.stackIn())
            restored.constructors.single().invoke(emptyList(), null)
            Function.addCommand(Commands.stackOut())
        }
        assertEquals(0, Project.errorCount)
        val machine = execute(main, output)
        assertEquals(IntTag(4), machine.readNbt("mcfpp:system", restored.nbtPath.memberIndex("z").pathToCommandPart().toString()))
        assertEquals(IntTag(5), machine.readNbt("mcfpp:system", restored.nbtPath.memberIndex("a").pathToCommandPart().toString()))
    }
}
