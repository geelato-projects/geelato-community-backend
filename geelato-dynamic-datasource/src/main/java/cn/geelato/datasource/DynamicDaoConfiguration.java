package cn.geelato.datasource;

import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.BaseDao;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dynamic Dao配置类
 * 负责配置各种Dynamic Dao实例，专门用于@UseDynamicDataSource注解的字段注入
 */
@Configuration
public class DynamicDaoConfiguration {
    
    /**
     * 动态数据访问对象
     * 使用动态数据源进行数据库操作
     * 专门用于@UseDynamicDataSource注解的Dao字段注入
     */
    @Bean(name = "dynamicDao")
    public Dao dynamicDao(@Qualifier("dynamicJdbcTemplate") JdbcTemplate dynamicJdbcTemplate) {
        return new Dao(dynamicJdbcTemplate);
    }
    

}