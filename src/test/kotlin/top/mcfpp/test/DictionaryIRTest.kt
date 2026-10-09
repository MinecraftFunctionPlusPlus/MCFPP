package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class DictionaryIRTest {
    private fun function(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue(name).single()
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return function("main").also { assertNotNull(it.typedIR) }
    }
    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }).also {
        assertEquals(0, it.stackDepth)
        assertTrue(it.branchGuards.isEmpty())
    }
    private fun modes(action: () -> Unit) {
        val original = CompileSettings.foldIRConstants
        try { for (enabled in listOf(true, false)) { CompileSettings.foldIRConstants = enabled; action() } }
        finally { CompileSettings.foldIRConstants = original }
    }

    @Test fun mutationsAndQueriesRunInsideTheSameLoopIR() = modes {
        for (version in listOf("26.3", "1.20.2", "1.20")) {
            val main = compile("""
                func main(){
                    var values as dict<int> = {first:2, second:9};
                    var i = 0;
                    while(i < 2){
                        values.remove("first");
                        values.merge({first:7} as dict<int>);
                        i += 1;
                    }
                    dynamic var present = values.containsKey("first");
                    dynamic var result = values["first"] + values["second"];
                    values.clear();
                    dynamic var removed = values.containsKey("first");
                }
            """, version)
            val machine = execute(main)
            assertEquals(16, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(1, machine.read(main.scope.getVar("present") as ScoreBool))
            assertEquals(0, machine.read(main.scope.getVar("removed") as ScoreBool))
        }
    }

    @Test fun deepMergeKeepsUntouchedFieldsAndCopiesTheSource() = modes {
        val main = compile("""
            func main(){
                var values as dict<any> = {nested:{first:2, second:9}, untouched:true};
                var source as dict<any> = {nested:{first:7}};
                values.merge(source);
                var nested = values["nested"] as dict<int>;
                nested["first"] = 8;
                var original = source["nested"] as dict<int>;
                dynamic var result = nested["first"] + nested["second"] + original["first"];
                dynamic var preserved = values["untouched"] == true;
            }
        """)
        val machine = execute(main)
        assertEquals(24, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("preserved") as ScoreBool))
    }

    @Test fun staticRemovalMapsItsFieldEffectAndPreservesSiblingTypes() = modes {
        val main = compile("""
            func erase(static values as dict<any>) { values.remove("first"); }
            func main(){
                var values as dict<any> = {first:2, second:9};
                erase(values);
                dynamic var result = values["second"] + 1;
                dynamic var removed = values.containsKey("first");
            }
        """)
        val machine = execute(main)
        assertEquals(10, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("removed") as ScoreBool))
        val erase = function("erase")
        assertNotNull(erase.typedIR)
        assertEquals(setOf(Place(erase.typedIR!!.parameters.single()).field("first")), assertIs<Effect.Writes>(erase.runtimeEffect).places)
    }

    @Test fun memberReceiverRetainsItsIndexAcrossArgumentEffects() = modes {
        val main = compile("""
            func identity(value as int) -> int { return value; }
            func change(static index as int) -> dict<int> { index = 1; return {first:7}; }
            func main(){
                var values = [{first:2},{first:9}];
                var index = identity(0);
                values[index].merge(change(index));
                dynamic var result = values[0]["first"] + values[1]["first"];
            }
        """)
        assertEquals(16, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun signaturesAndKeyCapabilitiesAreCheckedBeforeCommands() {
        for (source in listOf(
            "func main(){ var values as dict<int> = {first:2}; values.remove(1); }",
            "func main(){ var values as dict<int> = {first:2}; values.merge({first:true}); }",
            "func inspect(values as dict<int>, key as string) -> bool { return values.containsKey(key); }\nfunc main(){ var values as dict<int> = {first:2}; var found = inspect(values,\"first\"); }"
        )) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertNotNull(function("main").typedIR)
            assertTrue(Project.errorCount > 0)
            assertTrue(Project.macroFunction.isEmpty())
        }
        val main = compile("""
            func main(){
                var values as dict<int> = {first:2};
                values["a.b"] = 7;
                dynamic var before = values.containsKey("a.b");
                values.remove("a.b");
                dynamic var after = values.containsKey("a.b");
            }
        """)
        val machine = execute(main)
        assertEquals(1, machine.read(main.scope.getVar("before") as ScoreBool))
        assertEquals(0, machine.read(main.scope.getVar("after") as ScoreBool))
    }

    @Test fun unknownCompositeParametersReadTheirDeclaredLayoutsAcrossFreshLibraries() = modes {
        val savedConfig = Project.config
        val output = java.nio.file.Files.createTempDirectory("mcfpp-dictionary-composite")
        Project.config = top.mcfpp.ProjectConfig()
        try {
            val declarations = """
                namespace fixture.dictionary_composite;
                data Node { value as int; next as Node?; constructor(value as int){this.value=value;} }
                func readList(values as dict<list<int>>)->int{return values["key"][0];}
                func readNode(values as dict<Node>)->int{return values["key"].value;}
                func readNested(values as dict<dict<int>>)->int{return values["key"]["value"];}
                func returnNode(values as dict<Node>)->Node{return values["key"];}
                func sameNode(value as Node)->Node{return value;}
                func readDeep(values as dict<Node>)->int{return values["key"].next.value;}
                func copyDeep(values as dict<Node>)->int{
                    var copied=values["key"];var before=copied.next.value;
                    copied.next.value=99;return before;
                }
                func returnDeep(values as dict<Node>)->int{
                    var returned=sameNode(returnNode(values));var before=returned.next.value;
                    returned.next.value=88;return before+returned.next.value-before;
                }
                func independentDeep(values as dict<Node>)->int{
                    var original=values["key"];var copied=original;
                    copied.next.value=999;return copied.next.value;
                }
                func repeatDeep(values as dict<Node>)->int{
                    var first=returnNode(values);var second=first;
                    var third=sameNode(second);var fourth=sameNode(third);
                    return fourth.next.next.next.next.value;
                }
                func copyNode(values as dict<Node>)->int{
                    var original=values["key"];var copied=original;var returned=returnNode(values);
                    copied.value=99;
                    return original.value*10000+copied.value*100+returned.value;
                }
            """.trimIndent()
            val body = """
                func main(){
                    var lists as dict<list<int>> = {key:[7]};
                    var node=Node(9);var nodes as dict<Node> = {key:node};
                    var nested as dict<dict<int>> = {key:{value:11}};
                    dynamic var listResult=readList(lists);
                    dynamic var nodeResult=readNode(nodes);
                    dynamic var nestedResult=readNested(nested);
                    var returned=returnNode(nodes);dynamic var returnResult=returned.value;
                    dynamic var copyResult=copyNode(nodes);dynamic var originalAgain=readNode(nodes);
                    var deep=Node(7);var next=Node(9);var third=Node(88);
                    var fourth=Node(123);var fifth=Node(456);
                    fourth.next=fifth;third.next=fourth;next.next=third;deep.next=next;
                    var deepNodes as dict<Node> = {key:deep};
                    dynamic var deepDirect=readDeep(deepNodes);
                    dynamic var deepCopy=copyDeep(deepNodes);
                    dynamic var deepReturned=returnDeep(deepNodes);
                    dynamic var deepIndependent=independentDeep(deepNodes);
                    dynamic var deepOriginalAgain=readDeep(deepNodes);
                    dynamic var deepRepeated=repeatDeep(deepNodes);
                }
            """.trimIndent()
            fun check() {
                assertEquals(0, Project.errorCount)
                val main = GlobalScope.localNamespaces.values.flatMap { it.scope.functions["main"].orEmpty() }.single()
                val node = assertNotNull(GlobalScope.getCanonicalTemplate("fixture.dictionary_composite", "Node"))
                val functions = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values)
                    .flatMap { it.scope.functions.values.flatten() }.plus(node.constructors + node.scope.functions.values.flatten())
                    .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
                    Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }
                val machine = ScoreCommandExecutor(main.commands.analyzeAll(), functions)
                for ((name, expected) in listOf("listResult" to 7, "nodeResult" to 9, "nestedResult" to 11,
                    "returnResult" to 9, "copyResult" to 99909, "originalAgain" to 9,
                    // The returned/copied next.value writes must not alias the original chain.
                    "deepDirect" to 9, "deepCopy" to 9, "deepReturned" to 88,
                    "deepIndependent" to 999, "deepOriginalAgain" to 9, "deepRepeated" to 456))
                    assertEquals(expected, machine.read(assertIs<MCInt>(main.scope.getVar(name))), name)
                assertEquals(0, machine.stackDepth)
                assertTrue(machine.branchGuards.isEmpty())
                val copyBody = GlobalScope.getFunctionCandidates("fixture.dictionary_composite", "copyNode", null).single()
                val accesses = listOf("original", "copied", "returned").map { assertNotNull(copyBody.scope.getVar(it)) }
                accesses.forEach { assertEquals(node.getType().typeId, it.type.typeId) }
                assertEquals(3, accesses.map { assertNotNull(it.storageBinding).place }.distinct().size)
                for (name in listOf("copyDeep", "returnDeep", "independentDeep", "repeatDeep")) {
                    val callee = GlobalScope.getFunctionCandidates("fixture.dictionary_composite", name, null).single()
                    val names = when (name) {
                        "copyDeep" -> listOf("copied")
                        "returnDeep" -> listOf("returned")
                        "independentDeep" -> listOf("original", "copied")
                        else -> listOf("first", "second", "third", "fourth")
                    }
                    val bindings = names.map {
                        val value = assertNotNull(callee.scope.getVar(it))
                        assertEquals(node.getType().typeId, value.type.typeId, "$name.$it")
                        assertNotNull(value.storageBinding)
                    }
                    assertEquals(names.size, bindings.map { it.place }.distinct().size, name)
                    bindings.forEach { binding ->
                        val view = binding.field("next")
                        assertEquals(binding.place.field("next"), view.place)
                        assertTrue(binding.place.overlaps(view.place))
                    }
                }
                val sameNode = GlobalScope.getFunctionCandidates("fixture.dictionary_composite", "sameNode", null).single()
                assertTrue(sameNode.compiledFunctions.isEmpty())
                val sameParameter = assertNotNull(sameNode.scope.getVar("value"))
                assertEquals(node.getType().typeId, sameParameter.type.typeId)
                assertNull(StorageAccess.snapshot(sameParameter))
                val sameBinding = assertNotNull(sameParameter.storageBinding)
                assertEquals(node.getType().typeId, sameBinding.data.facts.read(sameBinding.place)?.readableLayout)
                var deepParameter = sameBinding.place
                repeat(8) { deepParameter = deepParameter.field("next") }
                assertEquals(ValueState.INITIALIZED, sameBinding.data.facts.readAccess(deepParameter.field("value"),
                    sameBinding.data.declarations, sameBinding.data.types)?.state)
                for (name in listOf("readList", "readNode", "readNested", "returnNode", "copyNode",
                    "readDeep", "copyDeep", "returnDeep", "independentDeep", "repeatDeep")) {
                    val callee = GlobalScope.getFunctionCandidates("fixture.dictionary_composite", name, null).single()
                    assertTrue(callee.compiledFunctions.isEmpty(), name)
                    val parameter = assertNotNull(callee.scope.getVar("values"))
                    assertEquals(callee.normalParams.single().type.typeId, parameter.type.typeId)
                    assertNull(StorageAccess.snapshot(parameter), name)
                    val binding = assertNotNull(parameter.storageBinding)
                    assertNotEquals(ValueState.INITIALIZED, binding.data.facts.read(binding.place.field("key"))?.state, name)
                    val key = binding.place.field("key")
                    val sourceFacts = binding.data.facts
                    for ((place, fact) in sourceFacts.entries().filterKeys {
                        it.root == key.root && it.path.take(key.path.size) == key.path
                    }) {
                        assertNull(fact.readableLayout, "$name raw $place")
                        assertFalse(fact.value is ValueKnowledge.Constant, "$name raw $place")
                    }
                    assertTrue(sourceFacts.knownLengths().keys.none {
                        it.root == key.root && it.path.take(key.path.size) == key.path
                    }, "$name must not invent key/descendant lengths")
                    if (name == "readDeep") {
                        for (state in listOf(ValueState.UNINITIALIZED, ValueState.MAYBE_INITIALIZED, ValueState.ERROR)) {
                            val blocked = sourceFacts.fork().apply {
                                refine(key, ValueFacts(TypeKnowledge.Exact(node.getType().typeId), ValueKnowledge.Unknown, state))
                            }
                            assertEquals(state, blocked.readAccess(key, binding.data.declarations, binding.data.types)?.state)
                            assertNotEquals(ValueState.INITIALIZED, blocked.readAccess(key.field("next").field("value"),
                                binding.data.declarations, binding.data.types)?.state, "$name explicit $state key")
                        }
                    }
                }
            }
            MCFPPStringTest.readFromString("$declarations\n$body", targetPath = output.toString(), version = "26.3")
            check()
            Project.config.includes = arrayListOf(output.toString())
            MCFPPStringTest.readFromString("import fixture.dictionary_composite:*;\n$body", version = "26.3")
            check()
        } finally {
            Project.config = savedConfig
            java.nio.file.Files.walk(output).use { paths -> paths.sorted(java.util.Comparator.reverseOrder()).forEach(java.nio.file.Files::deleteIfExists) }
        }
    }

    @Test fun compositeAccessDoesNotProveMissingFieldsOrErasedLayouts() {
        for (source in listOf(
            "data Node { value as int; constructor(){} }\nfunc main(){var node=Node();dynamic var result=node.value;}",
            "func reject(values as dict<any>){dynamic var result=values[\"key\"][0];}\nfunc main(){}",
            "func reject(values as dict<object>){dynamic var result=values[\"key\"][0];}\nfunc main(){}"
        )) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0, source)
            val owner = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .single { it.identifier == if ("func reject" in source) "reject" else "main" }
            val result = owner.scope.getVar("result")
            assertTrue(result == null || result.isError || result.storageBinding?.let {
                it.data.facts.read(it.place)?.state != ValueState.INITIALIZED
            } == true, source)
        }
        // An ordinary incoming parameter provides initialization, not a closed coordinate payload.
        MCFPPStringTest.readFromString("func reject(values as dict<pos3>){}\nfunc main(){}", version = "26.3")
        assertEquals(0, Project.errorCount)
        val reject = function("reject")
        reject.runInFunction {
            val values = assertNotNull(reject.scope.getVar("values"))
            val binding = StorageAccess.bindIncomingParameter(values)
            assertEquals(ValueState.INITIALIZED, binding.data.facts.read(binding.place)?.state)
            assertNull(StorageAccess.snapshot(values))
            val string = top.mcfpp.type.MCFPPBaseType.String
            val key = StorageAccess.literal(string, CompilerValue.Typed(string.typeId, CompilerValue.Text("key")), "key")
            val errors = Project.errorCount
            val before = reject.commands.size
            val element = StorageAccess.element(values, key, top.mcfpp.type.MCFPPBaseType.Pos3)
            val read = StorageAccess.read(element)
            assertTrue(read.isError)
            assertTrue(Project.errorCount > errors)
            assertEquals(before, reject.commands.size)
            assertNotEquals(ValueState.INITIALIZED, binding.data.facts.read(binding.place.field("key"))?.state)
        }
    }
}
