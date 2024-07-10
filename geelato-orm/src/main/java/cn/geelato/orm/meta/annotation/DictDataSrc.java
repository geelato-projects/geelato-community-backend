package cn.geelato.orm.meta.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by hongxq on 2016/8/17.
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface DictDataSrc {
    String group();
    String code() default "code";
}
