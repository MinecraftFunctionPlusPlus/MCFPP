package top.mcfpp.annotations

/** Marks a JVM function as an MCFPP native implementation. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MCFPPNative
