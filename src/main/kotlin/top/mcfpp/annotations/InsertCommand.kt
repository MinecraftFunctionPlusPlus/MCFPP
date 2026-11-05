package top.mcfpp.annotations

/** Marks a JVM function or constructor as an entry point that inserts generated commands. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class InsertCommand
