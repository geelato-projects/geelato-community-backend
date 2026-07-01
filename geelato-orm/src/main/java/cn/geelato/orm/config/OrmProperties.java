package cn.geelato.orm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ORM 自动装配属性。
 */
@ConfigurationProperties(prefix = "geelato.orm")
public class OrmProperties {
    /**
     * 显式指定 MetaCommandExecutor 绑定的 Dao Bean 名称。
     */
    private String daoBeanName;

    public String getDaoBeanName() {
        return daoBeanName;
    }

    public void setDaoBeanName(String daoBeanName) {
        this.daoBeanName = daoBeanName;
    }
}
