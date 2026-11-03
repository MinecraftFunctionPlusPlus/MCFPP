package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPListType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceMethodTest {
    @Test fun moreSpecificDiamondOverrideWinsInBothParentOrders() {
        for (parents in listOf("L,R", "R,L")) {
            val main = compile(
                """
                data A {func f():int{return 1;}}
                data L:A {}
                data R:A {override func f():int{return 2;}}
                data C:$parents {}
                func main(){var c=C();external result=c.f();}
            """
            )
            assertEquals(0, Project.errorCount)
            assertSame(template("R"), template("C").scope.getFunctionCandidates("f").single().owner)
            assertEquals(2, execute(main).read(main.scope.getVar("result") as MCInt))
        }
    }

    @Test fun commonDiamondContributesOnlyOneOriginalMethod() {
        val main = compile(
            """
            data A {func f():int{return 1;}}
            data L:A {}
            data R:A {}
            data C:L,R {}
            func main(){var c=C();external result=c.f();}
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(1, template("C").scope.getFunctionCandidates("f").size)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun exactOverloadBeatsConversionInEitherParentOrder() {
        for (parents in listOf("A,B", "B,A")) {
            val main = compile(
                """
                data A {func f(x:object):int{return 1;}}
                data B {func f(x:int):int{return 2;}}
                data C:$parents {}
                func main(){var c=C();external result=c.f(3);}
            """
            )
            assertEquals(0, Project.errorCount)
            assertEquals(2, execute(main).read(main.scope.getVar("result") as MCInt))
        }
    }

    @Test fun unrelatedSameSignatureIsAmbiguousWithBothDeclarationOrigins() {
        for (parents in listOf("A,B", "B,A")) {
            compile(
                """
                data A {func f():int{return 1;}}
                data B {func f():int{return 2;}}
                data C:$parents {}
                func main(){}
            """
            )
            val child = template("C")
            val messages = compilerDiagnostics {
                assertIs<UnknownFunction>(child.scope.getFunction("f", emptyList(), emptyList()))
            }
            assertTrue(Project.errorCount > 0, "The ambiguous member query must be diagnosed")
            assertAmbiguousOrigins(messages.joinToString("\n"), "A", "B")
            val failure = assertFailsWith<DeclarationBinding.Failure> { infer(child, "this.f()") }
            assertAmbiguousOrigins(failure.message.orEmpty(), "A", "B")
        }
    }

    @Test fun incomparableOverloadsDoNotGenerateTheFirstParentCall() {
        compile(
            """
            data A {func f(x:int,y:object):int{return 1;}}
            data B {func f(x:object,y:int):int{return 2;}}
            data C:A,B {}
            func main(){}
        """
        )
        assertEquals(0, Project.errorCount)
        val child = template("C")
        val before = Function.currFunction.commands.analyzeAll().toList()
        val arguments = listOf(MCInt(1), MCInt(2))
        val messages = compilerDiagnostics {
            assertIs<UnknownFunction>(child.scope.getFunction("f", emptyList(), arguments))
        }
        assertAmbiguousOrigins(messages.joinToString("\n"), "A", "B")
        val failure = assertFailsWith<DeclarationBinding.Failure> { infer(child, "this.f(1,2)") }
        assertAmbiguousOrigins(failure.message.orEmpty(), "A", "B")
        assertEquals(before, Function.currFunction.commands.analyzeAll())
    }

    @Test fun inferenceAndInvocationChooseTheSameOverloadWithoutExecutingInference() {
        val discarded = Function.extraFunction.commands.analyzeAll().toList()
        val main = compile(
            """
            data A {func f(x:object):int{return 1;}}
            data B {
                var calls:int=0;
                func f(x:int):bool{this.calls=this.calls+1;return true;}
            }
            data C:A,B {var result=this.f(3);}
            func main(){
                var c=C();
                external initialCalls=c.calls;
                external initialResult=c.result;
                external direct=c.f(3);
                external finalCalls=c.calls;
            }
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(MCFPPBaseType.Bool.typeId, template("C").scope.getVar("result")!!.type.typeId)
        assertEquals(discarded, Function.extraFunction.commands.analyzeAll(), "Type inference must not emit discarded calls")
        val machine = execute(main)
        assertEquals(1, machine.read(main.scope.getVar("initialCalls") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("initialResult") as ScoreBool))
        assertEquals(1, machine.read(main.scope.getVar("direct") as ScoreBool))
        assertEquals(2, machine.read(main.scope.getVar("finalCalls") as MCInt))
    }

    @Test fun readonlyConversionRanksAndBothKindsOfDefaultsAgreeAcrossQueries() {
        val programs = listOf(
            """
                data A {func f<N:float>(x:int):int{return 1;}}
                data B {func f<N:int>(x:int):bool{return true;}}
                data C:A,B {var result=this.f<1>(3);}
                func main(){var c=C();external result=c.result;}
            """,
            """
                data A {func f<N:int=1>(x:int):int{return 1;}}
                data B {func f(x:int):bool{return true;}}
                data C:A,B {var result=this.f(3);}
                func main(){var c=C();external result=c.result;}
            """,
            """
                data A {func f(x:int,y:int=0):int{return 1;}}
                data B {func f(x:int):bool{return true;}}
                data C:A,B {var result=this.f(3);}
                func main(){var c=C();external result=c.result;}
            """
        )
        for (source in programs) {
            val main = compile(source)
            assertEquals(0, Project.errorCount, source)
            assertEquals(MCFPPBaseType.Bool.typeId, template("C").scope.getVar("result")!!.type.typeId)
            assertEquals(1, execute(main).read(main.scope.getVar("result") as ScoreBool))
        }
    }

    @Test fun boundGenericOwnersUseCanonicalIdentityEvenWhenDisplayNamesCoincide() {
        compile(
            """
            data Base<T>{func f():int{return 1;}}
            data Specific<T>:Base<(T)>{override func f():int{return 2;}}
            data IntSpecific:Specific<int>{}
            data BoolBase:Base<bool>{}
            func main(){}
        """
        )
        assertEquals(0, Project.errorCount)
        val specific = template("IntSpecific").parent.single()
        val intBase = specific.parent.single()
        val boolBase = template("BoolBase").parent.single()
        for (owner in listOf(specific, intBase, boolBase)) owner.identifier = "SameDisplayName"
        assertEquals(specific.identifier, intBase.identifier)
        assertFalse(specific.inheritanceIdentity == intBase.inheritanceIdentity)
        assertFalse(intBase.inheritanceIdentity == boolBase.inheritanceIdentity)
        assertSame(specific, specific.scope.getFunctionCandidates("f").single().owner)
        val combined = DataTemplate("Combined", "default.test")
        combined.extends(intBase).extends(boolBase)
        assertEquals(2, combined.scope.getFunctionCandidates("f").size, "Base<int> and Base<bool> are distinct origins")
        assertIs<ParameterMatcher.TypeSelection.Ambiguous>(combined.scope.getFunctionByTypes("f", emptyList()))
        assertEquals(0, Project.errorCount)
    }

    @Test fun malformedScopeCyclesTerminateAndGenericParameterRenamingKeepsOneSignature() {
        compile("func main(){}")
        val a = DataTemplate("CycleA", "default.test")
        val b = DataTemplate("CycleB", "default.test")
        val method = Function("f", a, null)
        a.scope.addFunction(method, false)
        a.scope.parent.add(b.scope)
        b.scope.parent.add(a.scope)
        assertSame(method, b.scope.getFunctionCandidates("f").single())
        assertTrue(b.scope.hasFunction(method, true))
        assertTrue(b.scope.getFunctionCandidates("missing").isEmpty())
        assertFalse(b.scope.hasFunction(Function("missing", b, null), true))

        fun generic(name: String): NativeFunction = NativeFunction("identity").apply {
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, name, this, isReadOnly = true))
            normalParams.add(FunctionParam(MCFPPListType(MCFPPGenericParamType(name, arrayListOf())), "values", this))
        }
        assertTrue(ParameterMatcher.sameSignature(generic("T"), generic("U")))
        assertEquals(0, Project.errorCount)
    }

    private fun assertAmbiguousOrigins(message: String, vararg owners: String) {
        assertTrue("Ambiguous overload 'f'" in message, message)
        for (owner in owners) assertTrue("default.test:$owner" in message, message)
    }

    private fun infer(owner: DataTemplate, expression: String): DeclarationBinding.Bound {
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(expression))))
        return DeclarationBinding(Function("inference", owner, null), emptyMap()).expression(parser.expression())
    }

    private fun template(name: String): DataTemplate = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue(name)

    private fun compile(source: String): Function {
        val previousConfig = Project.config
        val previousIgnore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
            return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        } finally {
            Project.config = previousConfig
            CompileSettings.ignoreStdLib = previousIgnore
        }
    }

    private fun execute(main: Function): ScoreCommandExecutor {
        val functions = linkedMapOf<String, List<String>>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        fun collect(function: Function) {
            if (!visited.add(function)) return
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
}
