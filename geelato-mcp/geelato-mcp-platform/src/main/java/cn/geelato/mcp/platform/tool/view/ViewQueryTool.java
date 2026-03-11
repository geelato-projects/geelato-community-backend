package cn.geelato.mcp.platform.tool.view;

import cn.geelato.core.meta.ViewManager;
import cn.geelato.core.meta.model.view.ViewMeta;
import cn.geelato.mcp.common.tool.BaseMcpTool;
import com.alibaba.fastjson2.JSON;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 视图查询工具
 * 提供视图元数据相关的查询功能
 */
@Component
public class ViewQueryTool extends BaseMcpTool {

    private final ViewManager viewManager = ViewManager.singleInstance();

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        return response;
    }

    private Map<String, Object> createSuccessResponse(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "查询成功");
        response.put("data", data);
        return response;
    }

    @Tool(description = "获取所有视图名称列表")
    public String listAllViewNames() {
        logToolExecution("listAllViewNames");
        try {
            Set<String> viewNames = viewManager.getAllViewNames();

            if (viewNames == null || viewNames.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("暂无可用的视图元数据"));
            }

            List<String> sortedNames = new ArrayList<>(viewNames);
            Collections.sort(sortedNames);

            String jsonResult = JSON.toJSONString(createSuccessResponse(sortedNames));
            logToolResult("listAllViewNames", "返回 " + sortedNames.size() + " 个视图名称");
            return jsonResult;
        } catch (Exception e) {
            logToolError("listAllViewNames", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据视图名称查询视图的详细信息")
    public String getViewMeta(String viewName) {
        logToolExecution("getViewMeta", viewName);
        try {
            ViewMeta viewMeta = viewManager.getByViewName(viewName);

            if (viewMeta == null) {
                return JSON.toJSONString(createErrorResponse("视图不存在: " + viewName));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("viewName", viewMeta.getViewName());
            result.put("viewType", viewMeta.getViewType());
            result.put("subjectEntity", viewMeta.getSubjectEntity());
            result.put("viewConstruct", viewMeta.getViewConstruct());
            
            // 解析 viewColumn 为 JSON 对象
            Object viewColumnObj = viewMeta.getViewColumn();
            if (viewColumnObj instanceof String) {
                try {
                    Object parsedColumns = JSON.parse((String) viewColumnObj);
                    result.put("viewColumn", parsedColumns);
                } catch (Exception e) {
                    result.put("viewColumn", viewColumnObj);
                }
            } else {
                result.put("viewColumn", viewColumnObj);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getViewMeta", "返回视图 " + viewName + " 的元数据");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getViewMeta", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据视图名称查询视图的列配置信息")
    public String getViewColumns(String viewName) {
        logToolExecution("getViewColumns", viewName);
        try {
            ViewMeta viewMeta = viewManager.getByViewName(viewName);

            if (viewMeta == null) {
                return JSON.toJSONString(createErrorResponse("视图不存在：" + viewName));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("viewName", viewMeta.getViewName());
            
            // 解析 viewColumn 为 JSON 对象
            Object viewColumnObj = viewMeta.getViewColumn();
            if (viewColumnObj instanceof String) {
                try {
                    Object parsedColumns = JSON.parse((String) viewColumnObj);
                    result.put("viewColumn", parsedColumns);
                } catch (Exception e) {
                    result.put("viewColumn", viewColumnObj);
                }
            } else {
                result.put("viewColumn", viewColumnObj);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getViewColumns", "返回视图 " + viewName + " 的列配置");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getViewColumns", e);
            return JSON.toJSONString(createErrorResponse("查询失败：" + e.getMessage()));
        }
    }

    @Tool(description = "根据实体名称查询该实体关联的所有视图")
    public String getViewsByEntity(String entityName) {
        logToolExecution("getViewsByEntity", entityName);
        try {
            Set<String> allViewNames = viewManager.getAllViewNames();

            if (allViewNames == null || allViewNames.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("暂无可用的视图元数据"));
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (String viewName : allViewNames) {
                ViewMeta viewMeta = viewManager.getByViewName(viewName);
                if (viewMeta != null && entityName.equals(viewMeta.getSubjectEntity())) {
                    Map<String, Object> viewInfo = new HashMap<>();
                    viewInfo.put("viewName", viewMeta.getViewName());
                    viewInfo.put("viewType", viewMeta.getViewType());
                    viewInfo.put("subjectEntity", viewMeta.getSubjectEntity());
                    result.add(viewInfo);
                }
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getViewsByEntity", "返回实体 " + entityName + " 的 " + result.size() + " 个视图");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getViewsByEntity", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }
}
