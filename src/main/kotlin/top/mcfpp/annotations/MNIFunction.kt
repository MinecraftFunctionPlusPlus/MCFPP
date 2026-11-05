package top.mcfpp.annotations

/**
 * 将公开静态 JVM 方法登记为 MCFPP 原生函数的实现。
 * 编译器在处理调用时执行该方法，由方法生成 Minecraft 命令或计算编译期结果。
 * 实现方法可接收 NativeCallContext，由上下文提供参数和结果。
 * 旧包装器签名按只读参数、普通参数、调用者和返回值包装器的顺序接收参数。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MNIFunction(

    /**
     * 只读参数的 MCFPP 类型列表，按调用实参顺序排列。
     */
    val readOnlyParams: Array<String> = [],

    /**
     * 普通参数的 MCFPP 类型列表，按调用实参顺序排列。
     */
    val normalParams: Array<String> = [],

    /**
     * 调用者的类型提示。默认值为 void，实际调用者类型由成员绑定确定。
     */
    val caller: String = "void",

    /**
     * 函数的返回类型。默认为void
     */
    val returnType: String = "void",

    /**
     * 是否重写了父类中的函数。默认为false
     */
    val `override`: Boolean = false,

    /**
     * 签名中使用的泛型参数名称。
     */
    val genericType: Array<String> = [],

    /**
     * 登记函数时附加的标记信息。
     */
    val tag: Array<String> = [],

    /**
     * MCFPP 函数名称。空字符串表示使用 JVM 方法名称。
     */
    val identifier: String = "",

    /**
     * 当参数都是常量时，是否返回常量。默认为false
     */
    val returnsConstWhenArgsConst: Boolean = false,
)
