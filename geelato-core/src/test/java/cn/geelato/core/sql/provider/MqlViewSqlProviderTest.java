package cn.geelato.core.sql.provider;

import cn.geelato.core.enums.ViewTypeEnum;
import cn.geelato.core.mql.MetaQLManager;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.view.ViewMeta;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MqlViewSqlProviderTest {
    private static final String DEFAULT_VIEW_NAME = "v_test_default_mql";
    private static final String COMPLEX_VIEW_NAME = "v_test_complex_mql";

    private final MetaManager metaManager = MetaManager.singleInstance();
    private final MetaQLManager metaQLManager = MetaQLManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();

    @BeforeEach
    public void setUp() {
        metaManager.removeOne(DEFAULT_VIEW_NAME);
        metaManager.removeOne(COMPLEX_VIEW_NAME);

        User user = new User();
        user.setUserId("u1");
        user.setUserName("tester");
        user.setOrgId("org1");
        user.setDefaultOrgId("org1");
        user.setTenantCode("geelato");
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("geelato"));

        metaManager.parseViewEntity(buildViewMeta(DEFAULT_VIEW_NAME, "default",
                "select id,creator,tenant_code,profit_amount_multi,status from raw_profit where 1=1 # and profit_amount_multi={profitAmountMulti}#"));
        metaManager.parseViewEntity(buildViewMeta(COMPLEX_VIEW_NAME, "complex",
                "select id,creator,tenant_code,profit_amount_multi,status from raw_profit where 1=1 # and profit_amount_multi={profitAmountMulti}#"));
    }

    @AfterEach
    public void tearDown() {
        metaManager.removeOne(DEFAULT_VIEW_NAME);
        metaManager.removeOne(COMPLEX_VIEW_NAME);
        SecurityContext.setCurrentUser(null);
        SecurityContext.setCurrentTenant(null);
    }

    @Test
    public void shouldRenderDefaultViewWithPfTemplate() throws Exception {
        String mql = "{\"" + DEFAULT_VIEW_NAME + "\":{" +
                "\"@fs\":\"id,profitAmountMulti,status\"," +
                "\"@order\":\"profitAmountMulti|+\"," +
                "\"@p\":\"1,10\"," +
                "\"status\":\"enabled\"," +
                "\"@pf\":{\"profitAmountMulti\":\"abc\"}" +
                "}}";

        BoundPageSql boundPageSql = generatePageSql(mql);
        String sql = boundPageSql.getBoundSql().getSql();
        String countSql = boundPageSql.getCountSql();

        assertTrue(sql.contains("from (select id,creator,tenant_code,profit_amount_multi,status from raw_profit where 1=1  and profit_amount_multi='abc') vt"));
        assertTrue(sql.contains("status=?"));
        assertTrue(sql.contains("creator='u1'"));
        assertTrue(sql.contains("order by profit_amount_multi asc"));
        assertTrue(sql.contains("limit 0,10"));
        assertTrue(countSql.contains("from (select id,creator,tenant_code,profit_amount_multi,status from raw_profit where 1=1  and profit_amount_multi='abc') vt"));
        assertEquals("geelato", boundPageSql.getBoundSql().getParams()[0]);
        assertEquals("enabled", boundPageSql.getBoundSql().getParams()[1]);
    }

    @Test
    public void shouldQueryComplexPhysicalViewAndIgnorePf() throws Exception {
        String mql = "{\"" + COMPLEX_VIEW_NAME + "\":{" +
                "\"@fs\":\"id,profitAmountMulti,status\"," +
                "\"@order\":\"profitAmountMulti|-\"," +
                "\"@p\":\"2,20\"," +
                "\"status\":\"enabled\"," +
                "\"@pf\":{\"profitAmountMulti\":\"abc\"}" +
                "}}";

        BoundPageSql boundPageSql = generatePageSql(mql);
        String sql = boundPageSql.getBoundSql().getSql();
        String countSql = boundPageSql.getCountSql();

        assertTrue(sql.contains("from " + COMPLEX_VIEW_NAME + "_complex"));
        assertFalse(sql.contains("raw_profit"));
        assertFalse(sql.contains("profit_amount_multi='abc'"));
        assertTrue(sql.contains("status=?"));
        assertTrue(sql.contains("creator='u1'"));
        assertTrue(sql.contains("order by profit_amount_multi desc"));
        assertTrue(sql.contains("limit 20,20"));
        assertTrue(countSql.contains("from " + COMPLEX_VIEW_NAME + "_complex"));
        assertFalse(countSql.contains("raw_profit"));
    }

    private BoundPageSql generatePageSql(String mql) throws Exception {
        QueryPayload payload = extractPf(mql);
        QueryCommand command = metaQLManager.generateQuerySql(payload.gql);
        applyViewTemplateParams(command, payload.paramsByEntity);
        return sqlManager.generatePageQuerySql(command);
    }

    private void applyViewTemplateParams(QueryCommand command, Map<String, Map<String, Object>> paramsByEntity) {
        if (command == null || paramsByEntity == null || paramsByEntity.isEmpty()) {
            return;
        }
        EntityMeta entityMeta = metaManager.getByEntityName(command.getEntityName());
        if (entityMeta == null) {
            return;
        }
        ViewMeta viewMeta = entityMeta.getViewMeta(entityMeta.getTableName());
        if (viewMeta != null
                && viewMeta.getViewType() != null
                && !ViewTypeEnum.DEFAULT.getCode().equalsIgnoreCase(viewMeta.getViewType())) {
            return;
        }
        Map<String, Object> params = paramsByEntity.get(command.getEntityName());
        if (params != null && !params.isEmpty()) {
            command.setViewTemplateParams(params);
        }
    }

    private QueryPayload extractPf(String mql) {
        JSONObject root = JSON.parseObject(mql);
        Map<String, Map<String, Object>> paramsByEntity = new HashMap<>();
        root.forEach((entityName, value) -> {
            if (!(value instanceof JSONObject entityBody)) {
                return;
            }
            JSONObject pf = entityBody.getJSONObject("@pf");
            if (pf != null) {
                paramsByEntity.put(entityName, new HashMap<>(pf));
                entityBody.remove("@pf");
            }
        });
        return new QueryPayload(JSON.toJSONString(root), paramsByEntity);
    }

    private Map<String, Object> buildViewMeta(String viewName, String viewType, String viewConstruct) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", viewName + "_id");
        view.put("view_name", viewName);
        view.put("title", viewName);
        view.put("entity_name", viewName);
        view.put("table_type", "view");
        view.put("view_type", viewType);
        view.put("view_construct", viewConstruct);
        view.put("view_column", JSON.toJSONString(buildColumns(viewName)));
        return view;
    }

    private List<Map<String, Object>> buildColumns(String viewName) {
        return List.of(
                buildColumn(viewName, "id", "id", true),
                buildColumn(viewName, "creator", "creator", false),
                buildColumn(viewName, "tenantCode", "tenant_code", false),
                buildColumn(viewName, "profitAmountMulti", "profit_amount_multi", false),
                buildColumn(viewName, "status", "status", false)
        );
    }

    private Map<String, Object> buildColumn(String tableName, String fieldName, String columnName, boolean key) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("id", tableName + "_" + fieldName);
        column.put("table_name", tableName);
        column.put("field_name", fieldName);
        column.put("column_name", columnName);
        column.put("title", fieldName);
        column.put("data_type", "VARCHAR");
        column.put("column_key", String.valueOf(key));
        column.put("is_nullable", "true");
        return column;
    }

    private static class QueryPayload {
        private final String gql;
        private final Map<String, Map<String, Object>> paramsByEntity;

        private QueryPayload(String gql, Map<String, Map<String, Object>> paramsByEntity) {
            this.gql = gql;
            this.paramsByEntity = paramsByEntity;
        }
    }
}
