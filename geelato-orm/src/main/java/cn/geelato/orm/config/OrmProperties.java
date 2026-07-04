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
     * 显式指定 MetaCommandExecutor 绑定的 Dao Bean 名称。
     */
    private String daoBeanName;

    /**
     * 是否启用 @Entity 自动扫描并注册到 MetaManager。
     */
    private Boolean entityAutoScanEnabled = Boolean.TRUE;

    /**
     * @Entity 自动扫描的 base packages。
     * 为空时将使用 Spring Boot 的 AutoConfigurationPackages。
     */
    private String[] entityScanBasePackages;

}
