package top.mcfpp.test

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.obj.TypeDataTemplateObject
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.TypeDataTemplateConstructor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceTypeConstructorTest {
    @Test
    fun validTypeConstructorSelectsAndInitializesTheActualReceiver() {
        Project.init()
        val template = typeTemplate("IntValue")
        val constructor = constructor(template)
        assertIs<ParameterMatcher.TypeSelection.Selected>(template.resolveConstructor(listOf(MCInt(17))))

        val receiver = template.getType().buildUnConcrete("receiver") as TypeDataTemplateObject
        val result = constructor.invoke(linkedMapOf("value" to MCInt(17)), receiver)

        assertSame(receiver, result)
        assertEquals(17, (receiver.delegateVar as MCInt).value)
        assertFalse(receiver.isError)
    }

    @Test
    fun directAndResolvedConstructionRejectAbstractAndInvalidTypesWithoutEffects() {
        val cases = listOf<(TypeDataTemplate) -> Unit>(
            { it.isAbstract = true },
            { invalidOverride ->
                val parent = DataTemplate("Parent", "fixture.typeconstructor")
                parent.scope.addFunction(Function("f", parent, null), false)
                invalidOverride.extends(parent)
                invalidOverride.scope.addFunction(Function("f", invalidOverride, null), false)
            }
        )

        for ((index, configure) in cases.withIndex()) {
            Project.init()
            val template = typeTemplate("Rejected$index")
            configure(template)
            val constructor = constructor(template)
            val receiver = template.getType().buildUnConcrete("receiver") as TypeDataTemplateObject
            val entry = Function("attempt", "fixture.typeconstructor", null)
            val commandsBefore = entry.commands.analyzeAll().toList()
            val messages = diagnostics {
                entry.runInFunction {
                    assertIs<ParameterMatcher.TypeSelection.Missing>(template.resolveConstructor(listOf(MCInt(3))))
                    assertIs<UnknownVar>(constructor.invoke(linkedMapOf("value" to MCInt(3)), receiver))
                }
            }.joinToString("\n")

            assertTrue(messages.contains("Cannot instantiate") || messages.contains("requires override"), messages)
            assertTrue(Project.errorCount > 0)
            assertTrue(receiver.isError)
            assertNull(receiver.storageBinding, "Rejected construction must not bind receiver storage")
            assertEquals(commandsBefore, entry.commands.analyzeAll())
        }
    }

    private fun typeTemplate(name: String) = TypeDataTemplate(
        MCFPPBaseType.Int,
        name,
        "fixture.typeconstructor"
    )

    private fun constructor(template: TypeDataTemplate) = TypeDataTemplateConstructor(template).apply {
        appendNormalParam(template.typeAs, "value")
        // Keep the candidate visible even for an invalid restored model so resolution itself
        // proves that canInstantiate gates the constructor set.
        template.constructors.add(this)
    }

    private fun diagnostics(action: () -> Unit): List<String> {
        val logger = assertIs<Logger>(LogProcessor.logger)
        val messages = arrayListOf<String>()
        val appender = object : AbstractAppender("type-constructor", null, null, false, Property.EMPTY_ARRAY) {
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
