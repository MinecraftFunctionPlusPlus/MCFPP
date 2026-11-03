package top.mcfpp.optimization

/** A backend location remains distinct from command syntax until rendering. */
sealed interface MinecraftLocation {
    fun render(): String
    data class Score(val player: String, val objective: String) : MinecraftLocation {
        override fun render() = "$player $objective"
    }
    data class Nbt(val kind: String, val target: String, val path: String) : MinecraftLocation {
        override fun render() = "$kind $target $path"
    }
    data class FrameSlot(val frame: Int, val path: String) : MinecraftLocation {
        override fun render() = "storage mcfpp:system stack_frame[$frame]$path"
    }
}

sealed interface MinecraftInstruction {
    fun render(): String
    data class ScoreSet(val target: MinecraftLocation.Score, val value: String) : MinecraftInstruction {
        override fun render() = "scoreboard players set ${target.render()} $value"
    }
    data class ScoreOperation(val target: MinecraftLocation.Score, val operation: String, val source: MinecraftLocation.Score) : MinecraftInstruction {
        override fun render() = "scoreboard players operation ${target.render()} $operation ${source.render()}"
    }
    data class Data(val operation: String, val target: MinecraftLocation, val suffix: List<String>) : MinecraftInstruction {
        override fun render() = (listOf("data", operation, target.render()) + suffix).joinToString(" ")
    }
    data class Execute(val clauses: List<String>, val body: MinecraftInstruction) : MinecraftInstruction {
        override fun render() = "execute ${clauses.joinToString(" ")} run ${body.render()}"
    }
    data class Call(val target: String, val arguments: List<String> = emptyList(), val returning: Boolean = false) : MinecraftInstruction {
        override fun render() = (listOf(if (returning) "return run function" else "function", target) + arguments).joinToString(" ")
    }
    data class Frame(val enter: Boolean) : MinecraftInstruction {
        override fun render() = if (enter) "data modify storage mcfpp:system stack_frame prepend value {}"
            else "data remove storage mcfpp:system stack_frame[0]"
    }
    data class Opaque(val text: String) : MinecraftInstruction { override fun render() = text }
    data class Comment(val text: String) : MinecraftInstruction { override fun render() = text }
}

/** Compatibility boundary for producers which still use Command builders. Unknown text is opaque. */
object MinecraftLowering {
    fun tokens(text: String): List<String> {
        val tokens = mutableListOf<String>()
        var start = -1
        var quote: Char? = null
        var escaped = false
        var depth = 0
        for ((index, ch) in text.withIndex()) {
            if (start < 0 && !ch.isWhitespace()) start = index
            if (quote != null) {
                if (ch == quote && !escaped) quote = null
                escaped = ch == '\\' && !escaped
            } else when (ch) {
                '\'', '"' -> quote = ch
                '[', '{', '(' -> depth++
                ']', '}', ')' -> depth--
                else -> if (ch.isWhitespace() && depth == 0 && start >= 0) {
                    tokens.add(text.substring(start, index)); start = -1
                }
            }
        }
        if (start >= 0) tokens.add(text.substring(start))
        return tokens
    }
    fun lower(text: String): MinecraftInstruction {
        val t = tokens(text)
        if (text.trimStart().startsWith('#')) return MinecraftInstruction.Comment(text)
        if (text == MinecraftInstruction.Frame(true).render()) return MinecraftInstruction.Frame(true)
        if (text == MinecraftInstruction.Frame(false).render()) return MinecraftInstruction.Frame(false)
        if (t.take(3) == listOf("scoreboard", "players", "set") && t.size == 6)
            return MinecraftInstruction.ScoreSet(MinecraftLocation.Score(t[3], t[4]), t[5])
        if (t.take(3) == listOf("scoreboard", "players", "operation") && t.size == 8)
            return MinecraftInstruction.ScoreOperation(MinecraftLocation.Score(t[3], t[4]), t[5], MinecraftLocation.Score(t[6], t[7]))
        if (t.firstOrNull() == "execute" && "run" in t) {
            val split = t.indexOf("run")
            return MinecraftInstruction.Execute(t.subList(1, split), lower(t.drop(split + 1).joinToString(" ")))
        }
        if (t.firstOrNull() == "function" && t.size >= 2) return MinecraftInstruction.Call(t[1], t.drop(2))
        if (t.take(3) == listOf("return", "run", "function") && t.size >= 4) return MinecraftInstruction.Call(t[3], t.drop(4), true)
        if (t.firstOrNull() == "data" && t.size >= 5 && t[2] in setOf("storage", "entity")) {
            val frame = Regex("stack_frame\\[(\\d+)](.*)").matchEntire(t[4])
            val location = if (t[2] == "storage" && t[3] == "mcfpp:system" && frame != null)
                MinecraftLocation.FrameSlot(frame.groupValues[1].toInt(), frame.groupValues[2])
            else MinecraftLocation.Nbt(t[2], t[3], t[4])
            return MinecraftInstruction.Data(t[1], location, t.drop(5))
        }
        return MinecraftInstruction.Opaque(text)
    }
}

/** Shared by actual Minecraft IR and the provisional typed-IR cost provider. */
class MinecraftCostEstimator(val model: CommandCostModel) {
    fun cost(instructions: List<MinecraftInstruction>): Double {
        val macro = instructions.any { it.render().trimStart().startsWith('$') }
        return instructions.sumOf { instruction -> cost(instruction) +
            if (macro && instruction !is MinecraftInstruction.Comment && !instruction.render().trimStart().startsWith('$')) model.macroFunctionCommand else 0.0 }
    }
    fun cost(instruction: MinecraftInstruction): Double {
        if (instruction is MinecraftInstruction.Comment) return 0.0
        val text = instruction.render()
        if (text.trimStart().startsWith('$')) {
            val plain = MinecraftLowering.lower(text.trimStart().drop(1))
            return cost(plain) + model.macroBase + text.length * model.macroCharacter +
                Regex("\\$\\([^)]+\\)").findAll(text).count() * model.macroArgument
        }
        if (instruction is MinecraftInstruction.Execute) return maxOf(locationCost(instruction.clauses), cost(instruction.body))
        if (instruction is MinecraftInstruction.Data) return when (val location = instruction.target) {
            is MinecraftLocation.FrameSlot -> model.storage
            is MinecraftLocation.Nbt -> if (location.kind == "storage") model.storage else entityCost(location.target)
            else -> model.command
        }.coerceAtLeast(locationCost(instruction.suffix))
        if (instruction is MinecraftInstruction.Frame) return model.storage
        return maxOf(model.command, locationCost(MinecraftLowering.tokens(text).takeIf { it.firstOrNull() != "say" }.orEmpty()))
    }
    private fun entityCost(target: String) = if (target.startsWith("@a") || target.startsWith("@p") || target.startsWith("@r") ||
        !target.startsWith('@') && !Regex("[0-9a-fA-F-]{36}").matches(target)) model.playerNbt else model.entityNbt
    private fun locationCost(tokens: List<String>): Double {
        var cost = model.command
        for ((index, token) in tokens.withIndex()) {
            val address = index > 0 && tokens[index - 1] in setOf("data", "from", "string", "with", "result", "success")
            if (address && token == "storage") cost = maxOf(cost, model.storage)
            if (address && token == "entity" && index + 1 < tokens.size) cost = maxOf(cost, entityCost(tokens[index + 1]))
        }
        return cost
    }
}

object MinecraftCleanup {
    fun run(input: List<MinecraftInstruction>): List<MinecraftInstruction> {
        val output = mutableListOf<MinecraftInstruction>()
        for (i in input) {
            if (i is MinecraftInstruction.ScoreOperation && i.operation == "=" && i.source == i.target) continue
            // Only adjacent unconditional sets are safe: conditional success/result is observable.
            if (i is MinecraftInstruction.ScoreSet && output.lastOrNull() is MinecraftInstruction.ScoreSet &&
                (output.last() as MinecraftInstruction.ScoreSet).target == i.target) output.removeAt(output.lastIndex)
            output.add(i)
        }
        return output
    }
}

/** Legacy entry scaffolding can also be removed when its body cannot observe a frame. */
object MinecraftFramePlanning {
    fun run(input: List<MinecraftInstruction>): List<MinecraftInstruction> {
        val executable = input.withIndex().filter { it.value !is MinecraftInstruction.Comment }
        if (executable.size < 2 || executable.first().value != MinecraftInstruction.Frame(true) ||
            executable.last().value != MinecraftInstruction.Frame(false)) return input
        val independent = executable.drop(1).dropLast(1).all { (_, instruction) -> when (instruction) {
            is MinecraftInstruction.ScoreSet, is MinecraftInstruction.ScoreOperation -> true
            is MinecraftInstruction.Opaque -> instruction.text.startsWith("scoreboard objectives ") ||
                instruction.text == "say" || instruction.text.startsWith("say ")
            else -> false
        } }
        if (!independent) return input
        val enter = executable.first().index
        val exit = executable.last().index
        return input.filterIndexed { index, _ -> index != enter && index != exit }
    }
}
