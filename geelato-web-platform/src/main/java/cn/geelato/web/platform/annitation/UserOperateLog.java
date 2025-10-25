package cn.geelato.web.platform.annitation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserOperateLog {
    String type();
}