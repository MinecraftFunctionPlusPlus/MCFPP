package top.mcfpp.test

import com.esotericsoftware.kryo.io.Input
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinFormat
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
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

    @Test fun unqualifiedInstanceFieldsUseTheCurrentReceiverInNestedBodies() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data Base {
                private hidden as int;
                protected shared as int;
                constructor(v as int){ hidden=v; }
                func increment()->int {
                    if(true){
                        dynamic var counter=0;
                        while(counter<1){ hidden=hidden+1; counter=counter+1; }
                    }
                    return hidden;
                }
                func read()->int { return hidden; }
                func shadow()->int { var hidden=8; return hidden; }
            }
            data Child:Base {
                constructor(){ shared=6; }
                func readProtected()->int { return shared; }
            }
            func main(){
                var first=Base(4); var second=Base(9); var child=Child();
                dynamic var incremented=first.increment();
                dynamic var unchanged=second.read();
                dynamic var shadowed=first.shadow();
                dynamic var inherited=child.readProtected();
            }
        """, output)
        val original = GlobalScope.localNamespaces.getValue("fixture.fields").scope.getTemplate("Base")!!
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var first=Base(4); var second=Base(9); var child=Child();
                dynamic var incremented=first.increment();
                dynamic var unchanged=second.read();
                dynamic var shadowed=first.shadow();
                dynamic var inherited=child.readProtected();
            }
        """, output)
        assertEquals(0, Project.errorCount)
        assertNotSame(original, GlobalScope.getTemplate("fixture.fields", "Base"))
        val machine = execute(main, output)
        assertEquals(5, machine.read(main.scope.getVar("incremented") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("unchanged") as MCInt))
        assertEquals(8, machine.read(main.scope.getVar("shadowed") as MCInt))
        assertEquals(6, machine.read(main.scope.getVar("inherited") as MCInt))
    }

    @Test fun genericTemplateRoundTripKeepsReadonlyArgumentsAndPrivateOwners() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data Box<N as int> {
                private value as int;
                constructor(v as int){ this.value=v; }
                private func secret()->int { return this.value; }
                func read()->int { return this.secret(); }
                func readArgument()->int { return N; }
            }
            func main(){
                dynamic var n=3;
                var first=Box<n>(4); var second=Box<3>(9);
                n=5;
                var third=Box<n>(6);
                dynamic var firstResult=first.read();
                dynamic var secondResult=second.read();
                dynamic var thirdResult=third.read();
                dynamic var firstArgument=first.readArgument(); dynamic var thirdArgument=third.readArgument();
            }
        """, output)
        val original = assertIs<GenericDataTemplate>(
            GlobalScope.localNamespaces.getValue("fixture.fields").scope.getTemplate("Box"))
        val producerMain = GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single()
        val firstSource = assertIs<CompiledGenericDataTemplate>(
            assertIs<DataTemplateObject>(producerMain.scope.getVar("first")).templateType)
        val secondSource = assertIs<CompiledGenericDataTemplate>(
            assertIs<DataTemplateObject>(producerMain.scope.getVar("second")).templateType)
        val thirdSource = assertIs<CompiledGenericDataTemplate>(
            assertIs<DataTemplateObject>(producerMain.scope.getVar("third")).templateType)
        assertSame(firstSource, secondSource)
        assertNotSame(firstSource, thirdSource)
        assertEquals(3, firstSource.args.single().value)
        assertEquals(5, thirdSource.args.single().value)
        val firstTypeId = firstSource.getType().typeId
        val thirdTypeId = thirdSource.getType().typeId
        assertNotEquals(firstTypeId, thirdTypeId)
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var third=Box<5>(6);
                var first=Box<3>(4); var second=Box<3>(9);
                dynamic var firstResult=first.read();
                dynamic var secondResult=second.read();
                dynamic var thirdResult=third.read();
                dynamic var firstArgument=first.readArgument(); dynamic var thirdArgument=third.readArgument();
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restored = assertIs<GenericDataTemplate>(GlobalScope.getTemplate("fixture.fields", "Box"))
        assertNotSame(original, restored)
        assertEquals(MCFPPBaseType.Int, restored.readOnlyParams.single().type)
        val first = assertIs<CompiledGenericDataTemplate>(
            assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
        val second = assertIs<CompiledGenericDataTemplate>(
            assertIs<DataTemplateObject>(main.scope.getVar("second")).templateType)
        val third = assertIs<CompiledGenericDataTemplate>(
            assertIs<DataTemplateObject>(main.scope.getVar("third")).templateType)
        assertSame(first, second)
        assertNotSame(first, third)
        assertEquals(3, first.args.single().value)
        assertEquals(5, third.args.single().value)
        assertEquals(firstTypeId, first.getType().typeId)
        assertEquals(thirdTypeId, third.getType().typeId)
        for (compiled in listOf(first, third)) {
            assertSame(restored, compiled.originTemplate)
            assertSame(compiled, compiled.scope.getVar("value")!!.declaredParentTemplate)
            assertSame(compiled, compiled.scope.getProperty("value")!!.declaredParentTemplate)
            assertSame(compiled, compiled.scope.functions.getValue("secret").single().owner)
            assertEquals(AccessModifier.PRIVATE, compiled.scope.getVar("value")!!.accessModifier)
            assertEquals(AccessModifier.PRIVATE, compiled.scope.getProperty("value")!!.accessModifier)
            assertEquals(AccessModifier.PRIVATE, compiled.scope.functions.getValue("secret").single().accessModifier)
        }
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
        assertEquals(6, machine.read(main.scope.getVar("thirdResult") as MCInt))
        assertEquals(3, machine.read(main.scope.getVar("firstArgument") as MCInt))
        assertEquals(5, machine.read(main.scope.getVar("thirdArgument") as MCInt))
        consume("""
            import fixture.fields:*;
            func make(n as int)->int {
                var item=Box<n>(1);
                return item.read();
            }
            func main(){ make(3); }
        """, output)
        assertTrue(Project.errorCount > 0, "A runtime parameter must not supply a readonly template argument")
        consume("""
            data Plain { constructor(v as int){} }
            func main(){ var item=Plain<3>(1); }
        """, output)
        assertTrue(Project.errorCount > 0, "An ordinary template must reject readonly template arguments")
    }

    @Test fun genericTemplateTypeArgumentsBindFieldsConstructorsAndReturnsAfterRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data Cell<T as type> {
                private value as T;
                constructor(v as T){ this.value=v; }
                func read()->T { return this.value; }
            }
            func main(){
                var first=Cell<int>(4); var second=Cell<int>(9); var flag=Cell<bool>(true);
                dynamic var firstResult=first.read(); dynamic var secondResult=second.read();
                dynamic var flagResult=flag.read();
                /scoreboard players set #generic_bool result 0
                if(flagResult){
                    /scoreboard players set #generic_bool result 1
                }
            }
        """, output)
        val original = assertIs<GenericDataTemplate>(
            GlobalScope.localNamespaces.getValue("fixture.fields").scope.getTemplate("Cell"))
        val producerMain = GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single()
        val sourceTemplates = listOf("first", "second", "flag").map { name ->
            assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(producerMain.scope.getVar(name)).templateType)
        }
        assertSame(sourceTemplates[0], sourceTemplates[1])
        assertNotSame(sourceTemplates[0], sourceTemplates[2])
        for ((compiled, type) in listOf(sourceTemplates[0] to MCFPPBaseType.Int, sourceTemplates[2] to MCFPPBaseType.Bool)) {
            assertSame(original, compiled.originTemplate)
            assertEquals(type, compiled.scope.getType("T"))
            val field = compiled.scope.getVar("value")!!
            val property = compiled.scope.getProperty("value")!!
            assertEquals(type, field.type)
            assertEquals(type, compiled.constructors.single().normalParams.single().type)
            assertEquals(type, compiled.scope.functions.getValue("read").single().returnType)
            assertSame(compiled, field.declaredParentTemplate)
            assertSame(compiled, property.declaredParentTemplate)
            assertEquals(AccessModifier.PRIVATE, field.accessModifier)
            assertEquals(AccessModifier.PRIVATE, property.accessModifier)
        }
        val intTypeId = sourceTemplates[0].getType().typeId
        val boolTypeId = sourceTemplates[2].getType().typeId
        assertNotEquals(intTypeId, boolTypeId)
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var flag=Cell<bool>(true);
                var first=Cell<int>(4); var second=Cell<int>(9);
                dynamic var firstResult=first.read(); dynamic var secondResult=second.read();
                dynamic var flagResult=flag.read();
                /scoreboard players set #generic_bool result 0
                if(flagResult){
                    /scoreboard players set #generic_bool result 1
                }
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restored = assertIs<GenericDataTemplate>(GlobalScope.getTemplate("fixture.fields", "Cell"))
        assertNotSame(original, restored)
        val templates = listOf("first", "second", "flag").map { name ->
            assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar(name)).templateType)
        }
        assertSame(templates[0], templates[1])
        assertNotSame(templates[0], templates[2])
        assertEquals(intTypeId, templates[0].getType().typeId)
        assertEquals(boolTypeId, templates[2].getType().typeId)
        for ((compiled, type) in listOf(templates[0] to MCFPPBaseType.Int, templates[2] to MCFPPBaseType.Bool)) {
            assertSame(restored, compiled.originTemplate)
            assertEquals(type, compiled.scope.getType("T"))
            val field = compiled.scope.getVar("value")!!
            val property = compiled.scope.getProperty("value")!!
            assertEquals(type, field.type)
            assertEquals(type, compiled.constructors.single().normalParams.single().type)
            assertEquals(type, compiled.scope.functions.getValue("read").single().returnType)
            assertSame(compiled, field.declaredParentTemplate)
            assertSame(compiled, property.declaredParentTemplate)
            assertEquals(AccessModifier.PRIVATE, field.accessModifier)
            assertEquals(AccessModifier.PRIVATE, property.accessModifier)
        }
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
        assertEquals(1, machine.values.getValue("#generic_bool result"))
    }

    @Test fun explicitGenericTypesShareCanonicalSpecializationsAcrossSignaturesAndInheritance() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            func readCell(value as Cell<int>)->int { return value.read(); }
            func readFlag(value as Cell<bool>)->bool { return value.read(); }
            func read(value as Cell<bool>)->bool { return readFlag(value); }
            func read(value as Cell<int>)->int { return readCell(value); }
            data Cell<T as type>:Base {
                private value as T;
                constructor(v as T){ this.base=2; this.value=v; }
                func read()->T { return this.value; }
                func readBase()->int { return this.base; }
            }
            data Base {
                protected base as int;
            }
            func main(){
                var first as Cell<int> = Cell<int>(4); var second as Cell<int> = Cell<int>(9);
                var flag as Cell<bool> = Cell<bool>(true);
                dynamic var firstResult=read(first); dynamic var secondResult=read(second);
                dynamic var baseResult=first.readBase(); dynamic var flagResult=read(flag);
                /scoreboard players set #generic_bool result 0
                if(flagResult){
                    /scoreboard players set #generic_bool result 1
                }
            }
        """, output)
        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        assertEquals(2, sourceScope.functions.getValue("read").map { it.namespaceID.toString() }.toSet().size)
        val original = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Cell"))
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceFirst = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("first")).templateType)
        val sourceSecond = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("second")).templateType)
        val sourceFlag = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("flag")).templateType)
        val sourceParameter = assertIs<MCFPPDataTemplateType>(
            sourceScope.functions.getValue("readCell").single().normalParams.single().type)
        assertSame(sourceFirst, sourceSecond)
        assertSame(sourceFirst, sourceParameter.template)
        val sourceBoolParameter = assertIs<MCFPPDataTemplateType>(
            sourceScope.functions.getValue("readFlag").single().normalParams.single().type)
        assertSame(sourceFlag, sourceBoolParameter.template)
        assertSame(sourceFirst, sourceFirst.constructors.single().data)
        assertSame(sourceFirst, sourceFirst.scope.getVar("value")!!.declaredParentTemplate)
        assertSame(sourceScope.getTemplate("Base"), sourceFirst.scope.getVar("base")!!.declaredParentTemplate)
        val intTypeId = sourceFirst.getType().typeId
        val boolTypeId = sourceFlag.getType().typeId
        val sourceIntIdentifier = sourceFirst.identifier
        val sourceBoolIdentifier = sourceFlag.identifier
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var flag as Cell<bool> = Cell<bool>(true);
                var first as Cell<int> = Cell<int>(4); var second as Cell<int> = Cell<int>(9);
                dynamic var firstResult=read(first); dynamic var secondResult=read(second);
                dynamic var baseResult=first.readBase(); dynamic var flagResult=read(flag);
                /scoreboard players set #generic_bool result 0
                if(flagResult){
                    /scoreboard players set #generic_bool result 1
                }
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restored = assertIs<GenericDataTemplate>(GlobalScope.getTemplate("fixture.fields", "Cell"))
        assertNotSame(original, restored)
        assertEquals(2, GlobalScope.libNamespaces.getValue("fixture.fields").scope.functions.getValue("read")
            .map { it.namespaceID.toString() }.toSet().size)
        val first = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
        val second = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("second")).templateType)
        val flag = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("flag")).templateType)
        val parameter = assertIs<MCFPPDataTemplateType>(
            GlobalScope.libNamespaces.getValue("fixture.fields").scope.functions.getValue("readCell").single().normalParams.single().type)
        assertSame(first, second)
        assertSame(first, parameter.template)
        val boolParameter = assertIs<MCFPPDataTemplateType>(
            GlobalScope.libNamespaces.getValue("fixture.fields").scope.functions.getValue("readFlag").single().normalParams.single().type)
        assertSame(flag, boolParameter.template)
        assertEquals(intTypeId, first.getType().typeId)
        assertEquals(boolTypeId, flag.getType().typeId)
        assertNotEquals(sourceIntIdentifier, first.identifier)
        assertNotEquals(sourceBoolIdentifier, flag.identifier)
        for (compiled in listOf(first, flag)) {
            assertSame(restored, compiled.originTemplate)
            assertSame(compiled, compiled.constructors.single().data)
            assertSame(compiled, compiled.scope.getVar("value")!!.declaredParentTemplate)
            assertSame(compiled, compiled.scope.getProperty("value")!!.declaredParentTemplate)
            assertEquals(AccessModifier.PRIVATE, compiled.scope.getVar("value")!!.accessModifier)
            assertSame(GlobalScope.getTemplate("fixture.fields", "Base"), compiled.scope.getVar("base")!!.declaredParentTemplate)
            assertEquals(AccessModifier.PROTECTED, compiled.scope.getVar("base")!!.accessModifier)
        }
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
        assertEquals(2, machine.read(main.scope.getVar("baseResult") as MCInt))
        assertEquals(1, machine.values.getValue("#generic_bool result"))
    }

    @Test fun explicitTemplateTypesRejectUnexpectedOrMissingReadonlyArguments() = withLibrary {
        MCFPPStringTest.readFromString("""
            data Plain {}
            func main(){ var item as Plain<3>; }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0, "An ordinary template type must reject unexpected readonly arguments")
        MCFPPStringTest.readFromString("""
            data Cell<T as type> {}
            func main(){ var item as Cell; }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0, "A generic template type must require its readonly arguments")
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
