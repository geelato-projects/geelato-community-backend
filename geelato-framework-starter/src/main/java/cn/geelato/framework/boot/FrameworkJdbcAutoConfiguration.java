package cn.geelato.framework.boot;

import cn.geelato.core.orm.Dao;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class FrameworkJdbcAutoConfiguration {

    @Bean(name = "primaryDataSourceProperties")
    @ConditionalOnProperty(prefix = "spring.datasource.primary", name = "jdbc-url")
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public PrimaryDataSourceProperties primaryDataSourceProperties() {
        return new PrimaryDataSourceProperties();
    }

    @Bean(name = "primaryDataSource")
    @ConditionalOnMissingBean(name = "primaryDataSource")
    @ConditionalOnProperty(prefix = "spring.datasource.primary", name = "jdbc-url")
    public DataSource primaryDataSource(@Qualifier("primaryDataSourceProperties") PrimaryDataSourceProperties props) {
        HikariDataSource ds = buildHikariDataSource(props);
        applyPoolConfig(ds, props, "PrimaryPool");
        return ds;
    }

    @Bean(name = "primaryJdbcTemplate")
    @ConditionalOnMissingBean(name = "primaryJdbcTemplate")
    @ConditionalOnBean(name = "primaryDataSource")
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "primaryDao")
    @ConditionalOnMissingBean(name = "primaryDao")
    @ConditionalOnBean(name = "primaryJdbcTemplate")
    public Dao primaryDao(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }

    @Bean(name = "dbGenerateDao")
    @ConditionalOnMissingBean(name = "dbGenerateDao")
    @ConditionalOnBean(name = "primaryJdbcTemplate")
    public Dao dbGenerateDao(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }

    @Bean(name = "secondaryDataSourceProperties")
    @ConditionalOnProperty(prefix = "spring.datasource.secondary", name = "jdbc-url")
    @ConfigurationProperties(prefix = "spring.datasource.secondary")
    public SecondaryDataSourceProperties secondaryDataSourceProperties() {
        return new SecondaryDataSourceProperties();
    }

    @Bean(name = "secondaryDataSource")
    @ConditionalOnMissingBean(name = "secondaryDataSource")
    @ConditionalOnProperty(prefix = "spring.datasource.secondary", name = "jdbc-url")
    public DataSource secondaryDataSource(@Qualifier("secondaryDataSourceProperties") SecondaryDataSourceProperties props) {
        HikariDataSource ds = buildHikariDataSource(props);
        applyPoolConfig(ds, props, "SecondaryPool");
        return ds;
    }

    @Bean(name = "secondaryJdbcTemplate")
    @ConditionalOnMissingBean(name = "secondaryJdbcTemplate")
    @ConditionalOnBean(name = "secondaryDataSource")
    public JdbcTemplate secondaryJdbcTemplate(@Qualifier("secondaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "secondaryDao")
    @ConditionalOnMissingBean(name = "secondaryDao")
    @ConditionalOnBean(name = "secondaryJdbcTemplate")
    public Dao secondaryDao(@Qualifier("secondaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }

    // ======================== 内部方法 ========================

    /**
     * 用连接参数构造 HikariDataSource（连接字段来自属性类）。
     * <p>直接 {@code new HikariDataSource()} 再 set 字段，与 {@code DataSourceFactory} 保持一致，
     * 避免 {@code DataSourceBuilder} 的泛型/类型推断差异。</p>
     */
    private static HikariDataSource buildHikariDataSource(AbstractDataSourceProperties props) {
        HikariDataSource ds = new HikariDataSource();
        if (props.getDriverClassName() != null) {
            ds.setDriverClassName(props.getDriverClassName());
        }
        if (props.getJdbcUrl() != null) {
            ds.setJdbcUrl(props.getJdbcUrl());
        }
        if (props.getUsername() != null) {
            ds.setUsername(props.getUsername());
        }
        if (props.getPassword() != null) {
            ds.setPassword(props.getPassword());
        }
        return ds;
    }

    /**
     * 把连接池调优参数显式应用到 HikariDataSource。
     * <p>属性类已带合理默认值（见 {@link PoolDefaults}），用户可通过 application.properties
     * 的 {@code spring.datasource.primary.*} / {@code spring.datasource.secondary.*} 覆盖。</p>
     * <p>关键：默认开启 keepalive（保活探测）与 connectionTestQuery，避免云数据库/SLB
     * 静默关闭空闲连接后，池把死连接借出导致
     * "No operations allowed after connection closed"。</p>
     */
    private static void applyPoolConfig(HikariDataSource ds, PoolDefaults p, String defaultPoolName) {
        if (p.getMaximumPoolSize() != null) {
            ds.setMaximumPoolSize(p.getMaximumPoolSize());
        }
        if (p.getMinimumIdle() != null) {
            ds.setMinimumIdle(p.getMinimumIdle());
        }
        if (p.getConnectionTimeout() != null) {
            ds.setConnectionTimeout(p.getConnectionTimeout());
        }
        if (p.getIdleTimeout() != null) {
            ds.setIdleTimeout(p.getIdleTimeout());
        }
        if (p.getMaxLifetime() != null) {
            ds.setMaxLifetime(p.getMaxLifetime());
        }
        if (p.getKeepaliveTime() != null) {
            ds.setKeepaliveTime(p.getKeepaliveTime());
        }
        if (p.getValidationTimeout() != null) {
            ds.setValidationTimeout(p.getValidationTimeout());
        }
        if (p.getLeakDetectionThreshold() != null) {
            ds.setLeakDetectionThreshold(p.getLeakDetectionThreshold());
        }
        if (p.getConnectionTestQuery() != null && !p.getConnectionTestQuery().trim().isEmpty()) {
            ds.setConnectionTestQuery(p.getConnectionTestQuery());
        }
        ds.setPoolName(p.getPoolName() != null && !p.getPoolName().trim().isEmpty() ? p.getPoolName() : defaultPoolName);
    }

    // ======================== 属性类 ========================

    /**
     * 连接池调优参数（含默认值）。
     * <p>默认值针对云数据库（如阿里云 OceanBase/RDS）+ 公网经 SLB 场景调优，
     * 要求 maxLifetime 严格小于数据库 wait_timeout（默认 28800s）。</p>
     */
    public static class PoolDefaults {
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
        /** 池内保活探测间隔（ms），开启后定期 ping 空闲连接，防止被服务端静默关闭 */
        private Long keepaliveTime = 300000L;
        /** 借出校验超时（ms） */
        private Long validationTimeout = 3000L;
        /** 连接泄漏检测阈值（ms），&gt;0 开启 */
        private Long leakDetectionThreshold = 60000L;
        /** 借出/保活时执行的探活 SQL */
        private String connectionTestQuery = "SELECT 1";
        /** 连接池名称（日志中区分） */
        private String poolName;

        public Integer getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(Integer maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
        public Integer getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(Integer minimumIdle) { this.minimumIdle = minimumIdle; }
        public Long getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(Long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        public Long getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(Long idleTimeout) { this.idleTimeout = idleTimeout; }
        public Long getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(Long maxLifetime) { this.maxLifetime = maxLifetime; }
        public Long getKeepaliveTime() { return keepaliveTime; }
        public void setKeepaliveTime(Long keepaliveTime) { this.keepaliveTime = keepaliveTime; }
        public Long getValidationTimeout() { return validationTimeout; }
        public void setValidationTimeout(Long validationTimeout) { this.validationTimeout = validationTimeout; }
        public Long getLeakDetectionThreshold() { return leakDetectionThreshold; }
        public void setLeakDetectionThreshold(Long leakDetectionThreshold) { this.leakDetectionThreshold = leakDetectionThreshold; }
        public String getConnectionTestQuery() { return connectionTestQuery; }
        public void setConnectionTestQuery(String connectionTestQuery) { this.connectionTestQuery = connectionTestQuery; }
        public String getPoolName() { return poolName; }
        public void setPoolName(String poolName) { this.poolName = poolName; }
    }

    /**
     * 数据源属性基类：连接字段 + 连接池调优字段（带默认值）。
     * 前缀由子类通过 {@code @ConfigurationProperties} 指定。
     */
    public abstract static class AbstractDataSourceProperties extends PoolDefaults {
        private String name;
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    }

    /** 主数据源属性（前缀 spring.datasource.primary） */
    public static class PrimaryDataSourceProperties extends AbstractDataSourceProperties {
    }

    /** 次数据源属性（前缀 spring.datasource.secondary） */
    public static class SecondaryDataSourceProperties extends AbstractDataSourceProperties {
    }
}
