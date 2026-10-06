package top.mcfpp.mni

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.ValueRef
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

/** Explicit native call boundary; Var and global StorageAccess remain internal compatibility bridges. */
class NativeCallContext internal constructor(val function: Function, private val receiverAdapter: Var<*>) {
    private val binding = StorageAccess.ensure(receiverAdapter)

    val receiver: ValueRef
        get() = binding.view ?: ValueRef.Read(receiverAdapter.type.typeId, binding.place)

    val receiverPlace: Place
        get() = binding.place

    val receiverSnapshot: CompilerValue?
        get() = StorageAccess.snapshot(receiverAdapter)

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
