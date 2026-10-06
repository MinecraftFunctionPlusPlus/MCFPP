package top.mcfpp.test

import com.esotericsoftware.kryo.io.Input
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinFormat
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class LibFieldAccessTest {
    @Test fun restoredFieldsKeepDeclarationOwnersThroughInheritanceAndNestedBodies() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data Base {
                private hidden as int;
                protected shared as int;
                constructor(){ this.hidden=4; }
                func read()->int {
                    if(true){
                        dynamic var counter=0;
                        while(counter<1){ this.hidden=this.hidden+1; counter=counter+1; }
                    }
                    return this.hidden;
                }
            }
            data Child:Base {
                constructor(){ this.shared=6; }
                func readProtected()->int { return this.shared; }
            }
            func main(){
                var base=Base(); var child=Child();
                dynamic var first=base.read(); dynamic var second=child.readProtected();
            }
        """, output)
        val original = GlobalScope.localNamespaces.getValue("fixture.fields").scope.getTemplate("Base")!!
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var base=Base(); var child=Child();
                dynamic var first=base.read(); dynamic var second=child.readProtected();
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restored = GlobalScope.getTemplate("fixture.fields", "Base")!!
        val child = GlobalScope.getTemplate("fixture.fields", "Child")!!
        assertNotSame(original, restored)
        for ((name, access) in listOf("hidden" to AccessModifier.PRIVATE, "shared" to AccessModifier.PROTECTED)) {
            val field = restored.scope.getVar(name)!!
            val property = restored.scope.getProperty(name)!!
            assertSame(restored, field.declaredParentTemplate)
            assertSame(restored, property.declaredParentTemplate)
            assertSame(restored, child.scope.getVar(name)!!.declaredParentTemplate)
            assertSame(restored, child.scope.getProperty(name)!!.declaredParentTemplate)
            assertEquals(access, field.accessModifier)
            assertEquals(access, property.accessModifier)
        }
        val machine = execute(main, output)
        assertEquals(5, machine.read(main.scope.getVar("first") as MCInt))
        assertEquals(6, machine.read(main.scope.getVar("second") as MCInt))
        for (source in listOf(
            """
                func main(){ var base=Base(); base.hidden; }
            """.trimIndent(),
            """
                func main(){ var child=Child(); child.shared; }
            """.trimIndent(),
            """
                data Intruder:Base { func leak()->int { return this.hidden; } }
                func main(){ var child=Intruder(); child.leak(); }
            """.trimIndent()
        )) {
            consume("import fixture.fields:*;\n$source", output)
            assertTrue(Project.errorCount > 0, "Inaccessible field access must be rejected: $source")
        }
    }

    @Test fun restoredObjectFieldsAllowLexicalStaticAccessAndRejectExternalReads() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            object data Defaults {
                private value as int=4;
                func setValue(v as int){ Defaults.value=v; }
                func read()->int { return Defaults.value; }
            }
            func main(){ Defaults.setValue(7); dynamic var result=Defaults.read(); }
        """, output)
        val original = GlobalScope.localNamespaces.getValue("fixture.fields").scope.getObject("Defaults")!!
        val main = consume("""
            import fixture.fields:*;
            func main(){ Defaults.setValue(7); dynamic var result=Defaults.read(); }
        """, output)
        assertEquals(0, Project.errorCount)
        val restored = GlobalScope.getObject("fixture.fields", "Defaults") as ObjectDataTemplate
        assertNotSame(original, restored)
        assertEquals(AccessModifier.PRIVATE, restored.scope.getVar("value")!!.accessModifier)
        assertEquals(AccessModifier.PRIVATE, restored.scope.getProperty("value")!!.accessModifier)
        assertSame(restored, restored.scope.getVar("value")!!.declaredParentTemplate)
        assertSame(restored, restored.scope.getProperty("value")!!.declaredParentTemplate)
        val initializer = Function("initialize", main.namespace, null)
        initializer.runInFunction {
            Function.addCommand(Commands.stackIn())
            restored.constructors.single().invoke(emptyList(), null)
            Function.addCommand(Commands.stackOut())
        }
        main.commands.addAll(0, initializer.commands)
        assertEquals(0, Project.errorCount)
        val machine = execute(main, output)
        assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
        consume("""
            import fixture.fields:*;
            func main(){ Defaults.value; }
        """, output)
        assertTrue(Project.errorCount > 0, "The object's private static field must reject external reads")
    }

    @Test fun unqualifiedFieldAccessChecksDeclarationsWithoutRejectingLocalShadows() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data Base {
                private hidden as int;
                constructor(){ this.hidden=4; }
                func read()->int { return this.hidden; }
            }
            data Intruder:Base {
                func shadow()->int { var hidden=8; return hidden; }
            }
            object data Defaults {
                private value as int=4;
                func setValue(v as int){ Defaults.value=v; }
                func read()->int { return value; }
            }
            func main(){
                var base=Base(); var child=Intruder();
                Defaults.setValue(7);
                dynamic var result=Defaults.read();
                dynamic var shadow=child.shadow(); dynamic var qualified=base.read();
            }
        """, output)
        val original = GlobalScope.localNamespaces.getValue("fixture.fields").scope.getObject("Defaults")!!
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var base=Base(); var child=Intruder();
                Defaults.setValue(7);
                dynamic var result=Defaults.read();
                dynamic var shadow=child.shadow(); dynamic var qualified=base.read();
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restored = GlobalScope.getObject("fixture.fields", "Defaults") as ObjectDataTemplate
        assertNotSame(original, restored)
        val initializer = Function("initialize", main.namespace, null)
        initializer.runInFunction {
            Function.addCommand(Commands.stackIn())
            restored.constructors.single().invoke(emptyList(), null)
            Function.addCommand(Commands.stackOut())
        }
        main.commands.addAll(0, initializer.commands)
        assertEquals(0, Project.errorCount)
        val machine = execute(main, output)
        assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(8, machine.read(main.scope.getVar("shadow") as MCInt))
        assertEquals(4, machine.read(main.scope.getVar("qualified") as MCInt))
        consume("""
            import fixture.fields:*;
            data IntruderLeak:Base {
                func leak()->int { return hidden; }
            }
            func main(){ var child=IntruderLeak(); child.leak(); }
        """, output)
        assertTrue(Project.errorCount > 0, "An unqualified inherited private field must reject access before reading")
    }

    private fun write(source: String, output: Path) {
        Project.config.includes = arrayListOf()
        MCFPPStringTest.readFromString(source.trimIndent(), targetPath = output.toString(), version = "26.3")
        assertEquals(0, Project.errorCount, "The producer must accept legal field access")
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
        val output = Files.createTempDirectory("mcfpp-field-access-")
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
