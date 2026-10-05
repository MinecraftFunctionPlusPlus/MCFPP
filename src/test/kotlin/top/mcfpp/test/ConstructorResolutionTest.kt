package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class ConstructorResolutionTest {
    private fun compile(source: String) = MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
    private fun main() = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    private fun box() = GlobalScope.getTemplate("default.test", "Box")!!

    private fun execute(): ScoreCommandExecutor {
        val functions = LinkedHashMap<String, List<String>>()
        fun collect(function: Function) {
            functions[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
        }
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.forEach(::collect)
        box().constructors.forEach(::collect)
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main().commands.analyzeAll(), functions).also { assertEquals(0, it.stackDepth) }
    }

    @Test fun exactConstructorWinsInEitherDeclarationOrder() {
        val integer = """
            constructor(value as int){
                /say integer
            }
        """.trimIndent()
        val floating = """
            constructor(value as float){
                /say floating
            }
        """.trimIndent()
        for (constructors in listOf("$floating\n$integer", "$integer\n$floating")) {
            compile("""
                data Box {
                    $constructors
                }
                func main(){ Box(1); }
            """)
            assertEquals(0, Project.errorCount)
            assertEquals(listOf("integer"), execute().messages)
        }
    }

    @Test fun constantConstructorRejectsUnknownParametersAndAcceptsLiterals() {
        compile("""
            data Box {
                constructor(value as int!){
                    /say constant
                }
            }
            func rejected(value as int){ Box(value); }
            func main(){}
        """)
        assertEquals(1, Project.errorCount)
        assertTrue(box().constructors.all { it.compiledFunctions.isEmpty() })
        assertTrue(execute().messages.isEmpty())
        compile("""
            data Box {
                constructor(value as int!){
                    /say constant
                }
            }
            func main(){ Box(1); }
        """)
        assertEquals(0, Project.errorCount)
        assertEquals(listOf("constant"), execute().messages)
    }

    @Test fun omittedArgumentsUseConstructorDefaults() {
        val direct = """
            if(value == 3){
                /say default
            }
        """.trimIndent()
        val chained = """
            if(value != 3){
                /say wrong
            }else if(value == 3){
                /say default
            }
        """.trimIndent()
        for (body in listOf(direct, chained)) {
            compile("""
                data Box {
                    constructor(value as int = 3){
                        $body
                    }
                }
                func main(){ Box(); }
            """)
            assertEquals(0, Project.errorCount)
            assertEquals(listOf("default"), execute().messages)
        }
    }

    @Test fun incomparableCandidatesReportAmbiguityBeforeInitializingAnObject() {
        compile("""
            data Box {
                constructor(left as int, right as float){
                    /say first
                }
                constructor(left as float, right as int){
                    /say second
                }
            }
            func main(){ Box(1,1); }
        """)
        assertEquals(1, Project.errorCount)
        assertTrue(box().constructors.all { it.compiledFunctions.isEmpty() })
        assertTrue(main().scope.allVars.none { it is top.mcfpp.core.lang.obj.DataTemplateObject })
        assertTrue(main().commands.analyzeAll().none {
            it.startsWith("function ") || it.startsWith("data modify ") && it != "data modify storage mcfpp:system stack_frame prepend value {}"
        })
        assertTrue(execute().messages.isEmpty())
    }
}
