package top.mcfpp.core.lang

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.SymbolId
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import java.util.Collections

/**
 * How a lexical binding enters a lambda environment.
 *
 * [CREATION_TIME_VALUE] is evaluated once when the lambda expression is evaluated. The
 * environment owns either an immutable [CompilerValue] snapshot or an independent runtime
 * copy produced through the existing [StorageAccess] copy boundary. [GLOBAL_READ] keeps the
 * original static/global declaration and reads that declaration on every call; it is never
 * replaced by a creation-time snapshot.
 */
enum class LambdaCaptureKind {
    CREATION_TIME_VALUE,
    GLOBAL_READ
}

/**
 * The existing value-copy family used for a capture. This deliberately does not describe a
 * second type system: the family is derived from the current [MCFPPType] and the existing
 * storage snapshot/copy behaviour.
 *
 * [RUNTIME_REPRESENTATION_COPY] copies the current runtime representation into an independent
 * location. It does not promise arbitrary user-visible deep copy, object identity, reference
 * preservation or persistence across ticks.
 */
enum class LambdaCopyContract(
    val displayName: String,
    val independentValue: Boolean,
    /**
     * Kept false for every family. Ordinary captures copy the type's existing representation;
     * they never claim a generic recursive deep copy.
     */
    val arbitraryDeepCopy: Boolean = false
) {
    /** Closed scalar/immutable value held as an immutable [CompilerValue]. */
    SCALAR_SNAPSHOT("creation-time scalar snapshot", true),

    /** Closed compiler-only value (for example pos/type) held as an immutable snapshot. */
    COMPILER_VALUE_SNAPSHOT("creation-time compiler-value snapshot", true),

    /** Independent runtime location created by the existing storage copy boundary. */
    RUNTIME_REPRESENTATION_COPY("independent runtime representation copy", true),

    /** ScoreRef/NBTRef capture is explicitly out of scope for this phase. */
    REFERENCE_DEFERRED("reference capture deferred", false),

    /** No independent ownership or copy/lifetime contract is established. */
    NOT_COPYABLE("no independent copy contract", false)
}

enum class LambdaCaptureDiagnosticCode {
    ERROR_VALUE,
    UNBOUND_VALUE,
    IDENTITY_MISMATCH,
    UNINITIALIZED_SOURCE,
    NO_COPY_CONTRACT,
    EXTERNAL_REFERENCE_WITHOUT_OWNERSHIP,
    REFERENCE_CAPTURE_DEFERRED,
    CAPTURE_WRITE_READONLY,
    COPY_FAILED
}

/** A concrete, reusable capture diagnostic. Integration decides when to publish it. */
data class LambdaCaptureDiagnostic(
    val code: LambdaCaptureDiagnosticCode,
    val message: String
) {
    fun report() = LogProcessor.error(message)
}

sealed interface LambdaCaptureResult {
    data class Captured(val capture: LambdaCapture) : LambdaCaptureResult
    data class Rejected(val diagnostic: LambdaCaptureDiagnostic) : LambdaCaptureResult
}

/**
 * One read-only lexical binding in a lambda environment.
 *
 * Identity is the declaration [SymbolId], never the spelling of the name. A creation-time
 * value keeps an immutable [snapshot] and/or an independent compiler-owned [owned] copy. A
 * global binding keeps the original [global] adapter and has no snapshot.
 *
 * Construction goes through the analysis rules; the constructor is internal so a capture
 * cannot silently lose its copy contract or become a mutable shared box.
 */
class LambdaCapture internal constructor(
    val symbol: SymbolId,
    val name: String,
    val declaredType: MCFPPType,
    val kind: LambdaCaptureKind,
    val contract: LambdaCopyContract,
    val snapshot: CompilerValue?,
    val owned: Var<*>?,
    val global: Var<*>?
) {
    init {
        require(contract != LambdaCopyContract.NOT_COPYABLE && contract != LambdaCopyContract.REFERENCE_DEFERRED) {
            "A captured binding must have an independent copy contract"
        }
        when (kind) {
            LambdaCaptureKind.CREATION_TIME_VALUE -> require(snapshot != null || owned != null) {
                "A creation-time capture must own a snapshot or an independent copy"
            }
            LambdaCaptureKind.GLOBAL_READ -> require(global != null && snapshot == null && owned == null) {
                "A global capture reads the original declaration and has no frozen value"
            }
        }
    }

    val typeId get() = declaredType.typeId

    val isCreationTimeValue get() = kind == LambdaCaptureKind.CREATION_TIME_VALUE

    val isGlobalRead get() = kind == LambdaCaptureKind.GLOBAL_READ

    /** Place of the owned runtime copy, or of the global declaration. Snapshots have none. */
    val storagePlace: Place?
        get() = owned?.storageBinding?.place ?: global?.storageBinding?.place

    /**
     * Current model value. For a creation-time capture this remains the value observed at
     * capture time. For a global read this deliberately reflects the global declaration now.
     */
    fun currentSnapshot(): CompilerValue? = when (kind) {
        LambdaCaptureKind.GLOBAL_READ -> StorageAccess.snapshot(global!!)
        LambdaCaptureKind.CREATION_TIME_VALUE -> snapshot ?: owned?.let { StorageAccess.snapshot(it) }
    }
}

/**
 * Immutable ordered set of captures. There is intentionally no write/replace operation:
 * lambda bodies read captures, they never mutate the captured outer binding.
 */
class LambdaEnvironment private constructor(captures: List<LambdaCapture>) {

    private val ordered: List<LambdaCapture> = Collections.unmodifiableList(ArrayList(captures))
    private val bySymbol: Map<SymbolId, LambdaCapture> = HashMap<SymbolId, LambdaCapture>().apply {
        for (capture in ordered) {
            require(put(capture.symbol, capture) == null) {
                "A lambda environment cannot capture the same lexical identity twice: ${capture.symbol}"
            }
        }
    }

    val captures: List<LambdaCapture> get() = ordered

    operator fun get(symbol: SymbolId): LambdaCapture? = bySymbol[symbol]

    fun requireCapture(symbol: SymbolId): LambdaCapture =
        bySymbol[symbol] ?: throw IllegalArgumentException("No capture for lexical symbol $symbol")

    fun diagnosticForWrite(symbol: SymbolId): LambdaCaptureDiagnostic? =
        bySymbol[symbol]?.takeIf { it.isCreationTimeValue }?.let { LambdaCaptureRulesAccess.readonlyDiagnostic(it) }

    companion object {
        val EMPTY = LambdaEnvironment(emptyList())

        fun of(captures: List<LambdaCapture>): LambdaEnvironment =
            if (captures.isEmpty()) EMPTY else LambdaEnvironment(captures)
    }
}

/**
 * Bridge to the shared diagnostic wording without giving the value model knowledge of the
 * analysis package's rule table.
 */
internal object LambdaCaptureRulesAccess {
    fun readonlyDiagnostic(capture: LambdaCapture) = LambdaCaptureDiagnostic(
        LambdaCaptureDiagnosticCode.CAPTURE_WRITE_READONLY,
        "Cannot modify captured value '${capture.name}': a lambda capture is read-only; " +
            "return a new value or accumulate explicitly (for example with fold)"
    )
}
