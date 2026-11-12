package cn.geelato.web.platform.srv.mv;

import cn.geelato.web.platform.srv.mv.engine.MaterializedViewEngine;
import cn.geelato.web.platform.srv.mv.engine.risingwave.RisingWaveDdlParser;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RisingWaveDdlParserTest {

    private static final Collection<String> CDC_TABLES = List.of("gl_1_db_orders");

    private static boolean allPassed(List<MaterializedViewEngine.CheckResult> items) {
        return items.stream().allMatch(MaterializedViewEngine.CheckResult::passed);
    }

    @Test
    void parseCdcTableDdlWithSnapshotClause() {
        String ddl = "CREATE TABLE IF NOT EXISTS \"gl_1_db_orders\" (\"id\" bigint, PRIMARY KEY (\"id\")) "
                + "FROM \"gl_1_src\" TABLE 'shop.orders' WITH ( snapshot = 'true' )";
        RisingWaveDdlParser.CdcTableDdlParsed parsed = RisingWaveDdlParser.parseCdcTableDdl(ddl);
        assertEquals("gl_1_db_orders", parsed.tableName());
        assertEquals("gl_1_src", parsed.sourceName());
        assertEquals("shop", parsed.sourceDatabase());
        assertEquals("orders", parsed.sourceTable());
        assertEquals(List.of("id"), parsed.primaryKeys());
    }

    @Test
    void parseCdcTableDdlWithoutWithClauseStillWorks() {
        String ddl = "CREATE TABLE IF NOT EXISTS \"gl_1_db_orders\" (\"id\" bigint, PRIMARY KEY (\"id\")) "
                + "FROM \"gl_1_src\" TABLE 'shop.orders'";
        RisingWaveDdlParser.CdcTableDdlParsed parsed = RisingWaveDdlParser.parseCdcTableDdl(ddl);
        assertEquals("orders", parsed.sourceTable());
    }

    @Test
    void parseCdcTableDdlRejectsInvalidSnapshotValue() {
        String ddl = "CREATE TABLE IF NOT EXISTS \"gl_1_db_orders\" (\"id\" bigint, PRIMARY KEY (\"id\")) "
                + "WITH ( snapshot = 'yes' ) FROM \"gl_1_src\" TABLE 'shop.orders'";
        assertThrows(RisingWaveDdlParser.DdlParseException.class, () -> RisingWaveDdlParser.parseCdcTableDdl(ddl));
    }

    @Test
    void parseMvDdlWithSourceRateLimit() {
        String ddl = "CREATE MATERIALIZED VIEW \"mv_orders\" WITH ( source_rate_limit = 50000 ) "
                + "AS SELECT id FROM gl_1_db_orders";
        RisingWaveDdlParser.MvDdlParsed parsed = RisingWaveDdlParser.parseMvDdl(ddl);
        assertEquals("mv_orders", parsed.viewName());
        assertEquals("SELECT id FROM gl_1_db_orders", parsed.body());
    }

    @Test
    void parseMvDdlWithoutWithClauseStillWorks() {
        RisingWaveDdlParser.MvDdlParsed parsed = RisingWaveDdlParser.parseMvDdl(
                "CREATE MATERIALIZED VIEW \"mv_orders\" AS SELECT id FROM gl_1_db_orders");
        assertEquals("SELECT id FROM gl_1_db_orders", parsed.body());
    }

    @Test
    void validateSourceAcceptsServerId() {
        String ddl = "CREATE SOURCE IF NOT EXISTS \"gl_1_src\" WITH (\n"
                + "  connector = 'mysql-cdc',\n"
                + "  hostname = '10.0.0.1',\n"
                + "  port = '3306',\n"
                + "  username = 'rw',\n"
                + "  password = '******',\n"
                + "  database.name = 'shop',\n"
                + "  server.id = '101000'\n"
                + ")";
        List<MaterializedViewEngine.CheckResult> items = RisingWaveDdlParser.validateSource(ddl, "gl_1_src", "shop");
        assertTrue(allPassed(items));
        assertTrue(items.stream().anyMatch(i -> "source-server-id".equals(i.item()) && i.passed()));
    }

    @Test
    void validateSourceRejectsNonNumericServerId() {
        String ddl = "CREATE SOURCE IF NOT EXISTS \"gl_1_src\" WITH (\n"
                + "  connector = 'mysql-cdc',\n"
                + "  hostname = '10.0.0.1',\n"
                + "  port = '3306',\n"
                + "  username = 'rw',\n"
                + "  password = '******',\n"
                + "  database.name = 'shop',\n"
                + "  server.id = 'abc'\n"
                + ")";
        List<MaterializedViewEngine.CheckResult> items = RisingWaveDdlParser.validateSource(ddl, "gl_1_src", "shop");
        assertTrue(items.stream().anyMatch(i -> "source-server-id".equals(i.item()) && !i.passed()));
    }

    @Test
    void validateMvAcceptsWithClauseDdl() {
        String ddl = "CREATE MATERIALIZED VIEW \"mv_orders\" WITH ( source_rate_limit = 50000 ) "
                + "AS SELECT id FROM gl_1_db_orders";
        List<MaterializedViewEngine.CheckResult> items = RisingWaveDdlParser.validateMv(ddl, "mv_orders", CDC_TABLES);
        assertTrue(allPassed(items));
    }

    @Test
    void validateCdcTableAcceptsSnapshotClauseDdl() {
        String ddl = "CREATE TABLE IF NOT EXISTS \"gl_1_db_orders\" (\"id\" bigint, PRIMARY KEY (\"id\")) "
                + "WITH ( snapshot = 'false' ) FROM \"gl_1_src\" TABLE 'shop.orders'";
        List<MaterializedViewEngine.CheckResult> items = RisingWaveDdlParser.validateCdcTable(
                ddl, "gl_1_db_orders", "gl_1_src", "shop", "orders", List.of("id"));
        assertTrue(allPassed(items));
    }
}
