package cn.geelato.core.graal;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GraalService {
    String name();
    String type() default "simple";
    String built() default "false";
    String descrption() default "";
}
