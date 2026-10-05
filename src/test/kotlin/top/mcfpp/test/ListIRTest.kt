package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class ListIRTest {
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

    @Test fun listSplicesAndLoopGrowthUseTheSameIR() = modes {
        for (version in listOf("26.3", "1.20.2", "1.20")) {
            val main = compile("""
                func main(){
                    var values = [2,9] as list<any>;
                    values.prepend(1);
                    values.insert(-1,7);
                    values.removeAt(1);
                    values.addAll([8,6]);
                    values.prependAll([4,5]);
                    var i = 0;
                    while(i < 2){ values.add(i); i += 1; }
                    dynamic var result = values[0] + values[1] + values[2] + values[3] + values[4] + values[5] + values[6] + values[7] + values[8];
                    values.clear();
                    values.add(3);
                    dynamic var cleared = values[0] + 0;
                }
            """, version)
            val machine = execute(main)
            assertEquals(41, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(3, machine.read(main.scope.getVar("cleared") as MCInt))
        }
    }

    @Test fun movingNestedElementsKeepsTheirShapesAndSelfAppendCopiesTheInput() = modes {
        val main = compile("""
            func main(){
                var values = [[2],[9]];
                values.prepend([7]);
                values.removeAt(1);
                values.addAll(values);
                values[0][0] = 8;
                var copied = values[2];
                copied[-1] = 6;
                dynamic var result = values[0][0] + values[1][-1] + values[2][-1] + copied[0];
            }
        """)
        assertEquals(30, execute(main).read(main.scope.getVar("result") as MCInt))
        val root = main.scope.getVar("values")!!.storageBinding!!
        assertEquals(4, root.data.facts.length(root.place))
        assertEquals(1, root.data.facts.length(root.place.index(2)))
    }

    @Test fun dynamicInsertionCapturesTheIndexValueBeforeLaterArguments() = modes {
        for (version in listOf("26.3", "1.20.2")) {
            val main = compile("""
                func identity(value as int) -> int { return value; }
                func change(static index as int) -> int { index = 0; return 7; }
                func main(){
                    var values = [2,9] as list<any>;
                    var index = identity(-1);
                    values.insert(index, change(index));
                    values.removeAt(identity(0));
                    dynamic var result = values[0] + values[1];
                }
            """, version)
            assertEquals(16, execute(main).read(main.scope.getVar("result") as MCInt))
        }
    }

    @Test fun staticListMutationHasAReceiverEffectAndKeepsFramesIndependent() = modes {
        val main = compile("""
            func append(static values as list<int>, count as int) {
                if(count > 0){ values.add(count); append(values, count - 1); }
            }
            func main(){
                var values = [2];
                append(values, 2);
                dynamic var result = values[0] + values[1] + values[2];
            }
        """)
        assertEquals(5, execute(main).read(main.scope.getVar("result") as MCInt))
        val append = function("append")
        assertNotNull(append.typedIR)
        assertEquals(setOf(Place(append.typedIR!!.parameters.first())), assertIs<Effect.Writes>(append.runtimeEffect).places)
    }

    @Test fun invalidWritesAreDiagnosedBeforeBackendCommands() {
        val cases = listOf(
            "func main(){ var values = [2,9] as list<any>; values.add(true); }" to "1.20.2",
            "func main(){ var values = [2]; values.insert(2,7); }" to "26.3",
            "func main(){ var values = [] as list<int>; values.removeAt(-1); }" to "26.3",
            "func main(){ var values = [2] as ImmutableList<int>; values.clear(); }" to "26.3",
            "func identity(value as int) -> int { return value; }\nfunc main(){ var values = [2]; values.insert(identity(0),7); }" to "1.20",
            "func main(){ var values = [2] as list<any>; var i = 0; while(i < 2){ var value = values[i] + 1; values.add(true); i += 1; }; }" to "26.3"
        )
        for ((source, version) in cases) {
            MCFPPStringTest.readFromString(source, version = version)
            assertNotNull(function("main").typedIR)
            assertTrue(Project.errorCount > 0, source)
            assertTrue(Project.macroFunction.isEmpty())
        }
        val main = compile("""
            func main(){
                var values = [2] as list<any>;
                var i = 0;
                while(i < 2){ values.add(true); i += 1; }
                values.prependAll([]);
                values.addAll([]);
                dynamic var result = values[0] + 1;
            }
        """)
        assertEquals(3, execute(main).read(main.scope.getVar("result") as MCInt))
    }
}
