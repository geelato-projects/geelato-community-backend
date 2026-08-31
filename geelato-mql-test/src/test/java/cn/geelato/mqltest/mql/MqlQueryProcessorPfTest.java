package cn.geelato.mqltest.mql;

import cn.geelato.core.enums.ViewTypeEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.mql.MqlQueryProcessor;
import cn.geelato.core.mql.ViewTemplateParamException;
import cn.geelato.mqltest.support.MqlTestSupport;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MqlQueryProcessor 的 @pf 视图模板参数链路测试。
 * <p>
 * 回归背景：/meta/list 的 Controller 预提取 @pf 后调 process(gql)（不传外部参数），
 * process 内部二次提取拿到空 map，视图模板段被静默消除（修复：process(cleanGql, paramsByEntity)）。
 * <p>
 * @pf 仅对虚拟视图（VIRTUAL，不落库、查询时动态构造）生效；DEFAULT/COMPLEX/CUSTOM
 * 视图及非视图实体收到 @pf 时硬失败。
 */
@DisplayName("MqlQueryProcessor：@pf 参数链路")
class MqlQueryProcessorPfTest extends MqlTestSupport {

    private static final String ENTITY_COMPLEX_VIEW = "mql_test_complex_view";
    private static final String ENTITY_DEFAULT_VIEW = "mql_test_default_view";

    private static final String MQL_WITH_PF = "{\"" + ENTITY_ORDER_VIEW + "\":{"
            + "\"@fs\":\"id,orderNo,status\","
            + "\"@pf\":{\"statusFilter\":\"pending\"},"
            + "\"@p\":\"1,15\"}}";

    @BeforeAll
    protected static void registerExtraViews() {
        MetaManager mm = MetaManager.singleInstance();
        if (!mm.containsEntity(ENTITY_COMPLEX_VIEW)) {
            mm.parseViewEntity(buildView(ENTITY_COMPLEX_VIEW, ViewTypeEnum.COMPLEX.getCode()));
        }
        if (!mm.containsEntity(ENTITY_DEFAULT_VIEW)) {
            mm.parseViewEntity(buildView(ENTITY_DEFAULT_VIEW, ViewTypeEnum.DEFAULT.getCode()));
        }
    }

    private static TableView buildView(String viewName, String viewType) {
        String viewColumn = "[{\"field_name\":\"id\",\"column_name\":\"id\",\"title\":\"ID\",\"data_type\":\"BIGINT\",\"column_key\":true}]";
        TableView view = new TableView();
        view.setViewName(viewName);
        view.setTitle("测试视图(" + viewType + ")");
        view.setViewType(viewType);
        view.setViewConstruct("select id from " + ENTITY_ORDER);
        view.setViewColumn(viewColumn);
        return view;
    }

    @Test
    @DisplayName("虚拟视图（VIRTUAL）Controller 预提取后经外部参数注入，@pf 渲染进视图构造 SQL")
    void pfRenderedWithExternalParams() {
        // 模拟 MetaRuntimeController.resolveQueryPayload：预提取 @pf 并从 JSON 移除
        Map<String, Map<String, Object>> paramsByEntity = new HashMap<>();
        JSONObject root = JSON.parseObject(MQL_WITH_PF);
        String cleanGql = MqlQueryProcessor.getInstance().extractPfAndSerialize(root, paramsByEntity);

        // 修复后：外部参数传透，渲染生效
        MqlQueryProcessor.ProcessedQuery pq = MqlQueryProcessor.getInstance().process(cleanGql, paramsByEntity);
        String sql = pq.getBoundPageSql().getBoundSql().getSql();
        assertTrue(sql.contains("o.status = 'pending'"), "外部注入的 @pf 应渲染进视图构造 SQL: " + sql);
    }

    @Test
    @DisplayName("预提取后的净化 JSON 不传外部参数时保持段消除（原 bug 症状对照）")
    void pfEliminatedWithoutExternalParams() {
        Map<String, Map<String, Object>> paramsByEntity = new HashMap<>();
        JSONObject root = JSON.parseObject(MQL_WITH_PF);
        String cleanGql = MqlQueryProcessor.getInstance().extractPfAndSerialize(root, paramsByEntity);

        MqlQueryProcessor.ProcessedQuery pq = MqlQueryProcessor.getInstance().process(cleanGql);
        String sql = pq.getBoundPageSql().getBoundSql().getSql();
        assertFalse(sql.contains("pending"), "无参数时模板段应被消除: " + sql);
    }

    @Test
    @DisplayName("原始 JSON 直传单参 process（Playground/测试场景），@pf 渲染生效")
    void pfRenderedFromRawJson() {
        MqlQueryProcessor.ProcessedQuery pq = MqlQueryProcessor.getInstance().process(MQL_WITH_PF);
        String sql = pq.getBoundPageSql().getBoundSql().getSql();
        assertTrue(sql.contains("o.status = 'pending'"), "JSON 内 @pf 应渲染进视图构造 SQL: " + sql);
    }

    @Test
    @DisplayName("JSON 内 @pf 与外部参数合并，外部优先")
    void externalParamsOverrideJsonPf() {
        Map<String, Map<String, Object>> external = Map.of(ENTITY_ORDER_VIEW, Map.of("statusFilter", "shipped"));
        MqlQueryProcessor.ProcessedQuery pq = MqlQueryProcessor.getInstance().process(MQL_WITH_PF, external);
        String sql = pq.getBoundPageSql().getBoundSql().getSql();
        assertTrue(sql.contains("o.status = 'shipped'"), "外部参数应覆盖 JSON 内同名参数: " + sql);
        assertFalse(sql.contains("pending"), "被覆盖的 JSON 内参数不应渲染: " + sql);
    }

    @Test
    @DisplayName("非视图实体收到 @pf 时硬失败，不静默丢弃")
    void nonViewEntityPfThrows() {
        String mql = "{\"" + ENTITY_ORDER + "\":{\"@fs\":\"id\",\"@pf\":{\"statusFilter\":\"pending\"}}}";
        Map<String, Map<String, Object>> paramsByEntity = new HashMap<>();
        JSONObject root = JSON.parseObject(mql);
        String cleanGql = MqlQueryProcessor.getInstance().extractPfAndSerialize(root, paramsByEntity);

        ViewTemplateParamException ex = assertThrows(ViewTemplateParamException.class,
                () -> MqlQueryProcessor.getInstance().process(cleanGql, paramsByEntity));
        assertTrue(ex.getMessage().contains(ENTITY_ORDER), "错误信息应含实体名: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("statusFilter"), "错误信息应含参数名: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("不是视图实体"), "错误信息应说明原因: " + ex.getMessage());
    }

    @Test
    @DisplayName("COMPLEX 视图收到 @pf 时硬失败，不静默丢弃")
    void complexViewPfThrows() {
        String mql = "{\"" + ENTITY_COMPLEX_VIEW + "\":{\"@fs\":\"id\",\"@pf\":{\"statusFilter\":\"pending\"}}}";
        Map<String, Map<String, Object>> paramsByEntity = new HashMap<>();
        JSONObject root = JSON.parseObject(mql);
        String cleanGql = MqlQueryProcessor.getInstance().extractPfAndSerialize(root, paramsByEntity);

        ViewTemplateParamException ex = assertThrows(ViewTemplateParamException.class,
                () -> MqlQueryProcessor.getInstance().process(cleanGql, paramsByEntity));
        assertTrue(ex.getMessage().contains(ENTITY_COMPLEX_VIEW), "错误信息应含实体名: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("statusFilter"), "错误信息应含参数名: " + ex.getMessage());
    }

    @Test
    @DisplayName("DEFAULT 视图收到 @pf 时硬失败（@pf 仅支持虚拟视图）")
    void defaultViewPfThrows() {
        String mql = "{\"" + ENTITY_DEFAULT_VIEW + "\":{\"@fs\":\"id\",\"@pf\":{\"statusFilter\":\"pending\"}}}";
        Map<String, Map<String, Object>> paramsByEntity = new HashMap<>();
        JSONObject root = JSON.parseObject(mql);
        String cleanGql = MqlQueryProcessor.getInstance().extractPfAndSerialize(root, paramsByEntity);

        ViewTemplateParamException ex = assertThrows(ViewTemplateParamException.class,
                () -> MqlQueryProcessor.getInstance().process(cleanGql, paramsByEntity));
        assertTrue(ex.getMessage().contains(ENTITY_DEFAULT_VIEW), "错误信息应含实体名: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("虚拟视图"), "错误信息应说明仅支持虚拟视图: " + ex.getMessage());
    }
}
