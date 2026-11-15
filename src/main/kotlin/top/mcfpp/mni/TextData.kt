package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.StandardTextOperations as TextOps
import top.mcfpp.mni.annotation.NoExternalWrites

/** Explicit factories distinguish value snapshots from live Minecraft text queries. */
@NoExternalWrites
object TextData {
    @JvmStatic @MNIFunction(returnType = "text")
    fun empty(context: NativeCallContext) { context.publishResult(TextOps.empty()) }
    @JvmStatic @MNIFunction(identifier = "of", normalParams = ["string"], returnType = "text")
    fun ofString(context: NativeCallContext) = TextOps.of(context)
    @JvmStatic @MNIFunction(identifier = "of", normalParams = ["int"], returnType = "text")
    fun ofInt(context: NativeCallContext) = TextOps.of(context)
    @JvmStatic @MNIFunction(identifier = "of", normalParams = ["bool"], returnType = "text")
    fun ofBool(context: NativeCallContext) = TextOps.of(context)
    @JvmStatic @MNIFunction(normalParams = ["string"], returnType = "text")
    fun fromJson(context: NativeCallContext) = TextOps.fromJson(context)
    @JvmStatic @MNIFunction(normalParams = ["string"], returnType = "text")
    fun keybind(context: NativeCallContext) = TextOps.component(context, "keybind", listOf("keybind"))
    @JvmStatic @MNIFunction(identifier = "translatable", normalParams = ["string"], returnType = "text")
    fun translateKey(context: NativeCallContext) = TextOps.component(context, "translatable", listOf("translate"))
    @JvmStatic @MNIFunction(identifier = "translatable", normalParams = ["string", "list<text>"], returnType = "text")
    fun translateArgs(context: NativeCallContext) = TextOps.component(context, "translatable", listOf("translate", "with"))
    @JvmStatic @MNIFunction(identifier = "translatable", normalParams = ["string", "list<text>", "string"], returnType = "text")
    fun translateFallback(context: NativeCallContext) = TextOps.component(context, "translatable", listOf("translate", "with", "fallback"))
    @JvmStatic @MNIFunction(identifier = "selector", normalParams = ["string"], returnType = "text")
    fun selector(context: NativeCallContext) = TextOps.component(context, "selector", listOf("selector"))
    @JvmStatic @MNIFunction(identifier = "selector", normalParams = ["string", "text"], returnType = "text")
    fun selectorSeparator(context: NativeCallContext) = TextOps.component(context, "selector", listOf("selector", "separator"))
    @JvmStatic @MNIFunction(normalParams = ["string", "string"], returnType = "text")
    fun score(context: NativeCallContext) = TextOps.score(context)
    @JvmStatic @MNIFunction(normalParams = ["string", "string", "bool"], returnType = "text")
    fun nbtStorage(context: NativeCallContext) = TextOps.nbtStorage(context)
}
