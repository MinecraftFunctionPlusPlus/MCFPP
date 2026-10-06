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

/** Explicit native call boundary; Var and global StorageAccess remain internal compatibility bridges. */
class NativeCallContext internal constructor(
    val function: Function,
    receiver: Var<*>,
    arguments: List<Var<*>>
) {
    private val receiverAdapter = normalize(receiver)
    private val argumentAdapters = arguments.map(::normalize)
    private val binding = StorageAccess.ensure(receiverAdapter)
    private var publishedResult: Var<*>? = null

    val arguments: List<ValueRef> = argumentAdapters.map(::reference)

    var result: ValueRef? = null
        private set

    val receiver: ValueRef
        get() = binding.view ?: ValueRef.Read(receiverAdapter.type.typeId, binding.place)

    val receiverPlace: Place
        get() = binding.place

    val receiverSnapshot: CompilerValue?
        get() = StorageAccess.snapshot(receiverAdapter)

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
        function.runInFunction { action(receiverAdapter, argumentAdapters) }
    }

    internal fun publishResult(value: Var<*>) {
        val adapter = normalize(value)
        val reference = reference(adapter)
        result = StorageAccess.snapshot(adapter)?.let { ValueRef.Constant(adapter.type.typeId, it) } ?: reference
        publishedResult = adapter
    }

    internal fun resultAdapter(): Var<*>? = publishedResult

    fun writeReceiver(payload: CompilerValue) {
        function.runInFunction {
            val value = StorageAccess.restore(StorageAccess.actualType(receiverAdapter), payload,
                receiverAdapter.identifier, binding.data.types)
            if (value == null) {
                LogProcessor.error("Cannot restore native receiver payload as '${StorageAccess.actualType(receiverAdapter)}'")
                return@runInFunction
            }
            StorageAccess.write(receiverAdapter, value)
        }
    }
}
