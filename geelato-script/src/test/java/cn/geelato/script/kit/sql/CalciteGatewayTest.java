package cn.geelato.script.kit.sql;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联邦执行集成测试：两个独立 H2 内存库（模拟跨实例），Calcite 挂 JdbcSchema 做跨源 join。
 * CUSTOMER 不登记（落执行库 local 挂载），CUSTOMER_ORDER 登记到另一实例连接。
 */
class CalciteGatewayTest {

    private static final JdbcDataSource dsA = h2("CALA");
    private static final JdbcDataSource dsB = h2("CALB");
    private static CrossDbSupport support;

    private static JdbcDataSource h2(String db) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    @BeforeAll
    static void init() {
        org.springframework.jdbc.core.JdbcTemplate jdbcA = new org.springframework.jdbc.core.JdbcTemplate(dsA);
        jdbcA.execute("CREATE TABLE IF NOT EXISTS CUSTOMER (ID INT PRIMARY KEY, NAME VARCHAR(64))");
        jdbcA.update("DELETE FROM CUSTOMER");
        jdbcA.update("INSERT INTO CUSTOMER VALUES (1, 'alice')");
        jdbcA.update("INSERT INTO CUSTOMER VALUES (2, 'bob')");

        org.springframework.jdbc.core.JdbcTemplate jdbcB = new org.springframework.jdbc.core.JdbcTemplate(dsB);
        jdbcB.execute("CREATE TABLE IF NOT EXISTS CUSTOMER_ORDER (ID INT PRIMARY KEY, CUSTOMER_ID INT, AMOUNT INT)");
        jdbcB.update("DELETE FROM CUSTOMER_ORDER");
        jdbcB.update("INSERT INTO CUSTOMER_ORDER VALUES (100, 1, 10)");
        jdbcB.update("INSERT INTO CUSTOMER_ORDER VALUES (101, 2, 500)");
        jdbcB.update("INSERT INTO CUSTOMER_ORDER VALUES (102, 1, 900)");

        // CUSTOMER 不登记（local 挂载）；CUSTOMER_ORDER 在另一实例（host hB vs 执行库 host null）
        Map<String, TableLocation> registry = new HashMap<>();
        registry.put("CUSTOMER_ORDER",
                new TableLocation("orderconn", "CALB", "CALB", "hB", 1, "h2"));
        support = new CrossDbSupport(registry::get, connectId -> "orderconn".equals(connectId) ? dsB : null);
    }

    @Test
    void federatedJoinAcrossTwoDatabases() {
        CrossDbSupport.Routed routed = support.route(
                "select o.ID, c.NAME from CUSTOMER_ORDER o join CUSTOMER c on o.CUSTOMER_ID = c.ID"
                        + " where o.AMOUNT > :amt order by o.ID",
                dsA, false);
        assertEquals(CrossDbAnalyzer.Route.FEDERATE, routed.route(), routed.summary());
        List<Map<String, Object>> rows = support.executeFederated(routed, dsA, Map.of("amt", 500), 100, 10_000, "test-exec");
        assertEquals(1, rows.size());
        assertEquals(102, ((Number) rows.get(0).get("ID")).intValue());
        assertEquals("alice", String.valueOf(rows.get(0).get("NAME")));
    }

    @Test
    void federatedAggregateWithPushdownFilter() {
        CrossDbSupport.Routed routed = support.route(
                "select count(*) as CNT, sum(o.AMOUNT) as TOTAL from CUSTOMER_ORDER o where o.AMOUNT > :amt",
                dsA, false);
        assertEquals(CrossDbAnalyzer.Route.FEDERATE, routed.route());
        List<Map<String, Object>> rows = support.executeFederated(routed, dsA, Map.of("amt", 100), 100, 10_000, "test-exec");
        assertEquals(1, rows.size());
        assertEquals(2, ((Number) rows.get(0).get("CNT")).intValue());
        assertEquals(1400, ((Number) rows.get(0).get("TOTAL")).intValue());
    }

    @Test
    void localOnlyQueryStaysDirect() {
        CrossDbSupport.Routed routed = support.route("select NAME from CUSTOMER where ID = :v", dsA, false);
        assertEquals(CrossDbAnalyzer.Route.DIRECT, routed.route(), routed.summary());
        List<Map<String, Object>> rows = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(dsA)
                .queryForList(routed.sql(), Map.of("v", 1));
        assertEquals("alice", String.valueOf(rows.get(0).get("NAME")));
    }

    @Test
    void missingPoolHardFails() {
        Map<String, TableLocation> registry = new HashMap<>();
        registry.put("ORPHAN_T", new TableLocation("orphanconn", "ORPHANDB", "ORPHANDB", "hX", 1, "h2"));
        CrossDbSupport orphan = new CrossDbSupport(registry::get, null);
        CrossDbSupport.Routed routed = orphan.route("select * from ORPHAN_T", dsA, false);
        assertEquals(CrossDbAnalyzer.Route.FEDERATE, routed.route());
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> orphan.executeFederated(routed, dsA, Map.of(), 10, 5_000, "test-exec"));
        assertTrue(e.getMessage().contains("orphanconn"), e.getMessage());
        assertTrue(e.getMessage().contains("无可用连接池"), e.getMessage());
    }
}
