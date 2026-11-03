package top.mcfpp.test

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceCompletionTest {
    @Test fun unusedCyclesAreCheckedForEveryDeclarationKindAndBothOrders() {
        val cycles = listOf(
            listOf("data A:A {}"),
            listOf("data A:B {}", "data B:C {}", "data C:A {}"),
            listOf("object data A:B {}", "object data B:A {}"),
            listOf("abstract data A:B {}", "abstract data B:A {}"),
            listOf("data A<T>:B<(T)> {}", "data B<T>:A<(T)> {}"),
            listOf("abstract data A<T>:B<(T)> {}", "abstract data B<T>:A<(T)> {}")
        )
        for (cycle in cycles) for (order in listOf(cycle, cycle.reversed())) {
            compileDeclarations(order.joinToString("\n")) { messages ->
                assertTrue(Project.errorCount > 0, order.toString())
                assertTrue(messages.any { "Cyclic inheritance:" in it && "fixture:A" in it }, messages.joinToString("\n"))
            }
        }
    }

    @Test fun changingGenericArgumentsCannotHideAnInheritanceCycle() {
        compileDeclarations(
            """
            data Loop<N:int>:Loop<(N+1)> {}
            data Used:Loop<0> {}
            """.trimIndent()
        ) { messages ->
            assertTrue(messages.any { "Cyclic inheritance: fixture:Loop -> fixture:Loop" in it })
            val loop = assertIs<GenericDataTemplate>(template("Loop"))
            assertTrue(loop.compiledTemplates.isEmpty())
        }
    }

    @Test fun parentFieldsFinishBeforeChildInferenceInEitherDeclarationOrder() {
        val parent = "data Parent {var value=1;}"
        val child = "data Child:Parent {var copied=this.value;}"
        for (order in listOf(listOf(parent, child), listOf(child, parent))) {
            compileDeclarations(order.joinToString("\n")) {
                assertEquals(0, Project.errorCount)
                assertSame(template("Parent"), template("Child").parent.single())
                assertEquals(MCFPPBaseType.Int, assertNotNull(template("Child").scope.getVar("copied")).type)
            }
        }
    }

    @Test fun genericAndObjectParentsUseTheSameCompletionOrder() {
        for (reverse in listOf(false, true)) {
            val declarations = listOf(
                "data Parent<T>{var value:T;}",
                "data Child<T>:Parent<(T)>{var copied=this.value;}",
                "object data State:Child<int>{}",
                "data Used:Child<int>{}"
            ).let { if (reverse) it.reversed() else it }
            compileDeclarations(declarations.joinToString("\n")) {
                assertEquals(0, Project.errorCount)
                val used = template("Used")
                val child = assertIs<DataTemplate>(used.parent.single())
                assertEquals(MCFPPBaseType.Int, assertNotNull(child.scope.getVar("copied")).type)
                assertEquals(MCFPPBaseType.Int, assertNotNull(child.scope.getVar("value")).type)
                val state = assertIs<DataTemplate>(GlobalScope.localNamespaces.getValue("fixture").scope.getObject("State"))
                assertSame(child, state.parent.single())
            }
        }
    }

    @Test fun anonymousGenericParentCompletesBeforeItsOwnInferredFields() {
        compileDeclarations(
            """
            func consume(value:data:Parent<int>{var copied=this.value;}) {}
            data Parent<T>{var value:T;}
            """.trimIndent()
        ) {
            assertEquals(0, Project.errorCount)
            val function = GlobalScope.localNamespaces.getValue("fixture").scope.functions.getValue("consume").single()
            val anonymous = assertIs<MCFPPDataTemplateType>(function.normalParams.single().type).template
            assertEquals(MCFPPBaseType.Int, assertNotNull(anonymous.scope.getVar("copied")).type)
            assertEquals(MCFPPBaseType.Int, assertNotNull(anonymous.scope.getVar("value")).type)
        }
    }

    @Test fun importsAndForwardParentsResolveAcrossFilesInBothOrders() {
        val child = "namespace fixture.child\nimport fixture.parent:Parent;\ndata Child:Parent<int>{var copied=this.value;}"
        val parent = "namespace fixture.parent\ndata Parent<T>{var value:T;}"
        for (files in listOf(listOf(child, parent), listOf(parent, child))) {
            compileDeclarations(*files.toTypedArray(), addNamespace = false) { messages ->
                assertEquals(0, Project.errorCount, messages.joinToString("\n"))
                val resolved = template("Child", "fixture.child")
                assertEquals("fixture.parent", resolved.parent.single().namespace)
                assertEquals(MCFPPBaseType.Int, assertNotNull(resolved.scope.getVar("copied")).type)
            }
        }
    }

    @Test fun completionAlsoDiagnosesMalformedRestoredParentGraphsWithoutRecursing() {
        compileDeclarations("data A{}\ndata B{}") { messages ->
            val a = template("A")
            val b = template("B")
            a.parent.add(b)
            b.parent.add(a)
            Project.completeTemplateDeclarations()
            assertTrue(messages.any { "Cyclic inheritance:" in it })
            assertTrue(a.parent.none { it === b } || b.parent.none { it === a })
        }
    }

    @Test fun unusedCyclesFollowImportsWhoseFilesHaveNotVisitedFieldsYet() {
        val a = "namespace fixture.a\nimport fixture.b:B;\ndata A:B{}"
        val b = "namespace fixture.b\nimport fixture.a:A;\ndata B:A{}"
        for (files in listOf(listOf(a, b), listOf(b, a))) {
            compileDeclarations(*files.toTypedArray(), addNamespace = false) { messages ->
                assertTrue(messages.any { "Cyclic inheritance:" in it && "fixture.a:A" in it && "fixture.b:B" in it }, messages.joinToString("\n"))
                assertTrue(messages.none { "Already have import" in it }, messages.joinToString("\n"))
            }
        }
    }

    private fun template(name: String, namespace: String = "fixture"): DataTemplate = assertNotNull(GlobalScope.localNamespaces.getValue(namespace).scope.getTemplate(name))

    private fun compileDeclarations(vararg sources: String, addNamespace: Boolean = true, check: (List<String>) -> Unit) {
        val root = Files.createTempDirectory("mcfpp-inheritance-completion-")
        val previousConfig = Project.config
        val previousIgnore = CompileSettings.ignoreStdLib
        val logger = assertIs<Logger>(LogProcessor.logger)
        val messages = arrayListOf<String>()
        val appender = object : AbstractAppender("inheritance-completion", null, null, false, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) {
                messages.add(event.message.formattedMessage)
            }
        }
        appender.start()
        logger.addAppender(appender)
        try {
            sources.forEachIndexed { index, source ->
                Files.writeString(root.resolve("%02d.mcfpp".format(index)), if (addNamespace) "namespace fixture\n$source" else source)
            }
            CompileSettings.ignoreStdLib = true
            Project.config = ProjectConfig(version = "26.3", sourcePath = root, rootNamespace = "fixture")
            Project.init()
            Project.readProject()
            Project.indexType()
            Project.resolveField()
            Project.runAnnotation()
            Project.completeTemplateDeclarations()
            check(messages)
        } finally {
            logger.removeAppender(appender)
            appender.stop()
            Project.config = previousConfig
            CompileSettings.ignoreStdLib = previousIgnore
            root.toFile().deleteRecursively()
        }
    }
}
