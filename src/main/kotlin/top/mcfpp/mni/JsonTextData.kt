package top.mcfpp.mni

import top.mcfpp.annotations.MNIOperator
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeTextOperations
import top.mcfpp.backend.StandardTextOperations as TextOps

/** Implements joining MCFPP text components and strings. */
@top.mcfpp.mni.annotation.NoExternalWrites
object JsonTextData {
    @JvmStatic @MNIFunction(caller = "text", returnType = "string")
    fun toJson(context: NativeCallContext) = TextOps.toJson(context)
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["string"], returnType = "text")
    fun color(context: NativeCallContext) = TextOps.style(context, "color")
    @JvmStatic @MNIFunction(identifier = "color", caller = "text", normalParams = ["int"], returnType = "text")
    fun colorRgb(context: NativeCallContext) = TextOps.colorRgb(context)
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["string"], returnType = "text")
    fun font(context: NativeCallContext) = TextOps.style(context, "font")
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["string"], returnType = "text")
    fun insertion(context: NativeCallContext) = TextOps.style(context, "insertion")
    @JvmStatic @MNIFunction(caller = "text", returnType = "text")
    fun bold(context: NativeCallContext) = TextOps.style(context, "bold", true)
    @JvmStatic @MNIFunction(identifier = "bold", caller = "text", normalParams = ["bool"], returnType = "text")
    fun boldValue(context: NativeCallContext) = TextOps.style(context, "bold")
    @JvmStatic @MNIFunction(caller = "text", returnType = "text")
    fun italic(context: NativeCallContext) = TextOps.style(context, "italic", true)
    @JvmStatic @MNIFunction(identifier = "italic", caller = "text", normalParams = ["bool"], returnType = "text")
    fun italicValue(context: NativeCallContext) = TextOps.style(context, "italic")
    @JvmStatic @MNIFunction(caller = "text", returnType = "text")
    fun underlined(context: NativeCallContext) = TextOps.style(context, "underlined", true)
    @JvmStatic @MNIFunction(identifier = "underlined", caller = "text", normalParams = ["bool"], returnType = "text")
    fun underlinedValue(context: NativeCallContext) = TextOps.style(context, "underlined")
    @JvmStatic @MNIFunction(caller = "text", returnType = "text")
    fun strikethrough(context: NativeCallContext) = TextOps.style(context, "strikethrough", true)
    @JvmStatic @MNIFunction(identifier = "strikethrough", caller = "text", normalParams = ["bool"], returnType = "text")
    fun strikethroughValue(context: NativeCallContext) = TextOps.style(context, "strikethrough")
    @JvmStatic @MNIFunction(caller = "text", returnType = "text")
    fun obfuscated(context: NativeCallContext) = TextOps.style(context, "obfuscated", true)
    @JvmStatic @MNIFunction(identifier = "obfuscated", caller = "text", normalParams = ["bool"], returnType = "text")
    fun obfuscatedValue(context: NativeCallContext) = TextOps.style(context, "obfuscated")
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["text"], returnType = "text")
    fun hover(context: NativeCallContext) = TextOps.hover(context)
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["string"], returnType = "text")
    fun runCommand(context: NativeCallContext) = TextOps.click(context, "run_command")
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["string"], returnType = "text")
    fun suggestCommand(context: NativeCallContext) = TextOps.click(context, "suggest_command")
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["string"], returnType = "text")
    fun openUrl(context: NativeCallContext) = TextOps.click(context, "open_url")
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["string"], returnType = "text")
    fun copyToClipboard(context: NativeCallContext) = TextOps.click(context, "copy_to_clipboard")
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["int"], returnType = "text")
    fun changePage(context: NativeCallContext) = TextOps.click(context, "change_page")
    @JvmStatic @MNIFunction(caller = "text", normalParams = ["text"], returnType = "text")
    fun append(context: NativeCallContext) = NativeTextOperations.plusText(context)
    @JvmStatic @MNIFunction(identifier = "append", caller = "text", normalParams = ["string"], returnType = "text")
    fun appendString(context: NativeCallContext) = NativeTextOperations.plusString(context)
    /** Joins the receiver text with the supplied text components. */
    @MNIOperator(paramType = "text", returnType = "text", operator = "+", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun plusText(context: NativeCallContext) {
        NativeTextOperations.plusText(context)
    }

    /** Appends the supplied string to the receiver text. */
    @MNIOperator(paramType = "string", returnType = "text", operator = "+", returnsConstWhenArgsConst = true)
    @JvmStatic
    fun plusString(context: NativeCallContext) {
        NativeTextOperations.plusString(context)
    }
}
