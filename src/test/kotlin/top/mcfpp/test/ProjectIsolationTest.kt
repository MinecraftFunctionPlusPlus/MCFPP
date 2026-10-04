package top.mcfpp.test

import org.junit.jupiter.api.io.TempDir
import top.mcfpp.Project
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.scope.GlobalScope
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
}
