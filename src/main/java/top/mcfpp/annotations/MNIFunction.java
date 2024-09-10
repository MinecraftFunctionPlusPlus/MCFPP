package top.mcfpp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MNIFunction {

    /**
     * 只读参数。格式是类型+空格+参数名
     */
    String[] readOnlyParams() default {};

    /**
     * 普通参数。格式是类型+空格+参数名
     */
    String[] normalParams() default {};

    /**
     * 调用者类型。默认为void
     */
    String caller() default "void";

    /**
     * 函数的返回类型。默认为void
     */
    String returnType() default "void";

    /**
     * 是否重写了父类中的函数。默认为false
     */
    boolean override() default false;

    /**
     * 是否是单例对象
     */
    boolean isObject() default false;

}
