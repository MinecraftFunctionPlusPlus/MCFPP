package top.mcfpp.annotations

/**
 * Describes a public static JVM method that expands an MCFPP macro during compilation.
 * Expose each macro with an explicit MCFPP `func ... = Provider.method` declaration.
 * The method receives MacroContext first. Value, Ref, Const, and InlineFn wrappers determine parameter modes.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class MNIMacro(
    /** Optional descriptor name; explicit MCFPP declarations supply the public name. */
    val identifier: String = "",
    /** MCFPP parameter types in JVM parameter order, excluding MacroContext. */
    val normalParams: Array<String> = [],
    /** MCFPP result type. A void macro returns a Block, and a value macro returns an Expr. */
    val returnType: String = "void",
)
