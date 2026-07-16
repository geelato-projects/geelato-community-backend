package cn.geelato.mqltest.support;

import cn.geelato.core.orm.Dao;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;

/**
 * Testcontainers MySQL 集成测试基类。
 * <p>
 * 启动 MySQL 8 容器，自动建表，提供 {@link Dao} 用于执行 MQL 生成的 SQL。
 * <p>
 * 命名约定：继承此类的测试类必须命名为 {@code *IT}（由 failsafe 在 {@code it} profile 下执行）。
 * <p>
 * Docker 不可用时，测试会被跳过（assumeTrue）而非失败，保证无 Docker 的开发环境能正常构建。
 */
public abstract class MysqlContainerITSupport extends MqlTestSupport {

    private static final String MYSQL_VERSION = "mysql:8.0.36";
    private static final String DB_NAME = "mql_test";
    private static final String DB_USER = "mql_test";
    private static final String DB_PASSWORD = "mql_test";

    protected static MySQLContainer<?> mysql;
    protected static JdbcTemplate jdbcTemplate;
    protected static Dao dao;

    /**
     * 启动 MySQL 容器（仅一次），并初始化表结构。
     */
    protected static synchronized void startMysql() {
        if (mysql != null) {
            return;
        }
        Assumptions.assumeTrue(isDockerAvailable(), "Docker 不可用，跳过 MQL 集成测试");
        try {
            mysql = new MySQLContainer<>(MYSQL_VERSION)
                    .withDatabaseName(DB_NAME)
                    .withUsername(DB_USER)
                    .withPassword(DB_PASSWORD)
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
            mysql.start();
            Assumptions.assumeTrue(mysql.isRunning(), "MySQL 容器未能启动");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "MySQL 容器启动失败，跳过集成测试: " + e.getMessage());
        }

        DataSource ds = configureDataSource();
        jdbcTemplate = new JdbcTemplate(ds);
        dao = new Dao(jdbcTemplate);

        // 初始化表结构 + gfn_* 函数
        initSchema();
    }

    /**
     * 创建并配置 DataSource（使用容器提供的 JDBC URL）。
     */
    private static DataSource configureDataSource() {
        org.springframework.jdbc.datasource.DriverManagerDataSource ds =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(mysql.getJdbcUrl());
        ds.setUsername(mysql.getUsername());
        ds.setPassword(mysql.getPassword());
        return ds;
    }

    /**
     * 初始化数据库表结构与自定义函数。
     */
    private static void initSchema() {
        // 注册 gfn_* 自定义函数（MQL 内置函数 increment/findinset/fuzzymatch 依赖）
        jdbcTemplate.execute("CREATE FUNCTION IF NOT EXISTS gfn_increment(val BIGINT, step INT) RETURNS BIGINT "
                + "DETERMINISTIC NO SQL RETURN val + step");

        // 建表 DDL（与测试实体 @Col 注解对应）
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS mql_test_org ("
                + "  id BIGINT PRIMARY KEY,"
                + "  name VARCHAR(128),"
                + "  code VARCHAR(64),"
                + "  pid BIGINT,"
                + "  status VARCHAR(32),"
                + "  create_at DATETIME,"
                + "  creator VARCHAR(64),"
                + "  tenant_code VARCHAR(64)"
                + ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS mql_test_user ("
                + "  id BIGINT PRIMARY KEY,"
                + "  name VARCHAR(128),"
                + "  login_name VARCHAR(64),"
                + "  email VARCHAR(128),"
                + "  mobile_phone VARCHAR(32),"
                + "  age INT,"
                + "  score BIGINT,"
                + "  balance DECIMAL(12,2),"
                + "  birthday DATE,"
                + "  create_at DATETIME,"
                + "  org_id BIGINT,"
                + "  pid BIGINT,"
                + "  enable_status INT,"
                + "  creator VARCHAR(64),"
                + "  tenant_code VARCHAR(64)"
                + ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS mql_test_order ("
                + "  id BIGINT PRIMARY KEY,"
                + "  order_no VARCHAR(64),"
                + "  user_id BIGINT,"
                + "  org_id BIGINT,"
                + "  amount DECIMAL(14,2),"
                + "  quantity INT,"
                + "  status VARCHAR(32),"
                + "  tags JSON,"
                + "  `index` VARCHAR(64),"
                + "  `key` VARCHAR(64),"
                + "  `enable` VARCHAR(32),"
                + "  create_at DATETIME,"
                + "  creator VARCHAR(64),"
                + "  tenant_code VARCHAR(64)"
                + ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS mql_test_order_item ("
                + "  id BIGINT PRIMARY KEY,"
                + "  order_id BIGINT,"
                + "  product_name VARCHAR(128),"
                + "  qty INT,"
                + "  price DECIMAL(12,2),"
                + "  create_at DATETIME,"
                + "  creator VARCHAR(64),"
                + "  tenant_code VARCHAR(64)"
                + ")");
    }

    /**
     * 清空所有测试表数据（每个测试方法前调用，保证隔离）。
     */
    protected void clearTestData() {
        if (jdbcTemplate == null) {
            return;
        }
        jdbcTemplate.execute("DELETE FROM mql_test_order_item");
        jdbcTemplate.execute("DELETE FROM mql_test_order");
        jdbcTemplate.execute("DELETE FROM mql_test_user");
        jdbcTemplate.execute("DELETE FROM mql_test_org");
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }
}
