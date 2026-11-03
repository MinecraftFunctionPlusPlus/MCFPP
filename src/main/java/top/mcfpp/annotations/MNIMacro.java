package top.mcfpp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Metadata for a public static MNI code-producing entry point. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MNIMacro {
    String identifier() default "";
    MacroParam[] params() default {};
    String returnType() default "void";
}
