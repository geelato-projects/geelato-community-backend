package cn.geelato.web.platform.srv;

import cn.geelato.core.mql.parser.InvalidPageParamException;
import cn.geelato.core.mql.parser.PageQueryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一分页参数解析测试：别名兼容、默认值、非法值硬失败、defaultOrder 修复。
 */
class ParameterOperatorTest {

    private final ParameterOperator operator = new ParameterOperator();

    // ===== POST body 版（pageQuery 链路）=====

    @Test
    void bodyAbsentParamsFallBackToDefaults() {
        PageQueryRequest r = operator.getPageQueryParameters(new HashMap<>());
        assertEquals(1, r.getPageNum());
        assertEquals(10, r.getPageSize());
        assertEquals("", r.getOrderBy());
    }

    @Test
    void bodyNullMapKeepsOriginalNoPaginationBehavior() {
        // null body 保持原行为：pageNum/pageSize 均为 0，QueryCommand.hasPagination() 判定不分页全量查询
        PageQueryRequest r = operator.getPageQueryParameters((Map<String, Object>) null);
        assertEquals(0, r.getPageNum());
        assertEquals(0, r.getPageSize());
    }

    @Test
    void bodyLegacyCurrentPageSizeOrderNames() {
        Map<String, Object> body = new HashMap<>();
        body.put("current", 3);
        body.put("pageSize", 25);
        body.put("order", "create_at|desc");
        PageQueryRequest r = operator.getPageQueryParameters(body);
        assertEquals(3, r.getPageNum());
        assertEquals(25, r.getPageSize());
        assertEquals("create_at desc", r.getOrderBy());
    }

    @Test
    void bodyCanonicalPageNumTakesPrecedenceOverLegacyAlias() {
        Map<String, Object> body = new HashMap<>();
        body.put("pageNum", 5);
        body.put("current", 9);
        PageQueryRequest r = operator.getPageQueryParameters(body);
        assertEquals(5, r.getPageNum());
    }

    @Test
    void bodyPageAndLimitAliases() {
        Map<String, Object> body = new HashMap<>();
        body.put("page", 2);
        body.put("limit", 30);
        body.put("orderBy", "seq_no|asc");
        PageQueryRequest r = operator.getPageQueryParameters(body);
        assertEquals(2, r.getPageNum());
        assertEquals(30, r.getPageSize());
        assertEquals("seq_no asc", r.getOrderBy());
    }

    @Test
    void bodyNonNumericPageNumFailsHard() {
        Map<String, Object> body = new HashMap<>();
        body.put("current", "abc");
        InvalidPageParamException ex = assertThrows(InvalidPageParamException.class,
                () -> operator.getPageQueryParameters(body));
        assertEquals(InvalidPageParamException.ERROR_CODE, ex.getErrorCode());
        assertEquals(400, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("current=abc"), ex.getMessage());
    }

    @Test
    void bodyZeroPageNumFailsHard() {
        Map<String, Object> body = new HashMap<>();
        body.put("current", 0);
        InvalidPageParamException ex = assertThrows(InvalidPageParamException.class,
                () -> operator.getPageQueryParameters(body));
        assertTrue(ex.getMessage().contains("current=0"), ex.getMessage());
    }

    @Test
    void bodyNegativePageSizeFailsHard() {
        Map<String, Object> body = new HashMap<>();
        body.put("pageSize", -5);
        assertThrows(InvalidPageParamException.class, () -> operator.getPageQueryParameters(body));
    }

    @Test
    void bodyPageSizeOverLimitFailsHard() {
        Map<String, Object> body = new HashMap<>();
        body.put("pageSize", 5000);
        InvalidPageParamException ex = assertThrows(InvalidPageParamException.class,
                () -> operator.getPageQueryParameters(body));
        assertTrue(ex.getMessage().contains("5000"), ex.getMessage());
        assertTrue(ex.getMessage().contains("1000"), ex.getMessage());
    }

    // ===== defaultOrder（修复：仅在未传排序时应用默认排序）=====

    @Test
    void defaultOrderAppliedOnlyWhenOrderByAbsent() {
        Map<String, Object> body = new HashMap<>();
        body.put("order", "seq_no asc");
        PageQueryRequest withUserOrder = operator.getPageQueryParameters(body, "create_at DESC");
        assertEquals("seq_no asc", withUserOrder.getOrderBy());

        PageQueryRequest withoutUserOrder = operator.getPageQueryParameters(new HashMap<>(), "create_at DESC");
        assertEquals("create_at DESC", withoutUserOrder.getOrderBy());
    }

    // ===== GET 版（query 链路）=====

    @Test
    void getAbsentParamsMeanNoPagination() {
        // 未传分页参数 → -1：QueryCommand.hasPagination() 以 >0 判定，即不分页全量查询
        PageQueryRequest r = operator.getPageQueryParameters(new MockHttpServletRequest());
        assertEquals(-1, r.getPageNum());
        assertEquals(-1, r.getPageSize());
    }

    @Test
    void getCanonicalParamsWithOrderByPipe() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pageNum", "2");
        request.setParameter("pageSize", "50");
        request.setParameter("orderBy", "update_at|desc");
        PageQueryRequest r = operator.getPageQueryParameters(request);
        assertEquals(2, r.getPageNum());
        assertEquals(50, r.getPageSize());
        assertEquals("update_at desc", r.getOrderBy());
    }

    @Test
    void getLegacyCurrentAlias() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("current", "7");
        PageQueryRequest r = operator.getPageQueryParameters(request);
        assertEquals(7, r.getPageNum());
    }

    @Test
    void getNonNumericFailsHard() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pageSize", "ten");
        InvalidPageParamException ex = assertThrows(InvalidPageParamException.class,
                () -> operator.getPageQueryParameters(request));
        assertTrue(ex.getMessage().contains("pageSize=ten"), ex.getMessage());
    }

    @Test
    void getDefaultOrderAppliedOnlyWhenOrderByAbsent() {
        MockHttpServletRequest withUserOrder = new MockHttpServletRequest();
        withUserOrder.setParameter("orderBy", "name asc");
        assertEquals("name asc", operator.getPageQueryParameters(withUserOrder, "update_at DESC").getOrderBy());

        assertEquals("update_at DESC",
                operator.getPageQueryParameters(new MockHttpServletRequest(), "update_at DESC").getOrderBy());
    }
}
