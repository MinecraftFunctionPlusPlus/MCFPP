package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Factory selection replaces the former parent-constructor execution plan. */
class DataInheritanceConstructorPlanTest {
    private fun compile(source: String, valid: Boolean = true): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        if (valid) assertEquals(0, Project.errorCount) else assertTrue(Project.errorCount > 0)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun template(name: String) = GlobalScope.getTemplate("default.test", name)!!

    private fun execute(main: Function): ScoreCommandExecutor {
        val functions = linkedMapOf<String, List<String>>()
        fun collect(function: Function) {
            functions[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
        }
        GlobalScope.localNamespaces.values.forEach { namespace ->
            namespace.scope.functions.values.flatten().forEach(::collect)
            namespace.scope.template.values.forEach { it.scope.forEachFunction(::collect) }
        }
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main.commands.analyzeAll(), functions)
    }

    @Test fun anExactEmptyFactoryBeatsAnOverloadWithDefaults() {
        val main = compile("""
            data Box {
                var value: int
                new(value: int = 2) { return { value } }
                new() { return { value: 1 } }
            }
            func main() { var box = Box(); external result = box.value }
        """)
        val selected = assertIs<ParameterMatcher.TypeSelection.Selected>(template("Box").resolveFactory(emptyList()))
        assertTrue(selected.function.normalParams.isEmpty())
        assertEquals(1, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun factorySelectionDoesNotCompileBodiesOrBindActualArgumentValues() {
        compile("""
            data Box {
                var value: int
                new(value: int) {
                    /say integer
                    return { value }
                }
                new(value: bool) {
                    /say boolean
                    return { value: 0 }
                }
            }
            func main() {}
        """)
        val box = template("Box")
        val selected = assertIs<ParameterMatcher.TypeSelection.Selected>(box.resolveFactory(listOf(MCInt(3))))
        assertSame(box.factories.first(), selected.function)
        assertTrue(box.factories.all { !it.bodyCompiled && it.compiledFunctions.isEmpty() })
        assertTrue(box.factories.all { it.scope.vars["this"] == null })
    }

    @Test fun equallyApplicableDefaultFactoriesRemainAmbiguousInEitherOrder() {
        for (declarations in listOf(
            "new(value: int = 1) { return { value } }\nnew(flag: bool = true) { return { value: 2 } }",
            "new(flag: bool = true) { return { value: 2 } }\nnew(value: int = 1) { return { value } }"
        )) {
            compile("data Box { var value: int; $declarations }\nfunc main() {}")
            assertIs<ParameterMatcher.TypeSelection.Ambiguous>(template("Box").resolveFactory(emptyList()))
            assertTrue(template("Box").factories.all { !it.bodyCompiled })
        }
    }

    @Test fun parentFactoryParametersImposeNoChildConstructionObligation() {
        val main = compile("""
            data Parent { var value: int; new(required: int) { return { value: required } } }
            data Child: Parent { new() { return { value: 7 } } }
            func main() { var child = Child(); external result = child.value }
        """)
        assertEquals(7, execute(main).read(main.scope.getVar("result") as MCInt))
        assertFalse(template("Parent").factories.single().bodyCompiled)
        assertTrue(template("Child").constructors.isEmpty())
        assertSame(template("Child"), template("Child").factories.single().owner)
    }

    @Test fun aRequiredInheritedFieldDoesNotImportAParentDefaultFactory() {
        compile("""
            data Parent { var value: int = 4 }
            data Child: Parent {}
            func main() { var child = Child() }
        """, valid = false)
        assertTrue(template("Child").factories.isEmpty())
        assertTrue(template("Parent").factories.single().isImplicit)
    }
}
