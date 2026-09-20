package cn.geelato.web.platform.srv.platform.service;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Id;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link RuleService} 的 count SQL 可推导跳过行为测试（不起数据库，Dao 打桩记录调用）。
 * <p>
 * 验证 /meta/list 链路（queryForMapList / queryForMultiMapList）：
 * <ul>
 *   <li>可推导场景（全量、主键等值、首页未满）不执行 count，total 由数据行派生；</li>
 *   <li>不可推导场景（首页恰好取满、非首页、含外键引用列）仍执行 count，total 为真实查询结果。</li>
 * </ul>
 */
@DisplayName("RuleService：count SQL 可推导跳过")
class RuleServiceCountSkipTest {

    private static final String ENTITY_A = "rule_service_count_skip_a";
    private static final String ENTITY_B = "rule_service_count_skip_b";

    @Entity(name = ENTITY_A, table = "rule_service_count_skip_a")
    public static class CountSkipEntityA {
        @Id
        @Col(name = "id", dataType = "VARCHAR")
        private String id;
        @Col(name = "status", dataType = "VARCHAR")
        private String status;
        @Col(name = "user_id", dataType = "VARCHAR")
        @ForeignKey(fTable = CountSkipEntityB.class)
        private String userId;
    }

    @Entity(name = ENTITY_B, table = "rule_service_count_skip_b")
    public static class CountSkipEntityB {
        @Id
        @Col(name = "id", dataType = "VARCHAR")
        private String id;
        @Col(name = "status", dataType = "VARCHAR")
        private String status;
    }

    @BeforeAll
    static void registerEntities() {
        MetaManager mm = MetaManager.singleInstance();
        mm.parseOne(CountSkipEntityA.class);
        mm.parseOne(CountSkipEntityB.class);
    }

    /** 打桩 Dao：记录 queryTotal 调用次数，数据查询返回预置行。 */
    static class CountingDao extends Dao {
        int queryTotalCalls;
        List<Map<String, Object>> rows = new ArrayList<>();

        CountingDao() {
            super(null);
        }

        @Override
        public List<Map<String, Object>> queryForMapList(BoundPageSql boundPageSql) {
            return rows;
        }

        @Override
        public Long queryTotal(BoundPageSql boundPageSql) {
            queryTotalCalls++;
            return 999L;
        }
    }

    private static List<Map<String, Object>> rows(int count) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", String.valueOf(i + 1));
            list.add(row);
        }
        return list;
    }

    private RuleService newService(CountingDao dao) {
        RuleService service = new RuleService();
        service.dao = dao;
        return service;
    }

    @Test
    @DisplayName("主键等值 + @p=1,1：跳过 count，total=行数")
    void idEqWithSingleRowPageSkipsCount() {
        CountingDao dao = new CountingDao();
        dao.rows = rows(1);
        Map<String, Object> result = newService(dao)
                .queryForMapList("{\"" + ENTITY_A + "\":{\"id\":\"1\",\"@p\":\"1,1\"}}", false);
        assertEquals(0, dao.queryTotalCalls, "主键等值检索结果 ≤1，count 应跳过");
        assertEquals(1L, ((Number) result.get("total")).longValue());
    }

    @Test
    @DisplayName("全量查询（未传 @p）：跳过 count，total=行数")
    void nonPagingQuerySkipsCount() {
        CountingDao dao = new CountingDao();
        dao.rows = rows(3);
        Map<String, Object> result = newService(dao)
                .queryForMapList("{\"" + ENTITY_A + "\":{\"@fs\":\"id\"}}", false);
        assertEquals(0, dao.queryTotalCalls, "无分页即全量，count 恒等于行数，应跳过");
        assertEquals(3L, ((Number) result.get("total")).longValue());
    }

    @Test
    @DisplayName("首页未满：跳过 count，total=行数")
    void firstPageUnderfullSkipsCount() {
        CountingDao dao = new CountingDao();
        dao.rows = rows(3);
        Map<String, Object> result = newService(dao)
                .queryForMapList("{\"" + ENTITY_A + "\":{\"@p\":\"1,10\"}}", false);
        assertEquals(0, dao.queryTotalCalls, "首页未取满则无更多行，count 应跳过");
        assertEquals(3L, ((Number) result.get("total")).longValue());
    }

    @Test
    @DisplayName("首页恰好取满且无主键条件（纯 @p=1,1）：仍执行 count")
    void fullPageStillCounts() {
        CountingDao dao = new CountingDao();
        dao.rows = rows(1);
        Map<String, Object> result = newService(dao)
                .queryForMapList("{\"" + ENTITY_A + "\":{\"@p\":\"1,1\"}}", false);
        assertEquals(1, dao.queryTotalCalls, "首页取满时真实总数可能 >1，count 不可跳过");
        assertEquals(999L, ((Number) result.get("total")).longValue());
    }

    @Test
    @DisplayName("非首页（@p=2,10）：仍执行 count")
    void secondPageStillCounts() {
        CountingDao dao = new CountingDao();
        dao.rows = rows(3);
        newService(dao).queryForMapList("{\"" + ENTITY_A + "\":{\"@p\":\"2,10\"}}", false);
        assertEquals(1, dao.queryTotalCalls, "非首页时前页是否取满未知，count 不可跳过");
    }

    @Test
    @DisplayName("含外键引用列（ref() 外键查询）时主键等值不再跳过：仍执行 count")
    void foreignFieldDisqualifiesSkip() {
        CountingDao dao = new CountingDao();
        dao.rows = rows(1);
        newService(dao).queryForMapList(
                "{\"" + ENTITY_A + "\":{\"@fs\":\"id,ref(userId) userName\",\"id\":\"1\",\"@p\":\"1,1\"}}", false);
        assertEquals(1, dao.queryTotalCalls, "join 可能放大行数，主键等值上界不成立，count 不可跳过");
    }

    @Test
    @DisplayName("multi 查询按每个命令独立判定：可推导跳过、不可推导执行")
    void multiQueryPerCommandDecision() {
        CountingDao dao = new CountingDao();
        dao.rows = rows(1);
        Map<String, Object> result = newService(dao).queryForMultiMapList(
                "[{\"" + ENTITY_A + "\":{\"id\":\"1\",\"@p\":\"1,1\"}},"
                        + "{\"" + ENTITY_B + "\":{\"@p\":\"1,1\"}}]", false);
        assertEquals(1, dao.queryTotalCalls, "仅第二个命令不可推导，count 应只执行一次");
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) result.get("data");
        Map<String, Object> pageA = (Map<String, Object>) dataMap.get(ENTITY_A);
        Map<String, Object> pageB = (Map<String, Object>) dataMap.get(ENTITY_B);
        assertEquals(1L, ((Number) pageA.get("total")).longValue(), "主键等值命令 total 派生为行数");
        assertEquals(999L, ((Number) pageB.get("total")).longValue(), "首页取满命令 total 为真实 count");
    }
}
