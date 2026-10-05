package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueSnapshot
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.util.StringHelper.toSnakeCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class ConstructorExecutionTest {
    private fun compile(source: String, output: Path? = null): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3", targetPath = output?.toString())
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }
    private fun template(name: String = "Box") = GlobalScope.getTemplate("default.test", name)!!
    private fun execute(main: Function, templates: List<DataTemplate> = listOf(template())): ScoreCommandExecutor {
        val functions = LinkedHashMap<String, List<String>>()
        fun collect(function: Function) {
            functions[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
        }
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.forEach(::collect)
        templates.forEach { data -> data.constructors.forEach(::collect); data.scope.forEachFunction { collect(it) } }
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main.commands.analyzeAll(), functions).also { assertEquals(0, it.stackDepth) }
    }
    private fun exported(output: Path, function: Function, owner: String = "box") = output.resolve("debug/data/${function.namespace}/function/$owner/${function.identifierWithParamType.toSnakeCase()}.mcfunction")

    @Test fun ordinaryArgumentsShareOneBodyAndExportRealFieldWrites() {
        val output = Files.createTempDirectory("mcfpp-constructor-body-")
        try {
            val main = compile("""
                data Box {
                    value as int;
                    constructor(value as int){ this.value = value; }
                }
                func main(){
                    var first = Box(1);
                    var second = Box(2);
                    first.value = 9;
                    dynamic var result = first.value*10 + second.value;
                }
            """, output)
            val constructor = template().constructors.single()
            assertTrue(constructor.compiledFunctions.isEmpty())
            assertTrue(constructor.bodyCompiled)
            assertEquals(92, execute(main).read(main.scope.getVar("result") as MCInt))
            val text = Files.readString(exported(output, constructor))
            assertTrue(text.contains("stack_frame[0].this.value"), text)
            assertTrue(text.contains("set from") || text.contains("store result"), text)
        } finally { output.toFile().deleteRecursively() }
    }

    @Test fun fieldInitializersRunForExplicitAndImplicitConstructors() {
        for (constructor in listOf("", "constructor(){}")) {
            val main = compile("""
                data Box { value as int = 4; $constructor }
                func main(){
                    var first = Box();
                    first.value = 9;
                    var second = Box();
                    dynamic var result = first.value*10 + second.value;
                }
            """)
            assertEquals(94, execute(main).read(main.scope.getVar("result") as MCInt))
            assertTrue(template().constructors.single().compiledFunctions.isEmpty())
            if (constructor.isEmpty()) assertNull(template().constructors.single().ast)
        }
        val output = Files.createTempDirectory("mcfpp-object-initializer-")
        try {
            compile("""
                object data Defaults { value as int = 4; }
                func main(){}
            """, output)
            val defaults = GlobalScope.localNamespaces.getValue("default.test").scope.objects
                .filterIsInstance<top.mcfpp.model.compound.ObjectDataTemplate>().single { it.identifier == "Defaults" }
            val constructor = defaults.constructors.single()
            val file = exported(output, constructor, "defaults/static")
            assertTrue(Files.exists(file))
            val body = Files.readString(file)
            val fieldPath = defaults.nbtPath.memberIndex("value")
            assertTrue(body.contains(fieldPath.toCommandPart().toString()), body)
            val machine = ScoreCommandExecutor(listOf("function ${constructor.namespaceID}"),
                mapOf(constructor.namespaceID.toString() to body.lines().filter { it.isNotBlank() }))
            assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(4), machine.readNbt("mcfpp:system", fieldPath.pathToCommandPart().toString()))
            assertTrue(defaults.scope.getVar("value")!!.isDynamic)
            assertTrue(constructor.namespaceID.toString().contains("defaults/static/"))
        } finally { output.toFile().deleteRecursively() }
    }

    @Test fun onlyRequiredConstantsDistinguishConstructorSpecializationsAndTheirExports() {
        val output = Files.createTempDirectory("mcfpp-constructor-required-")
        try {
            val main = compile("""
                data Box {
                    value as int;
                    constructor(kind as int!, value as int){ this.value = kind + value; }
                }
                func main(){
                    var first = Box(1,1);
                    var second = Box(1,2);
                    var third = Box(2,3);
                    dynamic var result = first.value*100 + second.value*10 + third.value;
                }
            """, output)
            val constructor = template().constructors.single()
            assertEquals(2, constructor.compiledFunctions.size)
            constructor.compiledFunctions.values.forEach { function ->
                assertEquals(listOf("value"), function.normalParams.map { it.identifier })
                assertNull(ValueSnapshot.of(function.scope.getVar("value")!!))
                assertTrue(Files.readString(exported(output, function)).contains("stack_frame[0].this.value"))
            }
            assertEquals(235, execute(main).read(main.scope.getVar("result") as MCInt))
        } finally { output.toFile().deleteRecursively() }
    }

    @Test fun nestedCallsPreserveTheConstructorReceiver() {
        val main = compile("""
            func bump(value as int) -> int { return value + 1; }
            data Box {
                value as int;
                constructor(value as int){
                    this.value = value;
                    this.value += bump(value);
                }
            }
            func main(){
                var first = Box(1);
                var second = Box(2);
                dynamic var result = first.value*10 + second.value;
            }
        """)
        assertEquals(35, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun memberCallsWriteBackOnlyTheirOwnReceiver() {
        val main = compile("""
            data Box {
                value as int;
                constructor(value as int){ this.value = value; }
                func setValue(value as int){ this.value = value; }
            }
            func main(){
                var first = Box(1);
                var second = Box(2);
                first.setValue(9);
                dynamic var result = first.value*10 + second.value;
            }
        """)
        assertEquals(92, execute(main).read(main.scope.getVar("result") as MCInt))
        assertTrue(template().scope.functions.getValue("setValue").single().compiledFunctions.isEmpty())
    }

    @Test fun calledDynamicBranchesLeaveTheirFramesForWritebackAndEntryReturnsPopOnce() {
        val main = compile("""
            func choose(value as int) -> int {
                var ignored = 6/2;
                if(value > 0){ return value + 1; }
                return 0;
            }
            data Box {
                value as int;
                constructor(value as int){
                    if(value > 0){ this.value = value; }
                    else{ this.value = 0; }
                }
            }
            func main(){
                var ignored = 6/2;
                var first = Box(1);
                var second = Box(0);
                dynamic var result = choose(2)*100 + first.value*10 + second.value;
                /say done
                return;
                /say unreachable
            }
        """)
        assertNull(main.typedIR)
        assertNull(GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("choose").single().typedIR)
        val machine = execute(main)
        assertEquals(310, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(listOf("done"), machine.messages)
        assertEquals(0, machine.stackDepth)
    }

    @Test fun aViewReceiverSharesWritesWithoutChangingSourceTypeOrItsIndependentCopy() {
        val main = compile("""
            data Source {
                value as int;
                constructor(value as int){ this.value = value; }
            }
            data Target {
                value as int;
                func setValue(value as int){ this.value = value; }
            }
            func main(){
                var source = Source(2);
                var copied = source;
                var view = source as Target;
                view.setValue(9);
                dynamic var result = source.value*10 + copied.value;
            }
        """)
        val source = main.scope.getVar("source")!!
        val binding = source.storageBinding!!
        assertEquals(template("Source").getType().typeId, source.type.typeId)
        assertNotEquals(TypeKnowledge.Exact(template("Target").getType().typeId), binding.data.facts.read(binding.place)!!.type)
        assertEquals(92, execute(main, listOf(template("Source"), template("Target"))).read(main.scope.getVar("result") as MCInt))
        assertNotEquals(binding.place, main.scope.getVar("copied")!!.storageBinding!!.place)
    }
}
