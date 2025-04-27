package top.mcfpp.annotations;

import top.mcfpp.type.MCFPPType;

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
     * 调用者类型。默认为void。仅用于标记，不决定函数的调用者类型
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
     * 当caller是泛型类型的时候，caller需要的泛型参数标记
     */
    String[] genericType() default {};

    /**
     * 额外的标记信息
     */
    String[] tag() default {};

    /**
     * 函数的唯一标识。若为空字符串，则和jvm函数名相同
     */
    String identifier() default "";

}
