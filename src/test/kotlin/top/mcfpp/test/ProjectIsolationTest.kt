package top.mcfpp.test

import org.junit.jupiter.api.io.TempDir
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.GenericDataTemplateInfo
import top.mcfpp.io.info.FunctionTagInfo
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.mni.expand.FunctionStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProjectIsolationTest {
    @TempDir lateinit var sourceRoot: Path

    @Test fun initializingTheNextProjectDropsTokensAndReloadsTheSameSourcePath() {
        val previousSourcePath = Project.config.sourcePath
        try {
            Project.config.sourcePath = sourceRoot
            Project.init()
            val source = sourceRoot.resolve("main.mcfpp")
            source.writeText("func before() {}")
            val oldFile = MCFPPFile(source.toFile())
            oldFile.tree()
            assertTrue(Project.tokens.containsKey(oldFile))
            assertTrue(Project.trees.containsKey(oldFile))

            source.writeText("func after() {}")
            Project.init()

            assertTrue(Project.tokens.isEmpty())
            assertTrue(Project.trees.isEmpty())
            val newFile = MCFPPFile(source.toFile())
            assertTrue(newFile.tree().text.contains("after"))
            assertFalse(newFile.tree().text.contains("before"))
        } finally {
            Project.config.sourcePath = previousSourcePath
            Project.init()
        }
    }

    @Test fun initializingTheNextProjectDropsThePreviousTaggedFunctions() {
        Project.init()
        val previous = Function("previous", "old_project", null)
        FunctionTag.LOAD.functions.add(previous)
        FunctionTag.TICK.functions.add(previous)

        Project.init()

        assertFalse(FunctionTag.LOAD.functions.contains(previous))
        assertFalse(FunctionTag.TICK.functions.contains(previous))
        assertTrue(FunctionTag.LOAD.functions.isEmpty())
        assertEquals(listOf(Project.mcfppSystemTick), FunctionTag.TICK.functions)
        assertSame(FunctionTag.LOAD, GlobalScope.functionTags["minecraft:load"])
        assertSame(FunctionTag.TICK, GlobalScope.functionTags["minecraft:tick"])
    }

    @Test fun initializingTheNextProjectDetachesSharedBuiltinCallersAndJournalRollbackRestoresThem() {
        try {
            MCFPPStringTest.readFromString("""
                namespace isolation.first
                func main() { var rendered = ("first").toText() }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val builtin = MCFPPBaseType.String.instanceData.scope.functions.getValue("toText").single()
            val firstCallers = builtin.parent.toList()
            assertTrue(firstCallers.isNotEmpty())
            assertTrue(firstCallers.all { caller -> caller.child.any { it === builtin } })
            val namespaces = NamespaceStateJournal.checkpoint()
            val functions = FunctionStateJournal.checkpoint(firstCallers + builtin)

            Project.init()

            assertTrue(builtin.parent.isEmpty())
            namespaces.restore()
            functions.restore()
            assertEquals(firstCallers.size, builtin.parent.size)
            firstCallers.forEachIndexed { index, caller ->
                assertSame(caller, builtin.parent[index])
                assertTrue(caller.child.any { it === builtin })
            }

            MCFPPStringTest.readFromString("""
                namespace isolation.second
                func main() { var rendered = ("second").toText() }
            """.trimIndent(), version = "26.3")

            assertEquals(0, Project.errorCount)
            assertSame(builtin, MCFPPBaseType.String.instanceData.scope.functions.getValue("toText").single())
            assertTrue(builtin.parent.isNotEmpty())
            assertTrue(builtin.parent.none { caller -> firstCallers.any { it === caller } })
            assertTrue(builtin.parent.all { caller -> caller.child.any { it === builtin } })
        } finally {
            Project.init()
        }
    }

    @Test fun callGraphEdgesUseFunctionIdentityWithoutRepeatingAnExistingEdge() {
        val caller = Function("caller", "isolation", null)
        val first = Function("same", "isolation", null)
        val second = Function("same", "isolation", null)
        caller.recordCall(first)
        caller.recordCall(first)
        caller.recordCall(second)

        assertEquals(2, caller.child.size)
        assertSame(first, caller.child[0])
        assertSame(second, caller.child[1])
        assertSame(caller, first.parent.single())
        assertSame(caller, second.parent.single())
    }

    @Test fun initializingTheNextProjectDropsThePreviousCompilationContext() {
        Project.init()
        val oldFile = MCFPPFile()
        MCFPPFile.currFile = oldFile
        val previous = Function("previous", "old_project", null)
        Function.currFunction = previous
        Function.forcedField = previous.scope
        DataTemplate.currTemplate = DataTemplate("Previous", "old_project")

        Project.init()

        assertFalse(Function("next", "next_project", null).scope.parent.contains(oldFile.field))
        assertNull(MCFPPFile.currFile)
        assertSame(Function.nullFunction, Function.currFunction)
        assertNull(Function.forcedField)
        assertNull(DataTemplate.currTemplate)
    }

    @Test fun templateMetadataCachesAreLimitedToTheCurrentProject() {
        Project.init()
        val original = DataTemplate("Cached", "metadata.isolation")
        val info = DataTemplateInfo.from(original)
        val first = info.get()
        assertSame(first, info.get())

        original.isAbstract = true
        Project.init()

        assertNotSame(first, info.get())
        val updated = DataTemplateInfo.from(original)
        assertNotSame(info, updated)
        assertTrue(updated.isAbstract)
        assertSame(DataTemplate.baseDataTemplate, DataTemplateInfo.from(DataTemplate.baseDataTemplate).get())
    }

    @Test fun genericTemplateMetadataCachesAreLimitedToTheCurrentProject() {
        Project.init()
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("{}"))))
        val original = GenericDataTemplate(parser.templateBody(), "Cached", "metadata.isolation")
        val info = GenericDataTemplateInfo.from(original)
        val first = info.get()
        assertSame(first, info.get())

        original.isAbstract = true
        Project.init()

        assertNotSame(first, info.get())
        val updated = GenericDataTemplateInfo.from(original)
        assertNotSame(info, updated)
        assertTrue(updated.isAbstract)
    }

    @Test fun cachedFunctionTagsDoNotRetainFunctionsFromThePreviousProject() {
        Project.init()
        val info = FunctionTagInfo("metadata.isolation", "cached")
        val first = info.get()
        first.functions.add(Function("previous", "metadata.isolation", null))
        assertSame(first, info.get())

        Project.init()

        val next = info.get()
        assertNotSame(first, next)
        assertTrue(next.functions.isEmpty())
    }
}
