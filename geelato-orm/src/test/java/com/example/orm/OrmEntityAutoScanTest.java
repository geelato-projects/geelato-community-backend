package com.example.orm;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.orm.config.OrmAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

public class OrmEntityAutoScanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrmAutoConfiguration.class))
            .withUserConfiguration(TestApplication.class);

    @Test
    void shouldAutoScanEntityAndRegisterMetadata() {
        contextRunner.run(context -> assertNotNull(MetaManager.singleInstance().get(AutoScanUserEntity.class)));
    }

    @SpringBootApplication
    static class TestApplication {
        @Bean
        Dao primaryDao() {
            return new Dao(mock(JdbcTemplate.class));
        }
    }
}
