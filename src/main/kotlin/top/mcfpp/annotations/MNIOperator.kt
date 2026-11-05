package top.mcfpp.annotations

/** 将公开静态 JVM 方法登记为 MCFPP 类型的运算符实现。 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MNIOperator(

    /** 源码中的运算符符号，例如 `+`。 */
    val operator: String,

    /**
     * 参数类型。如果为空字符串，则表示一个一元运算符
     */
    val paramType: String = "",

    /**
     * 运算结果的 MCFPP 类型。
     */
    val returnType: String,

    /**
     * 额外的标记信息
     */
    val tag: Array<String> = [],

    /**
     * 当参数都是常量时，是否返回常量。默认为false
     */
    val returnsConstWhenArgsConst: Boolean = false,
)
