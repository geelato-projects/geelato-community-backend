package cn.geelato.mqltest.mql;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.mql.MqlQueryProcessor;
import cn.geelato.core.orm.PageTotalStrategy;
import cn.geelato.mqltest.support.MysqlContainerITSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页 total 可推导跳过的等价性 IT（需 Docker，MySQL 8 容器）。
 * <p>
 * 对同一批真实数据执行查询矩阵，逐条验证两件事：
 * <ol>
 *   <li><b>等价性（硬验证）</b>：按 {@link PageTotalStrategy} 判定走"派生 total = 行数"或
 *       "执行 count"两条路径，其结果必须与无条件执行 count 的真实总数完全一致；</li>
 *   <li><b>行为断言</b>：可推导场景（全量/主键等值/首页未满）确实跳过 count，
 *       不可推导场景（首页取满/非首页）确实执行 count。</li>
 * </ol>
 * Docker 不可用时跳过（{@link MysqlContainerITSupport#startMysql()} 内 assumeTrue）。
 */
@DisplayName("分页 total 可推导跳过：与真实 count 等价性")
class PageTotalDerivationEquivalenceIT extends MysqlContainerITSupport {

    private static final int TOTAL_ROWS = 7;

    @BeforeAll
    static void prepare() {
        startMysql();
        jdbcTemplate.update("DELETE FROM mql_test_order");
        for (int i = 1; i <= TOTAL_ROWS; i++) {
            jdbcTemplate.update(
                    "INSERT INTO mql_test_order (id, order_no, status) VALUES (?, ?, 'done')",
                    i, "SO00" + i);
        }
    }

    /** 执行一条 MQL，返回 [行数, 派生路径total, 真实count, 是否跳过count]。 */
    private Object[] execute(String mql) {
        getEntityMetaWithMySqlType(ENTITY_ORDER);
        MqlQueryProcessor.ProcessedQuery pq = MqlQueryProcessor.getInstance().process(mql);
        List<Map<String, Object>> rows = dao.queryForMapList(pq.getBoundPageSql());
        long realCount = dao.queryTotal(pq.getBoundPageSql());
        boolean bounded = PageTotalStrategy.uniqueKeyBounded(pq.getCommand(), MetaManager.singleInstance());
        boolean derivable = PageTotalStrategy.totalDerivable(bounded, pq.getCommand(), rows.size());
        long resolvedTotal = derivable ? rows.size() : realCount;
        return new Object[]{rows.size(), resolvedTotal, realCount, derivable};
    }

    private void assertEquivalent(String mql, boolean expectDerivable) {
        Object[] r = execute(mql);
        boolean derivable = (Boolean) r[3];
        assertEquals(expectDerivable, derivable,
                "count 跳过判定与预期不符（rows=" + r[0] + "）: " + mql);
        assertEquals((Long) r[2], (Long) r[1],
                "派生/执行 count 的 total 必须与真实总数一致（rows=" + r[0] + "）: " + mql);
    }

    @Test
    @DisplayName("全量查询（未传 @p）：跳过 count，total=行数=7")
    void nonPagingDerivesFromRows() {
        assertEquivalent("{\"" + ENTITY_ORDER + "\":{\"@fs\":\"id\"}}", true);
    }

    @Test
    @DisplayName("主键等值 + @p=1,1：跳过 count，total=1")
    void idEqSinglePageDerives() {
        assertEquivalent("{\"" + ENTITY_ORDER + "\":{\"id\":\"3\",\"@p\":\"1,1\"}}", true);
    }

    @Test
    @DisplayName("主键等值命中 0 行：跳过 count，total=0")
    void idEqNoRowDerives() {
        assertEquivalent("{\"" + ENTITY_ORDER + "\":{\"id\":\"999\",\"@p\":\"1,1\"}}", true);
    }

    @Test
    @DisplayName("首页未满（@p=1,10 共 7 行）：跳过 count，total=7")
    void firstPageUnderfullDerives() {
        assertEquivalent("{\"" + ENTITY_ORDER + "\":{\"@p\":\"1,10\"}}", true);
    }

    @Test
    @DisplayName("主键 in + 首页未满（@p=1,10 in 2 个值）：跳过 count，total=2")
    void primaryKeyInUnderfullDerives() {
        assertEquivalent("{\"" + ENTITY_ORDER + "\":{\"id|in\":\"1,2\",\"@p\":\"1,10\"}}", true);
    }

    @Test
    @DisplayName("首页恰好取满（@p=1,5 共 7 行）：执行 count，total=7")
    void firstPageFullStillCounts() {
        assertEquivalent("{\"" + ENTITY_ORDER + "\":{\"@p\":\"1,5\"}}", false);
    }

    @Test
    @DisplayName("纯 @p=1,1 无主键条件（返回满 1 行）：执行 count，total=7")
    void singleRowFullPageStillCounts() {
        assertEquivalent("{\"" + ENTITY_ORDER + "\":{\"@p\":\"1,1\"}}", false);
    }

    @Test
    @DisplayName("非首页未满（@p=2,5）：执行 count，total=7")
    void laterPageStillCounts() {
        assertEquivalent("{\"" + ENTITY_ORDER + "\":{\"@p\":\"2,5\"}}", false);
    }
}
