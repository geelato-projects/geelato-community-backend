package cn.geelato.lang.meta.annotation;

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


//    /**
//     * (Optional) Unique constraints that are to be placed on
//     * the entity. These are only used if entity generation is in
//     * effect. These constraints apply in addition to any constraints
//     * specified by the <code>Column</code> and <code>JoinColumn</code>
//     * annotations and constraints entailed by primary key mappings.
//     *
//     * Defaults to no additional constraints.
    // * @return *
//     */
//    UniqueConstraint[] uniqueConstraints() default { };
//
//    /**
//     * (Optional) Indexes for the entity. These are only used if entity generation is in effect.  Defaults to no
//     * additional indexes.
//     *
//     * @return The indexes
//     */
//    Index[] indexes() default {};
}