package top.mcfpp.annotations

/** Marks a JVM method for registration as an MCFPP member. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MNIMember
