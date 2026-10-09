package top.mcfpp.test

import com.esotericsoftware.kryo.io.Input
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinFormat
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class LibMemberAccessTest {
    @Test fun restoredOrdinaryAndGenericMethodsRejectExternalAccess() = withLibrary { output ->
        write("""
            namespace fixture.access;
            data Box {
                private func secret()->int { return 4; }
                protected func add<T as type>(value as int)->int { return value+4; }
            }
            data Child:Box {}
        """, output)
        val original = GlobalScope.localNamespaces.getValue("fixture.access").scope.getTemplate("Box")!!
        consume("""
            import fixture.access:*;
            func main(){ var box=Box(); var child=Child(); box.secret(); child.add<int>(2); }
        """, output)
        assertEquals(2, Project.errorCount, "Both external member calls must be denied")
        val restored = template("Box")
        assertNotSame(original, restored)
        assertEquals(AccessModifier.PRIVATE, restored.scope.functions.getValue("secret").single().accessModifier)
        val add = restored.scope.functions.getValue("add").single()
        assertEquals(AccessModifier.PROTECTED, add.accessModifier)
        assertSame(restored, add.owner)
        assertSame(restored, template("Child").scope.getFunction("add", listOf(top.mcfpp.core.lang.MCFPPTypeVar(MCFPPBaseType.Int)), listOf(top.mcfpp.core.lang.MCInt(2))).owner)
    }

    @Test fun restoredMethodsUseLexicalOwnerForPrivateAndProtectedCalls() = withLibrary { output ->
        write("""
            namespace fixture.access;
            data Box {
                private func secret()->int { return 4; }
                protected func add<T as type>(value as int)->int { return value+4; }
                func read()->int { return this.secret(); }
            }
            data Child:Box {
                func readProtected()->int { return this.add<int>(2); }
            }
            func main(){
                var box=Box(); var child=Child();
                dynamic var first=box.read(); dynamic var second=child.readProtected();
            }
        """, output)
        val main = consume("""
            import fixture.access:*;
            func main(){
                var box=Box(); var child=Child();
                dynamic var first=box.read(); dynamic var second=child.readProtected();
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("first") as MCInt))
        assertEquals(6, machine.read(main.scope.getVar("second") as MCInt))
        consume("""
            import fixture.access:*;
            data Intruder:Box { func steal()->int { return this.secret(); } }
            func main(){ var child=Intruder(); child.steal(); }
        """, output)
        assertEquals(1, Project.errorCount, "A derived receiver must not grant access to the base owner's private method")
    }

    @Test fun modelInjectedPrivateNativeAccessSurvivesRealLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.access;
            @From<"top.mcfpp.mni.ConversionData">
            data NativeConversions {}
        """, output)
        val original = GlobalScope.localNamespaces.getValue("fixture.access").scope.getTemplate("NativeConversions")!!
        val native = original.scope.functions.getValue("toInt").single {
            it.normalParams.single().type == MCFPPBaseType.Int
        }
        assertIs<NativeFunction>(native)
        // Native injection has no private-source syntax; exercise the existing model API before writing the real bin.
        native.accessModifier = AccessModifier.PRIVATE
        Project.genIndex()
        checkHeader(output)
        consume("""
            import fixture.access:*;
            func main(){ var box=NativeConversions(); box.toInt(4); }
        """, output)
        assertEquals(1, Project.errorCount)
        val restored = template("NativeConversions")
        assertNotSame(original, restored)
        val restoredNative = restored.scope.functions.getValue("toInt").single {
            it.normalParams.single().type == MCFPPBaseType.Int
        }
        assertIs<NativeFunction>(restoredNative)
        assertNotSame(native, restoredNative)
        assertEquals(AccessModifier.PRIVATE, restoredNative.accessModifier)
        assertSame(restored, restoredNative.owner)
    }

    private fun template(name: String): DataTemplate =
        GlobalScope.libNamespaces.getValue("fixture.access").scope.getTemplate(name)!!

    private fun write(source: String, output: Path) {
        Project.config.includes = arrayListOf()
        MCFPPStringTest.readFromString(source.trimIndent(), targetPath = output.toString(), version = "26.3")
        assertEquals(0, Project.errorCount, "The producer must accept legal member access")
        checkHeader(output)
    }

    private fun checkHeader(output: Path) {
        Input(Files.newInputStream(output.resolve("bin.mclib"))).use {
            assertEquals(LibBinFormat.MAGIC, it.readInt())
            assertEquals(LibBinFormat.VERSION, it.readInt())
        }
    }

    private fun consume(source: String, output: Path): Function {
        Project.config.includes = arrayListOf(output.toString())
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
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
        return ScoreCommandExecutor(functions.getValue(main.namespaceID.toString()), functions).also {
            assertEquals(0, it.stackDepth)
        }
    }

    private fun withLibrary(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-member-access-")
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
}
