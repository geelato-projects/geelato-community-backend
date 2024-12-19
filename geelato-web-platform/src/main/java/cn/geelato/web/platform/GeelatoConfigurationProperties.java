package cn.geelato.web.platform;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GeelatoConfigurationProperties {
    String value() default "";
    String tenant() default "";
}
