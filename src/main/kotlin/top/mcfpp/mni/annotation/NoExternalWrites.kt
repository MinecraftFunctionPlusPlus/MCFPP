package top.mcfpp.mni.annotation

/** Marks native operations whose writes are limited to temporary storage and results. Input values retain their observable contents. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class NoExternalWrites
