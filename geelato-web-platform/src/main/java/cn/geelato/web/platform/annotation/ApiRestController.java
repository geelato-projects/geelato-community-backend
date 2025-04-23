package cn.geelato.web.platform.annotation;

import cn.geelato.web.platform.constants.MediaTypes;
import cn.geelato.web.platform.conditional.DesigntimeServiceConditional;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
@RequestMapping(produces = MediaTypes.APPLICATION_JSON_UTF_8)
@Conditional(DesigntimeServiceConditional.class)
public @interface ApiRestController {
    @AliasFor(annotation = RequestMapping.class)
    String name() default "";

    @AliasFor(annotation = RequestMapping.class)
    String[] value() default {};

    @AliasFor(annotation = RequestMapping.class)
    String[] path() default {};
}
