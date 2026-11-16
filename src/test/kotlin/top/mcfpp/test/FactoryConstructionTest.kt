package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.util.compilerDiagnostics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryConstructionTest {
    private fun compile(source: String, valid: Boolean = true): Function? {
        val previousConfig = Project.config
        val previousIgnore = CompileSettings.ignoreStdLib
        Project.config = ProjectConfig()
        CompileSettings.ignoreStdLib = true
        try {
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
            if (valid) assertEquals(0, Project.errorCount)
            else assertTrue(Project.errorCount > 0, "Expected a factory diagnostic")
            return GlobalScope.localNamespaces["default.test"]?.scope?.functions?.get("main")?.singleOrNull()
        } finally {
            Project.config = previousConfig
            CompileSettings.ignoreStdLib = previousIgnore
        }
    }

    private fun execute(main: Function): ScoreCommandExecutor {
        val bodies = linkedMapOf<String, List<String>>()
        fun collect(function: Function) {
            bodies[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
        }
        for (namespace in GlobalScope.localNamespaces.values) {
            namespace.scope.functions.values.flatten().forEach(::collect)
            namespace.scope.template.values.forEach { template ->
                template.scope.forEachFunction(::collect)
                (template as? top.mcfpp.model.compound.GenericDataTemplate)?.compiledTemplates?.values?.forEach { actual ->
                    actual.scope.forEachFunction(::collect)
                }
            }
        }
        bodies.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main.commands.analyzeAll(), bodies)
    }

    @Test fun constructionAndNamedFactoryCallsReturnIndependentValues() {
        val main = compile("""
            data Box {
                var value: int
                new(value: int) { return { value } }
            }
            func main() {
                var first = Box(1)
                var second = Box.new(2)
                first.value = 9
                external result = first.value * 10 + second.value
            }
        """)!!
        assertEquals(92, execute(main).read(main.scope.getVar("result") as MCInt))
        val factory = GlobalScope.getTemplate("default.test", "Box")!!.factories.single()
        assertTrue(factory.isStatic)
        assertFalse(factory.scope.vars.containsKey("this"))
    }

    @Test fun initializerFactoryCoexistsWithExplicitOverloadAndExplicitDefaultReplacesIt() {
        val main = compile("""
            data Defaults {
                var value: int = 4
                new(value: int) { return { value } }
            }
            data Replaced {
                var value: int = 4
                new() { return { value: 7 } }
            }
            data Empty {}
            func main() {
                var first = Defaults()
                var second = Defaults(2)
                var third = Replaced()
                var empty = Empty()
                external result = first.value * 100 + second.value * 10 + third.value
            }
        """)!!
        assertEquals(427, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(2, GlobalScope.getTemplate("default.test", "Defaults")!!.factories.size)
        assertFalse(GlobalScope.getTemplate("default.test", "Replaced")!!.factories.single().isImplicit)
        assertTrue(GlobalScope.getTemplate("default.test", "Empty")!!.factories.single().isImplicit)
    }

    @Test fun genericFactoryReturnsTheInstantiatedSelf() {
        val main = compile("""
            data Box<T> {
                var value: T
                new(value: T) { return { value } }
            }
            func main() {
                var box = Box<int>(8)
                external result = box.value
            }
        """)!!
        assertEquals(8, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun implicitFactoriesKeepDeclaredCollectionContextsForActualGenericOwners() {
        compile("""
            data Item { var value: int = 1 }
            data Buffer<T> {
                var values: list<T> = []
                var lookup: dict<T>? = {}
            }
            func main() {
                var primitive = Buffer<int>()
                var nominal = Buffer<Item>()
            }
        """)
    }

    @Test fun typeFactoryUsesExplicitArgumentsAndReturnsIndependentValues() {
        val main = compile("""
            data Parent {
                var value: int
                new(source: { value: int }, amount: int) { return { value: source.value + amount } }
            }
            data Child: Parent {
                new(value: int) { return { value } }
                func copy(): Parent { return Parent.new(this, 2) }
            }
            data Other {
                var value: int
                new(value: int) { return { value } }
            }
            func main() {
                var child = Child(4)
                var copied = child.copy()
                var other = Other(7)
                var explicit = Parent.new(other, 1)
                copied.value = 9
                external result = child.value * 100 + copied.value * 10 + explicit.value
            }
        """)!!
        assertEquals(498, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(1, GlobalScope.getTemplate("default.test", "Child")!!.factories.size)
        compile("""
            data Parent { var value: int; new(source: { value: int }) { return { value: source.value } } }
            data Other {
                var value: int
                new(value: int) { return { value } }
                func copy(): Parent { return Parent.new() }
            }
            func main() { var other = Other(7); var copied = other.copy() }
        """, valid = false)
    }

    @Test fun incompleteDefaultAndFactoryReturnAreRejected() {
        compile("""
            data Missing { var value: int }
            func main() { var missing = Missing() }
        """, valid = false)
        compile("""
            data Missing { var value: int; new() { return {} } }
            func main() { var missing = Missing() }
        """, valid = false)
    }

    @Test fun factoriesHaveNoImplicitReceiverAndRequireExplicitReturnsOnEveryExit() {
        compile("""
            data Box { var value: int; new() { return { value: this.value } } }
            func main() { var box = Box() }
        """, valid = false)
        compile("""
            data Box { var value: int; new(flag: bool) { if flag { return { value: 1 } } } }
            func main() { var box = Box(true) }
        """, valid = false)
    }

    @Test fun oldDataConstructorsProduceAMigrationDiagnostic() {
        compile("""
            data Box { var value: int; constructor(value: int) { this.value = value } }
            func main() {}
        """, valid = false)
    }

    @Test fun discardedCallsCannotInvokeAPrivateFactory() {
        for (call in listOf("Box()", "Box.new()")) {
            val main = compile("""
                data Box {
                    private new() {
                        /say private-factory
                        return {}
                    }
                }
                func main() { $call }
            """, valid = false)!!
            val factory = GlobalScope.getTemplate("default.test", "Box")!!.factories.single()
            assertFalse(factory.bodyCompiled)
            assertTrue(main.commands.analyzeAll().none { it == "function ${factory.namespaceID}" })
        }
    }

    @Test fun factoriesMustBeCalledThroughTheirTypeNamespace() {
        for (call in listOf("box.new(2)", "var copied = box.new(2)")) {
            val messages = compilerDiagnostics {
                compile("""
                    data Box { var value: int; new(value: int) { return { value } } }
                    func main() {
                        var box = Box.new(1)
                        $call
                    }
                """, valid = false)
            }.joinToString("\n")
            assertTrue("type namespace" in messages, messages)
        }
    }
}
