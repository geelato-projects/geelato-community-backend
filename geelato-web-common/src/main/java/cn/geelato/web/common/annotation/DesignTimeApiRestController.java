package cn.geelato.web.common.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiRestController
public @interface DesignTimeApiRestController {
    @AliasFor(annotation = ApiRestController.class)
    String name() default "";

    @AliasFor(annotation = ApiRestController.class)
    String[] value() default {};

    @AliasFor(annotation = ApiRestController.class)
    String[] path() default {};

    @AliasFor(annotation = ApiRestController.class)
    String category() default "platform-design";
}
