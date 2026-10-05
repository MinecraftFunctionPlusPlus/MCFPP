package top.mcfpp.backend

/** Compare normalized decimal components without mutating operands or requiring function return support. */
object LegacyFloatComparison {
    data class Components(val sign: String, val int0: String, val int1: String, val exp: String)

    fun emit(left: Components, right: Components, operation: String, comparison: String, result: String,
             emit: (String) -> Unit) {
        emit("scoreboard players set $comparison 0")
        // Higher components overwrite lower comparisons; equality retains the lower result.
        for ((a, b) in listOf(left.int1 to right.int1, left.int0 to right.int0, left.exp to right.exp)) {
            emit("execute if score $a < $b run scoreboard players set $comparison -1")
            emit("execute if score $a > $b run scoreboard players set $comparison 1")
        }
        emit("scoreboard players operation $comparison *= ${left.sign}")
        emit("execute if score ${left.sign} < ${right.sign} run scoreboard players set $comparison -1")
        emit("execute if score ${left.sign} > ${right.sign} run scoreboard players set $comparison 1")
        val matches = when (operation) {
            ">" -> "1"
            "<" -> "-1"
            ">=" -> "0.."
            "<=" -> "..0"
            "==", "!=" -> "0"
            else -> error("Unsupported float comparison: $operation")
        }
        emit("scoreboard players set $result ${if (operation == "!=") 1 else 0}")
        emit("execute if score $comparison matches $matches run scoreboard players set $result ${if (operation == "!=") 0 else 1}")
    }
}
