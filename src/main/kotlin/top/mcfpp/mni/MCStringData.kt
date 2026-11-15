package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction

/** Implements conversion from an MCFPP string to a text component. */
@top.mcfpp.mni.annotation.NoExternalWrites
object MCStringData {
    /** 拼接字符串，保留所有 UTF-16 单元和转义字符。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = ["string"], returnType = "string")
    fun contact(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.contact(context)
    /** 编译期字符串操作：contains。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = ["string"], returnType = "bool")
    fun contains(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.constant(context, "contains")
    /** 编译期字符串操作：startsWith。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = ["string"], returnType = "bool")
    fun startsWith(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.constant(context, "startsWith")
    /** 编译期字符串操作：endsWith。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = ["string"], returnType = "bool")
    fun endsWith(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.constant(context, "endsWith")
    /** 编译期字符串操作：indexOf。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = ["string"], returnType = "int")
    fun indexOf(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.constant(context, "indexOf")
    /** 编译期字符串操作：replace。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = ["string", "string"], returnType = "string")
    fun replace(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.constant(context, "replace")
    /** 编译期字符串操作：split。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = ["string"], returnType = "list<string>")
    fun split(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.constant(context, "split")
    /** 编译期字符串操作：trim。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = [], returnType = "string")
    fun trim(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.constant(context, "trim")
    /** 编译期字符串操作：lowercase。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = [], returnType = "string")
    fun lowercase(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.constant(context, "lowercase")
    /** 编译期字符串操作：uppercase。 */
    @JvmStatic @MNIFunction(caller = "string", normalParams = [], returnType = "string")
    fun uppercase(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.constant(context, "uppercase")
    @JvmStatic @MNIFunction(caller = "string", returnType = "int")
    fun length(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.length(context)
    @JvmStatic @MNIFunction(caller = "string", returnType = "bool")
    fun isEmpty(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.length(context, true)
    @JvmStatic @MNIFunction(caller = "string", returnType = "bool")
    fun isNotEmpty(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.length(context, false)
    @JvmStatic @MNIFunction(caller = "string", normalParams = ["int", "int"], returnType = "string")
    fun substring(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.substring(context)
    @JvmStatic @top.mcfpp.annotations.MNIOperator(operator = "==", paramType = "string", returnType = "bool")
    fun equalsString(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.equal(context, false)
    @JvmStatic @top.mcfpp.annotations.MNIOperator(operator = "!=", paramType = "string", returnType = "bool")
    fun notEqualsString(context: NativeCallContext) = top.mcfpp.backend.StandardStringOperations.equal(context, true)

    /** Creates a text component that displays the receiver value. */
    @MNIFunction(caller = "string", returnType = "text", `override` = true)
    @JvmStatic
    fun toText(context: NativeCallContext) {
        top.mcfpp.backend.NativeTextOperations.string(context)
    }
}
