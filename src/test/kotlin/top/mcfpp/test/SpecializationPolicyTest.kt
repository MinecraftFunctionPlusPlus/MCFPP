package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.SpecializationPolicy
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDeclaredConcreteType
import kotlin.test.Test
import kotlin.test.*

class SpecializationPolicyTest {
    @Test fun compilerTypePayloadsUseInternalSpecializationThroughObjectAndAny() {
        MCFPPStringTest.readFromString("""
            func abstractValue(value as object) {}
            func flexibleValue(value as any) {}
            func main(){
                var payload as object = int;
                var flexible as any = int;
                abstractValue(payload);
                abstractValue(payload);
                abstractValue(float);
                flexibleValue(flexible);
                flexibleValue(float);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        assertEquals(2, function("abstractValue").compiledFunctions.size)
        assertEquals(2, function("flexibleValue").compiledFunctions.size)
        assertTrue(function("abstractValue").compiledFunctions.values.all { it.normalParams.isEmpty() })
        assertTrue(function("main").commands.analyzeAll().none { it.contains(".payload") })
        assertNotNull(top.mcfpp.analysis.ValueSnapshot.of(function("main").scope.getVar("payload")))
        assertNotNull(top.mcfpp.analysis.ValueSnapshot.of(function("main").scope.getVar("flexible")))
    }

    @Test fun compilerTypePayloadsCannotBecomeDynamicErasedValues() {
        for (type in listOf("object", "any")) {
            MCFPPStringTest.readFromString("func main(){ dynamic var payload as $type = int; }", version = "26.3")
            assertTrue(Project.errorCount > 0, type)
            assertTrue(function("main").commands.analyzeAll().none { it.contains("minecraft:from_int") })
        }
    }

    @Test fun compilerOnlyContainerParametersUseCompletePayloadSpecialization() {
        MCFPPStringTest.readFromString("""
            func inspect<T as type>(value as T) -> type { return value["kind"]; }
            func main(){
                var first = inspect<any>({kind:int});
                var second = inspect<any>({kind:int});
                var third = inspect<any>({kind:float});
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val generic = function("inspect") as GenericFunction
        assertEquals(2, generic.compiledFunctions.size)
        assertTrue(generic.compiledFunctions.values.all { it.normalParams.isEmpty() })
        assertEquals(MCFPPBaseType.Int, assertIs<MCFPPTypeVar>(function("main").scope.getVar("first")).value)
        assertEquals(MCFPPBaseType.Int, assertIs<MCFPPTypeVar>(function("main").scope.getVar("second")).value)
        assertEquals(MCFPPBaseType.Float, assertIs<MCFPPTypeVar>(function("main").scope.getVar("third")).value)
        assertTrue(function("main").commands.analyzeAll().none { "set value" in it || "set from" in it })
        executeMain()
    }

    @Test fun missingReturnPathsAreRejectedWhileAContinuationReturnIsAccepted() {
        MCFPPStringTest.readFromString("""
            func choose(flag as bool) -> int {
                if(flag){ return 1; }
            }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        MCFPPStringTest.readFromString("""
            func choose(flag as bool) -> int {
                if(flag){ return 1; }
                return 2;
            }
            func main(){ var first = choose(true); var second = choose(false); }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val machine = executeMain()
        assertEquals(1, machine.read(function("main").scope.getVar("first") as top.mcfpp.core.lang.MCInt))
        assertEquals(2, machine.read(function("main").scope.getVar("second") as top.mcfpp.core.lang.MCInt))
    }
    private fun function(name: String) = GlobalScope.localNamespaces["default.test"]!!.scope.functions.getValue(name).single()
    private fun executeMain(): top.mcfpp.test.util.ScoreCommandExecutor {
        val declarations = GlobalScope.localNamespaces["default.test"]!!.scope.functions.values.flatten()
        val functions = declarations.flatMap { listOf(it) + it.compiledFunctions.values }.associate { it.namespaceID.toString() to it.commands.analyzeAll() }
        return top.mcfpp.test.util.ScoreCommandExecutor(function("main").commands.analyzeAll(), functions).also { assertEquals(0, it.stackDepth) }
    }

    @Test fun ordinaryConstantCallsKeepOneBodyAndMaterializeParameters() {
        MCFPPStringTest.readFromString("""
            func increment(value as int) -> int { return value + 1; }
            func main(){
                var first = increment(1);
                var second = increment(2);
                var third = increment(3);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val increment = function("increment")
        assertTrue(increment.compiledFunctions.isEmpty())
        val commands = function("main").commands.analyzeAll()
        assertEquals(3, commands.count { it == "function ${increment.namespaceID}" })
        val parameter = increment.scope.getVar("value") as top.mcfpp.core.lang.MCInt
        assertEquals(3, commands.count { it.startsWith("scoreboard players set ${parameter.name} ${parameter.sbObject}") })
        val machine = executeMain()
        for ((name, value) in listOf("first" to 2, "second" to 3, "third" to 4))
            assertEquals(value, machine.read(function("main").scope.getVar(name) as top.mcfpp.core.lang.MCInt))
    }

    @Test fun genericCacheIgnoresOrdinaryConstantValuesButKeepsGenericValues() {
        MCFPPStringTest.readFromString("""
            func add<offset as int>(value as int) -> int { return offset + value; }
            func main(){
                var first = add<10>(1);
                var second = add<10>(2);
                var third = add<20>(1);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val generic = function("add") as GenericFunction
        assertEquals(2, generic.compiledFunctions.size)
        generic.compiledFunctions.values.forEach { compiled ->
            assertEquals(listOf(MCFPPBaseType.Int), compiled.normalParams.map { it.type })
            assertFalse(compiled.scope.getVar("value") is top.mcfpp.core.lang.MCFPPValue<*>)
        }
        val machine = executeMain()
        for ((name, value) in listOf("first" to 11, "second" to 12, "third" to 21))
            assertEquals(value, machine.read(function("main").scope.getVar(name) as top.mcfpp.core.lang.MCInt))
    }

    @Test fun genericTypeBindingsApplyBeforeNormalParameterAndReturnConstruction() {
        MCFPPStringTest.readFromString("""
            func identity<T as type>(value as T) -> T { return value; }
            func main(){
                var first = identity<int>(1);
                var second = identity<int>(2);
                var third = identity<float>(3.0);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val generic = function("identity") as GenericFunction
        assertEquals(2, generic.compiledFunctions.size)
        assertEquals(setOf(MCFPPBaseType.Int, MCFPPBaseType.Float), generic.compiledFunctions.values.map { it.returnType }.toSet())
        assertEquals(MCFPPBaseType.Int, function("main").scope.getVar("first")!!.type)
        assertEquals(MCFPPBaseType.Float, function("main").scope.getVar("third")!!.type)
    }

    @Test fun requiredValuesRemainInKeysWhileOrdinaryValuesDoNot() {
        val declaration = Function("specialize", context = null)
        val first = SpecializationPolicy.key(declaration, listOf(MCIntConcrete(10), MCIntConcrete(1)), listOf(true, false))
        val second = SpecializationPolicy.key(declaration, listOf(MCIntConcrete(10), MCIntConcrete(2)), listOf(true, false))
        val third = SpecializationPolicy.key(declaration, listOf(MCIntConcrete(20), MCIntConcrete(1)), listOf(true, false))
        assertEquals(first, second)
        assertNotEquals(first, third)
        assertFalse(SpecializationPolicy.requiresParameter(MCFPPBaseType.Int, MCIntConcrete(1)))
        assertTrue(SpecializationPolicy.requiresParameter(MCFPPDeclaredConcreteType(MCFPPBaseType.Int), MCIntConcrete(1)))
        assertTrue(SpecializationPolicy.requiresParameter(top.mcfpp.type.MCFPPConcreteType.Type, MCFPPTypeVar(MCFPPBaseType.Int)))
    }

    @Test fun nestedCallsDoNotOverwriteAnUnconsumedIntegerReturn() {
        MCFPPStringTest.readFromString("""
            func increment(value as int) -> int { return value + 1; }
            func add(left as int, right as int) -> int { return left + right; }
            func main(){
                var expression = increment(1) + increment(2);
                var arguments = add(increment(3), increment(4));
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val machine = executeMain()
        assertEquals(5, machine.read(function("main").scope.getVar("expression") as top.mcfpp.core.lang.MCInt))
        assertEquals(9, machine.read(function("main").scope.getVar("arguments") as top.mcfpp.core.lang.MCInt))
        assertTrue(function("increment").compiledFunctions.isEmpty())
        assertTrue(function("add").compiledFunctions.isEmpty())
    }

    @Test fun booleanCallResultsAreCapturedBeforeTheNextCall() {
        MCFPPStringTest.readFromString("""
            func identity(value as bool) -> bool { return value; }
            func main(){ var different = identity(true) != identity(false); }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val machine = executeMain()
        assertEquals(1, machine.read(function("main").scope.getVar("different") as top.mcfpp.core.lang.bool.ScoreBool))
        assertTrue(function("identity").compiledFunctions.isEmpty())
    }

    @Test fun runtimeBranchesDoNotTurnTheLastCompiledReturnIntoAConstant() {
        MCFPPStringTest.readFromString("""
            func choose(flag as bool) -> int {
                if(flag){ return 1; } else { return 2; }
            }
            func main(){ var first = choose(true); var second = choose(false); }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        assertFalse(function("choose").returnVar is top.mcfpp.core.lang.MCFPPValue<*>)
        assertFalse(function("main").scope.getVar("first") is top.mcfpp.core.lang.MCFPPValue<*>)
        val machine = executeMain()
        assertEquals(1, machine.read(function("main").scope.getVar("first") as top.mcfpp.core.lang.MCInt))
        assertEquals(2, machine.read(function("main").scope.getVar("second") as top.mcfpp.core.lang.MCInt))
    }
}
