package top.mcfpp.annotations

/** Registers a public static JVM method as the getter for an MCFPP property. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MNIAccessor(
    /** Name of the MCFPP property to read. */
    val value: String,
)
