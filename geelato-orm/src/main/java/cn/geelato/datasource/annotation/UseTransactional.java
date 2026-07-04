package cn.geelato.datasource.annotation;

import java.lang.annotation.*;

/**
 * 使用事务注解
 * 当Dao字段被此注解标记时，表示该Dao中的所有方法都需要事务支持
 * 系统会自动为该Dao的方法添加@Transactional注解的功能
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseTransactional {
    
    /**
     * 事务传播行为
     * 默认为REQUIRED
     */
    String propagation() default "REQUIRED";
    
    /**
     * 事务隔离级别
     * 默认为DEFAULT（使用数据库默认隔离级别）
     */
    String isolation() default "DEFAULT";
    
    /**
     * 事务超时时间（秒）
     * 默认为-1（使用系统默认超时时间）
     */
    int timeout() default -1;
    
    /**
     * 是否为只读事务
     * 默认为false
     */
    boolean readOnly() default false;
    
    /**
     * 指定哪些异常类型会导致事务回滚
     * 默认为RuntimeException和Error
     */
    Class<? extends Throwable>[] rollbackFor() default {};
    
    /**
     * 指定哪些异常类型不会导致事务回滚
     */
    Class<? extends Throwable>[] noRollbackFor() default {};
    
    /**
     * 指定哪些异常类名会导致事务回滚
     */
    String[] rollbackForClassName() default {};
    
    /**
     * 指定哪些异常类名不会导致事务回滚
     */
    String[] noRollbackForClassName() default {};
}