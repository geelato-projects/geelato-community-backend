package cn.geelato.mcp.fms.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderQueryToolTest {

    @Autowired
    private OrderQueryTool orderQueryTool;

    @Test
    void testQueryOrderByNo() {
        String result = orderQueryTool.queryOrderByNo("ORD123456");
        assertNotNull(result);
        assertTrue(result.contains("ORD123456"));
        assertTrue(result.contains("订单信息"));
    }

    @Test
    void testQueryUserOrders() {
        String result = orderQueryTool.queryUserOrders("USER001");
        assertNotNull(result);
        assertTrue(result.contains("USER001"));
        assertTrue(result.contains("订单列表"));
    }

    @Test
    void testQueryOrderStatistics() {
        String result = orderQueryTool.queryOrderStatistics("2025-01");
        assertNotNull(result);
        assertTrue(result.contains("2025-01"));
        assertTrue(result.contains("订单统计"));
    }
}
