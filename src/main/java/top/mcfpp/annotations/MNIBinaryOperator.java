package top.mcfpp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MNIBinaryOperator {

    String operator();

    /**
     * 普通参数。格式是类型+空格+参数名
     */
    String paramType();

    /**
     * 函数的返回类型。默认为void
     */
    String returnType();

    /**
     * 额外的标记信息
     */
    String[] tag() default {};
}
