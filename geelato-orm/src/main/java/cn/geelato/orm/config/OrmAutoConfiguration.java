package cn.geelato.orm.config;

import cn.geelato.orm.interceptor.BaseEntityInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * ORM模块自动配置类
 * 提供MyBatis Plus和BaseEntity拦截器的自动配置
 */
@Configuration
public class OrmAutoConfiguration {

    /**
     * 配置BaseEntity拦截器
     * @return BaseEntityInterceptor
     */
    @Bean
    public BaseEntityInterceptor baseEntityInterceptor() {
        return new BaseEntityInterceptor();
    }

    /**
     * 配置MyBatis Plus拦截器
     * @return MybatisPlusInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

}