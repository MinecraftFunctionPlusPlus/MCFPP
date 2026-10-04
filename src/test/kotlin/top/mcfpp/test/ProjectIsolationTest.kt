package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.scope.GlobalScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProjectIsolationTest {
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
}
