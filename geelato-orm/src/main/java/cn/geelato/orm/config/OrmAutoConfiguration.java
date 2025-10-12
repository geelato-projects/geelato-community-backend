package cn.geelato.orm.config;

import cn.geelato.orm.executor.JdbcTemplateQueryExecutor;
import cn.geelato.orm.executor.QueryExecutor;
import cn.geelato.orm.handler.BaseEntityMetaObjectHandler;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OrmAutoConfiguration {

//    @Bean
//    @ConditionalOnMissingBean
//    public QueryExecutor queryExecutor(JdbcTemplate jdbcTemplate) {
//        return new JdbcTemplateQueryExecutor(jdbcTemplate);
//    }
}