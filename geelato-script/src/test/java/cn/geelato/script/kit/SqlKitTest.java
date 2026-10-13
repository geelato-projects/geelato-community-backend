package cn.geelato.script.kit;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlKitTest {

    private static final DataSource ds = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2).setName("builtin-sql-test").build();
    private static DefaultScriptEngine readonlyEngine;
    private static DefaultScriptEngine dryRunEngine;
    private static DefaultScriptEngine commitEngine;
    private static DefaultScriptEngine noDataSourceEngine;
    private static DefaultScriptEngine rowLimitEngine;

    @BeforeAll
    static void init() {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE IF NOT EXISTS grant_apply (id VARCHAR(32), name VARCHAR(64), amount INT)");
        jdbc.update("DELETE FROM grant_apply");
        jdbc.update("INSERT INTO grant_apply VALUES ('1', 'a', 500)");
        jdbc.update("INSERT INTO grant_apply VALUES ('2', 'b', 50)");
        jdbc.update("INSERT INTO grant_apply VALUES ('3', 'c', 5000)");

        readonlyEngine = builder(false, true).build();
        dryRunEngine = builder(true, true).build();
        commitEngine = builder(true, false).build();
        noDataSourceEngine = new ScriptEngineBuilder().build();
        rowLimitEngine = builder(false, true).sqlRowLimit(1).build();
    }

    private static ScriptEngineBuilder builder(boolean writeEnabled, boolean dryRun) {
        return new ScriptEngineBuilder()
                .dataSourceSupplier(() -> ds)
                .daoWriteEnabled(writeEnabled)
                .daoDryRun(dryRun)
                .profile(ScriptProfile.builder()
                        .id("default").namespaces(Set.of(ScriptProfile.ALL)).variables(Set.of(ScriptProfile.ALL))
                        .defaultTimeoutMs(5_000).build());
    }

    @AfterEach
    void resetData() {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("DELETE FROM grant_apply");
        jdbc.update("INSERT INTO grant_apply VALUES ('1', 'a', 500)");
        jdbc.update("INSERT INTO grant_apply VALUES ('2', 'b', 50)");
        jdbc.update("INSERT INTO grant_apply VALUES ('3', 'c', 5000)");
    }

    @AfterAll
    static void shutdown() {
        readonlyEngine.close();
        dryRunEngine.close();
        commitEngine.close();
        noDataSourceEngine.close();
        rowLimitEngine.close();
    }

    private static ScriptResult run(DefaultScriptEngine engine, String script) {
        return engine.execute(ScriptExecution.builder().content(script).build());
    }

    @Test
    void queryWithNamedParams() {
        ScriptResult result = run(readonlyEngine, "return $gl.sql.query('select id, name from grant_apply where amount > :min order by id', {min: 100})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        List<?> rows = (List<?>) result.getValue();
        assertEquals(2, rows.size());
        assertEquals("1", String.valueOf(((Map<?, ?>) rows.get(0)).get("ID")));
        assertEquals(2L, result.getRowCount());
    }

    @Test
    void rowLimitTruncates() {
        ScriptResult result = run(rowLimitEngine, "return $gl.sql.query('select id from grant_apply order by id', {})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals(1, ((List<?>) result.getValue()).size());
    }

    @Test
    void queryOneAndValue() {
        ScriptResult one = run(readonlyEngine, "return $gl.sql.queryOne('select id from grant_apply where id = :id', {id: '2'})");
        assertEquals("2", String.valueOf(((Map<?, ?>) one.getValue()).get("ID")));
        ScriptResult missing = run(readonlyEngine, "return $gl.sql.queryOne('select id from grant_apply where id = :id', {id: 'none'})");
        assertNull(missing.getValue());
        ScriptResult value = run(readonlyEngine, "return $gl.sql.queryValue('select count(*) from grant_apply', {})");
        assertEquals(3L, ((Number) value.getValue()).longValue());
    }

    @Test
    void updateRejectedWhenWriteDisabled() {
        ScriptResult result = run(readonlyEngine, "return $gl.sql.update('update grant_apply set amount = 0 where id = :id', {id: '1'})");
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("写通道未启用"), result.getError().getMessage());
        assertEquals(500, queryAmount("1"));
    }

    @Test
    void updateDryRunRollsBack() {
        ScriptResult result = run(dryRunEngine, "return $gl.sql.update('update grant_apply set amount = 0 where id = :id', {id: '1'})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        Map<?, ?> out = (Map<?, ?>) result.getValue();
        assertEquals(true, out.get("dryRun"));
        assertEquals(1, ((Number) out.get("affected")).intValue());
        assertEquals(500, queryAmount("1"), "dry-run 后数据必须未变");
    }

    @Test
    void updateCommitsWhenDryRunOff() {
        ScriptResult result = run(commitEngine, "return $gl.sql.update('update grant_apply set amount = 9 where id = :id', {id: '3'})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals(false, ((Map<?, ?>) result.getValue()).get("dryRun"));
        assertEquals(9, queryAmount("3"));
    }

    @Test
    void executeDdlGatedSameAsUpdate() {
        ScriptResult result = run(readonlyEngine, "return $gl.sql.execute('update grant_apply set amount = 1')");
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("写通道未启用"), result.getError().getMessage());
    }

    @Test
    void traceSummaryKeepsFullSql() {
        String longSql = "select id, name from grant_apply where amount > :min"
                + " and name in ('a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z')"
                + " order by id";
        assertTrue(longSql.length() > 150, "前提：SQL 长度远超旧 120 截断");
        ScriptResult result = run(readonlyEngine, "return $gl.sql.query(\"" + longSql + "\", {min: 0})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals(longSql, result.getTrace().get(0).getSummary(), "trace/日志摘要须保留完整 SQL");
    }

    @Test
    void noDataSourceThrowsPrecisely() {
        ScriptResult result = run(noDataSourceEngine, "return $gl.sql.query('select 1', {})");
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("SQL 数据源未配置"), result.getError().getMessage());
    }

    private static int queryAmount(String id) {
        return new JdbcTemplate(ds).queryForObject("select amount from grant_apply where id = ?", Integer.class, id);
    }
}
