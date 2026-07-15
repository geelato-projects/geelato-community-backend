package cn.geelato.orm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ORM 自动装配属性。
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "geelato.orm")
public class OrmProperties {
    /**
     * 元数据执行模式。
     * 默认走 Dao 执行策略，可切换为直接基于 JdbcTemplate 的执行策略。
     */
    private MetaExecutorMode executionMode = MetaExecutorMode.DAO;

    /**
     * 显式指定 MetaCommandExecutor 绑定的 Dao Bean 名称。
     */
    private String daoBeanName;

    /**
     * 显式指定 JDBC_TEMPLATE 模式下绑定的 JdbcTemplate Bean 名称。
     */
    private String jdbcTemplateBeanName;

    /**
     * 默认 ORM 数据源键。
     * 未显式 useDataSource 且实体元数据未声明 connectId 时，回退使用该数据源。
     */
    private String defaultDataSourceKey;

    /**
     * 是否启用 @Entity 自动扫描并注册到 MetaManager。
     */
    private Boolean entityAutoScanEnabled = Boolean.TRUE;

    /**
     * 注解 @Entity 自动扫描的 base packages。
     * 为空时将使用 Spring Boot 的 AutoConfigurationPackages。
     */
    private String[] entityScanBasePackages;

}
