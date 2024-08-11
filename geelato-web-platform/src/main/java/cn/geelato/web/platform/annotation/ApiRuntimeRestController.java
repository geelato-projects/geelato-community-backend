package cn.geelato.web.platform.annotation;

import cn.geelato.web.platform.conditional.RuntimeServiceConditional;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.AliasFor;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiRestController
@Conditional(RuntimeServiceConditional.class)
public @interface ApiRuntimeRestController {
    @AliasFor(annotation = ApiRestController.class)
    String name() default "";

    @AliasFor(annotation = ApiRestController.class)
    String[] value() default {};

    @AliasFor(annotation = ApiRestController.class)
    String[] path() default {};
}
