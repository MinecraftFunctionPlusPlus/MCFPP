package top.mcfpp.type

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.scope.IScopeWithType
import top.mcfpp.model.compound.RawPreparationFact
import top.mcfpp.util.LogProcessor

class MCFPPTypeAliasType(val t: mcfppParser.TypeContext): MCFPPType() {
    override val isValueType: Boolean get() = false

    @Transient private var resolving = false
    @Transient private var failed = false
    @Transient private var resolved: MCFPPType? = null

    internal val cachedTarget: MCFPPType? get() = resolved

    private class RawState(val target: MCFPPType?, val resolving: Boolean, val failed: Boolean)
    private fun rawState() = RawState(resolved, resolving, failed)

    /** The exact same slots as rollback, with no resolution or traversal of the target. */
    internal fun rawPreparationFacts(): RawPreparationFact = rawState().let {
        RawPreparationFact.row(RawPreparationFact.scalar("alias"), RawPreparationFact.identity(this),
            RawPreparationFact.identity(it.target), RawPreparationFact.scalar(it.resolving), RawPreparationFact.scalar(it.failed))
    }

    /** Alias resolution flags and raw target; target internals belong to the declaration deep journal. */
    internal class PrivateStateCheckpoint internal constructor(
        elements: List<MCFPPType>, private val restoreState: () -> Unit,
        private val recapture: () -> PrivateStateCheckpoint
    ) {
        val elements: List<MCFPPType> = java.util.Collections.unmodifiableList(ArrayList(elements))
        fun restore() = restoreState()
        fun captureCurrent(): PrivateStateCheckpoint = recapture()
    }

    internal fun checkpointPrivateState(): PrivateStateCheckpoint = checkpointPrivateState(emptyList())

    private fun checkpointPrivateState(retained: List<MCFPPType>): PrivateStateCheckpoint {
        // This compiler class is final. No parser, scope lookup, typeId getter or target hash is used.
        val raw = rawState()
        val target = raw.target
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>())
        val elements = (retained + listOfNotNull(target)).filter { seen.add(it) }
        return PrivateStateCheckpoint(elements, {
            resolving = raw.resolving
            failed = raw.failed
            resolved = target
        }, { checkpointPrivateState(elements) })
    }

    /** The alias is a declaration placeholder, never a distinct language value type. */
    fun resolve(scope: IScopeWithType): MCFPPType? {
        resolved?.let { return it }
        if (failed) return null
        if (resolving) {
            failed = true
            LogProcessor.error("Cyclic type alias: ${t.text}")
            return null
        }
        resolving = true
        try {
            val target = MCFPPType.parseFromContext(t, scope)
            if (target == null) {
                if (!failed) LogProcessor.error("Undefined type in alias: ${t.text}")
                failed = true
            } else {
                resolved = target
            }
            return target
        } finally {
            resolving = false
        }
    }
}
