package top.mcfpp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MNIRegister {

    String[] readOnlyParams() default {};
    String[] normalParams() default {};

    String caller() default "void";
    String returnType() default "void";

    boolean override() default false;

}
