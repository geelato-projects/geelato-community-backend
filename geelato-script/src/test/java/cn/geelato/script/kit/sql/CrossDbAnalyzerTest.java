package cn.geelato.script.kit.sql;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯单测（无数据库）：掩码/还原、表提取（JOIN/子查询/CTE/DML）、三段分类与前缀改写、透传与硬失败。
 */
class CrossDbAnalyzerTest {

    private static final ExecutingTarget MYSQL_MAIN = new ExecutingTarget("mysql", "h1", 3306, "main");

    private static TableLocation loc(String connectId, String catalog, String host, Integer port, String dbType) {
        return new TableLocation(connectId, catalog, catalog, host, port, dbType);
    }

    private static CrossDbAnalyzer analyzer(Map<String, TableLocation> registry) {
        return new CrossDbAnalyzer(registry::get);
    }

    private static Map<String, TableLocation> defaultRegistry() {
        Map<String, TableLocation> registry = new HashMap<>();
        registry.put("platform_user", loc("primary", "main", "h1", 3306, "mysql"));
        registry.put("biz_order", loc("c2", "bizdb", "h1", 3306, "mysql"));
        registry.put("remote_log", loc("c3", "logdb", "h2", 3306, "mysql"));
        return registry;
    }

    // ==================== NamedParamSupport ====================

    @Test
    void maskSkipsStringsCommentsAndCasts() {
        Optional<NamedParamSupport.Masked> masked = NamedParamSupport.mask(
                "select 'a:b' as c1, \"x:y\" as c2, `t:c` as c3 -- :ignored\n"
                        + ", cast(col AS decimal)::numeric as c4 /* :block */ from t where a = :a and b = :a");
        assertTrue(masked.isPresent());
        assertEquals("??", countMarks(masked.get().sql()));
        assertEquals(2, masked.get().order().size());
        assertEquals("a", masked.get().order().get(0));
        assertEquals("a", masked.get().order().get(1));
        assertTrue(masked.get().sql().contains("'a:b'"));
        assertTrue(masked.get().sql().contains("-- :ignored"));
    }

    @Test
    void maskRejectsBarePositionalParam() {
        assertTrue(NamedParamSupport.mask("select * from t where x = ?").isEmpty());
    }

    @Test
    void restoreRoundTripsInOrder() {
        NamedParamSupport.Masked masked = NamedParamSupport.mask("update t set a = :a, b = :b where c = :a").orElseThrow();
        String rewritten = "UPDATE `x`.`t` SET a = ? , b = ? WHERE c = ?";
        assertEquals("UPDATE `x`.`t` SET a = :a , b = :b WHERE c = :a",
                NamedParamSupport.restore(rewritten, masked.params()));
    }

    @Test
    void maskWrapsIntervalParamInParens() {
        NamedParamSupport.Masked masked = NamedParamSupport.mask(
                "select * from t where eta <= date_add(now(), interval :leadDays day) and x = :x").orElseThrow();
        // INTERVAL 位置参数掩成 (?)，普通参数仍为 ?
        assertTrue(masked.sql().contains("interval (?) day"), masked.sql());
        assertTrue(masked.sql().contains("x = ?"), masked.sql());
        assertEquals(2, masked.params().size());
        assertTrue(masked.params().get(0).interval());
        assertEquals("leadDays", masked.params().get(0).name());
        assertFalse(masked.params().get(1).interval());
        assertEquals("x", masked.params().get(1).name());
    }

    @Test
    void restoreKeepsUserParensAndDropsMaskParens() {
        // 用户自带括号的普通参数（IN (:id)）与 INTERVAL 掩码括号在掩码文本中同为 (?)，按记录区分还原
        String original = "select * from t where id in (:id) and eta <= date_add(now(), interval :d day)";
        NamedParamSupport.Masked masked = NamedParamSupport.mask(original).orElseThrow();
        assertTrue(masked.sql().contains("in (?)"), masked.sql());
        assertEquals(original, NamedParamSupport.restore(masked.sql(), masked.params()));
    }

    @Test
    void intervalParamParsesAndPrefixesLikeRealRule() {
        // EXC-BL-016 场景回归：INTERVAL 命名参数此前导致 Calcite 解析失败、整句透传、跨库改写失效
        Map<String, TableLocation> registry = new HashMap<>();
        registry.put("il_eco", loc("obconn", "ob", "h1", 3306, "mysql"));
        registry.put("il_order_bol", loc("obconn", "ob", "h1", 3306, "mysql"));
        String original = String.join("\n",
                "SELECT 'ORDER' AS biz_type,",
                "         e.id AS biz_id, e.eco_no AS biz_no,",
                "         e.eta AS anchor_time,",
                "         DATEDIFF(e.eta, NOW()) AS lead_days",
                "  FROM il_eco e",
                "  WHERE e.del_status = 0",
                "    AND e.eta IS NOT NULL",
                "    AND e.eta <= DATE_ADD(NOW(), INTERVAL :leadDays DAY)",
                "    AND e.order_status NOT IN ('0','1','15')",
                "    AND NOT EXISTS (",
                "      SELECT 1 FROM il_order_bol b",
                "      WHERE b.order_id = e.id AND b.del_status = 0",
                "        AND b.bol_status IN ('1','2','3','4')",
                "    )");
        CrossDbAnalyzer.Analysis analysis = analyzer(registry).analyze(original, MYSQL_MAIN, false);
        assertTrue(analysis.parseable(), analysis.skipReason());
        assertEquals(CrossDbAnalyzer.Route.PREFIX, analysis.route());
        // 表名加前缀，INTERVAL 参数按原文还原（无掩码括号），其余逐字符保持
        String expected = original.replace("FROM il_eco e", "FROM `ob`.`il_eco` e")
                .replace("FROM il_order_bol b", "FROM `ob`.`il_order_bol` b");
        assertEquals(expected, analysis.prefixSql());
    }

    @Test
    void federatedSqlKeepsIntervalParamPositionalAndOrdered() {
        Map<String, TableLocation> registry = new HashMap<>();
        registry.put("remote_t", loc("c3", "logdb", "h2", 3306, "mysql"));
        CrossDbAnalyzer.Analysis analysis = analyzer(registry).analyze(
                "select a from remote_t where x = :a and eta <= date_add(now(), interval :d day) and z = :c",
                MYSQL_MAIN, false);
        assertEquals(CrossDbAnalyzer.Route.FEDERATE, analysis.route(), analysis.skipReason());
        String federated = analysis.federatedSql();
        // 联邦渲染后 INTERVAL 参数保持 ? 形态，且普通/INTERVAL 参数按掩码顺序出现（bindOrder 前提）
        assertTrue(federated.contains("INTERVAL (?) DAY)"), federated);
        assertEquals(3, federated.chars().filter(ch -> ch == '?').count(), federated);
        assertEquals(java.util.List.of("a", "d", "c"), analysis.masked().order());
    }

    private static String countMarks(String sql) {
        return sql.replaceAll("[^?]", "");
    }

    // ==================== 解析与分类 ====================

    @Test
    void extractAndClassifyJoinSubquery() {
        CrossDbAnalyzer.Analysis analysis = analyzer(defaultRegistry()).analyze(
                "select a.x from platform_user a join biz_order b on a.id = b.uid"
                        + " where b.amt > :m and exists (select 1 from remote_log r where r.uid = a.id)",
                MYSQL_MAIN, false);
        assertTrue(analysis.parseable(), analysis.skipReason());
        assertEquals(CrossDbAnalyzer.Action.LOCAL, analysis.decisions().get("platform_user").action());
        assertEquals(CrossDbAnalyzer.Action.PREFIX, analysis.decisions().get("biz_order").action());
        assertEquals(CrossDbAnalyzer.Action.FEDERATE, analysis.decisions().get("remote_log").action());
        assertEquals(CrossDbAnalyzer.Route.FEDERATE, analysis.route());
    }

    @Test
    void unregisteredTableIsLocal() {
        CrossDbAnalyzer.Analysis analysis = analyzer(defaultRegistry())
                .analyze("select * from not_registered_t where x = :v", MYSQL_MAIN, false);
        assertTrue(analysis.parseable());
        assertEquals(CrossDbAnalyzer.Action.LOCAL, analysis.decisions().get("not_registered_t").action());
        assertNull(analysis.decisions().get("not_registered_t").location());
        assertEquals(CrossDbAnalyzer.Route.DIRECT, analysis.route());
    }

    @Test
    void cteNameShadowsRegisteredTable() {
        CrossDbAnalyzer.Analysis analysis = analyzer(defaultRegistry()).analyze(
                "with biz_order as (select * from platform_user) select * from biz_order where x = :v",
                MYSQL_MAIN, false);
        assertTrue(analysis.parseable());
        assertFalse(analysis.decisions().containsKey("biz_order"), "CTE 别名不得被当成物理表");
        assertEquals(CrossDbAnalyzer.Action.LOCAL, analysis.decisions().get("platform_user").action());
        assertEquals(CrossDbAnalyzer.Route.DIRECT, analysis.route());
    }

    @Test
    void alreadyQualifiedTableSkipped() {
        CrossDbAnalyzer.Analysis analysis = analyzer(defaultRegistry())
                .analyze("select * from otherdb.biz_order", MYSQL_MAIN, false);
        assertTrue(analysis.parseable());
        assertTrue(analysis.decisions().isEmpty());
        assertEquals(CrossDbAnalyzer.Route.DIRECT, analysis.route());
    }

    @Test
    void parseFailureAndMultiStatementPassThrough() {
        CrossDbAnalyzer an = analyzer(defaultRegistry());
        assertFalse(an.analyze("grant all on t to u", MYSQL_MAIN, false).parseable());
        assertFalse(an.analyze("select 1; select 2", MYSQL_MAIN, false).parseable());
        assertFalse(an.analyze("select * from t where x = ?", MYSQL_MAIN, false).parseable());
    }

    @Test
    void nullTargetSkipsAnalysis() {
        assertFalse(analyzer(defaultRegistry()).analyze("select * from t", null, false).parseable());
    }

    // ==================== 前缀改写（原文切片替换） ====================

    @Test
    void prefixSqlQualifiesAndKeepsNamedParams() {
        CrossDbAnalyzer.Analysis analysis = analyzer(defaultRegistry()).analyze(
                "select id from biz_order where amt > :m and name = :n", MYSQL_MAIN, false);
        assertEquals(CrossDbAnalyzer.Route.PREFIX, analysis.route());
        assertEquals("select id from `bizdb`.`biz_order` where amt > :m and name = :n", analysis.prefixSql());
    }

    @Test
    void prefixSqlPreservesEverythingExceptTableName() {
        String original = String.join("\n",
                "select /*+ MAX_EXECUTION_TIME(5000) */ b.id, 'literal:x' as c",
                "from platform_user a -- trailing comment :x",
                "join biz_order b on a.id = b.uid",
                "where b.note = 'it''s' and b.amt > :m",
                "order by b.id limit 5, 10");
        CrossDbAnalyzer.Analysis analysis = analyzer(defaultRegistry()).analyze(original, MYSQL_MAIN, false);
        assertEquals(CrossDbAnalyzer.Route.PREFIX, analysis.route(), analysis.skipReason());
        // 除表名区间外逐字符保持原文：注释/hint/小写关键字/limit 逗号形式/字符串全部不动
        assertEquals(original.replace("biz_order", "`bizdb`.`biz_order`"), analysis.prefixSql());
    }

    @Test
    void prefixSqlReplacesEveryOccurrenceAndKeepsAlias() {
        String original = "select * from biz_order b where b.id in (select uid from biz_order) and b.amt > :v";
        CrossDbAnalyzer.Analysis analysis = analyzer(defaultRegistry()).analyze(original, MYSQL_MAIN, false);
        assertEquals(CrossDbAnalyzer.Route.PREFIX, analysis.route());
        assertEquals(original.replace("biz_order", "`bizdb`.`biz_order`"), analysis.prefixSql());
    }

    @Test
    void prefixSqlCoversDmlTargets() {
        CrossDbAnalyzer analyzer = analyzer(defaultRegistry());
        CrossDbAnalyzer.Analysis insert = analyzer.analyze(
                "insert into biz_order (id) select uid from platform_user where x = :v", MYSQL_MAIN, true);
        assertEquals(CrossDbAnalyzer.Route.PREFIX, insert.route());
        assertEquals("insert into `bizdb`.`biz_order` (id) select uid from platform_user where x = :v", insert.prefixSql());

        CrossDbAnalyzer.Analysis update = analyzer.analyze(
                "update biz_order set amt = :a where id in (select uid from platform_user)", MYSQL_MAIN, true);
        assertEquals(CrossDbAnalyzer.Route.PREFIX, update.route());
        assertEquals("update `bizdb`.`biz_order` set amt = :a where id in (select uid from platform_user)",
                update.prefixSql());

        CrossDbAnalyzer.Analysis delete = analyzer.analyze("delete from remote_log where id = :v", MYSQL_MAIN, true);
        assertEquals(CrossDbAnalyzer.Route.FEDERATE, delete.route());
    }

    @Test
    void pgSameDatabaseDifferentSchemaPrefixes() {
        Map<String, TableLocation> registry = new HashMap<>();
        registry.put("biz_t", new TableLocation("pgc", "appdb", "biz_schema", "h1", 5432, "postgresql"));
        ExecutingTarget pgTarget = new ExecutingTarget("postgresql", "h1", 5432, "appdb");
        CrossDbAnalyzer.Analysis analysis = analyzer(registry).analyze("select * from biz_t where x = :v", pgTarget, false);
        assertEquals(CrossDbAnalyzer.Action.PREFIX, analysis.decisions().get("biz_t").action());
        // PG 纯小写段裸拼，避免引号改变大小写折叠语义
        assertEquals("select * from biz_schema.biz_t where x = :v", analysis.prefixSql());
    }

    @Test
    void pgCrossDatabaseFederates() {
        Map<String, TableLocation> registry = new HashMap<>();
        registry.put("other_db_t", new TableLocation("pgc2", "otherdb", "public", "h1", 5432, "postgresql"));
        ExecutingTarget pgTarget = new ExecutingTarget("postgresql", "h1", 5432, "appdb");
        CrossDbAnalyzer.Analysis analysis = analyzer(registry)
                .analyze("select * from other_db_t where x = :v", pgTarget, false);
        assertEquals(CrossDbAnalyzer.Action.FEDERATE, analysis.decisions().get("other_db_t").action());
    }

    // ==================== 路由门面与写通道硬失败 ====================

    private static DataSource mysqlDs(String url) {
        SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setUrl(url);
        return ds;
    }

    @Test
    void routePrefixOnSameInstanceAndFederateOnCross() {
        CrossDbSupport support = new CrossDbSupport(defaultRegistry()::get, null);
        CrossDbSupport.Routed prefix = support.route(
                "select * from biz_order where amt > :m", mysqlDs("jdbc:mysql://h1:3306/main"), false);
        assertEquals(CrossDbAnalyzer.Route.PREFIX, prefix.route());
        assertEquals("select * from `bizdb`.`biz_order` where amt > :m", prefix.sql());

        CrossDbSupport.Routed federated = support.route(
                "select * from remote_log", mysqlDs("jdbc:mysql://h1:3306/main"), false);
        assertEquals(CrossDbAnalyzer.Route.FEDERATE, federated.route());
        assertEquals("select * from remote_log", federated.sql(), "联邦改写发生在网关内部，路由返回原文");
    }

    @Test
    void federatedWriteHardFailsWithDetails() {
        CrossDbSupport support = new CrossDbSupport(defaultRegistry()::get, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> support.route(
                "update remote_log set x = :v", mysqlDs("jdbc:mysql://h1:3306/main"), true));
        assertTrue(e.getMessage().contains("remote_log"), e.getMessage());
        assertTrue(e.getMessage().contains("h2:3306"), e.getMessage());
        assertTrue(e.getMessage().contains("h1:3306"), e.getMessage());
    }

    @Test
    void routeSkipsWhenTargetUnknown() {
        DataSource opaque = new javax.sql.DataSource() {
            @Override
            public java.sql.Connection getConnection() {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.sql.Connection getConnection(String username, String password) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T unwrap(Class<T> iface) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger() {
                return null;
            }
        };
        CrossDbSupport.Routed routed = new CrossDbSupport(defaultRegistry()::get, null)
                .route("select * from remote_log", opaque, false);
        assertEquals(CrossDbAnalyzer.Route.DIRECT, routed.route());
        assertEquals("select * from remote_log", routed.sql());
        assertTrue(routed.summary().contains("无法识别执行库身份"));
    }

    @Test
    void jdbcUrlParsing() {
        ExecutingTarget mysql = ExecutingTarget.parse("jdbc:mysql://H1:3307/app?useSSL=false");
        assertEquals("mysql", mysql.dbType());
        assertEquals("H1", mysql.host());
        assertEquals(3307, mysql.port());
        assertEquals("app", mysql.db());

        ExecutingTarget pg = ExecutingTarget.parse("jdbc:postgresql://h2:5432/pdb?sslmode=disable");
        assertEquals("postgresql", pg.dbType());
        assertEquals(5432, pg.port());

        ExecutingTarget h2 = ExecutingTarget.parse("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        assertEquals("h2", h2.dbType());
        assertNull(h2.host());
        assertEquals("testdb", h2.db());
    }
}
