package top.mcfpp.analysis.lambda

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.Symbol
import top.mcfpp.analysis.ValueState
import top.mcfpp.core.lang.LambdaCapture
import top.mcfpp.core.lang.LambdaCaptureDiagnostic
import top.mcfpp.core.lang.LambdaCaptureDiagnosticCode
import top.mcfpp.core.lang.LambdaCaptureKind
import top.mcfpp.core.lang.LambdaCaptureResult
import top.mcfpp.core.lang.LambdaCaptureRulesAccess
import top.mcfpp.core.lang.LambdaCopyContract
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPImmutableListType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPNotCompiledGenericType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPReferenceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPUnionType
import top.mcfpp.type.MCFPPVectorType
import top.mcfpp.type.UnresolvedType

/** Legacy location-only capture requests, without an owned public reference descriptor. */
enum class LambdaReferenceKind(val displayName: String) {
    SCORE("ScoreRef"),
    NBT("NBTRef")
}

/**
 * Reusable capture capability and creation rules for ordinary synchronous lambdas.
 *
 * This object does not parse, bind or visit source. It consumes an already-resolved lexical
 * [Symbol] plus its current [Var] and either creates a read-only [LambdaCapture] or returns a
 * concrete [LambdaCaptureDiagnostic]. The parser/visitor integration is a later node.
 */
object LambdaCaptureRules {

    /**
     * The copy family of a declared type. [capture] still inspects actual type evidence: an
     * erased `any`/`object` may be capturable when a complete snapshot or concrete runtime
     * producer exists, and is rejected otherwise.
     */
    fun contractFor(type: MCFPPType): LambdaCopyContract = when {
        type == MCFPPBaseType.Int ||
            type == MCFPPBaseType.Bool ||
            type == MCFPPBaseType.Float ||
            type == MCFPPBaseType.String ||
            type == MCFPPNBTType.Byte ||
            type == MCFPPNBTType.Short ||
            type == MCFPPNBTType.Long ||
            type == MCFPPNBTType.Double ||
            type == MCFPPBaseType.Range ||
            type == MCFPPPrivateType.Null -> LambdaCopyContract.SCALAR_SNAPSHOT

        type is MCFPPEnumType -> LambdaCopyContract.SCALAR_SNAPSHOT

        // The opaque payload owns both the registered target and its value environment.
        type is MCFPPFunctionType -> LambdaCopyContract.RUNTIME_REPRESENTATION_COPY

        // A public reference owns its address descriptor; copying it never reads the target.
        type is MCFPPReferenceType -> LambdaCopyContract.RUNTIME_REPRESENTATION_COPY

        // A private reference view points at someone else's storage; it does not own a copy.
        type == MCFPPPrivateType.StaticMemberViewType -> LambdaCopyContract.NOT_COPYABLE
        type == MCFPPPrivateType.MCFPPCoordinateDimension ||
            type == MCFPPBaseType.Pos2 ||
            type == MCFPPBaseType.Pos3 ||
            type == MCFPPConcreteType.Type -> LambdaCopyContract.COMPILER_VALUE_SNAPSHOT
        type is MCFPPPrivateType -> LambdaCopyContract.NOT_COPYABLE

        // Erased values need concrete actual-type evidence before a copy contract is claimed.
        type == MCFPPBaseType.Any || type == MCFPPBaseType.Object -> LambdaCopyContract.NOT_COPYABLE

        type is MCFPPGenericParamType ||
            type is UnresolvedType ||
            type is MCFPPNotCompiledGenericType -> LambdaCopyContract.NOT_COPYABLE

        type is MCFPPUnionType -> {
            val families = type.types.map(::contractFor)
            when {
                families.any { it == LambdaCopyContract.NOT_COPYABLE || it == LambdaCopyContract.REFERENCE_DEFERRED } ->
                    LambdaCopyContract.NOT_COPYABLE
                families.all { it == LambdaCopyContract.SCALAR_SNAPSHOT } -> LambdaCopyContract.SCALAR_SNAPSHOT
                families.all { it == LambdaCopyContract.COMPILER_VALUE_SNAPSHOT } -> LambdaCopyContract.COMPILER_VALUE_SNAPSHOT
                else -> LambdaCopyContract.RUNTIME_REPRESENTATION_COPY
            }
        }

        type is MCFPPDataTemplateType ||
            type is MCFPPListType ||
            type is MCFPPImmutableListType ||
            type is MCFPPEntityType ||
            type == MCFPPNBTType.NBT ||
            type == MCFPPNBTType.ByteArray ||
            type == MCFPPNBTType.IntArray ||
            type == MCFPPNBTType.LongArray ||
            type is MCFPPVectorType ||
            type == MCFPPBaseType.JsonText ||
            type is top.mcfpp.type.MCFPPDictType ||
            type is top.mcfpp.type.MCFPPMapType ->
            if (type.hasRuntimeRepresentation) LambdaCopyContract.RUNTIME_REPRESENTATION_COPY
            else LambdaCopyContract.COMPILER_VALUE_SNAPSHOT

        // A runtime representation is not by itself a copy/lifetime contract. Only the
        // explicitly enumerated families above may claim one; anything else stays rejected
        // until a concrete contract is added here.
        else -> LambdaCopyContract.NOT_COPYABLE
    }

    /**
     * Captures a resolved lexical value. [expectedSymbol] is optional and only exists so an
     * integration point can prove it is capturing the declaration selected by lexical lookup
     * rather than a same-named binding from another scope.
     */
    fun capture(value: Var<*>, expectedSymbol: Symbol? = null): LambdaCaptureResult {
        if (value.isError) return reject(LambdaCaptureDiagnosticCode.ERROR_VALUE,
            "Cannot capture erroneous value '${value.identifier}'")
        top.mcfpp.core.lang.context.ContextBorrowing.escapeDiagnostic(value, "an ordinary closure capture")?.let {
            return reject(LambdaCaptureDiagnosticCode.NO_COPY_CONTRACT, it)
        }

        val declared = value.symbol
            ?: return reject(LambdaCaptureDiagnosticCode.UNBOUND_VALUE,
                "Cannot capture '${value.identifier}' without a lexical declaration identity")
        if (expectedSymbol != null && expectedSymbol.id != declared.id) return reject(
            LambdaCaptureDiagnosticCode.IDENTITY_MISMATCH,
            "Cannot capture '${value.identifier}': lexical identity ${declared.id} does not match " +
                "the resolved declaration ${expectedSymbol.id}"
        )

        val actual = StorageAccess.actualType(value)

        // Global/static symbols are read through their original declaration. Freezing one
        // requires assigning it to a local value first; capture never snapshots it implicitly.
        if (value.isStatic) {
            val contract = contractFor(actual)
            if (contract == LambdaCopyContract.NOT_COPYABLE) return noContract(actual)
            return LambdaCaptureResult.Captured(LambdaCapture(
                symbol = declared.id,
                name = declared.name,
                declaredType = value.type,
                kind = LambdaCaptureKind.GLOBAL_READ,
                contract = contract,
                snapshot = null,
                owned = null,
                global = value
            ))
        }

        val binding = StorageAccess.ensure(value)
        val state = binding.data.facts.readAccess(binding.place, binding.data.declarations, binding.data.types)?.state
        if (state != ValueState.INITIALIZED) return reject(
            LambdaCaptureDiagnosticCode.UNINITIALIZED_SOURCE,
            "Cannot capture uninitialized variable '${value.identifier}'"
        )

        val snapshot = StorageAccess.snapshot(value)
        if (snapshot != null) {
            val snapshotType = snapshotType(value, snapshot) ?: actual
            val family = contractFor(snapshotType).takeUnless { it == LambdaCopyContract.NOT_COPYABLE }
                ?: LambdaCopyContract.COMPILER_VALUE_SNAPSHOT
            return LambdaCaptureResult.Captured(LambdaCapture(
                symbol = declared.id,
                name = declared.name,
                declaredType = value.type,
                kind = LambdaCaptureKind.CREATION_TIME_VALUE,
                contract = family,
                snapshot = snapshot,
                owned = null,
                global = null
            ))
        }

        val contract = contractFor(actual)
        if (contract == LambdaCopyContract.NOT_COPYABLE) return noContract(actual)
        if (binding.view != null && !StorageAccess.hasRuntimeRepresentation(value)) return reject(
            LambdaCaptureDiagnosticCode.EXTERNAL_REFERENCE_WITHOUT_OWNERSHIP,
            "Cannot capture '${value.identifier}' through a reference view without independent ownership; " +
                "capture an owned value or a complete immutable snapshot"
        )

        val copy = StorageAccess.capture(value)
        if (copy.isError) return reject(
            LambdaCaptureDiagnosticCode.COPY_FAILED,
            "Cannot create an independent capture copy of '${value.identifier}' (type '${actual.typeName}')"
        )
        return LambdaCaptureResult.Captured(LambdaCapture(
            symbol = declared.id,
            name = declared.name,
            declaredType = value.type,
            kind = LambdaCaptureKind.CREATION_TIME_VALUE,
            contract = contract,
            snapshot = StorageAccess.snapshot(copy),
            owned = copy,
            global = null
        ))
    }

    /**
     * A name/type pair cannot supply an address descriptor. Public ScoreRef/NBTRef values use
     * [capture] and its ordinary value-copy contract; this legacy request remains rejected.
     */
    fun captureReference(kind: LambdaReferenceKind, name: String, type: MCFPPType): LambdaCaptureResult.Rejected =
        reject(
            LambdaCaptureDiagnosticCode.REFERENCE_CAPTURE_DEFERRED,
            "Cannot capture '$name' by ${kind.displayName}: reference capture is deferred; " +
                "capture an ordinary value copy instead (type '${type.typeName}')"
        )

    /** Only captured locals are read-only; globals retain their ordinary access semantics. */
    fun writeDiagnostic(capture: LambdaCapture): LambdaCaptureDiagnostic? =
        if (capture.isCreationTimeValue) LambdaCaptureRulesAccess.readonlyDiagnostic(capture) else null

    private fun noContract(actual: MCFPPType): LambdaCaptureResult.Rejected = reject(
        LambdaCaptureDiagnosticCode.NO_COPY_CONTRACT,
        "Type '${actual.typeName}' has no capture copy contract: ${reason(actual)}"
    )

    private fun reason(type: MCFPPType): String = when {
        type == MCFPPBaseType.Any || type == MCFPPBaseType.Object ->
            "the erased value has no concrete actual-type evidence; capture a value with a known " +
                "declared type (an 'as' cast is a reinterpretation, not proof)"
        type == MCFPPPrivateType.StaticMemberViewType ->
            "a static member view exposes a reference, not an owned value"
        type == MCFPPPrivateType.Void -> "void is not a value"
        type is MCFPPConcreteType -> "the type has no compiler-readable value model"
        type is MCFPPPrivateType -> "the internal value type has no independent runtime representation"
        !type.hasRuntimeRepresentation -> "the compiler-only value has no complete immutable snapshot"
        else -> "the type declares no independent copy or lifetime contract"
    }

    private fun snapshotType(value: Var<*>, snapshot: CompilerValue): MCFPPType? {
        val typed = snapshot as? CompilerValue.Typed ?: return null
        return StorageAccess.boundTypes(value)[typed.type]
    }

    private fun reject(code: LambdaCaptureDiagnosticCode, message: String) =
        LambdaCaptureResult.Rejected(LambdaCaptureDiagnostic(code, message))
}
