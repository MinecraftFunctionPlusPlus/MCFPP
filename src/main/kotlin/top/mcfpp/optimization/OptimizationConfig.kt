package top.mcfpp.optimization

/** Heuristic weights, deliberately independent of the optimizer implementation. */
data class CommandCostModel(
    val command: Double = 1.0,
    val storage: Double = 10.0,
    val entityNbt: Double = 60.0,
    val playerNbt: Double = 150.0,
    val macroFunctionCommand: Double = 1.0,
    val macroBase: Double = 1.0,
    val macroCharacter: Double = 0.01,
    val macroArgument: Double = 1.0,
) {
    init {
        require(listOf(command, storage, entityNbt, playerNbt, macroFunctionCommand,
            macroBase, macroCharacter, macroArgument).all { it.isFinite() && it >= 0 }) {
            "optimization.costs: weights must be finite and nonnegative"
        }
    }
}

class OptimizationConfig(
    val level: Int = 2,
    disabledPasses: Set<String> = emptySet(),
    val costs: CommandCostModel = CommandCostModel(),
) {
    val disabledPasses: Set<String> = java.util.Collections.unmodifiableSet(LinkedHashSet(disabledPasses))
    init {
        require(level in 0..2) { "optimization.level: expected 0, 1 or 2" }
        require(disabledPasses.all { it in PassIds.optional }) {
            "optimization.disabledPasses: unknown or mandatory pass: ${disabledPasses - PassIds.optional}"
        }
    }
    fun enabled(id: String, minimumLevel: Int = 1) = level >= minimumLevel && id !in disabledPasses
    fun copy(level: Int = this.level, disabledPasses: Set<String> = this.disabledPasses,
             costs: CommandCostModel = this.costs) = OptimizationConfig(level, disabledPasses, costs)
    override fun equals(other: Any?) = other is OptimizationConfig && level == other.level &&
        disabledPasses == other.disabledPasses && costs == other.costs
    override fun hashCode() = 31 * (31 * level + disabledPasses.hashCode()) + costs.hashCode()
    override fun toString() = "OptimizationConfig(level=$level, disabledPasses=$disabledPasses, costs=$costs)"
}

object PassIds {
    const val CONSTANTS = "constant-propagation"
    const val SAVES = "call-save-elimination"
    const val FRAMES = "stack-frame-planning"
    const val ALGEBRA = "algebraic-simplification"
    const val COPIES = "copy-propagation"
    const val COMMON = "common-subexpression-elimination"
    const val CONTROL = "control-flow-simplification"
    const val DEAD = "dead-value-elimination"
    const val INLINE = "automatic-inlining"
    const val COMMANDS = "minecraft-command-cleanup"
    val optional = setOf(CONSTANTS, SAVES, FRAMES, ALGEBRA, COPIES, COMMON, CONTROL, DEAD, INLINE, COMMANDS)
}
