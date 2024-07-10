package cn.geelato.lang.meta.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 外键
 * Created by liuwq on 2020/3/19.
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface ForeignKey {

    /**
     * @return 外键表实体类.
     */
    Class fTable();

    /**
     * @return 表实体类的外键字段，默认读取实体类的主键.
     */
    String fCol() default "";
}
