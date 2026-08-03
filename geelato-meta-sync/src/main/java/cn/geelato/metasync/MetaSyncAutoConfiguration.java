package cn.geelato.metasync;

import cn.geelato.metasync.api.MetaSyncApiController;
import cn.geelato.metasync.core.MetaSourceLoader;
import cn.geelato.metasync.fix.JavaSourceWriter;
import cn.geelato.metasync.fix.JavaToMetaFixer;
import cn.geelato.metasync.fix.TableToMetaFixer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import cn.geelato.core.orm.Dao;

/**
 * 实体三者同步校验工具自动配置。
 * <p>
 * 仅在 {@code geelato.meta.sync.enabled=true} 时装配。
 * 默认关闭，生产环境保持 false。开启后可访问 http://host:port/meta-sync.html。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "geelato.meta.sync.enabled", havingValue = "true")
@Import({MetaSyncPageController.class, MetaSyncApiController.class})
public class MetaSyncAutoConfiguration {

    @org.springframework.context.annotation.Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public MetaSourceLoader metaSourceLoader(@Qualifier("primaryDao") Dao dao) {
        return new MetaSourceLoader(dao);
    }

    @org.springframework.context.annotation.Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public cn.geelato.metasync.core.ConsistencyChecker consistencyChecker(MetaSourceLoader metaSourceLoader) {
        return new cn.geelato.metasync.core.ConsistencyChecker(metaSourceLoader);
    }

    @org.springframework.context.annotation.Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public TableToMetaFixer tableToMetaFixer(@Qualifier("primaryDao") Dao dao) {
        return new TableToMetaFixer(dao);
    }

    @org.springframework.context.annotation.Bean
    public JavaSourceWriter javaSourceWriter() {
        return new JavaSourceWriter();
    }

    @org.springframework.context.annotation.Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public JavaToMetaFixer javaToMetaFixer(@Qualifier("primaryDao") Dao dao, MetaSourceLoader metaSourceLoader) {
        return new JavaToMetaFixer(dao, metaSourceLoader);
    }
}
