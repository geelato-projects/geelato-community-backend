package cn.geelato.core.meta.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by hongxq on 2015/7/20.
 */
@Target({ElementType.TYPE,METHOD, FIELD})
@Retention(RUNTIME)
@Documented
public @interface Title {
    String title();
    String description() default "";
}
