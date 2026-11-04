package cn.geelato.web.platform.boot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 归档数据源属性（前缀 spring.datasource.archive，键风格对齐 primary/secondary）。
 * <p>配置 jdbc-url 即启用归档数据源；池参数默认值沿用平台同套保活/探活哲学，
 * 可经 application.properties 覆盖。
 */
@Data
@ConfigurationProperties(prefix = "spring.datasource.archive")
public class ArchiveDataSourceProperties {

    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;

    /** 最大连接数 */
    private Integer maximumPoolSize = 10;
    /** 最小空闲连接数 */
    private Integer minimumIdle = 1;
    /** 借连接超时（ms） */
    private Long connectionTimeout = 5000L;
    /** 空闲连接回收超时（ms） */
    private Long idleTimeout = 600000L;
    /** 连接最大存活时间（ms），必须 &lt; 数据库 wait_timeout */
    private Long maxLifetime = 1200000L;
    /** 池内保活探测间隔（ms），定期 ping 空闲连接防止被服务端静默关闭 */
    private Long keepaliveTime = 300000L;
    /** 借出校验超时（ms） */
    private Long validationTimeout = 3000L;
    /** 连接泄漏检测阈值（ms），&gt;0 开启 */
    private Long leakDetectionThreshold = 60000L;
    /** 借出/保活时执行的探活 SQL */
    private String connectionTestQuery = "SELECT 1";
    /** 连接池名称（日志中区分） */
    private String poolName = "ArchivePool";
}
