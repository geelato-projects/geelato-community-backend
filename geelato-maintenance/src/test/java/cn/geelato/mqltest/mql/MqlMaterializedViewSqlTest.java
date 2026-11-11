package cn.geelato.mqltest.mql;

import cn.geelato.core.enums.ViewTypeEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.mql.MqlQueryProcessor;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 物化视图（view_type=materialized）双记录架构：
 * 同名两行（原视图 + materialized 副本）时元数据注册以 materialized 优先（connect_id 指向引擎 → 查询路由引擎），
 * 与注册顺序无关；移除副本后原视图接管注册。
 * materialized 行的 viewConstruct 仅存原始建表语句（源表名形式），查询不消费它——按物理视图名直查。
 */
@DisplayName("物化视图：双记录物化优先加载")
class MqlMaterializedViewSqlTest extends MqlTestSupport {

    private static final String ENTITY_MV_VIEW = "mql_test_materialized_view";
    private static final String ORIGIN_CONNECT = "mql_test_origin_connect";
    private static final String ENGINE_CONNECT = "mql_test_engine_connect";
    private static final String ORIGIN_CONSTRUCT = "select o.id, o.order_no from orders o where o.del_status = 0";
    private static final String VIEW_COLUMN = "[{\"field_name\":\"id\",\"column_name\":\"id\",\"title\":\"ID\",\"data_type\":\"BIGINT\",\"column_key\":true},"
            + "{\"field_name\":\"order_no\",\"column_name\":\"order_no\",\"title\":\"订单号\",\"data_type\":\"VARCHAR\"}]";

    @BeforeAll
    static void registerMvView() {
        MetaManager mm = MetaManager.singleInstance();
        if (!mm.containsEntity(ENTITY_MV_VIEW)) {
            mm.parseViewEntity(buildView(ViewTypeEnum.MATERIALIZED.getCode(), ENGINE_CONNECT));
        }
    }

    @AfterAll
    static void unregisterViews() {
        MetaManager mm = MetaManager.singleInstance();
        mm.removeOne(ENTITY_MV_VIEW);
    }

    private static TableView buildView(String viewType, String connectId) {
        TableView view = new TableView();
        view.setViewName(ENTITY_MV_VIEW);
        view.setTitle("物化视图构建测试(" + viewType + ")");
        view.setViewType(viewType);
        view.setConnectId(connectId);
        view.setViewConstruct(ORIGIN_CONSTRUCT);
        view.setViewColumn(VIEW_COLUMN);
        return view;
    }

    @Test
    @DisplayName("物化视图按物理视图名直查，viewConstruct 不被包成派生表")
    void materializedViewQueriesByPhysicalName() {
        String mql = "{\"" + ENTITY_MV_VIEW + "\":{\"@fs\":\"id,order_no\",\"@p\":\"1,10\"}}";
        MqlQueryProcessor.ProcessedQuery pq = MqlQueryProcessor.getInstance().process(mql);
        String sql = pq.getBoundPageSql().getBoundSql().getSql();
        assertFalse(sql.contains("(" + ORIGIN_CONSTRUCT), "viewConstruct 不应被包成派生表: " + sql);
        assertFalse(sql.contains("from orders"), "不应引用原始源表: " + sql);
        assertTrue(sql.contains(ENTITY_MV_VIEW), "FROM 应为物理视图名: " + sql);
    }

    @Test
    @DisplayName("原视图先注册、物化副本后注册 → 副本覆盖（connectId 指向引擎）")
    void materializedOverridesOriginRegardlessOfOrder1() {
        MetaManager mm = MetaManager.singleInstance();
        mm.removeOne(ENTITY_MV_VIEW);
        mm.parseViewEntity(buildView(ViewTypeEnum.CUSTOM.getCode(), ORIGIN_CONNECT));
        assertEquals(ORIGIN_CONNECT, mm.getByEntityName(ENTITY_MV_VIEW).getTableMeta().getConnectId());
        mm.parseViewEntity(buildView(ViewTypeEnum.MATERIALIZED.getCode(), ENGINE_CONNECT));
        assertEquals(ENGINE_CONNECT, mm.getByEntityName(ENTITY_MV_VIEW).getTableMeta().getConnectId());
    }

    @Test
    @DisplayName("物化副本先注册、原视图后注册 → 原视图不覆盖副本")
    void materializedOverridesOriginRegardlessOfOrder2() {
        MetaManager mm = MetaManager.singleInstance();
        mm.removeOne(ENTITY_MV_VIEW);
        mm.parseViewEntity(buildView(ViewTypeEnum.MATERIALIZED.getCode(), ENGINE_CONNECT));
        mm.parseViewEntity(buildView(ViewTypeEnum.CUSTOM.getCode(), ORIGIN_CONNECT));
        assertEquals(ENGINE_CONNECT, mm.getByEntityName(ENTITY_MV_VIEW).getTableMeta().getConnectId());
    }

    @Test
    @DisplayName("移除物化副本注册后原视图接管（停用回退语义）")
    void originTakesOverAfterMaterializedRemoved() {
        MetaManager mm = MetaManager.singleInstance();
        mm.removeOne(ENTITY_MV_VIEW);
        mm.parseViewEntity(buildView(ViewTypeEnum.MATERIALIZED.getCode(), ENGINE_CONNECT));
        assertEquals(ENGINE_CONNECT, mm.getByEntityName(ENTITY_MV_VIEW).getTableMeta().getConnectId());
        // 模拟停用：副本行软删后 refreshViewMeta 只剩原视图行
        mm.removeOne(ENTITY_MV_VIEW);
        mm.parseViewEntity(buildView(ViewTypeEnum.CUSTOM.getCode(), ORIGIN_CONNECT));
        EntityMeta restored = mm.getByEntityName(ENTITY_MV_VIEW);
        assertEquals(ORIGIN_CONNECT, restored.getTableMeta().getConnectId());
    }
}
