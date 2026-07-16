package cn.geelato.mqltest;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.orm.Dao;
import cn.geelato.mqltest.support.MysqlContainerITSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 3 层：Testcontainers MySQL 端到端集成测试。
 * <p>
 * 验证 MQL 生成的 SQL 在真实 MySQL 上能正确执行并返回预期结果。
 * <p>
 * 运行方式：{@code mvn verify -pl geelato-mql-test -Pit}（需要 Docker）。
 * 无 Docker 时测试自动跳过（Assumptions.assumeTrue）。
 */
@DisplayName("MQL Testcontainers 端到端集成测试")
class MqlIntegrationIT extends MysqlContainerITSupport {

    @BeforeAll
    static void setupContainer() {
        // 先注册测试实体元数据（父类 @BeforeAll 已注册，但 IT 可能单独运行）
        registerTestModels();
        startMysql();
    }

    @AfterAll
    static void teardown() {
        // 容器由 Testcontainers 自动清理（@Container 或手动 stop）
        // 这里保留容器跨测试方法复用，全部测试结束后由 JVM shutdown hook 清理
    }

    @BeforeEach
    void cleanupData() {
        clearTestData();
    }

    // ==================== 基础 CRUD 端到端 ====================

    @Test
    @DisplayName("插入数据后查询返回正确行数")
    void insertAndQuery() {
        assumeContainerRunning();
        // 插入测试数据
        jdbcTemplate.update(
                "INSERT INTO mql_test_user (id, name, age, login_name, tenant_code) VALUES (?, ?, ?, ?, ?)",
                1L, "张三", 25, "zhangsan", "test"
        );
        jdbcTemplate.update(
                "INSERT INTO mql_test_user (id, name, age, login_name, tenant_code) VALUES (?, ?, ?, ?, ?)",
                2L, "李四", 30, "lisi", "test"
        );

        // MQL 查询
        QueryCommand cmd = parse("{\"mql_test_user\":{}}");
        BoundPageSql bps = generatePageSql(cmd);
        List<Map<String, Object>> rows = dao.queryForMapList(bps);

        assertNotNull(rows);
        assertEquals(2, rows.size());
    }

    @Test
    @DisplayName("eq 操作符真实过滤")
    void eqFilter() {
        assumeContainerRunning();
        insertTestUsers();

        QueryCommand cmd = parse("{\"mql_test_user\":{\"name|eq\":\"张三\"}}");
        BoundPageSql bps = generatePageSql(cmd);
        List<Map<String, Object>> rows = dao.queryForMapList(bps);

        assertEquals(1, rows.size());
        assertEquals("张三", rows.get(0).get("name"));
    }

    @Test
    @DisplayName("gt 操作符真实过滤")
    void gtFilter() {
        assumeContainerRunning();
        insertTestUsers();

        QueryCommand cmd = parse("{\"mql_test_user\":{\"age|gt\":\"25\"}}");
        List<Map<String, Object>> rows = dao.queryForMapList(generatePageSql(cmd));

        assertEquals(1, rows.size());
        assertEquals("李四", rows.get(0).get("name"));
    }

    @Test
    @DisplayName("in 操作符真实过滤")
    void inFilter() {
        assumeContainerRunning();
        insertTestUsers();

        QueryCommand cmd = parse("{\"mql_test_user\":{\"id|in\":\"1,2\"}}");
        List<Map<String, Object>> rows = dao.queryForMapList(generatePageSql(cmd));

        assertEquals(2, rows.size());
    }

    @Test
    @DisplayName("分页查询真实返回正确页")
    void pagination() {
        assumeContainerRunning();
        insertTestUsers();

        // 第1页，每页1条
        QueryCommand cmd = parse("{\"mql_test_user\":{\"@p\":\"1,1\",\"@order\":\"id|+\"}}");
        BoundPageSql bps = generatePageSql(cmd);
        List<Map<String, Object>> rows = dao.queryForMapList(bps);

        assertEquals(1, rows.size());
        assertEquals("张三", rows.get(0).get("name"));

        // count 应为 2
        Long total = dao.queryTotal(bps);
        assertEquals(2L, total);
    }

    @Test
    @DisplayName("contains 模糊匹配真实过滤")
    void containsFilter() {
        assumeContainerRunning();
        insertTestUsers();

        QueryCommand cmd = parse("{\"mql_test_user\":{\"name|contains\":\"张\"}}");
        List<Map<String, Object>> rows = dao.queryForMapList(generatePageSql(cmd));

        assertEquals(1, rows.size());
        assertEquals("张三", rows.get(0).get("name"));
    }

    @Test
    @DisplayName("JOIN 关联查询真实返回关联数据")
    void joinQuery() {
        assumeContainerRunning();
        // 插入 org + user（带 org_id）
        jdbcTemplate.update("INSERT INTO mql_test_org (id, name, code, tenant_code) VALUES (?, ?, ?, ?)",
                1L, "技术部", "TECH", "test");
        jdbcTemplate.update("INSERT INTO mql_test_user (id, name, age, org_id, login_name, tenant_code) VALUES (?, ?, ?, ?, ?, ?)",
                1L, "张三", 25, 1L, "zhangsan", "test");

        // 用 ref 关联查询 org 的 name
        QueryCommand cmd = parse("{\"mql_test_user\":{\"@fs\":\"name,ref(orgId->name) orgName\"}}");
        List<Map<String, Object>> rows = dao.queryForMapList(generatePageSql(cmd));

        assertNotNull(rows);
        assertEquals(1, rows.size());
        assertEquals("张三", rows.get(0).get("name"));
    }

    @Test
    @DisplayName("nil 操作符 IS NULL 真实过滤")
    void nilFilter() {
        assumeContainerRunning();
        // 插入一条 email 为 null 的数据
        jdbcTemplate.update("INSERT INTO mql_test_user (id, name, age, login_name, tenant_code) VALUES (?, ?, ?, ?, ?)",
                1L, "无邮箱", 20, "noemail", "test");

        QueryCommand cmd = parse("{\"mql_test_user\":{\"email|nil\":\"1\"}}");
        List<Map<String, Object>> rows = dao.queryForMapList(generatePageSql(cmd));

        assertEquals(1, rows.size());
        assertEquals("无邮箱", rows.get(0).get("name"));
    }

    // ==================== 辅助方法 ====================

    private void insertTestUsers() {
        jdbcTemplate.update("INSERT INTO mql_test_user (id, name, age, login_name, tenant_code) VALUES (?, ?, ?, ?, ?)",
                1L, "张三", 25, "zhangsan", "test");
        jdbcTemplate.update("INSERT INTO mql_test_user (id, name, age, login_name, tenant_code) VALUES (?, ?, ?, ?, ?)",
                2L, "李四", 30, "lisi", "test");
    }

    /** Docker/容器不可用时跳过而非失败 */
    private void assumeContainerRunning() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                mysql != null && mysql.isRunning() && dao != null,
                "MySQL 容器未运行，跳过端到端测试"
        );
    }
}
