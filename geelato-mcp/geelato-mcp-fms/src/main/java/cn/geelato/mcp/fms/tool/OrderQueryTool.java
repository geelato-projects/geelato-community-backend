package cn.geelato.mcp.fms.tool;

import cn.geelato.mcp.common.tool.BaseMcpTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class OrderQueryTool extends BaseMcpTool {

    @Tool(description = "根据订单号查询订单信息")
    public String queryOrderByNo(String orderNo) {
        logToolExecution("queryOrderByNo", orderNo);
        try {
            String result = String.format("订单信息：订单号=%s, 状态=已完成，金额=1000.00", orderNo);
            logToolResult("queryOrderByNo", result);
            return result;
        } catch (Exception e) {
            logToolError("queryOrderByNo", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询用户的所有订单")
    public String queryUserOrders(String userId) {
        logToolExecution("queryUserOrders", userId);
        try {
            String result = String.format("用户 %s 的订单列表：订单 1, 订单 2, 订单 3", userId);
            logToolResult("queryUserOrders", result);
            return result;
        } catch (Exception e) {
            logToolError("queryUserOrders", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询订单统计信息")
    public String queryOrderStatistics(String dateRange) {
        logToolExecution("queryOrderStatistics", dateRange);
        try {
            String result = String.format("订单统计：时间范围=%s, 总订单数=100, 总金额=50000.00", dateRange);
            logToolResult("queryOrderStatistics", result);
            return result;
        } catch (Exception e) {
            logToolError("queryOrderStatistics", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
