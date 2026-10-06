package top.mcfpp.mni

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.ValueRef
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

/** Explicit native call boundary; Var and global StorageAccess remain internal compatibility bridges. */
class NativeCallContext internal constructor(
    val function: Function,
    private val receiverAdapter: Var<*>,
    private val argumentAdapters: List<Var<*>>
) {
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

    /** The legacy domain implementation uses adapters only inside the explicit caller context. */
    internal fun withAdapters(action: (Var<*>, List<Var<*>>) -> Unit) {
        function.runInFunction { action(receiverAdapter, argumentAdapters) }
    }

    internal fun publishResult(value: Var<*>) {
        val reference = reference(value)
        result = StorageAccess.snapshot(value)?.let { ValueRef.Constant(value.type.typeId, it) } ?: reference
        publishedResult = value
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
