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
     * 当前仅支持 DAO 执行策略，预留后续扩展其他执行模式。
     */
    private MetaExecutorMode executionMode = MetaExecutorMode.DAO;

    /**
     * 显式指定 MetaCommandExecutor 绑定的 Dao Bean 名称。
     */
    private String daoBeanName;

    /**
     * 默认 ORM 数据源键。
     * 未显式 useDataSource 且实体元数据未声明 connectId 时，回退使用该数据源。
     */
    private String defaultDataSourceKey;

}
