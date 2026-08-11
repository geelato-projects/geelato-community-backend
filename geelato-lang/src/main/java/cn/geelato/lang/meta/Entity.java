package cn.geelato.lang.meta;

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

    /**
     * (Optional) 数据源连接标识，对应动态数据源 key（platform_dev_db_connect 的 id）。
     * <p>
     * 作为实体数据源的显式声明，优先级高于 {@link #catalog()} 的映射。
     * 默认空表示不显式指定，回退到 catalog 映射或数据库登记值（platform_dev_table.connect_id）。
     *
     * @return 数据源 key，默认空（走默认数据源）
     */
    String connectId() default "";
}