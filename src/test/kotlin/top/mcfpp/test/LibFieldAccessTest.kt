package top.mcfpp.test

import com.esotericsoftware.kryo.io.Input
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.ValueSnapshot
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.nbt.NBTListConcrete
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinFormat
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPUnionType
import top.mcfpp.type.MCFPPVectorType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.TypeId
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
                var T=bool; var N=90;
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
                var T=bool; var N=90;
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
                var leafType=Leaf;
                var types as list<type> = [leafType];
                var first=Bundle<types>(Cell<Leaf>(Leaf(4)));
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
                var leafType=Leaf;
                var types as list<type> = [leafType];
                var first=Bundle<types>(Cell<Leaf>(Leaf(4)));
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
