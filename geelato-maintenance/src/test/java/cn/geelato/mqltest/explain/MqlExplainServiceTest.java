package cn.geelato.mqltest.explain;

import cn.geelato.mqltest.dto.MqlExplainResult;
import cn.geelato.mqltest.dto.MqlValidateResult;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MqlExplainService 单元测试。
 * 验证 explain 工具链后端的核心逻辑。
 */
@DisplayName("MQL Explain Service")
class MqlExplainServiceTest extends MqlTestSupport {

    private final MqlExplainService service = new MqlExplainService();

    @Test
    @DisplayName("explain 正常 MQL 返回 SQL")
    void explainValidMql() {
        MqlExplainResult result = service.explain("{\"mql_test_user\":{\"name|eq\":\"张三\"}}");
        assertTrue(result.isSuccess());
        assertEquals("mql_test_user", result.getEntityName());
        assertNotNull(result.getSql());
        assertTrue(result.getSql().contains("mql_test_user"));
        assertArrayEquals(new Object[]{"张三"}, result.getParams());
    }

    @Test
    @DisplayName("explain 分页查询返回 countSql")
    void explainPagingQuery() {
        MqlExplainResult result = service.explain("{\"mql_test_user\":{\"@p\":\"1,10\"}}");
        assertTrue(result.isSuccess());
        assertTrue(result.isPagingQuery());
        assertNotNull(result.getCountSql());
        assertTrue(result.getCountSql().contains("count(*)"));
    }

    @Test
    @DisplayName("explain 非法 MQL 返回错误")
    void explainInvalidMql() {
        MqlExplainResult result = service.explain("{\"nonexistent_entity\":{}}");
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    @DisplayName("explain 返回 AST 快照")
    void explainReturnsAst() {
        MqlExplainResult result = service.explain("{\"mql_test_user\":{\"name|eq\":\"张三\",\"@p\":\"1,10\"}}");
        assertTrue(result.isSuccess());
        Map<String, Object> ast = result.getAst();
        assertNotNull(ast);
        assertEquals("mql_test_user", ast.get("entityName"));
        assertEquals(true, ast.get("pagingQuery"));
    }

    @Test
    @DisplayName("validate 合法 MQL")
    void validateValid() {
        MqlValidateResult result = service.validate("{\"mql_test_user\":{}}");
        assertTrue(result.isValid());
        assertEquals("mql_test_user", result.getEntityName());
    }

    @Test
    @DisplayName("validate 非法 MQL")
    void validateInvalid() {
        MqlValidateResult result = service.validate("{\"nonexistent\":{}}");
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("listEntities 返回测试实体")
    void listEntities() {
        List<String> entities = service.listEntities();
        assertNotNull(entities);
        assertTrue(entities.contains("mql_test_user"));
        assertTrue(entities.contains("mql_test_order"));
    }

    @Test
    @DisplayName("getEntitySchema 返回字段和外键")
    void getEntitySchema() {
        Map<String, Object> schema = service.getEntitySchema("mql_test_user");
        assertNotNull(schema);
        assertEquals("mql_test_user", schema.get("entityName"));
        assertNotNull(schema.get("fields"));
        assertNotNull(schema.get("foreigns"));
        // user 有 org 外键
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> foreigns = (List<Map<String, Object>>) schema.get("foreigns");
        assertFalse(foreigns.isEmpty());
    }

    @Test
    @DisplayName("getEntitySchema 不存在的实体返回 null")
    void getEntitySchemaNotFound() {
        assertNull(service.getEntitySchema("nonexistent"));
    }
}
