package cn.geelato.script.kit.builtin;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import cn.geelato.script.kit.sql.CrossDbSupport;
import cn.geelato.script.kit.sql.TableLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * $gl.sql 跨库路由端到端（H2 双 schema 模拟同实例异库）：BIZ_ORDER 登记到 OTHERDB（前缀改写直连执行）、
 * REMOTE_TAB 登记到异实例（查询走联邦/写硬失败）、LOCAL_T 未登记（原样透传）。
 * 经 registerScoped 覆盖内置 sql kit 注入 fake resolver（包级构造器）。
 */
class SqlKitCrossDbTest {

    private static final DataSource ds = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2).setName("builtin-sql-xdb").build();
    private static DefaultScriptEngine dryRunEngine;
    private static DefaultScriptEngine commitEngine;

    @BeforeAll
    static void init() {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS OTHERDB");
        jdbc.execute("CREATE TABLE IF NOT EXISTS OTHERDB.BIZ_ORDER (ID VARCHAR(32), AMOUNT INT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS LOCAL_T (ID VARCHAR(32))");
        resetData();

        Map<String, TableLocation> registry = new HashMap<>();
        // host 双未知视为同实例 → qualifier 不同走前缀改写
        registry.put("BIZ_ORDER", new TableLocation("conn2", "OTHERDB", "OTHERDB", null, null, "h2"));
        // host 已知且与执行库不同 → 联邦
        registry.put("REMOTE_TAB", new TableLocation("conn3", "REMOTEDB", "REMOTEDB", "h9", 1, "h2"));
        CrossDbSupport support = new CrossDbSupport(registry::get, null);

        dryRunEngine = builder(true, true, support).build();
        commitEngine = builder(true, false, support).build();
    }

    private static ScriptEngineBuilder builder(boolean writeEnabled, boolean dryRun, CrossDbSupport support) {
        return new ScriptEngineBuilder()
                .dataSourceSupplier(() -> ds)
                .daoWriteEnabled(writeEnabled)
                .daoDryRun(dryRun)
                .profile(ScriptProfile.builder()
                        .id("default").namespaces(Set.of(ScriptProfile.ALL)).variables(Set.of(ScriptProfile.ALL))
                        .defaultTimeoutMs(15_000).build())
                .registerScoped("sql", SqlKit.class, ctx -> new SqlKit(ctx, support));
    }

    private static void resetData() {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("DELETE FROM OTHERDB.BIZ_ORDER");
        jdbc.update("INSERT INTO OTHERDB.BIZ_ORDER VALUES ('1', 500)");
        jdbc.update("INSERT INTO OTHERDB.BIZ_ORDER VALUES ('2', 50)");
        jdbc.update("INSERT INTO OTHERDB.BIZ_ORDER VALUES ('3', 5000)");
    }

    @AfterEach
    void restoreData() {
        resetData();
    }

    @AfterAll
    static void shutdown() {
        dryRunEngine.close();
        commitEngine.close();
    }

    private static ScriptResult run(DefaultScriptEngine engine, String script) {
        return engine.execute(ScriptExecution.builder().content(script).build());
    }

    private static int amount(String id) {
        return new JdbcTemplate(ds).queryForObject("select AMOUNT from OTHERDB.BIZ_ORDER where ID = ?", Integer.class, id);
    }

    @Test
    void crossSchemaQueryPrefixRewritten() {
        ScriptResult result = run(dryRunEngine,
                "return $gl.sql.query('select ID from BIZ_ORDER where AMOUNT > :m order by ID', {m: 100})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        List<?> rows = (List<?>) result.getValue();
        assertEquals(2, rows.size());
        assertEquals("1", String.valueOf(((Map<?, ?>) rows.get(0)).get("ID")));
        assertTrue(result.getTrace().stream().anyMatch(t -> "route".equals(t.getMethod())
                        && t.getSummary().contains("改写前[") && t.getSummary().contains("改写后[")
                        && t.getSummary().contains("OTHERDB")),
                "改写前后 SQL 须落入 trace: " + result.getTrace());
    }

    @Test
    void crossSchemaWriteDryRunRollsBack() {
        ScriptResult result = run(dryRunEngine,
                "return $gl.sql.update('update BIZ_ORDER set AMOUNT = :a where ID = :id', {a: 0, id: '1'})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals(true, ((Map<?, ?>) result.getValue()).get("dryRun"));
        assertEquals(500, amount("1"), "跨 schema dry-run 后数据必须未变");
    }

    @Test
    void crossSchemaWriteCommitsWhenDryRunOff() {
        ScriptResult result = run(commitEngine,
                "return $gl.sql.update('update BIZ_ORDER set AMOUNT = :a where ID = :id', {a: 9, id: '3'})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals(9, amount("3"));
    }

    @Test
    void crossInstanceWriteHardFails() {
        ScriptResult result = run(commitEngine, "return $gl.sql.update('update REMOTE_TAB set x = 1')");
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("不支持联邦执行"), result.getError().getMessage());
    }

    @Test
    void crossInstanceQueryWithoutPoolHardFails() {
        ScriptResult result = run(dryRunEngine, "return $gl.sql.query('select * from REMOTE_TAB', {})");
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("conn3"), result.getError().getMessage());
    }

    @Test
    void unregisteredTablePassesThrough() {
        ScriptResult result = run(dryRunEngine,
                "return $gl.sql.queryValue('select count(*) from LOCAL_T', {})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
    }
}
