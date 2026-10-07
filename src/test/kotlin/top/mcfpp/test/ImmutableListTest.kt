package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class ImmutableListTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }).also {
        assertEquals(0, it.stackDepth)
    }

    @Test fun constantAndRuntimeFactoriesKeepImmutableIdentityAndOneMemberTable() {
        compile("func main(){}")
        val type = MCFPPImmutableListType(MCFPPBaseType.Int)
        val runtime = type.buildUnConcrete("runtime")
        val known = type.build("constant")
        val member = known.getMemberFunction("toText", emptyList(), emptyList(), top.mcfpp.model.Member.AccessModifier.PUBLIC).first
        assertIs<top.mcfpp.model.function.NativeFunction>(member)
        assertSame(member, runtime.getMemberFunction("toText", emptyList(), emptyList(), top.mcfpp.model.Member.AccessModifier.PUBLIC).first)
        assertEquals(type.typeId, runtime.type.typeId)
        assertEquals(type.typeId, known.type.typeId)
        assertEquals(type.typeId, type.buildUnConcrete("runtime").clone().type.typeId)
    }

    @Test fun readOnlyViewsObserveWritesThroughTheMutableSource() {
        val main = compile("""
            func main(){
                var source = [2,3];
                var view = source as ImmutableList<int>;
                var first = view[0];
                source[0] = 7;
                dynamic var result = view[0]*10 + view[-1];
            }
        """)
        assertEquals(73, execute(main).read(main.scope.getVar("result") as MCInt))
        val source = main.scope.getVar("source")!!
        val view = main.scope.getVar("view")!!
        assertSame(source.storageBinding!!.data, view.storageBinding!!.data)
    }

    @Test fun ordinaryImmutableCopiesAndRuntimeParametersAndReturnsUseIndependentValues() {
        val main = compile("""
            func copy(values as ImmutableList<int>) -> ImmutableList<int> { return values; }
            func main(){
                var source = [2,3];
                var view = source as ImmutableList<int>;
                var copied = view;
                var returned = copy(view);
                source[0] = 7;
                dynamic var result = view[0]*100 + copied[0]*10 + returned[0];
            }
        """)
        assertEquals(722, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(MCFPPImmutableListType(MCFPPBaseType.Int).typeId, main.scope.getVar("returned")!!.type.typeId)
    }

    @Test fun runtimeQueriesHaveIndependentResultsAndSupportOldTargets() {
        val main = compile("""
            func lookup(values as ImmutableList<int>, value as int) -> int {
                var first = values.indexOf(value);
                var last = values.lastIndexOf(value);
                return first*100 + last*10 + values[0];
            }
            func contains(values as ImmutableList<int>, value as int) -> bool { return values.contains(value); }
            func main(){
                var values = [2,4,2] as ImmutableList<int>;
                dynamic var result = lookup(values,2);
                dynamic var found = contains(values,4);
            }
        """, "1.20.1")
        val machine = execute(main)
        assertEquals(22, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("found") as ScoreBool))
        assertTrue(Project.macroFunction.isEmpty())
    }

    @Test fun immutableViewsRejectElementWritesAndMutatingMembers() {
        for (declaration in listOf(
            "var values = [2,3] as ImmutableList<int>;",
            "var view = [2,3] as ImmutableList<int>; var values = view;",
            "var values = copy([2,3] as ImmutableList<int>);")) {
            for (action in listOf("values[0] = 7;", "values.add(7);", "values.clear();", "values.removeAt(0);")) {
                MCFPPStringTest.readFromString("func copy(value as ImmutableList<int>) -> ImmutableList<int> { return value; } func main(){ $declaration $action }", version = "26.3")
                assertTrue(Project.errorCount > 0, "$declaration $action")
            }
        }
    }

}
