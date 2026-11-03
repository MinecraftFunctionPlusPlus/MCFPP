package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

internal data class ExpansionFrame(val macroSignature: String, val callSite: SourceSpan, val callback: CallbackId? = null)

internal class ExpansionDiagnostic(val source: SourceSpan, val message: String, frames: List<ExpansionFrame> = emptyList()) {
    val frames = immutableList(frames)
    override fun toString(): String = "$message at $source" + frames.joinToString("", prefix = if (frames.isEmpty()) "" else "\nExpansion chain:") {
        "\n  ${it.macroSignature} at ${it.callSite}"
    }
}

internal class ExpansionFailure(val diagnostic: ExpansionDiagnostic) : IllegalStateException(diagnostic.toString())

/** A compilation unit owns one budget; abandoning a savepoint never refunds allocations. */
internal class ExpansionBudget(val maxDepth: Int = 64, val maxNodes: Int = 100_000) {
    init {
        require(maxDepth > 0 && maxNodes > 0) { "Macro limits must be positive" }
    }
    private val stack = arrayListOf<BudgetFrame>()
    var usedNodes: Long = 0
        private set
    val depth: Int get() = stack.size
    fun frames(): List<ExpansionFrame> = immutableList(stack.map { it.frame })

    @Synchronized
    fun enter(frame: ExpansionFrame): BudgetFrame {
        val chain = frames() + frame
        if (stack.size >= maxDepth) throw ExpansionFailure(ExpansionDiagnostic(frame.callSite, "Macro expansion exceeds depth $maxDepth", chain))
        if (frame.callback != null && stack.any { it.frame.callback == frame.callback })
            throw ExpansionFailure(ExpansionDiagnostic(frame.callSite, "Recursive inline callback", chain))
        return BudgetFrame(this, frame).also { stack += it }
    }

    @Synchronized
    fun allocate(count: Int, origin: Origin) {
        require(count >= 0) { "Node allocation count must not be negative" }
        if (count == 0) return
        usedNodes += count.toLong()
        if (usedNodes > maxNodes) {
            val chain = if (stack.isNotEmpty()) frames() else origin.expansionFrames.map { ExpansionFrame(it.macroName, it.callSite) }
            throw ExpansionFailure(ExpansionDiagnostic(origin.callSite, "Macro expansion exceeds $maxNodes generated nodes", chain))
        }
    }

    @Synchronized
    internal fun requireCurrent(frame: BudgetFrame) {
        check(!frame.closed && stack.lastOrNull() === frame) { "Expansion frames must close in reverse order" }
    }

    @Synchronized
    internal fun leave(frame: BudgetFrame) {
        if (frame.closed) return
        requireCurrent(frame)
        stack.removeAt(stack.lastIndex)
        frame.retire()
    }

    /** Cancellation revokes the owned dynamic extent, including finally handles not yet closed. */
    @Synchronized
    internal fun cancel(frame: BudgetFrame) {
        if (frame.closed) return
        val index = stack.indexOfFirst { it === frame }
        check(index >= 0) { "Expansion frame does not belong to the active stack" }
        while (stack.size > index) stack.removeAt(stack.lastIndex).retire()
    }
}

internal class BudgetFrame internal constructor(private val budget: ExpansionBudget, internal val frame: ExpansionFrame) : AutoCloseable {
    internal var closed = false
        private set
    internal fun retire() {
        closed = true
    }
    internal fun requireCurrent() {
        budget.requireCurrent(this)
    }
    internal fun cancel() {
        budget.cancel(this)
    }
    override fun close() {
        budget.leave(this)
    }
}
