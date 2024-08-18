package cn.geelato.web.platform.interceptor.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 
 * rest方法中加了该注解，则不需要进行JWT验证
 * @author geemeta
 */
@Target({METHOD})
@Retention(RUNTIME)
public @interface IgnoreJWTVerify {
}
