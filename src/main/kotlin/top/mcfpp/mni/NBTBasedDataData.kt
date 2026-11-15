package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction

/** Compound-copy operations and text conversion for NBT values. */
object NBTBasedDataData {

    /** Returns a compound copy with the argument merged over the receiver. */
    @top.mcfpp.mni.annotation.NoExternalWrites
    @MNIFunction(caller = "nbt", normalParams = ["nbt"], returnType = "nbt")
    @JvmStatic
    fun merge(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val base = requireNotNull(receiver)
        for (value in listOf(base, args.single())) {
            val known = top.mcfpp.analysis.StorageAccess.constantEncoding(value)
            if (known != null && known !is top.mcfpp.nbt.tags.CompoundTag) {
                top.mcfpp.util.LogProcessor.error("nbt.merge requires compound NBT values")
                return@withAdapters
            }
        }
        val result = top.mcfpp.core.lang.nbt.NBTBasedData()
        val path = top.mcfpp.analysis.StorageAccess.ensure(result).path
        top.mcfpp.analysis.StorageAccess.encodeTo(path, base)
        top.mcfpp.analysis.StorageAccess.materialize(args.single())
        top.mcfpp.model.function.Function.addCommand(top.mcfpp.command.Command("data modify")
            .build(path.toCommandPart()).build("merge from")
            .build(top.mcfpp.analysis.StorageAccess.ensure(args.single()).path.toCommandPart()))
        context.publishResult(top.mcfpp.analysis.StorageAccess.publishNbt(result))
    }

    /** Creates a text component that displays the receiver value. */
    @MNIFunction(caller = "nbt", returnType = "text")
    @JvmStatic
    fun toText(context: NativeCallContext) {
        top.mcfpp.backend.NativeTextOperations.nbt(context)
    }

}
