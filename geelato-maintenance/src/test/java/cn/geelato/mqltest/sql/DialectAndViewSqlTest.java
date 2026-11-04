package cn.geelato.mqltest.sql;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.mqltest.support.MqlSqlAssertions;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static cn.geelato.mqltest.support.MqlSqlAssertions.assertSqlContains;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 2 层：SQL 生成层 —— 多方言引用符、保留字、originalWhere、视图模板。
 */
@DisplayName("MQL SQL 生成：多方言/保留字/视图/权限")
class DialectAndViewSqlTest extends MqlTestSupport {

    // ==================== 多方言引用符 ====================

    @Nested
    @DisplayName("多方言标识符引用")
    class DialectIdentifier {

        @Test
        @DisplayName("MySQL 方言：反引号")
        void mysqlBacktick() {
            // 测试实体 dbType 已强制为 mysql，ORDER BY 使用 appendQuotedIdentifier
            BoundSql sql = generateSql(parse("{\"mql_test_user\":{\"@fs\":\"name\",\"@order\":\"name|+\"}}"));
            // order by 中的列名用反引号
            assertSqlContains("`name`", sql);
        }

        @Test
        @DisplayName("PostgreSQL 方言：双引号")
        void postgresDoubleQuote() {
            BoundSql sql = generateWithDialect("mql_test_user", "postgresql",
                    "{\"@fs\":\"name\",\"@order\":\"name|+\"}");
            assertSqlContains("\"name\"", sql);
        }

        @Test
        @DisplayName("SQL Server 方言：方括号")
        void sqlserverBracket() {
            BoundSql sql = generateWithDialect("mql_test_user", "sqlserver",
                    "{\"@fs\":\"name\",\"@order\":\"name|+\"}");
            assertSqlContains("[name]", sql);
        }

        @Test
        @DisplayName("Oracle 方言：双引号")
        void oracleDoubleQuote() {
            BoundSql sql = generateWithDialect("mql_test_user", "oracle",
                    "{\"@fs\":\"name\",\"@order\":\"name|+\"}");
            assertSqlContains("\"name\"", sql);
        }
    }

    /**
     * 用指定方言生成 SQL（临时覆盖实体的 dbType，不经过 generateSql 的强制 mysql 设置）。
     */
    private BoundSql generateWithDialect(String entityName, String dbType, String mqlBody) {
        EntityMeta em = MetaManager.singleInstance().getByEntityName(entityName);
        em.getTableMeta().setDbType(dbType);
        try {
            QueryCommand cmd = parse("{\"" + entityName + "\":" + mqlBody + "}");
            // 直接用 provider 生成，不经过 generateSql（后者会强制 dbType=mysql）
            return newProvider().generate(cmd);
        } finally {
            // 恢复为 mysql（测试基类的默认约定）
            em.getTableMeta().setDbType("mysql");
        }
    }

    // ==================== 保留字列名 ====================

    @Nested
    @DisplayName("保留字列名")
    class ReservedWordColumn {

        @Test
        @DisplayName("保留字 index 列在 WHERE 中加单引号")
        void reservedIndexInWhere() {
            // index/inner/enable/key 是 keywordsMap 中的保留字，WHERE 中加单引号
            BoundSql sql = generateSql(parse("{\"mql_test_order\":{\"index|eq\":\"val\"}}"));
            // tryAppendKeywords 对保留字加单引号
            assertSqlContains("'index'", sql);
        }

        @Test
        @DisplayName("保留字 key 列在 WHERE 中加单引号")
        void reservedKeyInWhere() {
            BoundSql sql = generateSql(parse("{\"mql_test_order\":{\"key|eq\":\"val\"}}"));
            assertSqlContains("'key'", sql);
        }

        @Test
        @DisplayName("保留字 enable 列在 WHERE 中加单引号")
        void reservedEnableInWhere() {
            BoundSql sql = generateSql(parse("{\"mql_test_order\":{\"enable|eq\":\"val\"}}"));
            assertSqlContains("'enable'", sql);
        }
    }

    // ==================== 视图模板渲染 ====================

    @Nested
    @DisplayName("视图模板 @pf")
    class ViewTemplate {

        @Test
        @DisplayName("视图查询：DEFAULT 视图生成派生表")
        void defaultViewDerivedTable() {
            BoundSql sql = generateSql(parse("{\"mql_test_order_view\":{}}"));
            // 视图应生成 from (viewSql) vt
            assertSqlContains("from (", sql);
            assertSqlContains("mql_test_order", sql);
            assertSqlContains(") vt", sql);
        }

        @Test
        @DisplayName("视图模板参数渲染（statusFilter）")
        void viewTemplateParamRendered() {
            // @pf 不在 JsonTextQueryParser 层处理（由 RuleService 处理），
            // 这里直接构造 QueryCommand 并设置 viewTemplateParams
            QueryCommand cmd = parse("{\"mql_test_order_view\":{}}");
            cmd.setViewTemplateParams(java.util.Map.of("statusFilter", "pending"));
            BoundSql sql = generateSql(cmd);
            // 渲染后应包含 status = 'pending'
            assertSqlContains("status", sql);
            assertSqlContains("pending", sql);
        }

        @Test
        @DisplayName("视图模板空参数段消除")
        void viewTemplateEmptyParamEliminated() {
            QueryCommand cmd = parse("{\"mql_test_order_view\":{}}");
            cmd.setViewTemplateParams(java.util.Collections.emptyMap());
            BoundSql sql = generateSql(cmd);
            // 无参数时，#...{param}...# 段应被消除
            String normalized = MqlSqlAssertions.normalize(sql.getSql());
            assertTrue(normalized.contains("from ("), "应生成派生表: " + normalized);
            assertTrue(!normalized.contains("{statusFilter}"), "未渲染的占位符应被消除: " + normalized);
        }
    }

    // ==================== originalWhere 数据权限 ====================

    @Nested
    @DisplayName("originalWhere 数据权限注入")
    class OriginalWhere {

        @Test
        @DisplayName("originalWhere 拼接到 WHERE 末尾")
        void originalWhereAppended() {
            QueryCommand cmd = parse("{\"mql_test_user\":{\"name|eq\":\"张三\"}}");
            cmd.setOriginalWhere("creator='u1'");
            BoundSql sql = generateSql(cmd);
            assertSqlContains("creator='u1'", sql);
            assertSqlContains("and", sql);
        }

        @Test
        @DisplayName("originalWhere 为 1=1 时原样输出")
        void originalWhereOneEqualsOne() {
            QueryCommand cmd = parse("{\"mql_test_user\":{\"name|eq\":\"张三\"}}");
            cmd.setOriginalWhere("1=1");
            BoundSql sql = generateSql(cmd);
            assertSqlContains("1=1", sql);
        }
    }
}
