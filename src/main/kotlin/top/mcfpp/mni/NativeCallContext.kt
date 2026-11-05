package top.mcfpp.mni

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.ValueRef
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor

/**
 * Arguments and compiler state for one native MCFPP function call.
 * Built-in native implementations use this context while the compiler handles the call.
 * @property function MCFPP function receiving the generated commands.
 * @property declaredReturnType MCFPP result type declared by the native signature.
 */
class NativeCallContext internal constructor(
    val function: Function,
    receiver: Var<*>?,
    arguments: List<Var<*>>,
    val declaredReturnType: MCFPPType = MCFPPPrivateType.Void
) {
    private val receiverAdapter = receiver?.let(::normalize)
    private val argumentAdapters = arguments.map(::normalize)
    private val binding = receiverAdapter?.let(StorageAccess::ensure)
    private var publishedResult: Var<*>? = null

    /** Bound argument values in JVM call order. */
    val arguments: List<ValueRef> = argumentAdapters.map(::reference)

    /** The result published by the native implementation, or null before publication. */
    var result: ValueRef? = null
        private set

    /** Instance receiving the call, or null for a global or static call. */
    val receiver: ValueRef?
        get() = receiverAdapter?.let(::reference)

    /** Storage location of the instance receiving the call, when an instance exists. */
    val receiverPlace: Place?
        get() = binding?.place

    /** Instance value known during compilation, or null when its value requires runtime evaluation. */
    val receiverSnapshot: CompilerValue?
        get() = receiverAdapter?.let(StorageAccess::snapshot)

    /** Returns the compile-time value at zero-based [index], or null when the argument value requires runtime evaluation. */
    fun argumentSnapshot(index: Int): CompilerValue? = StorageAccess.snapshot(argumentAdapters[index])

    private fun reference(value: Var<*>): ValueRef = StorageAccess.ensure(value).let {
        it.view ?: ValueRef.Read(value.type.typeId, it.place)
    }

    private fun normalize(value: Var<*>): Var<*> {
        var adapter = if (top.mcfpp.core.lang.context.ContextBorrowing.payload(value) != null) StorageAccess.read(value) else value
        function.runInFunction {
            if (value is BaseBool && value !is ScoreBool) adapter = value.toScoreBool(false)
        }
        return adapter
    }

    /** The legacy domain implementation uses adapters only inside the explicit caller context. */
    internal fun withAdapters(action: (Var<*>, List<Var<*>>) -> Unit) {
        function.runInFunction {
            val receiver = receiverAdapter
            if (receiver == null) {
                LogProcessor.error("Native call has no receiver")
                return@runInFunction
            }
            action(receiver, argumentAdapters)
        }
    }

    internal fun withArguments(action: (List<Var<*>>) -> Unit) {
        function.runInFunction { action(argumentAdapters) }
    }

    internal fun publishResult(value: Var<*>) {
        top.mcfpp.core.lang.context.ContextBorrowing.escapeDiagnostic(value, "a native function return")?.let {
            LogProcessor.error(it)
            return
        }
        val adapter = normalize(value)
        if (adapter.isError) return
        val binding = StorageAccess.ensure(adapter)
        if (binding.data.facts.read(binding.place)?.state != top.mcfpp.analysis.ValueState.INITIALIZED) {
            LogProcessor.error("Native operation did not produce an initialized result '${adapter.identifier}'")
            return
        }
        val reference = reference(adapter)
        result = StorageAccess.snapshot(adapter)?.let { ValueRef.Constant(adapter.type.typeId, it) } ?: reference
        publishedResult = adapter
    }

    internal fun resultAdapter(): Var<*>? = publishedResult

    /** Restores [payload] as the receiver's MCFPP type and writes it through the receiver's storage location. */
    fun writeReceiver(payload: CompilerValue) {
        function.runInFunction {
            val receiver = receiverAdapter
            if (receiver == null) {
                LogProcessor.error("Native call has no receiver to write")
                return@runInFunction
            }
            val stored = StorageAccess.ensure(receiver)
            val value = StorageAccess.restore(
                StorageAccess.actualType(receiver),
                payload,
                receiver.identifier,
                stored.data.types
            )
            if (value == null) {
                LogProcessor.error("Cannot restore native receiver payload as '${StorageAccess.actualType(receiver)}'")
                return@runInFunction
            }
            StorageAccess.write(receiver, value)
        }
    }
}
