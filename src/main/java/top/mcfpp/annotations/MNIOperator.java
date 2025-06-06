package top.mcfpp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MNIOperator {

    String operator();

    /**
     * 参数类型。如果为空字符串，则表示一个一元运算符
     */
    String paramType() default "";

    /**
     * 函数的返回类型。默认为void
     */
    String returnType();

    /**
     * 额外的标记信息
     */
    String[] tag() default {};
}
