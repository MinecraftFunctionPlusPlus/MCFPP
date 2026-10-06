package top.mcfpp.test

import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionConditionTest {
    @Test
    fun functionConditionsRespectReturnsAndScoreShortCircuiting() {
        val count = "scoreboard players add calls test 1"
        val functions = mapOf(
            "test:positive" to listOf(count, "return 2", "say unreachable positive"),
            "test:negative" to listOf(count, "return -3", "say unreachable negative"),
            "test:zero" to listOf(count, "return 0"),
            "test:no_return" to listOf(count),
            "test:wrapper" to listOf(
                "function test:negative", "say nested continued",
                "return run function test:positive", "say unreachable wrapper"
            )
        )
        val machine = ScoreCommandExecutor(listOf(
            "scoreboard players set calls test 0",
            "scoreboard players set gate test 0",
            "execute if function test:positive run say positive",
            "execute unless function test:positive run say wrong positive",
            "execute if function test:negative run say negative",
            "execute if function test:zero run say wrong zero",
            "execute unless function test:zero run say zero",
            "execute if function test:no_return run say wrong missing",
            "execute unless function test:no_return run say missing",
            "execute if score gate test matches 1 if function test:positive run say wrong short circuit",
            "execute if score gate test > gate test if function test:positive run say wrong compare",
            "execute if function test:positive unless score gate test matches 1 run say chain",
            "function test:wrapper", "say caller continued",
            "execute if function test:wrapper run say returned", "say after condition"
        ), functions)
        assertEquals(12, machine.values.getValue("calls test"))
        assertEquals(listOf("positive", "negative", "zero", "missing", "chain",
            "nested continued", "caller continued", "nested continued", "returned", "after condition"), machine.messages)
    }
}
