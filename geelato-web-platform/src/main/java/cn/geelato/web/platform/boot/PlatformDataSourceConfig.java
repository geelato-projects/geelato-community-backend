package cn.geelato.web.platform.boot;

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
@MapperScan(basePackages = "cn.geelato.web", sqlSessionFactoryRef = "platformSqlSessionFactory")
public class PlatformDataSourceConfig {

    /**
     * 配置Platform模块的SqlSessionFactory
     * @param primaryDataSource 主数据源
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Bean(name = "platformSqlSessionFactory")
    public SqlSessionFactory platformSqlSessionFactory(
            @Qualifier("primaryDataSource") DataSource primaryDataSource) throws Exception {
        MybatisSqlSessionFactoryBean mybatisSqlSessionFactoryBean = new MybatisSqlSessionFactoryBean();
        mybatisSqlSessionFactoryBean.setDataSource(primaryDataSource);
        mybatisSqlSessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/platform/*Mapper.xml"));
        return mybatisSqlSessionFactoryBean.getObject();
    }
}