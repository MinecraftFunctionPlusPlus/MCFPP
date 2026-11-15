package top.mcfpp.core.lang.bool

import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.model.function.Function

/** A field-position predicate materializes a score only when consumed as a value. */
internal class PresenceBool(val source: StorageBinding, val present: Boolean) : ScoreBool() {
    private var score: ScoreBool? = null
    fun prefix(): Command = Command("execute ${if (present) "if" else "unless"}").build(toCommandPart())
    override fun toCommandPart(): Command {
        source.data.materialize(source.place)
        return Command.buildAll("data", source.path)
    }
    fun materializePredicate(): ScoreBool = score ?: ScoreBool().also { result ->
        Function.addCommand("scoreboard players set ${result.name} ${result.boolObject} 0")
        Function.addCommand(prefix().build("run scoreboard players set ${result.name} ${result.boolObject} 1"))
        StorageAccess.publishBoolean(result, StorageLayout.Scoreboard(result.name, result.boolObject.toString()))
        score = result
    }
    override fun negation() = PresenceBool(source, !present)
    override fun clone() = PresenceBool(source, present)
    override fun toScoreBool(replace: Boolean) = materializePredicate()
    fun assume(result: Boolean) {
        val fact = source.data.facts.read(source.place) ?: source.data.declarations[source.place]?.let {
            ValueFacts(TypeKnowledge.Exact(it.declaredType), ValueKnowledge.Unknown)
        } ?: return
        source.data.facts.refine(source.place, fact.copy(
            state = if (present == result) ValueState.INITIALIZED else fact.state,
            presence = if (present == result) Presence.Present else Presence.Absent))
    }
}
