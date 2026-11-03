package top.mcfpp.test

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.ConstructorPlan
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceConstructorPlanTest {
    @Test fun sharedAncestorsHaveOneStableParentFirstStage() {
        for (reverse in listOf(false, true)) {
            Project.init()
            val a = model("A")
            val left = model("Left", a)
            val right = model("Right", a)
            val child = if (reverse) model("Child", right, left) else model("Child", left, right)
            val constructor = constructor(child, MCFPPBaseType.Int)
            val result = constructor.initializationPlan()
            assertTrue(result.isValid, result.diagnostics.joinToString())
            val stages = assertNotNull(result.plan).stages(constructor)
            assertEquals(
                if (reverse) listOf("A", "Right", "Left", "Child") else
                    listOf("A", "Left", "Right", "Child"),
                stages.map { it.owner.identifier }
            )
            assertSame(constructor, stages.last().constructor)
            assertTrue(stages.dropLast(1).all { it.constructor == null })
            // The actual child arguments are selected at the call, never guessed for parents.
            assertIs<ParameterMatcher.TypeSelection.Selected>(child.resolveConstructor(listOf(MCInt(4))))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun anExactEmptyConstructorBeatsDefaultsUsingTheCommonMatcher() {
        Project.init()
        val parent = model("Parent")
        constructor(parent, MCFPPBaseType.Int, default = true)
        val empty = constructor(parent)
        val child = model("Child", parent)
        val selected = assertNotNull(ConstructorPlan.build(child).plan).ancestors.single()
        assertSame(empty, selected.constructor)
        parent.constructors.reverse()
        assertSame(empty, assertNotNull(ConstructorPlan.build(child).plan).ancestors.single().constructor)
        assertEquals(0, Project.errorCount)
    }

    @Test fun allDefaultParametersAreSelectedWithoutBindingValuesOrExecutingBodies() {
        Project.init()
        val parent = model("Parent")
        val selected = constructor(parent, MCFPPBaseType.Int, default = true)
        selected.normalParams.add(
            FunctionParam(MCFPPBaseType.Bool, "flag", selected, hasDefault = true).apply {
                defaultVar = ScoreBool(true)
            }
        )
        val snapshot = selected.normalParams.first().defaultValue
        val entry = Function("entry", "fixture.plan", null)
        entry.commands.add("say sentinel")
        val child = model("Child", parent)
        entry.runInFunction {
            val stage = assertNotNull(ConstructorPlan.build(child).plan).ancestors.single()
            assertSame(selected, stage.constructor)
        }
        assertSame(snapshot, selected.normalParams.first().defaultValue)
        assertFalse(selected.bodyCompiled)
        assertEquals(listOf("say sentinel"), entry.commands.analyzeAll())
        assertEquals(0, Project.errorCount)
    }

    @Test fun accessIsFilteredBeforeRankingAndProtectedConstructorsRemainUsable() {
        Project.init()
        val parent = model("Parent")
        constructor(parent).accessModifier = AccessModifier.PRIVATE
        val protected = constructor(parent, MCFPPBaseType.Int, default = true).apply {
            accessModifier = AccessModifier.PROTECTED
        }
        val child = model("Child", parent)
        assertSame(protected, assertNotNull(ConstructorPlan.build(child).plan).ancestors.single().constructor)
        protected.accessModifier = AccessModifier.PRIVATE
        val rejected = ConstructorPlan.build(child)
        assertFalse(rejected.isValid)
        assertTrue(rejected.diagnostics.single().contains("No accessible parent constructor"))
        assertTrue(rejected.diagnostics.single().contains("fixture.plan:Parent"))
        assertTrue(rejected.diagnostics.single().contains("fixture.plan:Child"))
        assertEquals(0, Project.errorCount, "Building a plan is pure; the declaration/call barrier reports it")
    }

    @Test fun equalDefaultOverloadsRemainAmbiguousInEitherDeclarationOrder() {
        Project.init()
        val parent = model("Parent")
        val first = constructor(parent, MCFPPBaseType.Int, default = true)
        val second = constructor(parent, MCFPPBaseType.Bool, default = true)
        val child = model("Child", parent)
        repeat(2) {
            val result = ConstructorPlan.build(child)
            assertFalse(result.isValid)
            assertNull(result.plan)
            val message = result.diagnostics.single()
            assertTrue(
                "Ambiguous parent constructor" in message &&
                    "fixture.plan:Parent" in message &&
                    "fixture.plan:Child" in message,
                message
            )
            assertTrue(first.identifier in message && second.identifier in message, message)
            parent.constructors.reverse()
        }
    }

    @Test fun abstractAncestorsHaveImplicitStagesWithoutBeingInstantiated() {
        Project.init()
        val ancestor = model("Abstract").apply { isAbstract = true }
        val middle = model("Middle", ancestor).apply { isAbstract = true }
        val child = model("Child", middle)
        val constructor = constructor(child)
        val plan = assertNotNull(constructor.initializationPlan().plan)
        assertEquals(listOf(ancestor, middle), plan.ancestors.map { it.owner })
        assertTrue(plan.ancestors.all { it.constructor == null })
        assertTrue(child.validateInheritance())
        assertIs<ParameterMatcher.TypeSelection.Selected>(child.resolveConstructor(emptyList()))
        assertEquals(0, Project.errorCount)
    }

    @Test fun requiredParentParametersRejectUnusedDeclarationsInBothSourceOrders() {
        val sources = listOf("data Parent{constructor(required:int){}}", "data Child:Parent{}")
        for (order in listOf(sources, sources.reversed())) {
            declarations(order.joinToString("\n")) { messages ->
                assertTrue(
                    messages.any {
                        "requires explicit parent constructor binding" in it &&
                            "fixture.plan:Parent" in it &&
                            "fixture.plan:Child" in it &&
                            "required: int" in it
                    },
                    messages.joinToString("\n")
                )
                assertTrue(Project.errorCount > 0)
                assertFalse(template("Child").constructors.single().bodyCompiled)
            }
        }
    }

    @Test fun sourceDefaultsAndAccessUseTheCompleteDeclarationBarrier() {
        for (parent in listOf(
            "data Parent{constructor(value:int=3){}}",
            "data Parent{protected constructor(){}}"
        )) {
            declarations("data Child:Parent{}\n$parent") { messages ->
                assertEquals(0, Project.errorCount, messages.joinToString("\n"))
                val stage = assertNotNull(ConstructorPlan.build(template("Child")).plan).ancestors.single {
                    it.owner === template("Parent")
                }
                assertSame(template("Parent").constructors.single(), stage.constructor)
            }
        }
        for ((parent, expected) in listOf(
            "data Parent{private constructor(){}}" to "No accessible parent constructor",
            "data Parent{constructor(value:int=3){}\nconstructor(value:bool=true){}}" to "Ambiguous parent constructor"
        )) {
            declarations("data Child:Parent{}\n$parent") { messages ->
                assertTrue(Project.errorCount > 0)
                assertTrue(messages.any { expected in it && "fixture.plan:Parent" in it }, messages.joinToString("\n"))
            }
        }
    }

    @Test fun rejectedPlansBlockBothCallEntrypointsBeforeReceiverInitialization() {
        for (kind in listOf("required", "private", "ambiguous")) {
            Project.init()
            val parent = model("Parent")
            when (kind) {
                "required" -> constructor(parent, MCFPPBaseType.Int)
                "private" -> constructor(parent).accessModifier = AccessModifier.PRIVATE
                else -> {
                    constructor(parent, MCFPPBaseType.Int, default = true)
                    constructor(parent, MCFPPBaseType.Bool, default = true)
                }
            }
            val child = model("Child", parent)
            val constructor = constructor(child)
            val receiver = child.getType().buildUnConcrete("receiver")
            val entry = Function("attempt", "fixture.plan", null)
            entry.commands.add("say sentinel")
            diagnostics {
                entry.runInFunction {
                    assertIs<ParameterMatcher.TypeSelection.Missing>(child.resolveConstructor(emptyList()))
                    assertIs<UnknownVar>(constructor.invoke(linkedMapOf(), receiver))
                }
            }
            assertTrue(receiver.isError)
            assertNull(receiver.storageBinding)
            assertFalse(constructor.bodyCompiled)
            assertEquals(listOf("say sentinel"), entry.commands.analyzeAll())
            assertTrue(Project.errorCount > 0)
        }
    }

    @Test fun canonicalGenericBindingsDeduplicateDiamondsWithoutMergingDifferentArguments() {
        declarations(
            """
            data Base<T>{}
            data Left:Base<int>{}
            data Right:Base<int>{}
            data Shared:Left,Right{}
            data Distinct:Base<int>,Base<bool>{}
        """
        ) { messages ->
            assertEquals(0, Project.errorCount, messages.joinToString("\n"))
            val shared = assertNotNull(ConstructorPlan.build(template("Shared")).plan)
            assertEquals(3, shared.ancestors.count { it.owner.namespace == "fixture.plan" })
            assertEquals(1, shared.ancestors.count { it.owner === DataTemplate.baseDataTemplate })
            assertEquals(1, shared.ancestors.count { it.owner.inheritanceIdentity == template("Left").parent.single().inheritanceIdentity })
            val distinct = template("Distinct")
            distinct.parent.forEach { it.identifier = "SameDisplayName" }
            val stages = assertNotNull(ConstructorPlan.build(distinct).plan).ancestors.filter {
                it.owner.namespace == "fixture.plan"
            }
            assertEquals(2, stages.size)
            assertEquals(2, stages.map { it.owner.inheritanceIdentity }.distinct().size)
        }
    }

    @Test fun malformedCyclesTerminateAndPlansRecomputeAfterModelChanges() {
        Project.init()
        val a = model("A")
        val b = model("B", a)
        a.parent.add(b)
        val bad = ConstructorPlan.build(a)
        assertFalse(bad.isValid)
        assertTrue(bad.diagnostics.single().contains("inheritance cycle"))
        assertTrue(bad.diagnostics.single().contains("fixture.plan:A"))
        assertTrue(bad.diagnostics.single().contains("fixture.plan:B"))
        a.parent.clear()
        val required = constructor(a, MCFPPBaseType.Int)
        assertFalse(ConstructorPlan.build(b).isValid)
        required.normalParams.single().hasDefault = true
        assertTrue(ConstructorPlan.build(b).isValid)
        assertEquals(0, Project.errorCount)
    }

    private fun model(name: String, vararg parents: DataTemplate) = DataTemplate(name, "fixture.plan").apply {
        parents.forEach { extends(it) }
    }

    private fun constructor(owner: DataTemplate, type: top.mcfpp.type.MCFPPType? = null, default: Boolean = false) = DataTemplateConstructor(owner, null).also { constructor ->
        if (type != null) constructor.normalParams.add(
            FunctionParam(type, "value", constructor, hasDefault = default).apply {
                if (type == MCFPPBaseType.Int && default) defaultVar = MCInt(3)
                if (type == MCFPPBaseType.Bool && default) defaultVar = ScoreBool(true)
            }
        )
        owner.constructors.add(constructor)
    }

    private fun template(name: String): DataTemplate = assertNotNull(GlobalScope.localNamespaces.getValue("fixture.plan").scope.getTemplate(name))

    private fun diagnostics(action: () -> Unit): List<String> {
        val messages = arrayListOf<String>()
        val logger = assertIs<Logger>(LogProcessor.logger)
        val appender = object : AbstractAppender("constructor-plan", null, null, false, Property.EMPTY_ARRAY) {
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

    private fun declarations(source: String, check: (List<String>) -> Unit) {
        val root = Files.createTempDirectory("mcfpp-constructor-plan-")
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        try {
            Files.writeString(root.resolve("test.mcfpp"), "namespace fixture.plan\n${source.trimIndent()}")
            CompileSettings.ignoreStdLib = true
            Project.config = ProjectConfig(version = "26.3", sourcePath = root, rootNamespace = "fixture")
            Project.init()
            val messages = diagnostics {
                Project.readProject()
                Project.indexType()
                Project.resolveField()
                Project.runAnnotation()
                assertEquals(0, Project.errorCount, "Parent selection must wait for all declaration signatures")
                Project.completeTemplateDeclarations()
            }
            check(messages)
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignore
            root.toFile().deleteRecursively()
        }
    }
}
