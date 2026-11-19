package com.example.orm;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.orm.config.OrmAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

public class OrmEntityAutoScanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrmAutoConfiguration.class))
            .withUserConfiguration(TestApplication.class);

    @Test
    void shouldAutoScanEntityAndRegisterMetadata() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(MetaManager.singleInstance().get(AutoScanUserEntity.class));
        });
    }

    // 仅登记自动配置基础包，不启用全量自动配置（classpath 上的 atomikos 会触发 XADataSourceAutoConfiguration 建 dataSource 失败）
    @Configuration
    @AutoConfigurationPackage
    static class TestApplication {
        @Bean
        Dao primaryDao() {
            return new Dao(mock(JdbcTemplate.class));
        }
    }
}
