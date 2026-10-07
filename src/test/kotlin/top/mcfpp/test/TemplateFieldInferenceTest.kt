package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class TemplateFieldInferenceTest {
    private fun compile(source: String, errors: Int = 0): Function {
        fun sourceCalls() = Function.extraFunction.commands.analyzeAll().filter { "function default.test:" in it }
        val discardedCalls = sourceCalls()
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(errors, Project.errorCount)
        assertEquals(discardedCalls, sourceCalls(), "Declaration binding must not execute a discarded source call")
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun template(name: String = "Box"): DataTemplate =
        GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue(name)

    private fun execute(main: Function): ScoreCommandExecutor {
        val functions = LinkedHashMap<String, List<String>>()
        fun collect(function: Function) {
            functions[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
        }
        GlobalScope.localNamespaces.values.forEach { namespace ->
            namespace.scope.functions.values.flatten().forEach(::collect)
            namespace.scope.template.values.forEach { template ->
                template.constructors.forEach(::collect)
                template.scope.forEachFunction(::collect)
            }
        }
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main.commands.analyzeAll(), functions).also { assertEquals(0, it.stackDepth) }
    }

    @Test fun forwardCallsAndIncomingParametersBindBeforeBodiesAndExecutePerInstance() {
        val main = compile("""
            data Box {
                @DataOnly
                const value = produce(initial);
                var next = this.value + 1;
                constructor(initial as int){}
            }
            func produce(value as int) -> int {
                /say produced
                return value;
            }
            func inspect(box as Box) -> int { return box.next; }
            func main(){
                var first = Box(3);
                var second = Box(6);
                dynamic var result = inspect(first)*10 + inspect(second);
            }
        """)
        val box = template()
        assertTrue(box.deferredFields.isEmpty())
        assertEquals(MCFPPBaseType.Int.typeId, box.scope.getVar("value")!!.type.typeId)
        assertTrue((box.scope.getVar("value") as MCInt).isDataOnly)
        val machine = execute(main)
        assertEquals(listOf("produced", "produced"), machine.messages)
        assertEquals(47, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun erasedReturnEvidenceAndOrdinaryConstructorValuesDoNotChangeFieldConstraints() {
        val main = compile("""
            data Box {
                var erased as any = produce(initial);
                const value = this.erased + 1;
                constructor(initial as int){}
            }
            func produce(value as int) -> any { return value; }
            func main(){
                var first = Box(3);
                var second = Box(6);
                dynamic var result = first.value*10 + second.value;
            }
        """)
        val value = template().scope.getVar("value")!!
        assertEquals(MCFPPBaseType.Int.typeId, value.type.typeId)
        assertFalse(value.symbol!!.mutable)
        assertEquals(47, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun inconsistentConstructorsAndPendingReadsDiagnoseWithoutChoosingTheFirstCall() {
        compile("""
            data Box {
                const value = initial;
                constructor(initial as int){}
                constructor(initial as bool){}
            }
            func main(){}
        """, errors = 1)
        assertNull(template().scope.getVar("value"))
        for (fields in listOf("var value = this.value + 1;", "var value = this.later + 1; var later = 4;")) {
            compile("data Box {$fields}\nfunc main(){}", errors = 1)
            assertNull(template().scope.getVar("value"))
        }
    }

    @Test fun inheritedInferredFieldsAreAvailableToUncalledTemplateSignatures() {
        compile("""
            data Parent { var value = 4; }
            data Child: Parent {}
            func inspect(child as Child) -> int { return child.value; }
            func copy(child as Child) -> Child { return child; }
            func main(){}
        """)
        assertEquals(MCFPPBaseType.Int.typeId, template("Child").scope.getVar("value")!!.type.typeId)
        val copy = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("copy").single()
        assertNotNull(copy.returnVar.getMemberVar("value", top.mcfpp.model.Member.AccessModifier.PUBLIC).first)
    }

    @Test fun anonymousTemplateSignaturesAlsoWaitForForwardFunctionDeclarations() {
        compile("""
            func inspect(box as data { const value = produce(); }) -> int { return box.value; }
            func produce() -> int { return 4; }
            func main(){}
        """)
        val inspect = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("inspect").single()
        val parameter = inspect.scope.getVar("box")!!
        assertEquals(parameter.symbol!!.id, parameter.storageBinding!!.place.root)
        assertNotNull(parameter.getMemberVar("value", top.mcfpp.model.Member.AccessModifier.PUBLIC).first)
    }

    @Test fun annotationsApplyToCompletedFieldsAndKeepInheritedReplacementOrder() {
        compile("""
            @DataOnly
            data Box { var value = 4; }
            data Parent { var value as int = 1; }
            data Child: Parent {
                @DataOnly
                const value = 8;
            }
            func main(){}
        """)
        assertTrue((template().scope.getVar("value") as MCInt).isDataOnly)
        val inherited = template("Parent").scope.getVar("value")!!
        assertSame(inherited, template("Child").scope.getVar("value"))
        assertFalse(inherited.isConst)
        assertTrue((inherited as MCInt).isDataOnly)
        assertTrue(inherited.annotations.any { it is top.mcfpp.mni.annotation.DataOnly })
    }
}
