package top.mcfpp.test

import com.esotericsoftware.kryo.io.Input
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.ValueSnapshot
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.analysis.SpecializationArgument
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.StaticMemberView
import top.mcfpp.core.lang.nbt.NBTListConcrete
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinFormat
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.compound.CompiledGenericObjectDataTemplate
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPUnionType
import top.mcfpp.type.MCFPPVectorType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.TypeId
import top.mcfpp.util.TempPool
import top.mcfpp.nbt.tags.Tag
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

    @Test fun genericTypeExpressionsUseBoundDeclarationScopeAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data Cell<T as type> {
                private value as T;
                constructor(v as T){ this.value=v; }
                func read()->T { return this.value; }
            }
            data Sized<N as int> {
                private value as int;
                constructor(v as int){ this.value=v+N; }
                func read()->int { return this.value; }
            }
            data Envelope<T as type,N as int> {
                private cell as Cell<(T)>;
                private sized as Sized<(N+1)>;
                constructor(supplied as Cell<(T)>){ this.cell=supplied; this.sized=Sized<N+1>(3); }
                func passCell(item as Cell<(T)>)->Cell<(T)> { return item; }
                func passSized(item as Sized<(N+1)>)->Sized<(N+1)> { return item; }
                func read()->T { return this.passCell(this.cell).read(); }
                func amount()->int { return this.passSized(this.sized).read(); }
            }
            func main(){
                var T=true; var N=90;
                var intCell=Cell<int>(4); var first=Envelope<int,2>(intCell);
                var flagCell=Cell<bool>(true); var flag=Envelope<bool,4>(flagCell);
                dynamic var intResult=first.read(); dynamic var intAmount=first.amount();
                dynamic var flagAmount=flag.amount(); dynamic var flagResult=flag.read();
                /scoreboard players set #generic_bool result 0
                if(flagResult){
                    /scoreboard players set #generic_bool result 1
                }
            }
        """, output)
        val original = assertIs<GenericDataTemplate>(GlobalScope.localNamespaces.getValue("fixture.fields").scope.getTemplate("Envelope"))
        val sourceMain = GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single()
        val sourceTemplates = listOf("first", "flag").map {
            assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar(it)).templateType)
        }
        val sourceTypeIds = sourceTemplates.map { it.getType().typeId }
        for ((index, compiled) in sourceTemplates.withIndex()) {
            val type = if (index == 0) MCFPPBaseType.Int else MCFPPBaseType.Bool
            assertEquals(type, compiled.scope.getType("T"))
            assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(if (index == 0) 2L else 4L)),
                ValueSnapshot.of(compiled.scope.getVar("N")))
            val cell = assertIs<MCFPPDataTemplateType>(compiled.scope.getVar("cell")!!.type)
            val supplied = assertIs<MCFPPDataTemplateType>(compiled.constructors.single().normalParams.single().type)
            val passCell = compiled.scope.functions.getValue("passCell").single()
            assertSame(cell.template, supplied.template)
            assertSame(cell.template, assertIs<MCFPPDataTemplateType>(passCell.normalParams.single().type).template)
            assertSame(cell.template, assertIs<MCFPPDataTemplateType>(passCell.returnType).template)
            assertSame(cell.template, assertIs<DataTemplateObject>(sourceMain.scope.getVar(if (index == 0) "intCell" else "flagCell")).templateType)
            assertEquals(type, cell.template.scope.getVar("value")!!.type)
            val sized = assertIs<MCFPPDataTemplateType>(compiled.scope.getVar("sized")!!.type)
            val passSized = compiled.scope.functions.getValue("passSized").single()
            assertSame(sized.template, assertIs<MCFPPDataTemplateType>(passSized.normalParams.single().type).template)
            assertSame(sized.template, assertIs<MCFPPDataTemplateType>(passSized.returnType).template)
            assertEquals(if (index == 0) 3 else 5, assertIs<CompiledGenericDataTemplate>(sized.template).args.single().value)
            for (name in listOf("cell", "sized")) {
                assertSame(compiled, compiled.scope.getVar(name)!!.declaredParentTemplate)
                assertSame(compiled, compiled.scope.getProperty(name)!!.declaredParentTemplate)
                assertEquals(AccessModifier.PRIVATE, compiled.scope.getVar(name)!!.accessModifier)
                assertEquals(AccessModifier.PRIVATE, compiled.scope.getProperty(name)!!.accessModifier)
            }
        }
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var T=true; var N=90;
                var flagCell=Cell<bool>(true); var flag=Envelope<bool,4>(flagCell);
                var intCell=Cell<int>(4); var first=Envelope<int,2>(intCell);
                dynamic var intResult=first.read(); dynamic var intAmount=first.amount();
                dynamic var flagAmount=flag.amount(); dynamic var flagResult=flag.read();
                /scoreboard players set #generic_bool result 0
                if(flagResult){
                    /scoreboard players set #generic_bool result 1
                }
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restored = assertIs<GenericDataTemplate>(GlobalScope.getTemplate("fixture.fields", "Envelope"))
        assertNotSame(original, restored)
        val templates = listOf("first", "flag").map {
            assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar(it)).templateType)
        }
        assertEquals(sourceTypeIds, templates.map { it.getType().typeId })
        for ((index, compiled) in templates.withIndex()) {
            assertSame(restored, compiled.originTemplate)
            val type = if (index == 0) MCFPPBaseType.Int else MCFPPBaseType.Bool
            assertEquals(type, compiled.scope.getType("T"))
            assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(if (index == 0) 2L else 4L)),
                ValueSnapshot.of(compiled.scope.getVar("N")))
            val cell = assertIs<MCFPPDataTemplateType>(compiled.scope.getVar("cell")!!.type)
            val supplied = assertIs<MCFPPDataTemplateType>(compiled.constructors.single().normalParams.single().type)
            val passCell = compiled.scope.functions.getValue("passCell").single()
            assertSame(cell.template, supplied.template)
            assertSame(cell.template, assertIs<MCFPPDataTemplateType>(passCell.normalParams.single().type).template)
            assertSame(cell.template, assertIs<MCFPPDataTemplateType>(passCell.returnType).template)
            assertSame(cell.template, assertIs<DataTemplateObject>(main.scope.getVar(if (index == 0) "intCell" else "flagCell")).templateType)
            assertEquals(type, cell.template.scope.getVar("value")!!.type)
            val sized = assertIs<MCFPPDataTemplateType>(compiled.scope.getVar("sized")!!.type)
            val passSized = compiled.scope.functions.getValue("passSized").single()
            assertSame(sized.template, assertIs<MCFPPDataTemplateType>(passSized.normalParams.single().type).template)
            assertSame(sized.template, assertIs<MCFPPDataTemplateType>(passSized.returnType).template)
            assertEquals(if (index == 0) 3 else 5, assertIs<CompiledGenericDataTemplate>(sized.template).args.single().value)
            for (name in listOf("cell", "sized")) {
                assertSame(compiled, compiled.scope.getVar(name)!!.declaredParentTemplate)
                assertSame(compiled, compiled.scope.getProperty(name)!!.declaredParentTemplate)
                assertEquals(AccessModifier.PRIVATE, compiled.scope.getVar(name)!!.accessModifier)
                assertEquals(AccessModifier.PRIVATE, compiled.scope.getProperty(name)!!.accessModifier)
            }
        }
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("intResult") as MCInt))
        assertEquals(6, machine.read(main.scope.getVar("intAmount") as MCInt))
        assertEquals(8, machine.read(main.scope.getVar("flagAmount") as MCInt))
        assertEquals(1, machine.values.getValue("#generic_bool result"))
    }

    @Test fun frozenDeclarationAndContainerTypeArgumentsRestoreCanonicalTypes() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            typealias Leaf as LeafAlias;
            func readLeaf(arg as Holder<LeafAlias>)->int { return arg.read().read(); }
            func readList(arg as Holder<list<int>>)->int { var values=arg.read(); return values[0]; }
            data Leaf {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            data Holder<T as type> {
                private value as T;
                constructor(v as T){ this.value=v; }
                func read()->T { return this.value; }
            }
            func main(){
                var first=Holder<LeafAlias>(Leaf(4));
                var second=Holder<Leaf>(Leaf(9));
                var listed=Holder<list<int>>([7]);
                dynamic var firstResult=readLeaf(first); dynamic var secondResult=readLeaf(second);
                dynamic var listResult=readList(listed);
            }
        """, output)
        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourceLeaf = sourceScope.getTemplate("Leaf")!!
        val sourcePrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Holder"))
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceFirst = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("first")).templateType)
        val sourceSecond = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("second")).templateType)
        val sourceListed = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("listed")).templateType)
        assertSame(sourceFirst, sourceSecond)
        assertNotSame(sourceFirst, sourceListed)
        val sourceBoundLeaf = assertIs<MCFPPTypeVar>(sourceFirst.scope.getVar("T")).value
        assertSame(sourceLeaf, assertIs<MCFPPDataTemplateType>(sourceBoundLeaf).template)
        for (type in listOf(sourceFirst.scope.getVar("value")!!.type,
            sourceFirst.constructors.single().normalParams.single().type,
            sourceFirst.scope.functions.getValue("read").single().returnType)) {
            assertEquals(sourceLeaf.getType().typeId, type.typeId)
            assertSame(sourceLeaf, assertIs<MCFPPDataTemplateType>(type).template)
        }
        val sourceListType = assertIs<MCFPPListType>(assertIs<MCFPPTypeVar>(sourceListed.scope.getVar("T")).value)
        assertEquals(MCFPPBaseType.Int, sourceListType.generic.single())
        for (type in listOf(sourceListed.scope.getVar("value")!!.type,
            sourceListed.constructors.single().normalParams.single().type,
            sourceListed.scope.functions.getValue("read").single().returnType)) {
            assertEquals(sourceListType.typeId, type.typeId)
        }
        assertSame(sourceFirst, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readLeaf").single().normalParams.single().type).template)
        assertSame(sourceListed, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readList").single().normalParams.single().type).template)
        val sourceLeafHolderId = sourceFirst.getType().typeId
        val sourceListHolderId = sourceListed.getType().typeId
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var listed=Holder<list<int>>([7]);
                var second=Holder<Leaf>(Leaf(9));
                var first=Holder<LeafAlias>(Leaf(4));
                dynamic var firstResult=readLeaf(first); dynamic var secondResult=readLeaf(second);
                dynamic var listResult=readList(listed);
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val leaf = restoredScope.getTemplate("Leaf")!!
        val prototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Holder"))
        assertNotSame(sourceLeaf, leaf)
        assertNotSame(sourcePrototype, prototype)
        val first = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
        val second = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("second")).templateType)
        val listed = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("listed")).templateType)
        assertSame(first, second)
        assertNotSame(first, listed)
        assertSame(prototype, first.originTemplate)
        assertSame(prototype, listed.originTemplate)
        assertEquals(sourceLeafHolderId, first.getType().typeId)
        assertEquals(sourceListHolderId, listed.getType().typeId)
        assertSame(leaf, assertIs<MCFPPDataTemplateType>(assertIs<MCFPPTypeVar>(first.scope.getVar("T")).value).template)
        for (type in listOf(first.scope.getVar("value")!!.type,
            first.constructors.single().normalParams.single().type,
            first.scope.functions.getValue("read").single().returnType)) {
            assertEquals(leaf.getType().typeId, type.typeId)
            assertSame(leaf, assertIs<MCFPPDataTemplateType>(type).template)
        }
        val listType = assertIs<MCFPPListType>(assertIs<MCFPPTypeVar>(listed.scope.getVar("T")).value)
        assertEquals(MCFPPBaseType.Int, listType.generic.single())
        for (type in listOf(listed.scope.getVar("value")!!.type,
            listed.constructors.single().normalParams.single().type,
            listed.scope.functions.getValue("read").single().returnType)) {
            assertEquals(listType.typeId, type.typeId)
        }
        assertSame(first, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readLeaf").single().normalParams.single().type).template)
        assertSame(listed, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readList").single().normalParams.single().type).template)
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
        assertEquals(7, machine.read(main.scope.getVar("listResult") as MCInt))
    }

    @Test fun frozenTypeCollectionsShareCanonicalSpecializationsAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            func readBundle(arg as Bundle<[Leaf]>)->int { return arg.read(); }
            data Leaf {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            data Cell<T as type> {
                private value as T;
                constructor(v as T){ this.value=v; }
                func read()->T { return this.value; }
            }
            data Bundle<Types as list<type>> {
                private cell as Cell<(Types[0])>;
                constructor(v as Cell<(Types[0])>){ this.cell=v; }
                func read()->int { return this.cell.read().read(); }
            }
            func main(){
                var first=Bundle<[Leaf]>(Cell<Leaf>(Leaf(4)));
                var second=Bundle<[Leaf]>(Cell<Leaf>(Leaf(9)));
                dynamic var firstResult=readBundle(first);
                dynamic var secondResult=readBundle(second);
            }
        """, output)

        fun checkBundle(bundle: CompiledGenericDataTemplate, leaf: DataTemplate,
                        cellPrototype: GenericDataTemplate): CompiledGenericDataTemplate {
            val types = assertIs<NBTListConcrete>(bundle.scope.getVar("Types"))
            assertEquals(StorageLayout.CompilerOnly, types.storageBinding!!.data.layout)
            assertNotNull(ValueSnapshot.of(types))
            val leafType = assertIs<MCFPPTypeVar>(types.value.single()).value
            assertSame(leaf, assertIs<MCFPPDataTemplateType>(leafType).template)
            val cell = assertIs<CompiledGenericDataTemplate>(assertIs<MCFPPDataTemplateType>(bundle.scope.getVar("cell")!!.type).template)
            assertSame(cellPrototype, cell.originTemplate)
            assertSame(cell, assertIs<MCFPPDataTemplateType>(bundle.constructors.single().normalParams.single().type).template)
            assertSame(leaf, assertIs<MCFPPDataTemplateType>(cell.scope.getVar("value")!!.type).template)
            assertSame(leaf, assertIs<MCFPPDataTemplateType>(cell.constructors.single().normalParams.single().type).template)
            assertSame(leaf, assertIs<MCFPPDataTemplateType>(cell.scope.functions.getValue("read").single().returnType).template)
            return cell
        }

        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourceLeaf = sourceScope.getTemplate("Leaf")!!
        val sourceCellPrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Cell"))
        val sourceBundlePrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Bundle"))
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceFirst = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("first")).templateType)
        val sourceSecond = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("second")).templateType)
        assertSame(sourceFirst, sourceSecond)
        assertSame(sourceBundlePrototype, sourceFirst.originTemplate)
        val sourceCell = checkBundle(sourceFirst, sourceLeaf, sourceCellPrototype)
        assertSame(sourceFirst, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readBundle").single().normalParams.single().type).template)
        val sourceSnapshot = ValueSnapshot.of(sourceFirst.scope.getVar("Types"))!!
        assertEquals(sourceSnapshot, ValueSnapshot.of(sourceSecond.scope.getVar("Types")))
        val sourceBundleId = sourceFirst.getType().typeId
        val sourceCellId = sourceCell.getType().typeId

        val main = consume("""
            import fixture.fields:*;
            func main(){
                var second=Bundle<[Leaf]>(Cell<Leaf>(Leaf(9)));
                var first=Bundle<[Leaf]>(Cell<Leaf>(Leaf(4)));
                dynamic var firstResult=readBundle(first);
                dynamic var secondResult=readBundle(second);
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val leaf = restoredScope.getTemplate("Leaf")!!
        val cellPrototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Cell"))
        val bundlePrototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Bundle"))
        assertNotSame(sourceLeaf, leaf)
        assertNotSame(sourceCellPrototype, cellPrototype)
        assertNotSame(sourceBundlePrototype, bundlePrototype)
        val first = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
        val second = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("second")).templateType)
        assertSame(first, second)
        assertSame(bundlePrototype, first.originTemplate)
        assertNotSame(sourceFirst, first)
        val cell = checkBundle(first, leaf, cellPrototype)
        assertNotSame(sourceCell, cell)
        assertEquals(sourceBundleId, first.getType().typeId)
        assertEquals(sourceCellId, cell.getType().typeId)
        assertEquals(sourceSnapshot, ValueSnapshot.of(first.scope.getVar("Types")))
        assertEquals(ValueSnapshot.of(first.scope.getVar("Types")), ValueSnapshot.of(second.scope.getVar("Types")))
        assertSame(first, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readBundle").single().normalParams.single().type).template)
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
    }

    @Test fun frozenSpecializedTypeArgumentsRestoreCanonicalTypesAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            func readNested(arg as Holder<Cell<int>>)->int { return arg.read().read(); }
            data Cell<T as type> {
                private value as T;
                constructor(v as T){ this.value=v; }
                func read()->T { return this.value; }
            }
            data Holder<T as type> {
                private value as T;
                constructor(v as T){ this.value=v; }
                func read()->T { return this.value; }
            }
            func main(){
                var first=Holder<Cell<int>>(Cell<int>(4));
                var second=Holder<Cell<int>>(Cell<int>(9));
                dynamic var firstResult=readNested(first);
                dynamic var secondResult=readNested(second);
            }
        """, output)

        fun checkHolder(holder: CompiledGenericDataTemplate,
                        cellPrototype: GenericDataTemplate): CompiledGenericDataTemplate {
            val type = assertIs<MCFPPTypeVar>(holder.scope.getVar("T")).value
            val cell = assertIs<CompiledGenericDataTemplate>(assertIs<MCFPPDataTemplateType>(type).template)
            assertSame(cellPrototype, cell.originTemplate)
            for (memberType in listOf(holder.scope.getVar("value")!!.type,
                holder.constructors.single().normalParams.single().type,
                holder.scope.functions.getValue("read").single().returnType)) {
                assertEquals(type.typeId, memberType.typeId)
                assertSame(cell, assertIs<MCFPPDataTemplateType>(memberType).template)
            }
            assertEquals(MCFPPBaseType.Int, assertIs<MCFPPTypeVar>(cell.scope.getVar("T")).value)
            for (memberType in listOf(cell.scope.getVar("value")!!.type,
                cell.constructors.single().normalParams.single().type,
                cell.scope.functions.getValue("read").single().returnType)) {
                assertEquals(MCFPPBaseType.Int.typeId, memberType.typeId)
            }
            return cell
        }

        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourceCellPrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Cell"))
        val sourceHolderPrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Holder"))
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceFirst = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("first")).templateType)
        val sourceSecond = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("second")).templateType)
        assertSame(sourceFirst, sourceSecond)
        assertSame(sourceHolderPrototype, sourceFirst.originTemplate)
        val sourceCell = checkHolder(sourceFirst, sourceCellPrototype)
        assertSame(sourceFirst, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readNested").single().normalParams.single().type).template)
        val sourceHolderId = sourceFirst.getType().typeId
        val sourceCellId = sourceCell.getType().typeId
        val sourceHolderSnapshot = assertNotNull(ValueSnapshot.of(sourceFirst.scope.getVar("T")))
        val sourceCellSnapshot = assertNotNull(ValueSnapshot.of(sourceCell.scope.getVar("T")))

        val main = consume("""
            import fixture.fields:*;
            func main(){
                var second=Holder<Cell<int>>(Cell<int>(9));
                var first=Holder<Cell<int>>(Cell<int>(4));
                dynamic var firstResult=readNested(first);
                dynamic var secondResult=readNested(second);
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val cellPrototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Cell"))
        val holderPrototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Holder"))
        assertNotSame(sourceCellPrototype, cellPrototype)
        assertNotSame(sourceHolderPrototype, holderPrototype)
        val first = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
        val second = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("second")).templateType)
        assertSame(first, second)
        assertSame(holderPrototype, first.originTemplate)
        assertNotSame(sourceFirst, first)
        val cell = checkHolder(first, cellPrototype)
        assertNotSame(sourceCell, cell)
        assertEquals(sourceHolderId, first.getType().typeId)
        assertEquals(sourceCellId, cell.getType().typeId)
        assertEquals(sourceHolderSnapshot, ValueSnapshot.of(first.scope.getVar("T")))
        assertEquals(sourceCellSnapshot, ValueSnapshot.of(cell.scope.getVar("T")))
        assertSame(first, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readNested").single().normalParams.single().type).template)
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
    }

    @Test fun frozenUnionTypeArgumentsNormalizeAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            typealias (int|string) as Scalar;
            typealias (string|int|int) as ReorderedScalar;
            func readBox(arg as Box<Scalar>)->int { return arg.read(); }
            data Box<T as type> {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            func main(){
                var first=Box<Scalar>(4);
                var second=Box<ReorderedScalar>(9);
                dynamic var firstResult=readBox(first);
                dynamic var secondResult=readBox(second);
            }
        """, output)
        val expectedAlternatives = setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.String.typeId)
        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourceScalar = assertIs<MCFPPUnionType>(sourceScope.getType("Scalar"))
        val sourceReordered = assertIs<MCFPPUnionType>(sourceScope.getType("ReorderedScalar"))
        assertEquals(expectedAlternatives, sourceScalar.types.map { it.typeId }.toSet())
        assertEquals(2, sourceScalar.types.size)
        assertEquals(2, sourceReordered.types.size)
        assertEquals(TypeId.Union(expectedAlternatives), sourceScalar.typeId)
        assertEquals(sourceScalar.typeId, sourceReordered.typeId)
        assertEquals(ValueSnapshot.of(sourceScalar), ValueSnapshot.of(sourceReordered))
        val sourcePrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Box"))
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceFirst = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("first")).templateType)
        val sourceSecond = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("second")).templateType)
        assertSame(sourceFirst, sourceSecond)
        assertSame(sourcePrototype, sourceFirst.originTemplate)
        val sourceBound = assertIs<MCFPPTypeVar>(sourceFirst.scope.getVar("T"))
        assertEquals(sourceScalar.typeId, assertIs<MCFPPUnionType>(sourceBound.value).typeId)
        val sourceSnapshot = assertNotNull(ValueSnapshot.of(sourceBound))
        assertSame(sourceFirst, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readBox").single().normalParams.single().type).template)
        val sourceBoxId = sourceFirst.getType().typeId

        val main = consume("""
            import fixture.fields:*;
            func main(){
                var second=Box<ReorderedScalar>(9);
                var first=Box<Scalar>(4);
                dynamic var firstResult=readBox(first);
                dynamic var secondResult=readBox(second);
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val scalar = assertIs<MCFPPUnionType>(restoredScope.getType("Scalar"))
        val reordered = assertIs<MCFPPUnionType>(restoredScope.getType("ReorderedScalar"))
        assertNotSame(sourceScalar, scalar)
        assertNotSame(sourceReordered, reordered)
        assertEquals(expectedAlternatives, scalar.types.map { it.typeId }.toSet())
        assertEquals(2, scalar.types.size)
        assertEquals(2, reordered.types.size)
        assertEquals(TypeId.Union(expectedAlternatives), scalar.typeId)
        assertEquals(sourceScalar.typeId, scalar.typeId)
        assertEquals(scalar.typeId, reordered.typeId)
        assertEquals(ValueSnapshot.of(sourceScalar), ValueSnapshot.of(scalar))
        assertEquals(ValueSnapshot.of(scalar), ValueSnapshot.of(reordered))
        val prototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Box"))
        assertNotSame(sourcePrototype, prototype)
        val first = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
        val second = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("second")).templateType)
        assertSame(first, second)
        assertSame(prototype, first.originTemplate)
        assertNotSame(sourceFirst, first)
        assertEquals(sourceBoxId, first.getType().typeId)
        val bound = assertIs<MCFPPTypeVar>(first.scope.getVar("T"))
        assertEquals(scalar.typeId, assertIs<MCFPPUnionType>(bound.value).typeId)
        assertEquals(sourceSnapshot, ValueSnapshot.of(bound))
        assertSame(first, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readBox").single().normalParams.single().type).template)
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
    }

    @Test fun frozenVectorTypeArgumentsRestoreDimensionsAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            func readTwo(arg as Box<vec2>)->int { return arg.read(); }
            func readThree(arg as Box<vec3>)->int { return arg.read(); }
            data Box<T as type> {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            func main(){
                var two=Box<vec2>(4); var three=Box<vec3>(9);
                dynamic var twoResult=readTwo(two);
                dynamic var threeResult=readThree(three);
            }
        """, output)
        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourcePrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Box"))
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceTwo = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("two")).templateType)
        val sourceThree = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("three")).templateType)
        assertNotSame(sourceTwo, sourceThree)
        assertSame(sourcePrototype, sourceTwo.originTemplate)
        assertSame(sourcePrototype, sourceThree.originTemplate)
        assertEquals(2, assertIs<MCFPPVectorType>(assertIs<MCFPPTypeVar>(sourceTwo.scope.getVar("T")).value).dimension)
        assertEquals(3, assertIs<MCFPPVectorType>(assertIs<MCFPPTypeVar>(sourceThree.scope.getVar("T")).value).dimension)
        assertSame(sourceTwo, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readTwo").single().normalParams.single().type).template)
        assertSame(sourceThree, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readThree").single().normalParams.single().type).template)
        val sourceTwoId = sourceTwo.getType().typeId
        val sourceThreeId = sourceThree.getType().typeId
        assertNotEquals(sourceTwoId, sourceThreeId)
        val sourceTwoSnapshot = assertNotNull(ValueSnapshot.of(sourceTwo.scope.getVar("T")))
        val sourceThreeSnapshot = assertNotNull(ValueSnapshot.of(sourceThree.scope.getVar("T")))
        assertNotEquals(sourceTwoSnapshot, sourceThreeSnapshot)

        val main = consume("""
            import fixture.fields:*;
            func main(){
                var three=Box<vec3>(9); var two=Box<vec2>(4);
                dynamic var twoResult=readTwo(two);
                dynamic var threeResult=readThree(three);
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val prototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Box"))
        assertNotSame(sourcePrototype, prototype)
        val two = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("two")).templateType)
        val three = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("three")).templateType)
        assertNotSame(two, three)
        assertSame(prototype, two.originTemplate)
        assertSame(prototype, three.originTemplate)
        assertNotSame(sourceTwo, two)
        assertNotSame(sourceThree, three)
        assertEquals(sourceTwoId, two.getType().typeId)
        assertEquals(sourceThreeId, three.getType().typeId)
        assertNotEquals(two.getType().typeId, three.getType().typeId)
        assertEquals(2, assertIs<MCFPPVectorType>(assertIs<MCFPPTypeVar>(two.scope.getVar("T")).value).dimension)
        assertEquals(3, assertIs<MCFPPVectorType>(assertIs<MCFPPTypeVar>(three.scope.getVar("T")).value).dimension)
        assertEquals(sourceTwoSnapshot, ValueSnapshot.of(two.scope.getVar("T")))
        assertEquals(sourceThreeSnapshot, ValueSnapshot.of(three.scope.getVar("T")))
        assertSame(two, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readTwo").single().normalParams.single().type).template)
        assertSame(three, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readThree").single().normalParams.single().type).template)
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("twoResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("threeResult") as MCInt))
    }

    @Test fun frozenSelectorTypeArgumentsPreserveFiltersAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            typealias entity<2,"minecraft:pig","!minecraft:cow"> as Selection;
            func readSelection(arg as Box<Selection>)->int { return arg.read(); }
            func readAnyEntity(arg as Box<entity>)->int { return arg.read(); }
            data Box<T as type> {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            func main(){
                var selected=Box<Selection>(4); var anyEntity=Box<entity>(9);
                dynamic var selectedResult=readSelection(selected);
                dynamic var anyResult=readAnyEntity(anyEntity);
            }
        """, output)
        val filters = listOf("\"minecraft:pig\"", "\"!minecraft:cow\"")
        val selectedId = TypeId.Selector(2, filters, false)
        val generalId = TypeId.Selector(null, null, false)
        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourcePrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Box"))
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceSelected = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("selected")).templateType)
        val sourceGeneral = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("anyEntity")).templateType)
        assertNotSame(sourceSelected, sourceGeneral)
        val sourceModels = listOf(sourceSelected, sourceGeneral)
        val expectedIds = listOf(selectedId, generalId)
        for ((model, expected) in sourceModels.zip(expectedIds)) {
            assertSame(sourcePrototype, model.originTemplate)
            val type = assertIs<MCFPPEntityType>(assertIs<MCFPPTypeVar>(model.scope.getVar("T")).value)
            assertEquals(expected.limit, type.limit)
            assertEquals(expected.entities, type.types)
            assertFalse(type.isName)
            assertEquals(expected, type.typeId)
        }
        assertSame(sourceSelected, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readSelection").single().normalParams.single().type).template)
        assertSame(sourceGeneral, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readAnyEntity").single().normalParams.single().type).template)
        val sourceIds = sourceModels.map { it.getType().typeId }
        val sourceSnapshots = sourceModels.map { assertNotNull(ValueSnapshot.of(it.scope.getVar("T"))) }
        assertNotEquals(sourceIds[0], sourceIds[1])
        assertNotEquals(sourceSnapshots[0], sourceSnapshots[1])

        val main = consume("""
            import fixture.fields:*;
            func main(){
                var anyEntity=Box<entity>(9); var selected=Box<Selection>(4);
                dynamic var selectedResult=readSelection(selected);
                dynamic var anyResult=readAnyEntity(anyEntity);
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val prototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Box"))
        assertNotSame(sourcePrototype, prototype)
        val selected = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("selected")).templateType)
        val general = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("anyEntity")).templateType)
        assertNotSame(selected, general)
        for ((index, model) in listOf(selected, general).withIndex()) {
            assertSame(prototype, model.originTemplate)
            assertNotSame(sourceModels[index], model)
            assertEquals(sourceIds[index], model.getType().typeId)
            assertEquals(sourceSnapshots[index], ValueSnapshot.of(model.scope.getVar("T")))
            val type = assertIs<MCFPPEntityType>(assertIs<MCFPPTypeVar>(model.scope.getVar("T")).value)
            assertEquals(expectedIds[index].limit, type.limit)
            assertEquals(expectedIds[index].entities, type.types)
            assertFalse(type.isName)
            assertEquals(expectedIds[index], type.typeId)
        }
        assertSame(selected, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readSelection").single().normalParams.single().type).template)
        assertSame(general, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readAnyEntity").single().normalParams.single().type).template)
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("selectedResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("anyResult") as MCInt))
    }

    @Test fun unionTypeExpressionsShareCanonicalSpecializationsAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            typealias (int|string) as Scalar;
            func readBox(arg as Box<(int|string)>)->int { return arg.read(); }
            data Box<T as type> {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            func main(){
                var first=Box<(int|string)>(4);
                var second=Box<(string|int|int)>(9);
                dynamic var firstResult=readBox(first);
                dynamic var secondResult=readBox(second);
            }
        """, output)
        val expectedId = TypeId.Union(setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.String.typeId))
        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourceAlias = assertIs<MCFPPUnionType>(sourceScope.getType("Scalar"))
        assertEquals(expectedId, sourceAlias.typeId)
        val sourcePrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Box"))
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceFirst = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("first")).templateType)
        val sourceSecond = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("second")).templateType)
        assertSame(sourceFirst, sourceSecond)
        assertSame(sourcePrototype, sourceFirst.originTemplate)
        val sourceBound = assertIs<MCFPPTypeVar>(sourceFirst.scope.getVar("T"))
        val sourceScalarSnapshot = assertNotNull(ValueSnapshot.of(sourceBound))
        assertEquals(2, assertIs<MCFPPUnionType>(sourceBound.value).types.size)
        assertEquals(expectedId, assertIs<MCFPPUnionType>(sourceBound.value).typeId)
        assertSame(sourceFirst, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readBox").single().normalParams.single().type).template)
        val sourceBoxId = sourceFirst.getType().typeId

        val main = consume("""
            import fixture.fields:*;
            func main(){
                var second=Box<(string|int|int)>(9);
                var first=Box<(string|int)>(4);
                dynamic var firstResult=readBox(first);
                dynamic var secondResult=readBox(second);
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val alias = assertIs<MCFPPUnionType>(restoredScope.getType("Scalar"))
        assertNotSame(sourceAlias, alias)
        assertEquals(expectedId, alias.typeId)
        val prototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Box"))
        assertNotSame(sourcePrototype, prototype)
        val first = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
        val second = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("second")).templateType)
        assertSame(first, second)
        assertSame(prototype, first.originTemplate)
        assertNotSame(sourceFirst, first)
        assertEquals(sourceBoxId, first.getType().typeId)
        val bound = assertIs<MCFPPTypeVar>(first.scope.getVar("T"))
        assertEquals(2, assertIs<MCFPPUnionType>(bound.value).types.size)
        assertEquals(alias.typeId, assertIs<MCFPPUnionType>(bound.value).typeId)
        assertEquals(sourceScalarSnapshot, ValueSnapshot.of(bound))
        assertSame(first, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readBox").single().normalParams.single().type).template)
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
    }

    @Test fun frozenAnonymousAliasTypeArgumentsRestoreCanonicalTypesAcrossLibraryRoundTrip() = withLibrary { output ->
        val namedIdentifier = "data_${TempPool.anonymousTemplateCount}"
        write("""
            namespace fixture.fields;
            typealias data {
                @DataOnly
                value as int;
            } as X;
            typealias X as Y;
            data $namedIdentifier { value as int; }
            func readBox(arg as Box<X>)->int { return arg.read(); }
            data Box<T as type> {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            func main(){
                var first=Box<X>(4); var second=Box<Y>(9);
                dynamic var firstResult=readBox(first);
                dynamic var secondResult=readBox(second);
            }
        """, output)
        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourceAlias = assertIs<MCFPPDataTemplateType>(sourceScope.getType("X"))
        sourceAlias.tryResolve()
        val sourceTarget = sourceAlias.template
        val sourceNamed = sourceScope.getTemplate(namedIdentifier)!!
        assertNotSame(sourceNamed, sourceTarget)
        assertNotEquals(sourceNamed.getType().typeId, sourceAlias.typeId)
        assertSame(sourceTarget, assertIs<MCFPPDataTemplateType>(sourceScope.getType("Y")).template)
        assertTrue(assertIs<MCInt>(sourceTarget.scope.getVar("value")).isDataOnly)
        val sourceAnonymousId = assertIs<TypeId.Declaration>(sourceAlias.typeId)
        val sourcePrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Box"))
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceFirst = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("first")).templateType)
        val sourceSecond = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(sourceMain.scope.getVar("second")).templateType)
        assertSame(sourceFirst, sourceSecond)
        assertSame(sourcePrototype, sourceFirst.originTemplate)
        val sourceBound = assertIs<MCFPPTypeVar>(sourceFirst.scope.getVar("T"))
        assertSame(sourceTarget, assertIs<MCFPPDataTemplateType>(sourceBound.value).template)
        assertSame(sourceTarget, assertIs<MCFPPDataTemplateType>(sourceFirst.scope.getType("T")).template)
        assertSame(sourceFirst, assertIs<MCFPPDataTemplateType>(sourceScope.functions.getValue("readBox").single().normalParams.single().type).template)
        val sourceSnapshot = assertNotNull(ValueSnapshot.of(sourceBound))
        val sourceBoxId = sourceFirst.getType().typeId

        val main = consume("""
            import fixture.fields:*;
            func main(){
                var second=Box<Y>(9); var first=Box<X>(4);
                dynamic var firstResult=readBox(first);
                dynamic var secondResult=readBox(second);
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val alias = assertIs<MCFPPDataTemplateType>(restoredScope.getType("X"))
        alias.tryResolve()
        val target = alias.template
        val named = restoredScope.getTemplate(namedIdentifier)!!
        assertNotSame(named, target)
        assertNotEquals(named.getType().typeId, alias.typeId)
        assertEquals(sourceNamed.getType().typeId, named.getType().typeId)
        assertSame(target, assertIs<MCFPPDataTemplateType>(restoredScope.getType("Y")).template)
        assertTrue(assertIs<MCInt>(target.scope.getVar("value")).isDataOnly)
        assertNotSame(sourceTarget, target)
        assertEquals(sourceAnonymousId, alias.typeId)
        val prototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Box"))
        assertNotSame(sourcePrototype, prototype)
        val first = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
        val second = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("second")).templateType)
        assertSame(first, second)
        assertSame(prototype, first.originTemplate)
        assertNotSame(sourceFirst, first)
        val bound = assertIs<MCFPPTypeVar>(first.scope.getVar("T"))
        assertSame(target, assertIs<MCFPPDataTemplateType>(bound.value).template)
        assertSame(target, assertIs<MCFPPDataTemplateType>(first.scope.getType("T")).template)
        assertEquals(sourceAnonymousId, bound.value.typeId)
        assertEquals(sourceSnapshot, ValueSnapshot.of(bound))
        assertEquals(sourceBoxId, first.getType().typeId)
        assertSame(first, assertIs<MCFPPDataTemplateType>(restoredScope.functions.getValue("readBox").single().normalParams.single().type).template)
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
    }

    @Test fun genericFunctionDependentTypesBindBeforeRuntimeArgumentsAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data Box<T as type> {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            func relay<T as type>(arg as Box<(T)>)->Box<(T)> { return arg; }
            func main(){
                var T="caller";
                dynamic var firstInput=4; dynamic var secondInput=9; dynamic var thirdInput=7;
                var first=relay<int>(Box<int>(firstInput));
                var second=relay<int>(Box<int>(secondInput));
                var third=relay<bool>(Box<bool>(thirdInput));
                dynamic var firstResult=first.read();
                dynamic var secondResult=second.read();
                dynamic var thirdResult=third.read();
            }
        """, output)

        fun checkBindings(main: Function, prototype: GenericDataTemplate, relay: GenericFunction): Map<TypeId, Function> {
            val first = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
            val second = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("second")).templateType)
            val third = assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar("third")).templateType)
            assertSame(first, second)
            assertNotSame(first, third)
            assertNotEquals(first.getType().typeId, third.getType().typeId)
            assertSame(prototype, first.originTemplate)
            assertSame(prototype, third.originTemplate)
            assertEquals(2, relay.compiledFunctions.size)
            val wrappers = relay.compiledFunctions.values.associateBy {
                assertIs<MCFPPTypeVar>(it.scope.getVar("T")).value.typeId
            }
            assertEquals(setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId), wrappers.keys)
            for ((type, template) in listOf(MCFPPBaseType.Int to first, MCFPPBaseType.Bool to third)) {
                val wrapper = wrappers.getValue(type.typeId)
                assertSame(template, assertIs<MCFPPDataTemplateType>(wrapper.normalParams.single().type).template)
                assertSame(template, assertIs<MCFPPDataTemplateType>(wrapper.returnType).template)
                assertSame(type, assertIs<MCFPPTypeVar>(wrapper.scope.getVar("T")).value)
                assertSame(type, wrapper.scope.getType("T"))
                assertNotNull(ValueSnapshot.of(wrapper.scope.getVar("T")!!))
                assertSame(type, assertIs<MCFPPTypeVar>(template.scope.getVar("T")).value)
            }
            return wrappers
        }

        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourcePrototype = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Box"))
        val sourceRelay = assertIs<GenericFunction>(sourceScope.functions.getValue("relay").single())
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceWrappers = checkBindings(sourceMain, sourcePrototype, sourceRelay)
        val sourceTemplates = sourceWrappers.mapValues { assertIs<MCFPPDataTemplateType>(it.value.returnType).template }
        val sourceSnapshots = sourceWrappers.mapValues { assertNotNull(ValueSnapshot.of(it.value.scope.getVar("T")!!)) }
        val sourceKeys = sourceRelay.compiledFunctions.map { (key, wrapper) ->
            assertIs<MCFPPTypeVar>(wrapper.scope.getVar("T")).value.typeId to key.arguments
        }.toMap()

        val main = consume("""
            import fixture.fields:*;
            func main(){
                var T="caller";
                dynamic var thirdInput=7; dynamic var secondInput=9; dynamic var firstInput=4;
                var third=relay<bool>(Box<bool>(thirdInput));
                var second=relay<int>(Box<int>(secondInput));
                var first=relay<int>(Box<int>(firstInput));
                dynamic var firstResult=first.read();
                dynamic var secondResult=second.read();
                dynamic var thirdResult=third.read();
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val prototype = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Box"))
        val relay = assertIs<GenericFunction>(restoredScope.functions.getValue("relay").single())
        assertNotSame(sourcePrototype, prototype)
        assertNotSame(sourceRelay, relay)
        val wrappers = checkBindings(main, prototype, relay)
        for ((typeId, wrapper) in wrappers) {
            assertNotSame(sourceWrappers.getValue(typeId), wrapper)
            val template = assertIs<MCFPPDataTemplateType>(wrapper.returnType).template
            assertNotSame(sourceTemplates.getValue(typeId), template)
            assertEquals(sourceTemplates.getValue(typeId).getType().typeId, template.getType().typeId)
            assertEquals(sourceSnapshots.getValue(typeId), ValueSnapshot.of(wrapper.scope.getVar("T")!!))
        }
        assertEquals(sourceKeys, relay.compiledFunctions.map { (key, wrapper) ->
            assertIs<MCFPPTypeVar>(wrapper.scope.getVar("T")).value.typeId to key.arguments
        }.toMap())
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
        assertEquals(7, machine.read(main.scope.getVar("thirdResult") as MCInt))
    }

    @Test fun genericObjectReadonlyValuesShareCanonicalSpecializationsAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            object data Settings<N as int> {
                func read()->int { return N; }
            }
            func main(){
                dynamic var firstResult=(Settings<4>).read();
                dynamic var secondResult=(Settings<9>).read();
                dynamic var repeatedResult=(Settings<4>).read();
            }
        """, output)

        fun checkBindings(prototype: GenericObjectDataTemplate): Map<Int, CompiledGenericObjectDataTemplate> {
            assertSame(prototype, prototype.companionObject)
            assertEquals(2, prototype.compiledTemplates.size)
            val objects = prototype.compiledTemplates.values.associateBy {
                assertIs<MCIntConcrete>(it.scope.getVar("N")).value
            }
            assertEquals(setOf(4, 9), objects.keys)
            val result = objects.mapValues { (_, value) -> assertIs<CompiledGenericObjectDataTemplate>(value) }
            assertNotSame(result.getValue(4), result.getValue(9))
            for ((key, value) in prototype.compiledTemplates) {
                val compiled = assertIs<CompiledGenericObjectDataTemplate>(value)
                assertSame(prototype, compiled.originTemplate)
                assertSame(compiled, compiled.companionObject)
                val bound = assertIs<MCIntConcrete>(compiled.scope.getVar("N"))
                assertTrue(bound.isConst)
                val snapshot = assertNotNull(ValueSnapshot.of(bound))
                assertEquals(StorageLayout.CompilerOnly, assertNotNull(bound.storageBinding).data.layout)
                assertEquals(listOf(snapshot), key.arguments.map { assertIs<SpecializationArgument.Constant>(it).value })
                val typeId = assertIs<TypeId.Specialized>(compiled.getType().typeId)
                assertEquals(TypeId.Declaration("object", "fixture.fields", "Settings"), typeId.constructor)
                assertEquals(listOf(snapshot), typeId.arguments)
                assertSame(compiled, assertIs<MCFPPDataTemplateType>(compiled.getType()).template)
                assertSame(compiled, assertIs<MCFPPDataTemplateType>(MCFPPType.resolveTypeId(typeId)).template)
                val read = compiled.scope.functions.getValue("read").single()
                assertSame(compiled, read.owner)
                assertTrue(read.isStatic)
                val staticView = assertIs<StaticMemberView>(read.scope.getVar("this"))
                assertSame(compiled, assertIs<MCFPPDataTemplateType>(staticView.value).template)
            }
            assertNotEquals(result.getValue(4).getType().typeId, result.getValue(9).getType().typeId)
            return result
        }

        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourcePrototype = assertIs<GenericObjectDataTemplate>(sourceScope.getObject("Settings"))
        val sourceObjects = checkBindings(sourcePrototype)
        val sourceIds = sourceObjects.mapValues { it.value.getType().typeId }
        val sourceSnapshots = sourceObjects.mapValues { assertNotNull(ValueSnapshot.of(it.value.scope.getVar("N")!!)) }
        val main = consume("""
            import fixture.fields:*;
            func main(){
                dynamic var secondResult=(Settings<9>).read();
                dynamic var firstResult=(Settings<4>).read();
                dynamic var repeatedResult=(Settings<4>).read();
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val prototype = assertIs<GenericObjectDataTemplate>(restoredScope.getObject("Settings"))
        assertNotSame(sourcePrototype, prototype)
        val objects = checkBindings(prototype)
        for ((number, compiled) in objects) {
            assertNotSame(sourceObjects.getValue(number), compiled)
            assertNotSame(sourceObjects.getValue(number).scope.functions.getValue("read").single(),
                compiled.scope.functions.getValue("read").single())
            assertEquals(sourceIds.getValue(number), compiled.getType().typeId)
            assertEquals(sourceSnapshots.getValue(number), ValueSnapshot.of(compiled.scope.getVar("N")!!))
        }
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
        assertEquals(4, machine.read(main.scope.getVar("repeatedResult") as MCInt))
    }

    @Test fun frozenGenericInterfaceTypeArgumentsRestoreBoundSignaturesAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            func readIntBox(arg as Box<(Contract<int>)>)->int { return arg.read(); }
            func readBoolBox(arg as Box<(Contract<bool>)>)->int { return arg.read(); }
            interface Contract<T as type> {
                abstract func exchange(value as T)->T;
            }
            data Box<T as type> {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            func main(){
                dynamic var firstInput=4; dynamic var secondInput=9;
                var first=Box<(Contract<int>)>(firstInput);
                var second=Box<(Contract<bool>)>(secondInput);
                dynamic var firstResult=readIntBox(first);
                dynamic var secondResult=readBoolBox(second);
            }
        """, output)

        fun checkBindings(contract: GenericDataTemplate, box: GenericDataTemplate, main: Function,
                          readers: List<Function>): Map<TypeId, CompiledGenericDataTemplate> {
            assertTrue(contract.isInterface)
            assertTrue(contract.isAbstract)
            assertEquals(2, contract.compiledTemplates.size)
            assertEquals(2, box.compiledTemplates.size)
            val contracts = contract.compiledTemplates.values.associateBy {
                assertIs<MCFPPTypeVar>(it.scope.getVar("T")).value.typeId
            }
            assertEquals(setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId), contracts.keys)
            for ((key, compiled) in contract.compiledTemplates) {
                assertSame(contract, compiled.originTemplate)
                assertTrue(compiled.isInterface)
                assertTrue(compiled.isAbstract)
                assertTrue(compiled.constructors.isEmpty())
                val bound = assertIs<MCFPPTypeVar>(compiled.scope.getVar("T"))
                val snapshot = assertNotNull(ValueSnapshot.of(bound))
                assertTrue(bound.isConst)
                assertEquals(StorageLayout.CompilerOnly, assertNotNull(bound.storageBinding).data.layout)
                assertSame(bound.value, compiled.scope.getType("T"))
                val id = assertIs<TypeId.Specialized>(compiled.getType().typeId)
                assertEquals(TypeId.Declaration("interface", "fixture.fields", "Contract"), id.constructor)
                assertEquals(listOf(snapshot), id.arguments)
                assertEquals(id.arguments, key.arguments.map { assertIs<SpecializationArgument.Constant>(it).value })
                assertSame(compiled, assertIs<MCFPPDataTemplateType>(MCFPPType.resolveTypeId(id)).template)
                val exchange = compiled.scope.functions.getValue("exchange").single()
                assertTrue(exchange.isAbstract)
                assertSame(compiled, exchange.owner)
                assertSame(bound.value, exchange.normalParams.single().type)
                assertSame(bound.value, exchange.returnType)
            }
            val boxes = listOf("first", "second").map { name ->
                assertIs<CompiledGenericDataTemplate>(assertIs<DataTemplateObject>(main.scope.getVar(name)).templateType)
            }
            assertNotSame(boxes[0], boxes[1])
            val result = LinkedHashMap<TypeId, CompiledGenericDataTemplate>()
            for ((index, type) in listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool).withIndex()) {
                val compiled = boxes[index]
                assertSame(box, compiled.originTemplate)
                val bound = assertIs<MCFPPTypeVar>(compiled.scope.getVar("T"))
                val actual = assertIs<MCFPPDataTemplateType>(bound.value)
                assertSame(contracts.getValue(type.typeId), actual.template)
                assertSame(actual.template, assertIs<MCFPPDataTemplateType>(compiled.scope.getType("T")).template)
                assertSame(compiled, assertIs<MCFPPDataTemplateType>(readers[index].normalParams.single().type).template)
                val id = assertIs<TypeId.Specialized>(compiled.getType().typeId)
                assertEquals(listOf(assertNotNull(ValueSnapshot.of(bound))), id.arguments)
                val key = box.compiledTemplates.entries.single { it.value === compiled }.key
                assertEquals(id.arguments, key.arguments.map { assertIs<SpecializationArgument.Constant>(it).value })
                result[type.typeId] = compiled
            }
            return result
        }

        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourceContract = assertIs<GenericDataTemplate>(sourceScope.getInterface("Contract"))
        val sourceBox = assertIs<GenericDataTemplate>(sourceScope.getTemplate("Box"))
        val sourceBoxes = checkBindings(sourceContract, sourceBox, sourceScope.functions.getValue("main").single(),
            listOf(sourceScope.functions.getValue("readIntBox").single(), sourceScope.functions.getValue("readBoolBox").single()))
        val main = consume("""
            import fixture.fields:*;
            func main(){
                dynamic var secondInput=9; dynamic var firstInput=4;
                var second=Box<(Contract<bool>)>(secondInput);
                var first=Box<(Contract<int>)>(firstInput);
                dynamic var firstResult=readIntBox(first);
                dynamic var secondResult=readBoolBox(second);
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
        val contract = assertIs<GenericDataTemplate>(restoredScope.getInterface("Contract"))
        val box = assertIs<GenericDataTemplate>(restoredScope.getTemplate("Box"))
        assertNotSame(sourceContract, contract)
        assertNotSame(sourceBox, box)
        val boxes = checkBindings(contract, box, main,
            listOf(restoredScope.functions.getValue("readIntBox").single(), restoredScope.functions.getValue("readBoolBox").single()))
        for ((typeId, compiled) in boxes) {
            val source = sourceBoxes.getValue(typeId)
            assertNotSame(source, compiled)
            assertEquals(source.getType().typeId, compiled.getType().typeId)
            assertEquals(ValueSnapshot.of(source.scope.getVar("T")!!), ValueSnapshot.of(compiled.scope.getVar("T")!!))
            val sourceTarget = assertIs<MCFPPDataTemplateType>(assertIs<MCFPPTypeVar>(source.scope.getVar("T")).value).template
            val target = assertIs<MCFPPDataTemplateType>(assertIs<MCFPPTypeVar>(compiled.scope.getVar("T")).value).template
            assertNotSame(sourceTarget, target)
            assertEquals(sourceTarget.getType().typeId, target.getType().typeId)
            assertEquals(ValueSnapshot.of(sourceTarget.scope.getVar("T")!!), ValueSnapshot.of(target.scope.getVar("T")!!))
            assertNotSame(sourceTarget.scope.functions.getValue("exchange").single(), target.scope.functions.getValue("exchange").single())
        }
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
    }

    @Test fun sourceGenericSpecializationsExportAllRuntimeTargetsToDisk() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data Box<T as type> {
                private value as int;
                constructor(v as int){ this.value=v; }
                func read()->int { return this.value; }
            }
            func relay<T as type>(arg as Box<(T)>)->int { return arg.read(); }
            object data Settings<N as int> {
                func read()->int { return N; }
            }
            func main(){
                dynamic var firstInput=4; dynamic var secondInput=9;
                var first=Box<int>(firstInput); var second=Box<int>(secondInput);
                dynamic var firstResult=relay<int>(first);
                dynamic var secondResult=relay<int>(second);
                dynamic var firstStatic=(Settings<4>).read();
                dynamic var secondStatic=(Settings<9>).read();
            }
        """, output)
        val scope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val box = assertIs<GenericDataTemplate>(scope.getTemplate("Box"))
        val relay = assertIs<GenericFunction>(scope.functions.getValue("relay").single())
        val settings = assertIs<GenericObjectDataTemplate>(scope.getObject("Settings"))
        assertEquals(1, box.compiledTemplates.size)
        assertEquals(1, relay.compiledFunctions.size)
        assertEquals(2, settings.compiledTemplates.size)
        val compiledBox = box.compiledTemplates.values.single()
        val targets = listOf(compiledBox.constructors.single(), compiledBox.scope.functions.getValue("read").single()) +
            settings.compiledTemplates.values.map { it.scope.functions.getValue("read").single() }
        val main = scope.functions.getValue("main").single()
        // The existing executor reads only generated disk files; no library consumer or in-memory body supplies targets.
        val machine = execute(main, output)
        val data = output.resolve("consumer").resolve(Project.config.name).resolve("data")
        for (target in targets) {
            val (namespace, identifier) = target.namespaceID.toString().split(':', limit = 2)
            val file = data.resolve(namespace).resolve("function").resolve("$identifier.mcfunction")
            assertTrue(Files.isRegularFile(file), "Missing source specialization: ${target.namespaceID}")
        }
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
        assertEquals(4, machine.read(main.scope.getVar("firstStatic") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondStatic") as MCInt))
    }

    @Test fun generatedSpecializationNamesDoNotOverwriteLegalSourceDeclarations() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            object data Settings<N as int> {
                func read()->int { return N; }
            }
            object data Settings_int_0 {
                func read()->int { return 9; }
            }
            func relay<T as type>(arg as int)->int { return arg; }
            func relay_0(arg as int)->int { return arg+5; }
            func main(){
                dynamic var input=4;
                dynamic var generatedObject=(Settings<4>).read();
                dynamic var declaredObject=Settings_int_0.read();
                dynamic var generatedFunction=relay<int>(input);
                dynamic var declaredFunction=relay_0(input);
            }
        """, output)
        val scope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val settings = assertIs<GenericObjectDataTemplate>(scope.getObject("Settings"))
        val relay = assertIs<GenericFunction>(scope.functions.getValue("relay").single())
        assertEquals(1, settings.compiledTemplates.size)
        assertEquals(1, relay.compiledFunctions.size)
        val generatedObject = settings.compiledTemplates.values.single().scope.functions.getValue("read").single()
        val declaredObject = assertIs<ObjectDataTemplate>(scope.getObject("Settings_int_0")).scope.functions.getValue("read").single()
        val generatedFunction = relay.compiledFunctions.values.single()
        val declaredFunction = scope.functions.getValue("relay_0").single()
        val main = scope.functions.getValue("main").single()
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("generatedObject") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("declaredObject") as MCInt))
        assertEquals(4, machine.read(main.scope.getVar("generatedFunction") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("declaredFunction") as MCInt))
        assertNotEquals(generatedObject.namespaceID, declaredObject.namespaceID)
        assertNotEquals(generatedObject.owner!!.prefix, declaredObject.owner!!.prefix)
        assertNotEquals(generatedFunction.namespaceID, declaredFunction.namespaceID)
        assertNotEquals(generatedFunction.prefix, declaredFunction.prefix)
        assertEquals(4, listOf(generatedObject, declaredObject, generatedFunction, declaredFunction)
            .map { it.namespaceID }.toSet().size)
    }

    @Test fun genericObjectStaticFieldsInitializeIndependentlyAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            object data Settings<N as int> {
                value as int=N;
                constructor(){}
                func read()->int { return value; }
            }
            func main(){
                dynamic var readFirst=(Settings<4>).read();
                dynamic var readSecond=(Settings<9>).read();
                dynamic var directFirst=(Settings<4>).value;
                dynamic var directSecond=(Settings<9>).value;
            }
        """, output)

        fun initializeAndExecute(main: Function, settings: GenericObjectDataTemplate): ScoreCommandExecutor {
            assertEquals(2, settings.compiledTemplates.size)
            val initializer = Function("initialize", main.namespace, null)
            initializer.runInFunction {
                Function.addCommand(Commands.stackIn())
                settings.compiledTemplates.values.forEach { it.constructors.single().invoke(emptyList(), null) }
                Function.addCommand(Commands.stackOut())
            }
            main.commands.addAll(0, initializer.commands)
            assertEquals(0, Project.errorCount)
            val fields = settings.compiledTemplates.values.map { compiled ->
                assertIs<CompiledGenericObjectDataTemplate>(compiled)
                for (method in listOf(compiled.constructors.single(), compiled.scope.functions.getValue("read").single())) {
                    assertSame(compiled, method.owner)
                    assertTrue(method.isStatic)
                }
                val field = assertIs<MCInt>(compiled.scope.getVar("value"))
                assertSame(compiled, field.declaredParentTemplate)
                assertTrue(field.isStatic)
                assertTrue(field.isDynamic)
                field
            }
            assertNotEquals(fields[0].nbtPath, fields[1].nbtPath)
            return execute(main, output)
        }

        val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
        val sourceSettings = assertIs<GenericObjectDataTemplate>(sourceScope.getObject("Settings"))
        val sourceObjects = sourceSettings.compiledTemplates.values.associateBy {
            assertIs<MCIntConcrete>(it.scope.getVar("N")).value
        }
        val sourceMain = sourceScope.functions.getValue("main").single()
        val sourceMachine = initializeAndExecute(sourceMain, sourceSettings)
        for ((name, expected) in listOf("directFirst" to 4, "directSecond" to 9, "readFirst" to 4, "readSecond" to 9))
            assertEquals(expected, sourceMachine.read(sourceMain.scope.getVar(name) as MCInt))

        val main = consume("""
            import fixture.fields:*;
            func main(){
                dynamic var readFirst=(Settings<9>).read();
                dynamic var readSecond=(Settings<4>).read();
                dynamic var directFirst=(Settings<9>).value;
                dynamic var directSecond=(Settings<4>).value;
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val settings = assertIs<GenericObjectDataTemplate>(GlobalScope.libNamespaces.getValue("fixture.fields").scope.getObject("Settings"))
        assertNotSame(sourceSettings, settings)
        val machine = initializeAndExecute(main, settings)
        for (compiled in settings.compiledTemplates.values) {
            val number = assertIs<MCIntConcrete>(compiled.scope.getVar("N")).value
            assertNotSame(sourceObjects.getValue(number), compiled)
        }
        for ((name, expected) in listOf("directFirst" to 9, "directSecond" to 4, "readFirst" to 9, "readSecond" to 4))
            assertEquals(expected, machine.read(main.scope.getVar(name) as MCInt))
    }

    @Test fun templateModifiersSurviveLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data abstract Base { abstract func read()->int; }
            data Child : Base {
                private value as int;
                constructor(v as int){this.value=v;}
                override func read()->int{return this.value;}
            }
            data abstract Contract<T as type> { abstract func exchange(value as T)->T; }
            data Box<T as type> {
                private value as int;
                constructor(v as int){this.value=v;}
                func read()->int{return this.value;}
            }
            final data Closed {}
            final data GenericClosed<T as type> {}
            final object data ClosedObject {}
            final object data GenericClosedObject<N as int> { func read()->int{return N;} }
            func acceptClosed(value as Closed)->int{return 0;}
            func main(){
                var first=Child(4);
                var second=Child(9);
                var box=Box<(Contract<int>)>(4);
                var closed=GenericClosed<int>();
                dynamic var firstResult=first.read();
                dynamic var secondResult=second.read();
                dynamic var boxResult=box.read();
                dynamic var objectResult=(GenericClosedObject<4>).read();
            }
        """, output)
        fun check(namespace: top.mcfpp.model.Namespace): List<DataTemplate> {
            val scope = namespace.scope
            val base = scope.getTemplate("Base")!!
            assertTrue(base.isAbstract)
            assertTrue(base.constructors.isEmpty())
            assertTrue(base.scope.functions.getValue("read").single().isAbstract)
            val child = scope.getTemplate("Child")!!
            assertFalse(child.isAbstract)
            val contract = assertIs<GenericDataTemplate>(scope.getTemplate("Contract"))
            assertTrue(contract.isAbstract)
            assertFalse(contract.isInterface)
            assertTrue(contract.constructors.isEmpty())
            val actual = contract.compiledTemplates.values.single()
            assertTrue(actual.isAbstract)
            assertFalse(actual.isInterface)
            assertTrue(actual.constructors.isEmpty())
            val exchange = actual.scope.functions.getValue("exchange").single()
            assertTrue(exchange.isAbstract)
            assertSame(actual, exchange.owner)
            assertEquals(MCFPPBaseType.Int.typeId, exchange.normalParams.single().type.typeId)
            assertEquals(MCFPPBaseType.Int.typeId, exchange.returnType.typeId)
            val finals = listOf(scope.getTemplate("Closed")!!, scope.getTemplate("GenericClosed")!!,
                scope.getObject("ClosedObject") as DataTemplate, scope.getObject("GenericClosedObject") as DataTemplate)
            finals.forEach { assertTrue(it.isFinal) }
            val specialized = finals.filterIsInstance<GenericDataTemplate>().map { it.compiledTemplates.values.single() }
            specialized.forEach { assertTrue(it.isFinal) }
            return listOf(base, child, contract, actual) + finals + specialized
        }
        val sourceNamespace = GlobalScope.localNamespaces.getValue("fixture.fields")
        val sourceModels = check(sourceNamespace)
        val sourceMain = sourceNamespace.scope.functions.getValue("main").single()
        val sourceMachine = execute(sourceMain, output)
        for ((name, expected) in listOf("firstResult" to 4, "secondResult" to 9, "boxResult" to 4, "objectResult" to 4))
            assertEquals(expected, sourceMachine.read(sourceMain.scope.getVar(name) as MCInt))
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var second=Child(9);
                var first=Child(4);
                var box=Box<(Contract<int>)>(4);
                var closed=GenericClosed<int>();
                dynamic var firstResult=first.read();
                dynamic var secondResult=second.read();
                dynamic var boxResult=box.read();
                dynamic var objectResult=(GenericClosedObject<4>).read();
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val restoredModels = check(GlobalScope.libNamespaces.getValue("fixture.fields"))
        sourceModels.zip(restoredModels).forEach { (source, restored) -> assertNotSame(source, restored) }
        val machine = execute(main, output)
        for ((name, expected) in listOf("firstResult" to 4, "secondResult" to 9, "boxResult" to 4, "objectResult" to 4))
            assertEquals(expected, machine.read(main.scope.getVar(name) as MCInt))
    }

    @Test fun finalTemplateParentsAreRejectedAcrossLibraryRoundTrip() = withLibrary { output ->
        val declarations = """
            final data Closed {}
            final object data ClosedObject {}
        """.trimIndent()
        write("namespace fixture.fields;\n$declarations\nfunc main(){}", output)
        for ((child, parentName) in listOf("data Child : Closed {}" to "Closed",
            "data Child<T as type> : Closed {}" to "Closed", "data Child : ClosedObject {}" to "ClosedObject")) {
            Project.config.includes = arrayListOf()
            MCFPPStringTest.readFromString("namespace fixture.fields;\n$declarations\n$child\nfunc main(){}", version = "26.3")
            assertTrue(Project.errorCount > 0, "Source must reject final parent: $child")
            val sourceScope = GlobalScope.localNamespaces.getValue("fixture.fields").scope
            val sourceParent = sourceScope.getTemplate(parentName) ?: sourceScope.getObject(parentName) as DataTemplate
            assertTrue(sourceParent.isFinal)
            assertFalse(sourceScope.getTemplate("Child")!!.parent.any { it === sourceParent })
            consume("import fixture.fields:*;\n$child\nfunc main(){}", output)
            assertTrue(Project.errorCount > 0, "Consumer must reject restored final parent: $child")
            val restoredScope = GlobalScope.libNamespaces.getValue("fixture.fields").scope
            val restoredParent = restoredScope.getTemplate(parentName) ?: restoredScope.getObject(parentName) as DataTemplate
            assertTrue(restoredParent.isFinal)
            assertFalse(GlobalScope.localNamespaces.getValue("default.test").scope.getTemplate("Child")!!.parent.any { it === restoredParent })
        }
    }

    @Test fun genericParentArgumentsBindCanonicalMembersAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            interface BaseMarker {}
            interface DerivedMarker : BaseMarker {}
            data Parent<T as type> {
                protected value as T;
                constructor(v as T){this.value=v;}
                func read()->T{return this.value;}
            }
            data IntChild : Parent<int> { constructor(v as int){this.value=v;} }
            data Child<T as type> : Parent<(T)> { constructor(v as T){this.value=v;} }
            data Offset<N as int> { func number()->int{return N;} }
            data Shift<N as int> : Offset<(N+1)> {}
            func main(){
                dynamic var input4=4; dynamic var input9=9;
                var first=IntChild(input4); var second=Child<int>(input9); var flag=Child<bool>(true);
                var shifted=Shift<4>();
                dynamic var firstResult=first.read(); dynamic var secondResult=second.read();
                dynamic var flagResult=flag.read(); dynamic var numberResult=shifted.number();
                /scoreboard players set #parent_bool result 0
                if(flagResult){
                    /scoreboard players set #parent_bool result 1
                }
            }
        """, output)
        fun check(namespace: top.mcfpp.model.Namespace, main: Function, shiftValue: Int): List<DataTemplate> {
            val scope = namespace.scope
            val baseMarker = scope.getInterface("BaseMarker")!!
            assertTrue(scope.getInterface("DerivedMarker")!!.parent.any { it === baseMarker })
            val parent = assertIs<GenericDataTemplate>(scope.getTemplate("Parent"))
            val child = assertIs<GenericDataTemplate>(scope.getTemplate("Child"))
            assertEquals(2, parent.compiledTemplates.size)
            assertEquals(2, child.compiledTemplates.size)
            val actualParents = parent.compiledTemplates.values.associateBy { it.scope.getType("T")!!.typeId }
            val actualChildren = child.compiledTemplates.values.associateBy { it.scope.getType("T")!!.typeId }
            val orderedTypes = listOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId)
            val orderedParents = orderedTypes.map { actualParents.getValue(it) }
            val orderedChildren = orderedTypes.map { actualChildren.getValue(it) }
            val intChild = scope.getTemplate("IntChild")!!
            val intParent = actualParents.getValue(MCFPPBaseType.Int.typeId)
            assertTrue(intChild.parent.any { it === intParent })
            val children = listOf(intChild) + orderedChildren
            for (actualChild in children) {
                val type = if (actualChild === intChild) MCFPPBaseType.Int else actualChild.scope.getType("T")!!
                val actualParent = actualParents.getValue(type.typeId)
                assertTrue(actualChild.parent.any { it === actualParent })
                val field = actualChild.scope.getVar("value")!!
                assertEquals(type.typeId, field.type.typeId)
                assertSame(actualParent, field.declaredParentTemplate)
                val reader = actualChild.scope.getFunction("read", emptyList(), emptyList())
                assertSame(actualParent, reader.owner)
                assertEquals(type.typeId, reader.returnType.typeId)
                assertTrue(actualChild.getType().isSubOf(actualParent.getType()))
            }
            assertSame(intChild, assertIs<DataTemplateObject>(main.scope.getVar("first")).templateType)
            val shift = assertIs<GenericDataTemplate>(scope.getTemplate("Shift")).compiledTemplates.values.single()
            val offset = assertIs<GenericDataTemplate>(scope.getTemplate("Offset")).compiledTemplates.values.single()
            assertEquals(shiftValue, assertIs<MCIntConcrete>(shift.scope.getVar("N")).value)
            assertTrue(shift.parent.any { it === offset })
            assertEquals(shiftValue + 1, assertIs<MCIntConcrete>(offset.scope.getVar("N")).value)
            assertSame(offset, shift.scope.getFunction("number", emptyList(), emptyList()).owner)
            return listOf(parent, child, intChild) + orderedParents + orderedChildren + listOf(shift, offset)
        }
        val sourceNamespace = GlobalScope.localNamespaces.getValue("fixture.fields")
        val sourceMain = sourceNamespace.scope.functions.getValue("main").single()
        val sourceModels = check(sourceNamespace, sourceMain, 4)
        val sourceIds = sourceModels.take(7).map { it.getType().typeId }
        val sourceMachine = execute(sourceMain, output)
        assertEquals(4, sourceMachine.read(sourceMain.scope.getVar("firstResult") as MCInt))
        assertEquals(9, sourceMachine.read(sourceMain.scope.getVar("secondResult") as MCInt))
        assertEquals(1, sourceMachine.values.getValue("#parent_bool result"))
        assertEquals(5, sourceMachine.read(sourceMain.scope.getVar("numberResult") as MCInt))
        val main = consume("""
            import fixture.fields:*;
            data Parent {}
            func main(){
                dynamic var input4=4; dynamic var input9=9;
                var flag=Child<bool>(true); var second=Child<int>(input9); var first=IntChild(input4);
                var shifted=Shift<9>();
                dynamic var firstResult=first.read(); dynamic var secondResult=second.read();
                dynamic var flagResult=flag.read(); dynamic var numberResult=shifted.number();
                /scoreboard players set #parent_bool result 0
                if(flagResult){
                    /scoreboard players set #parent_bool result 1
                }
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val models = check(GlobalScope.libNamespaces.getValue("fixture.fields"), main, 9)
        sourceModels.zip(models).forEach { (source, restored) -> assertNotSame(source, restored) }
        assertEquals(sourceIds.toSet(), models.take(7).map { it.getType().typeId }.toSet())
        val machine = execute(main, output)
        assertEquals(4, machine.read(main.scope.getVar("firstResult") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("secondResult") as MCInt))
        assertEquals(1, machine.values.getValue("#parent_bool result"))
        assertEquals(10, machine.read(main.scope.getVar("numberResult") as MCInt))
    }

    @Test
    fun nativeListClearUsesExplicitReceiverContextAcrossLibraryRoundTrip() = withLibrary { output ->
        write("""
            namespace fixture.fields;
            data Box {
                items as list<int>;
                constructor(){this.items=[2,3];}
                func reset()->int {
                    this.items.clear();
                    this.items.add(7);
                    return this.items[0];
                }
            }
            func main(){
                var box=Box();
                dynamic var result=box.reset();
            }
        """, output)
        val sourceMain = GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single()
        val sourceMachine = execute(sourceMain, output)
        assertEquals(7, sourceMachine.read(sourceMain.scope.getVar("result") as MCInt))
        val main = consume("""
            import fixture.fields:*;
            func main(){
                var box=Box();
                dynamic var result=box.reset();
            }
        """, output)
        assertEquals(0, Project.errorCount)
        val machine = execute(main, output)
        assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test
    fun nativeListMethodsUseArgumentAndResultReferencesAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = """
            func main(){
                dynamic var index=2;
                dynamic var two=2; dynamic var three=3; dynamic var absent=9;
                var mutationBox=Box(); var bulkBox=Box(); var queryBox=Box();
                dynamic var mutationResult=mutationBox.mutation(index);
                dynamic var bulkResult=bulkBox.bulk();
                dynamic var sourceResult=bulkBox.extra[0];
                dynamic var firstQuery=queryBox.query(two);
                dynamic var secondQuery=queryBox.query(three);
                dynamic var absentQuery=queryBox.query(absent);
            }
        """
        write("""
            namespace fixture.fields;
            data Box {
                items as list<int>; extra as list<int>;
                constructor(){this.items=[2,3,2];this.extra=[4,5];}
                func mutation(index as int)->int {
                    this.items.prepend(1); this.items.add(4);
                    this.items.insert(index,8); this.items.removeAt(-1); this.items.remove(8);
                    return this.items[2];
                }
                func bulk()->int {
                    this.items.addAll(this.extra); this.items.prependAll(this.extra);
                    return this.items[0]+this.items[6];
                }
                func query(needle as int)->int {
                    var first=this.items.indexOf(needle);
                    var last=this.items.lastIndexOf(needle);
                    if(this.items.contains(needle)){return first*10+last;}
                    return -1;
                }
            }
            $mainSource
        """, output)
        fun check(main: Function) {
            val machine = execute(main, output)
            for ((name, expected) in listOf("mutationResult" to 3, "bulkResult" to 9, "sourceResult" to 4,
                    "firstQuery" to 2, "secondQuery" to 11, "absentQuery" to -1)) {
                assertEquals(expected, machine.read(main.scope.getVar(name) as MCInt), name)
            }
        }
        check(GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single())
        val main = consume("import fixture.fields:*;\n$mainSource", output)
        assertEquals(0, Project.errorCount)
        check(main)
    }

    @Test
    fun nativeCollectionMethodsUseExplicitContextAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = """
            func main(){
                var box=Box();
                dynamic var key="first";
                dynamic var two=2; dynamic var three=3; dynamic var absent=9;
                dynamic var dictionaryResult=box.dictionary();
                dynamic var dictionarySource=box.dictionaryExtra["added"];
                dynamic var mapResult=box.mapEdit(key);
                dynamic var mapSource=box.mapExtra["third"];
                dynamic var firstQuery=box.query(two);
                dynamic var secondQuery=box.query(three);
                dynamic var absentQuery=box.query(absent);
            }
        """
        write("""
            namespace fixture.fields;
            data Box {
                dictionaryItems as dict<int>; dictionaryExtra as dict<int>;
                mapItems as map<int>; mapExtra as map<int>;
                readOnlyItems as ImmutableList<int>;
                constructor(){
                    this.dictionaryItems={gone:2,kept:3} as dict<int>; this.dictionaryExtra={added:5} as dict<int>;
                    this.mapItems={entries:[{key:"first",value:2},{key:"second",value:3}]} as map<int>;
                    this.mapExtra={entries:[{key:"third",value:7}]} as map<int>;
                    this.readOnlyItems=[2,3,2] as ImmutableList<int>;
                }
                func dictionary()->int {
                    var before=this.dictionaryItems.containsKey("gone");
                    this.dictionaryItems.remove("gone");
                    var gone=this.dictionaryItems.containsKey("gone");
                    this.dictionaryItems.merge(this.dictionaryExtra);
                    var merged=this.dictionaryItems.containsKey("added");
                    var copied=this.dictionaryItems["added"];
                    this.dictionaryItems.clear();
                    var cleared=this.dictionaryItems.containsKey("added");
                    if(before&&!gone&&merged&&!cleared){return copied+this.dictionaryExtra["added"];}
                    return -1;
                }
                func mapEdit(key as string)->int {
                    var firstSize=this.mapItems.size();
                    var found=this.mapItems.containsKey(key);
                    this.mapItems.remove(key); this.mapItems.merge(this.mapExtra);
                    var afterSize=this.mapItems.size();
                    var present=this.mapItems.containsKey("third");
                    var copied=this.mapItems["third"];
                    this.mapItems.clear();
                    var empty=this.mapItems.isEmpty();
                    var clearedSize=this.mapItems.size();
                    if(found&&present&&empty){return firstSize*100+afterSize*10+copied+clearedSize;}
                    return -1;
                }
                func query(needle as int)->int {
                    var first=this.readOnlyItems.indexOf(needle);
                    var last=this.readOnlyItems.lastIndexOf(needle);
                    if(this.readOnlyItems.contains(needle)){return first*10+last;}
                    return -1;
                }
            }
            $mainSource
        """, output)
        fun check(main: Function) {
            val machine = execute(main, output)
            for ((name, expected) in listOf("dictionaryResult" to 10, "dictionarySource" to 5,
                    "mapResult" to 227, "mapSource" to 7, "firstQuery" to 2,
                    "secondQuery" to 11, "absentQuery" to -1)) {
                assertEquals(expected, machine.read(main.scope.getVar(name) as MCInt), name)
            }
        }
        check(GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single())
        val main = consume("import fixture.fields:*;\n$mainSource", output)
        assertEquals(0, Project.errorCount)
        check(main)
    }

    @Test
    fun nativePrimitiveOperatorsUseExplicitContextAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = """
            func main(){
                var box=Box();
                dynamic var a=17; dynamic var b=5; dynamic var outside=27;
                dynamic var x as float=7.5; dynamic var y as float=2.0;
                dynamic var yes=true; dynamic var no=false;
                dynamic var integerResult=box.integer(a,b);
                dynamic var floatingResult=box.floating(x,y);
                dynamic var insideResult=box.ranged(a);
                dynamic var outsideResult=box.ranged(outside);
                dynamic var firstLogical=box.logical(yes,no);
                dynamic var secondLogical=box.logical(no,yes);
                dynamic var thirdLogical=box.logical(yes,yes);
            }
        """
        write("""
            namespace fixture.fields;
            data Box {
                func integer(a as int,b as int)->int {
                    if(a+b!=22){return -1;}
                    if(a-b!=12){return -2;}
                    if(a*b!=85){return -3;}
                    if(a/b!=3){return -4;}
                    if(a%b!=2){return -5;}
                    if(a>b&&b<a&&a>=b&&b<=a&&a!=b&&a==a){return 1;}
                    return 0;
                }
                func floating(a as float,b as float)->int {
                    if(a+b!=9.5){return -1;}
                    if(a-b!=5.5){return -2;}
                    if(a*b!=15.0){return -3;}
                    if(a/b!=3.75){return -4;}
                    if(a%b!=1.5){return -5;}
                    if(a>b&&b<a&&a>=b&&b<=a&&a!=b&&a==a){return 1;}
                    return 0;
                }
                func ranged(value as int)->int {
                    if(value~=10..20){return 1;}
                    return 0;
                }
                func logical(a as bool,b as bool)->bool {
                    return (a!=b)&&!(a==b)&&(a||b)&&!(a&&b)&&!(!a);
                }
            }
            $mainSource
        """, output)
        fun check(main: Function) {
            val machine = execute(main, output)
            for ((name, expected) in listOf("integerResult" to 1, "floatingResult" to 1,
                    "insideResult" to 1, "outsideResult" to 0)) {
                assertEquals(expected, machine.read(main.scope.getVar(name) as MCInt), name)
            }
            for ((name, expected) in listOf("firstLogical" to 1, "secondLogical" to 0, "thirdLogical" to 0)) {
                assertEquals(expected, machine.read(main.scope.getVar(name) as ScoreBool), name)
            }
        }
        check(GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single())
        val main = consume("import fixture.fields:*;\n$mainSource", output)
        assertEquals(0, Project.errorCount)
        check(main)
    }

    @Test
    fun nativeStaticConversionsUseArgumentContextAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = """
            func main(){
                var box=Box();
                dynamic var narrowInput=255; dynamic var promoteInput=17;
                dynamic var flag=true; dynamic var stringInput="hello";
                dynamic var payload as nbt={value:7} as nbt;
                dynamic var narrowed=box.narrow(narrowInput);
                dynamic var promoted=box.promote(promoteInput);
                dynamic var booleanPayload=box.boolPayload(flag);
                dynamic var stringPayload=box.stringPayload(stringInput);
                dynamic var nbtPayload=box.nbtPayload(payload);
                /data modify storage mcfpp:system temp.native_conversion_bool set from storage mcfpp:system stack_frame[0].booleanPayload
                /data modify storage mcfpp:system temp.native_conversion_string set from storage mcfpp:system stack_frame[0].stringPayload
                /data modify storage mcfpp:system temp.native_conversion_nbt set from storage mcfpp:system stack_frame[0].nbtPayload
            }
        """
        write("""
            namespace fixture.fields;
            func decodeByte(value as byte)->int = top.mcfpp.mni.ConversionData.toIntFromByte;
            data Box {
                func narrow(value as int)->int {return decodeByte(toByte(value));}
                func promote(value as int)->int {return toInt(toFloat(value));}
                func boolPayload(value as bool)->nbt {return toNBT(value);}
                func stringPayload(value as string)->nbt {return toNBT(value);}
                func nbtPayload(value as nbt)->nbt {return toNBT(value);}
            }
            $mainSource
        """, output)
        fun check(main: Function) {
            val machine = execute(main, output)
            assertEquals(-1, machine.read(main.scope.getVar("narrowed") as MCInt))
            assertEquals(17, machine.read(main.scope.getVar("promoted") as MCInt))
            assertEquals(Tag.toNBT("1b"), machine.readNbt("mcfpp:system", "temp.native_conversion_bool"))
            assertEquals(Tag.toNBT("\"hello\""), machine.readNbt("mcfpp:system", "temp.native_conversion_string"))
            assertEquals(Tag.toNBT("{value:7}"), machine.readNbt("mcfpp:system", "temp.native_conversion_nbt"))
        }
        check(GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single())
        val main = consume("import fixture.fields:*;\n$mainSource", output)
        assertEquals(0, Project.errorCount)
        check(main)
    }

    @Test
    fun nativeTextMethodsUseReceiverContextAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = """
            func main(){
                var box=Box();
                dynamic var integerInput=7; dynamic var stringInput="live";
                dynamic var nbtInput as nbt={value:9} as nbt;
                dynamic var result=box.observe(integerInput,stringInput,nbtInput);
            }
        """
        write("""
            namespace fixture.fields;
            data Box {
                func observe(v as int,s as string,n as nbt)->int {
                    dynamic var runtimeInt=v.toText();
                    dynamic var constantInt=(4).toText();
                    dynamic var runtimeString=s.toText();
                    dynamic var constantString=("fixed").toText();
                    dynamic var runtimeNbt=n.toText();
                    dynamic var constantNbt=toNBT(3).toText();
                    /data modify storage fixture:observed runtimeInt set from storage mcfpp:system stack_frame[0].runtimeInt
                    /data modify storage fixture:observed constantInt set from storage mcfpp:system stack_frame[0].constantInt
                    /data modify storage fixture:observed runtimeString set from storage mcfpp:system stack_frame[0].runtimeString
                    /data modify storage fixture:observed constantString set from storage mcfpp:system stack_frame[0].constantString
                    /data modify storage fixture:observed runtimeNbt set from storage mcfpp:system stack_frame[0].runtimeNbt
                    /data modify storage fixture:observed constantNbt set from storage mcfpp:system stack_frame[0].constantNbt
                    return 7;
                }
            }
            $mainSource
        """, output)
        fun check(main: Function) {
            val machine = execute(main, output)
            assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
            for ((name, value) in listOf("constantInt" to "4", "constantString" to "fixed", "constantNbt" to "3")) {
                assertEquals(Tag.toNBT("""[{type:"text",text:"$value"}]"""), machine.readNbt("fixture:observed", name))
            }
            val scoreName = (machine.readNbt("fixture:observed", "runtimeInt[0].score.name") as top.mcfpp.nbt.tags.primitive.StringTag).value
            val objective = (machine.readNbt("fixture:observed", "runtimeInt[0].score.objective") as top.mcfpp.nbt.tags.primitive.StringTag).value
            assertEquals(Tag.toNBT("""[{type:"score",score:{name:"$scoreName",objective:"$objective"}}]"""), machine.readNbt("fixture:observed", "runtimeInt"))
            assertEquals(7, machine.values.getValue("$scoreName $objective"))
            for ((name, parameter) in listOf("runtimeString" to "s", "runtimeNbt" to "n")) {
                assertEquals(Tag.toNBT("""[{type:"nbt",storage:"mcfpp:system",nbt:"stack_frame[0].$parameter",interpret:false}]"""), machine.readNbt("fixture:observed", name))
            }
        }
        check(GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single())
        val main = consume("import fixture.fields:*;\n$mainSource", output)
        assertEquals(0, Project.errorCount)
        check(main)
    }

    @Test
    fun nativeTextConcatenationUsesOneSignatureAcrossLibraryRoundTrip() = withLibrary { output ->
        val observations = linkedMapOf(
            "joinedRuntime" to "LR", "suffixedRuntime" to "LS", "joinedConstant" to "AB", "suffixedConstant" to "AS",
            "left" to "L", "right" to "R", "originalA" to "A", "originalB" to "B",
            "leftCopy" to "L", "rightCopy" to "R", "constantLeftCopy" to "A", "constantRightCopy" to "B"
        )
        val copies = observations.keys.joinToString("\n") { name ->
            "/data modify storage fixture:observed $name set from storage mcfpp:system stack_frame[0].$name"
        }
        val mainSource = """
            func main(){
                var box=Box();
                dynamic var leftInput as text=("L").toText();
                dynamic var rightInput as text=("R").toText();
                dynamic var result=box.observe(leftInput,rightInput);
            }
        """
        write("""
            namespace fixture.fields;
            data Box {
                func observe(left as text,right as text)->int {
                    dynamic var leftCopy=left; dynamic var rightCopy=right;
                    var constantLeft=("A").toText(); var constantRight=("B").toText();
                    dynamic var constantLeftCopy=constantLeft; dynamic var constantRightCopy=constantRight;
                    dynamic var joinedRuntime=left+right;
                    dynamic var suffixedRuntime=left+"S";
                    dynamic var joinedConstant=("A").toText()+("B").toText();
                    dynamic var suffixedConstant=("A").toText()+"S";
                    dynamic var originalA=constantLeft; dynamic var originalB=constantRight;
                    $copies
                    return 7;
                }
            }
            $mainSource
        """, output)
        fun check(main: Function) {
            val machine = execute(main, output)
            assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
            for ((name, expected) in observations) {
                val payload = assertIs<top.mcfpp.nbt.tags.collection.ListTag>(machine.readNbt("fixture:observed", name))
                assertTrue(payload.isNotEmpty(), name)
                val actual = payload.joinToString("") { element ->
                    val component = assertIs<top.mcfpp.nbt.tags.CompoundTag>(element)
                    assertEquals(Tag.toNBT("\"text\""), component["type"], name)
                    assertIs<top.mcfpp.nbt.tags.primitive.StringTag>(component["text"]).value
                }
                assertEquals(expected, actual, name)
            }
        }
        check(GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single())
        val main = consume("import fixture.fields:*;\n$mainSource", output)
        assertEquals(0, Project.errorCount)
        check(main)
    }

    @Test
    fun knownTextCopiesAndConcatenationPreserveCompileTimeValuesAcrossLibraryRoundTrip() = withLibrary { output ->
        val mainSource = """
            func main(){
                var box=Box();
                dynamic var result=box.observe();
            }
        """
        write("""
            namespace fixture.fields;
            data Box {
                func observe()->int {
                    var original as text! = ("A").toText();
                    var copied as text! = original;
                    var joined as text! = ("A").toText()+("B").toText();
                    var suffixed as text! = ("A").toText()+"S";
                    /data modify storage fixture:observed original set from storage mcfpp:system stack_frame[0].original
                    /data modify storage fixture:observed copied set from storage mcfpp:system stack_frame[0].copied
                    /data modify storage fixture:observed joined set from storage mcfpp:system stack_frame[0].joined
                    /data modify storage fixture:observed suffixed set from storage mcfpp:system stack_frame[0].suffixed
                    return 7;
                }
            }
            $mainSource
        """, output)
        fun check(main: Function) {
            val machine = execute(main, output)
            assertEquals(7, machine.read(main.scope.getVar("result") as MCInt))
            for ((name, expected) in listOf("original" to "A", "copied" to "A", "joined" to "AB", "suffixed" to "AS")) {
                val payload = assertIs<top.mcfpp.nbt.tags.collection.ListTag>(machine.readNbt("fixture:observed", name))
                assertTrue(payload.isNotEmpty(), name)
                val actual = payload.joinToString("") { element ->
                    val component = assertIs<top.mcfpp.nbt.tags.CompoundTag>(element)
                    assertEquals(Tag.toNBT("\"text\""), component["type"], name)
                    assertIs<top.mcfpp.nbt.tags.primitive.StringTag>(component["text"]).value
                }
                assertEquals(expected, actual, name)
            }
        }
        check(GlobalScope.localNamespaces.getValue("fixture.fields").scope.functions.getValue("main").single())
        val main = consume("import fixture.fields:*;\n$mainSource", output)
        assertEquals(0, Project.errorCount)
        check(main)
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
