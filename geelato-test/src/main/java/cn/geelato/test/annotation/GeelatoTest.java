package cn.geelato.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Geelato测试注解
 * 用于标注需要进行自动化测试的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GeelatoTest {
    /**
     * 测试描述
     */
    String description() default "";
    
    /**
     * 测试用例文件名
     * 默认为空，会根据类名和方法名自动生成
     */
    String testCaseFile() default "";
    
    /**
     * 是否启用测试
     */
    boolean enabled() default true;
}