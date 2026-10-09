package top.mcfpp.mni

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.ValueRef
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPPrivateType

/** Explicit native call boundary; Var and global StorageAccess remain internal compatibility bridges. */
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

    val arguments: List<ValueRef> = argumentAdapters.map(::reference)

    var result: ValueRef? = null
        private set

    val receiver: ValueRef?
        get() = receiverAdapter?.let(::reference)

    val receiverPlace: Place?
        get() = binding?.place

    val receiverSnapshot: CompilerValue?
        get() = receiverAdapter?.let(StorageAccess::snapshot)

    fun argumentSnapshot(index: Int): CompilerValue? = StorageAccess.snapshot(argumentAdapters[index])

    private fun reference(value: Var<*>): ValueRef = StorageAccess.ensure(value).let {
        it.view ?: ValueRef.Read(value.type.typeId, it.place)
    }

    private fun normalize(value: Var<*>): Var<*> {
        var adapter = value
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

    fun writeReceiver(payload: CompilerValue) {
        function.runInFunction {
            val receiver = receiverAdapter
            if (receiver == null) {
                LogProcessor.error("Native call has no receiver to write")
                return@runInFunction
            }
            val stored = StorageAccess.ensure(receiver)
            val value = StorageAccess.restore(StorageAccess.actualType(receiver), payload,
                receiver.identifier, stored.data.types)
            if (value == null) {
                LogProcessor.error("Cannot restore native receiver payload as '${StorageAccess.actualType(receiver)}'")
                return@runInFunction
            }
            StorageAccess.write(receiver, value)
        }
    }
}
