package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataInheritanceConstructionTest {
    private fun compile(source: String, output: java.nio.file.Path? = null): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3", targetPath = output?.toString())
        assertEquals(0, Project.errorCount)
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
            namespace.scope.template.values.forEach { template ->
                template.scope.forEachFunction(::collect)
                (template as? top.mcfpp.model.compound.GenericDataTemplate)?.compiledTemplates?.values?.forEach { actual ->
                    actual.scope.forEachFunction(::collect)
                }
            }
        }
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main.commands.analyzeAll(), functions)
    }

    @Test fun parentFactoriesAndInitializersDoNotExecuteForChildFactories() {
        val main = compile("""
            func initial(): int {
                /say initializer
                return 1
            }
            data Parent {
                var value: int = initial()
                new() {
                    /say parent
                    return { value: 2 }
                }
            }
            data Child: Parent {
                new() {
                    /say child
                    return { value: 3 }
                }
            }
            func main() { var child = Child(); external result = child.value }
        """)
        val machine = execute(main)
        assertEquals(listOf("child"), machine.messages)
        assertEquals(3, machine.read(main.scope.getVar("result") as MCInt))
        assertFalse(template("Parent").factories.single().bodyCompiled)
    }

    @Test fun childFactoriesExplicitlyComposeIndependentParentResults() {
        val main = compile("""
            data Parent {
                var value: int
                new(value: int) {
                    /say parent
                    return { value }
                }
            }
            data Child: Parent {
                var extra: int
                new(value: int, extra: int) {
                    var parent = Parent(value)
                    /say child
                    return { value: parent.value, extra }
                }
            }
            func main() {
                var first = Child(2, 3)
                var second = Child(4, 5)
                first.value = 9
                external result = first.value * 100 + second.value * 10 + second.extra
            }
        """)
        val machine = execute(main)
        assertEquals(listOf("parent", "child", "parent", "child"), machine.messages)
        assertEquals(945, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(0, machine.stackDepth)
    }

    @Test fun diamondFieldLayoutsHaveOneSlotWithoutAnyImplicitFactoryStages() {
        for (parents in listOf("Left, Right", "Right, Left")) {
            val main = compile("""
                data Root {
                    var value: int
                    new(value: int) {
                        /say root
                        return { value }
                    }
                }
                data Left: Root {}
                data Right: Root {}
                data Child: $parents { new(value: int) { return { value } } }
                func main() { var child = Child(8); external result = child.value }
            """)
            val machine = execute(main)
            assertTrue(machine.messages.isEmpty())
            assertEquals(8, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(1, template("Child").scope.allVars.count { it.identifier == "value" })
            assertFalse(template("Root").factories.single().bodyCompiled)
        }
    }

    @Test fun genericFactoriesUseFullyInstantiatedSelfAndNoAncestorStages() {
        val main = compile("""
            data Parent<T> {
                var value: T
                new(value: T) {
                    /say parent
                    return { value }
                }
            }
            data Child<T>: Parent<T> { new(value: T) { return { value } } }
            func main() { var child = Child<int>(6); external result = child.value }
        """)
        val machine = execute(main)
        assertTrue(machine.messages.isEmpty())
        assertEquals(6, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun exportedFactoriesContainExplicitParentCallsAndReturnSlots() {
        val output = Files.createTempDirectory("mcfpp-factory-parent-export-")
        try {
            val main = compile("""
                data Parent { var value: int; new(value: int) { return { value } } }
                data Child: Parent {
                    new(value: int) { var parent = Parent.new(value); return { value: parent.value } }
                }
                func main() { var child = Child(5); external result = child.value }
            """, output)
            val parent = template("Parent").factories.single()
            val child = template("Child").factories.single()
            val functions = output.resolve("debug/data/default.test/function")
            val parentFile = functions.resolve("${parent.namespaceID.identifier}.mcfunction")
            val childFile = functions.resolve("${child.namespaceID.identifier}.mcfunction")
            assertTrue(Files.exists(parentFile))
            assertTrue(Files.exists(childFile))
            assertTrue(Files.readString(childFile).contains("function ${parent.namespaceID}"))
            assertTrue(Files.readString(parentFile).contains("stack_frame[0].return"))
            assertEquals(5, execute(main).read(main.scope.getVar("result") as MCInt))
        } finally {
            output.toFile().deleteRecursively()
        }
    }
}
