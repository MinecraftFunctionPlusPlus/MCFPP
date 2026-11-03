package top.mcfpp.test

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.InheritanceContractValidator
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceContractTest {
    @Test fun overrideMarkersRequireAVisibleParentWithTheSameParameterSignature() {
        Project.init()
        val base = model("A")
        method(base)
        val child = model("C", base)
        val local = method(child)
        assertInvalid(child, "requires override", "fixture.contract:A", "fixture.contract:C")
        local.isOverride = true
        assertValid(child)
        val orphan = model("Orphan")
        method(orphan).isOverride = true
        assertInvalid(orphan, "overrides nothing", "fixture.contract:Orphan")
        val overload = model("Overload", base)
        val different = method(overload, parameters = listOf(MCFPPBaseType.Int))
        assertValid(overload)
        different.isOverride = true
        assertInvalid(overload, "overrides nothing")
    }

    @Test fun finalReturnAndAccessAreCheckedAgainstAllParents() {
        for (parents in listOf(false, true)) {
            Project.init()
            val a = model("A")
            val b = model("B")
            val first = method(a, result = MCFPPBaseType.Object).apply { isFinal = true }
            method(b)
            val c = if (parents) model("C", b, a) else model("C", a, b)
            val local = method(c, result = MCFPPBaseType.Object).apply {
                isOverride = true
                accessModifier = AccessModifier.PROTECTED
            }
            assertInvalid(c, "final", "return type", "access", "fixture.contract:A", "fixture.contract:B")
            first.isFinal = false
            local.returnType = MCFPPBaseType.Int
            local.accessModifier = AccessModifier.PUBLIC
            assertValid(c)
        }
    }

    @Test fun covariantReturnsAndWiderAccessAreAcceptedAndVoidMustMatch() {
        Project.init()
        val animal = model("Animal")
        val dog = model("Dog", animal)
        val a = model("A")
        val base = method(a, result = animal.getType()).apply { accessModifier = AccessModifier.PROTECTED }
        val c = model("C", a)
        val local = method(c, result = dog.getType()).apply { isOverride = true }
        assertValid(c)
        for ((expected, actual) in listOf(
            MCFPPPrivateType.Void to MCFPPBaseType.Int,
            MCFPPBaseType.Object to MCFPPPrivateType.Void
        )) {
            base.returnType = expected
            local.returnType = actual
            assertInvalid(c, "return type")
        }
        base.returnType = MCFPPPrivateType.Void
        local.returnType = MCFPPPrivateType.Void
        assertValid(c)
        base.returnType = MCFPPBaseType.Float
        local.returnType = MCFPPBaseType.Int
        assertInvalid(c, "return type") // Numeric conversion is not return covariance.
    }

    @Test fun hiddenAncestorsRetainContractsAndInvalidInheritedImplementationsBlockConstruction() {
        Project.init()
        val a = model("A")
        method(a, result = MCFPPBaseType.Bool)
        val b = model("B", a)
        method(b).isOverride = true
        val c = model("C", b)
        val local = method(c).apply { isOverride = true }
        val result = InheritanceContractValidator.check(c)
        assertEquals(3, result.slots.single().declarations.size)
        assertSame(local, result.slots.single().effective.single().function)
        assertTrue(result.diagnostics.any { "fixture.contract:C" in it && "fixture.contract:A" in it && "return type" in it })
        val inheritedOnly = model("InheritedOnly", b)
        assertInvalid(inheritedOnly, "return type", "fixture.contract:B", "fixture.contract:A")
        inheritedOnly.addMember(DataTemplateConstructor(inheritedOnly, null))
        assertIs<ParameterMatcher.TypeSelection.Selected>(ParameterMatcher.selectConstructor(inheritedOnly.constructors, emptyList()))
        assertIs<ParameterMatcher.TypeSelection.Missing>(inheritedOnly.resolveConstructor(emptyList()))
    }

    @Test fun abstractObligationsAndUnrelatedImplementationsUseTheSameSlot() {
        Project.init()
        val a = model("A").apply { isAbstract = true }
        method(a).isAbstract = true
        val deferred = model("Deferred", a).apply { isAbstract = true }
        assertValid(deferred)
        val c = model("C", deferred)
        assertInvalid(c, "Abstract function 'f'", "not implemented", "fixture.contract:A")
        method(c).isOverride = true
        assertValid(c)
        val b = model("B")
        method(b)
        for (parents in listOf(listOf(a, b), listOf(b, a))) {
            val conflict = model("Conflict", *parents.toTypedArray())
            assertInvalid(conflict, "Ambiguous inherited function 'f'", "fixture.contract:A", "fixture.contract:B")
            method(conflict).isOverride = true
            assertValid(conflict)
        }
        val contract = model("Contract").apply { isAbstract = true }
        method(contract).isAbstract = true
        assertValid(contract)
        assertInvalid(model("Implementor", contract), "not implemented")
        val concreteWithAbstract = model("InvalidLocal")
        assertTrue(concreteWithAbstract.addMember(Function("f", concreteWithAbstract, null).apply { isAbstract = true }))
        assertInvalid(concreteWithAbstract, "not implemented")
    }

    @Test fun privateParentMethodsCreateNoOverrideOrAbstractObligation() {
        Project.init()
        val a = model("A").apply { isAbstract = true }
        method(a).apply {
            isAbstract = true
            accessModifier = AccessModifier.PRIVATE
            isFinal = true
        }
        val c = model("C", a)
        assertValid(c)
        val local = method(c).apply { accessModifier = AccessModifier.PRIVATE }
        assertValid(c)
        local.isOverride = true
        assertInvalid(c, "overrides nothing")
        val b = model("B")
        method(b)
        assertValid(model("Combined", a, b))
    }

    @Test fun privateInvalidOverridesStillBlockDescendantConstructionWithoutBeingInherited() {
        Project.init()
        val a = model("A")
        val original = method(a)
        val b = model("B", a)
        method(b).apply {
            accessModifier = AccessModifier.PRIVATE
            isOverride = true
        }
        assertInvalid(b, "access", "fixture.contract:B", "fixture.contract:A")
        val c = model("C", b)
        val result = assertInvalid(c, "access", "fixture.contract:B", "fixture.contract:A")
        val slot = result.slots.single()
        assertEquals(1, slot.declarations.size)
        assertSame(original, slot.effective.single().function)

        val constructor = DataTemplateConstructor(c, null)
        c.constructors.add(constructor)
        assertIs<ParameterMatcher.TypeSelection.Selected>(ParameterMatcher.selectConstructor(c.constructors, emptyList()))
        val receiver = c.getType().buildUnConcrete("receiver")
        val entry = Function("private_override_attempt", "fixture.contract", null)
        entry.runInFunction {
            val messages = diagnostics {
                assertIs<ParameterMatcher.TypeSelection.Missing>(c.resolveConstructor(emptyList()))
                assertIs<UnknownVar>(constructor.invoke(emptyList(), receiver))
            }.joinToString("\n")
            assertTrue("access" in messages && "fixture.contract:B" in messages, messages)
        }
        assertTrue(receiver.isError)
        assertTrue(entry.commands.analyzeAll().isEmpty())
        assertFalse(constructor.bodyCompiled)
    }

    @Test fun readonlyTypeRenamingPreservesParameterAndReturnContracts() {
        Project.init()
        val a = model("A")
        val c = model("C", a)
        fun generic(owner: DataTemplate, name: String) = NativeFunction("identity").apply {
            this.owner = owner
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, name, this, isReadOnly = true))
            val type = MCFPPListType(MCFPPGenericParamType(name, arrayListOf()))
            normalParams.add(FunctionParam(type, "values", this))
            returnType = type
            owner.scope.addFunction(this, false)
        }
        generic(a, "T")
        val local = generic(c, "U").apply { isOverride = true }
        assertValid(c)
        local.returnType = MCFPPBaseType.Int
        assertInvalid(c, "return type")
    }

    @Test fun canonicalGenericOwnersRemainDistinctAndMalformedGraphsTerminate() {
        compile(
            """
            data Base<T>{func f():int{return 1;}}
            data C:Base<int>,Base<bool>{}
            func main(){}
        """
        )
        val child = template("C")
        child.parent.forEach { it.identifier = "SameDisplayName" }
        val result = InheritanceContractValidator.check(child)
        assertFalse(result.isValid)
        val methodSlot = result.slots.single { it.declarations.first().function.identifier == "f" }
        assertEquals(2, methodSlot.declarations.size)
        child.parent.forEach { assertTrue(it.inheritanceIdentity.toString() in result.diagnostics.joinToString()) }
        Project.init()
        val a = model("CycleA")
        val b = model("CycleB")
        method(a)
        method(b)
        a.parent.add(b)
        b.parent.add(a)
        a.scope.parent.add(b.scope)
        b.scope.parent.add(a.scope)
        assertEquals(2, InheritanceContractValidator.check(a).slots.single().declarations.size)
    }

    @Test fun compatibleOverrideExecutesThroughAnAbstractAncestor() {
        val main = compile(
            """
            abstract data A{abstract func f():int;}
            data C:A{override func f():int{return 2;}}
            func main(){var child=C();external result=child.f();}
        """
        )
        assertEquals(0, Project.errorCount)
        assertTrue(template("C").validateInheritance())
        assertEquals(2, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(0, Project.errorCount)
    }

    @Test fun sourceConstructionRejectsAbstractAndInvalidConcreteTypesBeforeInitializingAReceiver() {
        for ((declarations, target, expected) in listOf(
            // Abstract declarations have no constructor candidate: inference rejects the call
            // before the runtime construction guard. Model tests exercise that guard directly.
            Triple("abstract data A{abstract func f():int;}", "A", "Cannot bind constructor 'A' to its declared argument types"),
            Triple("abstract data A{abstract func f():int;}\ndata C:A{}", "C", "not implemented"),
            Triple("data A{func f():int{return 1;}}\ndata C:A{func f():int{return 2;}}", "C", "requires override")
        )) {
            lateinit var main: Function
            val messages = diagnostics {
                main = compile("$declarations\nfunc main(){var child=$target();}")
            }.joinToString("\n")
            assertTrue(Project.errorCount > 0)
            assertTrue(expected in messages, messages)
            assertTrue(main.scope.getVar("child")?.isError != false)
            assertTrue(main.commands.analyzeAll().none { "function default.test:${target.lowercase()}/_init_" in it })
            assertEquals(0, execute(main).stackDepth)
        }
    }

    @Test fun directConstructorInvocationCannotBypassValidationOrPublishItsReceiver() {
        for (kind in listOf("abstract", "missing", "override")) {
            Project.init()
            val a = model("A").apply { isAbstract = true }
            method(a).isAbstract = true
            val c = when (kind) {
                "abstract" -> a
                "missing" -> model("C", a)
                else -> model("C", a).apply { method(this) }
            }
            val constructor = DataTemplateConstructor(c, null)
            // A restored model may carry a constructor even when its declaration is invalid.
            c.constructors.add(constructor)
            assertIs<ParameterMatcher.TypeSelection.Selected>(ParameterMatcher.selectConstructor(c.constructors, emptyList()))
            val receiver = c.getType().buildUnConcrete("receiver")
            val entry = Function("attempt", "fixture.contract", null)
            entry.runInFunction {
                assertIs<UnknownVar>(constructor.invoke(emptyList(), receiver))
            }
            assertTrue(receiver.isError)
            assertTrue(entry.commands.analyzeAll().isEmpty())
            assertIs<ParameterMatcher.TypeSelection.Missing>(c.resolveConstructor(emptyList()))
            assertTrue(Project.errorCount > 0)
        }
    }

    @Test fun pureQueriesAndRepeatedValidationDoNotEmitCommandsOrDuplicateDiagnostics() {
        Project.init()
        val a = model("A")
        method(a)
        val c = model("C", a)
        method(c)
        val before = Function.currFunction.commands.analyzeAll().toList()
        val errors = Project.errorCount
        assertFalse(InheritanceContractValidator.check(c).isValid)
        assertEquals(errors, Project.errorCount)
        assertFalse(c.validateInheritance())
        val diagnosed = Project.errorCount
        assertTrue(diagnosed > errors)
        assertFalse(c.validateInheritance())
        assertEquals(diagnosed, Project.errorCount)
        assertEquals(before, Function.currFunction.commands.analyzeAll())
    }

    private fun model(name: String, vararg parents: DataTemplate) = DataTemplate(name, "fixture.contract").apply {
        parents.forEach { extends(it) }
    }

    private fun method(
        owner: DataTemplate,
        result: MCFPPType = MCFPPBaseType.Int,
        parameters: List<MCFPPType> = emptyList()
    ): Function = Function("f", owner, null).apply {
        returnType = result
        parameters.forEachIndexed { index, type -> normalParams.add(FunctionParam(type, "p$index", this)) }
        assertTrue(owner.scope.addFunction(this, false))
    }

    private fun assertInvalid(template: DataTemplate, vararg fragments: String): InheritanceContractValidator.Result {
        val result = InheritanceContractValidator.check(template)
        assertFalse(result.isValid)
        val message = result.diagnostics.joinToString("\n")
        fragments.forEach { assertTrue(it in message, message) }
        return result
    }

    private fun assertValid(template: DataTemplate) {
        val result = InheritanceContractValidator.check(template)
        assertTrue(result.isValid, result.diagnostics.joinToString("\n"))
    }

    private fun template(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue(name)

    private fun compile(source: String): Function {
        val config = Project.config
        val settings = listOf(
            CompileSettings.isDebug,
            CompileSettings.ignoreStdLib,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
            return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        } finally {
            Project.config = config
            CompileSettings.isDebug = settings[0]
            CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
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

    private fun diagnostics(action: () -> Unit): List<String> {
        val logger = assertIs<Logger>(LogProcessor.logger)
        val messages = arrayListOf<String>()
        val appender = object : AbstractAppender("inheritance-contract", null, null, false, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) {
                messages.add(event.message.formattedMessage)
            }
        }
        appender.start()
        logger.addAppender(appender)
        try {
            action()
        } finally {
            logger.removeAppender(appender)
            appender.stop()
        }
        return messages
    }
}
