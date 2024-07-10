package cn.geelato.orm.meta.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by hongxq on 2016/5/5.
 * 如果注解的name为空，则取类名(clazz.getSimpleName())
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
public @interface Entity {
    /**
     * (Optional) The name of the model.
     * Defaults to the class name.
     * @return *
     */
    String name() default "";


    /**
     * (Optional) The name of the entity.
     *
     * Defaults to the model name.
     * @return *
     */
    String table() default "";

    /**
     * (Optional) The catalog of the entity.
     *
     * Defaults to the default catalog.
     * @return *
     */
    String catalog() default "";

    /**
     * (Optional) The schema of the entity.
     *
     * Defaults to the default schema for user.
     * @return *
     */
    String schema() default "";

}