package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.ValueSnapshot
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.io.DatapackCreator
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class DeclarationInitializationContractTest {
    @Test fun declarationsBindSignaturesAndPropertiesWithoutExecutingInitializers() = isolated { output ->
        val declarations = """
            namespace fixture.declarations;
            func identity(value as any)->any{return value;}
            data Leaf { value as int; constructor(value as int){this.value=value;} }
            data Box {
                var first=1;
                var second=this.first;
                var erased=identity(1);
                var declaredAny as any=1;
                var copiedAny=this.declaredAny;
                var promoted as float=1;
                var copiedFloat=this.promoted;
                var bytes=[B;1b,2b];
                private backing as int;
                nested as Leaf;
                observed as int { get=backing as int; set {this.backing=value;} }
                chained as int {get=this.nested.value;}
                computed as int {get {return 7;}}
                constructor(value as int){
                    /say constructed
                    this.backing=value;this.nested=Leaf(value);
                }
                func read()->int{return this.observed;}
            }
        """
        val main = """
            func main(){
                var first=Box(4);var second=Box(9);
                dynamic var firstResult=first.read();dynamic var secondResult=second.read();
                first.observed=7;
                dynamic var changed=first.observed;dynamic var computed=first.computed;
                dynamic var nested=second.chained;
                var erasedInt=identity(1);var erasedString=identity("other");
            }
        """
        roundTrip(declarations, main, output) { function, machine ->
            for ((name, expected) in listOf("firstResult" to 4, "secondResult" to 9, "changed" to 7, "computed" to 7, "nested" to 9))
                assertEquals(expected, machine.read(function.scope.getVar(name) as MCInt))
            assertEquals(listOf("constructed", "constructed"), machine.messages)
            val template = (function.scope.getVar("first") as DataTemplateObject).templateType
            assertEquals(MCFPPBaseType.Any, template.scope.getVar("erased")!!.type)
            assertEquals(MCFPPBaseType.Any, template.scope.getVar("copiedAny")!!.type)
            assertEquals(MCFPPBaseType.Float, template.scope.getVar("copiedFloat")!!.type)
            assertEquals(MCFPPNBTType.ByteArray, template.scope.getVar("bytes")!!.type)
            assertEquals(MCFPPBaseType.Any, function.scope.getVar("erasedInt")!!.type)
            assertEquals(MCFPPBaseType.Any, function.scope.getVar("erasedString")!!.type)
            val identity = GlobalScope.getFunctionCandidates("fixture.declarations", "identity", null).single()
            assertEquals(MCFPPBaseType.Any, identity.normalParams.single().type)
            assertTrue(identity.compiledFunctions.isEmpty())
            assertNull(ValueSnapshot.of(identity.scope.getVar("value")))
        }
    }

    @Test fun compilerOnlyCallsBindActualPayloadsAndDoNotCacheOrdinaryValues() = isolated { output ->
        val declarations = """
            namespace fixture.payloads;
            func observe(position as pos3)->int=top.mcfpp.test.DeclarationInitializationContractTest.observe;
            func erased(position as any)->int{return observe(position as pos3);}
            data PositionBox {
                private position as any;
                constructor(position as pos3){
                    /say constructed
                    this.position=position;
                }
                func read()->int{return observe(this.position as pos3);}
                func change(position as pos3)->int{this.position=position;return observe(this.position as pos3);}
                func unused(position as pos3)->int{return observe(position);}
            }
            object data PositionState {
                private position as any=4 0 0;
                func read()->int{return observe(position as pos3);}
                func change(next as pos3)->int{position=next;return observe(position as pos3);}
            }
        """
        val main = """
            func main(){
                var first=PositionBox(4 0 0);var second=PositionBox(9 0 0);
                dynamic var firstResult=first.read();dynamic var secondResult=second.read();
                dynamic var changed=first.change(7 0 0);dynamic var readAgain=first.read();
                dynamic var otherAgain=second.read();dynamic var erasedResult=erased(9 0 0);
                dynamic var objectFirst=PositionState.read();dynamic var objectChanged=PositionState.change(7 0 0);
                dynamic var objectAgain=PositionState.read();
            }
        """
        roundTrip(declarations, main, output, startup=true) { function, machine ->
            for ((name, expected) in listOf("firstResult" to 4, "secondResult" to 9, "changed" to 7,
                "readAgain" to 7, "otherAgain" to 9, "erasedResult" to 9,
                "objectFirst" to 4,"objectChanged" to 7,"objectAgain" to 7))
                assertEquals(expected, machine.read(function.scope.getVar(name) as MCInt))
            assertEquals(listOf("constructed", "constructed", "4 0 0", "9 0 0", "7 0 0", "7 0 0", "9 0 0", "9 0 0","4 0 0","7 0 0","7 0 0"), machine.messages)
            val template = (function.scope.getVar("first") as DataTemplateObject).templateType
            assertFalse(template.scope.functions.getValue("unused").single().bodyCompiled)
            assertTrue(template.scope.functions.getValue("read").single().compiledFunctions.isEmpty())
            assertNotEquals(ValueSnapshot.of(function.scope.getVar("first")), ValueSnapshot.of(function.scope.getVar("second")))
        }
    }

    @Test fun inferredVarianceUsesOriginalNestedFormalTypes() = isolated { output ->
        roundTrip("""
            namespace fixture.constfirst;
            data Fixed {const first as int;constructor(value as int){this.first=value;}
                func read()->int{return first;}}
        """, "func main(){var a=Fixed(4);var b=Fixed(9);dynamic var first=a.read();dynamic var second=b.read();}",output) { function,machine ->
            assertEquals(4,machine.read(function.scope.getVar("first") as MCInt))
            assertEquals(9,machine.read(function.scope.getVar("second") as MCInt))
        }
        compile("import fixture.constfirst:*;func main(){var item=Fixed(4);item.first=9;}")
        assertEquals(1,Project.errorCount)
        Project.config.includes=arrayListOf()
        compile("""
            data Wrapper<out T as type>{constructor(){}}
            data Good<out T as type>{const item=Wrapper<T>();}
            func main(){var item=Good<int>();}
        """, output)
        assertEquals(0, Project.errorCount)
        Project.config.includes=arrayListOf(output.toString())
        compile("func main(){var item=Good<int>();}")
        assertEquals(0, Project.errorCount)
        for (declaration in listOf("var item=Wrapper<T>();", "const item=Invariant<T>();")) {
            Project.config.includes=arrayListOf()
            compile("""
                data Wrapper<out T as type>{constructor(){}}
                data Invariant<T as type>{constructor(){}}
                data Bad<out T as type>{$declaration}
                func main(){var item=Bad<int>();}
            """)
            assertTrue(Project.errorCount>0, declaration)
        }
        compile("data Bad<out T as type>{const first as T;var second=this.first;}\nfunc main(){var item=Bad<int>();}")
        assertEquals(1,Project.errorCount)
    }

    @Test fun actualStartupInitializesDependenciesAndGenericOwnersOnceAcrossLibrary() = isolated { output ->
        val declarations = """
            namespace fixture.startup;
            func number(value as int)->int{
                /say initialized
                return value;
            }
            object data First {value as int=Second.read();func read()->int{return value;}}
            object data Second {value as int=number(9);func read()->int{return value;}}
            object data Settings<N as int>{value as int=number(N);func read()->int{return value;}}
            data Box {value as int;constructor(value as int){this.value=value;}func read()->int{return this.value;}}
            func helper(flag as bool)->int{
                if(flag){return First.read();}
                return Second.read();
            }
        """
        val main = """
            func main(){
                dynamic var dependent=helper(true);
                dynamic var four=Settings<4>.read();dynamic var nine=Settings<9>.read();
                dynamic var again=Settings<4>.read();
                var box=Box(7);dynamic var ordinary=box.read();
            }
        """
        roundTrip(declarations,main,output, startup=true) { function,machine ->
            for((name,value) in listOf("dependent" to 9,"four" to 4,"nine" to 9,"again" to 4,"ordinary" to 7))
                assertEquals(value,machine.read(function.scope.getVar(name) as MCInt))
            assertEquals(listOf("initialized","initialized","initialized"),machine.messages)
            assertEquals(1,machine.bootstrapMarkers.size)
            assertEquals(0,machine.stackDepth)
            val box=function.scope.getVar("box") as DataTemplateObject
            assertEquals("fixture.startup",box.templateType.namespace)
        }
    }

    @Test fun genericPropertiesKeepLexicalTokensAndPrivateInternalFunctionsAcrossLibrary() = isolated { output ->
        val declarations="""
            namespace fixture.properties;
            data Box<T as type>{
                private backing as T;
                value as T {get=this.backing as T;set {this.backing=value;}}
                private hidden as T {get=this.backing;}
                constructor(value as T){this.backing=value;}
                func get_hidden()->int{return 8;}
                func readHidden()->T{return this.hidden;}
            }
        """
        val main="""
            func main(){var first=Box<int>(4);var second=Box<int>(9);
                dynamic var four=first.value;dynamic var nine=second.value;
                first.value=7;dynamic var seven=first.readHidden();dynamic var userGetter=first.get_hidden();}
        """
        roundTrip(declarations,main,output) { function,machine ->
            for((name,value) in listOf("four" to 4,"nine" to 9,"seven" to 7,"userGetter" to 8))
                assertEquals(value,machine.read(function.scope.getVar(name) as MCInt))
            val template=(function.scope.getVar("first") as DataTemplateObject).templateType
            assertTrue(template.scope.functions.getValue("get-hidden").all { it.accessModifier==top.mcfpp.model.Member.AccessModifier.PRIVATE })
        }
        val rejected="func main(){var item=Box<int>(4);var illegal=item.hidden;}"
        Project.config.includes=arrayListOf(output.toString())
        compile("import fixture.properties:*; $rejected")
        assertTrue(Project.errorCount>0)
        Project.config.includes=arrayListOf()
        compile("$declarations $rejected")
        assertTrue(Project.errorCount>0)
    }

    @Test fun declarationDefaultsRunOnlyForMissingArgumentsAndClosedReadonlyLiteralsBindPurely() = isolated { output ->
        val declarations="""
            namespace fixture.defaults;
            func number()->int{
                /say default
                return 4;
            }
            func take(values as list<int> = [number()])->int{return values[0];}
            func earlier(value as int,values as list<int> = [value])->int{return values[0];}
            func genericTake<T as type>(values as list<T> = [number()])->T{return values[0];}
            func sink(values as list<int> = [number()])->int=top.mcfpp.test.DeclarationInitializationContractTest.sink;
            data DefaultOwner {private value as int;constructor(value as int){this.value=value;}
                func read(values as list<int> = [this.value])->int{return values[0];}}
            func DefaultOwner.extra<T as type>(values as list<T> = [this.read()])->T{return values[0];}
            data Factory<N as int>{constructor(){}}
            data RangeFactory<R as range>{constructor(){}}
            data Ranges {const item=RangeFactory<1..3>();}
            enum Mode {first=3,second=7}
            data Choice {const selected=Mode.second;}
            data Values {var bytes=[B;1b,2b];var longs=[L;4294967296L];}
        """
        val main="""
            func main(){dynamic var defaultResult=take();dynamic var provided=take([9]);
                var value=9;dynamic var earlierResult=earlier(4);
                var owner=DefaultOwner(7);dynamic var ownerResult=owner.read();
                dynamic var nativeDefault=sink();dynamic var nativeProvided=sink([9]);
                dynamic var genericDefault=genericTake<int>();dynamic var genericProvided=genericTake<int>([9]);
                dynamic var extensionDefault=owner.extra<int>();dynamic var extensionProvided=owner.extra<int>([9]);
                const var count=2;var factory=Factory<-count>();var values=Values();var ranges=Ranges();var choice=Choice();}
        """
        roundTrip(declarations,main,output) { function,machine ->
            assertEquals(4,machine.read(function.scope.getVar("defaultResult") as MCInt))
            assertEquals(9,machine.read(function.scope.getVar("provided") as MCInt))
            assertEquals(4,machine.read(function.scope.getVar("earlierResult") as MCInt))
            assertEquals(7,machine.read(function.scope.getVar("ownerResult") as MCInt))
            assertEquals(4,machine.read(function.scope.getVar("nativeDefault") as MCInt))
            assertEquals(9,machine.read(function.scope.getVar("nativeProvided") as MCInt))
            assertEquals(4,machine.read(function.scope.getVar("genericDefault") as MCInt))
            assertEquals(9,machine.read(function.scope.getVar("genericProvided") as MCInt))
            assertEquals(7,machine.read(function.scope.getVar("extensionDefault") as MCInt))
            assertEquals(9,machine.read(function.scope.getVar("extensionProvided") as MCInt))
            assertEquals(listOf("default","default","default"),machine.messages)
            val values=(function.scope.getVar("values") as DataTemplateObject).templateType
            assertEquals(MCFPPNBTType.ByteArray,values.scope.getVar("bytes")!!.type)
            assertEquals(MCFPPNBTType.LongArray,values.scope.getVar("longs")!!.type)
            val choice=(function.scope.getVar("choice") as DataTemplateObject).templateType
            val selected=choice.scope.getVar("selected")!!
            val enumType=selected.type as top.mcfpp.type.MCFPPEnumType
            assertEquals("Mode",enumType.enum.identifier)
            val second=enumType.enum.members.getValue("second")
            assertEquals(1,second.value)
            assertEquals(7,second.dataAsInt())
            val selectedInitializer=top.mcfpp.analysis.DeclarationBinding(
                Function("enum-declaration-check",choice,null),emptyMap())
                .expression(choice.preInit.getValue("selected"))
            assertEquals(enumType,selectedInitializer.type)
            assertEquals(top.mcfpp.analysis.CompilerValue.Typed(enumType.typeId,
                top.mcfpp.analysis.CompilerValue.Record(mapOf(
                    "ordinal" to top.mcfpp.analysis.CompilerValue.Integral(1),
                    "data" to top.mcfpp.analysis.CompilerValue.Nbt("7")))),selectedInitializer.constant)
            val ranges=(function.scope.getVar("ranges") as DataTemplateObject).templateType
            val item=ranges.scope.getVar("item")!!.type as top.mcfpp.type.MCFPPDataTemplateType
            val rangeFactory=item.template as top.mcfpp.model.compound.CompiledGenericDataTemplate
            assertEquals(top.mcfpp.analysis.CompilerValue.Typed(MCFPPBaseType.Range.typeId,
                top.mcfpp.analysis.CompilerValue.Record(mapOf(
                    "left" to top.mcfpp.analysis.CompilerValue.Typed(MCFPPBaseType.Int.typeId,top.mcfpp.analysis.CompilerValue.Integral(1)),
                    "right" to top.mcfpp.analysis.CompilerValue.Typed(MCFPPBaseType.Int.typeId,top.mcfpp.analysis.CompilerValue.Integral(3))))),
                top.mcfpp.analysis.ValueSnapshot.of(rangeFactory.scope.getVar("R")!!))
        }
    }

    @Test fun compilerOnlyConstructionJoinsReceiverWritesAndKeepsUnrelatedEffects() = isolated { output ->
        val declarations="""
            namespace fixture.paths;
            func observe(position as pos3)->int=top.mcfpp.test.DeclarationInitializationContractTest.observe;
            data Stable {position as pos3;constructor(flag as bool,position as pos3){
                this.position=position;
                if(flag){
                    /say side-effect
                }
            }
                func read()->int{return observe(this.position);}}
            data Varying {position as pos3;constructor(flag as bool,left as pos3,right as pos3){
                this.position=left;
                if(flag){this.position=right;}
            }
                func read()->int{return observe(this.position);}}
            func select(flag as bool)->int{var item=Stable(flag,4 0 0);return item.read();}
        """
        roundTrip(declarations,"func main(){dynamic var result=select(true);}",output) { function,machine ->
            assertEquals(4,machine.read(function.scope.getVar("result") as MCInt))
            assertEquals(listOf("side-effect","4 0 0"),machine.messages)
        }
        val negative="func reject(flag as bool){var item=Varying(flag,4 0 0,9 0 0);var result=item.read();}\nfunc main(){reject(true);}"
        Project.config.includes=arrayListOf(output.toString())
        compile("import fixture.paths:*; $negative")
        assertTrue(Project.errorCount>0)
        Project.config.includes=arrayListOf()
        compile("$declarations $negative")
        assertTrue(Project.errorCount>0)
    }

    @Test fun sameNamespaceSelectsDeclarationsByNameAndKindInsteadOfMatchingLibraryOverloads() = isolated { output ->
        val library="""
            namespace fixture.names;
            typealias int as Alias;
            func choose(value as int)->int{return 9;}
            func libraryOnly()->int{return 4;}
            data Box {value as int;constructor(value as int){this.value=value;}func read()->int{return this.value;}}
            interface Marker {abstract func read()->int;}
        """
        compile("$library func main(){}",output)
        assertEquals(0,Project.errorCount)
        Project.config.includes=arrayListOf(output.toString())
        val local="""
            namespace fixture.names;
            typealias bool as Alias;
            func choose(value as string)->int{return 7;}
            data Marker {constructor(){}}
        """
        compile("""$local func main(){dynamic var localResult=choose("local");dynamic var libraryResult=libraryOnly();
            var box=Box(9);dynamic var fieldResult=box.read();var alias as Alias=true;}""")
        assertEquals(0,Project.errorCount)
        val main=GlobalScope.localNamespaces.getValue("fixture.names").scope.functions.getValue("main").single()
        val machine=execute(main,output.resolve("consumer"))
        for((name,value) in listOf("localResult" to 7,"libraryResult" to 4,"fieldResult" to 9))
            assertEquals(value,machine.read(main.scope.getVar(name) as MCInt))
        assertEquals(MCFPPBaseType.Bool,main.scope.getVar("alias")!!.type)
        assertNotNull(GlobalScope.getInterface("fixture.names","Marker"))
        assertTrue(GlobalScope.getTemplate("fixture.names","Marker") === GlobalScope.localNamespaces.getValue("fixture.names").scope.getTemplate("Marker"))
        for(call in listOf("choose(4)","fixture.names:choose(4)")) {
            compile("$local func main(){var illegal=$call;}")
            assertTrue(Project.errorCount>0,call)
        }
        compile("namespace fixture.names;typealias Missing as Alias;func main(){var illegal as Alias=1;}")
        assertTrue(Project.errorCount>0)
        assertNull(GlobalScope.localNamespaces.getValue("fixture.names").scope.getType("Alias"))
    }

    companion object {
        @JvmStatic @MNIFunction(normalParams=["list<int>"], returnType="int")
        fun sink(context: NativeCallContext) = context.withArguments { arguments ->
            val list=arguments.single() as top.mcfpp.core.lang.nbt.NBTList
            val first=top.mcfpp.analysis.StorageAccess.element(list,MCIntConcrete(0),MCFPPBaseType.Int)
            context.publishResult(top.mcfpp.analysis.StorageAccess.read(first))
        }
        @JvmStatic @MNIFunction(normalParams=["pos3"], returnType="int")
        fun observe(context: NativeCallContext) = context.withArguments { arguments ->
            val position=arguments.single() as Pos3Var
            Function.addCommand(Command("say").build(position.toCommandPart()))
            context.publishResult(MCIntConcrete(position.x.number.toInt()))
        }
    }

    private fun roundTrip(declarations: String, main: String, output: Path, startup: Boolean=false,
                          check: (Function, ScoreCommandExecutor)->Unit) {
        compile("$declarations\n$main", output)
        assertEquals(0, Project.errorCount)
        val namespace=Regex("namespace ([^;]+);").find(declarations)!!.groupValues[1]
        val source=GlobalScope.localNamespaces.getValue(namespace).scope.functions.getValue("main").single()
        check(source,execute(source, output.resolve("source"),startup))
        Project.config.includes=arrayListOf(output.toString())
        compile("import $namespace:*;\n$main")
        assertEquals(0, Project.errorCount)
        val fresh=GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        check(fresh,execute(fresh, output.resolve("fresh"),startup))
    }


    private fun compile(source: String, output: Path?=null) {
        val parser=top.mcfpp.antlr.mcfppParser(org.antlr.v4.runtime.CommonTokenStream(
            top.mcfpp.antlr.mcfppLexer(org.antlr.v4.runtime.CharStreams.fromString(source))))
        parser.compilationUnit()
        assertEquals(0,parser.numberOfSyntaxErrors,"Semantic contract fixtures must use valid source syntax")
        MCFPPStringTest.readFromString(source, arrayOf(), output?.toString(), "26.3")
    }

    private fun execute(main: Function, output: Path, startup: Boolean=false): ScoreCommandExecutor {
        val load=Project.projectLoad.namespaceID.toString()
        DatapackCreator.createDatapack(output.toString())
        val data=output.resolve(Project.config.name).resolve("data")
        val functions=linkedMapOf<String,List<String>>()
        Files.walk(data).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach {
            val path=data.relativize(it)
            if(path.nameCount>=3 && path.getName(1).toString()=="function")
                functions["${path.getName(0)}:${path.subpath(2,path.nameCount).joinToString("/").removeSuffix(".mcfunction")}"]=Files.readAllLines(it)
        } }
        val commands=if(startup) listOf("function $load","function ${main.namespaceID}") else functions.getValue(main.namespaceID.toString())
        // The bundled mathematics module is already initialized; its world/tick bootstrap is outside this driver.
        return ScoreCommandExecutor(commands,functions,initialScores=if(startup) mapOf("math mcfpp_init" to 1) else emptyMap())
            .also { assertEquals(0,it.stackDepth) }
    }

    private fun isolated(action: (Path)->Unit) {
        val output=Files.createTempDirectory("mcfpp-declaration-contract-")
        val config=Project.config
        val ignore=CompileSettings.ignoreStdLib
        try { Project.config=ProjectConfig();CompileSettings.ignoreStdLib=true;action(output) }
        finally { Project.config=config;CompileSettings.ignoreStdLib=ignore;output.toFile().deleteRecursively() }
    }
}
