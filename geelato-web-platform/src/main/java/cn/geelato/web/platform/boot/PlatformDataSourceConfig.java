package cn.geelato.web.platform.boot;

import cn.geelato.orm.handler.BaseEntityMetaObjectHandler;
import cn.geelato.orm.handler.EntityTableNameHandler;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * Platform模块MyBatis配置类
 * 参照MarketDataSourceConfig配置MyBatis SqlSessionFactory
 */
@Configuration
@MapperScan(basePackages = "cn.geelato.web,cn.geelato.meta", sqlSessionFactoryRef = "platformSqlSessionFactory")
public class PlatformDataSourceConfig {

    /**
     * 配置MyBatis-Plus拦截器
     * @return MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 添加动态表名拦截器
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();
        dynamicTableNameInnerInterceptor.setTableNameHandler(new EntityTableNameHandler());
        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
        //分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 配置Platform模块的SqlSessionFactory
     * @param primaryDataSource 主数据源
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Bean(name = "platformSqlSessionFactory")
    public SqlSessionFactory platformSqlSessionFactory(
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            MetaObjectHandler baseEntityMetaObjectHandler) throws Exception {
        MybatisSqlSessionFactoryBean mybatisSqlSessionFactoryBean = new MybatisSqlSessionFactoryBean();
        mybatisSqlSessionFactoryBean.setDataSource(primaryDataSource);
        mybatisSqlSessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/platform/*Mapper.xml"));
        // 设置拦截器
        mybatisSqlSessionFactoryBean.setPlugins(mybatisPlusInterceptor());

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(baseEntityMetaObjectHandler);
        mybatisSqlSessionFactoryBean.setGlobalConfig(globalConfig);
        return mybatisSqlSessionFactoryBean.getObject();
    }
}