package cn.geelato.search.lucene.config;

import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.event.DeleteEventManager;
import cn.geelato.core.orm.event.SaveEventManager;
import cn.geelato.search.lucene.sync.SearchSyncDeleteListener;
import cn.geelato.search.lucene.sync.SearchSyncSaveListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 装配歧义回归：平台存在 3 个 Dao（primaryDao / dbGenerateDao / dynamicDao）与
 * 2 个 DataSource（primaryDataSource / dynamicDataSource），且均无 @Primary
 * （曾先后报 "required a single bean, but 2/3 were found"），
 * searchJdbcTemplate 必须能自行消解并选中 dynamicDao（回表与业务切库写入同链路）。
 * <p>
 * 装配会向静态 EventManager 注册监听器（上下文关闭不撤销），每个用例结束时手动注销防泄漏。
 */
class GeelatoSearchAutoConfigurationTest {

    @TempDir
    Path tempDir;

    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner();

    private DataSource h2(String name) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    private DataSource routingDataSource(DataSource fallback) {
        AbstractRoutingDataSource ds = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return null; // 无路由 key：走 default（与平台异步线程兜底行为一致）
            }
        };
        ds.setTargetDataSources(Map.of("default", fallback));
        ds.setDefaultTargetDataSource(fallback);
        ds.afterPropertiesSet();
        return ds;
    }

    /** 运行上下文：执行断言，并在结束时注销静态 EventManager 上的监听器。 */
    private void runAndClean(ApplicationContextRunner runner, Consumer<AssertableApplicationContext> assertions) {
        runner.run(context -> {
            try {
                assertions.accept(context);
            } finally {
                if (context.isRunning()) {
                    if (context.containsBean("searchSyncSaveListener")) {
                        SaveEventManager.unregisterAfter(context.getBean(SearchSyncSaveListener.class));
                    }
                    if (context.containsBean("searchSyncDeleteListener")) {
                        DeleteEventManager.unregisterAfter(context.getBean(SearchSyncDeleteListener.class));
                    }
                }
            }
        });
    }

    @Test
    void resolvesJdbcTemplateWithPlatformDaoAndDataSourceTopology() {
        // 完整平台形态：3 个 Dao + 2 个 DataSource，均无 @Primary（复现两次线上报错条件）
        DataSource primary = h2("searchctx_primary");
        DataSource dynamic = routingDataSource(primary);
        JdbcTemplate dynamicJdbcTemplate = new JdbcTemplate(dynamic);
        runAndClean(baseRunner
                .withConfiguration(AutoConfigurations.of(GeelatoSearchAutoConfiguration.class))
                .withPropertyValues("geelato.search.index-root-dir=" + tempDir.resolve("idx"))
                .withBean("primaryDataSource", DataSource.class, () -> primary)
                .withBean("dynamicDataSource", DataSource.class, () -> dynamic)
                .withBean("primaryDao", Dao.class, () -> new Dao(new JdbcTemplate(primary)))
                .withBean("dbGenerateDao", Dao.class, () -> new Dao(new JdbcTemplate(primary)))
                .withBean("dynamicDao", Dao.class, () -> new Dao(dynamicJdbcTemplate)),
                context -> {
                    assertNull(context.getStartupFailure(),
                            () -> "装配失败: " + context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(JdbcTemplate.class).size());
                    // 必须选中 dynamicDao 的 JdbcTemplate（与业务切库写入同链路），而非新造
                    assertSame(dynamicJdbcTemplate, context.getBean(JdbcTemplate.class));
                });
    }

    @Test
    void resolvesJdbcTemplateWhenTwoDataSourcesWithoutPrimary() {
        DataSource primary = h2("searchctx_primary2");
        DataSource dynamic = routingDataSource(primary);
        runAndClean(baseRunner
                .withConfiguration(AutoConfigurations.of(GeelatoSearchAutoConfiguration.class))
                .withPropertyValues("geelato.search.index-root-dir=" + tempDir.resolve("idx2"))
                .withBean("primaryDataSource", DataSource.class, () -> primary)
                .withBean("dynamicDataSource", DataSource.class, () -> dynamic),
                context -> {
                    assertNull(context.getStartupFailure(),
                            () -> "装配失败: " + context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(JdbcTemplate.class).size());
                });
    }

    @Test
    void resolvesJdbcTemplateWithSingleNonRoutingDataSource() {
        runAndClean(baseRunner
                .withConfiguration(AutoConfigurations.of(GeelatoSearchAutoConfiguration.class))
                .withPropertyValues("geelato.search.index-root-dir=" + tempDir.resolve("idx3"))
                .withBean("primaryDataSource", DataSource.class, () -> h2("searchctx_single")),
                context -> {
                    assertNull(context.getStartupFailure(),
                            () -> "装配失败: " + context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(JdbcTemplate.class).size());
                });
    }
}
