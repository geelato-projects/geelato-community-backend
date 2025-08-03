package cn.geelato.lang.meta;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by hongxq on 2016/5/5.
 */
@Target({ METHOD, FIELD })
@Retention(RUNTIME)
@Documented
public @interface Transient {
}