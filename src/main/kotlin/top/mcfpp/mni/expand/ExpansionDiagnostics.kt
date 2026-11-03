package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

/**
 * Canonical presentation of expansion failures and of the fully expanded tree.
 *
 * A failure keeps the deepest failing [SourceSpan] so the exact generated/source node stays
 * inspectable, but every root expansion adds the user's original call and the complete
 * root-to-leaf [ExpansionFrame] chain. The dump is a pure function of the tree: [CodePrinter]
 * assigns names by traversal and public declaration names, never by arena/session identity.
 */
internal object ExpansionDiagnostics {
    private const val USER_CALL_MARKER = "(user call "

    /**
     * Return a diagnostic that still points at [ExpansionDiagnostic.source] (the precise failing
     * node) while naming the user's [userCall] when it differs, and whose frame list starts at that
     * call. The result is idempotent for an already sourced diagnostic.
     */
    fun sourced(
        diagnostic: ExpansionDiagnostic,
        userCall: SourceSpan,
        rootSignature: String = ""
    ): ExpansionDiagnostic {
        val frames = completeFrames(diagnostic.frames, userCall, rootSignature)
        val message = if (diagnostic.source == userCall || diagnostic.message.contains(USER_CALL_MARKER))
            diagnostic.message
        else "${diagnostic.message} $USER_CALL_MARKER$userCall)"
        if (message == diagnostic.message && frames == diagnostic.frames) return diagnostic
        return ExpansionDiagnostic(diagnostic.source, message, frames)
    }

    /** Rewrap a failure once at the root boundary without losing its original cause chain. */
    fun sourcedFailure(
        failure: ExpansionFailure,
        userCall: SourceSpan,
        rootSignature: String = ""
    ): ExpansionFailure {
        val diagnostic = sourced(failure.diagnostic, userCall, rootSignature)
        if (diagnostic === failure.diagnostic) return failure
        val replacement = ExpansionFailure(diagnostic)
        failure.cause?.let { replacement.initCause(it) }
        failure.suppressed.forEach { replacement.addSuppressed(it) }
        return replacement
    }

    /** Ensure the frame list starts at the user call; never drop the deeper failure frames. */
    fun completeFrames(
        frames: List<ExpansionFrame>,
        userCall: SourceSpan,
        rootSignature: String = ""
    ): List<ExpansionFrame> {
        if (frames.firstOrNull()?.callSite == userCall) return frames
        val root = ExpansionFrame(rootSignature.ifBlank { "<root>" }, userCall)
        return immutableList(listOf(root) + frames)
    }

    /** Render the chain root-first, without physical identity or allocation counters. */
    fun chain(frames: List<ExpansionFrame>): String = frames.joinToString("\n") { "  ${it.macroSignature} at ${it.callSite}" }

    /** Diagnostic plus any preserved cause messages, for hosts that log a complete failure. */
    fun render(failure: ExpansionFailure): String = buildString {
        append(failure.diagnostic.toString())
        val seen = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Throwable, Boolean>()
        )
        var cause = failure.cause
        while (cause != null && seen.add(cause)) {
            append("\nCaused by: ").append(cause.message ?: cause.javaClass.simpleName)
            cause = cause.cause
        }
    }

    /**
     * The single canonical dump header/body used by configuration-driven logging and tests.
     * [signature] must be a canonical macro signature, not a physical session identity.
     */
    fun dump(code: Code, call: SourceSpan, signature: String): String = "Macro expansion dump at $call for $signature:\n${canonical(code)}"

    /** Print a tree with traversal-stable names only. */
    fun canonical(code: Code): String = CodePrinter().print(code)
}
