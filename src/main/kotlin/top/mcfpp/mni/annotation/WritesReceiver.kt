package top.mcfpp.mni.annotation

/** Marks a native operation that writes its receiver and updates the compiler's stored value information. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class WritesReceiver
