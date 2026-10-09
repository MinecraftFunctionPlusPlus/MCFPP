package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedValueContractTest {
    @Test
    fun constRequiresEveryReachablePredecessorToProveTheFirstAssignment() = isolated { _ ->
        val rejected = listOf(
            "if(flag){value=4;}; value=9;",
            "if(flag){value=4;} else {value=5;}; value=9;",
            "while(flag){value=4;};",
            "while(flag){value=4;break;}; value=9;",
            "do {value=4;} while(flag);",
            "if(flag){value=4;}; dynamic var observed=value;",
            "value=1;replace(value);",
            "var source=1;const var alias=source as int;alias=2;",
            "var source=1;const var alias=source as int;replace(alias);",
            "const var source=1;var alias=source as int;alias=2;"
        )
        for (fold in listOf(false, true)) {
            CompileSettings.foldIRConstants = fold
            for (body in rejected) {
                compile("func replace(static target as int){target=4;}\n" +
                    "func reject(flag as bool){const var value as int; $body}\nfunc main(){}")
                assertTrue(Project.errorCount > 0, "fold=$fold: $body")
            }
        }
    }

    @Test
    fun constFirstAssignmentAndMutableContentsWorkAcrossCallsAndLibraryReloads() = isolated { output ->
        val body = """
            const var answer as int;
            var marker=0;
            if(flag){marker=1;} else {marker=2;}
            answer=4;
            const var values as list<int> = [1];
            values[0]=answer;
            const var shared=values as list<int>;
            shared[0]=answer;
            append(shared);
            return values[0]+values[1]+marker;
        """.trimIndent()
        for (ir in listOf(false, true)) for (fold in listOf(false, true)) {
            CompileSettings.foldIRConstants = fold
            val declarations = "namespace fixture.const_values;\nfunc append(static values as list<int>){values[0]+=1;values.add(8);}\n" +
                if (ir) "func choose(flag as bool)->int {$body}" else "data Box {func choose(flag as bool)->int {$body}}"
            val receiver = if (ir) "" else "box."
            val main = "func main(){${if (ir) "" else "var box=Box();"}" +
                "dynamic var result=${receiver}choose(true)*100+${receiver}choose(false);}"
            roundTrip(declarations, main, output.resolve("const-$ir-$fold")) { entry, machine ->
                val function = if (ir) namespace("fixture.const_values").scope.functions.getValue("choose").single()
                    else (entry.scope.getVar("box") as DataTemplateObject).templateType.scope.functions.getValue("choose").single()
                if (ir) assertNotNull(function.typedIR) else assertNull(function.typedIR)
                assertTrue(function.compiledFunctions.isEmpty())
                assertTrue(declaration("fixture.const_values", "append").compiledFunctions.isEmpty())
                assertEquals(1415, machine.read(entry.scope.getVar("result") as MCInt))
            }
        }
    }

    @Test
    fun selectorProgramsKeepCapturedOperandsAfterReturnsCopiesAndLaterCalls() = isolated { output ->
        val resources = output.resolve("resources")
        val entities = output.resolve("entities")
        compile("""
            namespace mcfpp.minecraft.resource;
            data ResourceID {id as string;}
            data EntityType:ResourceID {}
            data Predicate:ResourceID {}
            data Advancement:ResourceID {}
        """.trimIndent(), resources)
        assertEquals(0, Project.errorCount)
        Project.config.includes = arrayListOf(resources.toString())
        compile("""
            namespace mcfpp.minecraft.entity;
            @From<"top.mcfpp.mni.SelectorData">
            data EntityData {}
        """.trimIndent(), entities)
        assertEquals(0, Project.errorCount)
        val dependencies = listOf(resources, entities)
        val declarations = """
            namespace fixture.selector_values;
            func build(label as string,coordinate as int)->entity {
                var selected=@e;
                selected.tag(label);selected.x(coordinate);
                label="inside";coordinate=88;
                return selected;
            }
            func relay(value as entity)->entity {
                var selected=value;
                selected.tag("relayed");
                return selected;
            }
        """.trimIndent()
        val main = """
            func main(){
                dynamic var label="first";dynamic var coordinate=4;
                var original=build(label,coordinate);
                var copied=original;
                var shared=original as entity;
                shared.limit(1);copied.tag("copied");
                label="mutated";coordinate=90;
                var later=build("second",9);
                var returned=relay(copied);
                /say ${'$'}{original}
                /say ${'$'}{copied}
                /say ${'$'}{returned}
                /say ${'$'}{later}
            }
        """.trimIndent()
        for (fold in listOf(false, true)) {
            CompileSettings.foldIRConstants = fold
            roundTrip(declarations, main, output.resolve("selectors-$fold"), dependencies) { _, machine ->
                assertEquals(listOf(
                    "@e[tag=first,x=4,limit=1]",
                    "@e[tag=first,x=4,tag=copied]",
                    "@e[tag=first,x=4,tag=copied,tag=relayed]",
                    "@e[tag=second,x=9]"
                ), machine.messages)
                for (name in listOf("build", "relay"))
                    assertTrue(declaration("fixture.selector_values", name).compiledFunctions.isEmpty())
            }
        }
        Project.config.includes = ArrayList(dependencies.map(Path::toString))
        compile("""
            data Frozen<selection as entity> {}
            func reject(label as string){
                var selected=@e;selected.tag(label);
                var invalid=Frozen<selected>();
            }
            func main(){var valid=Frozen<@e>();}
        """.trimIndent())
        assertEquals(1, Project.errorCount, "A runtime operand is legal in an ordinary selector payload, but is not a frozen readonly argument")
    }

    @Test
    fun compilerOnlyActualPayloadsKeepTheDeclaredOrdinaryOverloadContract() = isolated { output ->
        val declarations = """
            namespace fixture.formal_values;
            data Base {position as pos3=1 0 0;}
            data Child:Base {marker as int=2;}
            func choose(value as Base)->int {return 1;}
            func choose(value as Child)->int {return 2;}
            func inspect(value as Base,count as int)->int {return choose(value)*10+count;}
            func iterateClosed(early as bool)->int {
                var values as list<pos3> = [1 0 0,2 0 0,3 0 0];
                var count=0;
                for(value:values){
                    value=9 0 0;
                    count+=1;
                    if(count==1){continue;}
                    if(count==2){if(early){return count+10;};break;}
                }
                return count;
            }
        """.trimIndent()
        val main = "func main(){var child=Child();dynamic var result=inspect(child,7);dynamic var direct=choose(child);" +
            "dynamic var iteration=iterateClosed(false);dynamic var earlyIteration=iterateClosed(true);}"
        for (fold in listOf(false, true)) {
            CompileSettings.foldIRConstants = fold
            roundTrip(declarations, main, output.resolve("formals-$fold")) { entry, machine ->
                assertEquals(17, machine.read(entry.scope.getVar("result") as MCInt))
                assertEquals(2, machine.read(entry.scope.getVar("direct") as MCInt))
                assertEquals(2, machine.read(entry.scope.getVar("iteration") as MCInt))
                assertEquals(12, machine.read(entry.scope.getVar("earlyIteration") as MCInt))
                namespace("fixture.formal_values").scope.functions.values.flatten().forEach {
                    assertTrue(it.compiledFunctions.isEmpty())
                }
            }
        }
    }

    @Test
    fun unknownIntegerRangeBoundsAreCapturedAndAllLoopExitsBalanceFrames() = isolated { output ->
        val body = """
            func iterate(start as int,finish as int)->int {
                var bounds=start .. finish;
                start=0;finish=0;
                var sum=0;
                for(index:bounds){
                    if(index==2){continue;}
                    sum+=index;
                    if(index>=4){break;}
                }
                return sum;
            }
            func natural(start as int,finish as int)->int {
                var bounds=start .. finish;
                var sum=0;
                for(index:bounds){const var term=index;sum+=term;bounds=1 .. 1;}
                return sum;
            }
            func early(start as int,finish as int)->int {
                for(index:start .. finish){if(index>=3){return index;};}
                return 0;
            }
            func nested(start as int,finish as int)->int {
                var selected=0;
                if(start==1){start=2;selected=10;}
                else if(start==2){selected=20;}
                else {selected=30;}
                for(outer:start .. finish){
                    for(inner:1 .. finish){if(inner>=3){return selected+outer*10+inner;};}
                }
                return 0;
            }
            func condition(static count as int,limit as int)->bool {
                count+=1;return count<limit;
            }
            func observe(limit as int)->int {
                var count=0;var iterations=0;
                while(condition(count,limit)){iterations+=1;}
                return count*10+iterations;
            }
        """.trimIndent()
        for (version in listOf("26.3", "1.20.2", "1.20.1"))
            for (ir in listOf(false, true)) for (fold in listOf(false, true)) {
            CompileSettings.foldIRConstants = fold
            val declarations = "namespace fixture.range_values;\n" + if (ir) body else
                "data Box {${body.replace("while(condition(", "while(this.condition(")}}"
            val receiver = if (ir) "" else "box."
            val main = "func main(){${if (ir) "" else "var box=Box();"}" +
                "dynamic var start=1;dynamic var finish=5;" +
                "dynamic var result=${receiver}iterate(start,finish)*100+${receiver}natural(start,3)*10+${receiver}early(start,finish);" +
                "dynamic var empty=${receiver}natural(3,1);" +
                "dynamic var branchResult=${receiver}nested(start,finish);" +
                "dynamic var alternative=${receiver}nested(2,finish);" +
                "dynamic var last=${receiver}nested(3,finish);" +
                "dynamic var skipped=${receiver}observe(1);" +
                "dynamic var completed=${receiver}observe(3);}"
            roundTrip(declarations, main, output.resolve("ranges-$version-$ir-$fold"), version = version) { entry, machine ->
                val functions = if (ir) namespace("fixture.range_values").scope.functions
                    else (entry.scope.getVar("box") as DataTemplateObject).templateType.scope.functions
                for (name in listOf("iterate", "natural", "early", "nested", "condition", "observe")) {
                    val function = functions.getValue(name).single()
                    if (ir) assertNotNull(function.typedIR) else assertNull(function.typedIR)
                    assertTrue(function.compiledFunctions.isEmpty())
                }
                assertEquals(863, machine.read(entry.scope.getVar("result") as MCInt))
                assertEquals(0, machine.read(entry.scope.getVar("empty") as MCInt))
                assertEquals(33, machine.read(entry.scope.getVar("branchResult") as MCInt))
                assertEquals(43, machine.read(entry.scope.getVar("alternative") as MCInt))
                assertEquals(63, machine.read(entry.scope.getVar("last") as MCInt))
                assertEquals(10, machine.read(entry.scope.getVar("skipped") as MCInt))
                assertEquals(32, machine.read(entry.scope.getVar("completed") as MCInt))
            }
        }
    }

    @Test
    fun nestedFloatTagsPreserveSignedZeroInReturnsDTOsAndIndependentCopies() = isolated { output ->
        val pack = """
            func pack(value as float)->nbt {
                var values as list<float> = [value];
                return {nested:{value:value,values:values}} as nbt;
            }
        """.trimIndent()
        val payload = """
            data Payload {
                nested as dict<any>;
                constructor(value as float){
                    var nested as dict<any> = {};
                    nested["value"]=value;
                    nested["values"]=[value];
                    this.nested=nested;
                }
            }
        """.trimIndent()
        for (ir in listOf(false, true)) for (fold in listOf(false, true)) {
            CompileSettings.foldIRConstants = fold
            val declarations = "namespace fixture.float_values;\n$payload\n" + if (ir) pack else "data Box {$pack}"
            val main = """
                func main(){
                    ${if (ir) "" else "var box=Box();"}
                    dynamic var zero=0.0;var negative=-zero;
                    var original=${if (ir) "" else "box."}pack(negative);
                    var copied=original;
                    var shared=original as dict<dict<any>>;
                    shared["nested"]["value"]=1.0;
                    var dto=Payload(negative);var dtoCopy=dto;
                    var dtoView=dto as Payload;dtoView.nested["value"]=2.0;
                    /data modify storage fixture:float_values original set from storage mcfpp:system stack_frame[0].original
                    /data modify storage fixture:float_values copied set from storage mcfpp:system stack_frame[0].copied
                    /data modify storage fixture:float_values dto set from storage mcfpp:system stack_frame[0].dto
                    /data modify storage fixture:float_values dtoCopy set from storage mcfpp:system stack_frame[0].dtoCopy
                }
            """.trimIndent()
            roundTrip(declarations, main, output.resolve("floats-$ir-$fold")) { entry, machine ->
                val function = if (ir) namespace("fixture.float_values").scope.functions.getValue("pack").single()
                    else (entry.scope.getVar("box") as DataTemplateObject).templateType.scope.functions.getValue("pack").single()
                if (ir) assertNotNull(function.typedIR) else assertNull(function.typedIR)
                for ((name, value) in listOf("original" to 1f, "copied" to -0.0f, "dto" to 2f, "dtoCopy" to -0.0f)) {
                    assertEquals(value.toRawBits(), assertIs<FloatTag>(machine.readNbt("fixture:float_values", "$name.nested.value")).value.toRawBits(), name)
                    assertEquals((-0.0f).toRawBits(), assertIs<FloatTag>(machine.readNbt("fixture:float_values", "$name.nested.values[0]")).value.toRawBits(), name)
                }
            }
        }
    }

    @Test
    fun vectorViewsShareTheirSourceAndOrdinaryReturnsHaveIndependentComponents() = isolated { output ->
        val declarations = "namespace fixture.vector_values;\nfunc relay(value as vec3)->vec3{return value;}"
        val main = """
            func main(){
                var values as list<int> = [1,2,3];
                var original=values as vec3;
                var copied=relay(original);
                var shared=original as vec3;
                shared[1]=9;
                dynamic var result=values[1]*100+copied[1]*10+copied[0];
            }
        """.trimIndent()
        for (fold in listOf(false, true)) {
            CompileSettings.foldIRConstants = fold
            roundTrip(declarations, main, output.resolve("vectors-$fold")) { entry, machine ->
                assertEquals(921, machine.read(entry.scope.getVar("result") as MCInt))
                assertTrue(namespace("fixture.vector_values").scope.functions.getValue("relay").single().compiledFunctions.isEmpty())
            }
        }
    }

    private fun compile(source: String, output: Path? = null, version: String = "26.3") {
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors, "Acceptance fixtures must use valid language syntax")
        MCFPPStringTest.readFromString(source, targetPath = output?.toString(), version = version)
    }

    private fun namespace(name: String) = GlobalScope.getUnsolvedImportNamespace(name)!!

    private fun declaration(namespace: String, name: String) =
        GlobalScope.getFunctionCandidates(namespace, name, null).single()

    private fun roundTrip(declarations: String, main: String, output: Path, dependencies: List<Path> = emptyList(),
                          version: String = "26.3",
                          check: (Function, ScoreCommandExecutor) -> Unit) {
        val namespace = Regex("namespace ([^;]+);").find(declarations)!!.groupValues[1]
        for (fresh in listOf(false, true)) {
            Project.config.includes = ArrayList((dependencies + if (fresh) listOf(output) else emptyList()).map(Path::toString))
            compile(if (fresh) "import $namespace:*;\n$main" else "$declarations\n$main", if (fresh) null else output, version)
            assertEquals(0, Project.errorCount, "$output/fresh=$fresh")
            val entry = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .single { it.identifier == "main" }
            check(entry, execute(entry, output.resolve(if (fresh) "fresh" else "source")))
        }
    }

    private fun execute(entry: Function, output: Path): ScoreCommandExecutor {
        DatapackCreator.createDatapack(output.toString())
        val data = output.resolve(Project.config.name).resolve("data")
        val functions = linkedMapOf<String, List<String>>()
        Files.walk(data).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
            val path = data.relativize(file)
            if (path.nameCount >= 3 && path.getName(1).toString() == "function")
                functions["${path.getName(0)}:${path.subpath(2, path.nameCount).joinToString("/").removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
        } }
        if (!TargetCapabilities.forVersion(Project.config.version)!!.functionReturnRun)
            assertTrue(functions.values.flatten().none { Regex("\\breturn\\s+run\\b").containsMatchIn(it) },
                "${Project.config.version} does not support return run")
        return ScoreCommandExecutor(functions.getValue(entry.namespaceID.toString()), functions, targetVersion = Project.config.version).also {
            assertEquals(0, it.stackDepth)
            assertTrue(it.branchGuards.isEmpty())
        }
    }

    private fun isolated(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-unified-values-")
        val config = Project.config
        val folding = CompileSettings.foldIRConstants
        val ignore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            action(output)
        } finally {
            Project.config = config
            CompileSettings.foldIRConstants = folding
            CompileSettings.ignoreStdLib = ignore
            output.toFile().deleteRecursively()
        }
    }
}
