package cn.geelato.mcp.fms.tool;

import cn.geelato.mcp.common.tool.BaseMcpTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ContainerQueryTool extends BaseMcpTool {

    @Tool(description = "根据集装箱号查询位置信息")
    public String queryContainerLocation(String containerNo) {
        logToolExecution("queryContainerLocation", containerNo);
        try {
            String result = String.format("集装箱 %s 位置：上海港，状态：运输中", containerNo);
            logToolResult("queryContainerLocation", result);
            return result;
        } catch (Exception e) {
            logToolError("queryContainerLocation", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询货代的集装箱列表")
    public String queryFreightContainers(String freightId) {
        logToolExecution("queryFreightContainers", freightId);
        try {
            String result = String.format("货代 %s 的集装箱列表：集装箱 1, 集装箱 2, 集装箱 3", freightId);
            logToolResult("queryFreightContainers", result);
            return result;
        } catch (Exception e) {
            logToolError("queryFreightContainers", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询集装箱运输轨迹")
    public String queryContainerTrack(String containerNo) {
        logToolExecution("queryContainerTrack", containerNo);
        try {
            String result = String.format("集装箱 %s 运输轨迹：上海港 -> 青岛港 -> 釜山港", containerNo);
            logToolResult("queryContainerTrack", result);
            return result;
        } catch (Exception e) {
            logToolError("queryContainerTrack", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
