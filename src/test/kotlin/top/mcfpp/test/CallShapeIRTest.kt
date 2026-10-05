package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueKnowledge
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class CallShapeIRTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        functions.values.flatten().filter { it.ast != null }.forEach { assertNotNull(it.typedIR, it.identifier) }
        return functions.getValue("main").single()
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

    @Test fun returnedNestedShapesKeepTypesAndLengthsButNoConstantValues() = modes {
        val main = compile("""
            func make() -> dict<any> { return {row:[2,9]} as dict<any>; }
            func forward(value as dict<any>) -> dict<any> { return value; }
            func main(){
                var values = forward(make());
                dynamic var result = values["row"][-1] + values["row"][0];
            }
        """, "1.20.1")
        assertEquals(11, execute(main).read(main.scope.getVar("result") as MCInt))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        val row = binding.place.field("row")
        assertEquals(2, binding.data.listSizes[row])
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), binding.data.facts.read(row.index(0))!!.type)
        assertEquals(ValueKnowledge.Unknown, binding.data.facts.read(row.index(0))!!.value)
    }

    @Test fun argumentsUseCapturedShapesBeforeLaterCallsReplaceTheirSource() = modes {
        val main = compile("""
            func replace(static value as list<any>) -> int { value = [false,false] as list<any>; return 0; }
            func forward(value as list<any>, ignored as int) -> list<any> { return value; }
            func main(){
                var values = [2,9] as list<any>;
                var copied = forward(values, replace(values));
                copied[0] = 5;
                dynamic var result = copied[-1] + copied[0];
            }
        """)
        assertEquals(14, execute(main).read(main.scope.getVar("result") as MCInt))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Bool.typeId), binding.data.facts.read(binding.place.index(0))!!.type)
    }

    @Test fun staticWholeReplacementCopiesNestedShapesAndForgetsObsoleteChildren() = modes {
        val main = compile("""
            func replace(static value as dict<any>, source as dict<any>) { value = source; }
            func main(){
                var values = {row:[false,false,false]} as dict<any>;
                var source = {row:[7,8]} as dict<any>;
                replace(values,source);
                source["row"][0] = 99;
                dynamic var result = values["row"][-1] + values["row"][0];
            }
        """)
        assertEquals(15, execute(main).read(main.scope.getVar("result") as MCInt))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        assertEquals(2, binding.data.listSizes[binding.place.field("row")])
        assertNull(binding.data.facts.read(binding.place.field("row").index(2)))
    }

    @Test fun branchingReturnsKeepOnlyCommonTypesAndShapes() = modes {
        val main = compile("""
            func choose(flag as bool) -> list<any> {
                if(flag){ return [2,9] as list<any>; }
                return [7] as list<any>;
            }
            func main(){ var values = choose(true); dynamic var result = values[0] + 1; }
        """)
        assertEquals(3, execute(main).read(main.scope.getVar("result") as MCInt))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        assertNull(binding.data.listSizes[binding.place])
        assertEquals(ValueKnowledge.Unknown, binding.data.facts.read(binding.place.index(0))!!.value)
    }

    @Test fun arrayReturnsPreserveLengthsAcrossOrdinaryCalls() = modes {
        val main = compile("""
            func make() -> IntArray { return [I; 2,9]; }
            func main(){ var values = make(); dynamic var result = values[-1]; }
        """, "1.20.1")
        assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun ordinaryArgumentsDoNotSelectReturnOrStaticWriteBranches() = modes {
        for (source in listOf("""
            func choose(flag as bool) -> list<any> {
                if(flag){ return [2] as list<any>; }
                return [false] as list<any>;
            }
            func main(){ var values = choose(true); var result = values[0] + 1; }
        """, """
            func change(static value as list<any>, flag as bool) {
                if(flag){ value = [false] as list<any>; }
            }
            func main(){ var values = [2] as list<any>; change(values,false); var result = values[0] + 1; }
        """)) {
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
            assertTrue(Project.errorCount > 0)
            assertNotNull(GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single().typedIR)
        }
    }
}
