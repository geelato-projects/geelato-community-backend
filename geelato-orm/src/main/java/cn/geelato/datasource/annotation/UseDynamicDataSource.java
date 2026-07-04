package cn.geelato.datasource.annotation;

import java.lang.annotation.*;

/**
 * 动态数据源注解
 * 当类或方法被此注解标记时，表示需要进行动态数据源切换
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseDynamicDataSource {
    
    /**
     * 默认数据源名称
     */
    String value() default "primary";
    
    /**
     * 实体与数据源的映射关系
     */
    EntitySourceMapping[] mappings() default {};
    
    /**
     * 实体与数据源映射配置
     */
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface EntitySourceMapping {
        /**
         * 实体名称
         */
        String entityName();
        
        /**
         * 数据源名称
         */
        String dataSource();
    }
}



