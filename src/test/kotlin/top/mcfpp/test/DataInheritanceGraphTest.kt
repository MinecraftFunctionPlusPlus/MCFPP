package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.util.LogProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceGraphTest {
    @Test fun rejectedCycleEdgesIncludeTheWholePathAndDoNotChangeEitherGraph() = diagnostics { messages ->
        Project.init()
        val a = CompoundData("A", "graph")
        val b = CompoundData("B", "graph")
        val c = CompoundData("C", "graph")

        a.extends(a)
        assertEquals(1, Project.errorCount)
        assertTrue(messages.any { "Cyclic inheritance: graph:A -> graph:A" in it })
        assertTrue(a.parent.isEmpty())
        assertTrue(a.scope.parent.isEmpty())

        a.extends(b)
        b.extends(c)
        c.extends(a)
        assertEquals(2, Project.errorCount)
        assertTrue(messages.any { "Cyclic inheritance: graph:C -> graph:A -> graph:B -> graph:C" in it })
        assertTrue(c.parent.isEmpty())
        assertTrue(c.scope.parent.isEmpty())
        assertTrue(a.isSubOf(c))
        assertFalse(c.isSubOf(a))
        assertSame(b.scope, a.scope.parent.single())
        assertSame(c.scope, b.scope.parent.single())
    }

    @Test fun aDiamondHasBothParentsAndOneSharedAncestorWithoutAnError() {
        Project.init()
        val a = DataTemplate("A", "graph")
        val left = DataTemplate("L", "graph").apply { extends(a) }
        val right = DataTemplate("R", "graph").apply { extends(a) }
        val child = DataTemplate("C", "graph").apply {
            extends(left)
            extends(right)
        }
        assertEquals(0, Project.errorCount)
        assertEquals(2, child.parent.size)
        assertSame(left, child.parent[0])
        assertSame(right, child.parent[1])
        assertTrue(child.isSubOf(a))
        assertTrue(child.isSubOf(left))
        assertTrue(child.isSubOf(right))
        assertFalse(a.isSubOf(child))
        assertEquals(child.getType().typeId, child.getType().typeId)

        child.unExtends(left)
        assertFalse(child.ifExtends(left))
        assertTrue(child.isSubOf(a))
        assertSame(right.scope, child.scope.parent.single())
    }

    @Test fun oldMalformedGraphsCannotOverflowSubtypeFieldOrTypeQueries() {
        Project.init()
        val a = CompoundData("A", "graph")
        val b = CompoundData("B", "graph")
        // Old models and external callers can bypass extends through the public parent list.
        a.parent.add(b)
        b.parent.add(a)
        assertTrue(a.isSubOf(b))
        assertFalse(a.isSubOf(CompoundData("Missing", "graph")))
        assertNull(a.getVar("missing"))
        assertSame(a, a.getType().objectData)

        val templateA = DataTemplate("TA", "graph")
        val templateB = DataTemplate("TB", "graph")
        templateA.parent.add(templateB)
        templateB.parent.add(templateA)
        assertTrue(templateA.isSubOf(templateB))
        assertFalse(templateA.isSubOf(DataTemplate("Missing", "graph")))
        assertEquals(templateA.inheritanceIdentity, templateA.getType().typeId)
        assertNull(templateA.getVar("missing"))
    }

    @Test fun genericGraphIdentityUsesDeclarationAndBoundValuesRatherThanGeneratedNames() = diagnostics { messages ->
        Project.init()
        val body = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("{}")))).templateBody()
        val origin = GenericDataTemplate(body, "Generic", "graph")
        fun specialization(display: String, argument: Long) = CompiledGenericDataTemplate(
            display,
            "graph",
            origin,
            emptyList(),
            listOf(CompilerValue.Integral(argument))
        )
        val one = specialization("display", 1)
        val sameOne = specialization("different_display", 1)
        val two = specialization("display", 2)
        assertEquals(one.inheritanceIdentity, sameOne.inheritanceIdentity)
        assertNotEquals(one.inheritanceIdentity, two.inheritanceIdentity)

        val child = DataTemplate("C", "graph")
        child.extends(one)
        child.extends(sameOne)
        child.extends(two)
        assertEquals(0, Project.errorCount)
        assertEquals(2, child.parent.size)
        assertTrue(child.ifExtends(sameOne))
        assertTrue(child.isSubOf(one))
        assertTrue(child.isSubOf(two))
        assertFalse(one.isSubOf(two))
        sameOne.extends(one)
        assertEquals(1, Project.errorCount)
        assertTrue(messages.any { "Cyclic inheritance" in it })
        assertTrue(sameOne.parent.isEmpty())

        child.unExtends(specialization("another_alias", 2))
        assertEquals(1, child.parent.size)
        assertSame(one, child.parent.single())
        assertSame(one.scope, child.scope.parent.single())
    }

    private fun diagnostics(block: (List<String>) -> Unit) {
        val logger = assertIs<Logger>(LogProcessor.logger)
        val messages = arrayListOf<String>()
        val appender = object : AbstractAppender("inheritance-graph-contract", null, null, false, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) {
                messages.add(event.message.formattedMessage)
            }
        }
        appender.start()
        logger.addAppender(appender)
        try {
            block(messages)
        } finally {
            logger.removeAppender(appender)
            appender.stop()
        }
    }
}
