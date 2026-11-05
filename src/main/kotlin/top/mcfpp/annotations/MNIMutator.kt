package top.mcfpp.annotations

/** 将公开静态 JVM 方法登记为 MCFPP 属性的 setter。 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MNIMutator(

    /**
     * 要写入的 MCFPP 属性名称。
     */
    val value: String,
)
