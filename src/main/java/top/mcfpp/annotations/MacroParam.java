package top.mcfpp.annotations;

import top.mcfpp.mni.api.ParamMode;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Source type metadata; INLINE uses a signature such as (int, ref int)->void. */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface MacroParam {
    String name();
    String type();
    ParamMode mode() default ParamMode.VALUE;
}
