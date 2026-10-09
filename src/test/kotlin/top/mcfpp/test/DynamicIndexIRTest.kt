package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class DynamicIndexIRTest {
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

    @Test fun loopIndicesReadAProvenCommonActualTypeWithoutReusingElementValues() = modes {
        for (version in listOf("26.3", "1.20.2")) {
            val main = compile("""
                func main(){
                    var values = [2,9] as list<any>;
                    var i = 0;
                    var sum = 0;
                    while(i < 2){ sum += values[i]; i += 1; }
                    dynamic var result = sum;
                }
            """, version)
            assertEquals(11, execute(main).read(main.scope.getVar("result") as MCInt))
            assertTrue(Project.macroFunction.isNotEmpty())
            if (version == "1.20.2") assertTrue(GlobalScope.localNamespaces.values.flatMap {
                it.scope.functions.values.flatten()
            }.flatMap { it.commands.analyzeAll() }.none { "return run" in it })
        }
    }

    @Test fun differentUnknownIndicesCaptureIndependentReadResults() = modes {
        val main = compile("""
            func identity(value as int) -> int { return value; }
            func main(){
                var values = [2,9] as list<any>;
                var left = identity(0);
                var right = identity(1);
                dynamic var result = values[left] + values[right];
            }
        """)
        assertEquals(11, execute(main).read(main.scope.getVar("result") as MCInt))
        val nested = compile("""
            func identity(value as int) -> int { return value; }
            func main(){
                var values = [[2,3],[8,9]];
                var left = identity(0);
                var right = identity(1);
                dynamic var result = values[left][right] + values[right][left];
            }
        """)
        assertEquals(11, execute(nested).read(nested.scope.getVar("result") as MCInt))
    }

    @Test fun assignmentIndicesAreCapturedBeforeTheRightHandSideChangesTheirVariable() = modes {
        val main = compile("""
            func identity(value as int) -> int { return value; }
            func change(static index as int) -> int { index = 1; return 7; }
            func main(){
                var values = [2,9] as list<any>;
                var index = identity(0);
                values[index] = change(index);
                dynamic var result = values[0] + values[1];
            }
        """)
        assertEquals(16, execute(main).read(main.scope.getVar("result") as MCInt))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        for (index in 0..1) {
            assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), binding.data.facts.read(binding.place.index(index))!!.type)
            assertEquals(ValueKnowledge.Unknown, binding.data.facts.read(binding.place.index(index))!!.value)
        }
        assertEquals(2, binding.data.facts.length(binding.place))
    }

    @Test fun staticScalarArgumentsWriteBackThroughTheirCapturedDynamicAddress() = modes {
        val main = compile("""
            func identity(value as int) -> int { return value; }
            func change(static index as int) -> int { index = 1; return 7; }
            func put(static value as int, next as int) { value = next; }
            func main(){
                var values = [2,9] as list<any>;
                var index = identity(0);
                put(values[index], change(index));
                dynamic var result = values[0] + values[1];
            }
        """)
        assertEquals(16, execute(main).read(main.scope.getVar("result") as MCInt))
        val root = main.scope.getVar("values")!!.storageBinding!!.place
        val call = main.typedIR!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>().last()
        assertEquals(setOf(root.unknownIndex()), assertIs<Effect.Writes>(call.effect).places)
        assertEquals(emptySet(), assertIs<Effect.Writes>(call.effect).contents)
    }

    @Test fun recursiveCallsKeepDynamicIndexPayloadsInsideTheirOwnFrame() = modes {
        for (version in listOf("26.3", "1.20.2")) {
            val main = compile("""
                func fetch(value as list<int>, index as int, count as int) -> int {
                    if(count <= 0){ return value[index]; }
                    return fetch(value, index, count - 1);
                }
                func main(){ var values = [2,9]; dynamic var result = fetch(values, 1, 3); }
            """, version)
            assertNotNull(function("fetch").typedIR)
            assertEquals(Effect.Pure, function("fetch").runtimeEffect)
            assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
        }
    }

    @Test fun staticCollectionRangeWritesPreserveCommonTypesWithoutProvingUntouchedUnknownElements() = modes {
        val main = compile("""
            func put(static values as list<int>, index as int) { values[index] = 7; }
            func main(){
                var values = [2,9] as list<any>;
                var view = values as list<int>;
                put(view, 0);
                dynamic var result = values[0] + values[1];
            }
        """)
        assertEquals(16, execute(main).read(main.scope.getVar("result") as MCInt))
        assertNotNull(function("put").typedIR)
        val binding = main.scope.getVar("values")!!.storageBinding!!
        for (index in 0..1) assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), binding.data.facts.read(binding.place.index(index))!!.type)
        MCFPPStringTest.readFromString("""
            func put(static value as int) { value = 7; }
            func inspect(values as list<any>, index as int, other as int) -> int {
                put(values[index] as int);
                return values[other] + 1;
            }
            func main(){ var values = [false,2] as list<any>; var result = inspect(values, 1, 0); }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("inspect").typedIR)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun namedNestedViewsKeepTheIndexSelectedWhenTheViewWasCreated() = modes {
        val main = compile("""
            func identity(value as int) -> int { return value; }
            func main(){
                var values = [[2],[9]];
                var index = identity(0);
                var view = values[index] as list<int>;
                index = 1;
                var i = 0;
                while(i < 2){ view[0] += 1; i += 1; }
                dynamic var result = values[0][0] + values[1][0];
            }
        """)
        assertEquals(13, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(main.scope.getVar("values")!!.storageBinding!!.data, main.scope.getVar("view")!!.storageBinding!!.data)
    }

    @Test fun copyingAnUnknownSelectedCollectionJoinsItsShapeAndKeepsTheCopyIndependent() = modes {
        val main = compile("""
            func identity(value as int) -> int { return value; }
            func main(){
                var values = [[2],[9]];
                var index = identity(0);
                var copied = values[index];
                copied[-1] = 7;
                dynamic var result = values[0][0] + copied[0];
            }
        """)
        assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
        val copy = main.scope.getVar("copied")!!.storageBinding!!
        assertEquals(1, copy.data.facts.length(copy.place))
        assertNotEquals(main.scope.getVar("values")!!.storageBinding!!.place.root, copy.place.root)
    }

    @Test fun unknownRangesWithMultipleActualTypesRequireAnExplicitView() {
        MCFPPStringTest.readFromString("""
            func identity(value as int) -> int { return value; }
            func main(){ var values = [2,false] as list<any>; var index = identity(0); var result = values[index] + 1; }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
        val main = compile("""
            func identity(value as int) -> int { return value; }
            func main(){ var values = [2,false] as list<any>; var index = identity(0); dynamic var result = (values[index] as int) + 1; }
        """)
        assertEquals(3, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun unknownNestedReplacementWithdrawsDescendantTypesAtEveryPossibleIndex() {
        MCFPPStringTest.readFromString("""
            func identity(value as int) -> int { return value; }
            func main(){
                var values = [[2],[9]] as list<any>;
                var index = identity(0);
                values[index] = [false];
                var result = values[1][0] + 1;
            }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun loopBackedgesWidenTypesAfterUnknownIndexWrites() = modes {
        MCFPPStringTest.readFromString("""
            func main(){
                var values = [2,9] as list<any>;
                var i = 0;
                while(i < 2){ var value = values[i] + 1; values[i] = false; i += 1; }
            }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun negativeRuntimeIndicesAreCapturedBeforeCollectionReplacement() = modes {
        val main = compile("""
            func identity(value as int) -> int { return value; }
            func main(){ var values = [2,9] as list<any>; var index = identity(-1); dynamic var result = values[index] + 1; }
        """)
        assertEquals(10, execute(main).read(main.scope.getVar("result") as MCInt))
        val replaced = compile("""
            func identity(value as int) -> int { return value; }
            func replace(static values as list<int>) -> int { values = [5,6,8]; return 7; }
            func main(){
                var values = [2,9];
                var index = identity(-1);
                values[index] = replace(values);
                dynamic var result = values[0] + values[1] + values[2];
            }
        """)
        assertEquals(20, execute(replaced).read(replaced.scope.getVar("result") as MCInt))
    }

    @Test fun targetsWithoutFunctionMacrosRejectDynamicAccessBeforeCommandGeneration() {
        for (version in listOf("1.20.1", "1.20")) {
            MCFPPStringTest.readFromString("""
                func identity(value as int) -> int { return value; }
                func main(){ var values = [2,9]; var index = identity(0); values[index] = 7; }
            """.trimIndent(), version = version)
            assertNotNull(function("main").typedIR)
            assertTrue(Project.errorCount > 0)
            assertTrue(Project.macroFunction.isEmpty())
        }
    }
}
