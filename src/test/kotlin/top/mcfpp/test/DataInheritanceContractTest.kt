package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.InheritanceContractValidator
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceContractTest {
    @Test fun sameSignatureReceiverFunctionsRemainIndependentDeclarations() {
        Project.init()
        val parent = model("Parent")
        val original = method(parent)
        val child = model("Child", parent)
        val local = method(child)
        val result = InheritanceContractValidator.check(child)
        assertTrue(result.isValid)
        assertEquals(listOf(local), result.slots.flatMap { it.declarations }.map { it.function })
        assertEquals(setOf(original, local), child.scope.getFunctionCandidates("f").toSet())
        assertEquals(listOf(local), child.scope.functions.getValue("f"))
        local.isOverride = true
        assertInvalid(child, "cannot override", "fixture.contract:Child")
    }

    @Test fun parentFinalReturnAndVisibilityDoNotConstrainADifferentReceiverFunction() {
        for (reverse in listOf(false, true)) {
            Project.init()
            val first = model("First")
            method(first, MCFPPBaseType.Object).apply { isFinal = true }
            val second = model("Second")
            method(second, MCFPPBaseType.Bool)
            val child = model("Child", *if (reverse) arrayOf(second, first) else arrayOf(first, second))
            val local = method(child, MCFPPBaseType.Int).apply { accessModifier = AccessModifier.PROTECTED }
            assertValid(child)
            assertSame(local, InheritanceContractValidator.check(child).slots.single().effective.single().function)
        }
    }

    @Test fun abstractParentFunctionsDoNotCreateChildImplementationObligations() {
        Project.init()
        val contract = model("Contract").apply { isAbstract = true }
        val abstractFunction = method(contract).apply { isAbstract = true }
        val middle = model("Middle", contract)
        val child = model("Child", middle)
        assertValid(middle)
        assertValid(child)
        assertFalse(child.scope.functions.containsKey("f"))
        assertSame(abstractFunction, child.scope.getFunctionCandidates("f").single())
        val invalidLocal = model("InvalidLocal")
        method(invalidLocal).isAbstract = true
        assertInvalid(invalidLocal, "Abstract function 'f'", "fixture.contract:InvalidLocal")
    }

    @Test fun privateParentFunctionsKeepLexicalVisibilityWithoutAnOverrideRelationship() {
        Project.init()
        val parent = model("Parent")
        val original = method(parent).apply { accessModifier = AccessModifier.PRIVATE }
        val child = model("Child", parent)
        assertTrue(child.scope.getFunctionCandidates("f").isEmpty())
        val lexicalCaller = Function("caller", parent, null)
        assertSame(original, child.scope.getFunctionCandidates("f", lexicalCaller).single())
        val local = method(child)
        assertValid(child)
        assertSame(local, child.scope.getFunctionCandidates("f").single())
    }

    @Test fun parentOverrideErrorsStayAtTheParentDeclarationSite() {
        Project.init()
        val parent = model("Parent")
        method(parent).isOverride = true
        assertInvalid(parent, "cannot override")
        val child = model("Child", parent)
        assertValid(child)
        assertTrue(InheritanceContractValidator.check(child).slots.isEmpty())
    }

    @Test fun generatedChildReceiverFunctionConflictsOnlyWithLocalDeclarations() {
        Project.init()
        val parent = model("Parent")
        val original = method(parent)
        val child = model("Child", parent)
        val generated = Function("f", child, null)
        assertEquals(null, child.generatedMethodSlotConflict(generated))
        assertTrue(child.publishGeneratedMethod(generated))
        assertSame(generated, child.generatedMethodSlotConflict(Function("f", child, null)))
        assertEquals(setOf(original, generated), child.scope.getFunctionCandidates("f").toSet())
        assertEquals(listOf(generated), child.scope.functions.getValue("f"))
    }

    @Test fun readonlyTypeRenamingKeepsTheOrdinaryParameterSignature() {
        Project.init()
        fun generic(owner: DataTemplate, name: String) = NativeFunction("identity").apply {
            this.owner = owner
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, name, this, isReadOnly = true))
            val type = MCFPPListType(MCFPPGenericParamType(name, arrayListOf()))
            normalParams.add(FunctionParam(type, "values", this))
            returnType = type
            owner.scope.addFunction(this, false)
        }
        val parent = model("Parent")
        val original = generic(parent, "T")
        val child = model("Child", parent)
        val local = generic(child, "U")
        assertTrue(ParameterMatcher.sameSignature(original, local))
        local.returnType = MCFPPBaseType.Int
        assertValid(child)
        assertEquals(2, child.scope.getFunctionCandidates("identity").size)
    }

    @Test fun canonicalGenericOwnersAndMalformedScopeGraphsKeepSeparateDeclarations() {
        compile("data Base<T>{func f():int{return 1;}}\ndata Child:Base<int>,Base<bool>{}\nfunc main(){}")
        val child = template("Child")
        child.parent.forEach { it.identifier = "SameDisplayName" }
        assertValid(child)
        assertEquals(2, child.scope.getFunctionCandidates("f").size)
        assertFalse(child.scope.functions.containsKey("f"))
        Project.init()
        val first = model("First")
        val second = model("Second")
        method(first)
        method(second)
        first.parent.add(second); second.parent.add(first)
        first.scope.parent.add(second.scope); second.scope.parent.add(first.scope)
        assertEquals(1, InheritanceContractValidator.check(first).slots.size)
        assertEquals(2, first.scope.getFunctionCandidates("f").size)
    }

    @Test fun localAndParentReceiverFunctionsUseOrdinaryCallSelection() {
        val main = compile(
            """
            data Parent{func read():int{return 4;}func f():int{return 1;}}
            data Child:Parent{func f():int{return 2;}}
            func main(){var child=Child();external local=child.f();external parent=child.read();}
        """
        )
        assertEquals(0, Project.errorCount)
        assertTrue(template("Child").validateInheritance())
        assertFalse(template("Child").scope.functions.containsKey("read"))
        val machine = executePublishedFunctions(main)
        assertEquals(2, machine.read(assertIs<MCInt>(main.scope.getVar("local"))))
        assertEquals(4, machine.read(assertIs<MCInt>(main.scope.getVar("parent"))))
        assertEquals(0, machine.stackDepth)
    }

    @Test fun concreteChildOfAbstractReceiverContractCanConstructWithoutCopyingTheFunction() {
        val main = compile(
            """
            abstract data Parent{abstract func f():int;}
            data Child:Parent{}
            func main(){var child=Child();}
        """
        )
        assertEquals(0, Project.errorCount)
        assertFalse(template("Child").isAbstract)
        assertFalse(template("Child").scope.functions.containsKey("f"))
        assertFalse(main.scope.getVar("child")!!.isError)
        assertEquals(0, executePublishedFunctions(main).stackDepth)
    }

    @Test fun pureQueriesAndRepeatedValidationDoNotEmitCommandsOrDuplicateDiagnostics() {
        Project.init()
        val template = model("Invalid")
        method(template).isOverride = true
        val before = Function.currFunction.commands.analyzeAll().toList()
        val errors = Project.errorCount
        assertInvalid(template, "cannot override")
        assertEquals(errors, Project.errorCount)
        assertFalse(template.validateInheritance())
        val diagnosed = Project.errorCount
        assertTrue(diagnosed > errors)
        assertFalse(template.validateInheritance())
        assertEquals(diagnosed, Project.errorCount)
        assertEquals(before, Function.currFunction.commands.analyzeAll())
    }

    private fun model(name: String, vararg parents: DataTemplate) = DataTemplate(name, "fixture.contract").apply {
        parents.forEach { extends(it) }
    }

    private fun method(owner: DataTemplate, result: MCFPPType = MCFPPBaseType.Int): Function =
        Function("f", owner, null).apply { returnType = result; assertTrue(owner.scope.addFunction(this, false)) }

    private fun assertInvalid(template: DataTemplate, vararg fragments: String) {
        val result = InheritanceContractValidator.check(template)
        assertFalse(result.isValid)
        val message = result.diagnostics.joinToString("\n")
        fragments.forEach { assertTrue(it in message, message) }
    }

    private fun assertValid(template: DataTemplate) {
        val result = InheritanceContractValidator.check(template)
        assertTrue(result.isValid, result.diagnostics.joinToString("\n"))
    }

    private fun template(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue(name)

    private fun compile(source: String): Function {
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
            return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        } finally { Project.config = config; CompileSettings.ignoreStdLib = ignore }
    }
}
